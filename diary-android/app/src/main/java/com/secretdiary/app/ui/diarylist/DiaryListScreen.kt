package com.secretdiary.app.ui.diarylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.secretdiary.app.domain.model.Diary
import com.secretdiary.app.ui.components.EmptyState
import com.secretdiary.app.ui.components.LoadingIndicator
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.navigation.Routes
import com.secretdiary.app.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiaryListScreen(
    navController: NavHostController,
    viewModel: DiaryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showFilterRow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "搜索") }
                    IconButton(onClick = { showFilterRow = !showFilterRow }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = if (uiState.filterMood != null || uiState.filterWeather != null || uiState.filterFavorites || uiState.selectedYear != null)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.diaryEdit(TimeUtils.todayBeijing())) },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "新建日记", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::search,
                    placeholder = { Text("搜索日记...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true
                )
            }
            if (showFilterRow) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // 第一行：全部 + 收藏
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = uiState.filterMood == null && uiState.filterWeather == null && !uiState.filterFavorites && uiState.selectedYear == null, onClick = { viewModel.clearFilters() }, label = { Text("全部") })
                        FilterChip(selected = uiState.filterFavorites, onClick = { viewModel.setFilterFavorites(!uiState.filterFavorites) }, label = { Icon(Icons.Default.Star, "收藏", modifier = Modifier.size(18.dp)) })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 日期级联筛选：年 → 月 → 日
                    Text("日期", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.availableYears.forEach { year ->
                            FilterChip(
                                selected = uiState.selectedYear == year,
                                onClick = { viewModel.setFilterYear(year) },
                                label = { Text("${year}年") }
                            )
                        }
                    }
                    if (uiState.selectedYear != null && uiState.availableMonths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.availableMonths.forEach { month ->
                                FilterChip(
                                    selected = uiState.selectedMonth == month,
                                    onClick = { viewModel.setFilterMonth(month) },
                                    label = { Text("${month}月") }
                                )
                            }
                        }
                    }
                    if (uiState.selectedMonth != null && uiState.availableDays.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.availableDays.forEach { day ->
                                FilterChip(
                                    selected = uiState.selectedDay == day,
                                    onClick = { viewModel.setFilterDay(day) },
                                    label = { Text("${day}日") }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 心情（可左右滑动）
                    Text("心情", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("happy" to "开心", "excited" to "兴奋", "calm" to "平静", "sad" to "难过", "angry" to "生气", "anxious" to "焦虑", "grateful" to "感恩", "loved" to "幸福").forEach { (key, label) ->
                            FilterChip(selected = uiState.filterMood == key, onClick = { viewModel.setFilterMood(if (uiState.filterMood == key) null else key) }, label = { Text(label) })
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 天气（可左右滑动）
                    Text("天气", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("sunny" to "晴", "cloudy" to "多云", "rainy" to "雨", "snowy" to "雪", "windy" to "风", "foggy" to "雾", "stormy" to "暴风雨").forEach { (key, label) ->
                            FilterChip(selected = uiState.filterWeather == key, onClick = { viewModel.setFilterWeather(if (uiState.filterWeather == key) null else key) }, label = { Text(label) })
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                LoadingIndicator()
            } else if (uiState.diaries.isEmpty()) {
                EmptyState("还没有日记，点击右下角开始写日记吧")
            } else {
                val grouped = viewModel.groupByMonth(uiState.diaries)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (month, diaries) ->
                        item { Text(month, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(diaries, key = { it.id }) { diary ->
                            DiaryCard(
                                diary = diary,
                                onClick = { navController.navigate(Routes.diaryDetail(diary.id)) },
                                onLongClick = { viewModel.toggleFavorite(diary.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiaryCard(diary: Diary, onClick: () -> Unit, onLongClick: () -> Unit) {
    WarmCard(
        onClick = onClick,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(diary.title.ifEmpty { diary.diaryDate }, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(diary.diaryDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    diary.mood?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    diary.weather?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
            if (diary.favorite) Icon(Icons.Default.Star, "收藏", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(diary.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}
