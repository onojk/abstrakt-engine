#!/usr/bin/env bash
# Dev script for abstrakt engine — build / install / run the DEBUG build
# (com.onojk.abstrakt.debug) on a physical device over USB (Galaxy S25 by default).
# The debug build installs side-by-side with the Play Store release
# (com.onojk.abstrakt); this script never touches the release package.
#
# Target a specific device with: ANDROID_SERIAL=<serial> ./dev.sh <cmd>
set -euo pipefail

cd "$(dirname "$0")"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
export ANDROID_SERIAL="${ANDROID_SERIAL:-RFCYA06RMTD}"
ADB="adb"
EMULATOR="${ANDROID_HOME}/emulator/emulator"
AVD="${AVD:-Pixel_8}"
PKG="com.onojk.abstrakt.debug"
# Activity class keeps the base namespace; only the applicationId gets the suffix.
ACTIVITY="com.onojk.abstrakt.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"

cmd="${1:-help}"

build()   { ./gradlew assembleDebug; }
install() { "$ADB" install -r "$APK"; }
run()     { "$ADB" shell am start -n "$PKG/$ACTIVITY"; }

case "$cmd" in
  build)
    echo "Building debug APK..."
    build
    ;;
  install)
    echo "Installing $APK..."
    install
    ;;
  run)
    echo "Launching $PKG..."
    run
    ;;
  bir)
    echo "==> Build";   build
    echo "==> Install"; install
    echo "==> Run";     run
    ;;
  log)
    pid="$("$ADB" shell pidof -s "$PKG" || true)"
    if [[ -z "$pid" ]]; then
      echo "$PKG is not running (try ./dev.sh run first)." >&2
      exit 1
    fi
    "$ADB" logcat --pid="$pid" -v brief
    ;;
  devices)
    "$ADB" devices -l
    ;;
  mirror)
    ver="$(scrcpy --version 2>/dev/null | head -1 | awk '{print $2}')"
    major="${ver%%.*}"
    if [[ "${major:-0}" -ge 2 ]]; then
      rate=(--video-bit-rate 8M)
    else
      rate=(--bit-rate 8M)
    fi
    scrcpy --serial "$ANDROID_SERIAL" --max-size 1920 --stay-awake "${rate[@]}"
    ;;
  uninstall)
    # Debug package only — never the Play Store release (com.onojk.abstrakt).
    echo "Uninstalling $PKG..."
    "$ADB" uninstall "$PKG"
    ;;
  emu)
    # Optional: start an emulator instead of using the phone.
    # Run other commands with ANDROID_SERIAL=emulator-5554 afterwards.
    echo "Starting $AVD emulator in background..."
    ANDROID_AVD_HOME="$ANDROID_AVD_HOME" "$EMULATOR" -avd "$AVD" -no-snapshot-save &
    ;;
  *)
    echo "Usage: ./dev.sh <command>   (device: $ANDROID_SERIAL)"
    echo ""
    echo "  build      ./gradlew assembleDebug"
    echo "  install    adb install -r debug APK"
    echo "  run        launch $PKG"
    echo "  bir        build + install + run (one shot)"
    echo "  log        logcat filtered to the app's pid"
    echo "  devices    adb devices -l"
    echo "  mirror     scrcpy screen mirror (1920px, stay awake, 8M bitrate)"
    echo "  uninstall  remove the debug package only (release is never touched)"
    echo "  emu        optional: start $AVD emulator"
    ;;
esac
