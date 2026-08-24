package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monster_definition")
data class MonsterDefinitionEntity(
    @PrimaryKey val code: String,
    val name: String,
    val maxHp: Int,
    val attackPower: Int,
    val attackRange: Int,
    val openingAttackRange: Int,
    val moveDistance: Int,
    val moveEveryTurns: Int,
    val goldDrop: Int,
    val goldDropRate: Double,
    val spritePath: String,
    val sortOrder: Int
)
