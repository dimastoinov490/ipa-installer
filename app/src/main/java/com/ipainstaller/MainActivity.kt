package com.ipainstaller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipainstaller.ui.components.InstructionsDialog
import com.ipainstaller.ui.components.IpaCard
import com.ipainstaller.ui.components.QrCodeDialog
import com.ipainstaller.ui.components.ServerLogsView
import com.ipainstaller.ui.screens.AppleIntegrationScreen
import com.ipainstaller.ui.theme.AppleBlue
import com.ipainstaller.ui.theme.AppleGreen
import com.ipainstaller.ui.theme.AppleOrange
import com.ipainstaller.ui.theme.ApplePurple
import com.ipainstaller.ui.theme.AppleRed
import com.ipainstaller.ui.theme.IPAInstallerTheme

enum class Screen {
    SERVER,
    APPLE_INTEGRATION
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IPAInstallerTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: IpaServerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.SERVER) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    if (currentScreen == Screen.APPLE_INTEGRATION) {
        BackHandler {
            currentScreen = Screen.SERVER
        }
        AppleIntegrationScreen(
            onNavigateBack = { currentScreen = Screen.SERVER }
        )
        return
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importIpaFromUri(context, uri)
        }
    }

    val serverUrl = "https://${uiState.selectedAddress?.ip ?: "127.0.0.1"}:${uiState.port}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "A-installer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AppleBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "OTA HTTPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppleBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Language toggle button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { viewModel.toggleLanguage(context) }
                            .testTag("toggle_lang_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                modifier = Modifier.size(16.dp),
                                tint = AppleBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.currentLanguage.code.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Apple Developer CI/CD secrets exporter
                    IconButton(
                        onClick = { currentScreen = Screen.APPLE_INTEGRATION },
                        modifier = Modifier.testTag("open_apple_integration_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Apple & GitHub CI/CD",
                            tint = ApplePurple
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            viewModel.refreshNetworkAddresses(context)
                            viewModel.loadIpaList(context)
                        },
                        modifier = Modifier.testTag("refresh_network_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить"
                        )
                    }

                    // Instructions
                    IconButton(
                        onClick = { viewModel.showInstructions(true) },
                        modifier = Modifier.testTag("open_instructions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Инструкция"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========================================================
            // ШАГ 1: Сервер и Сеть
            // ========================================================
            item {
                StepOneServerCard(
                    uiState = uiState,
                    serverUrl = serverUrl,
                    onToggleServer = { viewModel.toggleServer(context) },
                    onSelectAddress = { addr -> viewModel.selectAddress(addr, context) },
                    onShowQr = { url -> viewModel.showQrDialog(url) }
                )
            }

            // ========================================================
            // ШАГ 2: Управление приложениями (signed_ipas)
            // ========================================================
            item {
                StepTwoAppsHeaderCard(
                    directoryPath = uiState.signedIpasPath,
                    ipaCount = uiState.ipaList.size,
                    onImportClick = { filePickerLauncher.launch("*/*") },
                    onRescan = { viewModel.loadIpaList(context) }
                )
            }

            // Список реально подписанных файлов
            if (uiState.ipaList.isEmpty()) {
                item {
                    EmptyRealIpaState(
                        signedIpasPath = uiState.signedIpasPath,
                        onImport = { filePickerLauncher.launch("*/*") }
                    )
                }
            } else {
                items(uiState.ipaList, key = { it.fileName }) { ipa ->
                    IpaCard(
                        ipa = ipa,
                        serverUrl = serverUrl,
                        isServerRunning = uiState.serverState == ServerState.RUNNING,
                        onShowQr = { itmsUrl -> viewModel.showQrDialog(itmsUrl) },
                        onDelete = { viewModel.deleteIpa(ipa, context) }
                    )
                }
            }

            // ========================================================
            // ШАГ 3: Установка на iPhone
            // ========================================================
            item {
                StepThreeInstallationCard(
                    serverUrl = serverUrl,
                    isServerRunning = uiState.serverState == ServerState.RUNNING,
                    onOpenInstructions = { viewModel.showInstructions(true) },
                    onShowCaQr = {
                        val caUrl = "$serverUrl/ca.mobileconfig"
                        viewModel.showQrDialog(caUrl)
                    }
                )
            }

            // Журнал трафика сервера
            item {
                ServerLogsView(
                    logs = uiState.serverLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // QR Code Dialog
    if (uiState.showQrDialog) {
        QrCodeDialog(
            url = uiState.activeQrUrl,
            onDismiss = { viewModel.dismissQrDialog() }
        )
    }

    // Instructions Dialog
    if (uiState.showInstructionsDialog) {
        InstructionsDialog(
            serverUrl = serverUrl,
            onDismiss = { viewModel.showInstructions(false) }
        )
    }
}

/**
 * ШАГ 1: Карточка сервера и выбора сети Wi-Fi
 */
@Composable
private fun StepOneServerCard(
    uiState: ServerUiState,
    serverUrl: String,
    onToggleServer: () -> Unit,
    onSelectAddress: (NetworkAddress) -> Unit,
    onShowQr: (String) -> Unit
) {
    val context = LocalContext.current
    var isDropdownOpen by remember { mutableStateOf(false) }

    val isRunning = uiState.serverState == ServerState.RUNNING
    val statusColor by animateColorAsState(
        when (uiState.serverState) {
            ServerState.RUNNING -> AppleGreen
            ServerState.STARTING -> AppleOrange
            ServerState.ERROR -> AppleRed
            ServerState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "status_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_1_server_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Заголовок Шага 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppleBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Шаг 1: Сервер и Сеть",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (uiState.serverState) {
                                    ServerState.RUNNING -> "Сервер работает (HTTPS онлайн)"
                                    ServerState.STARTING -> "Запуск TLS сервера..."
                                    ServerState.ERROR -> "Ошибка запуска сервера"
                                    ServerState.STOPPED -> "Сервер остановлен"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Выбор сетевого интерфейса (Wi-Fi)
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { isDropdownOpen = true }
                            .testTag("select_network_ip_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppleBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.selectedAddress?.ip ?: "Нет IP",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDropdownOpen,
                        onDismissRequest = { isDropdownOpen = false }
                    ) {
                        uiState.availableAddresses.forEach { addr ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(addr.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            addr.ip,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectAddress(addr)
                                    isDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = AppleRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Панель адреса сервера при активном состоянии
            AnimatedVisibility(visible = isRunning) {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Адрес для Safari на iPhone:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Server URL", serverUrl))
                                        Toast.makeText(context, "Адрес скопирован в буфер!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_server_url_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Скопировать",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onShowQr(serverUrl) },
                                    modifier = Modifier.testTag("show_server_qr_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "QR-код",
                                        tint = AppleBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("open_browser_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "Открыть в браузере",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Большая главная кнопка запуска / остановки сервера
            Button(
                onClick = onToggleServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("toggle_server_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) AppleRed else AppleGreen
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "Остановить сервер" else "Запустить сервер",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * ШАГ 2: Карточка управления приложениями и выбора .ipa
 */
@Composable
private fun StepTwoAppsHeaderCard(
    directoryPath: String,
    ipaCount: Int,
    onImportClick: () -> Unit,
    onRescan: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_2_apps_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Заголовок Шага 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ApplePurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Шаг 2: Управление приложениями",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Папка signed_ipas (найдено файлов: $ipaCount)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRescan,
                    modifier = Modifier.testTag("rescan_ipas_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить список",
                        tint = AppleBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Кнопка импорта IPA файла
            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_ipa_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Выбрать .IPA файл для установки",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Отображение пути к папке signed_ipas
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ApplePurple
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = directoryPath.ifEmpty { "signed_ipas" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("signed_ipas path", directoryPath))
                            Toast.makeText(context, "Путь к папке скопирован!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Скопировать путь",
                            tint = ApplePurple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Пустое состояние при отсутствии файлов в signed_ipas
 */
@Composable
private fun EmptyRealIpaState(
    signedIpasPath: String,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_ipa_state"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📦",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "В папке signed_ipas пока нет файлов",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Сервер показывает только реальные подписанные приложения из папки:\n$signedIpasPath\nНажмите кнопку ниже, чтобы добавить свой .ipa файл.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onImport,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Выбрать файл .IPA")
            }
        }
    }
}

/**
 * ШАГ 3: Карточка с инструкцией по установке на iPhone и SSL-сертификату
 */
@Composable
private fun StepThreeInstallationCard(
    serverUrl: String,
    isServerRunning: Boolean,
    onOpenInstructions: () -> Unit,
    onShowCaQr: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_3_install_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Заголовок Шага 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppleGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "3",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Шаг 3: Установка на iPhone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Инструкция по установке через Safari (itms-services)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 понятных шага на русском языке
            InstallationStepRow(
                number = "1",
                text = "Подключите iPhone к той же Wi-Fi сети, что и этот Android-телефон."
            )
            InstallationStepRow(
                number = "2",
                text = "Откройте браузер Safari и перейдите по адресу сервера (или отсканируйте QR-код)."
            )
            InstallationStepRow(
                number = "3",
                text = "Нажмите «Установить» возле нужного приложения для запуска OTA-установки на экран «Домой»."
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Кнопка вызова подробной инструкции по SSL-сертификату
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenInstructions,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp)
                        .testTag("open_ssl_guide_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AppleBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Инструкция по SSL-сертификату",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isServerRunning) {
                    OutlinedButton(
                        onClick = onShowCaQr,
                        modifier = Modifier
                            .weight(0.7f)
                            .height(46.dp)
                            .testTag("show_ca_qr_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "QR профиля",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallationStepRow(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = AppleBlue.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppleBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
