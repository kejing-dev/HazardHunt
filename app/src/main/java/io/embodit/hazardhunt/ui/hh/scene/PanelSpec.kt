package io.embodit.hazardhunt.ui.hh.scene

/**
 * Ids and dp sizes of every attachment panel (design `panels`); the Compose side declares exactly these, once.
 * Every panel is shown at 0.75 (1 dp = 0.6 mm). The inspect panel's box is its tallest layout (debrief / detail,
 * 488 dp); shorter layouts draw their glass from the top of the box down.
 */
object PanelSpec {
    const val INSPECT = "inspectPanel"
    const val CARD = "classifyCard"
    const val MENU = "moreMenu"
    const val SNACK = "snackbar"
    const val DIALOG = "dialog"
    const val COACH = "coach"

    val INSPECT_SIZE = 400f to 488f
    val CARD_SIZE = 360f to 400f
    val MENU_SIZE = 300f to 384f
    val SNACK_SIZE = 360f to 56f
    val DIALOG_SIZE = 400f to 208f
    val COACH_SIZE = 320f to 120f

    const val SCALE = 0.75f

    val pool: List<String> = listOf(INSPECT, CARD, MENU, SNACK, DIALOG, COACH)

    fun size(id: String): Pair<Float, Float> =
        when (id) {
            INSPECT -> INSPECT_SIZE
            CARD -> CARD_SIZE
            MENU -> MENU_SIZE
            SNACK -> SNACK_SIZE
            DIALOG -> DIALOG_SIZE
            else -> COACH_SIZE
        }

    /** Half width / height of a panel in metres. */
    fun halfM(id: String, scale: Float = SCALE): Pair<Float, Float> {
        val (w, h) = size(id)
        return (w * PanelRig.DP_M * scale / 2f) to (h * PanelRig.DP_M * scale / 2f)
    }
}
