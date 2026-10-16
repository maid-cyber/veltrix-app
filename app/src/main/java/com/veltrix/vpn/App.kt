package com.veltrix.vpn

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val base = filesDir.apply { mkdirs() }
            val work = (getExternalFilesDir(null) ?: File(filesDir, "work")).apply { mkdirs() }
            val temp = cacheDir.apply { mkdirs() }
            Libbox.setup(
                SetupOptions().also {
                    it.basePath = base.path
                    it.workingPath = work.path
                    it.tempPath = temp.path
                    // https://github.com/golang/go/issues/68760 (isto kao službeni klijent)
                    it.fixAndroidStack = (Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1) ||
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    it.logMaxLines = 300
                    it.debug = false
                },
            )
            Libbox.redirectStderr(File(work, "stderr.log").path)
        } catch (e: Exception) {
            Log.e("Veltrix", "libbox setup", e)
        }
    }

    companion object {
        lateinit var application: App
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
        val powerManager by lazy { application.getSystemService<PowerManager>()!! }
        val wifiManager by lazy { application.getSystemService<WifiManager>()!! }
    }
}
