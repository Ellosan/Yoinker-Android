package com.pylo.yoinker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.engine.MediaInfo
import com.pylo.yoinker.ui.theme.Yk
import com.pylo.yoinker.ui.theme.YoinkerTheme
import org.junit.Rule
import org.junit.Test

class DesignSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun designSystem() {
        paparazzi.snapshot {
            YoinkerTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Yk.screen)
                        .padding(20.dp),
                ) {
                    Text(
                        "Paste a link,\nget a clean file.",
                        style = MaterialTheme.typography.displaySmall,
                        color = Yk.Ink,
                    )
                    Text(
                        "No sketchy converter sites. Runs on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Yk.InkFaint,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    SectionLabel("Link preview")
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

                    SectionLabel("Progress")
                    YkProgress(fraction = 0.62f)
                    StatusLine("62%  ·  1m 20s left", Yk.InkSoft)

                    Spacer(Modifier.height(22.dp))
                    GoldButton("⬇   Yoink it", modifier = Modifier.fillMaxWidth()) {}
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Add to queue", modifier = Modifier.fillMaxWidth()) {}

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextAction("Open") {}
                        TextAction("Make it playable") {}
                        TextAction("Remove", tint = Yk.InkFaint) {}
                    }
                }
            }
        }
    }

    @Test
    fun emptyQueue() {
        paparazzi.snapshot {
            YoinkerTheme {
                Column(Modifier.fillMaxSize().background(Yk.screen)) {
                    EmptyState(
                        mark = "🪝",
                        title = "Nothing queued",
                        body = "Share a link to Yoinker, or paste one on the Yoink tab.",
                    )
                }
            }
        }
    }
}
