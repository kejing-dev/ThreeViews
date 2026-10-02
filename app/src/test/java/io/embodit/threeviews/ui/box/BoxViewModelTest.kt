package io.embodit.threeviews.ui.box

import io.embodit.threeviews.data.repository.FakePartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.model.ViewFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoxViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePartRepository()
    private val store = SceneSessionStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BoxViewModel(repository, store, dispatcher = dispatcher)

    @Test
    fun `the box is empty until the first drawing is ready`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertNull(viewModel.state.value.scene)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals("l_block", state.scene?.partId)
        assertFalse(state.failed)
        assertEquals(ViewKind.entries.toSet(), state.rays)
        assertEquals(ViewKind.entries.toSet(), state.emphasised)
        assertEquals(SceneState.DEFAULT_YAW, state.yawDegrees, 0f)
        assertFalse(state.libraryOpen)
        assertFalse(state.sheetOpen)
    }

    @Test
    fun `the toolbar drives focus, unfold and the line options of the shared scene`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(BoxEvent.Focus(ViewFocus.TOP))
        viewModel.onEvent(BoxEvent.SetRays(false))
        viewModel.onEvent(BoxEvent.SetHidden(false))
        advanceUntilIdle()
        assertEquals(SceneState(focus = ViewFocus.TOP, showRays = false, showHidden = false), store.state.value)
        assertEquals(ViewFocus.TOP, viewModel.state.value.focus)
        assertTrue(viewModel.state.value.rays.isEmpty())
        assertEquals(setOf(ViewKind.TOP), viewModel.state.value.emphasised)
        assertTrue(viewModel.state.value.raysEnabled)
        assertTrue(viewModel.state.value.resetEnabled)
        // unfolding keeps the focus and switches off what only a standing box can do
        viewModel.onEvent(BoxEvent.SetUnfolded(true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.unfolded)
        assertEquals(ViewFocus.TOP, viewModel.state.value.focus)
        assertFalse(viewModel.state.value.raysEnabled)
        assertFalse(viewModel.state.value.resetEnabled)
    }

    @Test
    fun `tapping a plane concentrates on its view and tapping the part shows all three again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(BoxEvent.PlaneTapped(ViewKind.LEFT))
        advanceUntilIdle()
        assertEquals(ViewFocus.LEFT, store.state.value.focus)
        assertEquals(setOf(ViewKind.LEFT), viewModel.state.value.rays)
        assertEquals(setOf(ViewKind.LEFT), viewModel.state.value.emphasised)
        viewModel.onEvent(BoxEvent.PartTapped)
        advanceUntilIdle()
        assertEquals(ViewFocus.ALL, store.state.value.focus)
        assertEquals(ViewKind.entries.toSet(), viewModel.state.value.rays)
    }

    @Test
    fun `dragging turns the box within its range, reset turns it back, and a flat box does neither`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEvent(BoxEvent.Turned(-20f))
        viewModel.onEvent(BoxEvent.Turned(-200f))
        advanceUntilIdle()
        assertEquals(SceneState.MIN_YAW, viewModel.state.value.yawDegrees, 0f)
        viewModel.onEvent(BoxEvent.ResetView)
        advanceUntilIdle()
        assertEquals(SceneState.DEFAULT_YAW, viewModel.state.value.yawDegrees, 0f)
        viewModel.onEvent(BoxEvent.Turned(-30f))
        viewModel.onEvent(BoxEvent.SetUnfolded(true))
        viewModel.onEvent(BoxEvent.Turned(50f))
        viewModel.onEvent(BoxEvent.ResetView)
        advanceUntilIdle()
        assertEquals(SceneState.DEFAULT_YAW - 30f, viewModel.state.value.yawDegrees, 0f)
        assertTrue(viewModel.state.value.rays.isEmpty())
    }

    @Test
    fun `a part that fails to compute leaves the previous part in the box and says so`() = runTest(dispatcher) {
        repository.failingIds = setOf("plate_hole")
        val viewModel = viewModel()
        advanceUntilIdle()
        store.update { it.copy(selectedPartId = "plate_hole") }
        advanceUntilIdle()
        assertEquals("l_block", viewModel.state.value.scene?.partId)
        assertTrue(viewModel.state.value.failed)
        // Retry on the sheet reaches the box through the shared scene.
        repository.failingIds = emptySet()
        store.update { it.copy(attempt = it.attempt + 1, showHidden = false) }
        advanceUntilIdle()
        assertEquals("plate_hole", viewModel.state.value.scene?.partId)
        assertFalse(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.showHidden)
    }

    @Test
    fun `the toolbar shows which 2D windows are open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        store.update { it.copy(libraryOpen = true) }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.libraryOpen)
        assertFalse(viewModel.state.value.sheetOpen)
        store.update { it.copy(libraryOpen = false, sheetOpen = true) }
        advanceUntilIdle()
        assertFalse(viewModel.state.value.libraryOpen)
        assertTrue(viewModel.state.value.sheetOpen)
    }
}
