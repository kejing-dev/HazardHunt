package io.embodit.hazardhunt.ui.hh.scene

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import io.embodit.hazardhunt.domain.model.Box3
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Paint
import io.embodit.hazardhunt.domain.model.Part
import io.embodit.hazardhunt.domain.model.Props
import io.embodit.hazardhunt.domain.model.Quat4
import io.embodit.hazardhunt.domain.model.Shape
import io.embodit.hazardhunt.domain.model.SignKind
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.usecase.RoundLayout
import io.embodit.hazardhunt.platform.HhLog
import io.embodit.hazardhunt.ui.theme.HhColors

/** Shared meshes: the unit shapes every checkpoint part scales, the sign quad, the pennant and the floor ring. */
internal class HhMeshes {
    lateinit var cube: MeshResource
    lateinit var cylinder: MeshResource
    lateinit var sphere: MeshResource
    lateinit var quad: MeshResource
    lateinit var pennant: MeshResource
    lateinit var ring: MeshResource

    fun of(shape: Shape): MeshResource =
        when (shape) {
            Shape.BOX -> cube
            Shape.CYL -> cylinder
            Shape.SPHERE -> sphere
            Shape.SIGN -> quad
        }

    /** Two meshes per paced step (design: at most 2 MeshResources per frame, ANR gate). */
    suspend fun build(shared: SharedResources, pace: suspend () -> Unit) {
        cube = shared.keep(UnitGeo.single(UnitGeo.cube, "unitCube"))
        cylinder = shared.keep(UnitGeo.single(UnitGeo.cylinder, "unitCylinder"))
        pace()
        sphere = shared.keep(UnitGeo.single(UnitGeo.sphere, "unitSphere"))
        quad = shared.keep(UnitGeo.single(UnitGeo.quad, "quadUv", withUv = true))
        pace()
        pennant = shared.keep(UnitGeo.single(UnitGeo.pennant, "pennant"))
        ring = shared.keep(dashedRing("floorRing"))
        pace()
    }
}

/**
 * Materials of the scene. Props are lit (PBR, matte, a faint self-glow so the shadow side keeps its colour in a room
 * without ambient light, as in Figure Model); marks (outline, flag cloth, beam) are unlit with tone mapping off so the
 * design colours stay exact. Textures are made once per key and kept until the scene goes.
 */
internal class HhMaterials(private val shared: SharedResources) {
    private val lit = HashMap<Paint, Material>()

    fun paint(p: Paint): Material =
        lit.getOrPut(p) {
            val c = Color(p.argb.toInt())
            val glow = if (p == Paint.TUBE || p == Paint.DIFFUSER) 0.6f else GLOW
            shared.keep(
                PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(c.color4())
                    setMetallic(if (p == Paint.MIRROR || p == Paint.BLADE) 0.6f else 0f)
                    setRoughness(if (p == Paint.MIRROR || p == Paint.OIL_GLOSS || p == Paint.WATER) 0.25f else 0.75f)
                    setEmissiveColor(Color4(c.red * glow, c.green * glow, c.blue * glow, 1f))
                }
            )
        }

    val outline: Material = unlit(HhColors.Interaction)
    val pennant: Material = unlit(HhColors.HazardOrange, twoSided = true)
    val beam: Material = sheer(HhColors.Error, 0.35f)

    fun unlit(color: Color, twoSided: Boolean = false): UnlitMaterial =
        shared.keep(
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                runCatching { setApplyToneMapping(false) }
                if (twoSided) setCullingMode(MaterialCullingMode.NONE)
            }
        )

    private fun sheer(color: Color, opacity: Float): UnlitMaterial =
        shared.keep(
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color.copy(alpha = 1f).color4())
                setOpacity(opacity)
                runCatching { setApplyToneMapping(false) }
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
            }
        )

    /** An unlit textured, two-sided face kept for the scene's lifetime (signs, flag numbers: a bounded set). */
    fun textured(bitmap: Bitmap): Material? = owned(bitmap)?.let { (mat, tex) -> shared.keep(tex); shared.keep(mat) }

    /**
     * An unlit textured face owned by the caller (tags that change with hover / round / language): close both with
     * [TexturedFace.close] when it is replaced. Null when the texture cannot be made.
     */
    fun owned(bitmap: Bitmap): TexturedFace? =
        try {
            val tex = TextureResource.create(bitmap)
            val mat =
                UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(tex)
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                }
            TexturedFace(mat, tex)
        } catch (t: Throwable) {
            HhLog.e("SCENE texture failed: $t", t)
            null
        }

    companion object {
        const val GLOW = 0.16f
    }
}

/** A material and the texture it samples, closed together. */
internal data class TexturedFace(val material: Material, val texture: TextureResource) {
    fun close() {
        runCatching { material.close() }
        runCatching { texture.close() }
    }
}

internal fun model(mesh: MeshResource, material: Material, name: String): Entity = ModelEntity(mesh, material).named(name)

/** Places [e] as [part] (its unit mesh scaled to the part size). */
internal fun Entity.asPart(part: Part) {
    val size = if (part.shape == Shape.SIGN) Vec3(part.size.x, part.size.y, 1f) else part.size
    setLocal(position = part.center, rotation = part.rotation, scale = size)
}

/**
 * The bay (design `layout`): one root in Stage space carrying the merged fixtures (one mesh per paint), the signs and
 * the 28 checkpoints. Each checkpoint root carries its pointer box (`InteractableComponent`, plus the system
 * `HoverEffectComponent` so the platform's own gaze / pointer hover lights the prop) and one child group per look.
 * Everything below the root is in bay coordinates (origin on the floor at the viewer, -Z ahead).
 */
internal class BayView(parent: Entity, private val m: HhMaterials, private val meshes: HhMeshes, private val art: SignArt) {
    val root: Entity = Entity().named("bay")
    private val fixtures = Entity().named("fixtures")
    private val signMaterials = HashMap<SignKind, Material>()
    private val signs = ArrayList<Pair<Entity, SignKind>>()
    private val poiRoots = HashMap<String, Entity>()
    private val looks = HashMap<String, List<Entity>>()
    private val byEntity = HashMap<Long, String>()
    private var shown: RoundLayout? = null
    var built = false
        private set

    /** The rack's far end panel takes taps so a checkpoint behind it (P27) cannot be picked through it. */
    private val endPanel = Entity().named("occluder:rackEnd")

    init {
        parent.addChild(root)
        root.addChild(fixtures)
        root.enabled = false
        // a key light that turns with the bay: high, from the front left
        val light = Entity().named("keyLight")
        light.components.set(DirectionalLightComponent(Color4(1f, 0.98f, 0.94f, 1f), LIGHT_LUX, false))
        light.setLocal(rotation = Quat4.fromTo(Vec3.AHEAD, Vec3(0.35f, -1f, -0.45f).normalized()))
        root.addChild(light)
        endPanel.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createBox(Vec3(0.5f, 2.0f, 0.04f).sdk())), physicsMaterial = PhysicsMaterialResource()))
        endPanel.components.set(InteractableComponent())
        endPanel.setLocal(position = Vec3(-1.75f, 1.0f, -2.1f))
        root.addChild(endPanel)
    }

    /** Fixtures: one merged mesh per paint (two per paced step), then the sign quads. */
    suspend fun buildFixtures(shared: SharedResources, test: Boolean, pace: suspend () -> Unit) {
        val byPaint = LinkedHashMap<Paint, Geo>()
        for (p in Props.fixtures) {
            if (p.shape == Shape.SIGN) continue
            byPaint.getOrPut(p.paint) { Geo() }.append(UnitGeo.of(p.shape), p)
        }
        var n = 0
        for ((paint, geo) in byPaint) {
            val mesh = shared.keep(geo.build("fix:${paint.name}"))
            fixtures.addChild(model(mesh, m.paint(paint), "fix:${paint.name}"))
            if (++n % 2 == 0) pace()
        }
        pace()
        for (p in Props.fixtures) {
            if (p.shape != Shape.SIGN) continue
            val e = model(meshes.quad, signMaterial(p.sign!!, test), "sign:${p.sign}")
            e.asPart(p)
            fixtures.addChild(e)
            signs += e to p.sign
            pace()
        }
        HhLog.i("SCENE fixtures meshes=${byPaint.size} signs=${signs.size}")
    }

    private fun signMaterial(kind: SignKind, test: Boolean): Material =
        signMaterials.getOrPut(kind) { m.textured(art.sign(kind, test)) ?: m.paint(Paint.WHITE) }

    /** Checkpoints, one per paced step: root + pointer box + every look's parts. */
    suspend fun buildPois(test: Boolean, pace: suspend () -> Unit) {
        for (poi in Catalog.all) {
            val r = Entity().named("poi:${poi.id}")
            r.tapBox(poi.box.size, poi.box.center)
            runCatching { r.components.set(HoverEffectComponent()) }
            root.addChild(r)
            poiRoots[poi.id] = r
            byEntity[r.id] = poi.id
            val look = Props.look(poi.id)
            val groups = ArrayList<Entity>()
            for ((i, parts) in (listOf(look.safe) + look.hazards).withIndex()) {
                val g = Entity().named("poi:${poi.id}:$i")
                for (p in parts) {
                    val mat = if (p.shape == Shape.SIGN) signMaterial(p.sign!!, test) else m.paint(p.paint)
                    val e = model(meshes.of(p.shape), mat, "part:${poi.id}")
                    e.asPart(p)
                    g.addChild(e)
                    if (p.shape == Shape.SIGN) signs += e to p.sign!!
                }
                g.enabled = i == 0
                r.addChild(g)
                groups += g
            }
            looks[poi.id] = groups
            pace()
        }
        built = true
        HhLog.i("SCENE pois=${poiRoots.size}")
    }

    /** Shows this round's looks: safe everywhere, the drawn variant on the hazards; P26 / P27 only in walk mode. */
    fun show(layout: RoundLayout) {
        if (!built || shown == layout) return
        shown = layout
        for (poi in Catalog.all) {
            val r = poiRoots[poi.id] ?: continue
            val present = !poi.challengeOnly || layout.walk
            r.enabled = present
            val groups = looks[poi.id] ?: continue
            val active = if (layout.isHazard(poi.id)) 1 + layout.variantOf(poi.id) else 0
            groups.forEachIndexed { i, g -> g.enabled = i == active.coerceAtMost(groups.size - 1) }
        }
        HhLog.i("SCENE layout seed=${layout.seed} walk=${layout.walk}")
    }

    fun poiOf(entity: Entity): String? = byEntity[entity.id]

    fun isOccluder(entity: Entity): Boolean = entity.id == endPanel.id

    /** Occluding boxes for the app-side hover ray (bay coordinates): the rack's far end panel. */
    val occluders: List<Box3> = listOf(Box3(Vec3(-1.75f, 1.0f, -2.1f), Vec3(0.5f, 2.0f, 0.04f)))

    fun destroy() {
        runCatching { root.destroy(true) }
    }

    companion object {
        const val LIGHT_LUX = 9000f
    }
}
