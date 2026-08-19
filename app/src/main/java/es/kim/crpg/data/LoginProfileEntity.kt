package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "login_profile")
data class LoginProfileEntity(
    @PrimaryKey val id: Long = 1L,
    val playerName: String,
    val autoLogin: Boolean,
    val lastLoginAt: Long
)
