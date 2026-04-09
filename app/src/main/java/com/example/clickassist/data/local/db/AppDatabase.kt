package com.example.clickassist.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.clickassist.data.local.dao.TaskDao
import com.example.clickassist.data.local.entity.ActionStepEntity
import com.example.clickassist.data.local.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        ActionStepEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val DATABASE_NAME = "clickassist.db"
    }
}
