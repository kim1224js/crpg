package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(
    tableName = "monster_drop",
    primaryKeys = ["monsterCode", "itemCode"]
)
data class MonsterDropEntity(
    val monsterCode: String,
    val itemCode: String,
    val dropRate: Double,
    @ColumnInfo(defaultValue = "1") val dropQuantity: Int = 1
)
