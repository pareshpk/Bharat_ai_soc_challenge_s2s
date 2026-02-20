# BhashaBridge — Offline English-to-Hindi Speech-to-Speech Translation System


![Uploading bhashabridge_icon_1024.png…]()


BhashaBridge is a fully offline, real-time Speech-to-Speech (S2ST) translation system engineered for ARM-based Android devices. The system accepts spoken English input, performs on-device Automatic Speech Recognition (ASR), translates the recognized text into Hindi using a quantized transformer model, and synthesizes spoken Hindi output — all without any internet connectivity or cloud dependency. The project is submitted as a solution to ARM Bharat SoC Challenge, Problem Statement 4.

---

## Problem Statement

India's linguistic diversity creates practical communication barriers across domains including healthcare, governance, education, and emergency response. The majority of deployed translation systems rely on centralized cloud infrastructure, requiring continuous internet access for audio transmission, remote processing, and response delivery.

This dependency renders such systems non-functional in rural regions, disaster zones, and low-bandwidth environments — precisely the scenarios where multilingual communication is most critical. Beyond connectivity, cloud-dependent translation introduces variable latency and data privacy concerns due to transmission of sensitive audio to remote servers.

The identified engineering gap is the absence of a robust, low-latency, fully offline Speech-to-Speech translation pipeline optimized for constrained ARM-based mobile hardware. While individual components such as offline ASR and machine translation exist independently, integrating them into a stable, memory-efficient, real-time mobile system presents non-trivial systems engineering challenges that this project directly addresses.

---

## Solution Overview

BhashaBridge implements a five-stage inference pipeline, executed entirely on-device:

1. **Audio Capture** — The device microphone records spoken English audio at 16 kHz mono PCM using the Android `AudioRecord` API.
2. **Speech Recognition** — The Vosk offline ASR engine performs streaming recognition, incrementally processing audio and producing a final English transcript upon user release.
3. **Neural Translation** — The English transcript is tokenized and passed through a quantized transformer model exported in ONNX format. ONNX Runtime executes the inference on-device.
4. **Linguistic Correction** — A deterministic post-processing layer refines the translated Hindi text by eliminating token duplication, auxiliary verb repetition, and morphological artifacts.
5. **Speech Synthesis** — The refined Hindi text is converted to speech using Android's built-in offline Hindi TTS engine.

The result is a seamless voice-in, voice-out translation experience that operates without any network dependency.

---

## System Architecture

```
Microphone Input
      |
      v
Audio Capture (AudioRecord API, 16 kHz, Mono PCM)
      |
      v
Vosk Streaming ASR Engine
      |
      v
English Transcript
      |
      v
ONNX Runtime Translator (Quantized Transformer Model)
      |
      v
Raw Hindi Token Output
      |
      v
Linguistic Correction Layer (Deterministic Post-Processing)
      |
      v
Refined Hindi Text
      |
      v
Android Offline TTS Engine
      |
      v
Hindi Speech Output
```

### Component Descriptions

**Audio Capture Module**
Audio is recorded using `AudioRecord` at 16 kHz, mono channel, PCM 16-bit format. Audio buffering is performed in fixed-size chunks to balance latency with processing throughput. Recording executes on a dedicated background thread to prevent blocking the UI thread.

**Automatic Speech Recognition**
Vosk, an offline ASR toolkit built on the Kaldi framework, performs streaming speech recognition. It processes audio incrementally and emits a finalized English transcript upon recording completion. The recognizer instance is maintained persistently across sessions to avoid repeated initialization overhead.

**Neural Machine Translation Engine**
The translation model uses a transformer architecture with attention mechanisms for contextual word mapping. The model is exported to ONNX format and executed via ONNX Runtime for Android, which provides ARM-optimized CPU inference kernels. The pipeline performs subword tokenization of the English input, model inference, and token decoding into Hindi text.

**Linguistic Correction Layer**
A rule-based, regex-driven post-processing module runs after translation decoding. It removes duplicate tokens, eliminates repeated auxiliary verbs (e.g., "है है" → "है"), collapses redundant morphological suffixes, and normalizes whitespace. This layer improves output fluency at negligible computational cost without increasing model size.

**Text-to-Speech Engine**
Android's built-in offline Hindi TTS engine synthesizes the corrected Hindi text into speech output. This eliminates the need to bundle a heavy neural TTS model, conserving device storage and runtime memory.

**Concurrency Architecture**
Audio capture operates on a dedicated background thread. Translation inference is dispatched via Kotlin coroutines on background dispatchers. UI updates are posted to the main thread. This separation prevents pipeline stages from blocking each other and ensures UI responsiveness throughout the translation process.

---

## Technology Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| ASR | Vosk (Kaldi-based) | Stable offline streaming ASR, lightweight model options, no cloud dependency |
| NMT (Translation) | Transformer (ONNX) | Efficient encoder-decoder architecture, portable model format |
| Inference Runtime | ONNX Runtime (Android) | ARM-optimized CPU kernels, NEON acceleration, broad model support |
| Tokenization | Custom vocab + subword logic | Lightweight, precisely matched to translation model training vocabulary |
| Linguistic Correction | Rule-based Kotlin layer | Improves Hindi fluency without increasing model size or inference cost |
| TTS | Android Offline Hindi TTS | No bundled heavy model required; native platform integration |
| Concurrency | Kotlin Coroutines | Structured background execution with lifecycle-aware cancellation |
| Audio Capture | Android AudioRecord API | Low-latency PCM streaming at 16 kHz mono |
| UI Rendering | Custom WaveformView | Energy-based amplitude visualization with minimal CPU overhead |

**Alternatives Evaluated**

- Cloud APIs (Google Translate, Azure Cognitive Services) were rejected due to connectivity dependency and data privacy concerns.
- TensorFlow Lite was evaluated as an alternative inference engine but rejected in favour of ONNX Runtime due to smoother integration with the selected transformer model architecture.
- A neural TTS model was considered but excluded to keep the storage footprint within acceptable bounds for mid-range devices.

---

## Repository Structure

```
PipelineClean/
|
+-- app/
|   +-- src/
|   |   +-- main/
|   |       +-- AndroidManifest.xml          # Permissions (RECORD_AUDIO)
|   |       |
|   |       +-- java/com/example/pipelineclean/
|   |       |   +-- MainActivity.kt          # Entry point, pipeline orchestration
|   |       |   +-- WaveformView.kt          # Real-time audio waveform rendering
|   |       |   +-- translator/
|   |       |       +-- ModelManager.kt      # ONNX session initialization and reuse
|   |       |       +-- Translator.kt        # Tokenization, inference, decoding
|   |       |
|   |       +-- assets/
|   |       |   +-- model/                   # Vosk ASR acoustic model (not committed)
|   |       |   +-- translation_model.onnx   # ONNX translation model (not committed)
|   |       |   +-- vocab.json               # Tokenizer vocabulary
|   |       |
|   |       +-- res/
|   |           +-- layout/activity_main.xml # Main UI layout
|   |           +-- drawable/                # UI background and button assets
|   |           +-- values/                  # Colors, strings, themes
|   |
|   +-- build.gradle                         # Module-level dependencies
|   +-- proguard-rules.pro
|
+-- build.gradle                             # Project-level build config
+-- settings.gradle
+-- gradle/wrapper/                          # Gradle wrapper for reproducible builds
+-- gradlew / gradlew.bat
```

**Key Files**

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Pipeline entry point; coordinates ASR, translation, and TTS stages |
| `Translator.kt` | Implements ONNX inference, tokenization, and output decoding |
| `ModelManager.kt` | Manages ONNX Runtime session lifecycle and persistent session reuse |
| `WaveformView.kt` | Custom view for energy-based waveform visualization |
| `vocab.json` | Subword vocabulary file used by the tokenizer |
| `AndroidManifest.xml` | Declares `RECORD_AUDIO` permission |
| `activity_main.xml` | Defines the main UI layout |

---

## Installation & Setup

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 (recommended) |
| Gradle | 8.0 or newer |
| Kotlin | 2.0.x (do not upgrade arbitrarily) |
| Android SDK | API 24+ (minimum), API 34 (target) |
| ADB | Required for device deployment |

### Step 1 — Clone the Repository

```bash
git clone https://github.com/<your-username>/BhashaBridge.git
cd BhashaBridge
```

### Step 2 — Download Required Model Files

Model files are excluded from the repository due to size constraints. Download and place them as follows:

**Vosk ASR Model** (English, small variant recommended for mobile):

Download from: https://alphacephei.com/vosk/models

Place the extracted model directory at:

```
app/src/main/assets/model/
```

The directory must contain the `am/`, `conf/`, `graph/`, and `ivector/` subdirectories.

**ONNX Translation Model**:

Place the model file at:

```
app/src/main/assets/translation_model.onnx
```

**Tokenizer Vocabulary**:

The `vocab.json` file is included in the repository at:

```
app/src/main/assets/vocab.json
```

### Step 3 — Verify Environment Variables

Ensure the following are configured in your shell environment:

```bash
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/jdk-17
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### Step 4 — Open in Android Studio

Open the project root directory (`PipelineClean/`) in Android Studio. Allow Gradle to sync automatically. Resolve any SDK or dependency prompts.

### Step 5 — Grant Device Permissions

On the target Android device:
- Enable **Developer Options** and **USB Debugging**
- Connect the device via USB
- Grant `RECORD_AUDIO` permission when prompted on first launch

---

## Running the Application

### Debug Build and Install

```bash
# Clean and build
./gradlew clean assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Release Build

```bash
./gradlew assembleRelease
```

Sign the APK before distribution. Refer to Android documentation for keystore configuration.

### Expected Behavior

1. Launch the application on the target device.
2. Press and hold the microphone button.
3. Speak a phrase in English clearly.
4. Release the button.
5. The recognized English text appears in the input field.
6. The system automatically performs translation and synthesis.
7. The Hindi translation is displayed and spoken aloud via the device speaker.

---

## Performance Metrics

> Tested on: Snapdragon ARM64 device (4–6 GB RAM class)

### Pipeline Latency

| Pipeline Stage | Measured Latency (Typical) | Notes |
|----------------|---------------------------|-------|
| ASR (Vosk streaming) | ~200–400 ms | Real-time incremental decoding |
| NMT Translation | ~300–600 ms | ONNX Runtime on ARM CPU |
| Linguistic Correction | <5 ms | Regex-based deterministic rules |
| TTS Synthesis | ~100–200 ms | Android offline Hindi TTS |
| End-to-End Latency | **<1 second (short phrases)** | No cloud dependency |
| Startup Model Load | ~2–4 seconds | One-time session initialization |
| CPU Usage (peak inference) | 60–85% (short burst) | Returns to idle after inference |

### Model Sizes

| Component | Size | Format | Quantization |
|-----------|------|--------|--------------|
| ASR (Vosk small English) | ~45–90 MB | Kaldi / Vosk | FP16 |
| NMT Transformer (English→Hindi) | ~120–200 MB | ONNX | FP16 (balanced) |
| Tokenizer (vocab.json) | ~2–3 MB | JSON | — |
| Linguistic Correction Layer | <1 MB | Kotlin (rule-based) | — |
| Android Hindi TTS | System-level | Native | — |
| Total APK (excluding system TTS) | ~170–290 MB | — | — |
| Peak Runtime RAM | ~300–500 MB | — | — |

**Trade-offs**

Aggressive INT8 quantization was experimentally observed to degrade translation semantic accuracy, particularly on sentences with complex verb agreement and morphological inflection. A balanced FP16 configuration was selected to preserve output fidelity while maintaining deployability on ARM CPU without NPU or GPU acceleration.

---

## Optimization for ARM SoC

### Quantization

INT8 quantization was evaluated to reduce model size and improve inference throughput. However, testing revealed significant semantic degradation under full INT8 quantization for the translation model. A balanced precision strategy was adopted, applying quantization selectively to layers where fidelity impact was minimal. This preserves translation quality while remaining deployable on ARM CPU without hardware accelerators.

### Model Format and Execution

The translation model is exported in ONNX format, which allows ONNX Runtime to apply backend-specific optimizations at load time. ONNX Runtime for Android includes ARM-optimized execution providers that leverage NEON SIMD instructions present on ARMv8 processors. This enables efficient tensor operations without requiring GPU or NPU availability.

### Persistent Session Reuse

Model sessions are initialized once during application startup and reused across all inference calls. This eliminates per-request session creation overhead, prevents memory fragmentation from repeated allocations, and ensures stable performance over long usage sessions. Both the ONNX Runtime inference session and the Vosk recognizer instance follow this persistent lifecycle pattern.

### Memory Management

Model assets are loaded from the `assets` directory into internal storage once. The Vosk model is extracted to `filesDir` on first run and accessed from there on subsequent launches, avoiding repeated asset extraction. Memory allocation for audio buffers is fixed-size and pre-allocated to prevent garbage collection pressure during active recording.

### Energy-Efficient Waveform Visualization

Signal energy averaging replaces full FFT-based spectral analysis for waveform visualization. This reduces CPU overhead during recording by avoiding computationally expensive frequency domain transformations, freeing processor cycles for the ASR and translation pipeline.

### Audio Lifecycle Engineering

Atomic start/stop state transitions prevent concurrent access to `AudioRecord` resources. Recording threads are joined before releasing audio resources to eliminate race conditions. This ensures deterministic resource cleanup under rapid or repeated usage, directly improving thermal and power behavior on constrained hardware.

---

## Innovation & Novelty

BhashaBridge's innovation is architectural and systems-level rather than algorithmic. The differentiating contributions are described below.

### 1. Lightweight Linguistic Correction Layer

Most offline translation systems address output quality issues by scaling model size, increasing memory requirements and inference latency. BhashaBridge instead implements a deterministic post-processing correction module that removes duplicate tokens, eliminates auxiliary verb repetition, collapses redundant morphological constructs, and normalizes whitespace. This approach produces measurable improvements in Hindi grammatical fluency at near-zero computational cost, with no increase in model size or RAM footprint.

### 2. Stability-Oriented Audio Lifecycle Design

Standard implementations of streaming ASR on Android frequently exhibit race conditions between recording threads and UI state management. BhashaBridge redesigned the audio pipeline with atomic transition semantics: explicit thread joining before resource release, thread-safe state flags, and prevention of recursive stop calls. This eliminated an entire class of intermittent crash states that are common in naive implementations.

### 3. Production-Grade Inference Session Management

Prototype-level implementations commonly reload model sessions per inference call, causing memory fragmentation and latency spikes. BhashaBridge maintains persistent ONNX Runtime and Vosk sessions across the application lifecycle, reflecting production-grade inference management practices. This ensures stable, consistent performance during extended usage sessions.

### 4. Empirically Validated Quantization Strategy

Rather than applying aggressive INT8 quantization as a blanket optimization, the project experimentally evaluated the accuracy-efficiency trade-off for this specific model. Observed semantic degradation under full INT8 quantization led to selection of a balanced precision configuration that preserves translation fidelity while remaining deployable on ARM CPU. This avoids the misleading optimization claims common in academic prototypes.

### 5. Fully Isolated Edge Deployment

The system operates with no cloud fallback, no hidden network dependencies, and no assumption of GPU or NPU availability. All components — ASR, translation, correction, and TTS — execute on ARM CPU. This represents genuine edge AI deployment discipline, not a hybrid system relabelled as offline.

---

## Limitations

- **Single language pair**: The current implementation supports English-to-Hindi translation only.
- **ASR accuracy under noise**: Vosk recognition performance degrades in environments with significant background noise. No noise suppression preprocessing is currently implemented.
- **Complex sentence fluency**: Long or syntactically complex input sentences may produce reduced translation fluency. The correction layer addresses surface artifacts but does not resolve deep structural translation errors.
- **Storage requirements**: Combined ASR and translation model assets require 90–350 MB of device storage depending on model variants selected.
- **No streaming translation**: Translation inference is triggered after full ASR finalization. Streaming translation is not yet implemented, introducing a perceptible pause between speech completion and Hindi output.
- **Hindi TTS quality**: The system relies on Android's built-in Hindi TTS, whose naturalness varies by device and Android version. A neural TTS model was not bundled due to storage constraints.

---

## Future Improvements

- **Multilingual expansion**: Extend translation to additional Indian language pairs (e.g., English-to-Tamil, English-to-Bengali) using multilingual transformer models.
- **Streaming translation**: Implement incremental token generation to begin synthesis before ASR finalization, reducing perceived latency.
- **Knowledge distillation**: Apply structured pruning and knowledge distillation to reduce model size without proportionate accuracy loss.
- **ARM NPU acceleration**: Investigate integration with ARM Ethos NPU execution providers in ONNX Runtime to offload inference from the CPU and reduce power consumption.
- **Noise suppression preprocessing**: Integrate lightweight noise suppression (e.g., RNNoise or WebRTC VAD) before ASR input to improve recognition accuracy in field conditions.
- **Neural TTS integration**: Evaluate lightweight neural TTS models (e.g., distilled FastSpeech variants) for improved Hindi speech naturalness while maintaining acceptable storage overhead.
- **Bidirectional translation**: Add Hindi-to-English reverse pipeline for conversational use cases.

---

## Hindi TTS Setup — Required Device Configuration

BhashaBridge uses Android's built-in Text-to-Speech engine for Hindi speech output. This engine is **not active by default** on most Android devices. Both users and evaluators must complete the following one-time setup before the application can produce spoken Hindi output.

> **This step is mandatory. If the Hindi TTS voice is not installed and enabled, the application will either produce no audio output or fall back to a non-Hindi voice.**

---

### Step 1 — Install a Hindi TTS Voice Pack

Android's TTS engine requires a language-specific voice pack to be downloaded before use. The process is the same on stock Android and most manufacturer skins (Samsung One UI, Xiaomi MIUI, Realme UI, etc.), though menu labels may vary slightly.

**On Stock Android (Google TTS Engine):**

1. Open **Settings** on your device.
2. Navigate to **General Management** → **Language and Input** → **Text-to-Speech Output**.
   - On some devices: **Settings** → **Accessibility** → **Text-to-Speech Output**.
3. Ensure **Google Text-to-Speech Engine** is selected as the preferred engine.
4. Tap the **Settings icon (gear icon)** next to Google Text-to-Speech Engine.
5. Tap **Install voice data**.
6. Locate **Hindi (India)** — `हिन्दी (भारत)` — in the language list.
7. Tap the **Download icon** next to Hindi.
8. Wait for the download to complete. An active internet connection is required for this one-time download only.
9. Once installed, return to the previous screen.

**On Samsung Devices (One UI):**

1. Open **Settings** → **General Management** → **Language and Input**.
2. Tap **Text-to-Speech**.
3. Select **Samsung TTS** or **Google TTS** as the preferred engine (Google TTS is recommended for Hindi support).
4. Follow steps 4–9 above for Google TTS voice installation.

**On Xiaomi / Redmi Devices (MIUI):**

1. Open **Settings** → **Additional Settings** → **Language & Input**.
2. Tap **Text-to-Speech output**.
3. Confirm **Google Text-to-Speech Engine** is selected.
4. Follow steps 4–9 above.

---

### Step 2 — Set Hindi as the TTS Language and Enable Offline Mode

After the voice pack is downloaded:

1. Return to **Text-to-Speech Output** settings.
2. Tap the **gear icon** next to Google Text-to-Speech Engine.
3. Under **Language**, select **Hindi (India)**.
4. Look for a **Download** or **Offline speech recognition** option and ensure the Hindi offline data is marked as installed.

> On some Android versions, the offline voice pack is separate from the standard voice pack. If you see a prompt to download an offline version of Hindi, download it to ensure the TTS functions without an internet connection.

---

### Step 3 — Verify TTS is Functional

To confirm Hindi TTS is working correctly before running BhashaBridge:

1. In the **Text-to-Speech settings**, locate the **Listen to an example** or **Play** button.
2. If the engine is set to Hindi, it should speak a sample sentence in Hindi.
3. If you hear English or no output, confirm the language selection in Step 2 was saved correctly.

Alternatively, you can verify via Android's accessibility settings:

1. Go to **Settings** → **Accessibility** → **Text-to-Speech Output**.
2. Confirm the language shown is **Hindi (India)**.
3. Press the play/preview button to test.

---

### Step 4 — Configure Speech Rate and Pitch (Optional)

BhashaBridge uses the system TTS defaults. If the speech output sounds too fast or unnatural:

1. Go to **Text-to-Speech Output** settings.
2. Adjust the **Speech Rate** slider (recommended: 0.8x–1.0x for Hindi).
3. Adjust the **Pitch** slider if desired.

These settings apply system-wide and will affect BhashaBridge's output.

---

### Troubleshooting

| Symptom | Likely Cause | Resolution |
|---------|-------------|------------|
| No audio output after translation | Hindi TTS voice not installed | Complete Steps 1 and 2 above |
| Output is in English, not Hindi | TTS language not set to Hindi | Set language to Hindi (India) in TTS engine settings |
| TTS produces robotic or broken audio | Offline voice pack not downloaded | Download the offline Hindi voice pack (Step 2) |
| App crashes on TTS call | TTS engine not initialized | Restart the device after completing TTS setup |
| "Language not supported" error | Google TTS not selected as engine | Switch preferred engine to Google Text-to-Speech |

---

### Notes for Evaluators

- The Hindi voice pack download requires an internet connection **once** during setup. After installation, TTS operates entirely offline.
- The application itself performs all ASR and translation processing offline at all times. The TTS internet dependency is limited to the one-time voice pack installation.
- If evaluating on an emulator, ensure the emulator image includes Google Play Services (required for Google TTS voice pack installation). API images without Google Play will not support voice pack download.
- Minimum recommended Android version for stable Hindi TTS support: **Android 7.0 (API 24)**.

---

## Contributing

Contributions are welcome. To contribute to this project:

1. Fork the repository and create a feature branch from `main`.
2. Follow the existing code style and architecture conventions.
3. Ensure all changes are tested on a physical ARM-based Android device (API 24 minimum).
4. Do not commit model files, build artifacts, or local configuration files (see `.gitignore`).
5. Submit a pull request with a clear description of the change, the problem it addresses, and any observed performance impact.

For significant architectural changes, open an issue for discussion before submitting a pull request.

**Areas where contributions are particularly welcome:**
- Noise suppression preprocessing
- Additional language pair support
- ARM NPU inference backend integration
- Improved tokenizer support for domain-specific vocabulary

---

## License

This project is currently unlicensed. A license will be specified prior to public release. All rights reserved until a license is formally applied.

---

## Contact

For technical queries, collaboration proposals, or submission-related correspondence, please contact:

**Project Maintainer**: V Paresh Kumar 
**Email**:  vpareshkumar.ece2024@citchennai.net 
**Institution**: Chennai Institute of Technology 
**GitHub**: [https://github.com/pareshpk

---

*Submitted for ARM Bharat SoC Challenge — Problem Statement 4*
