package org.chtholly.seriahud

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

data class HudConfig(
    val showCpuOverall: Boolean = true,
    val showCpuCores: Boolean = false,
    val selectedCpuCores: List<Int> = listOf(4, 5, 6, 7), // Default to big cores
    val showGpu: Boolean = true,
    val showRam: Boolean = true,
    val showSocTemp: Boolean = true,
    val showBattery: Boolean = true,
    val showFps: Boolean = true,
    val showFrametimeGraph: Boolean = true,
    val showRecordButton: Boolean = true,
    val doubleBatteryPower: Boolean = false
)

class HudConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hud_config", Context.MODE_PRIVATE)
    
    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<HudConfig> = _configFlow.asStateFlow()

    private fun loadConfig(): HudConfig {
        val selectedCoresJson = prefs.getString("selectedCpuCores", "[4,5,6,7]") ?: "[4,5,6,7]"
        val selectedCores = mutableListOf<Int>()
        try {
            val jsonArray = JSONArray(selectedCoresJson)
            for (i in 0 until jsonArray.length()) {
                selectedCores.add(jsonArray.getInt(i))
            }
        } catch (e: Exception) {
            selectedCores.addAll(listOf(4, 5, 6, 7))
        }

        return HudConfig(
            showCpuOverall = prefs.getBoolean("showCpuOverall", true),
            showCpuCores = prefs.getBoolean("showCpuCores", false),
            selectedCpuCores = selectedCores,
            showGpu = prefs.getBoolean("showGpu", true),
            showRam = prefs.getBoolean("showRam", true),
            showSocTemp = prefs.getBoolean("showSocTemp", true),
            showBattery = prefs.getBoolean("showBattery", true),
            showFps = prefs.getBoolean("showFps", true),
            showFrametimeGraph = prefs.getBoolean("showFrametimeGraph", true),
            showRecordButton = prefs.getBoolean("showRecordButton", true),
            doubleBatteryPower = prefs.getBoolean("doubleBatteryPower", false)
        )
    }

    fun updateConfig(config: HudConfig) {
        prefs.edit().apply {
            putBoolean("showCpuOverall", config.showCpuOverall)
            putBoolean("showCpuCores", config.showCpuCores)
            putString("selectedCpuCores", JSONArray(config.selectedCpuCores).toString())
            putBoolean("showGpu", config.showGpu)
            putBoolean("showRam", config.showRam)
            putBoolean("showSocTemp", config.showSocTemp)
            putBoolean("showBattery", config.showBattery)
            putBoolean("showFps", config.showFps)
            putBoolean("showFrametimeGraph", config.showFrametimeGraph)
            putBoolean("showRecordButton", config.showRecordButton)
            putBoolean("doubleBatteryPower", config.doubleBatteryPower)
        }.apply()
        
        _configFlow.value = config
    }
}
