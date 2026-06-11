package org.chtholly.seriahud

import com.topjohnwu.superuser.Shell

class QcomHardwareProvider : IHardwareProvider {

    private var lastTotalTime = 0L
    private var lastIdleTime = 0L

    private var cpuPaths = mutableListOf<String>()
    private var gpuUsagePath = ""
    private var gpuFreqPath = ""
    private var socTempPath = ""
    private var battVoltagePath = ""
    private var battCurrentPath = ""
    private var battTempPath = ""

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

    override fun getCommands(): Array<String> {
        val cmds = mutableListOf<String>()
        cmds.add("cat /proc/stat | grep -w cpu")
        cmds.addAll(cpuPaths.map { "cat $it 2>/dev/null || echo 0" })
        cmds.add(if (gpuUsagePath.isNotEmpty()) "cat $gpuUsagePath 2>/dev/null || echo 0" else "echo 0")
        cmds.add(if (gpuFreqPath.isNotEmpty()) "cat $gpuFreqPath 2>/dev/null || echo 0" else "echo 0")
        cmds.add(if (socTempPath.isNotEmpty()) "cat $socTempPath 2>/dev/null || echo 0" else "echo 0")
        cmds.add(if (battVoltagePath.isNotEmpty()) "cat $battVoltagePath 2>/dev/null || echo 0" else "echo 0")
        cmds.add(if (battCurrentPath.isNotEmpty()) "cat $battCurrentPath 2>/dev/null || echo 0" else "echo 0")
        cmds.add(if (battTempPath.isNotEmpty()) "cat $battTempPath 2>/dev/null || echo 0" else "echo 0")
        cmds.add("cat /proc/meminfo | head -n 3")
        return cmds.toTypedArray()
    }

    override fun getDetectedPaths(): Map<String, String> {
        val paths = mutableMapOf<String, String>()
        paths["CPU"] = cpuPaths.joinToString("\n")
        if (gpuUsagePath.isNotEmpty()) paths["GPU Usage"] = gpuUsagePath
        if (gpuFreqPath.isNotEmpty()) paths["GPU Freq"] = gpuFreqPath
        if (socTempPath.isNotEmpty()) paths["SOC Temp"] = socTempPath
        if (battTempPath.isNotEmpty()) paths["Battery Temp"] = battTempPath
        return paths
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

        // CPU Freqs
        for (i in cpuPaths.indices) {
            if (lineIndex < out.size) {
                val line = out[lineIndex]
                line.toIntOrNull()?.let { freq ->
                    builder.cpuFreqs.add(freq / 1000)
                } ?: builder.cpuFreqs.add(0) // add 0 if offline/unreadable
                lineIndex++
            }
        }

        // GPU Usage
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line.contains("%")) {
                builder.gpuUsage = line.replace("%", "").trim().toIntOrNull() ?: 0
            } else if (line.trim().split("\\s+".toRegex()).size == 2) {
                // gpubusy format: "busy total"
                val parts = line.trim().split("\\s+".toRegex())
                val busy = parts[0].toFloatOrNull() ?: 0f
                val total = parts[1].toFloatOrNull() ?: 1f
                builder.gpuUsage = if (total > 0) ((busy / total) * 100).toInt() else 0
            } else {
                builder.gpuUsage = line.toIntOrNull() ?: 0
            }
            lineIndex++
        }

        // GPU Freq
        if (lineIndex < out.size) {
            builder.gpuFreq = (out[lineIndex].trim().toIntOrNull() ?: 0) / 1000000
            lineIndex++
        }

        // SOC Temp
        if (lineIndex < out.size) {
            builder.socTemp = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000f
            lineIndex++
        }

        // Batt Voltage
        if (lineIndex < out.size) {
            builder.bVoltage = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        // Batt Current
        if (lineIndex < out.size) {
            builder.bCurrent = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        // Batt Temp
        if (lineIndex < out.size) {
            val rawTemp = out[lineIndex].toFloatOrNull() ?: 0f
            if (battTempPath.contains("thermal_zone")) {
                builder.bTemp = rawTemp / 1000f // thermal zone millidegrees
            } else {
                builder.bTemp = rawTemp / 10f // power_supply decidegrees
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
