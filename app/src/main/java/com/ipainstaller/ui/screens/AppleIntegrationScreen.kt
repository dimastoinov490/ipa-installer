package com.ipainstaller.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ipainstaller.AppleCertExporter
import com.ipainstaller.ui.theme.AppleBlue
import com.ipainstaller.ui.theme.AppleGreen
import com.ipainstaller.ui.theme.AppleOrange
import com.ipainstaller.ui.theme.ApplePurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleIntegrationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // State
    var targetUdid by remember { mutableStateOf("") }
    var p12FileName by remember { mutableStateOf("") }
    var p12Base64 by remember { mutableStateOf("") }
    var p12Password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var provisionFileName by remember { mutableStateOf("") }
    var provisionBase64 by remember { mutableStateOf("") }

    var showWorkflowDialog by remember { mutableStateOf(false) }

    // Load persisted values from Keystore/Prefs
    LaunchedEffect(Unit) {
        targetUdid = AppleCertExporter.getSavedUdid(context)
        p12Password = AppleCertExporter.getDecryptedPassword(context)
    }

    // File pickers
    val p12PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = AppleCertExporter.readUriBytes(context, uri)
            if (bytes != null) {
                p12Base64 = AppleCertExporter.encodeToBase64(bytes)
                p12FileName = uri.lastPathSegment?.substringAfterLast("/") ?: "certificate.p12"
                Toast.makeText(context, "Loaded .p12 certificate (${bytes.size} bytes)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val provisionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = AppleCertExporter.readUriBytes(context, uri)
            if (bytes != null) {
                provisionBase64 = AppleCertExporter.encodeToBase64(bytes)
                provisionFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "profile.mobileprovision"
                Toast.makeText(context, "Loaded .mobileprovision (${bytes.size} bytes)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isUdidValid = targetUdid.isNotBlank() && AppleCertExporter.validateUdid(targetUdid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Apple & GitHub CI/CD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_from_apple_integration_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Security Architecture & Standards Notice
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AppleBlue.copy(alpha = 0.1f)
                ),
                border = BorderStroke(1.dp, AppleBlue.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Apple CI/CD Security Best Practice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AppleBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "For automated CI/CD signing, Apple recommends using exported .p12 certificates and provisioning profiles stored as encrypted repository secrets. This tool prepares and encodes your credentials locally on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Target Device UDID Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneIphone,
                            contentDescription = null,
                            tint = ApplePurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Target iPhone UDID",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = targetUdid,
                        onValueChange = {
                            targetUdid = it.trim()
                            AppleCertExporter.saveUdid(context, targetUdid)
                        },
                        label = { Text("Device UDID (25 or 40 characters)") },
                        placeholder = { Text("00008030-001234567890ABCD") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("udid_input_field"),
                        singleLine = true,
                        trailingIcon = {
                            if (targetUdid.isNotEmpty()) {
                                if (isUdidValid) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Valid UDID",
                                        tint = AppleGreen
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Invalid UDID",
                                        tint = AppleOrange
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (targetUdid.isEmpty()) {
                            "Enter the UDID registered in your provisioning profile."
                        } else if (isUdidValid) {
                            "✓ Valid format (${if (targetUdid.contains("-")) "Modern 25-char UDID" else "Legacy 40-char UDID"})"
                        } else {
                            "⚠️ Invalid UDID format. Must be 40 hex digits or 8-16 hyphenated hex digits."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUdidValid) AppleGreen else if (targetUdid.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else AppleOrange
                    )
                }
            }

            // 3. Apple Certificate (.p12) Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Developer Certificate (.p12)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { p12PickerLauncher.launch("*/*") },
                            modifier = Modifier.testTag("import_p12_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (p12Base64.isEmpty()) "Import .p12" else "Replace")
                        }
                    }

                    if (p12FileName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = p12FileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${p12Base64.length} Base64 chars",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppleGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = p12Password,
                        onValueChange = {
                            p12Password = it
                            AppleCertExporter.saveEncryptedPassword(context, it)
                        },
                        label = { Text("P12 Export Password") },
                        placeholder = { Text("Enter password (encrypted in Keystore)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("p12_password_field"),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        }
                    )
                }
            }

            // 4. Provisioning Profile (.mobileprovision) Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AppleGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Provisioning Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { provisionPickerLauncher.launch("*/*") },
                            modifier = Modifier.testTag("import_mobileprovision_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (provisionBase64.isEmpty()) "Import Profile" else "Replace")
                        }
                    }

                    if (provisionFileName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = provisionFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${provisionBase64.length} Base64 chars",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppleGreen
                                )
                            }
                        }
                    }
                }
            }

            // 5. GitHub Secrets Export Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "GitHub Secrets (Ready to Paste)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste these in your GitHub Repo: Settings > Secrets and variables > Actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Secret 1: CERT_P12_BASE64
                    SecretRow(
                        secretName = "CERT_P12_BASE64",
                        value = p12Base64,
                        placeholder = "Import a .p12 file above to generate",
                        onCopy = { copyToClipboard(context, "CERT_P12_BASE64", p12Base64) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secret 2: P12_PASSWORD
                    SecretRow(
                        secretName = "P12_PASSWORD",
                        value = p12Password,
                        placeholder = "Enter P12 password above",
                        onCopy = { copyToClipboard(context, "P12_PASSWORD", p12Password) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secret 3: MOBILEPROVISION_BASE64
                    SecretRow(
                        secretName = "MOBILEPROVISION_BASE64",
                        value = provisionBase64,
                        placeholder = "Import a .mobileprovision file above to generate",
                        onCopy = { copyToClipboard(context, "MOBILEPROVISION_BASE64", provisionBase64) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val bundle = """
CERT_P12_BASE64=$p12Base64
P12_PASSWORD=$p12Password
MOBILEPROVISION_BASE64=$provisionBase64
                                """.trimIndent()
                                copyToClipboard(context, "All GitHub Secrets", bundle)
                            },
                            enabled = p12Base64.isNotEmpty() || provisionBase64.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_all_secrets_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All Secrets")
                        }

                        OutlinedButton(
                            onClick = { showWorkflowDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_ci_workflow_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Workflow")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showWorkflowDialog) {
        WorkflowDialog(
            snippet = AppleCertExporter.generateGitHubWorkflowSnippet(),
            onDismiss = { showWorkflowDialog = false }
        )
    }
}

@Composable
private fun SecretRow(
    secretName: String,
    value: String,
    placeholder: String,
    onCopy: () -> Unit
) {
    val isReady = value.isNotEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isReady) AppleGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = secretName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isReady) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isReady) "${value.take(20)}... (${value.length} chars)" else placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCopy,
                enabled = isReady,
                modifier = Modifier.testTag("copy_${secretName.lowercase()}_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $secretName",
                    tint = if (isReady) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun WorkflowDialog(
    snippet: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "GitHub Actions Workflow Step",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add this step to your .github/workflows/ios.yml to decode and import the certificates into the macOS runner keychain:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "GitHub Workflow Snippet", snippet)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Snippet")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, content: String) {
    if (content.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
    Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
}
