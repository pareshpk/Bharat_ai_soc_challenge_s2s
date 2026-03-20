# Bharat AI SoC Challenge — Speech-to-Speech Translation

## Project: BhashaBridge

Fully offline, bidirectional English ↔ Hindi speech-to-speech translator for Android.

## Repository Structure

| Folder | Description |
|---|---|
| `V1/` | BhashaBridge V1 — baseline implementation |
| `V3/` | BhashaBridge V3.4 — final submission |

## BhashaBridge V3.4 — Final Submission

### Download APK
[**Download BhashaBridge V3.4 APK**](https://github.com/pareshpk/Bharat_ai_soc_challenge_s2s/releases/tag/v3.4)

### Key Features
- Fully offline — no internet at any point
- Bidirectional EN→HI and HI→EN
- Indian English ASR model (vosk-model-small-en-in-0.4)
- Sub-500ms latency for conversational sentences
- Emergency phrases panel — 32 pre-loaded critical phrases
- Audio file import and translation
- Hindi TTS availability detection
- Bilingual UI (English + Hindi)

### Performance (Samsung Galaxy M31, Exynos 9611)
| Sentence length | Latency |
|---|---|
| 3–5 words | 250–450ms |
| 6–8 words | 450–700ms |

### Install
1. Download APK from Releases link above
2. Enable "Install unknown apps" on Android
3. Install — requires Android 7.0+, arm64-v8a

## Tech Stack
- Vosk (Indian English ASR — vosk-model-small-en-in-0.4)
- IndicTrans2 ONNX int8 (Neural Machine Translation by AI4Bharat)
- Android TTS with Hindi availability detection
- ONNX Runtime 1.17.1
- Custom SentencePiece tokenizer (Kotlin)
- Grammar-based ASR correction layer
