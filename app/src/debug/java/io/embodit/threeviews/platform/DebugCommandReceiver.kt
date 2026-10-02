package io.embodit.threeviews.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.embodit.threeviews.domain.model.ViewFocus
import io.embodit.threeviews.domain.usecase.SceneControlUseCase
import io.embodit.threeviews.ui.box.scene.BoxSceneHolder

/**
 * Debug builds only. Drives the shared scene from adb so every state can be captured without a
 * controller:
 *
 *     adb shell am broadcast -a io.embodit.threeviews.DEBUG -p io.embodit.threeviews --es cmd select --es a plate_hole
 *
 * Commands: select <partId>, focus <ALL|FRONT|TOP|LEFT>, unfold, fold, rays <on|off>, hidden <on|off>,
 * guides <on|off>, reset, retry, yaw <degrees>, evidence. The two 2D windows are opened and closed
 * from the toolbar, which needs a tap.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = ThreeViewsGraph.from(context)
        val store = graph.sessionStore
        val control = SceneControlUseCase()
        val arg = intent.getStringExtra("a").orEmpty()
        val on = arg != "off"
        when (val cmd = intent.getStringExtra("cmd")) {
            "select" -> store.update { control.selectPart(it, arg, graph.repository.parts().map { part -> part.id }) }
            "focus" -> store.update { control.focus(it, runCatching { ViewFocus.valueOf(arg.uppercase()) }.getOrDefault(ViewFocus.ALL)) }
            "unfold" -> store.update { control.setUnfolded(it, true) }
            "fold" -> store.update { control.setUnfolded(it, false) }
            "rays" -> store.update { control.setRays(it, on) }
            "hidden" -> store.update { control.setHidden(it, on) }
            "guides" -> store.update { control.setGuides(it, on) }
            "reset" -> store.update(control::reset)
            "retry" -> store.update(control::retry)
            "yaw" -> arg.toFloatOrNull()?.let { yaw -> store.update { it.copy(yawDegrees = yaw) } }
            "evidence" -> Log.i(TAG, "EVIDENCE ${BoxSceneHolder.controller?.evidence() ?: "box closed"}")
            else -> Log.w(TAG, "unknown debug command: $cmd")
        }
        Log.i(TAG, "debug cmd=${intent.getStringExtra("cmd")} a=$arg state=${store.state.value}")
    }

    private companion object {
        const val TAG = "ThreeViews"
    }
}
