package io.embodit.hazardhunt.ui.hh.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.hazardhunt.ui.hh.HhEvent
import io.embodit.hazardhunt.ui.hh.HhUiState
import io.embodit.hazardhunt.ui.hh.debug.LocalProbePanel
import io.embodit.hazardhunt.ui.hh.scene.HhScene
import io.embodit.hazardhunt.ui.hh.scene.PanelSpec
import kotlinx.coroutines.delay

/**
 * The Stage content: one full-size SpatialView carrying the 3D scene and the panel pool. Every attachment panel is
 * declared here once, with a fixed size, whether it is showing or not; the scene parents each panel entity under its
 * holder and shows or hides the holder (design: single panel with popup; no WindowContainer, no planar window).
 */
@Composable
internal fun HhStage(state: HhUiState, scene: HhScene, onEvent: (HhEvent) -> Unit) {
    val context = LocalContext.current
    val bindTick = scene.bindTick.intValue

    SpatialView(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectSpatialTapGesture(context = context, targetedToEntity = TargetEntity.any()) { value -> scene.onSpatialTap(value) }
                },
        attachments = {
            AttachmentPanel(PanelSpec.INSPECT) {
                PoolPanel(PanelSpec.INSPECT, scene, refresh = listOf(state.language, state.phase, state.card == null, state.detail == null)) { inView -> InspectPanel(state, inView, onEvent) }
            }
            AttachmentPanel(PanelSpec.CARD) {
                PoolPanel(PanelSpec.CARD, scene, refresh = listOf(state.card?.serial, state.card?.step)) { inView -> ClassifyCard(state, inView, onEvent) }
            }
            AttachmentPanel(PanelSpec.MENU) {
                PoolPanel(PanelSpec.MENU, scene, refresh = listOf(state.language, state.menuOpen)) { inView -> MoreMenu(state, inView, onEvent) }
            }
            AttachmentPanel(PanelSpec.SNACK) {
                PoolPanel(PanelSpec.SNACK, scene, refresh = state.snack?.serial) { inView -> SnackPanel(state, inView, onEvent) }
            }
            AttachmentPanel(PanelSpec.DIALOG) {
                PoolPanel(PanelSpec.DIALOG, scene, refresh = state.dialog) { inView -> DialogPanel(state, inView, onEvent) }
            }
            AttachmentPanel(PanelSpec.COACH) {
                PoolPanel(PanelSpec.COACH, scene, refresh = state.coach) { inView -> CoachPanel(state, inView, onEvent) }
            }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

/**
 * Root of a pooled panel. [content] gets `inView`: the panel has been moved into view; a pooled panel draws its content
 * only then. On the idle emulator a panel can keep a half-recorded first frame (black below the glass): the content is
 * composed afresh 1.5, 4 and 8 s after the panel comes into view or [refresh] changes (factory finding, Bianzhong).
 */
@Composable
private fun PoolPanel(id: String, scene: HhScene, refresh: Any? = null, content: @Composable (inView: Boolean) -> Unit) {
    val inView = scene.panelShown[id] == true
    var gen by remember { mutableIntStateOf(0) }
    LaunchedEffect(inView, refresh) {
        if (!inView) return@LaunchedEffect
        for (wait in REFRESH_STEPS_MS) {
            delay(wait)
            gen++
        }
    }
    CompositionLocalProvider(LocalProbePanel provides id) { key(gen) { content(inView) } }
}

private val REFRESH_STEPS_MS = longArrayOf(1_500L, 2_500L, 4_000L)
