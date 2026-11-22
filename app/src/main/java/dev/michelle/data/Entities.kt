package dev.michelle.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "meal", indices = [Index("time")])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,                 // when it was eaten, epoch ms
    val mealType: String,           // MealType.name
    val title: String,
    val photoPath: String?,         // in filesDir/photos
    val rawAnalysis: String?,       // Claude's JSON, as it came (R12)
    val model: String?,             // the model that made it
    val hcSynced: Boolean = false,  // written to Health Connect, as it is now
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "item",
    foreignKeys = [ForeignKey(entity = Meal::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mealId")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long = 0,
    val position: Int = 0,
    val name: String,
    val portion: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** A meal deleted here whose Health Connect record still has to go (no permission or Health Connect busy). */
@Entity(tableName = "hc_delete")
data class PendingDelete(@PrimaryKey val mealId: Long)

data class MealWithItems(
    @Embedded val meal: Meal,
    @Relation(parentColumn = "id", entityColumn = "mealId") val items: List<Item>,
) {
    val sorted get() = items.sortedBy { it.position }
    val totals get() = Totals.of(items)
}
