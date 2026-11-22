package dev.michelle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Meal::class, Item::class, PendingDelete::class], version = 1)
abstract class Db : RoomDatabase() {
    abstract fun meals(): MealDao

    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, Db::class.java, "michelle.db").build()
    }
}
