package com.veltrix.vpn.data

import android.content.Context
import com.veltrix.vpn.bg.BoxService

/** Priprema veze bez ekrana (paljenje telefona, "Uvijek uključen VPN"). */
object Connector {
    fun account(): Account? = Store.accountJson.takeIf { it.isNotBlank() }?.let { runCatching { Account.parse(it) }.getOrNull() }

    fun serverFor(acc: Account): Server? =
        acc.servers.firstOrNull { it.id == Store.selectedServer } ?: acc.servers.firstOrNull { !it.isChain } ?: acc.servers.firstOrNull()

    /** Upiše konfiguraciju za zadnji izabrani server. false = nema naloga ili se ne može spojiti. */
    fun writeConfig(ctx: Context, server: Server? = null): Boolean {
        val acc = account() ?: return false
        if (!acc.canConnect) return false
        val sv = server ?: serverFor(acc) ?: return false
        BoxService.configFile(ctx).writeText(ConfigBuilder.build(sv, Store.protocol, SecurityOptions.from(acc)))
        Store.selectedNameCache = if (sv.isChain) sv.name else "${sv.flag} ${sv.city}"
        return true
    }
}
