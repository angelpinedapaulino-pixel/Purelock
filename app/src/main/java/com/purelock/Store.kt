package com.purelock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Estado local. Todo queda en el teléfono; la app no tiene permiso de internet. */
object Store {
    const val COOLDOWN_MS = 24L * 3600_000   // espera tras pedir el desbloqueo
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
        if (hasPin(c)) return                       // el PIN existente no se puede sobrescribir
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit().putString("name", name).putString("phone", phone)
            .putString("salt", b64(salt)).putString("hash", b64(hash(pin, salt))).apply()
    }
    fun name(c: Context) = p(c).getString("name", "") ?: ""
    fun phone(c: Context) = p(c).getString("phone", "") ?: ""

    fun armed(c: Context) = p(c).getBoolean("armed", false)
    fun arm(c: Context) { p(c).edit().putBoolean("armed", true).putLong("start", now()).apply() }
    fun disarm(c: Context) { p(c).edit().putBoolean("armed", false).putLong("window", 0).putLong("req", 0).apply() }
    fun days(c: Context): Long = if (!armed(c)) 0 else (now() - p(c).getLong("start", now())) / 86_400_000

    fun corrections(c: Context) = p(c).getInt("corrections", 0)
    fun addCorrection(c: Context) { p(c).edit().putInt("corrections", corrections(c) + 1).apply() }
    fun dnsSetOnce(c: Context) = p(c).getBoolean("dnsOnce", false)
    fun setDnsSetOnce(c: Context) { p(c).edit().putBoolean("dnsOnce", true).apply() }
    fun suspended(c: Context) = p(c).getInt("suspended", 0)
    fun setSuspended(c: Context, n: Int) { p(c).edit().putInt("suspended", n).apply() }
    fun lastCheck(c: Context) = p(c).getLong("lastCheck", 0)
    fun setLastCheck(c: Context) { p(c).edit().putLong("lastCheck", now()).apply() }
    fun error(c: Context) = p(c).getString("error", "") ?: ""
    fun setError(c: Context, s: String) { p(c).edit().putString("error", s).apply() }

    fun unlockOpen(c: Context) = now() < p(c).getLong("window", 0)
    fun windowLeft(c: Context) = maxOf(0L, p(c).getLong("window", 0) - now())
    fun closeWindow(c: Context) { p(c).edit().putLong("window", 0).apply() }
    fun guardActive(c: Context) = armed(c) && !unlockOpen(c)

    fun requestUnlock(c: Context) { if (p(c).getLong("req", 0) == 0L) p(c).edit().putLong("req", now()).apply() }
    fun cancelRequest(c: Context) { p(c).edit().putLong("req", 0).apply() }
    /** -1 = sin solicitud; 0 = ya puede ingresar el PIN; >0 = ms restantes */
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
            if (f >= 5) e.putInt("fails", 0).putLong("lockUntil", t + 3600_000)   // 5 fallos = 1 hora bloqueado
            e.apply()
            return R.BAD_PIN
        }
        pr.edit().putInt("fails", 0).putLong("req", 0).putLong("window", t + WINDOW_MS).putLong("start", t).apply()
        return R.OK
    }
}
