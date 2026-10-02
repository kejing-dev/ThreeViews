package io.embodit.threeviews.ui.sheet

import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.PartSummary
import io.embodit.threeviews.domain.model.ViewFocus

/** Everything the drawing sheet window shows. */
data class SheetUiState(
    val part: PartSummary? = null,
    val drawing: DrawingStatus = DrawingStatus.Loading,
    val focus: ViewFocus = ViewFocus.ALL,
    val showHidden: Boolean = true,
    val showGuides: Boolean = false,
) {
    /** Guides run between the views, so they need a drawing. */
    val guidesEnabled: Boolean get() = drawing !is DrawingStatus.Failed
}

sealed interface SheetEvent {
    /** A tap on one view of the sheet. */
    data class ViewTapped(val view: ViewKind) : SheetEvent

    data class SetGuides(val show: Boolean) : SheetEvent

    /** Compute the drawing of the selected part again after a failure. */
    data object Retry : SheetEvent

    /** The sheet window appeared. */
    data object Opened : SheetEvent

    /** The sheet window was closed by the user or the system. */
    data object Closed : SheetEvent
}
