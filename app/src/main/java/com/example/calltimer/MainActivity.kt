package com.example.calltimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), CallEventListener {

    private lateinit var modeGroup: RadioGroup
    private lateinit var rbVoip: RadioButton
    private lateinit var voipConfig: LinearLayout
    private lateinit var numberInput: EditText
    private lateinit var sipDomain: EditText
    private lateinit var sipUser: EditText
    private lateinit var sipPass: EditText
    private lateinit var secondsSeek: SeekBar
    private lateinit var secondsLabel: TextView
    private lateinit var callButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var logView: TextView
    private lateinit var historyContainer: LinearLayout

    private lateinit var historyStore: CallHistoryStore
    private var engine: CallEngine? = null

    private var currentNumber = ""
    private var currentMode = ""

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            placeCall()
        } else {
            appendLog("Chưa cấp đủ quyền cần thiết.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modeGroup = findViewById(R.id.modeGroup)
        rbVoip = findViewById(R.id.rbVoip)
        voipConfig = findViewById(R.id.voipConfig)
        numberInput = findViewById(R.id.numberInput)
        sipDomain = findViewById(R.id.sipDomain)
        sipUser = findViewById(R.id.sipUser)
        sipPass = findViewById(R.id.sipPass)
        secondsSeek = findViewById(R.id.secondsSeek)
        secondsLabel = findViewById(R.id.secondsLabel)
        callButton = findViewById(R.id.callButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        logView = findViewById(R.id.logView)
        historyContainer = findViewById(R.id.historyContainer)

        historyStore = CallHistoryStore(this)

        secondsSeek.max = 4          // 0..4 -> 1..5 giây
        secondsSeek.progress = 2     // mặc định 3 giây
        updateSecondsLabel()
        secondsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = updateSecondsLabel()
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        modeGroup.setOnCheckedChangeListener { _, _ ->
            voipConfig.visibility = if (isVoip()) View.VISIBLE else View.GONE
        }

        callButton.setOnClickListener { onCallClicked() }
        clearHistoryButton.setOnClickListener {
            historyStore.clear()
            renderHistory()
            appendLog("Đã xóa lịch sử cuộc gọi.")
        }

        renderHistory()
    }

    private fun isVoip() = rbVoip.isChecked

    private fun selectedSeconds() = secondsSeek.progress + 1

    private fun updateSecondsLabel() {
        secondsLabel.text = "Tự ngắt sau: ${selectedSeconds()} giây"
    }

    private fun onCallClicked() {
        if (numberInput.text.toString().trim().isEmpty()) {
            appendLog("Vui lòng nhập số điện thoại.")
            return
        }
        val perms = if (isVoip())
            arrayOf(Manifest.permission.RECORD_AUDIO)
        else
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) placeCall() else permLauncher.launch(perms)
    }

    private fun placeCall() {
        val number = numberInput.text.toString().trim()
        val seconds = selectedSeconds()
        currentNumber = number
        currentMode = if (isVoip()) "VoIP" else "Gọi thường"
        appendLog("────────── $currentMode tới $number ──────────")

        engine = if (isVoip()) {
            val domain = sipDomain.text.toString().trim()
            val user = sipUser.text.toString().trim()
            val pass = sipPass.text.toString()
            if (domain.isEmpty() || user.isEmpty()) {
                appendLog("Thiếu cấu hình SIP (domain/tên đăng nhập).")
                return
            }
            SipCallEngine(this, domain, user, pass, this)
        } else {
            CellularCallEngine(this, this)
        }

        callButton.isEnabled = false
        engine?.startCall(number, seconds)
    }

    // ---- CallEventListener ----

    override fun onLog(message: String) = appendLog(message)

    override fun onCountdownTick(remaining: Int) {
        runOnUiThread {
            callButton.text = if (remaining > 0) "Còn ${remaining}s" else "Đang ngắt…"
        }
    }

    override fun onResult(durationSec: Double, result: String) {
        val record = CallRecord(System.currentTimeMillis(), currentNumber, currentMode, durationSec, result)
        historyStore.add(record)
        runOnUiThread { renderHistory() }
    }

    override fun onCallFinished() {
        runOnUiThread {
            callButton.isEnabled = true
            callButton.text = "Gọi"
        }
    }

    // ---- Helpers ----

    private fun appendLog(msg: String) {
        runOnUiThread {
            val t = DateFormat.format("HH:mm:ss", Date())
            logView.append("[$t] $msg\n")
        }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()
        val items = historyStore.load().reversed()
        if (items.isEmpty()) {
            val tv = TextView(this)
            tv.text = "(chưa có lịch sử)"
            historyContainer.addView(tv)
            return
        }
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        for (r in items) {
            val tv = TextView(this)
            tv.setPadding(0, 12, 0, 12)
            tv.textSize = 13f
            tv.text = "${fmt.format(Date(r.timestamp))} • ${r.mode} • ${r.number}\n→ ${r.result}"
            historyContainer.addView(tv)
        }
    }
}
