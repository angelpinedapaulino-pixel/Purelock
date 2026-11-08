package com.purelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reaplica la protección al arrancar, tras actualizar la app y al terminar una ventana de desbloqueo. */
class EnforceReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        val pr = goAsync()
        Thread {
            try {
                if (Store.armed(app) && Enforcer.isOwner(app)) {
                    Enforcer.apply(app)
                    Enforcer.scheduleWatchers(app)
                }
            } finally { pr.finish() }
        }.start()
    }
}
