package io.embodit.hazardhunt.ui.hh.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import io.embodit.hazardhunt.domain.model.Part
import io.embodit.hazardhunt.domain.model.Shape
import io.embodit.hazardhunt.domain.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Plain triangle geometry (no SDK types), so that shapes can be merged before one `MeshResource` is made. */
internal class Geo {
    val positions = ArrayList<Vec3>()
    val normals = ArrayList<Vec3>()
    val uvs = ArrayList<Pair<Float, Float>>()
    val indices = ArrayList<Int>()

    fun vertex(p: Vec3, n: Vec3, u: Float = 0f, v: Float = 0f): Int {
        positions += p
        normals += n
        uvs += u to v
        return positions.size - 1
    }

    /** Appends [g] transformed by [part]'s size, rotation and centre. */
    fun append(g: Geo, part: Part) {
        val base = positions.size
        val q = part.rotation
        val s = part.size
        for (i in g.positions.indices) {
            val p = g.positions[i]
            positions += part.center + q.rotate(Vec3(p.x * s.x, p.y * s.y, p.z * s.z))
            val n = g.normals[i]
            normals += q.rotate(Vec3(n.x / s.x.coerceAtLeast(1e-4f), n.y / s.y.coerceAtLeast(1e-4f), n.z / s.z.coerceAtLeast(1e-4f))).normalized()
            uvs += g.uvs[i]
        }
        for (i in g.indices) indices += base + i
    }

    val isEmpty: Boolean
        get() = indices.isEmpty()

    fun build(name: String, withUv: Boolean = false): MeshResource {
        var mn = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var mx = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (p in positions) {
            mn = Vec3(min(mn.x, p.x), min(mn.y, p.y), min(mn.z, p.z))
            mx = Vec3(max(mx.x, p.x), max(mx.y, p.y), max(mx.z, p.z))
        }
        val c = (mn + mx) * 0.5f
        val h = ((mx - mn) * 0.5f).let { Vec3(max(it.x, 0.001f), max(it.y, 0.001f), max(it.z, 0.001f)) }
        val model =
            if (withUv) MeshModel(positions = positions.map { it.sdk() }, triangleIndices = indices, normals = normals.map { it.sdk() }, uv0 = uvs.map { Vector2(it.first, it.second) })
            else MeshModel(positions = positions.map { it.sdk() }, triangleIndices = indices, normals = normals.map { it.sdk() })
        return MeshResource.createWithMeshModel(model = model, bounds = BoundingBox(center = c.sdk(), halfExtent = h.sdk()), name = name)
    }
}

/** Unit shapes centred on the origin, 1 m across: scaled per part (a cylinder's axis is +Y). */
internal object UnitGeo {
    val cube: Geo by lazy {
        val g = Geo()
        val faces =
            listOf(
                Triple(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f)),
                Triple(Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f)),
                Triple(Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f)),
                Triple(Vec3(0f, -1f, 0f), Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f)),
                Triple(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f)),
                Triple(Vec3(0f, 0f, -1f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f)),
            )
        for ((n, u, v) in faces) {
            val b = g.positions.size
            for ((a, c) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) g.vertex((n + u * a + v * c) * 0.5f, n)
            // u x v points along n for every face: counter-clockwise seen from outside
            g.indices += listOf(b, b + 1, b + 2, b, b + 2, b + 3)
        }
        g
    }

    val cylinder: Geo by lazy {
        val g = Geo()
        val seg = 18
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg
            val a1 = 2 * PI * (i + 1) / seg
            val n0 = Vec3(cos(a0).toFloat(), 0f, sin(a0).toFloat())
            val n1 = Vec3(cos(a1).toFloat(), 0f, sin(a1).toFloat())
            val b = g.positions.size
            g.vertex(Vec3(n0.x * 0.5f, -0.5f, n0.z * 0.5f), n0)
            g.vertex(Vec3(n1.x * 0.5f, -0.5f, n1.z * 0.5f), n1)
            g.vertex(Vec3(n1.x * 0.5f, 0.5f, n1.z * 0.5f), n1)
            g.vertex(Vec3(n0.x * 0.5f, 0.5f, n0.z * 0.5f), n0)
            g.indices += listOf(b, b + 2, b + 1, b, b + 3, b + 2)
        }
        for (up in listOf(true, false)) {
            val y = if (up) 0.5f else -0.5f
            val n = Vec3(0f, if (up) 1f else -1f, 0f)
            val c = g.vertex(Vec3(0f, y, 0f), n)
            for (i in 0 until seg) {
                val a = 2 * PI * i / seg
                g.vertex(Vec3((cos(a) * 0.5).toFloat(), y, (sin(a) * 0.5).toFloat()), n)
            }
            for (i in 0 until seg) {
                val a = c + 1 + i
                val b = c + 1 + (i + 1) % seg
                g.indices += if (up) listOf(c, b, a) else listOf(c, a, b)
            }
        }
        g
    }

    val sphere: Geo by lazy {
        val g = Geo()
        val around = 16
        val rings = 10
        for (j in 0..rings) {
            val phi = PI * j / rings - PI / 2
            val y = sin(phi).toFloat()
            val r = cos(phi).toFloat()
            for (i in 0..around) {
                val a = 2 * PI * i / around
                val n = Vec3(r * cos(a).toFloat(), y, r * sin(a).toFloat())
                g.vertex(n * 0.5f, n)
            }
        }
        val row = around + 1
        for (j in 0 until rings) for (i in 0 until around) {
            val a = j * row + i
            val b = a + row
            g.indices += listOf(a, b, a + 1, a + 1, b, b + 1)
        }
        g
    }

    /**
     * A centred unit quad facing +Z with texture coordinates. The engine samples v from the bottom row of the bitmap
     * (emulator check 2026-10-28: with v = 0 on the top edge every sign came out upside down, as Bianzhong found for
     * PBR), so v = 0 sits on the bottom edge and a bitmap drawn the normal way reads upright from the front.
     */
    val quad: Geo by lazy {
        val g = Geo()
        val n = Vec3(0f, 0f, 1f)
        val a = g.vertex(Vec3(-0.5f, -0.5f, 0f), n, 0f, 0f)
        val b = g.vertex(Vec3(0.5f, -0.5f, 0f), n, 1f, 0f)
        val c = g.vertex(Vec3(0.5f, 0.5f, 0f), n, 1f, 1f)
        val d = g.vertex(Vec3(-0.5f, 0.5f, 0f), n, 0f, 1f)
        g.indices += listOf(a, b, c, a, c, d)
        g
    }

    /** The flag cloth: a triangle in the XY plane from the pole (x = 0) to the tip (x = 1), 1 high. */
    val pennant: Geo by lazy {
        val g = Geo()
        val n = Vec3(0f, 0f, 1f)
        val a = g.vertex(Vec3(0f, 0.5f, 0f), n)
        val b = g.vertex(Vec3(0f, -0.5f, 0f), n)
        val c = g.vertex(Vec3(1f, 0f, 0f), n)
        g.indices += listOf(b, c, a)
        g
    }

    fun of(shape: Shape): Geo =
        when (shape) {
            Shape.BOX -> cube
            Shape.CYL -> cylinder
            Shape.SPHERE -> sphere
            Shape.SIGN -> quad
        }

    fun single(g: Geo, name: String, withUv: Boolean = false): MeshResource {
        val out = Geo()
        out.append(g, Part(Shape.BOX, io.embodit.hazardhunt.domain.model.Paint.WHITE, Vec3.ZERO, Vec3.ONE))
        return out.build(name, withUv)
    }
}

/** A dashed ring on the floor (XZ plane, facing +Y), radius 1: the hover outline of floor checkpoints. */
internal fun dashedRing(name: String, dashes: Int = 24, inner: Float = 0.94f): MeshResource {
    val g = Geo()
    val up = Vec3.UP
    val slot = 2 * PI / dashes
    for (d in 0 until dashes) {
        val a0 = d * slot
        val a1 = a0 + slot * 0.6
        for (k in 0 until 2) {
            val s = a0 + (a1 - a0) * k / 2
            val e = a0 + (a1 - a0) * (k + 1) / 2
            val b = g.positions.size
            g.vertex(Vec3((cos(s) * inner).toFloat(), 0f, (sin(s) * inner).toFloat()), up)
            g.vertex(Vec3(cos(s).toFloat(), 0f, sin(s).toFloat()), up)
            g.vertex(Vec3(cos(e).toFloat(), 0f, sin(e).toFloat()), up)
            g.vertex(Vec3((cos(e) * inner).toFloat(), 0f, (sin(e) * inner).toFloat()), up)
            g.indices += listOf(b, b + 2, b + 1, b, b + 3, b + 2)
        }
    }
    return g.build(name)
}

@Suppress("unused")
private fun Vector3.len(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
