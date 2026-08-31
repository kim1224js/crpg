package es.kim.crpg.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NicknameAccountDao {
    @Query("SELECT * FROM nickname_account WHERE nickname = :nickname COLLATE NOCASE LIMIT 1")
    fun getByNickname(nickname: String): NicknameAccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(account: NicknameAccountEntity): Long

    @Query("UPDATE nickname_account SET nickname = :nickname WHERE id = :id")
    fun rename(id: Long, nickname: String)
}
