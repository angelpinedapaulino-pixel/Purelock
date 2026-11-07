package com.purelock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Mientras sea administrador del dispositivo, Android no deja desinstalar la app. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        if (Store.guardActive(context)) {
            Store.addAttempt(context)
            Store.alert(context, "Intento de quitar la protección de PureLock.")
        }
        return "Quitar esta protección requiere el desbloqueo de tu persona de confianza."
    }
}
