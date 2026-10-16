package com.veltrix.vpn.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.veltrix.vpn.data.Connector
import com.veltrix.vpn.data.Store

/** Automatsko spajanje kad se telefon upali (ako je kupac uključio i admin dozvolio). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Store.bootConnect || Store.link.isBlank()) return
        val acc = Connector.account() ?: return
        if (!acc.features.boot || VpnService.prepare(context) != null) return
        if (Connector.writeConfig(context)) BoxService.start(context)
    }
}
