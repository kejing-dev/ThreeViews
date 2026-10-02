package io.embodit.threeviews.domain.model

import io.embodit.threeviews.domain.geometry.PartDrawing

/** Result of computing one part: its drawing plus where the views sit on the sheet and in the box. */
sealed interface DrawingStatus {
    data object Loading : DrawingStatus

    data class Ready(val drawing: PartDrawing, val sheet: SheetLayout, val box: BoxLayout) : DrawingStatus

    data class Failed(val partId: String) : DrawingStatus
}
