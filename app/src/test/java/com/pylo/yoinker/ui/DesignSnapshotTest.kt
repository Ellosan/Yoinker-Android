package com.pylo.yoinker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.engine.MediaInfo
import com.pylo.yoinker.ui.theme.Space
import com.pylo.yoinker.ui.theme.Yk
import com.pylo.yoinker.ui.theme.YoinkerTheme
import com.pylo.yoinker.ui.theme.asContent
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real components on the JVM so the design can be looked at rather than
 * assumed. Screens are composed here from the same pieces the app uses, because the
 * panes themselves read singletons that want a device.
 */
class DesignSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun yoinkScreen() {
        paparazzi.snapshot {
            YoinkerTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Yk.screen)
                        .padding(horizontal = Space.gutter),
                ) {
                    ScreenTitle(
                        title = "Paste a link,\nget a clean file.",
                        subtitle = "No sketchy converter sites. All of it runs here.",
                    )

                    SectionLabel("Link")
                    LinkPreview(
                        url = "https://instagram.com/reel/abc",
                        state = PeekState.Loaded(
                            MediaInfo(
                                title = "Video by ilovecrackingpatrick",
                                uploader = "instagram.com",
                                durationSec = 402,
                                thumbnail = null,
                                ext = "mp4",
                            )
                        ),
                    )

                    SectionLabel("Format")
                    FormatToggle(format = Fmt.MP3) {}

                    SectionLabel("Quality")
                    QualityPicker(format = Fmt.MP3, quality = "192K") {}

                    Spacer(Modifier.height(Space.huge))
                    PrimaryButton(
                        text = "Yoink it",
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.fillMaxWidth(),
                    ) {}
                    Spacer(Modifier.height(Space.md))
                    SecondaryButton(
                        text = "Add to queue",
                        icon = Icons.Rounded.PlaylistAdd,
                        modifier = Modifier.fillMaxWidth(),
                    ) {}
                }
            }
        }
    }

    @Test
    fun queueScreen() {
        paparazzi.snapshot {
            YoinkerTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Yk.screen)
                        .padding(horizontal = Space.gutter),
                ) {
                    ScreenTitle(title = "Queue", subtitle = "2 waiting to be yoinked.")
                    Spacer(Modifier.height(Space.lg))

                    // In flight.
                    YkCard(
                        brush = Yk.raisedCard,
                        border = Yk.GoldDeep,
                        padding = Space.md,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(url = null, duration = "6:42")
                            Column(Modifier.weight(1f).padding(start = Space.lg)) {
                                Text(
                                    "Tiny Desk Concert — full session",
                                    style = MaterialTheme.typography.titleMedium.asContent(),
                                    color = Yk.Ink,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    "MP3  ·  320K  ·  youtube.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Yk.InkFaint,
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.lg))
                        YkProgress(fraction = 0.62f)
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "62%   ·   1m 20s left",
                            style = MaterialTheme.typography.labelMedium,
                            color = Yk.InkSoft,
                        )
                        Spacer(Modifier.height(Space.sm))
                        Row { TextAction("Stop", icon = Icons.Rounded.Stop) {} }
                    }

                    Spacer(Modifier.height(Space.md))

                    // Finished.
                    YkCard(padding = Space.md, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(url = null, duration = "3:18")
                            Column(Modifier.weight(1f).padding(start = Space.lg)) {
                                Text(
                                    "Video by ilovecrackingpatrick",
                                    style = MaterialTheme.typography.titleMedium.asContent(),
                                    color = Yk.Ink,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    "MP4  ·  1080  ·  instagram.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Yk.InkFaint,
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.md))
                        StatusChip("Saved  ·  24.1 MB", Yk.Green)
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            TextAction("Open", icon = Icons.Rounded.OpenInNew) {}
                            TextAction("Remove", tint = Yk.InkFaint) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun emptyQueue() {
        paparazzi.snapshot {
            YoinkerTheme {
                Box(Modifier.fillMaxSize().background(Yk.screen)) {
                    EmptyState(
                        icon = Icons.Rounded.Inbox,
                        title = "Nothing queued",
                        body = "Share a link to Yoinker, or paste one yourself.",
                    )
                }
            }
        }
    }
}
