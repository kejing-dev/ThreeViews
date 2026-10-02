package io.embodit.threeviews.ui.navigation

/**
 * Ids of the window containers. Each has one instance; the tag keeps a second open request from
 * creating another.
 */
object Containers {
    /** The Volumetric projection box: the default container, declared in the manifest. */
    const val BOX = "ProjectionBox"

    /** The Planar part library, left of the box. */
    const val LIBRARY = "PartLibrary"
    const val LIBRARY_TAG = "library"

    /** The Planar drawing sheet, right of the box. */
    const val SHEET = "DrawingSheet"
    const val SHEET_TAG = "sheet"
}
