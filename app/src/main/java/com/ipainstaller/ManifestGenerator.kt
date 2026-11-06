package com.ipainstaller

import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParsedIpaMetadata(
    val bundleId: String,
    val bundleVersion: String,
    val appTitle: String
)

object ManifestGenerator {

    /**
     * Generates a 100% Apple OTA-compliant XML Plist manifest string.
     * iOS itms-services protocol relies on this exact structure to download and verify
     * the application package before installation.
     */
    fun generateManifestXml(
        scheme: String = "https",
        host: String,
        port: Int,
        fileName: String,
        bundleId: String,
        bundleVersion: String,
        title: String
    ): String {
        val encodedFileName = Uri.encode(fileName)
        val ipaDownloadUrl = "$scheme://$host:$port/download/$encodedFileName"
        val iconUrl = "$scheme://$host:$port/icon.png"

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>items</key>
    <array>
        <dict>
            <key>assets</key>
            <array>
                <dict>
                    <key>kind</key>
                    <string>software-package</string>
                    <key>url</key>
                    <string>$ipaDownloadUrl</string>
                </dict>
                <dict>
                    <key>kind</key>
                    <string>display-image</string>
                    <key>needs-shine</key>
                    <false/>
                    <key>url</key>
                    <string>$iconUrl</string>
                </dict>
                <dict>
                    <key>kind</key>
                    <string>full-size-image</string>
                    <key>needs-shine</key>
                    <false/>
                    <key>url</key>
                    <string>$iconUrl</string>
                </dict>
            </array>
            <key>metadata</key>
            <dict>
                <key>bundle-identifier</key>
                <string>$bundleId</string>
                <key>bundle-version</key>
                <string>$bundleVersion</string>
                <key>kind</key>
                <string>software</string>
                <key>title</key>
                <string>$title</string>
            </dict>
        </dict>
    </array>
</dict>
</plist>""".trimIndent()
    }

    // Inspects the given .ipa file (ZIP archive) to locate and parse Payload/AppName.app/Info.plist
    // Handles XML plists and extracts strings from binary plists with robust fallbacks.
    fun extractMetadata(ipaFile: File): ParsedIpaMetadata {
        val defaultName = ipaFile.nameWithoutExtension.ifEmpty { "iOS App" }
        var bundleId: String? = null
        var bundleVersion: String? = null
        var appTitle: String? = null

        try {
            ipaFile.inputStream().buffered().use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.matches(Regex("Payload/[^/]+\\.app/Info\\.plist", RegexOption.IGNORE_CASE))) {
                            val bytes = zis.readBytes()
                            val parsed = parseInfoPlistBytes(bytes, defaultName)
                            bundleId = parsed.bundleId
                            bundleVersion = parsed.bundleVersion
                            appTitle = parsed.appTitle
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedIpaMetadata(
            bundleId = bundleId ?: "com.local.${sanitizeIdentifier(defaultName)}",
            bundleVersion = bundleVersion ?: "1.0.0",
            appTitle = appTitle ?: defaultName
        )
    }

    private fun parseInfoPlistBytes(bytes: ByteArray, fallbackTitle: String): ParsedIpaMetadata {
        val content = String(bytes, Charsets.UTF_8)
        val isXml = content.contains("<?xml") || content.contains("<plist")

        if (isXml) {
            val id = extractXmlValue(content, "CFBundleIdentifier")
            val ver = extractXmlValue(content, "CFBundleShortVersionString")
                ?: extractXmlValue(content, "CFBundleVersion")
            val title = extractXmlValue(content, "CFBundleDisplayName")
                ?: extractXmlValue(content, "CFBundleName")

            return ParsedIpaMetadata(
                bundleId = id ?: "com.local.${sanitizeIdentifier(fallbackTitle)}",
                bundleVersion = ver ?: "1.0.0",
                appTitle = title ?: fallbackTitle
            )
        } else {
            // Binary plist fallback: look for string values following key names
            val id = extractBinaryPlistString(bytes, "CFBundleIdentifier")
            val ver = extractBinaryPlistString(bytes, "CFBundleShortVersionString")
                ?: extractBinaryPlistString(bytes, "CFBundleVersion")
            val title = extractBinaryPlistString(bytes, "CFBundleDisplayName")
                ?: extractBinaryPlistString(bytes, "CFBundleName")

            return ParsedIpaMetadata(
                bundleId = id ?: "com.local.${sanitizeIdentifier(fallbackTitle)}",
                bundleVersion = ver ?: "1.0.0",
                appTitle = title ?: fallbackTitle
            )
        }
    }

    private fun extractXmlValue(xml: String, key: String): String? {
        val pattern = Pattern.compile(
            "<key>$key</key>\\s*<string>([^<]+)</string>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractBinaryPlistString(bytes: ByteArray, key: String): String? {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val index = raw.indexOf(key)
        if (index == -1) return null

        val substring = raw.substring(index + key.length, (index + key.length + 80).coerceAtMost(raw.length))
        val matcher = Regex("([a-zA-Z0-9_.-]{3,})").find(substring)
        return matcher?.value?.trim()
    }

    private fun sanitizeIdentifier(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}
