package com.jarvis.control.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.jarvis.control.JarvisViewModel
import com.jarvis.control.serial.NavDir

@Composable
fun JarvisScreen(vm: JarvisViewModel) {
    val ui by vm.ui.collectAsState()
    var pickerDrivers by remember { mutableStateOf<List<UsbSerialDriver>?>(null) }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", color = Accent, fontSize = 20.sp, letterSpacing = 4.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(10.dp).background(
                    if (ui.connected) Ok else Err, CircleShape
                )
            )
            Spacer(Modifier.width(6.dp))
            Text(ui.status, color = Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (ui.connected) vm.disconnect()
                    else {
                        val d = vm.listDrivers()
                        when {
                            d.isEmpty() -> vm.hintNoDevice()
                            d.size == 1 -> vm.connect(d.first())
                            else -> pickerDrivers = d
                        }
                    }
                },
                enabled = !ui.busy,
            ) { Text(if (ui.connected) "Disconnetti" else "Connetti") }
        }

        // ---- Screen mirror ----
        Box(
            Modifier.fillMaxWidth().aspectRatio(320f / 170f)
                .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(8.dp))
                .border(2.dp, Line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val screen = ui.screen
            if (screen != null) {
                Image(
                    bitmap = screen,
                    contentDescription = "Schermo del dispositivo",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Text(
                    if (ui.connected) "In attesa dello schermo…"
                    else "Collega il T-Embed via USB-C e premi Connetti",
                    color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
        }

        // ---- D-pad ----
        DPad(enabled = ui.connected, onNav = vm::nav, onRefresh = vm::refreshNow)

        // ---- Menu ----
        Text(ui.menu?.title ?: "—", color = Accent, fontSize = 13.sp)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f)
                .background(Panel, RoundedCornerShape(8.dp))
                .border(1.dp, Line, RoundedCornerShape(8.dp)).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val opts = ui.menu?.options.orEmpty()
            if (opts.isEmpty()) item {
                Text("Nessuna voce (app in esecuzione)", color = Dim, fontSize = 13.sp,
                    modifier = Modifier.padding(8.dp))
            }
            items(opts) { o ->
                val active = o.n == ui.menu?.active
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (active) Accent2.copy(alpha = .22f) else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(6.dp))
                        .clickable(enabled = ui.connected) { vm.runOption(o.n) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text("${o.n}", color = Dim, fontSize = 13.sp, modifier = Modifier.width(26.dp))
                    Text(o.label, color = if (active) Accent else TextMain, fontSize = 13.sp)
                }
            }
        }

        // ---- Terminal ----
        Terminal(vm, ui.terminal, ui.appendNewline, ui.autoRefresh, ui.connected)
    }

    pickerDrivers?.let { drivers ->
        AlertDialog(
            onDismissRequest = { pickerDrivers = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickerDrivers = null }) { Text("Annulla") } },
            title = { Text("Scegli il dispositivo") },
            text = {
                Column {
                    drivers.forEach { d ->
                        val label = d.device.productName ?: d.device.deviceName
                        TextButton(onClick = { pickerDrivers = null; vm.connect(d) }) {
                            Text("$label  (${d.device.vendorId}:${d.device.productId})")
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DPad(enabled: Boolean, onNav: (NavDir) -> Unit, onRefresh: () -> Unit) {
    @Composable
    fun navBtn(dir: NavDir, modifier: Modifier = Modifier) {
        Button(
            onClick = { onNav(dir) }, enabled = enabled, modifier = modifier.size(52.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = if (dir == NavDir.SEL)
                ButtonDefaults.buttonColors(containerColor = Accent2) else ButtonDefaults.buttonColors(),
            shape = if (dir == NavDir.SEL) CircleShape else RoundedCornerShape(10.dp),
        ) { Text(dir.glyph, fontSize = 18.sp) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            navBtn(NavDir.PREV_PAGE); navBtn(NavDir.UP); navBtn(NavDir.NEXT_PAGE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            navBtn(NavDir.PREV); navBtn(NavDir.SEL); navBtn(NavDir.NEXT)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            navBtn(NavDir.ESC)
            IconButton(onClick = onRefresh, enabled = enabled) {
                Text("⟳", color = Accent, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Terminal(
    vm: JarvisViewModel, text: String, appendNewline: Boolean, autoRefresh: Boolean, connected: Boolean,
) {
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(130.dp)
                .background(androidx.compose.ui.graphics.Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .verticalScroll(scroll).padding(8.dp),
        ) {
            Text(text.ifEmpty { "Terminale CLI — digita un comando Bruce qui sotto." },
                color = if (text.isEmpty()) Dim else TextMain,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("comando / testo", fontSize = 13.sp) },
                enabled = connected,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.sendCommand(input); input = ""
                }),
            )
            IconButton(onClick = { vm.sendCommand(input); input = "" }, enabled = connected) {
                Text("➤", color = Accent, fontSize = 18.sp)
            }
            IconButton(onClick = { vm.clearTerminal() }) {
                Text("✕", color = Dim, fontSize = 16.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = appendNewline, onClick = { vm.toggleNewline() },
                label = { Text("Invio a capo", fontSize = 12.sp) })
            FilterChip(selected = autoRefresh, onClick = { vm.toggleAutoRefresh() },
                label = { Text("Auto-refresh", fontSize = 12.sp) })
        }
    }
}
