#!/usr/bin/env bash
# Quick dev script for MyFistApp — build / install / run on Pixel 8 emulator
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
ADB="adb"
EMULATOR="${ANDROID_HOME}/emulator/emulator"
AVD="Pixel_8"
PKG="com.example.myfistapp"
ACTIVITY=".MainActivity"

cmd="${1:-help}"

case "$cmd" in
  emu)
    echo "Starting $AVD emulator in background..."
    ANDROID_AVD_HOME="$ANDROID_AVD_HOME" "$EMULATOR" -avd "$AVD" -no-snapshot-save &
    echo "Waiting for device..."
    "$ADB" wait-for-device
    echo "Emulator ready."
    ;;
  build)
    echo "Building debug APK..."
    ./gradlew assembleDebug
    ;;
  install)
    echo "Installing APK..."
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
    ;;
  run)
    echo "Launching $PKG..."
    "$ADB" shell am start -n "$PKG/$PKG$ACTIVITY"
    ;;
  bir)
    echo "==> Build"
    ./gradlew assembleDebug
    echo "==> Install"
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
    echo "==> Run"
    "$ADB" shell am start -n "$PKG/$PKG$ACTIVITY"
    ;;
  log)
    "$ADB" logcat --pid="$("$ADB" shell pidof -s "$PKG")" -v brief
    ;;
  devices)
    "$ADB" devices
    ;;
  *)
    echo "Usage: ./dev.sh <command>"
    echo ""
    echo "  emu      Start Pixel 8 emulator"
    echo "  build    ./gradlew assembleDebug"
    echo "  install  adb install debug APK"
    echo "  run      adb shell am start (launch app)"
    echo "  bir      build + install + run (one shot)"
    echo "  log      logcat filtered to this app"
    echo "  devices  adb devices"
    ;;
esac
