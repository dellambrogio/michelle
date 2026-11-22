/*
 * Health Connect, write only: each meal is one NutritionRecord whose clientRecordId is "meal-<id>", so
 * writing it again replaces it and deleting goes by that id (R15–R17). Nothing here ever fails a local
 * save: a meal that couldn't be written stays unsynced and is retried on the next start or "sync now".
 */
package dev.michelle.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import dev.michelle.data.MealDao
import dev.michelle.data.MealType
import dev.michelle.data.MealWithItems
import dev.michelle.data.PendingDelete
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import androidx.health.connect.client.records.MealType as HcMealType

private const val TAG = "michelle"

class HealthWriter(private val context: Context, private val dao: MealDao) {
    val available get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val permissionContract = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(): Boolean = available &&
        runCatching {
            withTimeout(10_000) { client.permissionController.getGrantedPermissions() }.containsAll(PERMISSIONS)
        }
            .onFailure { Log.w(TAG, "health connect: can't read the permissions", it) }
            .getOrDefault(false)

    /** Write the meal (again); marks it synced when it worked. */
    suspend fun write(meal: MealWithItems): Boolean {
        if (!granted()) return false
        return try {
            client.insertRecords(listOf(record(meal)))
            dao.markSynced(meal.meal.id, meal.meal.updatedAt)
            Log.i(TAG, "health connect: wrote meal ${meal.meal.id}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "health connect: writing meal ${meal.meal.id} failed", e)
            false
        }
    }

    /** Delete the meal's record; if that can't be done now, remember to do it later. */
    suspend fun delete(mealId: Long) {
        if (granted() && runCatching {
                client.deleteRecords(NutritionRecord::class, emptyList(), listOf(clientId(mealId)))
            }.onFailure { Log.w(TAG, "health connect: deleting meal $mealId failed", it) }.isSuccess
        ) {
            dao.donePendingDelete(mealId)
            Log.i(TAG, "health connect: deleted meal $mealId")
        } else {
            dao.addPendingDelete(PendingDelete(mealId))
        }
    }

    /** Write what isn't written yet and delete what should be gone. */
    suspend fun syncPending() {
        Log.i(TAG, "health connect: sync (sdk status ${HealthConnectClient.getSdkStatus(context)})")
        if (!granted()) {
            Log.i(TAG, "health connect: ${if (available) "no permission" else "not available"}, nothing synced")
            return
        }
        dao.pendingDeletes().forEach { delete(it.mealId) }
        dao.unsynced().forEach { write(it) }
    }

    companion object {
        val PERMISSIONS = setOf(HealthPermission.getWritePermission(NutritionRecord::class))

        fun clientId(mealId: Long) = "meal-$mealId"

        fun record(m: MealWithItems): NutritionRecord {
            val start = Instant.ofEpochMilli(m.meal.time)
            val end = start.plusSeconds(60)
            val offset = ZoneId.systemDefault().rules.getOffset(start)
            val t = m.totals
            return NutritionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                metadata = Metadata.manualEntry(clientId(m.meal.id), m.meal.updatedAt),
                energy = Energy.kilocalories(t.kcal.toDouble()),
                protein = Mass.grams(t.proteinG),
                totalCarbohydrate = Mass.grams(t.carbsG),
                totalFat = Mass.grams(t.fatG),
                name = m.meal.title.ifBlank { null },
                mealType = hcMealType(MealType.of(m.meal.mealType)),
            )
        }

        fun hcMealType(t: MealType) = when (t) {
            MealType.BREAKFAST -> HcMealType.MEAL_TYPE_BREAKFAST
            MealType.LUNCH -> HcMealType.MEAL_TYPE_LUNCH
            MealType.DINNER -> HcMealType.MEAL_TYPE_DINNER
            MealType.SNACK -> HcMealType.MEAL_TYPE_SNACK
        }
    }
}
