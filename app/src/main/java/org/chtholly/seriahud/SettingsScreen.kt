package org.chtholly.seriahud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.chtholly.seriahud.theme.HudPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(configManager: HudConfigManager) {
    val config by configManager.configFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Drive the core selector off the actual CPU count rather than assuming 8.
    val coreCount = remember { Runtime.getRuntime().availableProcessors() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SettingsSection(stringResource(R.string.setting_display_items)) {
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
        }

        SettingsSection(stringResource(R.string.setting_cpu_cores_config)) {
            SwitchSetting(stringResource(R.string.setting_show_individual_cores), config.showCpuCores) {
                configManager.updateConfig(config.copy(showCpuCores = it))
            }
            if (config.showCpuCores) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.setting_select_cores), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until coreCount) {
                            CoreCheckbox(coreId = i, selected = config.selectedCpuCores.contains(i)) { checked ->
                                val newList = config.selectedCpuCores.toMutableList()
                                if (checked) newList.add(i) else newList.remove(i)
                                configManager.updateConfig(config.copy(selectedCpuCores = newList.sorted()))
                            }
                        }
                    }
                }
            }
        }

        SettingsSection(stringResource(R.string.setting_advanced_features)) {
            SwitchSetting(stringResource(R.string.setting_show_frametime_graph), config.showFrametimeGraph) {
                configManager.updateConfig(config.copy(showFrametimeGraph = it))
            }
            SwitchSetting(stringResource(R.string.setting_show_record_button), config.showRecordButton) {
                configManager.updateConfig(config.copy(showRecordButton = it))
            }
            SwitchSetting(stringResource(R.string.setting_double_battery_power), config.doubleBatteryPower) {
                configManager.updateConfig(config.copy(doubleBatteryPower = it))
            }
        }

        SettingsSection(stringResource(R.string.setting_overlay_appearance)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SliderSetting(
                    label = stringResource(R.string.setting_overlay_opacity),
                    value = config.overlayOpacity,
                    valueRange = 0.3f..1.0f,
                    valueText = "${(config.overlayOpacity * 100).roundToInt()}%"
                ) { configManager.updateConfig(config.copy(overlayOpacity = it)) }

                SliderSetting(
                    label = stringResource(R.string.setting_overlay_font_scale),
                    value = config.fontScale,
                    valueRange = 0.8f..1.4f,
                    valueText = String.format("%.1fx", config.fontScale)
                ) { configManager.updateConfig(config.copy(fontScale = it)) }

                Column {
                    Text(stringResource(R.string.setting_overlay_corner_radius), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    val cornerOptions = listOf(
                        0 to R.string.corner_none,
                        8 to R.string.corner_small,
                        16 to R.string.corner_medium,
                        24 to R.string.corner_large
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        cornerOptions.forEachIndexed { index, (radius, labelRes) ->
                            SegmentedButton(
                                selected = config.cornerRadiusDp == radius,
                                onClick = { configManager.updateConfig(config.copy(cornerRadiusDp = radius)) },
                                shape = SegmentedButtonDefaults.itemShape(index, cornerOptions.size)
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }

                Column {
                    Text(stringResource(R.string.setting_overlay_accent), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HudPalette.Presets.forEachIndexed { index, palette ->
                            PresetSwatch(palette = palette, selected = config.accentPresetIndex == index) {
                                configManager.updateConfig(config.copy(accentPresetIndex = index))
                            }
                        }
                    }
                }

                Column {
                    Text(stringResource(R.string.setting_overlay_position), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val positionOptions = listOf(
                        HudConfig.POS_CUSTOM to R.string.pos_custom,
                        HudConfig.POS_TOP_LEFT to R.string.pos_top_left,
                        HudConfig.POS_TOP_RIGHT to R.string.pos_top_right,
                        HudConfig.POS_BOTTOM_LEFT to R.string.pos_bottom_left,
                        HudConfig.POS_BOTTOM_RIGHT to R.string.pos_bottom_right
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        positionOptions.forEach { (value, labelRes) ->
                            FilterChip(
                                selected = config.positionPreset == value,
                                onClick = { configManager.updateConfig(config.copy(positionPreset = value)) },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                }

                Column {
                    Text(stringResource(R.string.setting_overlay_compact), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val metricOptions = listOf(
                        HudConfig.METRIC_FPS to R.string.overlay_fps,
                        HudConfig.METRIC_GPU to R.string.overlay_gpu,
                        HudConfig.METRIC_CPU to R.string.overlay_cpu,
                        HudConfig.METRIC_RAM to R.string.overlay_ram,
                        HudConfig.METRIC_BAT to R.string.overlay_batt_temp
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        metricOptions.forEach { (key, labelRes) ->
                            FilterChip(
                                selected = key in config.compactMetrics,
                                onClick = {
                                    val newSet = config.compactMetrics.toMutableSet()
                                    if (key in newSet) newSet.remove(key) else newSet.add(key)
                                    configManager.updateConfig(config.copy(compactMetrics = newSet))
                                },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                }

                // Reset only the overlay-appearance fields to their HudConfig
                // defaults, leaving display toggles and core selection alone.
                OutlinedButton(
                    onClick = {
                        val defaults = HudConfig()
                        configManager.updateConfig(
                            config.copy(
                                overlayOpacity = defaults.overlayOpacity,
                                accentPresetIndex = defaults.accentPresetIndex,
                                cornerRadiusDp = defaults.cornerRadiusDp,
                                fontScale = defaults.fontScale,
                                compactMetrics = defaults.compactMetrics,
                                positionPreset = defaults.positionPreset,
                                overlayX = defaults.overlayX,
                                overlayY = defaults.overlayY
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.setting_restore_defaults))
                }
            }
        }

        SettingsSection(stringResource(R.string.setting_diagnostic)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.setting_diagnostic_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun CoreCheckbox(coreId: Int, selected: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = onCheckedChange)
        Text(stringResource(R.string.setting_core_format, coreId), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
fun PresetSwatch(palette: HudPalette, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(palette.fps, palette.gpu, palette.cpu).forEach { dotColor ->
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            }
        }
    }
}
