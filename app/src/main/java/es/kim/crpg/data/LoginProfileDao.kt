package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LoginProfileDao {
    @Query("SELECT * FROM login_profile WHERE id = 1 AND autoLogin = 1 LIMIT 1")
    fun getAutoLoginProfile(): LoginProfileEntity?

    @Query("SELECT * FROM login_profile WHERE id = :id LIMIT 1")
    fun getById(id: Long): LoginProfileEntity?

    @Query("UPDATE login_profile SET gold = :gold WHERE id = :id")
    fun updateGold(id: Long, gold: Int)

    @Upsert
    fun save(profile: LoginProfileEntity)
}
