package com.purelock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/** Guardián: (1) bloquea pantallas de Ajustes peligrosas, (2) vigila que el filtro siga activo. */
class GuardService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var last = 0L
    private var lastAlert = 0L
    private val tick = object : Runnable { override fun run() { health(); h.postDelayed(this, 15_000) } }

    override fun onServiceConnected() { h.post(tick) }
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        h.removeCallbacks(tick)
        if (Store.guardActive(this)) Store.alert(this, "El guardián de PureLock fue desactivado.")
        return super.onUnbind(intent)
    }

    private fun health() {
        if (!Store.guardActive(this) || DnsVpnService.running) return
        val canAlert = System.currentTimeMillis() - lastAlert > 300_000   // un aviso cada 5 min como máximo
        if (VpnService.prepare(this) == null) {
            if (canAlert) { lastAlert = System.currentTimeMillis(); Store.alert(this, "El filtro DNS estaba apagado. Reactivándolo.") }
            try { startService(Intent(this, DnsVpnService::class.java)) } catch (_: Exception) {}
        } else if (canAlert) {
            lastAlert = System.currentTimeMillis()
            Store.alert(this, "El filtro DNS está apagado. Abre PureLock para reactivarlo.")
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !Store.guardActive(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (!(pkg.contains("settings") || pkg.contains("packageinstaller") || pkg.contains("permissioncontroller"))) return
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder(); collect(root, sb, 0)
        if (forbidden(sb.toString().lowercase())) block()
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, d: Int) {
        if (n == null || d > 14) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, d + 1)
    }

    // Ajusta estas reglas si tu marca usa otros textos.
    private fun forbidden(t: String): Boolean =
        t.contains("purelock") ||                                            // info de app, desinstalar, accesibilidad, admin
        t.contains("adult-filter-dns") || t.contains("cleanbrowsing") ||
        ((t.contains("private dns") || t.contains("dns privado")) &&
            (t.contains("hostname") || t.contains("nombre de host"))) ||
        (t.contains("vpn") && (t.contains("always-on") || t.contains("always on") ||
            t.contains("siempre activ") || t.contains("block connections without") ||
            t.contains("bloquear conexiones sin"))) ||
        t.contains("factory reset") || t.contains("datos de fábrica") || t.contains("valores de fábrica") ||
        t.contains("usb debugging") || t.contains("depuración usb") || t.contains("depuración por usb") ||
        t.contains("oem unlock") || t.contains("desbloqueo de oem")

    private fun block() {
        val t = System.currentTimeMillis()
        if (t - last < 1500) return
        last = t
        performGlobalAction(GLOBAL_ACTION_HOME)
        Store.addAttempt(this)
        Toast.makeText(this, "PureLock: bloqueado. Si de verdad lo necesitas, pide el desbloqueo en la app.", Toast.LENGTH_LONG).show()
    }
}
