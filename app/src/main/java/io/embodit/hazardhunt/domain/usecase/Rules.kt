package io.embodit.hazardhunt.domain.usecase

import io.embodit.hazardhunt.domain.model.Box3
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.domain.model.Level
import io.embodit.hazardhunt.domain.model.Poi
import io.embodit.hazardhunt.domain.model.Quat4
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.model.Zone
import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One round's layout: which checkpoints show their hazard version, and which variant (P12: H12 or H12b). */
data class RoundLayout(val seed: Long, val walk: Boolean, val hazards: Map<String, Int>) {
    val total: Int
        get() = hazards.size

    /** Most flags at once (design `rules.mark`: hazards + 2). */
    val cap: Int
        get() = total + 2

    fun isHazard(poiId: String): Boolean = hazards.containsKey(poiId)

    fun variantOf(poiId: String): Int = hazards[poiId] ?: 0

    /** Checkpoints that exist this round: the 26 regular ones, plus P26 / P27 in walk mode. */
    val pois: List<Poi>
        get() = if (walk) Catalog.all else Catalog.regular
}

/**
 * The round draw (design `pois.roundRules`): 8 of the 26 regular checkpoints with every zone at least once, at least
 * two low and two high, at most two of one category; P12 picks one of its two variants. Walk mode adds P26 and P27
 * (10 hazards). The draw only depends on the 64-bit seed, so "replay last round" rebuilds the same bay.
 */
object RoundRules {
    const val STANDARD = 8

    fun draw(seed: Long, walk: Boolean): RoundLayout {
        val rng = SplittableRandom(seed)
        val pool = Catalog.regular
        var picked: List<Poi> = emptyList()
        for (attempt in 0 until 10_000) {
            val shuffled = pool.toMutableList()
            for (i in shuffled.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = shuffled[i]
                shuffled[i] = shuffled[j]
                shuffled[j] = t
            }
            val candidate = shuffled.take(STANDARD)
            if (valid(candidate)) {
                picked = candidate
                break
            }
        }
        if (picked.isEmpty()) picked = FALLBACK.mapNotNull { Catalog.get(it) }
        val hazards = LinkedHashMap<String, Int>()
        for (p in picked.sortedBy { it.index }) hazards[p.id] = if (p.hazards.size > 1) rng.nextInt(p.hazards.size) else 0
        if (walk) for (p in Catalog.challenge) hazards[p.id] = 0
        return RoundLayout(seed, walk, hazards)
    }

    fun valid(pick: List<Poi>): Boolean {
        if (pick.size != STANDARD) return false
        if (Zone.entries.any { z -> pick.none { it.zone == z } }) return false
        if (pick.count { it.level == Level.LOW } < 2) return false
        if (pick.count { it.level == Level.HIGH } < 2) return false
        val perCategory = pick.groupingBy { it.hazard(0).category }.eachCount()
        return perCategory.values.all { it <= 2 }
    }

    /** A known-valid layout, used only if the sampler somehow fails. */
    private val FALLBACK = listOf("P01", "P04", "P07", "P12", "P13", "P15", "P20", "P22")

    /** The four fix indices (0 = the right one) in this round's order for [poiId]: shuffled per round (design `rules.classify`). */
    fun fixOrder(seed: Long, poiId: String): List<Int> {
        val rng = SplittableRandom(seed xor (poiId.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()))
        val order = mutableListOf(0, 1, 2, 3)
        for (i in 3 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = order[i]
            order[i] = order[j]
            order[j] = t
        }
        return order
    }

    /** A fresh seed from the clock (never 0, so 0 can mean "none"). */
    fun newSeed(nowNanos: Long): Long = (nowNanos * 6364136223846793005L + 1442695040888963407L).let { if (it == 0L) 1L else it }
}

/** A flag the user planted: the checkpoint, the chosen category and the chosen fix (canonical index, 0 = right). */
data class Mark(val poiId: String, val category: Category, val fixIndex: Int)

/** Debrief colour of one row / tag (design `tagsInWorld.verdictTag`). */
enum class Verdict { GREEN, YELLOW, RED, GREY }

/** One hazard's result (or a false alarm when [hazard] is false). */
data class Outcome(
    val poiId: String,
    val hazard: Boolean,
    val variant: Int,
    val found: Boolean,
    val typeOk: Boolean,
    val fixOk: Boolean,
    val mark: Mark?,
) {
    val points: Int
        get() = (if (found) 1 else 0) + (if (typeOk) 1 else 0) + (if (fixOk) 1 else 0)

    val verdict: Verdict
        get() =
            when {
                !hazard -> Verdict.GREY
                !found -> Verdict.RED
                points == 3 -> Verdict.GREEN
                else -> Verdict.YELLOW
            }
}

/** Grade of a round (design `pois.scoring`). */
enum class Grade { EXCELLENT, GOOD, AGAIN }

data class Debrief(
    val outcomes: List<Outcome>,
    val order: List<String>,
    val score: Int,
    val max: Int,
    val found: Int,
    val typed: Int,
    val fixed: Int,
    val falseAlarms: Int,
    val grade: Grade,
) {
    val hazardOutcomes: List<Outcome>
        get() = order.mapNotNull { id -> outcomes.firstOrNull { it.poiId == id } }

    val missed: List<String>
        get() = hazardOutcomes.filter { it.verdict == Verdict.RED }.map { it.poiId }

    fun outcome(poiId: String): Outcome? = outcomes.firstOrNull { it.poiId == poiId }
}

/**
 * Scoring (design `pois.scoring`): each hazard = found 1 + category 1 + fix 1; a flag on a safe checkpoint is a
 * false alarm (grey, no points). The list order puts the missed (red) first, then the rest, each group clockwise by
 * bearing from the bay front (front -> right -> back -> left).
 */
object Scoring {
    fun debrief(layout: RoundLayout, marks: List<Mark>): Debrief {
        val outcomes = ArrayList<Outcome>()
        for ((id, variant) in layout.hazards) {
            val poi = Catalog.get(id) ?: continue
            val spec = poi.hazard(variant)
            val m = marks.firstOrNull { it.poiId == id }
            outcomes += Outcome(id, true, variant, m != null, m != null && m.category == spec.category, m != null && m.fixIndex == 0, m)
        }
        for (m in marks) if (!layout.isHazard(m.poiId)) outcomes += Outcome(m.poiId, false, 0, true, false, false, m)
        val hazards = outcomes.filter { it.hazard }
        val red = hazards.filter { it.verdict == Verdict.RED }.sortedBy { bearing(it.poiId) }
        val rest = hazards.filter { it.verdict != Verdict.RED }.sortedBy { bearing(it.poiId) }
        val score = hazards.sumOf { it.points }
        val max = layout.total * 3
        return Debrief(
            outcomes = outcomes,
            order = (red + rest).map { it.poiId },
            score = score,
            max = max,
            found = hazards.count { it.found },
            typed = hazards.count { it.typeOk },
            fixed = hazards.count { it.fixOk },
            falseAlarms = outcomes.count { !it.hazard },
            grade = grade(score, layout.walk),
        )
    }

    fun grade(score: Int, walk: Boolean): Grade =
        when {
            score >= (if (walk) 26 else 21) -> Grade.EXCELLENT
            score >= (if (walk) 20 else 16) -> Grade.GOOD
            else -> Grade.AGAIN
        }

    /** Clockwise bearing of a checkpoint from the bay front, radians 0 .. 2 pi (front 0, right pi/2, back pi, left 3pi/2). */
    fun bearing(poiId: String): Float {
        val p = Catalog.get(poiId)?.pos ?: return 0f
        var a = atan2(p.x, -p.z)
        if (a < 0f) a += (2 * PI).toFloat()
        return a
    }

    /**
     * The QA preset (design `qaHooks` result=preset): on [layout], six hazards found, all six with the right category,
     * five with the right fix, plus two false alarms = 17 / 24 "good".
     */
    fun presetMarks(layout: RoundLayout): List<Mark> {
        val ids = layout.hazards.keys.toList().take(6)
        val marks = ArrayList<Mark>()
        ids.forEachIndexed { i, id ->
            val spec = Catalog.get(id)!!.hazard(layout.variantOf(id))
            marks += Mark(id, spec.category, if (i == 5) 1 else 0)
        }
        val safe = layout.pois.filter { !layout.isHazard(it.id) }.take(2)
        for (p in safe) marks += Mark(p.id, p.hazard(0).category, 2)
        return marks
    }
}

/** Where the bay stands in Stage space: origin on the floor (y = [floorY]) and its heading (yaw about +Y). */
data class BayPose(val originX: Float, val originZ: Float, val yaw: Float, val floorY: Float) {
    val rotation: Quat4
        get() = Quat4.axisAngle(Vec3.UP, yaw)

    val origin: Vec3
        get() = Vec3(originX, floorY, originZ)

    fun toStage(local: Vec3): Vec3 = origin + rotation.rotate(local)

    fun toLocal(stage: Vec3): Vec3 = rotation.conjugate().rotate(stage - origin)

    fun dirToLocal(dir: Vec3): Vec3 = rotation.conjugate().rotate(dir)

    /** The bay turned by [delta] radians about the vertical through the Stage point ([pivotX], [pivotZ]). */
    fun turned(delta: Float, pivotX: Float, pivotZ: Float): BayPose {
        val dx = originX - pivotX
        val dz = originZ - pivotZ
        val c = cos(delta)
        val s = sin(delta)
        // rotation about +Y: x' = x cos + z sin, z' = -x sin + z cos
        return copy(originX = pivotX + dx * c + dz * s, originZ = pivotZ - dx * s + dz * c, yaw = wrap(yaw + delta))
    }

    companion object {
        /** Placement (design `rules.place`): origin = head floor point, -Z = the head's horizontal heading. */
        fun facing(head: Vec3, forward: Vec3, floorY: Float): BayPose = BayPose(head.x, head.z, yawOf(forward), floorY)
    }
}

/** Yaw (about +Y) that turns -Z onto the horizontal part of [dir]. */
fun yawOf(dir: Vec3): Float = atan2(-dir.x, -dir.z)

fun wrap(a: Float): Float {
    var r = a
    val twoPi = (2 * PI).toFloat()
    while (r > PI) r -= twoPi
    while (r < -PI) r += twoPi
    return r
}

/**
 * The turn that brings [poiLocal] (bay coordinates) straight ahead of the viewer at [head] looking along [forward]:
 * the bay turns about the viewer's floor point (design `rules.bringToFront`). Returns the angle for [BayPose.turned].
 */
fun bringToFrontDelta(bay: BayPose, poiLocal: Vec3, head: Vec3, forward: Vec3): Float {
    val target = bay.toStage(poiLocal)
    val want = yawOf(forward)
    val now = yawOf(Vec3(target.x - head.x, 0f, target.z - head.z))
    return wrap(want - now)
}

/**
 * Snap turn on the stick (design `rules.turn`): one 45 degree step when |x| passes 0.6, the next only after the stick
 * came back under 0.3. Left (x < 0) turns the bay counter-clockwise seen from above (+delta), right clockwise.
 */
class StickStepper(private val stepRad: Float = STEP_RAD) {
    private var armed = true

    /** The turn to apply this frame (radians), or 0. */
    fun update(x: Float): Float {
        if (abs(x) < REARM) armed = true
        if (armed && abs(x) > FIRE) {
            armed = false
            return if (x < 0f) stepRad else -stepRad
        }
        return 0f
    }

    fun reset() {
        armed = true
    }

    companion object {
        const val FIRE = 0.6f
        const val REARM = 0.3f
        val STEP_RAD = Math.toRadians(45.0).toFloat()
    }
}

/** Distance along a ray (origin, unit direction) to where it enters [box], or null (slab test). */
fun rayBox(origin: Vec3, dir: Vec3, box: Box3, maxDistance: Float = 20f): Float? {
    var tMin = 0f
    var tMax = maxDistance
    val mn = box.min
    val mx = box.max
    val o = floatArrayOf(origin.x, origin.y, origin.z)
    val d = floatArrayOf(dir.x, dir.y, dir.z)
    val lo = floatArrayOf(mn.x, mn.y, mn.z)
    val hi = floatArrayOf(mx.x, mx.y, mx.z)
    for (i in 0..2) {
        if (abs(d[i]) < 1e-8f) {
            if (o[i] < lo[i] || o[i] > hi[i]) return null
        } else {
            var t1 = (lo[i] - o[i]) / d[i]
            var t2 = (hi[i] - o[i]) / d[i]
            if (t1 > t2) {
                val t = t1
                t1 = t2
                t2 = t
            }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return null
        }
    }
    return tMin
}

/** Hint line of the inspect panel, first that applies (design `rules.hintPriority`). */
enum class Hint { WALK, FIRST, TURN, SUBMIT }

fun hintFor(walk: Boolean, marked: Int, total: Int): Hint =
    when {
        walk -> Hint.WALK
        marked == 0 -> Hint.FIRST
        marked < total -> Hint.TURN
        else -> Hint.SUBMIT
    }
