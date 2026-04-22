package com.TradeRoutine.LZLapp.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.TradeRoutine.LZLapp.data.local.db.AppDatabase
import com.TradeRoutine.LZLapp.data.repository.TaskRepositoryImpl
import com.TradeRoutine.LZLapp.data.settings.DataStoreSettingsRepository
import com.TradeRoutine.LZLapp.domain.repository.SettingsRepository
import com.TradeRoutine.LZLapp.domain.repository.TaskRepository
import com.TradeRoutine.LZLapp.service.overlay.OverlayController
import com.TradeRoutine.LZLapp.service.overlay.OverlayHandleController
import com.TradeRoutine.LZLapp.service.overlay.OverlayPanelController
import com.TradeRoutine.LZLapp.service.overlay.OverlayTargetController
import com.TradeRoutine.LZLapp.service.overlay.OverlayToolbarController
import com.TradeRoutine.LZLapp.service.overlay.OverlayTutorialController
import com.TradeRoutine.LZLapp.service.runner.TaskStartValidator
import com.TradeRoutine.LZLapp.service.runner.TaskRunnerEngine
import com.TradeRoutine.LZLapp.ui.tutorial.TutorialController

class ClickAssistApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}

interface AppContainer {
    val appContext: Context
    val taskRepository: TaskRepository
    val settingsRepository: SettingsRepository
    val tutorialController: TutorialController
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

    override val tutorialController: TutorialController by lazy {
        TutorialController()
    }

    private val overlayToolbarController: OverlayToolbarController by lazy {
        OverlayToolbarController(
            context = appContext,
            settingsRepository = settingsRepository,
            tutorialController = tutorialController,
        )
    }

    private val overlayTargetController: OverlayTargetController by lazy {
        OverlayTargetController(
            context = appContext,
            tutorialController = tutorialController,
        )
    }

    private val overlayPanelController: OverlayPanelController by lazy {
        OverlayPanelController(context = appContext)
    }

    private val overlayHandleController: OverlayHandleController by lazy {
        OverlayHandleController(
            context = appContext,
            settingsRepository = settingsRepository,
            tutorialController = tutorialController,
        )
    }

    private val overlayTutorialController: OverlayTutorialController by lazy {
        OverlayTutorialController(context = appContext)
    }

    override val overlayController: OverlayController by lazy {
        OverlayController(
            toolbarController = overlayToolbarController,
            targetController = overlayTargetController,
            panelController = overlayPanelController,
            handleController = overlayHandleController,
            tutorialController = overlayTutorialController,
            tutorialAnchorController = tutorialController,
        )
    }

    private val taskStartValidator: TaskStartValidator by lazy {
        TaskStartValidator(context = appContext)
    }

    override val taskRunnerEngine: TaskRunnerEngine by lazy {
        TaskRunnerEngine(
            appContext = appContext,
            taskRepository = taskRepository,
            settingsRepository = settingsRepository,
            overlayController = overlayController,
            taskStartValidator = taskStartValidator,
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClickAssistApplication).container
