package com.example.nag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.nag.logic.Schedule

/**
 * Bars for each session with a trailing-average line over the top. Bars rather than a
 * line for the values themselves: sessions are discrete events, and joining them up
 * would imply readings on the days in between that don't exist.
 *
 * Axis labels are ordinary composables above and below rather than text painted into
 * the canvas, which keeps this free of platform text measuring.
 */
@Composable
fun BarChart(
    points: List<Schedule.Point>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    showTrend: Boolean = true
) {
    if (points.isEmpty()) {
        Text(
            "Nothing logged in this period yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }

    val max = (points.maxOf { it.value }).coerceAtLeast(1)
    val average = if (showTrend) Schedule.movingAverage(points) else emptyList()
    val trendColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("$max $unit", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val count = points.size
            val slot = size.width / count
            val barWidth = (slot * 0.62f).coerceAtLeast(1.5f)
            val inset = (slot - barWidth) / 2f

            points.forEachIndexed { index, point ->
                val fraction = point.value.toFloat() / max
                val barHeight = size.height * fraction
                drawRect(
                    color = color,
                    topLeft = Offset(index * slot + inset, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }

            if (average.size == count && count > 1) {
                val path = Path()
                average.forEachIndexed { index, value ->
                    val x = index * slot + slot / 2f
                    val y = size.height - size.height * (value / max).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, trendColor, style = Stroke(width = 2.5f))
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                points.first().date.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            if (points.size > 1) {
                Text(points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
