package io.embodit.threeviews.platform

import android.content.Context
import io.embodit.threeviews.data.repository.BuiltInPartRepository
import io.embodit.threeviews.data.repository.PartRepository
import io.embodit.threeviews.data.session.SceneSessionStore

/**
 * Tiny hand-rolled object graph. The projection box, the part library and the drawing sheet are
 * separate window containers with separate ViewModels; what they share lives here: the part library
 * data and the scene state. Created by [SpatialApplication]; the screens' default ViewModel
 * factories read it from the application context.
 */
class ThreeViewsGraph {
    private val library = BuiltInPartRepository()
    val repository: PartRepository = library
    val sessionStore: SceneSessionStore = SceneSessionStore()

    init {
        // The whole library is ready by the time the user reaches for another part.
        library.warmUp()
    }

    companion object {
        fun from(context: Context): ThreeViewsGraph =
            (context.applicationContext as SpatialApplication).graph
    }
}
