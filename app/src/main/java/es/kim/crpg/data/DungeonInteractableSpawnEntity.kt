package es.kim.crpg.data

import androidx.room.Entity

@Entity(
    tableName = "dungeon_interactable_spawn",
    primaryKeys = ["floor", "spawnOrder"]
)
data class DungeonInteractableSpawnEntity(
    val floor: Int,
    val spawnOrder: Int,
    val interactableCode: String,
    val column: Int,
    val row: Int
)
