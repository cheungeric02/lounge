package ca.cheungeric.lounge.home

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ca.cheungeric.lounge.LoungeApp
import ca.cheungeric.lounge.ui.ambient.AmbientScreen
import ca.cheungeric.lounge.ui.theme.LoungeTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * System screensaver "Lounge Ambient": the same pure-black [AmbientScreen]. Non-interactive, so
 * any key wakes the box and the system finishes the dream (the key is not delivered to apps).
 *
 * DreamService is not a LifecycleOwner, so it drives its own lifecycle for Compose:
 * CREATED in onCreate, STARTED/RESUMED while dreaming, DESTROYED in onDestroy.
 */
class AmbientDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Keep weather fresh at its usual 30-minute cadence while the dream is visible.
        val repo = (application as LoungeApp).container.weatherRepository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.startAutoRefresh(this)
                awaitCancellation()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val repo = (application as LoungeApp).container.weatherRepository
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AmbientDreamService)
            setViewTreeSavedStateRegistryOwner(this@AmbientDreamService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LoungeTheme {
                    val weather by repo.weather.collectAsStateWithLifecycle()
                    AmbientScreen(weather)
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
