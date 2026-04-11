package com.example.clickassist.domain.repository

import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<TaskWithSteps>>

    fun observeTask(taskId: Long): Flow<TaskWithSteps?>

    suspend fun getTask(taskId: Long): TaskWithSteps?

    suspend fun saveTask(
        task: TaskEntity,
        steps: List<ActionStepEntity>,
    ): Long

    suspend fun updateTapStepPosition(
        taskId: Long,
        stepId: Long,
        x: Int,
        y: Int,
    )

    suspend fun updateSwipeStepPosition(
        taskId: Long,
        stepId: Long,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    )

    suspend fun deleteTask(taskId: Long)
}
