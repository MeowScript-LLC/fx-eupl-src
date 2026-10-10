/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-04-19.
 */

#include "wled.h"

unsigned long _wasm_millis = 0;
bool useAMPM = false;
uint32_t randomPaletteChangeTime = 5;
bool useHarmonicRandomPalette = true;

const CRGBPalette16 fastledPalettes[] = {
  RainbowColors_p, PartyColors_p, CloudColors_p, LavaColors_p,
  OceanColors_p, ForestColors_p, RainbowStripeColors_p
};
const TProgmemRGBGradientPalettePtr gGradientPalettes[] = { nullptr };
uint8_t errorFlag = 0;
uint8_t realtimeMode = REALTIME_MODE_INACTIVE;
uint8_t paletteBlend = 0;
uint8_t blendingStyle = TRANSITION_FADE;
bool gammaCorrectBri = false;
bool gammaCorrectCol = false;
bool stateChanged = false;
std::vector<void*> customPalettes;

uint32_t color_blend(uint32_t c1, uint32_t c2, uint8_t blend) {
  if (blend == 0) return c1;
  if (blend == 255) return c2;
  uint32_t rb1 = c1 & 0x00FF00FF;
  uint32_t rb2 = c2 & 0x00FF00FF;
  uint32_t ga1 = (c1 >> 8) & 0x00FF00FF;
  uint32_t ga2 = (c2 >> 8) & 0x00FF00FF;
  uint32_t rb = rb1 + ((int32_t)(rb2 - rb1) * blend / 255);
  uint32_t ga = ga1 + ((int32_t)(ga2 - ga1) * blend / 255);
  return (rb & 0x00FF00FF) | ((ga << 8) & 0xFF00FF00);
}

uint32_t color_blend16(uint32_t c1, uint32_t c2, uint16_t blend) {
  if (blend == 0) return c1;
  if (blend == 0xFFFF) return c2;
  return color_blend(c1, c2, (uint8_t)(blend >> 8));
}

uint32_t color_add(uint32_t c1, uint32_t c2, bool fast) {
  uint32_t r = R(c1) + R(c2);
  uint32_t g = G(c1) + G(c2);
  uint32_t b = B(c1) + B(c2);
  uint32_t w = W(c1) + W(c2);
  if (r > 255) r = 255;
  if (g > 255) g = 255;
  if (b > 255) b = 255;
  if (w > 255) w = 255;
  return RGBW32(r, g, b, w);
}

uint32_t color_fade(uint32_t c, uint8_t amount, bool video) {
  uint32_t scaleFactor = 1 + amount;
  uint32_t r = R(c) * scaleFactor >> 8;
  uint32_t g = G(c) * scaleFactor >> 8;
  uint32_t b = B(c) * scaleFactor >> 8;
  uint32_t w = W(c) * scaleFactor >> 8;
  return RGBW32(r, g, b, w);
}

uint32_t fast_color_scale(uint32_t c, uint8_t amount) {
  return color_fade(c, amount, false);
}

uint8_t get_random_wheel_index(uint8_t pos) {
  uint8_t r = 0, x = 0, y = 0, d = 0;
  while (d < 42) {
    r = random8();
    x = abs8((int8_t)(pos - r));
    y = 255 - x;
    d = (x < y) ? x : y;
  }
  return r;
}

uint32_t hashInt(uint32_t s) {
  s ^= s >> 16;
  s *= 0x7feb352d;
  s ^= s >> 15;
  s *= 0x846ca68b;
  s ^= s >> 16;
  return s;
}

static um_data_t _fakeAudioData = {};
static float _fakeFFT[16] = {};
static float _fakeVolume = 0;
um_data_t* simulateSound(uint8_t si) {
  return &_fakeAudioData;
}

uint32_t adjust_color(uint32_t c, uint8_t amt, uint8_t cctFromRgb, bool correctWB) {
  if (amt == 255) return c;
  return color_fade(c, amt);
}

uint32_t hw_random(uint32_t howbig) { return howbig ? (rand() % howbig) : 0; }
uint32_t hw_random(uint32_t howsmall, uint32_t howbig) { return howsmall + hw_random(howbig - howsmall); }
uint8_t hw_random8(uint8_t howbig) { return howbig ? (rand() & 0xFF) % howbig : 0; }
uint8_t hw_random8(uint8_t howsmall, uint8_t howbig) { return howsmall + hw_random8(howbig - howsmall); }
uint16_t hw_random16(uint16_t howbig) { return howbig ? (rand() & 0xFFFF) % howbig : 0; }
uint16_t hw_random16(uint16_t howsmall, uint16_t howbig) { return howsmall + hw_random16(howbig - howsmall); }

CRGBPalette16 generateRandomPalette(CRGBPalette16& base) {
  CRGBPalette16 pal;
  for (int i = 0; i < 16; i++) {
    CHSV hsv(random8(), 200 + random8(56), 200 + random8(56));
    CRGB rgb;
    hsv2rgb_rainbow(hsv, rgb);
    pal[i] = rgb;
  }
  return pal;
}

CRGBPalette16 generateHarmonicRandomPalette(CRGBPalette16& base, CRGBPalette16& prev) {
  return generateRandomPalette(base);
}

CRGBPalette16 generateRandomPalette() {
  CRGBPalette16 pal;
  generateRandomPalette(pal);
  return pal;
}
void* pDoc = nullptr;
