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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import org.chtholly.seriahud.theme.AppearanceConfig
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(configManager: HudConfigManager) {
    val config by configManager.configFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.setting_appearance_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        val contentResolver = context.contentResolver
        val pickImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    AppearanceConfig.updateBackgroundUri(uri.toString())
                    AppearanceConfig.save(context)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to grant URI permission", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_pick_background), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.setting_pick_background_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { pickImageLauncher.launch(arrayOf("image/*")) }) {
                Text("Select")
            }
        }

        if (AppearanceConfig.backgroundImageUri != null) {
            Button(
                onClick = { 
                    AppearanceConfig.updateBackgroundUri(null)
                    AppearanceConfig.save(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.setting_clear_background))
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.setting_card_alpha), style = MaterialTheme.typography.bodyLarge)
                Text("${(AppearanceConfig.cardAlpha * 100).roundToInt()}%")
            }
            Slider(
                value = AppearanceConfig.cardAlpha,
                onValueChange = { AppearanceConfig.updateAlpha(it) },
                onValueChangeFinished = { AppearanceConfig.save(context) },
                valueRange = 0f..1f
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.setting_diagnostic), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.setting_diagnostic_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Button(
            onClick = {
                val successMsg = context.getString(R.string.diagnostic_success)
                val timeoutMsg = context.getString(R.string.diagnostic_timeout)
                Toast.makeText(context, "Running diagnostic...", Toast.LENGTH_SHORT).show()
                coroutineScope.launch(Dispatchers.IO) {
                    val script = """
                        TIMESTAMP=${'$'}(date +%Y%m%d_%H%M%S)
                        OUTPUT_FILE="/sdcard/Download/seriahud_diagnostic_${'$'}{TIMESTAMP}.txt"
                        echo "SeriaHud Hardware Diagnostic Report" > "${'$'}OUTPUT_FILE"
                        echo "===================================" >> "${'$'}OUTPUT_FILE"
                        echo "Date: ${'$'}(date)" >> "${'$'}OUTPUT_FILE"
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[1. Device Information]" >> "${'$'}OUTPUT_FILE"
                        echo "Model: ${'$'}(getprop ro.product.model)" >> "${'$'}OUTPUT_FILE"
                        echo "Manufacturer: ${'$'}(getprop ro.product.manufacturer)" >> "${'$'}OUTPUT_FILE"
                        echo "Board Platform: ${'$'}(getprop ro.board.platform)" >> "${'$'}OUTPUT_FILE"
                        echo "Hardware: ${'$'}(getprop ro.hardware)" >> "${'$'}OUTPUT_FILE"
                        echo "Android Version: ${'$'}(getprop ro.build.version.release) (SDK ${'$'}(getprop ro.build.version.sdk))" >> "${'$'}OUTPUT_FILE"
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[2. CPU Topology]" >> "${'$'}OUTPUT_FILE"
                        for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                            if [ -d "${'$'}cpu" ]; then
                                if [ -f "${'$'}cpu/cpufreq/scaling_cur_freq" ]; then
                                    echo "${'$'}cpu -> ${'$'}(cat ${'$'}cpu/cpufreq/scaling_cur_freq 2>/dev/null) Hz" >> "${'$'}OUTPUT_FILE"
                                else
                                    echo "${'$'}cpu -> offline or inaccessible" >> "${'$'}OUTPUT_FILE"
                                fi
                            fi
                        done
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[3. GPU Nodes]" >> "${'$'}OUTPUT_FILE"
                        echo "--- Qualcomm (KGSL) ---" >> "${'$'}OUTPUT_FILE"
                        for gpu in /sys/class/kgsl/*; do
                            if [ -d "${'$'}gpu" ]; then
                                echo "[${'$'}gpu]" >> "${'$'}OUTPUT_FILE"
                                ls -l "${'$'}gpu"/ 2>/dev/null | grep -iE "busy|clk|freq|usage" >> "${'$'}OUTPUT_FILE"
                            fi
                        done
                        echo "--- MediaTek (GED) ---" >> "${'$'}OUTPUT_FILE"
                        ls -l /sys/module/ged/parameters/ 2>/dev/null >> "${'$'}OUTPUT_FILE"
                        ls -l /sys/kernel/ged/hal/ 2>/dev/null | grep -i freq >> "${'$'}OUTPUT_FILE"
                        echo "--- Mali / Generic Devfreq ---" >> "${'$'}OUTPUT_FILE"
                        ls -d /sys/class/misc/mali* 2>/dev/null >> "${'$'}OUTPUT_FILE"
                        find /sys/class/devfreq/ -maxdepth 2 -name "*gpu*" 2>/dev/null >> "${'$'}OUTPUT_FILE"
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[4. Thermal Zones]" >> "${'$'}OUTPUT_FILE"
                        for tz in /sys/class/thermal/thermal_zone*; do
                            if [ -d "${'$'}tz" ]; then
                                TYPE=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                                TEMP=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                                echo "${'$'}tz -> Type: [${'$'}TYPE], Temp: [${'$'}TEMP]" >> "${'$'}OUTPUT_FILE"
                            fi
                        done
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[5. Power Supply & Battery]" >> "${'$'}OUTPUT_FILE"
                        echo "Available Power Supplies:" >> "${'$'}OUTPUT_FILE"
                        ls /sys/class/power_supply/ 2>/dev/null >> "${'$'}OUTPUT_FILE"
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "Battery Details:" >> "${'$'}OUTPUT_FILE"
                        for psu in /sys/class/power_supply/*; do
                            if [ -d "${'$'}psu" ]; then
                                echo "--- ${'$'}psu ---" >> "${'$'}OUTPUT_FILE"
                                grep -H "" "${'$'}psu"/* 2>/dev/null | grep -iE "current|voltage|temp|capacity|health|status" >> "${'$'}OUTPUT_FILE"
                            fi
                        done
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "[6. Memory Info]" >> "${'$'}OUTPUT_FILE"
                        head -n 5 /proc/meminfo >> "${'$'}OUTPUT_FILE"
                        echo "" >> "${'$'}OUTPUT_FILE"
                        echo "===================================" >> "${'$'}OUTPUT_FILE"
                        echo "Diagnostic completed." >> "${'$'}OUTPUT_FILE"
                        echo "${'$'}OUTPUT_FILE"
                    """.trimIndent()

                    val result = withTimeoutOrNull(15000L) {
                        Shell.cmd(script).exec()
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (result != null && result.isSuccess) {
                            val savedFile = result.out.lastOrNull() ?: "/sdcard/Download/seriahud_diagnostic.txt"
                            Toast.makeText(context, "$successMsg\n$savedFile", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, timeoutMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setting_diagnostic))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
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
