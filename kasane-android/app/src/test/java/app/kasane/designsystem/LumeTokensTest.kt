package app.kasane.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The rules behind LumeTokens. The same test runs in all five apps. */
class LumeTokensTest {
    private val n = LumeTokens.Neutral

    @Test fun inksReadComfortablyOnEveryPage() {
        for (page in n.lightPages + n.lightFills) {
            assertAtLeast(7.0, n.InkLight, page, "ink")
            assertAtLeast(4.5, n.InkLightSecondary, page, "secondary ink")
        }
        for (page in n.darkPages + n.darkFills) {
            assertAtLeast(7.0, n.InkDark, page, "ink")
            assertAtLeast(4.5, n.InkDarkSecondary, page, "secondary ink")
        }
    }

    @Test fun typeScaleGrowsAndStaysReadable() {
        val sizes = LumeTokens.Type.scale.map { it.value }
        assertEquals(sizes.sorted(), sizes)
        assertTrue(sizes.first() >= 12f)
        assertTrue(LumeTokens.Type.Body.value >= 16f)
    }

    @Test fun controlsMeetTouchTargets() {
        assertTrue(LumeTokens.Control.MinTouch.value >= 48f)
        assertTrue(LumeTokens.Control.ButtonHeight.value >= LumeTokens.Control.MinTouch.value)
        assertTrue(LumeTokens.Control.DISABLED_ALPHA in 0.3f..0.5f)
    }

    @Test fun accentsWorkAsTextAndAsButtons() {
        for (accent in LumeTokens.Accent.light) {
            for (page in n.lightPages) assertAtLeast(4.5, accent, page, "accent")
            for (fill in n.lightFills) assertAtLeast(3.0, accent, fill, "accent")
            assertAtLeast(4.5, Color.White, accent, "label on accent")
        }
        for (accent in LumeTokens.Accent.dark) {
            for (page in n.darkPages + n.darkFills) assertAtLeast(4.5, accent, page, "accent")
            assertAtLeast(4.5, n.OnLightAccent, accent, "label on accent")
        }
    }

    @Test fun meaningColoursStandOutAsShapes() {
        for (c in LumeTokens.Semantic.light) for (p in n.lightPages + n.lightFills) assertAtLeast(3.0, c, p, "semantic")
        for (c in LumeTokens.Semantic.dark) for (p in n.darkPages + n.darkFills) assertAtLeast(3.0, c, p, "semantic")
    }

    /**
     * The calm ceiling: no accent or meaning colour goes past OKLCH chroma 0.16. The neon system
     * greens, yellows and reds sit at 0.18-0.23; only Destructive may, so red keeps its weight.
     */
    @Test fun noColourShouts() {
        val all = LumeTokens.Accent.light + LumeTokens.Accent.dark + LumeTokens.Semantic.light + LumeTokens.Semantic.dark
        for (c in all) assertTrue("${hex(c)} chroma ${chroma(c)}", chroma(c) <= 0.16)
    }

    /** Pages may carry a hint of paper warmth or night blue, never a colour of their own. */
    @Test fun neutralsStayNeutral() {
        for (c in n.lightPages + n.lightFills + n.darkPages + n.darkFills) assertTrue(hex(c), chroma(c) <= 0.025)
    }

    @Test fun hoursSplitTheDayIntoFourParts() {
        val parts = (0..23).map(LumeTokens.Hours::partOf)
        assertEquals(listOf(3, 3, 3, 3, 3, 3), parts.subList(0, 6))
        assertEquals(List(6) { 0 }, parts.subList(6, 12))
        assertEquals(List(8) { 1 }, parts.subList(12, 20))
        assertEquals(listOf(2, 2, 3, 3), parts.subList(20, 24))
    }

    private fun assertAtLeast(ratio: Double, fg: Color, bg: Color, what: String) {
        val r = contrast(fg, bg)
        assertTrue("$what ${hex(fg)} on ${hex(bg)} is ${"%.2f".format(r)}:1, needs $ratio:1", r >= ratio)
    }

    private fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color) = 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

    private fun contrast(fg: Color, bg: Color): Double {
        // A translucent colour is judged where it lands: composited over the page.
        val a = fg.alpha
        val flat = Color(fg.red * a + bg.red * (1 - a), fg.green * a + bg.green * (1 - a), fg.blue * a + bg.blue * (1 - a))
        val l1 = luminance(flat)
        val l2 = luminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun chroma(c: Color): Double {
        val r = linear(c.red); val g = linear(c.green); val b = linear(c.blue)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val aa = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return hypot(aa, bb)
    }

    private fun hex(c: Color) = "#%08X".format(c.value.toLong() ushr 32)
}
