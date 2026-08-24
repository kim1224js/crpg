package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DeceasedCharacterDao {
    @Insert
    fun insert(character: DeceasedCharacterEntity)

    @Query("SELECT * FROM deceased_character ORDER BY generation DESC, diedAt DESC")
    fun getAll(): List<DeceasedCharacterEntity>

    @Query("SELECT COUNT(*) FROM deceased_character")
    fun count(): Int
}
