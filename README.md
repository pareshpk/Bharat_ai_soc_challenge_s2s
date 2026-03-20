# BhashaBridge V3.4

**Offline Speech-to-Speech Translation for Android**

Bidirectional, real-time, fully offline translation between spoken English and spoken Hindi. Runs entirely on ARM Android devices using ONNX Runtime with ARM NEON SIMD acceleration. No cloud. No internet. No GPU required. Pure on-device inference.

Built for the **Bharat AI-SoC Challenge 2026** — Problem Statement 4: Real-Time On-Device Speech-to-Speech Translation using NEON on ARM CPU.

<p align="center">
  <img src="V3/app/src/main/res/drawable/logo_bhashabridge.png" alt="BhashaBridge Logo" width="350"/>
</p>

---

## Features

- **Fully offline** — zero network dependency, all inference runs on-device
- **Bidirectional translation** — English → Hindi and Hindi → English, switchable at runtime
- **Indian English ASR** — Vosk model trained on Indian English (NPTEL dataset) for accurate recognition of Indian accents
- **Real-time speech pipeline** — Vosk ASR → IndicTrans2 NMT → Android TTS, end-to-end in 250–700ms
- **Streaming translation** — partial results appear while you are still speaking
- **Text translation** — type or paste input, translate instantly with audio playback
- **Voice file import** — upload a pre-recorded audio file (MP3, M4A, WAV, OGG) and translate through the same on-device pipeline
- **Emergency Phrases** — 32 pre-translated critical phrases across four categories: Basic, Medical, Safety, and Location; one tap plays the phrase in the target language instantly, no model involved
- **Translation history** — last five translations stored locally on-device
- **Hindi TTS detection** — detects missing Hindi voice pack on first launch and guides user to install it
- **Interface language switching** — app UI available in English and Hindi, switchable from the side menu
- **INT8 quantization** — IndicTrans2 models quantized from float32 to int8, ~4× size reduction with less than 5% accuracy loss
- **ARM NEON SIMD acceleration** — ONNX Runtime leverages ARM vector instructions to accelerate transformer matrix operations on CPU

---

## What This App Does

1. **Onboarding screen** introduces the app with three core features: speak in English or Hindi, instant offline translation, and emergency phrases ready.
2. **Language selection** asks the user to choose their preferred interface language on first launch, with a Hindi TTS availability check.
3. **Model loading** initialises the Vosk ASR model and IndicTrans2 ONNX models on a background thread with a progress indicator.
4. **Main translator screen** provides a language pair selector (English ↔ Hindi with a swap button), text input area, Translate button, translation output area, a speech mode microphone with live waveform visualiser, confidence indicator, and the Emergency Phrases button.
5. **Speech mode** — tap the mic, speak naturally; Vosk ASR transcribes, IndicTrans2 translates, Android TTS speaks the output aloud. The "Heard:" hint shows what the ASR understood for transparency.
6. **Emergency Phrases panel** provides categorised pre-translated safety phrases with one-tap audio playback.
7. **Side menu** (hamburger icon ☰) provides access to History, Import Audio, and Language settings.

---

## Pipeline Architecture

```
Microphone / Audio File (16 kHz PCM)
        │
        ▼
┌───────────────────────────────────────┐
│   Vosk ASR  (Speech → Text)           │
│   vosk-model-small-en-in-0.4          │
│   Indian English, offline             │
│   180 – 350 ms                        │
└───────────────┬───────────────────────┘
                │  Raw recognised text
                ▼
┌───────────────────────────────────────┐
│   ASRCorrector                        │
│   50+ Indian English grammar rules    │
│   Grammar disambiguation (4 rules)    │
│   < 2 ms                              │
└───────────────┬───────────────────────┘
                │  Corrected text
                ▼
┌───────────────────────────────────────┐
│   SentencePiece Tokenizer (Kotlin)    │
│   Subword tokenization                │
│   IndicTrans2 format:                 │
│   [src_lang, tgt_lang, tokens, </s>]  │
└───────────────┬───────────────────────┘
                │  Token IDs
                ▼
┌───────────────────────────────────────┐
│   IndicTrans2 NMT (ONNX int8)         │
│   Transformer encoder-decoder         │
│   ONNX Runtime + ARM NEON SIMD        │
│   Greedy decode, maxLength=18         │
│   Encoder: ~100 ms                    │
│   Decoder: ~220 ms (8 steps)          │
└───────────────┬───────────────────────┘
                │  Translated text
                ▼
┌───────────────────────────────────────┐
│   EnPostProcessor (HI→EN only)        │
│   Capitalisation, pronoun fix,        │
│   duplicate removal  —  < 1 ms        │
└───────────────┬───────────────────────┘
                │
                ▼
┌───────────────────────────────────────┐
│   Android Text-to-Speech              │
│   Hindi voice with availability check │
│   < 5 ms                              │
└───────────────┬───────────────────────┘
                │
                ▼
        Speaker Output

Total end-to-end (3–5 word sentence): 250 – 450 ms
Total end-to-end (6–8 word sentence): 450 – 700 ms
```

---

## Tech Stack

| Component | Choice | Reason |
|---|---|---|
| ASR | Vosk (vosk-model-small-en-in-0.4) | Indian English model trained on NPTEL; handles Indian accents natively |
| NMT | IndicTrans2 (ONNX int8) | State-of-the-art open-source EN↔HI model by AI4Bharat, IIT Madras |
| Inference Runtime | ONNX Runtime 1.17.1 (Android) | ARM NEON SIMD acceleration; unified inference for encoder and decoder |
| Tokenization | Custom SentencePiece (Kotlin) | Pure Kotlin — no JNI, no external library dependency |
| TTS | Android native TTS | On-device speech synthesis with Hindi availability detection |
| Quantization | INT8 (post-training) | ~4× size reduction with <5% accuracy loss |
| ASR Correction | Custom rule engine (Kotlin) | 50+ Indian English grammar rules + 4 structural disambiguation rules |

---

## Model Details

| Component | Size | Format | Notes |
|---|---|---|---|
| Vosk ASR (Indian English) | 36 MB | Kaldi/Vosk | vosk-model-small-en-in-0.4, trained on NPTEL |
| Vosk ASR (Hindi) | ~45 MB | Kaldi/Vosk | Loaded on demand for HI→EN direction |
| IndicTrans2 EN→HI Encoder | 72 MB | ONNX int8 | Quantized from float32 |
| IndicTrans2 EN→HI Decoder | 195 MB | ONNX int8 | Quantized from float32 |
| IndicTrans2 HI→EN Encoder | 116 MB | ONNX int8 | Quantized from float32 |
| IndicTrans2 HI→EN Decoder | 107 MB | ONNX int8 | Quantized from float32 |
| SentencePiece vocab dicts | ~4 MB | JSON | Pre-serialized for Kotlin |
| **Total APK** | **~577 MB** | | All models bundled |

---

## Performance

### Latency Breakdown (Samsung Galaxy M31, Exynos 9611, 6GB RAM)

| Pipeline Stage | Latency |
|---|---|
| Vosk ASR (speech → text) | 180 – 350 ms |
| ASRCorrector | < 2 ms |
| IndicTrans2 Encoder | ~100 ms |
| IndicTrans2 Decoder (8 steps) | ~220 ms |
| Android TTS | < 5 ms |
| **Total (3–5 word sentence)** | **250 – 450 ms** |
| **Total (6–8 word sentence)** | **450 – 700 ms** |

### Comparison

| System | Latency | Offline |
|---|---|---|
| **BhashaBridge V3.4** | **250 – 700 ms** | **Yes** |
| Google Translate (online) | ~2800 ms | No |
| Microsoft Translator (online) | ~3100 ms | No |

BhashaBridge is **4 to 6 times faster** than cloud-based systems and operates with **zero internet dependency**.

---

## Device Requirements

| Requirement | Value |
|---|---|
| Architecture | arm64-v8a (ARM NEON required) |
| Android version | Android 7.0 (API 24) or higher |
| RAM | 3 GB or more recommended |
| Storage | ~600 MB free for APK + models |
| Permissions | Microphone, Storage (for audio import) |

---

## Installation

### Install APK directly

Download the latest APK from the [Releases](../../releases) section and install on your Android device.

> Enable **Install from unknown sources** in device settings if prompted.

### Build from source

```bash
# Clone the repository
git clone https://github.com/pareshpk/Bharat_ai_soc_challenge_s2s.git
cd Bharat_ai_soc_challenge_s2s/V3

# Place model files in app/src/main/assets/
# (see Model Setup below)

# Build
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Fresh install — reset onboarding
adb shell pm clear com.example.bhashabridge_v3_4
```

### Model Setup

Place the following files under `app/src/main/assets/`:

```
assets/
├── model/                          # Vosk Indian English ASR model
├── model-hi/                       # Vosk Hindi ASR model
├── encoder_model_int8.onnx         # IndicTrans2 EN→HI encoder
├── decoder_model_int8.onnx         # IndicTrans2 EN→HI decoder
├── hi_en_encoder_int8.onnx         # IndicTrans2 HI→EN encoder
├── hi_en_decoder_int8.onnx         # IndicTrans2 HI→EN decoder
├── dict.SRC.json                   # EN→HI source vocabulary
├── dict.TGT.json                   # EN→HI target vocabulary
├── dict.SRC_HI.json                # HI→EN source vocabulary
└── dict.TGT_EN.json                # HI→EN target vocabulary
```

> Models are not included in the repository due to size. Download via the APK from [Releases](../../releases) — all models are bundled inside.

---

## Usage

1. **Launch** the app — onboarding screen with tricolour BhashaBridge logo
2. **Select interface language** — English or Hindi; Hindi TTS availability is checked here
3. **Wait for model initialisation** — loading overlay shows progress
4. **Translate by text** — type in the input box and tap **TRANSLATE**
5. **Translate by speech** — tap the microphone, speak naturally, tap again to stop
6. **Swap direction** — tap ⇄ to toggle EN→HI / HI→EN; loading overlay appears for first HI→EN switch
7. **Emergency Phrases** — tap **🆘 EMERGENCY PHRASES**; tap any phrase to hear it instantly
8. **Import Audio** — side menu ☰ → Import Audio; supports MP3, M4A, WAV, OGG
9. **View History** — side menu ☰ → History; last 5 translations
10. **Change Language** — side menu ☰ → Language

---

## Emergency Phrases Reference

| Category | Example Phrases |
|---|---|
| **Basic** | I need help, Please help me, Emergency, I am in danger, Stay calm, Do not panic |
| **Medical** | Call an ambulance, Call a doctor, I am injured, I am bleeding, I cannot breathe, I have chest pain, He is unconscious, I need medicine, I am diabetic, I am allergic |
| **Safety** | Call the police, Fire, There is a fire, Earthquake, Flood, I am lost, Get out now, Run away, Do not touch that |
| **Location** | Where is the hospital, Where is the police station, Take me to the hospital, What is this place, I need water, I need food, Is there a shelter nearby |

---

## Project Structure (V3/)

```
V3/
├── app/
│   └── src/main/
│       ├── java/com/example/bhashabridge_v3_4/
│       │   ├── MainActivity.kt              # Main translator screen
│       │   ├── OnboardingActivity.kt        # First launch onboarding
│       │   ├── SetupActivity.kt             # Language + TTS setup
│       │   ├── Translator.kt                # IndicTrans2 ONNX inference
│       │   ├── SentencePieceTokenizer.kt    # Custom tokenizer
│       │   ├── ASRCorrector.kt              # Indian English correction layer
│       │   ├── AudioFileTranslator.kt       # Audio file import + transcription
│       │   ├── ModelManager.kt              # ONNX session factory
│       │   ├── WaveformView.kt              # Live audio waveform
│       │   ├── EmergencyPhrases.kt          # Pre-loaded phrase data
│       │   └── FileUtils.kt                 # Asset copy utilities
│       ├── assets/                          # Model files (not in repo — see Setup)
│       └── res/                             # Layouts, drawables, strings
└── build.gradle
```

---

## Troubleshooting

| Issue | Fix |
|---|---|
| App crashes on launch | Ensure all model files are in `assets/` — see Model Setup above |
| Loading screen hangs | Check available storage — at least 600 MB free required |
| No speech recognition | Grant **Microphone** permission in device settings |
| Hindi audio sounds wrong | Hindi TTS voice pack may be missing — tap the red banner to install |
| Audio import not working | Grant **Storage** permission; supported formats: WAV, MP3, M4A, OGG |
| High latency on first translation | First inference is slower due to ONNX Runtime warm-up; subsequent translations are faster |
| HI→EN direction frozen | First switch loads Hindi models (~10–15s); loading overlay shows progress |

---

## Built With

- [Vosk](https://alphacephei.com/vosk/) — offline speech recognition (Apache 2.0)
- [IndicTrans2](https://github.com/AI4Bharat/IndicTrans2) — neural machine translation for Indian languages by AI4Bharat, IIT Madras (MIT)
- [ONNX Runtime](https://onnxruntime.ai/) — cross-platform inference engine with ARM NEON acceleration (MIT)
- [Android TTS](https://developer.android.com/reference/android/speech/tts/TextToSpeech) — on-device speech synthesis

---

## Team

**Chennai Institute of Technology** — Bharat AI-SoC Challenge 2026

| Name | Department |
|---|---|
| Vishnu Vardhan KS | Electronics and Communication Engineering |
| V Paresh Kumar | Electronics and Communication Engineering |
| Yugawathi E | Electronics and Communication Engineering |

---

## License

Submitted as part of the Bharat AI-SoC Challenge 2026. Third-party licenses:

| Component | License |
|---|---|
| Vosk | Apache 2.0 |
| IndicTrans2 | MIT |
| ONNX Runtime | MIT |
| Android TTS | Apache 2.0 |
