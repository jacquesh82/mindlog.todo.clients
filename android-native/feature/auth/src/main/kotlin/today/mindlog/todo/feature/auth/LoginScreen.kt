package today.mindlog.todo.feature.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("mindlog todo", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))

        if (state.pendingToken != null) {
            PendingEmailForm(state, viewModel)
        } else {
            CredentialsForm(state, viewModel, context, scope)
        }

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CredentialsForm(
    state: LoginUiState,
    viewModel: LoginViewModel,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    OutlinedTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))

    Button(
        onClick = viewModel::signIn,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.height(18.dp))
        } else {
            Text("Sign in")
        }
    }

    // Only drawn when the deployment says it has mindlog id configured;
    // otherwise the button would lead straight to a 503.
    if (state.providers.mindlogId) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { scope.launch { viewModel.openMindlogId(context, create = false) } },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue with mindlog id") }

        TextButton(
            onClick = { scope.launch { viewModel.openMindlogId(context, create = true) } },
            enabled = !state.busy,
        ) { Text("Create a mindlog account") }
    }
}

@Composable
private fun PendingEmailForm(state: LoginUiState, viewModel: LoginViewModel) {
    Text(
        "Your mindlog account has no email address. Add one to finish signing in.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.pendingEmail,
        onValueChange = viewModel::onPendingEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitPendingEmail() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = viewModel::submitPendingEmail,
        enabled = !state.busy && state.pendingEmail.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Finish") }
}

/**
 * Opens the identity provider in a Chrome Custom Tab.
 *
 * A Custom Tab and not a WebView: providers reject WebViews outright, and a
 * Custom Tab shares the browser's session, so a user already signed in to
 * mindlog id is not asked twice.
 */
internal suspend fun LoginViewModel.openMindlogId(context: Context, create: Boolean) {
    val url = mindlogIdUrl(create) ?: return
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(context, url.toUri())
}
