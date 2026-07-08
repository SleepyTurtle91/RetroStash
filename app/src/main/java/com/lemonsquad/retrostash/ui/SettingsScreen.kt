package com.lemonsquad.retrostash.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
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
import com.lemonsquad.retrostash.BuildConfig
import com.lemonsquad.retrostash.ui.theme.RetroStashTheme
import com.lemonsquad.retrostash.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val isAiAuditorEnabled by viewModel.isAiAuditorEnabled.collectAsState()
    val maxActiveDownloads by viewModel.maxActiveDownloads.collectAsState()
    val apiHealthStatus by viewModel.apiHealthStatus.collectAsState()
    val isCheckingHealth by viewModel.isCheckingHealth.collectAsState()
    val sdCardUri by viewModel.sdCardUri.collectAsState()
    val syncFolderUri by viewModel.syncFolderUri.collectAsState()
    
    SettingsScreenContent(
        geminiApiKey = geminiApiKey,
        isAiAuditorEnabled = isAiAuditorEnabled,
        maxActiveDownloads = maxActiveDownloads,
        apiHealthStatus = apiHealthStatus,
        isCheckingHealth = isCheckingHealth,
        sdCardUri = sdCardUri,
        syncFolderUri = syncFolderUri,
        onBackClick = onBackClick,
        onSaveApiKey = { viewModel.saveGeminiApiKey(it) },
        onClearApiKey = { viewModel.clearGeminiApiKey() },
        onToggleAiAuditor = { viewModel.setAiAuditorEnabled(it) },
        onMaxDownloadsChange = { viewModel.setMaxActiveDownloads(it) },
        onCheckHealth = { viewModel.checkApiHealth(it) },
        onDismissHealthStatus = { viewModel.clearApiHealthStatus() },
        onSaveSdCardUri = { viewModel.saveSdCardUri(it) },
        onSaveSyncFolderUri = { viewModel.saveSyncFolderUri(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    geminiApiKey: String?,
    isAiAuditorEnabled: Boolean,
    maxActiveDownloads: Int,
    apiHealthStatus: Result<String>?,
    isCheckingHealth: Boolean,
    sdCardUri: String?,
    syncFolderUri: String?,
    onBackClick: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onToggleAiAuditor: (Boolean) -> Unit,
    onMaxDownloadsChange: (Int) -> Unit,
    onCheckHealth: (String) -> Unit,
    onDismissHealthStatus: () -> Unit,
    onSaveSdCardUri: (Uri) -> Unit,
    onSaveSyncFolderUri: (Uri) -> Unit
) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }

    val sdCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onSaveSdCardUri(it) }
    }

    val syncFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onSaveSyncFolderUri(it) }
    }

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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
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

            OutlinedButton(
                onClick = { onCheckHealth(apiKeyInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKeyInput.isNotBlank() && !isCheckingHealth
            ) {
                if (isCheckingHealth) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check Connectivity")
                }
            }

            AnimatedVisibility(visible = apiHealthStatus != null) {
                apiHealthStatus?.let { result ->
                    val color = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    val message = if (result.isSuccess) result.getOrThrow() else "Connection Failed: ${result.exceptionOrNull()?.message}"
                    
                    Surface(
                        color = color.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissHealthStatus) {
                                Text("Dismiss", color = color)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Storage Configuration",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )

            FolderSelectionItem(
                title = "Download Destination (SD Card)",
                subtitle = "Where ROMs and files will be downloaded.",
                uri = sdCardUri,
                onClick = { sdCardLauncher.launch(null) }
            )

            FolderSelectionItem(
                title = "Scraper Folder Path",
                subtitle = "Folder to scan for metadata and box art sync.",
                uri = syncFolderUri,
                onClick = { syncFolderLauncher.launch(null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Silent AI Auditor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically refine search results using AI.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isAiAuditorEnabled,
                    onCheckedChange = onToggleAiAuditor,
                    enabled = geminiApiKey != null
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Max Active Downloads", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Limit how many files download at once to save bandwidth.",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = maxActiveDownloads.toFloat(),
                    onValueChange = { onMaxDownloadsChange(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text(
                    text = "$maxActiveDownloads files",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.End)
                )
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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun FolderSelectionItem(
    title: String,
    subtitle: String,
    uri: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                if (uri != null) {
                    Text(
                        text = "Path: ${Uri.decode(uri).substringAfterLast(":")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (uri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    RetroStashTheme {
        SettingsScreenContent(
            geminiApiKey = null,
            isAiAuditorEnabled = false,
            maxActiveDownloads = 2,
            apiHealthStatus = null,
            isCheckingHealth = false,
            sdCardUri = null,
            syncFolderUri = null,
            onBackClick = {},
            onSaveApiKey = {},
            onClearApiKey = {},
            onToggleAiAuditor = {},
            onMaxDownloadsChange = {},
            onCheckHealth = {},
            onDismissHealthStatus = {},
            onSaveSdCardUri = {},
            onSaveSyncFolderUri = {}
        )
    }
}
