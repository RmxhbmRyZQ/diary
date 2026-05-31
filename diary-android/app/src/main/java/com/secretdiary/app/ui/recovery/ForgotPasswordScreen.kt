package com.secretdiary.app.ui.recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.secretdiary.app.ui.components.WarmButton
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.components.WarmProgressBar
import com.secretdiary.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    navController: NavHostController,
    viewModel: RecoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success) {
        if (uiState.success) navController.navigate(Routes.LOGIN) { popUpTo(0) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (uiState.step == 3) "设置新密码" else "找回密码", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        WarmCard {
            when (uiState.step) {
                1 -> {
                    OutlinedTextField(uiState.username, viewModel::onUsernameChanged, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    WarmButton("查询托管信息", onClick = viewModel::fetchRecoveryInfo, enabled = !uiState.isLoading)
                    if (uiState.isLoading) { Spacer(modifier = Modifier.height(12.dp)); WarmProgressBar() }
                }
                2 -> {
                    OutlinedTextField(uiState.recoveryPhrase, viewModel::onRecoveryPhraseChanged, label = { Text("恢复口令") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    WarmButton("验证", onClick = viewModel::verifyRecoveryPhrase, enabled = !uiState.isLoading)
                    if (uiState.isLoading) { Spacer(modifier = Modifier.height(12.dp)); WarmProgressBar() }
                }
                3 -> {
                    val vis = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                    OutlinedTextField(uiState.newPassword, viewModel::onNewPasswordChanged, label = { Text("新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = vis, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(uiState.newPasswordConfirm, viewModel::onNewPasswordConfirmChanged, label = { Text("确认新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = vis, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "切换可见") } })
                    Spacer(modifier = Modifier.height(16.dp))
                    WarmButton(if (uiState.isLoading) "重置中..." else "重置密码", onClick = viewModel::resetPassword, enabled = !uiState.isLoading)
                    if (uiState.isLoading) { Spacer(modifier = Modifier.height(12.dp)); WarmProgressBar() }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) { Text("返回登录") }
    }
    uiState.error?.let { AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("错误") }, text = { Text(it) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }) }
}
