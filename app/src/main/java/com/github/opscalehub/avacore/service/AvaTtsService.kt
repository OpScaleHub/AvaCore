package com.github.opscalehub.avacore.service

import android.content.Intent
import android.os.IBinder
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

class AvaTtsService : TextToSpeechService() {

    private val persianLocale = Locale("fa", "IR")
    private val VOICE_NAME = "fa-ir-ava-premium"
    
    @Volatile
    private var tts: OfflineTts? = null
    private val isInterrupted = AtomicBoolean(false)

    companion object {
        private const val TAG = "AvaTtsService"
        private const val ASSET_SUBDIR = "tts"
        private const val MODEL_NAME = "persian_model.onnx"
        private const val TOKENS_NAME = "tokens.txt"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val MAX_BUFFER_SIZE = 8192 
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate: ENTER")
        super.onCreate()
        thread(start = true, name = "TtsInitializer") {
            prepareAndInitialize()
        }
    }

    private fun prepareAndInitialize() {
        try {
            val modelFile = copyAssetToFile(MODEL_NAME)
            val tokensFile = copyAssetToFile(TOKENS_NAME)
            val espeakDir = copyAssetDirToFiles(ESPEAK_DIR)

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                lexicon = "",
                tokens = tokensFile.absolutePath,
                dataDir = espeakDir.absolutePath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 1,
                debug = true,
                provider = "cpu"
            )

            val config = OfflineTtsConfig(model = modelConfig)
            tts = OfflineTts(config = config)
            Log.d(TAG, "TTS Engine ready. Sample Rate: ${tts?.sampleRate()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS engine", e)
        }
    }

    private fun copyAssetToFile(fileName: String): File {
        val targetFile = File(filesDir, fileName)
        if (!targetFile.exists() || targetFile.length() == 0L) {
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
        // Only accept standard 'fa' or 'fas' to match the Persian (Iran) entry
        return if (lang != null && (lang.equals("fa", true) || lang.equals("fas", true))) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        // Return standard fa-IR
        return arrayOf("fa", "IR", "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onGetVoices(): MutableList<Voice> {
        return mutableListOf(Voice(VOICE_NAME, persianLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, mutableSetOf()))
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        return if (onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE) VOICE_NAME else null
    }

    override fun onStop() {
        isInterrupted.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        val text = request?.charSequenceText?.toString() ?: ""
        if (callback == null) return
        
        isInterrupted.set(false)
        
        var engine = tts
        var retry = 0
        while (engine == null && retry < 25) {
            if (isInterrupted.get()) return
            Thread.sleep(200)
            engine = tts
            retry++
        }

        if (engine == null || isInterrupted.get()) {
            callback.error()
            return
        }
        
        try {
            val audio = engine.generate(text)
            if (audio != null && audio.samples.isNotEmpty()) {
                val pcmData = ShortArray(audio.samples.size)
                for (i in audio.samples.indices) {
                    pcmData[i] = (audio.samples[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                }
                
                val rawBytes = java.nio.ByteBuffer.allocate(pcmData.size * 2)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .apply { for (s in pcmData) putShort(s) }
                    .array()
                
                if (isInterrupted.get()) return
                
                callback.start(audio.sampleRate, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
                
                var offset = 0
                while (offset < rawBytes.size && !isInterrupted.get()) {
                    val remaining = rawBytes.size - offset
                    val length = if (remaining > MAX_BUFFER_SIZE) MAX_BUFFER_SIZE else remaining
                    callback.audioAvailable(rawBytes, offset, length)
                    offset += length
                }

                if (!isInterrupted.get()) {
                    callback.done()
                }
            } else {
                callback.error()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }
    
    override fun onDestroy() {
        tts?.release()
        super.onDestroy()
    }
}
