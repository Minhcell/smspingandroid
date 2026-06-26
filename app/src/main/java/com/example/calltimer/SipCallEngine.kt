package com.example.calltimer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * Gọi VoIP qua SIP (Linphone SDK).
 * Đăng ký tài khoản SIP -> đặt cuộc gọi -> đếm N giây -> tự ngắt.
 * Ghi lại SIP response (180/183/200/486/404...) để phân loại số.
 */
class SipCallEngine(
    private val context: Context,
    private val domain: String,
    private val username: String,
    private val password: String,
    private val listener: CallEventListener
) : CallEngine {

    private val handler = Handler(Looper.getMainLooper())
    private var core: Core? = null
    private var currentCall: Call? = null
    private var coreListener: CoreListenerStub? = null

    private var durationSeconds = 0
    private var number = ""
    private var finished = false
    private var startElapsed = 0L
    private var ticksLeft = 0
    private var lastSipCode = 0

    private val hangUpRunnable = Runnable {
        try {
            currentCall?.let {
                if (it.state != Call.State.End && it.state != Call.State.Released) it.terminate()
            }
            listener.onLog("Tự ngắt VoIP đúng ${durationSeconds}s.")
        } catch (e: Exception) {
            listener.onLog("Lỗi ngắt VoIP: ${e.message}")
        }
    }

    private val regTimeout = Runnable {
        if (!finished && currentCall == null) {
            listener.onLog("Hết 15s chờ đăng ký SIP — hủy.")
            finish(0.0, "Đăng ký SIP quá hạn")
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (finished) return
            ticksLeft--
            if (ticksLeft >= 0) listener.onCountdownTick(ticksLeft)
            if (ticksLeft > 0) handler.postDelayed(this, 1000)
        }
    }

    override fun startCall(number: String, durationSeconds: Int) {
        this.number = number
        this.durationSeconds = durationSeconds
        finished = false
        lastSipCode = 0
        try {
            setup()
        } catch (e: Exception) {
            listener.onLog("Lỗi khởi tạo VoIP: ${e.message}")
            finish(0.0, "Lỗi khởi tạo VoIP")
        }
    }

    private fun setup() {
        val factory = Factory.instance()
        val c = factory.createCore(null, null, context)
        core = c

        val stub = object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(
                core: Core, account: Account, state: RegistrationState?, message: String
            ) {
                listener.onLog("Đăng ký SIP: $state ($message)")
                when (state) {
                    RegistrationState.Ok -> placeInvite()
                    RegistrationState.Failed -> finish(0.0, "Đăng ký SIP thất bại")
                    else -> {}
                }
            }

            override fun onCallStateChanged(
                core: Core, call: Call, state: Call.State?, message: String
            ) {
                handleCallState(call, state, message)
            }
        }
        coreListener = stub
        c.addListener(stub)
        c.start()

        val authInfo = factory.createAuthInfo(username, null, password, null, null, domain, null)
        c.addAuthInfo(authInfo)

        val params = c.createAccountParams()
        params.setIdentityAddress(factory.createAddress("sip:$username@$domain"))
        val server = factory.createAddress("sip:$domain")
        server?.setTransport(TransportType.Udp)
        params.setServerAddress(server)
        params.setRegisterEnabled(true)

        val account = c.createAccount(params)
        c.addAccount(account)
        c.setDefaultAccount(account)

        listener.onLog("Đang đăng ký SIP tới $domain ...")
        handler.postDelayed(regTimeout, 15000)
    }

    private fun placeInvite() {
        handler.removeCallbacks(regTimeout)
        if (finished) return
        val c = core ?: return
        listener.onLog("Gọi VoIP tới $number ...")
        val remote = Factory.instance().createAddress("sip:$number@$domain")
        currentCall = if (remote != null) c.inviteAddress(remote) else c.invite(number)
        startElapsed = SystemClock.elapsedRealtime()
        ticksLeft = durationSeconds
        listener.onCountdownTick(ticksLeft)
        handler.postDelayed(tickRunnable, 1000)
        handler.postDelayed(hangUpRunnable, durationSeconds * 1000L)
    }

    private fun handleCallState(call: Call, state: Call.State?, message: String) {
        when (state) {
            Call.State.OutgoingProgress -> listener.onLog("VoIP: đang kết nối (100 Trying).")
            Call.State.OutgoingRinging -> {
                lastSipCode = 180
                listener.onLog("VoIP: đổ chuông (180 Ringing) → số ACTIVE.")
            }
            Call.State.OutgoingEarlyMedia -> {
                lastSipCode = 183
                listener.onLog("VoIP: 183 Early media.")
            }
            Call.State.Connected, Call.State.StreamsRunning -> {
                lastSipCode = 200
                listener.onLog("VoIP: bên kia đã nghe (200 OK).")
            }
            Call.State.Error -> {
                val code = call.errorInfo?.protocolCode ?: 0
                if (code != 0) lastSipCode = code
                listener.onLog("VoIP lỗi: SIP $code ${call.errorInfo?.phrase ?: message}")
            }
            else -> {}
        }
        if (state == Call.State.End || state == Call.State.Released || state == Call.State.Error) {
            val dur = (SystemClock.elapsedRealtime() - startElapsed) / 1000.0
            finish(dur, sipResultText())
        }
    }

    private fun sipResultText(): String = when (lastSipCode) {
        200 -> "Đã ngắt sau ${durationSeconds}s (200 OK — bên kia đã nghe)"
        183 -> "Ngắt sau ${durationSeconds}s (183 Early media)"
        180 -> "Ngắt sau ${durationSeconds}s (180 Ringing — ACTIVE)"
        486 -> "486 Busy — máy bận (ACTIVE)"
        480 -> "480 Unavailable — tạm không liên lạc"
        404 -> "404 Not Found — số không tồn tại (ABANDONED)"
        603 -> "603 Decline — từ chối"
        0 -> "Kết thúc (không rõ mã SIP)"
        else -> "SIP $lastSipCode"
    }

    override fun cancel() {
        try { currentCall?.terminate() } catch (_: Exception) {}
        val dur = if (startElapsed > 0) (SystemClock.elapsedRealtime() - startElapsed) / 1000.0 else 0.0
        finish(dur, "Đã hủy")
    }

    private fun finish(durationSec: Double, result: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(hangUpRunnable)
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(regTimeout)
        listener.onResult(durationSec, result)
        try {
            val c = core
            coreListener?.let { c?.removeListener(it) }
            c?.stop()
        } catch (_: Exception) {
        }
        core = null
        currentCall = null
        coreListener = null
        listener.onCallFinished()
    }
}
