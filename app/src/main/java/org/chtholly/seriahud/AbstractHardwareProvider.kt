package org.chtholly.seriahud

abstract class AbstractHardwareProvider : IHardwareProvider {

    private var lastTotalTime = 0L
    private var lastIdleTime = 0L

    protected val cpuPaths = mutableListOf<String>()
    protected var gpuUsagePath = ""
    protected var gpuFreqPath = ""
    protected var socTempPath = ""
    protected var battVoltagePath = ""
    protected var battCurrentPath = ""
    protected var battTempPath = ""

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

    abstract fun parseGpuUsage(line: String): Int
    abstract fun parseGpuFreq(line: String): Int

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
            builder.gpuUsage = parseGpuUsage(out[lineIndex])
            lineIndex++
        }

        // GPU Freq
        if (lineIndex < out.size) {
            builder.gpuFreq = parseGpuFreq(out[lineIndex])
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

        // RAM
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
