package com.example.seriahud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(configManager: HudConfigManager) {
    val config by configManager.configFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Display Items", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting("Show CPU Overall", config.showCpuOverall) {
            configManager.updateConfig(config.copy(showCpuOverall = it))
        }
        
        SwitchSetting("Show GPU", config.showGpu) {
            configManager.updateConfig(config.copy(showGpu = it))
        }
        
        SwitchSetting("Show RAM", config.showRam) {
            configManager.updateConfig(config.copy(showRam = it))
        }
        
        SwitchSetting("Show SoC Temp", config.showSocTemp) {
            configManager.updateConfig(config.copy(showSocTemp = it))
        }
        
        SwitchSetting("Show Battery", config.showBattery) {
            configManager.updateConfig(config.copy(showBattery = it))
        }
        
        SwitchSetting("Show FPS / Frametime", config.showFps) {
            configManager.updateConfig(config.copy(showFps = it))
        }

        HorizontalDivider()

        Text("CPU Cores Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting("Show Individual CPU Cores", config.showCpuCores) {
            configManager.updateConfig(config.copy(showCpuCores = it))
        }
        
        if (config.showCpuCores) {
            Text("Select Cores to Monitor:", style = MaterialTheme.typography.bodyMedium)
            // 8 cores total
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0..3) {
                    CoreCheckbox(coreId = i, selected = config.selectedCpuCores.contains(i)) { checked ->
                        val newList = config.selectedCpuCores.toMutableList()
                        if (checked) newList.add(i) else newList.remove(i)
                        configManager.updateConfig(config.copy(selectedCpuCores = newList.sorted()))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 4..7) {
                    CoreCheckbox(coreId = i, selected = config.selectedCpuCores.contains(i)) { checked ->
                        val newList = config.selectedCpuCores.toMutableList()
                        if (checked) newList.add(i) else newList.remove(i)
                        configManager.updateConfig(config.copy(selectedCpuCores = newList.sorted()))
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Advanced Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting("Show Real-time Frametime Graph", config.showFrametimeGraph) {
            configManager.updateConfig(config.copy(showFrametimeGraph = it))
        }
        
        SwitchSetting("Show Data Record Button", config.showRecordButton) {
            configManager.updateConfig(config.copy(showRecordButton = it))
        }
    }
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CoreCheckbox(coreId: Int, selected: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = onCheckedChange)
        Text("Core $coreId", style = MaterialTheme.typography.bodyMedium)
    }
}
