package dev.michelle.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.michelle.App
import dev.michelle.ai.Analysis
import dev.michelle.ai.Outcome
import dev.michelle.ai.Trouble
import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealType
import dev.michelle.data.Totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** One item being edited: text fields, so half-typed numbers are fine. */
class EditItem(name: String = "", portion: String = "", kcal: String = "", protein: String = "", carbs: String = "", fat: String = "") {
    var name by mutableStateOf(name)
    var portion by mutableStateOf(portion)
    var kcal by mutableStateOf(kcal)
    var protein by mutableStateOf(protein)
    var carbs by mutableStateOf(carbs)
    var fat by mutableStateOf(fat)

    fun toItem() = Item(
        name = name.trim().ifBlank { "Item" }, portion = portion.trim(),
        kcal = num(kcal).roundToInt(), proteinG = num(protein), carbsG = num(carbs), fatG = num(fat),
    )

    companion object {
        fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }
}

sealed interface Phase {
    data object Preparing : Phase                    // the photo is being shrunk
    data object Analyzing : Phase
    data object Describing : Phase                   // no photo: the description to estimate (R33)
    data class NoFood(val note: String) : Phase
    data class Failed(val trouble: Trouble, val detail: String?) : Phase
    data object Editing : Phase
    data object Done : Phase                         // saved, discarded or deleted: leave the screen
}

class MealViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private var started = false
    private var existing: Meal? = null

    var phase by mutableStateOf<Phase>(Phase.Preparing); private set
    var photoPath by mutableStateOf<String?>(null); private set
    var title by mutableStateOf("")
    var mealType by mutableStateOf(MealType.SNACK)
        private set
    var time by mutableStateOf(LocalDateTime.now())
        private set
    private var typeChosen = false                   // the meal type was picked by hand: the time no longer changes it

    fun pickType(t: MealType) { mealType = t; typeChosen = true }

    /** A new date or time; the meal type follows the time unless it was picked by hand (R6, R7). */
    fun setWhen(t: LocalDateTime) {
        time = t
        if (!typeChosen) mealType = MealType.fromTime(t.toLocalTime())
    }
    var note by mutableStateOf("")
    val items = mutableStateListOf<EditItem>()
    var description by mutableStateOf("")
    var manualKcal by mutableStateOf("")             // typed on the manual screen, instead of estimating
    private var described = false                    // the last estimate was from words, not a photo
    private var raw: String? = null
    private var model: String? = null

    val isNew get() = existing == null
    val totals get() = Totals.of(items.map { it.toItem() })

    /** Open a saved meal (id > 0) or start a new one from a captured photo. */
    fun start(id: Long, capture: String?, describe: Boolean = false) {
        if (started) return
        started = true
        viewModelScope.launch {
            if (id > 0) {
                val m = app.meals.get(id) ?: run { phase = Phase.Done; return@launch }
                existing = m.meal
                photoPath = m.meal.photoPath
                title = m.meal.title
                mealType = MealType.of(m.meal.mealType)
                typeChosen = true
                time = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.meal.time), ZoneId.systemDefault())
                raw = m.meal.rawAnalysis
                model = m.meal.model
                m.sorted.forEach { items += EditItem(it.name, it.portion, it.kcal.toString(), plain(it.proteinG), plain(it.carbsG), plain(it.fatG)) }
                phase = Phase.Editing
            } else if (describe) {
                time = LocalDateTime.now()
                mealType = MealType.fromTime(LocalTime.now())
                phase = Phase.Describing
            } else {
                time = LocalDateTime.now()
                mealType = MealType.fromTime(LocalTime.now())
                val kept = capture?.let { withContext(Dispatchers.IO) { app.photos.keep(File(it)) } }
                if (kept == null) { phase = Phase.Done; return@launch }
                photoPath = kept.path
                analyze()
            }
        }
    }

    fun analyze() {
        if (described) return estimate()
        val path = photoPath ?: return
        phase = Phase.Analyzing
        viewModelScope.launch { take(app.analyzer.analyze(File(path))) }
    }

    /** Claude estimates the meal from [description] (R34). */
    fun estimate() {
        if (description.isBlank()) return
        described = true
        phase = Phase.Analyzing
        viewModelScope.launch { take(app.analyzer.describe(description)) }
    }

    private fun take(o: Outcome) {
        when (o) {
            is Outcome.Failed -> phase = Phase.Failed(o.trouble, o.detail)
            is Outcome.Done -> {
                raw = o.raw
                model = o.model
                if (!o.analysis.food) phase = Phase.NoFood(o.analysis.note) else fill(o.analysis)
            }
        }
    }

    /** Back to the description, to change it. */
    fun redescribe() { phase = Phase.Describing }

    val isDescribed get() = photoPath == null

    private fun fill(a: Analysis) {
        title = a.title.ifBlank { description.trim().take(60) }
        note = a.note
        items.clear()
        a.items.forEach { items += EditItem(it.name, it.portion, it.kcal.roundToInt().toString(), plain(it.proteinG), plain(it.carbsG), plain(it.fatG)) }
        phase = Phase.Editing
    }

    /** No food found, or the analysis failed: type the items in by hand. */
    fun byHand() {
        if (items.isEmpty()) items += EditItem()
        phase = Phase.Editing
    }

    /** Manual screen, kcal typed: one item named after the description (R33). */
    fun saveManual(onSaved: () -> Unit) {
        val name = description.trim().ifBlank { "Food" }
        items.clear()
        items += EditItem(name = name, kcal = manualKcal.trim())
        if (title.isBlank()) title = name.take(60)
        save(onSaved)
    }

    val manualKcalOk get() = EditItem.num(manualKcal) > 0

    fun addItem() { items += EditItem() }
    fun removeItem(i: EditItem) { items.remove(i) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val meal = Meal(
            id = existing?.id ?: 0,
            time = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            mealType = mealType.name,
            title = title.trim(),
            photoPath = photoPath,
            rawAnalysis = raw,
            model = model,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        app.meals.save(meal, items.map { it.toItem() })
        onSaved()
        phase = Phase.Done
    }

    /** Leave without saving; a new meal's photo goes too (R9). */
    fun discard() {
        if (isNew) app.photos.delete(photoPath)
        phase = Phase.Done
    }

    fun delete() = viewModelScope.launch {
        existing?.let { app.meals.delete(it.id) }
        phase = Phase.Done
    }
}
