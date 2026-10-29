package io.embodit.hazardhunt.debug

import android.content.Context
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.domain.model.LanguagePref
import io.embodit.hazardhunt.platform.HhLog
import io.embodit.hazardhunt.ui.hh.CameraOverride
import io.embodit.hazardhunt.ui.hh.HhEvent
import io.embodit.hazardhunt.ui.hh.debug.HhProbe
import java.io.File
import java.util.Locale

/**
 * QA hooks of the debug build (design `qaHooks`). One log line (tag `HazardHunt`) answers every command.
 * Accepted forms: `--es cmd 'state=S04'`, `--es cmd 'state S04'`, `--es cmd state --es v S04`
 * (values with commas: `mark=P12,fire,0`, `camera=0,0,180,-30`).
 *
 * - `state=S01..S23`        straight to a designed state (frozen: hover / placement stop following sensing; `clear=1`)
 * - `seed=<n>`              new round with a fixed layout (`seed=1` is QA's reference layout)
 * - `poi=<Pxx>`             open the classify card on that checkpoint; `hover=<Pxx>|off` only highlight it
 * - `mark=<Pxx>[,<cat>[,<fixIndex>]]`  plant a flag (category floor|elec|fire|chem|mach|store|ppe; fix 0 = right)
 * - `marks=<n>`             the round's first n hazards flagged right (then safe ones: false alarms) - S11 / S12
 * - `result=preset`         6 found / 6 type / 5 fix / 2 false = 17 / 24 good, then the debrief
 * - `stick=1|-1`            one 45 degree snap turn (right / left); `turn=<deg>` any turn; `face=<Pxx>` bring it in front
 * - `nofloor=1|0`, `blocked=1|0`, `walk=1|0`, `lang=zh|en|system`, `floor=<cm>`, `timer=1|0`, `sound=1|0`
 * - `coach=reset`, `last=clear`, `camera=<x>,<z>,<yawDeg>,<pitchDeg>|off`, `clear=1`, `testtex=1|0` (restart to apply)
 * - `start`, `submit`, `menu`, `recenter`, `again`, `replay`, `missed`, `back`, `next`, `prev`, `exit`
 * - `ui <control>`          presses a panel control by name (start, recenter, submit, mute, more, cat_fire, fix_0..3,
 *                           card_close, card_cancel, card_remove, card_back, dialog_ok, dialog_cancel, missed, again,
 *                           replay, overview, detail_prev, detail_next, row_p12, lang_zh, lang_en, sound_on, timer_on,
 *                           walk_on, floor_plus, floor_minus, menu_reset, menu_exit, coach_ok, snack_submit, edit_p12)
 * - `dump`                  the model as one JSON line (`DUMP {...}`), also files/dump.json
 * - `hmd`, `panels`, `bay`, `poiview=<Pxx>` (bearing / pitch / distance of a checkpoint from the view)
 */
internal object DebugCommands {

    fun run(context: Context, extras: Map<String, String>) {
        val raw = extras["cmd"]?.trim() ?: return
        val tokens = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.isEmpty()) return
        var name = tokens.removeAt(0).lowercase(Locale.US)
        val args = ArrayList<String>()
        if (name.contains('=')) {
            val v = name.substringAfter('=')
            name = name.substringBefore('=')
            args += v.split(',').filter { it.isNotEmpty() }
        }
        for (t in tokens) args += t.split(',').filter { it.isNotEmpty() }
        extras["v"]?.let { args += it.split(',') }
        val host = HhProbe.host
        if (host == null) {
            HhLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            dispatch(context, host, name, args)
        } catch (t: Throwable) {
            HhLog.e("CMD $name failed: $t", t)
        }
    }

    private fun dispatch(context: Context, host: HhProbe.Host, name: String, args: List<String>) {
        val scene = host.scene()
        val first = args.getOrNull(0)?.lowercase(Locale.US) ?: ""
        fun num(i: Int): Float? = args.getOrNull(i)?.toFloatOrNull()
        fun on(): Boolean = first != "0" && first != "off" && first != "false"
        fun off(): Boolean = first == "off" || first == "none" || first == "null" || first == "0"
        when (name) {
            "state", "goto" -> host.dispatch(HhEvent.DebugState(args.getOrNull(0) ?: return HhLog.i("CMD state needs S01..S23")))
            "seed" -> host.dispatch(HhEvent.DebugSeed(args.getOrNull(0)?.toLongOrNull() ?: return HhLog.i("CMD seed needs a number")))
            "poi", "card" -> host.dispatch(HhEvent.DebugCard(args.getOrNull(0) ?: return HhLog.i("CMD poi needs Pxx")))
            "hover" -> host.dispatch(HhEvent.DebugHover(if (off()) null else args.getOrNull(0)))
            "mark" -> {
                val id = args.getOrNull(0) ?: return HhLog.i("CMD mark needs Pxx[,cat[,fix]]")
                host.dispatch(HhEvent.DebugMark(id, args.getOrNull(1)?.let { Category.fromTag(it) }, num(2)?.toInt() ?: 0))
            }
            "marks" -> host.dispatch(HhEvent.DebugMarks(num(0)?.toInt() ?: return HhLog.i("CMD marks needs n")))
            "result" -> host.dispatch(HhEvent.DebugResultPreset)
            "stick" -> host.dispatch(HhEvent.DebugStick(num(0) ?: return HhLog.i("CMD stick needs 1 or -1")))
            "turn" -> host.dispatch(HhEvent.DebugTurn(num(0) ?: 45f))
            "face" -> host.dispatch(HhEvent.DebugFace(args.getOrNull(0) ?: return HhLog.i("CMD face needs Pxx")))
            "nofloor" -> host.dispatch(HhEvent.DebugNoFloor(on()))
            "blocked" -> host.dispatch(HhEvent.DebugBlocked(on()))
            "walk" -> host.dispatch(HhEvent.DebugWalk(on()))
            "lang" -> host.dispatch(HhEvent.LanguageSelected(LanguagePref.fromTag(first)))
            "floor" -> host.dispatch(HhEvent.DebugFloorCm(num(0)?.toInt() ?: 0))
            "timer" -> host.dispatch(HhEvent.TimerSelected(on()))
            "sound" -> host.dispatch(HhEvent.SoundSelected(on()))
            "coach" -> host.dispatch(HhEvent.DebugCoachReset)
            "last" -> host.dispatch(HhEvent.DebugLastClear)
            "camera" ->
                if (off()) host.dispatch(HhEvent.DebugCamera(null))
                else host.dispatch(HhEvent.DebugCamera(CameraOverride(num(0) ?: 0f, num(1) ?: 0f, num(2) ?: 0f, num(3) ?: 0f)))
            "clear" -> host.dispatch(HhEvent.DebugClear)
            "testtex" -> host.dispatch(HhEvent.DebugTestTexture(on()))
            "start" -> host.dispatch(HhEvent.StartClicked)
            "submit" -> host.dispatch(HhEvent.SubmitClicked)
            "menu", "more" -> host.dispatch(HhEvent.MoreClicked)
            "recenter" -> host.dispatch(HhEvent.RecenterClicked)
            "again" -> host.dispatch(HhEvent.AgainClicked)
            "replay" -> host.dispatch(HhEvent.ReplayClicked)
            "missed" -> host.dispatch(HhEvent.MissedClicked)
            "back" -> host.dispatch(HhEvent.DetailBack)
            "next" -> host.dispatch(HhEvent.DetailNext)
            "prev" -> host.dispatch(HhEvent.DetailPrev)
            "exit" -> host.dispatch(HhEvent.ExitClicked)
            "ui" -> {
                val control = args.getOrNull(0) ?: return HhLog.i("CMD ui needs a control name")
                HhLog.i(if (host.pressControl(control)) "CMD ui $control pressed" else "CMD ui $control not composed (open its panel first)")
            }
            "dump" -> {
                val json = host.dumpJson()
                runCatching { File(context.filesDir, "dump.json").writeText(json) }
                HhLog.i("DUMP $json")
            }
            "hmd" -> HhLog.i(scene?.poseLine() ?: "POSE no scene")
            "panels", "bounds" -> scene?.panelLines()?.forEach { HhLog.i(it) } ?: HhLog.i("PANEL no scene")
            "bay" -> HhLog.i(scene?.bayLine() ?: "BAY no scene")
            "poiview" -> HhLog.i(scene?.poiLine(args.getOrNull(0)?.uppercase(Locale.US) ?: "P01") ?: "POI no scene")
            else -> HhLog.i("CMD $name unknown")
        }
    }
}
