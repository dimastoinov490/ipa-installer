package com.ipainstaller.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ipainstaller.model.IpaAppInfo
import com.ipainstaller.ui.theme.AppleBlue
import com.ipainstaller.ui.theme.AppleGreen
import com.ipainstaller.ui.theme.AppleOrange
import com.ipainstaller.ui.theme.ApplePurple

@Composable
fun IpaCard(
    ipa: IpaAppInfo,
    serverUrl: String,
    isServerRunning: Boolean,
    onShowQr: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val encodedFileName = Uri.encode(ipa.fileName)
    val manifestUrl = "$serverUrl/manifest.plist?app=$encodedFileName"
    val itmsUrl = "itms-services://?action=download-manifest&url=" + Uri.encode(manifestUrl)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ipa_card_${ipa.fileName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon Box
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(AppleBlue, ApplePurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneIphone,
                        contentDescription = "iOS App Icon",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = ipa.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ipa.bundleId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ipa.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_ipa_${ipa.fileName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete IPA",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meta tags row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaPill(text = "v${ipa.bundleVersion}", backgroundColor = MaterialTheme.colorScheme.surfaceVariant)
                MetaPill(text = ipa.formattedSize, backgroundColor = AppleOrange.copy(alpha = 0.15f), textColor = AppleOrange)
                MetaPill(text = "Pre-Signed", backgroundColor = AppleGreen.copy(alpha = 0.15f), textColor = AppleGreen)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("iOS Install URL", itmsUrl))
                        Toast.makeText(context, "Copied itms-services URL!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_itms_url_${ipa.fileName}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Link", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        onShowQr(if (isServerRunning) itmsUrl else serverUrl)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("show_qr_${ipa.fileName}"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install QR", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MetaPill(
    text: String,
    backgroundColor: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
