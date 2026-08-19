package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OwnedItemDao {
    @Query("SELECT * FROM owned_item WHERE ownerId = :ownerId ORDER BY container, slotIndex")
    fun getForOwner(ownerId: Long): List<OwnedItemEntity>

    @Query("SELECT COUNT(*) FROM owned_item WHERE ownerId = :ownerId")
    fun countForOwner(ownerId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: OwnedItemEntity)
}
