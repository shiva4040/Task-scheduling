# Whisper Small Q5_1 Bundled Model Setup & Deployment Guide

This guide describes how the offline quantized OpenAI Whisper `small-q5_1` multilingual model is bundled inside the application and automatically deployed on Android devices (e.g., Samsung Galaxy Tab A9, SM-X115).

---

## 1. Model Specifications

- **Filename**: `ggml-small-q5_1.bin`
- **Model Variant**: Whisper `small-q5_1` (Multilingual Q5_1 GGML format, ~244M params)
- **File Size**: `190,085,487 bytes` (~181.28 MiB)
- **Official SHA-256**: `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`
- **Official Download URL**: [HuggingFace - ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin)
- **Bundled Asset Path**: `app/src/main/assets/models/ggml-small-q5_1.bin`

---

## 2. Self-Contained APK Architecture

The Whisper model is bundled directly into the APK assets.
- When you press **Run ▶** in Android Studio or install the debug APK (`./gradlew installDebug`), the model is packaged inside the APK.
- **Zero manual copying**: No manual file transfer, ADB push, or SD card staging required.
- **Zero runtime downloads**: The app never downloads the model over the internet at runtime.

---

## 3. Automatic First-Launch Installation & Storage Lifecycle

1. **First Launch**:
   - The app detects that `ggml-small-q5_1.bin` is not yet installed in internal storage (`files/models/whisper/`).
   - The bundled asset is streamed into a temporary file (`ggml-small-q5_1.bin.tmp`).
   - Size (`190,085,487 bytes`) and SHA-256 (`ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`) are cryptographically verified during copy.
   - The file is atomically renamed to `ggml-small-q5_1.bin`.
   - Fast verification metadata (`model_metadata.json`) is generated.
   - Any obsolete legacy models (e.g. old large-v3-turbo files) are deleted to reclaim space.
   - Model reports **READY**.

2. **Subsequent Launches**:
   - Metadata check completes in < 1ms.
   - Existing valid installed model is reused immediately without re-copying or re-hashing.

3. **Corruption Safeguards**:
   - If the installed file is modified, truncated, or corrupted, the app detects it, cleans it up, and re-installs a fresh copy from APK assets.

---

## 4. Verification

### Verification Task (ADB)
You can verify the on-device installed model at any time:
```powershell
.\gradlew.bat verifyWhisperModel
```
Reports the on-device filename, size, and SHA-256 hash.
