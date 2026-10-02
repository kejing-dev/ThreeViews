package io.embodit.threeviews

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.threeviews.ui.box.BoxScreen
import io.embodit.threeviews.ui.library.LibraryScreen
import io.embodit.threeviews.ui.navigation.Containers
import io.embodit.threeviews.ui.sheet.SheetScreen
import io.embodit.threeviews.ui.theme.ThreeViewsTheme

/**
 * Three surfaces in Shared Space. The projection box is the main window (Volumetric, 0.8 × 0.72 × 0.9 m
 * from the manifest) and opens in front of the user. The part library (Planar, 360×720 dp) opens
 * to its left, the drawing sheet (Planar, 960×720 dp) to its right.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            ThreeViewsTheme { BoxScreen() }
        }
        WindowContainer(
            Containers.LIBRARY,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 360.dp, height = 720.dp),
            placement = { containers.firstOrNull { it.id == Containers.BOX }?.let { Placement.left(it) } ?: Placement.none() },
        ) {
            ThreeViewsTheme {
                LibraryScreen(Modifier.windowConstraints(minWidth = 320.dp, minHeight = 480.dp, maxWidth = 480.dp, maxHeight = 960.dp))
            }
        }
        WindowContainer(
            Containers.SHEET,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 960.dp, height = 720.dp),
            placement = { containers.firstOrNull { it.id == Containers.BOX }?.let { Placement.right(it) } ?: Placement.none() },
        ) {
            ThreeViewsTheme {
                SheetScreen(Modifier.windowConstraints(minWidth = 720.dp, minHeight = 540.dp, maxWidth = 1600.dp, maxHeight = 1200.dp))
            }
        }
    }
