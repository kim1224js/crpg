package es.kim.crpg.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nickname_account",
    indices = [Index(value = ["nickname"], unique = true)]
)
data class NicknameAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val nickname: String,
    val createdAt: Long
)
