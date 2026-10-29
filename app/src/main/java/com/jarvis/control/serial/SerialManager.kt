package com.jarvis.control.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Enumerates USB serial devices, asks for permission and opens a [SerialDevice]
 * at 115200 8N1 — the same link JarvisControl uses over Web Serial.
 */
class SerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun listDrivers(): List<UsbSerialDriver> =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    /** Opens the first port of [driver], requesting USB permission if needed. */
    suspend fun open(driver: UsbSerialDriver): SerialDevice {
        if (!usbManager.hasPermission(driver.device)) {
            val granted = requestPermission(driver.device)
            if (!granted) throw SerialException("Permesso USB negato")
        }
        val connection: UsbDeviceConnection = usbManager.openDevice(driver.device)
            ?: throw SerialException("Impossibile aprire il dispositivo USB")
        val port = driver.ports.firstOrNull()
            ?: throw SerialException("Nessuna porta seriale sul dispositivo")
        port.open(connection)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(true) }
        return SerialDevice(port)
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    context.unregisterReceiver(this)
                    val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(ok)
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(ACTION_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            usbManager.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        }

    companion object {
        const val ACTION_USB_PERMISSION = "com.jarvis.control.USB_PERMISSION"
    }
}

class SerialException(message: String) : Exception(message)
