package io.embodit.threeviews.data.repository

import io.embodit.threeviews.domain.geometry.PartDefinition
import io.embodit.threeviews.domain.geometry.PartDrawing
import io.embodit.threeviews.domain.geometry.PartLibrary
import io.embodit.threeviews.domain.geometry.computeDrawing
import io.embodit.threeviews.domain.model.Difficulty
import io.embodit.threeviews.domain.model.PartSummary
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** The part library and the drawings computed from it. */
interface PartRepository {
    /** The library in display order. */
    fun parts(): List<PartSummary>

    /** Mesh and three views of one part. Throws when the part is unknown or its geometry cannot be computed. */
    suspend fun drawing(partId: String): PartDrawing
}

/** What the library says about a part besides its geometry. */
private class PartNote(val summaryZh: String, val summaryEn: String, val difficulty: Difficulty)

private val notes: Map<String, PartNote> = mapOf(
    "l_block" to PartNote("底板加一侧立板", "Base with an upright", Difficulty.EASY),
    "step_block" to PartNote("三级台阶逐级升高", "Three rising steps", Difficulty.EASY),
    "wedge_cut" to PartNote("长方体切去一角", "Block with one corner cut", Difficulty.EASY),
    "plate_hole" to PartNote("平板中央一个通孔", "Plate with a through hole", Difficulty.MEDIUM),
    "v_block" to PartNote("顶面开 V 形槽", "V groove in the top", Difficulty.MEDIUM),
    "slot_block" to PartNote("顶面开矩形通槽", "Square slot in the top", Difficulty.MEDIUM),
    "bearing_seat" to PartNote("底板、立板加轴孔", "Base, upright and bore", Difficulty.HARD),
    "notched_tube" to PartNote("圆筒顶部开通槽", "Tube with a slot across", Difficulty.HARD),
)

private fun PartDefinition.summary(number: Int): PartSummary {
    val note = notes[id]
    return PartSummary(id, number, nameZh, nameEn, descriptionZh, note?.summaryZh, note?.summaryEn, note?.difficulty)
}

/**
 * The eight built-in practice parts. A drawing is computed off the main thread, once: the drawing
 * window and the projection box ask for the same part at the same moment and share one computation,
 * and the result is kept. [warmUp] computes the rest of the library in the background so that
 * picking another part is immediate.
 */
class BuiltInPartRepository(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val compute: (String) -> PartDrawing = ::computeDrawing,
) : PartRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val drawings = ConcurrentHashMap<String, Deferred<PartDrawing>>()

    override fun parts(): List<PartSummary> =
        PartLibrary.parts.mapIndexed { index, part -> part.summary(index + 1) }

    override suspend fun drawing(partId: String): PartDrawing {
        val shared = drawings.computeIfAbsent(partId) { scope.async { compute(partId) } }
        try {
            return shared.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed computation is not kept: Retry computes again.
            drawings.remove(partId, shared)
            throw e
        }
    }

    /** Computes every part that has not been asked for yet, one after the other. */
    fun warmUp(): Job = scope.launch {
        for (part in PartLibrary.parts) {
            runCatching { drawing(part.id) }
        }
    }
}

/** Test double: real geometry for a chosen subset, with switchable failures. */
class FakePartRepository(
    private val ids: List<String> = listOf("l_block", "plate_hole"),
    var failingIds: Set<String> = emptySet(),
) : PartRepository {
    var drawingCalls = 0
        private set

    override fun parts(): List<PartSummary> =
        ids.mapIndexed { index, id -> PartLibrary[id].summary(index + 1) }

    override suspend fun drawing(partId: String): PartDrawing {
        drawingCalls++
        if (partId in failingIds) throw IllegalStateException("drawing failed for $partId")
        return computeDrawing(partId)
    }
}
