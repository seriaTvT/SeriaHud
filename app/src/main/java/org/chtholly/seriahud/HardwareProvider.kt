package org.chtholly.seriahud

interface IHardwareProvider {
    fun getCommands(): Array<String>
    
    // Parses the command output starting from lineIndex.
    // Returns the new lineIndex and populates the given SystemStatsBuilder.
    fun getDetectedPaths(): Map<String, String>
    fun parseOutput(out: List<String>, startIndex: Int, builder: SystemStatsBuilder): Int
}

class SystemStatsBuilder {
    var cpuUsage: Float = 0f
    var cpuFreqs: MutableList<Int> = mutableListOf()
    var gpuUsage: Int = 0
    var gpuFreq: Int = 0
    var socTemp: Float = 0f
    var bVoltage: Float = 0f
    var bCurrent: Float = 0f
    var bTemp: Float = 0f
    var ramTotal: Long = 0L
    var ramFree: Long = 0L
    var ramAvailable: Long = 0L
    
    fun build(fps: Int, frametime: Float): SystemStats {
        val ramUsed = ramTotal - (if (ramAvailable > 0) ramAvailable else ramFree)
        val ramUsagePct = if (ramTotal > 0) (ramUsed.toFloat() / ramTotal.toFloat()) * 100f else 0f
        val ramUsedGB = ramUsed / 1024f / 1024f
        val ramTotalGB = ramTotal / 1024f / 1024f
        val bPower = (bVoltage * bCurrent).let { if (it < 0) -it else it }

        return SystemStats(
            cpuUsage = cpuUsage,
            cpuFrequencies = cpuFreqs,
            gpuUsage = gpuUsage,
            gpuFreq = gpuFreq,
            socTemp = socTemp,
            batteryVoltage = bVoltage,
            batteryCurrent = bCurrent,
            batteryPower = bPower,
            batteryTemp = bTemp,
            ramUsage = ramUsagePct,
            ramUsedGB = ramUsedGB,
            ramTotalGB = ramTotalGB,
            fps = fps,
            frametime = frametime
        )
    }
}
