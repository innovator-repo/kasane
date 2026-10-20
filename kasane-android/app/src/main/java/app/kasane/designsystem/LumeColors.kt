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

object LumePalette {
    val SumiIndigo = Color(0xFF3D4FB8)
    val SumiIndigoDark = Color(0xFF8C9EFF)
    val SageLight = Color(0xFF2E9E6B)
    val SageDark = Color(0xFF30D158)
    val OchreLight = Color(0xFFD9822B)
    val OchreDark = Color(0xFFFFD60A)
    val SlateBlue = Color(0xFF5E7CE2)
    val Terracotta = Color(0xFFC95B3C)
    val SealLight = Color(0xFF5856D6)
    val SealDark = Color(0xFF8E8BFF)
    val AmberLight = Color(0xFFD97706)
    val AmberDark = Color(0xFFFF9F0A)
    /** The border tint when Ren or Duolingo is still open after 21:00. */
    val Urgent = Color(0xFFFF9500)

    val Washi = Color(0xFFFAF9F6)
    val Paper = Color(0xFFFFFFFF)
    val DayDark = Color(0xFF1C1C1E)
    val WarmGraphite = Color(0xFF181715)
    val Obsidian = Color(0xFF0B0F14)
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
        phase == Phase.SANCTUARY -> Color(0xFF141A22)
        phase == Phase.EVENING -> Color(0xFF23211E)
        dark -> Color(0xFF252527)
        else -> Color(0xFFF4F4F6)
    }
    return if (dark) LumeColors(
        accent = LumePalette.SumiIndigoDark,
        onAccent = Color(0xFF0E1652),
        accentContainer = Color(0xFF26316F),
        onAccentContainer = Color(0xFFE4E8FF),
        canvas = canvas,
        grouped = grouped,
        hairline = Color(0x1FFFFFFF),
        text = Color(0xFFE8E9ED),
        textSecondary = Color(0xFFA3A7B3),
        floor = LumePalette.SlateBlue,
        target = LumePalette.SageDark,
        stretch = LumePalette.OchreDark,
        slip = LumePalette.Terracotta,
        seal = LumePalette.SealDark,
        caution = LumePalette.AmberDark,
        error = Color(0xFFF2B8B5),
        dark = true,
    ) else LumeColors(
        accent = LumePalette.SumiIndigo,
        onAccent = Color.White,
        accentContainer = Color(0xFFE4E8FF),
        onAccentContainer = Color(0xFF14205E),
        canvas = canvas,
        grouped = grouped,
        hairline = Color(0x243C3C43),
        text = Color(0xFF15161A),
        textSecondary = Color(0xFF5B5F6B),
        floor = LumePalette.SlateBlue,
        target = LumePalette.SageLight,
        stretch = LumePalette.OchreLight,
        slip = LumePalette.Terracotta,
        seal = LumePalette.SealLight,
        caution = LumePalette.AmberLight,
        error = Color(0xFFB3261E),
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
