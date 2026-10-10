/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-10.
 */

#include "wled.h"

unsigned long _wasm_millis = 0;
bool useAMPM = false;
uint32_t randomPaletteChangeTime = 5;
bool useHarmonicRandomPalette = true;

#include "palettes.h"

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
static uint8_t  _fakeFFT[16] = {};
static float    _fakeVolumeSmth = 0;
static float    _fakeVolumeRaw  = 0;
static uint8_t  _fakeSamplePeak = 0;
static float    _fakeMajorPeak  = 440.0f;
static float    _fakeMagnitude  = 0.0f;
static uint8_t  _fakeMaxVol     = 31;
static uint8_t  _fakeBinNum     = 8;
static float    _fakeBin256[256] = {};
static bool     _fakeAudioInited = false;

static unsigned long _externalAudioMs = 0;
static const unsigned long EXTERNAL_AUDIO_FRESH_MS = 500;

extern "C" void _wled_set_external_audio(float volumeSmth, float volumeRaw, uint8_t samplePeak,
                                         float majorPeak, float magnitude, uint8_t maxVol,
                                         uint8_t binNum, const uint8_t* fftResult16) {
  _fakeVolumeSmth = volumeSmth;
  _fakeVolumeRaw  = volumeRaw;
  _fakeSamplePeak = samplePeak;
  _fakeMajorPeak  = majorPeak;
  _fakeMagnitude  = magnitude;
  _fakeMaxVol     = maxVol;
  _fakeBinNum     = binNum;
  if (fftResult16) {
    for (int i = 0; i < 16; i++) _fakeFFT[i] = fftResult16[i];
  }
  _externalAudioMs = _wasm_millis;
}

static void _initFakeAudio() {
  _fakeAudioData.u_type = 0;
  _fakeAudioData.u_size = 0;
  _fakeAudioData.u_data[0] = &_fakeVolumeSmth;
  _fakeAudioData.u_data[1] = &_fakeVolumeRaw;
  _fakeAudioData.u_data[2] = _fakeFFT;
  _fakeAudioData.u_data[3] = &_fakeSamplePeak;
  _fakeAudioData.u_data[4] = &_fakeMajorPeak;
  _fakeAudioData.u_data[5] = &_fakeMagnitude;
  _fakeAudioData.u_data[6] = &_fakeMaxVol;
  _fakeAudioData.u_data[7] = &_fakeBinNum;
  _fakeAudioData.u_data[8] = _fakeBin256;
  _fakeAudioInited = true;
}

um_data_t* simulateSound(uint8_t si) {
  if (!_fakeAudioInited) _initFakeAudio();

  if (_externalAudioMs != 0 &&
      (_wasm_millis - _externalAudioMs) <= EXTERNAL_AUDIO_FRESH_MS) {
    return &_fakeAudioData;
  }

  float t = (float)_wasm_millis * 0.001f;
  float beatPhase  = t * 2.4f;
  float beatEnv    = 0.5f + 0.5f * sinf(beatPhase * (float)M_TWOPI);
  beatEnv = beatEnv * beatEnv * beatEnv;
  for (int b = 0; b < 16; b++) {
    float bandPhase  = t * (0.6f + 0.18f * b) + b * 0.91f;
    float bandEnv    = 0.5f + 0.5f * sinf(bandPhase * (float)M_TWOPI);
    float bassWeight = (b < 4) ? 1.0f : 0.55f;
    float val = bassWeight * (0.30f * bandEnv + 0.70f * beatEnv * bandEnv);
    int v = (int)(val * 230.0f);
    if (v < 0) v = 0; else if (v > 255) v = 255;
    _fakeFFT[b] = (uint8_t)v;
  }
  _fakeVolumeSmth = 32.0f + 96.0f * beatEnv;
  _fakeVolumeRaw  = _fakeVolumeSmth;
  _fakeSamplePeak = (beatEnv > 0.85f) ? 1 : 0;
  _fakeMajorPeak  = 220.0f + 440.0f * (0.5f + 0.5f * sinf(t * 0.7f));
  _fakeMagnitude  = 256.0f * beatEnv;
  return &_fakeAudioData;
}

uint32_t adjust_color(uint32_t c, uint8_t amt, uint8_t cctFromRgb, bool correctWB) {
  if (amt == 255) return c;
  return color_fade(c, amt);
}

uint32_t hw_random(uint32_t upperlimit) { return upperlimit ? ((uint32_t)rand() % upperlimit) : 0; }
int32_t  hw_random(int32_t lowerlimit, int32_t upperlimit) {
  int32_t range = upperlimit - lowerlimit;
  if (range <= 0) return lowerlimit;
  return lowerlimit + (int32_t)hw_random((uint32_t)range);
}
uint8_t  hw_random8(uint32_t upperlimit) { return upperlimit ? (uint8_t)((rand() & 0xFF) % upperlimit) : 0; }
uint8_t  hw_random8(uint32_t lowerlimit, uint32_t upperlimit) {
  if (upperlimit <= lowerlimit) return (uint8_t)lowerlimit;
  return (uint8_t)(lowerlimit + hw_random8(upperlimit - lowerlimit));
}
uint16_t hw_random16(uint32_t upperlimit) { return upperlimit ? (uint16_t)((rand() & 0xFFFF) % upperlimit) : 0; }
int16_t  hw_random16(int32_t lowerlimit, int32_t upperlimit) {
  int32_t range = upperlimit - lowerlimit;
  if (range <= 0) return (int16_t)lowerlimit;
  return (int16_t)(lowerlimit + (int32_t)hw_random16((uint32_t)range));
}

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
