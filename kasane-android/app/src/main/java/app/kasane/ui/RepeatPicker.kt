package app.kasane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kasane.R
import app.kasane.core.MonthMode
import app.kasane.core.Recurrence
import app.kasane.core.RepeatEnd
import app.kasane.core.RepeatUnit
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The repeat screen, laid out like Samsung Reminder: a summary line, one row per unit with its
 * number written inline, the extra choices under the chosen unit, and a duration section.
 * `first` is the task's own date, which the month and year choices are worded from.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepeatPickerDialog(first: LocalDate, current: Recurrence?, onDismiss: () -> Unit, onDone: (Recurrence?) -> Unit) {
    var rec by remember { mutableStateOf(current) }
    var pickUntil by remember { mutableStateOf(false) }
    val r = rec

    fun setUnit(unit: RepeatUnit) {
        val keep = r?.takeIf { it.unit == unit }
        rec = keep ?: Recurrence(unit, 1, end = r?.end ?: RepeatEnd.FOREVER, count = r?.count ?: 10, until = r?.until,
            weekdays = if (unit == RepeatUnit.WEEK) listOf(first.dayOfWeek.value) else emptyList())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleLarge)
                Text(r?.summary(first) ?: stringResource(R.string.repeat_doesnt), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

                Row(Modifier.fillMaxWidth().clickable { rec = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(r == null, { rec = null })
                    Text(stringResource(R.string.repeat_none))
                }
                for ((unit, label) in UNITS) {
                    val on = r?.unit == unit
                    Row(Modifier.fillMaxWidth().clickable { setUnit(unit) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(on, { setUnit(unit) })
                        Text(stringResource(R.string.repeat_every))
                        NumberField(if (on) r!!.interval else 1, Modifier.padding(horizontal = 8.dp).width(72.dp)) {
                            if (!on) setUnit(unit)
                            rec = (rec ?: return@NumberField).copy(interval = it.coerceIn(1, 999))
                        }
                        val n = if (on) r!!.interval else 1
                        Text(stringResource(label) + if (n == 1) "" else "s")
                    }
                    if (!on || r == null) continue
                    when (unit) {
                        RepeatUnit.WEEK -> WeekdayPicker(r.weekdays) { rec = r.copy(weekdays = it.sorted().ifEmpty { listOf(first.dayOfWeek.value) }) }
                        RepeatUnit.MONTH -> {
                            Pills {
                                Pill(stringResource(R.string.repeat_on_day, Recurrence.ordinal(first.dayOfMonth)),
                                    r.monthMode == MonthMode.DAY_OF_MONTH) { rec = r.copy(monthMode = MonthMode.DAY_OF_MONTH) }
                                Pill(stringResource(R.string.repeat_on_nth, Recurrence.nthLabel(first)),
                                    r.monthMode == MonthMode.NTH_WEEKDAY) { rec = r.copy(monthMode = MonthMode.NTH_WEEKDAY) }
                                Pill(stringResource(R.string.repeat_pick_dates), r.monthMode == MonthMode.DATES) {
                                    rec = r.copy(monthMode = MonthMode.DATES, monthDates = r.monthDates.ifEmpty { listOf(first.dayOfMonth) })
                                }
                            }
                            if (r.monthMode == MonthMode.DATES) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (d in 1..31) {
                                    val chosen = d in r.monthDates
                                    FilterChip(chosen, {
                                        val next = if (chosen) r.monthDates - d else r.monthDates + d
                                        if (next.isNotEmpty()) rec = r.copy(monthDates = next.sorted())
                                    }, label = { Text("$d") })
                                }
                            }
                        }
                        RepeatUnit.YEAR -> {
                            val monthName = first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            Pills {
                                Pill(stringResource(R.string.repeat_on_date_year, "${Recurrence.ordinal(first.dayOfMonth)} $monthName"),
                                    r.monthMode == MonthMode.DAY_OF_MONTH) { rec = r.copy(monthMode = MonthMode.DAY_OF_MONTH) }
                                Pill(stringResource(R.string.repeat_on_nth_year, Recurrence.nthLabel(first), monthName),
                                    r.monthMode == MonthMode.NTH_WEEKDAY) { rec = r.copy(monthMode = MonthMode.NTH_WEEKDAY) }
                            }
                            Text(stringResource(R.string.repeat_pick_months, Recurrence.ordinal(first.dayOfMonth)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (m in 1..12) {
                                    val chosen = m in r.months.ifEmpty { listOf(first.monthValue) }
                                    FilterChip(chosen, {
                                        val base = r.months.ifEmpty { listOf(first.monthValue) }
                                        val next = if (chosen) base - m else base + m
                                        if (next.isNotEmpty()) rec = r.copy(months = next.sorted())
                                    }, label = { Text(Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
                                }
                            }
                        }
                        else -> {}
                    }
                }

                if (r != null) {
                    Label(stringResource(R.string.repeat_duration))
                    Row(Modifier.fillMaxWidth().clickable { rec = r.copy(end = RepeatEnd.FOREVER) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(r.end == RepeatEnd.FOREVER, { rec = r.copy(end = RepeatEnd.FOREVER) })
                        Text(stringResource(R.string.repeat_forever))
                    }
                    Row(Modifier.fillMaxWidth().clickable { rec = r.copy(end = RepeatEnd.COUNT) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(r.end == RepeatEnd.COUNT, { rec = r.copy(end = RepeatEnd.COUNT) })
                        if (r.end == RepeatEnd.COUNT) {
                            NumberField(r.count, Modifier.padding(end = 8.dp).width(72.dp)) { rec = r.copy(count = it.coerceIn(1, 999)) }
                            Text(stringResource(R.string.repeat_times_total))
                        } else Text(stringResource(R.string.repeat_count))
                    }
                    Row(Modifier.fillMaxWidth().clickable { rec = r.copy(end = RepeatEnd.UNTIL, until = r.until ?: first.plusYears(1).toString()); pickUntil = true },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(r.end == RepeatEnd.UNTIL, { rec = r.copy(end = RepeatEnd.UNTIL, until = r.until ?: first.plusYears(1).toString()); pickUntil = true })
                        val until = r.until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        Text(if (r.end == RepeatEnd.UNTIL && until != null)
                            stringResource(R.string.repeat_until_date, until.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
                        else stringResource(R.string.repeat_until))
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onDone(rec) }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }

    if (pickUntil) {
        val start = r?.until?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: first
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickUntil = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        rec = rec?.copy(end = RepeatEnd.UNTIL, until = maxOf(d, first).toString())
                    }
                    pickUntil = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

private val UNITS = listOf(
    RepeatUnit.MINUTE to R.string.repeat_minute, RepeatUnit.HOUR to R.string.repeat_hour, RepeatUnit.DAY to R.string.repeat_day,
    RepeatUnit.WEEK to R.string.repeat_week, RepeatUnit.MONTH to R.string.repeat_month, RepeatUnit.YEAR to R.string.repeat_year,
)

/** A number the user types; an empty box is held as 1 so the repeat is never invalid. */
@Composable
private fun NumberField(value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(value = value.toString(), onValueChange = { text -> text.filter(Char::isDigit).take(3).toIntOrNull()?.let(onChange) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 40.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(text) }, modifier = Modifier.fillMaxWidth())
}
