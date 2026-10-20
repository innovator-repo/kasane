package app.kasane.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import app.kasane.R

/**
 * The voices of the Lume apps. Geist for structure, Newsreader for the words that matter
 * (the habit to do now, a commitment), Geist Mono for numbers that tick.
 * Noto Serif JP is a subset (kana, latin and the 重ね emblem), for the Kanji voice and the watermark.
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
    val NotoSerifJP = FontFamily(
        Font(R.font.noto_serif_jp_regular, FontWeight.Normal),
        Font(R.font.noto_serif_jp_semibold, FontWeight.SemiBold),
    )

    /** The Kanji voice: tagged ja-JP so Han characters take their Japanese forms. */
    val Kanji = TextStyle(fontFamily = NotoSerifJP, fontWeight = FontWeight.SemiBold, localeList = LocaleList("ja-JP"))

    /** The emblem, from Kasane's name. */
    const val EMBLEM = "重ね"

    val Newsreader = FontFamily(
        Font(R.font.newsreader_regular, FontWeight.Normal),
        Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    )
}
