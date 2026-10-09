package pitcher.android.ui.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Variant

private fun signed(cents: Int) = if (cents >= 0) "+${cents}c" else "${cents}c"

/**
 * The pitch shelf: the original, every kept pitch, `+ save` to keep the
 * current pitch, and `render` to save it as a file. Tapping a pitch plays it;
 * tapping the pitch that is already playing opens the render sheet. Long-press
 * a pitch for render, share, rename, and delete. Every card has the same size
 * so nothing shifts while rendering.
 */
@Composable
fun PitchShelf(
    variants: List<Variant>,
    faderCents: Int,
    accent: Color,
    onSelectPitch: (Variant?) -> Unit,
    onSavePitch: () -> Unit,
    onOpenRender: () -> Unit,
    onRenderVariant: (Variant) -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onShareVariant: (Variant) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    saving: Boolean = false,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    var renaming by remember { mutableStateOf<Variant?>(null) }
    val selected = variants.firstOrNull { it.cents == faderCents }
    val canSave = faderCents != 0 && selected == null

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            PitchCard(
                label = "original",
                sub = "+0c",
                selected = faderCents == 0,
                accent = accent,
                onClick = { onSelectPitch(null) },
            )
        }
        items(variants, key = { it.id }) { v ->
            val isSelected = v.id == selected?.id
            PitchCard(
                label = v.name?.takeIf { it.isNotBlank() } ?: signed(v.cents),
                sub = if (v.name?.isNotBlank() == true) signed(v.cents) else null,
                badge = if (v.renderCount > 0) "file" else null,
                selected = isSelected,
                accent = accent,
                onClick = { if (isSelected) onOpenRender() else onSelectPitch(v) },
                menuItems = listOf(
                    "save file" to { onRenderVariant(v) },
                    "share" to { onShareVariant(v) },
                    "rename" to { renaming = v },
                    "delete" to { onDeleteVariant(v) },
                ),
            )
        }
        item {
            Box(modifier = Modifier.onboardingTarget(onboarding, "save")) {
                PitchCard(
                    label = "+ save",
                    sub = signed(faderCents),
                    selected = false,
                    enabled = canSave,
                    accent = accent,
                    onClick = onSavePitch,
                )
            }
        }
        item {
            PitchCard(
                label = "render",
                sub = signed(faderCents),
                selected = false,
                outlined = true,
                busy = saving,
                accent = accent,
                onClick = onOpenRender,
            )
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
    accent: Color,
    onClick: () -> Unit,
    badge: String? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
    busy: Boolean = false,
    menuItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var menu by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.38f

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                selected -> accent.copy(alpha = 0.22f)
                outlined -> Color.Transparent
                else -> Color.White.copy(alpha = 0.05f)
            },
            border = if (outlined) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
            modifier = Modifier
                .width(104.dp)
                .height(64.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = { if (menuItems.isNotEmpty()) menu = true },
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = accent,
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = (if (selected || outlined) accent else MaterialTheme.colorScheme.onSurface)
                            .copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            maxLines = 1,
                        )
                    }
                    if (badge != null) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            menuItems.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this pitch") },
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
