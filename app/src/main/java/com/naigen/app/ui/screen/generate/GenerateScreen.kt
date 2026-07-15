package com.naigen.app.ui.screen.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.naigen.app.data.repository.NaiRepository
import com.naigen.app.data.styles.SizeOptions
import com.naigen.app.data.styles.StyleRegistry
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow
import com.naigen.app.ui.components.PrimaryButton
import com.naigen.app.util.DateUtils
import com.naigen.app.util.ImageSaver
import com.naigen.app.util.ShareUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

@Composable
fun GenerateScreen(vm: GenerateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val customStyles by vm.customStyles.collectAsStateWithLifecycle(initialValue = emptyList())
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 合并内置 + 社区 + 自定义风格
    val allStyles = remember(customStyles) { StyleRegistry.mergedWith(customStyles) }

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
            Text(
                stringResource(R.string.gen_title),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            // 正向提示词
            OutlinedTextField(
                value = state.prompt,
                onValueChange = vm::updatePrompt,
                placeholder = { Text(stringResource(R.string.gen_prompt_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .heightIn(min = 96.dp, max = 180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors()
            )

            // 负面提示词
            OutlinedTextField(
                value = state.negative,
                onValueChange = vm::updateNegative,
                placeholder = { Text(stringResource(R.string.gen_negative_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .heightIn(min = 56.dp, max = 120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors()
            )

            // 风格 + 尺寸（下拉菜单）
            GroupedList(title = "风格 / 尺寸") {
                // 风格下拉
                DropdownRow(
                    label = "画风",
                    value = allStyles[state.styleKey]?.name ?: "自定义画师串",
                    options = allStyles.values.map { it.key to it.name },
                    onSelected = vm::updateStyle
                )
                // 尺寸下拉
                DropdownRow(
                    label = "尺寸",
                    value = SizeOptions.get(state.sizeKey).label,
                    options = SizeOptions.ALL.map { it.key to it.label },
                    onSelected = vm::updateSize
                )
                // 张数输入框
                VariantsInputRow(state.variants, vm::updateVariants, isLast = true)
            }

            // 高级参数
            AdvancedParams(state, vm, allStyles)

            // 进度
            if (state.isGenerating || state.progress !is GenProgress.Idle) {
                ProgressCard(state.progress, modifier = Modifier.padding(16.dp))
            }

            // 结果
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
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun DropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,  // key to display
    onSelected: (String) -> Unit,
    helpText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (helpText != null) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(6.dp))
                com.naigen.app.ui.components.HelpIcon(
                    title = label,
                    description = helpText
                )
            } else {
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VariantsInputRow(variants: Int, onChange: (Int) -> Unit, isLast: Boolean = false) {
    var text by remember(variants) { mutableStateOf(variants.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.gen_count), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))

        // − 按钮
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChange((variants - 1).coerceAtLeast(1)) },
            contentAlignment = Alignment.Center
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))

        // 输入框（支持自定义张数 1-6）
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                // 只允许 1-2 位数字
                if (input.all { it.isDigit() } && input.length <= 2) {
                    text = input
                    val v = input.toIntOrNull()?.coerceIn(1, NaiRepository.MAX_VARIANTS) ?: 1
                    onChange(v)
                }
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = fieldColors()
        )
        Spacer(Modifier.width(8.dp))

        // + 按钮
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChange((variants + 1).coerceAtMost(NaiRepository.MAX_VARIANTS)) },
            contentAlignment = Alignment.Center
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.gen_count_unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AdvancedParams(state: GenerateUiState, vm: GenerateViewModel, allStyles: Map<String, com.naigen.app.data.model.StylePreset>) {
    var expanded by remember { mutableStateOf(false) }

    GroupedList(title = "高级参数（NAI 4.5 全参数）") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) stringResource(R.string.gen_collapse_label) else stringResource(R.string.gen_expand_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "Steps / Scale / CFG / Seed / Sampler / …",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // 自定义画师串
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.gen_custom_artist_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.customArtist,
                    onValueChange = vm::updateCustomArtist,
                    placeholder = { Text(stringResource(R.string.gen_custom_artist_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors()
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // 数值参数 3 列
            ParamRow3(
                "Steps", state.steps?.toString() ?: "默认", "1-50",
                KeyboardType.Number,
                helpText = com.naigen.app.ui.components.ParamHelp.steps
            ) { v -> vm.updateSteps(v?.toIntOrNull()) }
            ParamRow3(
                "Scale", state.scale?.toString() ?: "6.0", "1-20",
                KeyboardType.Decimal,
                helpText = com.naigen.app.ui.components.ParamHelp.scale
            ) { v -> vm.updateScale(v?.toDoubleOrNull()) }
            ParamRow3(
                "CFG", state.cfg?.toString() ?: "0.0", "0-1",
                KeyboardType.Decimal,
                helpText = com.naigen.app.ui.components.ParamHelp.cfg
            ) { v -> vm.updateCfg(v?.toDoubleOrNull()) }
            ParamRow3(
                "Seed", state.seed?.toString() ?: "随机", "留空 = 随机",
                KeyboardType.Number,
                helpText = com.naigen.app.ui.components.ParamHelp.seed
            ) { v -> vm.updateSeed(v?.toLongOrNull()) }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Sampler 下拉
            DropdownRow(
                label = "Sampler",
                value = state.sampler ?: "默认 (k_dpmpp_2m_sde)",
                options = listOf(
                    "k_dpmpp_2m_sde", "k_dpmpp_2m", "k_dpmpp_sde",
                    "k_dpmpp_2s_ancestral", "k_euler_ancestral", "k_euler"
                ).map { it to it },
                onSelected = { vm.updateSampler(it) },
                helpText = com.naigen.app.ui.components.ParamHelp.sampler
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Noise Schedule 下拉
            DropdownRow(
                label = "Noise Schedule",
                value = state.noiseSchedule ?: "karras (默认)",
                options = listOf("karras" to "karras (推荐)", "native" to "native"),
                onSelected = { vm.updateNoiseSchedule(it) },
                helpText = com.naigen.app.ui.components.ParamHelp.noiseSchedule
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Uncond Scale
            ParamRow3(
                "Uncond Scale", state.uncondScale?.toString() ?: "1.0", "负面词权重 1-5",
                KeyboardType.Decimal,
                helpText = com.naigen.app.ui.components.ParamHelp.uncondScale
            ) { v -> vm.updateUncondScale(v?.toDoubleOrNull()) }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // SM / SM Dynamic 开关（仅 k_dpmpp_sde 系列有效）
            val samplerVal = state.sampler ?: "k_dpmpp_2m_sde"
            val smEnabled = samplerVal.startsWith("k_dpmpp_sde") || samplerVal.startsWith("k_dpmpp_2s")
            SwitchRow(
                "SM (SMEA)",
                "SDE-DPM 求解器，仅 k_dpmpp_sde 系列有效",
                state.sm == true,
                enabled = smEnabled,
                helpText = com.naigen.app.ui.components.ParamHelp.sm
            ) { vm.updateSm(if (it) true else null) }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                "SM Dynamic",
                "动态 SDE-DPM，进一步增加细节",
                state.smDynamic == true,
                enabled = smEnabled,
                helpText = com.naigen.app.ui.components.ParamHelp.smDynamic
            ) { vm.updateSmDynamic(if (it) true else null) }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SwitchRow(
                "Variety Plus",
                "NAI 4.5 新增，增加多样性（消耗更多点数）",
                state.varietyPlus,
                enabled = true,
                isLast = true,
                helpText = com.naigen.app.ui.components.ParamHelp.varietyPlus
            ) { vm.updateVarietyPlus(it) }
        }
    }
}

@Composable
private fun ParamRow3(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    helpText: String? = null,
    onChange: (String?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (helpText != null) {
                    Spacer(Modifier.width(6.dp))
                    com.naigen.app.ui.components.HelpIcon(
                        title = label,
                        description = helpText
                    )
                }
            }
            Text(placeholder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.ifBlank { null })
            },
            modifier = Modifier.width(120.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = fieldColors()
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    isLast: Boolean = false,
    helpText: String? = null,
    onChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (helpText != null) {
                        Spacer(Modifier.width(6.dp))
                        com.naigen.app.ui.components.HelpIcon(
                            title = label,
                            description = helpText
                        )
                    }
                }
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row {
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
    Row(
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
