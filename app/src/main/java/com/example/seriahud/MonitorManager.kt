package com.example.seriahud

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.util.Log
import kotlin.math.abs

data class SystemStats(
    val cpuUsage: Float = 0f,
    val cpuFrequencies: List<Int> = emptyList(),
    val gpuUsage: Int = 0,
    val gpuFreq: Int = 0,
    val socTemp: Float = 0f,
    val batteryVoltage: Float = 0f,
    val batteryCurrent: Float = 0f,
    val batteryPower: Float = 0f,
    val batteryTemp: Float = 0f,
    val ramUsage: Float = 0f,
    val ramUsedGB: Float = 0f,
    val ramTotalGB: Float = 0f,
    val fps: Int = 0,
    val frametime: Float = 0f
)

class MonitorManager {

    private var lastTotalTime = 0L
    private var lastIdleTime = 0L
    
    private var activeWindows: List<String> = emptyList()
    private var lastFpsCheckFrames = LongArray(128)
    private var windowUpdateCounter = 0

    fun getStatsFlow(): Flow<SystemStats> = flow {
        if (!Shell.getShell().isRoot) {
            emit(SystemStats())
            return@flow
        }

        while (true) {
            val stats = fetchStats()
            emit(stats)
            delay(500)
        }
    }.flowOn(Dispatchers.IO)

    private fun fetchStats(): SystemStats {
        // Update active window every 2 seconds (4 * 500ms)
        if (windowUpdateCounter++ % 4 == 0) {
            val focusOut = Shell.cmd("dumpsys window | grep mCurrentFocus").exec().out.joinToString("")
            val regex = "Window\\{.*? (?:u\\d+ )?([^ }]+)\\}".toRegex()
            regex.find(focusOut)?.let { matchResult ->
                val focusPkgAct = matchResult.groupValues[1]
                val pkgName = focusPkgAct.substringBefore("/")
                val sfList = Shell.cmd("dumpsys SurfaceFlinger --list").exec().out
                
                val candidates = sfList.filter { it.contains(pkgName) }
                    .filter { !it.contains("Bounds") && !it.contains("ActivityRecord") && !it.contains("Background") && !it.contains("InputSink") }
                
                val parsedLayers = candidates.mapNotNull { layerStr ->
                    val layerRegex = "RequestedLayerState\\{(.*?) (parentId|relativeParentId|z)=".toRegex()
                    val match = layerRegex.find(layerStr)
                    if (match != null) {
                        match.groupValues[1].trim()
                    } else if (layerStr.startsWith("RequestedLayerState{")) {
                        layerStr.removePrefix("RequestedLayerState{").substringBefore("}").trim()
                    } else {
                        layerStr.trim()
                    }
                }.distinct()
                
                activeWindows = parsedLayers
            }
        }

        val sfCmds = activeWindows.flatMap { listOf("dumpsys SurfaceFlinger --latency '$it'", "echo 'NEXT_LAYER'") }.toTypedArray()
        val cmds = arrayOf(
            "cat /proc/stat | grep -w cpu",
            "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq",
            "cat /sys/module/ged/parameters/gpu_loading",
            "cat /sys/kernel/ged/hal/current_freqency",
            "cat /sys/class/thermal/thermal_zone4/temp",
            "cat /sys/class/power_supply/battery/voltage_now",
            "cat /sys/class/power_supply/battery/current_now",
            "cat /sys/class/power_supply/battery/temp",
            "cat /proc/meminfo | head -n 3"
        ) + sfCmds
        val result = Shell.cmd(*cmds).exec()
        val out = result.out

        var cpuUsage = 0f
        val cpuFreqs = mutableListOf<Int>()
        var gpuUsage = 0
        var gpuFreq = 0
        var socTemp = 0f
        var bVoltage = 0f
        var bCurrent = 0f
        var bTemp = 0f

        var ramTotal = 0L
        var ramFree = 0L
        var ramAvailable = 0L

        var lineIndex = 0

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
                        cpuUsage = (1f - idleDelta.toFloat() / totalDelta.toFloat()) * 100f
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
                    cpuFreqs.add(freq / 1000)
                }
                if (line.isNotEmpty()) lineIndex++
            }
        }

        if (lineIndex < out.size && out[lineIndex].toIntOrNull() != null) {
            gpuUsage = out[lineIndex].toInt()
            lineIndex++
        } else {
            // handle edge cases where the previous block consumed fewer lines
            while (lineIndex < out.size && out[lineIndex].toIntOrNull() == null && !out[lineIndex].contains(" ")) {
                lineIndex++
            }
            if (lineIndex < out.size && out[lineIndex].toIntOrNull() != null) {
                gpuUsage = out[lineIndex].toInt()
                lineIndex++
            }
        }

        if (lineIndex < out.size) {
            val parts = out[lineIndex].trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                gpuFreq = (parts[1].toIntOrNull() ?: 0) / 1000
            }
            lineIndex++
        }

        if (lineIndex < out.size) {
            socTemp = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            bVoltage = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            bCurrent = (out[lineIndex].toFloatOrNull() ?: 0f) / 1000000f
            lineIndex++
        }

        if (lineIndex < out.size) {
            bTemp = (out[lineIndex].toFloatOrNull() ?: 0f) / 10f
            lineIndex++
        }

        // Parse meminfo
        for (i in 0 until 3) {
            if (lineIndex < out.size && out[lineIndex].startsWith("Mem")) {
                val parts = out[lineIndex].split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val value = parts[1].toLongOrNull() ?: 0L
                    when {
                        out[lineIndex].startsWith("MemTotal:") -> ramTotal = value
                        out[lineIndex].startsWith("MemFree:") -> ramFree = value
                        out[lineIndex].startsWith("MemAvailable:") -> ramAvailable = value
                    }
                }
                lineIndex++
            }
        }

        val ramUsed = ramTotal - (if (ramAvailable > 0) ramAvailable else ramFree)
        val ramUsagePct = if (ramTotal > 0) (ramUsed.toFloat() / ramTotal.toFloat()) * 100f else 0f
        val ramUsedGB = ramUsed / 1024f / 1024f
        val ramTotalGB = ramTotal / 1024f / 1024f

        val power = bVoltage * abs(bCurrent)

        // Parse SurfaceFlinger
        var maxFps = 0
        var bestFrametime = 0f

        while (lineIndex < out.size) {
            val refreshPeriodStr = out[lineIndex]
            if (refreshPeriodStr == "NEXT_LAYER") {
                lineIndex++
                continue
            }
            if (refreshPeriodStr == "END_SF" || lineIndex >= out.size) break
            
            val refreshPeriod = refreshPeriodStr.toLongOrNull()
            lineIndex++
            
            if (refreshPeriod != null) {
                var frameCount = 0
                var totalDuration = 0L
                var previousTimestamp = 0L
                
                while (lineIndex < out.size) {
                    val line = out[lineIndex]
                    if (line == "NEXT_LAYER" || line == "END_SF") {
                        lineIndex++
                        break
                    }
                    if (line.isEmpty()) {
                        lineIndex++
                        continue
                    }
                    val cols = line.trim().split("\\s+".toRegex())
                    if (cols.size >= 2) {
                        val timestamp = cols[1].toLongOrNull() ?: 0L
                        if (timestamp != 0L && timestamp != Long.MAX_VALUE) {
                            if (previousTimestamp != 0L && timestamp > previousTimestamp) {
                                val duration = timestamp - previousTimestamp
                                if (duration in 1..1000000000L) {
                                    totalDuration += duration
                                    frameCount++
                                }
                            }
                            previousTimestamp = timestamp
                        }
                    }
                    lineIndex++
                }

                if (frameCount > 0) {
                    val avgFrametimeNs = totalDuration / frameCount
                    val currentFrametime = avgFrametimeNs / 1000000f // ms
                    val currentFps = if (avgFrametimeNs > 0) (1000000000L / avgFrametimeNs).toInt() else 0
                    if (currentFps > maxFps) {
                        maxFps = currentFps
                        bestFrametime = currentFrametime
                    }
                }
            } else {
                while (lineIndex < out.size && out[lineIndex] != "NEXT_LAYER" && out[lineIndex] != "END_SF") {
                    lineIndex++
                }
                if (lineIndex < out.size) lineIndex++
            }
        }
        
        val fps = maxFps
        val frametime = bestFrametime

        return SystemStats(
            cpuUsage = cpuUsage,
            cpuFrequencies = cpuFreqs,
            gpuUsage = gpuUsage,
            gpuFreq = gpuFreq,
            socTemp = socTemp,
            batteryVoltage = bVoltage,
            batteryCurrent = bCurrent,
            batteryPower = power,
            batteryTemp = bTemp,
            ramUsage = ramUsagePct,
            ramUsedGB = ramUsedGB,
            ramTotalGB = ramTotalGB,
            fps = fps,
            frametime = frametime
        )
    }
}
