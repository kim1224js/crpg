package es.kim.crpg.data

import androidx.room.Entity

@Entity(
    tableName = "monster_drop",
    primaryKeys = ["monsterCode", "itemCode"]
)
data class MonsterDropEntity(
    val monsterCode: String,
    val itemCode: String,
    val dropRate: Double
)
