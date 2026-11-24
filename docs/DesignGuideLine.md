# 만보이야기(TMT for Runner) — 프로젝트 개발 가이드라인

> **문서 목적**: 현재까지 완료된 개발 내용과 확정된 설계 원칙을 정리하여, 이후 개발 세션에서 일관성을 유지하기 위한 기준 문서
> **최종 업데이트**: 8차 개발 세션 — 응원 멘트 분리·대화 스타일 체크박스, 다운로드 진행률 수정, R8 난독화

---

## 1. 프로젝트 개요

### 1-1. 개요
| 항목 | 내용 |
|------|------|
| 앱 이름 | 만보이야기(TMT for Runner) |
| 패키지 | `com.frankensosege.tmtforrunner` |
| 플랫폼 | Android (Kotlin + Jetpack Compose) |
| 최소 SDK | Android Q (API 29) 이상 |
| 아키텍처 | MVVM + Foreground Service + Room DB |
| 음성인식 | Vosk (오프라인, 인터넷 다운로드) |
| TTS | Android 기본 TextToSpeech |
| 다국어 | Room DB 기반 동적 문자열 관리 (AppStrings) |
| 코드 품질 | Slack Compose Lint 규칙 전면 준수 |

> **앱 이름 표기 원칙**: 문서 전체에서 앱 이름은 `만보이야기(TMT for Runner)`로 표기한다.
> 패키지명·코드 식별자(`com.frankensosege.tmtforrunner`, `TMTforRunnerTheme` 등)는 변경하지 않는다.

### 1-2. 목적(기능)
- 운동 중 운동량 및 운동시간, 현재시간 등을 음성으로 알려준다
- 운동 중 개인 관심사에 대한 잡담이나, LLM을 통한 업무 처리의 내용 등을 음성으로 알려준다
- 앱이 시작되면 해당일의 신체할동을 일자별로 누적 저장한다
- 매일 신체활동을 기록하여 신체활동의 변화를 그래프를 통하여 조회할 수 있도록 한다
- 음성 알람이나 잡담은 시동어를 말하거나 화면상 버튼을 클릭하는 것으로 활성/비활성 하도록 한다

---

## 2. 확정 가이드라인 (전체)

### 2-1. 화면 흐름

```
최초 실행
    └→ SettingsScreen (설정 화면)
            └→ [저장하고 시작] 버튼 클릭
                    └→ RunningScreen (메인 운동 화면)
                            ├→ [⚙️] → SettingsScreen
                            ├→ [Dashboard] → DashboardScreen
                            │       └→ [⚙️] → SettingsScreen
                            └→ [앱 종료] → 앱 완전 종료

재실행 시 (setup_done = true)
    └→ RunningScreen 바로 진입
```

- 설정 완료 여부: SharedPreferences `setup_done` Boolean으로 관리
- SettingsScreen에서 저장 후 RunningScreen으로 이동할 때 `popUpTo(SETTINGS) { inclusive = true }` 처리

### 2-2. SettingsScreen

- 언어 선택 목록: **영어 / 한국어 / 일본어 / 중국어 4개만** 표시
  - `SupportedLanguage` enum에서 ENGLISH, KOREAN, JAPANESE, CHINESE만 정의
  - 언어 변경 시 즉시 UI 미리보기 반영 (`onLanguageChange` 콜백 → `viewModel.changeLanguage()`)
- 설정 항목: 언어, 목소리(여성/남성), 대화 스타일(체크박스 + 조용/수다 라디오), 시간 알림 주기, 운동량 알림 주기, 키/몸무게
- **대화 스타일**: 제목 옆 체크박스(`talk_enabled`)로 응원 멘트 사용 여부를 정한다
  - 체크: 조용하게/수다스럽게 라디오 버튼 활성화
  - 해제: 라디오 버튼 비활성화(회색 처리), 응원 멘트 발화 안 함
  - 체크박스는 `toggleable(role = Role.Checkbox)` + `Checkbox(onCheckedChange = null)` 조합 (접근성 트리 중복 방지)
- 알림 주기 선택지: 무작위 / 5분 / 10분 / 15분 / 20분
- 저장 시 SharedPreferences(`tmt_settings`)에 저장

### 2-3. RunningScreen

- **좌측 상단**: ⚙️ 아이콘 버튼 → SettingsScreen 이동
- **시간 표시**: 경과시간을 **시:분(HH:MM)** 형식으로 표시
  - `RunningState.timerText` = `"%02d:%02d".format(elapsedSec / 3600, (elapsedSec % 3600) / 60)`
  - 예: 1시간 30분 경과 → `01:30`
- **Start/Stop 버튼**:
  - **음성 기능(TTS 알람·대화)만** on/off. 센서 집계·DB 저장은 항상 동작.
  - 버튼 누름 → `isVoiceActive` 토글 → TTS 시작/종료 메시지 재생
  - 운동 전: 선택 언어로 번역된 "시작" / "Start" 등 (`strings.startWorkout`)
  - 운동 중: 선택 언어로 번역된 "종료" / "Stop" 등 (`strings.stopWorkout`)
  - 버튼 색상: 음성 off = `primary`, 음성 on = `tertiary`
- **Dashboard 버튼**: DashboardScreen으로 이동
- **앱 종료 버튼**: 음성알림·대화기능·마이크 포함 앱 완전 종료 (`ACTION_QUIT`)
- **다운로드 진행 표시**: Vosk 모델 다운로드 중 `LinearProgressIndicator` 표시 (다국어)
- **마이크 상태 표시**: MicIndicator 컴포넌트 (로딩/대기/듣는 중 3가지 상태)
- RunningScreen 진입 시 신체활동 집계 시작 (Foreground Service 시작)
- **음성 볼륨 슬라이더**: 마이크 상태 표시(MicIndicator) 바로 아래에 🔊 + Slider(0~100%) + 퍼센트 표시
  - 값은 SharedPreferences `tts_volume`(Float 0.0~1.0, 기본 1.0)에 저장
  - 드래그 중에는 UI 값만 갱신(`setTtsVolume`), 손을 뗄 때 저장(`saveTtsVolume`)
  - 서비스는 안내 음성을 낼 때마다 `tts_volume`을 읽어 `KEY_PARAM_VOLUME`으로 적용 (재생 중인 문장은 바뀌지 않고 다음 안내부터 반영)
  - 기기 미디어 볼륨 대비 비율이므로 기기 볼륨보다 크게 할 수 없음
  - 슬라이더에 `contentDescription`(`strings.ttsVolumeLabel`) 지정

### 2-4. DashboardScreen

### 2-4. DashboardScreen

- **좌측 상단**: ⚙️ 아이콘 버튼 → SettingsScreen 이동
- **마이크 상태 표시**: MicIndicator (헤더 아래)
- **탭 2개**: 일별 / 월별 (기존 걸음수·거리·칼로리 3탭 구조 폐지)
- **공통 색상(4색 고정)**: 걸음수 파랑 `ColorSteps` / 이동거리 초록 `ColorDistance` / 칼로리 주황 `ColorCalories` / 평균속도 보라 `ColorSpeed` (ChartView.kt에 정의)
- **평균속도 정의**: 거리(km) ÷ 운동시간(h), `workout_records.durationMin` 사용. 시간이 0이면 0

#### 탭 1: 일별
- 상단: ◀ 날짜(탭하면 DatePicker) ▶ — 기록이 있는 날짜만 선택 가능, 수평 스와이프(80f 이상)로 날짜 전환 (스와이프는 일별 탭 내부에서만 동작)
- 날짜 아래: 선택한 날의 걸음수 / 이동거리 / 소모칼로리 / 평균속도를 텍스트로 표시 (`workout_records` 기준)
- 하단: MPAndroidChart LineChart, `Mode.HORIZONTAL_BEZIER`, 4개 항목을 각자 최댓값 대비 0~100%로 정규화해 4색으로 표시
  - 걸음수·거리·칼로리는 누적값, 속도는 누적 평균속도
  - 차트 터치 시 그래프 아래에 해당 시점의 실제 값 표시, 선택이 없으면 안내 문구(`normalizedNote`)
  - X축은 `minuteIndex` 값 자체를 `H:MM`으로 포맷 (빈 분이 있어도 라벨이 어긋나지 않음)
  - 차트 자체 드래그/줌은 비활성화 (부모 스와이프와 충돌 방지)
- 기록 없음: `noRecordsMsg` 표시

#### 탭 2: 월별
- 상단: 년 / 월 드롭다운 선택
- 아래: 해당 월 **일평균** 걸음수 / 이동거리 / 소모칼로리 / 평균속도 (기록이 있는 날 기준)
- 하단: MPAndroidChart BarChart **stacked bars**, 일(1~말일)별 막대, 각 항목을 월 최댓값 대비 0~100%로 정규화해 4색으로 쌓음
  - 막대 전체 높이는 서로 다른 단위의 합이므로 비교용, Y축 눈금은 숨김
  - 막대 터치 시 그 날짜의 실제 값 표시
- 탭 진입 시 `reloadMonth()`로 최신 데이터 갱신
- 기록 없음: `noMonthRecordsMsg` 표시

#### 공통
- **FAB**: ▶ 버튼으로 RunningScreen 복귀
- **배너 광고**: 화면 하단 고정. 탭 콘텐츠는 `Modifier.weight(1f)`를 받고, **빈 상태(기록 없음) Box도 `fillMaxSize()`가 아니라 `fillMaxWidth().weight(1f)`를 사용**해야 한다 (fillMaxSize 사용 시 배너가 화면 밖으로 밀림 — 재발 이력 있음)

### 2-5. 음성 시동어 (VoiceCommandManager)

| 항목 | 내용 |
|------|------|
| 엔진 | Vosk (오프라인 STT) |
| 모델 다운로드 | assets 사용 안 함, 인터넷 다운로드 후 `context.filesDir/models/` 에 저장 |
| 다운로드 시점 | 앱 최초 실행 시 1회. 이후 재실행 시 기존 모델 재사용 |
| 시동어 대기 시작 | Vosk 모델 다운로드/로드 완료 시점부터 앱 실행 동안 상시 대기 |
| 시동어 | **모든 언어에서 "start" / "stop" 영어 단어로 통일** |
| 쿨다운 | 명령어 인식 후 5초간 재인식 방지 |
| 신뢰도 임계값 | 0.80 이상만 인식 |
| AudioManager | 마이크 입력 대기음 제거를 위해 `STREAM_NOTIFICATION`, `STREAM_RING` 음소거 (VoiceCommandManager 시작 시 적용, 종료 시 해제) |

#### 언어별 Vosk 모델 매핑

| 선택 언어 | Vosk 모델 | 이유 |
|-----------|-----------|------|
| 영어(en) | `vosk-model-small-en-us-0.15` | 영어 모델 직접 사용 |
| **한국어(ko)** | **`vosk-model-small-en-us-0.15` (영어 모델)** | **한국어 모델 인식률이 낮아 영어 모델 사용** |
| 일본어(ja) | `vosk-model-small-ja-0.22` | 일본어 모델 (영어 시동어도 인식) |
| 중국어(zh) | `vosk-model-small-cn-0.22` | 중국어 모델 (영어 시동어도 인식) |

> `SupportedLanguage.voskModelLang` 필드로 관리. 한국어는 `voskModelLang = "en"` 으로 설정.

### 2-6. 다국어(i18n) 처리 원칙

- **모든 UI 텍스트**는 `AppStrings` data class를 통해 선택된 언어로 표시
- 문자열은 `StringsDatabase` (Room)에 저장, `StringsRepository`로 접근
- 언어 변경 시 즉시 전체 UI 갱신 (StateFlow)
- TTS 음성도 선택된 언어 로케일 적용 (`SupportedLanguage.ttsLocale`)
  - TTS 언어 미지원 시 영어로 Fallback
- 알림(Notification) 텍스트도 선택 언어로 표시
- 앱 미시작 시 기본값(`defaultStrings`)은 영어 기준

#### TTS 메시지 포맷 (placeholders)

| 포맷 키 | placeholders |
|---------|-------------|
| `ttsTimeFormat` | `{ampm}`, `{h}`, `{m}` |
| `ttsWorkoutFormat` | `{min}`, `{steps}`, `{dist}`, `{cal}` |

### 2-7. 운동 데이터 저장

- **상시 저장**: 앱 시작~종료 전 구간 신체기록을 항상 저장 (Start/Stop 버튼 무관)
  - 5분(300초)마다 일 단위 합산 DB 저장 (`saveCurrentDataToDB`)
  - 매 분마다 분단위 포인트 저장 (`workout_minutes`)
  - 앱 종료(`ACTION_QUIT`) 또는 음성 종료(`ACTION_STOP`) 시 즉시 저장
- **날짜 단위 합산**: 같은 날 여러 번 운동 시 누적 저장 (`addToDate`)
- **자정 reset**: 일자 변경 감지 시 걸음수·경과시간·분타이머 자동 reset
  - `startSteps = -1` 재초기화 → 다음 센서값부터 새 날짜 기준으로 집계
- **재실행 reset**: 앱 재실행 시 `checkTodayDataAndReset()` 호출
  - DB에서 오늘자 `workout_records` 조회 → 데이터 없으면 reset (날짜 바뀐 채 재실행)
  - 데이터 있으면 이어서 집계 (경과시간은 이번 실행 기준 0부터 재시작)
  - 자정 reset의 보완: 앱이 꺼진 사이 날짜가 바뀐 경우를 처리
- **신규 날짜**: 해당 날 첫 운동이면 `INSERT`, 이미 있으면 누적분만 `UPDATE`
- **분 단위 그래프 데이터**: `workout_minutes` 테이블에 저장 (날짜 + minuteIndex 기준)
  - 새 세션 시작 시 해당 날의 마지막 `minuteIndex`에 이어서 저장
- **DB 버전**: `AppDatabase` v3, `StringsDatabase` v3 (변경 시 `fallbackToDestructiveMigration`)
  - `AppDatabase`는 사용자 운동 기록이 있으므로 엔티티 변경이 없으면 버전을 올리지 않는다 (올리면 기록 삭제됨)
  - `StringsDatabase`는 문자열 키 추가 시 버전을 올려야 기존 설치본에 반영됨 (문자열만 삭제 후 재시드)
- **월별 조회**: `WorkoutDao.getByMonth("yyyy-MM")` — 스키마 변경 없음

### 2-8. 알림 주기

```kotlin
enum class NotifyInterval { RANDOM, MIN_5, MIN_10, MIN_15, MIN_20 }
// RANDOM: 2~5분 사이 무작위
```

- 현재 시간 알림과 운동량 알림 주기를 각각 독립 설정
- 기본값: 시간 알림 10분, 운동량 알림 5분

### 2-9. 앱 종료 처리 (ACTION_QUIT)

`RunningForegroundService.quitApp()` 에서 아래 순서로 처리:
1. `voiceCommandManager?.stop()` — 마이크 포함 음성 기능 완전 정지
2. `sensorManager?.unregisterListener()` — 센서 해제
3. `tts?.shutdown()` — TTS 종료
4. `stopForeground()` + `stopSelf()` — 서비스 종료
5. `MainActivity.ACTION_FINISH` Intent로 액티비티 종료

### 2-10. 기능상 상충 발생 시 처리 원칙

> 기능상 상충되는 가이드라인이 발생하면 반드시 질문하여 답변을 가이드라인에 추가한다.

### 2-11. 권한 정책 및 포그라운드 서비스 타입

| 권한 | 구분 | 거부 시 동작 |
|------|------|-------------|
| `ACTIVITY_RECOGNITION` (신체활동) | **필수** | 서비스를 시작하지 않고 필수 안내 다이얼로그 표시 |
| `RECORD_AUDIO` (마이크) | 선택 | 서비스는 정상 시작, 음성 시동어만 동작하지 않음 |
| `POST_NOTIFICATIONS` (알림) | 선택 | 서비스는 정상 시작, 알림만 표시되지 않음 |

- **필수 권한 다이얼로그**: `setCancelable(false)`, 거부 시 확인 → 권한 요청 창으로 복귀
  - 시스템이 더 이상 권한 창을 띄우지 않는 경우(영구 거부, `shouldShowRequestPermissionRationale == false`)에는 "설정 열기" 버튼으로 앱 설정 화면 이동, 복귀(`onResume`) 시 재검사
  - Android 10 미만은 신체활동이 런타임 권한이 아니므로 검사 생략
  - 메시지는 `permRequiredTitle` / `permRequiredMsg` / `permSettingsMsg` / `permOpenSettings` (다국어)
- **포그라운드 서비스 타입 동적 지정** (Android 14+ 크래시 방지): 매니페스트는 `foregroundServiceType="health|microphone"`이지만, `startForeground`는 **허용된 권한에 해당하는 타입만** 지정한다
  - 신체활동 허용 → `HEALTH`, 마이크까지 허용 → `HEALTH | MICROPHONE`
  - `RunningForegroundService.startForegroundSafely()`에서 처리, 실패하거나 신체활동 권한이 없으면 `stopSelf()`
  - `MainActivity.startRunningService()`의 `startForegroundService()`도 `try/catch`
- 마이크를 나중에 허용한 경우 앱 종료 후 재실행해야 백그라운드 음성 인식이 정상 동작

### 2-12. 응원 멘트

- 응원 멘트는 시간·운동량 안내 멘트와 **별개로** 랜덤 발화한다. 안내 멘트 끝에 응원 문구를 붙이지 않는다
- 문구는 `ttsEncouragement1~3` 중 랜덤 선택 (앞뒤 공백은 `trim()`)
- 음성 기능이 켜져 있을 때(`isVoiceActive`)만 발화. 센서 집계·DB 저장과는 무관
- 발화 빈도는 대화 스타일에 따라 다르다

| 대화 스타일 | 발화 간격(랜덤) | 10분당 횟수 |
|---|---|---|
| 조용하게 | 300~600초 | 약 1~2회 |
| 수다스럽게 | 150~200초 | 약 3~4회 |

- `talk_enabled`가 false이면 발화하지 않고 예약을 초기화한다. 다시 true가 되면 새 간격으로 예약한다
- 안내 멘트 재생 중(`tts.isSpeaking`)이면 10초 뒤로 미룬다 (겹침 방지)
- 구현: `RunningForegroundService.checkEncouragement()`를 `onTimerTick()`의 `isVoiceActive` 블록에서 매초 호출, 예약 시각은 `SystemClock.elapsedRealtime()` 기준(`nextEncourageAtMs`, 0 = 미예약)
- 발화는 `speak(msg, QUEUE_ADD, "encourage")` 래퍼 사용 (볼륨 적용)

### 2-13. 인사말 / 앱 업데이트

- **시간대별 인사**: 음성 시작 시 시각(아침 5~11, 오후 12~17, 저녁 18~21, 그 외 밤)에 맞는 인사(`ttsGreetingMorning/Afternoon/Evening/Night` + 번호)를 시작 인사 앞에 붙여 한 문장으로 말한다. 문구 추가는 번호 키만 이어서 추가하고 `StringsDatabase` 버전을 올린다
- **앱 업데이트**: Google Play 인앱 업데이트(FLEXIBLE). `InAppUpdateManager`가 `onResume`에서 확인(하루 1회 안내, 디버그 빌드 생략)하고, 다운로드 완료 시 재시작 안내 다이얼로그를 표시한다. 즉시 적용(IMMEDIATE)은 운동 중 앱이 막히므로 사용하지 않는다
- 업데이트는 Play 설치본에서만 동작하며, 새 버전 게시 후 반영까지 시간이 걸릴 수 있다

---

## 3. 프로젝트 파일 구조

```
app/src/main/java/com/example/tmtforrunner/
│
├── MainActivity.kt                    # NavHost, 권한 요청, 서비스 시작
│
├── data/
│   ├── WorkoutRecord.kt               # Room Entity (workout_records, workout_minutes), DAO, AppDatabase
│   └── i18n/
│       ├── SupportedLanguage.kt       # 지원 언어 enum (4개), voskModelLang 포함
│       ├── AppStrings.kt              # 모든 UI 문자열 data class
│       ├── StringsDatabase.kt         # 문자열 Room DB, 언어별 데이터 빌더
│       └── StringsRepository.kt       # 문자열 조회/시드 레포지토리
│
├── manager/
│   ├── VoiceCommandManager.kt         # Vosk STT, 모델 다운로드, AudioManager 음소거
│   └── TtsManager.kt                  # TTS 래퍼 (ConversationProvider 패턴, LLM 확장 준비)
│
├── model/
│   └── NotifyInterval.kt              # 알림 주기 enum, shouldAnnounce 로직
│
├── service/
│   └── RunningForegroundService.kt    # 센서, TTS, VoiceCommand, 타이머, 데이터 저장, Broadcast
│
├── ui/
│   ├── component/
│   │   ├── MicIndicator.kt            # 마이크 상태 표시 컴포넌트 (로딩/대기/듣는 중)
│   │   ├── ChartView.kt               # 정규화 꺾은선(NormalizedLineChart), 월별 누적 막대(MonthlyStackedBarChart), 4색 상수, StatLabels, DayStat
│   │   └── AdBanner.kt                # AdMob 배너 (remember(adUnitId) + DisposableEffect)
│   ├── screen/
│   │   ├── RunningScreen.kt           # 메인 운동 화면
│   │   ├── SettingsScreen.kt          # 설정 화면
│   │   └── DashboardScreen.kt         # 대시보드 (날짜별 그래프)
│   └── theme/
│       └── Theme.kt                   # Material3 테마
│
└── viewmodel/
    └── MainViewModel.kt               # RunningState, RecordsState, 날짜 네비게이션, 언어 관리
```

---

## 4. 핵심 데이터 모델

### RunningState (UI 상태)
```kotlin
data class RunningState(
  val steps: Int              = 0,
  val distanceKm: Double      = 0.0,
  val calories: Int           = 0,
  val elapsedSec: Int         = 0,
  val isRunning: Boolean      = false,
  val isListening: Boolean    = false,   // Vosk 마이크 활성 여부
  val isModelLoading: Boolean = false,   // 모델 다운로드/로딩 중
  val downloadProgress: Int   = -1       // -1: 없음, 0~100: 진행률
)
```

### SupportedLanguage
```kotlin
enum class SupportedLanguage(
  val languageCode: String,   // ISO 639-1
  val countryCode: String,    // ISO 3166-1
  val displayName: String,    // 자국어 표기
  val englishName: String,
  val ttsLocale: String,      // BCP 47 (TTS용)
  val voskModelLang: String   // vosk 모델 언어코드 (ko → "en")
) {
  ENGLISH  ("en", "US", "English (United States)", ..., "en-US", "en"),
  KOREAN   ("ko", "KR", "한국어 (대한민국)",        ..., "ko-KR", "en"),  // 영어 모델 사용
  JAPANESE ("ja", "JP", "日本語 (日本)",            ..., "ja-JP", "ja"),
  CHINESE  ("zh", "CN", "中文 (中国)",              ..., "zh-CN", "zh")
}
```

### AppStrings 주요 필드 그룹
```
공통: appName, save, loading, quitApp
대시보드: dashboardTitle, settings, chartSteps, chartDistance, chartCalories, noRecordsMsg,
         tabDaily, tabMonthly, avgSpeed, unitKmh, yearLabel, monthLabel,
         confirm, cancel, noMonthRecordsMsg, normalizedNote, dailyAvgTitle, statsDashboard
운동화면: running, steps, distance, calories, backgroundNotice,
         stopWorkout("종료"), startWorkout("시작"), workingOut,
         ttsVolumeLabel
설정화면: settingsTitle, settingsSubtitle, voiceSelection, voiceFemale, voiceMale,
         talkStyle, talkQuiet, talkChattily, timeNotifyInterval, workoutNotifyInterval,
         countrySelection, saveAndStart, interval[Random/5/10/15/20]min,
         heightLabel, weightLabel
안내문구: infoVoiceStart, infoVoiceStop, infoTimeAnnounce, infoWorkoutAnnounce, infoBackground
마이크: micListening, micWaitStop, micWaitStart
모델상태: modelDownloading, modelLoading, modelReady
TTS: ttsWorkoutStart, ttsWorkoutStop, ttsAmPm, ttsPmPm,
     ttsTimeFormat, ttsWorkoutFormat, ttsVolumeLabel
     ttsEncouragements(List), ttsStartGreetings(List), ttsStopGreetings(List)
단위: unitKm, unitKcal, unitSteps, unitMin
알림: notifChannelName, notifWaitStart, notifWorking, notifStopAction, notifModelLoading
권한: permRequiredTitle, permRequiredMsg, permSettingsMsg, permOpenSettings
```

---

## 5. SharedPreferences 키 목록 (`tmt_settings`)

| 키 | 타입      | 기본값   | 설명                     |
|--------------|---------|-------|------------------------|
| `setup_done` | Boolean | false | 최초 설정 완료 여부            |
| `language_code` | String  | "en"  | 선택 언어 코드 (en/ko/ja/zh) |
| `voice_male` | Boolean | false | true=남성, false=여성 TTS  |
| `talkative` | Boolean | false | true=수다, false=조용      |
| `time_notify_interval` | String  | "10"  | 시간 알림 주기 prefKey       |
| `workout_notify_interval` | String  | "5"   | 운동량 알림 주기 prefKey      |
| `tts_volume` | Float | 1.0 | 안내 음성 볼륨 (0.0~1.0, 미디어 볼륨 대비 비율) |
| `height_cm` | Int | 170 | 키(cm), 입력 범위 100~250 |
| `weight_kg` | Int | 65 | 몸무게(kg), 입력 범위 30~150 |
| `talk_enabled` | Boolean | true | 응원 멘트 사용 여부 (대화 스타일 체크박스) |

---

## 6. Broadcast 통신 구조

### Service → ViewModel (BroadcastReceiver)

| Action | Extra 키 | 타입 | 설명 |
|--------|----------|------|------|
| `BROADCAST_UPDATE` | `steps` | Int | 현재 걸음 수 |
| | `distance` | Double | 거리(km) |
| | `calories` | Int | 칼로리 |
| | `elapsed_sec` | Int | 경과 초 |
| | `session_id` | Long | 세션 ID |
| | `is_running` | Boolean | 운동 중 여부 |
| `BROADCAST_VOICE_STATE` | `is_listening` | Boolean | 마이크 활성 |
| | `model_loading` | Boolean | 모델 로딩 중 |
| | `download_progress` | Int | 다운로드 진행률 (-1~100) |
| `BROADCAST_MINUTE_POINT` | `minute_date`, `minute_index`, `minute_steps`, `minute_distance`, `minute_calories` | 분 단위 저장 직후 ViewModel에 전달, DB 재조회 없이 `currentDateMinutes` 갱신 |

### ViewModel → Service (Intent Action)

| Action | 설명 |
|--------|------|
| `ACTION_START` | 운동 세션 시작 |
| `ACTION_STOP` | 운동 세션 종료 + DB 저장 |
| `ACTION_QUIT` | 마이크·TTS·센서 포함 앱 완전 종료 |

---

## 7. Vosk 모델 다운로드 URL

| 언어코드 | 폴더명 | URL |
|----------|--------|-----|
| `en` | `vosk-model-small-en-us-0.15` | https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip |
| `ja` | `vosk-model-small-ja-0.22` | https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip |
| `zh` | `vosk-model-small-cn-0.22` | https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip |

- 저장 경로: `context.filesDir/app/src/main/models/{folderName}/`
- 한국어 선택 시 → `en` 키로 영어 모델 사용 (`SupportedLanguage.voskModelLang`)
- `ko` 키는 MODEL_MAP에 의도적으로 미등록

---

## 8. Slack Compose Lint 준수 원칙

> **모든 Composable 코드는 Slack Compose Lint 규칙을 전면 준수한다.**
> 신규 코드 작성 및 기존 코드 수정 시 아래 규칙을 체크리스트로 활용한다.

### 8-1. Composable 함수 설계

#### ✅ Modifier 파라미터 필수 선언
모든 UI를 구성하는 `@Composable` 함수는 반드시 `modifier: Modifier = Modifier` 파라미터를 첫 번째 또는 명확한 위치에 선언하고, 루트 레이아웃에 전달해야 한다.

```kotlin
// ✅ 올바른 예
@Composable
fun MicIndicator(
  modifier: Modifier = Modifier,   // 항상 선언
  isListening: Boolean,
  ...
) {
  Column(modifier = modifier, ...) { ... }   // 루트에 전달
}

// ❌ 잘못된 예 — modifier 파라미터 없음
@Composable
fun MicIndicator(isListening: Boolean) {
  Column { ... }
}
```

#### ✅ Composable 함수 명명 규칙
- 값을 반환하는 Composable: 소문자 카멜케이스 (`remember`, `produceState` 등 관용어 제외)
- UI를 방출(emit)하는 Composable: **PascalCase** (대문자 시작)
- `@Composable` 람다 파라미터 이름: `content`를 기본으로 사용

```kotlin
// ✅ UI 방출 → PascalCase
@Composable fun RunningScreen(...) { }
@Composable fun StatCard(...) { }

// ✅ 값 반환 → camelCase
@Composable fun rememberScrollState(): ScrollState { }
```

#### ✅ Unit을 반환하는 Composable에 @Composable 람다 사용 시 후행 람다 형식 권장
```kotlin
// ✅ 올바른 예
SettingSection(title = "언어 선택") {
  DropdownSelector(...)
}

// ❌ 잘못된 예
SettingSection(title = "언어 선택", content = { DropdownSelector(...) })
```

---

### 8-2. 상태(State) 관리

#### ✅ remember 없이 mutableStateOf 사용 금지
Composable 내부에서 상태를 생성할 때는 반드시 `remember`로 감싸야 한다. 리컴포지션마다 초기화되는 버그를 방지한다.

```kotlin
// ✅ 올바른 예
var expanded by remember { mutableStateOf(false) }
var dragAccum by remember { mutableFloatStateOf(0f) }

// ❌ 잘못된 예 — 리컴포지션마다 초기화됨
var expanded by mutableStateOf(false)
```

#### ✅ 타입별 특화 State API 사용
| 타입 | 사용할 API |
|------|-----------|
| `Boolean` | `remember { mutableStateOf(false) }` |
| `Int` | `remember { mutableIntStateOf(0) }` |
| `Float` | `remember { mutableFloatStateOf(0f) }` |
| `Long` | `remember { mutableLongStateOf(0L) }` |
| 객체/String | `remember { mutableStateOf(...) }` |

#### ✅ ViewModel State는 StateFlow로, UI에서 collectAsStateWithLifecycle() 사용
```kotlin
// ViewModel
val state = MutableStateFlow(RunningState())

// Composable
val state by viewModel.state.collectAsStateWithLifecycle()

// ❌ 금지 — collectAsState()는 라이프사이클 미인식
val state by viewModel.state.collectAsState()
```

---

### 8-3. 람다와 이벤트 핸들러

#### ✅ 이벤트 콜백 파라미터 명명: `on` + 동사
```kotlin
// ✅ 올바른 예
onStartClick: () -> Unit
onNavigateToSettings: () -> Unit
onLanguageChange: (SupportedLanguage) -> Unit

// ❌ 잘못된 예
startCallback: () -> Unit
settingsNavigator: () -> Unit
```

#### ✅ 람다를 매번 새로 생성하지 않기 — 안정적인 참조 사용
Composable에 전달하는 람다가 매번 새 인스턴스로 생성되면 불필요한 리컴포지션이 발생한다. NavController 콜백 등은 상위에서 안정적인 참조로 전달한다.

```kotlin
// ✅ 올바른 예 — 상위 Composable에서 람다를 한 번만 정의하여 전달
RunningScreen(
  onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
  ...
)

// ❌ 잘못된 예 — 내부에서 매번 생성
Button(onClick = { viewModel.doSomething() })  // ViewModel 참조가 stable하면 허용
```

---

### 8-4. 성능 — 리컴포지션 최소화

#### ✅ 무거운 계산은 remember로 캐싱
```kotlin
// ✅ 올바른 예
val intervalLabels = remember(strings) {
  listOf(
    strings.intervalRandom to NotifyInterval.RANDOM,
    strings.interval5min   to NotifyInterval.MIN_5,
    ...
  )
}

// ❌ 잘못된 예 — 리컴포지션마다 리스트 재생성
val intervalLabels = listOf(
  strings.intervalRandom to NotifyInterval.RANDOM,
  ...
)
```

#### ✅ LaunchedEffect / SideEffect 올바른 사용
- 부수 효과(side effect)는 반드시 `LaunchedEffect`, `SideEffect`, `DisposableEffect` 안에서 처리
- Composable 본문에서 직접 코루틴 실행, 서비스 시작 등 금지

```kotlin
// ✅ 올바른 예
LaunchedEffect(language) {
  viewModel.changeLanguage(language)
}

// ❌ 잘못된 예 — Composable 본문에서 직접 부수 효과 실행
viewModel.changeLanguage(language)  // 리컴포지션마다 호출됨
```

#### ✅ key 파라미터 명시 — LaunchedEffect, remember
```kotlin
// ✅ key를 명확히 지정
LaunchedEffect(Unit) { /* 최초 1회만 실행 */ }
LaunchedEffect(state.isRunning) { /* isRunning 변경 시 실행 */ }

// ❌ key 없이 사용 — Lint 경고 발생
LaunchedEffect { }
```

---

### 8-5. Compose UI 구성 규칙

#### ✅ LinearProgressIndicator — progress 람다 형식 사용 (Compose 1.6+)
```kotlin
// ✅ 올바른 예 (Compose 1.6+ API)
LinearProgressIndicator(
  progress = { state.downloadProgress / 100f },
  modifier = Modifier.fillMaxWidth()
)

// ❌ 구버전 API — Lint 경고 발생
LinearProgressIndicator(
  progress = state.downloadProgress / 100f,
  ...
)
```

#### ✅ Scaffold padding 반드시 사용
`Scaffold`가 제공하는 `paddingValues`는 반드시 내부 콘텐츠에 적용해야 한다.

```kotlin
// ✅ 올바른 예
Scaffold { paddingValues ->
  Column(modifier = Modifier.padding(paddingValues)) { ... }
}

// ❌ 잘못된 예 — padding 무시
Scaffold { _ ->
  Column { ... }
}
```

#### ✅ Box/Column/Row — fillMaxSize/fillMaxWidth 등 크기 지정 시 modifier 순서
Modifier 체이닝은 선언 순서가 적용 순서이므로, 크기 → 패딩 → 클릭 순서를 권장한다.

```kotlin
// ✅ 권장 순서: 크기 → 패딩 → 동작
Modifier
  .fillMaxWidth()
  .padding(16.dp)
  .clickable { }

// ❌ 잘못된 순서 (패딩이 클릭 영역에 포함됨)
Modifier
  .clickable { }
  .padding(16.dp)
  .fillMaxWidth()
```

#### ✅ InfiniteTransition — label 파라미터 필수 지정
```kotlin
// ✅ 올바른 예
val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
val scale by infiniteTransition.animateFloat(
  ...,
label = "scale"
)

// ❌ label 없음 — Lint 경고
val infiniteTransition = rememberInfiniteTransition()
val scale by infiniteTransition.animateFloat(...)
```

---

### 8-6. 접근성(Accessibility)

#### ✅ 아이콘 버튼에 contentDescription 지정
```kotlin
// ✅ 올바른 예
IconButton(onClick = onNavigateToSettings) {
  Icon(
    imageVector = Icons.Default.Settings,
    contentDescription = strings.settings   // 다국어 문자열 사용
  )
}

// 현재 코드처럼 Text("⚙️")를 사용할 경우 Box에 semantics 추가
Box(
  modifier = Modifier.semantics { contentDescription = strings.settings }
) {
  Text("⚙️", fontSize = 24.sp)
}
```

#### ✅ RadioButton — onClick = null + 부모 selectable 조합
```kotlin
// ✅ 올바른 예 — 접근성 트리 중복 방지
Row(
  Modifier.selectable(
    selected = selected,
    onClick  = onClick,
    role     = Role.RadioButton
  )
) {
  RadioButton(selected = selected, onClick = null)  // null로 위임
  Text(label)
}
```

---


## 9. 다음 개발 시 주의사항 및 확장 포인트

### 반드시 지켜야 할 원칙

1. **새 UI 텍스트 추가 시**: `AppStrings` data class 필드 추가 → `StringsDatabase`의 4개 언어 모두 값 추가 → `StringsRepository.toAppStrings()` 매핑 추가 → `MainViewModel.defaultStrings` 추가 (영어 기준) — 이 4곳을 반드시 동시에 수정
2. **새 언어 추가 금지**: 현재 4개 언어(EN/KO/JA/ZH)가 확정 목록. 추가 필요 시 가이드라인 개정 필요
3. **하드코딩 문자열 금지**: 모든 사용자 노출 텍스트는 `strings.*` 참조
4. **DB 스키마 변경 시**: `AppDatabase` 또는 `StringsDatabase` 버전 번호 올릴 것. 현재 각각 v3, v2
5. **시동어 변경 금지**: "start" / "stop" 영어 단어 고정. `VoiceCommandManager.START_KEYWORDS`, `STOP_KEYWORDS` 에서만 관리
6. **AudioManager 음소거**: Vosk 시작/종료 시 반드시 `muteAudioInput()` / `unmuteAudioInput()` 쌍으로 처리
7. **Compose Lint 준수**: 모든 신규 Composable 작성 및 기존 코드 수정 시 섹션 8의 체크리스트를 반드시 확인. 특히 아래 항목은 자주 누락되므로 주의:
  - 모든 UI Composable에 `modifier: Modifier = Modifier` 선언 및 루트 전달
  - `remember` 없이 `mutableStateOf` 단독 사용 금지
  - `Scaffold` padding 반드시 내부 콘텐츠에 적용
  - `InfiniteTransition` / `animateFloat` 등 애니메이션 API에 `label` 필수 지정
  - `LinearProgressIndicator`는 람다 형식 progress 사용
8. **배너 광고**: DashboardScreen의 빈 상태 Box에 `fillMaxSize()` 금지 (`weight(1f)` 사용)
9. **권한**: 신체활동은 필수, 마이크·알림은 선택. 포그라운드 서비스 타입은 허용된 권한에 맞춰 동적 지정 (2-11 참조)
10. **TTS 발화**: `tts?.speak()` 직접 호출 금지, 반드시 `speak()` 래퍼 사용 (볼륨 적용)
11. **패키지명**: `com.frankensosege.tmtforrunner` — 신규 파일의 package/import도 동일하게 작성
12. **AppDatabase 버전**: 엔티티 변경이 없으면 올리지 않는다 (운동 기록 보존)
13. **응원 멘트**: 안내 멘트(`announceCurrentTime`, `announceWorkout`)에 응원 문구를 붙이지 않는다. 응원은 `checkEncouragement()`에서만 발화하고, 빈도·사용 여부는 `talkative`·`talk_enabled`로만 제어한다
14. **TTS 큐 모드**: 안내·응원 멘트는 `QUEUE_ADD`를 사용한다. `QUEUE_FLUSH`는 시작/종료 멘트에만 사용 (재생 중인 음성을 끊기 때문)
15. **ACTION_START 설정값**: `isMale`, `isTalkative`는 인텐트 extra가 없으면 저장된 설정(`voice_male`, `talkative`)을 기본값으로 사용한다 (Start 버튼은 extra 없이 액션만 전달)
16. **응원·인사 문구 추가**: `StringsDatabase`의 4개 언어에 키만 이어서 추가한다 (`ttsEncouragementN`, `ttsWorkoutStartN`, `ttsWorkoutStopN` — N은 번호, 번호 없는 `ttsWorkoutStart/Stop`은 1번). `StringsRepository.collectNumbered()`가 번호 순으로 리스트로 수집하므로 `AppStrings` 수정은 필요 없고 `StringsDatabase` 버전만 올린다. 연속 같은 문구는 `pickDifferent()`로 피한다

### LLM 대화 기능 확장 준비 (TtsManager)

현재 `TtsManager`는 `ConversationProvider` 인터페이스 기반으로 설계되어 있음:
```kotlin
interface ConversationProvider {
    fun getMessage(steps: Int, distanceKm: Double, elapsedMin: Int, isTalkative: Boolean): String
}
// 현재: DefaultConversationProvider (하드코딩 문구 랜덤 선택)
// 확장: LlmConversationProvider (Claude API 연동) — TtsManager.kt 주석 참조
```
Claude API 연동 시 `TtsManager` 생성자의 `conversationProvider` 파라미터만 교체하면 됨.

### 운동 데이터 계산 공식 (현재 고정값, 추후 개인화 가능)

```
거리(km) = 
// 시간에 따른 동적 보폭 계산 예시
fun getDynamicStride(heightCm: Double, timeIntervalSeconds: Double): Double {
    val stepsPerSecond = 1.0 / timeIntervalSeconds
    
    return when {
        stepsPerSecond >= 2.5 -> (heightCm * 0.65) / 100.0 // 뛸 때 (보폭 크게)
        stepsPerSecond in 1.8..2.5 -> (heightCm * 0.50) / 100.0 // 빠르게 걸을 때
        else -> (heightCm * 0.415) / 100.0 // 보통 걸을 때
    }
}
칼로리   = 
fun calculateCalories(weightKg: Double, durationSeconds: Double, speedKmH: Double): Double {
    // 속도에 따른 MET 값 지정 (예시 데이터)
    val met = when {
        speedKmH < 3.0 -> 2.0
        speedKmH in 3.0..4.5 -> 3.0  // 보통 걷기
        speedKmH in 4.5..6.0 -> 3.8  // 빠르게 걷기
        else -> 5.0                  // 매우 빠르게 걷기 또는 가벼운 러닝
    }
    
    val durationMinutes = durationSeconds / 60.0
    
    // 칼로리 공식 = 0.0175 * MET * 체중(kg) * 시간(분)
    return 0.0175 * met * weightKg * durationMinutes
}
```
### 기능상 상충 발생 시

> 새 기능 개발 중 기존 가이드라인과 상충되는 경우, 반드시 먼저 질문하고 답변을 이 문서에 추가한 후 개발 진행.

---

## 10. 완료된 개발 이력

| 세션 | 완료 내용 |
|------|----------|
| 1차 | 기본 앱 구조, Room DB, Vosk 연동, 3개 화면(Running/Settings/Dashboard), 다국어 DB 구조, MPAndroidChart 그래프, Foreground Service |
| 2차 | 언어 4개로 제한, 한국어→영어 Vosk 모델, 시동어 "start"/"stop" 통일, AudioManager 음소거, ⚙️ 버튼 양 화면 추가, 버튼 텍스트 선택 언어 번역, 다운로드 진행률 다국어화, MicIndicator 다국어, TTS 다국어 로케일, 알림 텍스트 다국어화, 센서 상시 활성화, 운동 데이터 날짜 합산 저장 |
| 3차 | Slack Compose Lint 준수 원칙 가이드라인 추가 (섹션 8 전체), 25개 위반 항목 전수 수정 완료: modifier 파라미터 누락(10개), collectAsState→collectAsStateWithLifecycle(4개), remember 캐싱 누락(5개), contentDescription 누락(4개), @Deprecated API 교체(1개), early return 구조 개선(1개) |
| 4차 | RunningScreen 시간 표시 경과시간을 시:분(HH:MM) 형식으로 변경 (기존 mm:ss → HH:MM) / 자정 일자 변경 시 신체기록 자동 reset / 앱 재실행 시 오늘 DB 데이터 없으면 reset(checkTodayDataAndReset) / 앱 시작~종료 전 구간 신체기록 일단위 저장(5분 주기 DB 저장 + 분단위 포인트 상시 저장) / Start/Stop 버튼 역할 분리: 음성 기능(TTS 알람·대화)만 on/off, 센서 집계·저장은 독립 동작 / isVoiceActive 플래그 도입 |
| 5차 | 설정 화면에 키/몸무게 추가, 거리 계산을 시간당 속도 기반 동적 보폭(걸음 증분 누적, 단조증가)으로 변경, 칼로리를 MET × 몸무게 × 시간 공식으로 변경 |
| 6차 | 키/몸무게 입력을 드롭다운에서 숫자 입력 필드로 변경(범위 보정), RunningScreen 통계 버튼 텍스트를 `statsDashboard`로 변경, 재실행 시 오늘 DB 값을 베이스로 복원, 분 단위 포인트 Broadcast(`BROADCAST_MINUTE_POINT`) 도입 |
| 7차 | 앱 이름 표기를 만보이야기(TMT for Runner)로 변경(문서 한정) / 패키지명 `com.frankensosege.tmtforrunner`로 통일 / DashboardScreen 탭 2개(일별·월별) 개편: 일별=날짜 선택+당일 요약+정규화 HORIZONTAL_BEZIER 꺾은선, 월별=년·월 선택+일평균+정규화 stacked bar / ChartView.kt 교체(RunningLineChart·ChartTab 제거) / `WorkoutDao.getByMonth` 추가 / 다국어 문자열 추가, `StringsDatabase` v3 / 빈 상태 Box `weight(1f)`로 배너 고정 / 신체활동 필수 권한 다이얼로그, 포그라운드 서비스 타입 동적 지정(Android 14+ 크래시 방지), 서비스 시작 예외 보호 / RunningScreen 음성 볼륨 슬라이더(`tts_volume`), `speak()` 래퍼 도입 |
| 8차 | 응원 멘트를 안내 멘트와 분리해 대화 스타일별 빈도로 랜덤 발화(조용 10분 1~2회, 수다 10분 3~4회) / 대화 스타일 체크박스(`talk_enabled`) 추가, 해제 시 라디오 비활성화·응원 중지 / 안내 멘트에 붙던 응원 문구 제거 / Vosk 모델 다운로드 진행률 계산 수정(받은 바이트 기준, 0~100 제한) / R8 난독화·리소스 축소 활성화(`proguard-rules.pro`), Vosk·JNA·MPAndroidChart keep 규칙 / 시작 버튼 시 목소리·대화 설정을 저장값으로 복원 |