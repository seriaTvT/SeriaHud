package com.example.seriahud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var windowLayoutParams: WindowManager.LayoutParams
    
    private val monitorManager = MonitorManager()
    
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
        
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        
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
                
                LaunchedEffect(Unit) {
                    monitorManager.getStatsFlow().collectLatest {
                        stats = it
                    }
                }
                
                OverlayUI(stats = stats, onDrag = { dx, dy ->
                    windowLayoutParams.x += dx.toInt()
                    windowLayoutParams.y += dy.toInt()
                    windowManager.updateViewLayout(composeView, windowLayoutParams)
                })
            }
        }
        
        windowManager.addView(composeView, windowLayoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(composeView)
    }
}

@Composable
fun OverlayUI(stats: SystemStats, onDrag: (Float, Float) -> Unit) {
    Column(
        modifier = Modifier
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
        
        // FPS
        Row {
            Text("FPS  ", style = textStyle, color = Color(0xFFE5C07B))
            Text("${stats.fps} ", style = textStyle)
            Text(String.format("%.1f ms", stats.frametime), style = textStyle)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // GPU
        Row {
            Text("GPU  ", style = textStyle, color = Color(0xFF98C379))
            Text(String.format("%3d%% ", stats.gpuUsage), style = textStyle)
            Text(String.format("%4d MHz", stats.gpuFreq), style = textStyle)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // CPU
        Row {
            Text("CPU  ", style = textStyle, color = Color(0xFF61AFEF))
            Text(String.format("%3.0f%% ", stats.cpuUsage), style = textStyle)
            Text(String.format("%2.0f°C", stats.socTemp), style = textStyle)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // RAM
        Row {
            Text("RAM  ", style = textStyle, color = Color(0xFFC678DD))
            Text(String.format("%3.0f%% ", stats.ramUsage), style = textStyle)
            Text(String.format("%.1f/%.1f GB", stats.ramUsedGB, stats.ramTotalGB), style = textStyle)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // BAT
        Row {
            Text("BAT  ", style = textStyle, color = Color(0xFFD19A66))
            Text(String.format("%4.1f W ", stats.batteryPower), style = textStyle)
            Text(String.format("%.1f°C", stats.batteryTemp), style = textStyle)
        }
    }
}
