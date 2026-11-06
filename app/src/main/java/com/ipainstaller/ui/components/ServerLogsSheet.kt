package com.ipainstaller.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ipainstaller.model.ServerLog
import com.ipainstaller.ui.theme.AppleBlue
import com.ipainstaller.ui.theme.AppleGreen
import com.ipainstaller.ui.theme.AppleOrange
import com.ipainstaller.ui.theme.AppleRed

@Composable
fun ServerLogsView(
    logs: List<ServerLog>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_logs_card"),
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Live Server Traffic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${logs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag("clear_logs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No server requests yet. Connect an iPhone to view real-time traffic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogRow(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: ServerLog) {
    val statusColor = when {
        log.statusCode in 200..299 -> AppleGreen
        log.statusCode in 300..399 -> AppleBlue
        log.statusCode in 400..499 -> AppleOrange
        else -> AppleRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = log.formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = log.method,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppleBlue,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = log.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(6.dp))

        Surface(
            color = statusColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "${log.statusCode}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
