package com.frankensosege.tmtforrunner.ui.component

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import android.util.Log

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String
) {
    val context = LocalContext.current

    // ✅ [수정] AdView를 remember로 고정 — 리컴포지션마다 재생성되지 않도록
    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("AdBanner", "광고 로드 성공: $adUnitId")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("AdBanner", "광고 로드 실패: $adUnitId, ${error.message}")
                }
            }
        }
    }

    // ✅ [수정] 광고 요청도 adUnitId가 바뀔 때만(사실상 최초 1회)
    LaunchedEffect(adUnitId) {
        adView.loadAd(AdRequest.Builder().build())
    }

    // ✅ [추가] 화면 이탈 시 리소스 해제
    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory  = { adView }   // ✅ [수정] remember된 인스턴스 그대로 반환
    )
}