package com.purelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.armed(c) && VpnService.prepare(c) == null) {
            try { c.startService(Intent(c, DnsVpnService::class.java)) } catch (_: Exception) {}
        }
    }
}
