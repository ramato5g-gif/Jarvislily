package com.jarvis.control

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.jarvis.control.serial.Menu
import com.jarvis.control.serial.NavDir
import com.jarvis.control.serial.Protocol
import com.jarvis.control.serial.SerialDevice
import com.jarvis.control.serial.SerialException
import com.jarvis.control.serial.SerialManager
import com.jarvis.control.serial.TftRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val connected: Boolean = false,
    val status: String = "Disconnesso",
    val screen: ImageBitmap? = null,
    val menu: Menu? = null,
    val terminal: String = "",
    val autoRefresh: Boolean = true,
    val appendNewline: Boolean = true,
    val busy: Boolean = false,
)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = SerialManager(app)
    private val renderer = TftRenderer()
    private var device: SerialDevice? = null
    private var refreshJob: Job? = null

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    fun listDrivers(): List<UsbSerialDriver> = manager.listDrivers()

    fun connect(driver: UsbSerialDriver) {
        if (_ui.value.connected) return
        viewModelScope.launch {
            _ui.update { it.copy(status = "Connessione…", busy = true) }
            try {
                device = withContext(Dispatchers.IO) { manager.open(driver) }
                val name = driver.device.productName ?: driver.device.deviceName
                _ui.update { it.copy(connected = true, status = "Connesso · $name", busy = false) }
                log("[Connesso a $name]")
                startAutoRefresh()
            } catch (e: Exception) {
                val msg = (e as? SerialException)?.message ?: e.message ?: "errore sconosciuto"
                _ui.update { it.copy(status = "Errore: $msg", busy = false) }
                log("[Connessione fallita: $msg]")
            }
        }
    }

    fun disconnect() {
        refreshJob?.cancel()
        device?.close()
        device = null
        _ui.update { it.copy(connected = false, status = "Disconnesso", screen = null, menu = null) }
        log("[Disconnesso]")
    }

    fun nav(dir: NavDir) = withDevice { dev ->
        dev.send("nav ${dir.cmd}", until = Protocol.NAV_END, idle = 250, timeout = 1500)
        delay(120)
        refreshOnce(dev)
    }

    fun runOption(n: Int) = withDevice { dev ->
        dev.send("options $n", idle = 300, timeout = 1500)
        delay(250)
        refreshOnce(dev)
    }

    /** Sends a free-form line typed on the keyboard (optionally without newline). */
    fun sendCommand(text: String) = withDevice { dev ->
        if (text.isEmpty()) return@withDevice
        val nl = _ui.value.appendNewline
        log("> $text")
        val out = dev.send(text, idle = 600, timeout = 8000, newline = nl)
        if (out.isNotBlank()) log(out.trimEnd())
        delay(200)
        refreshOnce(dev)
    }

    fun toggleAutoRefresh() {
        val on = !_ui.value.autoRefresh
        _ui.update { it.copy(autoRefresh = on) }
        if (on) startAutoRefresh() else refreshJob?.cancel()
    }

    fun toggleNewline() = _ui.update { it.copy(appendNewline = !it.appendNewline) }

    fun clearTerminal() = _ui.update { it.copy(terminal = "") }

    fun refreshNow() = withDevice { dev -> refreshOnce(dev) }

    fun hintNoDevice() = log("[Nessun dispositivo USB rilevato. Collega il T-Embed via USB-C.]")

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _ui.value.connected && _ui.value.autoRefresh) {
                val dev = device ?: break
                runCatching { refreshOnce(dev) }
                delay(1000)
            }
        }
    }

    private suspend fun refreshOnce(dev: SerialDevice) {
        val dump = dev.send("display dump", until = Protocol.DUMP_END, idle = 800, timeout = 4000)
        Protocol.parseDump(dump)?.let { bytes ->
            // Copy: the renderer reuses one Bitmap across frames, so hand Compose a snapshot.
            val bmp = withContext(Dispatchers.Default) {
                renderer.render(bytes).copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            }
            _ui.update { it.copy(screen = bmp.asImageBitmap()) }
        }
        val menuOut = dev.send("optionsJSON", until = Protocol.MENU_END, idle = 300, timeout = 2000)
        Protocol.parseMenu(menuOut)?.let { menu -> _ui.update { it.copy(menu = menu) } }
    }

    private fun withDevice(block: suspend (SerialDevice) -> Unit): Unit {
        val dev = device ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { block(dev) } }
    }

    private fun log(line: String) = _ui.update {
        val merged = (it.terminal + line + "\n")
        it.copy(terminal = if (merged.length > 20_000) merged.takeLast(20_000) else merged)
    }

    override fun onCleared() {
        device?.close()
        super.onCleared()
    }
}
