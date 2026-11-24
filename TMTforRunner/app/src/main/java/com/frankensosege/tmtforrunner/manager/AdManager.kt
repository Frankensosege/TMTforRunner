package com.frankensosege.tmtforrunner.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.android.gms.ads.RequestConfiguration

/**
 * AdMob 초기화 및 GDPR/개인정보 동의(UMP) 처리
 * MainActivity.onCreate()에서 1회 호출
 */
object AdManager {

    private const val TAG = "AdManager"
    private var isInitialized = false

    // 전면 광고 관련 상태
    private var interstitialAd: InterstitialAd? = null
    // 기존 PREF_NAME / PREF_LAST_INTERSTITIAL_TIME 상수 대신 추가
    private var lastInterstitialShownAt = 0L   // 앱 프로세스가 살아있는 동안만 유지
    private const val PREF_NAME = "tmt_settings"
//    private const val PREF_LAST_INTERSTITIAL_TIME = "last_interstitial_time"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 60_000L   // 최소 노출 간격 60초

    // 테스트 기기 ID 목록 (logcat "Ads" 태그에서 확인한 값). 이 기기에서는 항상 테스트 광고가 나온다.
    private val TEST_DEVICE_IDS = listOf(
        "7A0E6BF18E076C3E63297B041C7718B9"
    )


    fun initialize(activity: Activity, onReady: () -> Unit) {
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(TEST_DEVICE_IDS)
                .build()
        )

        val consentInformation: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)

        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // 필요한 경우 동의 폼 표시
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "동의 폼 오류: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsIfNeeded(activity, onReady)
                    }
                }
            },
            { requestError ->
                Log.w(TAG, "동의 정보 요청 실패: ${requestError.message}")
                // 동의 정보 조회 실패해도 SDK 자체는 초기화 시도 (비개인화 광고로 폴백될 수 있음)
                initializeMobileAdsIfNeeded(activity, onReady)
            }
        )

        // 이미 동의를 받은 상태라면 바로 광고 요청 가능
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsIfNeeded(activity, onReady)
        }
    }

    private fun initializeMobileAdsIfNeeded(context: Context, onReady: () -> Unit) {
        if (isInitialized) { onReady(); return }
        MobileAds.initialize(context) {
            isInitialized = true
            Log.d(TAG, "MobileAds 초기화 완료")
            onReady()
        }
    }

    // 전면 광고 미리 로드
    fun loadInterstitial(context: Context, adUnitId: String) {
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "전면 광고 로드 성공")
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "전면 광고 로드 실패: ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    // 최소 간격 충족 시에만 전면 광고 표시, 아니면 즉시 콜백 진행
//    fun showInterstitialIfReady(activity: Activity, adUnitId: String, onDismissed: () -> Unit) {
//        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
//        val lastShown = prefs.getLong(PREF_LAST_INTERSTITIAL_TIME, 0L)
//        val now = System.currentTimeMillis()
//        val ad = interstitialAd
//
//        if (ad != null && (now - lastShown) >= MIN_INTERSTITIAL_INTERVAL_MS) {
//            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
//                override fun onAdDismissedFullScreenContent() {
//                    interstitialAd = null
//                    prefs.edit().putLong(PREF_LAST_INTERSTITIAL_TIME, now).apply()
//                    loadInterstitial(activity, adUnitId)   // 다음 노출 대비 재로드
//                    onDismissed()
//                }
//                override fun onAdFailedToShowFullScreenContent(error: AdError) {
//                    Log.w(TAG, "전면 광고 표시 실패: ${error.message}")
//                    interstitialAd = null
//                    onDismissed()
//                }
//            }
//            ad.show(activity)
//        } else {
//            // 광고 준비 안 됐거나 최소 간격 미충족 → 광고 없이 즉시 진행
//            onDismissed()
//        }
//    }

    fun showInterstitialIfReady(activity: Activity, adUnitId: String, onDismissed: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd

        if (ad != null && (now - lastInterstitialShownAt) >= MIN_INTERSTITIAL_INTERVAL_MS) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownAt = System.currentTimeMillis()   // ← 광고가 닫힌 뒤부터 60초
                    loadInterstitial(activity, adUnitId)
                    onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "전면 광고 표시 실패: ${error.message}")
                    interstitialAd = null
                    onDismissed()
                }
            }
            ad.show(activity)
        } else {
            onDismissed()
        }
    }
}