package org.chtholly.seriahud

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class CsvRow(
    val timeStr: String,
    val fps: Float,
    val frametime: Float,
    val cpuUsage: Float,
    val gpuUsage: Float,
    val gpuFreq: Float,
    val socTemp: Float,
    val batteryPower: Float,
    val ramUsage: Float
)

fun parseCsv(file: File): List<CsvRow> {
    val rows = mutableListOf<CsvRow>()
    file.useLines { lines ->
        lines.drop(1).forEach { line ->
            val cols = line.split(",")
            if (cols.size >= 9) {
                try {
                    rows.add(
                        CsvRow(
                            timeStr = cols[0],
                            fps = cols[1].toFloat(),
                            frametime = cols[2].toFloat(),
                            cpuUsage = cols[3].toFloat(),
                            gpuUsage = cols[4].toFloat(),
                            gpuFreq = cols[5].toFloat(),
                            socTemp = cols[6].toFloat(),
                            batteryPower = cols[7].toFloat(),
                            ramUsage = cols[8].toFloat()
                        )
                    )
                } catch (e: Exception) {
                    // Ignore parse errors for single lines
                }
            }
        }
    }
    // Post-processing to filter fake GPU Usage zeros
    for (i in rows.indices) {
        val row = rows[i]
        if (row.gpuUsage == 0f && row.gpuFreq > 300f) {
            var prevUsage = 0f
            for (j in i - 1 downTo 0) {
                if (!(rows[j].gpuUsage == 0f && rows[j].gpuFreq > 300f)) {
                    prevUsage = rows[j].gpuUsage
                    break
                }
            }
            var nextUsage = 0f
            for (j in i + 1 until rows.size) {
                if (!(rows[j].gpuUsage == 0f && rows[j].gpuFreq > 300f)) {
                    nextUsage = rows[j].gpuUsage
                    break
                }
            }
            
            val interpolated = if (nextUsage != 0f && prevUsage != 0f) {
                (prevUsage + nextUsage) / 2f
            } else if (prevUsage != 0f) {
                prevUsage
            } else {
                nextUsage
            }
            
            rows[i] = row.copy(gpuUsage = interpolated)
        }
    }
    return rows
}

data class FpsStats(
    val min: Float,
    val max: Float,
    val avg: Float,
    val onePercentLow: Float
)

// 1% low here is the average of the lowest 1% of *sampled* FPS readings (the CSV
// has one row per ~500ms sample, not one per rendered frame), so it approximates
// rather than equals a frame-accurate 1% low. Surfaced with a note in the UI.
fun computeFpsStats(rows: List<CsvRow>): FpsStats {
    if (rows.isEmpty()) return FpsStats(0f, 0f, 0f, 0f)
    val sorted = rows.map { it.fps }.sorted()
    val onePercentCount = max(1, sorted.size / 100)
    val onePercentLow = sorted.take(onePercentCount).average().toFloat()
    return FpsStats(
        min = sorted.first(),
        max = sorted.last(),
        avg = sorted.average().toFloat(),
        onePercentLow = onePercentLow
    )
}

private fun exportChartImage(context: Context, chart: LineChart?) {
    if (chart == null) return
    try {
        val bitmap = chart.chartBitmap
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dest = File(downloads, "seriahud_chart_${System.currentTimeMillis()}.png")
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(context, context.getString(R.string.chart_export_success), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.chart_export_failed), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ChartScreen(file: File, onBack: () -> Unit) {
    var allRows by remember { mutableStateOf(emptyList<CsvRow>()) }
    var displayRows by remember { mutableStateOf(emptyList<CsvRow>()) }
    var sliderPosition by remember { mutableStateOf(0f..1f) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val parsed = parseCsv(file)
            allRows = parsed
            displayRows = parsed
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
        }
        
        if (allRows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Slider and Confirm Row
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val startIdx = (sliderPosition.start * (allRows.size - 1)).roundToInt()
                    val endIdx = (sliderPosition.endInclusive * (allRows.size - 1)).roundToInt()
                    if (startIdx < endIdx) {
                        displayRows = allRows.subList(startIdx, endIdx + 1)
                    }
                }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
            
            val startIndex = (sliderPosition.start * (allRows.size - 1)).roundToInt().coerceIn(0, allRows.size - 1)
            val endIndex = (sliderPosition.endInclusive * (allRows.size - 1)).roundToInt().coerceIn(0, allRows.size - 1)
            Text(
                stringResource(R.string.chart_selected_range, allRows[startIndex].timeStr, allRows[endIndex].timeStr, endIndex - startIndex),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val fpsStats = remember(displayRows) { computeFpsStats(displayRows) }
                FpsStatsCard(fpsStats)

                MpChartCard(stringResource(R.string.chart_fps_over_time), displayRows) { row, i -> listOf(Entry(i, row.fps)) }

                MpChartCardMulti(
                    title = stringResource(R.string.chart_cpu_gpu_usage),
                    label1 = stringResource(R.string.chart_cpu_usage_pct),
                    label2 = stringResource(R.string.chart_gpu_usage_pct),
                    rows = displayRows
                ) { row, i -> 
                    Pair(Entry(i, row.cpuUsage), Entry(i, row.gpuUsage))
                }

                MpChartCard(stringResource(R.string.chart_soc_temp), displayRows) { row, i -> listOf(Entry(i, row.socTemp)) }

                MpChartCard(stringResource(R.string.chart_battery_power), displayRows) { row, i -> listOf(Entry(i, row.batteryPower)) }
            }
        }
    }
}

@Composable
fun FpsStatsCard(stats: FpsStats) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.chart_fps_stats),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.chart_stat_min), stats.min)
                StatItem(stringResource(R.string.chart_stat_avg), stats.avg)
                StatItem(stringResource(R.string.chart_stat_max), stats.max)
                StatItem(stringResource(R.string.chart_stat_1pct_low), stats.onePercentLow)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.chart_stat_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(String.format("%.0f", value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartCardHeader(title: String, onReset: () -> Unit, onExport: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.chart_reset_zoom), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.chart_export_image), modifier = Modifier.size(18.dp))
        }
    }
}

// Enable horizontal (time-axis) pinch-zoom + pan. Y zoom stays off and the
// chart requests disallow-intercept while gesturing, so the outer vertical
// scroll keeps working.
private fun LineChart.enableTimeAxisZoom() {
    setTouchEnabled(true)
    isDragEnabled = true
    setScaleXEnabled(true)
    setScaleYEnabled(false)
    setPinchZoom(true)
    isDoubleTapToZoomEnabled = false
}

@Composable
fun MpChartCard(title: String, rows: List<CsvRow>, extractor: (CsvRow, Float) -> List<Entry>) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    var chartRef by remember { mutableStateOf<LineChart?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            ChartCardHeader(
                title = title,
                onReset = { chartRef?.fitScreen() },
                onExport = { exportChartImage(context, chartRef) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        enableTimeAxisZoom()
                        description.isEnabled = false
                        legend.textColor = textColor
                        xAxis.textColor = textColor
                        xAxis.setDrawGridLines(false)
                        axisLeft.textColor = textColor
                        axisRight.isEnabled = false
                        setDrawGridBackground(false)
                        chartRef = this
                    }
                },
                update = { chart ->
                    val entries = rows.mapIndexed { index, row -> extractor(row, index.toFloat()).first() }
                    val dataSet = LineDataSet(entries, title).apply {
                        color = primaryColor
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 1.5f
                    }
                    chart.data = LineData(dataSet)
                    chart.invalidate()
                }
            )
        }
    }
}

@Composable
fun MpChartCardMulti(title: String, label1: String, label2: String, rows: List<CsvRow>, extractor: (CsvRow, Float) -> Pair<Entry, Entry>) {
    val context = LocalContext.current
    val color1 = MaterialTheme.colorScheme.primary.toArgb()
    val color2 = MaterialTheme.colorScheme.tertiary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    var chartRef by remember { mutableStateOf<LineChart?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            ChartCardHeader(
                title = title,
                onReset = { chartRef?.fitScreen() },
                onExport = { exportChartImage(context, chartRef) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        enableTimeAxisZoom()
                        description.isEnabled = false
                        legend.textColor = textColor
                        xAxis.textColor = textColor
                        xAxis.setDrawGridLines(false)
                        axisLeft.textColor = textColor
                        axisRight.isEnabled = false
                        setDrawGridBackground(false)
                        chartRef = this
                    }
                },
                update = { chart ->
                    val pairs = rows.mapIndexed { index, row -> extractor(row, index.toFloat()) }
                    val entries1 = pairs.map { it.first }
                    val entries2 = pairs.map { it.second }

                    val set1 = LineDataSet(entries1, label1).apply {
                        color = color1
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 1.5f
                    }
                    val set2 = LineDataSet(entries2, label2).apply {
                        color = color2
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 1.5f
                    }
                    chart.data = LineData(set1, set2)
                    chart.invalidate()
                }
            )
        }
    }
}
