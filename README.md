# AvaCore: Native Persian Text-to-Speech (TTS) Engine for Android

AvaCore is a high-performance, on-device Persian (Farsi) Text-to-Speech engine designed to provide a natural and seamless voice experience for Android users. By integrating directly with the `android.speech.tts` framework, AvaCore enables all Android applications to speak Farsi with human-like prosody and high clarity. On the device it runs **entirely offline** — no network access is needed at run time.

## Project Vision
To bridge the accessibility gap for Persian speakers on Android by delivering a state-of-the-art TTS engine that overcomes the unique linguistic challenges of the Farsi language, such as short-vowel omission and hidden *Ezafe* (کسرهٔ اضافه) constructions.

## Setup (first build)
The large binary assets (the `.aar` engine, the ~63 MB neural model, and the eSpeak-NG data) are **not committed to git** — they are provisioned on demand to keep the repo slim. Before the first build, run:

```bash
./download_assets.sh
```

This fetches the Sherpa-ONNX AAR, the Piper VITS model, and the eSpeak-NG data into place (one network fetch). After that, the build and the installed app are fully offline.

---

## Current Architecture (what ships today)

AvaCore is built on a compact, proven, fully-offline stack:

| Layer | Technology | Notes |
| :--- | :--- | :--- |
| **Inference engine** | Sherpa-ONNX 1.10.41 (`app/libs/sherpa-onnx.aar`) | JNI + Kotlin wrapper around ONNX Runtime |
| **Acoustic + vocoder** | **Piper VITS** (`persian_model.onnx`) | End-to-end model — the HiFi-GAN-style decoder *is* the vocoder; 22.05 kHz |
| **Grapheme-to-phoneme** | **eSpeak-NG** (`espeak-ng-data/`) | Persian phonemization |
| **Text front-end** | AvaCore `nlp/` pipeline (Kotlin) | Normalization, number expansion, lexicon, segmentation, SSML |
| **System integration** | `AvaTtsService : TextToSpeechService` | Serves every app on the device |

> Note: VITS is a single end-to-end network. There is **no separate Tacotron front-end or WaveRNN vocoder** in the shipping engine — those belong to the future roadmap below.

### Synthesis pipeline
Text flows through `nlp/TextProcessor` before the neural model:

1. **SSML** (`nlp/Ssml.kt`) — `<speak>`, `<break>`, `<say-as>` are honored; plain text passes through.
2. **Number expansion** (`nlp/NumberToWords.kt`) — full Persian cardinals/ordinals/decimals/percent (`۱۴۰۳` → «هزار و چهارصد و سه»), Persian/Arabic/ASCII digits.
3. **Normalization** (`nlp/Normalizer.kt`) — Arabic→Persian letter folding, ZWNJ (نیم‌فاصله) normalization, kashida/tanvin/diacritic cleanup, punctuation spacing.
4. **Pronunciation lexicon** (`nlp/PronunciationLexicon.kt` + `assets/tts/lexicon.txt`) — high-precision overrides for short-vowel restoration and fixed *ezafe* compounds; curated and easily extensible.
5. **Sentence segmentation** (`nlp/SentenceSegmenter.kt`) — splits text into short, prosodically-paused units.

### Streaming + responsiveness
- **Incremental streaming:** synthesis uses Sherpa's `generateWithCallback`, so the first audio plays after the first chunk — latency-to-first-audio stays roughly constant regardless of text length.
- **Instant interruption:** `onStop()` aborts the current utterance mid-stream.
- **System speech-rate:** the platform speech-rate is mapped to the engine speed multiplier.
- **System pitch:** the platform pitch setting is honored via a duration-preserving SOLA pitch shifter (`dsp/PitchShifter`); the default pitch path streams untouched audio.
- **OEM-safe buffering:** audio is delivered in ≤ 8 KB chunks to satisfy strict OEM audio paths (e.g. Oppo/OnePlus).
- **Robust asset migration:** bundled assets are extracted to `filesDir` once, versioned (`ASSETS_VERSION`) and copied atomically so a stale or partial copy is repaired automatically.

See `ARCHITECTURE.md` for deployment details (16 KB page-size packaging, background-execution permissions on some OEMs).

---

## Future Roadmap (not yet implemented)

These items are aspirational targets, not current behavior:

### Linguistic depth
- **Ezafe prediction & homograph disambiguation** — an ML model (GE2PE-style two-step G2P: large machine-generated pre-training + manual fine-tuning) to resolve مرد/مُرد-type ambiguities and predict ezafe in context, replacing the curated lexicon seed.
- **Richer normalizer** — abbreviation/date/currency expansion, DadmaTools-style preprocessing.

### Model & inference
- **Smaller distribution** — dynamic INT8 quantization was evaluated and dropped (it crashes this Sherpa/ORT build at load and gives no APK-size win since zip already compresses the fp32 weights). The viable lever is per-ABI splits / an Android App Bundle.
- **Hardware acceleration** — trial the NNAPI provider for NPU/GPU execution.
- **SOTA model evaluation** — Matcha-TTS (flow-matching) and Kokoro are drop-in candidates via Sherpa's existing `OfflineTtsMatchaModelConfig` / `OfflineTtsKokoroModelConfig`.
- **SSML expansion** — prosody/emphasis/phoneme tags.

### Training methodology (reference)
- **Dataset:** the **ManaTTS** corpus (≈86 h, 44.1 kHz), cleaned with Spleeter.
- **Forced alignment:** multi-model ASR voting; strict CER thresholds (HIGH < 0.05, MIDDLE < 0.20) for data selection.

## Aspirational KPIs

| Metric | Target | Note |
| :--- | :--- | :--- |
| **Real-Time Factor** | > 3.0× | mid-range CPU/NPU |
| **Latency to first audio** | < 180 ms | enabled by streaming synthesis |
| **RAM usage** | 10–20 MB active | depends on quantization |
| **Storage** | < 80 MB | model weights |
| **Mean Opinion Score** | > 4.0 | subjective naturalness |

---
AvaCore aims to set a new standard for Persian accessibility on Android: a robust, offline, high-quality voice for navigators, screen readers and virtual assistants.
