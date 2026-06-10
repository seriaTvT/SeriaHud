package org.chtholly.seriahud

import android.app.Application
import com.topjohnwu.superuser.Shell

class SeriaHudApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize libsu
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
