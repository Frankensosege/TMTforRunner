package com.frankensosege.tmtforrunner.data.i18n

import android.content.Context

class StringsRepository(context: Context) {

    private val dao = StringsDatabase.getInstance(context).stringsDao()

    /** DB에 언어 데이터가 없으면 시드 후 반환 */
    suspend fun getStrings(lang: SupportedLanguage): AppStrings {
        if (dao.countForLanguage(lang.languageCode) == 0) {
            dao.insertAll(buildStringsMap(lang))
        }
        val rows = dao.getStrings(lang.languageCode)
        return rows.toAppStrings()
    }

    /** 모든 지원 언어 시드 (최초 1회) — 4개 언어만 */
    suspend fun seedAll() {
        SupportedLanguage.entries.forEach { lang ->
            if (dao.countForLanguage(lang.languageCode) == 0) {
                dao.insertAll(buildStringsMap(lang))
            }
        }
    }

    /** prefix, prefix2, prefix3 ... 를 번호 순으로 수집 (번호 없는 키는 1번) */
    private fun Map<String, String>.collectNumbered(prefix: String): List<String> {
        val regex = Regex("${Regex.escape(prefix)}(\\d*)")
        return entries
            .mapNotNull { (k, v) ->
                regex.matchEntire(k)?.let { (it.groupValues[1].toIntOrNull() ?: 1) to v.trim() }
            }
            .sortedBy { it.first }
            .map { it.second }
    }
    private fun List<StringEntity>.toAppStrings(): AppStrings {
        val m = associate { it.stringKey to it.value }
        fun s(key: String) = m[key] ?: key   // 키 없으면 키 자체 반환 (fallback)

        val encouragements = m.collectNumbered("ttsEncouragement")
        val startGreetings = m.collectNumbered("ttsWorkoutStart")
        val stopGreetings  = m.collectNumbered("ttsWorkoutStop")
        val greetMorning   = m.collectNumbered("ttsGreetingMorning")
        val greetAfternoon = m.collectNumbered("ttsGreetingAfternoon")
        val greetEvening   = m.collectNumbered("ttsGreetingEvening")
        val greetNight     = m.collectNumbered("ttsGreetingNight")

        return AppStrings(
            appName                = s("appName"),
            save                   = s("save"),
            loading                = s("loading"),
            quitApp                = s("quitApp"),
            quitConfirmMsg         = s("quitConfirmMsg"),
            dashboardTitle         = s("dashboardTitle"),
            statsDashboard         = s("statsDashboard"),
            tabDaily               = s("tabDaily"),
            tabMonthly             = s("tabMonthly"),
            avgSpeed               = s("avgSpeed"),
            unitKmh                = s("unitKmh"),
            yearLabel              = s("yearLabel"),
            monthLabel             = s("monthLabel"),
            confirm                = s("confirm"),
            cancel                 = s("cancel"),
            noMonthRecordsMsg      = s("noMonthRecordsMsg"),
            normalizedNote         = s("normalizedNote"),
            dailyAvgTitle          = s("dailyAvgTitle"),
            settings               = s("settings"),
            chartSteps             = s("chartSteps"),
            chartDistance          = s("chartDistance"),
            chartCalories          = s("chartCalories"),
            noRecordsMsg           = s("noRecordsMsg"),
            running                = s("running"),
            steps                  = s("steps"),
            distance               = s("distance"),
            calories               = s("calories"),
            backgroundNotice       = s("backgroundNotice"),
            stopWorkout            = s("stopWorkout"),
            startWorkout           = s("startWorkout"),
            workingOut             = s("workingOut"),
            settingsTitle          = s("settingsTitle"),
            settingsSubtitle       = s("settingsSubtitle"),
            voiceSelection         = s("voiceSelection"),
            voiceFemale            = s("voiceFemale"),
            voiceMale              = s("voiceMale"),
            talkStyle              = s("talkStyle"),
            talkQuiet              = s("talkQuiet"),
            talkChattily           = s("talkChattily"),
            timeNotifyInterval     = s("timeNotifyInterval"),
            timeNotifySubtitle     = s("timeNotifySubtitle"),
            workoutNotifyInterval  = s("workoutNotifyInterval"),
            workoutNotifySubtitle  = s("workoutNotifySubtitle"),
            countrySelection       = s("countrySelection"),
            saveAndStart           = s("saveAndStart"),
            // ── [5차 추가] 신체 정보 ──────────────────────
            heightLabel            = s("heightLabel"),
            weightLabel            = s("weightLabel"),
            // ─────────────────────────────────────────────
            intervalRandom         = s("intervalRandom"),
            interval5min           = s("interval5min"),
            interval10min          = s("interval10min"),
            interval15min          = s("interval15min"),
            interval20min          = s("interval20min"),
            infoVoiceStart         = s("infoVoiceStart"),
            infoVoiceStop          = s("infoVoiceStop"),
            infoTimeAnnounce       = s("infoTimeAnnounce"),
            infoWorkoutAnnounce    = s("infoWorkoutAnnounce"),
            infoBackground         = s("infoBackground"),
            micListening           = s("micListening"),
            micWaitStop            = s("micWaitStop"),
            micWaitStart           = s("micWaitStart"),
            modelDownloading       = s("modelDownloading"),
            modelLoading           = s("modelLoading"),
            modelReady             = s("modelReady"),
            ttsWorkoutStart        = s("ttsWorkoutStart"),
            ttsWorkoutStop         = s("ttsWorkoutStop"),
            ttsAmPm                = s("ttsAmPm"),
            ttsPmPm                = s("ttsPmPm"),
            ttsTimeFormat          = s("ttsTimeFormat"),
            ttsWorkoutFormat       = s("ttsWorkoutFormat"),
            ttsEncouragements      = encouragements,
            ttsStartGreetings      = startGreetings,
            ttsStopGreetings       = stopGreetings,
            ttsGreetingMorning     = greetMorning,
            ttsGreetingAfternoon   = greetAfternoon,
            ttsGreetingEvening     = greetEvening,
            ttsGreetingNight       = greetNight,
            ttsVolumeLabel         = s("ttsVolumeLabel"),
            unitKm                 = s("unitKm"),
            unitKcal               = s("unitKcal"),
            unitSteps              = s("unitSteps"),
            unitMin                = s("unitMin"),
            notifChannelName       = s("notifChannelName"),
            notifWaitStart         = s("notifWaitStart"),
            notifWorking           = s("notifWorking"),
            notifStopAction        = s("notifStopAction"),
            notifModelLoading      = s("notifModelLoading"),
            permRequiredTitle      = s("permRequiredTitle"),
            permRequiredMsg        = s("permRequiredMsg"),
            permSettingsMsg        = s("permSettingsMsg"),
            permOpenSettings       = s("permOpenSettings"),
            updateReadyMsg         = s("updateReadyMsg"),
            updateRestart          = s("updateRestart"),
            updateLater            = s("updateLater"),
        )
    }
}