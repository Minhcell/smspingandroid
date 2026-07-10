package com.smsping.otg

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver

class MainActivity : AppCompatActivity() {

    // ---- Danh sách IMEI thiết bị được phép dùng app (giữ nguyên như bản laptop) ----
    private val allowedImeiList = listOf(
        "862636051970828", "862636051979746", "862636054171572", "862636054064009",
        "862636054182835", "862636054166416", "866506050985885", "862636056523887",
        "862636057265306"
    )

    private lateinit var usb: UsbAtManager
    private val handler = Handler(Looper.getMainLooper())

    private var drivers: List<UsbSerialDriver> = emptyList()
    private var myImei = ""
    private var flagCheckImei = false
    private var strDocKq = ""
    private val canBao = mutableListOf<DauVao>()
    private var pingOk = 0

    // views
    private lateinit var spDevices: Spinner
    private lateinit var btnScan: Button
    private lateinit var btnDebugUsb: Button
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var lbStatus: TextView
    private lateinit var rgMode: RadioGroup
    private lateinit var cardPing: View
    private lateinit var cardAt: View
    private lateinit var etTarget: EditText
    private lateinit var ckbBao: CheckBox
    private lateinit var etNotify: EditText
    private lateinit var btnSend: Button
    private lateinit var btnXoaBao: Button
    private lateinit var etAt: EditText
    private lateinit var ckbCr: CheckBox
    private lateinit var btnSendAt: Button
    private lateinit var etRaw: EditText
    private lateinit var tvDecode: TextView
    private lateinit var btnChkHard: Button
    private lateinit var btnTk: Button
    private lateinit var btnDecode: Button
    private lateinit var btnClr: Button
    private lateinit var btnHelp: Button
    private lateinit var btnAbout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        usb = UsbAtManager(this, ::onDataReceived, ::onUsbStatusChanged)
        usb.register()
        scanDevices()
        wireEvents()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // App đã mở sẵn, vừa có thiết bị USB mới được cắm vào -> tự quét lại danh sách modem
        if (intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            scanDevices()
            toast("Đã phát hiện thiết bị USB mới, đã quét lại danh sách")
        }
    }

    override fun onResume() {
        super.onResume()
        // Phòng trường hợp cắm modem khi app đang chạy nền, quét lại mỗi khi quay lại màn hình app
        scanDevices()
    }

    override fun onDestroy() {
        usb.disconnect()
        usb.unregister()
        super.onDestroy()
    }

    private fun bindViews() {
        spDevices = findViewById(R.id.spDevices)
        btnScan = findViewById(R.id.btnScan)
        btnDebugUsb = findViewById(R.id.btnDebugUsb)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        lbStatus = findViewById(R.id.lbStatus)
        rgMode = findViewById(R.id.rgMode)
        cardPing = findViewById(R.id.cardPing)
        cardAt = findViewById(R.id.cardAt)
        etTarget = findViewById(R.id.etTarget)
        ckbBao = findViewById(R.id.ckbBao)
        etNotify = findViewById(R.id.etNotify)
        btnSend = findViewById(R.id.btnSend)
        btnXoaBao = findViewById(R.id.btnXoaBao)
        etAt = findViewById(R.id.etAt)
        ckbCr = findViewById(R.id.ckbCr)
        btnSendAt = findViewById(R.id.btnSendAt)
        etRaw = findViewById(R.id.etRaw)
        tvDecode = findViewById(R.id.tvDecode)
        btnChkHard = findViewById(R.id.btnChkHard)
        btnTk = findViewById(R.id.btnTk)
        btnDecode = findViewById(R.id.btnDecode)
        btnClr = findViewById(R.id.btnClr)
        btnHelp = findViewById(R.id.btnHelp)
        btnAbout = findViewById(R.id.btnAbout)
        btnDisconnect.isEnabled = false
    }

    private fun wireEvents() {
        btnScan.setOnClickListener { scanDevices() }
        btnDebugUsb.setOnClickListener {
            val list = usb.listAllRawDevices()
            val text = if (list.isEmpty()) "Không có thiết bị USB nào đang cắm (kể cả không đúng loại modem)."
                        else list.joinToString("\n\n")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Danh sách USB đang cắm")
                .setMessage(text)
                .setPositiveButton("Đóng", null)
                .show()
        }

        btnConnect.setOnClickListener { onConnectClick() }
        btnDisconnect.setOnClickListener { onDisconnectClick() }

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val pingMode = checkedId == R.id.rbPing
            cardPing.visibility = if (pingMode) View.VISIBLE else View.GONE
            cardAt.visibility = if (pingMode) View.GONE else View.VISIBLE
        }

        ckbBao.setOnCheckedChangeListener { _, checked -> etNotify.isEnabled = checked }

        btnSend.setOnClickListener { onSendPingClick() }
        btnXoaBao.setOnClickListener { onXoaBaoClick() }
        btnSendAt.setOnClickListener { onSendAtClick() }
        btnChkHard.setOnClickListener { onCheckHardwareClick() }
        btnTk.setOnClickListener { onCheckTkClick() }
        btnDecode.setOnClickListener { onDecodeClick() }
        btnClr.setOnClickListener {
            etRaw.setText("")
            tvDecode.text = ""
        }
        btnHelp.setOnClickListener {
            val helpText = "- Với version OTG, bạn có thể PING cho một hoặc nhiều số ĐT đang tắt máy.\n" +
                    "Thiết bị sẽ SMS báo cho bạn khi SĐT đó online trở lại.\n" +
                    "- Trong khi chờ SMS báo hiệu, không được tắt app hoặc rút modem, không để điện thoại vào chế độ tiết kiệm pin diệt app nền.\n\n" +
                    "--- LỆNH SET SMSC THEO NHÀ MẠNG (dùng khi đổi SIM báo lỗi SMSC) ---\n" +
                    "Viettel: AT+CSCA=\"+84980200030\"\n" +
                    "Vinaphone: AT+CSCA=\"+8491020005\"\n" +
                    "Mobifone (Bắc): AT+CSCA=\"+84900000011\"\n" +
                    "Mobifone (Trung): AT+CSCA=\"+84900000017\"\n" +
                    "Mobifone (Nam): AT+CSCA=\"+84900000023\"\n" +
                    "Vietnamobile: AT+CSCA=\"+84925252525\"\n" +
                    "Gmobile: AT+CSCA=\"+84995252525\"\n\n" +
                    "Nếu gặp lỗi 'Memory full': gửi AT+CMGD=1,4 để dọn bộ nhớ SIM."
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Trợ giúp")
                .setMessage(helpText)
                .setPositiveButton("Đóng", null)
                .show()
        }
        btnAbout.setOnClickListener {
            toast("Thiết bị do tác giả và PTH thực hiện.\nBản Android OTG - port từ bản Windows Forms.")
        }
    }

    // ---------------- Quét & kết nối thiết bị USB ----------------

    private fun scanDevices() {
        drivers = usb.findAvailableDrivers()
        val names = drivers.map { "${it.device.deviceName} (VID ${it.device.vendorId})" }
            .ifEmpty { listOf("Không tìm thấy thiết bị") }
        spDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
    }

    private fun onConnectClick() {
        val driver = drivers.getOrNull(spDevices.selectedItemPosition)
        if (driver == null) {
            toast("Không tìm thấy modem. Hãy cắm SIM7600G-H qua OTG rồi bấm Quét")
            return
        }
        lbStatus.text = "Đang kết nối..."
        lbStatus.setTextColor(0xFFDD6B00.toInt())
        usb.connect(driver) { success, message ->
            if (!success) {
                toast(message)
                setUiConnected(false)
                return@connect
            }
            myImei = ""
            flagCheckImei = true
            usb.write("AT\r\n")
            handler.postDelayed({ checkModemAlive(0) }, 500)
        }
    }

    /** Tương ứng vòng lặp gửi "AT" chờ "OK" trong btnConnect_Click bản gốc. */
    private fun checkModemAlive(attempt: Int) {
        if (myImei.contains("OK") || attempt >= 10) {
            usb.write("AT+CGSN\r\n")
            handler.postDelayed({ waitForImei(0) }, 500)
            return
        }
        usb.write("AT\r\n")
        handler.postDelayed({ checkModemAlive(attempt + 1) }, 1500)
    }

    /** Chờ và thử lại tối đa ~3 giây cho tới khi nhận đủ 15 chữ số IMEI (modem qua USB có thể trả lời chậm). */
    private fun waitForImei(attempt: Int) {
        val hasImei = Regex("\\d{15}").containsMatchIn(myImei)
        if (hasImei || attempt >= 6) {
            checkImei()
            return
        }
        handler.postDelayed({ waitForImei(attempt + 1) }, 500)
    }

    // SMSC mặc định theo từng nhà mạng Việt Nam (dùng khi SIM chưa có sẵn SMSC được lưu)
    private val smscByNetwork = mapOf(
        "01" to "+84900000023", // Mobifone
        "02" to "+8491020005",  // Vinaphone
        "04" to "+84980200030"  // Viettel
    )

    private fun extractCurrentSmsc(data: String): String =
        Regex("\\+CSCA:\\s*\"([^\"]*)\"").find(data)?.groupValues?.get(1) ?: ""

    private fun detectSmscByNetwork(data: String): String? {
        val m = Regex("452-0(\\d)").find(data) ?: return null
        val mnc = m.groupValues[1].padStart(2, '0')
        return smscByNetwork[mnc]
    }

    /** Tương ứng TimerImei_Tick bản gốc: kiểm tra IMEI modem có trong danh sách cho phép không. */
    private fun checkImei() {
        // Không dựa vào việc modem có echo lại lệnh hay không (ATE0/ATE1),
        // mà tìm trực tiếp dãy 15 chữ số liên tiếp (đúng độ dài chuẩn IMEI) trong dữ liệu nhận về.
        val match = Regex("\\d{15}").find(myImei)
        if (match != null) {
            val imei = match.value
            if (!allowedImeiList.contains(imei)) {
                toast("Thiết bị của bạn chưa được đăng ký sử dụng với tác giả.\nVui lòng liên hệ tác giả!")
                usb.disconnect()
                setUiConnected(false)
                flagCheckImei = false
                return
            }
        } else {
            toast("Cổng USB chưa nhận được thiết bị! Thử kết nối lại.")
            usb.disconnect()
            setUiConnected(false)
            flagCheckImei = false
            return
        }
        usb.write("AT+CNMP=38\r\n")
        handler.postDelayed({
            // Dọn sạch bộ nhớ SMS/report cũ trên SIM, tránh lỗi "Memory full" sau nhiều lần PING
            usb.write("AT+CMGD=1,4\r\n")
            handler.postDelayed({
                usb.write("AT+CSCA?\r\n")
                handler.postDelayed({
                    val currentSmsc = extractCurrentSmsc(myImei)
                    usb.write("AT+CPSI?\r\n")
                    handler.postDelayed({
                        if (currentSmsc.isEmpty()) {
                            val autoSmsc = detectSmscByNetwork(myImei)
                            if (autoSmsc != null) {
                                usb.write("AT+CSCA=\"$autoSmsc\"\r\n")
                            } else {
                                toast("Không tự nhận diện được nhà mạng để set SMSC.\nVào chế độ AT Command, gửi: AT+CSCA=\"số SMSC nhà mạng\" trước khi PING.")
                            }
                        }
                        usb.write("AT+CNMI=1,0,0,1,0\r\n")
                        usb.write("AT+CLIP=1\r\n")
                        flagCheckImei = false
                        setUiConnected(true)
                    }, 500)
                }, 300)
            }, 1000)
        }, 300)
    }

    private fun onDisconnectClick() {
        usb.disconnect()
        setUiConnected(false)
    }

    private fun setUiConnected(connected: Boolean) {
        btnConnect.isEnabled = !connected
        btnDisconnect.isEnabled = connected
        lbStatus.text = if (connected) "Đã kết nối" else "Chưa kết nối"
        lbStatus.setTextColor(if (connected) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt())
    }

    private fun onUsbStatusChanged(connected: Boolean) {
        runOnUiThread { if (!connected) setUiConnected(false) }
    }

    // ---------------- Nhận dữ liệu từ modem ----------------

    private fun onDataReceived(text: String) {
        runOnUiThread {
            etRaw.append(text)
            etRaw.setSelection(etRaw.text.length)
            if (canBao.isNotEmpty()) strDocKq += text
            if (flagCheckImei) myImei += text
        }
    }

    // ---------------- Gửi PDU ----------------

    private fun senPdu(pdu: String) {
        usb.write("AT+CMGF=0\r\n")
        handler.postDelayed({
            usb.write("AT+CMGS=19\r\n")
            handler.postDelayed({
                usb.write(pdu)
                handler.postDelayed({ usb.write("\u001a") }, 200)
            }, 300)
        }, 200)
    }

    private fun validPhone(sdt: String): Boolean =
        sdt.length >= 10 && sdt.all { it.isDigit() } && sdt[0] == '0'

    private fun onSendPingClick() {
        if (!usb.isOpen) { toast("Connect modem trước khi sử dụng lệnh"); return }
        val sdt = etTarget.text.toString()
        if (!validPhone(sdt)) { toast("Kiểm tra lại định dạng SĐT cần PING (10 số, bắt đầu bằng 0)"); return }

        if (!ckbBao.isChecked) {
            senPdu(PduCodec.buildPingPdu(sdt))
            toast("Đã gửi PING tới $sdt")
            return
        }

        val notifyNumber = etNotify.text.toString()
        if (!validPhone(notifyNumber)) { toast("Kiểm tra lại định dạng SĐT của bạn (10 số, bắt đầu bằng 0)"); return }
        if (sdt == notifyNumber) { toast("SĐT cần PING trùng SĐT nhận báo. Nhập lại."); return }

        val entry = DauVao(
            strCmgs = "",
            sdtCanPing = sdt,
            sdtCanPingDao = PduCodec.swapDigits(sdt),
            sdtTrs = "+84" + notifyNumber.substring(1)
        )
        canBao.add(entry)
        strDocKq = ""
        senPdu(PduCodec.buildPingPdu(sdt))
        pingOk = 0
        ckbBao.isChecked = false
        handler.postDelayed({ checkPingSubmitted() }, 1500)
    }

    /** Tương ứng TimerPINGOK_Tick: kiểm tra xem lệnh submit PDU đã OK/ERROR chưa. */
    private fun checkPingSubmitted() {
        if (canBao.isEmpty()) return
        pingOk++
        val last = canBao.last()
        val marker = "00B1000B9148" + last.sdtCanPingDao + "4000AA03201008"
        val posSubmit = strDocKq.indexOf(marker)
        if (posSubmit < 0) {
            toast("Chưa xác nhận được PING, đang thử lại...")
            if (pingOk < 5) handler.postDelayed({ checkPingSubmitted() }, 1500) else canBao.removeAt(canBao.size - 1)
            return
        }
        val tail = strDocKq.substring(posSubmit)
        when {
            tail.contains("ERROR") -> {
                toast("Cuộc PING của bạn bị lỗi")
                canBao.removeAt(canBao.size - 1)
            }
            tail.contains("OK") -> {
                val cmgsIdx = strDocKq.indexOf("CMGS: ")
                if (cmgsIdx >= 0) {
                    val endIdx = strDocKq.indexOf("\r\n", cmgsIdx).let { if (it < 0) strDocKq.length else it }
                    last.strCmgs = strDocKq.substring(cmgsIdx + 6, endIdx)
                }
                toast("Đã PING số ${last.sdtCanPing} thành công. Đang chờ SĐT online lại để báo cho ${last.sdtTrs}")
                handler.postDelayed({ pollForReport() }, 5000)
            }
            pingOk < 5 -> handler.postDelayed({ checkPingSubmitted() }, 1500)
            else -> {
                toast("Đã xảy ra lỗi, hãy kiểm tra lại trong RAW CODE")
                canBao.removeAt(canBao.size - 1)
            }
        }
    }

    /** Tương ứng TimerChoKQ_Tick: định kỳ kiểm tra xem SMSC đã gửi report (CDS) chưa. */
    private fun pollForReport() {
        if (canBao.isEmpty()) return
        if (strDocKq.isNotEmpty()) {
            var catKetQua = ""
            val cdsIdx = strDocKq.indexOf("CDS:")
            if (cdsIdx >= 0) {
                val i06 = strDocKq.indexOf("069148", cdsIdx)
                val i07 = strDocKq.indexOf("079148", cdsIdx)
                if (i06 >= 0) catKetQua = strDocKq.substring(i06, (i06 + 64).coerceAtMost(strDocKq.length))
                else if (i07 >= 0) catKetQua = strDocKq.substring(i07, (i07 + 66).coerceAtMost(strDocKq.length))
            }
            val kq = PduCodec.decode(catKetQua)
            if (kq.er) {
                val match = canBao.firstOrNull { it.strCmgs == kq.mr }
                if (match != null) {
                    usb.write("AT+CMGF=1\r\n")
                    handler.postDelayed({
                        usb.write("AT+CMGS=\"${match.sdtTrs}\"\r\n")
                        handler.postDelayed({
                            val body = "PING CMGS: ${match.strCmgs}; Den ${match.sdtCanPing}; " +
                                    "SMSC nhan: ${kq.tPing}; Phat: ${kq.tReport}; ket qua: ${kq.kqSms}"
                            usb.write(body)
                            handler.postDelayed({
                                usb.write("\u001a")
                                canBao.remove(match)
                                strDocKq = ""
                                handler.postDelayed({ usb.write("AT+CMGF=0\r\n") }, 300)
                            }, 1000)
                        }, 1000)
                    }, 300)
                } else {
                    strDocKq = ""
                }
            } else {
                strDocKq = ""
            }
        }
        if (canBao.isNotEmpty()) handler.postDelayed({ pollForReport() }, 5000)
    }

    private fun onXoaBaoClick() {
        canBao.clear()
        ckbBao.isChecked = false
        etNotify.setText("")
        strDocKq = ""
        toast("Đã xoá mọi yêu cầu báo")
    }

    // ---------------- AT command thủ công ----------------

    private fun onSendAtClick() {
        if (!usb.isOpen) { toast("Connect modem trước khi sử dụng lệnh"); return }
        val cmd = etAt.text.toString()
        usb.write(if (ckbCr.isChecked) "$cmd\r" else cmd)
    }

    private fun onCheckHardwareClick() {
        if (!usb.isOpen) { toast("Connect modem trước khi sử dụng lệnh"); return }
        usb.write("AT\r\n")
        handler.postDelayed({ usb.write("AT+CSQ\r\n") }, 200)
    }

    private fun onCheckTkClick() {
        if (!usb.isOpen) { toast("Connect modem trước khi sử dụng lệnh"); return }
        usb.write("AT+CMGF=1\r\n")
        handler.postDelayed({
            usb.write("AT+CUSD=1,\"*101#\"\r\n")
            handler.postDelayed({ usb.write("AT+CMGF=0\r\n") }, 200)
        }, 200)
    }

    // ---------------- Decode thủ công đoạn text được chọn ----------------

    private fun onDecodeClick() {
        val start = etRaw.selectionStart
        val end = etRaw.selectionEnd
        if (start < 0 || end < 0 || start == end) {
            toast("Bạn phải chọn (bôi đen) đoạn text REPORT cần DECODE trước")
            return
        }
        val selected = etRaw.text.substring(minOf(start, end), maxOf(start, end))
            .trim().replace("\r\n", "").replace("\r", "").replace("\n", "")
        val kq = PduCodec.decode(selected)
        if (!kq.er) {
            toast("Chỉ chọn phần kết quả REPORT của lệnh PING để DECODE")
            return
        }
        val line = "PING SMS có CMGS: ${kq.mr}\nĐến SĐT ${kq.sdtDcPing}\n" +
                "Được SMSC nhận lúc: ${kq.tPing}, phát lúc: ${kq.tReport}\nCó kết quả: ${kq.kq}\n\n"
        tvDecode.text = SpannableStringBuilder(tvDecode.text).append(line)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
