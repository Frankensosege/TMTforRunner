package com.frankensosege.tmtforrunner.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

// ─────────────────────────────────────────────────────────
// TTS 관리 클래스
// LLM 확장 시: conversationProvider를
//   LlmConversationProvider(apiKey, userProfile) 로 교체
// ─────────────────────────────────────────────────────────
class TtsManager(
    context: Context,
    private val isMale: Boolean,
    private val isTalkative: Boolean,
    private val conversationProvider: ConversationProvider = DefaultConversationProvider()
) {
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.KOREAN
                tts.setPitch(if (isMale) 0.8f else 1.2f)
                tts.setSpeechRate(if (isTalkative) 1.1f else 0.95f)
            }
        }
    }

    fun speak(steps: Int, distanceKm: Double, elapsedMin: Int) {
        val text = conversationProvider.getMessage(
            steps = steps, distanceKm = distanceKm,
            elapsedMin = elapsedMin, isTalkative = isTalkative
        )
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "running_tts")
    }

    fun shutdown() {
        if (::tts.isInitialized) tts.shutdown()
    }
}

interface ConversationProvider {
    fun getMessage(steps: Int, distanceKm: Double, elapsedMin: Int, isTalkative: Boolean): String
}

class DefaultConversationProvider : ConversationProvider {
    private val quietPhrases = listOf<(Int, Double) -> String>(
        { steps, dist -> "${steps}걸음, ${"%.2f".format(dist)}킬로미터예요." },
        { steps, _    -> "지금까지 ${steps}걸음 걸으셨어요!" },
        { _,     dist -> "${"%.2f".format(dist)}킬로미터 달성! 잘 하고 있어요." }
    )
    private val chattyPhrases = listOf(
        "오늘 날씨 참 좋죠? 달리기엔 딱이에요!",
        "페이스 좋은데요? 이 정도면 마라톤도 문제없겠어요!",
        "물은 충분히 마셨나요? 수분 보충 잊지 마세요!",
        "달리다 보면 어느새 목적지예요, 조금만 더!",
        "오늘 목표 거리 거의 다 왔어요, 파이팅!"
    )

    override fun getMessage(steps: Int, distanceKm: Double, elapsedMin: Int, isTalkative: Boolean): String =
        if (isTalkative && Math.random() > 0.4) chattyPhrases.random()
        else quietPhrases.random()(steps, distanceKm)
}

// ─────────────────────────────────────────────────────────
// LLM 확장 구현체 — API 키 준비 후 주석 해제
// ─────────────────────────────────────────────────────────
/*
data class UserProfile(
    val name: String = "",
    val interests: List<String> = emptyList(),
    val tone: String = "친근한",
    val runningGoal: String = ""
)

class LlmConversationProvider(
    private val apiKey: String,
    private val userProfile: UserProfile
) : ConversationProvider {
    private val history = mutableListOf<Map<String, String>>()

    override fun getMessage(steps: Int, distanceKm: Double, elapsedMin: Int, isTalkative: Boolean): String {
        val prompt = """
            사용자: ${userProfile.name}, 관심사: ${userProfile.interests.joinToString()},
            말투: ${userProfile.tone}, 목표: ${userProfile.runningGoal}
            현재: ${steps}걸음 / ${"%.2f".format(distanceKm)}km / ${elapsedMin}분
            짧은 응원 또는 대화 한 문장을 한국어로 생성해줘.
        """.trimIndent()
        // TODO: Claude API 호출 (Retrofit / OkHttp)
        return "LLM 응답"
    }
}
*/
