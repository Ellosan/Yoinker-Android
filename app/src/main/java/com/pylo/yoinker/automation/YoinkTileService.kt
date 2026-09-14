package com.pylo.yoinker.automation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.share.ShareActivity

/** Quick Settings tile: yoink whatever link is on the clipboard, in the current mode. */
class YoinkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Prefs.init(this)
        qsTile?.apply {
            val mode = Automation.activeMode()
            label = "Yoink"
            // The tile's second line only exists from Android 10.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "${mode.emoji} ${mode.name}"
            }
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ShareActivity::class.java)
            .setAction(ShareActivity.ACTION_FROM_CLIPBOARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            // The PendingIntent overload doesn't exist before Android 14; this is
            // the only call that works there, deprecated or not.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
