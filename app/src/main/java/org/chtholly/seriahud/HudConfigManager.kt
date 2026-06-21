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
    val doubleBatteryPower: Boolean = false,
    // --- Overlay appearance (Phase 3 skinning) ---
    val overlayOpacity: Float = 0.67f, // matches the original 0xAA black background
    val accentPresetIndex: Int = 0, // index into HudPalette.Presets
    val cornerRadiusDp: Int = 8,
    val fontScale: Float = 1.0f,
    val compactMetrics: Set<String> = emptySet(), // metric keys rendered inline
    val positionPreset: String = POS_CUSTOM,
    val overlayX: Int = 100,
    val overlayY: Int = 100,
) {
    companion object {
        const val POS_CUSTOM = "custom"
        const val POS_TOP_LEFT = "top_left"
        const val POS_TOP_RIGHT = "top_right"
        const val POS_BOTTOM_LEFT = "bottom_left"
        const val POS_BOTTOM_RIGHT = "bottom_right"

        const val METRIC_FPS = "fps"
        const val METRIC_GPU = "gpu"
        const val METRIC_CPU = "cpu"
        const val METRIC_RAM = "ram"
        const val METRIC_BAT = "bat"
    }
}

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
            doubleBatteryPower = prefs.getBoolean("doubleBatteryPower", false),
            overlayOpacity = prefs.getFloat("overlayOpacity", 0.67f),
            accentPresetIndex = prefs.getInt("accentPresetIndex", 0),
            cornerRadiusDp = prefs.getInt("cornerRadiusDp", 8),
            fontScale = prefs.getFloat("fontScale", 1.0f),
            compactMetrics = prefs.getStringSet("compactMetrics", emptySet())?.toSet() ?: emptySet(),
            positionPreset = prefs.getString("positionPreset", HudConfig.POS_CUSTOM) ?: HudConfig.POS_CUSTOM,
            overlayX = prefs.getInt("overlayX", 100),
            overlayY = prefs.getInt("overlayY", 100),
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
            putFloat("overlayOpacity", config.overlayOpacity)
            putInt("accentPresetIndex", config.accentPresetIndex)
            putInt("cornerRadiusDp", config.cornerRadiusDp)
            putFloat("fontScale", config.fontScale)
            putStringSet("compactMetrics", config.compactMetrics)
            putString("positionPreset", config.positionPreset)
            putInt("overlayX", config.overlayX)
            putInt("overlayY", config.overlayY)
        }.apply()

        _configFlow.value = config
    }
}
