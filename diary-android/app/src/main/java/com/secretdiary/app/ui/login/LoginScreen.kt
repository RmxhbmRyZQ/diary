package com.secretdiary.app.ui.login

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.secretdiary.app.ui.components.WarmButton
import com.secretdiary.app.ui.components.WarmCard
import com.secretdiary.app.ui.components.WarmProgressBar
import com.secretdiary.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            navController.navigate(Routes.DIARY_LIST) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "隐秘日记",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "隐秘日记",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(40.dp))

        WarmCard {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "切换密码可见"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            WarmButton(
                text = "登录",
                onClick = viewModel::login,
                enabled = !uiState.isLoading
            )

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                WarmProgressBar()
            }

            if (uiState.isBiometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                val activity = LocalContext.current.findFragmentActivity()
                OutlinedButton(
                    onClick = { activity?.let { viewModel.startBiometricAuth(it) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("指纹/面容登录")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { navController.navigate(Routes.REGISTER) }) {
                Text("注册")
            }
            TextButton(onClick = { navController.navigate(Routes.FORGOT_PASSWORD) }) {
                Text("忘记密码？")
            }
        }
    }

    uiState.error?.let {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("登录失败") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } }
        )
    }
}

/** 沿 ContextWrapper 链解包找到 FragmentActivity，兼容 Compose 对 Context 的包装 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = if (ctx is ContextWrapper) ctx.baseContext else null
    }
    return null
}
