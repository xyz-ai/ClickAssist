package com.example.clickassist

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
import com.example.clickassist.app.navigation.AppNavHost
import com.example.clickassist.app.appContainer
import com.example.clickassist.common.i18n.LocaleManager
import com.example.clickassist.ui.theme.ClickAssistTheme
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
                    AppNavHost(appContainer = applicationContext.appContainer)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
