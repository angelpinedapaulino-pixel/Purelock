package com.purelock

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * VPN local solo para DNS: el sistema envía las consultas DNS a una IP virtual,
 * este servicio las reenvía a CleanBrowsing Adult Filter. El resto del tráfico NO pasa por la VPN.
 * Si CleanBrowsing no responde, no se resuelve nada (falla cerrado).
 */
class DnsVpnService : VpnService() {
    companion object {
        @Volatile var running = false
        val SERVERS = listOf("185.228.168.10", "185.228.169.11")
        const val VIRTUAL_DNS = "10.111.0.1"
    }

    private var tun: ParcelFileDescriptor? = null
    private val pool = Executors.newFixedThreadPool(8)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            if (Store.unlockOpen(this)) stopVpn()   // solo se puede pausar con la ventana abierta
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    @Synchronized private fun startVpn() {
        if (running) return
        val fd = Builder().setSession("PureLock")
            .addAddress("10.111.0.2", 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            .establish() ?: return
        tun = fd; running = true
        if (Build.VERSION.SDK_INT >= 29) Store.saveVpnFlags(this, isAlwaysOn, isLockdownEnabled)
        Thread { loop(fd) }.start()
    }

    private fun stopVpn() { running = false; tun?.close(); tun = null; stopSelf() }

    override fun onRevoke() {
        running = false
        if (Store.guardActive(this)) {
            Store.addAttempt(this)
            Store.alert(this, "El filtro DNS fue apagado. Abre PureLock para reactivarlo.")
        }
        super.onRevoke()
    }

    override fun onDestroy() { running = false; tun?.close(); pool.shutdownNow(); super.onDestroy() }

    private fun loop(fd: ParcelFileDescriptor) {
        val inp = FileInputStream(fd.fileDescriptor)
        val out = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = inp.read(buf)
                if (n < 0) break
                if (n > 28) handle(buf.copyOf(n), out)
            }
        } catch (_: Exception) {}
        if (tun === fd) running = false
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)

    private fun handle(p: ByteArray, out: FileOutputStream) {
        if ((p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return      // solo IPv4/UDP
        val ihl = (p[0].toInt() and 15) * 4
        if (p.size < ihl + 8 || u16(p, ihl + 2) != 53) return
        val len = u16(p, ihl + 4) - 8
        if (len <= 0 || ihl + 8 + len > p.size) return
        val q = p.copyOfRange(ihl + 8, ihl + 8 + len)
        pool.execute {
            val r = resolve(q) ?: return@execute
            val resp = build(p, ihl, r)
            synchronized(out) { try { out.write(resp) } catch (_: Exception) {} }
        }
    }

    private fun resolve(q: ByteArray): ByteArray? {
        for (s in SERVERS) {
            try {
                DatagramSocket().use { sock ->
                    protect(sock)                       // esta consulta sale por la red real, no por la VPN
                    sock.soTimeout = 3000
                    sock.send(DatagramPacket(q, q.size, InetAddress.getByName(s), 53))
                    val buf = ByteArray(4096)
                    val r = DatagramPacket(buf, buf.size)
                    sock.receive(r)
                    return buf.copyOf(r.length)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun build(p: ByteArray, ihl: Int, r: ByteArray): ByteArray {
        val total = ihl + 8 + r.size
        val o = ByteArray(total)
        System.arraycopy(p, 0, o, 0, ihl)
        System.arraycopy(p, 12, o, 16, 4)       // origen <-> destino
        System.arraycopy(p, 16, o, 12, 4)
        o[2] = (total shr 8).toByte(); o[3] = total.toByte()
        o[6] = 0; o[7] = 0; o[8] = 64; o[10] = 0; o[11] = 0
        var s = 0; var i = 0
        while (i < ihl) { s += u16(o, i); i += 2 }
        while ((s shr 16) != 0) s = (s and 0xffff) + (s shr 16)
        val c = s.inv() and 0xffff
        o[10] = (c shr 8).toByte(); o[11] = c.toByte()
        System.arraycopy(p, ihl + 2, o, ihl, 2)  // puertos intercambiados
        System.arraycopy(p, ihl, o, ihl + 2, 2)
        val ul = 8 + r.size
        o[ihl + 4] = (ul shr 8).toByte(); o[ihl + 5] = ul.toByte()   // checksum UDP = 0 (válido en IPv4)
        System.arraycopy(r, 0, o, ihl + 8, r.size)
        return o
    }
}
