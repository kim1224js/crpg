package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dungeon_run")
data class DungeonRunEntity(
    @PrimaryKey val playerId: Long,
    val payloadJson: String,
    val savedAt: Long
)
