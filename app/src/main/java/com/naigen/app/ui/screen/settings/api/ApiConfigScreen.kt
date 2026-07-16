package com.naigen.app.ui.screen.settings.api

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.naigen.app.data.provider.ApiProvider
import com.naigen.app.data.provider.ProviderType
import com.naigen.app.ui.screen.settings.SettingsViewModel
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

/**
 * API 服务商配置页（v2）。
 *
 * 重构后支持：
 *   - 内置 NAI 2 API（固定，只能改 token）
 *   - 用户自定义 provider（OpenAI 兼容生图，可增删改）
 *   - provider 列表选择当前使用哪个
 *
 * 设计参考 kelivo 的 provider 管理页：列表 + 选中态 + 编辑弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(vm: SettingsViewModel = viewModel(), nav: NavController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedId by vm.selectedProviderId.collectAsStateWithLifecycle(initialValue = ApiProvider.BUILTIN_NAI2_ID)

    var showToken by remember { mutableStateOf(false) }
    var tokenInput by remember(state.token) { mutableStateOf(state.token) }
    var editingProvider by remember { mutableStateOf<ApiProvider?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加供应商")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ── 供应商列表 ──────────────────────────────────────────────
            Text(
                "服务商列表",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            providers.forEach { provider ->
                ProviderRow(
                    provider = provider,
                    selected = provider.id == selectedId,
                    onSelect = { vm.setSelectedProvider(provider.id) },
                    onEdit = { editingProvider = provider }
                )
                if (provider != providers.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            // ── 当前选中 provider 的 Token 配置 ─────────────────────────
            val current = providers.firstOrNull { it.id == selectedId } ?: ApiProvider.BUILTIN_NAI2
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${current.name} · Token",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it; vm.setToken(it) },
                    placeholder = { Text(stringResource(R.string.api_token_hint)) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.api_show_plaintext), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showToken, onCheckedChange = { showToken = it })
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            // ── NSFW 开关 ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.api_nsfw_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.api_nsfw_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.nsfwEnabled, onCheckedChange = vm::setNsfw)
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            // ── 隐私说明 ────────────────────────────────────────────────
            Text(
                stringResource(R.string.api_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }

    // 编辑自定义 provider 弹窗
    editingProvider?.let { provider ->
        if (!provider.builtin) {
            EditProviderDialog(
                provider = provider,
                onDismiss = { editingProvider = null },
                onSave = { updated ->
                    vm.updateProvider(updated)
                    editingProvider = null
                },
                onDelete = {
                    vm.deleteProvider(provider.id)
                    editingProvider = null
                }
            )
        }
    }

    // 新增 provider 弹窗
    if (showAddDialog) {
        EditProviderDialog(
            provider = null,
            onDismiss = { showAddDialog = false },
            onSave = { newProvider ->
                vm.addProvider(newProvider)
                showAddDialog = false
            },
            onDelete = {}
        )
    }
}

@Composable
private fun ProviderRow(
    provider: ApiProvider,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = providerIcon(provider),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // 名称 + 类型
        Column(Modifier.weight(1f)) {
            Text(
                provider.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${providerTypeLabel(provider.type)} · ${provider.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 选中标记
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        // 编辑按钮（自定义 provider 才显示）
        if (!provider.builtin) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditProviderDialog(
    provider: ApiProvider?,
    onDismiss: () -> Unit,
    onSave: (ApiProvider) -> Unit,
    onDelete: () -> Unit
) {
    val isNew = provider == null
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl ?: "") }
    var type by remember { mutableStateOf(provider?.type ?: ProviderType.OPENAI_COMPATIBLE) }
    var note by remember { mutableStateOf(provider?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加供应商" else "编辑供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("类型", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(providerTypeLabel(t)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        val saved = if (isNew) {
                            ApiProvider(
                                id = "custom_${System.currentTimeMillis()}",
                                name = name, type = type, baseUrl = baseUrl,
                                builtin = false, note = note,
                                createdAt = System.currentTimeMillis()
                            )
                        } else {
                            provider!!.copy(name = name, type = type, baseUrl = baseUrl, note = note)
                        }
                        onSave(saved)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)

private fun providerIcon(provider: ApiProvider): ImageVector = when {
    provider.builtin -> Icons.Outlined.AutoAwesome
    provider.type == ProviderType.OPENAI_COMPATIBLE -> Icons.Outlined.Cloud
    else -> Icons.Outlined.AutoAwesome
}

private fun providerTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.NAI_2 -> "NAI 2"
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
    ProviderType.CUSTOM -> "自定义"
}
