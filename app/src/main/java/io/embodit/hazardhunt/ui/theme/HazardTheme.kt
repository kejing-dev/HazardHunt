package io.embodit.hazardhunt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: SpatialUI Vibrant with all 16 colour roles inherited from the system scheme (design
 * `palette.colorScheme`: every role "inherit") and the bundled Noto Sans / Noto Sans SC family. Brand colours
 * ([HhColors]) are used for domain content only: the bay, flags, verdict dots and the beam.
 */
@Composable
fun HazardTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = systemColorScheme(context)
    val family = rememberHhFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
