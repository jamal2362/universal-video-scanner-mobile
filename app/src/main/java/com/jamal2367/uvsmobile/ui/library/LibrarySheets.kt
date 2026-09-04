package com.jamal2367.uvsmobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.LibraryQuery
import com.jamal2367.uvsmobile.data.model.RangeField
import com.jamal2367.uvsmobile.data.model.RangeUnit
import com.jamal2367.uvsmobile.data.model.RangeValue
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.SortOrder
import com.jamal2367.uvsmobile.util.HdrGroup
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The order to put the library in.
 *
 * Every order the web interface offers, ranked the same way by the server -
 * including its combined modes, which travel as one comma-separated `sort`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SortSheet(
    current: SortOption,
    order: SortOrder,
    onSelect: (SortOption) -> Unit,
    onOrder: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.library_order),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            // Two halves of one choice, so they are drawn as one connected
            // pair: the outer ends are rounded, the join between them is not,
            // and the half in force swells into a different shape as it takes
            // over. That shape change is the whole tell - it says which way
            // the library is sorted without a tick mark.
            //
            // One word each, held to one line: half the width of a phone is
            // not enough for a second one, and a label that wraps makes its
            // half of the pair taller than the other.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToggleButton(
                    checked = order == SortOrder.ASC,
                    onCheckedChange = { onOrder(SortOrder.ASC) },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.library_order_asc), maxLines = 1)
                }
                ToggleButton(
                    checked = order == SortOrder.DESC,
                    onCheckedChange = { onOrder(SortOrder.DESC) },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.library_order_desc), maxLines = 1)
                }
            }

            Text(
                text = stringResource(R.string.library_sort),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            SortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == current, onClick = { onSelect(option) })
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Narrowing the library down, server-side.
 *
 * The exact-match fields offer what the library actually contains, read off the
 * statistics; the three the counts do not cover stay free text, which the API
 * matches case-insensitively anyway. Every range is inclusive at both ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    query: LibraryQuery,
    options: FilterOptions,
    onFilter: (FilterField, String?) -> Unit,
    onFilters: (Map<FilterField, String?>) -> Unit,
    onRange: (RangeField, RangeValue) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filter_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.filter_reset_all))
                }
            }

            // The chart filter the API documents as `max_imdb_top250=250`:
            // "in the chart" rather than "everything", because an entry with no
            // rank is dropped from a ranged field rather than read as zero.
            FilterChip(
                selected = query.ranges[RangeField.IMDB_TOP250]?.max == 250.0,
                onClick = {
                    val active = query.ranges[RangeField.IMDB_TOP250]?.max == 250.0
                    onRange(
                        RangeField.IMDB_TOP250,
                        if (active) RangeValue() else RangeValue(min = 1.0, max = 250.0),
                    )
                },
                label = { Text(stringResource(R.string.filter_only_top250)) },
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.filter_exact),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            FilterField.entries.forEach { field ->
                // The grade is the one field whose stored value is not what
                // anyone is looking for, so it gets its own row of choices.
                if (field == FilterField.HDR_FORMAT && options.hdrGroups.isNotEmpty()) {
                    HdrFilter(
                        label = stringResource(field.labelRes),
                        groups = options.hdrGroups,
                        filters = query.filters,
                        onSelect = onFilters,
                    )
                    return@forEach
                }

                val values = options.forField(field)
                when {
                    values.isNotEmpty() -> ChoiceFilter(
                        label = stringResource(field.labelRes),
                        values = values,
                        selected = query.filters[field],
                        onSelect = { onFilter(field, it) },
                    )

                    // The values are known and there are none: the library holds
                    // nothing to narrow down by here, so the field is left out
                    // rather than offered as a box that can only come back empty.
                    options.loaded -> Unit

                    // Not known yet - the server did not answer. A text box at
                    // least lets someone who knows the exact value type it.
                    else -> FreeTextFilter(
                        label = stringResource(field.labelRes),
                        value = query.filters[field].orEmpty(),
                        onValue = { onFilter(field, it) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.filter_ranges),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )

            RangeField.entries.filter { it != RangeField.MTIME }.forEach { field ->
                RangeFilterRow(
                    label = stringResource(field.labelRes),
                    unit = field.unit,
                    value = query.ranges[field] ?: RangeValue(),
                    onValue = { onRange(field, it) },
                )
            }

            // The modification time is the one range nobody wants to type an
            // epoch for: what a person means by it is "recently".
            RecentlyModifiedRow(
                value = query.ranges[RangeField.MTIME] ?: RangeValue(),
                onValue = { onRange(RangeField.MTIME, it) },
            )
        }
    }
}

/**
 * The grade, chosen by the name it is known under.
 *
 * Each chip carries the field that finds it - the detail line for a profile,
 * the enhancement layer for a FEL or a MEL, the format for the rest - so
 * picking one clears the other two picture fields as it sets its own. One
 * grade at a time is what a row of chips promises, and three fields left half
 * set would quietly narrow the library to nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HdrFilter(
    label: String,
    groups: List<HdrGroup>,
    filters: Map<FilterField, String>,
    onSelect: (Map<FilterField, String?>) -> Unit,
) {
    val cleared = HDR_FIELDS.associateWith { null }
    val selected = groups.firstOrNull { filters[it.field].equals(it.value, ignoreCase = true) }

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(cleared) },
                label = { Text(stringResource(R.string.filter_any)) },
            )
            groups.forEach { group ->
                FilterChip(
                    selected = group == selected,
                    onClick = {
                        onSelect(
                            if (group == selected) cleared else cleared + (group.field to group.value)
                        )
                    },
                    label = { Text(group.label) },
                )
            }
        }
    }
}

/** The three fields one grade is spread across. */
private val HDR_FIELDS =
    listOf(FilterField.HDR_FORMAT, FilterField.HDR_DETAIL, FilterField.EL_TYPE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceFilter(
    label: String,
    values: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.filter_any)) },
            )
            values.forEach { value ->
                FilterChip(
                    selected = selected.equals(value, ignoreCase = true),
                    onClick = { onSelect(if (selected.equals(value, ignoreCase = true)) null else value) },
                    label = { Text(value) },
                )
            }
        }
    }
}

/**
 * A filter typed out by hand.
 *
 * Only reached when the server never told us which values exist. The API
 * matches a filter exactly, so this says so rather than letting someone guess
 * at a substring and conclude the filter is broken.
 */
@Composable
private fun FreeTextFilter(label: String, value: String, onValue: (String?) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }

    // A keystroke is not a question: the library is asked once the typing stops.
    LaunchedEffect(text) {
        if (text != value) {
            delay(INPUT_DEBOUNCE_MS.milliseconds)
            onValue(text.ifBlank { null })
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        supportingText = { Text(stringResource(R.string.filter_exact_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
}

/**
 * The two ends of a range.
 *
 * Typed in the unit a person thinks in - minutes, gigabytes, megabits - and
 * converted to what the API stores, which is seconds, bytes and kilobits.
 */
@Composable
private fun RangeFilterRow(
    label: String,
    unit: RangeUnit,
    value: RangeValue,
    onValue: (RangeValue) -> Unit,
) {
    var minText by remember(value.min) { mutableStateOf(value.min.toDisplay(unit)) }
    var maxText by remember(value.max) { mutableStateOf(value.max.toDisplay(unit)) }

    // Typing "6", "60", "600" is one question, not three - and without this the
    // library is re-fetched, and the list redrawn, on every digit.
    LaunchedEffect(minText, maxText) {
        val typed = RangeValue(min = minText.toStored(unit), max = maxText.toStored(unit))
        if (typed != value) {
            delay(INPUT_DEBOUNCE_MS.milliseconds)
            onValue(typed)
        }
    }

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = "$label${unit.suffix()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it },
                label = { Text(stringResource(R.string.filter_min)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it },
                label = { Text(stringResource(R.string.filter_max)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * "Changed in the last N days", which is what the `min_mtime` range is for in
 * practice - the API takes epoch seconds, and nobody types those.
 */
@Composable
private fun RecentlyModifiedRow(value: RangeValue, onValue: (RangeValue) -> Unit) {
    var text by remember(value.min) { mutableStateOf(value.min.toDaysAgo()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val days = it.trim().toIntOrNull()
            onValue(
                if (days == null || days <= 0) {
                    RangeValue()
                } else {
                    RangeValue(min = nowInSeconds() - days * 86_400.0)
                }
            )
        },
        label = { Text(stringResource(R.string.filter_recent_days)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
}

/** How long the sheet waits after the last keystroke before asking again. */
private const val INPUT_DEBOUNCE_MS = 450L

private fun nowInSeconds(): Double = System.currentTimeMillis() / 1000.0

private fun Double?.toDaysAgo(): String {
    val since = this ?: return ""
    val days = ((nowInSeconds() - since) / 86_400.0).toInt()
    return if (days > 0) days.toString() else ""
}

/** How many of the stored unit one typed unit is worth. */
private fun RangeUnit.factor(): Double = when (this) {
    RangeUnit.SECONDS -> 60.0            // typed in minutes
    RangeUnit.BYTES -> 1_000_000_000.0   // typed in gigabytes
    RangeUnit.MEGABITS -> 1_000.0        // typed in megabits per second
    RangeUnit.KILOBITS,                  // typed in the kilobits it is stored in
    RangeUnit.YEAR, RangeUnit.RATING_10, RangeUnit.RATING_100, RangeUnit.RANK -> 1.0
}

private fun RangeUnit.suffix(): String = when (this) {
    RangeUnit.SECONDS -> " (min)"
    RangeUnit.BYTES -> " (GB)"
    RangeUnit.MEGABITS -> " (Mb/s)"
    RangeUnit.KILOBITS -> " (Kb/s)"
    RangeUnit.RATING_10 -> " (0–10)"
    RangeUnit.RATING_100 -> " (0–100)"
    RangeUnit.YEAR, RangeUnit.RANK -> ""
}

private fun Double?.toDisplay(unit: RangeUnit): String {
    val value = this ?: return ""
    val scaled = value / unit.factor()
    return if (scaled == kotlin.math.floor(scaled)) scaled.toLong().toString() else scaled.toString()
}

private fun String.toStored(unit: RangeUnit): Double? =
    replace(',', '.').trim().toDoubleOrNull()?.times(unit.factor())
