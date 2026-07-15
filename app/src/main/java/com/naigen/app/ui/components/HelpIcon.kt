package com.naigen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

/**
 * 参数名旁的问号图标，点击弹出说明对话框。
 *
 * 用法：
 *   ParamWithHelp(
 *       title = "Steps",
 *       helpText = "采样步数，1-50。推荐 28。越大越精细但越慢。",
 *       content = { Text("Steps") }
 *   )
 */
@Composable
fun HelpIcon(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showDialog = true }
            .padding(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = "$title 说明",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.common_got_it) }
            }
        )
    }
}

/**
 * 参数说明对照表（NAI Diffusion 4.5 全参数）
 */
object ParamHelp {
    val steps = "采样步数 (Sampling Steps)\n\n" +
        "控制去噪过程的迭代次数。\n\n" +
        "• 范围：1-50\n" +
        "• 推荐：28（NAI 4.5 默认）\n" +
        "• 越大越精细，但耗时越长、消耗点数越多\n" +
        "• 低于 15 可能细节不足，高于 35 收益递减"

    val scale = "引导比例 (Guidance Scale / CFG Scale)\n\n" +
        "控制 prompt 对生成结果的引导强度。\n\n" +
        "• 范围：1-20\n" +
        "• 推荐：6（NAI 4.5 默认）\n" +
        "• 越大越贴合 prompt，但可能过曝\n" +
        "• 越小越有创意，但可能偏离 prompt\n" +
        "• 配合 CFG Rescale 使用效果更好"

    val cfg = "CFG Rescale\n\n" +
        "重缩放 CFG 输出，缓解高 scale 时的过曝问题。\n\n" +
        "• 范围：0-1\n" +
        "• 推荐：0（关闭）\n" +
        "• 0 = 标准 CFG\n" +
        "• 0.5-1 = 缓解过曝，颜色更自然\n" +
        "• 配合 scale ≥ 8 时建议设为 0.3-0.5"

    val seed = "随机种子 (Seed)\n\n" +
        "控制生成结果的随机性。\n\n" +
        "• 留空 = 随机种子（每次不同）\n" +
        "• 填入数字 = 固定种子（可复现）\n" +
        "• 同 prompt + 同 seed + 同参数 = 完全相同的图\n" +
        "• 用于微调：固定 seed 改 prompt 看变化"

    val sampler = "采样器 (Sampler)\n\n" +
        "控制去噪过程的数学算法。\n\n" +
        "• k_dpmpp_2m_sde（推荐，默认）\n" +
        "  DPM++ 2M SDE，平衡速度与质量\n" +
        "• k_dpmpp_2m：DPM++ 2M，更快但稍弱\n" +
        "• k_dpmpp_sde：DPM++ SDE，配合 SM 使用\n" +
        "• k_dpmpp_2s_ancestral：DPM++ 2S，有随机性\n" +
        "• k_euler_ancestral：Euler a，有创意但噪声多\n" +
        "• k_euler：Euler，简单快速"

    val noiseSchedule = "噪声调度 (Noise Schedule)\n\n" +
        "控制去噪过程中噪声的衰减方式。\n\n" +
        "• karras（推荐，默认）\n" +
        "  Karras 调度，前期去噪快后期精细\n" +
        "• native\n" +
        "  原生调度，线性衰减\n" +
        "• karras 通常比 native 更精细"

    val uncondScale = "负面词权重 (Uncond Scale)\n\n" +
        "控制负面提示词的影响强度。\n\n" +
        "• 范围：1.0-5.0\n" +
        "• 推荐：1.0（标准）\n" +
        "• 越大负面词影响越强\n" +
        "• 用于强力排除不想要的元素（如多手指）\n" +
        "• 过大可能导致画面异常"

    val sm = "SMEA (Sampling Mode for Equal Aspect)\n\n" +
        "专门为高分辨率（≥1024px）设计的采样模式。\n\n" +
        "• 仅 k_dpmpp_sde 系列采样器有效\n" +
        "• 开启后减少大图的伪影\n" +
        "• 会略微增加耗时"

    val smDynamic = "动态 SMEA (Dynamic SMEA)\n\n" +
        "SMEA 的动态版本，按需启用。\n\n" +
        "• 仅 k_dpmpp_sde 系列采样器有效\n" +
        "• 比普通 SMEA 更智能\n" +
        "• 配合 SM 开关使用"

    val varietyPlus = "Variety Plus (多样性增强)\n\n" +
        "NAI 4.5 新增参数，增加生成结果的多样性。\n\n" +
        "• 开启后同 prompt 同 seed 也能出不同结果\n" +
        "• 消耗更多点数\n" +
        "• 适用于批量生成挑选最佳\n" +
        "• 不适合需要复现的场景"

    val customArtist = "自定义画师串 (Custom Artist String)\n\n" +
        "覆盖风格预设的画师串。\n\n" +
        "• 留空 = 使用当前选中的风格预设\n" +
        "• 填入 = 完全替换画师串\n" +
        "• 语法示例：\n" +
        "  - by wlop\n" +
        "  - artist:mika pikazo\n" +
        "  - 1.4::artist:asanagi:: (加权)\n" +
        "  - artist:wlop, artist:ciloranko\n" +
        "• 多个画师用逗号分隔，可加权重"
}
