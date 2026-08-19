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
    val itemCode: String,
    val displayName: String,
    val quantity: Int,
    val container: String,
    val slotIndex: Int
)
