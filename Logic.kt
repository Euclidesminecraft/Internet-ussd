package org.megaflow

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Pedido(
    val id: Int, val idM: String, val numero: String, val valor: Double, var mb: Int?,
    var estado: String, var nota: String, val criado: Long, var iniciado: Long = 0, var passo: Int = 0
)

object Store {
    val rev = mutableIntStateOf(0)
    val pedidos = ArrayList<Pedido>()
    val precos = LinkedHashMap<Int, Int>()
    var remetentes: MutableList<String> = mutableListOf("M-PESA", "MPESA")
    var limite = 10
    var fmt = "9"
    val diag = ArrayList<String>()
    private lateinit var file: File
    private val RE_PAG = Regex("""Confirmado\s+([A-Z0-9]+)\.\s+Recebeste\s+([\d.,]+)MT\s+de\s+(258\d{9})""", RegexOption.IGNORE_CASE)
    private val RE_ENV = Regex("""Transferiste\s+(\d+)\s*MB.*?numero\s+(?:258)?(\d{9})""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun init(c: Context) {
        if (::file.isInitialized) return
        file = File(c.filesDir, "megaflow.json")
        try {
            val j = JSONObject(file.readText())
            val o = j.getJSONObject("precos")
            o.keys().forEach { precos[it.toInt()] = o.getInt(it) }
            val r = j.getJSONArray("rem")
            remetentes = MutableList(r.length()) { r.getString(it) }
            limite = j.getInt("limite"); fmt = j.getString("fmt")
            val a = j.getJSONArray("pedidos")
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                pedidos.add(Pedido(p.getInt("id"), p.getString("idM"), p.getString("numero"), p.getDouble("valor"),
                    p.getInt("mb").takeIf { it >= 0 }, p.getString("estado"), p.getString("nota"),
                    p.getLong("criado"), p.getLong("iniciado"), p.getInt("passo")))
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val j = JSONObject()
        val o = JSONObject(); precos.forEach { (k, v) -> o.put(k.toString(), v) }
        j.put("precos", o); j.put("rem", JSONArray(remetentes)); j.put("limite", limite); j.put("fmt", fmt)
        val a = JSONArray()
        pedidos.forEach { p ->
            a.put(JSONObject().put("id", p.id).put("idM", p.idM).put("numero", p.numero).put("valor", p.valor)
                .put("mb", p.mb ?: -1).put("estado", p.estado).put("nota", p.nota).put("criado", p.criado)
                .put("iniciado", p.iniciado).put("passo", p.passo))
        }
        j.put("pedidos", a)
        val t = File(file.path + ".tmp"); t.writeText(j.toString()); t.renameTo(file)
        rev.intValue++
    }

    fun commit() = save()

    fun log(s: String) {
        diag.add(0, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + " " + s)
        while (diag.size > 20) diag.removeAt(diag.size - 1)
        rev.intValue++
    }

    fun valor(s: String): Double {
        var t = s
        if (',' in t && '.' in t) {
            val dec = if (t.lastIndexOf(',') > t.lastIndexOf('.')) ',' else '.'
            t = t.replace(if (dec == ',') "." else ",", "").replace(dec, '.')
        } else if (',' in t) {
            val i = t.lastIndexOf(','); val b = t.substring(i + 1)
            t = t.substring(0, i).replace(",", "") + (if (b.length == 2) ".$b" else b)
        }
        return t.toDouble()
    }

    private fun dia(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .also { it.timeZone = TimeZone.getTimeZone("Africa/Maputo") }.format(Date(t))

    @Synchronized fun feitosHoje(): Int = pedidos.count {
        (it.estado == "ENTREGUE" || it.estado == "EM_EXECUCAO") && it.iniciado > 0 && dia(it.iniciado) == dia(System.currentTimeMillis())
    }

    @Synchronized fun sms(c: Context, rem: String, texto: String): String {
        init(c)
        val conf = RE_ENV.find(texto)
        if (conf != null) {
            val p = pedidos.firstOrNull { it.estado == "EM_EXECUCAO" } ?: return "confirmação sem pedido em execução"
            if (p.numero.takeLast(9) != conf.groupValues[2] || p.mb != conf.groupValues[1].toInt()) return "confirmação não corresponde ao pedido"
            p.estado = "ENTREGUE"; p.nota = "confirmado por SMS"; save()
            return "pedido #${p.id} entregue"
        }
        val m = RE_PAG.find(texto) ?: return "ignorado: não é recebimento M-Pesa"
        if (remetentes.isNotEmpty() && remetentes.none { it.equals(rem.trim(), true) }) return "remetente não permitido: $rem"
        val id = m.groupValues[1].uppercase()
        if (pedidos.any { it.idM == id }) return "duplicado: $id"
        val v = valor(m.groupValues[2])
        val mb = if (v == Math.floor(v)) precos[v.toInt()] else null
        pedidos.add(Pedido(pedidos.size + 1, id, m.groupValues[3], v, mb, if (mb != null) "A_ENTREGAR" else "REVISAO",
            if (mb != null) "ok" else "valor fora da tabela de preços", System.currentTimeMillis()))
        save()
        return if (mb != null) "pedido criado: $mb MB" else "pedido em REVISAO: valor fora da tabela"
    }

    @Synchronized fun expirar() {
        var ch = false
        pedidos.filter { it.estado == "EM_EXECUCAO" && System.currentTimeMillis() - it.iniciado > 300_000 }.forEach {
            it.estado = "FALHA"; it.nota = "sem confirmação a tempo; confirma o saldo antes de reenviar"; ch = true
        }
        if (ch) save()
    }

    @Synchronized fun proximo(): Pedido? {
        if (pedidos.any { it.estado == "EM_EXECUCAO" } || feitosHoje() >= limite) return null
        val p = pedidos.firstOrNull { it.estado == "A_ENTREGAR" } ?: return null
        p.estado = "EM_EXECUCAO"; p.iniciado = System.currentTimeMillis(); p.passo = 0; save()
        return p
    }

    @Synchronized fun manual(id: Int, ok: Boolean) {
        pedidos.firstOrNull { it.id == id && it.estado == "EM_EXECUCAO" }?.let {
            it.estado = if (ok) "ENTREGUE" else "FALHA"; it.nota = "marcado à mão"; save()
        }
    }

    @Synchronized fun requeue(id: Int) {
        pedidos.firstOrNull { it.id == id && (it.estado == "FALHA" || it.estado == "REVISAO") }?.let {
            if (it.mb == null && it.valor == Math.floor(it.valor)) it.mb = precos[it.valor.toInt()]
            it.estado = if (it.mb != null) "A_ENTREGAR" else "REVISAO"
            it.nota = if (it.mb != null) "reposto à mão" else "valor fora da tabela de preços"; save()
        }
    }
}
