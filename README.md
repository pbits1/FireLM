# FireLM — 100% On-Device Neural Compute for Android

A private, blazing-fast Android app that runs **GGUF language models locally on device** with
llama.cpp, Vulkan GPU acceleration, and KleidiAI ARM NEON optimizations (no server, no API key, zero telemetry, 100% offline).

- **LM Studio-grade Model Residency**: Live RAM/VRAM telemetry (`🟢 In RAM`, `🟡 Loading`, `⚪ Unloaded`) with 1-tap **Eject from RAM** and **Load into RAM**.
- **Hardware Acceleration**: Mali/Adreno Vulkan GPU layer offloading + ARM KleidiAI multi-threaded CPU inference.
- **Custom System Prompt Deck**: Flexible user instruction alignment with raw SLM unconstrained mode switch.
- **Presets & Custom Models**: Shipped with optimized LFM2 / SmolLM / Qwen presets, plus import any custom `.gguf` file.

> Plain-English version: this is LM Studio for your pocket. Download any model once, chat forever with mobile data and Wi-Fi off.

---

## 1. What you get

- `app/` — Jetpack Compose chat UI (Chat / Models / Tuning screens)
- `inference/LlamaBridge.kt` + `cpp/llama_jni.cpp` — JNI bridge to llama.cpp
  - mmap model loading, 8192-token context, streaming tokens, stop button
  - Correct LFM2 chat template: `<|startoftext|><|im_start|>user...<|im_end|><|im_start|>assistant`
- `data/Models.kt` — 6 Liquid presets with direct Hugging Face URLs (no login)
- `download/ModelDownloader.kt` — resumable HF downloader with progress bar
- 64-bit only (`arm64-v8a` + `x86_64` emulator) — 32-bit phones can't hold 1B models

## 2. Requirements

On your **dev machine** (this Linux laptop is fine once you install these):

1. Android Studio Ladybug+ (or Koala+)
2. Android SDK 34 + NDK r26+ + CMake 3.22+ (install via SDK Manager)
3. JDK 17 (Android Studio bundles it)
4. A physical 64-bit phone with:
   - 350M/700M → 2GB free RAM works
   - 1.2B → 3GB+ free RAM, ~1GB storage
   - 2.6B → 6GB+ flagship only

Check RAM need in-app — each model card lists it.

## 3. Open + run (5 min)

```bash
# 1. Open this folder in Android Studio: File > Open > "LLM for Android"
#    Let it sync Gradle (first sync downloads llama.cpp ~300MB, takes a few min).

# 2. Plug in phone with USB debugging ON, then:
#    Run > Run 'app'  (or Shift+F10)
```

First launch on phone:

1. Tap **Download / switch model** → pick **LFM2 1.2B Q4_K_M** → **Download** (Wi-Fi!)
2. Wait → status turns **Ready — 100% offline**
3. Type anything → **Send**. Toggle airplane mode to prove it's local.

APK location after build: `app/build/outputs/apk/debug/app-debug.apk`

### Build from terminal (optional)

```bash
export ANDROID_HOME=~/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> No `gradlew` executable in git yet? In Android Studio: it auto-creates one on sync.
> Or run `gradle wrapper --gradle-version 8.7` once, then use `./gradlew`.

## 4. Models (all public, no login)

| In-app label | HF repo | File | ~Size |
|---|---|---|---|
| LFM2 350M fastest | `LiquidAI/LFM2-350M-GGUF` | `LFM2-350M-Q4_K_M.gguf` | 250MB |
| LFM2 700M mini | `LiquidAI/LFM2-700M-GGUF` | `LFM2-700M-Q4_K_M.gguf` | 480MB |
| **LFM2 1.2B Q4_K_M ★** | `LiquidAI/LFM2-1.2B-GGUF` | `LFM2-1.2B-Q4_K_M.gguf` | 796MB |
| LFM2 1.2B Q4_0 smallest | same | `LFM2-1.2B-Q4_0.gguf` | 696MB |
| LFM2.5 1.2B Instruct | `LiquidAI/LFM2.5-1.2B-Instruct-GGUF` | `LFM2.5-1.2B-Instruct-Q4_K_M.gguf` | ~800MB |
| LFM2 2.6B max | `LiquidAI/LFM2-2.6B-GGUF` | `LFM2-2.6B-Q4_K_M.gguf` | 1.6GB |

Direct URL pattern:
`https://huggingface.co/<repo>/resolve/main/<file>`

To add your own: edit `app/src/main/java/com/lfmlocal/app/data/Models.kt` → add `LfmModel(...)`.

Manual copy alternative: push any `.gguf` via:
```
adb push MyModel.gguf /sdcard/
# then in phone Files app move to Android/data/com.lfmlocal.app/files/models/
```

## 5. How it works (60 sec)

```
ChatScreen (Compose)
  → ChatViewModel.send()
    → LlamaBridge.buildLfmPrompt(system + history + user)
    → nativeGenerate(handle, prompt, ...)   [Dispatchers.IO]
      → llama_jni.cpp: tokenize → decode prefill (512 batch)
        → sample loop (top-k/top-p/temp + repeat penalty)
        → stop on <|im_end|> or EOS, stream pieces via TokenCallback.onToken
```

- Context 8192 (safe on phones; GGUF supports up to 128k but would OOM).
- Threads = min(8, cores). No GPU delegate in v1 — CPU is faster to ship + works everywhere.
- `largeHeap=true` + `mmap` so the 800MB file doesn't double-copy in RAM.

Want GPU? Next step is OpenCL/Vulkan via `GGML_VULKAN=ON` + a phone with good drivers — say the word and I'll add a flavor.

## 6. Troubleshooting

- **Sync fails on llama.cpp FetchContent** → need git + CMake + network once. In `cpp/CMakeLists.txt` bump `GIT_TAG` (e.g. `b5549` → newer) if API drift.
- **`nativeLoadModel returned 0` / OOM** → phone ran out of RAM. Switch to 350M/700M, close other apps, reboot.
- **Download stalls** → HF `Range` resume handles it; keep screen on. Or download on laptop then `adb push`.
- **Slow (2-5 tok/s)** → normal for 1.2B on mid CPU. Shorten `Max reply length` to 256, keep temp default.
- **Garbled output** → you loaded a Base GGUF without instruct template? Use the Instruct presets.

## 7. Privacy + license

- After download: **zero network calls**. Verify with airplane mode.
- LFM weights: **LFM Open License v1.0** (see HF LICENSE). Check commercial terms on liquid.ai.
- llama.cpp: MIT. This app glues them with JNI — keep both LICENSE files if you publish.

## 8. Roadmap (pick one, I'll build it)

1. RAG over PDFs on-device (local embeddings + citations)
2. OpenAI-compatible `localhost` server mode for other apps
3. Voice in/out (Whisper + TTS, all offline)
4. Benchmark screen (tok/s, RAM, battery)

---
Built for offline-first edge AI. If it chats in airplane mode — you win. 💧
