package com.frankensosege.tmtforrunner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.frankensosege.tmtforrunner.data.i18n.SupportedLanguage
import com.frankensosege.tmtforrunner.manager.AdManager
import com.frankensosege.tmtforrunner.model.NotifyInterval
import com.frankensosege.tmtforrunner.service.RunningForegroundService
import com.frankensosege.tmtforrunner.ui.screen.DashboardScreen
import com.frankensosege.tmtforrunner.ui.screen.RunningScreen
import com.frankensosege.tmtforrunner.ui.screen.SettingsScreen
import com.frankensosege.tmtforrunner.ui.theme.TMTforRunnerTheme
import com.frankensosege.tmtforrunner.viewmodel.MainViewModel
import com.frankensosege.tmtforrunner.viewmodel.RecordsState
import com.frankensosege.tmtforrunner.manager.InAppUpdateManager
import android.app.AlertDialog
import android.net.Uri
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.frankensosege.tmtforrunner.data.i18n.AppStrings

object Routes {
    const val DASHBOARD = "dashboard"
    const val RUNNING   = "running"
    const val SETTINGS  = "settings"
}

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_FINISH = "com.frankensosege.tmtforrunner.ACTION_FINISH"

        // 전면 광고 단위 ID (테스트/실제 분기)
        val INTERSTITIAL_AD_UNIT_ID = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/1033173712"        // 공식 테스트 ID
        else
            "ca-app-pub-2733222333675520/3620860355"        // ← 발급받은 ID로 교체
    }

    private val viewModel: MainViewModel by viewModels()

    private var permissionDialog: AlertDialog? = null
    private var waitingForSettings = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { handlePermissionResult() }

    private lateinit var updateManager: InAppUpdateManager

    // 음성 "start"로 시작될 때 전면 광고 표시 (화면이 보이는 동안만 수신)
    private val voiceStartAdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AdManager.showInterstitialIfReady(
                this@MainActivity, INTERSTITIAL_AD_UNIT_ID
            ) { /* 운동은 이미 시작됨 — 광고 후 추가 동작 없음 */ }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            voiceStartAdReceiver,
            IntentFilter(RunningForegroundService.BROADCAST_VOICE_START),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(voiceStartAdReceiver) } catch (_: Exception) {}
    }
    private var updateReady by mutableStateOf(false)    // 다운로드 완료 → 재시작 안내 표시

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_FINISH) finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.registerReceiver(this)
        updateManager = InAppUpdateManager(this) { updateReady = true }

        val prefs    = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        val langCode = prefs.getString("language_code", "en") ?: "en"
        val setupDone = prefs.getBoolean("setup_done", false)
        viewModel.loadLanguage(langCode)
        viewModel.setInitialSetupDone(setupDone)  // ← ViewModel에 전달

        // AdMob 초기화 (동의 처리 포함)
//        com.frankensosege.tmtforrunner.manager.AdManager.initialize(this) {
        AdManager.initialize(this) {
            Log.d("MainActivity", "광고 SDK 준비 완료")
            AdManager.loadInterstitial(this, INTERSTITIAL_AD_UNIT_ID)   // ✅ [추가] 앱 시작 시 미리 로드
        }

        checkAndRequestPermissions()

        setContent {
            TMTforRunnerTheme {
                // ✅ [Lint-Activity] setupDone을 Activity에서 읽어 Composable에 전달
                //    → Composable 내부에서 직접 SharedPreferences IO 접근 제거
                TMTApp(
                    viewModel  = viewModel
                )
                if (updateReady) {
                    val strings by viewModel.strings.collectAsStateWithLifecycle()
                    UpdateReadyDialog(
                        strings   = strings,
                        onRestart = {
                            updateReady = false
                            updateManager.completeUpdate()
                        },
                        onLater   = { updateReady = false }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.unregister()
        viewModel.unregisterReceiver(this)
        permissionDialog?.dismiss()
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACTIVITY_RECOGNITION))
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS))
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasPermission(Manifest.permission.RECORD_AUDIO))
            needed.add(Manifest.permission.RECORD_AUDIO)

        if (needed.isEmpty()) startRunningService()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    /** 신체활동 권한은 Android 10(Q)부터 런타임 권한. 그 미만은 항상 통과 */
    private fun isActivityRecognitionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)

    private fun handlePermissionResult() {
        if (isActivityRecognitionGranted()) {
            startRunningService()
        } else {
            showRequiredPermissionDialog()
        }
    }

    private fun showRequiredPermissionDialog() {
        if (isFinishing || isDestroyed) return
        if (permissionDialog?.isShowing == true) return

        val s = viewModel.strings.value
        // true: 시스템 권한 창을 다시 띄울 수 있음 / false: 영구 거부 → 설정 화면으로 안내
        val canAskAgain = shouldShowRequestPermissionRationale(
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        permissionDialog = AlertDialog.Builder(this)
            .setTitle(s.permRequiredTitle)
            .setMessage(if (canAskAgain) s.permRequiredMsg else s.permSettingsMsg)
            .setCancelable(false)
            .setPositiveButton(if (canAskAgain) s.confirm else s.permOpenSettings) { _, _ ->
                if (canAskAgain) {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
                    )
                } else {
                    waitingForSettings = true
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateManager.checkForUpdate()
        // 설정 화면에서 돌아온 경우 권한 재검사
        if (waitingForSettings) {
            waitingForSettings = false
            handlePermissionResult()
        }
    }

    private fun startRunningService() {
        val prefs  = getSharedPreferences("tmt_settings", MODE_PRIVATE)
        val intent = Intent(this, RunningForegroundService::class.java).apply {
            putExtra(RunningForegroundService.EXTRA_IS_MALE,   prefs.getBoolean("voice_male", false))
            putExtra(RunningForegroundService.EXTRA_TALKATIVE, prefs.getBoolean("talkative", false))
        }
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "서비스 시작 실패: ${e.message}")
        }
    }
}

// ── Lint 수정 목록 ────────────────────────────────────────
// ✅ [Lint-1]  TMTApp → modifier: Modifier = Modifier 추가
// ✅ [Lint-2]  SplashScreen → modifier: Modifier = Modifier 추가
// ✅ [Lint-3]  setupDone 파라미터로 수신 — Composable 내 SharedPreferences 직접 접근 제거
// ✅ [Lint-4]  composable 블록 내 prefs IO 접근 → ViewModel의 currentLanguage 활용
//             설정값(isMale 등)은 SettingsViewModel 이관이 이상적이나,
//             현재 구조에서는 context.getSharedPreferences 를 remember로 감싸서
//             리컴포지션마다 재접근하지 않도록 처리
// ✅ [Lint-5]  NavHost 내 composable 블록 안 prefs 접근 → rememberUpdatedState 활용

@Composable
fun TMTApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val navController = rememberNavController()
    // ✅ [수정] runningState 구독을 TMTApp에서 제거
    //          → 각 화면 composable 블록 안으로 이동
    val recordsState  by viewModel.recordsState.collectAsStateWithLifecycle()
    val strings       by viewModel.strings.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    val setupDone     by viewModel.setupDone.collectAsStateWithLifecycle()

    if (recordsState is RecordsState.Loading) {
        SplashScreen(
            modifier    = modifier,
            appName     = strings.appName,
            loadingText = strings.loading
        )
        return
    }

    val startDest = if (setupDone) Routes.RUNNING else Routes.SETTINGS

    NavHost(
        navController    = navController,
        startDestination = startDest,
        modifier         = modifier
    ) {

        // ── 대시보드 ──────────────────────────────────────
        composable(Routes.DASHBOARD) {
//            Log.d("MainActivity", "DASHBOARD composable 블록 진입")
            // ✅ [수정] DashboardScreen에서만 필요한 값 구독
            //          isListening/isRunning/isModelLoading만 추출
            //          → elapsedSec 변경에 의한 리컴포지션 차단
            val isListening    by remember {
                derivedStateOf { viewModel.state.value.isListening }
            }
            val isRunning      by remember {
                derivedStateOf { viewModel.state.value.isRunning }
            }
            val isModelLoading by remember {
                derivedStateOf { viewModel.state.value.isModelLoading }
            }

            DashboardScreen(
                viewModel            = viewModel,
                isListening          = isListening,
                isRunning            = isRunning,
                isModelLoading       = isModelLoading,
                strings              = strings,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToRunning  = { navController.navigate(Routes.RUNNING) }
            )
        }

        // ── 운동 중 ───────────────────────────────────────
        composable(Routes.RUNNING) {
            val runningState by viewModel.state.collectAsStateWithLifecycle()
            val ttsVolume    by viewModel.ttsVolume.collectAsStateWithLifecycle()   // 추가

            RunningScreen(
                state                  = runningState,
                strings                = strings,
                ttsVolume              = ttsVolume,                              // 추가
                onVolumeChange         = { viewModel.setTtsVolume(it) },         // 추가
                onVolumeChangeFinished = { viewModel.saveTtsVolume() },          // 추가
                // 이하 기존 인자 그대로
                onStartClick          = {
                    // 전면 광고 표시 후 원래 동작 실행
                    val activity = context as? Activity
                    val startAction: () -> Unit = {
                        context.startService(
                            Intent(context, RunningForegroundService::class.java).apply {
                                action = RunningForegroundService.ACTION_START
                            }
                        )
                    }

                    if (activity != null) {
                        AdManager.showInterstitialIfReady(activity, MainActivity.INTERSTITIAL_AD_UNIT_ID, startAction)
                    } else {
                        startAction()
                    }
                },
                onStopClick           = {
                    context.startService(
                        Intent(context, RunningForegroundService::class.java).apply {
                            action = RunningForegroundService.ACTION_STOP
                        }
                    )

                },
                onNavigateToSettings  = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDashboard = {
                    val activity = context as? Activity
                        val goToDashboard: () -> Unit = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                    }

                    if (activity != null) {
                        AdManager.showInterstitialIfReady(
                            activity,
                            MainActivity.INTERSTITIAL_AD_UNIT_ID,
                            goToDashboard
                        )
                    } else {
                        goToDashboard()
                    }
                },
                onQuit = {
                    context.startService(
                        Intent(context, RunningForegroundService::class.java).apply {
                            action = RunningForegroundService.ACTION_QUIT
                        }
                    )
                }
            )
        }

        // ── 설정 (변경 없음) ──────────────────────────────
        composable(Routes.SETTINGS) {
            val prefs = remember(context) {
                context.getSharedPreferences("tmt_settings", android.content.Context.MODE_PRIVATE)
            }
            val savedHeight = prefs.getInt("height_cm", 170)
            val savedWeight = prefs.getInt("weight_kg", 65)
            val initialIsMale by remember(prefs) {
                mutableStateOf(prefs.getBoolean("voice_male", false))
            }
            val initialIsTalkative by remember(prefs) {
                mutableStateOf(prefs.getBoolean("talkative", false))
            }
            val initialTalkEnabled by remember(prefs) {
                mutableStateOf(prefs.getBoolean("talk_enabled", true))
            }
            val initialTimeInterval by remember(prefs) {
                mutableStateOf(
                    NotifyInterval.fromPrefKey(
                        prefs.getString(RunningForegroundService.PREF_TIME_INTERVAL, "10") ?: "10"
                    )
                )
            }
            val initialWorkoutInterval by remember(prefs) {
                mutableStateOf(
                    NotifyInterval.fromPrefKey(
                        prefs.getString(RunningForegroundService.PREF_WORKOUT_INTERVAL, "5") ?: "5"
                    )
                )
            }

            SettingsScreen(
                strings                = strings,
                initialIsMale          = initialIsMale,
                initialIsTalkative     = initialIsTalkative,
                initialTimeInterval    = initialTimeInterval,
                initialWorkoutInterval = initialWorkoutInterval,
                initialTalkEnabled     = initialTalkEnabled,
                initialLanguage        = SupportedLanguage.fromCode(
                    prefs.getString("language_code", "en") ?: "en"
                ),
                initialHeightCm        = savedHeight,
                initialWeightKg        = savedWeight,
                onLanguageChange       = { lang -> viewModel.changeLanguage(lang) },
                onSave = { isMale, isTalkative, talkEnabled, timeInterval, workoutInterval, language, heightCm, weightKg ->
                    prefs.edit()
                        .putBoolean("voice_male",  isMale)
                        .putBoolean("talkative",   isTalkative)
                        .putBoolean("talk_enabled", talkEnabled)
                        .putBoolean("setup_done",  true)
                        .putString(RunningForegroundService.PREF_TIME_INTERVAL,    timeInterval.prefKey)
                        .putString(RunningForegroundService.PREF_WORKOUT_INTERVAL, workoutInterval.prefKey)
                        .putString("language_code", language.languageCode)
                        .putInt("height_cm", heightCm)
                        .putInt("weight_kg", weightKg)
                        .apply()
                    viewModel.markSetupDone()
                    navController.navigate(Routes.RUNNING) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                }
            )
        }
    }
}

// ✅ [Lint-2] modifier 파라미터 추가 및 루트 Box에 전달
@Composable
private fun SplashScreen(
    modifier: Modifier = Modifier,          // ✅ [Lint-2]
    appName: String,
    loadingText: String
) {
    Box(
        modifier         = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                appName,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                loadingText,
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun UpdateReadyDialog(
    strings: AppStrings,
    onRestart: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier         = modifier,
        onDismissRequest = onLater,
        title            = { Text(strings.appName) },
        text             = { Text(strings.updateReadyMsg) },
        confirmButton    = { TextButton(onClick = onRestart) { Text(strings.updateRestart) } },
        dismissButton    = { TextButton(onClick = onLater) { Text(strings.updateLater) } }
    )
}