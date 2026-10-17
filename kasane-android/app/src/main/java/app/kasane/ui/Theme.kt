package app.kasane.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kasane.R

/** Geist, the same family as Ren, so the apps read as one set. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

/** Indigo for momentum, the colour of layered ink. */
private val Ink = Color(0xFF3D4FB8)
private val InkLight = Color(0xFFAAB6FF)

internal val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E8FF),
    onPrimaryContainer = Color(0xFF14205E),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF15161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15161A),
    surfaceVariant = Color(0xFFF2F3F6),
    onSurfaceVariant = Color(0xFF5B5F6B),
    outline = Color(0xFFD9DBE1),
    error = Color(0xFFB3261E),
)

internal val Dark = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF0E1652),
    primaryContainer = Color(0xFF26316F),
    onPrimaryContainer = Color(0xFFE4E8FF),
    background = Color(0xFF0F1013),
    onBackground = Color(0xFFE8E9ED),
    surface = Color(0xFF0F1013),
    onSurface = Color(0xFFE8E9ED),
    surfaceVariant = Color(0xFF1C1E23),
    onSurfaceVariant = Color(0xFFA3A7B3),
    outline = Color(0xFF2E3138),
    error = Color(0xFFF2B8B5),
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Geist, fontWeight = weight, fontSize = size.sp)

private val Type = Typography(
    headlineMedium = style(28, FontWeight.SemiBold),
    titleLarge = style(20, FontWeight.SemiBold),
    titleMedium = style(16, FontWeight.Medium),
    bodyLarge = style(16),
    bodyMedium = style(15),
    bodySmall = style(13),
    labelLarge = style(14, FontWeight.Medium),
    labelMedium = style(12, FontWeight.Medium),
    labelSmall = style(11, FontWeight.Medium),
)

@Composable
fun KasaneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
