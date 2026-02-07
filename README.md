# AvaCore: Native Persian Text-to-Speech (TTS) Engine for Android

AvaCore is a high-performance, on-device Persian (Farsi) Text-to-Speech engine designed to provide a natural and seamless voice experience for Android users. By integrating directly with the `android.speech.tts` framework, AvaCore enables all Android applications to speak Farsi with human-like prosody and high clarity, entirely offline.

## Project Vision
To bridge the accessibility gap for Persian speakers on Android by delivering a state-of-the-art TTS engine that overcomes the unique linguistic challenges of the Farsi language, such as short vowel omissions and hidden *Ezafe* constructions.

## Technical Roadmap

### 1. NLP and Linguistic Analysis Pipeline
The quality of synthesized speech depends heavily on text analysis. Farsi poses unique challenges with homographs and hidden vowels.
*   **Preprocessing:** Utilizing `DadmaTools` for character normalization and noise removal.
*   **Ezafe Detection:** Advanced detection of "Kasreh Ezafe" to ensure grammatical correctness in pronunciation.
*   **Homograph Disambiguation:** Implementation of the `GE2PE` protocol to resolve phonetic ambiguities in written Farsi, significantly improving recognition accuracy.
*   **Two-Step G2P Training:** A Grapheme-to-Phoneme model trained first on massive machine-generated data, followed by fine-tuning on high-precision manual transcriptions.

### 2. Neural Synthesis Architecture
A hybrid approach focusing on stability and performance for mobile environments.
*   **Frontend (Tacotron):** Employs **Stepwise Monotonic Attention** to prevent word skipping or repeating in long sentences, ensuring robust synthesis for complex Farsi literature.
*   **Backend/Vocoder (WaveRNN):** Generates high-quality 24kHz audio. WaveRNN reduces the storage footprint from standard large-scale models to a mobile-friendly 2.5MB - 70MB range.

### 3. On-Device Optimization (Edge AI)
Optimized for real-time performance on various mobile hardware tiers.
*   **Quantization:** 8-bit mu-law quantization with a **0.86 Pre-emphasis filter** to maintain signal-to-noise ratio while reducing model size.
*   **Hardware Acceleration:** Leveraging the **Android Neural Networks API (NNAPI)** for NPU/GPU execution.
*   **Computation Efficiency:** Split-state GRU architecture and result caching for a 15-50% boost in computational efficiency, targeting at least **3x Real-Time Factor (RTF)**.

### 4. Android System Integration
Seamlessly integrated into the Android ecosystem to serve all installed applications.
*   **System Engine:** Implemented via the standard `android.speech.tts.TextToSpeechService` API.
*   **Streaming Logic:** Uses an **Inner/Outer stream loop** (5:1 ratio, approx. 100ms speech chunks) for ultra-low latency incremental audio delivery.
*   **Standard Compliance:** Supports system intents like `ACTION_INSTALL_TTS_DATA` for seamless resource management and installation.

### 5. Training Methodology & ManaTTS Dataset
*   **Dataset:** Powered by the **ManaTTS** dataset, featuring 86 hours of high-quality 44.1kHz speech.
*   **Cleaning:** Audio preprocessing using Spleeter to ensure noise-free training samples.
*   **Forced Alignment:** Multi-model ASR voting and Interval/Gapped search for precise text-to-audio alignment.
*   **Quality Control:** Strict Character Error Rate (CER) thresholds (HIGH < 0.05, MIDDLE < 0.20) for training data selection.

## Performance Benchmarks (KPIs)

| Metric | Target Value | Technical Note |
| :--- | :--- | :--- |
| **Real-Time Factor (RTF)** | > 3.0x | Speed on mid-range CPU/NPU |
| **Perceived Latency (CPL)** | < 180ms | Time until first audio buffer playback |
| **RAM Usage** | 10 - 20 MB | Active memory footprint during synthesis |
| **Storage (Disk)** | < 80 MB | Total model weights (Tacotron + WaveRNN) |
| **Mean Opinion Score (MOS)** | > 4.0 | Subjective naturalness vs. human speech |

---
AvaCore aims to set a new standard for Persian accessibility on Android, providing a robust, offline, and high-quality voice for navigators, screen readers, and virtual assistants.
