package com.purelock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings

/**
 * Toda la protección usa únicamente la API oficial de administración de Android (DevicePolicyManager).
 * REQUIERE que PureLock sea "propietario del dispositivo" (Device Owner); sin eso Android no permite nada de esto.
 */
object Enforcer {
    const val HOST = "adult-filter-dns.cleanbrowsing.org"
    const val ACTION = "com.purelock.ENFORCE"
    private const val CHROME = "com.android.chrome"

    /** Restricciones de usuario que impone Android mientras la protección está activa. */
    val RESTRICTIONS = arrayOf(
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,          // no se puede cambiar el DNS privado en Ajustes
        UserManager.DISALLOW_NETWORK_RESET,               // "Restablecer Wi-Fi, móvil y Bluetooth" podría borrarlo
        UserManager.DISALLOW_CONFIG_VPN,                  // no configurar VPN
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,     // no instalar APK externos (otro intento de saltarlo)
        UserManager.DISALLOW_ADD_USER,                    // no crear usuarios/espacios nuevos
        UserManager.DISALLOW_SAFE_BOOT,                   // no arrancar en modo seguro
        UserManager.DISALLOW_FACTORY_RESET,               // no borrar de fábrica desde Ajustes
        UserManager.DISALLOW_DEBUGGING_FEATURES           // no depuración USB / ADB
    )

    /** Navegadores alternativos que se suspenden (Chrome queda como único navegador, con incógnito desactivado). */
    val BROWSERS = arrayOf(
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
        "com.brave.browser", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.microsoft.emmx", "com.duckduckgo.mobile.android", "org.torproject.torbrowser",
        "com.vivaldi.browser", "com.kiwibrowser.browser", "com.sec.android.app.sbrowser",
        "com.UCMobile.intl", "com.uc.browser.en", "mark.via.gp", "com.yandex.browser", "com.ecosia.android",
        "com.android.browser", "com.mi.globalbrowser"            // navegadores de Xiaomi
    )

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)
    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    private fun chromePolicy() = Bundle().apply {
        putInt("IncognitoModeAvailability", 1)      // 1 = incógnito desactivado
        putString("DnsOverHttpsMode", "off")        // Chrome debe usar el DNS del sistema (el filtrado)
        putBoolean("ForceGoogleSafeSearch", true)
    }

    /** Aplica (o reaplica) la protección. No hace nada si no es propietario, no está armada o hay ventana abierta. */
    @Synchronized
    fun apply(c: Context): Boolean {
        if (!isOwner(c) || !Store.guardActive(c)) return false
        val d = dpm(c); val a = admin(c)
        try {
            val dnsOk = d.getGlobalPrivateDnsMode(a) == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME &&
                d.getGlobalPrivateDnsHost(a) == HOST
            var err = ""
            if (!dnsOk) {
                if (Store.dnsSetOnce(c)) Store.addCorrection(c)         // alguien lo cambió y lo restauramos
                val r = d.setGlobalPrivateDnsModeSpecifiedHost(a, HOST)  // Android resuelve el host: hace falta internet
                if (r == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) Store.setDnsSetOnce(c)
                else err = "No se pudo fijar el DNS (código $r). ¿Sin internet? Se reintentará."
            } else Store.setDnsSetOnce(c)

            RESTRICTIONS.forEach { d.addUserRestriction(a, it) }
            d.setUninstallBlocked(a, c.packageName, true)
            d.setUserControlDisabledPackages(a, listOf(c.packageName))   // impide "Borrar datos" y "Forzar detención"
            d.setApplicationRestrictions(a, CHROME, chromePolicy())
            val failed = d.setPackagesSuspended(a, BROWSERS, true)       // "failed" = no instalados
            Store.setSuspended(c, BROWSERS.size - failed.size)
            Store.setLastCheck(c)
            Store.setError(c, err)
        } catch (e: Exception) {
            Store.setError(c, e.javaClass.simpleName + ": " + (e.message ?: ""))
            return false
        }
        scheduleWatchers(c)
        return true
    }

    /** Quita las restricciones (ventana de desbloqueo). Se reaplican solas al cerrarse la ventana. */
    fun release(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c); val a = admin(c)
        try {
            RESTRICTIONS.forEach { d.clearUserRestriction(a, it) }
            d.setPackagesSuspended(a, BROWSERS, false)
            d.setUserControlDisabledPackages(a, emptyList())
            d.setApplicationRestrictions(a, CHROME, Bundle())
        } catch (e: Exception) { Store.setError(c, e.javaClass.simpleName) }
    }

    /** Sale definitivamente del modo propietario. Solo se llama con la ventana de desbloqueo abierta. */
    @Suppress("DEPRECATION")
    fun removeOwner(c: Context) {
        if (!isOwner(c) || !Store.unlockOpen(c)) return
        release(c)
        val js = c.getSystemService(JobScheduler::class.java)
        js.cancel(1); js.cancel(2)
        Store.disarm(c)
        dpm(c).clearDeviceOwnerApp(c.packageName)
    }

    fun scheduleWatchers(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        val cn = ComponentName(c, EnforceJobService::class.java)
        if (js.getPendingJob(1) == null) {
            js.schedule(JobInfo.Builder(1, cn).setPeriodic(15 * 60_000L).setPersisted(true).build())
        }
        val f = JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
        js.schedule(JobInfo.Builder(2, cn)
            .addTriggerContentUri(JobInfo.TriggerContentUri(Settings.Global.getUriFor("private_dns_mode"), f))
            .addTriggerContentUri(JobInfo.TriggerContentUri(Settings.Global.getUriFor("private_dns_specifier"), f))
            .build())
    }

    fun scheduleReapply(c: Context, delayMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 0, Intent(c, EnforceReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs + 1500, pi)
    }

    class Status(
        val owner: Boolean, val dnsOk: Boolean, val dnsText: String,
        val restrictionsOk: Int, val restrictionsTotal: Int, val chromeOk: Boolean
    )

    /** Lee de vuelta el estado real desde Android (no es un indicador decorativo). */
    fun status(c: Context): Status {
        val total = RESTRICTIONS.size
        if (!isOwner(c)) return Status(false, false, "Sin modo propietario", 0, total, false)
        return try {
            val d = dpm(c); val a = admin(c)
            val mode = d.getGlobalPrivateDnsMode(a)
            val host = d.getGlobalPrivateDnsHost(a)
            val ok = mode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME && host == HOST
            val text = when (mode) {
                DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME -> "Nombre de host: $host"
                DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC -> "Automático (NO filtrado)"
                DevicePolicyManager.PRIVATE_DNS_MODE_OFF -> "Desactivado (NO filtrado)"
                else -> "Desconocido"
            }
            val ur = c.getSystemService(UserManager::class.java).userRestrictions
            val n = RESTRICTIONS.count { ur.getBoolean(it) }
            val chrome = d.getApplicationRestrictions(a, CHROME).getInt("IncognitoModeAvailability", 0) == 1
            Status(true, ok, text, n, total, chrome)
        } catch (e: Exception) {
            Status(true, false, "Error al leer: ${e.javaClass.simpleName}", 0, total, false)
        }
    }
}
