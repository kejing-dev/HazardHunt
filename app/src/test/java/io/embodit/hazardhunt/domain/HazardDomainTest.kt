package io.embodit.hazardhunt.domain

import io.embodit.hazardhunt.domain.model.Box3
import io.embodit.hazardhunt.domain.model.Catalog
import io.embodit.hazardhunt.domain.model.Category
import io.embodit.hazardhunt.domain.model.Level
import io.embodit.hazardhunt.domain.model.Props
import io.embodit.hazardhunt.domain.model.Vec3
import io.embodit.hazardhunt.domain.model.Zone
import io.embodit.hazardhunt.domain.usecase.BayPose
import io.embodit.hazardhunt.domain.usecase.Grade
import io.embodit.hazardhunt.domain.usecase.Hint
import io.embodit.hazardhunt.domain.usecase.Mark
import io.embodit.hazardhunt.domain.usecase.RoundRules
import io.embodit.hazardhunt.domain.usecase.Scoring
import io.embodit.hazardhunt.domain.usecase.StickStepper
import io.embodit.hazardhunt.domain.usecase.Verdict
import io.embodit.hazardhunt.domain.usecase.bringToFrontDelta
import io.embodit.hazardhunt.domain.usecase.hintFor
import io.embodit.hazardhunt.domain.usecase.rayBox
import io.embodit.hazardhunt.domain.usecase.wrap
import io.embodit.hazardhunt.domain.usecase.yawOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardDomainTest {

    // ---------------------------------------------------------------- catalog

    @Test
    fun catalogHas28CheckpointsWithFourFixesEach() {
        assertEquals(28, Catalog.all.size)
        assertEquals(26, Catalog.regular.size)
        assertEquals(listOf("P26", "P27"), Catalog.challenge.map { it.id })
        for (p in Catalog.all) {
            assertTrue(p.id, p.hazards.isNotEmpty())
            for (h in p.hazards) {
                assertEquals(h.id, 4, h.fixes.size)
                assertTrue(h.id, h.fixes.all { it.zh.isNotBlank() && it.en.isNotBlank() })
                assertTrue(h.id, h.basis.zh.isNotBlank() && h.basis.en.isNotBlank())
                assertFalse("${h.id} still says 研发站核", h.basis.zh.contains("研发站核") || h.basis.en.contains("研发站核"))
            }
        }
        assertEquals(2, Catalog.get("P12")!!.hazards.size)
    }

    @Test
    fun pointerBoxesAreAtLeast25cmAndHoldTheirPoint() {
        for (p in Catalog.all) {
            val s = p.box.size
            assertTrue("${p.id} box $s", s.x >= 0.25f - 1e-4f && s.y >= 0.25f - 1e-4f && s.z >= 0.25f - 1e-4f)
            // the design point lies in (or within 5 cm of) its box
            val mn = p.box.min - Vec3(0.05f, 0.05f, 0.05f)
            val mx = p.box.max + Vec3(0.05f, 0.05f, 0.05f)
            val q = p.pos
            assertTrue("${p.id} pos $q outside ${p.box}", q.x in mn.x..mx.x && q.z in mn.z..mx.z)
        }
    }

    @Test
    fun levelsMatchTheSightCheck() {
        // low: looking down at least 25 degrees; high: up at least 12 degrees from 1.6 m eye height (design sightCheck)
        for (p in Catalog.regular) {
            val horiz = hypot(p.pos.x, p.pos.z)
            val pitch = Math.toDegrees(kotlin.math.atan2((p.pos.y - 1.6f).toDouble(), horiz.toDouble()))
            when (p.level) {
                Level.LOW -> assertTrue("${p.id} pitch $pitch", pitch <= -24.5)
                Level.HIGH -> assertTrue("${p.id} pitch $pitch", pitch >= 11.5)
                Level.MID -> Unit
            }
        }
        assertEquals(7, Catalog.regular.count { it.level == Level.LOW })
        assertEquals(7, Catalog.regular.count { it.level == Level.HIGH })
    }

    // ---------------------------------------------------------------- props

    @Test
    fun everythingKeeps85cmClearOfTheViewer() {
        // taste D14: nothing placed within 0.85 m of the head (the pallet corner at (0.6, -0.6) is 0.849 m)
        assertTrue("fixtures ${Props.nearest(Props.fixtures)}", Props.nearest(Props.fixtures) >= 0.84f)
        for (p in Catalog.all) {
            val look = Props.look(p.id)
            for ((i, parts) in (listOf(look.safe) + look.hazards).withIndex()) {
                val d = Props.nearest(parts)
                assertTrue("${p.id} look $i nearest $d", d >= 0.84f)
            }
        }
    }

    @Test
    fun everyCheckpointHasOneLookPerHazardVariant() {
        for (p in Catalog.all) {
            val look = Props.look(p.id)
            assertEquals(p.id, p.hazards.size, look.hazards.size)
            assertTrue("${p.id} hazard look is empty", look.hazards.all { it.isNotEmpty() })
        }
        val poiParts = Catalog.all.sumOf { p -> Props.look(p.id).let { it.safe.size + it.hazards.sumOf { h -> h.size } } }
        val paints = Props.fixtures.filter { it.shape != io.embodit.hazardhunt.domain.model.Shape.SIGN }.map { it.paint }.toSet().size
        println("ENTITIES fixture_parts=${Props.fixtures.size} fixture_meshes=$paints poi_part_entities=$poiParts")
    }

    // ---------------------------------------------------------------- round draw

    @Test
    fun everyDrawFollowsTheRoundRules() {
        for (seed in 1L..400L) {
            val layout = RoundRules.draw(seed, walk = false)
            assertEquals(8, layout.total)
            assertEquals(10, layout.cap)
            val pois = layout.hazards.keys.map { Catalog.get(it)!! }
            assertTrue("seed $seed", RoundRules.valid(pois))
            for (z in Zone.entries) assertTrue("seed $seed zone $z", pois.any { it.zone == z })
            assertTrue(pois.count { it.level == Level.LOW } >= 2)
            assertTrue(pois.count { it.level == Level.HIGH } >= 2)
            assertTrue(pois.groupingBy { it.hazard(layout.variantOf(it.id)).category }.eachCount().values.all { it <= 2 })
            assertFalse(layout.isHazard("P26") || layout.isHazard("P27"))
        }
    }

    @Test
    fun theSameSeedGivesTheSameBay() {
        assertEquals(RoundRules.draw(1L, false), RoundRules.draw(1L, false))
        assertEquals(RoundRules.draw(987654321L, true), RoundRules.draw(987654321L, true))
        val distinct = (1L..50L).map { RoundRules.draw(it, false).hazards.keys }.toSet()
        assertTrue("draws vary", distinct.size > 30)
        println("SEED1 ${RoundRules.draw(1L, false).hazards}")
    }

    @Test
    fun walkModeAddsTheTwoHiddenCheckpoints() {
        val layout = RoundRules.draw(5L, walk = true)
        assertEquals(10, layout.total)
        assertEquals(12, layout.cap)
        assertTrue(layout.isHazard("P26") && layout.isHazard("P27"))
        assertEquals(28, layout.pois.size)
        assertEquals(26, RoundRules.draw(5L, walk = false).pois.size)
    }

    @Test
    fun fixOrderIsAPermutationStablePerRound() {
        val a = RoundRules.fixOrder(42L, "P12")
        assertEquals(listOf(0, 1, 2, 3), a.sorted())
        assertEquals(a, RoundRules.fixOrder(42L, "P12"))
        val firstSlots = (1L..200L).map { RoundRules.fixOrder(it, "P01").indexOf(0) }.toSet()
        assertEquals("the right fix moves around", setOf(0, 1, 2, 3), firstSlots)
    }

    // ---------------------------------------------------------------- scoring

    @Test
    fun presetIs17Of24Good() {
        val layout = RoundRules.draw(1L, false)
        val d = Scoring.debrief(layout, Scoring.presetMarks(layout))
        assertEquals(17, d.score)
        assertEquals(24, d.max)
        assertEquals(6, d.found)
        assertEquals(6, d.typed)
        assertEquals(5, d.fixed)
        assertEquals(2, d.falseAlarms)
        assertEquals(Grade.GOOD, d.grade)
        assertEquals(8, d.order.size)
        // the missed (red) come first, then the rest; each group clockwise from the front
        val verdicts = d.hazardOutcomes.map { it.verdict }
        assertEquals(listOf(Verdict.RED, Verdict.RED), verdicts.take(2))
        assertTrue(verdicts.drop(2).none { it == Verdict.RED })
        assertEquals(5, verdicts.count { it == Verdict.GREEN })
        assertEquals(1, verdicts.count { it == Verdict.YELLOW })
        val reds = d.hazardOutcomes.take(2).map { Scoring.bearing(it.poiId) }
        assertTrue(reds[0] <= reds[1])
        assertEquals(2, d.outcomes.count { it.verdict == Verdict.GREY })
    }

    @Test
    fun gradesFollowTheThresholds() {
        assertEquals(Grade.EXCELLENT, Scoring.grade(21, false))
        assertEquals(Grade.GOOD, Scoring.grade(20, false))
        assertEquals(Grade.GOOD, Scoring.grade(16, false))
        assertEquals(Grade.AGAIN, Scoring.grade(15, false))
        assertEquals(Grade.EXCELLENT, Scoring.grade(26, true))
        assertEquals(Grade.GOOD, Scoring.grade(20, true))
        assertEquals(Grade.AGAIN, Scoring.grade(19, true))
    }

    @Test
    fun wrongCategoryKeepsTheFindPoint() {
        val layout = RoundRules.draw(3L, false)
        val id = layout.hazards.keys.first()
        val right = Catalog.get(id)!!.hazard(layout.variantOf(id)).category
        val wrong = Category.entries.first { it != right }
        val d = Scoring.debrief(layout, listOf(Mark(id, wrong, 0)))
        val o = d.outcome(id)!!
        assertTrue(o.found)
        assertFalse(o.typeOk)
        assertTrue(o.fixOk)
        assertEquals(2, o.points)
        assertEquals(Verdict.YELLOW, o.verdict)
        assertEquals(2, d.score)
    }

    @Test
    fun bearingsRunClockwiseFromTheFront() {
        // front (P22, -z) < right (P07, +x) < back (P05, +z) < left (P13, -x)
        val order = listOf("P22", "P07", "P05", "P13").map { Scoring.bearing(it) }
        assertEquals(order.sorted(), order)
    }

    // ---------------------------------------------------------------- bay pose, turn, ray

    @Test
    fun placementFacesTheHead() {
        val head = Vec3(1f, 1.7f, 2f)
        val fwd = Vec3(1f, -0.3f, 0f).normalized()
        val bay = BayPose.facing(head, fwd, 0.4f)
        val front = bay.toStage(Vec3(0f, 0f, -2f))
        assertEquals(3f, front.x, 1e-4f)
        assertEquals(2f, front.z, 1e-4f)
        assertEquals(0.4f, front.y, 1e-4f)
        val back = bay.toLocal(front)
        assertEquals(-2f, back.z, 1e-4f)
    }

    @Test
    fun snapTurnKeepsTheViewerFixedAndStepsBy45() {
        val bay = BayPose(0.5f, -0.2f, 0.3f, 0f)
        val pivot = Vec3(0.5f, 1.6f, -0.2f)
        val turned = bay.turned(StickStepper.STEP_RAD, pivot.x, pivot.z)
        assertEquals(0.5f, turned.originX, 1e-5f)
        assertEquals(-0.2f, turned.originZ, 1e-5f)
        assertEquals(0.3f + Math.toRadians(45.0).toFloat(), turned.yaw, 1e-5f)
        // a point at the bay front moves counter-clockwise seen from above (towards -x) on a left push (+delta)
        val p0 = BayPose(0f, 0f, 0f, 0f).toStage(Vec3(0f, 0f, -2f))
        val p1 = BayPose(0f, 0f, 0f, 0f).turned(StickStepper.STEP_RAD, 0f, 0f).toStage(Vec3(0f, 0f, -2f))
        assertEquals(0f, p0.x, 1e-5f)
        assertTrue(p1.x < -1f)
    }

    @Test
    fun stickStepsOncePerPushAndRearmsBelow03() {
        val s = StickStepper()
        assertEquals(0f, s.update(0.5f))
        val a = s.update(0.7f)
        assertTrue(a < 0f) // right: clockwise
        assertEquals(0f, s.update(0.95f))
        assertEquals(0f, s.update(0.4f))
        assertEquals(0f, s.update(0.7f))
        assertEquals(0f, s.update(0.2f))
        assertTrue(s.update(-0.8f) > 0f) // left: counter-clockwise
    }

    @Test
    fun bringToFrontPutsTheCheckpointStraightAhead() {
        val bay = BayPose(0.2f, 0.1f, 1.0f, 0f)
        val head = Vec3(0.2f, 1.6f, 0.1f)
        val fwd = Vec3(0.3f, -0.1f, -1f).normalized()
        for (id in listOf("P05", "P13", "P20", "P07")) {
            val poi = Catalog.get(id)!!
            val delta = bringToFrontDelta(bay, poi.pos, head, fwd)
            val t = bay.turned(delta, head.x, head.z).toStage(poi.pos)
            val diff = wrap(yawOf(Vec3(t.x - head.x, 0f, t.z - head.z)) - yawOf(fwd))
            assertTrue("$id off by $diff", abs(diff) < 1e-3f)
        }
    }

    @Test
    fun rayHitsTheNearestBoxFace() {
        val box = Box3(Vec3(0f, 1f, -2f), Vec3(0.5f, 0.5f, 0.5f))
        val t = rayBox(Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f), box)
        assertNotNull(t)
        assertEquals(1.75f, t!!, 1e-4f)
        assertNull(rayBox(Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), box))
        assertNull(rayBox(Vec3(0f, 2f, 0f), Vec3(0f, 0f, -1f), box))
    }

    @Test
    fun theOccludedCheckpointsAreHiddenFromTheOrigin() {
        // P27 sits behind the rack's far end panel seen from the standing point (design sightCheck)
        val eye = Vec3(0f, 1.6f, 0f)
        val p27 = Catalog.get("P27")!!.box.center
        val panel = Box3(Vec3(-1.75f, 1.0f, -2.1f), Vec3(0.5f, 2.0f, 0.04f))
        val dir = (p27 - eye).normalized()
        val tPanel = rayBox(eye, dir, panel)
        assertNotNull(tPanel)
        assertTrue(tPanel!! < (p27 - eye).length)
        // from the walk-mode spot (-1.0, -2.2) the panel is out of the way
        val spot = Vec3(-1.0f, 1.6f, -2.2f)
        assertNull(rayBox(spot, (p27 - spot).normalized(), panel))
        // P26 sits behind the pallet stack (P04's box) seen from the origin
        val p26 = Catalog.get("P26")!!.box.center
        val pallet = Catalog.get("P04")!!.box
        val t26 = rayBox(eye, (p26 - eye).normalized(), pallet)
        assertNotNull(t26)
    }

    @Test
    fun hintPriority() {
        assertEquals(Hint.WALK, hintFor(true, 3, 10))
        assertEquals(Hint.FIRST, hintFor(false, 0, 8))
        assertEquals(Hint.TURN, hintFor(false, 3, 8))
        assertEquals(Hint.SUBMIT, hintFor(false, 8, 8))
        assertEquals(Hint.SUBMIT, hintFor(false, 9, 8))
    }

    @Suppress("unused")
    private val twoPi = (2 * PI).toFloat()
}
