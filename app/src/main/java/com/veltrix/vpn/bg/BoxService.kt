// Pojednostavljeno prema sing-box-for-android (GPLv3): pokreće sing-box sa konfiguracijom koju napravi aplikacija.
package com.veltrix.vpn.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.veltrix.vpn.App
import com.veltrix.vpn.R
import com.veltrix.vpn.data.Store
import com.veltrix.vpn.ui.MainActivity
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Status { Stopped, Starting, Started, Stopping }

/** Stanje VPN-a za ekran (isti proces, pa nije potreban AIDL). */
object VpnState {
    val status = MutableStateFlow(Status.Stopped)
    val error = MutableStateFlow<String?>(null)
    @Volatile var connectedAt = 0L
    val alwaysOn = MutableStateFlow(false)
    val lockdown = MutableStateFlow(false)
    /** kupac je sam prekinuo vezu (tad nema obavještenja o prekidu) */
    @Volatile var userStopped = false
}

class BoxService(private val service: Service, private val platformInterface: PlatformInterface) : CommandServerHandler {
    companion object {
        const val ACTION_STOP = "com.veltrix.vpn.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1

        fun configFile(context: Context) = File(context.filesDir, "config.json")

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VPNService::class.java))
        }

        fun stop(context: Context) {
            VpnState.userStopped = true
            context.sendBroadcast(Intent(ACTION_STOP).setPackage(context.packageName))
        }
    }

    var fileDescriptor: ParcelFileDescriptor? = null
    private var commandServer: CommandServer? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP) stopService()
        }
    }

    private fun notification(text: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            App.notification.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java), flags)
        val stop = PendingIntent.getBroadcast(service, 1, Intent(ACTION_STOP).setPackage(service.packageName), flags)
        return NotificationCompat.Builder(service, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Store.brand(service))
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(0, if (Store.bosnian()) "Prekini" else "Disconnect", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showForeground(text: String) {
        val n = notification(text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(service, NOTIFICATION_ID, n, type)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun onStartCommand(): Int {
        if (VpnState.status.value != Status.Stopped) return Service.START_NOT_STICKY
        // "Uvijek uključen VPN" ili paljenje telefona pokrene servis bez aplikacije: napravi konfiguraciju ako je nema
        if (!configFile(service).exists() && !com.veltrix.vpn.data.Connector.writeConfig(service)) {
            service.stopSelf()
            return Service.START_NOT_STICKY
        }
        VpnState.userStopped = false
        VpnState.status.value = Status.Starting
        VpnState.error.value = null
        showForeground(if (Store.bosnian()) "Spajanje…" else "Connecting…")
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(service, receiver, IntentFilter(ACTION_STOP), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val content = configFile(service).readText()
                val server = CommandServer(this@BoxService, platformInterface)
                server.start()
                commandServer = server
                DefaultNetworkMonitor.start()
                server.startOrReloadService(content, OverrideOptions().apply { autoRedirect = false })
                VpnState.connectedAt = System.currentTimeMillis()
                VpnState.status.value = Status.Started
                withContext(Dispatchers.Main) {
                    showForeground((if (Store.bosnian()) "Spojeno · " else "Connected · ") + Store.selectedName(service))
                }
            } catch (e: Throwable) {
                Log.e("Veltrix", "start", e)
                fail(e.message ?: e.toString())
            }
        }
        return Service.START_NOT_STICKY
    }

    private suspend fun fail(message: String) {
        VpnState.error.value = message
        com.veltrix.vpn.data.Notifier.dropped(service, message)
        cleanup()
        withContext(Dispatchers.Main) { finish() }
    }

    private suspend fun cleanup() {
        fileDescriptor?.let { runCatching { it.close() } }
        fileDescriptor = null
        runCatching { DefaultNetworkMonitor.stop() }
        commandServer?.let { s ->
            runCatching { s.closeService() }
            runCatching { s.close() }
        }
        commandServer = null
    }

    private fun finish() {
        if (receiverRegistered) {
            runCatching { service.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        VpnState.status.value = Status.Stopped
        VpnState.connectedAt = 0
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        service.stopSelf()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun stopService() {
        val st = VpnState.status.value
        if (st == Status.Stopped || st == Status.Stopping) return
        VpnState.status.value = Status.Stopping
        GlobalScope.launch(Dispatchers.IO) {
            cleanup()
            withContext(Dispatchers.Main) { finish() }
        }
    }

    fun onRevoke() {
        if (!VpnState.userStopped) com.veltrix.vpn.data.Notifier.dropped(service, null)
        stopService()
    }
    fun onDestroy() {}

    // ---- CommandServerHandler
    override fun serviceStop() {
        if (!VpnState.userStopped) com.veltrix.vpn.data.Notifier.dropped(service, null)
        stopService()
    }

    override fun serviceReload() {}

    override fun getSystemProxyStatus(): SystemProxyStatus? = SystemProxyStatus()

    override fun setSystemProxyEnabled(isEnabled: Boolean) {}

    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message ?: "")
    }
}
