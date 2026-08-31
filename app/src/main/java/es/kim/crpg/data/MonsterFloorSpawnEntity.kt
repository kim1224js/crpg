package es.kim.crpg.data

import androidx.room.Entity

@Entity(
    tableName = "monster_floor_spawn",
    primaryKeys = ["floor", "spawnOrder"]
)
data class MonsterFloorSpawnEntity(
    val floor: Int,
    val spawnOrder: Int,
    val monsterCode: String,
    val column: Int,
    val row: Int
)
