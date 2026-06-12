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

    protected var currentScale: Float = 1000000f // Android standard is uA

    protected var lastGpuUsage: Int = 0
    protected var zeroGpuUsageCounter: Int = 0
    protected var lastGpuFreq: Int = 0
    protected var lastSocTemp: Float = 0f
    protected var lastBVoltage: Float = 0f
    protected var lastBCurrent: Float = 0f
    protected var lastBTemp: Float = 0f

    init {
        // Detect Oplus devices which commonly use mA instead of uA for battery current
        val manufacturer = android.os.Build.MANUFACTURER.lowercase(java.util.Locale.US)
        if (manufacturer.contains("oneplus") || manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            currentScale = 1000f
        }
    }
    private fun safeCat(path: String): String {
        if (path.isEmpty()) return "echo \"-1\""
        return "val=\$(cat $path 2>/dev/null); echo \"\${val:--1}\" | head -n 1"
    }

    override fun getCommands(): Array<String> {
        val cmds = mutableListOf<String>()
        cmds.add("val=\$(cat /proc/stat 2>/dev/null | grep -w '^cpu ' | head -n 1); echo \"\${val:-cpu 0 0 0 0 0 0 0}\"")
        cmds.addAll(cpuPaths.map { safeCat(it) })
        cmds.add(safeCat(gpuUsagePath))
        cmds.add(safeCat(gpuFreqPath))
        cmds.add(safeCat(socTempPath))
        cmds.add(safeCat(battVoltagePath))
        cmds.add(safeCat(battCurrentPath))
        cmds.add(safeCat(battTempPath))
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
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val usage = parseGpuUsage(line)
                if (usage > 0) {
                    lastGpuUsage = usage
                    zeroGpuUsageCounter = 0
                } else if (usage == 0) {
                    zeroGpuUsageCounter++
                    if (zeroGpuUsageCounter >= 2) {
                        lastGpuUsage = 0
                    }
                }
            }
            builder.gpuUsage = lastGpuUsage
            lineIndex++
        }

        // GPU Freq
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val freq = parseGpuFreq(line)
                if (freq >= 0) lastGpuFreq = freq
            }
            builder.gpuFreq = lastGpuFreq
            lineIndex++
        }

        // SOC Temp
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val raw = line.toFloatOrNull() ?: 0f
                if (raw != 0f) {
                    lastSocTemp = when {
                        raw < 150f -> raw // degrees
                        raw < 1500f -> raw / 10f // decidegrees
                        else -> raw / 1000f // millidegrees
                    }
                }
            }
            builder.socTemp = lastSocTemp
            lineIndex++
        }

        // Batt Voltage
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val raw = line.toFloatOrNull() ?: 0f
                if (raw != 0f) {
                    lastBVoltage = when {
                        raw < 100f -> raw // V
                        raw < 10000f -> raw / 1000f // mV
                        else -> raw / 1000000f // uV
                    }
                }
            }
            builder.bVoltage = lastBVoltage
            lineIndex++
        }

        // Batt Current
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val raw = line.toFloatOrNull() ?: 0f
                if (raw != 0f) {
                    val absRaw = if (raw < 0) -raw else raw
                    if (currentScale == 1000f && absRaw > 50000f) {
                        currentScale = 1000000f
                    }
                    lastBCurrent = if (currentScale > 0f) raw / currentScale else 0f
                } else {
                    lastBCurrent = 0f
                }
            }
            builder.bCurrent = lastBCurrent
            lineIndex++
        }

        // Batt Temp
        if (lineIndex < out.size) {
            val line = out[lineIndex].trim()
            if (line != "-1" && line.isNotEmpty()) {
                val raw = line.toFloatOrNull() ?: 0f
                if (raw != 0f) {
                    lastBTemp = when {
                        raw < 150f -> raw // degrees
                        raw < 1500f -> raw / 10f // decidegrees
                        else -> raw / 1000f // millidegrees
                    }
                }
            }
            builder.bTemp = lastBTemp
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
