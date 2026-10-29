package io.embodit.hazardhunt.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand tokens of the design (`palette.brandTokens`). This is the only file with UI colour literals; the 16
 * ColorScheme roles are inherited from SpatialUI Vibrant. These colours paint domain content only (flags, verdict
 * dots on the ink plates, the beam, the hover outline in the 3D scene); panel chrome uses PicoTheme roles.
 */
object HhColors {
    val HazardOrange = Color(0xFFFF7A1A) // design-style: fixed-figma-color hazardOrange: flags
    val SafetyYellow = Color(0xFFF5C400) // design-style: fixed-figma-color safetyYellow
    val StripeBlack = Color(0xFF1F1F1F) // design-style: fixed-figma-color stripeBlack
    val EpoxyGrey = Color(0xFF6F7378) // design-style: fixed-figma-color epoxyGrey: floor plate
    val PlateInk = Color(0xFF1C1F22) // design-style: fixed-figma-color plateInk: tag inner card
    val White = Color(0xFFFFFFFF) // design-style: fixed-figma-color white: flag numbers, text on ink

    /** Verdict dots and the beam, on the ink plate (design `palette.semantic`; matched to the Vibrant roles). */
    val Interaction = Color(0xFFFFD24A) // design-style: fixed-figma-color interaction: hover outline in 3D
    val Passable = Color(0xFF5BD68A) // design-style: fixed-figma-color passable: verdict 3/3
    val Alert = Color(0xFFFFB020) // design-style: fixed-figma-color alert: verdict with a mistake
    val Error = Color(0xFFFF5A4E) // design-style: fixed-figma-color error: verdict missed, beam
    val Grey = Color(0xFF9AA0A6) // design-style: fixed-figma-color labelTertiary: verdict false alarm
}
