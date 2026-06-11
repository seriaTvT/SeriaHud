package org.chtholly.seriahud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        Text(stringResource(R.string.setting_display_items), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting(stringResource(R.string.setting_show_cpu_overall), config.showCpuOverall) {
            configManager.updateConfig(config.copy(showCpuOverall = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_gpu), config.showGpu) {
            configManager.updateConfig(config.copy(showGpu = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_ram), config.showRam) {
            configManager.updateConfig(config.copy(showRam = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_soc_temp), config.showSocTemp) {
            configManager.updateConfig(config.copy(showSocTemp = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_battery), config.showBattery) {
            configManager.updateConfig(config.copy(showBattery = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_fps), config.showFps) {
            configManager.updateConfig(config.copy(showFps = it))
        }

        HorizontalDivider()

        Text(stringResource(R.string.setting_cpu_cores_config), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting(stringResource(R.string.setting_show_individual_cores), config.showCpuCores) {
            configManager.updateConfig(config.copy(showCpuCores = it))
        }
        
        if (config.showCpuCores) {
            Text(stringResource(R.string.setting_select_cores), style = MaterialTheme.typography.bodyMedium)
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

        Text(stringResource(R.string.setting_advanced_features), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        SwitchSetting(stringResource(R.string.setting_show_frametime_graph), config.showFrametimeGraph) {
            configManager.updateConfig(config.copy(showFrametimeGraph = it))
        }
        
        SwitchSetting(stringResource(R.string.setting_show_record_button), config.showRecordButton) {
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
        Text(stringResource(R.string.setting_core_format, coreId), style = MaterialTheme.typography.bodyMedium)
    }
}
