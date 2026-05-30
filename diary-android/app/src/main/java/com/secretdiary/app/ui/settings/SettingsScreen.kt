package com.secretdiary.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 对话框状态
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var recoveryPhrase by remember { mutableStateOf("") }
    var deleteAuthPassword by remember { mutableStateOf("") }

    // 注销 / 修改密码 / 登出后跳转登录
    LaunchedEffect(uiState.message) {
        when (uiState.message) {
            "账户已注销", "密码已修改，请重新登录", "已登出" -> {
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // 同步状态
        WarmCard {
            Text("上次同步: ${uiState.lastSyncTime ?: "从未"}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = viewModel::triggerSync, enabled = !uiState.isSyncing) {
                Text(if (uiState.isSyncing) "同步中..." else "立即同步")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 生物识别
        WarmCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("生物识别登录")
                Switch(checked = uiState.isBiometricEnabled, onCheckedChange = viewModel::toggleBiometric)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 恢复口令
        WarmCard {
            Text("恢复口令托管: ${if (uiState.hasRecovery) "已开启" else "未开启"}")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showRecoveryDialog = true }) { Text("管理托管") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 修改密码
        WarmCard {
            Button(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("修改密码") }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 登出 + 注销账户
        WarmCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.weight(1f)
                ) { Text("登出") }
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text("注销账户") }
            }
        }
    }

    // ── 修改密码对话框 ──
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("修改密码") },
            text = {
                Column {
                    OutlinedTextField(oldPassword, { oldPassword = it }, label = { Text("旧密码") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("新密码") }, singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("确认新密码") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.changePassword(oldPassword, newPassword, confirmPassword)
                    showPasswordDialog = false
                    oldPassword = ""; newPassword = ""; confirmPassword = ""
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("取消") } }
        )
    }

    // ── 恢复口令托管对话框 ──
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("恢复口令托管") },
            text = {
                Column {
                    if (uiState.hasRecovery) {
                        Text("当前已开启托管。关闭后只能通过重新注册恢复账户。")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(deleteAuthPassword, { deleteAuthPassword = it }, label = { Text("输入登录密码确认") }, singleLine = true)
                    } else {
                        Text("设置恢复口令（不能与登录密码相同）")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(recoveryPhrase, { recoveryPhrase = it }, label = { Text("恢复口令") }, singleLine = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(deleteAuthPassword, { deleteAuthPassword = it }, label = { Text("登录密码（身份验证）") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (uiState.hasRecovery) {
                        viewModel.deleteRecovery(deleteAuthPassword)
                    } else {
                        viewModel.setRecovery(recoveryPhrase, deleteAuthPassword)
                    }
                    showRecoveryDialog = false
                    recoveryPhrase = ""; deleteAuthPassword = ""
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showRecoveryDialog = false }) { Text("取消") } }
        )
    }

    // ── 注销账户对话框 ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("注销账户") },
            text = {
                Column {
                    Text("此操作不可逆！将清除所有日记和附件数据。")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(deleteAuthPassword, { deleteAuthPassword = it }, label = { Text("输入密码确认") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(deleteAuthPassword)
                    showDeleteDialog = false
                    deleteAuthPassword = ""
                }) { Text("确认注销", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    // ── 消息/错误对话框 ──
    uiState.message?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("确定") } }
        )
    }
    uiState.error?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("错误") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }
        )
    }
}
