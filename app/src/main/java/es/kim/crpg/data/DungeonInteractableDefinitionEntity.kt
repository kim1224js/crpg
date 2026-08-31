package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dungeon_interactable_definition")
data class DungeonInteractableDefinitionEntity(
    @PrimaryKey val code: String,
    val name: String,
    val healPercent: Int,
    val assetPath: String,
    val footprintWidth: Int,
    val footprintHeight: Int
)
