package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DailyMetric
import com.example.ui.theme.LocalLapakColors

@Composable
fun RevenueProfitBarChart(
    metrics: List<DailyMetric>,
    modifier: Modifier = Modifier
) {
    if (metrics.isEmpty()) return

    val lapakColors = LocalLapakColors.current
    val maxVal = (metrics.maxOfOrNull { maxOf(it.revenue, it.adminProfit) } ?: 0.0).coerceAtLeast(10000.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grafik 7 Hari Terakhir",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(lapakColors.omzetCardAccent, RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Omzet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(lapakColors.profitCardAccent, RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Untung Lapak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                val primaryColor = lapakColors.omzetCardAccent
                val secondaryColor = lapakColors.profitCardAccent
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                val labelColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val bottomPadding = 24.dp.toPx()
                    val chartHeight = canvasHeight - bottomPadding

                    // Horizontal grid lines
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = chartHeight * (i.toFloat() / gridLines)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val barGroupCount = metrics.size
                    val groupWidth = canvasWidth / barGroupCount
                    val barWidth = (groupWidth * 0.30f).coerceAtMost(20.dp.toPx())
                    val spaceBetweenBars = 3.dp.toPx()

                    metrics.forEachIndexed { index, metric ->
                        val groupCenterX = (index * groupWidth) + (groupWidth / 2f)

                        val omzetHeight = ((metric.revenue / maxVal) * chartHeight).toFloat().coerceIn(4f, chartHeight)
                        val profitHeight = ((metric.adminProfit / maxVal) * chartHeight).toFloat().coerceIn(4f, chartHeight)

                        val omzetLeft = groupCenterX - barWidth - (spaceBetweenBars / 2f)
                        val profitLeft = groupCenterX + (spaceBetweenBars / 2f)

                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(omzetLeft, chartHeight - omzetHeight),
                            size = Size(barWidth, omzetHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        drawRoundRect(
                            color = secondaryColor,
                            topLeft = Offset(profitLeft, chartHeight - profitHeight),
                            size = Size(barWidth, profitHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = labelColorArgb
                                textSize = 10.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            drawText(
                                metric.dateLabel,
                                groupCenterX,
                                canvasHeight - 4.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
        }
    }
}
