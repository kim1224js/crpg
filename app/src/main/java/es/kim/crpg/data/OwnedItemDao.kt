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

    @Query("SELECT * FROM owned_item WHERE ownerId = :ownerId AND container = :container AND itemCode = :itemCode LIMIT 1")
    fun findItem(ownerId: Long, container: String, itemCode: String): OwnedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: OwnedItemEntity)

    @Query("UPDATE owned_item SET quantity = :quantity WHERE id = :id")
    fun updateQuantity(id: Long, quantity: Int)

    @Query("UPDATE owned_item SET container = :container, slotIndex = :slotIndex WHERE id = :id")
    fun updateLocation(id: Long, container: String, slotIndex: Int)

    @Query("DELETE FROM owned_item WHERE id = :id")
    fun deleteById(id: Long)
}
