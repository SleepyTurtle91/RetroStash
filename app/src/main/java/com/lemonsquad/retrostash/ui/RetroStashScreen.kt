package com.lemonsquad.retrostash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lemonsquad.retrostash.ui.components.FileItem
import com.lemonsquad.retrostash.ui.components.SearchBar
import com.lemonsquad.retrostash.ui.theme.RetroStashTheme
import com.lemonsquad.retrostash.ui.viewmodel.AiFilterEvent
import com.lemonsquad.retrostash.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroStashScreen(
    onSettingsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isAiFiltering by viewModel.isAiFiltering.collectAsState()
    val aiFilterEvent by viewModel.aiFilterEvent.collectAsState()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    
    var identifier by remember { mutableStateOf("") }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(aiFilterEvent) {
        aiFilterEvent?.let { event ->
            when (event) {
                is AiFilterEvent.MissingKey -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Gemini API key is missing.",
                        actionLabel = "Settings",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onSettingsClick()
                    }
                }
                is AiFilterEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
            viewModel.consumeAiFilterEvent()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.saveFolderUri(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("RetroStash") },
                actions = {
                    IconButton(onClick = onDownloadsClick) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { launcher.launch(null) }) {
                        Icon(
                            imageVector = if (selectedFolderUri == null) 
                                Icons.Filled.Folder 
                            else 
                                Icons.Filled.FolderSpecial,
                            contentDescription = "Select SD Card Folder"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                SearchBar(
                    value = identifier,
                    onValueChange = { identifier = it },
                    onLoadClick = { viewModel.loadCollection(identifier) },
                    isLoading = isLoading
                )
                
                if (selectedFolderUri == null) {
                    Text(
                        text = "Please select a destination folder (SD Card) using the folder icon above.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState) { fileItem ->
                        FileItem(
                            fileItem = fileItem,
                            onDownloadClick = { viewModel.downloadFile(fileItem) },
                            enabled = selectedFolderUri != null
                        )
                    }
                }
            }

            if (isAiFiltering) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "AI is sorting archive contents...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RetroStashScreenPreview() {
    RetroStashTheme {
        Surface {
            Column {
                SearchBar(value = "example_collection", onValueChange = {}, onLoadClick = {}, isLoading = false)
                FileItem(
                    fileItem = com.lemonsquad.retrostash.ui.viewmodel.FileItemState(
                        com.lemonsquad.retrostash.data.model.ArchiveFile("game.zip", "ZIP", "100 MB"),
                        com.lemonsquad.retrostash.ui.viewmodel.FileStatus.IDLE,
                        identifier = "example_id",
                        uploader = "example_uploader"
                    ),
                    onDownloadClick = {}
                )
            }
        }
    }
}
