package org.chtholly.seriahud

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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.chtholly.seriahud.theme.HudPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        // Observable so the Home screen toggle reflects live service state.
        var isRunning by mutableStateOf(false)
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var windowLayoutParams: WindowManager.LayoutParams
    
    private lateinit var monitorManager: MonitorManager
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
        monitorManager = MonitorManager(configManager)
        
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
        )
        applyPosition(configManager.configFlow.value)
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            
            setContent {
                var stats by remember { mutableStateOf(SystemStats()) }
                var visualGpuUsage by remember { mutableIntStateOf(0) }
                val config by configManager.configFlow.collectAsState()
                
                var frametimeHistory by remember { mutableStateOf(emptyList<Float>()) }
                var isRecording by remember { mutableStateOf(false) }
                
                LaunchedEffect(Unit) {
                    monitorManager.getStatsFlow().collectLatest {
                        stats = it
                        if (!(it.gpuUsage == 0 && it.gpuFreq > 300)) {
                            visualGpuUsage = it.gpuUsage
                        }
                        
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
                    visualGpuUsage = visualGpuUsage,
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
                    onDragStart = {
                        // A drag converts a corner preset into a free 'custom'
                        // position, anchored from the view's current location.
                        val cfg = configManager.configFlow.value
                        if (cfg.positionPreset != HudConfig.POS_CUSTOM) {
                            val loc = IntArray(2)
                            composeView.getLocationOnScreen(loc)
                            windowLayoutParams.gravity = Gravity.TOP or Gravity.START
                            windowLayoutParams.x = loc[0]
                            windowLayoutParams.y = loc[1]
                            windowManager.updateViewLayout(composeView, windowLayoutParams)
                        }
                    },
                    onDrag = { dx, dy ->
                        windowLayoutParams.x += dx.toInt()
                        windowLayoutParams.y += dy.toInt()
                        windowManager.updateViewLayout(composeView, windowLayoutParams)
                    },
                    onDragEnd = {
                        configManager.updateConfig(
                            configManager.configFlow.value.copy(
                                positionPreset = HudConfig.POS_CUSTOM,
                                overlayX = windowLayoutParams.x,
                                overlayY = windowLayoutParams.y
                            )
                        )
                    }
                )
            }
        }

        windowManager.addView(composeView, windowLayoutParams)
        isRunning = true

        // Re-apply position live when the user picks a corner preset in settings.
        lifecycleScope.launch {
            configManager.configFlow
                .map { it.positionPreset }
                .distinctUntilChanged()
                .drop(1)
                .collect { preset ->
                    if (preset != HudConfig.POS_CUSTOM) {
                        applyPosition(configManager.configFlow.value)
                        runCatching { windowManager.updateViewLayout(composeView, windowLayoutParams) }
                    }
                }
        }
    }

    private fun applyPosition(cfg: HudConfig) {
        val margin = 24
        when (cfg.positionPreset) {
            HudConfig.POS_TOP_LEFT -> {
                windowLayoutParams.gravity = Gravity.TOP or Gravity.START
                windowLayoutParams.x = margin
                windowLayoutParams.y = margin
            }
            HudConfig.POS_TOP_RIGHT -> {
                windowLayoutParams.gravity = Gravity.TOP or Gravity.END
                windowLayoutParams.x = margin
                windowLayoutParams.y = margin
            }
            HudConfig.POS_BOTTOM_LEFT -> {
                windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.START
                windowLayoutParams.x = margin
                windowLayoutParams.y = margin
            }
            HudConfig.POS_BOTTOM_RIGHT -> {
                windowLayoutParams.gravity = Gravity.BOTTOM or Gravity.END
                windowLayoutParams.x = margin
                windowLayoutParams.y = margin
            }
            else -> {
                windowLayoutParams.gravity = Gravity.TOP or Gravity.START
                windowLayoutParams.x = cfg.overlayX
                windowLayoutParams.y = cfg.overlayY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dataRecorder.isRecording) {
            dataRecorder.stopRecording()
        }
        windowManager.removeView(composeView)
        isRunning = false
    }
}

// One HUD metric line. `compact` metrics are packed together onto a shared row.
private class MetricCell(val compact: Boolean, val content: @Composable () -> Unit)

@Composable
fun OverlayUI(
    stats: SystemStats,
    visualGpuUsage: Int,
    config: HudConfig,
    frametimeHistory: List<Float>,
    isRecording: Boolean,
    showRecordDot: Boolean,
    onToggleRecord: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val palette = HudPalette.byIndex(config.accentPresetIndex)
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        color = palette.text,
        fontSize = (12 * config.fontScale).sp,
        fontWeight = FontWeight.Bold
    )

    // Ordered metric cells for the metrics that can share lines (cores are
    // always rendered as their own block below).
    val cells = buildList {
        if (config.showFps) {
            add(MetricCell(HudConfig.METRIC_FPS in config.compactMetrics) {
                Row {
                    Text("FPS  ", style = textStyle, color = palette.fps)
                    Text(String.format("%3d ", stats.fps), style = textStyle)
                    Text(String.format("%5.1f ms", stats.frametime), style = textStyle)
                }
            })
        }
        if (config.showGpu) {
            add(MetricCell(HudConfig.METRIC_GPU in config.compactMetrics) {
                Row {
                    Text("GPU  ", style = textStyle, color = palette.gpu)
                    Text(String.format("%3d%% ", visualGpuUsage), style = textStyle)
                    Text(String.format("%4d MHz", stats.gpuFreq), style = textStyle)
                }
            })
        }
        if (config.showCpuOverall) {
            add(MetricCell(HudConfig.METRIC_CPU in config.compactMetrics) {
                Row {
                    Text("CPU  ", style = textStyle, color = palette.cpu)
                    Text(String.format("%3.0f%% ", stats.cpuUsage), style = textStyle)
                    if (config.showSocTemp) {
                        Text(String.format("%2.0f°C", stats.socTemp), style = textStyle)
                    }
                }
            })
        }
        if (config.showRam) {
            add(MetricCell(HudConfig.METRIC_RAM in config.compactMetrics) {
                Row {
                    Text("RAM  ", style = textStyle, color = palette.ram)
                    Text(String.format("%3.0f%% ", stats.ramUsage), style = textStyle)
                    Text(String.format("%.1f/%.1f GB", stats.ramUsedGB, stats.ramTotalGB), style = textStyle)
                }
            })
        }
        if (config.showBattery) {
            add(MetricCell(HudConfig.METRIC_BAT in config.compactMetrics) {
                Row {
                    Text("BAT  ", style = textStyle, color = palette.battery)
                    Text(String.format("%4.1f W ", stats.batteryPower), style = textStyle)
                    Text(String.format("%.1f°C", stats.batteryTemp), style = textStyle)
                }
            })
        }
    }

    // Group consecutive compact cells into shared lines; others stand alone.
    val lines = buildList {
        var i = 0
        while (i < cells.size) {
            if (cells[i].compact) {
                val run = mutableListOf<MetricCell>()
                while (i < cells.size && cells[i].compact) {
                    run.add(cells[i]); i++
                }
                add(run)
            } else {
                add(listOf(cells[i])); i++
            }
        }
    }

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .background(palette.background.copy(alpha = config.overlayOpacity), RoundedCornerShape(config.cornerRadiusDp.dp))
            .padding(12.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        lines.forEachIndexed { index, line ->
            if (index == 0 && config.showRecordButton) {
                // Keep the record button anchored top-right of the first line.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row { MetricLine(line) }
                    RecordButton(isRecording, showRecordDot, onToggleRecord)
                }
            } else {
                MetricLine(line)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Record button when no metric lines are visible but it's enabled.
        if (lines.isEmpty() && config.showRecordButton) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RecordButton(isRecording, showRecordDot, onToggleRecord)
            }
        }

        // CPU Cores (always their own rows)
        if (config.showCpuCores && stats.cpuFrequencies.isNotEmpty()) {
            config.selectedCpuCores.forEach { coreId ->
                if (coreId < stats.cpuFrequencies.size) {
                    Row {
                        Text("CPU$coreId ", style = textStyle, color = palette.cpuCore)
                        Text(String.format("%4d MHz", stats.cpuFrequencies[coreId]), style = textStyle)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }

        // Frametime Graph
        if (config.showFrametimeGraph && frametimeHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            FrametimeGraph(frametimeHistory, textStyle, palette)
        }
    }
}

@Composable
private fun MetricLine(line: List<MetricCell>) {
    if (line.size == 1) {
        line[0].content()
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            line.forEach { it.content() }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, showRecordDot: Boolean, onToggleRecord: () -> Unit) {
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

@Composable
fun FrametimeGraph(history: List<Float>, textStyle: androidx.compose.ui.text.TextStyle, palette: HudPalette) {
    val graphColor = palette.graphLine
    val maxFt = (history.maxOrNull() ?: 16.6f).coerceAtLeast(16.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(palette.graphBackground, RoundedCornerShape(4.dp))
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
