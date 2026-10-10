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

#include "FastLED.h"
#include <cmath>
#include <cstdlib>

CRGB CRGB::Black = CRGB(0, 0, 0);
const CRGB CRGB::Red = CRGB(255, 0, 0);
const CRGB CRGB::Green = CRGB(0, 255, 0);
const CRGB CRGB::Blue = CRGB(0, 0, 255);
const CRGB CRGB::White = CRGB(255, 255, 255);
const CRGB CRGB::Orange = CRGB(255, 165, 0);
const CRGB CRGB::DarkOrange = CRGB(255, 140, 0);
const CRGB CRGB::Yellow = CRGB(255, 255, 0);
const CRGB CRGB::Gray = CRGB(128, 128, 128);

float atan2_t(float y, float x) { return atan2f(y, x); }

CHSV rgb2hsv_approximate(CRGB rgb) {
  uint8_t r = rgb.r, g = rgb.g, b = rgb.b;
  uint8_t mx = r > g ? (r > b ? r : b) : (g > b ? g : b);
  uint8_t mn = r < g ? (r < b ? r : b) : (g < b ? g : b);
  uint8_t d = mx - mn;
  uint8_t h = 0, s = mx ? (uint8_t)((uint16_t)d * 255 / mx) : 0;
  if (d) {
    if (mx == r) h = (uint8_t)(((int)(g - b) * 43) / d);
    else if (mx == g) h = 85 + (uint8_t)(((int)(b - r) * 43) / d);
    else h = 171 + (uint8_t)(((int)(r - g) * 43) / d);
  }
  return CHSV(h, s, mx);
}

static const uint8_t _sin8_table[256] = {
  128,131,134,137,140,143,146,149,152,155,158,162,165,167,170,173,
  176,179,182,185,188,190,193,196,198,201,203,206,208,211,213,215,
  218,220,222,224,226,228,230,232,234,235,237,239,240,242,243,244,
  246,247,248,249,250,251,251,252,253,253,254,254,254,255,255,255,
  255,255,255,255,254,254,254,253,253,252,251,251,250,249,248,247,
  246,244,243,242,240,239,237,235,234,232,230,228,226,224,222,220,
  218,215,213,211,208,206,203,201,198,196,193,190,188,185,182,179,
  176,173,170,167,165,162,158,155,152,149,146,143,140,137,134,131,
  128,124,121,118,115,112,109,106,103,100,97,93,90,88,85,82,
  79,76,73,70,67,65,62,59,57,54,52,49,47,44,42,40,
  37,35,33,31,29,27,25,23,21,20,18,16,15,13,12,11,
  9,8,7,6,5,4,4,3,2,2,1,1,1,0,0,0,
  0,0,0,0,1,1,1,2,2,3,4,4,5,6,7,8,
  9,11,12,13,15,16,18,20,21,23,25,27,29,31,33,35,
  37,40,42,44,47,49,52,54,57,59,62,65,67,70,73,76,
  79,82,85,88,90,93,97,100,103,106,109,112,115,118,121,124
};

uint8_t sin8_t(uint8_t theta) { return _sin8_table[theta]; }
uint8_t cos8_t(uint8_t theta) { return _sin8_table[(uint8_t)(theta + 64)]; }

int16_t sin16_t(uint16_t theta) {
  return (int16_t)((sin8_t(theta >> 8) - 128) * 256);
}

int16_t cos16_t(uint16_t theta) {
  return sin16_t(theta + 16384);
}

float sin_t(float x) { return sinf(x); }
float cos_t(float x) { return cosf(x); }

extern unsigned long _wasm_millis;

uint8_t beat8(uint16_t bpm, uint32_t timebase) {
  return (uint8_t)(((uint32_t)((_wasm_millis - timebase) * bpm * 256)) / 60000);
}

uint16_t beat16(uint16_t bpm, uint32_t timebase) {
  return (uint16_t)(((uint32_t)((_wasm_millis - timebase) * bpm * 65536UL)) / 60000);
}

uint8_t beatsin8_t(uint16_t bpm, uint8_t lo, uint8_t hi, uint32_t timebase, uint8_t phase) {
  uint8_t beat = beat8(bpm, timebase);
  uint8_t beatsin = sin8_t(beat + phase);
  uint8_t range = hi - lo;
  return lo + scale8(beatsin, range);
}

uint16_t beat88(uint16_t bpm88, uint32_t timebase) {
  return beat16(bpm88 >> 8, timebase);
}

uint16_t beatsin16_t(uint16_t bpm, uint16_t lo, uint16_t hi, uint32_t timebase, uint16_t phase) {
  uint16_t beat = beat16(bpm, timebase);
  uint16_t beatsin = (sin16_t(beat + phase) + 32768);
  uint16_t range = hi - lo;
  return lo + scale16(beatsin, range);
}

uint16_t beatsin88_t(uint16_t bpm88, uint16_t lo, uint16_t hi, uint32_t timebase, uint16_t phase) {
  uint16_t beat = beat16(bpm88 >> 8, timebase);
  uint16_t beatsin = (sin16_t(beat + phase) + 32768);
  uint16_t range = hi - lo;
  return lo + scale16(beatsin, range);
}

uint8_t cubicwave8(uint8_t in) {
  uint8_t n = in << 1;
  if (in & 0x80) n = ~n;
  return ((uint16_t)n * n * (3 * 256 - 2 * n)) >> 16;
}

uint8_t triwave8(uint8_t in) {
  if (in & 0x80) { return (uint8_t)(255 - ((in & 0x7F) << 1)); }
  return (uint8_t)((in & 0x7F) << 1);
}

uint8_t quadwave8(uint8_t in) {
  return sin8_t(in);
}

uint8_t scale8(uint8_t i, uint8_t scale) {
  return ((uint16_t)i * (uint16_t)(1 + scale)) >> 8;
}

uint16_t scale16(uint16_t i, uint16_t scale) {
  return ((uint32_t)i * (uint32_t)(1 + scale)) >> 16;
}

uint8_t lerp8by8(uint8_t a, uint8_t b, uint8_t frac) {
  if (b > a) return a + scale8(b - a, frac);
  return a - scale8(a - b, frac);
}

uint16_t lerp16by16(uint16_t a, uint16_t b, uint16_t frac) {
  if (b > a) return a + scale16(b - a, frac);
  return a - scale16(a - b, frac);
}

uint8_t ease8InOutQuad(uint8_t i) {
  uint8_t j = i;
  if (j & 0x80) j = 255 - j;
  uint8_t jj = scale8(j, j);
  uint8_t jj2 = jj << 1;
  if (i & 0x80) jj2 = 255 - jj2;
  return jj2;
}

uint8_t qadd8(uint8_t i, uint8_t j) {
  unsigned int t = i + j;
  if (t > 255) t = 255;
  return (uint8_t)t;
}

uint8_t qsub8(uint8_t i, uint8_t j) {
  int t = (int)i - (int)j;
  if (t < 0) t = 0;
  return (uint8_t)t;
}

int8_t qadd7(int8_t i, int8_t j) {
  int t = (int)i + (int)j;
  if (t > 127) t = 127;
  if (t < -128) t = -128;
  return (int8_t)t;
}

uint8_t blend8(uint8_t a, uint8_t b, uint8_t amountOfB) {
  return lerp8by8(a, b, amountOfB);
}

CRGB blend(const CRGB& p1, const CRGB& p2, uint8_t amountOfP2) {
  return CRGB(lerp8by8(p1.r, p2.r, amountOfP2),
              lerp8by8(p1.g, p2.g, amountOfP2),
              lerp8by8(p1.b, p2.b, amountOfP2));
}

void hsv2rgb_rainbow(const CHSV& hsv, CRGB& rgb) {
  uint8_t hue = hsv.h;
  uint8_t sat = hsv.s;
  uint8_t val = hsv.v;

  uint8_t offset = hue & 0x1F;
  uint8_t offset8 = offset << 3;
  uint8_t third = scale8(offset8, 85);

  uint8_t r, g, b;
  uint8_t section = hue >> 5;

  switch (section) {
    case 0: r = 255 - third; g = third; b = 0; break;
    case 1: r = 171; g = 85 + third; b = 0; break;
    case 2: r = 171 - (third << 1); g = 170 + third; b = 0; break;
    case 3: r = 0; g = 255 - third; b = third; break;
    case 4: r = 0; g = 171 - (third << 1); b = 85 + (third << 1); break;
    case 5: r = third; g = 0; b = 255 - third; break;
    case 6: r = 85 + third; g = 0; b = 171 - (third << 1); break;
    default: r = 170 + third; g = 0; b = 85 - third; break;
  }

  if (sat != 255) {
    uint8_t desat = 255 - sat;
    desat = scale8(desat, desat);
    uint8_t satscale = 255 - desat;
    r = scale8(r, satscale) + desat;
    g = scale8(g, satscale) + desat;
    b = scale8(b, satscale) + desat;
  }

  if (val != 255) {
    r = scale8(r, val);
    g = scale8(g, val);
    b = scale8(b, val);
  }

  rgb.r = r; rgb.g = g; rgb.b = b;
}

CRGB ColorFromPalette(const CRGBPalette16& pal, uint8_t index, uint8_t brightness, TBlendType blendType) {
  uint8_t hi4 = index >> 4;
  uint8_t lo4 = index & 0x0F;

  const CRGB& entry = pal.entries[hi4];

  CRGB result;
  if (blendType == NOBLEND || lo4 == 0) {
    result = entry;
  } else {
    const CRGB& next = pal.entries[(hi4 + 1) & 0x0F];
    uint8_t f2 = lo4 << 4;
    result.r = lerp8by8(entry.r, next.r, f2);
    result.g = lerp8by8(entry.g, next.g, f2);
    result.b = lerp8by8(entry.b, next.b, f2);
  }

  if (brightness < 255) {
    result.r = scale8(result.r, brightness);
    result.g = scale8(result.g, brightness);
    result.b = scale8(result.b, brightness);
  }
  return result;
}

void nblendPaletteTowardPalette(CRGBPalette16& current, const CRGBPalette16& target, uint8_t maxChanges) {
  for (uint8_t i = 0; i < 16; i++) {
    current.entries[i] = blend(current.entries[i], target.entries[i], maxChanges);
  }
}

static uint8_t _noise_hash(int32_t x) {
  x = ((x >> 16) ^ x) * 0x45d9f3b;
  x = ((x >> 16) ^ x) * 0x45d9f3b;
  x = (x >> 16) ^ x;
  return (uint8_t)(x & 0xFF);
}

uint8_t perlin8(uint16_t x, uint16_t y, uint16_t z) {
  uint8_t xi = x >> 8, yi = y >> 8, zi = z >> 8;
  uint8_t xf = x & 0xFF, yf = y & 0xFF, zf = z & 0xFF;
  uint8_t a = _noise_hash(xi + _noise_hash(yi + _noise_hash(zi)));
  uint8_t b = _noise_hash(xi + 1 + _noise_hash(yi + _noise_hash(zi)));
  uint8_t c = _noise_hash(xi + _noise_hash(yi + 1 + _noise_hash(zi)));
  uint8_t d = _noise_hash(xi + 1 + _noise_hash(yi + 1 + _noise_hash(zi)));
  uint8_t ab = lerp8by8(a, b, xf);
  uint8_t cd = lerp8by8(c, d, xf);
  return lerp8by8(ab, cd, yf);
}

uint16_t perlin16(uint32_t x, uint32_t y, uint32_t z) {
  return (uint16_t)perlin8((uint16_t)(x >> 8), (uint16_t)(y >> 8), (uint16_t)(z >> 8)) << 8;
}

static uint16_t _random16_seed = 1337;

uint16_t random16_get_seed() { return _random16_seed; }
void random16_set_seed(uint16_t seed) { _random16_seed = seed; }

uint8_t random8() {
  _random16_seed = (_random16_seed * 2053) + 13849;
  return (uint8_t)((_random16_seed + (_random16_seed >> 8)) & 0xFF);
}
uint8_t random8(uint8_t lim) { return lim ? random8() % lim : 0; }
uint8_t random8(uint8_t min, uint8_t lim) { return min + random8(lim - min); }
uint16_t random16() { return ((uint16_t)random8() << 8) | random8(); }
uint16_t random16(uint16_t lim) { return lim ? random16() % lim : 0; }
uint16_t random16(uint16_t min, uint16_t lim) { return min + random16(lim - min); }

CRGB HeatColor(uint8_t temperature) {
  CRGB heatcolor;
  uint8_t t192 = scale8(temperature, 191);
  uint8_t heatramp = t192 & 0x3F;
  heatramp <<= 2;
  if (t192 > 0x80) { heatcolor.r = 255; heatcolor.g = 255; heatcolor.b = heatramp; }
  else if (t192 > 0x40) { heatcolor.r = 255; heatcolor.g = heatramp; heatcolor.b = 0; }
  else { heatcolor.r = heatramp; heatcolor.g = 0; heatcolor.b = 0; }
  return heatcolor;
}

void fill_solid(CRGB* leds, int numToFill, CRGB color) {
  for (int i = 0; i < numToFill; i++) leds[i] = color;
}

void fill_rainbow(CRGB* leds, int numToFill, uint8_t initialhue, uint8_t deltahue) {
  CHSV hsv(initialhue, 240, 255);
  for (int i = 0; i < numToFill; i++) {
    hsv2rgb_rainbow(hsv, leds[i]);
    hsv.h += deltahue;
  }
}

const CRGBPalette16 RainbowColors_p(CRGB(255,0,0), CRGB(0,255,0), CRGB(0,0,255), CRGB(255,0,0));
const CRGBPalette16 PartyColors_p(CRGB(90,0,205), CRGB(255,100,0), CRGB(0,200,100), CRGB(200,0,100));
const CRGBPalette16 CloudColors_p(CRGB(0,0,240), CRGB(0,80,240), CRGB(80,160,255), CRGB(255,255,255));
const CRGBPalette16 LavaColors_p(CRGB(0,0,0), CRGB(128,0,0), CRGB(255,128,0), CRGB(255,255,0));
const CRGBPalette16 OceanColors_p(CRGB(0,0,32), CRGB(0,32,128), CRGB(0,128,255), CRGB(128,255,255));
const CRGBPalette16 ForestColors_p(CRGB(0,32,0), CRGB(0,128,0), CRGB(0,64,0), CRGB(32,128,32));
const CRGBPalette16 RainbowStripeColors_p(CRGB(255,0,0), CRGB(0,0,0), CRGB(0,0,255), CRGB(0,0,0));
const CRGBPalette16 HeatColors_p(CRGB(0,0,0), CRGB(255,0,0), CRGB(255,255,0), CRGB(255,255,255));
