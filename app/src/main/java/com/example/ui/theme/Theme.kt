package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EditorialDarkColorScheme = darkColorScheme(
    primary = BroadcastAmber,
    onPrimary = AmberDarkInk,
    primaryContainer = AmberContainer,
    onPrimaryContainer = WarmAmberGlow,
    secondary = NewsroomCopper,
    onSecondary = AmberDarkInk,
    secondaryContainer = CopperContainer,
    onSecondaryContainer = WarmIvory,
    tertiary = VerifiedEmerald,
    onTertiary = ObsidianBg,
    tertiaryContainer = VerifiedEmeraldBg,
    onTertiaryContainer = VerifiedEmerald,
    background = ObsidianBg,
    onBackground = WarmIvory,
    surface = StudioSlate,
    onSurface = WarmIvory,
    surfaceVariant = ArchivalCharcoal,
    onSurfaceVariant = WarmAsh,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = ArchivalCharcoal,
    surfaceContainerHigh = DeepGraphite,
    outline = HairlineOutline,
    outlineVariant = HairlineOutline,
    error = DisputedCrimson,
    onError = ObsidianBg,
    errorContainer = DisputedCrimsonBg,
    onErrorContainer = DisputedCrimson
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ShubhamAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EditorialDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
