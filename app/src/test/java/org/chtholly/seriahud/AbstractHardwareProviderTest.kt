package org.chtholly.seriahud

import org.junit.Assert.assertEquals
import org.junit.Test

class AbstractHardwareProviderTest {

    // 构建一个用于测试的假提供者，暴露公共逻辑
    private class TestHardwareProvider(val coreCount: Int) : AbstractHardwareProvider() {
        init {
            // 模拟动态获取到的核心路径
            for (i in 0 until coreCount) {
                cpuPaths.add("/mock/cpu$i")
            }
            gpuUsagePath = "/mock/gpu_usage"
            gpuFreqPath = "/mock/gpu_freq"
            socTempPath = "/mock/soc_temp"
            battVoltagePath = "/mock/batt_voltage"
            battCurrentPath = "/mock/batt_current"
            battTempPath = "/mock/batt_temp" // 默认不含thermal_zone，将走除以10的分支
        }

        // 测试时直接返回数字即可，因为我们要测的是公共流程而非特定平台的字符串格式
        override fun parseGpuUsage(line: String): Int = line.toIntOrNull() ?: 0
        override fun parseGpuFreq(line: String): Int = line.toIntOrNull() ?: 0
    }

    @Test
    fun testParseOutput_WithOfflineCore_ShouldReturnZeroFreqAndNotShiftOthers() {
        val provider = TestHardwareProvider(8)
        
        // 模拟底层 Shell.cmd 的输出序列。注意，这里完全复刻了带 || echo 0 容错的机制
        val mockOut = listOf(
            "cpu  100 0 200 700 0 0 0", // [0] CPU Usage
            "1800000",                 // [1] cpu0 freq
            "1800000",                 // [2] cpu1 freq
            "1800000",                 // [3] cpu2 freq
            "1800000",                 // [4] cpu3 freq
            "2000000",                 // [5] cpu4 freq
            "2000000",                 // [6] cpu5 freq
            "0",                       // [7] cpu6 freq -> 模拟 Offline 核心返回了 echo 0
            "0",                       // [8] cpu7 freq -> 模拟 Offline 核心返回了 echo 0
            "45",                      // [9] gpu usage
            "350",                     // [10] gpu freq
            "38000",                   // [11] soc temp (千分之一度)
            "4200000",                 // [12] batt voltage (微伏)
            "1500000",                 // [13] batt current (微安)
            "350",                     // [14] batt temp (十分之一度，标准 power_supply)
            "MemTotal:        8000000 kB", // [15] RAM
            "MemFree:         1000000 kB", // [16] RAM
            "MemAvailable:    3000000 kB"  // [17] RAM
        )

        val builder = SystemStatsBuilder()
        // 模拟解析操作
        provider.parseOutput(mockOut, 0, builder)
        
        // 构建最终的 UI 展现层对象
        val stats = builder.build(60, 16.6f)
        
        // 验证: CPU 核心解析容错性，确保 Offline 的核心被解析为了 0，并且未出现错位吞噬 GPU 数据的情况
        assertEquals(8, stats.cpuFrequencies.size)
        assertEquals(1800, stats.cpuFrequencies[0])
        assertEquals(2000, stats.cpuFrequencies[4])
        assertEquals(0, stats.cpuFrequencies[6])
        assertEquals(0, stats.cpuFrequencies[7])
        
        // 验证: 如果没有发生错位，GPU的数据应当精准落在原位
        assertEquals(45, stats.gpuUsage)
        assertEquals(350, stats.gpuFreq)
        
        // 验证: 温度传感器的单位换算是否正确（分别除了 1000f 和 10f）
        assertEquals(38.0f, stats.socTemp, 0.01f)
        assertEquals(35.0f, stats.batteryTemp, 0.01f)
        
        // 验证: 电池功耗 (P = U * I)
        assertEquals(4.2f, stats.batteryVoltage, 0.01f)
        assertEquals(1.5f, stats.batteryCurrent, 0.01f)
        assertEquals(6.3f, stats.batteryPower, 0.01f) // 4.2V * 1.5A = 6.3W
    }
}
