package org.chtholly.seriahud

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.topjohnwu.superuser.Shell
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chtholly.seriahud.theme.SeriaHudTheme
import java.io.BufferedReader
import java.io.FileReader

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var configManager: HudConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configManager = HudConfigManager(this)
        enableEdgeToEdge()

        setContent {
            SeriaHudTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary) }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text(stringResource(R.string.title_home)) },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, contentDescription = "Records") },
                                label = { Text(stringResource(R.string.title_records)) },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text(stringResource(R.string.title_settings)) },
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (selectedTab == 0) {
                            HomeScreen()
                        } else if (selectedTab == 1) {
                            RecordsScreen()
                        } else {
                            SettingsScreen(configManager)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HomeScreen() {
        var hardwarePaths by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var isPathsExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                // Determine platform and initialize provider to get paths
                val platform = Shell.cmd("getprop ro.board.platform").exec().out.joinToString("").lowercase()
                val provider = if (platform.startsWith("mt")) {
                    MtkHardwareProvider()
                } else {
                    QcomHardwareProvider()
                }
                hardwarePaths = provider.getDetectedPaths()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_system_info_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        InfoRow(stringResource(R.string.sys_model), Build.MODEL)
                        val soc = Shell.cmd("getprop ro.soc.model").exec().out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: Build.HARDWARE
                        InfoRow(stringResource(R.string.sys_soc), soc)
                        val memInfo = ActivityManager.MemoryInfo()
                        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
                        val totalRam = memInfo.totalMem / (1024 * 1024 * 1024f)
                        InfoRow(stringResource(R.string.sys_ram), String.format("%.2f GB", totalRam))
                    }
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { isPathsExpanded = !isPathsExpanded },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.card_paths_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        AnimatedVisibility(visible = isPathsExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                if (hardwarePaths.isEmpty()) {
                                    Text("Detecting...", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    hardwarePaths.forEach { (key, value) ->
                                        Text(
                                            text = "$key: $value",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } else {
                            val intent = Intent(this@MainActivity, OverlayService::class.java)
                            startService(intent)
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_start_monitor))
                }
            }
            
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val intent = Intent(this@MainActivity, OverlayService::class.java)
                        stopService(intent)
                    }
                ) {
                    Text(stringResource(R.string.btn_stop_monitor))
                }
            }
        }
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
