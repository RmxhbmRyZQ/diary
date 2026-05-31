package com.secretdiary.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secretdiary.app.data.remote.api.GlobalErrorHandler
import com.secretdiary.app.ui.login.LoginScreen
import com.secretdiary.app.ui.register.RegisterScreen
import com.secretdiary.app.ui.recovery.ForgotPasswordScreen
import com.secretdiary.app.ui.diarylist.DiaryListScreen
import com.secretdiary.app.ui.diaryedit.DiaryEditScreen
import com.secretdiary.app.ui.diarydetail.DiaryDetailScreen
import com.secretdiary.app.ui.statistics.StatisticsScreen
import com.secretdiary.app.ui.settings.SettingsScreen

/** 导航路由 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val MAIN = "main"
    const val DIARY_LIST = "diary_list"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val DIARY_EDIT = "diary_edit/{diaryDate}"
    const val DIARY_DETAIL = "diary_detail/{entryId}"

    fun diaryEdit(diaryDate: String) = "diary_edit/$diaryDate"
    fun diaryDetail(entryId: String) = "diary_detail/$entryId"
}

/** 底部 Tab 项 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessionExpired by sessionViewModel.sessionExpired.collectAsState()
    val timeSkew by sessionViewModel.timeSkew.collectAsState()
    val rateLimited by sessionViewModel.rateLimited.collectAsState()
    var showSessionExpiredDialog by remember { mutableStateOf(false) }
    var showTimeSkewDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }

    // 会话过期处理
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) showSessionExpiredDialog = true
    }
    LaunchedEffect(timeSkew) {
        if (timeSkew != null) showTimeSkewDialog = true
    }
    LaunchedEffect(rateLimited) {
        if (rateLimited != null) showRateLimitDialog = true
    }

    // 会话过期对话框
    if (showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("会话已失效") },
            text = { Text("账号在其他设备登录，当前会话已失效，请重新登录") },
            confirmButton = {
                TextButton(onClick = {
                    showSessionExpiredDialog = false
                    sessionViewModel.dismissSessionExpired()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }) { Text("确定") }
            }
        )
    }

    // 时间偏差对话框
    if (showTimeSkewDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("时间偏差") },
            text = { Text("客户端时间与服务器时间偏差过大，请校准系统时间后重试") },
            confirmButton = {
                TextButton(onClick = {
                    showTimeSkewDialog = false
                    sessionViewModel.dismissTimeSkew()
                }) { Text("确定") }
            }
        )
    }

    // 限流对话框
    if (showRateLimitDialog) {
        val seconds = rateLimited ?: 60
        AlertDialog(
            onDismissRequest = {},
            title = { Text("请求过于频繁") },
            text = { Text("请稍后再试（${seconds}秒后重试）") },
            confirmButton = {
                TextButton(onClick = {
                    showRateLimitDialog = false
                    sessionViewModel.dismissRateLimit()
                }) { Text("确定") }
            }
        )
    }

    val bottomItems = listOf(
        BottomNavItem(Routes.DIARY_LIST, "日记", { Icon(Icons.Default.Book, contentDescription = "日记") }),
        BottomNavItem(Routes.STATISTICS, "统计", { Icon(Icons.Default.BarChart, contentDescription = "统计") }),
        BottomNavItem(Routes.SETTINGS, "设置", { Icon(Icons.Default.Settings, contentDescription = "设置") })
    )

    val showBottomBar = currentRoute in listOf(Routes.DIARY_LIST, Routes.STATISTICS, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.DIARY_LIST) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Routes.LOGIN) { LoginScreen(navController = navController) }
            composable(Routes.REGISTER) { RegisterScreen(navController = navController) }
            composable(Routes.FORGOT_PASSWORD) { ForgotPasswordScreen(navController = navController) }

            composable(Routes.DIARY_LIST) { DiaryListScreen(navController = navController) }
            composable(Routes.STATISTICS) { StatisticsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(navController = navController) }

            composable(
                Routes.DIARY_EDIT,
                arguments = listOf(navArgument("diaryDate") { type = NavType.StringType })
            ) { backStackEntry ->
                val diaryDate = backStackEntry.arguments?.getString("diaryDate") ?: ""
                DiaryEditScreen(navController = navController, diaryDate = diaryDate)
            }

            composable(
                Routes.DIARY_DETAIL,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getString("entryId") ?: ""
                DiaryDetailScreen(navController = navController, entryId = entryId)
            }
        }
    }
}
