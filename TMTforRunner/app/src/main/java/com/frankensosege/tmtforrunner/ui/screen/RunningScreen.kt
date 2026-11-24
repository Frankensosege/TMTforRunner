package com.frankensosege.tmtforrunner.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.frankensosege.tmtforrunner.R
import com.frankensosege.tmtforrunner.data.i18n.AppStrings
import com.frankensosege.tmtforrunner.ui.component.AdBanner
import com.frankensosege.tmtforrunner.ui.component.MicIndicator
import com.frankensosege.tmtforrunner.viewmodel.RunningState
import com.frankensosege.tmtforrunner.BuildConfig
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import com.frankensosege.tmtforrunner.ui.component.MediumRectangleAdBanner

// ── Lint 준수 항목 (3차 세션 완료, 유지) ──────────────────
// ✅ [Lint-1]  modifier: Modifier = Modifier 파라미터 추가 (루트 Box에 전달)
// ✅ [Lint-2]  미사용 onBack 파라미터 제거
// ✅ [Lint-3]  ⚙️ IconButton semantics { contentDescription } 추가
// ✅ [Lint-4]  Button onClick = if(running) onStopClick else onStartClick
// ✅ [Lint-5]  StatCard → modifier: Modifier = Modifier 파라미터 추가
// ✅ [Lint-6]  LinearProgressIndicator → 람다 형식 progress (Compose 1.6+)
// ── 4차 변경 사항 (유지) ──────────────────────────────────
// [변경 1] 시간 표시: 경과시간 시:분(HH:MM) — state.timerText 사용
// [변경 4] Start/Stop 버튼: 음성 기능(isVoiceActive = state.isRunning)만 on/off
// ── 6차 변경 사항 ─────────────────────────────────────────
// [변경 6] Dashboard 버튼 텍스트: strings.dashboardTitle("TMT for Runner")
//          → strings.statsDashboard("통계(그래프)") 로 변경

@Composable
fun RunningScreen(
    modifier: Modifier            = Modifier,         // ✅ [Lint-1]
    state: RunningState,
    strings: AppStrings,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    ttsVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onQuit: () -> Unit
) {
    var showQuitDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier                           // ✅ [Lint-1] 루트에 전달
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 배경 워터마크 (신규 추가) ──────────────────
        Image(
            painter = painterResource(id = R.drawable.bg_watermark_zombie_run),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentScale = ContentScale.Fit
        )

        // ── 좌측 상단 ⚙️ 설정 버튼 ─────────────────────────
        val settingsDesc = strings.settings
        IconButton(
            onClick  = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .semantics { contentDescription = settingsDesc }  // ✅ [Lint-3]
        ) {
            Text("⚙️", fontSize = 24.sp)
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── 앱 이름 ────────────────────────────────────
            Text(
                text       = strings.appName,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            // ── 경과시간 시:분(HH:MM) 표시 [변경 1] ───────
            Text(
                text       = state.timerText,
                fontSize   = 64.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(22.dp))

            // ── 운동 통계 카드 ─────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    label = strings.steps,
                    value = "${state.steps} ${strings.unitSteps}",
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    label = strings.distance,
                    value = "${"%.2f".format(state.distanceKm)} ${strings.unitKm}",
                    color = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    label = strings.calories,
                    value = "${state.calories} ${strings.unitKcal}",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── 운동 중 배지 ───────────────────────────────
            AnimatedVisibility(
                visible = state.isRunning,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text     = strings.workingOut,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(15.dp))

            // ── 다운로드/로딩 진행률 ──────────────────────
            if (state.isModelLoading) {
                Text(
                    text = if (state.downloadProgress >= 0)
                        "${strings.modelDownloading} ${state.downloadProgress}%"
                    else
                        strings.modelLoading,
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                if (state.downloadProgress >= 0) {
                    // ✅ [Lint-6] 람다 형식 progress (Compose 1.6+ API)
                    LinearProgressIndicator(
                        progress = { state.downloadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── 마이크 상태 [가이드라인 2-3] ──────────────
            MicIndicator(
                isListening    = state.isListening,
                isRunning      = state.isRunning,
                isModelLoading = state.isModelLoading,
                labelListening = strings.micListening,
                labelWaitStop  = strings.micWaitStop,
                labelWaitStart = strings.micWaitStart,
                labelLoading   = strings.modelLoading
            )

            Spacer(Modifier.height(8.dp))

            // ── 안내 음성 볼륨 슬라이더 ──────────────────
            val volumeDesc = strings.ttsVolumeLabel
            Row(
                modifier          = Modifier.width(220.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔊", fontSize = 16.sp)
                Slider(
                    value                 = ttsVolume,
                    onValueChange         = onVolumeChange,
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange            = 0f..1f,
                    modifier              = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .semantics { contentDescription = volumeDesc }
                )
                Text(
                    text      = "${(ttsVolume * 100).toInt()}%",
                    fontSize  = 12.sp,
                    textAlign = TextAlign.End,
                    modifier  = Modifier.width(36.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text     = strings.backgroundNotice,
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(8.dp))

            // ── Start/Stop 버튼 [변경 4] ──────────────────
            // 음성 기능(TTS 알람·대화)만 on/off
            // 버튼 색상: 음성 off=primary, 음성 on=tertiary [가이드라인 2-3]
            Button(
                onClick  = if (state.isRunning) onStopClick else onStartClick, // ✅ [Lint-4]
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRunning)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .width(220.dp)
                    .height(56.dp)
            ) {
                Text(
                    text       = if (state.isRunning) strings.stopWorkout else strings.startWorkout,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Dashboard(통계) 버튼 [가이드라인 2-3] [변경 6] ──
            OutlinedButton(
                onClick  = onNavigateToDashboard,
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
            ) {
                Text(text = strings.statsDashboard, fontSize = 15.sp)  // ✅ [변경 6]
            }

            Spacer(Modifier.height(12.dp))

            // ── 앱 종료 버튼 [가이드라인 2-3, 2-9] ────────
            OutlinedButton(
                onClick  = { showQuitDialog = true },
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
            ) {
                Text(text = strings.quitApp, fontSize = 15.sp)
            }

            Spacer(Modifier.height(12.dp))

            // 배너 광고
            val bannerAdUnitId = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/6300978111"       // 디버그 빌드 → 항상 테스트 ID
            else
                "ca-app-pub-2733222333675520/4072247539"        // 릴리스 빌드 → 실제 ID

            AdBanner(
//                adUnitId = "ca-app-pub-2733222333675520/4072247539",
                adUnitId = bannerAdUnitId,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (showQuitDialog) {
            // 종료 팝업용 배너 광고 ID (디버그는 테스트 ID)
            val quitBannerAdUnitId = remember {
                if (BuildConfig.DEBUG)
                    "ca-app-pub-3940256099942544/6300978111"       // 디버그 빌드 → 테스트 ID
                else
                    "ca-app-pub-2733222333675520/9478300034"        // 릴리스 빌드 → 종료 팝업 배너
            }

            AlertDialog(
                onDismissRequest = { showQuitDialog = false },
                // 300dp 광고가 들어가도록 기본 폭 제한 해제
                properties       = DialogProperties(usePlatformDefaultWidth = false),
                text             = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 광고는 위쪽, 버튼과는 메시지 텍스트로 간격 확보 (실수 클릭 방지)
                        MediumRectangleAdBanner(adUnitId = quitBannerAdUnitId)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = strings.quitConfirmMsg,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
               },
                confirmButton    = {
                    TextButton(
                        onClick = {
                            showQuitDialog = false
                            onQuit()
                        }
                    ) { Text(strings.confirm) }
                },
                dismissButton    = {
                    TextButton(onClick = { showQuitDialog = false }) { Text(strings.cancel) }
                }
            )
        }
    }
}

// ✅ [Lint-5] modifier 파라미터 추가 및 루트 Surface에 전달
@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = color.copy(alpha = 0.08f),
        modifier = modifier.width(100.dp)       // 외부 modifier 먼저, 내부 size 후
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = value,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = color
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = label,
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}