package io.embodit.hazardhunt.ui.hh

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.embodit.hazardhunt.data.SettingsStore
import io.embodit.hazardhunt.domain.model.BlockReason
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.LanguagePref
import io.embodit.hazardhunt.domain.model.LastRound
import io.embodit.hazardhunt.domain.model.Settings
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.usecase.BayPose
import io.embodit.hazardhunt.domain.usecase.Mark
import io.embodit.hazardhunt.domain.usecase.RoundRules
import io.embodit.hazardhunt.domain.usecase.Scoring
import io.embodit.hazardhunt.domain.usecase.Verdict
import io.embodit.hazardhunt.domain.usecase.bringToFrontDelta
import io.embodit.hazardhunt.platform.HhGraph
import io.embodit.hazardhunt.platform.HhLog
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state machine of the app (design `states`, `rules`, `interactions`). Panel controls, scene callbacks (head,
 * floor, block reason, stick steps, hover, taps, the 100 ms tick) and the debug hooks all arrive through [onEvent].
 * Main thread only.
 */
class HhViewModel(private val store: SettingsStore) : ViewModel() {

    private val _state: MutableStateFlow<HhUiState>
    val state: StateFlow<HhUiState>
    private val _effects = MutableSharedFlow<HhEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<HhEffect> = _effects

    private var head: Vec3? = null
    private var headForward: Vec3 = Vec3.AHEAD
    private var snackSerial = 0
    private var cardSerial = 0
    private var lastLogged = ""
    private var debugBlocked = false
    private var realBlock: BlockReason? = null
    private var stateBeforeBlock: Phase = Phase.STARTING

    init {
        val settings = store.load()
        val seed = RoundRules.newSeed(System.nanoTime())
        _state = MutableStateFlow(HhUiState(settings = settings, english = english(settings.language), layout = RoundRules.draw(seed, settings.walkMode), previous = store.loadLast()))
        state = _state.asStateFlow()
        HhLog.i("SETTINGS $settings")
        logRound("init")
        logState()
    }

    private val now: Long
        get() = SystemClock.elapsedRealtime()

    private fun update(f: (HhUiState) -> HhUiState) {
        _state.value = f(_state.value)
        logState()
    }

    private fun logState() {
        val s = _state.value
        val line = "${s.stateId()} marked=${s.marks.size} seed=${s.layout.seed}${s.stateTags()}"
        if (line != lastLogged) {
            lastLogged = line
            HhLog.i("HH-STATE $line")
        }
    }

    private fun logRound(why: String) {
        val s = _state.value
        HhLog.i("ROUND $why seed=${s.layout.seed} walk=${s.layout.walk} hazards=${s.layout.hazards.entries.joinToString(",") { (k, v) -> if (v == 0) k else "$k:$v" }}")
    }

    private fun emit(e: HhEffect) {
        if (e is HhEffect.Sound && !_state.value.settings.soundOn) {
            HhLog.i("SOUND ${e.kind} muted")
            return
        }
        if (e is HhEffect.Sound) HhLog.i("SOUND ${e.kind}")
        _effects.tryEmit(e)
    }

    private fun english(pref: LanguagePref): Boolean =
        when (pref) {
            LanguagePref.ZH -> false
            LanguagePref.EN -> true
            LanguagePref.SYSTEM -> Locale.getDefault().language != "zh"
        }

    private fun saveSettings(f: (Settings) -> Settings) {
        val next = f(_state.value.settings)
        store.save(next)
        update { it.copy(settings = next, english = english(next.language)) }
        HhLog.i("SETTINGS $next")
    }

    // =========================================================================================
    // events
    // =========================================================================================

    fun onEvent(event: HhEvent) {
        when (event) {
            is HhEvent.Tick -> tick(event.nowMs)
            is HhEvent.HeadSample -> {
                head = event.position
                headForward = event.forward
            }
            HhEvent.SceneReady -> {
                HhLog.i("EVENT scene ready")
                update { it.copy(sceneReady = true, placeSerial = it.placeSerial + 1) }
            }
            is HhEvent.Placed -> placed(event)
            is HhEvent.FloorChanged -> floor(event.y)
            is HhEvent.BlockChanged -> {
                realBlock = event.reason
                applyBlock()
            }
            is HhEvent.StickTurn -> stickTurn(event.delta, event.pivot)
            is HhEvent.HoverChanged -> {
                val s = _state.value
                if (s.frozen && s.camera == null) return
                if (event.poiId != s.hover) {
                    HhLog.i("HOVER ${event.poiId ?: "none"}")
                    update { it.copy(hover = event.poiId) }
                }
            }
            is HhEvent.PoiTapped -> poiTapped(event.poiId, event.flag)
            HhEvent.TappedOutside -> if (_state.value.menuOpen) update { it.copy(menuOpen = false) }
            HhEvent.WentBackground -> background()
            HhEvent.CameForeground -> foreground()

            HhEvent.StartClicked -> start()
            HhEvent.RecenterClicked -> recenter("button")
            HhEvent.SubmitClicked -> submitClicked()
            HhEvent.MoreClicked -> update { it.copy(menuOpen = !it.menuOpen, card = if (!it.menuOpen) null else it.card) }
            HhEvent.MuteToggled -> saveSettings { it.copy(soundOn = !it.soundOn) }
            HhEvent.CoachDismissed -> dismissCoach()
            is HhEvent.CategoryPicked -> {
                val c = _state.value.card ?: return
                emit(HhEffect.Sound(SoundKind.PAGE))
                update { it.copy(card = c.copy(step = 2, category = event.category)) }
            }
            is HhEvent.FixPicked -> fixPicked(event.fixIndex)
            HhEvent.CardBack -> _state.value.card?.let { c -> update { it.copy(card = c.copy(step = 1)) } }
            HhEvent.CardClosed -> update { it.copy(card = null) }
            HhEvent.MarkRemoved -> removeMark()
            is HhEvent.EditMark -> openCard(event.poiId)
            HhEvent.DialogConfirmed -> dialogConfirmed()
            HhEvent.DialogCancelled -> update { it.copy(dialog = if (it.dialog == DialogKind.BLOCKED) it.dialog else null) }
            HhEvent.SnackAction -> {
                val s = _state.value
                if (s.snack?.kind == SnackKind.ALL_MARKED) {
                    update { it.copy(snack = null) }
                    submit()
                }
            }
            is HhEvent.DebriefRow -> openDetail(event.poiId, tour = false)
            HhEvent.MissedClicked -> _state.value.debrief?.missed?.firstOrNull()?.let { openDetail(it, tour = true) }
            HhEvent.AgainClicked -> again()
            HhEvent.ReplayClicked -> replay()
            HhEvent.DetailPrev -> stepDetail(-1)
            HhEvent.DetailNext -> stepDetail(1)
            HhEvent.DetailBack -> update { it.copy(detail = null) }
            is HhEvent.LanguageSelected -> saveSettings { it.copy(language = event.language) }
            is HhEvent.SoundSelected -> saveSettings { it.copy(soundOn = event.on) }
            is HhEvent.FloorStep -> saveSettings { it.copy(floorOffsetCm = (it.floorOffsetCm + event.cm).coerceIn(-FLOOR_RANGE_CM, FLOOR_RANGE_CM)) }
            is HhEvent.TimerSelected -> saveSettings { it.copy(timerOn = event.on) }
            is HhEvent.WalkSelected -> saveSettings { it.copy(walkMode = event.on) }
            HhEvent.ResetBayClicked -> resetClicked()
            HhEvent.ExitClicked -> {
                HhLog.i("EXIT requested")
                emit(HhEffect.ExitApp)
            }
            else -> debug(event)
        }
    }

    // =========================================================================================
    // clock, placement, floor, block
    // =========================================================================================

    /**
     * The 100 ms scene tick. The state only changes when a snackbar runs out or, with the timer shown, when the
     * displayed second changes: panels are not recomposed ten times a second for nothing.
     */
    private fun tick(t: Long) {
        val s = _state.value
        // a debug jump holds its snackbar until the next command, so QA can capture S11 / S12
        val snackDone = s.snack != null && t >= s.snack.untilMs && !s.frozen
        val running = s.runningSinceMs
        val secondChanged = s.settings.timerOn && running != null && (s.elapsedMs + t - running) / 1000 != s.elapsedNow / 1000
        if (!snackDone && !secondChanged) return
        update { it.copy(nowMs = t, snack = if (snackDone) null else it.snack) }
    }

    /** Inspection time up to now (the state's own `elapsedNow` only moves with the shown seconds). */
    private fun elapsed(s: HhUiState): Long = s.elapsedMs + (s.runningSinceMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L)

    private fun placed(e: HhEvent.Placed) {
        val s = _state.value
        if (s.frozen && s.bay != null) return
        val floorY = if (!s.noFloor) s.floorPlaneY ?: (e.headY - HEAD_ABOVE_FLOOR_M) else e.headY - HEAD_ABOVE_FLOOR_M
        val bay = BayPose.facing(e.head, e.forward, floorY)
        HhLog.i("PLACE origin=${HhLog.pos(Vec3(bay.originX, bay.floorY, bay.originZ))} yaw=${deg(bay.yaw)} floor=${if (s.floorPlaneY != null && !s.noFloor) "plane" else "head-1.60"}")
        val phase = if (s.phase == Phase.STARTING) Phase.READY else s.phase
        update {
            it.copy(
                bay = bay,
                baySerial = it.baySerial + 1,
                phase = phase,
                coach = if (s.phase == Phase.STARTING) !it.settings.coachSeen else it.coach,
            )
        }
    }

    private fun floor(y: Float?) {
        val s = _state.value
        if (y != null) HhLog.i("HH-FLOOR ${HhLog.f(y, 3)}")
        if (s.noFloor) {
            update { it.copy(floorPlaneY = y) }
            return
        }
        val bay = s.bay
        update { it.copy(floorPlaneY = y, bay = if (bay != null && y != null) bay.copy(floorY = y) else bay) }
    }

    private fun applyBlock() {
        val reason = if (debugBlocked) BlockReason.NOT_IN_FULL_SPACE else realBlock
        val s = _state.value
        if (reason != null && s.phase != Phase.BLOCKED) {
            stateBeforeBlock = s.phase
            HhLog.i("BLOCKED reason=$reason")
            update { it.copy(phase = Phase.BLOCKED, blockReason = reason, dialog = DialogKind.BLOCKED, card = null, menuOpen = false, coach = false, runningSinceMs = null, elapsedMs = elapsed(it)) }
        } else if (reason == null && s.phase == Phase.BLOCKED) {
            HhLog.i("BLOCKED cleared")
            val back = if (stateBeforeBlock == Phase.BLOCKED) Phase.STARTING else stateBeforeBlock
            update { it.copy(phase = back, blockReason = null, dialog = null, runningSinceMs = if (back == Phase.INSPECTING) now else null) }
        }
    }

    /** "Reset to front" and coming back from the background: the bay follows the current head pose (design `rules.place`). */
    private fun recenter(why: String) {
        HhLog.i("RECENTER $why")
        update { it.copy(placeSerial = it.placeSerial + 1) }
    }

    private fun background() {
        HhLog.i("LIFECYCLE background")
        update { it.copy(elapsedMs = elapsed(it), runningSinceMs = null, hover = null) }
    }

    private fun foreground() {
        HhLog.i("LIFECYCLE foreground")
        val s = _state.value
        update { it.copy(runningSinceMs = if (s.phase == Phase.INSPECTING) now else null, placeSerial = if (s.bay != null) it.placeSerial + 1 else it.placeSerial) }
    }

    // =========================================================================================
    // round flow
    // =========================================================================================

    private fun start() {
        val s = _state.value
        if (s.phase != Phase.READY) return
        if (s.coach) dismissCoach()
        HhLog.i("START seed=${s.layout.seed}")
        update { it.copy(phase = Phase.INSPECTING, elapsedMs = 0L, runningSinceMs = now, nowMs = now, coach = false) }
    }

    private fun dismissCoach() {
        if (!_state.value.settings.coachSeen) saveSettings { it.copy(coachSeen = true) }
        update { it.copy(coach = false) }
    }

    private fun stickTurn(delta: Float, pivot: Vec3) {
        val s = _state.value
        val bay = s.bay ?: return
        if (s.phase == Phase.STARTING || s.phase == Phase.BLOCKED || s.card != null || s.menuOpen || s.dialog != null) return
        if (s.walkRound) {
            HhLog.i("TURN blocked (walk mode)")
            showSnack(SnackKind.WALK, SNACK_MS)
            return
        }
        val turned = bay.turned(delta, pivot.x, pivot.z)
        HhLog.i("TURN delta=${deg(delta)} yaw=${deg(turned.yaw)}")
        update { it.copy(bay = turned, baySerial = it.baySerial + 1) }
    }

    private fun poiTapped(id: String, flag: Boolean) {
        val s = _state.value
        HhLog.i("TAP ${if (flag) "flag" else "poi"} $id")
        if (s.menuOpen) {
            update { it.copy(menuOpen = false) }
            return
        }
        if (s.phase != Phase.INSPECTING || s.card != null || s.dialog != null) return
        if (s.layout.pois.none { it.id == id }) return
        update { it.copy(hover = id) }
        openCard(id)
    }

    private fun openCard(id: String) {
        val s = _state.value
        if (s.phase != Phase.INSPECTING) return
        val existing = s.markOf(id)
        if (existing == null && s.marks.size >= s.cap) {
            HhLog.i("MARK limit cap=${s.cap}")
            emit(HhEffect.Sound(SoundKind.FAIL))
            showSnack(SnackKind.LIMIT, SNACK_MS)
            return
        }
        cardSerial++
        HhLog.i("CARD open $id editing=${existing != null}")
        emit(HhEffect.Sound(SoundKind.PAGE))
        update { it.copy(card = CardState(id, 1, existing?.category, existing != null, cardSerial), menuOpen = false) }
    }

    private fun fixPicked(fixIndex: Int) {
        val s = _state.value
        val c = s.card ?: return
        val category = c.category ?: return
        val mark = Mark(c.poiId, category, fixIndex)
        val marks = if (s.markOf(c.poiId) != null) s.marks.map { if (it.poiId == c.poiId) mark else it } else s.marks + mark
        HhLog.i("MARK ${c.poiId} cat=${category.name.lowercase(Locale.US)} fix=$fixIndex n=${marks.size}")
        emit(HhEffect.Sound(SoundKind.MARK))
        val reached = s.marks.size < s.total && marks.size == s.total
        update { it.copy(card = null, marks = marks) }
        if (reached) showSnack(SnackKind.ALL_MARKED, SNACK_ACTION_MS)
    }

    private fun removeMark() {
        val s = _state.value
        val c = s.card ?: return
        HhLog.i("MARK remove ${c.poiId}")
        update { it.copy(card = null, marks = it.marks.filter { m -> m.poiId != c.poiId }) }
    }

    private fun showSnack(kind: SnackKind, ms: Long) {
        snackSerial++
        update { it.copy(snack = Snack(kind, now + ms, snackSerial)) }
    }

    private fun submitClicked() {
        val s = _state.value
        if (s.phase != Phase.INSPECTING) return
        if (s.marks.size < s.total) update { it.copy(dialog = DialogKind.SUBMIT, card = null, menuOpen = false) } else submit()
    }

    private fun submit() {
        val s = _state.value
        if (s.phase != Phase.INSPECTING) return
        val d = Scoring.debrief(s.layout, s.marks)
        val elapsed = elapsed(s)
        val previous = store.loadLast()
        store.saveLast(LastRound(System.currentTimeMillis(), d.score, d.max, d.found, d.typed, d.fixed, d.falseAlarms, elapsed, s.layout.seed, s.layout.walk))
        HhLog.i("DEBRIEF score=${d.score}/${d.max} found=${d.found} type=${d.typed} fix=${d.fixed} false=${d.falseAlarms} grade=${d.grade} elapsed_ms=$elapsed order=${d.order}")
        emit(HhEffect.Sound(SoundKind.REVEAL))
        update {
            it.copy(
                phase = Phase.DEBRIEF, debrief = d, detail = null, dialog = null, card = null, snack = null, hover = null,
                elapsedMs = elapsed, runningSinceMs = null, previous = previous, revealAtMs = now,
            )
        }
    }

    private fun dialogConfirmed() {
        when (_state.value.dialog) {
            DialogKind.SUBMIT -> {
                update { it.copy(dialog = null) }
                submit()
            }
            DialogKind.NEW_ROUND -> {
                update { it.copy(dialog = null) }
                newRound(RoundRules.newSeed(System.nanoTime()), _state.value.settings.walkMode, "new")
            }
            DialogKind.BLOCKED -> emit(HhEffect.ExitApp)
            null -> Unit
        }
    }

    private fun again() {
        val s = _state.value
        when (s.phase) {
            Phase.INSPECTING -> update { it.copy(dialog = DialogKind.NEW_ROUND, card = null, menuOpen = false) }
            Phase.DEBRIEF, Phase.READY -> newRound(RoundRules.newSeed(System.nanoTime()), s.settings.walkMode, "new")
            else -> Unit
        }
    }

    private fun resetClicked() {
        update { it.copy(menuOpen = false) }
        again()
    }

    /**
     * The debrief's replay key: the round just debriefed again, same seed (design `pois.roundRules`: "重玩这轮用同一种子";
     * S21's "disabled in the first round" only fits the stored previous round: the design station is asked; this build
     * replays the round on screen, the more useful reading for practice). Debrief only.
     */
    private fun replay() {
        val s = _state.value
        if (s.phase != Phase.DEBRIEF) return
        newRound(s.layout.seed, s.layout.walk, "replay")
    }

    /** S17 / S21: a fresh layout, flags and time cleared, the bay's heading kept, back to S03 without the coach. */
    private fun newRound(seed: Long, walk: Boolean, why: String) {
        update {
            it.copy(
                phase = if (it.phase == Phase.STARTING || it.phase == Phase.BLOCKED) it.phase else Phase.READY,
                layout = RoundRules.draw(seed, walk), marks = emptyList(), debrief = null, detail = null, card = null,
                dialog = null, snack = null, hover = null, elapsedMs = 0L, runningSinceMs = null, coach = false, menuOpen = false,
            )
        }
        logRound(why)
    }

    private fun openDetail(id: String, tour: Boolean) {
        val s = _state.value
        if (s.phase != Phase.DEBRIEF) return
        HhLog.i("DETAIL $id tour=$tour")
        update { it.copy(detail = DetailState(id, tour)) }
        bringToFront(id)
    }

    private fun stepDetail(step: Int) {
        val s = _state.value
        val d = s.debrief ?: return
        val cur = s.detail ?: return
        val list = if (cur.tour) d.missed else d.order
        if (list.isEmpty()) return
        val i = list.indexOf(cur.poiId).let { if (it < 0) 0 else it }
        val next = list[((i + step) % list.size + list.size) % list.size]
        openDetail(next, cur.tour)
    }

    /** Turns the bay about the viewer so the checkpoint is straight ahead (design `rules.bringToFront`). */
    private fun bringToFront(id: String) {
        val s = _state.value
        val bay = s.bay ?: return
        val poi = Catalog.get(id) ?: return
        val cam = s.camera
        val h = if (cam != null) Vec3(cam.x, 1.6f, cam.z) else head ?: return
        val f = if (cam != null) yawDir(cam.yawDeg) else headForward
        val delta = bringToFrontDelta(bay, poi.pos, h, f)
        HhLog.i("FRONT $id delta=${deg(delta)}")
        update { it.copy(bay = bay.turned(delta, h.x, h.z), baySerial = it.baySerial + 1) }
    }

    // =========================================================================================
    // debug hooks (design `qaHooks`)
    // =========================================================================================

    private fun debug(event: HhEvent) {
        when (event) {
            is HhEvent.DebugState -> debugState(event.id.uppercase(Locale.US))
            is HhEvent.DebugSeed -> {
                update { it.copy(frozen = true) }
                newRound(event.seed, _state.value.layout.walk, "seed")
            }
            is HhEvent.DebugCard -> {
                ensureInspecting()
                update { it.copy(frozen = true, card = null, dialog = null, menuOpen = false) }
                bringToFront(event.poiId.uppercase(Locale.US))
                openCard(event.poiId.uppercase(Locale.US))
            }
            is HhEvent.DebugHover -> update { it.copy(frozen = true, hover = event.poiId?.uppercase(Locale.US)) }
            is HhEvent.DebugMark -> {
                ensureInspecting()
                val id = event.poiId.uppercase(Locale.US)
                val poi = Catalog.get(id) ?: return
                val cat = event.category ?: poi.hazard(_state.value.layout.variantOf(id)).category
                update { it.copy(card = CardState(id, 2, cat, it.markOf(id) != null, ++cardSerial)) }
                fixPicked(event.fixIndex)
            }
            is HhEvent.DebugMarks -> {
                ensureInspecting()
                val s = _state.value
                val ids = s.layout.hazards.keys.toList() + s.layout.pois.map { it.id }.filter { !s.layout.isHazard(it) }
                val marks = ids.take(event.n.coerceIn(0, s.cap)).map { id -> Mark(id, Catalog.get(id)!!.hazard(s.layout.variantOf(id)).category, 0) }
                update { it.copy(marks = marks, card = null, frozen = true) }
                HhLog.i("MARK debug n=${marks.size}")
                if (marks.size == s.total) showSnack(SnackKind.ALL_MARKED, SNACK_ACTION_MS)
            }
            HhEvent.DebugResultPreset -> {
                ensureInspecting()
                val s = _state.value
                update { it.copy(marks = Scoring.presetMarks(s.layout), card = null, dialog = null, frozen = true) }
                submit()
            }
            is HhEvent.DebugStick -> {
                // one snap step, as if the stick had passed 0.6 (design `qaHooks` stick=1|-1)
                val x = event.x ?: return
                val s = _state.value
                val pivot = s.camera?.let { Vec3(it.x, 0f, it.z) } ?: head ?: s.bay?.let { Vec3(it.originX, 0f, it.originZ) } ?: return
                HhLog.i("STICK debug x=$x")
                stickTurn(if (x < 0f) STEP else -STEP, pivot)
            }
            is HhEvent.DebugNoFloor -> {
                update { it.copy(noFloor = event.on) }
                val s = _state.value
                val bay = s.bay
                val h = head
                if (bay != null && h != null) update { it.copy(bay = bay.copy(floorY = if (event.on || s.floorPlaneY == null) h.y - HEAD_ABOVE_FLOOR_M else s.floorPlaneY)) }
                HhLog.i("FLOOR debug nofloor=${event.on}")
            }
            is HhEvent.DebugBlocked -> {
                debugBlocked = event.on
                applyBlock()
            }
            is HhEvent.DebugWalk -> {
                saveSettings { it.copy(walkMode = event.on) }
                newRound(RoundRules.newSeed(System.nanoTime()), event.on, "walk")
            }
            is HhEvent.DebugFloorCm -> saveSettings { it.copy(floorOffsetCm = event.cm.coerceIn(-FLOOR_RANGE_CM, FLOOR_RANGE_CM)) }
            HhEvent.DebugCoachReset -> {
                saveSettings { it.copy(coachSeen = false) }
                update { it.copy(coach = it.phase == Phase.READY) }
            }
            HhEvent.DebugLastClear -> {
                store.saveLast(null)
                update { it.copy(previous = null) }
                HhLog.i("LAST cleared")
            }
            // camera= drives hover and panels from the simulated pose; it does not freeze hover (independent QA P2-5)
            is HhEvent.DebugCamera -> update { it.copy(camera = event.camera) }
            HhEvent.DebugClear -> {
                update { it.copy(frozen = false, camera = null, debugStick = null) }
                HhLog.i("CMD clear: live sensing again")
            }
            is HhEvent.DebugFace -> {
                val s = _state.value
                if (s.phase == Phase.DEBRIEF) openDetail(event.poiId.uppercase(Locale.US), false) else bringToFront(event.poiId.uppercase(Locale.US))
            }
            is HhEvent.DebugTurn -> {
                val s = _state.value
                val bay = s.bay ?: return
                val h = head ?: Vec3(bay.originX, 0f, bay.originZ)
                val t = bay.turned(Math.toRadians(event.deg.toDouble()).toFloat(), h.x, h.z)
                update { it.copy(bay = t, baySerial = it.baySerial + 1) }
                HhLog.i("TURN debug delta=${event.deg} yaw=${deg(t.yaw)}")
            }
            is HhEvent.DebugTestTexture -> update { it.copy(testTexture = event.on) }
            else -> Unit
        }
    }

    private fun ensureInspecting() {
        val s = _state.value
        if (s.phase == Phase.INSPECTING) return
        if (s.phase == Phase.DEBRIEF) newRound(s.layout.seed, s.layout.walk, "debug")
        if (_state.value.phase == Phase.READY) start()
        if (_state.value.phase == Phase.STARTING && _state.value.bay != null) update { it.copy(phase = Phase.INSPECTING, runningSinceMs = now) }
    }

    /** `state=Sxx`: straight to a designed state, frozen (design `qaHooks`; `clear=1` lets sensing drive again). */
    private fun debugState(id: String) {
        HhLog.i("CMD state $id")
        update { it.copy(frozen = true, menuOpen = false, dialog = if (it.dialog == DialogKind.BLOCKED) it.dialog else null, snack = null, hover = null, card = null) }
        val s = _state.value
        fun firstHazard(): String = _state.value.layout.hazards.keys.first()
        when (id) {
            "S01" -> update { it.copy(phase = Phase.STARTING, card = null, coach = false) }
            "S03" -> {
                if (s.phase != Phase.READY) newRound(s.layout.seed, s.layout.walk, "debug")
                update { it.copy(phase = Phase.READY, coach = false) }
            }
            "S20" -> {
                if (s.phase != Phase.READY) newRound(s.layout.seed, s.layout.walk, "debug")
                update { it.copy(phase = Phase.READY, coach = true) }
            }
            "S04" -> {
                if (s.phase == Phase.DEBRIEF) newRound(s.layout.seed, s.layout.walk, "debug")
                ensureInspecting()
                update { it.copy(card = null, hover = null) }
            }
            "S05" -> {
                ensureInspecting()
                bringToFront("P12")
                update { it.copy(card = null, hover = "P12") }
            }
            "S06" -> {
                ensureInspecting()
                bringToFront(firstHazard())
                openCard(firstHazard())
            }
            "S07" -> {
                ensureInspecting()
                update { it.copy(card = null) }
                val pid = firstHazard()
                bringToFront(pid)
                openCard(pid)
                val cat = Catalog.get(pid)!!.hazard(_state.value.layout.variantOf(pid)).category
                _state.value.card?.let { c -> update { it.copy(card = c.copy(step = 2, category = cat)) } }
            }
            "S08" -> {
                ensureInspecting()
                debug(HhEvent.DebugMarks(1))
            }
            "S09" -> {
                ensureInspecting()
                if (_state.value.marks.isEmpty()) debug(HhEvent.DebugMarks(1))
                update { it.copy(snack = null) }
                bringToFront(_state.value.marks.first().poiId)
                openCard(_state.value.marks.first().poiId)
            }
            "S10" -> debug(HhEvent.DebugTurn(-45f))
            "S11" -> {
                ensureInspecting()
                debug(HhEvent.DebugMarks(_state.value.total))
            }
            "S12" -> {
                ensureInspecting()
                debug(HhEvent.DebugMarks(_state.value.cap))
                update { it.copy(snack = null) }
                val free = _state.value.layout.pois.first { _state.value.markOf(it.id) == null }
                openCard(free.id)
            }
            "S13" -> {
                ensureInspecting()
                if (_state.value.marks.size >= _state.value.total) debug(HhEvent.DebugMarks(3))
                update { it.copy(snack = null) }
                submitClicked()
            }
            "S14" -> debug(HhEvent.DebugResultPreset)
            "S15" -> {
                if (_state.value.phase != Phase.DEBRIEF) debug(HhEvent.DebugResultPreset)
                val d = _state.value.debrief ?: return
                openDetail(d.missed.firstOrNull() ?: d.order.first(), false)
            }
            "S16" -> {
                if (_state.value.phase != Phase.DEBRIEF) debug(HhEvent.DebugResultPreset)
                onEvent(HhEvent.MissedClicked)
            }
            "S17" -> {
                ensureInspecting()
                again()
            }
            "S18" -> update { it.copy(menuOpen = true, card = null) }
            "S19" -> {
                update { it.copy(menuOpen = true, card = null) }
                debug(HhEvent.DebugFloorCm(10))
            }
            "S21" -> {
                if (_state.value.phase != Phase.DEBRIEF) debug(HhEvent.DebugResultPreset)
                replay()
            }
            "S22" -> debug(HhEvent.DebugWalk(true))
            "S23" -> debug(HhEvent.DebugBlocked(true))
            else -> HhLog.i("CMD state $id: not a reachable state (S02 / S24 come from sensing and the lifecycle)")
        }
        logState()
    }

    fun dumpJson(): String {
        val s = _state.value
        val b = s.bay
        return buildString {
            append("{")
            append("\"state\":\"${s.stateId()}\",\"tags\":\"${s.stateTags().trim()}\",\"phase\":\"${s.phase}\",")
            append("\"seed\":${s.layout.seed},\"walk\":${s.layout.walk},\"hazards\":\"${s.layout.hazards}\",")
            append("\"marks\":\"${s.marks.joinToString(";") { "${it.poiId},${it.category},${it.fixIndex}" }}\",")
            append("\"hover\":\"${s.hover}\",\"card\":\"${s.card}\",\"dialog\":\"${s.dialog}\",\"menu\":${s.menuOpen},\"snack\":\"${s.snack?.kind}\",")
            append("\"bay\":${if (b == null) "null" else String.format(Locale.US, "[%.3f,%.3f,%.3f,%.1f]", b.originX, b.floorY, b.originZ, Math.toDegrees(b.yaw.toDouble()))},")
            append("\"floorPlaneY\":${s.floorPlaneY},\"offsetCm\":${s.settings.floorOffsetCm},\"elapsedMs\":${elapsed(s)},")
            append("\"debrief\":\"${s.debrief?.let { "${it.score}/${it.max} ${it.grade} f${it.found} t${it.typed} x${it.fixed} fa${it.falseAlarms} order=${it.order}" }}\",")
            append("\"previous\":\"${s.previous?.let { "${it.score}/${it.max} seed=${it.seed}" }}\",\"settings\":\"${s.settings}\",\"frozen\":${s.frozen}")
            append("}")
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HhViewModel(HhGraph.settings(context.applicationContext)) as T
    }

    companion object {
        /** Floor fallback: eye height minus 1.60 m (design `rules.place`). */
        const val HEAD_ABOVE_FLOOR_M = 1.60f
        const val FLOOR_RANGE_CM = 30
        const val SNACK_MS = 3_000L
        const val SNACK_ACTION_MS = 6_000L
        private val STEP = io.embodit.hazardhunt.domain.usecase.StickStepper.STEP_RAD

        private fun deg(r: Float): String = String.format(Locale.US, "%.1f", Math.toDegrees(r.toDouble()))

        fun yawDir(yawDeg: Float): Vec3 {
            val y = Math.toRadians(yawDeg.toDouble()).toFloat()
            return Vec3(-sin(y), 0f, -cos(y))
        }

        /** Verdict of a checkpoint in the debrief (null: not part of the debrief). */
        fun verdictOf(s: HhUiState, poiId: String): Verdict? = s.debrief?.outcome(poiId)?.verdict
    }
}
