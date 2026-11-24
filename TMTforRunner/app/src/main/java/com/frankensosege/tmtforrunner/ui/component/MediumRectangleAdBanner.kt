package com.frankensosege.tmtforrunner.ui.component

import android.util.Log
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** 중간 크기 사각형(300×250) 배너. 로드되기 전에는 1dp만 차지해 빈 공간이 보이지 않는다. */
@Composable
fun MediumRectangleAdBanner(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoaded by remember(adUnitId) { mutableStateOf(false) }

    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("ExitAdBanner", "광고 로드 성공: $adUnitId")
                    isLoaded = true
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("ExitAdBanner", "광고 로드 실패: $adUnitId, ${error.message}")
                    isLoaded = false
                }
            }
        }
    }

    LaunchedEffect(adUnitId) {
        adView.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier
            .width(300.dp)
            .height(if (isLoaded) 250.dp else 1.dp),
        factory  = { adView }
    )
}