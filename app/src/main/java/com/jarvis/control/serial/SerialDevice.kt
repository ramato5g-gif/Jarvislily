package com.jarvis.control.serial

import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * An open serial link to the device. Reading runs on a background thread
 * (usb-serial-for-android's [SerialInputOutputManager]); commands are issued
 * one at a time with the same until/idle/timeout semantics as JarvisControl.
 */
class SerialDevice(private val port: UsbSerialPort) {

    /** Raw incoming bytes, decoded loss-lessly as Latin-1 (the dump is ASCII hex). */
    private val rx = Channel<String>(Channel.UNLIMITED)
    private val cmdMutex = Mutex()

    private val ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            rx.trySend(String(data, Charsets.ISO_8859_1))
        }
        override fun onRunError(e: Exception) {
            rx.trySend("\n[Errore lettura: ${e.message}]\n")
        }
    }).apply { start() }

    fun writeRaw(text: String) {
        port.write(text.toByteArray(Charsets.ISO_8859_1), WRITE_TIMEOUT_MS)
    }

    /**
     * Sends [cmd] (a newline is appended when [newline]) and collects the reply.
     * Returns once [until] matches, or after [idle] ms of silence, or [timeout] ms.
     */
    suspend fun send(
        cmd: String,
        until: Regex? = null,
        idle: Long = 300,
        timeout: Long = 2500,
        newline: Boolean = true,
    ): String = cmdMutex.withLock {
        while (rx.tryReceive().isSuccess) { /* drain stale data */ }
        writeRaw(if (newline) cmd + "\n" else cmd)
        val sb = StringBuilder()
        withTimeoutOrNull(timeout) {
            while (true) {
                val chunk = withTimeoutOrNull(idle) { rx.receive() } ?: break
                sb.append(chunk)
                if (until != null && until.containsMatchIn(sb)) break
            }
        }
        sb.toString()
    }

    fun close() {
        runCatching { ioManager.stop() }
        runCatching { port.close() }
        rx.close()
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 2000
    }
}
