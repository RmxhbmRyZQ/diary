package com.secretdiary.app.ui.diaryedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.secretdiary.app.ui.components.WarmButton
import com.secretdiary.app.ui.diarydetail.MarkdownContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryEditScreen(
    navController: NavHostController,
    diaryDate: String,
    viewModel: DiaryEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val imageLoader = remember { coil.Coil.imageLoader(context.applicationContext) }
    LaunchedEffect(diaryDate) { viewModel.loadDiary(diaryDate) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) navController.popBackStack() }

    // 本地 TextFieldValue 追踪光标位置，避免 ViewModel 依赖 Compose 类型
    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.content)) }
    LaunchedEffect(uiState.content) {
        if (textFieldValue.text != uiState.content) {
            textFieldValue = TextFieldValue(uiState.content)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addImage(it, textFieldValue.selection.start) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "新建日记" else "编辑日记") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = viewModel::togglePreview) {
                        Icon(
                            if (uiState.isPreviewing) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (uiState.isPreviewing) "编辑" else "预览"
                        )
                    }
                    IconButton(onClick = viewModel::onFavoriteToggled) {
                        Icon(if (uiState.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                if (!uiState.isPreviewing) {
                    WarmButton(
                        text = if (uiState.isLoading) "保存中..." else "保存",
                        onClick = viewModel::saveDiary,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        if (uiState.isPreviewing) {
            // 预览模式：渲染 Markdown
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(uiState.title.ifBlank { "(无标题)" }, style = MaterialTheme.typography.headlineLarge)
                Text(uiState.diaryDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    uiState.mood?.let { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    uiState.weather?.let { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                MarkdownContent(uiState.content, context)
            }
        } else {
            // 编辑模式 — 可滚动
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 点击日期弹出选择器
                var showDatePicker by remember { mutableStateOf(false) }
                val datePickerState = rememberDatePickerState()
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日记日期: ${uiState.diaryDate}", style = MaterialTheme.typography.bodyLarge)
                        Text("选择", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = java.time.Instant.ofEpochMilli(millis)
                                        .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                        .toLocalDate()
                                    viewModel.onDateChanged(date.toString())
                                }
                                showDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(uiState.title, viewModel::onTitleChanged, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                // 心情 Chips（可左右滑动）
                Text("心情", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("happy" to "开心", "excited" to "兴奋", "calm" to "平静", "sad" to "难过", "angry" to "生气", "anxious" to "焦虑", "grateful" to "感恩", "loved" to "幸福").forEach { (key, label) ->
                        FilterChip(selected = uiState.mood == key, onClick = { viewModel.onMoodChanged(if (uiState.mood == key) null else key) }, label = { Text(label) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 天气 Chips（可左右滑动）
                Text("天气", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("sunny" to "晴", "cloudy" to "多云", "rainy" to "雨", "snowy" to "雪", "windy" to "风", "foggy" to "雾", "stormy" to "暴风雨").forEach { (key, label) ->
                        FilterChip(selected = uiState.weather == key, onClick = { viewModel.onWeatherChanged(if (uiState.weather == key) null else key) }, label = { Text(label) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 标签
                OutlinedTextField(uiState.tags, viewModel::onTagsChanged, label = { Text("标签（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (uiState.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                            InputChip(selected = false, onClick = {}, label = { Text(tag) })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 附件区域
                if (uiState.pendingImages.isNotEmpty() || uiState.attachmentIds.isNotEmpty()) {
                    Text("附件（点击 × 删除）", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 已有附件 — 可点击删除
                        itemsIndexed(uiState.attachmentIds) { _, attId ->
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 2.dp
                            ) {
                                Box {
                                    AsyncImage(
                                        model = Uri.parse("attachment:$attId"),
                                        contentDescription = null,
                                        imageLoader = imageLoader,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeExistingAttachment(attId) },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Close, "删除附件", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        // 待上传的图片
                        itemsIndexed(uiState.pendingImages) { index, (_, uri) ->
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 2.dp
                            ) {
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { viewModel.removePendingImage(index) },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Close, "移除", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 图片添加按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加图片")
                    }
                    if (!uiState.isOnline) {
                        Text("离线模式 - 无法添加附件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 内容 — 更大区域（使用 TextFieldValue 追踪光标位置，供 addImage 使用）
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        viewModel.onContentChanged(newValue.text)
                    },
                    label = { Text("日记内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    maxLines = Int.MAX_VALUE
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    uiState.error?.let { AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("保存失败") }, text = { Text(it) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
