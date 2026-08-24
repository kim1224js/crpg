package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deceased_character")
data class DeceasedCharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerName: String,
    val generation: Int,
    val reachedFloor: Int,
    val survivedTurns: Int,
    val diedAt: Long
)
