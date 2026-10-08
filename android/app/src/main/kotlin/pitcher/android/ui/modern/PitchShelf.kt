package pitcher.android.ui.modern

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Variant
import pitcher.android.media.RenderedStore

@Composable
fun PitchShelf(
    variants: List<Variant>,
    selectedVariantId: Long?,
    faderCents: Int,
    accent: Color,
    onSelectOriginal: () -> Unit,
    onSelectVariant: (Variant) -> Unit,
    onSaveCurrent: () -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onExportVariant: (Variant) -> Unit,
    onShareVariant: (Variant) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    saving: Boolean = false,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    var renaming by remember { mutableStateOf<Variant?>(null) }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            PitchCard(
                label = "original",
                sub = "+0c",
                selected = selectedVariantId == null,
                saved = true,
                accent = accent,
                onClick = onSelectOriginal,
                onRename = null,
                onExport = null,
                onShare = null,
                onDelete = null,
            )
        }
        items(variants, key = { it.id }) { v ->
            PitchCard(
                label = v.name?.takeIf { it.isNotBlank() }
                    ?: (if (v.cents >= 0) "+${v.cents}c" else "${v.cents}c"),
                sub = if (v.name?.isNotBlank() == true) {
                    (if (v.cents >= 0) "+${v.cents}c" else "${v.cents}c")
                } else {
                    null
                },
                selected = v.id == selectedVariantId,
                saved = RenderedStore.exists(v.outputPath),
                accent = accent,
                onClick = { onSelectVariant(v) },
                onRename = { renaming = v },
                onExport = { onExportVariant(v) },
                onShare = { onShareVariant(v) },
                onDelete = { onDeleteVariant(v) },
            )
        }
        item {
            Box(modifier = Modifier.onboardingTarget(onboarding, "save")) {
                PitchCard(
                    label = "+ save",
                    sub = (if (faderCents >= 0) "+${faderCents}c" else "${faderCents}c"),
                    selected = false,
                    saved = false,
                    saving = saving,
                    accent = accent,
                    onClick = { if (!saving) onSaveCurrent() },
                    onRename = null,
                    onExport = null,
                    onShare = null,
                    onDelete = null,
                )
            }
        }
    }

    renaming?.let { v ->
        RenameDialog(
            initial = v.name ?: "",
            onConfirm = {
                onRenameVariant(v, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PitchCard(
    label: String,
    sub: String?,
    selected: Boolean,
    saved: Boolean,
    saving: Boolean = false,
    accent: Color,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val hasMenu = onRename != null || onExport != null || onShare != null || onDelete != null

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
            modifier = Modifier
                .width(104.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (hasMenu) menu = true },
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (saving) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = accent,
                        )
                        Text(
                            "saving",
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                        )
                    }
                } else {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (saved) {
                    Text(
                        "saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            onRename?.let { DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; it() }) }
            onExport?.let { DropdownMenuItem(text = { Text("Export to library") }, onClick = { menu = false; it() }) }
            onShare?.let { DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; it() }) }
            onDelete?.let { DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; it() }) }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename pitch") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
