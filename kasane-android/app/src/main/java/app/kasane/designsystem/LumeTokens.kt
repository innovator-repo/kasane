package app.kasane.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Lume, the design system Sigil, Vow, Ren, Kasane and Katachi share.
 *
 * This file is the same in all five apps apart from its package line, so a change here is made in
 * every app at once. The reasoning is in docs/LUME.md, and LumeTokensTest holds the rules: text
 * contrast, accent contrast, and the calm chroma ceiling that keeps colour from shouting.
 */
object LumeTokens {

    /**
     * Pages for the four parts of the day and the inks that sit on them. Light pages are warm
     * paper rather than blue-white; dark pages are soft graphite rather than pure black, which
     * smears white text on OLED and reads as harsh.
     */
    object Neutral {
        /** Morning page, light. */
        val Washi = Color(0xFFFAF9F6)
        val WashiGrouped = Color(0xFFF1EFE9)
        /** Day page, light. */
        val Paper = Color(0xFFFFFFFF)
        val PaperGrouped = Color(0xFFF4F4F6)

        /** Day page, dark. */
        val DayDark = Color(0xFF1C1C1E)
        val DayDarkGrouped = Color(0xFF252527)
        val DayDarkRaised = Color(0xFF2C2C2E)
        /** The page under raised dark cards, where a screen is built from cards on a deeper page. */
        val DayDarkBase = Color(0xFF121214)

        /** Evening page: warm graphite, dark whatever the system says. */
        val WarmGraphite = Color(0xFF181715)
        val WarmGraphiteRaised = Color(0xFF211F1D)
        val WarmGraphiteGrouped = Color(0xFF23211E)

        /** Sleep page: obsidian, while Vow's Sleep Lock and Kasane's sanctuary hold. */
        val Obsidian = Color(0xFF0B0F14)
        val ObsidianRaised = Color(0xFF131922)
        val ObsidianGrouped = Color(0xFF141A22)

        val InkLight = Color(0xFF1D1D1F)
        val InkLightSecondary = Color(0xFF5E5E63)
        val InkDark = Color(0xFFF2F2F5)
        val InkDarkSecondary = Color(0xFFA1A1A6)
        val HairlineLight = Color(0x243C3C43)
        val HairlineDark = Color(0x1FFFFFFF)

        /** Text on the light accents that dark pages use. */
        val OnLightAccent = Color(0xFF101418)

        val lightPages = listOf(Washi, Paper)
        val darkPages = listOf(DayDark, DayDarkBase, WarmGraphite, Obsidian)
        val lightFills = listOf(WashiGrouped, PaperGrouped)
        val darkFills = listOf(DayDarkGrouped, DayDarkRaised, WarmGraphiteRaised, WarmGraphiteGrouped, ObsidianRaised, ObsidianGrouped)
    }

    /** Each app's one accent, as a light-page / dark-page pair. */
    object Accent {
        val SigilSapphire = Color(0xFF2A68B7)
        val SigilSapphireDark = Color(0xFF76B2F5)
        val VowTeal = Color(0xFF007D70)
        val VowTealDark = Color(0xFF5DD2BD)
        val RenAkane = Color(0xFFBB443A)
        val RenAkaneDark = Color(0xFFF18477)
        val KasaneSumi = Color(0xFF3F52AF)
        val KasaneSumiDark = Color(0xFF97A8F7)
        val KatachiJade = Color(0xFF1E7D68)
        val KatachiJadeDark = Color(0xFF66D4B7)

        val light = listOf(SigilSapphire, VowTeal, RenAkane, KasaneSumi, KatachiJade)
        val dark = listOf(SigilSapphireDark, VowTealDark, RenAkaneDark, KasaneSumiDark, KatachiJadeDark)
    }

    /** Colours that mean the same thing in every app. */
    object Semantic {
        /** Done, correct, on track. */
        val Sage = Color(0xFF2D8152)
        val SageDark = Color(0xFF70CB92)
        /** Reward and the stretch goal: warm, not gold-rush yellow. */
        val Ochre = Color(0xFFA9730F)
        val OchreDark = Color(0xFFE6BA5C)
        /** A miss or a slip. Never red, so a bad moment does not turn into a bad day. */
        val Terracotta = Color(0xFFC95B3C)
        val TerracottaDark = Color(0xFFE5886E)
        /** Sealed, locked, time-bound commitments across the suite. */
        val Seal = Color(0xFF5D5CC2)
        val SealDark = Color(0xFF9B9DF4)
        /** Due soon, pending, worth a look. */
        val Amber = Color(0xFFB86A10)
        val AmberDark = Color(0xFFF5AD5A)
        /** Neutral highlights: links, digits, radicals. */
        val Info = Color(0xFF197CB3)
        val InfoDark = Color(0xFF72BDF0)
        /** Only for deleting things or a real security fault, so red keeps its weight. */
        val Destructive = Color(0xFFB3261E)
        val DestructiveDark = Color(0xFFF2B8B5)

        val light = listOf(Sage, Ochre, Terracotta, Seal, Amber, Info)
        val dark = listOf(SageDark, OchreDark, TerracottaDark, SealDark, AmberDark, InfoDark)
    }

    /** The hours where the four parts of the day begin. Every app's phase follows these. */
    object Hours {
        const val MORNING = 6
        const val DAY = 12
        const val EVENING = 20
        const val SLEEP = 22

        /** 0 morning, 1 day, 2 evening, 3 sleep. */
        fun partOf(hour: Int): Int = when {
            hour < MORNING || hour >= SLEEP -> 3
            hour < DAY -> 0
            hour < EVENING -> 1
            else -> 2
        }
    }

    /** Springs settle in about 250 ms, quick enough to feel instant and slow enough to follow. */
    object Motion {
        /** Movement, scale, sheets: a little life, no wobble. */
        const val SPATIAL_DAMPING = 0.75f
        const val SPATIAL_STIFFNESS = 380f
        /** Colour, opacity, progress: no overshoot, so nothing flickers. */
        const val EFFECTS_DAMPING = 1.0f
        const val EFFECTS_STIFFNESS = 1500f
        const val PRESSED_SCALE = 0.97f
        /** One half of a breath, in or out. */
        const val BREATH_MS = 4000
    }

    /** Corner radii. Pick the step; do not invent a 7 or a 14. */
    object Shape {
        /** Bars, chips' inner marks. */
        val XXS: Dp = 4.dp
        /** Tiles, small fields. */
        val XS: Dp = 8.dp
        /** Buttons, inputs, rows. */
        val S: Dp = 12.dp
        /** Cards and grouped lists. */
        val M: Dp = 16.dp
        /** Hero cards and dialogs. */
        val L: Dp = 24.dp
        /** Bottom sheets. */
        val XL: Dp = 32.dp
    }

    /** Spacing steps on a 4 dp grid. */
    object Space {
        val XS: Dp = 4.dp
        val S: Dp = 8.dp
        val M: Dp = 12.dp
        val L: Dp = 16.dp
        val XL: Dp = 24.dp
        val XXL: Dp = 32.dp
    }

    /**
     * Type scale in sp, so it follows the user's font size. Body is 16 and nothing is
     * below 12. Weights stay at Normal, Medium and SemiBold: heavier weights shout.
     */
    object Type {
        val Caption: TextUnit = 12.sp
        val Footnote: TextUnit = 13.sp
        val Body: TextUnit = 16.sp
        val Callout: TextUnit = 17.sp
        val Title: TextUnit = 22.sp
        val Headline: TextUnit = 28.sp
        val Display: TextUnit = 34.sp

        /** Line height as a multiple of size: 1.4 for reading, 1.2 for headings. */
        const val BODY_LINE = 1.4f
        const val HEADING_LINE = 1.2f

        val scale = listOf(Caption, Footnote, Body, Callout, Title, Headline, Display)
    }

    /** Sizes and states for controls, so a button feels the same in every app. */
    object Control {
        /** Smallest tap target. Material and WCAG 2.5.8 ask for 48 dp; fingers miss smaller ones. */
        val MinTouch: Dp = 48.dp
        val ButtonHeight: Dp = 48.dp
        val ChipHeight: Dp = 40.dp
        /** Checkbox and radio mark inside a MinTouch target. */
        val CheckMark: Dp = 24.dp
        val Hairline: Dp = 1.dp
        /** Disabled controls keep their shape and drop to this opacity, never to a new colour. */
        const val DISABLED_ALPHA = 0.38f
        /** Opacity of a pressed or focused tint laid over a surface. */
        const val STATE_ALPHA = 0.12f
    }
}
