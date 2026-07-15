package com.naigen.app.ui.screen.album

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naigen.app.data.db.entities.HistoryEntity
import com.naigen.app.ui.components.PrimaryButton
import com.naigen.app.util.DateUtils
import com.naigen.app.util.ImageSaver
import com.naigen.app.util.ShareUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

@Composable
fun AlbumScreen(vm: AlbumViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var detailItem by remember { mutableStateOf<HistoryEntity?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.album_title), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${items.size} 张",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (items.isNotEmpty()) {
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "还没有生成图片",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "去「生成」Tab 创作你的第一张图吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        AlbumThumb(item) { detailItem = item }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }

    // 详情弹窗
    detailItem?.let { item ->
        AlbumDetailDialog(
            item = item,
            onDismiss = { detailItem = null },
            onSaveToGallery = {
                val bytes = ctx.openFileInput(item.imagePath)?.use { it.readBytes() } ?: return@AlbumDetailDialog
                val uri = ImageSaver.saveToGallery(ctx, bytes)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (uri != null) "已保存到相册 Pictures/NaiGen/" else "保存失败"
                    )
                }
            },
            onShare = {
                val bytes = ctx.openFileInput(item.imagePath)?.use { it.readBytes() } ?: return@AlbumDetailDialog
                val uri = ImageSaver.saveToGallery(ctx, bytes)
                if (uri != null) {
                    ShareUtils.share(ctx, "NaiGen 生成图片", uri)
                }
            },
            onDelete = {
                vm.delete(item.id)
                detailItem = null
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.album_clear_title)) },
            text = { Text("将删除所有 ${items.size} 张图片，不可恢复。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmDeleteAll = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun AlbumThumb(item: HistoryEntity, onClick: () -> Unit) {
    val bmp = remember(item.id) {
        if (item.thumbBytes.isEmpty()) null
        else BitmapFactory.decodeByteArray(item.thumbBytes, 0, item.thumbBytes.size)
    }
    Box(
        modifier = Modifier
            .aspectRatio(832f / 1216f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.prompt,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.album_no_image)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AlbumDetailDialog(
    item: HistoryEntity,
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val bmp = remember(item.id) {
        if (item.thumbBytes.isEmpty()) null
        else BitmapFactory.decodeByteArray(item.thumbBytes, 0, item.thumbBytes.size)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.styleName, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(item.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${item.sizeKey} · ${DateUtils.duration(item.generationTimeMs)} · ${DateUtils.display(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                IconButton(onClick = onSaveToGallery) {
                    Icon(Icons.Outlined.Download, contentDescription = "相册", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = "分享", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.album_close)) } }
    )
}
