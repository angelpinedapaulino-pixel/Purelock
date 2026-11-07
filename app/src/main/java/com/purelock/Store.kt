package com.purelock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Store {
    const val COOLDOWN_MS = 24L * 3600_000   // espera antes de poder pedir el desbloqueo
    const val WINDOW_MS = 10L * 60_000       // ventana de cambios tras PIN correcto

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("purelock", Context.MODE_PRIVATE)
    private fun now() = System.currentTimeMillis()
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    fun hasPin(c: Context) = p(c).contains("hash")
    fun saveTrusted(c: Context, name: String, phone: String, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit().putString("name", name).putString("phone", phone)
            .putString("salt", b64(salt)).putString("hash", b64(hash(pin, salt))).apply()
    }
    fun name(c: Context) = p(c).getString("name", "") ?: ""
    fun phone(c: Context) = p(c).getString("phone", "") ?: ""

    fun armed(c: Context) = p(c).getBoolean("armed", false)
    fun arm(c: Context) { if (!armed(c)) p(c).edit().putBoolean("armed", true).putLong("start", now()).apply() }
    fun days(c: Context): Long = if (!armed(c)) 0 else (now() - p(c).getLong("start", now())) / 86_400_000
    fun attempts(c: Context) = p(c).getInt("attempts", 0)
    fun addAttempt(c: Context) { p(c).edit().putInt("attempts", attempts(c) + 1).apply() }

    fun unlockOpen(c: Context) = now() < p(c).getLong("window", 0)
    fun windowLeft(c: Context) = maxOf(0L, p(c).getLong("window", 0) - now())
    fun closeWindow(c: Context) { p(c).edit().putLong("window", 0).apply() }
    fun guardActive(c: Context) = armed(c) && !unlockOpen(c)

    fun requestUnlock(c: Context) { if (p(c).getLong("req", 0) == 0L) p(c).edit().putLong("req", now()).apply() }
    fun cancelRequest(c: Context) { p(c).edit().putLong("req", 0).apply() }
    /** -1 = sin solicitud; 0 = ya puede ingresar PIN; >0 = ms restantes */
    fun cooldownLeft(c: Context): Long {
        val r = p(c).getLong("req", 0)
        return if (r == 0L) -1 else maxOf(0L, r + COOLDOWN_MS - now())
    }

    enum class R { OK, NO_REQUEST, WAIT, LOCKED, BAD_PIN }
    fun confirmUnlock(c: Context, pin: String): R {
        val pr = p(c); val t = now()
        if (t < pr.getLong("lockUntil", 0)) return R.LOCKED
        val cd = cooldownLeft(c)
        if (cd < 0) return R.NO_REQUEST
        if (cd > 0) return R.WAIT
        val ok = MessageDigest.isEqual(
            hash(pin, unb64(pr.getString("salt", "")!!)), unb64(pr.getString("hash", "")!!))
        if (!ok) {
            val f = pr.getInt("fails", 0) + 1
            val e = pr.edit().putInt("fails", f)
            if (f >= 5) e.putInt("fails", 0).putLong("lockUntil", t + 3600_000)
            e.apply(); addAttempt(c)
            return R.BAD_PIN
        }
        pr.edit().putInt("fails", 0).putLong("req", 0).putLong("window", t + WINDOW_MS).putLong("start", t).apply()
        return R.OK
    }

    fun saveVpnFlags(c: Context, always: Boolean, lockdown: Boolean) {
        p(c).edit().putBoolean("always", always).putBoolean("lockdown", lockdown).apply()
    }
    fun alwaysOn(c: Context) = p(c).getBoolean("always", false)
    fun lockdown(c: Context) = p(c).getBoolean("lockdown", false)

    fun privateDns(c: Context): String = Settings.Global.getString(c.contentResolver, "private_dns_mode") ?: "?"

    @Suppress("DEPRECATION")
    fun vpnActive(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    fun a11yOn(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(c, GuardService::class.java)
        return s.contains(cn.flattenToString(), true) || s.contains(cn.flattenToShortString(), true)
    }

    fun alert(c: Context, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("purelock", "PureLock", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(1, Notification.Builder(c, "purelock").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PureLock").setContentText(text).setAutoCancel(true).build())
    }
}
