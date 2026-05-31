package com.secretdiary.app.ui.diarydetail

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.secretdiary.app.ui.components.LoadingIndicator
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.navigation.Routes
import io.noties.markwon.Markwon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryDetailScreen(
    navController: NavHostController,
    entryId: String,
    viewModel: DiaryDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showConflictMsg by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) { viewModel.loadDiary(entryId) }
    LaunchedEffect(uiState.deleted) { if (uiState.deleted) navController.popBackStack() }
    LaunchedEffect(uiState.error) { if (uiState.error != null) showConflictMsg = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记详情") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(@Suppress("DEPRECATION") Icons.Default.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { uiState.diary?.let { navController.navigate(Routes.diaryEdit(it.diaryDate)) } }) { Icon(Icons.Default.Edit, "编辑") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "删除") }
                }
            )
        }
    ) { padding ->
        val diary = uiState.diary
        if (uiState.isLoading) LoadingIndicator(Modifier.padding(padding))
        else if (diary != null) {
            Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                WarmCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(diary.title.ifEmpty { diary.diaryDate }, style = MaterialTheme.typography.headlineLarge)
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(if (diary.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(diary.diaryDate, style = MaterialTheme.typography.labelSmall)

                    Spacer(modifier = Modifier.height(8.dp))
                    // 可编辑的心情/天气
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("happy" to "开心", "excited" to "兴奋", "calm" to "平静", "sad" to "难过", "angry" to "生气", "anxious" to "焦虑", "grateful" to "感恩", "loved" to "幸福").forEach { (key, label) ->
                            FilterChip(
                                selected = diary.mood == key,
                                onClick = { viewModel.updateMood(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("sunny" to "晴", "cloudy" to "多云", "rainy" to "雨", "snowy" to "雪", "windy" to "风", "foggy" to "雾", "stormy" to "暴风雨").forEach { (key, label) ->
                            FilterChip(
                                selected = diary.weather == key,
                                onClick = { viewModel.updateWeather(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Markdown 渲染（附件图片通过 AttachmentFetcher + AsyncImage 加载）
                    MarkdownContent(diary.content, context)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除日记") },
            text = { Text("确定删除这篇日记？删除后不可恢复") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDiary(entryId); showDeleteConfirm = false }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
    if (showConflictMsg && uiState.error != null) {
        AlertDialog(
            onDismissRequest = { showConflictMsg = false; viewModel.clearError() },
            title = { Text("提示") },
            text = { Text(uiState.error ?: "") },
            confirmButton = { TextButton(onClick = { showConflictMsg = false; viewModel.clearError() }) { Text("确定") } }
        )
    }
}

/**
 * 按原始顺序渲染 Markdown：文本段用 Markwon 渲染，图片段用 Coil AsyncImage + AttachmentFetcher 解密显示。
 */
@Composable
fun MarkdownContent(markdown: String, context: Context) {
    val imageLoader = remember { coil.Coil.imageLoader(context.applicationContext) }
    val markwon = remember {
        Markwon.builder(context.applicationContext)
            .usePlugin(io.noties.markwon.image.coil.CoilImagesPlugin.create(context.applicationContext))
            .build()
    }

    val segments = remember(markdown) {
        val result = mutableListOf<MarkdownSegment>()
        val regex = Regex("!\\[[^\\]]*\\]\\(attachment:([^)]+)\\)")
        var lastIndex = 0
        regex.findAll(markdown).forEach { match ->
            val textBefore = markdown.substring(lastIndex, match.range.first)
            if (textBefore.isNotEmpty()) {
                result.add(MarkdownSegment.Text(textBefore))
            }
            result.add(MarkdownSegment.Image(match.groupValues[1]))
            lastIndex = match.range.last + 1
        }
        val remaining = markdown.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            result.add(MarkdownSegment.Text(remaining))
        }
        if (result.isEmpty()) {
            result.add(MarkdownSegment.Text(markdown))
        }
        result.toList()
    }

    Column {
        for (i in segments.indices) {
            key("seg$i") {
                when (val segment = segments[i]) {
                    is MarkdownSegment.Text -> {
                        val textContent = segment.content
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    movementMethod = LinkMovementMethod.getInstance()
                                    textSize = 16f
                                }.also { markwon.setMarkdown(it, textContent) }
                            },
                            update = { markwon.setMarkdown(it, textContent) }
                        )
                    }
                    is MarkdownSegment.Image -> {
                        AsyncImage(
                            model = android.net.Uri.parse("attachment:${segment.attachmentId}"),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 400.dp)
                                .padding(vertical = 4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

/** Markdown 解析出的有序片段 */
private sealed class MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment()
    data class Image(val attachmentId: String) : MarkdownSegment()
}
