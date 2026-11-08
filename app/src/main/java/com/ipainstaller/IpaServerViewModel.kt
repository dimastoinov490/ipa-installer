package com.ipainstaller

import android.content.Context
import android.net.Uri
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
    val activeQrUrl: String = "",
    val signedIpasPath: String = "",
    val currentLanguage: AppLanguage = AppLanguage.RU
)

class IpaServerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var serverManager: LocalServerManager? = null

    fun initialize(context: Context) {
        AppLocale.initialize(context)
        val dir = SignedIpaStorage.getDirectory(context)
        _uiState.update {
            it.copy(
                signedIpasPath = dir.absolutePath,
                currentLanguage = AppLocale.currentLanguage
            )
        }

        viewModelScope.launch {
            refreshNetworkAddresses(context)
            loadIpaList(context)
        }
    }

    fun toggleLanguage(context: Context) {
        val next = AppLocale.toggleLanguage(context)
        _uiState.update { it.copy(currentLanguage = next) }
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
            // Only scan real signed files from signed_ipas folder
            val items = SignedIpaStorage.getRealIpaAppInfos(context)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        ipaList = items,
                        signedIpasPath = SignedIpaStorage.getDirectory(context).absolutePath
                    )
                }
            }
        }
    }

    fun importIpaFromUri(context: Context, uri: Uri) {
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SignedIpaStorage.importIpa(context, uri)
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

    fun deleteIpa(ipa: IpaAppInfo, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SignedIpaStorage.deleteIpa(context, ipa.fileName)
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
