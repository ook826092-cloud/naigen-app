package com.naigen.app.ui.screen.settings.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.naigen.app.data.model.NsfwLevel
import com.naigen.app.data.model.StylePreset
import com.naigen.app.data.model.StyleSource
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleManagerScreen(vm: StyleManagerViewModel = viewModel(), nav: NavController) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<StylePreset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("风格管理") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增自定义")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 搜索框
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::search,
                placeholder = { Text("搜索风格名 / key") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            // NSFW 开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("显示 NSFW 风格", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = state.nsfwEnabled, onCheckedChange = vm::setNsfw)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 自定义风格
                val customs = state.customStyles.filter { matchQuery(it, state.searchQuery) }
                if (customs.isNotEmpty()) {
                    item {
                        SectionHeader("自定义风格 · ${customs.size}")
                    }
                    items(customs, key = { it.key }) { style ->
                        StyleRow(
                            style = style,
                            isSelected = state.selectedKey == style.key,
                            canDelete = true,
                            onClick = { vm.select(style.key) },
                            onDelete = { deleteTarget = style }
                        )
                    }
                }

                // 内置
                val builtins = vm.builtinStyles.filter { matchQuery(it, state.searchQuery) && (state.nsfwEnabled || it.nsfw == NsfwLevel.SAFE) }
                item { SectionHeader("内置预设 · ${builtins.size}") }
                items(builtins, key = { it.key }) { style ->
                    StyleRow(
                        style = style,
                        isSelected = state.selectedKey == style.key,
                        onClick = { vm.select(style.key) }
                    )
                }

                // 社区
                val communities = vm.communityStyles.filter { matchQuery(it, state.searchQuery) && (state.nsfwEnabled || it.nsfw == NsfwLevel.SAFE) }
                item { SectionHeader("社区风格 · ${communities.size}") }
                items(communities, key = { it.key }) { style ->
                    StyleRow(
                        style = style,
                        isSelected = state.selectedKey == style.key,
                        onClick = { vm.select(style.key) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomStyleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, artist, posPrefix, negPrompt, steps, scale, cfg, sampler ->
                vm.addCustomStyle(name, artist, posPrefix, negPrompt, steps, scale, cfg, sampler)
                showAddDialog = false
            }
        )
    }

    deleteTarget?.let { style ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除自定义风格") },
            text = { Text("确定删除「${style.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCustomStyle(style)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

private fun matchQuery(s: StylePreset, q: String): Boolean {
    val qq = q.trim().lowercase()
    if (qq.isBlank()) return true
    return s.name.lowercase().contains(qq) || s.key.lowercase().contains(qq)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun StyleRow(
    style: StylePreset,
    isSelected: Boolean,
    canDelete: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(style.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    TagChip(
                        when (style.source) {
                            StyleSource.BUILTIN -> "内置"
                            StyleSource.COMMUNITY -> if (style.provider == "自定义") "自定义" else "社区"
                        }
                    )
                    if (style.nsfw != NsfwLevel.SAFE) {
                        Spacer(Modifier.width(4.dp))
                        TagChip(if (style.nsfw == NsfwLevel.EXPLICIT) "NSFW" else "Q")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    style.key,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (isSelected) {
                Icon(Icons.Outlined.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AddCustomStyleDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String, artistString: String, positivePrefix: String, negativePrompt: String,
        steps: Int, scale: Double, cfg: Double, sampler: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var posPrefix by remember { mutableStateOf("") }
    var negPrompt by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("28") }
    var scale by remember { mutableStateOf("6.0") }
    var cfg by remember { mutableStateOf("0.0") }
    var sampler by remember { mutableStateOf("k_dpmpp_2m_sde") }

    val samplers = listOf(
        "k_dpmpp_2m_sde", "k_dpmpp_2m", "k_dpmpp_sde",
        "k_dpmpp_2s_ancestral", "k_euler_ancestral", "k_euler"
    )
    var samplerExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义风格") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("风格名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("画师串 * (例如: by wlop, artist:mika pikazo)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = posPrefix, onValueChange = { posPrefix = it }, label = { Text("正向提示词前缀 (可选)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = negPrompt, onValueChange = { negPrompt = it }, label = { Text("负面提示词 (可选)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = steps, onValueChange = { steps = it }, label = { Text("Steps") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = scale, onValueChange = { scale = it }, label = { Text("Scale") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = cfg, onValueChange = { cfg = it }, label = { Text("CFG") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Sampler", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedTextField(
                        value = sampler,
                        onValueChange = { },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().clickable { samplerExpanded = true },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = samplerExpanded, onDismissRequest = { samplerExpanded = false }) {
                        samplers.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { sampler = s; samplerExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && artist.isNotBlank()) {
                        onConfirm(
                            name.trim(), artist.trim(), posPrefix.trim(), negPrompt.trim(),
                            steps.toIntOrNull() ?: 28,
                            scale.toDoubleOrNull() ?: 6.0,
                            cfg.toDoubleOrNull() ?: 0.0,
                            sampler
                        )
                    }
                },
                enabled = name.isNotBlank() && artist.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
