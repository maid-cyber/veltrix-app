package com.veltrix.vpn.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.veltrix.vpn.R
import com.veltrix.vpn.ui.MainActivity

/** Obavještenja: VPN se prekinuo, pretplata uskoro ističe. */
object Notifier {
    private const val CHANNEL = "alerts"

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!allowed(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, if (Store.bosnian()) "Upozorenja" else "Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), flags)
        nm.notify(
            id,
            NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    fun dropped(ctx: Context, reason: String?) {
        if (!Store.notifyDrop) return
        val bs = Store.bosnian()
        show(
            ctx, 2,
            if (bs) "VPN veza je prekinuta" else "VPN disconnected",
            (if (bs) "Tvoja veza više nije zaštićena. Otvori aplikaciju da se ponovo spojiš." else "Your connection is no longer protected. Open the app to reconnect.") +
                (reason?.let { "\n$it" } ?: ""),
        )
    }

    /** Jednom dnevno, kad pretplata ističe za 3 dana ili manje. */
    fun expiry(ctx: Context, acc: Account) {
        if (!Store.notifyExpiry || acc.expiresAt <= 0) return
        val left = acc.expiresAt - System.currentTimeMillis()
        if (left <= 0 || left > 3 * 86_400_000L) return
        val today = System.currentTimeMillis() / 86_400_000L
        if (Store.expiryNotifiedDay == today) return
        Store.expiryNotifiedDay = today
        val days = (left / 86_400_000L).toInt() + 1
        val bs = Store.bosnian()
        show(
            ctx, 3,
            if (bs) "Pretplata uskoro ističe" else "Subscription expiring soon",
            if (bs) "Tvoj VPN ističe za $days ${if (days == 1) "dan" else "dana"}. Javi se prodavcu da produžiš." else "Your VPN expires in $days day(s). Contact your seller to renew.",
        )
    }
}
