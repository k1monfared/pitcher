package pitcher.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ModernDark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF06121F),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF1A1200),
    tertiary = Color(0xFF7DDF9A),
    background = Color(0xFF08080B),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1B1B20),
    onSurfaceVariant = Color(0xFF9A9AA6),
    outline = Color(0xFF33333B),
)

/** Accent hues used to tint the screen by how far the pitch has moved. */
object PitchHues {
    val Down = Color(0xFF6AA9FF)
    val Up = Color(0xFFFFB060)
    val Neutral = Color(0xFF8AB4FF)
    val Saved = Color(0xFF7DDF9A)

    fun forCents(cents: Int): Color {
        if (cents == 0) return Neutral
        val t = (kotlin.math.abs(cents) / 1200f).coerceIn(0f, 1f)
        val base = if (cents < 0) Down else Up
        return lerpColor(Neutral, base, 0.35f + 0.65f * t)
    }
}

/** Expressive motion curves (M3 Expressive's API is not public in material3 1.4.0). */
object Motion {
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

@Composable
fun ModernTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides ModernDark.onBackground,
    ) {
        MaterialTheme(
            colorScheme = ModernDark,
            content = content,
        )
    }
}
