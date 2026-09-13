package com.pylo.yoinker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Charred wood and gold — the same palette the desktop build wears.
val YkBackground = Color(0xFF0B0A09)
val YkPanel = Color(0xFF141009)
val YkPanelHigh = Color(0xFF1E1810)
val YkInk = Color(0xFFF2E9D8)
val YkInkSoft = Color(0xFFC9BDA3)
val YkInkFaint = Color(0xFF7D735F)
val YkGold = Color(0xFFD9A441)
val YkGoldBright = Color(0xFFFFD469)
val YkEmber = Color(0xFFD0602F)
val YkLine = Color(0xFF2C2418)
val YkGreen = Color(0xFF6AD48C)

private val YoinkerDark = darkColorScheme(
    primary = YkGold,
    onPrimary = Color(0xFF1A130A),
    primaryContainer = YkPanelHigh,
    onPrimaryContainer = YkGoldBright,
    secondary = YkGoldBright,
    onSecondary = Color(0xFF1A130A),
    background = YkBackground,
    onBackground = YkInk,
    surface = YkPanel,
    onSurface = YkInk,
    surfaceVariant = YkPanelHigh,
    onSurfaceVariant = YkInkSoft,
    outline = YkLine,
    outlineVariant = YkLine,
    error = YkEmber,
    onError = Color(0xFF1A130A),
)

// Yoinker is a dark app by design; the light scheme only exists so no surface
// falls back to an undefined colour on a light-themed device.
private val YoinkerLight = lightColorScheme(
    primary = Color(0xFF8A6314),
    onPrimary = Color.White,
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF1A130A),
    surface = Color(0xFFF3ECDF),
    onSurface = Color(0xFF1A130A),
    outline = Color(0xFFCBBB9B),
    error = YkEmber,
)

private val YoinkerTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

@Composable
fun YoinkerTheme(forceDark: Boolean = true, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) YoinkerDark else YoinkerLight,
        typography = YoinkerTypography,
        content = content,
    )
}
