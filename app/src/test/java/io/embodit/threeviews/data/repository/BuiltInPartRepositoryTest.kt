package io.embodit.threeviews.data.repository

import io.embodit.threeviews.domain.geometry.PartLibrary
import io.embodit.threeviews.domain.geometry.computeDrawing
import io.embodit.threeviews.domain.model.Difficulty
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuiltInPartRepositoryTest {

    @Test
    fun `the library lists eight numbered parts with a summary and a difficulty each`() {
        val parts = BuiltInPartRepository().parts()
        assertEquals(8, parts.size)
        assertEquals((1..8).toList(), parts.map { it.number })
        assertEquals("l_block", parts.first().id)
        assertTrue(parts.all { !it.summaryZh.isNullOrBlank() && !it.summaryEn.isNullOrBlank() && it.difficulty != null })
        assertEquals(Difficulty.EASY, parts.first().difficulty)
        assertEquals(Difficulty.HARD, parts.last().difficulty)
    }

    @Test
    fun `two callers asking for the same part share one computation, and the result is kept`() = runTest {
        val calls = AtomicInteger()
        val repository = BuiltInPartRepository(StandardTestDispatcher(testScheduler)) { id -> calls.incrementAndGet(); computeDrawing(id) }
        val (a, b) = listOf(async { repository.drawing("l_block") }, async { repository.drawing("l_block") }).awaitAll()
        assertSame(a, b)
        assertSame(a, repository.drawing("l_block"))
        assertEquals(1, calls.get())
    }

    @Test
    fun `a failed computation is not kept, so asking again computes again`() = runTest {
        val calls = AtomicInteger()
        val repository = BuiltInPartRepository(StandardTestDispatcher(testScheduler)) { id ->
            if (calls.incrementAndGet() == 1) error("boom") else computeDrawing(id)
        }
        try {
            repository.drawing("l_block")
            fail("the first computation should fail")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals("l_block", repository.drawing("l_block").part.id)
        assertEquals(2, calls.get())
    }

    @Test
    fun `warming up computes every part once, and survives a part that fails`() = runTest {
        val computed = ArrayList<String>()
        val repository = BuiltInPartRepository(StandardTestDispatcher(testScheduler)) { id ->
            computed.add(id)
            if (id == "wedge_cut") error("boom") else computeDrawing("l_block")
        }
        repository.drawing("plate_hole")
        repository.warmUp().join()
        advanceUntilIdle()
        assertEquals(PartLibrary.parts.map { it.id }.toSet(), computed.toSet())
        assertEquals(PartLibrary.parts.size, computed.size)
    }
}
