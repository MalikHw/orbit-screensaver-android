#!/usr/bin/env bash
set -euo pipefail

ROOT="app/src/main/res"

if ! command -v identify >/dev/null 2>&1; then
  echo "ImageMagick (identify) not found. Install it first:"
  echo "  sudo apt install imagemagick"
  exit 1
fi

echo "Scanning PNG files under $ROOT"
echo "--------------------------------------"

find "$ROOT" -type f -name "*.png" | while read -r file; do
  dims=$(identify -format "%wx%h" "$file" 2>/dev/null || echo "UNKNOWN")

  echo "$file  ->  $dims"
done

echo ""
echo "Flagging potential issues:"
echo "--------------------------------------"

find "$ROOT" -type f -name "*.png" | while read -r file; do
  dims=$(identify -format "%w %h" "$file" 2>/dev/null || echo "0 0")
  w=$(echo "$dims" | awk '{print $1}')
  h=$(echo "$dims" | awk '{print $2}')

  # flag small icons
  if [ "$w" -lt 512 ] || [ "$h" -lt 512 ]; then
    echo "⚠️  $file is $w x $h (below 512x512 requirement for launcher icon)"
  fi

  # flag banner issues
  if [[ "$file" == *"tv_banner"* ]]; then
    if [ "$w" -ne 320 ] || [ "$h" -ne 180 ]; then
      echo "⚠️  $file is $w x $h (TV banner should be 320x180)"
    fi
  fi
done
