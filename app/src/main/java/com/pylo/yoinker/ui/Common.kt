package com.pylo.yoinker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Qualities
import com.pylo.yoinker.core.formatDuration
import com.pylo.yoinker.core.hostOf
import com.pylo.yoinker.engine.MediaInfo
import com.pylo.yoinker.ui.theme.YkGold
import com.pylo.yoinker.ui.theme.YkInkFaint
import com.pylo.yoinker.ui.theme.YkPanelHigh

/** What we know about the pasted link so far. */
sealed interface PeekState {
    data object None : PeekState
    data object Loading : PeekState
    data class Loaded(val info: MediaInfo) : PeekState
    data class Failed(val message: String) : PeekState
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = YkInkFaint,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun FormatToggle(format: Fmt, enabled: Boolean = true, onChange: (Fmt) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Fmt.entries.forEach { option ->
            FilterChip(
                selected = format == option,
                enabled = enabled,
                onClick = { onChange(option) },
                label = {
                    Text(
                        text = "${option.emoji}  ${option.label}",
                        maxLines = 1,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YkPanelHigh,
                    selectedLabelColor = YkGold,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun QualityPicker(format: Fmt, quality: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Qualities.forFormat(format).forEach { option ->
            FilterChip(
                selected = option.value == quality,
                enabled = enabled,
                onClick = { onChange(option.value) },
                label = { Text(option.label.substringBefore(" —")) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = YkPanelHigh,
                    selectedLabelColor = YkGold,
                ),
            )
        }
    }
}

@Composable
fun LinkPreview(url: String, state: PeekState, modifier: Modifier = Modifier) {
    if (state is PeekState.None) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = YkPanelHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is PeekState.Loaded -> AsyncImage(
                        model = state.info.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    )

                    is PeekState.Loading -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )

                    else -> Text("🔗", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                val title = when (state) {
                    is PeekState.Loaded -> state.info.title
                    is PeekState.Loading -> "Reading link…"
                    is PeekState.Failed -> "Couldn't read that link"
                    else -> ""
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when (state) {
                    is PeekState.Loaded -> listOfNotNull(
                        state.info.uploader,
                        formatDuration(state.info.durationSec).takeIf { it.isNotEmpty() },
                    ).joinToString(" · ").ifEmpty { hostOf(url) }

                    is PeekState.Failed -> state.message
                    else -> hostOf(url)
                }
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = YkInkFaint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
