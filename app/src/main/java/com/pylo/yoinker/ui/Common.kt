package com.pylo.yoinker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pylo.yoinker.ui.theme.Yk

/** What we know about the pasted link so far. */
sealed interface PeekState {
    data object None : PeekState
    data object Loading : PeekState
    data class Loaded(val info: MediaInfo) : PeekState
    data class Failed(val message: String) : PeekState
}

// ---- surfaces ---------------------------------------------------------------

/**
 * The standard surface: a panel lit slightly along its top edge, hairline bordered.
 * Nothing in this app is a flat rectangle of one colour.
 */
@Composable
fun YkCard(
    modifier: Modifier = Modifier,
    brush: Brush = Yk.card,
    border: Color = Yk.Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Yk.InkFaint,
        modifier = modifier.padding(top = 22.dp, bottom = 9.dp),
    )
}

// ---- buttons ----------------------------------------------------------------

@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    return scale
}

/** The one piece of polished brass on the screen. Use it once per view. */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .scale(if (enabled) pressScale(interaction) else 1f)
            .height(56.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (enabled) Yk.gold else Brush.verticalGradient(listOf(Yk.PanelHigh, Yk.Panel)))
            .border(
                BorderStroke(1.dp, if (enabled) Color.Transparent else Yk.Line),
                RoundedCornerShape(17.dp),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Yk.OnGold else Yk.InkFaint,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Yk.InkSoft,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .scale(if (enabled) pressScale(interaction) else 1f)
            .height(56.dp)
            .clip(RoundedCornerShape(17.dp))
            .border(BorderStroke(1.dp, Yk.Line), RoundedCornerShape(17.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else Yk.InkFaint,
        )
    }
}

/** Small inline action — "Open", "Retry", "Remove". */
@Composable
fun TextAction(text: String, tint: Color = Yk.Gold, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// ---- selection --------------------------------------------------------------

/** A choice, lit gold when it's the one in force. */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        if (selected) Yk.Gold else Yk.Line,
        tween(160),
        label = "pillBorder",
    )
    val content by animateColorAsState(
        when {
            !enabled -> Yk.InkFaint
            selected -> Yk.GoldBright
            else -> Yk.InkSoft
        },
        tween(160),
        label = "pillText",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Yk.goldFaint else Brush.verticalGradient(listOf(Yk.PanelHigh, Yk.Panel)))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun FormatToggle(format: Fmt, enabled: Boolean = true, onChange: (Fmt) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Fmt.entries.forEach { option ->
            Pill(
                label = "${option.emoji}   ${option.label}",
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
        horizontalArrangement = Arrangement.spacedBy(9.dp),
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

/** Rounded track, brass fill, and it moves rather than jumping. */
@Composable
fun YkProgress(fraction: Float, modifier: Modifier = Modifier, indeterminate: Boolean = false) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(320), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Yk.PanelHigh)
            .border(BorderStroke(1.dp, Yk.Line), RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (indeterminate) 1f else animated.coerceAtLeast(0.015f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (indeterminate) Yk.goldFaint else Yk.gold),
        )
    }
}

@Composable
fun StatusLine(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = tint,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun EmptyState(mark: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Yk.card)
                .border(BorderStroke(1.dp, Yk.Line), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Yk.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
    }
}

// ---- link preview -----------------------------------------------------------

/** The thumbnail, with the duration burned into the corner like a video app. */
@Composable
fun Thumb(url: String?, duration: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 116.dp, height = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Yk.Bg)
            .border(BorderStroke(1.dp, Yk.Line), RoundedCornerShape(12.dp)),
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
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
        } else {
            // An emoji here renders in its own colours; the app's own mark stays on palette.
            Icon(
                painter = painterResource(R.drawable.ic_yoink),
                contentDescription = null,
                tint = Yk.InkFaint,
                modifier = Modifier.size(26.dp),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
fun LinkPreview(url: String, state: PeekState, modifier: Modifier = Modifier) {
    if (state is PeekState.None) return

    YkCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val info = (state as? PeekState.Loaded)?.info
            Thumb(
                url = info?.thumbnail,
                duration = info?.durationSec?.let { formatDuration(it) }.orEmpty(),
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        is PeekState.Loaded -> state.info.title
                        is PeekState.Loading -> "Reading link…"
                        is PeekState.Failed -> "Couldn't read that link"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Yk.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val sub = when (state) {
                    is PeekState.Loaded -> state.info.uploader?.takeIf { it.isNotBlank() }
                        ?: hostOf(url)

                    is PeekState.Failed -> state.message
                    else -> hostOf(url)
                }
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state is PeekState.Failed) Yk.Ember else Yk.InkFaint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
