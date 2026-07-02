package com.lemonsquad.retrostash.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lemonsquad.retrostash.ui.viewmodel.FileItemState
import com.lemonsquad.retrostash.ui.viewmodel.FileStatus

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onLoadClick: () -> Unit,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text("Search Games (e.g. PS2)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onLoadClick() })
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onLoadClick,
            enabled = !isLoading && value.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Load")
            }
        }
    }
}

@Composable
fun FileItem(
    fileItem: FileItemState,
    onDownloadClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.file.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Size: ${fileItem.file.size ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            val buttonText = when (fileItem.status) {
                FileStatus.IDLE -> "Download"
                FileStatus.DOWNLOADING -> "Downloading..."
                FileStatus.EXTRACTING -> "Extracting..."
                FileStatus.COMPLETED -> "Done"
                FileStatus.ERROR -> "Error"
            }
            
            Button(
                onClick = onDownloadClick,
                enabled = enabled && fileItem.status == FileStatus.IDLE
            ) {
                Text(buttonText)
            }
        }
    }
}
