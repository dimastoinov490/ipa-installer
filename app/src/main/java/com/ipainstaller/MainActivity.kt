package com.ipainstaller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipainstaller.ui.components.InstructionsDialog
import com.ipainstaller.ui.components.IpaCard
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.VpnKey
import com.ipainstaller.ui.screens.AppleIntegrationScreen
import com.ipainstaller.ui.components.QrCodeDialog
import com.ipainstaller.ui.components.ServerLogsView
import androidx.compose.foundation.BorderStroke
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
                            text = "IPA Installer",
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
                    IconButton(
                        onClick = { currentScreen = Screen.APPLE_INTEGRATION },
                        modifier = Modifier.testTag("open_apple_integration_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Apple & GitHub CI/CD Secrets",
                            tint = AppleBlue
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshNetworkAddresses(context) },
                        modifier = Modifier.testTag("refresh_network_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Network"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.showInstructions(true) },
                        modifier = Modifier.testTag("open_instructions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Instructions"
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
            // 1. Server Control Card
            item {
                ServerStatusCard(
                    uiState = uiState,
                    serverUrl = serverUrl,
                    onToggleServer = { viewModel.toggleServer(context) },
                    onSelectAddress = { addr -> viewModel.selectAddress(addr, context) },
                    onShowQr = { url -> viewModel.showQrDialog(url) }
                )
            }

            // 2. iOS Trust Certificate Banner
            item {
                IosSetupBanner(
                    onOpenInstructions = { viewModel.showInstructions(true) },
                    onShowCaQr = {
                        val caUrl = "$serverUrl/ca.mobileconfig"
                        viewModel.showQrDialog(caUrl)
                    }
                )
            }

            // 2b. GitHub Actions CI/CD Secrets Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentScreen = Screen.APPLE_INTEGRATION }
                        .testTag("cicd_secrets_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ApplePurple.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, ApplePurple.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ApplePurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = ApplePurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Apple & GitHub CI/CD Secrets",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Export Base64 .p12 & .mobileprovision",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { currentScreen = Screen.APPLE_INTEGRATION },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Configure", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 3. IPA Management Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Pre-Signed IPAs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.ipaList.size} ready to install wirelessly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.createSampleIpa(context) },
                            modifier = Modifier.testTag("create_sample_ipa_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sample", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.testTag("import_ipa_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import .IPA", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 4. List of IPAs
            if (uiState.ipaList.isEmpty()) {
                item {
                    EmptyIpaState(
                        onImport = { filePickerLauncher.launch("*/*") },
                        onCreateSample = { viewModel.createSampleIpa(context) }
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

            // 5. Server Live Request Logs
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

@Composable
private fun ServerStatusCard(
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
            .testTag("server_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Status Indicator & Interface Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (uiState.serverState) {
                            ServerState.RUNNING -> "HTTPS Server Online"
                            ServerState.STARTING -> "Starting TLS Server..."
                            ServerState.ERROR -> "Server Error"
                            ServerState.STOPPED -> "Server Offline"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // IP Interface Selector Dropdown
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { isDropdownOpen = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = AppleBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.selectedAddress?.ip ?: "No IP",
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
                                        Text(addr.ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // URL Bar when running
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
                                    text = "Safari Access URL",
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
                                        Toast.makeText(context, "URL copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_server_url_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy URL",
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
                                        contentDescription = "QR Code",
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
                                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("open_browser_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "Open in Browser",
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

            // Big Start/Stop Server Toggle Button
            Button(
                onClick = onToggleServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("toggle_server_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) AppleRed else AppleGreen
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Stop HTTPS Server" else "Start HTTPS Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun IosSetupBanner(
    onOpenInstructions: () -> Unit,
    onShowCaQr: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ios_setup_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppleBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "iPhone SSL Certificate Setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trust local CA once to allow itms-services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenInstructions,
                    modifier = Modifier.testTag("view_guide_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Guide", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EmptyIpaState(
    onImport: () -> Unit,
    onCreateSample: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_ipa_state"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📦",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No iOS Apps Added",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Import pre-signed .ipa files from device storage, or generate a sample test IPA to try the installation flow right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCreateSample,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Create Test IPA")
                }
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Import .IPA")
                }
            }
        }
    }
}
