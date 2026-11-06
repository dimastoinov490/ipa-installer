package com.ipainstaller

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.ipainstaller.model.IpaAppInfo
import com.ipainstaller.model.ServerLog
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder

class LocalServerManager(
    private val context: Context,
    private val hostIp: String,
    private val port: Int = 8443,
    private val onLogReceived: (ServerLog) -> Unit = {}
) {

    private var serverInstance: IpaNanoServer? = null
    var isRunning: Boolean = false
        private set

    val serverUrl: String
        get() = "https://$hostIp:$port"

    val ipasDirectory: File
        get() = File(context.filesDir, "ipas").apply { mkdirs() }

    fun startServer(): Result<String> {
        return try {
            if (isRunning) {
                stopServer()
            }

            // Generate or fetch SSL bundle with dynamic SAN and Root CA
            val sslBundle = CertificateManager.getOrCreateSslBundle(context, hostIp)

            val server = IpaNanoServer(
                host = "0.0.0.0", // bind all interfaces
                port = port,
                hostIp = hostIp,
                ipasDir = ipasDirectory,
                sslBundle = sslBundle,
                onLog = { log -> onLogReceived(log) }
            )

            // Enable TLS with our generated KeyStore
            server.makeSecure(sslBundle.sslServerSocketFactory, null)
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            serverInstance = server
            isRunning = true

            onLogReceived(
                ServerLog(
                    method = "SYSTEM",
                    path = "INIT",
                    clientIp = hostIp,
                    statusCode = 200,
                    message = "HTTPS Server listening on https://$hostIp:$port"
                )
            )

            Result.success("Server started at $serverUrl")
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            onLogReceived(
                ServerLog(
                    method = "ERROR",
                    path = "INIT",
                    clientIp = hostIp,
                    statusCode = 500,
                    message = "Failed to start server: ${e.message}"
                )
            )
            Result.failure(e)
        }
    }

    fun stopServer() {
        try {
            serverInstance?.stop()
            serverInstance = null
            isRunning = false
            onLogReceived(
                ServerLog(
                    method = "SYSTEM",
                    path = "STOP",
                    clientIp = hostIp,
                    statusCode = 200,
                    message = "HTTPS Server stopped"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private class IpaNanoServer(
        host: String,
        private val port: Int,
        private val hostIp: String,
        private val ipasDir: File,
        private val sslBundle: SslBundle,
        private val onLog: (ServerLog) -> Unit
    ) : NanoHTTPD(host, port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method.name
            val clientIp = session.remoteIpAddress ?: "unknown"
            val params = session.parms

            try {
                val response = routeRequest(session, uri, params)
                onLog(
                    ServerLog(
                        method = method,
                        path = uri,
                        clientIp = clientIp,
                        statusCode = response.status.requestStatus,
                        message = "Responded with ${response.mimeType}"
                    )
                )
                return response
            } catch (e: Exception) {
                e.printStackTrace()
                onLog(
                    ServerLog(
                        method = method,
                        path = uri,
                        clientIp = clientIp,
                        statusCode = 500,
                        message = "Error: ${e.message}"
                    )
                )
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Internal Server Error: ${e.message}"
                )
            }
        }

        private fun routeRequest(
            session: IHTTPSession,
            uri: String,
            params: Map<String, String>
        ): Response {
            return when {
                // Root landing page
                uri == "/" || uri.isEmpty() -> {
                    serveHtmlIndex()
                }

                // Apple OTA Manifest Plist
                uri == "/manifest.plist" -> {
                    serveManifestPlist(params)
                }

                // Download Root CA certificate (.crt)
                uri == "/ca.crt" -> {
                    val stream = ByteArrayInputStream(sslBundle.rootCaDer)
                    val resp = newFixedLengthResponse(
                        Response.Status.OK,
                        "application/x-x509-ca-cert",
                        stream,
                        sslBundle.rootCaDer.size.toLong()
                    )
                    resp.addHeader("Content-Disposition", "attachment; filename=\"ipa-installer-ca.crt\"")
                    resp
                }

                // Download Apple Configuration Profile (.mobileconfig) for one-click iOS install
                uri == "/ca.mobileconfig" -> {
                    val bytes = sslBundle.mobileConfig.toByteArray(Charsets.UTF_8)
                    val stream = ByteArrayInputStream(bytes)
                    val resp = newFixedLengthResponse(
                        Response.Status.OK,
                        "application/x-apple-aspen-config; charset=utf-8",
                        stream,
                        bytes.size.toLong()
                    )
                    resp.addHeader("Content-Disposition", "attachment; filename=\"ipa-installer-ca.mobileconfig\"")
                    resp
                }

                // Default Icon for iOS OTA installer
                uri == "/icon.png" -> {
                    serveAppIcon()
                }

                // Download IPA package
                uri.startsWith("/download/") -> {
                    val rawFilename = uri.removePrefix("/download/")
                    val filename = URLDecoder.decode(rawFilename, "UTF-8")
                    serveIpaFile(session, filename)
                }

                uri == "/download" -> {
                    val filename = params["file"]?.let { URLDecoder.decode(it, "UTF-8") }
                    if (filename != null) {
                        serveIpaFile(session, filename)
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing file parameter")
                    }
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
                }
            }
        }

        private fun serveHtmlIndex(): Response {
            val ipaFiles = ipasDir.listFiles { file -> file.extension.equals("ipa", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val apps = ipaFiles.map { file ->
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

            val appListHtml = if (apps.isEmpty()) {
                """
                <div class="empty-state">
                    <div class="empty-icon">📦</div>
                    <h3>No IPA Files Available</h3>
                    <p>Load pre-signed .ipa files in the Android app to make them installable on your iPhone.</p>
                </div>
                """.trimIndent()
            } else {
                apps.joinToString("\n") { app ->
                    val encodedName = Uri.encode(app.fileName)
                    val manifestUrl = "https://$hostIp:$port/manifest.plist?app=$encodedName"
                    val itmsUrl = "itms-services://?action=download-manifest&url=" + Uri.encode(manifestUrl)
                    val directDownloadUrl = "/download/$encodedName"

                    """
                    <div class="app-card">
                        <div class="app-info">
                            <div class="app-icon">📱</div>
                            <div class="app-details">
                                <h3 class="app-title">${escapeHtml(app.appTitle)}</h3>
                                <div class="app-meta">
                                    <span class="badge bundle">${escapeHtml(app.bundleId)}</span>
                                    <span class="badge version">v${escapeHtml(app.bundleVersion)}</span>
                                    <span class="badge size">${app.formattedSize}</span>
                                </div>
                                <div class="file-name">${escapeHtml(app.fileName)}</div>
                            </div>
                        </div>
                        <div class="app-actions">
                            <a href="$itmsUrl" class="btn btn-install">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                                Install on iPhone
                            </a>
                            <a href="$directDownloadUrl" class="btn btn-secondary" title="Download .IPA file directly">
                                Direct .IPA
                            </a>
                        </div>
                    </div>
                    """.trimIndent()
                }
            }

            val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <title>IPA Wireless Installer</title>
    <style>
        :root {
            --bg: #0D1117;
            --card-bg: #161B22;
            --border: #30363D;
            --text-primary: #F0F6FC;
            --text-secondary: #8B949E;
            --accent-blue: #0A84FF;
            --accent-green: #30D158;
            --accent-orange: #FF9F0A;
            --font: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: var(--font);
            background-color: var(--bg);
            color: var(--text-primary);
            line-height: 1.5;
            padding: 20px 16px 60px;
            max-width: 680px;
            margin: 0 auto;
            -webkit-tap-highlight-color: transparent;
        }
        header {
            text-align: center;
            margin-bottom: 24px;
            padding: 12px 0;
        }
        .header-icon {
            font-size: 42px;
            margin-bottom: 8px;
            display: inline-block;
        }
        h1 {
            font-size: 24px;
            font-weight: 700;
            letter-spacing: -0.5px;
        }
        .subtitle {
            color: var(--text-secondary);
            font-size: 14px;
            margin-top: 4px;
        }
        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(48, 209, 88, 0.15);
            color: var(--accent-green);
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 600;
            margin-top: 10px;
        }
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--accent-green);
            box-shadow: 0 0 8px var(--accent-green);
        }
        .card {
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 18px;
            margin-bottom: 20px;
        }
        .card-title {
            font-size: 16px;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 12px;
        }
        .step-list {
            list-style: none;
            counter-reset: steps;
        }
        .step-item {
            counter-increment: steps;
            position: relative;
            padding-left: 32px;
            margin-bottom: 12px;
            font-size: 14px;
            color: var(--text-secondary);
        }
        .step-item strong {
            color: var(--text-primary);
        }
        .step-item::before {
            content: counter(steps);
            position: absolute;
            left: 0;
            top: 0;
            width: 22px;
            height: 22px;
            border-radius: 50%;
            background: var(--accent-blue);
            color: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            font-weight: 700;
        }
        .cert-buttons {
            display: flex;
            gap: 10px;
            margin-top: 14px;
            flex-wrap: wrap;
        }
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            padding: 10px 16px;
            border-radius: 10px;
            font-size: 14px;
            font-weight: 600;
            text-decoration: none;
            cursor: pointer;
            border: none;
            transition: all 0.2s ease;
        }
        .btn-primary {
            background: var(--accent-blue);
            color: #FFFFFF;
        }
        .btn-primary:active { opacity: 0.8; }
        .btn-secondary {
            background: rgba(255, 255, 255, 0.08);
            color: var(--text-primary);
            border: 1px solid var(--border);
        }
        .btn-secondary:active { background: rgba(255, 255, 255, 0.15); }
        .btn-install {
            background: #34C759;
            color: #FFFFFF;
            padding: 12px 20px;
            font-size: 15px;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(52, 199, 89, 0.3);
        }
        .btn-install:active { transform: scale(0.98); }
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 24px 0 12px;
        }
        .section-header h2 {
            font-size: 18px;
            font-weight: 600;
        }
        .app-card {
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 16px;
            padding: 16px;
            margin-bottom: 14px;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }
        .app-info {
            display: flex;
            gap: 14px;
            align-items: center;
        }
        .app-icon {
            width: 52px;
            height: 52px;
            border-radius: 12px;
            background: linear-gradient(135deg, #0A84FF, #5E5CE6);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            flex-shrink: 0;
            box-shadow: 0 4px 12px rgba(10, 132, 255, 0.2);
        }
        .app-details {
            flex: 1;
            overflow: hidden;
        }
        .app-title {
            font-size: 17px;
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .app-meta {
            display: flex;
            gap: 6px;
            margin-top: 4px;
            flex-wrap: wrap;
        }
        .badge {
            font-size: 11px;
            padding: 2px 8px;
            border-radius: 6px;
            font-weight: 500;
        }
        .badge.bundle { background: rgba(10, 132, 255, 0.15); color: #58A6FF; }
        .badge.version { background: rgba(255, 255, 255, 0.08); color: var(--text-secondary); }
        .badge.size { background: rgba(255, 159, 10, 0.15); color: var(--accent-orange); }
        .file-name {
            font-size: 11px;
            color: var(--text-secondary);
            margin-top: 4px;
            font-family: monospace;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .app-actions {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        .empty-state {
            text-align: center;
            padding: 40px 20px;
            background: var(--card-bg);
            border: 1px dashed var(--border);
            border-radius: 16px;
        }
        .empty-icon { font-size: 48px; margin-bottom: 12px; }
        .empty-state h3 { font-size: 16px; margin-bottom: 6px; }
        .empty-state p { font-size: 13px; color: var(--text-secondary); }
        .tip-box {
            background: rgba(255, 159, 10, 0.1);
            border-left: 3px solid var(--accent-orange);
            padding: 10px 12px;
            font-size: 13px;
            color: #FFAA33;
            border-radius: 0 8px 8px 0;
            margin-top: 10px;
        }
        footer {
            text-align: center;
            margin-top: 30px;
            font-size: 12px;
            color: var(--text-secondary);
        }
    </style>
</head>
<body>
    <header>
        <div class="header-icon">📲</div>
        <h1>IPA Wireless Installer</h1>
        <p class="subtitle">Direct iOS OTA Deployment via itms-services</p>
        <div class="status-pill">
            <span class="status-dot"></span>
            Host: $hostIp:$port
        </div>
    </header>

    <div class="card">
        <div class="card-title">
            <span>🛡️</span>
            <span>First Time on this iPhone? Trust SSL Certificate</span>
        </div>
        <p style="font-size: 13px; color: var(--text-secondary); margin-bottom: 12px;">
            iOS Safari requires an encrypted HTTPS connection with a trusted Certificate Authority to trigger wireless installations.
        </p>
        <ol class="step-list">
            <li class="step-item">
                Tap <strong>Download CA Profile</strong> below and choose <em>Allow</em>.
            </li>
            <li class="step-item">
                Open iPhone <strong>Settings</strong> &rarr; tap <strong>Profile Downloaded</strong> at the top &rarr; tap <strong>Install</strong>.
            </li>
            <li class="step-item">
                Open iPhone <strong>Settings</strong> &rarr; <strong>General</strong> &rarr; <strong>About</strong> &rarr; <strong>Certificate Trust Settings</strong>.
            </li>
            <li class="step-item">
                Under <em>"Enable full trust for root certificates"</em>, toggle <strong>IPA Installer Local CA</strong> ON.
            </li>
        </ol>
        <div class="cert-buttons">
            <a href="/ca.mobileconfig" class="btn btn-primary">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                Download CA Profile (.mobileconfig)
            </a>
            <a href="/ca.crt" class="btn btn-secondary">
                Direct .CRT File
            </a>
        </div>
        <div class="tip-box">
            💡 After enabling Root Trust in Settings, return to this page and tap <strong>Install on iPhone</strong> below.
        </div>
    </div>

    <div class="section-header">
        <h2>Available iOS Applications</h2>
        <span style="font-size: 13px; color: var(--text-secondary);">${apps.size} ready</span>
    </div>

    $appListHtml

    <footer>
        Local OTA Server running on Android &bull; Uses Apple itms-services protocol
    </footer>
</body>
</html>
            """.trimIndent()

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        }

        private fun serveManifestPlist(params: Map<String, String>): Response {
            val fileName = params["app"] ?: params["file"]
            if (fileName.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'app' parameter")
            }

            val decodedFileName = URLDecoder.decode(fileName, "UTF-8")
            val targetFile = File(ipasDir, decodedFileName)

            val meta = if (targetFile.exists()) {
                ManifestGenerator.extractMetadata(targetFile)
            } else {
                ParsedIpaMetadata(
                    bundleId = "com.local.${decodedFileName.replace(".ipa", "").replace("[^a-zA-Z0-9]".toRegex(), "")}",
                    bundleVersion = "1.0.0",
                    appTitle = decodedFileName.replace(".ipa", "")
                )
            }

            val xml = ManifestGenerator.generateManifestXml(
                scheme = "https",
                host = hostIp,
                port = port,
                fileName = decodedFileName,
                bundleId = meta.bundleId,
                bundleVersion = meta.bundleVersion,
                title = meta.appTitle
            )

            val resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml)
            resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            return resp
        }

        private fun serveIpaFile(session: IHTTPSession, filename: String): Response {
            val file = File(ipasDir, filename)
            if (!file.exists() || !file.isFile) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $filename")
            }

            val fileLength = file.length()
            val rangeHeader = session.headers["range"]

            val mimeType = "application/octet-stream"

            return if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Support byte range requests (essential for iOS downloads and resumes)
                try {
                    val range = rangeHeader.substring(6)
                    val parts = range.split("-")
                    val start = parts[0].toLongOrNull() ?: 0L
                    val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                        parts[1].toLongOrNull() ?: (fileLength - 1)
                    } else {
                        fileLength - 1
                    }

                    val length = (end - start + 1).coerceAtLeast(0)
                    val fis = FileInputStream(file)
                    fis.skip(start)

                    val resp = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, length)
                    resp.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    resp.addHeader("Content-Length", length.toString())
                    resp.addHeader("Accept-Ranges", "bytes")
                    resp.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                    resp
                } catch (e: Exception) {
                    val fis = FileInputStream(file)
                    val resp = newFixedLengthResponse(Response.Status.OK, mimeType, fis, fileLength)
                    resp.addHeader("Content-Length", fileLength.toString())
                    resp.addHeader("Accept-Ranges", "bytes")
                    resp.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                    resp
                }
            } else {
                val fis = FileInputStream(file)
                val resp = newFixedLengthResponse(Response.Status.OK, mimeType, fis, fileLength)
                resp.addHeader("Content-Length", fileLength.toString())
                resp.addHeader("Accept-Ranges", "bytes")
                resp.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                resp
            }
        }

        private fun serveAppIcon(): Response {
            // Generate a 128x128 solid rounded-blue icon bitmap with an install glyph
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#0A84FF")
                isAntiAlias = true
            }
            canvas.drawRoundRect(0f, 0f, 128f, 128f, 28f, 28f, paint)

            paint.color = android.graphics.Color.WHITE
            paint.strokeWidth = 8f
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeCap = android.graphics.Paint.Cap.ROUND

            // Down arrow
            canvas.drawLine(64f, 38f, 64f, 74f, paint)
            canvas.drawLine(48f, 60f, 64f, 76f, paint)
            canvas.drawLine(80f, 60f, 64f, 76f, paint)
            // Tray
            canvas.drawLine(40f, 88f, 88f, 88f, paint)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val bytes = baos.toByteArray()

            return newFixedLengthResponse(
                Response.Status.OK,
                "image/png",
                ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        }

        private fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }
    }
}
