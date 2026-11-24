package com.frankensosege.tmtforrunner.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.frankensosege.tmtforrunner.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frankensosege.tmtforrunner.BuildConfig
import com.frankensosege.tmtforrunner.data.WorkoutMinute
import com.frankensosege.tmtforrunner.data.WorkoutRecord
import com.frankensosege.tmtforrunner.data.i18n.AppStrings
import com.frankensosege.tmtforrunner.ui.component.AdBanner
import com.frankensosege.tmtforrunner.ui.component.ColorCalories
import com.frankensosege.tmtforrunner.ui.component.ColorDistance
import com.frankensosege.tmtforrunner.ui.component.ColorSpeed
import com.frankensosege.tmtforrunner.ui.component.ColorSteps
import com.frankensosege.tmtforrunner.ui.component.DayStat
import com.frankensosege.tmtforrunner.ui.component.MicIndicator
import com.frankensosege.tmtforrunner.ui.component.MonthlyStackedBarChart
import com.frankensosege.tmtforrunner.ui.component.NormalizedLineChart
import com.frankensosege.tmtforrunner.ui.component.StatLabels
import com.frankensosege.tmtforrunner.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ── Lint 수정 목록 (유지) ─────────────────────────────────
// ✅ [Lint-1]  modifier: Modifier = Modifier 파라미터 (모든 Composable)
// ✅ [Lint-2]  collectAsStateWithLifecycle() 사용
// ✅ [Lint-4]  SWIPE_THRESHOLD → const val
// ✅ [Lint-5]  ⚙️/◀/▶ semantics { contentDescription }
// ── 7차 변경 ──────────────────────────────────────────────
// [변경] 탭 2개: 일별(날짜 선택 + 요약 + 정규화 꺾은선) / 월별(년·월 선택 + 일평균 + 누적 막대)
//        기존 tabConfigs(3탭), RunningLineChart/ChartTab 사용 제거

private const val SWIPE_THRESHOLD = 80f

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    isListening: Boolean,
    isRunning: Boolean,
    isModelLoading: Boolean,
    strings: AppStrings,
    onNavigateToSettings: () -> Unit,
    onNavigateToRunning: () -> Unit
) {
    val dateMinutes  by viewModel.currentDateMinutes.collectAsStateWithLifecycle()
    val currentDate  by viewModel.currentDate.collectAsStateWithLifecycle()
    val canGoPrev    by viewModel.canGoPrevState.collectAsStateWithLifecycle()
    val canGoNext    by viewModel.canGoNextState.collectAsStateWithLifecycle()
    val dayRecord    by viewModel.currentDayRecord.collectAsStateWithLifecycle()
    val allDates     by viewModel.allDates.collectAsStateWithLifecycle()
    val yearMonth    by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val monthRecords by viewModel.monthRecords.collectAsStateWithLifecycle()

    var selectedTab    by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }

    val tabLabels = remember(strings) { listOf(strings.tabDaily, strings.tabMonthly) }
    val availableDates = remember(allDates) { allDates.toSet() }

    // 월별 탭 진입 시 최신 데이터로 갱신
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.reloadMonth()
    }

    val settingsDesc = strings.settings

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onNavigateToRunning,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("▶", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ── 배경 워터마크 (신규 추가) ──────────────
            Image(
                painter = painterResource(id = R.drawable.bg_watermark_zombie_run),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ── 헤더: 좌측 상단 ⚙️ ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { contentDescription = settingsDesc }
                    ) {
                        Text("⚙️", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        strings.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                MicIndicator(
                    isListening = isListening,
                    isRunning = isRunning,
                    isModelLoading = isModelLoading,
                    labelListening = strings.micListening,
                    labelWaitStop = strings.micWaitStop,
                    labelWaitStart = strings.micWaitStart,
                    labelLoading = strings.modelLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                )

                Spacer(Modifier.height(4.dp))

                // ── 탭 (일별 / 월별) ──────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    indicator = { tabPositions ->
                        val current = tabPositions.getOrNull(selectedTab)
                        if (current != null) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(current)
                                    .height(3.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                HorizontalDivider(
                                    thickness = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                ) {
                    tabLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label, fontSize = 14.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (selectedTab == 0) {
                    DailyTab(
                        modifier = Modifier.weight(1f),
                        strings = strings,
                        currentDate = currentDate,
                        dayRecord = dayRecord,
                        minutes = dateMinutes,
                        canGoPrev = canGoPrev,
                        canGoNext = canGoNext,
                        onPrevDate = { viewModel.goPrevDate() },
                        onNextDate = { viewModel.goNextDate() },
                        onDateClick = { showDatePicker = true }
                    )
                } else {
                    MonthlyTab(
                        modifier = Modifier.weight(1f),
                        strings = strings,
                        allDates = allDates,
                        yearMonth = yearMonth,
                        records = monthRecords,
                        onYearMonthChange = { viewModel.selectYearMonth(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 배너 광고
                val bannerAdUnitId = if (BuildConfig.DEBUG)
                    "ca-app-pub-3940256099942544/6300978111"       // 디버그 빌드 → 항상 테스트 ID
                else
                    "ca-app-pub-2733222333675520/6655118199"        // 릴리스 빌드 → 실제 ID

                AdBanner(
    //                adUnitId = "ca-app-pub-2733222333675520/4072247539",
                    adUnitId = bannerAdUnitId,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        DashboardDatePicker(
            strings        = strings,
            currentDate    = currentDate,
            availableDates = availableDates,
            onConfirm      = { date ->
                viewModel.selectDate(date)
                showDatePicker = false
            },
            onDismiss      = { showDatePicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────
// 탭 1: 일별
// ─────────────────────────────────────────────────────────
@Composable
private fun DailyTab(
    modifier: Modifier = Modifier,
    strings: AppStrings,
    currentDate: String,
    dayRecord: WorkoutRecord?,
    minutes: List<WorkoutMinute>,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevDate: () -> Unit,
    onNextDate: () -> Unit,
    onDateClick: () -> Unit
) {
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var selectedMinute by remember(currentDate) { mutableStateOf<Int?>(null) }

    val prevAction by rememberUpdatedState(onPrevDate)
    val nextAction by rememberUpdatedState(onNextDate)

    val labels = remember(strings) {
        StatLabels(strings.steps, strings.distance, strings.calories, strings.avgSpeed)
    }
    val prevDateDesc = "previous date"
    val nextDateDesc = "next date"

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canGoPrev, canGoNext) {
                detectHorizontalDragGestures(
                    onDragStart      = { dragAccum = 0f },
                    onDragEnd        = {
                        when {
                            dragAccum >  SWIPE_THRESHOLD && canGoPrev -> prevAction()
                            dragAccum < -SWIPE_THRESHOLD && canGoNext -> nextAction()
                        }
                        dragAccum = 0f
                    },
                    onDragCancel     = { dragAccum = 0f },
                    onHorizontalDrag = { _, delta -> dragAccum += delta }
                )
            }
    ) {
        // ── 날짜 선택 (◀ 날짜(탭하면 달력) ▶) ───────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick  = { if (canGoPrev) onPrevDate() },
                enabled  = canGoPrev,
                modifier = Modifier.semantics { contentDescription = prevDateDesc }
            ) {
                Text(
                    "◀",
                    fontSize = 18.sp,
                    color    = if (canGoPrev) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                )
            }

            Text(
                text       = currentDate.ifEmpty { "-" },
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.primary,
                modifier   = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(enabled = currentDate.isNotEmpty(), onClick = onDateClick)
            )

            IconButton(
                onClick  = { if (canGoNext) onNextDate() },
                enabled  = canGoNext,
                modifier = Modifier.semantics { contentDescription = nextDateDesc }
            ) {
                Text(
                    "▶",
                    fontSize = 18.sp,
                    color    = if (canGoNext) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        if (minutes.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    strings.noRecordsMsg,
                    color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize  = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // ── 당일 요약: 걸음수 / 이동거리 / 칼로리 / 평균속도 ──
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatSummary(
                    label    = strings.steps,
                    value    = dayRecord?.let { "${it.steps} ${strings.unitSteps}" } ?: "-",
                    color    = ColorSteps,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.distance,
                    value    = dayRecord?.let { "%.2f %s".format(it.distanceKm, strings.unitKm) } ?: "-",
                    color    = ColorDistance,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.calories,
                    value    = dayRecord?.let { "${it.calories} ${strings.unitKcal}" } ?: "-",
                    color    = ColorCalories,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.avgSpeed,
                    value    = dayRecord?.let { "%.1f %s".format(avgSpeedKmH(it), strings.unitKmh) } ?: "-",
                    color    = ColorSpeed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            NormalizedLineChart(
                minutes          = minutes,
                labels           = labels,
                textColor        = MaterialTheme.colorScheme.onBackground,
                onMinuteSelected = { selectedMinute = it },
                modifier         = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // ── 선택 지점의 실제 값 (없으면 안내 문구) ─────
            val selectedPoint = remember(minutes, selectedMinute) {
                minutes.lastOrNull { it.minuteIndex == selectedMinute }
            }
            Text(
                text = selectedPoint?.let {
                    val speed = if (it.minuteIndex > 0) it.distanceKm / (it.minuteIndex / 60.0) else 0.0
                    "%d:%02d · %d%s · %.2f%s · %d%s · %.1f%s".format(
                        it.minuteIndex / 60, it.minuteIndex % 60,
                        it.steps, strings.unitSteps,
                        it.distanceKm, strings.unitKm,
                        it.calories, strings.unitKcal,
                        speed, strings.unitKmh
                    )
                } ?: strings.normalizedNote,
                fontSize  = 12.sp,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier  = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 탭 2: 월별
// ─────────────────────────────────────────────────────────
@Composable
private fun MonthlyTab(
    modifier: Modifier = Modifier,
    strings: AppStrings,
    allDates: List<String>,
    yearMonth: YearMonth,
    records: List<WorkoutRecord>,
    onYearMonthChange: (YearMonth) -> Unit
) {
    var selectedDay by remember(yearMonth) { mutableStateOf<Int?>(null) }

    val labels = remember(strings) {
        StatLabels(strings.steps, strings.distance, strings.calories, strings.avgSpeed)
    }
    val yearOptions = remember(allDates, yearMonth) {
        val years = allDates.mapNotNull { it.take(4).toIntOrNull() }.toMutableSet()
        years.add(YearMonth.now().year)
        years.add(yearMonth.year)
        years.sortedDescending().map { it.toString() to it }
    }
    val monthOptions = remember { (1..12).map { it.toString() to it } }

    val dayStats = remember(records) {
        records.map {
            DayStat(
                day        = LocalDate.parse(it.date).dayOfMonth,
                steps      = it.steps,
                distanceKm = it.distanceKm,
                calories   = it.calories,
                speedKmH   = avgSpeedKmH(it)
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── 년 / 월 선택 ─────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SimpleDropdown(
                label    = "${strings.yearLabel}: ${yearMonth.year}",
                options  = yearOptions,
                onSelect = { onYearMonthChange(YearMonth.of(it, yearMonth.monthValue)) },
                modifier = Modifier.weight(1f)
            )
            SimpleDropdown(
                label    = "${strings.monthLabel}: ${yearMonth.monthValue}",
                options  = monthOptions,
                onSelect = { onYearMonthChange(YearMonth.of(yearMonth.year, it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (records.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    strings.noMonthRecordsMsg,
                    color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize  = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // ── 월 일평균 요약 ────────────────────────────
            val days     = records.size
            val avgSteps = records.sumOf { it.steps } / days
            val avgDist  = records.sumOf { it.distanceKm } / days
            val avgCal   = records.sumOf { it.calories } / days
            val speeds   = dayStats.map { it.speedKmH }.filter { it > 0.0 }
            val avgSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0

            Text(
                text       = strings.dailyAvgTitle,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatSummary(
                    label    = strings.steps,
                    value    = "$avgSteps ${strings.unitSteps}",
                    color    = ColorSteps,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.distance,
                    value    = "%.2f %s".format(avgDist, strings.unitKm),
                    color    = ColorDistance,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.calories,
                    value    = "$avgCal ${strings.unitKcal}",
                    color    = ColorCalories,
                    modifier = Modifier.weight(1f)
                )
                StatSummary(
                    label    = strings.avgSpeed,
                    value    = "%.1f %s".format(avgSpeed, strings.unitKmh),
                    color    = ColorSpeed,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            MonthlyStackedBarChart(
                days          = dayStats,
                daysInMonth   = yearMonth.lengthOfMonth(),
                labels        = labels,
                textColor     = MaterialTheme.colorScheme.onBackground,
                onDaySelected = { selectedDay = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            val selectedStat = remember(dayStats, selectedDay) {
                dayStats.firstOrNull { it.day == selectedDay }
            }
            Text(
                text = selectedStat?.let {
                    "%s-%02d · %d%s · %.2f%s · %d%s · %.1f%s".format(
                        yearMonth.toString(), it.day,
                        it.steps, strings.unitSteps,
                        it.distanceKm, strings.unitKm,
                        it.calories, strings.unitKcal,
                        it.speedKmH, strings.unitKmh
                    )
                } ?: strings.normalizedNote,
                fontSize  = 12.sp,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier  = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// 공통 컴포넌트
// ─────────────────────────────────────────────────────────

/** 하루 평균속도(km/h) = 거리 ÷ 운동시간(h). 시간이 0이면 0 */
private fun avgSpeedKmH(record: WorkoutRecord): Double =
    if (record.durationMin > 0) record.distanceKm / (record.durationMin / 60.0) else 0.0

@Composable
private fun StatSummary(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            color      = color,
            textAlign  = TextAlign.Center
        )
        Text(
            text      = label,
            fontSize  = 11.sp,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun <T> SimpleDropdown(
    label: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick  = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text("▼", fontSize = 12.sp)
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text    = { Text(text, fontSize = 14.sp) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDatePicker(
    strings: AppStrings,
    currentDate: String,
    availableDates: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectableDates = remember(availableDates) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return availableDates.contains(date.toString())
            }
        }
    }
    val initialMillis = remember(currentDate) {
        runCatching {
            LocalDate.parse(currentDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates           = selectableDates
    )

    DatePickerDialog(
        modifier         = modifier,
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        )
                    } ?: onDismiss()
                }
            ) { Text(strings.confirm) }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

// ── TabRow indicator offset 확장 (기존 유지) ──────────────
private fun Modifier.tabIndicatorOffset(tabPosition: TabPosition): Modifier =
    this
        .fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = tabPosition.left)
        .width(tabPosition.width)