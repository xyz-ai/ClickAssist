package com.example.clickassist.data.repository

import android.util.Log
import com.example.clickassist.data.local.dao.TaskDao
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import com.example.clickassist.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
) : TaskRepository {
    override fun observeTasks(): Flow<List<TaskWithSteps>> {
        return taskDao.observeTasks().map { tasks ->
            tasks.map { task -> task.sortedSteps() }
        }
    }

    override fun observeTask(taskId: Long): Flow<TaskWithSteps?> {
        return taskDao.observeTask(taskId).map { task ->
            task?.sortedSteps()
        }
    }

    override suspend fun getTask(taskId: Long): TaskWithSteps? {
        return taskDao.getTask(taskId)?.sortedSteps()
    }

    override suspend fun saveTask(
        task: TaskEntity,
        steps: List<ActionStepEntity>,
    ): Long {
        val sortedSteps = steps.sortedBy { it.orderIndex }
        Log.i(
            TAG,
            "saveTask start mode=${if (task.id == 0L) "create" else "update"} taskId=${task.id} stepCount=${sortedSteps.size}",
        )
        return try {
            taskDao.upsertTaskWithSteps(
                task = task,
                steps = sortedSteps,
            ).also { savedTaskId ->
                Log.i(TAG, "saveTask success savedTaskId=$savedTaskId stepCount=${sortedSteps.size}")
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "saveTask failed taskId=${task.id} stepCount=${sortedSteps.size}",
                throwable,
            )
            throw IllegalStateException(
                throwable.message?.takeIf { it.isNotBlank() } ?: "Failed to save task: unknown error",
                throwable,
            )
        }
    }

    override suspend fun updateTapStepPosition(
        taskId: Long,
        stepId: Long,
        x: Int,
        y: Int,
    ) {
        taskDao.updateTapStepPosition(
            taskId = taskId,
            stepId = stepId,
            x = x,
            y = y,
        )
    }

    override suspend fun updateSwipeStepPosition(
        taskId: Long,
        stepId: Long,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    ) {
        taskDao.updateSwipeStepPosition(
            taskId = taskId,
            stepId = stepId,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
    }

    override suspend fun deleteTask(taskId: Long) {
        taskDao.deleteTaskById(taskId)
    }

    private fun TaskWithSteps.sortedSteps(): TaskWithSteps {
        return copy(
            steps = steps.sortedWith(
                compareBy<ActionStepEntity> { it.orderIndex }
                    .thenBy { it.id },
            ),
        )
    }

    private companion object {
        const val TAG = "TaskRepository"
    }
}
