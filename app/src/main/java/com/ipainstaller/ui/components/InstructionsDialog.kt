package com.ipainstaller.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ipainstaller.ui.theme.AppleBlue
import com.ipainstaller.ui.theme.AppleOrange

@Composable
fun InstructionsDialog(
    serverUrl: String,
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
                .clip(RoundedCornerShape(24.dp))
                .testTag("instructions_dialog"),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppleBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "iOS OTA Security Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_instructions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Apple's itms-services protocol requires a trusted HTTPS server to install apps directly to the iOS Home Screen. Follow these steps once per iPhone:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Steps
                InstructionStep(
                    stepNumber = "1",
                    title = "Connect to Same Local Network",
                    description = "Ensure both this Android device and the target iPhone are connected to the same Wi-Fi router or Android Hotspot."
                )

                InstructionStep(
                    stepNumber = "2",
                    title = "Open Safari to the Local Server",
                    description = "On your iPhone, launch Safari and enter $serverUrl (or scan the QR Code on this screen)."
                )

                InstructionStep(
                    stepNumber = "3",
                    title = "Download Root CA Profile",
                    description = "Tap 'Download CA Profile' on the web page. When Safari asks 'This website is trying to download a configuration profile. Do you want to allow this?', tap Allow."
                )

                InstructionStep(
                    stepNumber = "4",
                    title = "Install Profile in iPhone Settings",
                    description = "Open iPhone Settings &rarr; Tap 'Profile Downloaded' near the top &rarr; Tap Install in the top right &rarr; Enter your passcode &rarr; Tap Install."
                )

                InstructionStep(
                    stepNumber = "5",
                    title = "Enable Full Trust for Root Certificate",
                    description = "In iPhone Settings, go to General &rarr; About &rarr; scroll to bottom &rarr; Certificate Trust Settings &rarr; Toggle 'IPA Installer Local CA' to ON &rarr; Tap Continue."
                )

                InstructionStep(
                    stepNumber = "6",
                    title = "Install Application",
                    description = "Return to Safari and tap 'Install on iPhone'. iOS will show '“$serverUrl” would like to install...'. Tap Install, and the app will appear on your Home Screen!"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Important Note Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AppleOrange.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AppleOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Crucial: The .ipa file MUST be pre-signed with a valid Apple Developer Certificate matching your iPhone's UDID (Ad-Hoc, Development) or signed with an Enterprise certificate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppleOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instructions_done_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Got It")
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(AppleBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
