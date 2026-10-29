package io.embodit.hazardhunt.ui.hh.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.Material
import io.embodit.hazardhunt.R
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.domain.model.LanguagePref
import io.embodit.hazardhunt.domain.usecase.RoundRules
import io.embodit.hazardhunt.ui.hh.DialogKind
import io.embodit.hazardhunt.ui.hh.HhEvent
import io.embodit.hazardhunt.ui.hh.HhUiState
import io.embodit.hazardhunt.ui.hh.SnackKind
import io.embodit.hazardhunt.ui.hh.debug.probe
import io.embodit.hazardhunt.ui.hh.scene.PanelSpec
import io.embodit.hazardhunt.ui.theme.HhColors

/**
 * The classify card (design `panels.classify`, 360 x 400): step 1 = seven category chips (3 long + 4 short), a pick
 * goes straight to step 2 = four fixes (shuffled per round); a pick there plants the flag. Same flow for safe
 * checkpoints (the user does not learn it was a false alarm).
 */
@Composable
internal fun ClassifyCard(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.CARD_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp), contentAlignment = Alignment.TopCenter) {
        val card = state.card
        if (!filled || card == null) return@Box
        val poi = Catalog.get(card.poiId) ?: return@Box
        val english = state.english
        val existing = state.markOf(card.poiId)
        Glass(w.dp, h.dp, corner = 28.dp) {
            if (card.step == 1) {
                Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = poi.name.of(english), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconKey("card_close", R.drawable.ic_close, rs(R.string.close)) { onEvent(HhEvent.CardClosed) }
                }
                Text(text = rs(R.string.q1), color = PicoTheme.colorScheme.labelPrimary, style = HhType.bodyLarge, maxLines = 1, modifier = Modifier.height(32.dp))
                val long = listOf(Category.ELEC, Category.CHEM, Category.MACH)
                val short = listOf(Category.FLOOR, Category.FIRE, Category.STORE, Category.PPE)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in long) CategoryChip(c, c.label(english), card.category == c, 104.dp) { onEvent(HhEvent.CategoryPicked(c)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in short) CategoryChip(c, c.label(english), card.category == c, 76.dp) { onEvent(HhEvent.CategoryPicked(c)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WordButton("card_cancel", rs(R.string.cancel_mark), { onEvent(HhEvent.CardClosed) })
                    Spacer(modifier = Modifier.weight(1f))
                    if (card.editing) WordButton("card_remove", rs(R.string.remove_mark), { onEvent(HhEvent.MarkRemoved) })
                }
            } else {
                val category = card.category ?: return@Glass
                Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconKey("card_back", R.drawable.ic_back, rs(R.string.back)) { onEvent(HhEvent.CardBack) }
                    Text(text = rs(R.string.q2), color = PicoTheme.colorScheme.labelPrimary, style = HhType.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Tag(category.label(english), iconRes = categoryIcon(category))
                }
                val variant = state.layout.variantOf(card.poiId)
                val spec = poi.hazard(variant)
                for (i in RoundRules.fixOrder(state.layout.seed, card.poiId)) {
                    val chosen = existing != null && existing.fixIndex == i && existing.category == category
                    FixOption("fix_$i", spec.fixes[i].of(english), chosen) { onEvent(HhEvent.FixPicked(i)) }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = rs(R.string.footnote), color = PicoTheme.colorScheme.labelSecondary, style = HhType.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(48.dp))
            }
        }
    }
}

/** A fix option (design `spatialUiMap.fixOptions`): tertiary fill, left-aligned body text, two lines at most. */
@Composable
private fun FixOption(name: String, text: String, chosen: Boolean, onClick: () -> Unit) {
    val container = if (chosen) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillTertiary
    val content = if (chosen) HhColors.PlateInk else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = 56.dp, minWidth = 328.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(text = text, color = content, style = HhType.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The More menu (design `panels.menu`, 300 x 384, above the inspect panel): language, sound, floor height, timer,
 * walk mode (+ note), reset bay, quit.
 */
@Composable
internal fun MoreMenu(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.MENU_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp)) {
        if (!filled || !state.menuOpen) return@Box
        val s = state.settings
        Glass(w.dp, h.dp, corner = 24.dp, padding = 12.dp, spacing = 4.dp) {
            // language: the three segments take the whole row (no room for a label beside them in either language)
            Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painter = painterResource(R.drawable.ic_globe), contentDescription = rs(R.string.menu_language), modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Segments(
                    listOf("lang_zh" to rs(R.string.lang_zh), "lang_en" to rs(R.string.lang_en), "lang_system" to rs(R.string.lang_system)),
                    s.language.ordinal,
                    { onEvent(HhEvent.LanguageSelected(LanguagePref.entries[it])) },
                    width = 246.dp,
                )
            }
            MenuRow(if (s.soundOn) R.drawable.ic_sound else R.drawable.ic_sound_off, rs(R.string.menu_sound)) {
                Segments(listOf("sound_on" to rs(R.string.on), "sound_off" to rs(R.string.off)), if (s.soundOn) 0 else 1, { onEvent(HhEvent.SoundSelected(it == 0)) }, width = 112.dp)
            }
            MenuRow(R.drawable.ic_floor, rs(R.string.menu_floor)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconKey("floor_minus", R.drawable.ic_minus, "-2 cm", width = 44.dp, height = 36.dp) { onEvent(HhEvent.FloorStep(-2)) }
                    Text(text = rs(R.string.floor_value, s.floorOffsetCm), color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                    IconKey("floor_plus", R.drawable.ic_plus, "+2 cm", width = 44.dp, height = 36.dp) { onEvent(HhEvent.FloorStep(2)) }
                }
            }
            MenuRow(R.drawable.ic_timer, rs(R.string.menu_timer)) {
                Segments(listOf("timer_off" to rs(R.string.off), "timer_on" to rs(R.string.on)), if (s.timerOn) 1 else 0, { onEvent(HhEvent.TimerSelected(it == 1)) }, width = 112.dp)
            }
            MenuRow(R.drawable.ic_walk, rs(R.string.menu_walk)) {
                Segments(listOf("walk_off" to rs(R.string.off), "walk_on" to rs(R.string.on)), if (s.walkMode) 1 else 0, { onEvent(HhEvent.WalkSelected(it == 1)) }, width = 112.dp)
            }
            Text(text = rs(R.string.walk_sub), color = PicoTheme.colorScheme.labelSecondary, style = HhType.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(16.dp))
            WordButton("menu_reset", rs(R.string.menu_reset), { onEvent(HhEvent.ResetBayClicked) }, iconRes = R.drawable.ic_retry, height = 44.dp, modifier = Modifier.fillMaxWidth(), style = HhType.labelLarge)
            WordButton("menu_exit", rs(R.string.exit), { onEvent(HhEvent.ExitClicked) }, iconRes = R.drawable.ic_exit, height = 44.dp, modifier = Modifier.fillMaxWidth(), style = HhType.labelLarge)
        }
    }
}

@Composable
private fun MenuRow(iconRes: Int, label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
        Text(text = label, color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        content()
    }
}

/** The snackbar (design `panels.snackbar`, 360 x 56): icon + one line + an optional button. */
@Composable
internal fun SnackPanel(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.SNACK_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp)) {
        val snack = state.snack
        if (!filled || snack == null) return@Box
        Glass(w.dp, h.dp, corner = 28.dp, padding = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(painter = painterResource(if (snack.kind == SnackKind.WALK) R.drawable.ic_walk else R.drawable.ic_flag), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
                val text =
                    when (snack.kind) {
                        SnackKind.ALL_MARKED -> rs(R.string.snack_all, state.marks.size)
                        SnackKind.LIMIT -> rs(R.string.snack_limit, state.cap)
                        SnackKind.WALK -> rs(R.string.snack_walk)
                    }
                Text(text = text, color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (snack.kind == SnackKind.ALL_MARKED) WordButton("snack_submit", rs(R.string.dialog_submit), { onEvent(HhEvent.SnackAction) }, primary = true, minWidth = 80.dp, height = 40.dp, style = HhType.labelLarge)
            }
        }
    }
}

/** Dialogs (design `panels.dialog`, 400 x 208, self-built: no AlertDialog in a Stage): submit, new round, blocked. */
@Composable
internal fun DialogPanel(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.DIALOG_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp)) {
        val kind = state.dialog
        if (!filled || kind == null) return@Box
        Glass(w.dp, h.dp, corner = 32.dp, material = Material.Thick, padding = 24.dp, spacing = 8.dp) {
            val (title, body) =
                when (kind) {
                    DialogKind.SUBMIT -> rs(R.string.submit_title) to rs(R.string.confirm_submit, (state.total - state.marks.size).coerceAtLeast(0))
                    DialogKind.NEW_ROUND -> rs(R.string.new_title) to rs(R.string.confirm_new)
                    DialogKind.BLOCKED -> rs(R.string.blocked_title) to blockText(state.blockReason)
                }
            Text(text = title, color = PicoTheme.colorScheme.labelPrimary, style = HhType.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = body, color = PicoTheme.colorScheme.labelSecondary, style = HhType.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(52.dp))
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                when (kind) {
                    DialogKind.SUBMIT -> {
                        WordButton("dialog_cancel", rs(R.string.dialog_cancel), { onEvent(HhEvent.DialogCancelled) }, minWidth = 120.dp)
                        WordButton("dialog_ok", rs(R.string.dialog_submit), { onEvent(HhEvent.DialogConfirmed) }, primary = true, minWidth = 120.dp)
                    }
                    DialogKind.NEW_ROUND -> {
                        WordButton("dialog_cancel", rs(R.string.dialog_cancel), { onEvent(HhEvent.DialogCancelled) }, minWidth = 120.dp)
                        WordButton("dialog_ok", rs(R.string.dialog_reset), { onEvent(HhEvent.DialogConfirmed) }, primary = true, minWidth = 120.dp)
                    }
                    DialogKind.BLOCKED -> WordButton("dialog_exit", rs(R.string.exit), { onEvent(HhEvent.ExitClicked) }, primary = true, minWidth = 120.dp, iconRes = R.drawable.ic_exit)
                }
            }
        }
    }
}

/** First-run coach bubble (design `panels.coach`, 320 x 120): three small icons with lines + Got it. */
@Composable
internal fun CoachPanel(state: HhUiState, filled: Boolean, onEvent: (HhEvent) -> Unit) {
    val (w, h) = PanelSpec.COACH_SIZE
    Box(modifier = Modifier.size(w.dp, h.dp)) {
        if (!filled || !state.coach) return@Box
        Glass(w.dp, h.dp, corner = 24.dp, padding = 12.dp, spacing = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoachLine(R.drawable.ic_eye_dwell, rs(R.string.coach_1))
                    CoachLine(R.drawable.ic_pinch, rs(R.string.coach_2))
                    CoachLine(R.drawable.ic_stick, rs(R.string.coach_3))
                }
                IconKey("coach_ok", R.drawable.ic_check, rs(R.string.got_it), width = 48.dp, height = 48.dp) { onEvent(HhEvent.CoachDismissed) }
            }
        }
    }
}

@Composable
private fun CoachLine(iconRes: Int, text: String) {
    Row(modifier = Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp), tint = PicoTheme.colorScheme.labelPrimary)
        Text(text = text, color = PicoTheme.colorScheme.labelPrimary, style = HhType.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Suppress("unused")
private val keepShape = RoundedCornerShape(0.dp)
