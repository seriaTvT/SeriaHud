package org.chtholly.seriahud

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.chtholly.seriahud.theme.getCardColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen() {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFileForChart by remember { mutableStateOf<File?>(null) }

    fun loadFiles() {
        val recordsDir = File(context.getExternalFilesDir(null), "records")
        if (recordsDir.exists()) {
            files = recordsDir.listFiles { _, name -> name.endsWith(".csv") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    LaunchedEffect(Unit) {
        loadFiles()
    }

    if (selectedFileForChart != null) {
        ChartScreen(file = selectedFileForChart!!, onBack = { selectedFileForChart = null })
        return
    }

    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No records found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            var showMenu by remember { mutableStateOf(false) }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { selectedFileForChart = file },
                        onLongClick = { showMenu = true }
                    ),
                colors = getCardColors()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.List, contentDescription = "CSV", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium)
                            val kb = file.length() / 1024
                            Text("$kb KB • ${dateFormat.format(Date(file.lastModified()))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export to Downloads") },
                            onClick = {
                                showMenu = false
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val destFile = File(downloadsDir, file.name)
                                try {
                                    file.copyTo(destFile, overwrite = true)
                                    Toast.makeText(context, "Exported to Downloads", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                file.delete()
                                loadFiles()
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
