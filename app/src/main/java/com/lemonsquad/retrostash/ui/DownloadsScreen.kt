package com.lemonsquad.retrostash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lemonsquad.retrostash.data.model.DownloadStatus
import com.lemonsquad.retrostash.data.model.DownloadTask
import com.lemonsquad.retrostash.ui.viewmodel.DownloadsViewModel

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    viewModel: DownloadsViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }) {
                        IconButton(onClick = { viewModel.clearCompletedTasks() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear Finished")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No active downloads")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tasks) { task ->
                    DownloadTaskItem(
                        task = task,
                        onCancelClick = { viewModel.cancelTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val statusText = when (task.status) {
                    DownloadStatus.QUEUED -> "Waiting in Queue..."
                    DownloadStatus.DOWNLOADING -> "Downloading..."
                    DownloadStatus.EXTRACTING -> "Extracting..."
                    DownloadStatus.COMPLETED -> "Completed"
                    DownloadStatus.FAILED -> "Failed"
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    if (task.status == DownloadStatus.DOWNLOADING && task.totalSize > 0) {
                        Text(
                            text = "${formatSize(task.downloadedSize)} / ${formatSize(task.totalSize)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.EXTRACTING) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (task.status == DownloadStatus.COMPLETED) {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (task.status != DownloadStatus.COMPLETED) {
                IconButton(onClick = onCancelClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Cancel Download",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
