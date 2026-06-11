package org.chtholly.seriahud

import com.topjohnwu.superuser.Shell

class MtkHardwareProvider : IHardwareProvider {

    private var lastTotalTime = 0L
    private var lastIdleTime = 0L
    private val cpuPaths = mutableListOf<String>()
    
    private var socTempPath = ""
    private var battTempPath = ""
    private var gpuUsagePath = "/sys/module/ged/parameters/gpu_loading"
    private var gpuFreqPath = "/sys/kernel/ged/hal/current_freqency"

    init {
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
        
        // Prefer power_supply for battery temp if it exists and is readable
        val pmi = "/sys/class/power_supply/battery"
        if (Shell.cmd("ls $pmi/temp").exec().isSuccess) {
            battTempPath = "$pmi/temp"
        }
        
        // Fallbacks
        if (socTempPath.isEmpty()) socTempPath = "/sys/class/thermal/thermal_zone4/temp"
        if (battTempPath.isEmpty()) battTempPath = "/sys/class/thermal/thermal_zone0/temp"
    }

    override fun getCommands(): Array<String> {
        val cmds = mutableListOf<String>()
        cmds.add("cat /proc/stat | grep -w cpu")
        cmds.addAll(cpuPaths.map { "cat $it 2>/dev/null || echo 0" })
        cmds.add("cat $gpuUsagePath 2>/dev/null || echo 0")
        cmds.add("cat $gpuFreqPath 2>/dev/null || echo 0")
        cmds.add("cat $socTempPath 2>/dev/null || echo 0")
        cmds.add("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null || echo 0")
        cmds.add("cat /sys/class/power_supply/battery/current_now 2>/dev/null || echo 0")
        cmds.add("cat $battTempPath 2>/dev/null || echo 0")
        cmds.add("cat /proc/meminfo | head -n 3")
        return cmds.toTypedArray()
    }

    override fun getDetectedPaths(): Map<String, String> {
        return mapOf(
            "CPU" to cpuPaths.joinToString("\n"),
            "GPU Usage" to "cat $gpuUsagePath",
            "GPU Freq" to "cat $gpuFreqPath",
            "SOC Temp" to "cat $socTempPath",
            "Battery Temp" to "cat $battTempPath"
        )
    }

    override fun parseOutput(out: List<String>, startIndex: Int, builder: SystemStatsBuilder): Int {
        var lineIndex = startIndex

        if (lineIndex < out.size && out[lineIndex].startsWith("cpu ")) {
            val parts = out[lineIndex].trim().split("\\s+".toRegex())
            if (parts.size >= 5) {
                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val system = parts[3].toLong()
                val idle = parts[4].toLong()
                val iowait = if (parts.size > 5) parts[5].toLong() else 0L
                val irq = if (parts.size > 6) parts[6].toLong() else 0L
                val softirq = if (parts.size > 7) parts[7].toLong() else 0L

                val totalIdle = idle + iowait
                val totalTime = user + nice + system + idle + iowait + irq + softirq

                if (lastTotalTime != 0L) {
                    val totalDelta = totalTime - lastTotalTime
                    val idleDelta = totalIdle - lastIdleTime
                    if (totalDelta > 0) {
                        builder.cpuUsage = (1f - idleDelta.toFloat() / totalDelta.toFloat()) * 100f
                    }
                }
                lastTotalTime = totalTime
                lastIdleTime = totalIdle
            }
            lineIndex++
        }

        for (i in cpuPaths.indices) {
            if (lineIndex < out.size) {
                val line = out[lineIndex]
                line.toIntOrNull()?.let { freq ->
                    builder.cpuFreqs.add(freq / 1000)
                } ?: builder.cpuFreqs.add(0)
                lineIndex++
            }
        }

        if (lineIndex < out.size && out[lineIndex].toIntOrNull() != null) {
            builder.gpuUsage = out[lineIndex].toInt()
            lineIndex++
        } else {
            while (lineIndex < out.size && out[lineIndex].toIntOrNull() == null && !out[lineIndex].contains(" ")) {
                lineIndex++
            }
            if (lineIndex < out.size && out[lineIndex].toIntOrNull() != null) {
                builder.gpuUsage = out[lineIndex].toInt()
                lineIndex++
            }
        }

        if (lineIndex < out.size) {
            val parts = out[lineIndex].trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                builder.gpuFreq = (parts[1].toIntOrNull() ?: 0) / 1000
            }
            lineIndex++
        }

        if (lineIndex < out.size) {
            builder.socTemp = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            builder.bVoltage = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            builder.bCurrent = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            val rawTemp = out[lineIndex].toFloatOrNull() ?: 0f
            if (battTempPath.contains("thermal_zone")) {
                builder.bTemp = rawTemp / 1000f // thermal zone is in millidegrees Celsius
            } else {
                builder.bTemp = rawTemp / 10f // power_supply is in decidegrees Celsius
            }
            lineIndex++
        }

        for (i in 0 until 3) {
            if (lineIndex < out.size && out[lineIndex].startsWith("Mem")) {
                val parts = out[lineIndex].split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val value = parts[1].toLongOrNull() ?: 0L
                    when {
                        out[lineIndex].startsWith("MemTotal:") -> builder.ramTotal = value
                        out[lineIndex].startsWith("MemFree:") -> builder.ramFree = value
                        out[lineIndex].startsWith("MemAvailable:") -> builder.ramAvailable = value
                    }
                }
                lineIndex++
            }
        }

        return lineIndex
    }
}
