package dev.michelle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal WHERE time >= :from AND time < :to ORDER BY time DESC")
    fun between(from: Long, to: Long): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal WHERE id = :id")
    suspend fun get(id: Long): MealWithItems?

    @Insert suspend fun insert(meal: Meal): Long
    @Update suspend fun update(meal: Meal)
    @Insert suspend fun insertItems(items: List<Item>)
    @Query("DELETE FROM item WHERE mealId = :mealId") suspend fun deleteItems(mealId: Long)
    @Query("DELETE FROM meal WHERE id = :id") suspend fun delete(id: Long)

    /** Store a meal with its items, replacing the items it had; returns its id. */
    @Transaction
    suspend fun save(meal: Meal, items: List<Item>): Long {
        val id = if (meal.id == 0L) insert(meal) else meal.id.also { update(meal); deleteItems(it) }
        insertItems(items.mapIndexed { i, it -> it.copy(id = 0, mealId = id, position = i) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM meal WHERE hcSynced = 0")
    suspend fun unsynced(): List<MealWithItems>

    @Query("UPDATE meal SET hcSynced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM meal WHERE hcSynced = 0")
    fun unsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addPendingDelete(p: PendingDelete)
    @Query("SELECT * FROM hc_delete") suspend fun pendingDeletes(): List<PendingDelete>
    @Query("DELETE FROM hc_delete WHERE mealId = :mealId") suspend fun donePendingDelete(mealId: Long)
}
