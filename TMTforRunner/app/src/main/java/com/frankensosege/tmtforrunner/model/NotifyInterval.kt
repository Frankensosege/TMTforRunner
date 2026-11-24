package com.frankensosege.tmtforrunner.model

/**
 * 알림 주기 설정값
 *
 * RANDOM : 2~5분 사이 무작위
 * MIN_5  : 정확히 5분마다
 * MIN_10 : 정확히 10분마다
 * MIN_15 : 정확히 15분마다
 * MIN_20 : 정확히 20분마다
 */
enum class NotifyInterval(val label: String, val prefKey: String) {
    RANDOM("무작위",  "random"),
    MIN_5 ("5분",    "5"),
    MIN_10("10분",   "10"),
    MIN_15("15분",   "15"),
    MIN_20("20분",   "20");

    companion object {
        fun fromPrefKey(key: String): NotifyInterval =
            entries.firstOrNull { it.prefKey == key } ?: MIN_10

        // 실제 알림을 울려야 하는지 판단
        // elapsedMin : 현재 경과 분
        // lastMin    : 마지막으로 알림을 울린 분 (-1 = 아직 없음)
        // randomNextMin : RANDOM 모드일 때 다음 알림 분
        fun shouldAnnounce(
            interval: NotifyInterval,
            elapsedMin: Int,
            lastMin: Int,
            randomNextMin: Int
        ): Boolean {
            if (elapsedMin <= 0 || elapsedMin == lastMin) return false
            return when (interval) {
                RANDOM -> elapsedMin >= randomNextMin
                MIN_5  -> elapsedMin > 0 && elapsedMin % 5  == 0
                MIN_10 -> elapsedMin > 0 && elapsedMin % 10 == 0
                MIN_15 -> elapsedMin > 0 && elapsedMin % 15 == 0
                MIN_20 -> elapsedMin > 0 && elapsedMin % 20 == 0
            }
        }

        // RANDOM 모드: 다음 알림까지의 분 계산 (2~5분 랜덤)
        fun nextRandomMin(currentMin: Int): Int =
            currentMin + (2..5).random()
    }
}
