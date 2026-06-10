package com.example.seriahud

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.seriahud.theme.SeriaHudTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                            title = { Text("SeriaHud", color = MaterialTheme.colorScheme.primary) }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (selectedTab == 0) {
                            HomeScreen()
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
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                Text("Start Overlay")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = {
                    val intent = Intent(this@MainActivity, OverlayService::class.java)
                    stopService(intent)
                }
            ) {
                Text("Stop Overlay")
            }
        }
    }
}
