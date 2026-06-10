package com.example.seriahud

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var windowLayoutParams: WindowManager.LayoutParams
    
    private val monitorManager = MonitorManager()
    private lateinit var dataRecorder: DataRecorder
    private lateinit var configManager: HudConfigManager
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
        
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        
        dataRecorder = DataRecorder(this)
        configManager = HudConfigManager(this)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            
            setContent {
                var stats by remember { mutableStateOf(SystemStats()) }
                val config by configManager.configFlow.collectAsState()
                
                var frametimeHistory by remember { mutableStateOf(emptyList<Float>()) }
                var isRecording by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    monitorManager.getStatsFlow().collectLatest {
                        stats = it
                        if (config.showFrametimeGraph) {
                            frametimeHistory = (frametimeHistory + it.frametime).takeLast(60)
                        }
                        if (dataRecorder.isRecording) {
                            dataRecorder.appendData(it)
                        }
                    }
                }
                
                // Record blinking effect
                var showRecordDot by remember { mutableStateOf(true) }
                LaunchedEffect(isRecording) {
                    if (isRecording) {
                        while (true) {
                            showRecordDot = !showRecordDot
                            delay(500)
                        }
                    } else {
                        showRecordDot = true
                    }
                }
                
                OverlayUI(
                    stats = stats,
                    config = config,
                    frametimeHistory = frametimeHistory,
                    isRecording = isRecording,
                    showRecordDot = showRecordDot,
                    onToggleRecord = {
                        if (dataRecorder.isRecording) {
                            dataRecorder.stopRecording()
                            isRecording = false
                        } else {
                            dataRecorder.startRecording()
                            isRecording = true
                        }
                    },
                    onDrag = { dx, dy ->
                        windowLayoutParams.x += dx.toInt()
                        windowLayoutParams.y += dy.toInt()
                        windowManager.updateViewLayout(composeView, windowLayoutParams)
                    }
                )
            }
        }
        
        windowManager.addView(composeView, windowLayoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dataRecorder.isRecording) {
            dataRecorder.stopRecording()
        }
        windowManager.removeView(composeView)
    }
}

@Composable
fun OverlayUI(
    stats: SystemStats, 
    config: HudConfig,
    frametimeHistory: List<Float>,
    isRecording: Boolean,
    showRecordDot: Boolean,
    onToggleRecord: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        val textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FPS
            if (config.showFps) {
                Row {
                    Text("FPS  ", style = textStyle, color = Color(0xFFE5C07B))
                    Text(String.format("%3d ", stats.fps), style = textStyle)
                    Text(String.format("%5.1f ms", stats.frametime), style = textStyle)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp)) // Maintain row if FPS is off but record button is on
            }
            
            // Record Button
            if (config.showRecordButton) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isRecording && showRecordDot) Color.Red else if (isRecording) Color.Transparent else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onToggleRecord() }
                )
            }
        }
        
        if (config.showFps) Spacer(modifier = Modifier.height(4.dp))
        
        // GPU
        if (config.showGpu) {
            Row {
                Text("GPU  ", style = textStyle, color = Color(0xFF98C379))
                Text(String.format("%3d%% ", stats.gpuUsage), style = textStyle)
                Text(String.format("%4d MHz", stats.gpuFreq), style = textStyle)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // CPU Overall
        if (config.showCpuOverall) {
            Row {
                Text("CPU  ", style = textStyle, color = Color(0xFF61AFEF))
                Text(String.format("%3.0f%% ", stats.cpuUsage), style = textStyle)
                if (config.showSocTemp) {
                    Text(String.format("%2.0f°C", stats.socTemp), style = textStyle)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // CPU Cores
        if (config.showCpuCores && stats.cpuFrequencies.isNotEmpty()) {
            config.selectedCpuCores.forEach { coreId ->
                if (coreId < stats.cpuFrequencies.size) {
                    Row {
                        Text("CPU$coreId ", style = textStyle, color = Color(0xFF56B6C2))
                        Text(String.format("%4d MHz", stats.cpuFrequencies[coreId]), style = textStyle)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        // RAM
        if (config.showRam) {
            Row {
                Text("RAM  ", style = textStyle, color = Color(0xFFC678DD))
                Text(String.format("%3.0f%% ", stats.ramUsage), style = textStyle)
                Text(String.format("%.1f/%.1f GB", stats.ramUsedGB, stats.ramTotalGB), style = textStyle)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // BAT
        if (config.showBattery) {
            Row {
                Text("BAT  ", style = textStyle, color = Color(0xFFD19A66))
                Text(String.format("%4.1f W ", stats.batteryPower), style = textStyle)
                Text(String.format("%.1f°C", stats.batteryTemp), style = textStyle)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // Frametime Graph
        if (config.showFrametimeGraph && frametimeHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            FrametimeGraph(frametimeHistory, textStyle)
        }
    }
}

@Composable
fun FrametimeGraph(history: List<Float>, textStyle: androidx.compose.ui.text.TextStyle) {
    val graphColor = Color(0xFFE5C07B)
    val maxFt = (history.maxOrNull() ?: 16.6f).coerceAtLeast(16.6f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val stepX = width / 60f
            
            val path = Path()
            history.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value / maxFt * height).coerceIn(0f, height)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = graphColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        Text(
            text = String.format("%.1f", maxFt),
            style = textStyle.copy(fontSize = 8.sp, color = Color.Gray),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
        )
    }
}
