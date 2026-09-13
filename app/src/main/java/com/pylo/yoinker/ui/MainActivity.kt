package com.pylo.yoinker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.ui.theme.YkGold
import com.pylo.yoinker.ui.theme.YkInkFaint
import com.pylo.yoinker.ui.theme.YoinkerTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Refusing either only costs a notification or a tidier save folder. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        askForPermissions()

        setContent {
            YoinkerTheme {
                YoinkerRoot(startTab = tabFromIntent(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun tabFromIntent(intent: Intent?): Tab = when (intent?.getStringExtra("tab")) {
        "queue" -> Tab.QUEUE
        "modes" -> Tab.MODES
        "routines" -> Tab.ROUTINES
        else -> Tab.YOINK
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
    YOINK("Yoink", Icons.Outlined.Download),
    QUEUE("Queue", Icons.Outlined.ViewList),
    MODES("Modes", Icons.Outlined.Tune),
    ROUTINES("Routines", Icons.Outlined.Bolt),
}

@Composable
fun YoinkerRoot(startTab: Tab) {
    var tab by remember { mutableStateOf(startTab) }
    val jobs by Queue.jobs.collectAsState()
    val activeModeId by Automation.activeModeId.collectAsState()
    val mode = Automation.modeById(activeModeId)
    val pending = jobs.count { !it.isFinished }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = YkGold,
                            selectedTextColor = YkGold,
                            unselectedIconColor = YkInkFaint,
                            unselectedTextColor = YkInkFaint,
                        ),
                        icon = {
                            if (entry == Tab.QUEUE && pending > 0) {
                                BadgedBox(badge = { Badge { Text("$pending") } }) {
                                    Icon(entry.icon, contentDescription = entry.label)
                                }
                            } else {
                                Icon(entry.icon, contentDescription = entry.label)
                            }
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(modeLine = "${mode.emoji} ${mode.name} · ${mode.summary}")

            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.YOINK -> YoinkPane()
                    Tab.QUEUE -> QueuePane()
                    Tab.MODES -> ModesPane()
                    Tab.ROUTINES -> RoutinesPane()
                }
            }
        }
    }
}

@Composable
private fun Header(modeLine: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🪝", style = MaterialTheme.typography.titleLarge)
            Text("Yoinker", style = MaterialTheme.typography.titleLarge, color = YkGold)
        }
        Text(
            text = modeLine,
            style = MaterialTheme.typography.bodyMedium,
            color = YkInkFaint,
        )
    }
}
