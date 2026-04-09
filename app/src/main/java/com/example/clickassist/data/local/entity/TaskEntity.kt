package com.example.clickassist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val enabled: Boolean = true,
    val totalRounds: Int = 1,
    val infiniteRounds: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
