package io.embodit.threeviews.domain.geometry

/**
 * Triangle mesh of a part for rendering. Units are millimetres, part space.
 *
 * Shading is flat: every polygon has its own vertices, all carrying the polygon normal.
 * Triangles are counter-clockwise when seen from outside. T-junctions left by the CSG are stitched
 * (a polygon with foreign vertices on its boundary is fanned from its centroid through all of them),
 * so the mesh is watertight.
 *
 * Treat the arrays as read-only.
 */
class MeshData(
    /** x, y, z per vertex. */
    val positions: FloatArray,
    /** Unit normal x, y, z per vertex. */
    val normals: FloatArray,
    /** Three vertex indices per triangle. */
    val indices: IntArray,
    /** View-independent feature edges (creases of 20 degrees or more), collinear pieces merged. */
    val sharpEdges: List<Segment3>,
    val boundsMin: Vec3,
    val boundsMax: Vec3,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    /** Enclosed volume in cubic millimetres, from the triangles (divergence theorem). */
    fun volume(): Double {
        var six = 0.0
        var t = 0
        while (t < indices.size) {
            val a = indices[t] * 3
            val b = indices[t + 1] * 3
            val c = indices[t + 2] * 3
            val ax = positions[a].toDouble(); val ay = positions[a + 1].toDouble(); val az = positions[a + 2].toDouble()
            val bx = positions[b].toDouble(); val by = positions[b + 1].toDouble(); val bz = positions[b + 2].toDouble()
            val cx = positions[c].toDouble(); val cy = positions[c + 1].toDouble(); val cz = positions[c + 2].toDouble()
            six += ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) + az * (bx * cy - by * cx)
            t += 3
        }
        return six / 6.0
    }

    companion object {
        fun from(geometry: PartGeometry): MeshData {
            val polygons = geometry.polygons
            var vertexTotal = 0
            var triangleTotal = 0
            for (i in polygons.indices) {
                val loop = geometry.outlines[i].size
                if (loop == polygons[i].vertices.size) {
                    vertexTotal += loop
                    triangleTotal += loop - 2
                } else {
                    vertexTotal += loop + 1
                    triangleTotal += loop
                }
            }
            val positions = FloatArray(vertexTotal * 3)
            val normals = FloatArray(vertexTotal * 3)
            val indices = IntArray(triangleTotal * 3)
            var v = 0
            var t = 0

            fun put(p: Vec3, n: Vec3) {
                positions[v * 3] = p.x.toFloat()
                positions[v * 3 + 1] = p.y.toFloat()
                positions[v * 3 + 2] = p.z.toFloat()
                normals[v * 3] = n.x.toFloat()
                normals[v * 3 + 1] = n.y.toFloat()
                normals[v * 3 + 2] = n.z.toFloat()
                v++
            }

            for (i in polygons.indices) {
                val poly = polygons[i]
                val normal = poly.plane.normal
                val loop = geometry.outlines[i]
                val base = v
                if (loop.size == poly.vertices.size) {
                    for (p in loop) put(p, normal)
                    for (k in 1 until loop.size - 1) {
                        indices[t++] = base
                        indices[t++] = base + k
                        indices[t++] = base + k + 1
                    }
                } else {
                    var centroid = Vec3.ZERO
                    for (p in poly.vertices) centroid += p
                    centroid /= poly.vertices.size.toDouble()
                    put(centroid, normal)
                    for (p in loop) put(p, normal)
                    for (k in loop.indices) {
                        indices[t++] = base
                        indices[t++] = base + 1 + k
                        indices[t++] = base + 1 + (k + 1) % loop.size
                    }
                }
            }
            return MeshData(positions, normals, indices, geometry.sharpEdges, geometry.boundsMin, geometry.boundsMax)
        }
    }
}
