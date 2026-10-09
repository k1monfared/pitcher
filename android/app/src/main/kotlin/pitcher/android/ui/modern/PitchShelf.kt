package pitcher.android.ui.modern

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Variant
import pitcher.core.ShelfModel

private fun signed(cents: Int) = if (cents >= 0) "+${cents}c" else "${cents}c"

/**
 * The pitch shelf: one pill per pitch, sorted by cents. The original and every
 * kept pitch always show. While the pitch is somewhere nothing is kept, one
 * dashed pill stands for it; tap it to keep that pitch, which also renders it
 * in the background. Tap a kept pitch to play it, and tap the playing one to
 * save or share it as a file. Long-press a kept pitch for more.
 */
@Composable
fun PitchShelf(
    variants: List<Variant>,
    faderCents: Int,
    accent: Color,
    busyIds: Set<Long>,
    onSelectPitch: (Variant?) -> Unit,
    onKeepPitch: () -> Unit,
    onOpenFile: () -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onShareVariant: (Variant) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    onCancelVariant: (Variant) -> Unit,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    var renaming by remember { mutableStateOf<Variant?>(null) }
    val byId = variants.associateBy { it.id }
    val pills = ShelfModel.pills(variants.map { ShelfModel.Kept(it.id, it.cents) }, faderCents)
    val listState = rememberLazyListState()
    val selectedIndex = pills.indexOfFirst { it.selected }

    // Keep the highlighted pill in view as the pitch moves along the shelf.
    LaunchedEffect(selectedIndex, pills.size) {
        if (selectedIndex >= 0) listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = modifier.onboardingTarget(onboarding, "save"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(pills, key = { "${it.kind}-${it.id ?: it.cents}" }) { pill ->
            when (pill.kind) {
                ShelfModel.Kind.ORIGINAL -> PitchCard(
                    label = "original",
                    sub = "+0c",
                    selected = pill.selected,
                    accent = accent,
                    onClick = { onSelectPitch(null) },
                )
                ShelfModel.Kind.NEW -> PitchCard(
                    label = signed(pill.cents),
                    sub = "tap to keep",
                    selected = pill.selected,
                    dashed = true,
                    accent = accent,
                    onClick = onKeepPitch,
                )
                ShelfModel.Kind.KEPT -> {
                    val v = byId.getValue(pill.id!!)
                    val busy = v.id in busyIds
                    val named = v.name?.isNotBlank() == true
                    PitchCard(
                        label = if (named) v.name!! else signed(v.cents),
                        sub = if (named) signed(v.cents) else null,
                        badge = if (v.renderCount > 0) "file" else null,
                        selected = pill.selected,
                        busy = busy,
                        accent = accent,
                        onClick = { if (pill.selected) onOpenFile() else onSelectPitch(v) },
                        menuItems = buildList {
                            add("save file" to { onSelectPitch(v); onOpenFile() })
                            add("share" to { onShareVariant(v) })
                            add("rename" to { renaming = v })
                            if (busy) add("cancel" to { onCancelVariant(v) })
                            add("remove" to { onDeleteVariant(v) })
                        },
                    )
                }
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
    accent: Color,
    onClick: () -> Unit,
    badge: String? = null,
    dashed: Boolean = false,
    busy: Boolean = false,
    menuItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var menu by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                selected && !dashed -> accent.copy(alpha = 0.22f)
                dashed -> Color.Transparent
                else -> Color.White.copy(alpha = 0.05f)
            },
            modifier = Modifier
                .drawBehind {
                    if (dashed) {
                        val stroke = 1.5.dp.toPx()
                        drawRoundRect(
                            color = accent.copy(alpha = 0.8f),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            ),
                        )
                    }
                }
                .width(104.dp)
                .height(68.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (menuItems.isNotEmpty()) menu = true },
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
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
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sub != null || badge != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (sub != null) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (badge != null) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                            )
                        }
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
