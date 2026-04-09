package com.example.clickassist.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithSteps(
    @Embedded
    val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
    )
    val steps: List<ActionStepEntity>,
)
