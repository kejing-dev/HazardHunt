package io.embodit.hazardhunt.ui.hh

import io.embodit.hazardhunt.domain.model.BlockReason
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.domain.model.LanguagePref
import io.embodit.hazardhunt.domain.model.LastRound
import io.embodit.hazardhunt.domain.model.Settings
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.usecase.BayPose
import io.embodit.hazardhunt.domain.usecase.Debrief
import io.embodit.hazardhunt.domain.usecase.Mark
import io.embodit.hazardhunt.domain.usecase.RoundLayout

/** Main states (design `stateModel.rule`): S01, S03, S04, S14, S23. Overlays live in their own fields. */
enum class Phase { STARTING, READY, INSPECTING, DEBRIEF, BLOCKED }

/** The classify card (S06 / S07 / S09): opened on [poiId]; [editing] = the checkpoint already carries a flag. */
data class CardState(val poiId: String, val step: Int, val category: Category?, val editing: Boolean, val serial: Int)

enum class DialogKind { SUBMIT, NEW_ROUND, BLOCKED }

enum class SnackKind { ALL_MARKED, LIMIT, WALK }

data class Snack(val kind: SnackKind, val untilMs: Long, val serial: Int)

/** Debrief detail (S15) or the missed tour (S16, only red rows). */
data class DetailState(val poiId: String, val tour: Boolean)

/** A simulated head pose for QA (`camera=`): Stage x / z, yaw and pitch in degrees. */
data class CameraOverride(val x: Float, val z: Float, val yawDeg: Float, val pitchDeg: Float)

data class HhUiState(
    val phase: Phase = Phase.STARTING,
    val settings: Settings = Settings(),
    val english: Boolean = false,
    val layout: RoundLayout = RoundLayout(1L, false, emptyMap()),
    val marks: List<Mark> = emptyList(),
    val debrief: Debrief? = null,
    val detail: DetailState? = null,
    /** The previous round (shown as "last time" in the debrief and used by "replay last round"). */
    val previous: LastRound? = null,
    val bay: BayPose? = null,
    /** Bumped for every bay pose change that must not be smoothed (placement, turn, bring to front). */
    val baySerial: Int = 0,
    /** Bumped when the scene must place the bay at the current head pose. */
    val placeSerial: Int = 0,
    val floorPlaneY: Float? = null,
    val hover: String? = null,
    val card: CardState? = null,
    val menuOpen: Boolean = false,
    val dialog: DialogKind? = null,
    val snack: Snack? = null,
    val coach: Boolean = false,
    val blockReason: BlockReason? = null,
    /** Inspection time: accumulated ms plus the running stretch since [runningSinceMs]. */
    val elapsedMs: Long = 0L,
    val runningSinceMs: Long? = null,
    val nowMs: Long = 0L,
    val revealAtMs: Long = 0L,
    val sceneReady: Boolean = false,
    // debug
    val frozen: Boolean = false,
    val camera: CameraOverride? = null,
    val debugStick: Float? = null,
    val noFloor: Boolean = false,
    val testTexture: Boolean = false,
) {
    val total: Int
        get() = layout.total

    val cap: Int
        get() = layout.cap

    val elapsedNow: Long
        get() = elapsedMs + (runningSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)

    fun markOf(poiId: String): Mark? = marks.firstOrNull { it.poiId == poiId }

    /** Flag number of a checkpoint (1-based, in marking order), or 0. */
    fun flagNumber(poiId: String): Int = marks.indexOfFirst { it.poiId == poiId } + 1

    val walkRound: Boolean
        get() = layout.walk

    /** Hover outlines are paused while a card, the menu or a dialog is open (design `rules.hover`). */
    val hoverAllowed: Boolean
        get() = phase == Phase.INSPECTING && card == null && !menuOpen && dialog == null

    val language: LanguagePref
        get() = settings.language

    /** The design state id for logs and QA (overlay first). */
    fun stateId(): String =
        when {
            phase == Phase.BLOCKED -> "S23"
            dialog == DialogKind.SUBMIT -> "S13"
            dialog == DialogKind.NEW_ROUND -> "S17"
            menuOpen -> "S18"
            card != null && card.editing && card.step == 1 -> "S09"
            card != null && card.step == 2 -> "S07"
            card != null -> "S06"
            snack?.kind == SnackKind.ALL_MARKED -> "S11"
            snack?.kind == SnackKind.LIMIT -> "S12"
            phase == Phase.DEBRIEF && detail?.tour == true -> "S16"
            phase == Phase.DEBRIEF && detail != null -> "S15"
            phase == Phase.DEBRIEF -> "S14"
            phase == Phase.INSPECTING && hover != null -> "S05"
            phase == Phase.INSPECTING -> "S04"
            phase == Phase.READY && coach -> "S20"
            phase == Phase.READY -> "S03"
            else -> "S01"
        }

    fun stateTags(): String =
        buildString {
            if (walkRound && (phase == Phase.READY || phase == Phase.INSPECTING)) append(" +S22")
            if (frozen) append(" frozen")
        }
}

sealed interface HhEvent {
    // scene
    data object SceneReady : HhEvent

    data class HeadSample(val position: Vec3, val forward: Vec3) : HhEvent

    data class Placed(val head: Vec3, val forward: Vec3, val headY: Float) : HhEvent

    data class FloorChanged(val y: Float?) : HhEvent

    data class BlockChanged(val reason: BlockReason?) : HhEvent

    data class StickTurn(val delta: Float, val pivot: Vec3) : HhEvent

    data class HoverChanged(val poiId: String?) : HhEvent

    data class PoiTapped(val poiId: String, val flag: Boolean) : HhEvent

    data object TappedOutside : HhEvent

    data class Tick(val nowMs: Long) : HhEvent

    data object WentBackground : HhEvent

    data object CameForeground : HhEvent

    // panels
    data object StartClicked : HhEvent

    data object RecenterClicked : HhEvent

    data object SubmitClicked : HhEvent

    data object MoreClicked : HhEvent

    data object MuteToggled : HhEvent

    data object CoachDismissed : HhEvent

    data class CategoryPicked(val category: Category) : HhEvent

    data class FixPicked(val fixIndex: Int) : HhEvent

    data object CardBack : HhEvent

    data object CardClosed : HhEvent

    data object MarkRemoved : HhEvent

    data class EditMark(val poiId: String) : HhEvent

    data object DialogConfirmed : HhEvent

    data object DialogCancelled : HhEvent

    data object SnackAction : HhEvent

    data class DebriefRow(val poiId: String) : HhEvent

    data object MissedClicked : HhEvent

    data object AgainClicked : HhEvent

    data object ReplayClicked : HhEvent

    data object DetailPrev : HhEvent

    data object DetailNext : HhEvent

    data object DetailBack : HhEvent

    data class LanguageSelected(val language: LanguagePref) : HhEvent

    data class SoundSelected(val on: Boolean) : HhEvent

    data class FloorStep(val cm: Int) : HhEvent

    data class TimerSelected(val on: Boolean) : HhEvent

    data class WalkSelected(val on: Boolean) : HhEvent

    data object ResetBayClicked : HhEvent

    data object ExitClicked : HhEvent

    // debug hooks (design `qaHooks`)
    data class DebugState(val id: String) : HhEvent

    data class DebugSeed(val seed: Long) : HhEvent

    data class DebugCard(val poiId: String) : HhEvent

    data class DebugHover(val poiId: String?) : HhEvent

    data class DebugMark(val poiId: String, val category: Category?, val fixIndex: Int) : HhEvent

    data class DebugMarks(val n: Int) : HhEvent

    data object DebugResultPreset : HhEvent

    data class DebugStick(val x: Float?) : HhEvent

    data class DebugNoFloor(val on: Boolean) : HhEvent

    data class DebugBlocked(val on: Boolean) : HhEvent

    data class DebugWalk(val on: Boolean) : HhEvent

    data class DebugFloorCm(val cm: Int) : HhEvent

    data object DebugCoachReset : HhEvent

    data object DebugLastClear : HhEvent

    data class DebugCamera(val camera: CameraOverride?) : HhEvent

    data object DebugClear : HhEvent

    data class DebugFace(val poiId: String) : HhEvent

    data class DebugTurn(val deg: Float) : HhEvent

    data class DebugTestTexture(val on: Boolean) : HhEvent
}

enum class SoundKind { MARK, PAGE, REVEAL, FAIL }

sealed interface HhEffect {
    data object ExitApp : HhEffect

    data class Sound(val kind: SoundKind) : HhEffect
}
