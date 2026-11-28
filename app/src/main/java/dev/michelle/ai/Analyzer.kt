/*
 * Claude looks at the photo: one request through the Anthropic API with the user's key, the photo as a
 * base64 JPEG, and the answer held to Prompt.SCHEMA by structured output. Opus and Sonnet get the
 * server-side refusal fallback (another model answers if the first declines); Haiku doesn't have it.
 */
package dev.michelle.ai

import android.util.Base64
import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaFallbacksParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaMessageContentParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import dev.michelle.Secrets
import dev.michelle.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "michelle"

/** Why there's no analysis. */
enum class Trouble(val message: String) {
    NO_KEY("No Anthropic API key yet: add it in settings."),
    BAD_KEY("The API key doesn't work: check it in settings."),
    NO_NETWORK("No connection to Anthropic. Check the network and retry."),
    BUSY("Anthropic is busy right now. Retry in a moment."),
    REFUSED("Claude declined to analyze this photo."),
    FAILED("The analysis failed."),
}

/** Claude's review of the log (R35), or why there's none. */
sealed interface ReviewOutcome {
    data class Done(val review: Review, val raw: String, val model: String) : ReviewOutcome
    data class Failed(val trouble: Trouble, val detail: String? = null) : ReviewOutcome
}

sealed interface Outcome {
    data class Done(val analysis: Analysis, val raw: String, val model: String) : Outcome
    data class Failed(val trouble: Trouble, val detail: String? = null) : Outcome
}

class Analyzer(private val secrets: Secrets, private val settings: Settings) {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    @Synchronized
    private fun client(): AnthropicClient? {
        val key = secrets.apiKey ?: return null
        if (key != clientKey) {
            client?.close()
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client
    }

    /** What's in the photo. */
    suspend fun analyze(photo: File): Outcome = run(Prompt.SYSTEM) {
        val image = BetaImageBlockParam.builder().source(
            BetaImageBlockParam.Source.ofBase64(
                BetaBase64ImageSource.builder()
                    .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                    .data(Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
                    .build()
            )
        ).build()
        listOf(BetaContentBlockParam.ofImage(image), BetaContentBlockParam.ofText(Prompt.ASK))
    }

    /** What's in a meal described in words (R34). */
    suspend fun describe(text: String): Outcome = run(Prompt.SYSTEM_TEXT) { listOf(BetaContentBlockParam.ofText(text.trim())) }

    // counting food doesn't need deep thought
    private suspend fun run(system: String, content: () -> List<BetaContentBlockParam>): Outcome =
        when (val a = ask("analysis", system, content(), Prompt.SCHEMA, BetaOutputConfig.Effort.LOW)) {
            is Answer.Failed -> Outcome.Failed(a.trouble, a.detail)
            is Answer.Text -> Analysis.parse(a.raw)?.let { Outcome.Done(it, a.raw, a.model) }
                ?: Outcome.Failed(Trouble.FAILED, "unexpected answer")
        }

    /** A look back at the food log [log], written as ReviewPrompt.log makes it (R35). */
    suspend fun review(log: String): ReviewOutcome {
        val content = listOf(BetaContentBlockParam.ofText(log), BetaContentBlockParam.ofText(ReviewPrompt.ASK))
        // reading two weeks for patterns earns a bit more thought than counting a plate
        return when (val a = ask("review", ReviewPrompt.SYSTEM, content, ReviewPrompt.SCHEMA, BetaOutputConfig.Effort.MEDIUM)) {
            is Answer.Failed -> ReviewOutcome.Failed(a.trouble, a.detail)
            is Answer.Text -> Review.parse(a.raw)?.let { ReviewOutcome.Done(it, a.raw, a.model) }
                ?: ReviewOutcome.Failed(Trouble.FAILED, "unexpected answer")
        }
    }

    private sealed interface Answer {
        data class Text(val raw: String, val model: String) : Answer
        data class Failed(val trouble: Trouble, val detail: String? = null) : Answer
    }

    /** One request, the answer held to [schema]: its JSON text, or why there's none. */
    private suspend fun ask(
        what: String, system: String, content: List<BetaContentBlockParam>, schema: Map<String, Any>, effort: BetaOutputConfig.Effort,
    ): Answer = withContext(Dispatchers.IO) {
        val client = client() ?: return@withContext Answer.Failed(Trouble.NO_KEY)
        val model = settings.model
        val started = System.currentTimeMillis()
        try {
            val response = client.beta().messages().create(params(model, system, content, schema, effort))
            val took = System.currentTimeMillis() - started
            val stop = response.stopReason().orElse(null)
            Log.i(TAG, "$what by ${response.model()} in $took ms, stop=$stop")
            if (stop == BetaStopReason.REFUSAL) return@withContext Answer.Failed(Trouble.REFUSED)
            val raw = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            Log.i(TAG, "$what: $raw")
            Answer.Text(raw, response.model().toString())
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
            val trouble = when (e) {
                is UnauthorizedException, is PermissionDeniedException -> Trouble.BAD_KEY
                is RateLimitException -> Trouble.BUSY
                is AnthropicIoException -> Trouble.NO_NETWORK
                is AnthropicServiceException -> if (e.statusCode() >= 500) Trouble.BUSY else Trouble.FAILED
                else -> Trouble.FAILED
            }
            Answer.Failed(trouble, e.message)
        }
    }

    private fun params(
        model: String, system: String, content: List<BetaContentBlockParam>, json: Map<String, Any>, effort: BetaOutputConfig.Effort,
    ): MessageCreateParams {
        val schema = BetaJsonOutputFormat.Schema.builder()
            .putAllAdditionalProperties(json.mapValues { JsonValue.from(it.value) })
            .build()
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .addUserMessage(BetaMessageContentParam.ofContentBlockParams(content))
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(effort)
                    .format(BetaJsonOutputFormat.builder().schema(schema).build())
                    .build()
            )
        if (!model.startsWith("claude-haiku")) {
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacks(BetaFallbacksParam.ofDefault())
        }
        return builder.build()
    }
}
