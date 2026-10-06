package pitcher.android.ui.modern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onOpenPitches: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            )
        }
        item {
            PitchCard(
                label = "+ save",
                sub = (if (faderCents >= 0) "+${faderCents}c" else "${faderCents}c"),
                selected = false,
                saved = false,
                accent = accent,
                onClick = onSaveCurrent,
            )
        }
    }
}

@Composable
private fun PitchCard(
    label: String,
    sub: String?,
    selected: Boolean,
    saved: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
}
