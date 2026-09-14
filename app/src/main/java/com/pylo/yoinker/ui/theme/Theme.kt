package com.pylo.yoinker.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pylo.yoinker.R

/**
 * Charred wood and gold.
 *
 * The palette is inherited from the desktop build and every neutral has red in it,
 * so nothing drifts blue-grey. Surfaces separate by tone rather than by shadow —
 * a stack of drop shadows on a near-black background just makes mud.
 */
object Yk {

    // Surfaces, darkest first. Each step up is "closer to the light".
    val Surface0 = Color(0xFF0B0A09)
    val Surface1 = Color(0xFF14110C)
    val Surface2 = Color(0xFF1C1711)
    val Surface3 = Color(0xFF251E15)

    val Hairline = Color(0xFF2A2318)
    val Edge = Color(0xFF3A3021)

    val Ink = Color(0xFFF6EFE2)
    val InkSoft = Color(0xFFC9BDA3)
    val InkFaint = Color(0xFF8A7F69)

    val Gold = Color(0xFFD9A441)
    val GoldBright = Color(0xFFFFD778)
    val GoldDeep = Color(0xFF7A5A1E)
    val OnGold = Color(0xFF1A1209)

    val Ember = Color(0xFFE0703C)
    val Green = Color(0xFF7BDD9B)

    // Legacy aliases, kept so older call sites keep reading naturally.
    val Bg = Surface0
    val Panel = Surface1
    val PanelHigh = Surface2
    val PanelTop = Surface3
    val Line = Hairline

    /** The warm glow behind everything, brightest just off the top of the screen. */
    val screen = Brush.radialGradient(
        colors = listOf(Color(0xFF1A140C), Surface0),
        center = Offset(560f, -240f),
        radius = 1700f,
    )

    val card = Brush.verticalGradient(listOf(Surface2, Surface1))
    val raisedCard = Brush.verticalGradient(listOf(Surface3, Surface2))

    /** The signature: the one thing on screen that looks like polished brass. */
    val gold = Brush.verticalGradient(listOf(GoldBright, Gold))

    val goldFaint = Brush.verticalGradient(
        listOf(Gold.copy(alpha = 0.16f), Gold.copy(alpha = 0.05f)),
    )
    val emberFaint = Brush.verticalGradient(
        listOf(Ember.copy(alpha = 0.18f), Ember.copy(alpha = 0.06f)),
    )
    val greenFaint = Brush.verticalGradient(
        listOf(Green.copy(alpha = 0.14f), Green.copy(alpha = 0.05f)),
    )
    val transparent = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
}

/** One spacing scale, so nothing is spaced "about right". */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val huge = 40.dp

    /** Every screen keeps the same side gutter. */
    val gutter = 20.dp
}

/**
 * Three families, on purpose.
 *
 * [display] and [ui] are bundled, so the app's own chrome looks the same on every
 * device. [content] is deliberately the system font: titles come from websites in
 * every script there is, and a Japanese title set in a Latin-only face is a column
 * of empty boxes.
 */
object YkType {
    val display = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

    val ui = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

    val content = FontFamily.Default
}

/** Marks a style as carrying text from the internet rather than from the app. */
fun TextStyle.asContent(): TextStyle = copy(fontFamily = YkType.content)

private val YoinkerType = Typography(
    displayLarge = TextStyle(
        fontFamily = YkType.display,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = YkType.display,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = YkType.display,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = YkType.ui,
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(fontFamily = YkType.ui, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(
        fontFamily = YkType.ui,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = YkType.ui,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = YkType.ui,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = YkType.ui,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.3.sp,
    ),
)

/** Springs, not linear slides — motion that settles rather than stops dead. */
object Motion {
    fun <T> spring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> springy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> quick() = tween<T>(durationMillis = 160)
}

private val YoinkerDark = darkColorScheme(
    primary = Yk.Gold,
    onPrimary = Yk.OnGold,
    primaryContainer = Yk.Surface3,
    onPrimaryContainer = Yk.GoldBright,
    secondary = Yk.GoldBright,
    onSecondary = Yk.OnGold,
    background = Yk.Surface0,
    onBackground = Yk.Ink,
    surface = Yk.Surface1,
    onSurface = Yk.Ink,
    surfaceVariant = Yk.Surface2,
    onSurfaceVariant = Yk.InkSoft,
    outline = Yk.Hairline,
    outlineVariant = Yk.Hairline,
    error = Yk.Ember,
    onError = Yk.OnGold,
    scrim = Color(0xD9000000),
)

private val YoinkerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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

// Names older call sites import directly.
val YkGold = Yk.Gold
val YkGoldBright = Yk.GoldBright
val YkInkFaint = Yk.InkFaint
val YkInkSoft = Yk.InkSoft
val YkEmber = Yk.Ember
val YkGreen = Yk.Green
val YkLine = Yk.Hairline
val YkPanelHigh = Yk.Surface2
val YkBackground = Yk.Surface0
