package io.embodit.hazardhunt.ui.hh.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.hazardhunt.R
import io.embodit.hazardhunt.domain.model.BlockReason
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.usecase.Grade
import io.embodit.hazardhunt.domain.usecase.Hint
import io.embodit.hazardhunt.domain.usecase.Outcome
import io.embodit.hazardhunt.domain.usecase.Verdict
import io.embodit.hazardhunt.domain.usecase.hintFor
import io.embodit.hazardhunt.ui.hh.HhEvent
import io.embodit.hazardhunt.ui.hh.HhUiState
import io.embodit.hazardhunt.ui.hh.Phase
import io.embodit.hazardhunt.ui.hh.debug.probe
import io.embodit.hazardhunt.ui.hh.scene.PanelSpec
import io.embodit.hazardhunt.ui.theme.HhColors

/**
 * The one resident panel (design `panels.inspect`, 400 dp wide at 0.75, lazy follow at the lower left of the view).
 * Layouts by state: compact (S01 / S23) 120, ready (S03) 224, inspecting (S04 ...) 424, strip while the card is open
 * 56, debrief (S14) 488, detail (S15 / S16) 488. The panel box is 488 high; the glass is drawn from its top.
 */
@Composable
internal fun InspectPanel(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.INSPECT_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp), contentAlignment = Alignment.TopCenter) {
        if (!filled) return@Box
        when {
            state.phase == Phase.STARTING || state.phase == Phase.BLOCKED -> Compact(state)
            state.card != null -> Strip(state, onEvent)
            state.phase == Phase.READY -> Ready(state, onEvent)
            state.phase == Phase.INSPECTING -> Inspecting(state, onEvent)
            state.phase == Phase.DEBRIEF && state.detail != null -> Detail(state, onEvent)
            else -> DebriefView(state, onEvent)
        }
    }
}

@Composable
private fun TitleRow(height: Dp = 40.dp, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun MuteKey(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    val on = state.settings.soundOn
    IconKey("mute", if (on) R.drawable.ic_sound else R.drawable.ic_sound_off, rs(if (on) R.string.mute else R.string.unmute)) { onEvent(HhEvent.MuteToggled) }
}

@Composable
private fun MoreKey(onEvent: (HhEvent) -> Unit, enabled: Boolean = true) {
    IconKey("more", R.drawable.ic_more, rs(R.string.more), enabled = enabled) { onEvent(HhEvent.MoreClicked) }
}

@Composable
private fun Compact(state: HhUiState) {
    Glass(400.dp, 120.dp) {
        TitleRow {
            Text(text = rs(R.string.title), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleLarge, maxLines = 1)
        }
        Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.phase == Phase.STARTING) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PicoTheme.colorScheme.labelPrimary, progressSize = CircularProgressDefaults.circleProgressSize(28.dp))
                Text(text = rs(R.string.starting), color = PicoTheme.colorScheme.labelSecondary, style = HhType.bodyLarge, maxLines = 1)
            } else {
                Icon(painter = painterResource(R.drawable.ic_unavailable), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Text(text = blockText(state.blockReason), color = PicoTheme.colorScheme.labelSecondary, style = HhType.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun blockText(reason: BlockReason?): String =
    when (reason) {
        BlockReason.WITHOUT_PERMISSION -> rs(R.string.blocked_permission)
        BlockReason.DEVICE_NOT_SUPPORTED -> rs(R.string.blocked_device)
        else -> rs(R.string.blocked_full_space)
    }

/** S03: title, intro (three lines for English / walk mode), the hint row, [Start] [Recenter] (256; design 224). */
@Composable
private fun Ready(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    Glass(400.dp, 256.dp) {
        TitleRow {
            Text(text = rs(R.string.title), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
            MuteKey(state, onEvent)
            MoreKey(onEvent)
        }
        val body = if (state.walkRound) rs(R.string.ready_walk, state.total) else rs(R.string.ready_body, state.total)
        Text(text = body, color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(64.dp))
        HintRow(if (state.walkRound) rs(R.string.hint_walk) else rs(R.string.ready_hint))
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WordButton("start", rs(R.string.start), { onEvent(HhEvent.StartClicked) }, primary = true, modifier = Modifier.weight(1f))
            WordButton("recenter", rs(R.string.recenter), { onEvent(HhEvent.RecenterClicked) }, iconRes = R.drawable.ic_relocate, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painter = painterResource(R.drawable.ic_stick), contentDescription = null, modifier = Modifier.size(16.dp), tint = PicoTheme.colorScheme.labelSecondary)
        Text(text = text, color = PicoTheme.colorScheme.labelSecondary, style = HhType.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** S04: count + timer + mute + more; hint by priority; the flag list (6 rows visible, scrolls); [Submit] [Recenter]. */
@Composable
private fun Inspecting(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    val english = state.english
    Glass(400.dp, 424.dp) {
        TitleRow {
            Text(text = rs(R.string.count, state.marks.size, state.total), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            if (state.settings.timerOn) Text(text = mmss(state.elapsedNow), color = PicoTheme.colorScheme.labelSecondary, style = HhType.labelLarge, maxLines = 1)
            MuteKey(state, onEvent)
            MoreKey(onEvent)
        }
        val hint =
            when (hintFor(state.walkRound, state.marks.size, state.total)) {
                Hint.WALK -> rs(R.string.hint_walk)
                Hint.FIRST -> rs(R.string.hint_first)
                Hint.TURN -> rs(R.string.hint_turn)
                Hint.SUBMIT -> rs(R.string.hint_submit)
            }
        HintRow(hint)
        Column(modifier = Modifier.fillMaxWidth().height(232.dp).clipToBounds().verticalScroll(rememberScrollState())) {
            if (state.marks.isEmpty()) {
                Text(text = rs(R.string.list_empty), color = PicoTheme.colorScheme.labelSecondary, style = HhType.bodyMedium, modifier = Modifier.height(40.dp))
            }
            state.marks.forEachIndexed { i, m ->
                val poi = Catalog.get(m.poiId) ?: return@forEachIndexed
                Row(modifier = Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberDot(i + 1)
                    Text(text = poi.name.of(english), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Tag(m.category.label(english), iconRes = categoryIcon(m.category))
                    IconKey("edit_${m.poiId.lowercase()}", R.drawable.ic_edit, rs(R.string.edit), width = 48.dp, height = 36.dp) { onEvent(HhEvent.EditMark(m.poiId)) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WordButton("submit", rs(R.string.submit), { onEvent(HhEvent.SubmitClicked) }, primary = true, modifier = Modifier.weight(1f))
            WordButton("recenter", rs(R.string.recenter), { onEvent(HhEvent.RecenterClicked) }, iconRes = R.drawable.ic_relocate, modifier = Modifier.weight(1f))
        }
    }
}

/** While the classify card is open the panel shrinks to one row (design `rules.panelCollapse`). */
@Composable
private fun Strip(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    Glass(400.dp, 56.dp, padding = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = rs(R.string.count, state.marks.size, state.total), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            MuteKey(state, onEvent)
        }
    }
}

@Composable
internal fun gradeText(g: Grade): String =
    when (g) {
        Grade.EXCELLENT -> rs(R.string.grade_excellent)
        Grade.GOOD -> rs(R.string.grade_good)
        Grade.AGAIN -> rs(R.string.grade_again)
    }

internal fun verdictColor(v: Verdict) =
    when (v) {
        Verdict.GREEN -> HhColors.Passable
        Verdict.YELLOW -> HhColors.Alert
        Verdict.RED -> HhColors.Error
        Verdict.GREY -> HhColors.Grey
    }

/** S14: score, grade, time; the three chips (+ false alarms); the hazard rows; last time; actions (488). */
@Composable
private fun DebriefView(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    val d = state.debrief ?: return
    val english = state.english
    Glass(400.dp, 488.dp) {
        TitleRow(height = 56.dp) {
            Text(text = "${d.score} / ${d.max}", color = PicoTheme.colorScheme.labelPrimary, style = HhType.headline, maxLines = 1)
            Text(text = gradeText(d.grade), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleMedium, maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = mmss(state.elapsedMs), color = PicoTheme.colorScheme.labelSecondary, style = HhType.labelLarge, maxLines = 1)
            MuteKey(state, onEvent)
            MoreKey(onEvent)
        }
        Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(rs(R.string.chip_found, d.found, state.total))
            Tag(rs(R.string.chip_type, d.typed))
            Tag(rs(R.string.chip_fix, d.fixed))
            if (d.falseAlarms > 0) Tag(rs(R.string.chip_false, d.falseAlarms))
        }
        Column(modifier = Modifier.fillMaxWidth().height(256.dp).clipToBounds().verticalScroll(rememberScrollState())) {
            for (o in d.hazardOutcomes) DebriefRow(o, english) { onEvent(HhEvent.DebriefRow(o.poiId)) }
        }
        val prev = state.previous
        Text(
            text = if (prev == null) rs(R.string.first_round) else rs(R.string.last_line, prev.score, prev.max, mmss(prev.elapsedMs)),
            color = PicoTheme.colorScheme.labelSecondary,
            style = HhType.labelMedium,
            maxLines = 1,
            modifier = Modifier.height(24.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val missed = d.missed.isNotEmpty()
            WordButton("missed", if (missed) rs(R.string.missed) else rs(R.string.all_found), { onEvent(HhEvent.MissedClicked) }, primary = missed, enabled = missed, modifier = Modifier.weight(1f))
            WordButton("again", rs(R.string.again), { onEvent(HhEvent.AgainClicked) }, modifier = Modifier.weight(1f))
            IconKey("replay", R.drawable.ic_retry, rs(R.string.replay), width = 56.dp, height = 48.dp) { onEvent(HhEvent.ReplayClicked) }
        }
    }
}

@Composable
private fun DebriefRow(o: Outcome, english: Boolean, onClick: () -> Unit) {
    val poi = Catalog.get(o.poiId) ?: return
    val spec = poi.hazard(o.variant)
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).probe("row_${o.poiId.lowercase()}", onClick).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VerdictDot(verdictColor(o.verdict))
        Text(text = spec.name.of(english), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Mini(o.found)
        Mini(o.typeOk)
        Mini(o.fixOk)
    }
}

@Composable
private fun Mini(ok: Boolean) {
    Icon(
        painter = painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_cross),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = if (ok) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
    )
}

/** S15 / S16: back + name; verdict row; right type; right fix; your pick; basis; previous / next + overview. */
@Composable
private fun Detail(state: HhUiState, onEvent: (HhEvent) -> Unit) {
    val d = state.debrief ?: return
    val det = state.detail ?: return
    val english = state.english
    val o = d.outcome(det.poiId) ?: return
    val poi = Catalog.get(det.poiId) ?: return
    val spec = poi.hazard(o.variant)
    Glass(400.dp, 488.dp) {
        TitleRow {
            IconKey("detail_back", R.drawable.ic_back, rs(R.string.back)) { onEvent(HhEvent.DetailBack) }
            Text(text = if (o.hazard) spec.name.of(english) else poi.name.of(english), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            MuteKey(state, onEvent)
        }
        Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerdictDot(verdictColor(o.verdict))
            if (o.hazard) {
                Text(text = "${rs(R.string.col_found)} ${mark(o.found)}  ${rs(R.string.col_type)} ${mark(o.typeOk)}  ${rs(R.string.col_fix)} ${mark(o.fixOk)}", color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1)
            } else {
                Text(text = rs(R.string.false_title), color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (det.tour) Tag(rs(R.string.tour_title, d.missed.indexOf(det.poiId) + 1, d.missed.size))
        }
        if (o.hazard) {
            Text(text = rs(R.string.right_type, spec.category.label(english)), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyLarge, maxLines = 1, modifier = Modifier.height(28.dp))
            Text(text = rs(R.string.right_fix, spec.fixes[0].of(english)), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(72.dp))
            val m = o.mark
            Text(
                text = if (m == null) rs(R.string.not_flagged) else rs(R.string.your_pick, m.category.label(english), spec.fixes.getOrNull(m.fixIndex)?.of(english) ?: ""),
                color = PicoTheme.colorScheme.labelPrimary,
                style = HhType.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
            Text(text = rs(R.string.basis, spec.basis.of(english)), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(60.dp))
        } else {
            Text(text = rs(R.string.false_body, poi.safe.of(english)), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyLarge, maxLines = 3, modifier = Modifier.fillMaxWidth().height(232.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconKey("detail_prev", R.drawable.ic_prev, rs(R.string.prev), width = 56.dp, height = 48.dp) { onEvent(HhEvent.DetailPrev) }
            IconKey("detail_next", R.drawable.ic_next, rs(R.string.next), width = 56.dp, height = 48.dp) { onEvent(HhEvent.DetailNext) }
            Spacer(modifier = Modifier.weight(1f))
            WordButton("overview", rs(R.string.overview), { onEvent(HhEvent.DetailBack) }, primary = true, minWidth = 128.dp)
        }
    }
}

private fun mark(ok: Boolean): String = if (ok) "✓" else "✗"
