package com.catmap.dubai.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import kotlinx.coroutines.delay

private fun NavHostController.goHome() = navigate(Routes.MAP) { popUpTo(0) { inclusive = true } }

@Composable
private fun AuthColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, imeAction: ImeAction = ImeAction.Next) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BusyButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(text)
    }
}

@Composable
fun LoginScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // The logo drops in with a bounce, then gently floats.
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val float by rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )

    AuthColumn {
        Image(
            painterResource(R.drawable.logo), null,
            Modifier.size(120.dp).graphicsLayer {
                translationY = (1f - drop.value) * -120f + float * -10f
                alpha = drop.value.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * drop.value
                scaleY = 0.8f + 0.2f * drop.value
            },
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tagline), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            email, { email = it.trim() },
            label = { Text(stringResource(R.string.email_or_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(password, { password = it }, stringResource(R.string.password), ImeAction.Done)
        BusyButton(stringResource(R.string.login), runner.busy, email.isNotBlank() && password.isNotBlank()) {
            runner.run(onSuccess = {
                if (container.authRepo.user.value?.isEmailVerified == true) nav.goHome()
                else nav.navigate(Routes.VERIFY) { popUpTo(0) { inclusive = true } }
            }) { container.authRepo.login(email, password) }
        }
        TextButton(onClick = { nav.navigate(Routes.FORGOT) }) { Text(stringResource(R.string.forgot_password)) }
        OutlinedButton(onClick = { nav.navigate(Routes.REGISTER) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.create_account))
        }
    }
}

@Composable
fun RegisterScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != password

    Scaffold(topBar = { AppTopBar(stringResource(R.string.create_account), onBack = { nav.popBackStack() }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AuthColumn {
                OutlinedTextField(
                    displayName, { displayName = it.take(60) },
                    label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    username, { username = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) },
                    label = { Text(stringResource(R.string.username)) },
                    supportingText = { Text(stringResource(R.string.username_rules)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    email, { email = it.trim() },
                    label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(password, { password = it }, stringResource(R.string.password_min))
                PasswordField(confirm, { confirm = it }, stringResource(R.string.confirm_password), ImeAction.Done)
                if (mismatch) Text(stringResource(R.string.passwords_dont_match), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.register_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BusyButton(
                    stringResource(R.string.create_account), runner.busy,
                    username.length >= 3 && email.contains('@') && password.length >= 8 && !mismatch && confirm.isNotEmpty(),
                ) {
                    runner.run(onSuccess = { nav.navigate(Routes.VERIFY) { popUpTo(0) { inclusive = true } } }) {
                        container.authRepo.register(email, password, username, displayName)
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.reset_password), onBack = { nav.popBackStack() }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AuthColumn {
                Text(stringResource(R.string.reset_password_hint))
                OutlinedTextField(
                    email, { email = it.trim() },
                    label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                BusyButton(stringResource(R.string.send_reset_link), runner.busy, email.contains('@')) {
                    runner.run(onSuccess = { sent = true }) { container.authRepo.sendPasswordReset(email) }
                }
                if (sent) {
                    Card { Text(stringResource(R.string.reset_sent, email), Modifier.padding(16.dp)) }
                    TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.back_to_login)) }
                }
            }
        }
    }
}

@Composable
fun VerifyEmailScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    var cooldown by rememberSaveable { mutableIntStateOf(0) }
    val notYet = stringResource(R.string.not_verified_yet)
    val resent = stringResource(R.string.verification_resent)

    // Check automatically every few seconds, so returning from the mail app is enough.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (runCatching { container.authRepo.reloadVerified() }.getOrDefault(false)) {
                nav.goHome()
                break
            }
        }
    }
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1_000)
            cooldown--
        }
    }

    AuthColumn {
        Icon(Icons.Default.MarkEmailRead, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.verify_email_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.verify_email_body, container.authRepo.email()), textAlign = TextAlign.Center)
        BusyButton(stringResource(R.string.i_verified), runner.busy) {
            runner.run {
                if (container.authRepo.reloadVerified()) nav.goHome() else com.catmap.dubai.data.AppErrors.show(notYet)
            }
        }
        OutlinedButton(
            onClick = {
                runner.run(resent, onSuccess = { cooldown = 60 }) { container.authRepo.resendVerification() }
            },
            enabled = cooldown == 0 && !runner.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (cooldown > 0) stringResource(R.string.resend_in, cooldown) else stringResource(R.string.resend_email))
        }
        TextButton(onClick = {
            container.authRepo.logout()
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }) { Text(stringResource(R.string.use_other_account)) }
    }
}

@Composable
fun SetupRequiredScreen() {
    AuthColumn {
        Image(painterResource(R.drawable.logo), null, Modifier.size(110.dp))
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.setup_body))
        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_steps), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
