package io.embodit.threeviews.domain.geometry

/**
 * Constructive solid geometry on BSP trees: a Kotlin port of csg.js (Evan Wallace, MIT licence).
 *
 * The algorithm is the original one. Two deliberate differences from csg.js:
 *  - a fragment produced by splitting keeps the plane of its parent polygon instead of recomputing
 *    it from its first three vertices (those can be collinear, which gives csg.js a NaN normal);
 *  - vertices carry positions only (normals are flat and come from the polygon plane).
 */

/** Tolerance, in millimetres, used to decide on which side of a plane a point lies. */
const val CSG_EPSILON: Double = 1e-5

/** Oriented plane: points p with normal . p = w. The normal points out of the solid. */
class Plane(val normal: Vec3, val w: Double) {

    fun flipped(): Plane = Plane(-normal, -w)

    /** Positive on the outside (the side the normal points to). */
    fun signedDistance(p: Vec3): Double = normal.dot(p) - w

    /**
     * Splits [polygon] by this plane and appends the pieces to the given lists.
     * Coplanar polygons go to [coplanarFront] or [coplanarBack] depending on their facing.
     */
    internal fun splitPolygon(
        polygon: Polygon3,
        coplanarFront: MutableList<Polygon3>,
        coplanarBack: MutableList<Polygon3>,
        front: MutableList<Polygon3>,
        back: MutableList<Polygon3>,
    ) {
        val vertices = polygon.vertices
        val count = vertices.size
        var polygonType = COPLANAR
        val types = IntArray(count)
        for (i in 0 until count) {
            val t = normal.dot(vertices[i]) - w
            val type = if (t < -CSG_EPSILON) BACK else if (t > CSG_EPSILON) FRONT else COPLANAR
            polygonType = polygonType or type
            types[i] = type
        }
        when (polygonType) {
            COPLANAR ->
                (if (normal.dot(polygon.plane.normal) > 0) coplanarFront else coplanarBack).add(polygon)
            FRONT -> front.add(polygon)
            BACK -> back.add(polygon)
            else -> {
                val f = ArrayList<Vec3>(count + 1)
                val b = ArrayList<Vec3>(count + 1)
                for (i in 0 until count) {
                    val j = (i + 1) % count
                    val ti = types[i]
                    val tj = types[j]
                    val vi = vertices[i]
                    val vj = vertices[j]
                    if (ti != BACK) f.add(vi)
                    if (ti != FRONT) b.add(vi)
                    if ((ti or tj) == SPANNING) {
                        val t = (w - normal.dot(vi)) / normal.dot(vj - vi)
                        val v = vi.lerp(vj, t)
                        f.add(v)
                        b.add(v)
                    }
                }
                if (f.size >= 3) front.add(Polygon3(f, polygon.plane))
                if (b.size >= 3) back.add(Polygon3(b, polygon.plane))
            }
        }
    }

    companion object {
        private const val COPLANAR = 0
        private const val FRONT = 1
        private const val BACK = 2
        private const val SPANNING = 3

        fun fromPoints(a: Vec3, b: Vec3, c: Vec3): Plane {
            val n = (b - a).cross(c - a).unit()
            return Plane(n, n.dot(a))
        }

        /** Plane of a planar polygon by Newell's method (robust to collinear leading vertices). */
        fun fromPolygon(vertices: List<Vec3>): Plane {
            var nx = 0.0
            var ny = 0.0
            var nz = 0.0
            for (i in vertices.indices) {
                val p = vertices[i]
                val q = vertices[(i + 1) % vertices.size]
                nx += (p.y - q.y) * (p.z + q.z)
                ny += (p.z - q.z) * (p.x + q.x)
                nz += (p.x - q.x) * (p.y + q.y)
            }
            val n = Vec3(nx, ny, nz).unit()
            return Plane(n, n.dot(vertices[0]))
        }
    }
}

/**
 * Convex planar polygon. Vertices are in counter-clockwise order when seen from outside the solid,
 * i.e. from the side [plane]'s normal points to.
 */
class Polygon3(val vertices: List<Vec3>, val plane: Plane) {

    constructor(vertices: List<Vec3>) : this(vertices, Plane.fromPolygon(vertices))

    init {
        require(vertices.size >= 3) { "A polygon needs at least three vertices" }
    }

    fun flipped(): Polygon3 = Polygon3(vertices.reversed(), plane.flipped())

    fun area(): Double {
        var sum = Vec3.ZERO
        val o = vertices[0]
        for (i in 1 until vertices.size - 1) {
            sum += (vertices[i] - o).cross(vertices[i + 1] - o)
        }
        return 0.5 * sum.length()
    }
}

/** Immutable closed solid described by its boundary polygons. */
class Solid(val polygons: List<Polygon3>) {

    /** Material that is in this solid or in [other]. */
    fun union(other: Solid): Solid {
        val a = BspNode(polygons)
        val b = BspNode(other.polygons)
        a.clipTo(b)
        b.clipTo(a)
        b.invert()
        b.clipTo(a)
        b.invert()
        a.build(b.allPolygons())
        return Solid(a.allPolygons())
    }

    /** Material that is in this solid but not in [other]. */
    fun subtract(other: Solid): Solid {
        val a = BspNode(polygons)
        val b = BspNode(other.polygons)
        a.invert()
        a.clipTo(b)
        b.clipTo(a)
        b.invert()
        b.clipTo(a)
        b.invert()
        a.build(b.allPolygons())
        a.invert()
        return Solid(a.allPolygons())
    }

    /** Material that is in both solids. */
    fun intersect(other: Solid): Solid {
        val a = BspNode(polygons)
        val b = BspNode(other.polygons)
        a.invert()
        b.clipTo(a)
        b.invert()
        a.clipTo(b)
        b.clipTo(a)
        a.build(b.allPolygons())
        a.invert()
        return Solid(a.allPolygons())
    }

    /** The complement: same polygons, facing the other way. */
    fun inverse(): Solid = Solid(polygons.map { it.flipped() })

    /** Enclosed volume in cubic millimetres (divergence theorem). */
    fun volume(): Double {
        var six = 0.0
        for (p in polygons) {
            val o = p.vertices[0]
            for (i in 1 until p.vertices.size - 1) {
                six += o.dot(p.vertices[i].cross(p.vertices[i + 1]))
            }
        }
        return six / 6.0
    }

    /** Axis-aligned bounding box as (min, max). */
    fun bounds(): Pair<Vec3, Vec3> {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        for (p in polygons) for (v in p.vertices) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.z < minZ) minZ = v.z
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
            if (v.z > maxZ) maxZ = v.z
        }
        return Vec3(minX, minY, minZ) to Vec3(maxX, maxY, maxZ)
    }
}

/**
 * A node of a BSP tree. Mutable, but only ever lives inside one boolean operation,
 * so nothing mutable is shared between calls or threads.
 */
private class BspNode(initial: List<Polygon3>? = null) {
    private var plane: Plane? = null
    private var front: BspNode? = null
    private var back: BspNode? = null
    private var polygons: MutableList<Polygon3> = ArrayList()

    init {
        if (initial != null) build(initial)
    }

    /** Converts solid space to empty space and empty space to solid space. */
    fun invert() {
        for (i in polygons.indices) polygons[i] = polygons[i].flipped()
        plane = plane?.flipped()
        front?.invert()
        back?.invert()
        val temp = front
        front = back
        back = temp
    }

    /** Removes the parts of [list] that are inside this BSP tree. */
    fun clipPolygons(list: List<Polygon3>): List<Polygon3> {
        val splitter = plane ?: return ArrayList(list)
        var f: MutableList<Polygon3> = ArrayList()
        var b: MutableList<Polygon3> = ArrayList()
        for (p in list) splitter.splitPolygon(p, f, b, f, b)
        front?.let { f = it.clipPolygons(f).toMutableList() }
        val backNode = back
        b = if (backNode != null) backNode.clipPolygons(b).toMutableList() else ArrayList()
        f.addAll(b)
        return f
    }

    /** Removes the parts of this tree's polygons that are inside [bsp]. */
    fun clipTo(bsp: BspNode) {
        polygons = bsp.clipPolygons(polygons).toMutableList()
        front?.clipTo(bsp)
        back?.clipTo(bsp)
    }

    fun allPolygons(): List<Polygon3> {
        val out = ArrayList<Polygon3>()
        collect(out)
        return out
    }

    private fun collect(out: MutableList<Polygon3>) {
        out.addAll(polygons)
        front?.collect(out)
        back?.collect(out)
    }

    /** Adds [list] to the tree, splitting the polygons by the existing planes as needed. */
    fun build(list: List<Polygon3>) {
        if (list.isEmpty()) return
        val splitter = plane ?: list[0].plane.also { plane = it }
        val f = ArrayList<Polygon3>()
        val b = ArrayList<Polygon3>()
        for (p in list) splitter.splitPolygon(p, polygons, polygons, f, b)
        if (f.isNotEmpty()) {
            val node = front ?: BspNode().also { front = it }
            node.build(f)
        }
        if (b.isNotEmpty()) {
            val node = back ?: BspNode().also { back = it }
            node.build(b)
        }
    }
}
