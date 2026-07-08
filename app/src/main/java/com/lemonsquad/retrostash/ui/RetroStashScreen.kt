package com.lemonsquad.retrostash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lemonsquad.retrostash.data.remote.ArchiveCategory
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
    val isSyncingMetadata by viewModel.isSyncingMetadata.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val aiFilterEvent by viewModel.aiFilterEvent.collectAsState()
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    
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
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                SearchBar(
                    value = identifier,
                    onValueChange = { identifier = it },
                    onLoadClick = { 
                        viewModel.loadCollection(identifier) 
                    },
                    isLoading = isLoading,
                    modifier = if (isLandscape) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier.padding(16.dp)
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    edgePadding = 16.dp,
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        if (selectedCategory.ordinal < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategory.ordinal])
                            )
                        }
                    }
                ) {
                    ArchiveCategory.values().forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(category.displayName) }
                        )
                    }
                }
                
                if (selectedFolderUri == null) {
                    TextButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Please set up your destination folder in Settings.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (isLandscape) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState) { fileItem ->
                            FileItem(
                                fileItem = fileItem,
                                onDownloadClick = { viewModel.downloadFile(fileItem) },
                                enabled = selectedFolderUri != null
                            )
                        }
                    }
                } else {
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

            if (isSyncingMetadata) {
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
                            text = syncProgress,
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
