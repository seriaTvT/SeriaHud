package org.chtholly.seriahud

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.chtholly.seriahud.theme.getCardColors
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    return rows
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
                    Text("确定")
                }
            }
            
            val startIndex = (sliderPosition.start * (allRows.size - 1)).roundToInt().coerceIn(0, allRows.size - 1)
            val endIndex = (sliderPosition.endInclusive * (allRows.size - 1)).roundToInt().coerceIn(0, allRows.size - 1)
            Text(
                "Selected: ${allRows[startIndex].timeStr} - ${allRows[endIndex].timeStr} (${endIndex - startIndex} points)",
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
                MpChartCard("FPS over Time", displayRows) { row, i -> listOf(Entry(i, row.fps)) }

                MpChartCardMulti("CPU and GPU Usage (%)", displayRows) { row, i -> 
                    Pair(Entry(i, row.cpuUsage), Entry(i, row.gpuUsage))
                }

                MpChartCard("SoC Temperature (°C)", displayRows) { row, i -> listOf(Entry(i, row.socTemp)) }

                MpChartCard("Battery Power (W)", displayRows) { row, i -> listOf(Entry(i, row.batteryPower)) }
            }
        }
    }
}

@Composable
fun MpChartCard(title: String, rows: List<CsvRow>, extractor: (CsvRow, Float) -> List<Entry>) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        colors = getCardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        setTouchEnabled(false) // Disable interactions for performance & static view
                        description.isEnabled = false
                        legend.textColor = textColor
                        xAxis.textColor = textColor
                        xAxis.setDrawGridLines(false)
                        axisLeft.textColor = textColor
                        axisRight.isEnabled = false
                        setDrawGridBackground(false)
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
fun MpChartCardMulti(title: String, rows: List<CsvRow>, extractor: (CsvRow, Float) -> Pair<Entry, Entry>) {
    val color1 = android.graphics.Color.parseColor("#4CAF50") // Green
    val color2 = android.graphics.Color.parseColor("#9C27B0") // Purple
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        colors = getCardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        setTouchEnabled(false)
                        description.isEnabled = false
                        legend.textColor = textColor
                        xAxis.textColor = textColor
                        xAxis.setDrawGridLines(false)
                        axisLeft.textColor = textColor
                        axisRight.isEnabled = false
                        setDrawGridBackground(false)
                    }
                },
                update = { chart ->
                    val pairs = rows.mapIndexed { index, row -> extractor(row, index.toFloat()) }
                    val entries1 = pairs.map { it.first }
                    val entries2 = pairs.map { it.second }

                    val set1 = LineDataSet(entries1, "CPU Usage (%)").apply {
                        color = color1
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 1.5f
                    }
                    val set2 = LineDataSet(entries2, "GPU Usage (%)").apply {
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
