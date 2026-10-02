package io.embodit.threeviews.ui.sheet

import io.embodit.threeviews.data.repository.FakePartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.DrawingStatus
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SheetViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePartRepository()
    private val store = SceneSessionStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it starts loading, then shows the drawing of the default part`() = runTest(dispatcher) {
        val viewModel = SheetViewModel(repository, store)
        assertEquals(DrawingStatus.Loading, viewModel.state.value.drawing)
        assertEquals("l_block", viewModel.state.value.part?.id)
        advanceUntilIdle()
        val state = viewModel.state.value
        val ready = state.drawing as DrawingStatus.Ready
        assertEquals("l_block", ready.drawing.part.id)
        assertEquals(1, state.part?.number)
        assertFalse(state.showGuides)
        assertTrue(state.guidesEnabled)
    }

    @Test
    fun `a part picked in the library gets its own drawing`() = runTest(dispatcher) {
        val viewModel = SheetViewModel(repository, store)
        advanceUntilIdle()
        store.update { it.copy(selectedPartId = "plate_hole", showHidden = false) }
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals("plate_hole", (state.drawing as DrawingStatus.Ready).drawing.part.id)
        assertEquals("plate_hole", state.part?.id)
        assertFalse(state.showHidden)
        assertEquals(2, repository.drawingCalls)
    }

    @Test
    fun `a failed drawing is reported, switches the guides off, and retry computes it again`() = runTest(dispatcher) {
        repository.failingIds = setOf("plate_hole")
        val viewModel = SheetViewModel(repository, store)
        advanceUntilIdle()
        store.update { it.copy(selectedPartId = "plate_hole") }
        advanceUntilIdle()
        assertEquals(DrawingStatus.Failed("plate_hole"), viewModel.state.value.drawing)
        assertFalse(viewModel.state.value.guidesEnabled)
        repository.failingIds = emptySet()
        viewModel.onEvent(SheetEvent.Retry)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.drawing is DrawingStatus.Ready)
        assertTrue(viewModel.state.value.guidesEnabled)
        assertEquals(1, store.state.value.attempt)
    }

    @Test
    fun `tapping a view focuses it for every surface, tapping it again shows all three`() = runTest(dispatcher) {
        val viewModel = SheetViewModel(repository, store)
        advanceUntilIdle()
        viewModel.onEvent(SheetEvent.ViewTapped(ViewKind.TOP))
        advanceUntilIdle()
        assertEquals(ViewFocus.TOP, store.state.value.focus)
        assertEquals(ViewFocus.TOP, viewModel.state.value.focus)
        viewModel.onEvent(SheetEvent.ViewTapped(ViewKind.TOP))
        advanceUntilIdle()
        assertEquals(ViewFocus.ALL, viewModel.state.value.focus)
    }

    @Test
    fun `the guides switch and the window state go to the shared scene`() = runTest(dispatcher) {
        val viewModel = SheetViewModel(repository, store)
        advanceUntilIdle()
        viewModel.onEvent(SheetEvent.Opened)
        viewModel.onEvent(SheetEvent.SetGuides(true))
        advanceUntilIdle()
        assertEquals(SceneState(showGuides = true, sheetOpen = true), store.state.value)
        assertTrue(viewModel.state.value.showGuides)
        viewModel.onEvent(SheetEvent.Closed)
        assertFalse(store.state.value.sheetOpen)
        // a focus chosen under the box reaches the sheet
        store.update { it.copy(focus = ViewFocus.LEFT) }
        advanceUntilIdle()
        assertEquals(ViewFocus.LEFT, viewModel.state.value.focus)
    }
}
