package com.frankensosege.tmtforrunner.manager

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import java.io.FilterInputStream

class VoiceCommandManager(
    private val context: Context,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onModelLoading: (Boolean) -> Unit,
    private val onDownloadProgress: ((Int) -> Unit)? = null
) {
    companion object {
        private const val TAG                  = "VoiceCmd"
        private const val SAMPLE_RATE          = 16000
        private const val BUFFER_SIZE          = 4096
        private const val COMMAND_COOLDOWN_MS  = 5000L
        private const val CONFIDENCE_THRESHOLD = 0.80f

        data class ModelInfo(val folderName: String, val downloadUrl: String)

        val MODEL_MAP = mapOf(
            "en" to ModelInfo(
                "vosk-model-small-en-us-0.15",
                "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
            ),
            "ja" to ModelInfo(
                "vosk-model-small-ja-0.22",
                "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip"
            ),
            "zh" to ModelInfo(
                "vosk-model-small-cn-0.22",
                "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
            )
        )

        val START_KEYWORDS: Set<String> = setOf("start")
        val STOP_KEYWORDS:  Set<String> = setOf("stop")
    }

    private var model:       Model?      = null
    private var recognizer:  Recognizer? = null
    private var audioRecord: AudioRecord? = null

    // ✅ @Volatile — 멀티스레드 환경에서 isActive 상태 즉시 반영
    @Volatile private var isActive = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mainJob: Job? = null   // ✅ 단일 Job으로 전체 흐름 관리

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var lastCommandTime = 0L

    private fun getVoskModelLang(): String {
        val langCode = context.getSharedPreferences("tmt_settings", Context.MODE_PRIVATE)
            .getString("language_code", "en") ?: "en"
        return com.frankensosege.tmtforrunner.data.i18n.SupportedLanguage
            .fromCode(langCode).voskModelLang
    }

    // ─── 시작 ─────────────────────────────────────────────
    fun start() {
        Log.d(TAG, "▶ start() 호출")
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ RECORD_AUDIO 권한 없음")
            return
        }
        if (isActive) {
            Log.w(TAG, "⚠ 이미 실행 중 — 중복 start() 무시")
            return
        }
        isActive = true
        muteAudioInput()

        // ✅ 전체 흐름을 단일 코루틴으로 관리 (다운로드 → 모델 로드 → 오디오 루프)
        mainJob = scope.launch {
            try {
                loadModelAndStartLoop()
            } catch (e: CancellationException) {
                // ✅ 정상 취소 신호 — 에러 로그 없이 재throw
                Log.d(TAG, "mainJob 정상 취소됨")
                throw e
            } catch (e: Exception) {
                // ✅ 실제 예외만 에러 로그 출력
                Log.e(TAG, "❌ 전체 흐름 실패: ${e.javaClass.simpleName} / ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) { onModelLoading(false) }
            }
        }
    }

    // ─── 정지 ─────────────────────────────────────────────
    fun stop() {
        Log.d(TAG, "▶ stop() 호출")
        isActive = false
        mainJob?.cancel()
        mainJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.close(); recognizer = null
        model?.close();      model      = null
        unmuteAudioInput()
        onListening(false)
        Log.d(TAG, "✅ VoiceCommandManager 정지 완료")
    }

    // ─── 전체 흐름: 다운로드 → 모델 로드 → 오디오 루프 ──
    // ✅ 단일 suspend 함수로 순차 실행 — 스레드 전환 문제 제거
    private suspend fun loadModelAndStartLoop() {
        Log.d(TAG, "▶ loadModelAndStartLoop() 시작")

        withContext(Dispatchers.Main) { onModelLoading(true) }

        val voskLang  = getVoskModelLang()
        val modelInfo = MODEL_MAP[voskLang] ?: MODEL_MAP["en"]!!
        val baseDir   = File(context.filesDir, "models/${modelInfo.folderName}")

        Log.d(TAG, "모델 언어: $voskLang, 폴더: ${modelInfo.folderName}")
        Log.d(TAG, "baseDir: ${baseDir.absolutePath}, 존재: ${baseDir.exists()}")

        // ── 1단계: 다운로드 ────────────────────────────────
        val needsDownload = !baseDir.exists() || !isValidModelDir(resolveModelDir(baseDir))
        Log.d(TAG, "다운로드 필요: $needsDownload")

        if (needsDownload) {
            if (baseDir.exists()) {
                baseDir.deleteRecursively()
                Log.d(TAG, "기존 폴더 삭제 완료")
            }
            Log.d(TAG, "▶ 다운로드 시작")
            downloadAndExtract(modelInfo.downloadUrl, baseDir)
            Log.d(TAG, "✅ 다운로드 완료")
        } else {
            Log.d(TAG, "기존 모델 재사용")
        }

        // ── 2단계: 모델 경로 확인 ──────────────────────────
        val modelDir = resolveModelDir(baseDir)
        Log.d(TAG, "최종 모델 경로: ${modelDir.absolutePath}")
        Log.d(TAG, "폴더 내용: ${modelDir.list()?.joinToString() ?: "비어있음"}")

        if (!isValidModelDir(modelDir)) {
            Log.e(TAG, "❌ 유효하지 않은 모델 폴더")
            withContext(Dispatchers.Main) { onModelLoading(false) }
            return
        }

        // ── 3단계: 모델 로드 ───────────────────────────────
        if (!isActive) { Log.w(TAG, "⚠ 모델 로드 전 중단됨"); return }
        Log.d(TAG, "▶ Model() 생성 시작")
        val loadedModel = try {
            Model(modelDir.absolutePath)
        } catch (e: UnsatisfiedLinkError) {   // ✅ [추가] 네이티브 라이브러리 로드 실패(16KB 정렬 등) 방어
            Log.e(TAG, "❌ 네이티브 라이브러리 로드 실패 (기기 비호환 가능성): ${e.message}")
            withContext(Dispatchers.Main) { onModelLoading(false) }
            return
        } catch (e: Exception) {
            // ✅ [추가] 모델 생성 실패 시 손상된 폴더를 삭제해
            //          다음 start() 호출(예: 앱 재시작) 시 자동으로 재다운로드되도록 함
            Log.e(TAG, "❌ Model() 생성 실패 — 손상된 폴더 삭제 후 재시도 유도: ${e.message}")
            baseDir.deleteRecursively()
            withContext(Dispatchers.Main) { onModelLoading(false) }
            return
        }
        Log.d(TAG, "✅ Model() 생성 완료")

        // ── 4단계: Recognizer 생성 ─────────────────────────
        if (!isActive) { Log.w(TAG, "⚠ Recognizer 생성 전 중단됨"); return }
        Log.d(TAG, "▶ Recognizer 생성 시작")
        model      = loadedModel
        recognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
        Log.d(TAG, "✅ Recognizer 생성 완료")

        withContext(Dispatchers.Main) { onModelLoading(false) }

        // ── 5단계: 오디오 루프 (같은 코루틴에서 바로 실행) ─
        if (!isActive) { Log.w(TAG, "⚠ 오디오 루프 시작 전 중단됨"); return }
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ 오디오 루프 시작 전 권한 없음")
            return
        }
        runAudioLoop()
    }

    // ─── 오디오 루프 (suspend) ────────────────────────────
    private suspend fun runAudioLoop() {
        Log.d(TAG, "▶ runAudioLoop() 시작")
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        Log.d(TAG, "minBuf=$minBuf")

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, BUFFER_SIZE)
            )
        } catch (e: SecurityException) {
            Log.d(TAG, "오디오 녹음 권한이 없습니다: ${e.message}")
            return
        }
        Log.d(TAG, "AudioRecord state=${recorder.state}")

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "❌ AudioRecord 초기화 실패 (state=${recorder.state})")
            return
        }

        audioRecord = recorder
        recorder.startRecording()
        withContext(Dispatchers.Main) { onListening(true) }
        Log.d(TAG, "✅ 오디오 루프 시작 — 음성 대기 중")

        try {
            val buffer    = ByteArray(maxOf(minBuf, BUFFER_SIZE))
            var loopCount = 0
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                if (loopCount < 3) {
                    Log.d(TAG, "오디오 read=$read bytes (loop#$loopCount)")
                    loopCount++
                }
                val isFinal = recognizer?.acceptWaveForm(buffer, read) ?: false
                if (isFinal) {
                    recognizer?.result?.let { parseAndCheck(it) }
                }
            }
        } finally {
            // ✅ isActive=false 되면 finally로 안전하게 정리
            recorder.stop()
            recorder.release()
            audioRecord = null
            withContext(Dispatchers.Main) { onListening(false) }
            Log.d(TAG, "오디오 루프 종료")
        }
    }

    // ─── AudioManager 음소거 ──────────────────────────────
    private fun muteAudioInput() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            Log.d(TAG, "AudioManager 음소거 적용")
        } catch (e: Exception) {
            Log.e(TAG, "AudioManager 음소거 실패: ${e.message}")
        }
    }

    private fun unmuteAudioInput() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            Log.d(TAG, "AudioManager 음소거 해제")
        } catch (e: Exception) {
            Log.e(TAG, "AudioManager 음소거 해제 실패: ${e.message}")
        }
    }

    // ─── ZIP 다운로드 & 압축 해제 ─────────────────────────
    private fun downloadAndExtract(urlString: String, destDir: File) {
        val url  = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout    = 120_000
        conn.connect()

        val totalBytes      = conn.contentLengthLong      // 압축된 ZIP 크기
        var downloadedBytes = 0L                          // 네트워크에서 실제로 받은 바이트
        var lastPercent     = -1
        var topLevelPrefix: String? = null

        // 압축을 풀기 전 '받은 바이트'를 센다 (분자·분모 기준 일치)
        val countingStream = object : FilterInputStream(conn.inputStream) {
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) downloadedBytes++
                return b
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) downloadedBytes += n
                return n
            }
        }

        // 0~100으로 제한하고, 값이 바뀔 때만 콜백 호출
        fun reportProgress() {
            if (totalBytes <= 0) return
            val percent = (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                onDownloadProgress?.invoke(percent)
            }
        }
        reportProgress()   // 시작 시 0% 표시

        ZipInputStream(countingStream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace("\\", "/")

                if (topLevelPrefix == null) {
                    val firstSlash = entryName.indexOf('/')
                    topLevelPrefix = if (firstSlash >= 0)
                        entryName.substring(0, firstSlash + 1)
                    else ""
                    Log.d(TAG, "ZIP prefix: '$topLevelPrefix'")
                }

                val relativePath = if (topLevelPrefix!!.isNotEmpty() && entryName.startsWith(topLevelPrefix!!))
                    entryName.removePrefix(topLevelPrefix!!)
                else entryName

                if (relativePath.isEmpty()) {
                    zip.closeEntry(); entry = zip.nextEntry; continue
                }

                val outFile = File(destDir, relativePath)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(8192)
                        var len: Int
                        while (zip.read(buf).also { len = it } != -1) {
                            fos.write(buf, 0, len)
                            reportProgress()
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        conn.disconnect()
        if (totalBytes > 0 && lastPercent < 100) onDownloadProgress?.invoke(100)
        Log.d(TAG, "ZIP 해제 완료, 파일: ${destDir.list()?.joinToString() ?: "없음"}")
    }
    // ─── 중첩 폴더 감지 ───────────────────────────────────
    private fun resolveModelDir(baseDir: File): File {
        if (isValidModelDir(baseDir)) return baseDir
        val subDirs = baseDir.listFiles { f -> f.isDirectory }
        if (!subDirs.isNullOrEmpty() && subDirs.size == 1) {
            val subDir = subDirs[0]
            if (isValidModelDir(subDir)) {
                Log.d(TAG, "중첩 폴더 감지: ${subDir.name}")
                return subDir
            }
        }
        return baseDir
    }

    private fun isValidModelDir(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false

        // ✅ [수정] 폴더 이름 존재 여부가 아니라, 각 폴더 안에 실제 파일이 있는지까지 확인
        //          → 다운로드 중단으로 빈 폴더만 생성된 손상 상태를 무효로 판정
        val amDir    = File(dir, "am")
        val confDir  = File(dir, "conf")
        val graphDir = File(dir, "graph")

        val amValid    = amDir.isDirectory    && !amDir.listFiles().isNullOrEmpty()
        val confValid  = confDir.isDirectory  && !confDir.listFiles().isNullOrEmpty()
        val graphValid = graphDir.isDirectory && !graphDir.listFiles().isNullOrEmpty()

        return amValid && confValid && graphValid
    }

    // ─── 시동어 감지 ──────────────────────────────────────
    private fun parseAndCheck(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val text = json.optString("text", "").trim()
            if (text.isEmpty() || text == "[unk]") return

            val cleanText = text.replace(" ", "").lowercase()
            Log.d(TAG, "인식 결과: '$cleanText'")

            // 신뢰도 체크
            val resultArray = json.optJSONArray("result")
            if (resultArray != null && resultArray.length() > 0) {
                var minConf = 1.0f
                for (i in 0 until resultArray.length()) {
                    val conf = resultArray.getJSONObject(i).optDouble("conf", 0.0).toFloat()
                    if (conf < minConf) minConf = conf
                }
                Log.d(TAG, "신뢰도: $minConf")
                if (minConf < CONFIDENCE_THRESHOLD) {
                    Log.d(TAG, "신뢰도 부족 ($minConf) — 무시")
                    return
                }
            }

            // 쿨다운 체크
            val now = System.currentTimeMillis()
            if (now - lastCommandTime < COMMAND_COOLDOWN_MS) {
                Log.d(TAG, "쿨다운 중 — 무시")
                return
            }

            val isStart = START_KEYWORDS.any { cleanText == it }
            val isStop  = STOP_KEYWORDS.any  { cleanText == it }

            when {
                isStart -> {
                    Log.d(TAG, "✅ start 명령 인식")
                    lastCommandTime = now
                    recognizer?.reset()
                    CoroutineScope(Dispatchers.Main).launch { onStart() }
                }
                isStop -> {
                    Log.d(TAG, "✅ stop 명령 인식")
                    lastCommandTime = now
                    recognizer?.reset()
                    CoroutineScope(Dispatchers.Main).launch { onStop() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "파싱 에러: ${e.message}")
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
}