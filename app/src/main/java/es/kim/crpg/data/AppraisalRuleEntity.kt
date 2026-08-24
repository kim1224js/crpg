package es.kim.crpg.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appraisal_rule")
data class AppraisalRuleEntity(
    @PrimaryKey val grade: String,
    val cost: Int,
    val successRate: Double,
    val colorValue: Long,
    val sortOrder: Int
)
