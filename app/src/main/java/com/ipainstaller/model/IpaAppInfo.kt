package com.ipainstaller.model

import java.io.File

data class IpaAppInfo(
    val fileName: String,
    val appTitle: String,
    val bundleId: String,
    val bundleVersion: String,
    val fileSize: Long,
    val lastModified: Long,
    val file: File
) {
    val formattedSize: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", kb)
            }
        }
}
