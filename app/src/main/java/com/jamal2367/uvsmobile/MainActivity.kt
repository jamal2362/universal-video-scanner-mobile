package com.jamal2367.uvsmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.ui.UvsApp
import com.jamal2367.uvsmobile.ui.theme.UvsTheme
import com.jamal2367.uvsmobile.util.LocalNetworkAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    /**
     * Whether the question of local network access has been answered yet.
     *
     * The screens are held back until it has: they ask the server for
     * something the moment they exist, and a request sent while the dialog is
     * still open is one that was refused before anyone decided anything.
     */
    private val networkAccessDecided = MutableStateFlow(false)

    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Either answer lets the app carry on: granted it works, denied it
            // says so on screen rather than blaming the server.
            networkAccessDecided.value = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (LocalNetworkAccess.isGranted(this)) {
            networkAccessDecided.value = true
        } else {
            requestLocalNetwork.launch(LocalNetworkAccess.PERMISSION)
        }

        val container = (application as UvsApplication).container
        val settingsFlow = container.settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppSettings())

        setContent {
            val settings by settingsFlow.collectAsState()
            val decided by networkAccessDecided.collectAsState()
            UvsTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                // Nothing but the theme's background until then - the dialog is
                // covering this anyway, and it is gone within a moment.
                if (decided) {
                    UvsApp(container = container, settings = settings)
                }
            }
        }
    }
}
