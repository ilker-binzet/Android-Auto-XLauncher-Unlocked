package com.google.android.projection.gearhead.xunlocked

import android.app.Application

class XLauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedStatus.init()
    }
}
