package pitcher.android.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Ways to support pitcher, the same as the author's sponsor page. */
data class SupportLink(val name: String, val blurb: String, val url: String)

val SUPPORT_LINKS = listOf(
    SupportLink("Ko-fi", "One-time or monthly. No account needed.", "https://ko-fi.com/k1monfared"),
    SupportLink("GitHub Sponsors", "One-time or recurring, through GitHub.", "https://github.com/sponsors/k1monfared"),
    SupportLink("Patreon", "Ongoing monthly support.", "https://www.patreon.com/cw/k1monfared"),
)

/**
 * The support section of Settings: one tappable card per platform, each
 * opening its page in the browser. Only the F-Droid build shows it.
 */
@Composable
fun SupportLinksSection(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Support pitcher", style = MaterialTheme.typography.titleMedium)
        Text(
            "pitcher is free, with no ads or tracking. If it helps your music, you can " +
                "support its development. Sharing it or reporting a bug helps too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SUPPORT_LINKS.forEach { link ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(link.url) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(link.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            link.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
