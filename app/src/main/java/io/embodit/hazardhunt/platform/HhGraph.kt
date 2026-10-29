package io.embodit.hazardhunt.platform

import android.content.Context
import io.embodit.hazardhunt.data.SettingsStore
import io.embodit.hazardhunt.platform.input.ControllerInput
import io.embodit.hazardhunt.platform.sense.PlaneTracker
import io.embodit.hazardhunt.platform.tracking.HeadTracker

/**
 * Process-wide wiring of the platform services. The scene starts and stops the sensing services
 * (every start has a stop: see `HhScene.attach` / `detach` / `onBackground`); the ViewModel only sees state.
 */
object HhGraph {
    val planes = PlaneTracker()
    val head = HeadTracker()
    val controller = ControllerInput()

    @Volatile var appContext: Context? = null

    @Volatile private var store: SettingsStore? = null

    fun settings(context: Context): SettingsStore =
        store ?: synchronized(this) { store ?: SettingsStore(context).also { store = it } }
}
