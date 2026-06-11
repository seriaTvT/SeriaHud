package org.chtholly.seriahud

import com.topjohnwu.superuser.Shell

class MtkHardwareProvider : AbstractHardwareProvider() {

    init {
        gpuUsagePath = "/sys/module/ged/parameters/gpu_loading"
        gpuFreqPath = "/sys/kernel/ged/hal/current_freqency"

        // Find CPU cores
        val cpus = Shell.cmd("ls -d /sys/devices/system/cpu/cpu[0-9]*").exec().out
        cpuPaths.addAll(cpus.map { "$it/cpufreq/scaling_cur_freq" })

        // Find thermal zones
        val thermals = Shell.cmd("for tz in /sys/class/thermal/thermal_zone*; do echo \"\$tz \$(cat \$tz/type 2>/dev/null)\"; done").exec().out
        for (line in thermals) {
            val lower = line.lowercase()
            // Look for common MTK SoC thermal zones
            if (lower.contains("mtktscpu") || lower.contains("mtktsap") || lower.contains("soc_therm") || lower.contains("cpu-therm") || lower.contains("cpu_therm") || lower.contains("ap_therm")) {
                socTempPath = line.split(" ")[0] + "/temp"
            }
            // Look for battery thermal
            if (lower.contains("mtktsbattery") || lower.contains("battery") || lower.contains("batt_therm")) {
                battTempPath = line.split(" ")[0] + "/temp"
            }
        }
        
        // Battery voltage/current/temp from power_supply
        val pmi = "/sys/class/power_supply/battery"
        if (Shell.cmd("ls $pmi/voltage_now").exec().isSuccess) battVoltagePath = "$pmi/voltage_now"
        if (Shell.cmd("ls $pmi/current_now").exec().isSuccess) battCurrentPath = "$pmi/current_now"
        
        // Prefer power_supply for battery temp if it exists and is readable
        if (Shell.cmd("ls $pmi/temp").exec().isSuccess) {
            battTempPath = "$pmi/temp"
        }
        
        // Fallbacks
        if (socTempPath.isEmpty()) socTempPath = "/sys/class/thermal/thermal_zone4/temp"
        if (battTempPath.isEmpty()) battTempPath = "/sys/class/thermal/thermal_zone0/temp"
    }

    override fun parseGpuUsage(line: String): Int {
        return line.trim().toIntOrNull() ?: 0
    }

    override fun parseGpuFreq(line: String): Int {
        val parts = line.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) {
            (parts[1].toIntOrNull() ?: 0) / 1000
        } else {
            (line.trim().toIntOrNull() ?: 0) / 1000
        }
    }
}
