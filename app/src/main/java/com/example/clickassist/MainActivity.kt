package com.example.clickassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.clickassist.app.navigation.AppNavHost
import com.example.clickassist.app.appContainer
import com.example.clickassist.common.i18n.LocaleManager
import com.example.clickassist.domain.repository.AppSettings
import com.example.clickassist.ui.theme.ClickAssistTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val initialSettings = runBlocking {
            applicationContext.appContainer.settingsRepository.settingsFlow.first()
        }
        LocaleManager.applyLanguage(initialSettings.languageMode)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by applicationContext.appContainer.settingsRepository.settingsFlow.collectAsState(
                initial = AppSettings(),
            )

            LaunchedEffect(settings.languageMode) {
                LocaleManager.applyLanguage(settings.languageMode)
            }

            ClickAssistTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(appContainer = applicationContext.appContainer)
                }
            }
        }
    }
}