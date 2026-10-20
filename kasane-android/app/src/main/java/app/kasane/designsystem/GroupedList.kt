package app.kasane.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The inset a row's hairline starts at, so it lines up with the text after a leading icon. */
val GroupedInset = 52.dp

/**
 * Rows on one soft inset fill, split by hairlines, instead of a stack of bordered cards.
 * Pass the rows as [items]; hairlines go between them, not after the last.
 */
@Composable
fun <T> GroupedList(
    items: List<T>,
    modifier: Modifier = Modifier,
    hairlineInset: Dp = 16.dp,
    row: @Composable (T) -> Unit,
) {
    val c = Lume.colors
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.grouped),
    ) {
        items.forEachIndexed { index, item ->
            row(item)
            if (index < items.lastIndex) Hairline(hairlineInset)
        }
    }
}

@Composable
fun Hairline(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(Lume.colors.hairline))
}
