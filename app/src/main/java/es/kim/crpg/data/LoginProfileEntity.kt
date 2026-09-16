package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "login_profile")
data class LoginProfileEntity(
    @PrimaryKey val id: Long,
    val playerName: String,
    val autoLogin: Boolean,
    val lastLoginAt: Long,
    val gold: Int = 20,
    val introSeen: Boolean = false,
    val survivalDay: Int = 1,
    val lastManorSearchDay: Int = 0,
    val highestFloor: Int = 1,
    val unlockedDungeonStartFloor: Int = 1,
    val pendingEstateLossCount: Int = 0,
    val pendingEstateKeptNames: String? = null,
    val lastMerchantFreeDay: Int = 0,
    val merchantFreeClaimMask: Int = 0,
    @ColumnInfo(defaultValue = "20") val storageCapacity: Int = 20,
    @ColumnInfo(defaultValue = "0") val activeCharacterId: Long = id
)
