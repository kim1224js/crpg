package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item_definition")
data class ItemDefinitionEntity(
    @PrimaryKey val code: String,
    val name: String,
    val category: String,
    val grade: String,
    val storeType: String,
    val basePrice: Int,
    val unitsPerPurchase: Int,
    val assetPath: String,
    val isConsumable: Boolean,
    val maxStack: Int,
    val attackPower: Int,
    val attackTurnCost: Int,
    val attackRange: Int,
    val healthBonus: Int,
    val detail: String?,
    val specialEffect: String?,
    val dropRate: Double,
    val playerSheetPath: String?,
    val sortOrder: Int
)
