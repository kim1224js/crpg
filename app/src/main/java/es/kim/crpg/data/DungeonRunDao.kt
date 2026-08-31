package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DungeonRunDao {
    @Query("SELECT * FROM dungeon_run WHERE playerId = :playerId LIMIT 1")
    fun get(playerId: Long): DungeonRunEntity?

    @Upsert
    fun save(run: DungeonRunEntity)

    @Query("DELETE FROM dungeon_run WHERE playerId = :playerId")
    fun delete(playerId: Long)
}
