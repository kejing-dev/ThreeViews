package io.embodit.threeviews.domain.geometry

/**
 * A cylindrical feature (boss or hole) of a part, used to draw centre lines.
 * [start] and [end] are the ends of the real feature on its axis; [radius] is the largest radius
 * of the concentric surfaces sharing that axis.
 */
data class AxisFeature(val start: Vec3, val end: Vec3, val radius: Double)

/** A catalogue entry: names, description, cylindrical features and the recipe that builds the solid. */
class PartDefinition(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val descriptionZh: String,
    val axes: List<AxisFeature>,
    private val builder: () -> Solid,
) {
    /** Builds a fresh solid. Pure: every call returns an equal, independent result. */
    fun build(): Solid = builder()

    override fun toString(): String = "PartDefinition($id)"
}

/** The eight practice parts. Dimensions in millimetres. */
object PartLibrary {

    val parts: List<PartDefinition> = listOf(
        PartDefinition(
            id = "l_block",
            nameZh = "L 形块",
            nameEn = "L-Block",
            descriptionZh = "底板加一侧立板，最基本的叠加体，左视图里有一条虚线。",
            axes = emptyList(),
        ) {
            b(0, 0, 0, 60, 15, 40).union(b(0, 0, 0, 15, 45, 40))
        },
        PartDefinition(
            id = "step_block",
            nameZh = "阶梯块",
            nameEn = "Step Block",
            descriptionZh = "三级台阶逐级升高，练习“长对正、高平齐、宽相等”。",
            axes = emptyList(),
        ) {
            b(0, 0, 0, 70, 15, 40)
                .union(b(0, 15, 0, 45, 30, 40))
                .union(b(0, 30, 0, 20, 45, 40))
        },
        PartDefinition(
            id = "wedge_cut",
            nameZh = "斜切块",
            nameEn = "Bevelled Block",
            descriptionZh = "长方体被正垂面切去一角，斜面在主视图中积聚成一条斜线。",
            axes = emptyList(),
        ) {
            // The cutter's sloped side lies exactly on the line through (25,40) and (60,15);
            // its other two sides are well outside the block, so no cutter face is coplanar with a block face.
            b(0, 0, 0, 60, 40, 40)
                .subtract(prism(listOf(18.0 to 45.0, 67.0 to 10.0, 67.0 to 45.0), -1.0, 41.0))
        },
        PartDefinition(
            id = "plate_hole",
            nameZh = "带孔底板",
            nameEn = "Plate with Hole",
            descriptionZh = "平板中央一个通孔，孔的轮廓在主、左视图中画成虚线。",
            axes = listOf(AxisFeature(Vec3(35.0, 0.0, 22.0), Vec3(35.0, 14.0, 22.0), 11.0)),
        ) {
            b(0, 0, 0, 70, 14, 44)
                .subtract(cylinder(Vec3(35.0, -1.0, 22.0), Vec3(35.0, 15.0, 22.0), 11.0))
        },
        PartDefinition(
            id = "v_block",
            nameZh = "V 形块",
            nameEn = "V-Block",
            descriptionZh = "顶面开一条 V 形槽，槽底线在左视图中是虚线。",
            axes = emptyList(),
        ) {
            b(0, 0, 0, 60, 40, 40)
                .subtract(prism(listOf(10.0 to 42.0, 30.0 to 20.0, 50.0 to 42.0), -1.0, 41.0))
        },
        PartDefinition(
            id = "slot_block",
            nameZh = "开槽块",
            nameEn = "Slotted Block",
            descriptionZh = "顶面中间开一条矩形通槽，槽底在左视图中是虚线。",
            axes = emptyList(),
        ) {
            b(0, 0, 0, 60, 30, 40).subtract(b(20, 15, -1, 40, 31, 41))
        },
        PartDefinition(
            id = "bearing_seat",
            nameZh = "支座",
            nameEn = "Bearing Seat",
            descriptionZh = "底板带两个安装孔，立板顶部为半圆并开轴孔，综合了叠加、相切与挖切。",
            axes = listOf(
                AxisFeature(Vec3(40.0, 40.0, 0.0), Vec3(40.0, 40.0, 15.0), 20.0),
                AxisFeature(Vec3(10.0, 0.0, 35.0), Vec3(10.0, 12.0, 35.0), 5.0),
                AxisFeature(Vec3(70.0, 0.0, 35.0), Vec3(70.0, 12.0, 35.0), 5.0),
            ),
        ) {
            val base = b(0, 0, 0, 80, 12, 50)
            val upright = b(20, 12, 0, 60, 40, 15)
                .union(cylinder(Vec3(40.0, 40.0, 0.0), Vec3(40.0, 40.0, 15.0), 20.0))
            base.union(upright)
                .subtract(cylinder(Vec3(40.0, 40.0, -1.0), Vec3(40.0, 40.0, 16.0), 10.0))
                .subtract(cylinder(Vec3(10.0, -1.0, 35.0), Vec3(10.0, 13.0, 35.0), 5.0))
                .subtract(cylinder(Vec3(70.0, -1.0, 35.0), Vec3(70.0, 13.0, 35.0), 5.0))
        },
        PartDefinition(
            id = "notched_tube",
            nameZh = "开口圆筒",
            nameEn = "Notched Tube",
            descriptionZh = "空心圆筒顶部横向开一条通槽，练习回转体上的截交线。",
            axes = listOf(AxisFeature(Vec3(30.0, 0.0, 30.0), Vec3(30.0, 50.0, 30.0), 28.0)),
        ) {
            cylinder(Vec3(30.0, 0.0, 30.0), Vec3(30.0, 50.0, 30.0), 28.0)
                .subtract(cylinder(Vec3(30.0, -1.0, 30.0), Vec3(30.0, 51.0, 30.0), 16.0))
                .subtract(b(-1, 35, 22, 61, 51, 38))
        },
    )

    private val byId: Map<String, PartDefinition> = parts.associateBy { it.id }

    /** The part with this id, or null. */
    fun find(id: String): PartDefinition? = byId[id]

    /** The part with this id; throws [IllegalArgumentException] for an unknown id. */
    operator fun get(id: String): PartDefinition =
        byId[id] ?: throw IllegalArgumentException("Unknown part id: $id")

    private fun b(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int): Solid =
        box(
            Vec3(x0.toDouble(), y0.toDouble(), z0.toDouble()),
            Vec3(x1.toDouble(), y1.toDouble(), z1.toDouble()),
        )
}
