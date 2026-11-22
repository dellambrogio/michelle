package dev.michelle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// greens: a deep green sea, mint for what you tap, a light whale-blue accent
private val Sea = Color(0xFF13593F)
private val Deep = Color(0xFF0E4733)
private val Deeper = Color(0xFF093627)
private val Whale = Color(0xFF6FDBA4)
private val Flipper = Color(0xFF2E9E6B)
private val Navy = Color(0xFF06291C)
private val Foam = Color(0xFFF4FFF8)
private val Mist = Color(0xFFBFE8D2)

private val colors = darkColorScheme(
    primary = Whale, onPrimary = Navy,
    primaryContainer = Flipper, onPrimaryContainer = Foam,
    secondary = Whale, onSecondary = Navy,
    secondaryContainer = Flipper, onSecondaryContainer = Foam,
    tertiary = Color(0xFFF2C46D), onTertiary = Navy,
    background = Sea, onBackground = Foam,
    surface = Sea, onSurface = Foam,
    surfaceVariant = Deep, onSurfaceVariant = Mist,
    surfaceContainerLowest = Deeper, surfaceContainerLow = Deep, surfaceContainer = Deep,
    surfaceContainerHigh = Deep, surfaceContainerHighest = Deep,
    inverseSurface = Foam, inverseOnSurface = Navy, inversePrimary = Flipper,
    outline = Color(0xFF7FC4A0), outlineVariant = Color(0xFF2B6E52),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

@Composable
fun MichelleTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
