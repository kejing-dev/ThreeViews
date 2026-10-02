package io.embodit.threeviews.domain.model

import io.embodit.threeviews.domain.geometry.Vec2
import io.embodit.threeviews.domain.geometry.ViewKind

/** A point on the unfolded sheet, millimetres. x runs right, y runs DOWN, origin at the front view's top-left corner. */
data class SheetPoint(val x: Double, val y: Double)

/** Where one view's bounding box sits on the sheet. */
data class ViewPlacement(
    val view: ViewKind,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** Drawing-space corner (minU, maxV) that maps to ([x], [y]). */
    val minU: Double,
    val maxV: Double,
) {
    /** Maps a drawing-space point of this view onto the sheet. */
    fun toSheet(p: Vec2): SheetPoint = SheetPoint(x + (p.u - minU), y + (maxV - p.v))

    val right: Double get() = x + width
    val bottom: Double get() = y + height
}

enum class AlignmentRule(val labelZh: String, val labelEn: String) {
    LENGTH("长对正", "Length aligned"),
    HEIGHT("高平齐", "Height level"),
    WIDTH("宽相等", "Width equal"),
}

/** One alignment guide: a polyline on the sheet plus the rule it demonstrates. */
data class AlignmentGuide(val rule: AlignmentRule, val points: List<SheetPoint>)

/** The first-angle arrangement of the three views: front top-left, top below it, left to its right. */
data class SheetLayout(
    val front: ViewPlacement,
    val top: ViewPlacement,
    val left: ViewPlacement,
    /** Gap between neighbouring views, millimetres. */
    val gap: Double,
    val width: Double,
    val height: Double,
    /** The 45 degree width-transfer line. */
    val miterStart: SheetPoint,
    val miterEnd: SheetPoint,
    val guides: List<AlignmentGuide>,
    /** Where the projection axes cross: the corner of the three planes, half a gap outside the front view. */
    val origin: SheetPoint = SheetPoint(front.right + gap / 2, front.bottom + gap / 2),
    /** Whether each rule holds for this drawing. A correct projection satisfies all three. */
    val holds: Map<AlignmentRule, Boolean> = emptyMap(),
) {
    /** The unfolded planes: the views plus half a gap all round. Axes and frame end here. */
    val paperLeft: Double get() = -gap / 2
    val paperTop: Double get() = -gap / 2
    val paperRight: Double get() = width + gap / 2
    val paperBottom: Double get() = height + gap / 2

    fun placement(view: ViewKind): ViewPlacement = when (view) {
        ViewKind.FRONT -> front
        ViewKind.TOP -> top
        ViewKind.LEFT -> left
    }
}
