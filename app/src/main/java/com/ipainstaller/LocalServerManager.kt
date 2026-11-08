package com.ipainstaller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    val signedIpasDirectory: File
        get() = SignedIpaStorage.getDirectory(context)

    fun startServer(): Result<String> {
        return try {
            if (isRunning) {
                stopServer()
            }

            val sslBundle = CertificateManager.getOrCreateSslBundle(context, hostIp)

            val server = IpaNanoServer(
                host = "0.0.0.0",
                port = port,
                hostIp = hostIp,
                signedIpasDir = signedIpasDirectory,
                sslBundle = sslBundle,
                context = context,
                onLog = { log -> onLogReceived(log) }
            )

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
                    message = "HTTPS Server listening on https://$hostIp:$port (scanning signed_ipas)"
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
        private val signedIpasDir: File,
        private val sslBundle: SslBundle,
        private val context: Context,
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
            val cleanUri = uri.trimEnd('/')

            return when {
                // Root landing page
                cleanUri.isEmpty() || cleanUri == "" -> {
                    serveHtmlIndex()
                }

                // Apps manifest / API for the HTML frontend
                cleanUri == "/apps/apps.json" || cleanUri == "/api/apps" || cleanUri == "/apps.json" -> {
                    serveAppsJson()
                }

                // Directory listing fallback for /apps/
                cleanUri == "/apps" -> {
                    serveAppsDirectoryListing()
                }

                // Apple OTA Manifest Plist
                cleanUri == "/manifest.plist" -> {
                    serveManifestPlist(params)
                }

                // Download Root CA certificate (.crt)
                cleanUri == "/ca.crt" -> {
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
                cleanUri == "/ca.mobileconfig" -> {
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
                cleanUri == "/icon.png" || cleanUri == "/app-icon.png" -> {
                    serveAppIcon()
                }

                // Download IPA package
                cleanUri.startsWith("/download/") -> {
                    val rawFilename = cleanUri.removePrefix("/download/")
                    val filename = URLDecoder.decode(rawFilename, "UTF-8")
                    serveIpaFile(session, filename)
                }

                cleanUri.startsWith("/apps/") && cleanUri.endsWith(".ipa", ignoreCase = true) -> {
                    val rawFilename = cleanUri.removePrefix("/apps/")
                    val filename = URLDecoder.decode(rawFilename, "UTF-8")
                    serveIpaFile(session, filename)
                }

                cleanUri == "/download" -> {
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

        private fun getRealApps(): List<IpaAppInfo> {
            val ipaFiles = signedIpasDir.listFiles { file ->
                file.isFile && file.extension.equals("ipa", ignoreCase = true) && file.length() > 0
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            return ipaFiles.map { file ->
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

        private fun serveAppsJson(): Response {
            val apps = getRealApps()

            val jsonArray = apps.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { app ->
                val encodedFileName = Uri.encode(app.fileName)
                val manifestUrl = "https://$hostIp:$port/manifest.plist?app=$encodedFileName"
                val downloadUrl = "https://$hostIp:$port/download/$encodedFileName"

                """  {
    "id": "${escapeJson(app.fileName)}",
    "name": "${escapeJson(app.appTitle)}",
    "version": "${escapeJson(app.bundleVersion)}",
    "file": "${escapeJson(app.fileName)}",
    "bundleId": "${escapeJson(app.bundleId)}",
    "category": "${escapeJson(guessCategory(app.appTitle))}",
    "description": "Bundle ID: ${escapeJson(app.bundleId)} • Размер: ${escapeJson(app.formattedSize)}",
    "manifestUrl": "$manifestUrl",
    "url": "$downloadUrl",
    "icon": "/icon.png",
    "size": "${escapeJson(app.formattedSize)}"
  }"""
            }

            val resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", jsonArray)
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp.addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
            return resp
        }

        private fun serveAppsDirectoryListing(): Response {
            val apps = getRealApps()
            val links = apps.joinToString("\n") { app ->
                """<a href="${escapeHtml(app.fileName)}">${escapeHtml(app.fileName)}</a>"""
            }
            val html = "<!DOCTYPE html><html><head><meta charset='utf-8'></head><body>$links</body></html>"
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        }

        private fun serveHtmlIndex(): Response {
            val html = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>IPA Hub</title>
<style>
:root{
  --bg:#050a1c; --panel:#0a1433; --panel2:#0d1a42; --line:#1c3170;
  --blue:#1f6bff; --blue2:#2f8bff; --glow:rgba(47,139,255,.45);
  --text:#eef3ff; --muted:#8fa3d6; --ok:#2ee06b; --warn:#ff5a5a;
}
*{box-sizing:border-box;margin:0}
html,body{min-height:100%}
body{
  font-family:-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
  color:var(--text);background:
    radial-gradient(900px 400px at 30% -10%,rgba(31,107,255,.35),transparent 70%),
    radial-gradient(700px 500px at 100% 100%,rgba(31,107,255,.18),transparent 70%),var(--bg);
  background-attachment:fixed;
}
button{font:inherit;color:inherit;cursor:pointer;border:0;background:none}
button:focus-visible,input:focus-visible,a:focus-visible{outline:2px solid var(--blue2);outline-offset:2px}
.wrap{max-width:1240px;margin:0 auto;padding:20px 20px 80px;position:relative}
svg{width:1em;height:1em;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;flex:none}

/* header */
.swoosh{position:absolute;top:0;left:0;width:100%;height:160px;pointer-events:none}
header{display:flex;align-items:center;gap:18px;flex-wrap:wrap}
.logo svg{width:56px;height:56px}
.logo{width:76px;height:76px;border-radius:22px;background:linear-gradient(160deg,#1c4fd0,#0b1f66);
  border:1px solid var(--line);box-shadow:0 0 24px var(--glow);display:grid;place-items:center}
.title h1{font-size:46px;line-height:1;font-weight:800;letter-spacing:-.5px}
.title p{color:var(--muted);margin-top:6px;font-size:18px}
.server{margin-left:auto;display:flex;align-items:center;gap:12px;padding:12px 22px;border-radius:40px;
  background:rgba(13,26,66,.7);border:1px solid var(--line)}
.dot{width:12px;height:12px;border-radius:50%;background:var(--ok);box-shadow:0 0 10px var(--ok)}
.dot.off{background:var(--warn);box-shadow:0 0 10px var(--warn)}
.server small{display:block;color:var(--muted);font-size:14px}

.header-actions{display:flex;align-items:center;gap:10px;margin-left:auto}
.lang-btn{display:flex;align-items:center;gap:8px;padding:10px 18px;border-radius:24px;border:1px solid var(--line);
  background:rgba(13,26,66,.8);font-size:15px;font-weight:700;color:var(--text);cursor:pointer;transition:all .2s}
.lang-btn:hover{border-color:var(--blue2);background:rgba(31,107,255,.2)}
.ssl-btn{display:flex;align-items:center;gap:8px;padding:10px 18px;border-radius:24px;border:1px solid var(--line);
  background:linear-gradient(180deg,#1a4fd0,#0b2a8a);font-size:15px;font-weight:700;color:#fff;text-decoration:none;
  box-shadow:0 0 14px var(--glow);transition:all .2s}
.ssl-btn:hover{filter:brightness(1.15)}

/* nav */
nav{margin-top:18px;display:flex;align-items:center;gap:6px;flex-wrap:wrap;padding:8px;border-radius:22px;
  background:rgba(10,20,51,.8);border:1px solid var(--line)}
.tab{display:flex;align-items:center;gap:10px;padding:14px 22px;border-radius:16px;font-size:17px;font-weight:600;color:#cdd8f5}
.tab svg{font-size:22px}
.tab.on{background:linear-gradient(180deg,#2f8bff,#1459e6);color:#fff;box-shadow:0 0 18px var(--glow)}
.refresh{display:flex;align-items:center;gap:8px;margin-left:auto;padding:12px 18px;border-radius:16px;border:1px solid var(--line);background:rgba(13,26,66,.7);font-weight:600;color:#cdd8f5}
.refresh:disabled{opacity:.5}
.search{margin-left:12px;flex:1 1 240px;max-width:380px;display:flex;align-items:center;gap:10px;padding:0 18px;
  height:50px;border-radius:30px;border:1px solid var(--line);background:rgba(8,16,44,.8);color:var(--blue2)}
.search input{flex:1;min-width:0;background:none;border:0;color:var(--text);font:inherit;font-size:16px}
.search input::placeholder{color:var(--muted)}
.search input:focus-visible{outline:0}

/* chips */
.chips{margin-top:16px;display:flex;gap:10px;flex-wrap:wrap}
.chip{display:flex;align-items:center;gap:8px;padding:11px 20px;border-radius:30px;border:1px solid var(--line);
  background:rgba(13,26,66,.7);font-size:16px;font-weight:600;color:#cdd8f5}
.chip.on{background:linear-gradient(180deg,#2f8bff,#1459e6);color:#fff;border-color:transparent;box-shadow:0 0 16px var(--glow)}
.chip i{font-style:normal;opacity:.7;font-weight:500}

/* layout */
.main{margin-top:20px;display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,1fr);gap:20px;align-items:start}
.main.single{grid-template-columns:1fr}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:16px}
.card{position:relative;padding:18px;border-radius:24px;background:linear-gradient(180deg,rgba(16,30,76,.85),rgba(9,18,48,.9));
  border:1px solid var(--line);display:flex;flex-direction:column;gap:6px;text-align:left;cursor:pointer;
  transition:border-color .15s,box-shadow .15s}
.card:hover{border-color:var(--blue2)}
.card.sel{border:2px solid var(--blue2);box-shadow:0 0 22px var(--glow)}
.card .icon{width:100px;height:100px;border-radius:24px;margin-bottom:6px}
.card h3{font-size:22px;font-weight:700}
.card .ver{color:var(--muted);font-size:15px}
.card .desc{color:#b7c5ec;font-size:16px;line-height:1.35;min-height:44px;
  display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.star{position:absolute;top:14px;right:14px;font-size:22px;color:var(--blue2);padding:4px}
.star.on{fill:var(--blue2)}
.star.on svg{fill:var(--blue2)}
.btn{display:flex;align-items:center;justify-content:center;gap:10px;height:48px;border-radius:24px;font-weight:700;font-size:17px;
  background:linear-gradient(180deg,#2f8bff,#1459e6);color:#fff;box-shadow:0 0 16px var(--glow);text-decoration:none;margin-top:8px}
.btn svg{font-size:20px}
.btn.big{height:62px;border-radius:32px;font-size:22px}

.icon{display:grid;place-items:center;overflow:hidden;font-weight:800;color:#fff;font-size:42px;object-fit:cover;
  background:linear-gradient(150deg,var(--c1,#2f8bff),var(--c2,#0b2a8a))}

/* detail */
.detail{position:sticky;top:16px;padding:22px;border-radius:28px;background:linear-gradient(180deg,rgba(14,28,72,.9),rgba(8,16,44,.95));
  border:1px solid var(--line);min-height:300px}
.detail .top{display:flex;justify-content:space-between;align-items:center}
.pill{display:inline-flex;align-items:center;gap:8px;padding:10px 18px;border-radius:24px;border:1px solid var(--line);background:rgba(13,26,66,.8);font-weight:600}
.heart{font-size:34px;color:var(--blue2);padding:4px}
.heart.on svg{fill:var(--blue2)}
.head{display:flex;gap:26px;align-items:center;margin:18px 0}
.head .icon{width:170px;height:170px;border-radius:40px;font-size:72px}
.head h2{font-size:44px;line-height:1.05}
.head .ver{color:var(--blue2);font-size:22px;margin:6px 0 12px}
.badge{display:inline-block;padding:7px 18px;border-radius:20px;background:#1a4fd0;font-weight:600}
.rate{margin-top:12px;color:var(--muted)}
.rate b{color:var(--text);margin:0 6px}
.stars{color:#ffb21e;letter-spacing:2px;font-size:20px}
.detail h4{display:flex;align-items:center;gap:10px;font-size:21px;margin:24px 0 10px}
.detail h4 span{width:26px;height:26px;border-radius:50%;background:var(--blue);display:grid;place-items:center;font-size:15px}
.detail p{color:#c4d0f0;line-height:1.55;font-size:17px;white-space:pre-line}
.feat{list-style:none;padding:0;display:grid;gap:8px}
.feat li{display:flex;gap:12px;color:#c4d0f0;font-size:17px}
.feat svg{color:var(--blue2);margin-top:3px;font-size:18px}
.shots{display:flex;gap:12px;overflow-x:auto;padding-bottom:6px}
.shots img{height:150px;border-radius:14px;border:1px solid var(--line)}
.meta{display:flex;gap:18px;flex-wrap:wrap;color:var(--muted);font-size:15px;margin-top:6px}
.placeholder{display:grid;place-items:center;text-align:center;color:var(--muted);min-height:260px;gap:8px;font-size:17px}

/* states */
.empty{grid-column:1/-1;text-align:center;padding:60px 20px;color:var(--muted);font-size:18px;line-height:1.6;
  border:1px dashed var(--line);border-radius:24px;background:rgba(10,20,51,.5)}
.empty code{background:rgba(31,107,255,.18);padding:2px 8px;border-radius:8px;color:#cfe0ff}
.settings{padding:24px;border-radius:24px;border:1px solid var(--line);background:rgba(10,20,51,.8);display:grid;gap:16px;max-width:640px}
.settings label{display:grid;gap:6px;color:var(--muted)}
.settings input,.settings select{height:46px;padding:0 14px;border-radius:14px;border:1px solid var(--line);background:rgba(8,16,44,.9);color:var(--text);font:inherit}

footer{position:fixed;left:0;right:0;bottom:0;display:flex;justify-content:space-between;align-items:center;padding:12px 24px;
  background:rgba(5,10,28,.92);border-top:1px solid var(--line);color:var(--muted);backdrop-filter:blur(8px)}
.online{display:flex;align-items:center;gap:8px;padding:6px 14px;border-radius:20px;border:1px solid var(--line);color:var(--ok)}
.online.off{color:var(--warn)}
.hide{display:none!important}

@media(max-width:900px){
  .main{grid-template-columns:1fr}
  .detail{position:fixed;inset:0;z-index:20;border-radius:0;overflow:auto;padding:18px 18px 90px}
  .head{flex-direction:column;align-items:flex-start}
  .title h1{font-size:36px}
  .header-actions{margin-left:0;width:100%;justify-content:space-between}
  .server{margin-left:0}
}
@media(prefers-reduced-motion:reduce){*{transition:none!important}}
</style>
</head>
<body>
<svg class="swoosh" viewBox="0 0 1240 160" preserveAspectRatio="none" aria-hidden="true"><path d="M380 0C560 150 800 120 1000 0" fill="none" stroke="#2f8bff" stroke-opacity=".55" stroke-width="2"/><path d="M420 0C600 130 820 100 1040 0" fill="none" stroke="#2f8bff" stroke-opacity=".25" stroke-width="10"/></svg>
<div class="wrap">
  <header>
    <div class="logo" aria-hidden="true"><svg viewBox="0 0 48 48"><g transform="translate(3 9) scale(1.55)" fill="#fff" stroke="none"><path d="M16.4 12.7c0-2.5 2-3.7 2.1-3.8-1.200-1.700-3-1.900-3.600-2-1.500-.2-3 .9-3.800.9-.8 0-2-.9-3.300-.9-1.700 0-3.200 1-4.100 2.500-1.800 3-.5 7.500 1.200 10 .8 1.200 1.800 2.500 3.100 2.500 1.200 0 1.700-.8 3.200-.8s1.900.8 3.200.8c1.300 0 2.200-1.200 3-2.400.9-1.400 1.300-2.700 1.300-2.800-.1 0-2.600-1-2.600-3.900zM14.200 5.200c.7-.8 1.100-2 1-3.200-1 0-2.200.7-2.900 1.500-.6.700-1.200 1.900-1 3 1.100.1 2.200-.6 2.900-1.300z"/></g><g stroke="#3aa0ff" stroke-width="2.6" fill="none" stroke-linecap="round"><path d="M31.500 15.500a6 6 0 0 1 8 0"/><path d="M28 11.500a11 11 0 0 1 15 0"/></g><circle cx="35.500" cy="19.500" r="2" fill="#3aa0ff" stroke="none"/></svg></div>
    <div class="title"><h1>IPA Hub</h1><p id="lblSubtitle">Локальный сервер приложений</p></div>
    <div class="header-actions">
      <a href="/ca.mobileconfig" class="ssl-btn" id="btnSsl" title="Установить профиль SSL для Safari"><svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg><span id="lblSsl">SSL Профиль</span></a>
      <button class="lang-btn" id="btnLang" title="Сменить язык / Switch language"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg><span id="lblLang">RU / EN</span></button>
      <div class="server"><span class="dot" id="dot"></span><div><span id="lblServer">Локальный сервер</span><small id="host"></small></div><svg viewBox="0 0 24 24" style="font-size:36px;color:#2f8bff;margin-left:10px"><path d="M2 9a15 15 0 0 1 20 0M5.500 12.500a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0"/><circle cx="12" cy="19.500" r="1.200" fill="currentColor"/></svg></div>
    </div>
  </header>

  <nav id="tabs">
    <button class="tab on" data-tab="apps"><svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg><span id="tabApps">Приложения</span></button>
    <button class="tab" data-tab="cats"><svg viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></svg><span id="tabCats">Категории</span></button>
    <button class="tab" data-tab="favs"><svg viewBox="0 0 24 24"><path d="M12 21s-8-5.2-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 5.8-8 11-8 11z"/></svg><span id="tabFavs">Избранное</span></button>
    <button class="tab" data-tab="settings"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.3.9a7 7 0 0 0-2-1.2L14.2 3h-4l-.4 2.6a7 7 0 0 0-2 1.2l-2.3-.9-2 3.4 2 1.5a7 7 0 0 0 0 2.4l-2 1.5 2 3.4 2.3-.9a7 7 0 0 0 2 1.2l.4 2.6h4l.4-2.6a7 7 0 0 0 2-1.2l2.3.9 2-3.4-2-1.5c.1-.4.1-.8.1-1.2z"/></svg><span id="tabSettings">Настройки</span></button>
    <button class="refresh" id="refresh" title="Обновить список"><svg viewBox="0 0 24 24"><path d="M20 12a8 8 0 1 1-2.5-5.8M20 4v5h-5"/></svg><span id="lblRefresh">Обновить</span></button>
    <label class="search"><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><input id="q" type="search" placeholder="Поиск приложений…" autocomplete="off"></label>
  </nav>

  <div class="chips" id="chips"></div>

  <div class="main" id="main">
    <div class="grid" id="grid"></div>
    <aside class="detail hide" id="detail"></aside>
  </div>
</div>

<footer>
  <span id="lblFooter">IPA Hub · Локальный магазин приложений</span>
  <span class="online" id="online"><span class="dot" id="dot2"></span><span id="onlineText">Онлайн</span></span>
</footer>

<script>
/* ---------- Мультиязычность (RU / EN) ---------- */
const I18N = {
  ru: {
    subtitle: 'Локальный сервер приложений (signed_ipas)',
    serverTitle: 'Локальный сервер',
    sslBtn: 'SSL Профиль',
    langLabel: 'Язык: RU',
    tabApps: 'Приложения',
    tabCats: 'Категории',
    tabFavs: 'Избранное',
    tabSettings: 'Настройки',
    refresh: 'Обновить',
    searchPlaceholder: 'Поиск приложений…',
    all: 'Все',
    install: 'Установить',
    back: 'Назад',
    descTitle: 'Описание',
    featTitle: 'Особенности',
    shotsTitle: 'Скриншоты',
    fileLabel: 'Файл',
    online: 'Онлайн',
    offline: 'Нет связи',
    emptyApps: 'В папке <code>signed_ipas</code> пока нет приложений.<br>Поместите подписанные файлы <code>.ipa</code> в эту папку на устройстве, и они сразу появятся здесь.',
    emptyFavs: 'В избранном пока пусто. Нажмите на звёздочку у приложения.',
    emptySearch: 'Ничего не найдено. Измените запрос или категорию.',
    selectApp: 'Выберите приложение,<br>чтобы увидеть подробности',
    settingsDir: 'Папка с приложениями на сервере',
    settingsMan: 'Файл описаний (необязательно)',
    settingsSave: 'Сохранить',
    footer: 'IPA Hub · Локальный магазин приложений',
    cats: ['Игры','Медиа','Утилиты','Общение','Прочее']
  },
  en: {
    subtitle: 'Local Application Server (signed_ipas)',
    serverTitle: 'Local Server',
    sslBtn: 'SSL Profile',
    langLabel: 'Lang: EN',
    tabApps: 'Apps',
    tabCats: 'Categories',
    tabFavs: 'Favorites',
    tabSettings: 'Settings',
    refresh: 'Refresh',
    searchPlaceholder: 'Search apps…',
    all: 'All',
    install: 'Install',
    back: 'Back',
    descTitle: 'Description',
    featTitle: 'Features',
    shotsTitle: 'Screenshots',
    fileLabel: 'File',
    online: 'Online',
    offline: 'Offline',
    emptyApps: 'No applications found in <code>signed_ipas</code>.<br>Place signed <code>.ipa</code> files into this directory on your Android device to install them wirelessly.',
    emptyFavs: 'Favorites list is empty. Click the star on an app card.',
    emptySearch: 'Nothing found. Try a different search query or category.',
    selectApp: 'Select an application<br>to view details',
    settingsDir: 'Apps directory on server',
    settingsMan: 'Manifest file (optional)',
    settingsSave: 'Save',
    footer: 'IPA Hub · Local Application Store',
    cats: ['Games','Media','Utilities','Social','Other']
  }
};

let currentLang = safeGet('iph_lang', 'ru');
if (currentLang !== 'ru' && currentLang !== 'en') currentLang = 'ru';

function t(k){ return I18N[currentLang][k] || k; }

function applyLanguage(){
  document.documentElement.lang = currentLang;
  $('#lblSubtitle').textContent = t('subtitle');
  $('#lblServer').textContent = t('serverTitle');
  $('#lblSsl').textContent = t('sslBtn');
  $('#lblLang').textContent = t('langLabel');
  $('#tabApps').textContent = t('tabApps');
  $('#tabCats').textContent = t('tabCats');
  $('#tabFavs').textContent = t('tabFavs');
  $('#tabSettings').textContent = t('tabSettings');
  $('#lblRefresh').textContent = t('refresh');
  $('#q').placeholder = t('searchPlaceholder');
  $('#lblFooter').textContent = t('footer');
  $('#onlineText').textContent = $('#online').classList.contains('off') ? t('offline') : t('online');
}

/* ---------- Настройки по умолчанию ---------- */
const DEFAULTS = {
  appsDir: 'apps/',
  manifest: 'apps/apps.json'
};
const CFG = Object.assign({}, DEFAULTS, safeGet('iph_cfg', {}));
const EXT = /\.ipa$/i;
const CAT_ICON = {
  Игры:'M6 12h4M8 10v4M15 13h.01M18 11h.01M7 7h10a4 4 0 0 1 4 4v3a3 3 0 0 1-5.4 1.8L15 15H9l-.6.8A3 3 0 0 1 3 14v-3a4 4 0 0 1 4-4z',
  Games:'M6 12h4M8 10v4M15 13h.01M18 11h.01M7 7h10a4 4 0 0 1 4 4v3a3 3 0 0 1-5.4 1.8L15 15H9l-.6.8A3 3 0 0 1 3 14v-3a4 4 0 0 1 4-4z',
  Медиа:'M7 4v16l13-8z',
  Media:'M7 4v16l13-8z',
  Утилиты:'M14.7 6.3a4 4 0 0 0-5 5L3 18l3 3 6.7-6.7a4 4 0 0 0 5-5l-2.5 2.5-2.2-.5-.5-2.2z',
  Utilities:'M14.7 6.3a4 4 0 0 0-5 5L3 18l3 3 6.7-6.7a4 4 0 0 0 5-5l-2.5 2.5-2.2-.5-.5-2.2z',
  Общение:'M21 12a8 8 0 0 1-11.8 7L4 20l1.2-4.6A8 8 0 1 1 21 12z',
  Social:'M21 12a8 8 0 0 1-11.8 7L4 20l1.2-4.6A8 8 0 1 1 21 12z',
  Прочее:'M5 12h.01M12 12h.01M19 12h.01',
  Other:'M5 12h.01M12 12h.01M19 12h.01'
};

let apps = [], tab = 'apps', cat = 'Все', query = '', selected = null;
let favs = new Set(safeGet('iph_favs', []));

const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function safeGet(k, d){ try{ const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; }catch(e){ return d; } }
function safeSet(k, v){ try{ localStorage.setItem(k, JSON.stringify(v)); }catch(e){} }

/* ---------- Загрузка списка приложений из signed_ipas ---------- */
function guessCategory(name){
  const n = name.toLowerCase();
  const cats = I18N[currentLang].cats;
  if (/(game|игр|puzzle|racing|arcade|play)/.test(n)) return cats[0];
  if (/(player|video|music|photo|media|tube|sound)/.test(n)) return cats[1];
  if (/(chat|messenger|telegram|whatsapp|discord|social)/.test(n)) return cats[3];
  if (/(tool|manager|helper|terminal|wifi|audit|util|clock|alarm|sign)/.test(n)) return cats[2];
  return cats[4];
}
function fromFilename(file){
  const base = file.replace(EXT, '');
  const m = base.match(/^(.*?)[-_ ]v?(\d+(?:\.\d+)*)$/i);
  const name = (m ? m[1] : base).replace(/[-_]+/g,' ').trim();
  return { id: file, name, version: m ? m[2] : '', file, base };
}
async function loadApps(){
  let list = null;
  // 1) apps/apps.json или api/apps (список реальных приложений из signed_ipas)
  try{
    const r = await fetch(CFG.manifest, {cache:'no-store'});
    if (r.ok){
      const j = await r.json();
      list = (Array.isArray(j) ? j : j.apps || []).filter(a => a.file && EXT.test(a.file)).map(a => ({...a, id: a.id || a.file || a.name}));
    }
  }catch(e){}
  // 2) иначе — листинг каталога
  if (!list){
    try{
      const r = await fetch(CFG.appsDir, {cache:'no-store'});
      if (!r.ok) throw new Error(r.status);
      const doc = new DOMParser().parseFromString(await r.text(), 'text/html');
      list = [...doc.querySelectorAll('a[href]')]
        .map(a => decodeURIComponent((a.getAttribute('href')||'').split('?')[0]))
        .filter(h => EXT.test(h))
        .map(h => h.split('/').pop())
        .filter((v,i,a) => a.indexOf(v) === i)
        .map(fromFilename);
    }catch(e){ setOnline(false); return; }
  }
  setOnline(true);
  const cats = I18N[currentLang].cats;
  apps = (list || []).map(a => {
    const f = a.file ? (/^(https?:)?\/\//.test(a.file) || a.file.startsWith('/') ? a.file : CFG.appsDir + a.file) : '';
    const base = (a.file || '').replace(EXT,'');
    return {
      ...a, url: f,
      icon: a.icon ? (a.icon.startsWith('/')||/^https?:/.test(a.icon) ? a.icon : CFG.appsDir + a.icon) : (a.base ? CFG.appsDir + a.base + '.png' : '/icon.png'),
      category: cats.includes(a.category) ? a.category : guessCategory(a.name || ''),
      version: a.version || ''
    };
  }).sort((a,b) => a.name.localeCompare(b.name, currentLang));
  if (selected && !apps.find(a => a.id === selected)) selected = null;
  render();
}
function setOnline(ok){
  ['dot','dot2'].forEach(i => $('#'+i).classList.toggle('off', !ok));
  $('#online').classList.toggle('off', !ok);
  $('#onlineText').textContent = ok ? t('online') : t('offline');
}

/* ---------- Отрисовка ---------- */
function iconHTML(a, cls){
  const h = [...(a.name||'?')].reduce((s,c) => s + c.charCodeAt(0), 0) % 360;
  const st = `--c1:hsl(${'$'}{h} 90% 58%);--c2:hsl(${'$'}{(h+50)%360} 80% 28%)`;
  const letter = esc([...(a.name||'?')][0].toUpperCase());
  const img = a.icon ? `<img class="${'$'}{cls}" src="${'$'}{esc(a.icon)}" alt="" onerror="this.outerHTML='<div class=\\'icon ${'$'}{cls}\\' style=\\'${'$'}{st}\\'>${'$'}{letter}</div>'">` : '';
  return img || `<div class="icon ${'$'}{cls}" style="${'$'}{st}">${'$'}{letter}</div>`;
}
function installHref(a){
  if (a.manifestUrl || a.plist){
    const u = new URL(a.manifestUrl || a.plist, location.href).href;
    return 'itms-services://?action=download-manifest&url=' + encodeURIComponent(u);
  }
  const manifest = location.origin + '/manifest.plist?app=' + encodeURIComponent(a.file || a.id);
  return 'itms-services://?action=download-manifest&url=' + encodeURIComponent(manifest);
}
const ICON_DL = '<svg viewBox="0 0 24 24"><path d="M12 4v11m0 0-4-4m4 4 4-4M5 20h14"/></svg>';
const ICON_STAR = '<svg viewBox="0 0 24 24"><path d="m12 3 2.8 5.8 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.3l1.1-6.2L3 9.7l6.2-.9z"/></svg>';

function filtered(){
  const q = query.trim().toLowerCase();
  const allWord = t('all');
  return apps.filter(a =>
    (tab !== 'favs' || favs.has(a.id)) &&
    (cat === 'Все' || cat === 'All' || a.category === cat) &&
    (!q || (a.name + ' ' + (a.description||'')).toLowerCase().includes(q)));
}
function render(){
  $('#host').textContent = location.host || 'локально';
  applyLanguage();
  const showApps = tab !== 'settings';
  $('#chips').classList.toggle('hide', !showApps);
  $('#grid').classList.toggle('hide', !showApps);

  const allWord = t('all');
  const cats = I18N[currentLang].cats;
  const count = c => apps.filter(a => c === allWord || c === 'Все' || c === 'All' || a.category === c).length;

  const currentCats = [allWord, ...cats];
  $('#chips').innerHTML = currentCats.map(c => {
    const isAll = c === allWord || c === 'Все' || c === 'All';
    const ic = isAll ? 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z' : (CAT_ICON[c] || 'M5 12h.01M12 12h.01M19 12h.01');
    const isSelected = (isAll && (cat === 'Все' || cat === 'All')) || cat === c;
    return `<button class="chip ${'$'}{isSelected?'on':''}" data-cat="${'$'}{c}"><svg viewBox="0 0 24 24"><path d="${'$'}{ic}"/></svg>${'$'}{c}<i>${'$'}{count(c)}</i></button>`;
  }).join('');

  if (tab === 'settings'){ renderSettings(); $('#detail').classList.add('hide'); $('#main').classList.add('single'); return; }

  const list = filtered();
  const grid = $('#grid');
  if (!apps.length){
    grid.innerHTML = `<div class="empty">${'$'}{t('emptyApps')}</div>`;
  } else if (!list.length){
    grid.innerHTML = `<div class="empty">${'$'}{tab==='favs' ? t('emptyFavs') : t('emptySearch')}</div>`;
  } else {
    grid.innerHTML = list.map(a => `
      <div class="card ${'$'}{a.id===selected?'sel':''}" data-id="${'$'}{esc(a.id)}" tabindex="0" role="button">
        <button class="star ${'$'}{favs.has(a.id)?'on':''}" data-fav="${'$'}{esc(a.id)}" aria-label="Favorite">${'$'}{ICON_STAR}</button>
        ${'$'}{iconHTML(a,'icon')}
        <h3>${'$'}{esc(a.name)}</h3>
        <div class="ver">${'$'}{a.version ? 'v'+esc(a.version) : ''}</div>
        <div class="desc">${'$'}{esc(a.description || '')}</div>
        <a class="btn" href="${'$'}{esc(installHref(a))}">${'$'}{ICON_DL}${'$'}{t('install')}</a>
      </div>`).join('');
  }
  renderDetail();
}
function renderDetail(){
  const d = $('#detail'), a = apps.find(x => x.id === selected);
  const main = $('#main');
  main.classList.toggle('single', !a && !apps.length);
  if (!a){
    d.classList.toggle('hide', !apps.length || window.innerWidth <= 900);
    d.innerHTML = `<div class="placeholder"><div>${'$'}{t('selectApp')}</div></div>`;
    return;
  }
  d.classList.remove('hide');
  const stars = n => '★'.repeat(Math.round(n)) + '☆'.repeat(5 - Math.round(n));
  d.innerHTML = `
    <div class="top">
      <button class="pill" id="back"><svg viewBox="0 0 24 24"><path d="M19 12H5m6-6-6 6 6 6"/></svg>${'$'}{t('back')}</button>
      <button class="heart ${'$'}{favs.has(a.id)?'on':''}" data-fav="${'$'}{esc(a.id)}" aria-label="Favorite"><svg viewBox="0 0 24 24"><path d="M12 21s-8-5.2-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 5.8-8 11-8 11z"/></svg></button>
    </div>
    <div class="head">
      ${'$'}{iconHTML(a,'icon')}
      <div>
        <h2>${'$'}{esc(a.name)}</h2>
        ${'$'}{a.version ? `<div class="ver">v${'$'}{esc(a.version)}</div>` : ''}
        <span class="badge">${'$'}{esc(a.category)}</span>
        ${'$'}{a.rating ? `<div class="rate"><span class="stars">${'$'}{stars(a.rating)}</span><b>${'$'}{Number(a.rating).toFixed(1)}</b>${'$'}{a.reviews!=null?`(${'$'}{esc(a.reviews)})`:''}</div>` : ''}
      </div>
    </div>
    <a class="btn big" href="${'$'}{esc(installHref(a))}">${'$'}{ICON_DL}${'$'}{t('install')}</a>
    ${'$'}{a.description ? `<h4><span>i</span>${'$'}{t('descTitle')}</h4><p>${'$'}{esc(a.long || a.description)}</p>` : ''}
    ${'$'}{Array.isArray(a.features) && a.features.length ? `<h4><span>✓</span>${'$'}{t('featTitle')}</h4><ul class="feat">${'$'}{a.features.map(f=>`<li><svg viewBox="0 0 24 24"><path d="m5 12 5 5 9-10"/></svg>${'$'}{esc(f)}</li>`).join('')}</ul>` : ''}
    ${'$'}{Array.isArray(a.screenshots) && a.screenshots.length ? `<h4><span>▣</span>${'$'}{t('shotsTitle')}</h4><div class="shots">${'$'}{a.screenshots.map(s=>`<img loading="lazy" alt="" src="${'$'}{esc(/^(https?:)?\/\/|^\//.test(s)?s:CFG.appsDir+s)}">`).join('')}</div>` : ''}
    ${'$'}{a.file ? `<div class="meta"><span>${'$'}{t('fileLabel')}: ${'$'}{esc(a.file)}</span></div>` : ''}`;
}
function renderSettings(){
  $('#grid').classList.add('hide');
  let s = $('#settingsBox');
  if (!s){ s = document.createElement('div'); s.id = 'settingsBox'; $('#main').prepend(s); }
  s.className = 'settings';
  s.innerHTML = `
    <label>${'$'}{t('settingsDir')}<input id="sDir" value="${'$'}{esc(CFG.appsDir)}"></label>
    <label>${'$'}{t('settingsMan')}<input id="sMan" value="${'$'}{esc(CFG.manifest)}"></label>
    <button class="btn" id="sSave">${'$'}{t('settingsSave')}</button>`;
  $('#sSave').onclick = () => {
    CFG.appsDir = ($('#sDir').value.trim() || DEFAULTS.appsDir).replace(/\/?${'$'}/, '/');
    CFG.manifest = $('#sMan').value.trim() || DEFAULTS.manifest;
    safeSet('iph_cfg', CFG); loadApps(); setTab('apps');
  };
}
function setTab(t){
  tab = t;
  document.querySelectorAll('.tab').forEach(b => b.classList.toggle('on', b.dataset.tab === t));
  const sb = $('#settingsBox'); if (sb) sb.remove();
  const allWord = t('all');
  if (t === 'cats') cat = (cat === 'Все' || cat === 'All') ? I18N[currentLang].cats[0] : cat;
  if (t === 'apps') cat = allWord;
  render();
}

/* ---------- Обработчики событий ---------- */
$('#tabs').addEventListener('click', e => { const b = e.target.closest('.tab'); if (b) setTab(b.dataset.tab); });
$('#chips').addEventListener('click', e => {
  const b = e.target.closest('.chip');
  if (b){
    cat = b.dataset.cat;
    render();
  }
});
$('#q').addEventListener('input', e => { query = e.target.value; if (tab === 'settings') setTab('apps'); else render(); });

$('#btnLang').addEventListener('click', () => {
  currentLang = currentLang === 'ru' ? 'en' : 'ru';
  safeSet('iph_lang', currentLang);
  cat = t('all');
  render();
});

document.addEventListener('click', e => {
  const fav = e.target.closest('[data-fav]');
  if (fav){
    e.stopPropagation();
    const id = fav.dataset.fav;
    favs.has(id) ? favs.delete(id) : favs.add(id);
    safeSet('iph_favs', [...favs]); render(); return;
  }
  if (e.target.closest('a.btn')) return;
  if (e.target.closest('#back')){ selected = null; render(); return; }
  const card = e.target.closest('.card');
  if (card){ selected = card.dataset.id; render(); }
});
document.addEventListener('keydown', e => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('card')){ e.preventDefault(); selected = e.target.dataset.id; render(); }
});
$('#refresh').addEventListener('click', async () => {
  const b = $('#refresh'); b.disabled = true; await loadApps(); b.disabled = false;
});

$('#host').textContent = location.host || 'локально';
applyLanguage();
render();
loadApps();
</script>
</body>
</html>"""

            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        }

        private fun serveManifestPlist(params: Map<String, String>): Response {
            val fileName = params["app"] ?: params["file"]
            if (fileName.isNullOrBlank()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'app' parameter")
            }

            val decodedFileName = URLDecoder.decode(fileName, "UTF-8")
            val targetFile = File(signedIpasDir, decodedFileName)

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
            val file = File(signedIpasDir, filename)
            if (!file.exists() || !file.isFile || file.length() == 0L) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $filename")
            }

            val fileLength = file.length()
            val rangeHeader = session.headers["range"]
            val mimeType = "application/octet-stream"

            return if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
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
            val heroFile = File(context.filesDir, "app_hero_icon.png")
            if (heroFile.exists() && heroFile.length() > 0) {
                return newFixedLengthResponse(Response.Status.OK, "image/png", FileInputStream(heroFile), heroFile.length())
            }

            // Fallback generation of crisp blue icon
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1f6bff")
                isAntiAlias = true
            }
            canvas.drawRoundRect(0f, 0f, 128f, 128f, 28f, 28f, paint)

            paint.color = android.graphics.Color.WHITE
            paint.strokeWidth = 8f
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeCap = android.graphics.Paint.Cap.ROUND

            canvas.drawLine(64f, 38f, 64f, 74f, paint)
            canvas.drawLine(48f, 60f, 64f, 76f, paint)
            canvas.drawLine(80f, 60f, 64f, 76f, paint)
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

        private fun guessCategory(name: String): String {
            val n = name.lowercase()
            return when {
                n.contains("game") || n.contains("игр") || n.contains("race") -> "Игры"
                n.contains("play") || n.contains("video") || n.contains("music") || n.contains("media") -> "Медиа"
                n.contains("chat") || n.contains("mess") || n.contains("tg") || n.contains("discord") -> "Общение"
                n.contains("tool") || n.contains("util") || n.contains("wifi") || n.contains("mgr") -> "Утилиты"
                else -> "Прочее"
            }
        }

        private fun escapeHtml(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        private fun escapeJson(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }
}
