package app.kasane.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.kasane.core.Phase

/**
 * Kasane's colours. The accent is Sumi Indigo, the colour of layered ink. A habit's tier
 * gets its own warm reward colour. A miss is never red: error is kept for deleting things,
 * and a slipped rule is a calm terracotta so a bad moment does not turn into a bad day.
 */
@Immutable
class LumeColors(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    /** The page. */
    val canvas: Color,
    /** The soft inset fill behind grouped lists. */
    val grouped: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    /** Tier.FLOOR, partial progress. */
    val floor: Color,
    /** Tier.TARGET, momentum sage. */
    val target: Color,
    /** Tier.STRETCH, golden ochre. */
    val stretch: Color,
    /** A slipped rule. */
    val slip: Color,
    /** Sealed or time-locked commitments, the ecosystem's seal indigo. */
    val seal: Color,
    /** Due, pending, last chance. */
    val caution: Color,
    val error: Color,
    val dark: Boolean,
)

/** Kasane's names for the shared [LumeTokens]. */
object LumePalette {
    val SumiIndigo = LumeTokens.Accent.KasaneSumi
    val SumiIndigoDark = LumeTokens.Accent.KasaneSumiDark
    val SageLight = LumeTokens.Semantic.Sage
    val SageDark = LumeTokens.Semantic.SageDark
    val OchreLight = LumeTokens.Semantic.Ochre
    val OchreDark = LumeTokens.Semantic.OchreDark
    val SlateBlue = LumeTokens.Semantic.Info
    val SlateBlueDark = LumeTokens.Semantic.InfoDark
    val Terracotta = LumeTokens.Semantic.Terracotta
    val TerracottaDark = LumeTokens.Semantic.TerracottaDark
    val SealLight = LumeTokens.Semantic.Seal
    val SealDark = LumeTokens.Semantic.SealDark
    val AmberLight = LumeTokens.Semantic.Amber
    val AmberDark = LumeTokens.Semantic.AmberDark
    /** The border tint when Ren or Duolingo is still open after 21:00, on the always-dark evening page. */
    val Urgent = LumeTokens.Semantic.AmberDark

    val Washi = LumeTokens.Neutral.Washi
    val Paper = LumeTokens.Neutral.Paper
    val DayDark = LumeTokens.Neutral.DayDark
    val WarmGraphite = LumeTokens.Neutral.WarmGraphite
    val Obsidian = LumeTokens.Neutral.Obsidian
}

/**
 * The page and grouped fills for the time of day: warm washi in the morning, plain paper by day,
 * warm graphite in the evening, and obsidian once the sleep lock holds. Evening and night are dark
 * whatever the system theme says, to keep blue light down before sleep.
 */
fun lumeColors(phase: Phase, systemDark: Boolean): LumeColors {
    val dark = systemDark || phase == Phase.EVENING || phase == Phase.SANCTUARY
    val canvas = when (phase) {
        Phase.MORNING -> if (dark) LumePalette.DayDark else LumePalette.Washi
        Phase.DAY -> if (dark) LumePalette.DayDark else LumePalette.Paper
        Phase.EVENING -> LumePalette.WarmGraphite
        Phase.SANCTUARY -> LumePalette.Obsidian
    }
    val grouped = when {
        phase == Phase.SANCTUARY -> LumeTokens.Neutral.ObsidianGrouped
        phase == Phase.EVENING -> LumeTokens.Neutral.WarmGraphiteGrouped
        dark -> LumeTokens.Neutral.DayDarkGrouped
        phase == Phase.MORNING -> LumeTokens.Neutral.WashiGrouped
        else -> LumeTokens.Neutral.PaperGrouped
    }
    return if (dark) LumeColors(
        accent = LumePalette.SumiIndigoDark,
        onAccent = LumeTokens.Neutral.OnLightAccent,
        accentContainer = Color(0xFF26316F),
        onAccentContainer = Color(0xFFE4E8FF),
        canvas = canvas,
        grouped = grouped,
        hairline = LumeTokens.Neutral.HairlineDark,
        text = LumeTokens.Neutral.InkDark,
        textSecondary = LumeTokens.Neutral.InkDarkSecondary,
        floor = LumePalette.SlateBlueDark,
        target = LumePalette.SageDark,
        stretch = LumePalette.OchreDark,
        slip = LumePalette.TerracottaDark,
        seal = LumePalette.SealDark,
        caution = LumePalette.AmberDark,
        error = LumeTokens.Semantic.DestructiveDark,
        dark = true,
    ) else LumeColors(
        accent = LumePalette.SumiIndigo,
        onAccent = Color.White,
        accentContainer = Color(0xFFE4E8FF),
        onAccentContainer = Color(0xFF14205E),
        canvas = canvas,
        grouped = grouped,
        hairline = LumeTokens.Neutral.HairlineLight,
        text = LumeTokens.Neutral.InkLight,
        textSecondary = LumeTokens.Neutral.InkLightSecondary,
        floor = LumePalette.SlateBlue,
        target = LumePalette.SageLight,
        stretch = LumePalette.OchreLight,
        slip = LumePalette.Terracotta,
        seal = LumePalette.SealLight,
        caution = LumePalette.AmberLight,
        error = LumeTokens.Semantic.Destructive,
        dark = false,
    )
}

/** Material's scheme drawn from the Lume tokens, so stock components pick up the same colours. */
fun LumeColors.toColorScheme(): ColorScheme = if (dark) darkColorScheme(
    primary = accent, onPrimary = onAccent, primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
    background = canvas, onBackground = text, surface = canvas, onSurface = text,
    surfaceVariant = grouped, onSurfaceVariant = textSecondary, outline = hairline, error = error,
) else lightColorScheme(
    primary = accent, onPrimary = onAccent, primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
    background = canvas, onBackground = text, surface = canvas, onSurface = text,
    surfaceVariant = grouped, onSurfaceVariant = textSecondary, outline = Color(0xFFD9DBE1), error = error,
)

val LocalLumeColors = staticCompositionLocalOf { lumeColors(Phase.DAY, false) }

/** `Lume.colors` inside any composable under [app.kasane.ui.KasaneTheme]. */
object Lume {
    val colors: LumeColors
        @androidx.compose.runtime.Composable get() = LocalLumeColors.current
}
