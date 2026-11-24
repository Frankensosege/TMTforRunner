package com.frankensosege.tmtforrunner.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Lint 수정 목록 ────────────────────────────────────────
// ✅ [Lint-1]  early return 제거 → 단일 Column으로 구조 통일하여
//             isModelLoading 분기 모두에서 modifier가 루트에 전달되도록 수정
//             (이전: isModelLoading 분기 Column은 modifier 전달, 이후 Column은 미전달)
// ✅ [Lint-2]  animateFloat label 이미 적용됨 (유지)
// ✅ [Lint-3]  modifier: Modifier = Modifier 이미 적용됨 (유지)

@Composable
fun MicIndicator(
    modifier: Modifier = Modifier,          // ✅ [Lint-3] 유지
    isListening: Boolean,
    isRunning: Boolean,
    isModelLoading: Boolean = false,
    labelListening: String  = "Listening...",
    labelWaitStop: String   = "Say 'stop'",
    labelWaitStart: String  = "Say 'start'",
    labelLoading: String    = "Loading voice model..."
) {
    // ✅ [Lint-1] early return 제거 — 모든 분기에서 동일한 루트 Column 사용
    //            → modifier 가 isModelLoading/정상 분기 모두에 동일하게 전달됨
    Column(
        modifier            = modifier,     // ✅ 루트 Column 에 항상 전달
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isModelLoading) {
            // ── 모델 로딩 중 ──────────────────────────────
            CircularProgressIndicator(
                modifier    = Modifier.size(48.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                labelLoading,
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            // ── 정상 대기/듣는 중 ─────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse") // ✅ [Lint-2]
            val scale by infiniteTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = if (isListening) 1.3f else 1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(700, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mic_scale"                                                  // ✅ [Lint-2]
            )

            val bgColor = when {
                isListening -> MaterialTheme.colorScheme.primary
                isRunning   -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                else        -> Color.Gray.copy(alpha = 0.4f)
            }

            val statusLabel = when {
                isListening -> labelListening
                isRunning   -> labelWaitStop
                else        -> labelWaitStart
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(if (isListening) scale else 1f)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🎤", fontSize = 24.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                statusLabel,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}
