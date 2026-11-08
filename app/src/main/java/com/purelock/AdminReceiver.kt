package com.purelock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    // Se llama cuando Android registra la app como propietario del dispositivo.
    // Si la protección ya estaba activada (p. ej. tras una actualización), se reaplica.
    override fun onEnabled(context: Context, intent: Intent) {
        val app = context.applicationContext
        Thread { Enforcer.apply(app) }.start()
    }
}
