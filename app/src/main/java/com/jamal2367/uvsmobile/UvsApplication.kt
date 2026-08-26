package com.jamal2367.uvsmobile

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.jamal2367.uvsmobile.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.Dispatchers

class UvsApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob()) + Dispatchers.Default

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // The router is what every request, every poster and the event stream
        // read the current configuration from, so it is kept in step with the
        // stored settings here rather than in each screen that changes them.
        container.settingsRepository.settings
            .distinctUntilChanged()
            .onEach { settings ->
                val before = container.router.settings
                container.router.update(settings)
                // A different instance answers different content under the same
                // question, so nothing remembered from the old one may be reused.
                if (before.primary != settings.primary || before.secondary != settings.secondary) {
                    container.repository.invalidateCache()
                }
            }
            .launchIn(applicationScope)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
