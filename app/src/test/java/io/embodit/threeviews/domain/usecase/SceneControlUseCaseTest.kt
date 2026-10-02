package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.model.ViewFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneControlUseCaseTest {
    private val control = SceneControlUseCase()
    private val ids = listOf("l_block", "plate_hole")

    @Test
    fun `selecting a part keeps the view options, and an unknown id changes nothing`() {
        val before = SceneState(focus = ViewFocus.TOP, unfolded = true, showRays = false)
        val after = control.selectPart(before, "plate_hole", ids)
        assertEquals(before.copy(selectedPartId = "plate_hole"), after)
        assertEquals(before, control.selectPart(before, "teapot", ids))
    }

    @Test
    fun `tapping a view focuses it and tapping it again shows all three`() {
        val focused = control.toggleFocus(SceneState(), ViewKind.LEFT)
        assertEquals(ViewFocus.LEFT, focused.focus)
        assertEquals(ViewFocus.TOP, control.toggleFocus(focused, ViewKind.TOP).focus)
        assertEquals(ViewFocus.ALL, control.toggleFocus(focused, ViewKind.LEFT).focus)
    }

    @Test
    fun `the box turns within its range and a flat sheet does not turn`() {
        val start = SceneState(yawDegrees = 70f)
        assertEquals(SceneState.MAX_YAW, control.turn(start, 30f).yawDegrees, 0f)
        assertEquals(SceneState.MIN_YAW, control.turn(start, -400f).yawDegrees, 0f)
        assertEquals(60f, control.turn(start, -10f).yawDegrees, 0f)
        val flat = start.copy(unfolded = true)
        assertEquals(flat, control.turn(flat, -10f))
    }

    @Test
    fun `reset turns the folded box back and touches nothing else, and retry asks again`() {
        val turned = SceneState(selectedPartId = "plate_hole", focus = ViewFocus.TOP, yawDegrees = -60f, showHidden = false, libraryOpen = true)
        assertEquals(turned.copy(yawDegrees = SceneState.DEFAULT_YAW), control.reset(turned))
        val flat = turned.copy(unfolded = true)
        assertEquals(flat, control.reset(flat))
        assertEquals(1, control.retry(turned).attempt)
        assertEquals(turned, control.retry(turned).copy(attempt = 0))
    }

    @Test
    fun `the two 2D windows are tracked separately`() {
        val library = control.setLibraryOpen(SceneState(), true)
        assertEquals(SceneState(libraryOpen = true), library)
        assertEquals(SceneState(libraryOpen = true, sheetOpen = true), control.setSheetOpen(library, true))
        assertEquals(SceneState(), control.setLibraryOpen(library, false))
    }

    @Test
    fun `rays show for the focused view only and never on a flat sheet`() {
        val all = SceneState()
        assertTrue(ViewKind.entries.all { control.raysVisible(all, it) })
        val top = all.copy(focus = ViewFocus.TOP)
        assertTrue(control.raysVisible(top, ViewKind.TOP))
        assertFalse(control.raysVisible(top, ViewKind.FRONT))
        assertFalse(control.raysVisible(all.copy(showRays = false), ViewKind.TOP))
        assertFalse(control.raysVisible(all.copy(unfolded = true), ViewKind.TOP))
        assertTrue(control.planeEmphasised(top, ViewKind.TOP))
        assertFalse(control.planeEmphasised(top, ViewKind.LEFT))
        assertTrue(control.planeEmphasised(all, ViewKind.LEFT))
    }
}
