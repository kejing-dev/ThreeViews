package io.embodit.threeviews.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the drafting table (design/design-doc.md §3): tracing paper, ink, a blue pencil
 * for construction lines and a red pencil for centre lines.
 *
 * Semantic roles that map onto a PicoTheme [com.pico.spatial.ui.design.ColorScheme] slot are
 * injected in [ThreeViewsTheme]; the rest are brand tokens used verbatim on the paper sheet and in
 * the projection box. The window root always keeps the system Material.Regular glass.
 */
object ThreeViewsColors {
    // ---- ColorScheme role overrides -------------------------------------------------------
    /** Blueprint blue: primary buttons, selection, switches that are on. */
    val Interaction = Color(0xFF2B5FD9) // design-style: fixed-figma-color design-doc §3 interaction
    /** A drawing that could not be computed. */
    val Error = Color(0xFFC62828) // design-style: fixed-figma-color design-doc §3 error
    /** The projection box is closed. */
    val Alert = Color(0xFFE08A00) // design-style: fixed-figma-color design-doc §3 alert
    /** The three alignment rules hold. */
    val Passable = Color(0xFF2E8B57) // design-style: fixed-figma-color design-doc §3 passable
    /** Text on the accent color. */
    val OnInteraction = Color(0xFFFFFFFF) // design-style: fixed-figma-color design-doc §3 labelPrimaryLight

    // ---- Brand tokens (no ColorScheme role) -----------------------------------------------
    /** Tracing paper: the opaque sheet inside the window, so black lines always have a light ground. */
    val Paper = Color(0xFFF6F3EA) // design-style: fixed-figma-color design-doc §3 Paper
    /** Sheet border and title block rules. */
    val PaperEdge = Color(0xFFD9D3C3) // design-style: fixed-figma-color design-doc §3 PaperEdge
    /** Thick visible outlines, dashed hidden outlines and text on the sheet. */
    val Ink = Color(0xFF16181C) // design-style: fixed-figma-color design-doc §3 Ink
    /** Secondary text on the sheet, plane outlines in the box. */
    val InkSoft = Color(0xFF5B616B) // design-style: fixed-figma-color design-doc §3 InkSoft
    /** Thin chain lines: axes and lines of symmetry. */
    val CenterLine = Color(0xFFC4372B) // design-style: fixed-figma-color design-doc §3 CenterLine
    /** Projection rays and alignment guides. */
    val RayBlue = Color(0xFF2B5FD9) // design-style: fixed-figma-color design-doc §3 RayBlue
    /** Tint of plane V (front view). */
    val PlaneV = Color(0xFFDCE6F7) // design-style: fixed-figma-color design-doc §3 PlaneV
    /** Tint of plane H (top view). */
    val PlaneH = Color(0xFFF4ECD2) // design-style: fixed-figma-color design-doc §3 PlaneH
    /** Tint of plane W (left view). */
    val PlaneW = Color(0xFFDCEFE2) // design-style: fixed-figma-color design-doc §3 PlaneW
    /** Surface of the part. */
    val PartBody = Color(0xFFB9C3CE) // design-style: fixed-figma-color design-doc §3 PartBody
}
