package com.frankensosege.tmtforrunner.viewmodel

import android.app.Application
import android.content.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frankensosege.tmtforrunner.data.AppDatabase
import com.frankensosege.tmtforrunner.data.WorkoutMinute
import com.frankensosege.tmtforrunner.data.i18n.AppStrings
import com.frankensosege.tmtforrunner.data.i18n.StringsRepository
import com.frankensosege.tmtforrunner.data.i18n.SupportedLanguage
import com.frankensosege.tmtforrunner.service.RunningForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.frankensosege.tmtforrunner.data.WorkoutRecord
import java.time.YearMonth

data class RunningState(
    val steps: Int              = 0,
    val distanceKm: Double      = 0.0,
    val calories: Int           = 0,
    val elapsedSec: Int         = 0,
    val isRunning: Boolean      = false,
    val isListening: Boolean    = false,
    val isModelLoading: Boolean = false,
    val downloadProgress: Int   = -1
) {
    val timerText get() = "%02d:%02d".format(elapsedSec / 3600, (elapsedSec % 3600) / 60)
}

sealed class RecordsState {
    object Loading : RecordsState()
    object Empty   : RecordsState()
    object HasData : RecordsState()
}

private val defaultStrings = AppStrings(
    appName                = "만보이야기",
    save                   = "Save",
    loading                = "Loading...",
    quitApp                = "Quit App",
    quitConfirmMsg         = "Quit the app?\n(Quitting the app ends your workout tracking.)",
    dashboardTitle         = "만보이야기",
    statsDashboard         = "Statistics",
    tabDaily               = "Daily",
    tabMonthly             = "Monthly",
    avgSpeed               = "Avg Speed",
    unitKmh                = "km/h",
    yearLabel              = "Year",
    monthLabel             = "Month",
    confirm                = "OK",
    cancel                 = "Cancel",
    noMonthRecordsMsg      = "No workout records this month.",
    normalizedNote         = "Each metric is normalized to its max (100%). Tap to see actual values.",
    dailyAvgTitle          = "Daily Average",
    settings               = "Settings",
    chartSteps             = "Steps",
    chartDistance          = "Distance (km)",
    chartCalories          = "Calories",
    noRecordsMsg           = "No workout records yet.\nSay 'start' or tap the button below! 🏃",
    running                = "만보이야기",
    steps                  = "Steps",
    distance               = "Distance",
    calories               = "Calories",
    backgroundNotice       = "Workout continues recording even when screen is off",
    stopWorkout            = "Stop",
    startWorkout           = "Start",
    workingOut             = "● Working Out",
    settingsTitle          = "만보이야기",
    settingsSubtitle       = "App Settings",
    voiceSelection         = "Voice Selection",
    voiceFemale            = "Female Voice",
    voiceMale              = "Male Voice",
    talkStyle              = "Conversation Style",
    talkQuiet              = "Quiet",
    talkChattily           = "Chatty",
    timeNotifyInterval     = "Time Announcement Interval",
    timeNotifySubtitle     = "Announces current time during workout",
    workoutNotifyInterval  = "Workout Stats Interval",
    workoutNotifySubtitle  = "Announces steps, distance, and calories",
    countrySelection       = "Language",
    saveAndStart           = "Save & Start",
    heightLabel            = "Height",
    weightLabel            = "Weight",
    intervalRandom         = "Random",
    interval5min           = "5 min",
    interval10min          = "10 min",
    interval15min          = "15 min",
    interval20min          = "20 min",
    infoVoiceStart         = "Say 'start' → Begin workout & voice features",
    infoVoiceStop          = "Say 'stop' → End workout & save record",
    infoTimeAnnounce       = "Announces current time at set intervals",
    infoWorkoutAnnounce    = "Announces workout stats at set intervals",
    infoBackground         = "Continues running in background when screen is off",
    micListening           = "Listening...",
    micWaitStop            = "Say 'stop'",
    micWaitStart           = "Say 'start'",
    modelDownloading       = "Downloading voice model...",
    modelLoading           = "Loading voice model...",
    modelReady             = "Say 'start' 🎤",
    ttsWorkoutStart        = "Starting workout. Let's go!",
    ttsWorkoutStop         = "Workout complete. Great job!",
    ttsAmPm                = "AM",
    ttsPmPm                = "PM",
    ttsTimeFormat          = "It is {ampm} {h}:{m}.",
    ttsWorkoutFormat       = "Minute {min}, {steps} steps, {dist} kilometers, {cal} calories burned.",
    ttsEncouragements      = listOf("You're doing great!", "Keep it up!", "Amazing pace!"),
    ttsStartGreetings      = listOf("Starting workout. Let's go!"),
    ttsStopGreetings       = listOf("Workout complete. Great job!"),
    ttsGreetingMorning     = listOf("Good morning!"),
    ttsGreetingAfternoon   = listOf("Good afternoon!"),
    ttsGreetingEvening     = listOf("Good evening!"),
    ttsGreetingNight       = listOf("It's getting late, don't overdo it!"),
    ttsVolumeLabel         = "Voice Volume",
    unitKm                 = "km",
    unitKcal               = "kcal",
    unitSteps              = "steps",
    unitMin                = "min",
    notifChannelName       = "TMT Running Service",
    notifWaitStart         = "Say 'start' 🎤",
    notifWorking           = "Working out 🏃",
    notifStopAction        = "Stop",
    notifModelLoading      = "Preparing voice model... ⏳",
    permRequiredTitle      = "Permission Required",
    permRequiredMsg        = "This app cannot count your steps without the Physical Activity permission. Please allow it to continue.",
    permSettingsMsg        = "The permission is blocked. Please allow \"Physical activity\" in the app settings.",
    permOpenSettings       = "Open Settings",
    updateReadyMsg         = "A new version has been downloaded. Restart now to apply the update?",
    updateRestart          = "Restart",
    updateLater            = "Later",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = AppDatabase.getInstance(application)
    private val stringsRepo = StringsRepository(application)

    private val settingsPrefs =
        application.getSharedPreferences("tmt_settings", Context.MODE_PRIVATE)

    private val _ttsVolume = MutableStateFlow(
        settingsPrefs.getFloat("tts_volume", 1.0f).coerceIn(0f, 1f)
    )
    val ttsVolume: StateFlow<Float> = _ttsVolume

    val state = MutableStateFlow(RunningState())

    private val _recordsState = MutableStateFlow<RecordsState>(RecordsState.Loading)
    val recordsState: StateFlow<RecordsState> = _recordsState

    private val _allDates             = MutableStateFlow<List<String>>(emptyList())

    val allDates: StateFlow<List<String>> = _allDates

    private val _currentDayRecord = MutableStateFlow<WorkoutRecord?>(null)
    val currentDayRecord: StateFlow<WorkoutRecord?> = _currentDayRecord

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth

    private val _monthRecords = MutableStateFlow<List<WorkoutRecord>>(emptyList())
    val monthRecords: StateFlow<List<WorkoutRecord>> = _monthRecords

    private val _dateIndex            = MutableStateFlow(0)
    val dateIndex: StateFlow<Int>     = _dateIndex

    private val _currentDateMinutes   = MutableStateFlow<List<WorkoutMinute>>(emptyList())
    val currentDateMinutes: StateFlow<List<WorkoutMinute>> = _currentDateMinutes

    private val _currentDate          = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate

    private val _canGoPrev            = MutableStateFlow(false)
    private val _canGoNext            = MutableStateFlow(false)
    val canGoPrevState: StateFlow<Boolean> = _canGoPrev
    val canGoNextState: StateFlow<Boolean> = _canGoNext

    private val _strings = MutableStateFlow(defaultStrings)
    val strings: StateFlow<AppStrings> = _strings

    private val _currentLanguage = MutableStateFlow(SupportedLanguage.ENGLISH)
    val currentLanguage: StateFlow<SupportedLanguage> = _currentLanguage

    private val _setupDone = MutableStateFlow(false)
    val setupDone: StateFlow<Boolean> = _setupDone

    init {
        viewModelScope.launch {
            val count = db.workoutMinuteDao().totalCount()
            _recordsState.value = if (count > 0) RecordsState.HasData else RecordsState.Empty
            loadDates()
            stringsRepo.seedAll()
            reloadMonth()
        }
    }

    private suspend fun loadDates() {
        val dates = db.workoutMinuteDao().getAllDates()
        _allDates.value = dates
        if (dates.isNotEmpty()) loadDateData(0)
    }

    private suspend fun loadDateData(index: Int) {
        val dates = _allDates.value
        if (dates.isEmpty()) return
        val date    = dates[index]
        val minutes = db.workoutMinuteDao().getMinutesForDateOnce(date)
        _currentDayRecord.value   = db.workoutDao().getByDate(date)
        _dateIndex.value          = index
        _currentDate.value        = date
        _currentDateMinutes.value = minutes
        _canGoPrev.value          = index < dates.size - 1
        _canGoNext.value          = index > 0
    }

    fun markSetupDone() {
        _setupDone.value = true
    }

    fun setInitialSetupDone(done: Boolean) {
        _setupDone.value = done
    }

    fun goPrevDate() {
        val next = _dateIndex.value + 1
        if (next >= _allDates.value.size) return
        viewModelScope.launch { loadDateData(next) }
    }

    fun goNextDate() {
        val prev = _dateIndex.value - 1
        if (prev < 0) return
        viewModelScope.launch { loadDateData(prev) }
    }

    fun refreshDates() {
        viewModelScope.launch {
            val count = db.workoutMinuteDao().totalCount()
            _recordsState.value = if (count > 0) RecordsState.HasData else RecordsState.Empty
            loadDates()
        }
    }

    /** DatePicker 등에서 특정 날짜 선택 */
    fun selectDate(date: String) {
            val index = _allDates.value.indexOf(date)
            if (index < 0) return
            viewModelScope.launch { loadDateData(index) }
        }

    /** 월별 탭: 년/월 선택 */
    fun selectYearMonth(yearMonth: YearMonth) {
            _selectedYearMonth.value = yearMonth
            viewModelScope.launch {
                _monthRecords.value = db.workoutDao().getByMonth(yearMonth.toString())
            }
        }

    fun reloadMonth() = selectYearMonth(_selectedYearMonth.value)

    // ✅ [추가] 분 단위 포인트를 StateFlow에 직접 append
    // Service에서 BROADCAST_MINUTE_POINT 수신 시 호출
    // DB 재조회 없이 currentDateMinutes를 즉시 갱신
    private fun appendMinutePoint(minute: WorkoutMinute) {
        val today = java.time.LocalDate.now().toString()

        // ✅ [버그 수정] 기존: `_currentDate.value != today` 조건으로 판단
        //   → _currentDate.value 초기값이 ""이므로 첫 실행/새 날짜 시 항상 return됐음
        //   → 수정: 날짜 네비게이터로 과거를 탐색 중일 때(_dateIndex > 0)만 스킵
        //     • _dateIndex == 0 : 가장 최신(오늘) 표시 중 → 항상 처리
        //     • _dateIndex  > 0 : 과거 날짜 조회 중      → 스킵 (실시간 업데이트 불필요)
        if (_dateIndex.value > 0) return

        val current = _currentDateMinutes.value

        // 동일 minuteIndex가 이미 있으면 교체, 없으면 추가
        val updated = if (current.any { it.minuteIndex == minute.minuteIndex }) {
            current.map { if (it.minuteIndex == minute.minuteIndex) minute else it }
        } else {
            current + minute
        }
        _currentDateMinutes.value = updated

        // _currentDate가 오늘이 아닌 경우 초기화
        // (빈 문자열 = 첫 실행, 어제 날짜 = 새 날짜 첫 운동)
        if (_currentDate.value != today) {
            _currentDate.value = today
        }

        // 날짜 목록에 오늘이 없으면 맨 앞에 추가
        if (!_allDates.value.contains(today)) {
            _allDates.value  = listOf(today) + _allDates.value
            _canGoPrev.value = _allDates.value.size > 1
            _canGoNext.value = false
        }

        // recordsState Empty → HasData 전환
        if (_recordsState.value is RecordsState.Empty) {
            _recordsState.value = RecordsState.HasData
        }
    }

    fun loadLanguage(langCode: String) {
        val lang = SupportedLanguage.fromCode(langCode)
        _currentLanguage.value = lang
        viewModelScope.launch {
            _strings.value = stringsRepo.getStrings(lang)
        }
    }

    fun changeLanguage(lang: SupportedLanguage) {
        _currentLanguage.value = lang
        viewModelScope.launch {
            _strings.value = stringsRepo.getStrings(lang)
        }
    }

    /** 드래그 중: UI 값만 갱신 */
    fun setTtsVolume(volume: Float) {
        _ttsVolume.value = volume.coerceIn(0f, 1f)
    }

    /** 드래그 종료: 저장 (서비스가 다음 안내 음성부터 이 값을 사용) */
    fun saveTtsVolume() {
        settingsPrefs.edit().putFloat("tts_volume", _ttsVolume.value).apply()
    }

    // ─── 브로드캐스트 수신 ───────────────────────────────
    private val updateReceiver = object : BroadcastReceiver() {

        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                RunningForegroundService.BROADCAST_UPDATE -> {
                    val running = intent.getBooleanExtra(RunningForegroundService.EXTRA_RUNNING, false)
                    Log.d("MainViewModel", "BROADCAST_UPDATE 수신: isRunning=$running")   // ✅ [임시 진단용]
                    state.value = state.value.copy(
                        steps      = intent.getIntExtra(RunningForegroundService.EXTRA_STEPS, 0),
                        distanceKm = intent.getDoubleExtra(RunningForegroundService.EXTRA_DISTANCE, 0.0),
                        calories   = intent.getIntExtra(RunningForegroundService.EXTRA_CALORIES, 0),
                        elapsedSec = intent.getIntExtra(RunningForegroundService.EXTRA_ELAPSED_SEC, 0),
                        isRunning  = intent.getBooleanExtra(RunningForegroundService.EXTRA_RUNNING, false)
                    )
                    // ✅ [제거] 60초마다 refreshDates() 호출 제거
                    //          → BROADCAST_MINUTE_POINT 수신으로 대체
                }
                RunningForegroundService.BROADCAST_VOICE_STATE -> {
                    val loading  = intent.getBooleanExtra(RunningForegroundService.EXTRA_MODEL_LOADING, false)
                    val progress = intent.getIntExtra(RunningForegroundService.EXTRA_DOWNLOAD_PROGRESS, -1)
                    Log.d("MainViewModel", "BROADCAST_VOICE_STATE 수신: modelLoading=$loading, progress=$progress")   // ✅ [임시 진단용]
                    state.value = state.value.copy(
                        isListening      = intent.getBooleanExtra(RunningForegroundService.EXTRA_LISTENING, false),
                        isModelLoading   = intent.getBooleanExtra(RunningForegroundService.EXTRA_MODEL_LOADING, false),
                        downloadProgress = intent.getIntExtra(RunningForegroundService.EXTRA_DOWNLOAD_PROGRESS, -1)
                    )
                }
                // ✅ [추가] 분 단위 포인트 Broadcast 수신
                RunningForegroundService.BROADCAST_MINUTE_POINT -> {
                    val date        = intent.getStringExtra(RunningForegroundService.EXTRA_MINUTE_DATE)        ?: return
                    val minuteIndex = intent.getIntExtra(RunningForegroundService.EXTRA_MINUTE_INDEX, -1)
                    val steps       = intent.getIntExtra(RunningForegroundService.EXTRA_MINUTE_STEPS, 0)
                    val distanceKm  = intent.getDoubleExtra(RunningForegroundService.EXTRA_MINUTE_DISTANCE, 0.0)
                    val calories    = intent.getIntExtra(RunningForegroundService.EXTRA_MINUTE_CALORIES, 0)
                    if (minuteIndex < 0) return
                    appendMinutePoint(
                        WorkoutMinute(
                            date        = date,
                            minuteIndex = minuteIndex,
                            steps       = steps,
                            distanceKm  = distanceKm,
                            calories    = calories
                        )
                    )
                }
            }
        }
    }

    fun registerReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(RunningForegroundService.BROADCAST_UPDATE)
            addAction(RunningForegroundService.BROADCAST_VOICE_STATE)
            addAction(RunningForegroundService.BROADCAST_MINUTE_POINT) // ✅ [추가]
        }
        Log.d("MainViewModel", "registerReceiver 호출됨")   // ✅ [임시 진단용]
        try {
            context.registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d("MainViewModel", "registerReceiver 성공")   // ✅ [임시 진단용]
        } catch (e: Exception) {
            Log.e("MainViewModel", "registerReceiver 실패: ${e.message}")   // ✅ [임시 진단용]
        }
//        context.registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun unregisterReceiver(context: Context) {
        try { context.unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }
}