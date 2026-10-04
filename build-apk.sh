#!/bin/bash
# Build FireLM APK without Android Studio — pure CLI.
# Usage: ./build-apk.sh [debug|release]
set -e
MODE=${1:-debug}
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
export PATH=$HOME/gradle/gradle-8.7/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
cd "$(dirname "$0")"
echo "== ANDROID_HOME=$ANDROID_HOME"
echo "== NDK=$ANDROID_NDK_HOME"
echo "== Java: $(java -version 2>&1 | head -n1)"
echo "== Gradle: $(gradle --version 2>&1 | grep Gradle | head -n1)"
echo "== Building $MODE APK (first build downloads llama.cpp + deps, ~5-10 min)..."
if [ "$MODE" = "release" ]; then
  gradle :app:assembleRelease --info
  echo "APK: app/build/outputs/apk/release/app-release.apk"
  ls -lh app/build/outputs/apk/release/*.apk
else
  gradle :app:assembleDebug
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
  ls -lh app/build/outputs/apk/debug/*.apk
fi
echo ""
echo "Install to phone (USB debugging ON):"
echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
