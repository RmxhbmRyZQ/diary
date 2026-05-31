package com.secretdiary.app.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secretdiary.app.ui.components.LoadingIndicator
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.theme.WarmPrimary

private val MoodLabels = mapOf(
    "happy" to "开心", "excited" to "兴奋", "calm" to "平静",
    "sad" to "难过", "angry" to "生气", "anxious" to "焦虑",
    "grateful" to "感恩", "loved" to "幸福"
)
private val WeatherLabels = mapOf(
    "sunny" to "晴", "cloudy" to "多云", "rainy" to "雨",
    "snowy" to "雪", "windy" to "风", "foggy" to "雾", "stormy" to "暴风雨"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.isLoading) { LoadingIndicator(); return }

    Scaffold(
        topBar = { TopAppBar(title = { Text("统计") }) }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 总览卡片
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WarmCard(modifier = Modifier.weight(1f)) {
                Text("写作天数", style = MaterialTheme.typography.labelSmall)
                Text("${uiState.totalDays}", style = MaterialTheme.typography.headlineLarge)
            }
            WarmCard(modifier = Modifier.weight(1f)) {
                Text("总字数", style = MaterialTheme.typography.labelSmall)
                Text("${uiState.totalWords}", style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        WarmCard {
            Text("最长连续天数", style = MaterialTheme.typography.labelSmall)
            Text("${uiState.consecutiveDays} 天", style = MaterialTheme.typography.headlineLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 月度趋势柱状图
        if (uiState.monthlyCounts.isNotEmpty()) {
            WarmCard {
                Text("月度趋势", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                MonthlyBarChart(uiState.monthlyCounts)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 心情分布
        if (uiState.moodDistribution.isNotEmpty()) {
            WarmCard {
                Text("心情分布", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.moodDistribution.entries
                    .sortedByDescending { it.value }
                    .forEach { (mood, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                MoodLabels[mood] ?: mood,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$count 篇", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 天气分布
        if (uiState.weatherDistribution.isNotEmpty()) {
            WarmCard {
                Text("天气分布", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.weatherDistribution.entries
                    .sortedByDescending { it.value }
                    .forEach { (weather, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                WeatherLabels[weather] ?: weather,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$count 篇", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 年度热力图（简化为月度热力）
        if (uiState.heatmapData.isNotEmpty()) {
            WarmCard {
                Text("写作热力图", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                HeatmapGrid(uiState.heatmapData)
            }
        }
    }
    }
}

@Composable
private fun MonthlyBarChart(data: List<Pair<String, Int>>) {
    val recent = data.takeLast(12)
    Column(modifier = Modifier.fillMaxWidth()) {
        val maxCount = recent.maxOfOrNull { it.second } ?: 1
        recent.forEach { (month, count) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    month.takeLast(2) + "月",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp)
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(fraction = count.toFloat() / maxCount)
                            .height(16.dp),
                        shape = MaterialTheme.shapes.small,
                        color = WarmPrimary
                    ) {}
                }
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(24.dp)
                )
            }
        }
    }
}

@Composable
private fun HeatmapGrid(data: Map<String, Int>) {
    val colors = listOf(
        Color(0xFFF5F0E8), Color(0xFFF0E0C0), Color(0xFFE8D098),
        Color(0xFFD4A574), Color(0xFFB8855A)
    )
    val maxCount = data.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    // 按年月分组展示
    val grouped: Map<String, List<Map.Entry<String, Int>>> = data.entries
        .sortedBy { it.key }
        .groupBy { it.key.take(7) }

    val recentMonths = grouped.entries.toList().takeLast(6)

    Column {
        for ((month, entries) in recentMonths) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    month.takeLast(2) + "月",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for ((_, count) in entries) {
                        val intensity = (count.toFloat() / maxCount * (colors.size - 1)).toInt()
                            .coerceIn(0, colors.size - 1)
                        Surface(
                            modifier = Modifier.size(14.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = colors[intensity]
                        ) {}
                    }
                }
            }
        }
    }
}
