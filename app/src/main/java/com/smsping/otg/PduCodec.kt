package com.smsping.otg

/**
 * Mã hóa và giải mã bản tin PDU dùng cho tính năng PING SMS.
 * Toàn bộ logic được port 1-1 từ bản laptop (frmMain.senPDU / Decode),
 * chỉ gọn lại cách viết bằng bảng tra thay cho chuỗi if-else dài.
 */
object PduCodec {

    /**
     * Tạo chuỗi PDU class-0 "ping" gửi tới [sdt] (số điện thoại 10 số, bắt đầu bằng 0).
     * Giữ nguyên thuật toán đảo cặp số (semi-octet swap) như bản gốc.
     */
    fun buildPingPdu(sdt: String): String {
        val sdtDao = "" + sdt[2] + sdt[1] + sdt[4] + sdt[3] + sdt[6] + sdt[5] + sdt[8] + sdt[7] + "F" + sdt[9]
        return "0071000B9148" + sdtDao + "000800050401020000"
    }

    /** Số đã đảo dùng để dò trong log khi chờ xác nhận đã submit thành công. */
    fun swapDigits(sdt: String): String =
        "" + sdt[2] + sdt[1] + sdt[4] + sdt[3] + sdt[6] + sdt[5] + sdt[8] + sdt[7] + "F" + sdt[9]

    /** Bảng tra ý nghĩa mã kết quả (kq) -> (hiển thị tiếng Việt, nội dung rút gọn gửi SMS). */
    private val KQ_TABLE: Map<String, Pair<String, String>> = linkedMapOf(
        "00" to ("Số điện thoại mà bạn PING đang ONLINE." to "SDT PING ONLINE."),
        "01" to ("SMS đã gửi tới SĐT đích n SMSC không thể xác nhận việc phát." to "SMSC can not send."),
        "02" to ("SMS được thay thế bởi SMSC." to "SMS replace SMSC."),
        "03" to ("Lower End of the Reserved Values in This Sector." to "Lower End of the Reserved Values in This Sector."),
        "0F" to ("High End of the Reserved Values in This Sector." to "High End of the Reserved Values in This Sector."),
        "10" to ("Lower End of Values Specific to each SMSC." to "Lower End of Values Specific to each SMSC."),
        "1F" to ("High End of Values Specific to each SMSC in This Sector." to "High End of Values Specific to each SMSC in This Sector."),
        "20" to ("Congestion." to "Congestion."),
        "60" to ("Congestion." to "Congestion."),
        "21" to ("ĐT đích bận." to "SDT ban."),
        "61" to ("ĐT đích bận." to "SDT ban."),
        "22" to ("Không hồi đáp ĐT đích." to "SDT Khong hoi dap."),
        "62" to ("Không hồi đáp ĐT đích." to "SDT Khong hoi dap."),
        "23" to ("Service rejected." to "Service rejected."),
        "63" to ("Service rejected." to "Service rejected."),
        "24" to ("service not available." to "service not available."),
        "64" to ("service not available." to "service not available."),
        "25" to ("Lỗi ở ĐT đích." to "Loi o DT dich."),
        "65" to ("Lỗi ở ĐT đích." to "Loi o DT dich."),
        "26" to ("Lower End of the Reserved Values in This Sector." to "Lower End of the Reserved Values in This Sector."),
        "66" to ("Lower End of the Reserved Values in This Sector." to "Lower End of the Reserved Values in This Sector."),
        "2F" to ("High End of the Reserved Values in This Sector." to "High End of the Reserved Values in This Sector."),
        "6F" to ("High End of the Reserved Values in This Sector." to "High End of the Reserved Values in This Sector."),
        "30" to ("Lower End of Values Specific to each SMSC." to "Lower End of Values Specific to each SMSC."),
        "70" to ("Lower End of Values Specific to each SMSC." to "Lower End of Values Specific to each SMSC."),
        "3F" to ("High End of Values Specific to each SMSC in This Sector." to "High End of Values Specific to each SMSC in This Sector."),
        "7F" to ("High End of Values Specific to each SMSC in This Sector." to "High End of Values Specific to each SMSC in This Sector."),
        "40" to ("Remote procedure error." to "Remote procedure error."),
        "41" to ("Incompatible destination." to "Incompatible destination."),
        "42" to ("Connection rejected by ĐT đích." to "Connection rejected by DT dich."),
        "43" to ("Not obtainable." to "Not obtainable."),
        "44" to ("Quality of service not available." to "Quality of service not available."),
        "45" to ("Số điện thoại KHÔNG CÓ THỰC." to "SDT PING KHONG CO THUC."),
        "46" to ("Đã hết hạn gửi SMS. SMS Center đã xóa tin nhắn." to "Het han. SMS xoa TN"),
        "47" to ("SMS Deleted by originating ĐT đích." to "SMS Deleted by originating DT dich."),
        "48" to ("SMS Deleted by SMSC Administration." to "SMS Deleted by SMSC Administration."),
        "49" to ("SMS does not exist." to "SMS does not exist."),
        "4A" to ("Lower End of the Reserved Values in This Sector." to "Lower End of the Reserved Values in This Sector."),
        "4F" to ("High End of the Reserved Values in This Sector." to "High End of the Reserved Values in This Sector."),
        "50" to ("Lower End of Values Specific to each SMSC." to "Lower End of Values Specific to each SMSC."),
        "5F" to ("High End of Values Specific to each SMSC in This Sector." to "High End of Values Specific to each SMSC in This Sector.")
    )

    /** Ghép 2 ký tự hex tại vị trí i, i+1 của [s] (giống Conversions.ToString nối 2 char). */
    private fun hx(s: String, i: Int): String = "" + s[i] + s[i + 1]

    /**
     * Giải mã bản tin report (CDS) trả về từ SMSC. Port 1-1 từ hàm Decode() gốc,
     * hỗ trợ 4 độ dài bản tin đã gặp trong thực tế: 66, 64, 52, 54 ký tự.
     */
    fun decode(textCode: String): KetQua {
        val r = KetQua()
        when (textCode.length) {
            66 -> {
                r.er = true
                r.mr = Integer.parseInt(hx(textCode, 18), 16).toString()
                r.sdtDcPing = "0" + textCode[27] + textCode[26] + textCode[29] + textCode[28] +
                        textCode[31] + textCode[30] + textCode[33] + textCode[32] + textCode[35]
                r.tPing = "" + textCode[43] + textCode[42] + ":" + textCode[45] + textCode[44] + ":" +
                        textCode[47] + textCode[46] + ", ngay " + textCode[41] + textCode[40] + "/" +
                        textCode[39] + textCode[38] + "/20" + textCode[37] + textCode[36]
                r.tReport = "" + textCode[57] + textCode[56] + ":" + textCode[59] + textCode[58] + ":" +
                        textCode[61] + textCode[60] + ", ngay " + textCode[55] + textCode[54] + "/" +
                        textCode[53] + textCode[52] + "/20" + textCode[51] + textCode[50]
                r.kq = hx(textCode, 64)
                r.kqSms = r.kq
            }
            64 -> {
                r.er = true
                r.mr = Integer.parseInt(hx(textCode, 16), 16).toString()
                r.sdtDcPing = "0" + textCode[25] + textCode[24] + textCode[27] + textCode[26] +
                        textCode[29] + textCode[28] + textCode[31] + textCode[30] + textCode[33]
                r.tPing = "" + textCode[41] + textCode[40] + ":" + textCode[43] + textCode[42] + ":" +
                        textCode[45] + textCode[44] + ", ngay " + textCode[39] + textCode[38] + "/" +
                        textCode[37] + textCode[36] + "/20" + textCode[35] + textCode[34]
                r.tReport = "" + textCode[55] + textCode[54] + ":" + textCode[57] + textCode[56] + ":" +
                        textCode[59] + textCode[58] + ", ngay " + textCode[53] + textCode[52] + "/" +
                        textCode[51] + textCode[50] + "/20" + textCode[49] + textCode[48]
                r.kq = hx(textCode, 62)
                r.kqSms = r.kq
            }
            52 -> {
                r.er = true
                r.mr = Integer.parseInt(hx(textCode, 4), 16).toString()
                r.sdtDcPing = "0" + textCode[13] + textCode[12] + textCode[15] + textCode[14] +
                        textCode[17] + textCode[16] + textCode[19] + textCode[18] + textCode[21]
                r.tPing = "" + textCode[29] + textCode[28] + ":" + textCode[31] + textCode[30] + ":" +
                        textCode[33] + textCode[32] + ", ngay " + textCode[27] + textCode[26] + "/" +
                        textCode[25] + textCode[24] + "/20" + textCode[23] + textCode[22]
                r.tReport = "" + textCode[43] + textCode[42] + ":" + textCode[45] + textCode[44] + ":" +
                        textCode[47] + textCode[46] + ", ngay " + textCode[40] + textCode[39] + "/" +
                        textCode[38] + textCode[37] + "/20" + textCode[36] + textCode[35]
                r.kq = hx(textCode, 50)
                r.kqSms = r.kq
            }
            54 -> {
                r.er = true
                r.mr = Integer.parseInt(hx(textCode, 4), 16).toString()
                r.sdtDcPing = "0" + textCode[13] + textCode[12] + textCode[15] + textCode[14] +
                        textCode[17] + textCode[16] + textCode[19] + textCode[18] + textCode[21]
                r.tPing = "" + textCode[29] + textCode[28] + ":" + textCode[31] + textCode[30] + ":" +
                        textCode[33] + textCode[32] + ", ngay " + textCode[27] + textCode[26] + "/" +
                        textCode[25] + textCode[24] + "/20" + textCode[23] + textCode[22]
                r.tReport = "" + textCode[43] + textCode[42] + ":" + textCode[45] + textCode[44] + ":" +
                        textCode[47] + textCode[46] + ", ngay " + textCode[41] + textCode[40] + "/" +
                        textCode[39] + textCode[38] + "/20" + textCode[37] + textCode[36]
                r.kq = hx(textCode, 52)
                r.kqSms = r.kq
            }
            else -> {
                r.er = false
            }
        }
        KQ_TABLE[r.kq]?.let { (vi, sms) ->
            r.kq = vi
            r.kqSms = sms
        }
        return r
    }
}
