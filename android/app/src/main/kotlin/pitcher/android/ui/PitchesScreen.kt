package pitcher.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Track
import pitcher.android.data.Variant

@Composable
fun PitchesScreen(
    track: Track?,
    variants: List<Variant>,
    selectedVariantId: Long?,
    onSelectOriginal: () -> Unit,
    onSelectVariant: (Variant) -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track == null) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("No track open", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open a track from the Library tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var editingId by remember { mutableStateOf<Long?>(null) }

    val below = variants.filter { it.cents < 0 }
    val atOrAbove = variants.filter { it.cents >= 0 }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "pitches (${variants.size + 1})",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(below, key = { it.id }) {
                VariantRow(it, selectedVariantId, editingId, { id -> editingId = id }, { v, n ->
                    onRenameVariant(v, n)
                    editingId = null
                }, onSelectVariant, onDeleteVariant)
            }
            item(key = "original") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectOriginal)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("original", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (selectedVariantId == null) "playing" else "+0c",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
            items(atOrAbove, key = { it.id }) {
                VariantRow(it, selectedVariantId, editingId, { id -> editingId = id }, { v, n ->
                    onRenameVariant(v, n)
                    editingId = null
                }, onSelectVariant, onDeleteVariant)
            }
        }
    }
}

@Composable
private fun VariantRow(
    v: Variant,
    selectedVariantId: Long?,
    editingId: Long?,
    onStartEdit: (Long) -> Unit,
    onCommit: (Variant, String) -> Unit,
    onSelect: (Variant) -> Unit,
    onDelete: (Variant) -> Unit,
) {
    var text by remember(editingId, v.name) {
        mutableStateOf(if (editingId == v.id) (v.name ?: "") else "")
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(v) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    v.name ?: (if (v.cents >= 0) "+${v.cents}c" else "${v.cents}c"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (v.name != null) {
                    Text(
                        (if (v.cents >= 0) "+${v.cents}c" else "${v.cents}c"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onStartEdit(v.id) }) { Text("name") }
            TextButton(onClick = { onDelete(v) }) { Text("delete") }
            if (selectedVariantId == v.id) {
                Text(
                    "playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (editingId == v.id) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onCommit(v, text) }) { Text("Save") }
            }
        }
        HorizontalDivider()
    }
}
