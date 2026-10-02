package io.embodit.threeviews.domain.model

import io.embodit.threeviews.domain.geometry.ViewKind

/** Which projection the user is concentrating on. ALL shows the three views with equal weight. */
enum class ViewFocus(val view: ViewKind?) {
    ALL(null),
    FRONT(ViewKind.FRONT),
    TOP(ViewKind.TOP),
    LEFT(ViewKind.LEFT);

    companion object {
        fun of(view: ViewKind): ViewFocus = entries.first { it.view == view }
    }
}

enum class Difficulty { EASY, MEDIUM, HARD }

/** One entry of the part library as the list shows it. */
data class PartSummary(
    val id: String,
    val number: Int,
    val nameZh: String,
    val nameEn: String,
    /** One sentence on what the part teaches. */
    val descriptionZh: String,
    /** A few words on the structure, for the list row. Null when the library has none. */
    val summaryZh: String? = null,
    val summaryEn: String? = null,
    val difficulty: Difficulty? = null,
)

/**
 * Everything the projection box, the part library and the drawing sheet share. It lives in one
 * process-wide store because the three surfaces are separate window containers.
 */
data class SceneState(
    val selectedPartId: String = DEFAULT_PART_ID,
    val focus: ViewFocus = ViewFocus.ALL,
    /** True while the three planes are laid flat into one sheet. */
    val unfolded: Boolean = false,
    val showRays: Boolean = true,
    val showHidden: Boolean = true,
    /** The alignment guides of the sheet: off until the user asks for them. */
    val showGuides: Boolean = false,
    /** Rotation of the whole box about the vertical axis, degrees. */
    val yawDegrees: Float = DEFAULT_YAW,
    /** Whether the part library window is currently open. */
    val libraryOpen: Boolean = false,
    /** Whether the drawing sheet window is currently open. */
    val sheetOpen: Boolean = false,
    /** Bumped by Retry, so both surfaces compute the selected part again after a failure. */
    val attempt: Int = 0,
) {
    companion object {
        const val DEFAULT_PART_ID = "l_block"

        /** A three-quarter view: the viewer sees V and W at once, like the textbook figure. */
        const val DEFAULT_YAW = 28f
        const val MIN_YAW = -80f
        const val MAX_YAW = 80f
    }
}
