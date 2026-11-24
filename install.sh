#!/usr/bin/env bash
# Installs the Hebrew keyboard on a paired Wear OS watch and makes it the default.
#
# Once per watch (watch Settings → Developer options → Wireless debugging → Pair new device):
#   adb pair <ip>:<pairing-port>        # enter the 6-digit code
#   adb connect <ip>:<port>             # the port shown on the Wireless debugging screen
#
# Then:
#   ./install.sh                        # downloads the latest build (needs `gh` logged in)
#   ./install.sh path/to/hebrew-keyboard.apk
set -euo pipefail

REPO="elazarcules/hebrew-wear-keyboard"
IME="ar.elaz.wearkeyboard/.HebrewIme"
APK="${1:-}"

if [[ -z "$APK" ]]; then
  if ! command -v gh >/dev/null; then
    echo "No APK given and no gh CLI. Download hebrew-keyboard.apk from"
    echo "  https://github.com/$REPO/releases/tag/latest"
    echo "and run: $0 ~/Downloads/hebrew-keyboard.apk"
    exit 1
  fi
  tmp="$(mktemp -d)"
  gh release download latest -R "$REPO" -p hebrew-keyboard.apk -D "$tmp"
  APK="$tmp/hebrew-keyboard.apk"
fi

if ! adb devices | grep -qw device; then
  echo "No watch connected. Run: adb connect <ip>:<port>"
  exit 1
fi

adb install -r "$APK"
adb shell ime enable "$IME"
adb shell ime set "$IME"
echo "Done. Open any text field on the watch."
