package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "owned_item",
    foreignKeys = [
        ForeignKey(
            entity = LoginProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("ownerId"),
        Index(value = ["ownerId", "container", "slotIndex"], unique = true)
    ]
)
data class OwnedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ownerId: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val characterId: Long = ownerId,
    val itemCode: String,
    val displayName: String,
    val quantity: Int,
    val container: String,
    val slotIndex: Int,
    val isEquipped: Boolean = false,
    val dungeonUseCount: Int = 0,
    val durability: Int = 3,
    val isIdentified: Boolean = true,
    val appraisedGrade: String? = null,
    val appraisedAttackPower: Int? = null,
    val appraisedEffectChance: Int? = null,
    val isSellable: Boolean = true
)
