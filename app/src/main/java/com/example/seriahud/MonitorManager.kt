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

    private var activeWindows: List<String> = emptyList()
    private var lastFpsCheckFrames = LongArray(128)
    private var windowUpdateCounter = 0

    private var hardwareProvider: IHardwareProvider = MtkHardwareProvider() // default

    init {
        val platform = Shell.cmd("getprop ro.board.platform").exec().out.joinToString("").lowercase()
        val hardware = Shell.cmd("getprop ro.hardware").exec().out.joinToString("").lowercase()
        if (platform.contains("mt") || hardware.contains("mt")) {
            hardwareProvider = MtkHardwareProvider()
        } else if (platform.contains("lito") || platform.contains("sm") || platform.contains("msm") || hardware.contains("qcom")) {
            hardwareProvider = QcomHardwareProvider()
        } else {
            // fallback
            hardwareProvider = MtkHardwareProvider()
        }
    }

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
        
        val hardwareCmds = hardwareProvider.getCommands()
        val cmds = hardwareCmds + sfCmds
        
        val result = Shell.cmd(*cmds).exec()
        val out = result.out

        val builder = SystemStatsBuilder()
        var lineIndex = hardwareProvider.parseOutput(out, 0, builder)

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
        
        return builder.build(fps = maxFps, frametime = bestFrametime)
    }
}
