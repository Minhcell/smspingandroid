package com.smsping.otg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

private const val ACTION_USB_PERMISSION = "com.smsping.otg.USB_PERMISSION"
private const val BAUD_RATE = 9600

/**
 * Bọc lại thư viện usb-serial-for-android để giao tiếp với modem SIM7600G-H
 * qua cổng USB-OTG. Vai trò tương đương SerialPort1 bên bản Windows Forms.
 */
class UsbAtManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit,
    private val onStatusChanged: (connected: Boolean) -> Unit
) {
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var permissionCallback: ((Boolean) -> Unit)? = null

    val isOpen: Boolean get() = port?.isOpen == true

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                permissionCallback?.invoke(granted)
                permissionCallback = null
            }
        }
    }

    fun register() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(permissionReceiver) }
    }

    /** Tìm modem SIM7600 đang cắm qua OTG (theo device_filter.xml) và mở kết nối. */
    fun findAvailableDrivers(): List<UsbSerialDriver> {
        // Bước 1: thử danh sách nhận diện mặc định của thư viện (chỉ gồm vài hãng chip phổ biến: FTDI, CP210x, CH340...)
        val standard = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (standard.isNotEmpty()) return standard

        // Bước 2: SIM7600 (SIMCom) thường KHÔNG nằm trong danh sách mặc định trên,
        // nên tự bọc thủ công dạng CDC-ACM cho thiết bị khớp đúng Vendor ID đã khai báo trong device_filter.xml
        val fallback = mutableListOf<UsbSerialDriver>()
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == 7726 || device.vendorId == 1478) { // SIMCom / Qualcomm
                try {
                    fallback.add(com.hoho.android.usbserial.driver.CdcAcmSerialDriver(device))
                } catch (_: Exception) {
                    // Thiết bị không đúng chuẩn CDC-ACM, bỏ qua
                }
            }
        }
        return fallback
    }

    fun connect(driver: UsbSerialDriver, onResult: (success: Boolean, message: String) -> Unit) {
        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            permissionCallback = { granted ->
                if (granted) openPort(driver, onResult) else onResult(false, "Bạn chưa cấp quyền truy cập USB")
            }
            usbManager.requestPermission(device, pi)
            return
        }
        openPort(driver, onResult)
    }

    private fun openPort(driver: UsbSerialDriver, onResult: (Boolean, String) -> Unit) {
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: return onResult(false, "Không mở được kết nối USB tới thiết bị")
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            ioManager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    onDataReceived(String(data, Charsets.US_ASCII))
                }
                override fun onRunError(e: Exception) {
                    onStatusChanged(false)
                }
            })
            Executors.newSingleThreadExecutor().submit(ioManager)
            onStatusChanged(true)
            onResult(true, "Kết nối thành công")
        } catch (e: Exception) {
            onResult(false, "Lỗi kết nối: ${e.message}")
        }
    }

    fun write(text: String) {
        port?.write(text.toByteArray(Charsets.US_ASCII), 1000)
    }

    fun disconnect() {
        try {
            ioManager?.stop()
            port?.close()
        } catch (_: Exception) {
        } finally {
            port = null
            ioManager = null
            onStatusChanged(false)
        }
    }
}
