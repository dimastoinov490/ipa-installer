package com.ipainstaller

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ipainstaller.security.KeystoreHelper
import java.io.File
import java.io.FileOutputStream

data class ExportedSecretsBundle(
    val p12Base64: String,
    val p12FileName: String,
    val p12Password: String,
    val mobileprovisionBase64: String,
    val mobileprovisionFileName: String,
    val targetUdid: String,
    val isUdidValid: Boolean
)

object AppleCertExporter {

    private const val PREFS_NAME = "apple_secrets_prefs"
    private const val KEY_ENCRYPTED_PASSWORD = "enc_p12_password"
    private const val KEY_SAVED_UDID = "saved_udid"

    /**
     * Validates iOS device UDIDs.
     * Supports:
     * - Legacy 40-character hexadecimal UDID (e.g., iPhone X and earlier)
     * - Modern 25-character formatted UDID (8-16 hyphenated hex, e.g., 00008030-001234567890ABCD)
     */
    fun validateUdid(udid: String): Boolean {
        val trimmed = udid.trim()
        val legacyPattern = Regex("^[0-9a-fA-F]{40}$")
        val modernPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{16}$")
        return legacyPattern.matches(trimmed) || modernPattern.matches(trimmed)
    }

    /**
     * Encodes byte array directly to Base64 string without line breaks for CI/CD secrets.
     */
    fun encodeToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Reads bytes from an Android content URI.
     */
    fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves encrypted P12 password in SharedPreferences via Android Keystore.
     */
    fun saveEncryptedPassword(context: Context, password: String) {
        val encrypted = KeystoreHelper.encrypt(password)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENCRYPTED_PASSWORD, encrypted).apply()
    }

    /**
     * Retrieves decrypted P12 password from SharedPreferences via Android Keystore.
     */
    fun getDecryptedPassword(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_ENCRYPTED_PASSWORD, null) ?: return ""
        return KeystoreHelper.decrypt(encrypted)
    }

    /**
     * Saves user's iPhone UDID.
     */
    fun saveUdid(context: Context, udid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_UDID, udid.trim()).apply()
    }

    /**
     * Gets saved iPhone UDID.
     */
    fun getSavedUdid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SAVED_UDID, "") ?: ""
    }

    /**
     * Generates a sample GitHub Actions workflow snippet demonstrating how to use these secrets.
     */
    fun generateGitHubWorkflowSnippet(): String {
        return """
# Sample step in .github/workflows/ios-build.yml
- name: Install Apple Certificate and Provisioning Profile
  env:
    BUILD_CERTIFICATE_BASE64: ${'$'}{{ secrets.CERT_P12_BASE64 }}
    P12_PASSWORD: ${'$'}{{ secrets.P12_PASSWORD }}
    BUILD_PROVISION_PROFILE_BASE64: ${'$'}{{ secrets.MOBILEPROVISION_BASE64 }}
    KEYCHAIN_PASSWORD: ${'$'}{{ secrets.KEYCHAIN_PASSWORD }}
  run: |
    # 1. Create temporary keychain
    CERTIFICATE_PATH=${'$'}RUNNER_TEMP/build_certificate.p12
    PP_PATH=${'$'}RUNNER_TEMP/build_pp.mobileprovision
    KEYCHAIN_PATH=${'$'}RUNNER_TEMP/app-signing.keychain-db

    # 2. Decode secrets into files
    echo -n "${'$'}BUILD_CERTIFICATE_BASE64" | base64 --decode -o ${'$'}CERTIFICATE_PATH
    echo -n "${'$'}BUILD_PROVISION_PROFILE_BASE64" | base64 --decode -o ${'$'}PP_PATH

    # 3. Create and unlock temporary keychain
    security create-keychain -p "${'$'}KEYCHAIN_PASSWORD" ${'$'}KEYCHAIN_PATH
    security set-keychain-settings -lut 21600 ${'$'}KEYCHAIN_PATH
    security unlock-keychain -p "${'$'}KEYCHAIN_PASSWORD" ${'$'}KEYCHAIN_PATH

    # 4. Import certificate to keychain
    security import ${'$'}CERTIFICATE_PATH -P "${'$'}P12_PASSWORD" -A -t cert -f pkcs12 -k ${'$'}KEYCHAIN_PATH
    security set-key-partition-list -S apple-tool:,apple: -s -k "${'$'}KEYCHAIN_PASSWORD" ${'$'}KEYCHAIN_PATH
    security list-keychain -d user -s ${'$'}KEYCHAIN_PATH

    # 5. Place provisioning profile into Apple provisioning directory
    mkdir -p ~/Library/MobileDevice/Provisioning\ Profiles
    cp ${'$'}PP_PATH ~/Library/MobileDevice/Provisioning\ Profiles/
        """.trimIndent()
    }
}
