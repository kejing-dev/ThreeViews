package io.embodit.threeviews.domain.geometry

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Writes every part's three views as JSON so the drawings can be rendered and inspected outside the JVM. */
class DrawingDumpTest {
    @Test
    fun `dump all drawings for visual review`() {
        val out = File("build/drawing-dump").apply { mkdirs() }
        val sb = StringBuilder("{")
        PartLibrary.parts.forEachIndexed { i, part ->
            val d = computeDrawing(part)
            if (i > 0) sb.append(',')
            sb.append("\"${part.id}\":{\"name\":\"${part.nameZh}\",\"tris\":${d.mesh.triangleCount},\"sharp\":${d.mesh.sharpEdges.size},\"views\":{")
            d.views.forEachIndexed { k, v ->
                if (k > 0) sb.append(',')
                fun lines(ls: List<Polyline2>) = ls.joinToString(",", "[", "]") { pl ->
                    "{\"closed\":${pl.closed},\"pts\":" + pl.points.joinToString(",", "[", "]") { "[${it.u},${it.v}]" } + "}"
                }
                sb.append("\"${v.view.name}\":{\"bounds\":[${v.bounds.minU},${v.bounds.minV},${v.bounds.maxU},${v.bounds.maxV}],")
                sb.append("\"visible\":${lines(v.visible)},\"hidden\":${lines(v.hidden)},\"center\":${lines(v.center)}}")
            }
            sb.append("}}")
        }
        sb.append('}')
        File(out, "drawings.json").writeText(sb.toString())
        assertTrue(File(out, "drawings.json").length() > 1000)
    }
}
