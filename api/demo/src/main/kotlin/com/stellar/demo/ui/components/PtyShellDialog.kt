package com.stellar.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellar.demo.DemoFunctions

@Composable
fun PtyShellDialog(
    session: DemoFunctions.PtyShellSession?,
    output: String,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(output) { scrollState.animateScrollTo(scrollState.maxValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PTY Shell") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = output.ifEmpty { "等待输出..." },
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (output.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入命令...", fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    )
                    IconButton(
                        onClick = {
                            android.util.Log.d("PtyShell", "input: $input")
                            session?.send(input)
                            input = ""
                        },
                        enabled = session != null && input.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                session?.destroy()
                onDismiss()
            }) { Text("关闭") }
        }
    )
}
