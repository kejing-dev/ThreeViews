package io.embodit.threeviews.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.threeviews.mainApp

class SpatialApplication : Application() {
    lateinit var graph: ThreeViewsGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = ThreeViewsGraph()
        launch(::mainApp)
    }
}
