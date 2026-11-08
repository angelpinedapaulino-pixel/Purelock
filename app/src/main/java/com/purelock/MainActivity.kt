package com.purelock

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val BG = 0xFF0F1115.toInt()
    private val CARD = 0xFF1A1D24.toInt()
    private val GREEN = 0xFF2ECC71.toInt()
    private val RED = 0xFFE74C3C.toInt()
    private val AMBER = 0xFFF1C40F.toInt()
    private val MUTED = 0xFF9AA0AA.toInt()
    private val CMD = "adb shell dpm set-device-owner com.purelock/.AdminReceiver"

    private lateinit var root: LinearLayout
    private val h = Handler(Looper.getMainLooper())
    private var sig = ""
    private var cdView: TextView? = null
    private var lastApply = 0L
    private val tick = object : Runnable { override fun run() { render(); h.postDelayed(this, 1000) } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = BG; window.navigationBarColor = BG
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
    }
    override fun onResume() { super.onResume(); sig = ""; h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    // ---------- selección de pantalla ----------
    private fun render() {
        if (!Store.hasPin(this)) {
            if (sig != "pin") { sig = "pin"; root.removeAllViews(); buildPin() }
            return
        }
        if (!Enforcer.isOwner(this)) {
            if (sig != "owner") { sig = "owner"; root.removeAllViews(); buildOwner() }
            return
        }
        if (!Store.armed(this)) {
            if (sig != "arm") { sig = "arm"; root.removeAllViews(); buildArm() }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastApply > 30_000) {
            lastApply = now
            val app = applicationContext
            Thread { Enforcer.apply(app) }.start()
        }
        val st = Enforcer.status(this)
        val open = Store.unlockOpen(this)
        val cd = Store.cooldownLeft(this)
        val phase = when { open -> "open"; cd < 0 -> "none"; cd > 0 -> "wait"; else -> "ready" }
        val s = "dash${st.dnsOk}${st.dnsText}${st.restrictionsOk}${st.chromeOk}${Store.days(this)}${Store.corrections(this)}" +
            "$phase${Store.suspended(this)}${Store.lastCheck(this) / 60000}${Store.error(this)}"
        if (s != sig) { sig = s; root.removeAllViews(); buildDash(st, phase) }
        cdView?.text = when (phase) {
            "wait" -> "Faltan ${fmt(cd)} para poder ingresar el PIN"
            "open" -> "Ventana abierta: ${fmt(Store.windowLeft(this))}"
            else -> ""
        }
    }

    // ---------- 1) persona de confianza ----------
    private fun buildPin() {
        add(tv("PURELOCK", 28f, GREEN, true))
        add(tv("Persona de confianza", 18f, Color.WHITE, true))
        add(tv("Entrega el teléfono a la persona que te va a acompañar. ELLA escribe el PIN y no te lo dice. " +
            "Sin ese PIN (y sin esperar 24 h) no podrás quitar la protección. El PIN no se puede cambiar después.", 14f, MUTED), 16)
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

    // ---------- 2) falta Device Owner ----------
    private fun buildOwner() {
        add(tv("PURELOCK", 28f, GREEN, true))
        add(card(
            tv("Falta el modo propietario del dispositivo", 17f, AMBER, true),
            tv("En Android 12 ninguna app puede impedir que cambies el DNS privado si no es \"propietaria del dispositivo\". " +
                "Sin ese modo, PureLock NO protege nada. Se activa una sola vez desde una computadora con este comando " +
                "(requisitos y pasos exactos en el README):", 13f, Color.WHITE)))
        add(card(tv(CMD, 13f, GREEN, true)))
        add(btn("Copiar comando", CARD) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("comando", CMD))
            toast("Copiado")
        })
        add(tv("Cuando el comando diga \"Success\", vuelve a abrir esta pantalla.", 12f, MUTED))
    }

    // ---------- 3) activar ----------
    private fun buildArm() {
        add(tv("PURELOCK", 28f, GREEN, true))
        add(card(
            tv("Todo listo para activar", 17f, GREEN, true),
            tv("Al activar, Android impondrá estas restricciones:\n" +
                "• DNS privado fijo: ${Enforcer.HOST}\n" +
                "• No se puede cambiar el DNS privado, ni configurar VPN, ni restablecer redes\n" +
                "• No se pueden instalar APK externos, crear usuarios, usar modo seguro, restablecer de fábrica ni depurar por USB\n" +
                "• La app no se puede desinstalar ni borrarle los datos\n" +
                "• Chrome: sin incógnito, sin DNS propio, SafeSearch forzado\n" +
                "• Otros navegadores (Firefox, Brave, Xiaomi Browser…) suspendidos\n\n" +
                "Para deshacerlo: solicitar desbloqueo, esperar 24 h y que ${Store.name(this)} escriba el PIN. " +
                "Si el PIN se pierde, la única salida es borrar el teléfono desde el modo recovery (pierdes los datos).", 13f, Color.WHITE)))
        add(btn("Activar protección", GREEN) {
            AlertDialog.Builder(this).setTitle("¿Activar ahora?")
                .setMessage("Esto no se puede deshacer sin el PIN de ${Store.name(this)} y 24 h de espera.")
                .setPositiveButton("Activar") { _, _ ->
                    Store.arm(this)
                    val app = applicationContext
                    Thread { Enforcer.apply(app) }.start()
                    sig = ""
                }.setNegativeButton("Cancelar", null).show()
        })
    }

    // ---------- 4) panel ----------
    private fun buildDash(st: Enforcer.Status, phase: String) {
        val open = phase == "open"
        val good = st.owner && st.dnsOk && st.restrictionsOk == st.restrictionsTotal
        val (label, col) = when {
            open -> "VENTANA DE DESBLOQUEO ABIERTA" to AMBER
            good -> "PROTECCIÓN ACTIVA" to GREEN
            else -> "PROTECCIÓN INCOMPLETA" to RED
        }
        add(tv("PURELOCK", 14f, MUTED, true), 6)
        add(card(tv(label, 22f, col, true),
            tv(if (good && !open) "Verificado ahora mismo leyendo la configuración de Android" else "Revisa los detalles de abajo", 12f, MUTED)))

        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(card(tv("${Store.days(this)}", 30f, GREEN, true), tv("días protegido", 12f, MUTED)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        r.addView(card(tv("${Store.corrections(this)}", 30f, if (Store.corrections(this) > 0) AMBER else GREEN, true),
            tv("cambios revertidos", 12f, MUTED)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        add(r)

        val lines = mutableListOf<View>(tv("Estado verificado", 14f, MUTED, true))
        lines.add(tv((if (st.dnsOk) "✔ " else "✖ ") + "DNS privado: ${st.dnsText}", 13f, if (st.dnsOk) GREEN else RED))
        lines.add(tv((if (st.restrictionsOk == st.restrictionsTotal) "✔ " else "✖ ") +
            "Restricciones de Android: ${st.restrictionsOk}/${st.restrictionsTotal}", 13f,
            if (st.restrictionsOk == st.restrictionsTotal) GREEN else RED))
        lines.add(tv((if (st.chromeOk) "✔ " else "✖ ") + "Chrome sin incógnito ni DNS propio", 13f, if (st.chromeOk) GREEN else RED))
        lines.add(tv("Navegadores alternativos suspendidos: ${Store.suspended(this)}", 13f, Color.WHITE))
        val lc = Store.lastCheck(this)
        lines.add(tv("Última comprobación: " + (if (lc == 0L) "—" else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(lc))), 12f, MUTED))
        if (Store.error(this).isNotEmpty()) lines.add(tv("⚠ ${Store.error(this)}", 12f, AMBER))
        add(card(*lines.toTypedArray()))

        add(card(tv("Lo que PureLock NO garantiza", 14f, AMBER, true),
            tv("• Si tu Xiaomi/MIUI no respeta alguna restricción en su pantalla de Ajustes, PureLock restaura el DNS en segundos o, como máximo, en ~15 min.\n" +
                "• No evita un borrado desde el modo recovery ni otro teléfono.\n" +
                "• No filtra apps con su propio DNS ni navegadores que no estén en su lista.\n" +
                "• El DNS filtra dominios; no todo el contenido.\n" +
                "• Quien controle el repositorio y la clave de firma de esta app puede publicar una versión sin PIN.", 12f, Color.WHITE)))

        add(btn("🆘 Pedir ayuda a ${Store.name(this)}", RED) {
            sms("Estoy teniendo un impulso fuerte ahora mismo. ¿Puedes llamarme o hablar conmigo?")
        }, 6)
        add(btn("📞 Llamar a ${Store.name(this)}", CARD) {
            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Store.phone(this)))) } catch (_: Exception) {}
        })

        cdView = null
        when (phase) {
            "none" -> add(btn("Solicitar desbloqueo (espera 24 h)", CARD) {
                AlertDialog.Builder(this).setTitle("Solicitar desbloqueo")
                    .setMessage("Deberás esperar 24 horas y luego ${Store.name(this)} tendrá que escribir el PIN. Se abrirá un mensaje para avisarle.")
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
                add(btn("Pausar protección (se reactiva sola al cerrar la ventana)", RED) {
                    val app = applicationContext
                    Thread { Enforcer.release(app); Enforcer.scheduleReapply(app, Store.windowLeft(app)) }.start()
                    toast("Protección en pausa")
                })
                add(btn("Quitar protección definitivamente", RED) {
                    AlertDialog.Builder(this).setTitle("Quitar protección")
                        .setMessage("Se quitará el modo propietario y todas las restricciones. PureLock dejará de proteger.")
                        .setPositiveButton("Quitar") { _, _ ->
                            val app = applicationContext
                            Thread { Enforcer.removeOwner(app) }.start()
                            sig = ""
                        }.setNegativeButton("Cancelar", null).show()
                })
                add(btn("Cerrar ventana ahora", GREEN) {
                    Store.closeWindow(this)
                    val app = applicationContext
                    Thread { Enforcer.apply(app) }.start()
                })
            }
        }
    }

    private fun askPin() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("PIN del administrador").setView(et)
            .setPositiveButton("Confirmar") { _, _ ->
                val res = Store.confirmUnlock(this, et.text.toString())
                if (res == Store.R.OK) Enforcer.scheduleReapply(applicationContext, Store.WINDOW_MS)
                toast(when (res) {
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
