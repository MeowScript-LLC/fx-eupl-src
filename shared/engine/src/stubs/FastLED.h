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

#pragma once

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <cmath>

enum TBlendType : uint8_t { LINEARBLEND = 1, LINEARBLEND_NOWRAP = 2, NOBLEND = 0 };

struct CHSV {
  uint8_t h, s, v;
  CHSV() : h(0), s(0), v(0) {}
  CHSV(uint8_t h, uint8_t s, uint8_t v) : h(h), s(s), v(v) {}
};

struct CHSV32 {
  uint16_t h; uint8_t s, v;
  CHSV32() : h(0), s(0), v(0) {}
  CHSV32(uint16_t h, uint8_t s, uint8_t v) : h(h), s(s), v(v) {}
};

typedef uint32_t TProgmemRGBGradientPalette_byte;
typedef const TProgmemRGBGradientPalette_byte* TProgmemRGBGradientPalettePtr;

struct CRGB;
void hsv2rgb_rainbow(const CHSV& hsv, CRGB& rgb);

struct CRGB {
  union {
    struct { uint8_t b, g, r; };
    struct { uint8_t blue, green, red; };
    uint8_t raw[3];
  };
  CRGB() : r(0), g(0), b(0) {}
  CRGB(uint8_t r, uint8_t g, uint8_t b) : r(r), g(g), b(b) {}
  CRGB(uint32_t colorcode) : r((colorcode >> 16) & 0xFF), g((colorcode >> 8) & 0xFF), b(colorcode & 0xFF) {}
  CRGB(const CHSV& hsv) { hsv2rgb_rainbow(hsv, *this); }

  CRGB& nscale8_video(uint8_t scaledown) {
    r = ((int)r * (int)scaledown) >> 8;
    g = ((int)g * (int)scaledown) >> 8;
    b = ((int)b * (int)scaledown) >> 8;
    return *this;
  }
  CRGB& nscale8(uint8_t scaledown) { return nscale8_video(scaledown); }
  CRGB& operator|=(const CRGB& rhs) {
    if (rhs.r > r) r = rhs.r;
    if (rhs.g > g) g = rhs.g;
    if (rhs.b > b) b = rhs.b;
    return *this;
  }
  CRGB& operator+=(const CRGB& rhs) {
    r = (uint8_t)((r + rhs.r > 255) ? 255 : r + rhs.r);
    g = (uint8_t)((g + rhs.g > 255) ? 255 : g + rhs.g);
    b = (uint8_t)((b + rhs.b > 255) ? 255 : b + rhs.b);
    return *this;
  }
  bool operator==(const CRGB& rhs) const { return r == rhs.r && g == rhs.g && b == rhs.b; }
  bool operator!=(const CRGB& rhs) const { return !(*this == rhs); }
  operator uint32_t() const { return ((uint32_t)r << 16) | ((uint32_t)g << 8) | b; }
  uint8_t getAverageLight() const { return ((uint16_t)r + g + b) / 3; }
  CRGB& fadeToBlackBy(uint8_t fadeBy) { return nscale8(255 - fadeBy); }

  static CRGB Black;
  static const CRGB Red;
  static const CRGB Green;
  static const CRGB Blue;
  static const CRGB White;
  static const CRGB Orange;
  static const CRGB DarkOrange;
  static const CRGB Yellow;
  static const CRGB Gray;
};

struct CRGBW {
  union {
    struct { uint8_t b, g, r, w; };
    uint32_t color32;
  };
  CRGBW() : color32(0) {}
  CRGBW(uint8_t r_, uint8_t g_, uint8_t b_, uint8_t w_ = 0) { r = r_; g = g_; b = b_; w = w_; }
  CRGBW(uint32_t c) : color32(c) {}
  CRGBW(const CRGB& c) { r = c.r; g = c.g; b = c.b; w = 0; }
  operator uint32_t() const { return color32; }
  bool operator==(const CRGBW& rhs) const { return color32 == rhs.color32; }
  bool operator!=(const CRGBW& rhs) const { return color32 != rhs.color32; }
  CRGBW operator+(const CRGBW& rhs) const {
    return CRGBW(
      (uint8_t)((r+rhs.r>255)?255:r+rhs.r),
      (uint8_t)((g+rhs.g>255)?255:g+rhs.g),
      (uint8_t)((b+rhs.b>255)?255:b+rhs.b),
      (uint8_t)((w+rhs.w>255)?255:w+rhs.w));
  }
};

struct CRGBPalette16 {
  CRGB entries[16];
  CRGBPalette16() { memset(entries, 0, sizeof(entries)); }
  CRGBPalette16(const CRGB& c) { for (int i = 0; i < 16; i++) entries[i] = c; }
  CRGBPalette16(const CRGB& c1, const CRGB& c2) {
    for (int i = 0; i < 8; i++) entries[i] = c1;
    for (int i = 8; i < 16; i++) entries[i] = c2;
  }
  CRGBPalette16(const CRGB& c1, const CRGB& c2, const CRGB& c3) {
    for (int i = 0; i < 5; i++) entries[i] = c1;
    for (int i = 5; i < 11; i++) entries[i] = c2;
    for (int i = 11; i < 16; i++) entries[i] = c3;
  }
  CRGBPalette16(const CRGB& c1, const CRGB& c2, const CRGB& c3, const CRGB& c4) {
    for (int i = 0; i < 4; i++) entries[i] = c1;
    for (int i = 4; i < 8; i++) entries[i] = c2;
    for (int i = 8; i < 12; i++) entries[i] = c3;
    for (int i = 12; i < 16; i++) entries[i] = c4;
  }
  CRGBPalette16(const CRGB c[16]) { for (int i = 0; i < 16; i++) entries[i] = c[i]; }
  CRGBPalette16(const TProgmemRGBGradientPalettePtr p) {  }
  CRGBPalette16(CRGB c0, CRGB c1, CRGB c2, CRGB c3,
                CRGB c4, CRGB c5, CRGB c6, CRGB c7,
                CRGB c8, CRGB c9, CRGB cA, CRGB cB,
                CRGB cC, CRGB cD, CRGB cE, CRGB cF) {
    entries[0]=c0; entries[1]=c1; entries[2]=c2; entries[3]=c3;
    entries[4]=c4; entries[5]=c5; entries[6]=c6; entries[7]=c7;
    entries[8]=c8; entries[9]=c9; entries[10]=cA; entries[11]=cB;
    entries[12]=cC; entries[13]=cD; entries[14]=cE; entries[15]=cF;
  }
  CRGBPalette16(uint32_t c0, uint32_t c1, uint32_t c2, uint32_t c3,
                uint32_t c4, uint32_t c5, uint32_t c6, uint32_t c7,
                uint32_t c8, uint32_t c9, uint32_t cA, uint32_t cB,
                uint32_t cC, uint32_t cD, uint32_t cE, uint32_t cF) {
    entries[0]=CRGB(c0); entries[1]=CRGB(c1); entries[2]=CRGB(c2); entries[3]=CRGB(c3);
    entries[4]=CRGB(c4); entries[5]=CRGB(c5); entries[6]=CRGB(c6); entries[7]=CRGB(c7);
    entries[8]=CRGB(c8); entries[9]=CRGB(c9); entries[10]=CRGB(cA); entries[11]=CRGB(cB);
    entries[12]=CRGB(cC); entries[13]=CRGB(cD); entries[14]=CRGB(cE); entries[15]=CRGB(cF);
  }
  CRGB& operator[](int i) { return entries[i & 0x0F]; }
  const CRGB& operator[](int i) const { return entries[i & 0x0F]; }
  bool operator==(const CRGBPalette16& rhs) const { return memcmp(entries, rhs.entries, sizeof(entries)) == 0; }
  bool operator!=(const CRGBPalette16& rhs) const { return !(*this == rhs); }
};

void hsv2rgb_rainbow(const CHSV& hsv, CRGB& rgb);

CRGB ColorFromPalette(const CRGBPalette16& pal, uint8_t index, uint8_t brightness = 255, TBlendType blendType = LINEARBLEND);
CRGBPalette16 CRGBPalette16_from_progmem(const TProgmemRGBGradientPalettePtr ptr);
void nblendPaletteTowardPalette(CRGBPalette16& current, const CRGBPalette16& target, uint8_t maxChanges = 24);

uint8_t sin8_t(uint8_t theta);
uint8_t cos8_t(uint8_t theta);
int16_t sin16_t(uint16_t theta);
int16_t cos16_t(uint16_t theta);
float sin_t(float x);
float cos_t(float x);

uint8_t beatsin8_t(uint16_t bpm, uint8_t lo = 0, uint8_t hi = 255, uint32_t timebase = 0, uint8_t phase = 0);
uint16_t beatsin88_t(uint16_t bpm88, uint16_t lo = 0, uint16_t hi = 65535, uint32_t timebase = 0, uint16_t phase = 0);
uint16_t beatsin16_t(uint16_t bpm, uint16_t lo = 0, uint16_t hi = 65535, uint32_t timebase = 0, uint16_t phase = 0);
uint16_t beat88(uint16_t bpm88, uint32_t timebase = 0);
uint8_t beat8(uint16_t bpm, uint32_t timebase = 0);
uint16_t beat16(uint16_t bpm, uint32_t timebase = 0);

uint8_t cubicwave8(uint8_t in);
uint8_t triwave8(uint8_t in);
uint8_t quadwave8(uint8_t in);

uint8_t scale8(uint8_t i, uint8_t scale);
uint16_t scale16(uint16_t i, uint16_t scale);
uint8_t lerp8by8(uint8_t a, uint8_t b, uint8_t frac);
uint16_t lerp16by16(uint16_t a, uint16_t b, uint16_t frac);
uint8_t ease8InOutQuad(uint8_t i);
inline uint8_t ease8InOutApprox(uint8_t i) { return ease8InOutQuad(i); }
float atan2_t(float y, float x);
CHSV rgb2hsv_approximate(CRGB rgb);

uint8_t qadd8(uint8_t i, uint8_t j);
uint8_t qsub8(uint8_t i, uint8_t j);
int8_t qadd7(int8_t i, int8_t j);

uint8_t blend8(uint8_t a, uint8_t b, uint8_t amountOfB);
CRGB blend(const CRGB& p1, const CRGB& p2, uint8_t amountOfP2);

uint8_t perlin8(uint16_t x, uint16_t y = 0, uint16_t z = 0);
uint16_t perlin16(uint32_t x, uint32_t y = 0, uint32_t z = 0);
#define inoise8 perlin8
#define inoise16 perlin16

CRGB HeatColor(uint8_t temperature);
void fill_solid(CRGB* leds, int numToFill, CRGB color);
void fill_rainbow(CRGB* leds, int numToFill, uint8_t initialhue, uint8_t deltahue = 5);

uint8_t random8();
uint8_t random8(uint8_t lim);
uint8_t random8(uint8_t min, uint8_t lim);
uint16_t random16();
uint16_t random16(uint16_t lim);
uint16_t random16(uint16_t min, uint16_t lim);
uint16_t random16_get_seed();
void random16_set_seed(uint16_t seed);
#define random random16

extern const CRGBPalette16 PartyColors_p;
extern const CRGBPalette16 CloudColors_p;
extern const CRGBPalette16 LavaColors_p;
extern const CRGBPalette16 OceanColors_p;
extern const CRGBPalette16 ForestColors_p;
extern const CRGBPalette16 RainbowColors_p;
extern const CRGBPalette16 RainbowStripeColors_p;
extern const CRGBPalette16 HeatColors_p;
