package io.embodit.hazardhunt.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.hazardhunt.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HhGraph.appContext = applicationContext
        launch(::mainApp)
    }
}
