package pitcher.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF6AA9FF)
private val Gold = Color(0xFFFFD166)
private val Danger = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = Gold,
    error = Danger,
    background = Color(0xFF0E0E12),
    surface = Color(0xFF16161A),
    surfaceVariant = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE8E8EE),
    onSurface = Color(0xFFE8E8EE),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FD0),
    secondary = Color(0xFF9A7B1E),
)

@Composable
fun PitcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
