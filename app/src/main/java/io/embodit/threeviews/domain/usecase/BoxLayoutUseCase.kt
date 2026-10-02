package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.MeshData
import io.embodit.threeviews.domain.geometry.Segment3
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxLayout
import io.embodit.threeviews.domain.model.ProjectionRay
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Places the three projection planes around a part and derives the projection rays.
 * First-angle projection: the part sits between the viewer and each plane.
 */
class BoxLayoutUseCase {

    fun layout(mesh: MeshData, margin: Double = DEFAULT_MARGIN): BoxLayout {
        require(margin > 0.0) { "margin must be positive" }
        val lo = mesh.boundsMin
        val hi = mesh.boundsMax
        return BoxLayout(
            margin = margin,
            backZ = lo.z - margin,
            bottomY = lo.y - margin,
            rightX = hi.x + margin,
            minX = lo.x - margin,
            topY = hi.y + margin,
            frontZ = hi.z + margin,
        )
    }

    /** The image of [p] on the plane that carries [view]. */
    fun imageOf(p: Vec3, view: ViewKind, box: BoxLayout): Vec3 = when (view) {
        ViewKind.FRONT -> Vec3(p.x, p.y, box.backZ)
        ViewKind.TOP -> Vec3(p.x, box.bottomY, p.z)
        ViewKind.LEFT -> Vec3(box.rightX, p.y, p.z)
    }

    /**
     * One ray per distinct image point: from the corner of the part that is farthest from the
     * plane, through every corner on the same projection line, to the plane.
     */
    fun rays(mesh: MeshData, view: ViewKind, box: BoxLayout): List<ProjectionRay> {
        val farthest = LinkedHashMap<Pair<Long, Long>, Vec3>()
        for (corner in corners(mesh.sharpEdges)) {
            val q = view.project(corner)
            val key = (q.u * KEY_SCALE).roundToLong() to (q.v * KEY_SCALE).roundToLong()
            val best = farthest[key]
            if (best == null || view.depth(corner) > view.depth(best)) farthest[key] = corner
        }
        return farthest.values.map { ProjectionRay(it, imageOf(it, view, box)) }
    }

    /**
     * Corners of the part: end points of sharp edges where the outline really turns. Points along
     * a faceted circle are skipped, otherwise every hole would sprout dozens of rays.
     */
    fun corners(sharpEdges: List<Segment3>): List<Vec3> {
        val directions = LinkedHashMap<Triple<Long, Long, Long>, MutableList<Vec3>>()
        val points = HashMap<Triple<Long, Long, Long>, Vec3>()
        fun key(p: Vec3) = Triple((p.x * KEY_SCALE).roundToLong(), (p.y * KEY_SCALE).roundToLong(), (p.z * KEY_SCALE).roundToLong())
        for (e in sharpEdges) {
            if (e.length() < 1e-9) continue
            val d = (e.b - e.a).unit()
            val ka = key(e.a)
            val kb = key(e.b)
            directions.getOrPut(ka) { mutableListOf() }.add(d)
            directions.getOrPut(kb) { mutableListOf() }.add(-d)
            points[ka] = e.a
            points[kb] = e.b
        }
        val result = ArrayList<Vec3>()
        for ((k, dirs) in directions) {
            // Three or more edges always meet at a real corner; two edges only when the outline turns.
            val isCorner = dirs.size != 2 || abs(dirs[0].dot(dirs[1])) < TURN_COSINE
            if (isCorner) result.add(points.getValue(k))
        }
        return result
    }

    companion object {
        const val DEFAULT_MARGIN = 14.0
        private const val KEY_SCALE = 1000.0

        /** cos 30 degrees: a two-edge vertex is a corner when the edges turn by more than 30 degrees. */
        private const val TURN_COSINE = 0.8660254
    }
}
