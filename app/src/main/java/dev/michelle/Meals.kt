package dev.michelle

import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealDao
import dev.michelle.health.HealthWriter
import dev.michelle.photo.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Saving and deleting meals: the database first, then Health Connect in the background (R9, R14, R17). */
class Meals(private val dao: MealDao, private val health: HealthWriter, private val photos: Photos, private val scope: CoroutineScope) {
    fun on(day: LocalDate) = ZoneId.systemDefault().let { zone ->
        dao.between(day.atStartOfDay(zone).toInstant().toEpochMilli(), day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    /** Meals from day [from] to day [to], both included. */
    fun range(from: LocalDate, to: LocalDate) = ZoneId.systemDefault().let { zone ->
        dao.between(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(meal: Meal, items: List<Item>): Long {
        val id = dao.save(meal.copy(hcSynced = false), items)
        scope.launch { dao.get(id)?.let { health.write(it) } }
        return id
    }

    suspend fun delete(id: Long) {
        val m = dao.get(id) ?: return
        dao.delete(id)
        photos.delete(m.meal.photoPath)
        scope.launch { health.delete(id) }
    }

    fun syncSoon() = scope.launch { health.syncPending() }

    val unsyncedCount get() = dao.unsyncedCount()
}
