package org.chtholly.seriahud

import com.topjohnwu.superuser.Shell

class QcomHardwareProvider : AbstractHardwareProvider() {

    init {
        // Find CPU frequencies for all individual cores
        val cpus = Shell.cmd("ls -d /sys/devices/system/cpu/cpu[0-9]*").exec().out
        cpuPaths.addAll(cpus.map { "$it/cpufreq/scaling_cur_freq" })

        // Find GPU paths
        val gpuBase = "/sys/class/kgsl/kgsl-3d0"
        if (Shell.cmd("ls $gpuBase/gpu_busy_percentage").exec().isSuccess) {
            gpuUsagePath = "$gpuBase/gpu_busy_percentage"
        } else if (Shell.cmd("ls $gpuBase/gpubusy").exec().isSuccess) {
            gpuUsagePath = "$gpuBase/gpubusy"
        }

        if (Shell.cmd("ls $gpuBase/gpuclk").exec().isSuccess) {
            gpuFreqPath = "$gpuBase/gpuclk"
        }

        // Find thermal zones
        val thermals = Shell.cmd("for tz in /sys/class/thermal/thermal_zone*; do echo \"\$tz \$(cat \$tz/type)\"; done").exec().out
        for (line in thermals) {
            if (line.contains("cpu_therm") || line.contains("xo_therm") || line.contains("cpullc-0-0")) {
                if (socTempPath.isEmpty() || line.contains("cpu_therm")) {
                    socTempPath = line.split(" ")[0] + "/temp"
                }
            }
            if (line.contains("batt_therm")) {
                battTempPath = line.split(" ")[0] + "/temp"
            }
        }

        // Battery voltage/current/temp from power_supply
        val pmi = "/sys/class/power_supply/battery"
        if (Shell.cmd("ls $pmi/voltage_now").exec().isSuccess) battVoltagePath = "$pmi/voltage_now"
        if (Shell.cmd("ls $pmi/current_now").exec().isSuccess) battCurrentPath = "$pmi/current_now"
        
        // Prefer power_supply battery temp as it's the Android OS standard
        if (Shell.cmd("ls $pmi/temp").exec().isSuccess) {
            battTempPath = "$pmi/temp"
        }
    }

    override fun parseGpuUsage(line: String): Int {
        val trimmed = line.trim()
        if (trimmed.contains("%")) {
            return trimmed.replace("%", "").trim().toIntOrNull() ?: 0
        } else if (trimmed.split("\\s+".toRegex()).size == 2) {
            // gpubusy format: "busy total"
            val parts = trimmed.split("\\s+".toRegex())
            val busy = parts[0].toFloatOrNull() ?: 0f
            val total = parts[1].toFloatOrNull() ?: 1f
            return if (total > 0) ((busy / total) * 100).toInt() else 0
        } else {
            return trimmed.toIntOrNull() ?: 0
        }
    }

    override fun parseGpuFreq(line: String): Int {
        return (line.trim().toIntOrNull() ?: 0) / 1000000
    }
}
