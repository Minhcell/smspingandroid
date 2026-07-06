package com.smsping.otg

/** Một yêu cầu PING đang chờ báo kết quả (tương ứng struct DAUVAO bên bản laptop). */
data class DauVao(
    var strCmgs: String = "",       // mã CMGS trả về khi gửi PING thành công
    var sdtCanPing: String = "",    // số điện thoại cần PING (dạng thường)
    var sdtCanPingDao: String = "", // số điện thoại đã đảo theo chuẩn PDU
    var sdtTrs: String = ""         // số điện thoại nhận SMS báo khi online lại
)

/** Kết quả decode một bản tin report CDS (tương ứng struct KETQUA bên bản laptop). */
data class KetQua(
    var er: Boolean = false,
    var mr: String = "",
    var sdtDcPing: String = "",
    var tPing: String = "",
    var tReport: String = "",
    var kq: String = "",
    var kqSms: String = ""
)
