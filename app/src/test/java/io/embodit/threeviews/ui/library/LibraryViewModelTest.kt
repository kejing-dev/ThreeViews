package io.embodit.threeviews.ui.library

import io.embodit.threeviews.data.repository.FakePartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.model.Difficulty
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
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePartRepository()
    private val store = SceneSessionStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it lists the parts in both languages with the default part selected`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, store)
        val state = viewModel.state.value
        assertEquals(listOf("l_block", "plate_hole"), state.parts.map { it.id })
        assertEquals("l_block", state.selectedPartId)
        val first = state.parts.first()
        assertEquals(1, first.number)
        assertEquals("L 形块", first.nameZh)
        assertEquals("L-Block", first.nameEn)
        assertEquals(Difficulty.EASY, first.difficulty)
        assertTrue(!first.summaryZh.isNullOrBlank() && !first.summaryEn.isNullOrBlank())
    }

    @Test
    fun `selecting a part goes to the shared scene and keeps the view options`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, store)
        store.update { it.copy(focus = ViewFocus.TOP, unfolded = true, showHidden = false) }
        viewModel.onEvent(LibraryEvent.SelectPart("plate_hole"))
        advanceUntilIdle()
        assertEquals("plate_hole", viewModel.state.value.selectedPartId)
        assertEquals(SceneState(selectedPartId = "plate_hole", focus = ViewFocus.TOP, unfolded = true, showHidden = false), store.state.value)
    }

    @Test
    fun `an unknown part is ignored`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, store)
        viewModel.onEvent(LibraryEvent.SelectPart("teapot"))
        advanceUntilIdle()
        assertEquals("l_block", viewModel.state.value.selectedPartId)
        assertEquals(SceneState(), store.state.value)
    }

    @Test
    fun `opening and closing the window is recorded for the toolbar under the box`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, store)
        viewModel.onEvent(LibraryEvent.Opened)
        assertTrue(store.state.value.libraryOpen)
        assertFalse(store.state.value.sheetOpen)
        viewModel.onEvent(LibraryEvent.Closed)
        assertEquals(SceneState(), store.state.value)
    }

    @Test
    fun `it follows a part picked elsewhere`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(repository, store)
        store.update { it.copy(selectedPartId = "plate_hole") }
        advanceUntilIdle()
        assertEquals("plate_hole", viewModel.state.value.selectedPartId)
    }
}
