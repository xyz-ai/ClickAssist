package com.example.clickassist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "action_steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId"]),
    ],
)
data class ActionStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: Long,
    val orderIndex: Int,
    val actionType: String,
    val x: Int? = null,
    val y: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val intervalMs: Long = 300L,
    val durationMs: Long = 80L,
    val repeatCount: Int = 1,
    val preDelayMs: Long = 0L,
    val postDelayMs: Long = 0L,
    val enabled: Boolean = true,
) {
    companion object {
        const val ACTION_TAP = "TAP"
        const val ACTION_SWIPE = "SWIPE"
        const val ACTION_WAIT = "WAIT"
    }
}
