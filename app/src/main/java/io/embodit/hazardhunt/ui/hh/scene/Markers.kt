package io.embodit.hazardhunt.ui.hh.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.Material
import io.embodit.hazardhunt.domain.model.Box3
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Paint
import io.embodit.hazardhunt.domain.model.Quat4
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.usecase.Outcome
import io.embodit.hazardhunt.domain.usecase.Verdict
import io.embodit.hazardhunt.ui.theme.HhColors
import kotlin.math.atan2
import kotlin.math.max

/** Where a checkpoint's flag stands and its tags float (bay coordinates; design `tagsInWorld`). */
internal object MarkSpots {
    /** Floor checkpoints carry their flag at the decal centre (design `tagsInWorld.flag`). */
    fun flagBase(poiId: String): Vec3 {
        val p = Catalog.get(poiId) ?: return Vec3.ZERO
        return if (isFloor(poiId)) Vec3(p.box.center.x, 0f, p.box.center.z) else p.box.top
    }

    /** Tags float 6 cm above the pointer box (design `tagsInWorld.hoverTag`). */
    fun tagAnchor(poiId: String): Vec3 {
        val p = Catalog.get(poiId) ?: return Vec3.ZERO
        val top = if (isFloor(poiId)) Vec3(p.box.center.x, FLAG_H + 0.04f, p.box.center.z) else p.box.top
        // rack checkpoints: the tag stands in front of the rack's open face, where the goods on the next tier cannot
        // hide it (independent QA: P15 / P03 / P20 tags were cut by boxes)
        val front = if (p.pos.x < RACK_FACE_X) Vec3(RACK_TAG_X, top.y, top.z) else top
        return front + Vec3(0f, TAG_GAP_M, 0f)
    }

    fun isFloor(poiId: String): Boolean = poiId == "P01"

    const val FLAG_H = 0.22f
    private const val RACK_FACE_X = -1.4f
    private const val RACK_TAG_X = -1.36f
    const val TAG_GAP_M = 0.06f
}

/**
 * Textures one frame may make (bitmap + upload): on the idle emulator (1 - 4 fps) the debrief made 8 - 10 verdict
 * textures in one frame and the app stopped answering input (ANR, independent QA 2026-10-28). Two per frame.
 */
internal class TextureBudget(var left: Int = PER_FRAME) {
    fun take(): Boolean = if (left > 0) {
        left--
        true
    } else false

    fun reset() {
        left = PER_FRAME
    }

    companion object {
        const val PER_FRAME = 2
    }
}

/** Yaw-only rotation that turns a quad's front (+Z) towards [viewer] from [at] (taste D10: no mirror, no tilt). */
internal fun billboardYaw(at: Vec3, viewer: Vec3): Quat4 = Quat4.axisAngle(Vec3.UP, atan2(viewer.x - at.x, viewer.z - at.z))

/**
 * The hover outline (design `tagsInWorld.highlight`): twelve thin interaction-yellow edges round the pointer box
 * (the SDK has no outline material, N3), or a dashed yellow ring for the floor decal; plus the name tag above it.
 */
internal class HoverView(parent: Entity, private val m: HhMaterials, private val meshes: HhMeshes, private val art: SignArt) {
    private val root = Entity().named("hover")
    private val edges = ArrayList<Entity>(12)
    private val ring: Entity
    private val tag: Entity
    private var tagFace: TexturedFace? = null
    private var shownPoi: String? = null
    private var tagKey: String? = null

    init {
        parent.addChild(root)
        repeat(12) {
            val e = model(meshes.cube, m.outline, "hover:edge")
            root.addChild(e)
            edges += e
        }
        ring = model(meshes.ring, m.outline, "hover:ring")
        root.addChild(ring)
        tag = model(meshes.quad, m.outline, "hover:tag")
        root.addChild(tag)
        root.enabled = false
    }

    fun show(poiId: String?, label: String, english: Boolean, test: Boolean, viewerLocal: Vec3?, budget: TextureBudget) {
        if (poiId == null) {
            if (shownPoi != null) root.enabled = false
            shownPoi = null
            return
        }
        val poi = Catalog.get(poiId) ?: return
        if (shownPoi != poiId) {
            shownPoi = poiId
            place(poi.box, MarkSpots.isFloor(poiId))
            root.enabled = true
        }
        val key = "$poiId:$english:$test"
        if (key != tagKey && budget.take()) {
            tagKey = key
            val face = m.owned(art.tag(label, 160, 36, test = test))
            tag.setMaterial(face?.material ?: m.outline)
            tagFace?.close()
            tagFace = face
        }
        val at = MarkSpots.tagAnchor(poiId)
        val k = constantScale(at, viewerLocal)
        tag.setLocal(position = at + Vec3(0f, TAG_H_M * k / 2f, 0f), rotation = viewerLocal?.let { billboardYaw(at, it) } ?: Quat4.IDENTITY, scale = Vec3(TAG_W_M * k, TAG_H_M * k, 1f))
    }

    private fun place(box: Box3, floor: Boolean) {
        edges.forEach { it.enabled = !floor }
        ring.enabled = floor
        if (floor) {
            val r = max(box.size.x, box.size.z) * 0.62f
            ring.setLocal(position = Vec3(box.center.x, 0.01f, box.center.z), scale = Vec3(r, 1f, r))
            return
        }
        val g = 0.02f
        val mn = box.min - Vec3(g, g, g)
        val mx = box.max + Vec3(g, g, g)
        val sx = mx.x - mn.x
        val sy = mx.y - mn.y
        val sz = mx.z - mn.z
        val c = (mn + mx) * 0.5f
        var i = 0
        for (y in listOf(mn.y, mx.y)) for (z in listOf(mn.z, mx.z)) edges[i++].setLocal(position = Vec3(c.x, y, z), scale = Vec3(sx, T, T))
        for (x in listOf(mn.x, mx.x)) for (z in listOf(mn.z, mx.z)) edges[i++].setLocal(position = Vec3(x, c.y, z), scale = Vec3(T, sy, T))
        for (x in listOf(mn.x, mx.x)) for (y in listOf(mn.y, mx.y)) edges[i++].setLocal(position = Vec3(x, y, c.z), scale = Vec3(T, T, sz))
    }

    fun destroy() {
        runCatching { root.destroy(true) }
        tagFace?.close()
        tagFace = null
    }

    companion object {
        private const val T = 0.008f

        /** Tag 160 x 36 dp; at 1.5 m it is 0.24 m wide (about 9 degrees), then it keeps its visual angle (design: constant visual size). */
        const val TAG_W_M = 0.24f
        const val TAG_H_M = 0.054f
        const val REF_DISTANCE_M = 1.5f

        fun constantScale(at: Vec3, viewer: Vec3?): Float = if (viewer == null) 1f else (at.distanceTo(viewer) / REF_DISTANCE_M).coerceIn(0.6f, 2.2f)
    }
}

/**
 * Flags (design `tagsInWorld.flag`): pole 0.22 m, an orange pennant, a billboard number plate; each flag is a pointer
 * target too (tapping it reopens that checkpoint's card). Up to [MAX] (hazards + 2).
 */
internal class FlagViews(parent: Entity, private val m: HhMaterials, private val meshes: HhMeshes, private val art: SignArt) {
    private val root = Entity().named("flags")
    private val flags = ArrayList<Flag>()
    private val numberMaterials = HashMap<String, Material>()
    private val byEntity = HashMap<Long, Int>()

    private inner class Flag(val holder: Entity, val plate: Entity) {
        var poi: String? = null
        var number = 0
        var plateKey = ""
    }

    init {
        parent.addChild(root)
        repeat(MAX) { i ->
            val holder = Entity().named("flag:$i")
            holder.tapBox(Vec3(0.14f, 0.28f, 0.14f), Vec3(0f, 0.14f, 0f))
            val pole = model(meshes.cylinder, m.paint(Paint.STEEL), "flag:pole")
            pole.setLocal(position = Vec3(0f, MarkSpots.FLAG_H / 2f, 0f), scale = Vec3(0.012f, MarkSpots.FLAG_H, 0.012f))
            holder.addChild(pole)
            val cloth = model(meshes.pennant, m.pennant, "flag:cloth")
            cloth.setLocal(position = Vec3(0f, MarkSpots.FLAG_H - 0.045f, 0f), scale = Vec3(0.13f, 0.08f, 1f))
            holder.addChild(cloth)
            val plate = model(meshes.quad, m.pennant, "flag:number")
            holder.addChild(plate)
            holder.enabled = false
            root.addChild(holder)
            flags += Flag(holder, plate)
            byEntity[holder.id] = i
        }
    }

    /** [marks]: (poi, number) in order; hidden entirely when [visible] is false (debrief). */
    fun show(marks: List<Pair<String, Int>>, visible: Boolean, test: Boolean, viewerLocal: Vec3?, budget: TextureBudget) {
        for ((i, f) in flags.withIndex()) {
            val mk = if (visible) marks.getOrNull(i) else null
            if (mk == null) {
                if (f.poi != null) f.holder.enabled = false
                f.poi = null
                continue
            }
            val (poi, number) = mk
            val base = MarkSpots.flagBase(poi)
            if (f.poi != poi) {
                f.poi = poi
                f.holder.setLocal(position = base)
                f.holder.enabled = true
            }
            val key = "$number:$test"
            if (f.plateKey != key && (numberMaterials.containsKey(key) || budget.take())) {
                f.plateKey = key
                f.number = number
                f.plate.setMaterial(numberMaterials.getOrPut(key) { m.textured(art.flagNumber(number, test)) ?: m.pennant })
            }
            val at = base + Vec3(0.1f, MarkSpots.FLAG_H + 0.05f, 0f)
            val rot = viewerLocal?.let { billboardYaw(at, it) } ?: Quat4.IDENTITY
            f.plate.setLocal(position = Vec3(0.1f, MarkSpots.FLAG_H + 0.05f, 0f), rotation = rot, scale = Vec3(0.09f, 0.09f, 1f))
        }
    }

    /** The checkpoint whose flag was tapped, or null. */
    fun poiOf(entity: Entity): String? = byEntity[entity.id]?.let { flags[it].poi }

    /** Flag boxes for the app-side hover ray (bay coordinates). */
    fun boxes(): List<Pair<String, Box3>> = flags.mapNotNull { f -> f.poi?.let { it to Box3(MarkSpots.flagBase(it) + Vec3(0f, 0.14f, 0f), Vec3(0.14f, 0.28f, 0.14f)) } }

    fun destroy() = runCatching { root.destroy(true) }

    companion object {
        const val MAX = 12
    }
}

/**
 * Debrief verdict tags (design `tagsInWorld.verdictTag`): colour dot + name + "3/3" on the ink plate above each
 * hazard (and grey for false alarms); the selected one is 1.25x and breathes. They light up clockwise over 0.6 s.
 */
internal class VerdictViews(parent: Entity, private val m: HhMaterials, private val meshes: HhMeshes, private val art: SignArt) {
    private val root = Entity().named("verdicts")
    private val tags = ArrayList<Entity>()
    private val keys = arrayOfNulls<String>(FlagViews.MAX)
    private val poiOf = arrayOfNulls<String>(FlagViews.MAX)
    private val faces = arrayOfNulls<TexturedFace>(FlagViews.MAX)

    init {
        parent.addChild(root)
        repeat(FlagViews.MAX) {
            val e = model(meshes.quad, m.outline, "verdict:$it")
            e.enabled = false
            root.addChild(e)
            tags += e
        }
    }

    /**
     * [items] in reveal order (clockwise). [revealT] seconds since the reveal began; [selected] breathes at [breath]
     * (0..1). Hidden when [items] is empty.
     */
    fun show(items: List<Pair<Outcome, String>>, revealT: Float, selected: String?, breath: Float, test: Boolean, english: Boolean, viewerLocal: Vec3?, budget: TextureBudget) {
        val n = items.size
        for (i in tags.indices) {
            val e = tags[i]
            val item = items.getOrNull(i)
            val on = item != null && revealT >= REVEAL_S * i / n.coerceAtLeast(1)
            if (!on) {
                if (poiOf[i] != null) e.enabled = false
                poiOf[i] = null
                continue
            }
            val (o, label) = item!!
            val key = "${o.poiId}:${o.verdict}:${o.points}:$english:$test"
            if (keys[i] != key) {
                if (!budget.take()) {
                    // texture not made yet: the tag waits a frame (the reveal then runs at two tags per frame)
                    if (poiOf[i] != null) e.enabled = false
                    poiOf[i] = null
                    continue
                }
                keys[i] = key
                val dot =
                    when (o.verdict) {
                        Verdict.GREEN -> HhColors.Passable
                        Verdict.YELLOW -> HhColors.Alert
                        Verdict.RED -> HhColors.Error
                        Verdict.GREY -> HhColors.Grey
                    }
                val fig = if (o.hazard) "${o.points}/3" else null
                val face = m.owned(art.tag(label, 180, 40, dot = dot, figure = fig, test = test))
                e.setMaterial(face?.material ?: m.outline)
                faces[i]?.close()
                faces[i] = face
            }
            if (poiOf[i] == null) e.enabled = true
            poiOf[i] = o.poiId
            val at = MarkSpots.tagAnchor(o.poiId)
            val sel = o.poiId == selected
            val k = HoverView.constantScale(at, viewerLocal) * (if (sel) 1.25f * (1f + 0.06f * breath) else 1f)
            e.setLocal(position = at + Vec3(0f, H_M * k / 2f, 0f), rotation = viewerLocal?.let { billboardYaw(at, it) } ?: Quat4.IDENTITY, scale = Vec3(W_M * k, H_M * k, 1f))
        }
    }

    fun destroy() {
        runCatching { root.destroy(true) }
        for (i in faces.indices) {
            faces[i]?.close()
            faces[i] = null
        }
    }

    companion object {
        const val REVEAL_S = 0.6f
        const val W_M = 0.27f
        const val H_M = 0.06f
    }
}

/** The guiding beam (design `tagsInWorld.beam`): a soft red column 1.2 m up from the checkpoint top, breathing. */
internal class BeamView(parent: Entity, m: HhMaterials, meshes: HhMeshes) {
    private val e = model(meshes.cylinder, m.beam, "beam")
    private var on = false

    init {
        parent.addChild(e)
        e.enabled = false
    }

    fun show(poiId: String?, breath: Float) {
        if (poiId == null) {
            if (on) e.enabled = false
            on = false
            return
        }
        val base = MarkSpots.tagAnchor(poiId) + Vec3(0f, 0.06f, 0f)
        val d = 0.12f * (0.85f + 0.3f * breath)
        e.setLocal(position = base + Vec3(0f, 0.6f, 0f), scale = Vec3(d, 1.2f, d))
        if (!on) e.enabled = true
        on = true
    }

    fun destroy() = runCatching { e.destroy(true) }
}
