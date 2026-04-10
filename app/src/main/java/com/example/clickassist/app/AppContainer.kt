package com.example.clickassist.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.clickassist.data.local.db.AppDatabase
import com.example.clickassist.data.repository.TaskRepositoryImpl
import com.example.clickassist.data.settings.DataStoreSettingsRepository
import com.example.clickassist.domain.repository.SettingsRepository
import com.example.clickassist.domain.repository.TaskRepository
import com.example.clickassist.service.overlay.OverlayController
import com.example.clickassist.service.runner.TaskStartValidator
import com.example.clickassist.service.runner.TaskRunnerEngine

class ClickAssistApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}

interface AppContainer {
    val appContext: Context
    val taskRepository: TaskRepository
    val settingsRepository: SettingsRepository
    val overlayController: OverlayController
    val taskRunnerEngine: TaskRunnerEngine
}

class DefaultAppContainer(
    application: Application,
) : AppContainer {
    override val appContext: Context = application.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    override val taskRepository: TaskRepository by lazy {
        TaskRepositoryImpl(taskDao = database.taskDao())
    }

    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(context = appContext)
    }

    override val overlayController: OverlayController by lazy {
        OverlayController(context = appContext)
    }

    private val taskStartValidator: TaskStartValidator by lazy {
        TaskStartValidator(context = appContext)
    }

    override val taskRunnerEngine: TaskRunnerEngine by lazy {
        TaskRunnerEngine(
            appContext = appContext,
            taskRepository = taskRepository,
            overlayController = overlayController,
            taskStartValidator = taskStartValidator,
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClickAssistApplication).container
