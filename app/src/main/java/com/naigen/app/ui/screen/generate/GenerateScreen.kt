package com.naigen.app.ui.screen.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.naigen.app.data.repository.GenProgress
import com.naigen.app.data.styles.SizeOptions
import com.naigen.app.data.styles.StyleRegistry
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow
import com.naigen.app.ui.components.PrimaryButton
import com.naigen.app.ui.components.TagChip
import com.naigen.app.util.DateUtils
import com.naigen.app.util.ImageSaver
import com.naigen.app.util.ShareUtils
import kotlinx.coroutines.launch

@Composable
fun GenerateScreen(vm: GenerateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp)
        ) {
            // ── 大标题 ──────────────────────────────────────────────────────
            Text(
                "生成",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            // ── 正向提示词 ──────────────────────────────────────────────────
            PromptInput(
                value = state.prompt,
                onChange = vm::updatePrompt,
                placeholder = "输入正向提示词，逗号分隔。\n例如：1girl, solo, silver hair, masterpiece…",
                negative = false
            )

            // ── 负面提示词 ──────────────────────────────────────────────────
            PromptInput(
                value = state.negative,
                onChange = vm::updateNegative,
                placeholder = "负面提示词（可选）",
                negative = true
            )

            // ── 风格 + 尺寸 ─────────────────────────────────────────────────
            GroupedList(title = "风格 / 尺寸") {
                ListRow(
                    label = "画风",
                    value = StyleRegistry.get(state.styleKey)?.name ?: "自定义画师串",
                    onClick = { vm.updateStyle(if (state.styleKey == "2.5d") "fresh" else "2.5d") } // 简化：到风格页详细选
                )
                ListRow(
                    label = "尺寸",
                    value = SizeOptions.get(state.sizeKey).label,
                    onClick = {
                        // 简化：在 9 个尺寸间循环
                        val all = SizeOptions.ALL
                        val idx = all.indexOfFirst { it.key == state.sizeKey }
                        vm.updateSize(all[(idx + 1) % all.size].key)
                    }
                )
                ListRow(label = "变体张数", value = "${state.variants} 张", isLast = true) {
                    TagChip("−", onClick = { vm.updateVariants(state.variants - 1) })
                    Spacer(Modifier.width(8.dp))
                    TagChip("+", onClick = { vm.updateVariants(state.variants + 1) })
                }
            }

            // ── 高级参数 ────────────────────────────────────────────────────
            AdvancedParams(state, vm)

            // ── 自动风格检测 ────────────────────────────────────────────────
            GroupedList(title = "智能") {
                ListRow(
                    label = "自动风格检测",
                    value = if (state.autoStyle) "开" else "关",
                    isLast = true,
                    onClick = { vm.toggleAutoStyle() }
                )
            }

            // ── 进度展示 ────────────────────────────────────────────────────
            if (state.isGenerating || state.progress !is GenProgress.Idle) {
                ProgressCard(state.progress, modifier = Modifier.padding(16.dp))
            }

            // ── 结果网格 ────────────────────────────────────────────────────
            if (state.results.isNotEmpty()) {
                ResultGrid(
                    results = state.results,
                    onSaveToGallery = { bytes ->
                        val uri = ImageSaver.saveToGallery(ctx, bytes)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (uri != null) "已保存到相册 Pictures/NaiGen/" else "保存失败"
                            )
                        }
                    },
                    onShare = { bytes ->
                        val uri = ImageSaver.saveToGallery(ctx, bytes)
                        if (uri != null) {
                            ShareUtils.share(ctx, "NaiGen 生成图片", uri)
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── 底部生成按钮 ──────────────────────────────────────────────────
        BottomGenerateBar(
            state = state,
            onGenerate = vm::generate,
            onCancel = vm::cancel,
            onCheckBalance = vm::checkBalance,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) { data ->
            Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PromptInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    negative: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 72.dp, max = 180.dp),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun AdvancedParams(state: GenerateUiState, vm: GenerateViewModel) {
    var expanded by remember { mutableStateOf(false) }

    GroupedList(title = "高级参数") {
        ListRow(
            label = "自定义画师串",
            value = state.customArtist.ifBlank { "（沿用风格预设）" },
            isLast = !expanded,
            onClick = { expanded = !expanded },
            trailing = {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        if (expanded) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            OutlinedTextField(
                value = state.customArtist,
                onValueChange = vm::updateCustomArtist,
                placeholder = { Text("覆盖画风预设的画师串，例如 by xxx, artist:yyy") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                minLines = 2,
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            ParamRow("Steps", state.steps?.toString() ?: "默认", TextInputType.Int) { v ->
                vm.updateSteps(v?.toIntOrNull())
            }
            ParamRow("Scale", state.scale?.toString() ?: "6.0", TextInputType.Double) { v ->
                vm.updateScale(v?.toDoubleOrNull())
            }
            ParamRow("CFG", state.cfg?.toString() ?: "0.0", TextInputType.Double, isLast = true) { v ->
                vm.updateCfg(v?.toDoubleOrNull())
            }
        }
    }
}

private enum class TextInputType { Int, Double, Text }

@Composable
private fun ParamRow(
    label: String,
    value: String,
    type: TextInputType,
    isLast: Boolean = false,
    onChange: (String?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    Column {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(it.ifBlank { null })
                },
                modifier = Modifier.width(120.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (type) {
                        TextInputType.Int -> KeyboardType.Number
                        TextInputType.Double -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun ProgressCard(progress: GenProgress, modifier: Modifier = Modifier) {
    val msg = when (progress) {
        is GenProgress.Creating -> "创建任务中… (${progress.variant + 1}/${progress.total})"
        is GenProgress.Polling -> "生成中… ${progress.elapsedSec}s · job ${progress.jobId.take(8)}"
        is GenProgress.Downloading -> "下载图片中…"
        is GenProgress.OneDone -> "已出 ${progress.variant + 1} 张…"
        is GenProgress.AllDone -> "完成"
        GenProgress.Idle -> ""
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress !is GenProgress.AllDone) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
            }
            Text(msg, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ResultGrid(
    results: List<com.naigen.app.data.model.GenResult>,
    onSaveToGallery: (ByteArray) -> Unit,
    onShare: (ByteArray) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        results.forEach { r ->
            ResultCard(r, onSaveToGallery, onShare)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ResultCard(
    r: com.naigen.app.data.model.GenResult,
    onSaveToGallery: (ByteArray) -> Unit,
    onShare: (ByteArray) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        if (r.success && r.images.isNotEmpty()) {
            val img = r.images.first()
            AsyncImage(
                model = img.bytes,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .aspectRatio(832f / 1216f),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${r.styleName} · ${r.sizeKey} · ${DateUtils.duration(r.generationTimeMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row {
                IconAction("相册", Icons.Outlined.Download) { onSaveToGallery(img.bytes) }
                Spacer(Modifier.width(12.dp))
                IconAction("分享", Icons.Outlined.Share) { onShare(img.bytes) }
            }
        } else {
            Text(
                "生成失败：${r.errorMessage ?: "未知错误"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun IconAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BottomGenerateBar(
    state: GenerateUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onCheckBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCheckBalance),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Bolt, contentDescription = "余额", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        if (state.isGenerating) {
            // 生成中：左边主按钮变取消，右边生成中状态
            PrimaryButton(
                text = "取消生成",
                onClick = onCancel,
                danger = true,
                modifier = Modifier.weight(1f)
            )
        } else {
            PrimaryButton(
                text = if (state.variants > 1) "并发出 ${state.variants} 张" else "生成",
                onClick = onGenerate,
                enabled = state.prompt.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
