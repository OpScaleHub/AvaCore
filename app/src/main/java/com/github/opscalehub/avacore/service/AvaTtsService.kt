package com.github.opscalehub.avacore.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.github.opscalehub.avacore.nlp.PronunciationLexicon
import com.github.opscalehub.avacore.nlp.TextProcessor
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * AvaTtsService: an offline-first, streaming Persian TTS engine.
 *
 * Synthesis is streamed sentence-by-sentence via Sherpa's generateWithCallback,
 * so the first audio plays almost immediately and stop requests are honored
 * mid-utterance. A Persian text front-end ([TextProcessor]) normalizes text,
 * expands numbers, applies a pronunciation lexicon and segments into prosodic
 * units before the audio model ever runs.
 */
class AvaTtsService : TextToSpeechService() {

    private val persianLocale = Locale("fa", "IR")
    private val voiceName = "fa-ir-ava-premium"

    @Volatile
    private var tts: OfflineTts? = null
    @Volatile
    private var textProcessor: TextProcessor? = null

    // Counts down once initialization finishes (success or failure).
    private val initLatch = CountDownLatch(1)

    private val isInterrupted = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)

    companion object {
        private const val TAG = "AvaTtsService"
        private const val ASSET_SUBDIR = "tts"
        private const val MODEL_NAME = "persian_model.onnx"
        private const val TOKENS_NAME = "tokens.txt"
        private const val LEXICON_NAME = "lexicon.txt"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val VERSION_MARKER = ".assets_version"

        // Bump whenever the bundled model/tokens/espeak/lexicon assets change so
        // stale copies in filesDir are re-extracted on the next launch.
        private const val ASSETS_VERSION = 2

        // Keep chunks small; some OEM audio paths reject large buffers.
        private const val MAX_CHUNK_BYTES = 8192
        private const val INIT_WAIT_MS = 10_000L

        // Map Android speech rate (percent of normal, 100 = default) to the
        // engine speed multiplier, clamped to a sane musical range.
        private const val MIN_SPEED = 0.5f
        private const val MAX_SPEED = 2.0f
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate: Initializing AvaCore TTS")
        super.onCreate()
        ensureEngineInitialized()
    }

    private fun ensureEngineInitialized() {
        if (isDestroyed.get() || tts != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        thread(start = true, name = "TtsInitializer") {
            try {
                prepareAndInitialize()
            } catch (e: Throwable) {
                Log.e(TAG, "CRITICAL: TTS init failed", e)
            } finally {
                isInitializing.set(false)
                initLatch.countDown()
            }
        }
    }

    private fun prepareAndInitialize() {
        ensureAssets()
        if (isDestroyed.get()) return

        val modelFile = File(filesDir, MODEL_NAME)
        val tokensFile = File(filesDir, TOKENS_NAME)
        val espeakDir = File(filesDir, ESPEAK_DIR)

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            lexicon = "",
            tokens = tokensFile.absolutePath,
            dataDir = espeakDir.absolutePath,
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )

        val cpuThreads = (Runtime.getRuntime().availableProcessors() / 2)
            .coerceIn(1, 4)
        Log.d(TAG, "Initializing engine with $cpuThreads threads")

        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = cpuThreads,
            debug = false,
            provider = "cpu"
        )

        val newTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))

        // Build the Persian text front-end (lexicon is optional / best-effort).
        val lexicon = try {
            PronunciationLexicon.fromStream(File(filesDir, LEXICON_NAME).takeIf { it.exists() }?.inputStream())
        } catch (e: Exception) {
            Log.w(TAG, "Lexicon load failed; continuing without it", e)
            PronunciationLexicon.fromStream(null)
        }
        textProcessor = TextProcessor(lexicon)

        if (isDestroyed.get()) {
            newTts.release()
        } else {
            tts = newTts
            Log.i(TAG, "AvaCore ready. SR=${newTts.sampleRate()} lexicon=${lexicon.size}")
        }
    }

    // ------------------------------------------------------------------
    // Asset migration (versioned + atomic)
    // ------------------------------------------------------------------

    private fun ensureAssets() {
        val marker = File(filesDir, VERSION_MARKER)
        val current = marker.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (current == ASSETS_VERSION &&
            File(filesDir, MODEL_NAME).length() > 0L &&
            File(filesDir, TOKENS_NAME).exists() &&
            File(filesDir, ESPEAK_DIR).isDirectory
        ) {
            return // up to date
        }

        Log.i(TAG, "Extracting assets (have=$current want=$ASSETS_VERSION)")
        marker.delete() // invalidate until the full copy succeeds

        copyAssetAtomic(MODEL_NAME)
        copyAssetAtomic(TOKENS_NAME)
        copyAssetAtomic(LEXICON_NAME)
        File(filesDir, ESPEAK_DIR).deleteRecursively()
        copyAssetDir("$ASSET_SUBDIR/$ESPEAK_DIR", File(filesDir, ESPEAK_DIR))

        marker.writeText(ASSETS_VERSION.toString())
    }

    /** Copy a single asset via a temp file + rename so a crash never leaves a
     * half-written target that later looks "present". */
    private fun copyAssetAtomic(fileName: String) {
        val target = File(filesDir, fileName)
        val tmp = File(filesDir, "$fileName.tmp")
        assets.open("$ASSET_SUBDIR/$fileName").use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun copyAssetDir(path: String, target: File) {
        val children = assets.list(path) ?: return
        if (children.isEmpty()) {
            // leaf file
            assets.open(path).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        } else {
            if (!target.exists()) target.mkdirs()
            for (child in children) {
                copyAssetDir("$path/$child", File(target, child))
            }
        }
    }

    // ------------------------------------------------------------------
    // TTS framework hooks
    // ------------------------------------------------------------------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return if (lang != null && (lang.equals("fa", true) || lang.equals("fas", true))) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> = arrayOf("fa", "IR", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetVoices(): MutableList<Voice> = mutableListOf(
        Voice(voiceName, persianLocale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, mutableSetOf())
    )

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        if (onIsLanguageAvailable(lang, country, variant) >= TextToSpeech.LANG_AVAILABLE) voiceName else null

    override fun onStop() {
        Log.d(TAG, "onStop: interrupting synthesis")
        isInterrupted.set(true)
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (callback == null) return
        val rawText = request?.charSequenceText?.toString().orEmpty()
        if (request == null || rawText.isBlank()) {
            callback.done()
            return
        }

        isInterrupted.set(false)

        val engine = awaitEngine()
        val processor = textProcessor
        if (engine == null || processor == null) {
            Log.e(TAG, "Engine not ready in time")
            callback.error(TextToSpeech.ERROR_SERVICE)
            ensureEngineInitialized()
            return
        }

        val speed = computeSpeed(request)
        val sampleRate = engine.sampleRate()
        // request.pitch is read but intentionally not applied: VITS has no pitch
        // control and naive resampling shifts formants and degrades quality.

        try {
            val units = processor.process(rawText)
            if (units.isEmpty()) {
                callback.done()
                return
            }

            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            val maxChunk = (minOf(callback.maxBufferSize, MAX_CHUNK_BYTES) and 1.inv())
                .coerceAtLeast(2)
            val writer = PcmWriter(callback, maxChunk)

            for (unit in units) {
                if (isInterrupted.get() || isDestroyed.get()) break
                if (unit.text.isNotBlank()) {
                    engine.generateWithCallback(unit.text, 0, speed) { chunk ->
                        writer.write(chunk)
                        if (isInterrupted.get() || isDestroyed.get()) 0 else 1
                    }
                }
                if (isInterrupted.get() || isDestroyed.get()) break
                if (unit.trailingPauseMs > 0) writer.writeSilence(sampleRate, unit.trailingPauseMs)
            }

            callback.done()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during synthesis", e)
            callback.error(TextToSpeech.ERROR_OUTPUT)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }

    private fun awaitEngine(): OfflineTts? {
        tts?.let { return it }
        ensureEngineInitialized()
        try {
            initLatch.await(INIT_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return tts
    }

    private fun computeSpeed(request: SynthesisRequest): Float {
        val rate = request.speechRate
        val speed = if (rate <= 0) 1.0f else rate / 100.0f
        return speed.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: releasing engine")
        isDestroyed.set(true)
        isInterrupted.set(true)
        tts?.release()
        tts = null
        super.onDestroy()
    }

    /**
     * Streams float samples to the framework as little-endian PCM16, reusing a
     * single scratch buffer and flushing in chunks no larger than the framework
     * allows.
     */
    private class PcmWriter(
        private val callback: SynthesisCallback,
        private val maxChunkBytes: Int
    ) {
        private val scratch = ByteArray(maxChunkBytes)
        private val samplesPerChunk = maxChunkBytes / 2

        fun write(samples: FloatArray) {
            var i = 0
            while (i < samples.size) {
                val n = minOf(samplesPerChunk, samples.size - i)
                var b = 0
                for (k in 0 until n) {
                    val s = (samples[i + k].coerceIn(-1f, 1f) * 32767f).toInt()
                    scratch[b++] = (s and 0xFF).toByte()
                    scratch[b++] = ((s shr 8) and 0xFF).toByte()
                }
                callback.audioAvailable(scratch, 0, b)
                i += n
            }
        }

        fun writeSilence(sampleRate: Int, durationMs: Int) {
            var remaining = (sampleRate.toLong() * durationMs / 1000).toInt() * 2 // bytes
            // scratch may hold stale data; zero only what we use each pass.
            while (remaining > 0) {
                val n = minOf(maxChunkBytes, remaining)
                for (k in 0 until n) scratch[k] = 0
                callback.audioAvailable(scratch, 0, n)
                remaining -= n
            }
        }
    }
}
