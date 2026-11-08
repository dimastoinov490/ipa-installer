package com.ipainstaller

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ipainstaller.model.IpaAppInfo
import java.io.File
import java.io.FileOutputStream

object SignedIpaStorage {

    const val DIRECTORY_NAME = "signed_ipas"

    /**
     * Returns the dedicated signed_ipas directory in external app storage.
     * Accessible to file managers and persistent across app launches.
     * Falls back to internal storage if external storage is unavailable.
     */
    fun getDirectory(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        val target = if (externalDir != null && (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)) {
            File(externalDir, DIRECTORY_NAME)
        } else {
            File(context.filesDir, DIRECTORY_NAME)
        }

        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /**
     * Scans the signed_ipas folder and returns only real, valid .ipa files.
     * No placeholders or dummy files.
     */
    fun getRealIpaFiles(context: Context): List<File> {
        val dir = getDirectory(context)
        return dir.listFiles { file ->
            file.isFile && file.extension.equals("ipa", ignoreCase = true) && file.length() > 0
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Scans the signed_ipas folder and extracts real metadata for all present IPAs.
     */
    fun getRealIpaAppInfos(context: Context): List<IpaAppInfo> {
        return getRealIpaFiles(context).map { file ->
            val meta = ManifestGenerator.extractMetadata(file)
            IpaAppInfo(
                fileName = file.name,
                appTitle = meta.appTitle,
                bundleId = meta.bundleId,
                bundleVersion = meta.bundleVersion,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                file = file
            )
        }
    }

    /**
     * Imports an external .ipa file directly into the signed_ipas directory.
     */
    fun importIpa(context: Context, sourceUri: Uri): Result<File> {
        return try {
            val dir = getDirectory(context)
            var fileName = "application.ipa"

            val cursor = context.contentResolver.query(sourceUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val resolved = it.getString(nameIdx)
                        if (!resolved.isNullOrBlank()) {
                            fileName = resolved
                        }
                    }
                }
            }

            if (!fileName.endsWith(".ipa", ignoreCase = true)) {
                fileName = "$fileName.ipa"
            }

            val targetFile = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                Result.success(targetFile)
            } else {
                Result.failure(Exception("File empty after copy"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Deletes an IPA file from signed_ipas.
     */
    fun deleteIpa(context: Context, fileName: String): Boolean {
        return try {
            val file = File(getDirectory(context), fileName)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
