package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_config")
data class GameConfigEntity(
    @PrimaryKey val key: String,
    val intValue: Int?,
    val doubleValue: Double?,
    val textValue: String?
)
