package org.megaflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val BG = Color(0xFF06121D); val CARD = Color(0xFF0E2234); val AZ = Color(0xFF2F8BFF); val VD = Color(0xFF18C98B)
val TX = Color(0xFFE9F2F9); val MU = Color(0xFF86A1B8); val AM = Color(0xFFF4B740); val VM = Color(0xFFFF6B6B); val RX = Color(0xFF9A7BFF)

fun cor(e: String) = when (e) { "A_ENTREGAR" -> AZ; "EM_EXECUCAO" -> AM; "REVISAO" -> RX; "FALHA" -> VM; else -> VD }

fun acessOn(c: Context): Boolean {
    val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return s.contains("org.megaflow") && s.contains("UssdService")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(applicationContext)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
            .launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.CALL_PHONE))
        setContent { App() }
    }
}

@Composable fun Cartao(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable fun Kpi(n: String, l: String, c: Color, m: Modifier) {
    Column(m.background(CARD, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(n, color = c, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(l, color = MU, fontSize = 11.sp)
    }
}

@Composable fun Chip(e: String) {
    Text(e, color = cor(e), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(cor(e).copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp))
}

@Composable fun App() {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val r = Store.rev.intValue
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val acess = tick >= 0 && acessOn(ctx)
    MaterialTheme(colorScheme = darkColorScheme(primary = AZ, background = BG, surface = CARD, onSurface = TX, onBackground = TX)) {
        Column(Modifier.fillMaxSize().background(BG).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Brush.linearGradient(listOf(AZ, VD)), RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MegaFlow", color = TX, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("pagamento M-Pesa → megas entregues", color = MU, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Pedidos", "Preços", "Definições", "Diagnóstico").forEachIndexed { i, t ->
                    Button(onClick = { tab = i }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (tab == i) AZ else CARD)) {
                        Text(t, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) { 0 -> Pedidos(ctx, acess); 1 -> Precos(); 2 -> Definicoes(); else -> Diagnostico() }
        }
    }
}

@Composable fun Pedidos(ctx: Context, acess: Boolean) {
    var t by remember { mutableStateOf("Confirmado TESTE001. Recebeste 80.00MT de 258840000000 - TESTE aos 20/10/2026 as 10:00.") }
    var res by remember { mutableStateOf("") }
    fun n(e: String) = Store.pedidos.count { it.estado == e }.toString()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi(n("A_ENTREGAR"), "FILA", AZ, Modifier.weight(1f)); Kpi(n("EM_EXECUCAO"), "A EXECUTAR", AM, Modifier.weight(1f)); Kpi(n("REVISAO"), "REVISÃO", RX, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi(n("FALHA"), "FALHAS", VM, Modifier.weight(1f)); Kpi(n("ENTREGUE"), "ENTREGUES", VD, Modifier.weight(1f)); Kpi("${Store.feitosHoje()}/${Store.limite}", "HOJE", TX, Modifier.weight(1f))
            }
        }
        if (!acess) item {
            Cartao {
                Text("Falta ativar a Acessibilidade do MegaFlow. Sem ela, nada é entregue.", color = AM)
                Text("Android 13 ou mais recente: primeiro Definições > Apps > MegaFlow > ⋮ > Permitir configurações restritas.", color = MU, fontSize = 12.sp)
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Abrir Acessibilidade") }
            }
        }
        item {
            Cartao {
                Text("Testar um SMS", color = TX, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = t, onValueChange = { t = it }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { res = Store.sms(ctx, "M-PESA", t) }) { Text("Simular recebimento") }
                if (res.isNotEmpty()) Text(res, color = VD, fontSize = 12.sp)
            }
        }
        items(Store.pedidos.reversed()) { p ->
            Cartao {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${"%.2f".format(p.valor)} MT → ${p.mb ?: "?"} MB", color = TX, fontWeight = FontWeight.Bold)
                    Chip(p.estado)
                }
                Text("${p.numero} · ${p.idM}", color = MU, fontSize = 13.sp)
                if (p.nota != "ok") Text(p.nota, color = MU, fontSize = 12.sp)
                if (p.estado == "EM_EXECUCAO") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Store.manual(p.id, true) }) { Text("Marcar entregue") }
                    Button(onClick = { Store.manual(p.id, false) }) { Text("Marcar falha") }
                }
                if (p.estado == "FALHA" || p.estado == "REVISAO") {
                    Button(onClick = { Store.requeue(p.id) }) { Text("Voltar à fila") }
                    if (p.estado == "FALHA") Text("Confirma o saldo antes: os MB podem já ter saído.", color = AM, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable fun Precos() {
    var mt by remember { mutableStateOf("") }
    var mb by remember { mutableStateOf("") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Cartao {
                Text("Tabela de preços", color = TX, fontWeight = FontWeight.Bold)
                Text("MB entre 100 e 10240. Os MB só valem 24h.", color = MU, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mt, { mt = it }, Modifier.weight(1f), label = { Text("MT") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(mb, { mb = it }, Modifier.weight(1f), label = { Text("MB") }, singleLine = true, keyboardOptions = num)
                }
                Button(onClick = {
                    val a = mt.toIntOrNull(); val b = mb.toIntOrNull()
                    if (a != null && b != null && a > 0 && b in 100..10240) { Store.precos[a] = b; Store.commit(); mt = ""; mb = "" }
                }) { Text("Adicionar") }
            }
        }
        items(Store.precos.entries.toList()) { e ->
            Cartao {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.key} MT → ${e.value} MB", color = TX)
                    TextButton(onClick = { Store.precos.remove(e.key); Store.commit() }) { Text("Apagar", color = VM) }
                }
            }
        }
    }
}

@Composable fun Definicoes() {
    var rem by remember { mutableStateOf(Store.remetentes.joinToString(",")) }
    var lim by remember { mutableStateOf(Store.limite.toString()) }
    var fmt by remember { mutableStateOf(Store.fmt) }
    Cartao {
        Text("Remetentes aceites (separados por vírgula; vazio desliga a verificação)", color = MU, fontSize = 12.sp)
        OutlinedTextField(rem, { rem = it }, Modifier.fillMaxWidth(), singleLine = true)
        Text("Limite diário (1 a 10)", color = MU, fontSize = 12.sp)
        OutlinedTextField(lim, { lim = it }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text("Número do destinatário no USSD", color = MU, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("9" to "9 dígitos", "258" to "258 + 9").forEach { (k, l) ->
                Button(onClick = { fmt = k }, colors = ButtonDefaults.buttonColors(containerColor = if (fmt == k) AZ else BG)) { Text(l) }
            }
        }
        Button(onClick = {
            Store.remetentes = rem.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            Store.limite = (lim.toIntOrNull() ?: 10).coerceIn(1, 10); Store.fmt = fmt; Store.commit()
        }) { Text("Guardar") }
    }
}

@Composable fun Diagnostico() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Últimos textos de diálogos USSD vistos pela app.", color = MU, fontSize = 12.sp) }
        items(Store.diag.toList()) { Cartao { Text(it, color = TX, fontSize = 12.sp) } }
    }
}
