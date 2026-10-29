package io.embodit.hazardhunt.domain.model

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Primitive of a procedural part: unit cube, cylinder (axis +Y), sphere, or a textured sign quad facing +Z. */
enum class Shape { BOX, CYL, SPHERE, SIGN }

/**
 * Colours of the procedural props (design `palette.brandTokens` plus plain material colours), ARGB. The scene makes
 * one lit material per paint; signs are textures (see [SignKind]).
 */
enum class Paint(val argb: Long) {
    PARTITION(0xFF8A9199),
    STEEL(0xFF5C6166),
    STEEL_DARK(0xFF3B3F44),
    GALVANIZED(0xFFB4BBC2),
    RACK_BLUE(0xFF2E5AAC),
    RACK_ORANGE(0xFFE8701A),
    BEECH(0xFFC9A66B),
    BEECH_DARK(0xFF8E6B3E),
    PEGBOARD(0xFFD8C7A0),
    CARDBOARD(0xFFB98A55),
    CARDBOARD_DARK(0xFF8C6639),
    PALLET(0xFFA9824F),
    DOOR(0xFF7D858C),
    PUSHBAR(0xFFC9CED3),
    BLACK(0xFF1F1F1F),
    YELLOW(0xFFF5C400),
    RED(0xFFC62828),
    CYLINDER(0xFF7E8790),
    BRASS(0xFFB08D57),
    CHAIN(0xFF2E3134),
    BIN_BLUE(0xFF2F6DB5),
    RAG(0xFF6E5130),
    OIL(0xFF14110E),
    OIL_GLOSS(0xFF3B352E),
    WATER(0xFF8FC3E0),
    BLADE(0xFFDCE1E6),
    GRINDER(0xFF1F5FA8),
    WHEEL(0xFF8A5A3C),
    AMBER(0xFFC58B2A),
    CLEAR_LIQUID(0xFFB9D7C8),
    WHITE(0xFFF2F2F2),
    STRIP(0xFFEDEDED),
    WIRE_RED(0xFFD32F2F),
    WIRE_BLUE(0xFF1E5BB8),
    WIRE_YG(0xFF8BC34A),
    COPPER(0xFFC87533),
    GREEN(0xFF1E9E5A),
    DARK_GLASS(0xFF2E353B),
    MIRROR(0xFFCFE3EE),
    ALU(0xFFC3C9CF),
    RUBBER(0xFF2A2A2A),
    DIFFUSER(0xFFF2F5F7),
    TUBE(0xFFFFFFFF),
    DRUM_BLUE(0xFF1F4E8C),
    DRUM_RED(0xFFB33A2B),
    CABINET_RED(0xFFC0392B),
    VISE(0xFF4A5560),
    LENS(0xFF9FD3E8),
    GAUGE_GREEN(0xFF2EBD5F),
    GAUGE_RED(0xFFE53935),
    TAG_RED(0xFFE5533D),
}

/** Self-drawn GB 2894-style signs and the floor plate (design `entities3d.signs`): one texture each. */
enum class SignKind { FLOOR_PLATE, EXIT_DOOR, EXIT_LIT, EXIT_DARK, ELECTRIC, GOGGLES, WET, LOAD, NO_BLOCK, EXTINGUISHER, FORKLIFT }

/**
 * One primitive in bay coordinates: [center] and [size] in metres (a cylinder is [size].x wide, [size].y tall along its
 * axis; a sign is [size].x x [size].y), turned by Euler angles in degrees applied roll (Z), then pitch (X), then yaw (Y).
 */
data class Part(
    val shape: Shape,
    val paint: Paint,
    val center: Vec3,
    val size: Vec3,
    val rotDeg: Vec3 = Vec3.ZERO,
    val sign: SignKind? = null,
) {
    val rotation: Quat4
        get() =
            Quat4.axisAngle(Vec3.UP, rad(rotDeg.y)) *
                Quat4.axisAngle(Vec3.RIGHT, rad(rotDeg.x)) *
                Quat4.axisAngle(Vec3(0f, 0f, 1f), rad(rotDeg.z))

    /** Corners of the part's bounding box after rotation (bay coordinates): clearance checks. */
    fun corners(): List<Vec3> {
        val h = size * 0.5f
        val half = if (shape == Shape.SIGN) Vec3(h.x, h.y, 0f) else h
        val q = rotation
        val out = ArrayList<Vec3>(8)
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            out += center + q.rotate(Vec3(half.x * sx, half.y * sy, half.z * sz))
        }
        return out
    }

    private fun rad(d: Float): Float = Math.toRadians(d.toDouble()).toFloat()
}

/** A checkpoint's looks: the safe version and one list per hazard variant. */
data class PoiLook(val safe: List<Part>, val hazards: List<List<Part>>)

/**
 * The procedural bay (design `layout.fixtures`, `entities3d`, asset fallback 4: every prop from unit cube / cylinder /
 * sphere / sign quad). Fixtures never change; each checkpoint shows its safe look or one hazard look per round.
 */
object Props {
    private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)

    private fun box(p: Paint, cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f) =
        Part(Shape.BOX, p, v(cx, cy, cz), v(sx, sy, sz), v(pitch, yaw, roll))

    private fun cyl(p: Paint, cx: Float, cy: Float, cz: Float, d: Float, h: Float, yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, depth: Float = d) =
        Part(Shape.CYL, p, v(cx, cy, cz), v(d, h, depth), v(pitch, yaw, roll))

    private fun ball(p: Paint, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float = dx, dz: Float = dx, yaw: Float = 0f) =
        Part(Shape.SPHERE, p, v(cx, cy, cz), v(dx, dy, dz), v(0f, yaw, 0f))

    private fun sign(k: SignKind, cx: Float, cy: Float, cz: Float, w: Float, h: Float, yaw: Float = 0f, pitch: Float = 0f) =
        Part(Shape.SIGN, Paint.WHITE, v(cx, cy, cz), v(w, h, 0.001f), v(pitch, yaw, 0f), k)

    /** A square bar of thickness [t] from [a] to [b]. */
    private fun bar(p: Paint, a: Vec3, b: Vec3, t: Float, depth: Float = t): Part {
        val d = b - a
        val len = d.length
        val yaw = Math.toDegrees(atan2(-d.z, d.x).toDouble()).toFloat()
        val roll = Math.toDegrees(atan2(d.y, hypot(d.x, d.z)).toDouble()).toFloat()
        return Part(Shape.BOX, p, (a + b) * 0.5f, v(len, t, depth), v(0f, yaw, roll))
    }

    // =========================================================================================
    // fixtures: floor plate, three partitions with the door, workbench and pegboard, rack, cable tray
    // =========================================================================================

    val fixtures: List<Part> by lazy {
        val out = ArrayList<Part>()
        // floor plate 4.0 x 4.6, centre (0, 0, -0.3): a sign quad laid flat (its top edge = the front, -Z)
        out += sign(SignKind.FLOOR_PLATE, 0f, 0.003f, -0.3f, 4.0f, 4.6f, pitch = -90f)
        partition(out, alongX = true, fixed = -2.6f, from = -2.0f, to = 2.0f, posts = listOf(-2f, -1f, 0f, 1f, 2f), gaps = emptyList())
        partition(out, alongX = false, fixed = -2.0f, from = -2.6f, to = 2.0f, posts = listOf(-2.6f, -1.45f, -0.3f, 0.85f, 2.0f), gaps = emptyList())
        partition(out, alongX = true, fixed = 2.0f, from = -2.0f, to = 2.0f, posts = listOf(-2f, -1.2f, -0.3f, 0.85f, 2.0f), gaps = listOf(-1.2f to -0.3f))
        // safety door (0.9 x 2.1 in x -1.2 .. -0.3), push bar, exit sign on the door, "keep clear" sign beside it
        out += box(Paint.DOOR, -0.75f, 1.05f, 1.99f, 0.88f, 2.08f, 0.04f)
        out += cyl(Paint.PUSHBAR, -0.75f, 1.0f, 1.945f, 0.035f, 0.7f, roll = 90f)
        out += box(Paint.STEEL, -1.08f, 1.0f, 1.955f, 0.03f, 0.06f, 0.05f)
        out += box(Paint.STEEL, -0.42f, 1.0f, 1.955f, 0.03f, 0.06f, 0.05f)
        out += sign(SignKind.EXIT_DOOR, -0.75f, 1.5f, 1.966f, 0.34f, 0.17f, yaw = 180f)
        out += sign(SignKind.NO_BLOCK, -1.6f, 1.45f, 1.982f, 0.22f, 0.22f, yaw = 180f)
        // workbench: beech top 1.8 x 0.75 at 0.9 m, open steel legs, lower rails; pegboard behind it
        out += box(Paint.BEECH, 0.9f, 0.875f, -2.225f, 1.8f, 0.05f, 0.75f)
        for (x in listOf(0.04f, 1.76f)) for (z in listOf(-1.89f, -2.56f)) out += box(Paint.STEEL, x, 0.425f, z, 0.05f, 0.85f, 0.05f)
        out += box(Paint.STEEL, 0.9f, 0.15f, -1.89f, 1.72f, 0.04f, 0.04f)
        out += box(Paint.STEEL, 0.9f, 0.15f, -2.56f, 1.72f, 0.04f, 0.04f)
        out += box(Paint.STEEL, 0.04f, 0.15f, -2.225f, 0.04f, 0.04f, 0.63f)
        out += box(Paint.STEEL, 1.76f, 0.15f, -2.225f, 0.04f, 0.04f, 0.63f)
        out += box(Paint.PEGBOARD, 0.9f, 1.5f, -2.565f, 1.8f, 1.0f, 0.02f)
        // tools on the pegboard
        out += box(Paint.STEEL, 1.05f, 1.55f, -2.548f, 0.025f, 0.22f, 0.012f, roll = 8f)
        out += box(Paint.STEEL, 1.15f, 1.52f, -2.548f, 0.025f, 0.26f, 0.012f, roll = -6f)
        out += box(Paint.RED, 1.32f, 1.6f, -2.545f, 0.03f, 0.12f, 0.025f)
        out += box(Paint.STEEL_DARK, 1.32f, 1.47f, -2.548f, 0.012f, 0.16f, 0.012f)
        out += box(Paint.YELLOW, 1.52f, 1.58f, -2.545f, 0.035f, 0.11f, 0.03f)
        out += box(Paint.STEEL_DARK, 1.52f, 1.45f, -2.548f, 0.01f, 0.16f, 0.01f)
        // rack: blue uprights (the front middle one belongs to P24), orange beams, galvanised shelves, far end panel
        for ((x, z) in listOf(-1.97f to -2.07f, -1.53f to -2.07f, -1.97f to -0.93f, -1.53f to -0.93f, -1.97f to -1.5f)) {
            out += box(Paint.RACK_BLUE, x, 1.0f, z, 0.06f, 2.0f, 0.06f)
        }
        for (y in listOf(0.08f, 0.63f, 1.18f, 1.73f, 1.98f)) for (x in listOf(-1.53f, -1.97f)) out += box(Paint.RACK_ORANGE, x, y, -1.5f, 0.05f, 0.07f, 1.2f)
        for (y in listOf(0.1f, 0.65f, 1.2f, 1.75f)) out += box(Paint.GALVANIZED, -1.75f, y + 0.0075f, -1.5f, 0.44f, 0.015f, 1.14f)
        out += box(Paint.GALVANIZED, -1.75f, 1.0f, -2.1f, 0.46f, 2.0f, 0.02f)
        // cable tray along the top of the front partition, with brackets
        out += box(Paint.STEEL, 0f, 2.38f, -2.56f, 4.0f, 0.06f, 0.12f)
        for (x in listOf(-1.8f, -0.6f, 0.6f, 1.8f)) out += box(Paint.STEEL_DARK, x, 2.29f, -2.585f, 0.03f, 0.14f, 0.03f)
        out
    }

    /**
     * A partition 2.2 m high: steel posts, top / mid / bottom rails, a light panel up to 1.6 m and a bar grid above.
     * [alongX]: the partition runs along x at z = [fixed]; otherwise along z at x = [fixed]. [gaps] are door openings.
     */
    private fun partition(out: MutableList<Part>, alongX: Boolean, fixed: Float, from: Float, to: Float, posts: List<Float>, gaps: List<Pair<Float, Float>>) {
        fun at(u: Float, y: Float): Vec3 = if (alongX) v(u, y, fixed) else v(fixed, y, u)
        fun slab(p: Paint, u0: Float, u1: Float, y: Float, h: Float, t: Float): Part {
            val c = at((u0 + u1) / 2f, y)
            val len = u1 - u0
            return if (alongX) box(p, c.x, c.y, c.z, len, h, t) else box(p, c.x, c.y, c.z, t, h, len)
        }
        for (u in posts) {
            val c = at(u, 1.1f)
            out += box(Paint.STEEL, c.x, c.y, c.z, 0.05f, 2.2f, 0.05f)
        }
        val segments = ArrayList<Pair<Float, Float>>()
        var start = from
        for ((g0, g1) in gaps.sortedBy { it.first }) {
            segments += start to g0
            start = g1
        }
        segments += start to to
        out += slab(Paint.STEEL, from, to, 2.18f, 0.04f, 0.05f)
        for ((u0, u1) in segments) {
            out += slab(Paint.STEEL, u0, u1, 1.6f, 0.04f, 0.05f)
            out += slab(Paint.STEEL, u0, u1, 0.04f, 0.06f, 0.05f)
            out += slab(Paint.PARTITION, u0, u1, 0.825f, 1.55f, 0.025f)
            out += slab(Paint.STEEL, u0, u1, 1.9f, 0.012f, 0.012f)
            var u = u0 + 0.1f
            while (u < u1 - 0.05f) {
                val c = at(u, 1.9f)
                out += if (alongX) box(Paint.STEEL, c.x, c.y, c.z, 0.012f, 0.56f, 0.012f) else box(Paint.STEEL, c.x, c.y, c.z, 0.012f, 0.56f, 0.012f)
                u += 0.1f
            }
        }
    }

    // =========================================================================================
    // checkpoints
    // =========================================================================================

    fun look(poiId: String): PoiLook = looks[poiId] ?: PoiLook(emptyList(), listOf(emptyList()))

    private val looks: Map<String, PoiLook> by lazy { buildLooks() }

    private fun buildLooks(): Map<String, PoiLook> {
        val m = LinkedHashMap<String, PoiLook>()
        fun put(id: String, safe: List<Part>, vararg hazards: List<Part>) {
            m[id] = PoiLook(safe, hazards.toList())
        }

        // P01 walkway floor: oil spill with a glossy patch
        put("P01", emptyList(), listOf(cyl(Paint.OIL, 0.2f, 0.004f, -1.2f, 0.5f, 0.004f, depth = 0.4f), cyl(Paint.OIL_GLOSS, 0.12f, 0.007f, -1.26f, 0.18f, 0.002f, depth = 0.1f)))

        // P02 under the bench: strip on the floor, cord across the walkway / strip mounted on the bench side
        put(
            "P02",
            listOf(
                box(Paint.STRIP, -0.005f, 0.6f, -2.2f, 0.03f, 0.06f, 0.3f),
                box(Paint.BLACK, 0.075f, 0.45f, -2.53f, 0.03f, 0.85f, 0.03f),
                bar(Paint.BLACK, v(-0.01f, 0.6f, -2.35f), v(0.06f, 0.75f, -2.52f), 0.012f),
            ),
            listOf(
                box(Paint.STRIP, 0.6f, 0.025f, -2.1f, 0.32f, 0.05f, 0.07f),
                box(Paint.BLACK, 0.52f, 0.06f, -2.1f, 0.04f, 0.03f, 0.04f),
                box(Paint.BLACK, 0.62f, 0.06f, -2.1f, 0.04f, 0.03f, 0.04f),
                bar(Paint.BLACK, v(0.45f, 0.008f, -2.08f), v(0.2f, 0.008f, -1.6f), 0.014f),
                bar(Paint.BLACK, v(0.2f, 0.008f, -1.6f), v(0.12f, 0.008f, -1.85f), 0.014f),
                bar(Paint.BLACK, v(0.76f, 0.02f, -2.12f), v(1.6f, 0.3f, -2.58f), 0.014f),
            ),
        )

        // P03 rack bottom bins: flush / one bin 30 cm into the aisle
        put(
            "P03",
            listOf(box(Paint.BIN_BLUE, -1.75f, 0.215f, -1.05f, 0.4f, 0.2f, 0.28f), box(Paint.BIN_BLUE, -1.75f, 0.215f, -1.38f, 0.4f, 0.2f, 0.28f)),
            listOf(box(Paint.BIN_BLUE, -1.45f, 0.215f, -1.12f, 0.4f, 0.2f, 0.28f), box(Paint.BIN_BLUE, -1.75f, 0.215f, -1.45f, 0.4f, 0.2f, 0.28f)),
        )

        // P04 pallet stack: outside the lane / shifted 0.3 m right, one corner in the forklift lane
        fun pallet(cx: Float): List<Part> =
            listOf(
                box(Paint.PALLET, cx, 0.135f, -1.2f, 1.0f, 0.03f, 1.2f),
                box(Paint.PALLET, cx - 0.45f, 0.06f, -1.2f, 0.1f, 0.12f, 1.2f),
                box(Paint.PALLET, cx, 0.06f, -1.2f, 0.1f, 0.12f, 1.2f),
                box(Paint.PALLET, cx + 0.45f, 0.06f, -1.2f, 0.1f, 0.12f, 1.2f),
                box(Paint.CARDBOARD, cx - 0.25f, 0.34f, -1.2f, 0.48f, 0.38f, 1.15f),
                box(Paint.CARDBOARD_DARK, cx + 0.25f, 0.34f, -1.2f, 0.48f, 0.38f, 1.15f),
                box(Paint.CARDBOARD_DARK, cx - 0.25f, 0.72f, -1.2f, 0.48f, 0.38f, 1.15f),
                box(Paint.CARDBOARD, cx + 0.25f, 0.72f, -1.2f, 0.48f, 0.38f, 1.15f),
                box(Paint.CARDBOARD, cx - 0.25f, 1.1f, -1.2f, 0.48f, 0.38f, 1.15f),
                box(Paint.CARDBOARD_DARK, cx + 0.25f, 1.1f, -1.2f, 0.48f, 0.38f, 1.15f),
            )
        put("P04", pallet(1.1f), pallet(1.4f))

        // P05 mop bucket by the door: wet-floor A-frame sign / puddle and no sign
        val bucket =
            listOf(
                cyl(Paint.YELLOW, 0.3f, 0.2f, 1.5f, 0.4f, 0.4f),
                box(Paint.YELLOW, 0.3f, 0.45f, 1.45f, 0.26f, 0.12f, 0.18f),
                cyl(Paint.STEEL, 0.4f, 0.62f, 1.55f, 0.03f, 1.15f, roll = -10f),
                box(Paint.RAG, 0.32f, 0.12f, 1.55f, 0.16f, 0.06f, 0.16f),
            )
        put(
            "P05",
            bucket + listOf(box(Paint.YELLOW, 0.75f, 0.3f, 1.4f, 0.3f, 0.6f, 0.02f, yaw = 180f, pitch = -12f), sign(SignKind.WET, 0.75f, 0.32f, 1.385f, 0.26f, 0.42f, yaw = 180f, pitch = -12f)),
            bucket + listOf(cyl(Paint.WATER, 0.05f, 0.004f, 1.3f, 0.8f, 0.004f, depth = 0.55f)),
        )

        // P06 waste bin: lidded metal / open plastic with oily rags
        put(
            "P06",
            listOf(cyl(Paint.GALVANIZED, -0.6f, 0.3f, -2.3f, 0.35f, 0.6f), cyl(Paint.STEEL, -0.6f, 0.615f, -2.3f, 0.37f, 0.03f), cyl(Paint.STEEL_DARK, -0.6f, 0.645f, -2.3f, 0.06f, 0.03f)),
            listOf(
                cyl(Paint.BIN_BLUE, -0.6f, 0.275f, -2.3f, 0.35f, 0.55f),
                ball(Paint.RAG, -0.64f, 0.57f, -2.28f, 0.16f, 0.1f, 0.14f),
                ball(Paint.RAG, -0.54f, 0.58f, -2.33f, 0.13f, 0.09f, 0.12f),
                ball(Paint.OIL_GLOSS, -0.6f, 0.62f, -2.25f, 0.1f, 0.07f, 0.09f),
            ),
        )

        // P07 gas cylinder: chained with a cap / no chain, no cap, leaning 6 degrees
        val stand =
            listOf(
                box(Paint.STEEL, 1.25f, 0.015f, -0.33f, 0.36f, 0.03f, 0.36f),
                box(Paint.STEEL, 1.25f, 0.6f, -0.48f, 0.04f, 1.2f, 0.04f),
                box(Paint.STEEL, 1.25f, 1.0f, -0.43f, 0.3f, 0.03f, 0.03f),
            )
        val bottle =
            listOf(cyl(Paint.CYLINDER, 1.25f, 0.65f, -0.3f, 0.23f, 1.3f), ball(Paint.CYLINDER, 1.25f, 1.3f, -0.3f, 0.23f, 0.16f, 0.23f), cyl(Paint.BRASS, 1.25f, 1.42f, -0.3f, 0.05f, 0.1f))
        put(
            "P07",
            stand + bottle + listOf(cyl(Paint.STEEL_DARK, 1.25f, 1.48f, -0.3f, 0.12f, 0.12f), box(Paint.CHAIN, 1.25f, 1.0f, -0.18f, 0.27f, 0.025f, 0.025f), box(Paint.CHAIN, 1.12f, 1.0f, -0.3f, 0.025f, 0.025f, 0.26f), box(Paint.CHAIN, 1.38f, 1.0f, -0.3f, 0.025f, 0.025f, 0.26f)),
            stand + lean(bottle, v(1.25f, 0f, -0.3f), 6f),
        )

        // P08 angle grinder: with / without the wheel guard
        val grinder =
            listOf(
                cyl(Paint.GRINDER, 0.36f, 0.945f, -2.25f, 0.07f, 0.24f, roll = 90f),
                box(Paint.STEEL_DARK, 0.2f, 0.945f, -2.25f, 0.08f, 0.07f, 0.08f),
                cyl(Paint.WHEEL, 0.2f, 0.912f, -2.25f, 0.125f, 0.008f),
            )
        put("P08", grinder + listOf(cyl(Paint.STEEL, 0.2f, 0.928f, -2.27f, 0.145f, 0.02f)), grinder)

        // P09 bottle: labelled cleaner / unlabelled liquid
        put(
            "P09",
            listOf(
                cyl(Paint.AMBER, 0.6f, 1.0f, -2.45f, 0.08f, 0.2f),
                cyl(Paint.WHITE, 0.6f, 1.12f, -2.45f, 0.035f, 0.04f),
                cyl(Paint.WHITE, 0.6f, 0.99f, -2.45f, 0.084f, 0.09f),
                box(Paint.RED, 0.6f, 0.995f, -2.407f, 0.03f, 0.03f, 0.004f, roll = 45f),
            ),
            listOf(cyl(Paint.CLEAR_LIQUID, 0.6f, 1.0f, -2.45f, 0.08f, 0.2f), cyl(Paint.WHITE, 0.6f, 1.12f, -2.45f, 0.035f, 0.04f)),
        )

        // P10 utility knife: in its holder / blade out over the bench edge
        put(
            "P10",
            listOf(box(Paint.BEECH_DARK, 0.6f, 0.95f, -2.12f, 0.07f, 0.1f, 0.07f), box(Paint.YELLOW, 0.6f, 1.04f, -2.12f, 0.025f, 0.1f, 0.018f)),
            listOf(box(Paint.YELLOW, 0.6f, 0.909f, -1.95f, 0.025f, 0.018f, 0.13f), box(Paint.BLADE, 0.6f, 0.906f, -1.85f, 0.018f, 0.004f, 0.075f)),
        )

        // P11 bench outlet: one strip in the wall socket / strips chained
        val outlet = listOf(box(Paint.WHITE, 0.03f, 1.1f, -2.548f, 0.08f, 0.08f, 0.02f), box(Paint.STRIP, 0.06f, 0.92f, -2.45f, 0.3f, 0.04f, 0.06f), bar(Paint.BLACK, v(-0.08f, 0.93f, -2.45f), v(0.02f, 1.08f, -2.54f), 0.012f))
        put(
            "P11",
            outlet + listOf(box(Paint.BLACK, 0.1f, 0.95f, -2.45f, 0.03f, 0.03f, 0.03f)),
            outlet +
                listOf(
                    box(Paint.STRIP, 0.14f, 0.92f, -2.32f, 0.3f, 0.04f, 0.06f, yaw = 18f),
                    bar(Paint.BLACK, v(0.16f, 0.94f, -2.45f), v(0.0f, 0.93f, -2.36f), 0.012f),
                    box(Paint.BLACK, 0.0f, 0.95f, -2.45f, 0.03f, 0.03f, 0.03f),
                    box(Paint.BLACK, 0.1f, 0.95f, -2.45f, 0.03f, 0.03f, 0.03f),
                    box(Paint.BLACK, 0.18f, 0.95f, -2.45f, 0.03f, 0.03f, 0.03f),
                    box(Paint.BLACK, 0.08f, 0.95f, -2.34f, 0.03f, 0.03f, 0.03f),
                    box(Paint.BLACK, 0.18f, 0.95f, -2.3f, 0.03f, 0.03f, 0.03f),
                ),
        )

        // P12 extinguisher on its hook: clear / boxes in front (H12) / gauge red and tag expired (H12b)
        fun extinguisher(gauge: Paint, tag: Paint): List<Part> =
            listOf(
                box(Paint.STEEL, 1.2f, 1.26f, 1.965f, 0.06f, 0.08f, 0.05f),
                cyl(Paint.RED, 1.2f, 0.96f, 1.9f, 0.16f, 0.48f),
                cyl(Paint.STEEL_DARK, 1.2f, 1.24f, 1.9f, 0.06f, 0.08f),
                box(Paint.BLACK, 1.2f, 1.29f, 1.9f, 0.13f, 0.02f, 0.03f),
                bar(Paint.BLACK, v(1.26f, 1.24f, 1.88f), v(1.29f, 0.84f, 1.85f), 0.02f),
                cyl(gauge, 1.2f, 1.2f, 1.82f, 0.05f, 0.012f, pitch = 90f),
                box(tag, 1.13f, 1.14f, 1.818f, 0.05f, 0.08f, 0.003f),
                sign(SignKind.EXTINGUISHER, 1.2f, 1.56f, 1.975f, 0.2f, 0.2f, yaw = 180f),
            )
        put(
            "P12",
            extinguisher(Paint.GAUGE_GREEN, Paint.WHITE),
            extinguisher(Paint.GAUGE_GREEN, Paint.WHITE) + listOf(box(Paint.CARDBOARD, 1.2f, 0.2f, 1.66f, 0.38f, 0.4f, 0.3f), box(Paint.CARDBOARD_DARK, 1.19f, 0.58f, 1.67f, 0.34f, 0.36f, 0.28f, yaw = 8f)),
            extinguisher(Paint.GAUGE_RED, Paint.TAG_RED),
        )

        // P13 electrical panel: shut with a shock sign / door open, live wires
        val cabinet = listOf(box(Paint.GALVANIZED, -1.92f, 1.4f, -0.2f, 0.15f, 0.6f, 0.4f))
        put(
            "P13",
            cabinet + listOf(box(Paint.DOOR, -1.838f, 1.4f, -0.2f, 0.015f, 0.58f, 0.38f), sign(SignKind.ELECTRIC, -1.828f, 1.5f, -0.2f, 0.16f, 0.14f, yaw = 90f)),
            cabinet +
                listOf(
                    box(Paint.DOOR, -1.65f, 1.4f, 0.01f, 0.38f, 0.58f, 0.015f),
                    box(Paint.STEEL_DARK, -1.842f, 1.4f, -0.2f, 0.004f, 0.52f, 0.34f),
                    box(Paint.BLACK, -1.836f, 1.52f, -0.2f, 0.01f, 0.08f, 0.3f),
                    bar(Paint.WIRE_RED, v(-1.84f, 1.36f, -0.26f), v(-1.76f, 1.12f, -0.2f), 0.014f),
                    bar(Paint.WIRE_BLUE, v(-1.84f, 1.36f, -0.2f), v(-1.74f, 1.1f, -0.14f), 0.014f),
                    bar(Paint.WIRE_YG, v(-1.84f, 1.36f, -0.14f), v(-1.77f, 1.14f, -0.08f), 0.014f),
                    ball(Paint.COPPER, -1.76f, 1.11f, -0.2f, 0.025f),
                    ball(Paint.COPPER, -1.74f, 1.09f, -0.14f, 0.025f),
                ),
        )

        // P14 emergency exit: clear / a pallet and boxes in front of the door
        put(
            "P14",
            emptyList(),
            listOf(
                box(Paint.PALLET, -0.75f, 0.07f, 1.62f, 0.8f, 0.14f, 0.55f),
                box(Paint.CARDBOARD, -0.88f, 0.365f, 1.64f, 0.48f, 0.45f, 0.4f),
                box(Paint.CARDBOARD_DARK, -0.62f, 0.76f, 1.66f, 0.4f, 0.34f, 0.34f, yaw = 12f),
            ),
        )

        // P15 rack middle tier: heavy drum below, light boxes above / the other way round
        put(
            "P15",
            listOf(cyl(Paint.DRUM_BLUE, -1.75f, 0.89f, -1.68f, 0.32f, 0.45f), box(Paint.CARDBOARD, -1.75f, 1.34f, -1.75f, 0.35f, 0.25f, 0.24f), box(Paint.CARDBOARD_DARK, -1.75f, 1.31f, -1.47f, 0.32f, 0.19f, 0.24f)),
            listOf(cyl(Paint.DRUM_BLUE, -1.75f, 1.44f, -1.68f, 0.32f, 0.45f), box(Paint.CARDBOARD_DARK, -1.75f, 0.74f, -1.72f, 0.36f, 0.15f, 0.26f, roll = 4f), box(Paint.CARDBOARD, -1.75f, 0.73f, -1.46f, 0.32f, 0.13f, 0.24f, roll = -3f)),
        )

        // P16 solvent drum: lid on / open
        val drum = listOf(cyl(Paint.DRUM_RED, -1.75f, 0.845f, -1.06f, 0.28f, 0.36f), box(Paint.YELLOW, -1.607f, 0.86f, -1.06f, 0.004f, 0.1f, 0.1f))
        put("P16", drum + listOf(cyl(Paint.STEEL, -1.75f, 1.035f, -1.06f, 0.29f, 0.02f)), drum + listOf(cyl(Paint.BLACK, -1.75f, 1.027f, -1.06f, 0.25f, 0.004f)))

        // P17 ladder: folded upright, all feet / one half leaning on the partition, a foot missing
        val safeLadder = ArrayList<Part>()
        for (x in listOf(-1.93f, -1.87f)) for (z in listOf(0.1f, 0.5f)) {
            safeLadder += box(Paint.ALU, x, 0.9f, z, 0.03f, 1.8f, 0.05f)
            safeLadder += box(Paint.RUBBER, x, 0.015f, z, 0.04f, 0.03f, 0.07f)
        }
        for (y in listOf(0.35f, 0.7f, 1.05f, 1.4f)) safeLadder += box(Paint.ALU, -1.87f, y, 0.3f, 0.03f, 0.03f, 0.4f)
        val leanLadder = ArrayList<Part>()
        val base = -1.4f
        val top = -1.96f
        for (z in listOf(0.1f, 0.5f)) leanLadder += bar(Paint.ALU, v(base, 0.03f, z), v(top, 1.75f, z), 0.04f, depth = 0.05f)
        for (f in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            val x = base + (top - base) * f
            leanLadder += box(Paint.ALU, x, 0.03f + 1.72f * f, 0.3f, 0.03f, 0.03f, 0.4f)
        }
        leanLadder += box(Paint.RUBBER, base, 0.015f, 0.1f, 0.06f, 0.03f, 0.07f)
        put("P17", safeLadder, leanLadder)

        // P18 first-aid kit: closed, green cross / open and empty, expired tag
        put(
            "P18",
            listOf(
                box(Paint.WHITE, 0.85f, 1.4f, 1.93f, 0.3f, 0.3f, 0.12f),
                box(Paint.GREEN, 0.85f, 1.4f, 1.868f, 0.13f, 0.04f, 0.004f),
                box(Paint.GREEN, 0.85f, 1.4f, 1.868f, 0.04f, 0.13f, 0.004f),
            ),
            listOf(
                box(Paint.WHITE, 0.85f, 1.4f, 1.93f, 0.3f, 0.3f, 0.12f),
                box(Paint.STEEL_DARK, 0.85f, 1.4f, 1.869f, 0.26f, 0.26f, 0.004f),
                box(Paint.WHITE, 0.7f, 1.4f, 1.72f, 0.015f, 0.3f, 0.3f),
                box(Paint.GREEN, 0.692f, 1.4f, 1.72f, 0.004f, 0.04f, 0.13f),
                box(Paint.GREEN, 0.692f, 1.4f, 1.72f, 0.004f, 0.13f, 0.04f),
                box(Paint.TAG_RED, 0.95f, 1.6f, 1.88f, 0.08f, 0.05f, 0.004f),
            ),
        )

        // P19 goggle hook: goggles and a "wear goggles" sign / empty hook, no sign
        val hook = listOf(box(Paint.STEEL, 0.28f, 1.92f, -2.525f, 0.012f, 0.012f, 0.07f))
        put(
            "P19",
            hook + listOf(box(Paint.BLACK, 0.28f, 1.86f, -2.52f, 0.17f, 0.055f, 0.03f), box(Paint.LENS, 0.28f, 1.86f, -2.503f, 0.15f, 0.04f, 0.004f), sign(SignKind.GOGGLES, 0.08f, 1.88f, -2.553f, 0.15f, 0.2f)),
            hook,
        )

        // P20 rack top tier: boxes flush with an edge guard / box overhanging 40 cm
        put(
            "P20",
            listOf(box(Paint.CARDBOARD, -1.75f, 1.965f, -1.9f, 0.4f, 0.4f, 0.35f), box(Paint.CARDBOARD_DARK, -1.75f, 2.265f, -1.9f, 0.35f, 0.2f, 0.3f), box(Paint.GALVANIZED, -1.52f, 1.81f, -1.9f, 0.02f, 0.09f, 0.42f)),
            listOf(box(Paint.CARDBOARD, -1.43f, 1.965f, -1.9f, 0.6f, 0.4f, 0.35f), box(Paint.CARDBOARD_DARK, -1.6f, 2.265f, -1.9f, 0.35f, 0.2f, 0.3f)),
        )

        // P21 rack top stacking: two level boxes / a heavy case on a crushed box, tilting
        put(
            "P21",
            listOf(box(Paint.CARDBOARD, -1.75f, 1.89f, -1.1f, 0.38f, 0.25f, 0.38f), box(Paint.CARDBOARD_DARK, -1.75f, 2.125f, -1.1f, 0.3f, 0.22f, 0.3f)),
            listOf(box(Paint.CARDBOARD_DARK, -1.75f, 1.845f, -1.1f, 0.4f, 0.16f, 0.38f, roll = 5f), box(Paint.STEEL_DARK, -1.71f, 2.08f, -1.08f, 0.36f, 0.3f, 0.34f, roll = -10f)),
        )

        // P22 light fixture on the partition: cover intact / cover broken, tube hanging
        val housing = listOf(box(Paint.GALVANIZED, 0.6f, 2.235f, -2.52f, 1.2f, 0.07f, 0.15f))
        put(
            "P22",
            housing + listOf(box(Paint.DIFFUSER, 0.6f, 2.19f, -2.52f, 1.18f, 0.03f, 0.13f)),
            housing + listOf(box(Paint.DIFFUSER, 0.88f, 2.19f, -2.52f, 0.6f, 0.03f, 0.13f), box(Paint.DIFFUSER, 0.45f, 2.12f, -2.47f, 0.25f, 0.02f, 0.12f, roll = 25f), bar(Paint.TUBE, v(1.15f, 2.19f, -2.52f), v(0.1f, 1.91f, -2.48f), 0.026f)),
        )

        // P23 cable tray: cover intact / no cover, bare splice hanging
        put(
            "P23",
            listOf(box(Paint.GALVANIZED, -1.2f, 2.416f, -2.56f, 0.5f, 0.012f, 0.13f)),
            listOf(
                bar(Paint.BLACK, v(-1.26f, 2.36f, -2.53f), v(-1.3f, 2.12f, -2.49f), 0.014f),
                bar(Paint.BLACK, v(-1.14f, 2.36f, -2.53f), v(-1.22f, 2.15f, -2.49f), 0.014f),
                ball(Paint.COPPER, -1.27f, 2.12f, -2.49f, 0.04f),
                ball(Paint.COPPER, -1.21f, 2.14f, -2.49f, 0.03f),
            ),
        )

        // P24 rack front middle upright and load sign: straight with a sign / bent, no sign
        put(
            "P24",
            listOf(box(Paint.RACK_BLUE, -1.53f, 1.0f, -1.5f, 0.06f, 2.0f, 0.06f), sign(SignKind.LOAD, -1.497f, 1.88f, -1.5f, 0.26f, 0.12f, yaw = 90f)),
            listOf(box(Paint.RACK_BLUE, -1.53f, 0.8f, -1.5f, 0.06f, 1.6f, 0.06f), bar(Paint.RACK_BLUE, v(-1.53f, 1.6f, -1.5f), v(-1.44f, 1.8f, -1.5f), 0.06f), bar(Paint.RACK_BLUE, v(-1.44f, 1.8f, -1.5f), v(-1.53f, 2.0f, -1.5f), 0.06f)),
        )

        // P25 exit sign light above the door: lit / dark
        val lamp = listOf(box(Paint.GALVANIZED, -0.75f, 2.22f, 1.96f, 0.36f, 0.14f, 0.05f))
        put("P25", lamp + listOf(sign(SignKind.EXIT_LIT, -0.75f, 2.22f, 1.933f, 0.33f, 0.12f, yaw = 180f)), lamp + listOf(sign(SignKind.EXIT_DARK, -0.75f, 2.22f, 1.933f, 0.33f, 0.12f, yaw = 180f)))

        // P26 bench right side (walk mode): vice and tools / an open solvent can by the vice
        val vice = listOf(box(Paint.VISE, 1.25f, 0.95f, -2.2f, 0.14f, 0.1f, 0.16f), box(Paint.VISE, 1.25f, 1.02f, -2.12f, 0.16f, 0.05f, 0.03f), box(Paint.STEEL, 1.55f, 0.906f, -2.35f, 0.22f, 0.012f, 0.04f, yaw = 20f))
        put("P26", vice, vice + listOf(cyl(Paint.DRUM_RED, 1.45f, 0.98f, -2.3f, 0.12f, 0.16f), cyl(Paint.BLACK, 1.45f, 1.061f, -2.3f, 0.1f, 0.003f)))

        // P27 corner behind the rack (walk mode): hose cabinet visible / blocked by empty pallets
        val hose = listOf(box(Paint.CABINET_RED, -1.88f, 1.3f, -2.35f, 0.24f, 1.0f, 0.45f), box(Paint.DARK_GLASS, -1.758f, 1.32f, -2.35f, 0.004f, 0.8f, 0.36f))
        put("P27", hose, hose + listOf(box(Paint.PALLET, -1.69f, 0.55f, -2.35f, 0.13f, 1.1f, 0.45f, roll = -4f), box(Paint.PALLET, -1.56f, 0.55f, -2.35f, 0.13f, 1.1f, 0.45f, roll = -7f)))

        // P28 lane corner mirror: convex mirror / bracket only (post and forklift sign in both)
        val post = listOf(cyl(Paint.STEEL, 1.97f, 1.1f, 1.0f, 0.05f, 2.2f), bar(Paint.STEEL, v(1.97f, 2.1f, 1.0f), v(1.86f, 2.1f, 0.93f), 0.025f), sign(SignKind.FORKLIFT, 1.94f, 1.5f, 1.0f, 0.2f, 0.2f, yaw = -90f))
        put("P28", post + listOf(cyl(Paint.BLACK, 1.84f, 2.06f, 0.92f, 0.4f, 0.02f, yaw = 45f, pitch = 90f), ball(Paint.MIRROR, 1.835f, 2.06f, 0.915f, 0.37f, 0.37f, 0.12f, yaw = 45f)), post + listOf(box(Paint.BLACK, 1.86f, 2.1f, 0.93f, 0.05f, 0.05f, 0.05f)))
        return m
    }

    /** [parts] leaned by [rollDeg] about the Z axis through [pivot] (parts must be unrotated). */
    private fun lean(parts: List<Part>, pivot: Vec3, rollDeg: Float): List<Part> {
        val q = Quat4.axisAngle(Vec3(0f, 0f, 1f), Math.toRadians(rollDeg.toDouble()).toFloat())
        return parts.map { it.copy(center = pivot + q.rotate(it.center - pivot), rotDeg = Vec3(0f, 0f, rollDeg)) }
    }

    /** Smallest horizontal distance from the origin to any corner of [parts] below [belowY] (clearance tests). */
    fun nearest(parts: List<Part>, belowY: Float = 2.0f): Float {
        var best = Float.MAX_VALUE
        for (p in parts) {
            if (p.shape == Shape.SIGN && p.sign == SignKind.FLOOR_PLATE) continue
            for (c in p.corners()) if (c.y < belowY) best = minOf(best, sqrt(c.x * c.x + c.z * c.z))
        }
        return best
    }
}
