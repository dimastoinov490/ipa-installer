package com.ipainstaller.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServerLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val clientIp: String,
    val statusCode: Int,
    val message: String = ""
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
