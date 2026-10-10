#!/bin/bash
# Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
set -e

if [ -f /tmp/emsdk/emsdk_env.sh ]; then
  source /tmp/emsdk/emsdk_env.sh 2>/dev/null
elif [ -f /private/tmp/emsdk/emsdk_env.sh ]; then
  source /private/tmp/emsdk/emsdk_env.sh 2>/dev/null
elif [ -f "$HOME/emsdk/emsdk_env.sh" ]; then
  source "$HOME/emsdk/emsdk_env.sh" 2>/dev/null
fi

if ! command -v emcc >/dev/null 2>&1; then
  echo "ERROR: emcc not found. Install emsdk:"
  echo "  git clone https://github.com/emscripten-core/emsdk.git /tmp/emsdk"
  echo "  cd /tmp/emsdk && ./emsdk install latest && ./emsdk activate latest"
  exit 1
fi

SRCDIR="$(dirname "$0")/src"
STUBDIR="$SRCDIR/stubs"
OUTDIR="$(dirname "$0")/build"

mkdir -p "$OUTDIR"

echo "=== Compiling WLED Effect Engine to WASM (with Particle System) ==="

emcc \
  "$SRCDIR/wled_wasm_bridge.cpp" \
  "$SRCDIR/wled_wasm_service.cpp" \
  "$SRCDIR/FX.cpp" \
  "$SRCDIR/FX_fcn.cpp" \
  "$SRCDIR/FX_2Dfcn.cpp" \
  "$SRCDIR/FXparticleSystem.cpp" \
  "$STUBDIR/fastled_math.cpp" \
  "$STUBDIR/fastled_palettes.cpp" \
  "$STUBDIR/wled_colors.cpp" \
  -I"$STUBDIR" \
  -I"$SRCDIR" \
  -DWLED_WASM \
  -DESP32 \
  -DCONFIG_IDF_TARGET_ESP32=1 \
  -std=c++17 \
  -O2 \
  -Wno-vla-cxx-extension \
  -s EXPORTED_FUNCTIONS='["_wled_init","_wled_set_effect","_wled_set_custom","_wled_set_options","_wled_set_audio","_wled_tick","_wled_get_pixels","_wled_get_pixel_count","_wled_get_mode_count","_wled_dump_palette","_wled_get_seg_palette","_malloc","_free"]' \
  -s EXPORTED_RUNTIME_METHODS='["ccall","cwrap","HEAPU8"]' \
  -s ALLOW_MEMORY_GROWTH=1 \
  -s INITIAL_MEMORY=8388608 \
  -s MODULARIZE=1 \
  -s EXPORT_NAME="WLEDEngine" \
  -s ENVIRONMENT=web \
  -s SINGLE_FILE=1 \
  -o "$OUTDIR/wled_engine.js"

echo "=== Build complete ==="
ls -la "$OUTDIR/wled_engine.js" "$OUTDIR/wled_engine.wasm"
echo "JS: $(wc -c < "$OUTDIR/wled_engine.js") bytes"
echo "WASM: $(wc -c < "$OUTDIR/wled_engine.wasm") bytes"
