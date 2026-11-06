package com.ipainstaller

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipainstaller.model.IpaAppInfo
import com.ipainstaller.model.ServerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class ServerUiState(
    val serverState: ServerState = ServerState.STOPPED,
    val errorMessage: String? = null,
    val selectedAddress: NetworkAddress? = null,
    val availableAddresses: List<NetworkAddress> = emptyList(),
    val port: Int = 8443,
    val ipaList: List<IpaAppInfo> = emptyList(),
    val serverLogs: List<ServerLog> = emptyList(),
    val isImporting: Boolean = false,
    val showQrDialog: Boolean = false,
    val showInstructionsDialog: Boolean = false,
    val activeQrUrl: String = ""
)

class IpaServerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var serverManager: LocalServerManager? = null

    fun initialize(context: Context) {
        viewModelScope.launch {
            refreshNetworkAddresses(context)
            loadIpaList(context)

            // Auto-create a sample test IPA if none exist so user can immediately test!
            val ipasDir = File(context.filesDir, "ipas")
            val existing = ipasDir.listFiles { f -> f.extension.equals("ipa", ignoreCase = true) }
            if (existing == null || existing.isEmpty()) {
                SampleIpaBuilder.createSampleIpa(ipasDir, "DemoApp", "com.example.demoapp")
                loadIpaList(context)
            }
        }
    }

    fun refreshNetworkAddresses(context: Context) {
        val list = NetworkUtils.getAvailableIpAddresses(context)
        _uiState.update { current ->
            val selected = current.selectedAddress ?: list.firstOrNull { it.isWifiOrAp } ?: list.firstOrNull()
            current.copy(
                availableAddresses = list,
                selectedAddress = selected
            )
        }
    }

    fun selectAddress(address: NetworkAddress, context: Context) {
        val wasRunning = _uiState.value.serverState == ServerState.RUNNING
        _uiState.update { it.copy(selectedAddress = address) }
        if (wasRunning) {
            startServer(context)
        }
    }

    fun toggleServer(context: Context) {
        if (_uiState.value.serverState == ServerState.RUNNING) {
            stopServer()
        } else {
            startServer(context)
        }
    }

    fun startServer(context: Context) {
        val hostIp = _uiState.value.selectedAddress?.ip ?: NetworkUtils.getPrimaryIpAddress(context)
        val port = _uiState.value.port

        _uiState.update { it.copy(serverState = ServerState.STARTING, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            serverManager?.stopServer()

            val manager = LocalServerManager(
                context = context.applicationContext,
                hostIp = hostIp,
                port = port,
                onLogReceived = { log ->
                    _uiState.update { state ->
                        state.copy(serverLogs = (listOf(log) + state.serverLogs).take(100))
                    }
                }
            )

            val result = manager.startServer()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    serverManager = manager
                    _uiState.update {
                        it.copy(
                            serverState = ServerState.RUNNING,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            serverState = ServerState.ERROR,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to start server"
                        )
                    }
                }
            }
        }
    }

    fun stopServer() {
        serverManager?.stopServer()
        serverManager = null
        _uiState.update { it.copy(serverState = ServerState.STOPPED) }
    }

    fun loadIpaList(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ipasDir = File(context.filesDir, "ipas").apply { mkdirs() }
            val files = ipasDir.listFiles { file -> file.extension.equals("ipa", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val items = files.map { file ->
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

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(ipaList = items) }
            }
        }
    }

    fun importIpaFromUri(context: Context, uri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ipasDir = File(context.filesDir, "ipas").apply { mkdirs() }
                var fileName = "ImportedApp.ipa"

                // Query display name
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val resolvedName = it.getString(nameIndex)
                            if (!resolvedName.isNullOrBlank()) {
                                fileName = resolvedName
                            }
                        }
                    }
                }

                if (!fileName.endsWith(".ipa", ignoreCase = true)) {
                    fileName = "$fileName.ipa"
                }

                val targetFile = File(ipasDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                loadIpaList(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isImporting = false) }
                }
            }
        }
    }

    fun createSampleIpa(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ipasDir = File(context.filesDir, "ipas").apply { mkdirs() }
            val count = _uiState.value.ipaList.size + 1
            SampleIpaBuilder.createSampleIpa(
                destinationDir = ipasDir,
                appTitle = "SampleApp$count",
                bundleId = "com.sample.app$count"
            )
            loadIpaList(context)
        }
    }

    fun deleteIpa(ipa: IpaAppInfo, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (ipa.file.exists()) {
                    ipa.file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadIpaList(context)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(serverLogs = emptyList()) }
    }

    fun showQrDialog(url: String) {
        _uiState.update { it.copy(showQrDialog = true, activeQrUrl = url) }
    }

    fun dismissQrDialog() {
        _uiState.update { it.copy(showQrDialog = false) }
    }

    fun showInstructions(show: Boolean) {
        _uiState.update { it.copy(showInstructionsDialog = show) }
    }

    override fun onCleared() {
        super.onCleared()
        serverManager?.stopServer()
    }
}
