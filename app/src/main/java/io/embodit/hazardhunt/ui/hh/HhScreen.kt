package io.embodit.hazardhunt.ui.hh

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.LocalAudioEffectPlayer
import com.pico.spatial.ui.platform.SpatialSoundEffect
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.hazardhunt.platform.HhLog
import io.embodit.hazardhunt.ui.hh.components.HhStage
import io.embodit.hazardhunt.ui.hh.components.LocalHhResources
import io.embodit.hazardhunt.ui.hh.components.localizedResources
import io.embodit.hazardhunt.ui.hh.debug.HhProbe
import io.embodit.hazardhunt.ui.hh.scene.HhFrameSystem
import io.embodit.hazardhunt.ui.hh.scene.HhScene

/**
 * The one screen of the app, living in a Mixed Stage. The ViewModel holds the state, the [HhScene] turns state into
 * entities and reports head, floor, stick steps, hover and taps, and [HhStage] declares the panels.
 */
@Composable
fun HhScreen() {
    val context = LocalContext.current
    val viewModel: HhViewModel = viewModel(factory = HhViewModel.Factory(context))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current

    remember(context) {
        HhProbe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember(viewModel) { HhScene(context.resources, viewModel::onEvent) }

    DisposableEffect(scene) {
        registerSystem<HhFrameSystem>()
        HhProbe.host =
            object : HhProbe.Host {
                override fun dispatch(event: HhEvent) = viewModel.onEvent(event)

                override fun uiState(): HhUiState = viewModel.state.value

                override fun dumpJson(): String = viewModel.dumpJson()

                override fun scene(): HhProbe.SceneProbe = scene

                override fun pressControl(name: String): Boolean = HhProbe.click(name)
            }
        HhLog.i("STAGE enter")
        onDispose {
            HhProbe.host = null
            unregisterSystem<HhFrameSystem>()
            scene.detach()
            HhLog.i("STAGE disposed")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            scene.onBackground()
                            viewModel.onEvent(HhEvent.WentBackground)
                        }
                        Lifecycle.Event.ON_START -> {
                            scene.onForeground()
                            viewModel.onEvent(HhEvent.CameForeground)
                        }
                        else -> Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scene, state) { scene.render(state) }

    val audio = LocalAudioEffectPlayer.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect: HhEffect ->
            when (effect) {
                HhEffect.ExitApp -> {
                    HhLog.i("STAGE close requested")
                    runCatching { navigator.closeStage() }.onFailure { HhLog.e("STAGE close failed: $it", it) }
                }
                is HhEffect.Sound ->
                    runCatching {
                        // system sounds stand in for ding / page / chord (design `rules.sound`); no custom volume or pan (N12)
                        when (effect.kind) {
                            SoundKind.MARK -> audio.playSystem(SpatialSoundEffect.StateSelected)
                            SoundKind.PAGE -> audio.playSystem(SpatialSoundEffect.OpClick)
                            SoundKind.REVEAL -> audio.playSystem(SpatialSoundEffect.OpDragEnd)
                            SoundKind.FAIL -> audio.playSystem(SpatialSoundEffect.StateFailure)
                        }
                    }.onFailure { HhLog.w("SOUND failed: $it") }
            }
        }
    }

    val resources = remember(state.language, context) { localizedResources(context, state.language) }
    CompositionLocalProvider(LocalHhResources provides resources) {
        HhStage(state = state, scene = scene, onEvent = viewModel::onEvent)
    }
}
