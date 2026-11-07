package com.purelock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val BG = 0xFF0F1115.toInt()
    private val CARD = 0xFF1A1D24.toInt()
    private val GREEN = 0xFF2ECC71.toInt()
    private val RED = 0xFFE74C3C.toInt()
    private val AMBER = 0xFFF1C40F.toInt()
    private val MUTED = 0xFF9AA0AA.toInt()

    private lateinit var root: LinearLayout
    private val h = Handler(Looper.getMainLooper())
    private var sig = ""
    private var cdView: TextView? = null
    private val tick = object : Runnable { override fun run() { render(); h.postDelayed(this, 1000) } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = BG; window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
    }
    override fun onResume() { super.onResume(); sig = ""; h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        super.onActivityResult(r, res, d)
        if (res == RESULT_OK) startVpn()
    }
    private fun startVpn() { try { startService(Intent(this, DnsVpnService::class.java)) } catch (_: Exception) {} }

    // ---------- pantallas ----------
    private fun render() {
        if (!Store.hasPin(this)) {
            if (sig != "pin") { sig = "pin"; root.removeAllViews(); buildPin() }
            return
        }
        val vpnOk = VpnService.prepare(this) == null
        val a11y = Store.a11yOn(this)
        val admin = getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(ComponentName(this, AdminReceiver::class.java))
        if (!(vpnOk && a11y && admin)) {
            val s = "perms$vpnOk$a11y$admin"
            if (s != sig) { sig = s; root.removeAllViews(); buildPerms(vpnOk, a11y, admin) }
            return
        }
        Store.arm(this)
        val open = Store.unlockOpen(this)
        if (!DnsVpnService.running && !open) startVpn()
        val cd = Store.cooldownLeft(this)
        val phase = when { open -> "open"; cd < 0 -> "none"; cd > 0 -> "wait"; else -> "ready" }
        val s = "dash${DnsVpnService.running}${Store.vpnActive(this)}${Store.days(this)}" +
            "${Store.attempts(this)}$phase${Store.privateDns(this)}${Store.alwaysOn(this)}${Store.lockdown(this)}"
        if (s != sig) { sig = s; root.removeAllViews(); buildDash(phase) }
        cdView?.text = when (phase) {
            "wait" -> "Faltan ${fmt(cd)} para poder ingresar el PIN"
            "open" -> "Ventana abierta: ${fmt(Store.windowLeft(this))}"
            else -> ""
        }
    }

    private fun buildPin() {
        add(tv("PURELOCK", 28f, GREEN, true))
        add(tv("Persona de confianza", 18f, Color.WHITE, true))
        add(tv("Entrega el teléfono a la persona que te va a acompañar. ELLA escribe el PIN y no te lo dice. " +
            "Sin ese PIN (y sin esperar 24 h) no podrás apagar la protección.", 14f, MUTED), 16)
        val name = et("Nombre de la persona de confianza", InputType.TYPE_CLASS_TEXT)
        val phone = et("Su teléfono (con código de país)", InputType.TYPE_CLASS_PHONE)
        val numPw = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val p1 = et("PIN nuevo (mínimo 6 dígitos)", numPw)
        val p2 = et("Repite el PIN", numPw)
        listOf(name, phone, p1, p2).forEach { add(it) }
        add(btn("Guardar", GREEN) {
            when {
                name.text.isBlank() || phone.text.isBlank() -> toast("Falta nombre o teléfono")
                p1.text.length < 6 -> toast("El PIN debe tener al menos 6 dígitos")
                p1.text.toString() != p2.text.toString() -> toast("Los PIN no coinciden")
                else -> { Store.saveTrusted(this, name.text.toString().trim(), phone.text.toString().trim(), p1.text.toString()); sig = "" }
            }
        })
    }

    private fun buildPerms(vpnOk: Boolean, a11y: Boolean, admin: Boolean) {
        add(tv("PURELOCK", 28f, GREEN, true))
        add(tv("Configuración inicial", 18f, Color.WHITE, true))
        add(tv("Antes de continuar: Ajustes > Red > DNS privado debe estar en \"Automático\" (no en \"Nombre de host\").", 13f, MUTED), 16)
        add(card(tv("Qué NO puede garantizar PureLock", 14f, AMBER, true),
            tv("Reduce el riesgo y añade fricción, pero no es imposible de saltar: un reinicio de fábrica, el modo seguro, " +
                "apps con DNS propio o borrar la app desde tu cuenta de Google desde otro dispositivo lo evaden. " +
                "Lo más fuerte es que otra persona controle tu cuenta de Google y el PIN.", 12f, Color.WHITE)))
        perm("1. Filtro DNS (VPN local)", "Solo reenvía consultas DNS; no ve tu tráfico.", vpnOk, "Permitir") {
            VpnService.prepare(this)?.let { startActivityForResult(it, 1) }
        }
        perm("2. Guardián (Accesibilidad)", "Bloquea las pantallas de Ajustes que apagan el filtro. " +
            "Si sale en gris: Ajustes > Apps > PureLock > ⋮ > Permitir ajustes restringidos.", a11y, "Activar") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        perm("3. Anti-desinstalación (Administrador)", "Android no deja desinstalar una app administradora.", admin, "Activar") {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Evita que PureLock se desinstale en un impulso."))
        }
    }

    private fun perm(title: String, desc: String, ok: Boolean, label: String, f: () -> Unit) {
        val v = mutableListOf(tv(title, 16f, Color.WHITE, true), tv(desc, 12f, MUTED))
        if (ok) v.add(tv("✔ Listo", 14f, GREEN, true)) else v.add(btn(label, GREEN, f))
        add(card(*v.toTypedArray()))
    }

    private fun buildDash(phase: String) {
        val open = phase == "open"
        val ok = DnsVpnService.running && Store.vpnActive(this)
        val (label, col) = when {
            open -> "VENTANA DE DESBLOQUEO ABIERTA" to AMBER
            ok -> "PROTECCIÓN ACTIVADA" to GREEN
            else -> "PROTECCIÓN DESACTIVADA" to RED
        }
        add(tv("PURELOCK", 14f, MUTED, true), 6)
        add(card(tv(label, 22f, col, true), tv("Filtro DNS CleanBrowsing Adult", 13f, MUTED)))

        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(card(tv("${Store.days(this)}", 30f, GREEN, true), tv("días protegido", 12f, MUTED)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        r.addView(card(tv("${Store.attempts(this)}", 30f, if (Store.attempts(this) > 0) AMBER else GREEN, true), tv("intentos de desactivación", 12f, MUTED)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        add(r)

        add(card(tv("Estado del DNS", 14f, MUTED, true),
            tv(if (DnsVpnService.running) "Activo · ${DnsVpnService.SERVERS[0]} / ${DnsVpnService.SERVERS[1]}" else "Inactivo",
                15f, if (DnsVpnService.running) GREEN else RED, true),
            tv("DNS privado de Android: ${Store.privateDns(this)}", 12f, MUTED)))

        val w = mutableListOf<String>()
        if (Store.privateDns(this) == "hostname") w += "⚠ DNS privado en \"Nombre de host\": ponlo en Automático o puede cortar el internet."
        if (!Store.alwaysOn(this)) w += "⚠ Falta VPN siempre activa: Ajustes > Red > VPN > PureLock > ⚙ (activa también \"Bloquear conexiones sin VPN\")."
        else if (!Store.lockdown(this)) w += "⚠ Falta activar \"Bloquear conexiones sin VPN\"."
        w += "ℹ Apps con DNS propio (DoH en Firefox/Brave, VPN de terceros) pueden saltarse el filtro; PureLock no puede detectarlo."
        add(card(tv("Revisión de evasiones", 14f, MUTED, true), tv(w.joinToString("\n\n"), 12f, Color.WHITE)))

        add(btn("🆘 Pedir ayuda a ${Store.name(this)}", RED) {
            sms("Estoy teniendo un impulso fuerte ahora mismo. ¿Puedes llamarme o hablar conmigo?")
        }, 6)
        add(btn("📞 Llamar a ${Store.name(this)}", CARD) {
            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Store.phone(this)))) } catch (_: Exception) {}
        })

        add(tv("PureLock añade fricción, no garantiza protección total. Mantén a tu persona de confianza al tanto.", 11f, MUTED), 8)
        if (!DnsVpnService.running && !open) add(btn("Reactivar protección", GREEN) {
            val i = VpnService.prepare(this)
            if (i != null) startActivityForResult(i, 1) else startVpn()
        })

        cdView = null
        when (phase) {
            "none" -> add(btn("Solicitar desbloqueo (espera 24 h)", CARD) {
                AlertDialog.Builder(this).setTitle("Solicitar desbloqueo")
                    .setMessage("Deberás esperar 24 horas y luego tu persona de confianza tendrá que escribir el PIN. Se abrirá un mensaje para avisarle.")
                    .setPositiveButton("Solicitar") { _, _ ->
                        Store.requestUnlock(this)
                        sms("Pedí desbloquear PureLock. Si no quieres que lo haga, no me des el PIN. Gracias por acompañarme.")
                    }.setNegativeButton("Cancelar", null).show()
            })
            "wait" -> { cdView = tv("", 14f, AMBER); add(card(cdView!!)); add(btn("Cancelar solicitud", CARD) { Store.cancelRequest(this) }) }
            "ready" -> {
                add(btn("Ingresar PIN del administrador", AMBER) { askPin() })
                add(btn("Cancelar solicitud", CARD) { Store.cancelRequest(this) })
            }
            "open" -> {
                cdView = tv("", 14f, AMBER); add(card(cdView!!))
                add(btn("Pausar filtro DNS", RED) { startService(Intent(this, DnsVpnService::class.java).setAction("STOP")) })
                add(btn("Cerrar ventana ahora", GREEN) { Store.closeWindow(this) })
            }
        }
    }

    private fun askPin() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("PIN del administrador").setView(et)
            .setPositiveButton("Confirmar") { _, _ ->
                toast(when (Store.confirmUnlock(this, et.text.toString())) {
                    Store.R.OK -> "Ventana abierta por 10 minutos"
                    Store.R.BAD_PIN -> "PIN incorrecto"
                    Store.R.LOCKED -> "Demasiados intentos: bloqueado 1 hora"
                    Store.R.WAIT -> "Aún no termina la espera"
                    Store.R.NO_REQUEST -> "Primero solicita el desbloqueo"
                })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun sms(body: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Store.phone(this))).putExtra("sms_body", body))
        } catch (_: Exception) { toast("No se pudo abrir la app de mensajes") }
    }

    // ---------- helpers de UI ----------
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }
    private fun add(v: View, bottom: Int = 12) {
        root.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(bottom)) })
    }
    private fun bg(c: Int, r: Int = 16) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun tv(t: String, sp: Float, col: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply { text = t; textSize = sp; setTextColor(col); if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); background = bg(CARD)
        v.forEach { addView(it) }
    }
    private fun btn(t: String, c: Int, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false
        setTextColor(if (c == AMBER || c == GREEN) Color.BLACK else Color.WHITE)
        background = bg(c, 14); setOnClickListener { f() }
    }
    private fun et(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; setTextColor(Color.WHITE); setHintTextColor(MUTED)
        background = bg(CARD, 12); setPadding(dp(14), dp(12), dp(14), dp(12))
    }
}
