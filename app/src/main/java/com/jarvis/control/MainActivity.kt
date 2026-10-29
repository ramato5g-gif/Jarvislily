package com.jarvis.control

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.jarvis.control.ui.JarvisScreen
import com.jarvis.control.ui.JarvisTheme

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { JarvisTheme { JarvisScreen(vm) } }
        maybeAutoConnect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoConnect(intent)
    }

    /** When launched by plugging the T-Embed in, connect to that device automatically. */
    private fun maybeAutoConnect(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val attached: UsbDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        val drivers = vm.listDrivers()
        val match = drivers.firstOrNull { it.device.deviceId == attached?.deviceId }
            ?: drivers.firstOrNull()
        match?.let { vm.connect(it) }
    }
}
