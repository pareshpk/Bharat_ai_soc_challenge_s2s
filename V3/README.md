# BhashaBridge V3.4

> Fully offline, bidirectional English ↔ Hindi speech-to-speech translator for Android — built for Indian users

## Overview

BhashaBridge is a fully offline Android application that performs real-time speech-to-speech translation between English and Hindi. It requires no internet connection at any point — all ASR, translation, and TTS processing happens entirely on-device. The system is built around the IndicTrans2 neural machine translation model, Vosk offline speech recognition (Indian English model), and Android's built-in TTS engine.

V3.4 upgrades the ASR model from US English to Indian English, adds Hindi TTS availability detection, a sliding drawer navigation menu, and a full-screen loading overlay on direction switch.

## Features

- **Fully offline** — no network calls at any stage
- **Bidirectional** — English→Hindi and Hindi→English
- **Indian English ASR** — Vosk model trained on Indian English (NPTEL dataset)
- **Speech input** — tap mic to start, tap again to stop
- **Streaming translation** — partial results appear while speaking
- **ASR correction** — grammar-based fixes tuned for Indian English patterns
- **Audio file import** — translate MP3, M4A, WAV, OGG files offline
- **Emergency phrases** — 32 pre-loaded phrases across 4 categories with TTS
- **Translation history** — last 5 translations accessible from drawer
- **Bilingual UI** — full Hindi and English interface
- **Hindi TTS check** — detects missing voice pack and guides user to install
- **Drawer navigation** — hamburger menu for History, Import Audio, Language
- **Indian flag branding** — tricolour logo across all screens

## Architecture

```
Microphone
    ↓
Vosk ASR (offline, vosk-model-small-en-in-0.4, 36MB)
    ↓
ASRCorrector (Indian English grammar fixes + disambiguation)
    ↓
IndicTrans2 ONNX (int8 quantized)
  Encoder → Decoder (greedy decode, maxLength=18)
    ↓
EnPostProcessor (HI→EN only)
    ↓
Output TextView + Android TTS
```

## Models

| Model | Task | Size | Format |
|---|---|---|---|
| vosk-model-small-en-in-0.4 | Indian English ASR | 36 MB | Kaldi/Vosk |
| encoder_model_int8.onnx | EN→HI encoding | 72 MB | ONNX int8 |
| decoder_model_int8.onnx | EN→HI decoding | 195 MB | ONNX int8 |
| hi_en_encoder_int8.onnx | HI→EN encoding | 116 MB | ONNX int8 |
| hi_en_decoder_int8.onnx | HI→EN decoding | 107 MB | ONNX int8 |

Total APK size: **~577 MB** (debug build, −32MB vs V3.2)

## Performance

| Sentence length | EN→HI latency | HI→EN latency |
|---|---|---|
| 3–5 words | ~250–450ms | ~300–500ms |
| 6–8 words | ~450–700ms | ~500–800ms |
| 10+ words | ~900–1400ms | ~1000–1500ms |

Measured on Samsung Galaxy M31 (Exynos 9611, 6GB RAM, Android 12).

## Installation

### Requirements
- Android 7.0+ (minSdk 24), arm64-v8a
- Android Studio or `adb` for installation

### Build from source
```bash
cd BhashaBridge_v3.4
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Fresh onboarding demo
```bash
adb shell pm clear com.example.bhashabridge_v3_4
```

## Usage

1. **First launch** — onboarding explains features; language selection with Hindi TTS check
2. **Translate by voice** — tap the mic button, speak, tap again to stop
3. **Translate by typing** — type in the input box, tap TRANSLATE
4. **Switch direction** — tap ⇄; loading overlay appears while Hindi models load
5. **Emergency phrases** — tap EMERGENCY PHRASES for instant pre-loaded translations
6. **Drawer menu** — tap ☰ for History, Import Audio, Language settings
7. **Import audio** — tap ☰ → Import Audio to translate an audio file

## What's New in V3.4

- **Indian English Vosk model** — replaces US model; better phoneme accuracy, 32MB smaller
- **ASRCorrector tuned for Indian model** — removed US-specific WH-word rules, added Indian grammar fixes
- **Hindi TTS detection** — warns user if Hindi voice pack missing, offers one-tap install
- **Loading overlay on swap** — full-screen "Loading Hindi model..." instead of silent mic disappearance
- **Drawer navigation** — hamburger menu replaces bottom action bar
- **Thread-safe model loading** — `AtomicBoolean` coordination for parallel HI→EN model init
- **Tricolour logo** — consistent branding across Onboarding, Setup, and loading screens
- **Mic hint fixed** — "Hold mic to speak" corrected to "Tap mic to speak"

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| vosk-android | 0.3.47 | Offline speech recognition |
| onnxruntime-android | 1.17.1 | ONNX model inference |
| androidx.multidex | 2.0.1 | DEX limit handling |
| kotlinx-coroutines-android | 1.7.3 | Async operations |
| androidx.appcompat | 1.6.1 | UI compatibility |
| androidx.drawerlayout | 1.2.0 | Navigation drawer |

## License

All rights reserved. Models are based on IndicTrans2 (AI4Bharat, MIT License) and Vosk (Alpha Cephei, Apache 2.0).
