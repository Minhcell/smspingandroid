package com.smsping.otg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val ACTION_USB_PERMISSION = "com.smsping.otg.USB_PERMISSION"

/**
 * Giao tiếp trực tiếp ở tầng USB thô (bulk transfer) với modem SIM7600G-H qua OTG.
 *
 * KHÔNG dùng thư viện usb-serial-for-android nữa: thư viện đó chỉ tự ghép cặp được
 * đúng 1/6 interface của thiết bị này thành "cổng serial" (do cách tự nhận diện
 * control+data endpoint không phù hợp với thiết bị), khiến 5 interface còn lại
 * (rất có thể có cổng AT command thật) bị bỏ sót hoàn toàn.
 *
 * Cách này cho phép chọn thử TỪNG interface thô một, giống hệt việc thử từng cổng
 * COM8/9/10/11 bên Windows.
 */
class UsbAtManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit,
    private val onStatusChanged: (connected: Boolean) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private val running = AtomicBoolean(false)

    val isOpen: Boolean get() = claimedInterface != null

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

    /** Liệt kê TOÀN BỘ thiết bị USB đang cắm (không lọc), dùng để chẩn đoán. */
    fun listAllRawDevices(): List<String> =
        usbManager.deviceList.values.map { d ->
            "Tên: ${d.deviceName} | VendorID: ${d.vendorId} (0x${d.vendorId.toString(16)}) | ProductID: ${d.productId} (0x${d.productId.toString(16)}) | InterfaceCount: ${d.interfaceCount}"
        }

    /** Tìm các thiết bị khớp Vendor ID modem (SIMCom 0x1e0e / Qualcomm 0x05c6) đang cắm. */
    fun findMatchingDevices(): List<UsbDevice> =
        usbManager.deviceList.values.filter { it.vendorId == 7694 || it.vendorId == 1478 }

    fun connect(device: UsbDevice, interfaceIndex: Int, onResult: (Boolean, String) -> Unit) {
        if (!usbManager.hasPermission(device)) {
            val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            permissionCallback = { granted ->
                if (granted) openInterface(device, interfaceIndex, onResult)
                else onResult(false, "Bạn chưa cấp quyền truy cập USB")
            }
            usbManager.requestPermission(device, pi)
            return
        }
        openInterface(device, interfaceIndex, onResult)
    }

    private fun openInterface(device: UsbDevice, interfaceIndex: Int, onResult: (Boolean, String) -> Unit) {
        if (interfaceIndex !in 0 until device.interfaceCount) {
            onResult(false, "Interface số $interfaceIndex không tồn tại trên thiết bị này")
            return
        }
        val iface = device.getInterface(interfaceIndex)
        val conn = usbManager.openDevice(device)
        if (conn == null) {
            onResult(false, "Không mở được thiết bị USB")
            return
        }

        var foundIn: UsbEndpoint? = null
        var foundOut: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep else foundOut = ep
            }
        }
        if (foundIn == null || foundOut == null) {
            conn.close()
            onResult(false, "Interface $interfaceIndex không có endpoint bulk IN/OUT (không phải cổng dữ liệu, thử interface khác)")
            return
        }
        if (!conn.claimInterface(iface, true)) {
            conn.close()
            onResult(false, "Không claim được interface $interfaceIndex (có thể đang bị hệ thống Android dùng riêng)")
            return
        }

        connection = conn
        claimedInterface = iface
        epIn = foundIn
        epOut = foundOut
        running.set(true)

        Executors.newSingleThreadExecutor().submit {
            val buffer = ByteArray(4096)
            while (running.get()) {
                val len = try {
                    connection?.bulkTransfer(epIn, buffer, buffer.size, 500) ?: -1
                } catch (e: Exception) {
                    -1
                }
                if (len > 0) {
                    val text = String(buffer, 0, len, Charsets.US_ASCII)
                    onDataReceived(text)
                }
            }
        }

        onStatusChanged(true)
        onResult(true, "Kết nối thành công (interface $interfaceIndex)")
    }

    fun write(text: String) {
        val out = epOut ?: return
        val data = text.toByteArray(Charsets.US_ASCII)
        connection?.bulkTransfer(out, data, data.size, 1000)
    }

    fun disconnect() {
        running.set(false)
        try {
            claimedInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (_: Exception) {
        } finally {
            connection = null
            claimedInterface = null
            epIn = null
            epOut = null
            onStatusChanged(false)
        }
    }
}
