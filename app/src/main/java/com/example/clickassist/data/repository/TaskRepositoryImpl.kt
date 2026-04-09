package com.example.clickassist.data.repository

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
        return taskDao.upsertTaskWithSteps(
            task = task,
            steps = steps.sortedBy { it.orderIndex },
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
}
