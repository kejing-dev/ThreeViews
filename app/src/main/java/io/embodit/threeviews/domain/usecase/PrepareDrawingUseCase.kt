package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.data.repository.PartRepository
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.SceneState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/** Loads one part and derives everything both surfaces need: the drawing, the sheet layout and the box layout. */
class PrepareDrawingUseCase(
    private val repository: PartRepository,
    private val sheetLayout: SheetLayoutUseCase = SheetLayoutUseCase(),
    private val boxLayout: BoxLayoutUseCase = BoxLayoutUseCase(),
) {
    suspend fun prepare(partId: String): DrawingStatus =
        try {
            val drawing = repository.drawing(partId)
            DrawingStatus.Ready(
                drawing = drawing,
                sheet = sheetLayout.layout(drawing, gap = BoxLayoutUseCase.DEFAULT_MARGIN * 2),
                box = boxLayout.layout(drawing.mesh),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DrawingStatus.Failed(partId)
        }

    /**
     * Follows the scene: every newly selected part, and every retry of the same part, first reports
     * [DrawingStatus.Loading], then its result. A newer request abandons the computation of an older one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(scene: Flow<SceneState>): Flow<DrawingStatus> =
        scene.map { it.selectedPartId to it.attempt }.distinctUntilChanged().transformLatest { (id, _) ->
            emit(DrawingStatus.Loading)
            emit(prepare(id))
        }
}
