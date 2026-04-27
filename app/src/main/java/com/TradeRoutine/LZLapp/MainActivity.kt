package com.TradeRoutine.LZLapp

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import com.TradeRoutine.LZLapp.app.navigation.AppNavHost
import com.TradeRoutine.LZLapp.app.appContainer
import com.TradeRoutine.LZLapp.common.i18n.LocaleManager
import com.TradeRoutine.LZLapp.ui.theme.ClickAssistTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val initialSettings = runBlocking {
            applicationContext.appContainer.settingsRepository.settingsFlow.first()
        }
        Log.i(
            TAG,
            "onCreate languageMode=${initialSettings.languageMode} savedInstanceState=${savedInstanceState != null}",
        )
        val languageChanged = LocaleManager.applyLanguage(initialSettings.languageMode)
        Log.i(TAG, "apply initial language changed=$languageChanged")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applicationContext.appContainer.taskRunnerEngine.refreshLocalizedResources()
        Log.i(TAG, "refreshLocalizedResources once in onCreate")

        setContent {
            val themeMode by applicationContext.appContainer.settingsRepository.settingsFlow
                .map { it.themeMode }
                .collectAsState(
                    initial = initialSettings.themeMode,
                )

            ClickAssistTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        appContainer = applicationContext.appContainer,
                        showOnboardingInitially = !initialSettings.hasSeenOnboarding,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
