package io.embodit.hazardhunt.ui.hh.scene

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.embodit.hazardhunt.domain.model.SignKind
import io.embodit.hazardhunt.ui.theme.HhColors
import io.embodit.hazardhunt.ui.theme.hhTypeface
import kotlin.math.min

/**
 * Bitmaps for the textured quads (design `entities3d.signs`: self-drawn GB 2894-style signs, no web images; the floor
 * plate; name / verdict tags; flag numbers). Every bitmap is drawn upright: [UnitGeo.quad] maps its top row to the
 * quad's top edge. `testTexture` draws an asymmetric "F↑" instead, to check orientation on the emulator (taste D10).
 */
internal class SignArt(private val resources: Resources) {
    private val face: Typeface by lazy { hhTypeface(resources) }

    private fun text(color: Int, size: Float, align: Paint.Align = Paint.Align.CENTER, bold: Boolean = true): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = Typeface.create(face, if (bold) 700 else 500, false)
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** "F↑": top / bottom and left / right are all distinguishable. */
    fun testPattern(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFFFFF.toInt())
        c.drawRect(0f, 0f, w.toFloat(), h * 0.12f, fill(0xFFE53935.toInt()))
        c.drawRect(0f, 0f, w * 0.08f, h.toFloat(), fill(0xFF1E5BB8.toInt()))
        c.drawText("F↑", w / 2f, h * 0.72f, text(0xFF000000.toInt(), min(w, h) * 0.55f))
        return b
    }

    // =========================================================================================
    // floor plate: epoxy grey, yellow walkway lines, black / yellow forklift lane on the right
    // =========================================================================================

    fun floorPlate(): Bitmap {
        val ppm = 250f
        val w = (4.0f * ppm).toInt()
        val h = (4.6f * ppm).toInt()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        fun px(x: Float) = (x + 2.0f) * ppm
        fun py(z: Float) = (z + 2.6f) * ppm
        c.drawColor(HhColors.EpoxyGrey.toArgb())
        // speckle so the floor reads as a surface, not a flat colour
        val rnd = java.util.Random(7)
        val speck = fill(0x22FFFFFF)
        val dark = fill(0x22000000)
        repeat(2600) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            c.drawCircle(x, y, 1.5f + rnd.nextFloat() * 2f, if (it % 2 == 0) speck else dark)
        }
        // walkway lines (10 cm) round the central standing area
        val line = fill(HhColors.SafetyYellow.toArgb())
        val lw = 0.1f * ppm
        val l = px(-1.2f)
        val r = px(0.5f)
        val t = py(-1.65f)
        val bt = py(1.25f)
        c.drawRect(l, t, r, t + lw, line)
        c.drawRect(l, bt - lw, r, bt, line)
        c.drawRect(l, t, l + lw, bt, line)
        c.drawRect(r - lw, t, r, bt, line)
        // forklift lane x 1.5 .. 2.0: diagonal stripes, a solid yellow edge line
        val laneL = px(1.5f)
        c.save()
        c.clipRect(laneL, 0f, w.toFloat(), h.toFloat())
        c.drawColor(HhColors.StripeBlack.toArgb())
        val stripe = Path()
        var y = -w.toFloat()
        val step = 0.2f * ppm
        while (y < h + w) {
            stripe.reset()
            stripe.moveTo(laneL, y)
            stripe.lineTo(laneL + step / 2f, y)
            stripe.lineTo(w.toFloat(), y + (w - laneL))
            stripe.lineTo(w - step / 2f, y + (w - laneL))
            stripe.close()
            c.drawPath(stripe, line)
            y += step
        }
        c.restore()
        c.drawRect(laneL, 0f, laneL + lw * 0.6f, h.toFloat(), line)
        // a painted arrow in the lane pointing to the back (+Z): it tells QA which way the plate is laid
        val arrow = Path().apply {
            val cx = px(1.75f)
            moveTo(cx, py(1.3f))
            lineTo(cx - 0.15f * ppm, py(0.95f))
            lineTo(cx + 0.15f * ppm, py(0.95f))
            close()
        }
        c.drawPath(arrow, fill(0xFFFFFFFF.toInt()))
        return b
    }

    // =========================================================================================
    // signs
    // =========================================================================================

    fun sign(kind: SignKind, test: Boolean): Bitmap {
        if (test) return testPattern(256, 256)
        return when (kind) {
            SignKind.FLOOR_PLATE -> floorPlate()
            SignKind.EXIT_DOOR -> exit(512, 256, lit = true, door = true)
            SignKind.EXIT_LIT -> exit(704, 256, lit = true, door = false)
            SignKind.EXIT_DARK -> exit(704, 256, lit = false, door = false)
            SignKind.ELECTRIC -> warning(448, 392, "当心触电") { c, cx, cy, s -> bolt(c, cx, cy, s) }
            SignKind.FORKLIFT -> warning(320, 320, "当心叉车") { c, cx, cy, s -> forklift(c, cx, cy, s) }
            SignKind.WET -> warning(320, 512, "小心地滑", sub = "WET FLOOR") { c, cx, cy, s -> slip(c, cx, cy, s) }
            SignKind.GOGGLES -> mandatory(300, 400)
            SignKind.LOAD -> load(520, 240)
            SignKind.NO_BLOCK -> noBlock(320, 320)
            SignKind.EXTINGUISHER -> extinguisher(320, 320)
        }
    }

    private fun exit(w: Int, h: Int, lit: Boolean, door: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val bg = if (lit) 0xFF1BA34A.toInt() else 0xFF26332B.toInt()
        val ink = if (lit) 0xFFFFFFFF.toInt() else 0xFF56645A.toInt()
        c.drawColor(bg)
        c.drawRect(6f, 6f, w - 6f, h - 6f, stroke(ink, 6f))
        // running figure towards a door, on the left
        val s = h * 0.62f
        val x0 = h * 0.22f
        val y0 = h * 0.2f
        c.drawRect(x0 + s * 0.55f, y0, x0 + s * 0.9f, y0 + s, stroke(ink, 8f))
        c.drawCircle(x0 + s * 0.3f, y0 + s * 0.12f, s * 0.09f, fill(ink))
        val body = stroke(ink, s * 0.08f)
        c.drawLine(x0 + s * 0.28f, y0 + s * 0.25f, x0 + s * 0.2f, y0 + s * 0.6f, body)
        c.drawLine(x0 + s * 0.2f, y0 + s * 0.6f, x0 + s * 0.38f, y0 + s * 0.95f, body)
        c.drawLine(x0 + s * 0.2f, y0 + s * 0.6f, x0 + s * 0.02f, y0 + s * 0.9f, body)
        c.drawLine(x0 + s * 0.26f, y0 + s * 0.34f, x0 + s * 0.48f, y0 + s * 0.42f, body)
        c.drawLine(x0 + s * 0.26f, y0 + s * 0.34f, x0 + s * 0.08f, y0 + s * 0.48f, body)
        val tx = x0 + s * 1.05f + (w - x0 - s * 1.05f) / 2f
        if (door) {
            c.drawText("安全出口", tx, h * 0.48f, text(ink, h * 0.24f))
            c.drawText("EXIT", tx, h * 0.82f, text(ink, h * 0.26f))
        } else {
            c.drawText("安全出口 EXIT", tx, h * 0.64f, text(ink, h * 0.3f))
        }
        return b
    }

    private fun warning(w: Int, h: Int, label: String, sub: String? = null, glyph: (Canvas, Float, Float, Float) -> Unit): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFFFFF.toInt())
        val side = w * 0.86f
        val th = side * 0.866f
        val cx = w / 2f
        val top = h * 0.05f
        val tri = Path().apply {
            moveTo(cx, top)
            lineTo(cx + side / 2f, top + th)
            lineTo(cx - side / 2f, top + th)
            close()
        }
        c.drawPath(tri, fill(HhColors.SafetyYellow.toArgb()))
        c.drawPath(tri, stroke(HhColors.StripeBlack.toArgb(), side * 0.06f))
        glyph(c, cx, top + th * 0.62f, side * 0.42f)
        val textTop = top + th
        val rest = h - textTop
        if (sub == null) {
            c.drawText(label, cx, textTop + rest * 0.72f, text(HhColors.StripeBlack.toArgb(), min(rest * 0.62f, w * 0.2f)))
        } else {
            c.drawText(label, cx, textTop + rest * 0.42f, text(HhColors.StripeBlack.toArgb(), min(rest * 0.3f, w * 0.2f)))
            c.drawText(sub, cx, textTop + rest * 0.82f, text(HhColors.StripeBlack.toArgb(), min(rest * 0.22f, w * 0.13f)))
        }
        return b
    }

    private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path().apply {
            moveTo(cx + s * 0.12f, cy - s * 0.55f)
            lineTo(cx - s * 0.22f, cy + s * 0.05f)
            lineTo(cx + s * 0.02f, cy + s * 0.05f)
            lineTo(cx - s * 0.12f, cy + s * 0.55f)
            lineTo(cx + s * 0.24f, cy - s * 0.08f)
            lineTo(cx, cy - s * 0.08f)
            close()
        }
        c.drawPath(p, fill(HhColors.StripeBlack.toArgb()))
    }

    private fun forklift(c: Canvas, cx: Float, cy: Float, s: Float) {
        val ink = fill(HhColors.StripeBlack.toArgb())
        c.drawRect(cx - s * 0.45f, cy - s * 0.1f, cx + s * 0.1f, cy + s * 0.3f, ink)
        c.drawRect(cx - s * 0.3f, cy - s * 0.4f, cx - s * 0.05f, cy - s * 0.1f, stroke(HhColors.StripeBlack.toArgb(), s * 0.06f))
        c.drawRect(cx + s * 0.18f, cy - s * 0.45f, cx + s * 0.24f, cy + s * 0.3f, ink)
        c.drawRect(cx + s * 0.24f, cy + s * 0.22f, cx + s * 0.5f, cy + s * 0.3f, ink)
        c.drawCircle(cx - s * 0.32f, cy + s * 0.36f, s * 0.1f, ink)
        c.drawCircle(cx + s * 0.0f, cy + s * 0.36f, s * 0.1f, ink)
    }

    private fun slip(c: Canvas, cx: Float, cy: Float, s: Float) {
        val ink = HhColors.StripeBlack.toArgb()
        val st = stroke(ink, s * 0.1f)
        c.drawCircle(cx + s * 0.12f, cy - s * 0.42f, s * 0.1f, fill(ink))
        c.drawLine(cx + s * 0.05f, cy - s * 0.28f, cx - s * 0.1f, cy + s * 0.05f, st)
        c.drawLine(cx - s * 0.1f, cy + s * 0.05f, cx + s * 0.3f, cy + s * 0.2f, st)
        c.drawLine(cx - s * 0.1f, cy + s * 0.05f, cx - s * 0.35f, cy + s * 0.3f, st)
        c.drawLine(cx + s * 0.02f, cy - s * 0.2f, cx + s * 0.35f, cy - s * 0.3f, st)
        c.drawLine(cx - s * 0.5f, cy + s * 0.45f, cx + s * 0.5f, cy + s * 0.45f, stroke(ink, s * 0.06f))
    }

    private fun mandatory(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFFFFF.toInt())
        val cx = w / 2f
        val r = w * 0.42f
        val cy = r + w * 0.06f
        c.drawCircle(cx, cy, r, fill(0xFF1565C0.toInt()))
        val white = 0xFFFFFFFF.toInt()
        // goggles: two lenses and a bridge
        c.drawRoundRect(RectF(cx - r * 0.7f, cy - r * 0.22f, cx - r * 0.06f, cy + r * 0.26f), r * 0.14f, r * 0.14f, fill(white))
        c.drawRoundRect(RectF(cx + r * 0.06f, cy - r * 0.22f, cx + r * 0.7f, cy + r * 0.26f), r * 0.14f, r * 0.14f, fill(white))
        c.drawLine(cx - r * 0.85f, cy - r * 0.05f, cx + r * 0.85f, cy - r * 0.05f, stroke(white, r * 0.08f))
        val rest = h - (cy + r)
        c.drawText("必须戴护目镜", cx, cy + r + rest * 0.48f, text(0xFF1565C0.toInt(), min(rest * 0.36f, w * 0.15f)))
        c.drawText("WEAR GOGGLES", cx, cy + r + rest * 0.86f, text(0xFF1565C0.toInt(), min(rest * 0.26f, w * 0.1f)))
        return b
    }

    private fun load(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFFFFF.toInt())
        c.drawRect(8f, 8f, w - 8f, h - 8f, stroke(HhColors.StripeBlack.toArgb(), 10f))
        c.drawText("限载 500 kg / 层", w / 2f, h * 0.48f, text(HhColors.StripeBlack.toArgb(), h * 0.26f))
        c.drawText("MAX 500 kg PER LEVEL", w / 2f, h * 0.8f, text(0xFF444444.toInt(), h * 0.17f))
        return b
    }

    private fun noBlock(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFFFFF.toInt())
        val cx = w / 2f
        val r = w * 0.34f
        val cy = r + w * 0.06f
        c.drawRect(cx - r * 0.45f, cy - r * 0.3f, cx + r * 0.45f, cy + r * 0.45f, fill(0xFF6D4C41.toInt()))
        c.drawCircle(cx, cy, r, stroke(0xFFD32F2F.toInt(), r * 0.16f))
        c.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, stroke(0xFFD32F2F.toInt(), r * 0.16f))
        val rest = h - (cy + r)
        c.drawText("禁止堵塞", cx, cy + r + rest * 0.5f, text(0xFFD32F2F.toInt(), rest * 0.36f))
        c.drawText("KEEP CLEAR", cx, cy + r + rest * 0.88f, text(0xFFD32F2F.toInt(), rest * 0.24f))
        return b
    }

    private fun extinguisher(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFD32F2F.toInt())
        val white = 0xFFFFFFFF.toInt()
        val cx = w * 0.5f
        c.drawRoundRect(RectF(cx - w * 0.11f, h * 0.24f, cx + w * 0.11f, h * 0.7f), w * 0.05f, w * 0.05f, fill(white))
        c.drawRect(cx - w * 0.04f, h * 0.14f, cx + w * 0.04f, h * 0.24f, fill(white))
        c.drawLine(cx + w * 0.04f, h * 0.17f, cx + w * 0.22f, h * 0.3f, stroke(white, w * 0.035f))
        c.drawLine(cx + w * 0.22f, h * 0.3f, cx + w * 0.2f, h * 0.5f, stroke(white, w * 0.035f))
        c.drawText("灭火器 FIRE EXT.", cx, h * 0.88f, text(white, h * 0.12f))
        return b
    }

    // =========================================================================================
    // tags and flag numbers
    // =========================================================================================

    /**
     * A tag plate (design `tagsInWorld`): 3 dp glass-grey edge round a plateInk card; an optional status dot on the left
     * and a right-aligned figure ("3/3"). [w] x [h] dp at 4 px per dp.
     */
    fun tag(label: String, wDp: Int, hDp: Int, dot: Color? = null, figure: String? = null, test: Boolean = false): Bitmap {
        val k = 4f
        val w = (wDp * k).toInt()
        val h = (hDp * k).toInt()
        if (test) return testPattern(w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFB9C0C7.toInt())
        val edge = 3 * k
        c.drawRoundRect(RectF(edge, edge, w - edge, h - edge), 6 * k, 6 * k, fill(HhColors.PlateInk.toArgb()))
        var x = edge + 10 * k
        if (dot != null) {
            c.drawCircle(x + 7 * k, h / 2f, 7 * k, fill(dot.toArgb()))
            x += 14 * k + 8 * k
        }
        val size = (hDp - 14) * k * 0.78f
        val p = text(HhColors.White.toArgb(), size, Paint.Align.LEFT)
        var right = w - edge - 10 * k
        if (figure != null) {
            val fp = text(HhColors.White.toArgb(), size, Paint.Align.RIGHT)
            fp.fontFeatureSettings = "tnum, lnum"
            c.drawText(figure, right, h / 2f + size * 0.36f, fp)
            right -= fp.measureText(figure) + 8 * k
        }
        // shrink to fit, never clip
        val avail = right - x
        val wText = p.measureText(label)
        if (wText > avail && avail > 0) p.textSize = size * avail / wText
        c.drawText(label, x, h / 2f + p.textSize * 0.36f, p)
        return b
    }

    /** The flag number: ink figure on hazard orange, 128 px square (white on this orange is 2.6:1, below taste D13's 3:1). */
    fun flagNumber(n: Int, test: Boolean): Bitmap {
        if (test) return testPattern(128, 128)
        val b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(HhColors.HazardOrange.toArgb())
        val p = text(HhColors.PlateInk.toArgb(), if (n >= 10) 78f else 96f)
        p.fontFeatureSettings = "tnum, lnum"
        c.drawText(n.toString(), 64f, 64f + p.textSize * 0.36f, p)
        return b
    }
}
