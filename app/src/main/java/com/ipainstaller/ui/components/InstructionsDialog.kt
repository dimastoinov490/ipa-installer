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
import com.ipainstaller.AppLocale
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
                            text = AppLocale.t("Настройка SSL для iOS OTA", "iOS OTA Security Setup"),
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
                            contentDescription = AppLocale.t("Закрыть", "Close")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = AppLocale.t(
                        "Протокол Apple itms-services требует доверенного HTTPS соединения. Выполните эти шаги один раз для вашего iPhone:",
                        "Apple's itms-services protocol requires a trusted HTTPS server to install apps directly to the iOS Home Screen. Follow these steps once per iPhone:"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Steps
                InstructionStep(
                    stepNumber = "1",
                    title = AppLocale.t("Подключитесь к одной сети", "Connect to Same Local Network"),
                    description = AppLocale.t(
                        "Убедитесь, что Android и iPhone подключены к одному Wi-Fi роутеру или точке доступа Android.",
                        "Ensure both this Android device and the target iPhone are connected to the same Wi-Fi router or Android Hotspot."
                    )
                )

                InstructionStep(
                    stepNumber = "2",
                    title = AppLocale.t("Откройте Safari", "Open Safari to the Local Server"),
                    description = AppLocale.t(
                        "На iPhone откройте Safari и перейдите по адресу $serverUrl (или отсканируйте QR код).",
                        "On your iPhone, launch Safari and enter $serverUrl (or scan the QR Code on this screen)."
                    )
                )

                InstructionStep(
                    stepNumber = "3",
                    title = AppLocale.t("Скачайте профиль CA", "Download Root CA Profile"),
                    description = AppLocale.t(
                        "Нажмите кнопку 'SSL Профиль' вверху страницы. В диалоге выберите 'Разрешить'.",
                        "Tap 'SSL Profile' at the top of the web page. When Safari prompts to download a profile, tap Allow."
                    )
                )

                InstructionStep(
                    stepNumber = "4",
                    title = AppLocale.t("Установите профиль в Настройках", "Install Profile in iPhone Settings"),
                    description = AppLocale.t(
                        "Откройте Настройки iPhone &rarr; 'Профиль загружен' &rarr; 'Установить' &rarr; введите пароль &rarr; 'Установить'.",
                        "Open iPhone Settings &rarr; Tap 'Profile Downloaded' &rarr; Tap Install &rarr; Enter passcode &rarr; Tap Install."
                    )
                )

                InstructionStep(
                    stepNumber = "5",
                    title = AppLocale.t("Включите полное доверие сертификату", "Enable Full Trust for Root Certificate"),
                    description = AppLocale.t(
                        "В Настройках: Основные &rarr; Об этом устройстве &rarr; Доверие сертификатов (внизу) &rarr; включите тумблер 'IPA Installer Local CA'.",
                        "In iPhone Settings: General &rarr; About &rarr; scroll down &rarr; Certificate Trust Settings &rarr; Toggle 'IPA Installer Local CA' to ON."
                    )
                )

                InstructionStep(
                    stepNumber = "6",
                    title = AppLocale.t("Установите приложение", "Install Application"),
                    description = AppLocale.t(
                        "Вернитесь в Safari и нажмите 'Установить'. Начнется беспроводная установка на экран «Домой»!",
                        "Return to Safari and tap 'Install'. The application will install directly to your Home Screen!"
                    )
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
                            text = AppLocale.t(
                                "Важно: файл .ipa ДОЛЖЕН быть предварительно подписан действительным сертификатом Apple Developer, соответствующим UDID вашего iPhone (Ad-Hoc, Development) или Enterprise сертификатом.",
                                "Crucial: The .ipa file MUST be pre-signed with a valid Apple Developer Certificate matching your iPhone's UDID (Ad-Hoc, Development) or signed with an Enterprise certificate."
                            ),
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
                    Text(AppLocale.t("Понятно", "Got It"))
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
