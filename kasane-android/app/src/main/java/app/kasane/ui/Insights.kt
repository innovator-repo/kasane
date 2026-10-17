package app.kasane.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.LumeCore
import app.kasane.core.Analysis
import app.kasane.core.Momentum
import app.kasane.core.PeriodRate
import app.kasane.core.SlipLink
import app.kasane.core.Item
import app.kasane.store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class Insights(
    val momentum: List<Momentum>,
    val weekly: Map<Item, List<PeriodRate>>,
    val lead: List<SlipLink>,
    val knockOn: List<SlipLink>,
    val engineIsLume: Boolean,
)

/** Runs the analysis off the main thread whenever the journal changes. */
@Composable
fun rememberInsights(): State<Insights?> {
    val journal by LocalContext.current.store.journal.collectAsStateWithLifecycle()
    return produceState<Insights?>(initialValue = null, journal) {
        value = withContext(Dispatchers.Default) {
            val core = LumeCore.active
            val analysis = Analysis(journal, LocalDate.now(), core)
            val momentum = analysis.momentum()
            val (lead, knockOn) = analysis.slipLinks()
            Insights(
                momentum = momentum,
                weekly = analysis.weeklyRates().mapValues { it.value.takeLast(8) },
                lead = lead.take(3),
                knockOn = knockOn.take(3),
                engineIsLume = core === LumeCore,
            )
        }
    }
}
