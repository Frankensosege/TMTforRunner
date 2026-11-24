package com.frankensosege.tmtforrunner.data.i18n

/**
 * 앱 내 모든 UI 문자열을 담는 데이터 클래스.
 * SQLite(Room)에 저장되며 선택된 언어에 따라 로드됩니다.
 *
 * [5차 세션 추가]
 *   heightLabel — 키 입력 항목 레이블 (다국어)
 *   weightLabel — 몸무게 입력 항목 레이블 (다국어)
 */
data class AppStrings(
    // 공통
    val appName: String,
    val save: String,
    val loading: String,
    val quitApp: String,
    val quitConfirmMsg: String,

// 대시보드
    val dashboardTitle: String,
    val statsDashboard: String,
    val tabDaily: String,
    val tabMonthly: String,
    val avgSpeed: String,
    val unitKmh: String,
    val yearLabel: String,
    val monthLabel: String,
    val confirm: String,
    val cancel: String,
    val noMonthRecordsMsg: String,
    val normalizedNote: String,
    val dailyAvgTitle: String,
    val settings: String,
    val chartSteps: String,
    val chartDistance: String,
    val chartCalories: String,
    val noRecordsMsg: String,

// 운동 화면
    val running: String,
    val steps: String,
    val distance: String,
    val calories: String,
    val backgroundNotice: String,
    val stopWorkout: String,
    val startWorkout: String,
    val workingOut: String,

// 설정 화면
    val settingsTitle: String,
    val settingsSubtitle: String,
    val voiceSelection: String,
    val voiceFemale: String,
    val voiceMale: String,
    val talkStyle: String,
    val talkQuiet: String,
    val talkChattily: String,
    val timeNotifyInterval: String,
    val timeNotifySubtitle: String,
    val workoutNotifyInterval: String,
    val workoutNotifySubtitle: String,
    val countrySelection: String,
    val saveAndStart: String,

// ── [5차 추가] 신체 정보 ──────────────────────────────
    val heightLabel: String,   // "키" / "Height" / "身長" / "身高"
    val weightLabel: String,   // "몸무게" / "Weight" / "体重" / "体重"

// 알림 주기
    val intervalRandom: String,
    val interval5min: String,
    val interval10min: String,
    val interval15min: String,
    val interval20min: String,

// 안내 문구
    val infoVoiceStart: String,
    val infoVoiceStop: String,
    val infoTimeAnnounce: String,
    val infoWorkoutAnnounce: String,
    val infoBackground: String,

// 마이크 상태
    val micListening: String,
    val micWaitStop: String,
    val micWaitStart: String,

// 모델 로딩/다운로드 상태 (다국어)
    val modelDownloading: String,
    val modelLoading: String,
    val modelReady: String,

// TTS 메시지
    val ttsWorkoutStart: String,
    val ttsWorkoutStop: String,
    val ttsAmPm: String,
    val ttsPmPm: String,
    val ttsTimeFormat: String,
    val ttsWorkoutFormat: String,
    val ttsEncouragements: List<String>,   // ttsEncouragement1~N 전체
    val ttsStartGreetings: List<String>,   // ttsWorkoutStart, ttsWorkoutStart2~N
    val ttsStopGreetings: List<String>,    // ttsWorkoutStop, ttsWorkoutStop2~N
    val ttsGreetingMorning: List<String>,
    val ttsGreetingAfternoon: List<String>,
    val ttsGreetingEvening: List<String>,
    val ttsGreetingNight: List<String>,
    val ttsVolumeLabel: String,

// 단위
    val unitKm: String,
    val unitKcal: String,
    val unitSteps: String,
    val unitMin: String,

// 알림 채널
    val notifChannelName: String,
    val notifWaitStart: String,
    val notifWorking: String,
    val notifStopAction: String,
    val notifModelLoading: String,

    val permRequiredTitle: String,
    val permRequiredMsg: String,
    val permSettingsMsg: String,
    val permOpenSettings: String,

    val updateReadyMsg: String,
    val updateRestart: String,
    val updateLater: String,
)