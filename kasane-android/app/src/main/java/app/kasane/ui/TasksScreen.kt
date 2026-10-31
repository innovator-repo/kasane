package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import app.kasane.designsystem.pressable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import app.kasane.designsystem.GroupedList
import app.kasane.designsystem.Lume
import app.kasane.designsystem.LumeCheck
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.core.Alert
import app.kasane.core.Recurrence
import app.kasane.core.RepeatUnit
import app.kasane.core.Task
import app.kasane.core.TaskBucket
import app.kasane.core.TaskList
import app.kasane.designsystem.LumeChip
import androidx.compose.foundation.layout.Arrangement
import app.kasane.store
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(modifier: Modifier = Modifier, onAdd: () -> Unit = {}) {
    val store = LocalContext.current.store
    val journal by store.journal.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var editing by remember { mutableStateOf<Task?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }   // null = every list
    // A row tapped in the widget opens its edit form straight away.
    val requested by app.kasane.MainActivity.taskToOpen
    androidx.compose.runtime.LaunchedEffect(requested, journal.tasks) {
        val id = requested ?: return@LaunchedEffect
        journal.tasks.firstOrNull { it.id == id }?.let { editing = it }
        app.kasane.MainActivity.taskToOpen.value = null
    }
    var newList by remember { mutableStateOf(false) }
    var removeList by remember { mutableStateOf<TaskList?>(null) }
    val inList = journal.tasks.filter {
        when (selected) { null -> true; DEFAULT -> it.listId == null; else -> it.listId == selected }
    }
    // Grouped as Samsung Reminder does: by when each is due, oldest first, untimed before timed; done ones last.
    val open = inList.filter { it.open && !it.doneOn(today) }
        .sortedWith(compareBy<Task> { it.nextDate(today) ?: LocalDate.MAX }.thenBy { it.remindAt ?: -1 })
    val groups = open.groupBy { TaskBucket.of(it.nextDate(today) ?: LocalDate.MAX, today) }
    val finished = inList.filter { it.doneOn(today) || (!it.open && it.date >= today.minusDays(7).toString()) }
        .sortedByDescending { it.date }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                Text(stringResource(R.string.tab_tasks), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp))
            }
            item(key = "lists") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumeChip(stringResource(R.string.filter_all), selected == null, { selected = null })
                    LumeChip(stringResource(R.string.lists_default), selected == DEFAULT, { selected = DEFAULT })
                    journal.lists.forEach { l ->
                        LumeChip(l.name, selected == l.id, { selected = l.id })
                    }
                    LumeChip("+", false, { newList = true })
                }
            }
            if (open.isEmpty() && finished.isEmpty()) {
                item {
                    Text(stringResource(R.string.tasks_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
            TaskBucket.entries.forEach { bucket ->
                val rows = groups[bucket].orEmpty()
                if (rows.isEmpty()) return@forEach
                item(key = "h-$bucket") { SectionLabel(bucketLabel(bucket) + " · " + rows.size) }
                item(key = "g-$bucket") { GroupedList(rows) { task -> ListTaskRow(task, today, { editing = task }) } }
            }
            if (finished.isNotEmpty()) {
                item(key = "h-done") { SectionLabel(stringResource(R.string.widget_horizon_completed_header, finished.size)) }
                item(key = "g-done") { GroupedList(finished) { task -> ListTaskRow(task, today, { editing = task }) } }
            }
            val current = journal.lists.firstOrNull { it.id == selected }
            if (current != null) {
                item(key = "del-list") {
                    TextButton(onClick = { removeList = current }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                        Text(stringResource(R.string.lists_delete, current.name))
                    }
                }
            }
        }
        editing?.let { task ->
            TaskDialog(existing = task, onDismiss = { editing = null }) { title, date, minute, recurrence, alert, listId ->
                store.editTask(task.id, title, date, minute, recurrence, alert, listId)
                editing = null
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Lume.colors.accent, contentColor = Lume.colors.onAccent) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tasks_add))
        }
    }
    if (newList) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newList = false },
            title = { Text(stringResource(R.string.lists_new)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.lists_name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { selected = store.addList(name); newList = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { newList = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    removeList?.let { l ->
        AlertDialog(
            onDismissRequest = { removeList = null },
            title = { Text(l.name) },
            text = { Text(stringResource(R.string.lists_delete_explain)) },
            confirmButton = { TextButton(onClick = { store.deleteList(l.id); selected = null; removeList = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { removeList = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val DEFAULT = "default"

@Composable
internal fun bucketLabel(bucket: TaskBucket): String = stringResource(
    when (bucket) {
        TaskBucket.OVERDUE -> R.string.widget_overdue
        TaskBucket.TODAY -> R.string.widget_today
        TaskBucket.TOMORROW -> R.string.widget_tomorrow
        TaskBucket.WEEK -> R.string.widget_horizon_week
        TaskBucket.LATER -> R.string.widget_horizon_upcoming
    },
)

@Composable
private fun ListTaskRow(task: Task, today: LocalDate, onEdit: () -> Unit) {
    val store = LocalContext.current.store
    val date = task.nextDate(today)
    val done = task.doneOn(today) || !task.open
    val late = date != null && date.isBefore(today) && !done
    Row(
        Modifier.fillMaxWidth().pressable().clickable { onEdit() }.padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LumeCheck(done, { store.setTaskDone(task.id, it) })
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            val time = task.remindAt?.let { "%02d:%02d".format(it / 60, it % 60) }
            val day = date?.format(DateTimeFormatter.ofPattern("EEE d MMM")) ?: task.date
            val line = if (task.repeats) {
                listOfNotNull(
                    if (done) stringResource(R.string.tasks_done_today)
                    else task.rule?.summary(runCatching { LocalDate.parse(task.date) }.getOrDefault(today)) ?: "",
                    time, stringResource(R.string.tasks_next, day))
            } else listOfNotNull(day, time)
            Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = if (late) Lume.colors.caution else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { store.deleteTask(task.id) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDialog(existing: Task?, onDismiss: () -> Unit, onSave: (String, LocalDate, Int?, Recurrence?, Alert, String?) -> Unit) {
    val store = LocalContext.current.store
    val journal by store.journal.collectAsStateWithLifecycle()
    var listId by remember { mutableStateOf(existing?.listId) }
    var pickList by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember { mutableStateOf(existing?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var minute by remember { mutableStateOf(if (existing == null) 9 * 60 else existing.remindAt) }
    var recurrence by remember { mutableStateOf(existing?.rule) }
    var alert by remember { mutableStateOf(existing?.alert ?: Alert.MEDIUM) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }

    // Repeating every few minutes or hours only makes sense from a time of day.
    val subDaily = recurrence?.unit.let { it == RepeatUnit.MINUTE || it == RepeatUnit.HOUR }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.tasks_add else R.string.tasks_edit)) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tasks_what)) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { pickDate = true }) {
                    val day = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
                    Text(if (recurrence == null) day else stringResource(R.string.tasks_starts, day))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickTime = true }) {
                        Text(minute?.let { stringResource(R.string.tasks_remind_at, "%02d:%02d".format(it / 60, it % 60)) }
                            ?: stringResource(R.string.tasks_no_reminder))
                    }
                    if (minute != null) TextButton(onClick = { minute = null }) { Text(stringResource(R.string.tasks_clear)) }
                }
                Box {
                    TextButton(onClick = { pickList = true }) {
                        Text(stringResource(R.string.lists_in, journal.lists.firstOrNull { it.id == listId }?.name
                            ?: stringResource(R.string.lists_default)))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = pickList, onDismissRequest = { pickList = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.lists_default)) },
                            onClick = { listId = null; pickList = false })
                        journal.lists.forEach { l ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(l.name) }, onClick = { listId = l.id; pickList = false })
                        }
                    }
                }
                Label(stringResource(R.string.tasks_repeat))
                TextButton(onClick = { pickRepeat = true }) {
                    Text(recurrence?.summary(date) ?: stringResource(R.string.repeat_none))
                }
                if (subDaily && minute == null) {
                    Text(stringResource(R.string.tasks_sub_daily_needs_time), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (minute != null) AlertPicker(alert) { alert = it }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && !(subDaily && minute == null),
                onClick = { onSave(title, date, minute, recurrence, alert, listId) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickRepeat) RepeatPickerDialog(date, recurrence, { pickRepeat = false }) { recurrence = it; pickRepeat = false }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val start = minute ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { minute = state.hour * 60 + state.minute; pickTime = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
