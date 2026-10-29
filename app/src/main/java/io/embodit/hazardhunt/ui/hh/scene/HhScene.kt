package io.embodit.hazardhunt.ui.hh.scene

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import io.embodit.hazardhunt.domain.model.BlockReason
import io.embodit.hazardhunt.domain.model.Box3
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Quat4
import io.embodit.hazardhunt.domain.model.SurfaceKind
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.usecase.BayPose
import io.embodit.hazardhunt.domain.usecase.StickStepper
import io.embodit.hazardhunt.domain.usecase.rayBox
import io.embodit.hazardhunt.domain.usecase.wrap
import io.embodit.hazardhunt.domain.usecase.yawOf
import io.embodit.hazardhunt.platform.HhGraph
import io.embodit.hazardhunt.platform.HhLog
import io.embodit.hazardhunt.platform.tracking.HeadPose
import io.embodit.hazardhunt.ui.hh.DialogKind
import io.embodit.hazardhunt.ui.hh.HhEvent
import io.embodit.hazardhunt.ui.hh.HhUiState
import io.embodit.hazardhunt.ui.hh.Phase
import io.embodit.hazardhunt.ui.hh.debug.HhProbe
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** The framework instantiates the system itself, so the running scene is reached through this holder. */
internal object ActiveScene {
    @Volatile var scene: HhScene? = null
}

/** The one per-frame ECS system of the app (design note: stick, hover, follow, beam pulse run here, not in Compose). */
class HhFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/**
 * The 3D side of the app: the bay with its 28 checkpoints, hover outline and tag, flags, verdict tags, the beam, and
 * the placement of the pooled panels. Compose state arrives through [render]; per-frame work happens in [onFrame]
 * only. Everything started in [attach] is stopped in [detach]. Main thread only.
 */
class HhScene(private val resources: Resources, private val sink: (HhEvent) -> Unit) : HhProbe.SceneProbe {

    /** Panel id -> the panel is in view: a pooled panel composes its content only then. */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private val planeTracker = HhGraph.planes
    private val headTracker = HhGraph.head
    private val controller = HhGraph.controller

    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var shared: SharedResources? = null
    private var materials: HhMaterials? = null
    private var meshes: HhMeshes? = null
    private var art: SignArt? = null
    private var bay: BayView? = null
    private var hoverView: HoverView? = null
    private var flagViews: FlagViews? = null
    private var verdicts: VerdictViews? = null
    private var beam: BeamView? = null
    private var rig: PanelRig? = null
    private var attached = false
    private var sensing = false
    private var builtWithTest = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var buildJob: Job? = null

    private var ui = HhUiState()
    private var head = HeadPose(Vec3.ZERO, Quat4.IDENTITY)
    private var hasHead = false
    private var firstHeadMs = NEVER
    private val headYSamples = ArrayList<Float>()
    private var lastHeadSentMs = 0L
    private var lastTickMs = 0L
    private var lastFrameMs = 0L
    private var lastFrameDtMs = 0L
    private var lastFloorY: Float? = null
    private var floorReported = false
    private var blockSinceMs = NEVER
    private var blockSent: BlockReason? = null
    private var placeSeen = 0

    // shown bay pose: yaw / x / z jump on every bay serial; the floor height glides (0.3 s)
    private var shownBay: BayPose? = null
    private var shownY = 0f
    private var baySerialSeen = -1
    private var fadeStartMs = NEVER

    // stick and hover
    private val stepper = StickStepper()
    private val budget = TextureBudget()
    private var hoverCandidate: String? = null
    private var hoverCandidateSince = NEVER
    private var hoverSent: String? = null

    // panel follow (yaw only)
    private var eyeHeight = Float.NaN
    private var anchorYaw = Float.NaN
    private var anchorHead: Vec3 = Vec3.ZERO
    private var outSinceMs = NEVER
    private var slideFromYaw = 0f
    private var slideFromHead = Vec3.ZERO
    private var slideStartMs = NEVER
    private var dodge = 0f
    private var cardPlaced = -1
    private var dialogPlaced: DialogKind? = null
    private var snackPlaced = -1

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        val t0 = SystemClock.elapsedRealtime()
        this.content = content
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        // An entity added to the SpatialView is not at the Stage origin: move the root once so that its local space
        // equals Stage space (Spatial Ruler).
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        HhLog.i("STAGE root offset=${HhLog.pos(offset.domain())}")
        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        for (id in PanelSpec.pool) {
            val (w, h) = PanelSpec.size(id)
            rig.world(id, w, h).also { if (id == PanelSpec.INSPECT || id == PanelSpec.MENU || id == PanelSpec.COACH) it.autoFace = false }
        }
        startSensing()
        ActiveScene.scene = this
        bindPanels(attachments)
        buildJob =
            scope.launch {
                // Right after a start (or with the emulator short of memory) the runtime can refuse the first
                // resources ("Invalid MaterialInstance", 2026-10-28): drop what was made and try again, 5 times.
                for (attempt in 1..BUILD_ATTEMPTS) {
                    try {
                        build(root, t0)
                        return@launch
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        HhLog.e("SCENE build failed attempt=$attempt: $t", t)
                        dropBuilt()
                        if (attempt == BUILD_ATTEMPTS) return@launch
                        delay(BUILD_RETRY_MS * attempt)
                    }
                }
            }
    }

    /** Everything [build] made so far, for a retry. */
    private fun dropBuilt() {
        hoverView?.destroy()
        flagViews?.destroy()
        verdicts?.destroy()
        beam?.destroy()
        bay?.destroy()
        runCatching { shared?.close() }
        hoverView = null
        flagViews = null
        verdicts = null
        beam = null
        bay = null
        shared = null
        materials = null
        meshes = null
        art = null
    }

    /** Head, controller and plane tracking (no hand tracking: it breaks gaze + pinch on the emulator). */
    private fun startSensing() {
        if (sensing) return
        sensing = true
        headTracker.start()
        controller.start()
        planeTracker.start { _, _, _ -> }
    }

    private fun stopSensing() {
        if (!sensing) return
        sensing = false
        planeTracker.stop()
        headTracker.stop()
        controller.stop()
        hasHead = false
        headYSamples.clear()
        firstHeadMs = NEVER
        lastFloorY = null
        floorReported = false
    }

    /** Leaving for the background stops the trackers; coming back starts them again (design `container.lifecycle`). */
    fun onBackground() {
        stopSensing()
        HhLog.i("SENSING stopped (background)")
    }

    fun onForeground() {
        if (stageRoot == null) return
        startSensing()
        HhLog.i("SENSING started (foreground)")
    }

    /** Meshes two per step, entities one checkpoint per step: many resources in one frame stall the emulator (ANR). */
    private suspend fun build(root: Entity, t0: Long) {
        val sh = SharedResources()
        shared = sh
        val m = HhMaterials(sh)
        materials = m
        val a = SignArt(resources)
        art = a
        yield()
        val ms = HhMeshes()
        ms.build(sh) { pace(MESH_GAP_SLOW_MS) }
        meshes = ms
        val test = ui.testTexture
        builtWithTest = test
        val b = BayView(root, m, ms, a)
        bay = b
        b.buildFixtures(sh, test) { pace(MESH_GAP_SLOW_MS) }
        b.buildPois(test) { pace(ENTITY_GAP_SLOW_MS) }
        hoverView = HoverView(b.root, m, ms, a)
        pace(ENTITY_GAP_SLOW_MS)
        flagViews = FlagViews(b.root, m, ms, a)
        pace(ENTITY_GAP_SLOW_MS)
        verdicts = VerdictViews(b.root, m, ms, a)
        beam = BeamView(b.root, m, ms)
        attached = true
        HhLog.i("SCENE attached build_ms=${SystemClock.elapsedRealtime() - t0}")
        render(ui)
        sink(HhEvent.SceneReady)
    }

    /** One frame when frames are fast (headset), [slowMs] when the idle emulator draws 1 - 2 fps. */
    private suspend fun pace(slowMs: Long) {
        val fast = lastFrameDtMs in 1..FAST_FRAME_MS
        delay(if (fast) 16L else slowMs)
    }

    /** Called from `SpatialView(update = ...)`: parents freshly composed panel entities under their holders. */
    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        val complete = rig.bind(attachments, PanelSpec.pool, SystemClock.elapsedRealtime())
        if (!complete) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        buildJob?.cancel()
        buildJob = null
        if (ActiveScene.scene === this) ActiveScene.scene = null
        main.removeCallbacksAndMessages(null)
        stopSensing()
        panelShown.clear()
        runCatching { rig?.release() }
        hoverView?.destroy()
        flagViews?.destroy()
        verdicts?.destroy()
        beam?.destroy()
        bay?.destroy()
        runCatching { stageRoot?.destroy(true) }
        shared?.close()
        shared = null
        stageRoot = null
        content = null
        materials = null
        meshes = null
        art = null
        bay = null
        hoverView = null
        flagViews = null
        verdicts = null
        beam = null
        rig = null
        shownBay = null
        anchorYaw = Float.NaN
        HhLog.i("SCENE detached")
    }

    // =========================================================================================
    // model -> scene
    // =========================================================================================

    fun render(state: HhUiState) {
        ui = state
        if (!attached) return
        if (state.testTexture != builtWithTest) HhLog.i("SCENE testtex=${state.testTexture}: restart the app to rebuild the textures")
        bay?.show(state.layout)
        syncPanels(state)
    }

    private fun syncPanels(state: HhUiState) {
        val rig = rig ?: return
        if (state.card == null) {
            rig[PanelSpec.CARD]?.hide()
            cardPlaced = -1
        }
        if (!state.menuOpen) rig[PanelSpec.MENU]?.hide()
        if (state.dialog == null) {
            rig[PanelSpec.DIALOG]?.hide()
            dialogPlaced = null
        }
        if (state.snack == null) {
            rig[PanelSpec.SNACK]?.hide()
            snackPlaced = -1
        }
        if (!state.coach) rig[PanelSpec.COACH]?.hide()
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
        if (lastFrameMs != 0L) lastFrameDtMs = now - lastFrameMs
        lastFrameMs = now
        readHead(now)
        val rig = rig ?: return
        rig.onFrame(now)
        val view = viewPose()
        if (hasHead) rig.stowBehind(io.embodit.hazardhunt.domain.usecase.ViewPose(view.position, view.rotation))
        val state = ui
        pollBlock(now)
        if (attached) {
            pollFloor()
            placeRequest(state, now)
            bayFrame(state, now, dt)
            stickFrame(state, view)
            hoverFrame(state, view, now)
            markersFrame(state, view, now)
        }
        if (hasHead) placePanels(rig, state, view, now)
        if (now - lastTickMs >= TICK_MS) {
            lastTickMs = now
            sink(HhEvent.Tick(now))
        }
        if (hasHead && now - lastHeadSentMs >= HEAD_SAMPLE_MS) {
            lastHeadSentMs = now
            sink(HhEvent.HeadSample(head.position, head.forward))
        }
        if (hasHead) rig.faceViewer(view.position)
    }

    /** The head pose the app reasons with: the real one, or QA's `camera=` override (Stage x / z, yaw, pitch). */
    private fun viewPose(): HeadPose {
        val cam = ui.camera ?: return head
        val y = (shownBay?.floorY ?: (head.position.y - 1.6f)) + 1.6f
        val rot = Quat4.axisAngle(Vec3.UP, rad(cam.yawDeg)) * Quat4.axisAngle(Vec3.RIGHT, rad(cam.pitchDeg))
        return HeadPose(Vec3(cam.x, y, cam.z), rot)
    }

    /** Head pose; the first samples of a fresh tracker are the origin, and samples within 0.3 m of the floor are dropped. */
    private fun readHead(now: Long) {
        val p = headTracker.pose() ?: return
        if (p.position.length < 1e-4f) return
        lastFloorY?.let { if (p.position.y - it < 0.3f) return }
        head = p
        if (!hasHead) {
            hasHead = true
            firstHeadMs = now
        }
        if (now - firstHeadMs <= HEAD_SETTLE_MS) headYSamples += p.position.y
        if (eyeHeight.isNaN() || now - firstHeadMs < EYE_SAMPLE_MS) eyeHeight = p.position.y else eyeHeight += (p.position.y - eyeHeight) * 0.02f
    }

    private fun pollBlock(now: Long) {
        val reason = headTracker.blockReason()
        if (reason == null) {
            blockSinceMs = NEVER
            if (blockSent != null) {
                blockSent = null
                sink(HhEvent.BlockChanged(null))
            }
            return
        }
        if (blockSinceMs == NEVER) blockSinceMs = now
        // just after install the support state reads NOT_IN_FULL_SPACE for a moment: 2 s before S23 (design note)
        if (blockSent != reason && now - blockSinceMs >= BLOCK_AFTER_MS) {
            blockSent = reason
            sink(HhEvent.BlockChanged(reason))
        }
    }

    /** FLOOR plane height (design S02): the largest floor's mean height, reported when it changes by 5 mm or more. */
    private fun pollFloor() {
        if (ui.frozen && floorReported) return
        val floors = planeTracker.all.filter { it.kind == SurfaceKind.FLOOR && it.vertices.isNotEmpty() }
        if (floors.isEmpty()) return
        val best = floors.maxByOrNull { f -> spread(f.vertices) } ?: return
        val y = best.vertices.map { it.y }.average().toFloat()
        val last = lastFloorY
        if (last != null && abs(last - y) < 0.005f) return
        lastFloorY = y
        floorReported = true
        sink(HhEvent.FloorChanged(y))
    }

    private fun spread(v: List<Vec3>): Float {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (p in v) {
            minX = minOf(minX, p.x)
            maxX = maxOf(maxX, p.x)
            minZ = minOf(minZ, p.z)
            maxZ = maxOf(maxZ, p.z)
        }
        return (maxX - minX) * (maxZ - minZ)
    }

    /** Placement asked by the ViewModel: after 0.5 s of head samples, at the median head height (design `rules.place`). */
    private fun placeRequest(state: HhUiState, now: Long) {
        if (state.placeSerial == placeSeen || !hasHead) return
        if (now - firstHeadMs < HEAD_SETTLE_MS) return
        if (state.frozen && state.bay != null) {
            placeSeen = state.placeSerial
            return
        }
        placeSeen = state.placeSerial
        val ys = headYSamples.sorted()
        val headY = if (ys.isEmpty()) head.position.y else ys[ys.size / 2]
        val pose = viewPose()
        HhLog.i("PLACE request head=${HhLog.pos(pose.position)} median_y=${HhLog.f(headY, 3)}")
        // "Recenter" / back from the background: the inspect panel comes to the new front too (taste D9)
        if (!anchorYaw.isNaN() && slideStartMs == NEVER) {
            slideFromYaw = anchorYaw
            slideFromHead = anchorHead
            slideStartMs = now
        }
        sink(HhEvent.Placed(pose.position, pose.forward, headY))
    }

    /** Bay transform: jumps on a new bay serial (placement, snap turn, bring to front); the floor glides 0.3 s. */
    private fun bayFrame(state: HhUiState, now: Long, dt: Float) {
        val view = bay ?: return
        val pose = state.bay
        if (pose == null || state.phase == Phase.STARTING) {
            if (view.root.enabled) view.root.enabled = false
            return
        }
        val targetY = pose.floorY + state.settings.floorOffsetCm / 100f
        val first = shownBay == null
        if (state.baySerial != baySerialSeen || first) {
            baySerialSeen = state.baySerial
            if (first) {
                shownY = targetY
                fadeStartMs = now
            }
            shownBay = pose
        } else {
            shownBay = pose.copy(originX = shownBay!!.originX, originZ = shownBay!!.originZ, yaw = shownBay!!.yaw)
        }
        shownY = if (abs(shownY - targetY) > 1f) targetY else shownY + (targetY - shownY) * (1f - exp(-dt / FLOOR_GLIDE_S))
        val b = shownBay!!
        view.root.setLocal(position = Vec3(b.originX, shownY, b.originZ), rotation = b.rotation)
        if (!view.root.enabled) view.root.enabled = true
        if (fadeStartMs != NEVER) {
            val q = ((now - fadeStartMs) / FADE_MS).coerceIn(0f, 1f)
            view.root.setOpacity(0.2f + 0.8f * q)
            if (q >= 1f) fadeStartMs = NEVER
        }
    }

    /** The bay as rendered (Stage), with the shown floor height. */
    private fun renderedBay(): BayPose? = shownBay?.copy(floorY = shownY)

    /** Thumbstick X: 45 degree snap turn of the whole bay about the viewer (design `rules.turn`). */
    private fun stickFrame(state: HhUiState, view: HeadPose) {
        val sample = controller.sample()
        val x = sample?.stickX ?: 0f
        val delta = stepper.update(x)
        if (delta != 0f && state.bay != null) {
            HhLog.i("STICK step x=${HhLog.f(x)}")
            sink(HhEvent.StickTurn(delta, view.position))
        }
    }

    /**
     * App-side hover (design `rules.hover`, N10: the platform does not hand pointer hover to the app): the controller ray
     * when a controller delivers a pose, else the head's forward ray as the last fallback; nearest pointer box wins
     * (flags count as their checkpoint, the rack's end panel occludes). 0.1 s to light, 0.2 s to let go.
     */
    private fun hoverFrame(state: HhUiState, view: HeadPose, now: Long) {
        val b = renderedBay()
        val frozenHover = state.frozen && state.camera == null
        if (!state.hoverAllowed || b == null || frozenHover) {
            hoverCandidate = null
            if (!frozenHover && hoverSent != null && !state.hoverAllowed) {
                hoverSent = null
                sink(HhEvent.HoverChanged(null))
            }
            return
        }
        val ray = controller.sample()?.ray
        val origin = b.toLocal(ray?.origin ?: view.position)
        val dir = b.dirToLocal(ray?.direction ?: view.forward).normalized()
        var best: String? = null
        var bestT = Float.MAX_VALUE
        for (poi in state.layout.pois) {
            val t = rayBox(origin, dir, poi.box) ?: continue
            // standing inside a pointer box (walk mode next to the pallets) must not steal the hover
            if (t <= 0f) continue
            if (t < bestT) {
                bestT = t
                best = poi.id
            }
        }
        flagViews?.boxes()?.forEach { (id, box) ->
            val t = rayBox(origin, dir, box)
            if (t != null && t < bestT) {
                bestT = t
                best = id
            }
        }
        bay?.occluders?.forEach { box: Box3 ->
            val t = rayBox(origin, dir, box)
            if (t != null && t < bestT) {
                bestT = t
                best = null
            }
        }
        if (best != hoverCandidate) {
            hoverCandidate = best
            hoverCandidateSince = now
        }
        val wait = if (best == null) HOVER_OFF_MS else HOVER_ON_MS
        if (hoverCandidate != hoverSent && now - hoverCandidateSince >= wait) {
            hoverSent = hoverCandidate
            sink(HhEvent.HoverChanged(hoverSent))
        }
    }

    private fun markersFrame(state: HhUiState, view: HeadPose, now: Long) {
        val b = renderedBay() ?: return
        val viewerLocal = b.toLocal(view.position)
        val english = state.english
        val test = state.testTexture
        val hoverId = if (state.phase == Phase.INSPECTING && state.card == null) state.hover else null
        val label = hoverId?.let { Catalog.get(it)?.name?.of(english) } ?: ""
        budget.reset()
        hoverView?.show(hoverId, label, english, test, viewerLocal, budget)
        val debrief = state.debrief
        val inDebrief = state.phase == Phase.DEBRIEF && debrief != null
        val marks = state.marks.mapIndexed { i, mk -> mk.poiId to (i + 1) }
        flagViews?.show(marks, visible = state.phase == Phase.INSPECTING, test = test, viewerLocal = viewerLocal, budget = budget)
        val breath = ((1 + cos(2 * Math.PI * (now % BREATH_MS) / BREATH_MS)) / 2).toFloat()
        if (inDebrief) {
            val items = debrief!!.outcomes.sortedBy { io.embodit.hazardhunt.domain.usecase.Scoring.bearing(it.poiId) }.map { it to (Catalog.get(it.poiId)?.name?.of(english) ?: it.poiId) }
            verdicts?.show(items, (now - state.revealAtMs) / 1000f, state.detail?.poiId, breath, test, english, viewerLocal, budget)
            beam?.show(state.detail?.poiId, breath)
        } else {
            verdicts?.show(emptyList(), 0f, null, 0f, test, english, viewerLocal, budget)
            beam?.show(null, 0f)
        }
    }

    // =========================================================================================
    // panels (design `panels.*.placement`)
    // =========================================================================================

    private fun yawOfHead(v: HeadPose): Float = yawOf(v.forward)

    private fun pitchOf(v: HeadPose): Float = asin(v.forward.y.coerceIn(-1f, 1f))

    private fun dirOf(yaw: Float, pitch: Float): Vec3 = Vec3(-sin(yaw) * cos(pitch), sin(pitch), -cos(yaw) * cos(pitch))

    private fun placePanels(rig: PanelRig, state: HhUiState, view: HeadPose, now: Long) {
        val yawNow = yawOfHead(view)
        val eye = if (state.camera != null) view.position.y else eyeHeight.takeIf { !it.isNaN() } ?: view.position.y
        if (anchorYaw.isNaN()) {
            anchorYaw = yawNow
            anchorHead = view.position
        }
        // lazy follow, yaw only: out of the 45 degree window (or 0.5 m away) for 0.5 s -> slide back in 0.4 s
        val hold = state.menuOpen || pitchOf(view) < rad(LOOK_DOWN_DEG) || (state.frozen && state.camera == null)
        if (slideStartMs != NEVER) {
            val q = ((now - slideStartMs) / SLIDE_MS).coerceIn(0f, 1f)
            val e = Ease.inOutCubic(q)
            anchorYaw = slideFromYaw + wrap(yawNow - slideFromYaw) * e
            anchorHead = slideFromHead.lerp(view.position, e)
            if (q >= 1f) slideStartMs = NEVER
        } else {
            // the panel's own bearing from the view (+ = left): it must stay in the lower left, 8 .. 45 degrees
            // (independent QA: a window on the head alone left the panel 63 degrees left after a 30 degree turn right)
            val rel = wrap(anchorYaw + rad(PANEL_LEFT_DEG) - yawNow)
            val off = abs(wrap(yawNow - anchorYaw))
            val moved = Vec3(view.position.x - anchorHead.x, 0f, view.position.z - anchorHead.z).length
            val outside = rel > rad(PANEL_REL_MAX_DEG) || rel < rad(PANEL_REL_MIN_DEG) || moved > FOLLOW_MOVE_M
            if (!outside || hold) {
                outSinceMs = NEVER
            } else if (outSinceMs == NEVER) {
                outSinceMs = now
            } else if (now - outSinceMs >= FOLLOW_OUT_MS) {
                slideFromYaw = anchorYaw
                slideFromHead = anchorHead
                slideStartMs = now
                outSinceMs = NEVER
                HhLog.i("FOLLOW slide off=${HhLog.f(Math.toDegrees(off.toDouble()).toFloat(), 1)}")
            }
        }
        if (state.camera != null) {
            anchorYaw = yawNow
            anchorHead = view.position
        }
        // PanelDodge (design `rules.panelCollapse`): when a dialog opens the inspect panel slides back to its home at
        // the lower left of the current view (28 degrees left, 25 down): the dialog in the middle (8.6 degrees half
        // width) then never overlaps it. The design's extra 12 degrees pushed it out of view on the emulator.
        if (state.dialog != null && state.dialog != dialogPlaced && slideStartMs == NEVER) {
            slideFromYaw = anchorYaw
            slideFromHead = anchorHead
            slideStartMs = now
        }
        val wantDodge = 0f
        dodge += (wantDodge - dodge) * 0.2f
        val base = Vec3(anchorHead.x, eye, anchorHead.z)
        val panelYaw = anchorYaw + rad(PANEL_LEFT_DEG) + dodge
        val down = rad(-PANEL_DOWN_DEG)
        val at = base + dirOf(panelYaw, down) * PANEL_DISTANCE_M
        val rot = Quat4.axisAngle(Vec3.UP, panelYaw) * Quat4.axisAngle(Vec3.RIGHT, rad(-PANEL_TILT_DEG))
        rig[PanelSpec.INSPECT]?.place(at, rot, PanelSpec.SCALE)
        // menu: above the inspect panel's top edge, same heading and tilt
        if (state.menuOpen) {
            val (_, ih) = PanelSpec.halfM(PanelSpec.INSPECT)
            val (_, mh) = PanelSpec.halfM(PanelSpec.MENU)
            val gap = 8f * PanelRig.DP_M * PanelSpec.SCALE
            rig[PanelSpec.MENU]?.place(at + rot.rotate(Vec3(0f, ih + gap + mh, 0f)), rot, PanelSpec.SCALE)
        }
        // coach bubble: straight ahead of the anchor, 10 degrees down, 0.9 m (design `panels.coach`)
        if (state.coach && state.phase == Phase.READY) {
            val cAt = base + dirOf(anchorYaw, rad(-COACH_DOWN_DEG)) * COACH_DISTANCE_M
            rig[PanelSpec.COACH]?.place(cAt, Quat4.axisAngle(Vec3.UP, anchorYaw) * Quat4.axisAngle(Vec3.RIGHT, rad(-COACH_DOWN_DEG)), PanelSpec.SCALE)
        }
        // classify card: beside the checkpoint, fixed in the room while open
        val card = state.card
        if (card != null && card.serial != cardPlaced) {
            val b = renderedBay()
            val poi = Catalog.get(card.poiId)
            if (b != null && poi != null) {
                cardPlaced = card.serial
                val target = b.toStage(poi.box.center)
                val to = target - view.position
                val poiYaw = yawOf(to)
                val rel = wrap(poiYaw - yawNow)
                // + yaw = left: the card goes 20 degrees right of the checkpoint, or left when it is beyond 40 degrees right
                val side = if (rel < -rad(40f)) 1f else -1f
                val cardYaw = poiYaw + side * rad(CARD_SIDE_DEG)
                val horiz = kotlin.math.sqrt(to.x * to.x + to.z * to.z)
                val pitch = atan2(to.y, horiz).coerceIn(rad(-25f), rad(10f))
                val cAt = view.position + dirOf(cardYaw, pitch) * CARD_DISTANCE_M
                rig[PanelSpec.CARD]?.show(cAt, PanelSpec.SCALE)
                HhLog.i("CARD placed ${card.poiId} side=${if (side > 0) "left" else "right"} pitch=${HhLog.f(Math.toDegrees(pitch.toDouble()).toFloat(), 1)}")
            }
        }
        // dialog: straight ahead 0.8 m when it opens (design `panels.dialog`)
        val dlg = state.dialog
        if (dlg != null && dlg != dialogPlaced) {
            dialogPlaced = dlg
            rig[PanelSpec.DIALOG]?.show(view.position + dirOf(yawNow, pitchOf(view).coerceIn(rad(-20f), rad(10f))) * DIALOG_DISTANCE_M, PanelSpec.SCALE)
        }
        // snackbar: upper part of the view, 0.9 m, where the user is looking when it appears
        val snack = state.snack
        if (snack != null && snack.serial != snackPlaced) {
            snackPlaced = snack.serial
            val p = (pitchOf(view) + rad(SNACK_UP_DEG)).coerceIn(rad(-30f), rad(20f))
            rig[PanelSpec.SNACK]?.show(view.position + dirOf(yawNow, p) * SNACK_DISTANCE_M, PanelSpec.SCALE)
        }
    }

    // =========================================================================================
    // system pointer (gaze + pinch, controller trigger, emulator mouse)
    // =========================================================================================

    fun onSpatialTap(value: SpatialTapValue) {
        val target = value.targetEntity
        if (target == null) {
            HhLog.i("TAP none")
            sink(HhEvent.TappedOutside)
            return
        }
        val poi = bay?.poiOf(target)
        val flag = flagViews?.poiOf(target)
        when {
            flag != null -> sink(HhEvent.PoiTapped(flag, flag = true))
            poi != null -> sink(HhEvent.PoiTapped(poi, flag = false))
            else -> {
                HhLog.i("TAP other ${runCatching { target.getName() }.getOrNull()}")
                sink(HhEvent.TappedOutside)
            }
        }
    }

    // =========================================================================================
    // debug probe
    // =========================================================================================

    override fun poseLine(): String =
        "POSE hmd pos=${HhLog.pos5(head.position)} ${HhLog.quat5(head.rotation)} fwd=${HhLog.pos5(head.forward)} valid=$hasHead eye=${HhLog.f(eyeHeight, 3)} floorY=${lastFloorY?.let { HhLog.f(it, 3) }}"

    override fun panelLines(): List<String> {
        val rig = rig ?: return emptyList()
        return rig.all.filter { it.shown && it.panel != null }.map { slot ->
            String.format(Locale.US, "PANEL id=%s pos=%s %s scale=%.3f size_dp=%.0fx%.0f", slot.id, HhLog.pos5(slot.position), HhLog.quat5(slot.rotation), slot.scale, slot.widthDp, slot.heightDp)
        }
    }

    override fun bayLine(): String {
        val b = renderedBay() ?: return "BAY none"
        return "BAY origin=${HhLog.pos(Vec3(b.originX, b.floorY, b.originZ))} yaw=${HhLog.f(Math.toDegrees(b.yaw.toDouble()).toFloat(), 1)} built=${bay?.built}"
    }

    override fun poiLine(id: String): String {
        val b = renderedBay() ?: return "POI no bay"
        val p = Catalog.get(id) ?: return "POI $id unknown"
        val w = b.toStage(p.box.center)
        val to = w - head.position
        val yaw = Math.toDegrees(wrap(yawOf(to) - yawOf(head.forward)).toDouble())
        val pitch = Math.toDegrees(atan2(to.y, kotlin.math.sqrt(to.x * to.x + to.z * to.z)).toDouble())
        return String.format(Locale.US, "POI %s stage=%s from_view yaw=%.1f pitch=%.1f dist=%.2f", id, HhLog.pos(w), yaw, pitch, to.length)
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE
        private const val BUILD_ATTEMPTS = 5
        private const val BUILD_RETRY_MS = 1_500L
        private const val REBIND_DELAY_MS = 150L
        private const val TICK_MS = 100L
        private const val HEAD_SAMPLE_MS = 100L
        private const val BLOCK_AFTER_MS = 2_000L
        private const val HEAD_SETTLE_MS = 500L
        private const val EYE_SAMPLE_MS = 3_000L
        private const val FAST_FRAME_MS = 40L
        private const val MESH_GAP_SLOW_MS = 1_200L
        private const val ENTITY_GAP_SLOW_MS = 500L
        private const val FADE_MS = 500f
        private const val FLOOR_GLIDE_S = 0.1f
        private const val HOVER_ON_MS = 100L
        private const val HOVER_OFF_MS = 200L
        private const val BREATH_MS = 2_000L

        private const val PANEL_LEFT_DEG = 28f
        private const val PANEL_DOWN_DEG = 20f // design 25; its spike fallback 20 keeps the 488 dp layouts' bottom row in a level view
        private const val PANEL_TILT_DEG = 10f
        private const val PANEL_DISTANCE_M = 0.65f
        private const val PANEL_REL_MAX_DEG = 45f
        private const val PANEL_REL_MIN_DEG = 8f
        private const val FOLLOW_MOVE_M = 0.5f
        private const val FOLLOW_OUT_MS = 500L
        private const val SLIDE_MS = 400f
        private const val LOOK_DOWN_DEG = -40f
        private const val COACH_DOWN_DEG = 10f
        private const val COACH_DISTANCE_M = 0.9f
        private const val CARD_SIDE_DEG = 20f
        private const val CARD_DISTANCE_M = 0.8f
        private const val DIALOG_DISTANCE_M = 0.8f
        private const val SNACK_UP_DEG = 14f
        private const val SNACK_DISTANCE_M = 0.9f

        private fun rad(d: Float): Float = Math.toRadians(d.toDouble()).toFloat()
    }
}
