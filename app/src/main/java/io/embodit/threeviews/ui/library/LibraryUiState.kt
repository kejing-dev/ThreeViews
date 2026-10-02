package io.embodit.threeviews.ui.library

import io.embodit.threeviews.domain.model.PartSummary
import io.embodit.threeviews.domain.model.SceneState

/** Everything the part library window shows. */
data class LibraryUiState(
    val parts: List<PartSummary> = emptyList(),
    val selectedPartId: String = SceneState.DEFAULT_PART_ID,
)

sealed interface LibraryEvent {
    data class SelectPart(val partId: String) : LibraryEvent

    /** The library window appeared. */
    data object Opened : LibraryEvent

    /** The library window was closed by the user or the system. */
    data object Closed : LibraryEvent
}
