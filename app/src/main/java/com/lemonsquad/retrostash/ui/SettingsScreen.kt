package com.lemonsquad.retrostash.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lemonsquad.retrostash.ui.theme.RetroStashTheme
import com.lemonsquad.retrostash.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    
    SettingsScreenContent(
        geminiApiKey = geminiApiKey,
        onBackClick = onBackClick,
        onSaveApiKey = { viewModel.saveGeminiApiKey(it) },
        onClearApiKey = { viewModel.clearGeminiApiKey() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    geminiApiKey: String?,
    onBackClick: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit
) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }

    // Update input field when the stored key changes (initial load)
    LaunchedEffect(geminiApiKey) {
        if (geminiApiKey != null) {
            apiKeyInput = geminiApiKey
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Gemini API Configuration",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "RetroStash uses Gemini for intelligent features. Please provide your own API key to enable them.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("Paste your API key here") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Button(
                onClick = { onSaveApiKey(apiKeyInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKeyInput.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save API Key")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://aistudio.google.com/".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Get Free API Key")
            }
            
            if (geminiApiKey != null) {
                TextButton(
                    onClick = { 
                        onClearApiKey()
                        apiKeyInput = ""
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Saved Key")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    RetroStashTheme {
        SettingsScreenContent(
            geminiApiKey = null,
            onBackClick = {},
            onSaveApiKey = {},
            onClearApiKey = {}
        )
    }
}
