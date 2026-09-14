package com.pylo.yoinker

import android.app.Application
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.Hooks
import com.pylo.yoinker.core.Notifications
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.engine.YoinkEngine
import kotlin.concurrent.thread

class YoinkerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Prefs.init(this)
        Automation.load()
        Queue.load()
        Notifications.createChannels(this)
        Hooks.refreshShareTargets(this)
        Automation.applySchedules(this)

        // Unpacking python and ffmpeg takes a moment on first launch, and the engine
        // self-updates at most once a day. Neither belongs on the main thread.
        thread(name = "yoink-engine-init", isDaemon = true) {
            YoinkEngine.ensureInit(this)
            YoinkEngine.maybeAutoUpdate(this)
        }
    }
}
