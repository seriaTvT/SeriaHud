package org.chtholly.seriahud

class MtkHardwareProvider : IHardwareProvider {

    private var lastTotalTime = 0L
    private var lastIdleTime = 0L

    override fun getCommands(): Array<String> {
        return arrayOf(
            "cat /proc/stat | grep -w cpu",
            "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq",
            "cat /sys/module/ged/parameters/gpu_loading",
            "cat /sys/kernel/ged/hal/current_freqency",
            "cat /sys/class/thermal/thermal_zone4/temp",
            "cat /sys/class/power_supply/battery/voltage_now",
            "cat /sys/class/power_supply/battery/current_now",
            "cat /sys/class/power_supply/battery/temp",
            "cat /proc/meminfo | head -n 3"
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

        for (i in 0 until 8) {
            if (lineIndex < out.size) {
                val line = out[lineIndex]
                line.toIntOrNull()?.let { freq ->
                    builder.cpuFreqs.add(freq / 1000)
                }
                if (line.isNotEmpty()) lineIndex++
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
            builder.bTemp = (out[lineIndex].toFloatOrNull() ?: 0f) / 10f
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
