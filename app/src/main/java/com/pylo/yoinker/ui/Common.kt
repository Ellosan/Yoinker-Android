package com.pylo.yoinker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pylo.yoinker.R
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Qualities
import com.pylo.yoinker.core.formatDuration
import com.pylo.yoinker.core.hostOf
import com.pylo.yoinker.engine.MediaInfo
import com.pylo.yoinker.ui.theme.Motion
import com.pylo.yoinker.ui.theme.Space
import com.pylo.yoinker.ui.theme.Yk
import com.pylo.yoinker.ui.theme.asContent

/** What we know about the pasted link so far. */
sealed interface PeekState {
    data object None : PeekState
    data object Loading : PeekState
    data class Loaded(val info: MediaInfo) : PeekState
    data class Failed(val message: String) : PeekState
}

val Fmt.icon: ImageVector
    get() = if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie

// ---- structure --------------------------------------------------------------

/**
 * Every screen opens the same way: one large title, one line saying what the screen
 * is for, then content. Consistency here is most of what makes an app feel built
 * rather than assembled.
 */
@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = Space.sm, bottom = Space.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = Yk.Ink,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Yk.InkFaint,
        modifier = modifier.padding(top = Space.xxl, bottom = Space.md),
    )
}

/** The standard surface: lit along its top edge, hairline bordered. */
@Composable
fun YkCard(
    modifier: Modifier = Modifier,
    brush: Brush = Yk.card,
    border: Color = Yk.Hairline,
    padding: androidx.compose.ui.unit.Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

/** A rounded tile holding one icon — the brand mark, an empty state, a file type. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color = Yk.GoldBright,
    brush: Brush = Yk.raisedCard,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(brush)
            .border(BorderStroke(1.dp, Yk.Hairline), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2.1f))
    }
}

// ---- buttons ----------------------------------------------------------------

@Composable
private fun pressScale(interaction: MutableInteractionSource, enabled: Boolean): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = Motion.springy(),
        label = "press",
    )
    return scale
}

/** The one piece of polished brass on the screen. Use it once per view. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .scale(pressScale(interaction, enabled))
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Yk.gold else Brush.verticalGradient(listOf(Yk.Surface2, Yk.Surface1)))
            .border(
                BorderStroke(1.dp, if (enabled) Color.Transparent else Yk.Hairline),
                RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) Yk.OnGold else Yk.InkFaint
        icon?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = Yk.InkSoft,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .scale(pressScale(interaction, enabled))
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Yk.card)
            .border(BorderStroke(1.dp, Yk.Hairline), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) tint else Yk.InkFaint
        icon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small inline action — "Open", "Retry", "Remove". */
@Composable
fun TextAction(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Yk.Gold,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

// ---- selection --------------------------------------------------------------

/** A choice, lit gold when it's the one in force. */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        if (selected) Yk.Gold else Yk.Hairline,
        Motion.quick(),
        label = "pillBorder",
    )
    val content by animateColorAsState(
        when {
            !enabled -> Yk.InkFaint
            selected -> Yk.GoldBright
            else -> Yk.InkSoft
        },
        Motion.quick(),
        label = "pillContent",
    )

    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Yk.goldFaint else Yk.card)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.lg)
            .alpha(if (enabled) 1f else 0.55f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun FormatToggle(format: Fmt, enabled: Boolean = true, onChange: (Fmt) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
        Fmt.entries.forEach { option ->
            Pill(
                label = option.label,
                icon = option.icon,
                selected = format == option,
                enabled = enabled,
                onClick = { onChange(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun QualityPicker(format: Fmt, quality: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Qualities.forFormat(format).forEach { option ->
            Pill(
                label = option.label.substringBefore(" —"),
                selected = option.value == quality,
                enabled = enabled,
                onClick = { onChange(option.value) },
            )
        }
    }
}

// ---- feedback ---------------------------------------------------------------

/** Rounded track, brass fill, settling into place rather than jumping. */
@Composable
fun YkProgress(fraction: Float, modifier: Modifier = Modifier, indeterminate: Boolean = false) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.spring(),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(Yk.Surface3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (indeterminate) 1f else animated.coerceAtLeast(0.02f))
                .height(7.dp)
                .clip(CircleShape)
                .background(if (indeterminate) Yk.goldFaint else Yk.gold),
        )
    }
}

/** A coloured dot and a word — reads faster than a sentence. */
@Composable
fun StatusChip(text: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon = icon, size = 76.dp, tint = Yk.Gold)
        Spacer(Modifier.height(Space.xl))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Yk.Ink)
        Spacer(Modifier.height(Space.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Space.xxl),
        )
    }
}

// ---- media ------------------------------------------------------------------

/** The thumbnail, with the duration burned into the corner like a video app. */
@Composable
fun Thumb(url: String?, duration: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 112.dp, height = 66.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Yk.Surface0)
            .border(BorderStroke(1.dp, Yk.Hairline), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Keeps the badge readable over a bright frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_yoink),
                contentDescription = null,
                tint = Yk.InkFaint,
                modifier = Modifier.size(24.dp),
            )
        }

        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = Yk.Ink,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun LinkPreview(url: String, state: PeekState, modifier: Modifier = Modifier) {
    if (state is PeekState.None) return

    YkCard(modifier = modifier.fillMaxWidth(), padding = Space.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val info = (state as? PeekState.Loaded)?.info
            Thumb(
                url = info?.thumbnail,
                duration = info?.durationSec?.let { formatDuration(it) }.orEmpty(),
            )

            Spacer(Modifier.width(Space.lg))

            Column(Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        is PeekState.Loaded -> state.info.title
                        is PeekState.Loading -> "Reading link…"
                        is PeekState.Failed -> "Couldn't read that link"
                        else -> ""
                    },
                    // A title from a website can be in any script.
                    style = MaterialTheme.typography.titleMedium.asContent(),
                    color = Yk.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                val sub = when (state) {
                    is PeekState.Loaded -> state.info.uploader?.takeIf { it.isNotBlank() } ?: hostOf(url)
                    is PeekState.Failed -> state.message
                    else -> hostOf(url)
                }
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium.asContent(),
                        color = if (state is PeekState.Failed) Yk.Ember else Yk.InkFaint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
