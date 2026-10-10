/*
 * Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 */

#ifdef __EMSCRIPTEN__
#include <emscripten/emscripten.h>
#else
#define EMSCRIPTEN_KEEPALIVE
#endif

#include "stubs/wled.h"

extern WS2812FX strip;

static uint8_t* _pixelBuffer = nullptr;
static int _pixelCount = 0;
static int _matrixWidth = 0;
static int _matrixHeight = 0;

extern "C" {

EMSCRIPTEN_KEEPALIVE
void wled_init(int width, int height) {
    _matrixWidth = width;
    _matrixHeight = height;
    _pixelCount = width * height;

    if (_pixelBuffer) free(_pixelBuffer);
    _pixelBuffer = (uint8_t*)calloc(_pixelCount * 3, 1);

    strip.isMatrix = (height > 1);
    Segment::maxWidth = width;
    Segment::maxHeight = height;

    strip.setLengthRaw(_pixelCount);

    strip.clearSegments();
    strip.appendSegment(0, width, 0, height);
    Segment& seg = strip.getSegment(0);
    seg.grouping = 1;
    seg.spacing = 0;
    seg.offset = 0;
    seg.opacity = 255;
    seg.mode = 0;
    seg.palette = 0;
    seg.speed = 128;
    seg.intensity = 128;
    seg.custom1 = 128;
    seg.custom2 = 128;
    seg.custom3 = 16;
    seg.check1 = false;
    seg.check2 = false;
    seg.check3 = false;
    seg.colors[0] = RGBW32(255, 160, 0, 0);
    seg.colors[1] = RGBW32(0, 0, 0, 0);
    seg.colors[2] = RGBW32(0, 0, 0, 0);
    seg.options = 0;
    seg.setOption(0, true);

    _wasm_millis = 0;
}

EMSCRIPTEN_KEEPALIVE
void wled_set_effect(int effectId, int speed, int intensity, int palette,
                     uint32_t color1, uint32_t color2, uint32_t color3) {
    Segment& seg = strip.getFirstSelectedSeg();
    seg.mode = (uint8_t)effectId;
    seg.speed = (uint8_t)speed;
    seg.intensity = (uint8_t)intensity;
    seg.palette = (uint8_t)palette;
    seg.colors[0] = color1;
    seg.colors[1] = color2;
    seg.colors[2] = color3;
}

EMSCRIPTEN_KEEPALIVE
void wled_set_custom(int c1, int c2, int c3, bool ch1, bool ch2, bool ch3) {
    Segment& seg = strip.getFirstSelectedSeg();
    seg.custom1 = (uint8_t)c1;
    seg.custom2 = (uint8_t)c2;
    seg.custom3 = (uint8_t)c3;
    seg.check1 = ch1;
    seg.check2 = ch2;
    seg.check3 = ch3;
}

EMSCRIPTEN_KEEPALIVE
uint8_t* wled_tick(int deltaMs) {
    _wasm_millis += deltaMs;
    strip.now = _wasm_millis;

    Segment& seg = strip.getFirstSelectedSeg();

    strip.serviceForWasm();

    if (_pixelBuffer) {
        for (int i = 0; i < _pixelCount; i++) {
            uint32_t c = seg.getPixelColor(i);
            _pixelBuffer[i * 3 + 0] = R(c);
            _pixelBuffer[i * 3 + 1] = G(c);
            _pixelBuffer[i * 3 + 2] = B(c);
        }
    }

    return _pixelBuffer;
}

EMSCRIPTEN_KEEPALIVE
uint8_t* wled_get_pixels() {
    return _pixelBuffer;
}

EMSCRIPTEN_KEEPALIVE
int wled_get_pixel_count() {
    return _pixelCount;
}

EMSCRIPTEN_KEEPALIVE
int wled_get_mode_count() {
    return strip.getModeCount();
}

}
