package com.ipainstaller

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory

data class SslBundle(
    val rootCaCert: X509Certificate,
    val serverCert: X509Certificate,
    val keyStore: KeyStore,
    val sslServerSocketFactory: SSLServerSocketFactory,
    val rootCaPem: String,
    val rootCaDer: ByteArray,
    val mobileConfig: String
)

object CertificateManager {

    private const val KEY_PASSWORD = "ipainstallerpass"
    private const val KEY_ALIAS = "ipaserver"
    private const val CA_ALIAS = "rootca"

    private val bcProvider: BouncyCastleProvider by lazy {
        ensureBouncyCastle()
        BouncyCastleProvider()
    }

    init {
        ensureBouncyCastle()
    }

    private fun ensureBouncyCastle() {
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        } catch (_: Exception) {}
        try {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (_: Exception) {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    /**
     * Generates or loads cached Root CA and Server TLS certificates tailored for iOS 13+ OTA installation.
     * Complies with Apple's strict TLS requirements:
     * - RSA 2048-bit key
     * - SHA256withRSA
     * - Validity <= 825 days
     * - ServerAuth EKU
     * - Subject Alternative Names (SAN) matching IP address and DNS name
     */
    fun getOrCreateSslBundle(context: Context, hostIp: String): SslBundle {
        ensureBouncyCastle()
        val certsDir = File(context.filesDir, "certs").apply { mkdirs() }
        val caKeyFile = File(certsDir, "ca.key")
        val caCertFile = File(certsDir, "ca.crt")

        // 1. Get or generate persistent Root CA KeyPair & Certificate
        val caKeyPair: KeyPair
        val rootCaCert: X509Certificate

        if (caKeyFile.exists() && caCertFile.exists()) {
            val loaded = loadExistingCa(caKeyFile, caCertFile)
            if (loaded != null) {
                caKeyPair = loaded.first
                rootCaCert = loaded.second
            } else {
                val generated = generateRootCa()
                caKeyPair = generated.first
                rootCaCert = generated.second
                saveCa(caKeyPair, rootCaCert, caKeyFile, caCertFile)
            }
        } else {
            val generated = generateRootCa()
            caKeyPair = generated.first
            rootCaCert = generated.second
            saveCa(caKeyPair, rootCaCert, caKeyFile, caCertFile)
        }

        // 2. Generate ephemeral Server KeyPair & Certificate signed by Root CA for this hostIp
        val serverKeyPair = generateRsaKeyPair()
        val serverCert = generateServerCertificate(serverKeyPair, caKeyPair, rootCaCert, hostIp)

        // 3. Build PKCS12 KeyStore with server cert chain
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry(CA_ALIAS, rootCaCert)
            setKeyEntry(
                KEY_ALIAS,
                serverKeyPair.private,
                KEY_PASSWORD.toCharArray(),
                arrayOf<Certificate>(serverCert, rootCaCert)
            )
        }

        // 4. Create SSLServerSocketFactory
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEY_PASSWORD.toCharArray())
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        val socketFactory = sslContext.serverSocketFactory

        val rootCaDer = rootCaCert.encoded
        val rootCaPem = certToPem(rootCaCert)
        val mobileConfig = generateMobileConfigProfile(rootCaCert)

        return SslBundle(
            rootCaCert = rootCaCert,
            serverCert = serverCert,
            keyStore = keyStore,
            sslServerSocketFactory = socketFactory,
            rootCaPem = rootCaPem,
            rootCaDer = rootCaDer,
            mobileConfig = mobileConfig
        )
    }

    private fun generateRsaKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        return keyGen.generateKeyPair()
    }

    private fun generateRootCa(): Pair<KeyPair, X509Certificate> {
        val keyPair = generateRsaKeyPair()
        val issuer = X500Name("CN=IPA Installer Local CA, O=Local OTA Installer, OU=Mobile Security, C=US")
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24 * 3600 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 800L * 24 * 3600 * 1000L) // 800 days

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer,
            keyPair.public
        )

        val extUtils = JcaX509ExtensionUtils()
        // CA = true (critical)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        // KeyUsage: CertSign and CRLSign
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature)
        )
        // Subject Key Identifier
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(keyPair.public)
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)

        val certHolder = builder.build(signer)
        val cert = JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certHolder)

        return Pair(keyPair, cert)
    }

    private fun generateServerCertificate(
        serverKeyPair: KeyPair,
        caKeyPair: KeyPair,
        caCert: X509Certificate,
        hostIp: String
    ): X509Certificate {
        val subject = X500Name("CN=$hostIp, O=Local OTA Server, OU=iOS Sideload, C=US")
        val issuer = X500Name(caCert.subjectX500Principal.name)
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 3600 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000L) // 365 days (well below 825d limit)

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            serverKeyPair.public
        )

        val extUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extUtils.createAuthorityKeyIdentifier(caCert)
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(serverKeyPair.public)
        )

        // Subject Alternative Name: IP Address & DNS Name
        val sanList = mutableListOf<GeneralName>()
        sanList.add(GeneralName(GeneralName.iPAddress, hostIp))
        sanList.add(GeneralName(GeneralName.dNSName, hostIp))
        sanList.add(GeneralName(GeneralName.dNSName, "localhost"))
        sanList.add(GeneralName(GeneralName.iPAddress, "127.0.0.1"))

        val subjectAltNames = GeneralNames(sanList.toTypedArray())
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames)

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(caKeyPair.private)

        val certHolder = builder.build(signer)
        return JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certHolder)
    }

    private fun certToPem(cert: X509Certificate): String {
        val b64 = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        val sb = StringBuilder("-----BEGIN CERTIFICATE-----\n")
        var i = 0
        while (i < b64.length) {
            val end = (i + 64).coerceAtMost(b64.length)
            sb.append(b64.substring(i, end)).append("\n")
            i = end
        }
        sb.append("-----END CERTIFICATE-----\n")
        return sb.toString()
    }

    /**
     * Generates an Apple .mobileconfig Configuration Profile containing the Root CA certificate.
     * When opened in iOS Safari, iOS natively triggers the profile download and installation prompt!
     */
    fun generateMobileConfigProfile(rootCaCert: X509Certificate): String {
        val certBase64 = Base64.encodeToString(rootCaCert.encoded, Base64.NO_WRAP)
        val profileUuid = UUID.nameUUIDFromBytes(rootCaCert.encoded).toString()
        val payloadUuid = UUID.nameUUIDFromBytes((profileUuid + "_payload").toByteArray()).toString()

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>PayloadCertificateFileName</key>
            <string>ipa-installer-ca.crt</string>
            <key>PayloadContent</key>
            <data>$certBase64</data>
            <key>PayloadDescription</key>
            <string>Adds local Root CA for wireless iOS OTA application installations</string>
            <key>PayloadDisplayName</key>
            <string>IPA Installer Local CA</string>
            <key>PayloadIdentifier</key>
            <string>com.ipainstaller.ca.$payloadUuid</string>
            <key>PayloadType</key>
            <string>com.apple.security.root</string>
            <key>PayloadUUID</key>
            <string>$payloadUuid</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
        </dict>
    </array>
    <key>PayloadDescription</key>
    <string>Local Root CA Profile to allow wireless iOS app installation via Safari (itms-services)</string>
    <key>PayloadDisplayName</key>
    <string>IPA Installer Certificate Authority</string>
    <key>PayloadIdentifier</key>
    <string>com.ipainstaller.profile.$profileUuid</string>
    <key>PayloadOrganization</key>
    <string>IPA Installer Local Server</string>
    <key>PayloadRemovalDisallowed</key>
    <false/>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadUUID</key>
    <string>$profileUuid</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>""".trimIndent()
    }

    private fun saveCa(
        keyPair: KeyPair,
        cert: X509Certificate,
        keyFile: File,
        certFile: File
    ) {
        try {
            FileOutputStream(keyFile).use { fos ->
                fos.write(keyPair.private.encoded)
            }
            FileOutputStream(certFile).use { fos ->
                fos.write(cert.encoded)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadExistingCa(
        keyFile: File,
        certFile: File
    ): Pair<KeyPair, X509Certificate>? {
        return try {
            val keyBytes = keyFile.readBytes()
            val certBytes = certFile.readBytes()

            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val privKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyBytes))

            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(certBytes.inputStream()) as X509Certificate

            val pubKey = cert.publicKey
            Pair(KeyPair(pubKey, privKey), cert)
        } catch (e: Exception) {
            null
        }
    }
}
