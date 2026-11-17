#!/usr/bin/env bash
set -euo pipefail
# Requires JDK 17, curl and unzip. SDK root can be outside the project.
piano_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$PWD/.android-sdk}}"
mkdir -p "$piano_sdk"
if [[ ! -x "$piano_sdk/cmdline-tools/latest/bin/sdkmanager" ]]; then
  piano_tmp=$(mktemp -d)
  trap 'rm -rf "$piano_tmp"' EXIT
  curl -fL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$piano_tmp/tools.zip"
  unzip -q "$piano_tmp/tools.zip" -d "$piano_tmp"
  mkdir -p "$piano_sdk/cmdline-tools"
  mv "$piano_tmp/cmdline-tools" "$piano_sdk/cmdline-tools/latest"
fi
# SDK license acceptance is explicit when this setup script is invoked.
set +o pipefail
yes | "$piano_sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$piano_sdk" --licenses >/dev/null
set -o pipefail
"$piano_sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$piano_sdk" \
  "platform-tools" "platforms;android-37.2" "build-tools;37.0.0" "ndk;27.2.12479018" "cmake;3.22.1"
printf 'sdk.dir=%s\n' "$piano_sdk" > local.properties
printf 'SDK pronto: %s\n' "$piano_sdk"
