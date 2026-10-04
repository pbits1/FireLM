#!/bin/bash
# Build FireLM APK without Android Studio — pure CLI.
# Usage: ./build-apk.sh [debug|release] [--rebuild-native]
set -e
MODE="debug"
REBUILD=""

for arg in "$@"; do
  case "$arg" in
    release) MODE="release" ;;
    debug) MODE="debug" ;;
    --rebuild-native) REBUILD="-PrebuildNative=true" ;;
  esac
done

export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$ANDROID_HOME}
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}
export JAVA_HOME=${JAVA_HOME:-$HOME/.jdks/jbr-21.0.11}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

cd "$(dirname "$0")"
echo "== ANDROID_HOME=$ANDROID_HOME"
echo "== NDK=$ANDROID_NDK_HOME"
echo "== Java: $($JAVA_HOME/bin/java -version 2>&1 | head -n1)"
echo "== Building $MODE APK (100% offline, prebuilt jniLibs ready)..."

if [ "$MODE" = "release" ]; then
  ./gradlew :app:assembleRelease $REBUILD
  echo "APK: app/build/outputs/apk/release/app-release.apk"
  ls -lh app/build/outputs/apk/release/*.apk 2>/dev/null || true
else
  ./gradlew :app:assembleDebug $REBUILD
  echo "APK: app/build/outputs/apk/debug/app-debug.apk"
  ls -lh app/build/outputs/apk/debug/*.apk
fi

echo ""
echo "Install to phone (USB debugging ON):"
echo "  adb install -r app/build/outputs/apk/$MODE/app-$MODE.apk"
