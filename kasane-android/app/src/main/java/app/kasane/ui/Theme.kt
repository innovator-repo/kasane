package app.kasane.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.kasane.core.Phase
import app.kasane.designsystem.LocalLumeColors
import app.kasane.designsystem.LocalLumeHaptics
import app.kasane.designsystem.LumeHaptics
import app.kasane.designsystem.LumeType
import app.kasane.designsystem.lumeColors
import app.kasane.designsystem.toColorScheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Kept for the widgets and older callers; the voices live in [LumeType]. */
val Geist = LumeType.Geist

/** The static schemes the home screen widgets use, which have no clock to follow. */
internal val Light = lumeColors(Phase.DAY, false).toColorScheme()
internal val Dark = lumeColors(Phase.DAY, true).toColorScheme()

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = LumeType.Geist, fontWeight = weight, fontSize = size.sp)

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
    val phase by rememberPhase()
    val colors = lumeColors(phase, isSystemInDarkTheme())
    val context = LocalContext.current
    CompositionLocalProvider(LocalLumeColors provides colors, LocalLumeHaptics provides LumeHaptics.get(context)) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = Type, content = content)
    }
}

/** The phase of the day, re-read every minute so the surface shifts while the app is open. */
@Composable
private fun rememberPhase(): State<Phase> {
    val phase = remember { mutableStateOf(Phase.of(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            phase.value = Phase.of(LocalTime.now())
        }
    }
    return phase
}
