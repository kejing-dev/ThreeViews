package io.embodit.threeviews.ui.box.scene

import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System

/**
 * Drives the unfold and the turn of the projection box once per rendered frame, inside the ECS
 * rather than through Compose recomposition. The SDK creates the system itself, so it finds the
 * scene through [BoxSceneHolder].
 */
class BoxAnimationSystem : System() {
    override fun update(context: SceneUpdateContext) {
        BoxSceneHolder.controller?.onFrame(context.deltaTime)
    }
}

/** The projection box currently on screen. There is at most one. */
internal object BoxSceneHolder {
    @Volatile
    var controller: BoxSceneController? = null
}
