package com.frankensosege.tmtforrunner.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frankensosege.tmtforrunner.MainActivity
import com.frankensosege.tmtforrunner.data.AppDatabase
import com.frankensosege.tmtforrunner.data.WorkoutMinute
import com.frankensosege.tmtforrunner.data.WorkoutRecord
import com.frankensosege.tmtforrunner.data.i18n.AppStrings
import com.frankensosege.tmtforrunner.data.i18n.StringsRepository
import com.frankensosege.tmtforrunner.data.i18n.SupportedLanguage
import com.frankensosege.tmtforrunner.manager.VoiceCommandManager
import com.frankensosege.tmtforrunner.model.NotifyInterval
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 러닝 포그라운드 서비스
 *
 * ── 변경 사항 (4차 세션) ────────────────────────────────
 * [변경 1] RunningScreen 시간 표시 = 경과시간 시:분(HH:MM)
 * [변경 2] 자정(일자 변경) 감지 시 걸음·경과시간·분 타이머 reset
 * [변경 3] 신체기록은 앱 시작~종료 내내 항상 저장
 * [변경 4] Start/Stop 버튼 = 음성 기능(TTS 알람, 대화)만 on/off
 * [변경 5] 앱 재실행 시 오늘 DB 데이터 확인 → 없으면 reset
 *
 * ── 변경 사항 (5차 세션) ────────────────────────────────
 * [변경 6] SharedPreferences에서 키(height_cm), 몸무게(weight_kg) 로드
 * [변경 7] 거리 계산: 시간당 속도 기반 동적 보폭 × 걸음 수 × 키
 * [변경 8] 칼로리 계산: MET × 몸무게(kg) × 시간(분) 공식 적용
 */
class RunningForegroundService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "TMTService"

        // strings 로드 전 첫 알림에만 쓰는 fallback (이후엔 strings.appName 사용)
        private const val DEFAULT_APP_NAME = "TMT for Runner"

        // ── 알림 업데이트 쓰로틀링용 변수 추가 (기존 변수 선언부에 추가) ──
        private var lastNotifUpdateSec = -1

        const val CHANNEL_ID              = "tmt_running_channel"
        const val NOTIF_ID                = 1001
        const val ACTION_START            = "ACTION_START"
        const val ACTION_STOP             = "ACTION_STOP"
        const val ACTION_QUIT             = "ACTION_QUIT"
        const val EXTRA_IS_MALE           = "extra_is_male"
        const val EXTRA_TALKATIVE         = "extra_talkative"

        const val BROADCAST_UPDATE        = "com.frankensosege.tmtforrunner.UPDATE"
        const val BROADCAST_VOICE_STATE   = "com.frankensosege.tmtforrunner.VOICE_STATE"
        const val BROADCAST_VOICE_START   = "com.frankensosege.tmtforrunner.VOICE_START"
        const val EXTRA_STEPS             = "steps"
        const val EXTRA_DISTANCE          = "distance"
        const val EXTRA_CALORIES          = "calories"
        const val EXTRA_ELAPSED_SEC       = "elapsed_sec"
        const val EXTRA_SESSION_ID        = "session_id"
        const val EXTRA_RUNNING           = "is_running"
        const val EXTRA_LISTENING         = "is_listening"
        const val EXTRA_MODEL_LOADING     = "model_loading"
        const val EXTRA_DOWNLOAD_PROGRESS = "download_progress"

        const val PREF_TIME_INTERVAL      = "time_notify_interval"
        const val PREF_WORKOUT_INTERVAL   = "workout_notify_interval"

        const val PREF_TALK_ENABLED       = "talk_enabled"

        // 응원 멘트 간격(초): 조용하게 10분에 1~2회, 수다스럽게 10분에 3~4회
        private const val ENCOURAGE_QUIET_MIN_SEC  = 300
        private const val ENCOURAGE_QUIET_MAX_SEC  = 600
//        private const val ENCOURAGE_CHATTY_MIN_SEC = 150
//        private const val ENCOURAGE_CHATTY_MAX_SEC = 200
        private const val ENCOURAGE_CHATTY_MIN_SEC = 100
        private const val ENCOURAGE_CHATTY_MAX_SEC = 200

        private const val ENCOURAGE_RETRY_SEC      = 10

        // ── [변경 6] SharedPreferences 키 ──────────────
        const val PREF_HEIGHT_CM          = "height_cm"
        const val PREF_WEIGHT_KG          = "weight_kg"

        // 기본값
        const val DEFAULT_HEIGHT_CM       = 170
        const val DEFAULT_WEIGHT_KG       = 65

        // 분 단위 포인트 Broadcast 상수
        const val BROADCAST_MINUTE_POINT    = "com.frankensosege.tmtforrunner.MINUTE_POINT"
        const val EXTRA_MINUTE_DATE         = "minute_date"
        const val EXTRA_MINUTE_INDEX        = "minute_index"
        const val EXTRA_MINUTE_STEPS        = "minute_steps"
        const val EXTRA_MINUTE_DISTANCE     = "minute_distance"
        const val EXTRA_MINUTE_CALORIES     = "minute_calories"
        private var lastEncourageText: String? = null
        private var lastStartGreeting: String? = null
        private var lastStopGreeting: String? = null

        // ── [변경 7] 거리 계산: 동적 보폭 (가이드라인 공식) ─
        /**
         * 시간 간격(초)에 따른 동적 보폭 계산
         * @param heightCm 키 (cm)
         * @param timeIntervalSeconds 이전 걸음 이후 경과 초
         * @return 보폭 (m)
         */
        fun getDynamicStride(heightCm: Double, timeIntervalSeconds: Double): Double {
            val stepsPerSecond = if (timeIntervalSeconds > 0) 1.0 / timeIntervalSeconds else 1.5
            return when {
                stepsPerSecond >= 2.5 -> (heightCm * 0.65) / 100.0   // 뛸 때
                stepsPerSecond >= 1.8 -> (heightCm * 0.50) / 100.0   // 빠르게 걷기
                else                  -> (heightCm * 0.415) / 100.0  // 보통 걷기
            }
        }

        // ── [변경 8] 칼로리 계산: MET 기반 ───────────────
        /**
         * @param weightKg   몸무게 (kg)
         * @param durationSeconds 경과 초
         * @param speedKmH   평균 속도 (km/h)
         * @return 소모 칼로리 (kcal)
         */
        fun calculateCalories(weightKg: Double, durationSeconds: Double, speedKmH: Double): Double {
            val met = when {
                speedKmH < 3.0              -> 2.0
                speedKmH < 4.5              -> 3.0   // 보통 걷기
                speedKmH < 6.0              -> 3.8   // 빠르게 걷기
                else                        -> 5.0   // 가벼운 러닝
            }
            val durationMinutes = durationSeconds / 60.0
            return 0.0175 * met * weightKg * durationMinutes
        }
    }

    // ── [변경 4] isVoiceActive ────────────────────────────
    private var isVoiceActive    = false

    // ── 신체 데이터 ───────────────────────────────────────
    private var startSteps       = -1
    private var currentSteps     = 0   // 이번 세션 센서 기준 걸음 수 (startSteps 차분)
    private var elapsedSeconds   = 0
    private var sessionId        = System.currentTimeMillis()

    // ── [6차 추가] 재실행 시 오늘 DB에서 복원하는 베이스값 ──
    // currentSteps/distanceKm/calories 는 이번 세션 증분만 담고,
    // 실제 UI/저장 시에는 todayBase* 를 더해 표시한다.
    // elapsedSeconds 는 가이드라인대로 이번 실행 기준 0부터 재시작.
    private var todayBaseSteps      = 0
    private var todayBaseDistanceKm = 0.0
    private var todayBaseCalories   = 0

    // ── 저장 중복 방지: 분 단위 / 일 단위 각각 독립 관리 ──
    // saveMinutePoint 와 saveCurrentDataToDB 가 같은 변수를 공유하면
    // 한쪽이 저장된 직후 다른 쪽이 "변경 없음"으로 오판하여 저장이 누락됨.
    private var lastMinSavedSteps      = -1
    private var lastMinSavedDistanceKm = -1.0
    private var lastMinSavedCalories   = -1

    private var lastDaySavedSteps      = -1
    private var lastDaySavedDistanceKm = -1.0
    private var lastDaySavedCalories   = -1

    // ── [변경 6] 키/몸무게 (SharedPreferences에서 로드) ──
    private var heightCm         = DEFAULT_HEIGHT_CM
    private var weightKg         = DEFAULT_WEIGHT_KG

    // 거리 누적 계산용 (동적 보폭 적용, 증분 누적 방식 — 단조증가 보장)
    private var distanceKm       = 0.0
    private var caloriesAccum    = 0.0    // 칼로리도 증분 누적 (Double, 정밀도 보존)
    private var lastStepCount    = 0      // 이전 틱의 걸음 수
    private var lastStepTime     = 0L    // 이전 걸음 감지 시각 (ms)

    // [변경 2] 날짜 변경 감지용
    private var currentDate      = LocalDate.now()

    private var isMale           = false
    private var isTalkative      = false

    // 응원 멘트 다음 발화 시각 (SystemClock.elapsedRealtime 기준, 0 = 미예약)
    private var nextEncourageAtMs = 0L
    private var isModelLoading   = false
    private var downloadProgress = -1

    private var timeInterval    = NotifyInterval.MIN_10
    private var workoutInterval = NotifyInterval.MIN_5
    private var lastTimeMin     = -1
    private var lastWorkoutMin  = -1
    private var nextTimeMin     = -1
    private var nextWorkoutMin  = -1
    private var lastMinuteSaved = -1

    private var strings: AppStrings? = null
    private var currentLang = SupportedLanguage.ENGLISH

    private var tts: TextToSpeech?             = null
    private var sensorManager: SensorManager?  = null
    private var voiceCommandManager: VoiceCommandManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val handler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            elapsedSeconds++
            onTimerTick()
            handler.postDelayed(this, 1000)
        }
    }

    /** 직전과 다른 문구를 랜덤 선택 (문구가 1개뿐이면 그대로 사용) */
    private fun pickDifferent(pool: List<String>, last: String?): String =
        pool.filter { it != last }.ifEmpty { pool }.random()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        loadLanguageStrings()
        loadIntervalSettings()
        loadBodySettings()                          // ✅ [변경 6] 키/몸무게 로드
        createNotificationChannel()

        val loadingMsg = strings?.notifModelLoading ?: "Preparing voice model... ⏳"
        if (!startForegroundSafely(buildNotification(loadingMsg, false))) {
            Log.e(TAG, "필수 권한 없음 또는 포그라운드 시작 불가 — 서비스 종료")
            stopSelf()
            return
        }

        try { setupTts() }    catch (e: Exception) { Log.e(TAG, "TTS 실패: ${e.message}") }
        try { setupSensor() } catch (e: Exception) { Log.e(TAG, "센서 실패: ${e.message}") }

        nextTimeMin    = NotifyInterval.nextRandomMin(0)
        nextWorkoutMin = NotifyInterval.nextRandomMin(0)

        // ✅ 수정 — timerTick과 setupVoiceCommands는 checkTodayDataAndReset()과
        //           독립적으로 반드시 실행되도록 분리
        handler.post(timerTick)
        handler.postDelayed({
            try { setupVoiceCommands() }
            catch (e: Exception) { Log.e(TAG, "음성인식 실패: ${e.message}") }
        }, 500)

        serviceScope.launch {
            try {
                checkTodayDataAndReset()
            } catch (e: Exception) {
                Log.e(TAG, "checkTodayDataAndReset 실패: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadLanguageStrings()
        loadBodySettings()                          // ✅ [변경 6] 설정 변경 반영
        when (intent?.action) {
            ACTION_START -> {
                val prefs = getSharedPreferences("tmt_settings", MODE_PRIVATE)
                isMale      = intent.getBooleanExtra(EXTRA_IS_MALE,   prefs.getBoolean("voice_male", false))
                isTalkative = intent.getBooleanExtra(EXTRA_TALKATIVE, prefs.getBoolean("talkative", false))
                activateVoice()
            }
            ACTION_STOP  -> deactivateVoice()
            ACTION_QUIT  -> quitApp()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 허용된 권한에 해당하는 타입만 지정해 startForeground 호출.
     * - 신체활동(ACTIVITY_RECOGNITION) 없음 → 앱 의미 없음 → false
     * - 마이크(RECORD_AUDIO)는 선택 → 없으면 health 타입만 사용
     * - 백그라운드 재시작 등으로 시스템이 거부하면 false (크래시 방지)
     */
    private fun startForegroundSafely(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!isGranted(Manifest.permission.ACTIVITY_RECOGNITION)) return false
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                if (isGranted(Manifest.permission.RECORD_AUDIO)) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패: ${e.javaClass.simpleName} / ${e.message}")
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        try { voiceCommandManager?.stop() }              catch (_: Exception) {}
        try { tts?.shutdown() }                          catch (_: Exception) {}
    }

    // ── [변경 6] 키/몸무게 SharedPreferences 로드 ────────
    private fun loadBodySettings() {
        val prefs = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        heightCm  = prefs.getInt(PREF_HEIGHT_CM, DEFAULT_HEIGHT_CM)
        weightKg  = prefs.getInt(PREF_WEIGHT_KG, DEFAULT_WEIGHT_KG)
        Log.d(TAG, "신체 정보 로드: height=${heightCm}cm, weight=${weightKg}kg")
    }

    // ── [변경 7/8] 거리·칼로리 계산 헬퍼 ────────────────
    /**
     * 현재까지의 총 이동거리(km)를 반환한다.
     * ※ 거리는 onSensorChanged()에서 걸음이 증가할 때마다
     *    "그 순간의 순시 페이스" 기준 보폭으로 증분 계산되어 distanceKm에 누적된다.
     *    (전체 평균 페이스로 매번 재계산하면 페이스가 느려질 때 과거 거리까지
     *     소급 축소되어 그래프가 감소하는 버그가 있었음 — 증분 누적 방식으로 수정)
     */
    private fun calcDistanceKm(): Double {
        // 오늘 누적 = 재실행 전 DB 베이스 + 이번 세션 누적 증분
        return todayBaseDistanceKm + distanceKm
    }

    /**
     * 현재까지의 소모 칼로리(kcal)를 반환한다.
     * ※ 칼로리도 거리와 동일하게 걸음 증분 구간마다의 순시 속도로 계산되어
     *   caloriesAccum에 누적된다 (단조증가 보장).
     */
    private fun calcCalories(): Int {
        // 오늘 누적 = 재실행 전 DB 베이스 + 이번 세션 누적 증분
        return todayBaseCalories + caloriesAccum.toInt()
    }

    // ── [변경 4] 음성 기능만 ON ──────────────────────────
    private fun activateVoice() {
        if (isVoiceActive) return
        isVoiceActive = true
        nextEncourageAtMs = 0L
        loadIntervalSettings()

        val startPool = strings?.ttsStartGreetings?.takeIf { it.isNotEmpty() }
        val startMsg  = startPool?.let { pickDifferent(it, lastStartGreeting) }
            ?: (strings?.ttsWorkoutStart ?: "Voice features started.")
        lastStartGreeting = startMsg
//        tts?.speak(startMsg, TextToSpeech.QUEUE_FLUSH, null, "start")
        val fullStartMsg = listOfNotNull(timeGreeting(), startMsg).joinToString(" ")
        speak(fullStartMsg, TextToSpeech.QUEUE_FLUSH, "start")
        val workingMsg = strings?.notifWorking ?: "Working out 🏃"
        updateNotification(workingMsg, true)
        broadcastUpdate()
    }

    // ── [변경 4] 음성 기능만 OFF ─────────────────────────
    private fun deactivateVoice() {
        if (!isVoiceActive) return
        isVoiceActive = false
        nextEncourageAtMs = 0L
        saveCurrentDataToDB()

        val stopPool = strings?.ttsStopGreetings?.takeIf { it.isNotEmpty() }
        val stopMsg  = stopPool?.let { pickDifferent(it, lastStopGreeting) }
            ?: (strings?.ttsWorkoutStop ?: "Voice features stopped.")
        lastStopGreeting = stopMsg
//        tts?.speak(stopMsg, TextToSpeech.QUEUE_FLUSH, null, "stop")
        speak(stopMsg, TextToSpeech.QUEUE_FLUSH, "stop")

        val waitMsg = strings?.notifWaitStart ?: "Say 'start' 🎤"
        updateNotification(waitMsg, false)
        broadcastUpdate()
    }

    // ── [변경 5] 앱 재실행 시 오늘 DB 확인 → 없으면 reset, 있으면 base값 복원
    private suspend fun checkTodayDataAndReset() {
        try {
            val today  = LocalDate.now().toString()
            val db     = AppDatabase.getInstance(this)
            val record = db.workoutDao().getByDate(today)
            if (record == null) {
                Log.d(TAG, "재실행 reset: 오늘($today) DB 데이터 없음")
                currentDate          = LocalDate.now()
                startSteps           = -1
                currentSteps         = 0
                distanceKm           = 0.0
                caloriesAccum        = 0.0
                elapsedSeconds       = 0
                lastMinuteSaved      = -1
                lastTimeMin          = -1
                lastWorkoutMin       = -1
                sessionId            = System.currentTimeMillis()
                todayBaseSteps       = 0
                todayBaseDistanceKm  = 0.0
                todayBaseCalories    = 0
                lastMinSavedSteps      = -1
                lastMinSavedDistanceKm = -1.0
                lastMinSavedCalories   = -1
                lastDaySavedSteps      = -1
                lastDaySavedDistanceKm = -1.0
                lastDaySavedCalories   = -1
            } else {
                // ── [6차 추가] 오늘 누적 데이터를 베이스로 복원 ──
                // elapsedSeconds 는 가이드라인에 따라 이번 실행 기준 0부터 재시작
                Log.d(TAG, "재실행 이어서: 오늘($today) steps=${record.steps}, dist=${record.distanceKm}, cal=${record.calories}")
                currentDate         = LocalDate.now()
                todayBaseSteps      = record.steps
                todayBaseDistanceKm = record.distanceKm
                todayBaseCalories   = record.calories
                // 이번 세션 센서 증분은 0부터 다시 시작 (startSteps 재초기화)
                startSteps          = -1
                currentSteps        = 0
                distanceKm          = 0.0
                caloriesAccum       = 0.0
                elapsedSeconds      = 0
                // lastMinuteSaved = DB의 마지막 분 인덱스로 설정해 중복 저장 방지
                val lastMin = db.workoutMinuteDao().getLastMinuteIndex(today)
                lastMinuteSaved = if (lastMin >= 0) lastMin else -1
                // broadcastUpdate로 즉시 복원된 값 UI에 반영
                withContext(Dispatchers.Main) { broadcastUpdate() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "재실행 reset 확인 실패: ${e.message}")
        }
    }

    // ── [변경 2] 날짜 변경 감지 → 자정 reset ────────────
    private fun checkAndResetOnDateChange() {
        val today = LocalDate.now()
        if (today != currentDate) {
            Log.d(TAG, "날짜 변경 감지: $currentDate → $today")

            // 리셋 전, 변경 전 날짜("어제")의 미저장 데이터를 먼저 flush
            //          → 자정 직전 활동량 유실 방지. currentDate가 아직 "어제"인 시점의
            //            값을 명시적으로 넘겨, 비동기 저장 중 날짜가 바뀌어도 안전하게 처리
            val yesterday = currentDate.toString()
            if (elapsedSeconds > 0) {
                val lastMin = elapsedSeconds / 60
                if (lastMin > lastMinuteSaved && lastMin > 0) {
                    saveMinutePoint(lastMin, dateOverride = yesterday)
                }
                saveCurrentDataToDB(dateOverride = yesterday)
            }

            currentDate          = today
            startSteps           = -1
            currentSteps         = 0
            distanceKm           = 0.0
            caloriesAccum        = 0.0
            elapsedSeconds       = 0
            lastMinuteSaved      = -1
            lastTimeMin          = -1
            lastWorkoutMin       = -1
            nextTimeMin          = NotifyInterval.nextRandomMin(0)
            nextWorkoutMin       = NotifyInterval.nextRandomMin(0)
            sessionId            = System.currentTimeMillis()
            todayBaseSteps       = 0
            todayBaseDistanceKm  = 0.0
            todayBaseCalories    = 0
            lastMinSavedSteps      = -1
            lastMinSavedDistanceKm = -1.0
            lastMinSavedCalories   = -1
            lastDaySavedSteps      = -1
            lastDaySavedDistanceKm = -1.0
            lastDaySavedCalories   = -1
        }
    }

    // ── 매초 타이머 틱 ───────────────────────────────────
    private fun onTimerTick() {
        checkAndResetOnDateChange()

        val currentMin = elapsedSeconds / 60

        // 분 단위 포인트 저장 (항상)
        if (currentMin > lastMinuteSaved && currentMin > 0) {
            lastMinuteSaved = currentMin
            saveMinutePoint(currentMin)
        }

        // 5분마다 일 단위 합산 저장
        if (elapsedSeconds > 0 && elapsedSeconds % 300 == 0) {
            saveCurrentDataToDB()
        }

        // TTS 알람은 isVoiceActive일 때만
        if (isVoiceActive) {
            if (NotifyInterval.shouldAnnounce(timeInterval, currentMin, lastTimeMin, nextTimeMin)) {
                lastTimeMin = currentMin
                if (timeInterval == NotifyInterval.RANDOM)
                    nextTimeMin = NotifyInterval.nextRandomMin(currentMin)
                announceCurrentTime()
            }
            if (NotifyInterval.shouldAnnounce(workoutInterval, currentMin, lastWorkoutMin, nextWorkoutMin)) {
                lastWorkoutMin = currentMin
                if (workoutInterval == NotifyInterval.RANDOM)
                    nextWorkoutMin = NotifyInterval.nextRandomMin(currentMin)
                announceWorkout()
            }
            checkEncouragement()
        }

        broadcastUpdate()

        // 알림 업데이트 (5초마다)
        // ✅ 수정 — 30초마다로 변경 + 중복 호출 방지
        if (elapsedSeconds % 30 == 0 && elapsedSeconds != lastNotifUpdateSec) {
            lastNotifUpdateSec = elapsedSeconds
            val unitSteps = strings?.unitSteps ?: "steps"
            val distStr   = "%.2f".format(calcDistanceKm())
            val notifMsg  = "${todayBaseSteps + currentSteps}${unitSteps}  ${distStr}km  " +
                    "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
            updateNotification(notifMsg, isVoiceActive)
        }
    }

    // ── DB 저장 (키/몸무게 기반 계산값 사용) ─────────────
    private fun saveCurrentDataToDB(dateOverride: String? = null) {
        val totalSteps = todayBaseSteps + currentSteps
        if (totalSteps <= 0) return
        val distKm = calcDistanceKm()   // base 포함 오늘 누적
        val cal    = calcCalories()     // base 포함 오늘 누적
//        val today  = currentDate.toString()
        val today  = dateOverride ?: currentDate.toString()

        // 일 단위 전용 lastSaved 로 비교 — 분 단위 저장과 독립
        if (totalSteps == lastDaySavedSteps &&
            distKm     == lastDaySavedDistanceKm &&
            cal        == lastDaySavedCalories
        ) {
            Log.d(TAG, "일 단위 저장 생략: 변경 없음 (steps=$totalSteps)")
            return
        }

        lastDaySavedSteps      = totalSteps
        lastDaySavedDistanceKm = distKm
        lastDaySavedCalories   = cal

        serviceScope.launch {
            try {
                val db  = AppDatabase.getInstance(this@RunningForegroundService)
                val dao = db.workoutDao()
                val existing = dao.getByDate(today)
                if (existing != null) {
                    dao.addToDate(
                        date        = today,
                        steps       = totalSteps - existing.steps,
                        distanceKm  = distKm - existing.distanceKm,
                        durationMin = (elapsedSeconds / 60) - existing.durationMin,
                        calories    = cal - existing.calories
                    )
                } else {
                    dao.insert(WorkoutRecord(
                        date        = today,
                        steps       = totalSteps,
                        distanceKm  = distKm,
                        durationMin = elapsedSeconds / 60,
                        calories    = cal
                    ))
                }
                Log.d(TAG, "DB 저장: steps=$totalSteps, dist=${"%.3f".format(distKm)}, cal=$cal")
            } catch (e: Exception) {
                Log.e(TAG, "DB 저장 실패: ${e.message}")
            }
        }
    }

    // ── 앱 완전 종료 ──────────────────────────────────────
    private fun quitApp() {
        isVoiceActive = false
        handler.removeCallbacksAndMessages(null)
        saveCurrentDataToDB()

        try { voiceCommandManager?.stop() }             catch (_: Exception) {}
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        try { tts?.shutdown() }                         catch (_: Exception) {}
        try { serviceScope.cancel() }                   catch (_: Exception) {}

        val closeIntent = Intent(this, MainActivity::class.java).apply {
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_FINISH
        }
        try { startActivity(closeIntent) } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun loadIntervalSettings() {
        val prefs = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        timeInterval    = NotifyInterval.fromPrefKey(prefs.getString(PREF_TIME_INTERVAL, "10") ?: "10")
        workoutInterval = NotifyInterval.fromPrefKey(prefs.getString(PREF_WORKOUT_INTERVAL, "5") ?: "5")
    }

    private fun loadLanguageStrings() {
        val prefs    = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        val langCode = prefs.getString("language_code", "en") ?: "en"
        currentLang  = SupportedLanguage.fromCode(langCode)
        serviceScope.launch {
            try {
                val repo = StringsRepository(this@RunningForegroundService)
                strings = repo.getStrings(currentLang)
            } catch (e: Exception) {
                Log.e(TAG, "문자열 로드 실패: ${e.message}")
            }
        }
    }

    private fun setupVoiceCommands() {
        val prefs = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        voiceCommandManager = VoiceCommandManager(
            context  = this,
            onStart  = {
                handler.post {
                    isMale      = prefs.getBoolean("voice_male", false)
                    isTalkative = prefs.getBoolean("talkative", false)
                    activateVoice()

                    sendBroadcast(Intent(BROADCAST_VOICE_START).apply { setPackage(packageName) })
                }
            },
            onStop             = { handler.post { deactivateVoice() } },
            onListening        = { listening ->
                handler.post { broadcastVoiceState(listening, isModelLoading) }
            },
            onModelLoading     = { loading ->
                handler.post {
                    isModelLoading = loading
                    if (!loading) downloadProgress = -1
                    broadcastVoiceState(false, loading, downloadProgress)
                    val msg = if (loading)
                        strings?.notifModelLoading ?: "Preparing voice model... ⏳"
                    else
                        strings?.notifWaitStart    ?: "Say 'start' 🎤"
                    updateNotification(msg, false)
                }
            },
            onDownloadProgress = { progress ->
                handler.post {
                    if (progress != downloadProgress) {   // ✅ [추가] 값이 실제로 바뀔 때만 처리
                        downloadProgress = progress
                        val base = strings?.modelDownloading ?: "Downloading..."
                        updateNotification("$base $progress%", false)
                        broadcastVoiceState(false, true, progress)
                    }
                }
            }
        )
        voiceCommandManager?.start()
    }

    private fun speak(text: String, queueMode: Int, utteranceId: String) {
        val volume = getSharedPreferences("tmt_settings", MODE_PRIVATE)
            .getFloat("tts_volume", 1.0f)
            .coerceIn(0f, 1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        tts?.speak(text, queueMode, params, utteranceId)
    }

    /** 현재 시각에 맞는 인사 (아침 5~11, 오후 12~17, 저녁 18~21, 그 외 밤) */
    private fun timeGreeting(): String? {
        val pool = when (LocalTime.now().hour) {
            in 5..11  -> strings?.ttsGreetingMorning
            in 12..17 -> strings?.ttsGreetingAfternoon
            in 18..21 -> strings?.ttsGreetingEvening
            else      -> strings?.ttsGreetingNight
        }
        return pool?.takeIf { it.isNotEmpty() }?.random()
    }

    private fun announceCurrentTime() {
        val now  = LocalTime.now()
        val fmt  = strings?.ttsTimeFormat ?: "It is {ampm} {h}:{m}."
        val ampm = if (now.hour < 12) (strings?.ttsAmPm ?: "AM") else (strings?.ttsPmPm ?: "PM")
        val h12  = if (now.hour % 12 == 0) 12 else now.hour % 12
        val min  = now.minute.toString().padStart(2, '0')
        val msg  = fmt
            .replace("{ampm}", ampm)
            .replace("{h}", h12.toString())
            .replace("{m}", min)
//        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "time")
        speak(msg, TextToSpeech.QUEUE_FLUSH, "time")
    }

    private fun isTalkEnabled(): Boolean =
        getSharedPreferences("tmt_settings", MODE_PRIVATE)
            .getBoolean(PREF_TALK_ENABLED, true)

    /** 매초 호출: 응원 멘트 발화 시점이 되면 말한다 (안내 멘트와 독립) */
    private fun checkEncouragement() {
        val prefs = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_TALK_ENABLED, true)) {
            nextEncourageAtMs = 0L          // 해제 상태 → 재활성화 시 새로 예약
            return
        }
        val talkative = prefs.getBoolean("talkative", false)
        val now       = SystemClock.elapsedRealtime()

        if (nextEncourageAtMs == 0L) {      // 미예약 → 예약만 하고 이번 틱은 종료
            scheduleNextEncouragement(talkative, now)
            return
        }
        if (now < nextEncourageAtMs) return

        // 안내 멘트를 읽는 중이면 잠시 뒤로 미룸 (겹침 방지)
        if (tts?.isSpeaking == true) {
            nextEncourageAtMs = now + ENCOURAGE_RETRY_SEC * 1000L
            return
        }
        scheduleNextEncouragement(talkative, now)
        speakEncouragement()
    }

    private fun scheduleNextEncouragement(talkative: Boolean, now: Long) {
        val range = if (talkative)
            ENCOURAGE_CHATTY_MIN_SEC..ENCOURAGE_CHATTY_MAX_SEC
        else
            ENCOURAGE_QUIET_MIN_SEC..ENCOURAGE_QUIET_MAX_SEC
        nextEncourageAtMs = now + range.random() * 1000L
    }

    private fun speakEncouragement() {
        val pool = strings?.ttsEncouragements?.takeIf { it.isNotEmpty() }
            ?: listOf("Great!", "Keep going!", "Amazing!")
        val msg = pickDifferent(pool, lastEncourageText)
        lastEncourageText = msg
        speak(msg, TextToSpeech.QUEUE_ADD, "encourage")
    }

    private fun announceWorkout() {
        val dist = calcDistanceKm()                    // ✅ [변경 7]
        val cal  = calcCalories()                      // ✅ [변경 8]
        val fmt  = strings?.ttsWorkoutFormat
            ?: "Minute {min}, {steps} steps, {dist} km, {cal} calories."
        val msg = fmt
            .replace("{min}",   (elapsedSeconds / 60).toString())
            .replace("{steps}", (todayBaseSteps + currentSteps).toString())
            .replace("{dist}",  "%.2f".format(dist))
            .replace("{cal}",   cal.toString())
//        tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "workout")
        speak(msg, TextToSpeech.QUEUE_ADD, "time")
    }

    private fun saveMinutePoint(minute: Int, dateOverride: String? = null) {
        val totalSteps = todayBaseSteps + currentSteps
        if (totalSteps <= 0) return   // 신체 활동이 전혀 없으면 분 단위 포인트 자체를 저장하지 않음
        val distKm     = calcDistanceKm()   // base 포함 오늘 누적
        val cal        = calcCalories()     // base 포함 오늘 누적
//        val today      = currentDate.toString()
        val today      = dateOverride ?: currentDate.toString()

        // 분 단위 전용 lastSaved 로 비교 — 일 단위 저장과 독립
        if (totalSteps == lastMinSavedSteps &&
            distKm     == lastMinSavedDistanceKm &&
            cal        == lastMinSavedCalories
        ) {
            Log.d(TAG, "분 단위 저장 생략: 변경 없음 (steps=$totalSteps)")
            return
        }

        lastMinSavedSteps      = totalSteps
        lastMinSavedDistanceKm = distKm
        lastMinSavedCalories   = cal

        serviceScope.launch {
            try {
                val dao   = AppDatabase.getInstance(this@RunningForegroundService).workoutMinuteDao()
                // minuteIndex = 실제 경과 분을 그대로 사용.
                // DB 에 INSERT OR REPLACE 로 upsert하므로 중복 저장 없음.
                val point = WorkoutMinute(
                    date        = today,
                    minuteIndex = minute,
                    steps       = totalSteps,
                    distanceKm  = distKm,
                    calories    = cal
                )
                dao.insert(point)

                // DB INSERT 완료 후 ViewModel로 즉시 전달
                sendBroadcast(Intent(BROADCAST_MINUTE_POINT).apply {
                    setPackage(packageName)   // ✅ [추가]
                    putExtra(EXTRA_MINUTE_DATE,     today)
                    putExtra(EXTRA_MINUTE_INDEX,    minute)
                    putExtra(EXTRA_MINUTE_STEPS,    totalSteps)
                    putExtra(EXTRA_MINUTE_DISTANCE, distKm)
                    putExtra(EXTRA_MINUTE_CALORIES, cal)
                })
            } catch (e: Exception) {
                Log.e(TAG, "분 단위 저장 실패: ${e.message}")
            }
        }
    }

    private fun broadcastUpdate() {
        Log.d(TAG, "broadcastUpdate 전송: isRunning=$isVoiceActive, downloadProgress=$downloadProgress")   // ✅ [임시 진단용]
        sendBroadcast(Intent(BROADCAST_UPDATE).apply {
            setPackage(packageName)   // ✅ [추가] 명시적 브로드캐스트로 전환 (OEM 배경 제한 우회)
            putExtra(EXTRA_STEPS,       todayBaseSteps + currentSteps)  // 오늘 누적 걸음 수
            putExtra(EXTRA_DISTANCE,    calcDistanceKm())
            putExtra(EXTRA_CALORIES,    calcCalories())
            putExtra(EXTRA_ELAPSED_SEC, elapsedSeconds)
            putExtra(EXTRA_SESSION_ID,  sessionId)
            putExtra(EXTRA_RUNNING,     isVoiceActive)
        })
    }

    private fun broadcastVoiceState(listening: Boolean, modelLoading: Boolean, progress: Int = -1) {
        Log.d(TAG, "broadcastVoiceState 전송: listening=$listening, modelLoading=$modelLoading, progress=$progress")   // ✅ [임시 진단용]
        sendBroadcast(Intent(BROADCAST_VOICE_STATE).apply {
            setPackage(packageName)   // ✅ [추가]
            putExtra(EXTRA_LISTENING,         listening)
            putExtra(EXTRA_MODEL_LOADING,     modelLoading)
            putExtra(EXTRA_DOWNLOAD_PROGRESS, progress)
        })
    }

    private fun setupSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        Log.d(TAG, "걸음계 센서: ${if (sensor != null) "감지됨" else "❌ 기기에 없음 또는 null"}")   // ✅ [임시 진단용]
        sensor?.let {
            val registered = sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "센서 리스너 등록 결과: $registered")   // ✅ [임시 진단용]
        }
    }

    // ── 걸음 증가분 발생 시점마다 거리·칼로리를 증분 계산해 누적 ──
    // 전체 평균 페이스로 매번 재계산하지 않으므로, 페이스가 느려져도
    // 이미 누적된 거리·칼로리가 줄어들지 않는다 (단조증가 보장).
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values[0].toInt()
        Log.d(TAG, "센서 이벤트 수신: raw=$raw")   // ✅ [임시 진단용]
        val now = System.currentTimeMillis()

        if (startSteps == -1) {
            // 세션의 첫 센서 이벤트 — 기준점만 설정하고 증분 계산은 다음부터
            startSteps    = raw
            lastStepCount = raw
            lastStepTime  = now
            return
        }

        val newTotalSteps = raw - startSteps
        val deltaSteps     = newTotalSteps - currentSteps
        if (deltaSteps <= 0) {
            currentSteps = newTotalSteps
            return
        }

        // 이번 구간(직전 센서 이벤트 ~ 지금) 동안의 순시 페이스로 보폭 결정
        val deltaTimeSec  = ((now - lastStepTime).coerceAtLeast(0L)) / 1000.0
        val timePerStep   = if (deltaSteps > 0 && deltaTimeSec > 0) deltaTimeSec / deltaSteps else 0.6
        val strideM       = getDynamicStride(heightCm.toDouble(), timePerStep)
        val deltaDistKm   = (deltaSteps * strideM) / 1000.0

        // 이번 구간의 순시 속도 기준으로 칼로리 증분 계산
        val speedKmH      = if (deltaTimeSec > 0) deltaDistKm / (deltaTimeSec / 3600.0) else 0.0
        val deltaCalories = calculateCalories(weightKg.toDouble(), deltaTimeSec, speedKmH)

        distanceKm    += deltaDistKm
        caloriesAccum += deltaCalories

        currentSteps  = newTotalSteps
        lastStepCount = raw
        lastStepTime  = now
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = try {
                    Locale.forLanguageTag(currentLang.ttsLocale)
                } catch (e: Exception) {
                    Locale.ENGLISH
                }
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS 언어 미지원: ${currentLang.ttsLocale}, 영어로 fallback")
                    tts?.language = Locale.ENGLISH
                }
                tts?.setPitch(if (isMale) 0.8f else 1.2f)
                tts?.setSpeechRate(if (isTalkative) 1.1f else 0.95f)
            }
        }
    }

    private fun createNotificationChannel() {
        val channelName = strings?.notifChannelName ?: "TMT Running Service"
        val channel = NotificationChannel(
            CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(content: String, voiceActive: Boolean) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(content, voiceActive))
    }

    private fun buildNotification(content: String, voiceActive: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RunningForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopLabel = strings?.notifStopAction ?: "Stop"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(strings?.appName ?: DEFAULT_APP_NAME)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .apply { if (voiceActive) addAction(android.R.drawable.ic_media_pause, stopLabel, stopIntent) }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}