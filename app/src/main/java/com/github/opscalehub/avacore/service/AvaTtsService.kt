package com.github.opscalehub.avacore.service

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * AvaTtsService: A robust, offline-first TTS engine for Persian.
 * Optimized for performance and resilience on Android devices.
 */
class AvaTtsService : TextToSpeechService() {

    private val persianLocale = Locale("fa", "IR")
    private val voiceName = "fa-ir-ava-premium"
    
    @Volatile
    private var tts: OfflineTts? = null
    private val isInterrupted = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    companion object {
        private const val TAG = "AvaTtsService"
        private const val ASSET_SUBDIR = "tts"
        private const val MODEL_NAME = "persian_model.onnx"
        private const val TOKENS_NAME = "tokens.txt"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val MAX_BUFFER_SIZE = 8192 
        private const val INIT_RETRY_COUNT = 50 // 10 seconds total wait
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate: Initializing AvaCore TTS")
        super.onCreate()
        ensureEngineInitialized()
    }

    private fun ensureEngineInitialized() {
        if (isDestroyed.get() || tts != null || isInitializing.get()) return
        
        isInitializing.set(true)
        thread(start = true, name = "TtsInitializer", priority = Thread.MAX_PRIORITY) {
            try {
                prepareAndInitialize()
            } finally {
                isInitializing.set(false)
            }
        }
    }

    private fun prepareAndInitialize() {
        try {
            val modelFile = copyAssetToFile(MODEL_NAME)
            val tokensFile = copyAssetToFile(TOKENS_NAME)
            val espeakDir = copyAssetDirToFiles(ESPEAK_DIR)

            if (isDestroyed.get()) return

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                lexicon = "",
                tokens = tokensFile.absolutePath,
                dataDir = espeakDir.absolutePath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val cpuThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1).coerceAtMost(4)
            Log.d(TAG, "Initializing with $cpuThreads threads")

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = cpuThreads,
                debug = false,
                provider = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)
            val newTts = OfflineTts(config = config)
            
            if (isDestroyed.get()) {
                newTts.release()
            } else {
                tts = newTts
                Log.i(TAG, "AvaCore TTS Engine ready. Sample Rate: ${tts?.sampleRate()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to initialize TTS engine", e)
        }
    }

    private fun copyAssetToFile(fileName: String): File {
        val targetFile = File(filesDir, fileName)
        if (!targetFile.exists() || targetFile.length() == 0L) {
            Log.d(TAG, "Copying asset: $fileName")
            assets.open("$ASSET_SUBDIR/$fileName").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return targetFile
    }

    private fun copyAssetDirToFiles(dirName: String): File {
        val targetDir = File(filesDir, dirName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            copyAssetsRecursively("$ASSET_SUBDIR/$dirName", targetDir)
        }
        return targetDir
    }

    private fun copyAssetsRecursively(path: String, target: File) {
        val list = assets.list(path) ?: return
        if (list.isEmpty()) {
            assets.open(path).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            if (!target.exists()) target.mkdirs()
            for (file in list) {
                copyAssetsRecursively("$path/$file", File(target, file))
            }
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return if (lang != null && (lang.equals("fa", true) || lang.equals("fas", true))) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf("fa", "IR", "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onGetVoices(): MutableList<Voice> {
        return mutableListOf(Voice(voiceName, persianLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, mutableSetOf()))
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        return if (onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE) voiceName else null
    }

    override fun onStop() {
        Log.d(TAG, "onStop: Interrupting synthesis")
        isInterrupted.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        val rawText = request?.charSequenceText?.toString() ?: ""
        if (callback == null || rawText.isBlank()) {
            callback?.done()
            return
        }
        
        isInterrupted.set(false)
        
        // Wait for engine if it's still initializing
        var engine = tts
        var retry = 0
        while (engine == null && retry < INIT_RETRY_COUNT) {
            if (isInterrupted.get() || isDestroyed.get()) return
            Log.d(TAG, "Waiting for engine initialization... ($retry)")
            Thread.sleep(200)
            engine = tts
            retry++
        }

        if (engine == null) {
            Log.e(TAG, "Engine failed to initialize in time")
            callback.error(TextToSpeech.ERROR_SERVICE)
            ensureEngineInitialized() 
            return
        }
        
        try {
            val audio = engine.generate(rawText)
            
            if (audio != null && audio.samples.isNotEmpty()) {
                if (isInterrupted.get() || isDestroyed.get()) return

                val sampleRate = audio.sampleRate
                callback.start(sampleRate, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
                
                val samples = audio.samples
                val totalSamples = samples.size
                val buffer = java.nio.ByteBuffer.allocate(MAX_BUFFER_SIZE)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)

                var i = 0
                while (i < totalSamples && !isInterrupted.get() && !isDestroyed.get()) {
                    buffer.clear()
                    while (i < totalSamples && buffer.hasRemaining()) {
                        val sample = (samples[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                        buffer.putShort(sample)
                        i++
                    }
                    buffer.flip()
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    callback.audioAvailable(data, 0, data.size)
                }

                if (!isInterrupted.get() && !isDestroyed.get()) {
                    callback.done()
                }
            } else {
                Log.w(TAG, "Synthesis produced no audio")
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during synthesis", e)
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET) 
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Releasing engine")
        isDestroyed.set(true)
        tts?.release()
        tts = null
        super.onDestroy()
    }
}
