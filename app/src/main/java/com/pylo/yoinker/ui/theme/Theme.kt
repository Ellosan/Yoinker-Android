package com.pylo.yoinker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Charred wood and gold.
 *
 * Yoinker is a dark app on purpose — it inherits the desktop build's look, which
 * reads like old leather and brass rather than a generic dark theme. The warmth is
 * the point: every neutral here has red in it, so nothing goes blue-grey.
 */
object Yk {

    val Bg = Color(0xFF0B0A09)
    val BgGlow = Color(0xFF17120B)
    val Panel = Color(0xFF141009)
    val PanelHigh = Color(0xFF1E1810)
    val PanelTop = Color(0xFF241C12)

    val Ink = Color(0xFFF2E9D8)
    val InkSoft = Color(0xFFC9BDA3)
    val InkFaint = Color(0xFF7D735F)

    val Gold = Color(0xFFD9A441)
    val GoldBright = Color(0xFFFFD469)
    val GoldDeep = Color(0xFF7A5A1E)
    val OnGold = Color(0xFF1A130A)

    val Ember = Color(0xFFD0602F)
    val Green = Color(0xFF6AD48C)
    val Line = Color(0xFF2C2418)

    /** The warm glow behind everything, brightest just off the top of the screen. */
    val screen = Brush.radialGradient(
        colors = listOf(BgGlow, Bg),
        center = androidx.compose.ui.geometry.Offset(540f, -180f),
        radius = 1500f,
    )

    /** Cards catch a little of that light along their top edge. */
    val card = Brush.verticalGradient(listOf(PanelHigh, Panel))

    val raisedCard = Brush.verticalGradient(listOf(PanelTop, PanelHigh))

    /** The signature: the one thing on screen that looks like polished brass. */
    val gold = Brush.verticalGradient(listOf(GoldBright, Gold))

    val goldFaint = Brush.verticalGradient(listOf(Gold.copy(alpha = 0.18f), Gold.copy(alpha = 0.06f)))

    val transparent = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))

    val emberFaint = Brush.verticalGradient(listOf(Ember.copy(alpha = 0.20f), Ember.copy(alpha = 0.07f)))
}

private val YoinkerDark = darkColorScheme(
    primary = Yk.Gold,
    onPrimary = Yk.OnGold,
    primaryContainer = Yk.PanelHigh,
    onPrimaryContainer = Yk.GoldBright,
    secondary = Yk.GoldBright,
    onSecondary = Yk.OnGold,
    background = Yk.Bg,
    onBackground = Yk.Ink,
    surface = Yk.Panel,
    onSurface = Yk.Ink,
    surfaceVariant = Yk.PanelHigh,
    onSurfaceVariant = Yk.InkSoft,
    outline = Yk.Line,
    outlineVariant = Yk.Line,
    error = Yk.Ember,
    onError = Yk.OnGold,
    scrim = Color(0xCC000000),
)

/**
 * Weight and size carry the hierarchy — there's no custom typeface, because a font
 * file is a megabyte an app this size doesn't need to spend.
 */
private val YoinkerType = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
)

private val YoinkerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun YoinkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YoinkerDark,
        typography = YoinkerType,
        shapes = YoinkerShapes,
        content = content,
    )
}

// Names the rest of the app already imports.
val YkGold = Yk.Gold
val YkGoldBright = Yk.GoldBright
val YkInkFaint = Yk.InkFaint
val YkInkSoft = Yk.InkSoft
val YkEmber = Yk.Ember
val YkGreen = Yk.Green
val YkLine = Yk.Line
val YkPanelHigh = Yk.PanelHigh
val YkBackground = Yk.Bg
