package app.kasane.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.kasane.R

/**
 * The voices of the Lume apps. Geist for structure, Newsreader for the words that matter
 * (the habit to do now, a commitment), Geist Mono for numbers that tick.
 * Noto Serif JP is not bundled: Kasane shows no Japanese text of its own.
 */
object LumeType {
    val Geist = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
    )
    val GeistMono = FontFamily(
        Font(R.font.geist_mono_regular, FontWeight.Normal),
        Font(R.font.geist_mono_medium, FontWeight.Medium),
    )
    val Newsreader = FontFamily(
        Font(R.font.newsreader_regular, FontWeight.Normal),
        Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    )
}
