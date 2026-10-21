package org.megaflow

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val m = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (m == null || m.isEmpty()) return
        Store.sms(c, m[0].originatingAddress ?: "", m.joinToString("") { it.messageBody ?: "" })
    }
}

class UssdService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var ultimo = ""
    private val proc = Runnable { processar() }
    private val loop = object : Runnable {
        override fun run() {
            Store.expirar()
            Store.proximo()?.let { ligar(it) }
            h.postDelayed(this, 10_000)
        }
    }

    override fun onServiceConnected() { Store.init(applicationContext); h.post(loop) }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val pkg = e?.packageName?.toString() ?: return
        if (pkg.contains("phone") || pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("incallui")) {
            h.removeCallbacks(proc); h.postDelayed(proc, 700)
        }
    }

    private fun ligar(p: Pedido) {
        ultimo = ""
        Store.log("a iniciar #${p.id}: ${p.mb} MB para ${p.numero}")
        try {
            (getSystemService(TELECOM_SERVICE) as TelecomManager).placeCall(Uri.fromParts("tel", "*162#", null), null)
        } catch (e: Exception) { falhar(p, "não consegui iniciar o USSD: ${e.message}") }
    }

    private fun falhar(p: Pedido, msg: String) { p.estado = "FALHA"; p.nota = msg; Store.commit() }

    private fun clicar(bs: List<AccessibilityNodeInfo>, nomes: List<String>) {
        val b = bs.firstOrNull { x -> nomes.any { x.text?.toString()?.trim()?.lowercase() == it } } ?: bs.lastOrNull() ?: return
        b.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun processar() {
        val p = Store.pedidos.firstOrNull { it.estado == "EM_EXECUCAO" } ?: return
        val root = rootInActiveWindow ?: return
        val textos = ArrayList<String>()
        val botoes = ArrayList<AccessibilityNodeInfo>()
        var campo: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val cls = n.className?.toString() ?: ""
            when {
                cls.contains("EditText") -> campo = n
                cls.contains("Button") -> botoes.add(n)
                !n.text.isNullOrBlank() -> textos.add(n.text.toString())
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val ed = campo
        val txt = textos.joinToString(" | ")
        val chave = txt + (ed != null) + p.passo
        if (chave == ultimo) return
        ultimo = chave
        Store.log("[p${p.passo}] ${txt.take(150)}${if (ed != null) " [campo]" else ""}")
        val low = txt.lowercase()
        val fechar = listOf("ok", "cancelar", "cancel", "fechar")
        if (listOf("nao permitida", "não permitida", "invalido", "inválido", "erro", "insuficiente").any { low.contains(it) }) {
            falhar(p, txt.take(120)); clicar(botoes, fechar); return
        }
        if (ed == null) {
            if (p.passo >= 3) { p.nota = "enviado; a aguardar SMS de confirmação"; Store.commit(); clicar(botoes, fechar) }
            return
        }
        val resp = when (p.passo) {
            0 -> "2"
            1 -> p.mb.toString()
            2 -> if (Store.fmt == "258") p.numero else p.numero.takeLast(9)
            else -> null
        }
        if (resp == null) { falhar(p, "diálogo inesperado"); clicar(botoes, fechar); return }
        ed.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, resp) })
        clicar(botoes, listOf("enviar", "send", "responder", "reply", "ok"))
        p.passo++; Store.commit()
    }
}
