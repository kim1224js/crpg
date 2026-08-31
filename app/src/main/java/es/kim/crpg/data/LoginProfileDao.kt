package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LoginProfileDao {
    @Query("SELECT * FROM login_profile WHERE autoLogin = 1 ORDER BY lastLoginAt DESC LIMIT 1")
    fun getAutoLoginProfile(): LoginProfileEntity?

    @Query("UPDATE login_profile SET autoLogin = 0")
    fun clearAutoLogin()

    @Query("SELECT * FROM login_profile WHERE id = :id LIMIT 1")
    fun getById(id: Long): LoginProfileEntity?

    @Query("UPDATE login_profile SET gold = :gold WHERE id = :id")
    fun updateGold(id: Long, gold: Int)

    @Query("UPDATE login_profile SET highestFloor = MAX(highestFloor, :floor) WHERE id = :id")
    fun updateHighestFloor(id: Long, floor: Int)

    @Query("UPDATE login_profile SET pendingEstateLossCount = 0, pendingEstateKeptNames = NULL WHERE id = :id")
    fun clearEstateNotice(id: Long)

    @Query("UPDATE login_profile SET lastMerchantFreeDay = :day WHERE id = :id")
    fun updateLastMerchantFreeDay(id: Long, day: Int)

    @Query("UPDATE login_profile SET merchantFreeClaimMask = :claimMask WHERE id = :id")
    fun updateMerchantFreeClaimMask(id: Long, claimMask: Int)

    @Query("UPDATE login_profile SET introSeen = 1 WHERE id = :id")
    fun markIntroSeen(id: Long)

    @Upsert
    fun save(profile: LoginProfileEntity)
}
