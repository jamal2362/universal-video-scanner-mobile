package com.jamal2367.uvsmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.ui.UvsApp
import com.jamal2367.uvsmobile.ui.theme.UvsTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as UvsApplication).container
        val settingsFlow = container.settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppSettings())

        setContent {
            val settings by settingsFlow.collectAsState()
            UvsTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                UvsApp(container = container, settings = settings)
            }
        }
    }
}
