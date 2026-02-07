#!/bin/bash
set -e

# ==============================================================================
# AvaCore Professional Asset Provisioner (100% Offline Build Ready)
# ==============================================================================

PROJECT_ROOT="."
LIBS_DIR="$PROJECT_ROOT/app/libs"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets/tts"

# 1. Sherpa-ONNX Core Engine (AAR) - Stable v1.10.41
AAR_URL="https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-1.10.41.aar"

# 2, 3 & 4. Neural Persian Model Bundle (Official Sherpa-ONNX version with metadata)
# Verified URL for the Persian Piper model prepared for Sherpa-ONNX
MODEL_BUNDLE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-gyro-medium.tar.bz2"

# 5. Linguistic Phonemizer Data (eSpeak-NG)
ESPEAK_DATA_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"

echo "🚀 Starting Professional Asset Provisioning for AvaCore..."

mkdir -p "$LIBS_DIR" "$ASSETS_DIR"

# --- PART 1: Core Engine ---
echo "📥 Syncing Sherpa-ONNX Engine (v1.10.41)..."
curl -L -f "$AAR_URL" -o "$LIBS_DIR/sherpa-onnx.aar" --progress-bar

# --- PART 2, 3 & 4: Model Bundle ---
echo "📥 Syncing Persian Neural Model & Phoneme Map..."
curl -L -f "$MODEL_BUNDLE_URL" -o "$ASSETS_DIR/model_bundle.tar.bz2" --progress-bar

# Verify it is a valid bzip2 archive before extracting
if ! file "$ASSETS_DIR/model_bundle.tar.bz2" | grep -q "bzip2"; then
    echo "❌ ERROR: Download failed or file is not a valid bzip2 archive."
    rm "$ASSETS_DIR/model_bundle.tar.bz2"
    exit 1
fi

mkdir -p "$ASSETS_DIR/tmp"
tar -xjf "$ASSETS_DIR/model_bundle.tar.bz2" -C "$ASSETS_DIR/tmp"

# The bundle extracts to a directory named 'vits-piper-fa_IR-gyro-medium'
EXTRACTED_DIR=$(find "$ASSETS_DIR/tmp" -maxdepth 1 -type d -name "vits-piper-fa_IR-gyro-medium" | head -n 1)

if [ -n "$EXTRACTED_DIR" ]; then
    # Move and standardize filenames for AvaTtsService
    mv "$EXTRACTED_DIR"/fa_IR-gyro-medium.onnx "$ASSETS_DIR/persian_model.onnx"
    mv "$EXTRACTED_DIR"/fa_IR-gyro-medium.onnx.json "$ASSETS_DIR/persian_model.onnx.json"
    mv "$EXTRACTED_DIR"/tokens.txt "$ASSETS_DIR/tokens.txt"
    echo "✅ Model assets extracted and standardized."
else
    echo "❌ ERROR: Could not find extracted model directory 'vits-piper-fa_IR-gyro-medium'."
    exit 1
fi

rm -rf "$ASSETS_DIR/tmp" "$ASSETS_DIR/model_bundle.tar.bz2"

# --- PART 5: eSpeak-NG Data ---
if [ ! -d "$ASSETS_DIR/espeak-ng-data" ]; then
    echo "📥 Syncing eSpeak-NG Phonemizer Data..."
    curl -L -f "$ESPEAK_DATA_URL" -o "$ASSETS_DIR/espeak-ng-data.tar.bz2" --progress-bar
    tar -xjf "$ASSETS_DIR/espeak-ng-data.tar.bz2" -C "$ASSETS_DIR"
    rm "$ASSETS_DIR/espeak-ng-data.tar.bz2"
fi

echo ""
echo "✅ Asset Provisioning Complete."
echo "--------------------------------------------------------"
echo "Engine: $(ls -lh "$LIBS_DIR/sherpa-onnx.aar")"
echo "Model:  $(ls -lh "$ASSETS_DIR/persian_model.onnx")"
echo "Tokens: $ASSETS_DIR/tokens.txt"
echo "eSpeak: $ASSETS_DIR/espeak-ng-data/"
echo "--------------------------------------------------------"
