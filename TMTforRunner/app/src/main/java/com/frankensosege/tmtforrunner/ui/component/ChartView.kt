package com.frankensosege.tmtforrunner.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.frankensosege.tmtforrunner.data.WorkoutMinute
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

// ── 항목별 고정 색상 (서로 구분이 잘 되는 4색) ─────────────
val ColorSteps    = Color(0xFF1E88E5)  // 파랑
val ColorDistance = Color(0xFF43A047)  // 초록
val ColorCalories = Color(0xFFFB8C00)  // 주황
val ColorSpeed    = Color(0xFF8E24AA)  // 보라

/** 범례에 표시할 다국어 라벨 */
data class StatLabels(
    val steps: String,
    val distance: String,
    val calories: String,
    val speed: String
)

/** 월별 막대 차트용 하루 요약 */
data class DayStat(
    val day: Int,
    val steps: Int,
    val distanceKm: Double,
    val calories: Int,
    val speedKmH: Double
)

private fun minuteLabel(minute: Int) = "%d:%02d".format(minute / 60, minute % 60)

// ─────────────────────────────────────────────────────────
// 일별: 정규화 꺾은선 (HORIZONTAL_BEZIER)
// ─────────────────────────────────────────────────────────
@Composable
fun NormalizedLineChart(
    minutes: List<WorkoutMinute>,
    labels: StatLabels,
    textColor: Color,
    onMinuteSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSelected by rememberUpdatedState(onMinuteSelected)
    val textArgb = textColor.toArgb()
    val lineData = remember(minutes, labels) { buildLineData(minutes, labels, textArgb) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                setDragEnabled(false)          // 부모 스와이프(날짜 전환)와 충돌 방지
                setNoDataText("")
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                axisLeft.axisMaximum = 105f
                axisLeft.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}%"
                }
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = minuteLabel(value.toInt())
                }
                legend.isWordWrapEnabled = true
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        currentOnSelected(e?.x?.toInt())
                    }
                    override fun onNothingSelected() {
                        currentOnSelected(null)
                    }
                })
            }
        },
        update = { chart ->
            chart.xAxis.textColor    = textArgb
            chart.axisLeft.textColor = textArgb
            chart.legend.textColor   = textArgb
            // 같은 데이터면 재설정하지 않음 → 터치 하이라이트 유지
            if (chart.tag !== lineData) {
                chart.tag  = lineData
                chart.data = lineData
                chart.highlightValues(null)
                chart.invalidate()
            }
        }
    )
}

private fun buildLineData(
    minutes: List<WorkoutMinute>,
    labels: StatLabels,
    textArgb: Int
): LineData {
    // minuteIndex 중복 시 마지막 값 사용, x 오름차순 정렬 (MPAndroidChart 요구사항)
    val points = minutes.groupBy { it.minuteIndex }.map { it.value.last() }.sortedBy { it.minuteIndex }

    val maxSteps = (points.maxOfOrNull { it.steps } ?: 0).coerceAtLeast(1).toFloat()
    val maxDist  = (points.maxOfOrNull { it.distanceKm } ?: 0.0).coerceAtLeast(0.001).toFloat()
    val maxCal   = (points.maxOfOrNull { it.calories } ?: 0).coerceAtLeast(1).toFloat()

    val speeds = points.filter { it.minuteIndex > 0 }
        .map { it.minuteIndex to it.distanceKm / (it.minuteIndex / 60.0) }
    val maxSpeed = (speeds.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.001).toFloat()

    val stepsSet = lineSet(labels.steps, ColorSteps,
        points.map { Entry(it.minuteIndex.toFloat(), it.steps / maxSteps * 100f) })
    val distSet = lineSet(labels.distance, ColorDistance,
        points.map { Entry(it.minuteIndex.toFloat(), it.distanceKm.toFloat() / maxDist * 100f) })
    val calSet = lineSet(labels.calories, ColorCalories,
        points.map { Entry(it.minuteIndex.toFloat(), it.calories / maxCal * 100f) })
    val speedSet = lineSet(labels.speed, ColorSpeed,
        speeds.map { Entry(it.first.toFloat(), it.second.toFloat() / maxSpeed * 100f) })

    return LineData(listOf(stepsSet, distSet, calSet, speedSet).filter { it.entryCount > 0 }).apply {
        setDrawValues(false)
        setValueTextColor(textArgb)
    }
}

private fun lineSet(label: String, color: Color, entries: List<Entry>) =
    LineDataSet(entries, label).apply {
        mode = LineDataSet.Mode.HORIZONTAL_BEZIER
        this.color = color.toArgb()
        lineWidth = 2.2f
        setDrawCircles(false)
        setDrawValues(false)
        setDrawHorizontalHighlightIndicator(false)
    }

// ─────────────────────────────────────────────────────────
// 월별: 정규화 누적 막대 (stacked bars)
// ─────────────────────────────────────────────────────────
@Composable
fun MonthlyStackedBarChart(
    days: List<DayStat>,
    daysInMonth: Int,
    labels: StatLabels,
    textColor: Color,
    onDaySelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSelected by rememberUpdatedState(onDaySelected)
    val textArgb = textColor.toArgb()
    val barData = remember(days, labels) { buildBarData(days, labels, textArgb) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                setDragEnabled(false)
                setNoDataText("")
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                axisLeft.setDrawLabels(false)   // 단위가 다른 값의 합이라 눈금 숨김
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toInt().toString()
                }
                legend.isWordWrapEnabled = true
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        currentOnSelected(e?.x?.toInt())
                    }
                    override fun onNothingSelected() {
                        currentOnSelected(null)
                    }
                })
            }
        },
        update = { chart ->
            chart.xAxis.textColor  = textArgb
            chart.legend.textColor = textArgb
            val key = Pair(barData, daysInMonth)
            if (chart.tag != key) {
                chart.tag = key
                chart.xAxis.axisMinimum = 0.5f
                chart.xAxis.axisMaximum = daysInMonth + 0.5f
                chart.data = barData
                chart.highlightValues(null)
                chart.invalidate()
            }
        }
    )
}

private fun buildBarData(days: List<DayStat>, labels: StatLabels, textArgb: Int): BarData {
    val maxSteps = (days.maxOfOrNull { it.steps } ?: 0).coerceAtLeast(1).toFloat()
    val maxDist  = (days.maxOfOrNull { it.distanceKm } ?: 0.0).coerceAtLeast(0.001).toFloat()
    val maxCal   = (days.maxOfOrNull { it.calories } ?: 0).coerceAtLeast(1).toFloat()
    val maxSpeed = (days.maxOfOrNull { it.speedKmH } ?: 0.0).coerceAtLeast(0.001).toFloat()

    val entries = days.sortedBy { it.day }.map {
        BarEntry(
            it.day.toFloat(),
            floatArrayOf(
                it.steps / maxSteps * 100f,
                it.distanceKm.toFloat() / maxDist * 100f,
                it.calories / maxCal * 100f,
                it.speedKmH.toFloat() / maxSpeed * 100f
            )
        )
    }
    val set = BarDataSet(entries, "").apply {
        setColors(ColorSteps.toArgb(), ColorDistance.toArgb(), ColorCalories.toArgb(), ColorSpeed.toArgb())
        stackLabels = arrayOf(labels.steps, labels.distance, labels.calories, labels.speed)
        setDrawValues(false)
    }
    return BarData(set).apply {
        barWidth = 0.8f
        setValueTextColor(textArgb)
    }
}