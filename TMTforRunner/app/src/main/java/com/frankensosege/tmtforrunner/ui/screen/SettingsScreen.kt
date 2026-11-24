package com.frankensosege.tmtforrunner.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frankensosege.tmtforrunner.data.i18n.AppStrings
import com.frankensosege.tmtforrunner.data.i18n.SupportedLanguage
import com.frankensosege.tmtforrunner.model.NotifyInterval
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// ── Lint 수정 목록 ────────────────────────────────────────
// ✅ [Lint-1]  modifier: Modifier = Modifier 추가 (모든 Composable)
// ✅ [Lint-2]  intervalLabels → remember(strings) { } 로 캐싱
// ✅ [Lint-3]  languageOptions → remember { } 로 캐싱
// ✅ [5차 추가] heightCm, weightKg 파라미터 추가
// ✅ [6차 변경] 키/몸무게 드롭다운 → 숫자 입력 필드(OutlinedTextField)로 변경
//             - heightOptions / weightOptions 리스트 제거
//             - KeyboardType.Number로 숫자 키패드만 허용
//             - 포커스 해제 시 범위 벗어난 값 자동 보정 (clamp)
//               키: 100~250 cm / 몸무게: 30~150 kg
//             - 저장 버튼 클릭 시에도 동일 범위 보정 적용

private const val HEIGHT_MIN = 100
private const val HEIGHT_MAX = 250
private const val WEIGHT_MIN = 30
private const val WEIGHT_MAX = 150

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,                 // ✅ [Lint-1]
    strings: AppStrings,
    initialIsMale: Boolean,
    initialIsTalkative: Boolean,
    initialTimeInterval: NotifyInterval,
    initialWorkoutInterval: NotifyInterval,
    initialLanguage: SupportedLanguage,
    initialHeightCm: Int,
    initialWeightKg: Int,
    initialTalkEnabled: Boolean,
    onLanguageChange: (SupportedLanguage) -> Unit,
    onSave: (
        isMale: Boolean,
        isTalkative: Boolean,
        talkEnabled: Boolean,
        timeInterval: NotifyInterval,
        workoutInterval: NotifyInterval,
        language: SupportedLanguage,
        heightCm: Int,
        weightKg: Int
    ) -> Unit
) {
    var isMale          by remember { mutableStateOf(initialIsMale) }
    var isTalkative     by remember { mutableStateOf(initialIsTalkative) }
    var talkEnabled     by remember { mutableStateOf(initialTalkEnabled) }
    var timeInterval    by remember { mutableStateOf(initialTimeInterval) }
    var workoutInterval by remember { mutableStateOf(initialWorkoutInterval) }
    var language        by remember { mutableStateOf(initialLanguage) }
    var heightCm        by remember { mutableIntStateOf(initialHeightCm) }
    var weightKg        by remember { mutableIntStateOf(initialWeightKg) }

    // ✅ [6차 변경] 입력 필드에 표시할 문자열 상태.
    //             숫자 외 문자는 입력 자체를 차단하고,
    //             포커스가 벗어날 때 범위를 벗어난 경우 보정 후 Int 상태에 반영한다.
    var heightText by remember { mutableStateOf(initialHeightCm.toString()) }
    var weightText by remember { mutableStateOf(initialWeightKg.toString()) }

    // ✅ [Lint-2] strings가 변경될 때만 재생성
    val intervalLabels = remember(strings) {
        listOf(
            strings.intervalRandom to NotifyInterval.RANDOM,
            strings.interval5min   to NotifyInterval.MIN_5,
            strings.interval10min  to NotifyInterval.MIN_10,
            strings.interval15min  to NotifyInterval.MIN_15,
            strings.interval20min  to NotifyInterval.MIN_20,
        )
    }

    // ✅ [Lint-3] 언어 목록은 정적 → 1회만 생성
    val languageOptions = remember {
        SupportedLanguage.entries.map { it.displayName to it }
    }

    Column(
        modifier            = modifier                          // ✅ [Lint-1] 루트에 전달
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 헤더 ──────────────────────────────────────────
        Text(
            strings.appName,
            fontWeight = FontWeight.Bold,
            fontSize   = 26.sp,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            strings.settingsSubtitle,
            fontSize = 15.sp,
            color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ── 언어 선택 ─────────────────────────────────────
        SettingSection(title = strings.countrySelection) {
            DropdownSelector(
                label    = language.displayName,
                options  = languageOptions,
                selected = language,
                onSelect = { selected ->
                    language = selected
                    onLanguageChange(selected)
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ── 목소리 선택 ───────────────────────────────────
        SettingSection(title = strings.voiceSelection) {
            Row(
                modifier              = Modifier
                    .selectableGroup()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioItem(
                    label    = strings.voiceFemale,
                    selected = !isMale,
                    onClick  = { isMale = false }
                )
                RadioItem(
                    label    = strings.voiceMale,
                    selected = isMale,
                    onClick  = { isMale = true }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 대화 스타일 (제목 옆 체크박스로 응원 멘트 on/off) ──
        val talkStyleDesc = strings.talkStyle
        SettingSection(
            title       = strings.talkStyle,
            titleAction = {
                Checkbox(
                    checked         = talkEnabled,
                    onCheckedChange = { talkEnabled = it },
                    modifier        = Modifier.semantics { contentDescription = talkStyleDesc }
                )
            }
        ) {
            Row(
                modifier              = Modifier
                    .selectableGroup()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioItem(
                    label    = strings.talkQuiet,
                    selected = !isTalkative,
                    enabled  = talkEnabled,
                    onClick  = { isTalkative = false }
                )
                RadioItem(
                    label    = strings.talkChattily,
                    selected = isTalkative,
                    enabled  = talkEnabled,
                    onClick  = { isTalkative = true }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ── [6차 변경] 신체 정보 — 키 / 몸무게 (숫자 입력 필드) ──
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 키 입력 필드
            SettingSection(
                title    = strings.heightLabel,
                modifier = Modifier.weight(1f)
            ) {
                NumberInputField(
                    value         = heightText,
                    unitLabel     = "cm",
                    onValueChange = { input ->
                        // 숫자만 허용, 최대 3자리
                        if (input.length <= 3 && input.all { it.isDigit() }) {
                            heightText = input
                        }
                    },
                    onFocusLost = {
                        val corrected = (heightText.toIntOrNull() ?: heightCm)
                            .coerceIn(HEIGHT_MIN, HEIGHT_MAX)
                        heightCm   = corrected
                        heightText = corrected.toString()
                    }
                )
            }

            // 몸무게 입력 필드
            SettingSection(
                title    = strings.weightLabel,
                modifier = Modifier.weight(1f)
            ) {
                NumberInputField(
                    value         = weightText,
                    unitLabel     = "kg",
                    onValueChange = { input ->
                        // 숫자만 허용, 최대 3자리
                        if (input.length <= 3 && input.all { it.isDigit() }) {
                            weightText = input
                        }
                    },
                    onFocusLost = {
                        val corrected = (weightText.toIntOrNull() ?: weightKg)
                            .coerceIn(WEIGHT_MIN, WEIGHT_MAX)
                        weightKg   = corrected
                        weightText = corrected.toString()
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ── 현재시간 알림 주기 ────────────────────────────
        SettingSection(
            title    = strings.timeNotifyInterval,
            subtitle = strings.timeNotifySubtitle
        ) {
            DropdownSelector(
                label    = intervalLabels.first { it.second == timeInterval }.first,
                options  = intervalLabels,
                selected = timeInterval,
                onSelect = { timeInterval = it }
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── 운동량 알림 주기 ──────────────────────────────
        SettingSection(
            title    = strings.workoutNotifyInterval,
            subtitle = strings.workoutNotifySubtitle
        ) {
            DropdownSelector(
                label    = intervalLabels.first { it.second == workoutInterval }.first,
                options  = intervalLabels,
                selected = workoutInterval,
                onSelect = { workoutInterval = it }
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ── 기능 안내 카드 ────────────────────────────────
        Surface(
            color    = MaterialTheme.colorScheme.secondaryContainer,
            shape    = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                InfoRow(emoji = "🎤", text = strings.infoVoiceStart)
                InfoRow(emoji = "🛑", text = strings.infoVoiceStop)
                InfoRow(emoji = "🕐", text = strings.infoTimeAnnounce)
                InfoRow(emoji = "🏃", text = strings.infoWorkoutAnnounce)
                InfoRow(emoji = "📱", text = strings.infoBackground)
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── 저장 버튼 ─────────────────────────────────────
        Button(
            onClick  = {
                // ✅ [6차 변경] 저장 직전, 포커스가 남아있어도 범위 보정 후 저장
                val finalHeight = (heightText.toIntOrNull() ?: heightCm).coerceIn(HEIGHT_MIN, HEIGHT_MAX)
                val finalWeight = (weightText.toIntOrNull() ?: weightKg).coerceIn(WEIGHT_MIN, WEIGHT_MAX)
                heightCm   = finalHeight
                weightKg   = finalWeight
                onSave(isMale, isTalkative, talkEnabled, timeInterval, workoutInterval, language, finalHeight, finalWeight)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(strings.saveAndStart, fontSize = 16.sp)
        }
    }
}

// ── [6차 추가] 숫자 전용 입력 필드 ─────────────────────────
// Slack Compose Lint 준수:
//   - modifier: Modifier = Modifier 선언 및 루트 전달
//   - wasFocused 상태는 remember로 감싸 리컴포지션 안전 보장
//   - onFocusChanged 를 Modifier 체인에 직접 적용 (별도 확장함수 불필요)
@Composable
private fun NumberInputField(
    value: String,
    unitLabel: String,
    onValueChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier    // ✅ [Lint-1]
) {
    // 직전 포커스 상태를 추적하여 "포커스를 잃는 순간"만 감지
    var wasFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused) {
                    onFocusLost()
                }
                wasFocused = focusState.isFocused
            },
        singleLine      = true,
        suffix          = { Text(unitLabel, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

// ── Dropdown 선택 컴포넌트 ────────────────────────────────
@Composable
private fun <T> DropdownSelector(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier    // ✅ [Lint-1]
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {       // ✅ [Lint-1] 루트 Box에 전달
        OutlinedButton(
            onClick  = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text("▼", fontSize = 12.sp)
        }

        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            modifier         = Modifier.fillMaxWidth(0.85f)
        ) {
            options.forEach { (displayLabel, value) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (value == selected) {
                                Text("✓ ", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            } else {
                                Spacer(Modifier.width(20.dp))
                            }
                            Text(displayLabel, fontSize = 14.sp)
                        }
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── 공통 컴포넌트 ─────────────────────────────────────────

// ✅ [Lint-1] modifier 파라미터 추가
@Composable
private fun RadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)   // null로 부모에 위임
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 15.sp,
            color    = if (enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
        )
    }
}

// ✅ [Lint-1] modifier 파라미터 추가
@Composable
private fun SettingSection(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {   // 변경: 제목을 Row로 감쌈
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            titleAction?.invoke()
        }
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

// ✅ [Lint-1] modifier 파라미터 추가
@Composable
private fun InfoRow(
    emoji: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier          = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}