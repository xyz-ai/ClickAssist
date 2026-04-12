package com.example.clickassist.data.local.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity
import com.example.clickassist.data.local.entity.TaskWithSteps
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    abstract fun observeTasks(): Flow<List<TaskWithSteps>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    abstract fun observeTask(taskId: Long): Flow<TaskWithSteps?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    abstract suspend fun getTask(taskId: Long): TaskWithSteps?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTask(task: TaskEntity): Long

    @Update
    protected abstract suspend fun updateTask(task: TaskEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertActionSteps(steps: List<ActionStepEntity>): List<Long>

    @Query("UPDATE action_steps SET x = :x, y = :y WHERE id = :stepId AND taskId = :taskId")
    protected abstract suspend fun updateTapStepPositionInternal(
        taskId: Long,
        stepId: Long,
        x: Int,
        y: Int,
    )

    @Query(
        """
        UPDATE action_steps
        SET x = :startX,
            y = :startY,
            endX = :endX,
            endY = :endY
        WHERE id = :stepId AND taskId = :taskId
        """,
    )
    protected abstract suspend fun updateSwipeStepPositionInternal(
        taskId: Long,
        stepId: Long,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    )

    @Query("UPDATE tasks SET updatedAt = :updatedAt WHERE id = :taskId")
    protected abstract suspend fun updateTaskUpdatedAt(
        taskId: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM action_steps WHERE taskId = :taskId")
    protected abstract suspend fun deleteStepsForTask(taskId: Long): Int

    @Query("DELETE FROM tasks WHERE id = :taskId")
    abstract suspend fun deleteTaskById(taskId: Long)

    @Transaction
    open suspend fun upsertTaskWithSteps(
        task: TaskEntity,
        steps: List<ActionStepEntity>,
    ): Long {
        Log.i(
            TAG,
            "upsertTaskWithSteps start mode=${if (task.id == 0L) "create" else "update"} taskId=${task.id} stepCount=${steps.size}",
        )

        val taskId = if (task.id == 0L) {
            insertTask(task).also { insertedTaskId ->
                check(insertedTaskId > 0L) {
                    "Failed to insert task: invalid task id returned"
                }
                Log.i(TAG, "insertTask success taskId=$insertedTaskId")
            }
        } else {
            val updatedRows = updateTask(task)
            Log.i(TAG, "updateTask result taskId=${task.id} updatedRows=$updatedRows")
            check(updatedRows == 1) {
                "Failed to update task: task #${task.id} was not found"
            }
            task.id
        }

        val deletedRows = deleteStepsForTask(taskId)
        Log.i(TAG, "deleteStepsForTask taskId=$taskId deletedRows=$deletedRows")

        if (steps.isNotEmpty()) {
            val insertIds = insertActionSteps(
                steps.map { step ->
                    step.copy(
                        id = 0L,
                        taskId = taskId,
                    )
                },
            )
            Log.i(
                TAG,
                "insertActionSteps taskId=$taskId requested=${steps.size} inserted=${insertIds.size}",
            )
            check(insertIds.size == steps.size && insertIds.all { it > 0L }) {
                "Failed to save steps: inserted step count does not match expected count"
            }
        } else {
            Log.w(TAG, "upsertTaskWithSteps taskId=$taskId with empty steps")
        }

        Log.i(TAG, "upsertTaskWithSteps success taskId=$taskId stepCount=${steps.size}")
        return taskId
    }

    @Transaction
    open suspend fun updateTapStepPosition(
        taskId: Long,
        stepId: Long,
        x: Int,
        y: Int,
    ) {
        updateTapStepPositionInternal(
            taskId = taskId,
            stepId = stepId,
            x = x,
            y = y,
        )
        updateTaskUpdatedAt(
            taskId = taskId,
            updatedAt = System.currentTimeMillis(),
        )
    }

    @Transaction
    open suspend fun updateSwipeStepPosition(
        taskId: Long,
        stepId: Long,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
    ) {
        updateSwipeStepPositionInternal(
            taskId = taskId,
            stepId = stepId,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
        updateTaskUpdatedAt(
            taskId = taskId,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val TAG = "TaskDao"
    }
}
