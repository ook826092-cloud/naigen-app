package com.naigen.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }
    var tokenInput by remember(state.token) { mutableStateOf(state.token) }
    var baseUrlInput by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
        )

        // ── API 凭证 ──
        GroupedList(title = "API 凭证") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Token", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        vm.setToken(it)
                    },
                    placeholder = { Text("STA1N-xxxxx…") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = colorParams()
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示明文", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showToken, onCheckedChange = { showToken = it })
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.padding(16.dp)) {
                Text("API 地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = {
                        baseUrlInput = it
                        vm.setBaseUrl(it)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = colorParams()
                )
            }
        }

        // ── 余额 ──
        GroupedList(title = "余额") {
            ListRow(
                label = if (state.checking) "查询中…" else "查询余额",
                value = state.balancePoints?.let { "$it 点" } ?: state.balanceError ?: "未查询",
                isLast = true,
                onClick = { if (!state.checking) vm.checkBalance() }
            )
        }

        // ── 内容过滤 ──
        GroupedList(title = "内容") {
            ListRow(label = "显示 NSFW 风格", isLast = true) {
                Switch(checked = state.nsfwEnabled, onCheckedChange = vm::setNsfw)
            }
        }

        // ── 关于 ──
        GroupedList(title = "关于") {
            ListRow(label = "应用名称", value = "NaiGen")
            ListRow(label = "版本", value = "1.0.0")
            ListRow(label = "API 服务", value = "Nai2API (nai.sta1n.cn)", isLast = true)
        }

        GroupedList(footer = "本应用基于 Nai2API 文生图教程实现，所有数据存储在本地，不上传任何用户信息。Token 仅保存在 App 私有目录，请勿在公开环境分享截图。") {
            ListRow(label = "隐私说明", isLast = true)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun colorParams() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)
