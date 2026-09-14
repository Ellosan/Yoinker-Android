package com.pylo.yoinker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.convert.ConvertState
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.R
import com.pylo.yoinker.ui.theme.Motion
import com.pylo.yoinker.ui.theme.Space
import com.pylo.yoinker.ui.theme.Yk
import com.pylo.yoinker.ui.theme.YoinkerTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Refusing either only costs a notification or a tidier save folder. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        askForPermissions()

        val startTab = if (takeSharedMedia(intent)) Tab.CONVERT else tabFromIntent(intent)

        setContent {
            YoinkerTheme {
                YoinkerRoot(startTab = startTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSharedMedia(intent)
    }

    private fun tabFromIntent(intent: Intent?): Tab = when (intent?.getStringExtra("tab")) {
        "queue" -> Tab.QUEUE
        "convert" -> Tab.CONVERT
        "modes" -> Tab.MODES
        "routines" -> Tab.ROUTINES
        else -> Tab.YOINK
    }

    /**
     * A media file shared to Yoinker is something to convert — a link is what the
     * share sheet's "Yoink" entry handles, and that goes to ShareActivity instead.
     */
    private fun takeSharedMedia(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND) return false
        val type = intent.type.orEmpty()
        if (!type.startsWith("video/") && !type.startsWith("audio/")) return false

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: return false

        ConvertState.setSource(uri, "")
        return true
    }

    /**
     * Two optional permissions: the progress notification on Android 13+, and — only
     * before scoped storage — writing into the phone's Music and Movies folders.
     */
    private fun askForPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    YOINK("Yoink", Icons.Rounded.Download),
    QUEUE("Queue", Icons.Rounded.ViewList),
    CONVERT("Convert", Icons.Rounded.AutoFixHigh),
    MODES("Modes", Icons.Rounded.Tune),
    ROUTINES("Routines", Icons.Rounded.Bolt),
}

@Composable
fun YoinkerRoot(startTab: Tab) {
    var tab by remember { mutableStateOf(startTab) }
    val jobs by Queue.jobs.collectAsState()
    val activeModeId by Automation.activeModeId.collectAsState()
    val mode = Automation.modeById(activeModeId)
    val pending = jobs.count { !it.isFinished }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Yk.screen),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                modeLabel = "${mode.emoji}  ${mode.name}",
                onModeClick = { tab = Tab.MODES },
            )

            Box(Modifier.weight(1f)) {
                Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
                    when (current) {
                        Tab.YOINK -> YoinkPane()
                        Tab.QUEUE -> QueuePane()
                        Tab.CONVERT -> ConvertPane()
                        Tab.MODES -> ModesPane()
                        Tab.ROUTINES -> RoutinesPane()
                    }
                }
            }

            NavBar(current = tab, pending = pending, onSelect = { tab = it })
        }
    }
}

@Composable
private fun Header(modeLabel: String, onModeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.md, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The app's own mark, not an emoji: emoji render in their own colours and
        // would be the one thing on screen ignoring the palette.
        IconTile(
            icon = ImageVector.vectorResource(R.drawable.ic_yoink),
            size = 40.dp,
        )

        Spacer(Modifier.width(Space.md))

        Text(
            text = "Yoinker",
            style = MaterialTheme.typography.titleLarge,
            color = Yk.Ink,
            modifier = Modifier.weight(1f),
        )

        // The mode in force, and the way into changing it.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(Yk.goldFaint)
                .border(BorderStroke(1.dp, Yk.GoldDeep), RoundedCornerShape(13.dp))
                .clickable(onClick = onModeClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Yk.GoldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavBar(current: Tab, pending: Int, onSelect: (Tab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Yk.Surface1),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Yk.Hairline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { entry ->
                NavItem(
                    tab = entry,
                    selected = entry == current,
                    badge = if (entry == Tab.QUEUE && pending > 0) pending else 0,
                    onClick = { onSelect(entry) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    badge: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val indicator by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = Motion.spring(),
                label = "navIndicator",
            )
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 30.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .alpha(indicator)
                    .background(Yk.goldFaint),
            )
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Yk.GoldBright else Yk.InkFaint,
                modifier = Modifier.size(21.dp),
            )
            if (badge > 0) {
                Text(
                    text = if (badge > 9) "9+" else "$badge",
                    style = MaterialTheme.typography.labelSmall,
                    color = Yk.OnGold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Yk.Gold)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Yk.GoldBright else Yk.InkFaint,
            maxLines = 1,
        )
    }
}
