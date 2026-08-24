package com.stellar.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellar.demo.ui.theme.AppShape
import roro.stellar.userservice.ServiceMode

@Composable
fun SettingsScreen(
    userServiceConnected: Boolean,
    currentServiceMode: ServiceMode,
    onServiceModeChange: (ServiceMode) -> Unit,
    ptyEchoEnabled: Boolean,
    onPtyEchoChange: (Boolean) -> Unit,
    logText: String,
    onClearLog: () -> Unit,
    serviceStartedLogs: List<String>,
    onClearServiceStartedLogs: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UserServiceSettingsCard(
            connected = userServiceConnected,
            currentMode = currentServiceMode,
            onModeChange = onServiceModeChange
        )

        PtySettingsCard(
            echoEnabled = ptyEchoEnabled,
            onEchoChange = onPtyEchoChange
        )

        ServiceStartedLogsCard(
            logs = serviceStartedLogs,
            onClear = onClearServiceStartedLogs
        )

        LogCard(
            logText = logText,
            onClear = onClearLog
        )
    }
}

@Composable
private fun PtySettingsCard(
    echoEnabled: Boolean,
    onEchoChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "PTY Shell 设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "显示回显", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (echoEnabled) "输入内容会显示在输出中" else "输入内容不显示在输出中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = echoEnabled, onCheckedChange = onEchoChange)
            }
        }
    }
}

@Composable
private fun UserServiceSettingsCard(
    connected: Boolean,
    currentMode: ServiceMode,
    onModeChange: (ServiceMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "UserService 设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "连接状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = AppShape.shapes.iconSmall,
                    color = if (connected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (connected) "已连接" else "未连接",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (connected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "守护模式",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (currentMode == ServiceMode.DAEMON)
                            "软件关闭后服务继续运行"
                        else
                            "软件关闭时服务也关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = currentMode == ServiceMode.DAEMON,
                    onCheckedChange = { isDaemon ->
                        onModeChange(if (isDaemon) ServiceMode.DAEMON else ServiceMode.ONE_TIME)
                    }
                )
            }
        }
    }
}

@Composable
private fun ServiceStartedLogsCard(
    logs: List<String>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "服务启动记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "清空记录")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = AppShape.shapes.cardMedium
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = if (logs.isEmpty()) "暂无记录" else logs.joinToString("\n"),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (logs.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LogCard(
    logText: String,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "输出日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "清空日志"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = AppShape.shapes.cardMedium
            ) {
                val scrollState = rememberScrollState()

                Text(
                    text = logText.ifEmpty { "日志输出将显示在这里...\n\n运行功能来查看输出" },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (logText.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
