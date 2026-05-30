package com.secretdiary.app.ui.register

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
import com.secretdiary.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    navController: NavHostController,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.registerSuccess) {
        if (uiState.registerSuccess) navController.popBackStack()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("创建账户", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        WarmCard {
            OutlinedTextField(uiState.username, viewModel::onUsernameChanged, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.password, onValueChange = viewModel::onPasswordChanged,
                label = { Text("密码") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "切换可见") } }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.passwordConfirm, onValueChange = viewModel::onPasswordConfirmChanged,
                label = { Text("确认密码") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(20.dp))
            WarmButton(if (uiState.isLoading) "注册中..." else "注册", onClick = viewModel::register, enabled = !uiState.isLoading)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) { Text("已有账户？登录") }
    }
    uiState.error?.let {
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("注册失败") }, text = { Text(it) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } })
    }
}
