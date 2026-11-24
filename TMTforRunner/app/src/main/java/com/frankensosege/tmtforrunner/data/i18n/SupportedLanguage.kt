package com.frankensosege.tmtforrunner.data.i18n

/**
 * 지원 언어 목록 (가이드라인: 영어·한국어·일본어·중국어 4개로 제한)
 *
 * languageCode : ISO 639-1 언어 코드
 * countryCode  : ISO 3166-1 alpha-2 국가 코드
 * displayName  : 해당 언어로 표기한 언어명 (자국어 표기)
 * englishName  : 영문 언어명
 * ttsLocale    : Android TTS에 전달할 로케일 문자열 (BCP 47)
 *
 * [가이드라인 8] 한국어 선택 시 vosk 모델은 영어(en) 모델을 사용하며
 *               시동어는 "start" / "stop" 으로 고정.
 *               voskModelLang : 실제로 로드할 vosk 모델의 언어코드
 */
enum class SupportedLanguage(
    val languageCode: String,
    val countryCode: String,
    val displayName: String,
    val englishName: String,
    val ttsLocale: String,
    val voskModelLang: String   // vosk 모델 언어코드 (ko→en 로 매핑)
) {
    ENGLISH  ("en", "US", "English (United States)", "English (United States)", "en-US", "en"),
    KOREAN   ("ko", "KR", "한국어 (대한민국)",         "Korean (South Korea)",   "ko-KR", "en"), // 가이드라인8: ko→en 모델
    JAPANESE ("ja", "JP", "日本語 (日本)",             "Japanese (Japan)",        "ja-JP", "ja"),
    CHINESE  ("zh", "CN", "中文 (中国)",               "Chinese (China)",         "zh-CN", "zh");

    companion object {
        fun fromCode(code: String): SupportedLanguage =
            entries.firstOrNull { it.languageCode == code } ?: ENGLISH

        fun fromCountryCode(code: String): SupportedLanguage =
            entries.firstOrNull { it.countryCode == code } ?: ENGLISH
    }
}
