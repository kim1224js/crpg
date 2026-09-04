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

    @Query("SELECT * FROM owned_item WHERE ownerId = :ownerId AND container = :container AND slotIndex = :slotIndex LIMIT 1")
    fun findAtSlot(ownerId: Long, container: String, slotIndex: Int): OwnedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: OwnedItemEntity)

    @Query("UPDATE owned_item SET quantity = :quantity WHERE id = :id")
    fun updateQuantity(id: Long, quantity: Int)

    @Query("UPDATE owned_item SET container = :container, slotIndex = :slotIndex WHERE id = :id")
    fun updateLocation(id: Long, container: String, slotIndex: Int)

    @Query("UPDATE owned_item SET characterId = :characterId, container = 'STORAGE', slotIndex = :slotIndex, isEquipped = 0 WHERE id = :id")
    fun claimEstateItem(id: Long, characterId: Long, slotIndex: Int)

    @Query("UPDATE owned_item SET container = 'ESTATE', slotIndex = :slotIndex, isEquipped = 0 WHERE id = :id")
    fun preserveAsEstate(id: Long, slotIndex: Int)

    @Query("UPDATE owned_item SET isEquipped = 0 WHERE ownerId = :ownerId")
    fun clearEquipped(ownerId: Long)

    @Query("UPDATE owned_item SET isEquipped = 0 WHERE ownerId = :ownerId AND itemCode IN (SELECT code FROM item_definition WHERE category IN (:categories))")
    fun clearEquippedCategories(ownerId: Long, categories: List<String>)

    @Query("UPDATE owned_item SET isEquipped = 1 WHERE id = :id")
    fun setEquipped(id: Long)

    @Query("UPDATE owned_item SET isIdentified = 1, appraisedGrade = :grade, displayName = :displayName, appraisedAttackPower = :attackPower, appraisedEffectChance = NULL, durability = :durability WHERE id = :id")
    fun markIdentified(id: Long, grade: String, displayName: String, attackPower: Int?, durability: Int)

    @Query("UPDATE owned_item SET dungeonUseCount = :useCount WHERE id = :id")
    fun updateDungeonUseCount(id: Long, useCount: Int)

    @Query("DELETE FROM owned_item WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM owned_item WHERE ownerId = :ownerId AND container = :container")
    fun deleteContainer(ownerId: Long, container: String)
}
