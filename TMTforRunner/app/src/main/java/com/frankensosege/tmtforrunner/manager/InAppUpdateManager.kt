package com.frankensosege.tmtforrunner.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.frankensosege.tmtforrunner.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play 인앱 업데이트 (FLEXIBLE)
 * - MainActivity.onCreate()에서 생성 (registerForActivityResult는 STARTED 전에 호출해야 함)
 * - onResume()에서 checkForUpdate(), onDestroy()에서 unregister()
 * - 다운로드 완료 시 onDownloaded 호출 → UI에서 재시작 안내 후 completeUpdate()
 */
class InAppUpdateManager(
    private val activity: ComponentActivity,
    private val onDownloaded: () -> Unit
) {
    companion object {
        private const val TAG = "InAppUpdate"
        private const val PREF_NAME = "tmt_settings"
        private const val PREF_LAST_PROMPT = "last_update_prompt_time"
        private const val PROMPT_INTERVAL_MS = 24L * 60 * 60 * 1000   // 업데이트 안내는 하루 1회
    }

    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private var listenerRegistered = false

    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "업데이트 취소/실패: resultCode=${result.resultCode}")
        }
    }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) onDownloaded()
    }

    fun checkForUpdate() {
        if (BuildConfig.DEBUG) return   // 디버그 빌드는 Play 설치본이 아니므로 생략

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    // 이미 내려받은 상태 → 재시작 안내
                    info.installStatus() == InstallStatus.DOWNLOADED -> onDownloaded()

                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                            canPromptAgain() -> {
                        if (!listenerRegistered) {
                            appUpdateManager.registerListener(installListener)
                            listenerRegistered = true
                        }
                        markPrompted()
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                // Play 설치본이 아니거나 네트워크 오류 등 — 조용히 무시
                Log.w(TAG, "업데이트 확인 실패: ${e.message}")
            }
    }

    /** 다운로드 완료 후 사용자가 재시작을 선택했을 때 호출 */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun unregister() {
        if (listenerRegistered) {
            appUpdateManager.unregisterListener(installListener)
            listenerRegistered = false
        }
    }

    private fun canPromptAgain(): Boolean {
        val last = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_LAST_PROMPT, 0L)
        return System.currentTimeMillis() - last >= PROMPT_INTERVAL_MS
    }

    private fun markPrompted() {
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putLong(PREF_LAST_PROMPT, System.currentTimeMillis()).apply()
    }
}