package app.kasane.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kasane.R
import app.kasane.core.Kind
import app.kasane.store

/** What the user is adding: a habit, a task or a rule. */
enum class AddType(val label: Int, val hint: Int, val badge: Int) {
    HABIT(R.string.add_type_habit, R.string.add_type_habit_hint, R.string.add_badge_habit),
    TASK(R.string.add_type_task, R.string.add_type_task_hint, R.string.add_badge_task),
    RULE(R.string.add_type_rule, R.string.add_type_rule_hint, R.string.add_badge_rule),
}

/**
 * The add flow used by the + buttons and the widgets: first ask whether it is a
 * habit, a task or a rule, then open the matching form.
 */
@Composable
fun AddFlow(onDismiss: () -> Unit) {
    val store = androidx.compose.ui.platform.LocalContext.current.store
    var type by remember { mutableStateOf<AddType?>(null) }
    when (type) {
        null -> AddTypeSelectorDialog(onDismiss = onDismiss, onSelect = { type = it })
        AddType.HABIT -> AddHabitDialog(onDismiss = onDismiss, kinds = listOf(Kind.CHECK, Kind.COUNT, Kind.TIMED)) {
            store.addItem(it)
            onDismiss()
        }
        AddType.RULE -> AddHabitDialog(onDismiss = onDismiss, kinds = listOf(Kind.RULE)) {
            store.addItem(it)
            onDismiss()
        }
        AddType.TASK -> AddTaskDialog(onDismiss = onDismiss) { title, date, minute, repeat, alert ->
            store.addTask(title, date, minute, repeat, alert)
            onDismiss()
        }
    }
}

@Composable
fun AddTypeSelectorDialog(onDismiss: () -> Unit, onSelect: (AddType) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_what), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.add_what_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AddType.entries.forEach { TypeOption(it) { onSelect(it) } }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun TypeOption(type: AddType, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(type.hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KindBadge(stringResource(type.badge))
        }
    }
}

/** A small tinted label such as HABIT, TASK or RULE. */
@Composable
fun KindBadge(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
