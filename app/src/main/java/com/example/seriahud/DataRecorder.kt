package com.example.seriahud

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataRecorder(private val context: Context) {
    private var fileWriter: FileWriter? = null
    var isRecording = false
        private set

    fun startRecording() {
        if (isRecording) return
        
        try {
            val recordsDir = File(context.getExternalFilesDir(null), "records")
            if (!recordsDir.exists()) {
                recordsDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(recordsDir, "record_$timestamp.csv")
            
            fileWriter = FileWriter(file, true)
            // Write CSV Header
            fileWriter?.append("Time,FPS,Frametime_ms,CPU_Usage_Pct,GPU_Usage_Pct,GPU_Freq_MHz,SoC_Temp_C,Battery_Power_W,RAM_Usage_Pct\n")
            fileWriter?.flush()
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        
        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileWriter = null
            isRecording = false
        }
    }

    fun appendData(stats: SystemStats) {
        if (!isRecording || fileWriter == null) return
        
        try {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            fileWriter?.append(
                String.format(
                    Locale.US,
                    "%s,%d,%.2f,%.1f,%d,%d,%.1f,%.2f,%.1f\n",
                    time,
                    stats.fps,
                    stats.frametime,
                    stats.cpuUsage,
                    stats.gpuUsage,
                    stats.gpuFreq,
                    stats.socTemp,
                    stats.batteryPower,
                    stats.ramUsage
                )
            )
            // Optionally flush every time or periodically
            // fileWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
