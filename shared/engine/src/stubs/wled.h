/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-08.
 */

#pragma once

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <vector>

#include <string>
typedef uint8_t byte;
typedef uint8_t fract8;
typedef std::string String;
typedef unsigned int uint;

using std::min;
using std::max;

#define PROGMEM
#define PSTR(x) (x)
#define F(x) (x)
#define pgm_read_byte_near(addr) (*(const uint8_t*)(addr))
#define pgm_read_word_near(addr) (*(const uint16_t*)(addr))
#define pgm_read_dword_near(addr) (*(const uint32_t*)(addr))
#define pgm_read_byte(addr) (*(const uint8_t*)(addr))
#define pgm_read_word(addr) (*(const uint16_t*)(addr))
#define pgm_read_dword(addr) (*(const uint32_t*)(addr))
#define strncmp_P strncmp
#define strcpy_P strcpy
#define memcpy_P memcpy
#define strlen_P strlen
#define sprintf_P sprintf
#define snprintf_P snprintf
#define IRAM_ATTR
#define IRAM_ATTR_YN
#define WLED_O2_ATTR
#define constrain(x,a,b) ((x)<(a)?(a):((x)>(b)?(b):(x)))

#ifndef PI
#define PI 3.14159265358979323846
#endif
#define radians(d) ((d) * PI / 180.0)
#define degrees(r) ((r) * 180.0 / PI)

inline long map(long x, long in_min, long in_max, long out_min, long out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

#define DEFAULT_LED_COUNT 30
#define MAX_LEDS 8192
#define MAX_LED_MEMORY 64000
#define WLED_MAX_SEGNAME_LEN 32

#define AW_GLOBAL_DISABLED 255
#define RGBW_MODE_DUAL 3
#define RGBW_MODE_MANUAL_ONLY 4
struct Bus {
  uint8_t getType() const { return 0; }
  uint8_t getPins(uint8_t* out) const { return 0; }
  bool isOk() const { return true; }
  bool containsPixel(unsigned i) const { return true; }
  bool hasRGB() const { return true; }
  bool hasCCT() const { return false; }
  bool hasWhite() const { return false; }
  uint8_t getAutoWhiteMode() const { return 0; }
  static uint8_t getGlobalAWMode() { return AW_GLOBAL_DISABLED; }
};

struct BusManager {
  static uint32_t getPixelColor(uint16_t i) { return 0; }
  static void setPixelColor(uint16_t i, uint32_t c) {}
  static int getNumBusses() { return 1; }
  static uint16_t getTotalLength() { return MAX_LEDS; }
  static bool canAllShow() { return true; }
  static void setStatusPixel(uint32_t c) {}
  static Bus* getBus(uint8_t n) { static Bus b; return &b; }
};

#define ESP32
#define CONFIG_IDF_TARGET_ESP32 1

extern unsigned long _wasm_millis;
inline unsigned long millis() { return _wasm_millis; }
inline void delay(unsigned long) {}
inline void yield() {}

#define BFRALLOC_PREFER_PSRAM 0
#define BFRALLOC_PREFER_DRAM 0
#define BFRALLOC_NOBYTEACCESS 0
#define BFRALLOC_CLEAR 1
#define BFRALLOC_ENFORCE_PSRAM 0

inline void* allocate_buffer(size_t len, int flags = 0) {
  void* p = calloc(1, len);
  return p;
}
inline void* d_malloc(size_t len) { return malloc(len); }
inline void  d_free(void* p) { free(p); }
inline void* p_malloc(size_t len) { return malloc(len); }
inline void  p_free(void* p) { free(p); }

#define DEBUG_PRINT(x)
#define DEBUG_PRINTLN(x)
#define DEBUG_PRINTF_P(...)
#define DEBUGFX_PRINT(x)
#define DEBUGFX_PRINTLN(x)
#define DEBUGFX_PRINTF(...)

#define ERR_NONE         0
#define ERR_NOT_IMPL     4
#define ERR_NORAM_PX     7
#define ERR_NORAM        8
extern uint8_t errorFlag;

#define REALTIME_MODE_INACTIVE 0
extern uint8_t realtimeMode;

#define R(c) ((uint8_t)((c) >> 16))
#define G(c) ((uint8_t)((c) >> 8))
#define B(c) ((uint8_t)(c))
#define W(c) ((uint8_t)((c) >> 24))
#define RGBW32(r,g,b,w) (((uint32_t)(w) << 24) | ((uint32_t)(r) << 16) | ((uint32_t)(g) << 8) | (uint32_t)(b))
#define COLOR_WHITE 0xFFFFFFFF
#define BLACK 0x00000000

extern uint8_t paletteBlend;
extern uint8_t blendingStyle;
extern bool gammaCorrectBri;
extern bool gammaCorrectCol;
extern bool stateChanged;

inline uint8_t gamma8(uint8_t v) { return v; }
inline uint32_t gamma32(uint32_t c) { return c; }
inline uint8_t gamma8inv(uint8_t v) { return v; }
inline uint32_t gamma32inv(uint32_t c) { return c; }
inline uint8_t scaledBri(uint8_t v) { return v; }

inline uint32_t hw_random() { return (uint32_t)rand(); }
inline uint8_t hw_random8() { return (uint8_t)(rand() & 0xFF); }
inline uint16_t hw_random16() { return (uint16_t)(rand() & 0xFFFF); }

inline uint8_t abs8(int8_t x) { return x < 0 ? -x : x; }
inline uint32_t sqrt32_bw(uint32_t n) { return (uint32_t)sqrtf((float)n); }

#define TRANSITION_FADE 0

#define USERMOD_ID_AUDIOREACTIVE 0
struct um_data_t {
  uint8_t u_type;
  void* u_data[10];
  size_t u_size;
};

struct UsermodManager {
  static bool getUMData(um_data_t** data, uint8_t type = 0) {
    *data = nullptr;
    return false;
  }
};

#define bitRead(value, bit) (((value) >> (bit)) & 0x01)
#define bitWrite(value, bit, bitvalue) ((bitvalue) ? ((value) |= (1UL << (bit))) : ((value) &= ~(1UL << (bit))))
#define bitSet(value, bit) ((value) |= (1UL << (bit)))
#define bitClear(value, bit) ((value) &= ~(1UL << (bit)))

#ifndef M_TWOPI
#define M_TWOPI (2.0 * PI)
#endif

extern std::vector<void*> customPalettes;

#define DYNAMIC_PALETTE_COUNT 6
#define FASTLED_PALETTE_COUNT 7
#define GRADIENT_PALETTE_COUNT 61
#define FIXED_PALETTE_COUNT (DYNAMIC_PALETTE_COUNT + FASTLED_PALETTE_COUNT + GRADIENT_PALETTE_COUNT)

#define SEG_OPTION_ON 0

#define SEG_CAPABILITY_RGB 0x01
#define SEG_CAPABILITY_W   0x02
#define SEG_CAPABILITY_CCT 0x04

#ifndef HALF_PI
#define HALF_PI (PI / 2.0)
#endif

inline int16_t extractModeDefaults(uint8_t mode, const char* key) { return -1; }

static int _wasm_localTime = 0;
#define localTime _wasm_localTime
inline int hour(int t) { return 12; }
inline int minute(int t) { return 0; }
inline int second(int t) { return 0; }
inline int month(int t) { return 3; }
inline int day(int t) { return 27; }
inline int year(int t) { return 2026; }
inline const char* monthShortStr(int m) { static const char* months[] = {"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"}; return months[m%13]; }
inline const char* monthStr(int m) { return monthShortStr(m); }
inline int weekday(int t) { return 1; }
inline const char* dayStr(int d) { return "Monday"; }
inline const char* dayShortStr(int d) { return "Mon"; }
extern bool useAMPM;
inline unsigned long micros() { return _wasm_millis * 1000; }
inline float mapf(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

#include "FastLED.h"

inline uint8_t add8(uint8_t a, uint8_t b) { return qadd8(a, b); }

extern const CRGBPalette16 fastledPalettes[];
extern const uint8_t* const gGradientPalettes[];
extern uint32_t randomPaletteChangeTime;
extern bool useHarmonicRandomPalette;
CRGBPalette16 generateRandomPalette(CRGBPalette16& pal);
CRGBPalette16 generateHarmonicRandomPalette(CRGBPalette16& pal, CRGBPalette16& prev);

uint32_t color_blend(uint32_t c1, uint32_t c2, uint8_t blend);
uint32_t color_blend16(uint32_t c1, uint32_t c2, uint16_t blend);
uint32_t color_add(uint32_t c1, uint32_t c2, bool fast = false);
uint32_t color_fade(uint32_t c, uint8_t amount, bool video = false);
uint32_t fast_color_scale(uint32_t c, uint8_t amount);

inline CRGBW color_fade(CRGBW c, uint8_t amount, bool video = false) { return CRGBW(color_fade(c.color32, amount, video)); }
inline CRGBW color_add(CRGBW c1, CRGBW c2, bool fast = false) { return CRGBW(color_add(c1.color32, c2.color32, fast)); }
uint8_t get_random_wheel_index(uint8_t pos);
uint32_t hashInt(uint32_t s);

#include "FX.h"
#include "fcn_declare.h"

extern WS2812FX strip;

inline uint32_t hsv2rgb(uint16_t h, uint8_t s, uint8_t v) {
  CHSV hsv(h >> 8, s, v);
  CRGB rgb;
  hsv2rgb_rainbow(hsv, rgb);
  return RGBW32(rgb.r, rgb.g, rgb.b, 0);
}
inline uint32_t hsv2rgb(CHSV32 hsv32) {
  return hsv2rgb(hsv32.h, hsv32.s, hsv32.v);
}
inline void hsv2rgb(CHSV32 hsv32, uint32_t& out) {
  out = hsv2rgb(hsv32.h, hsv32.s, hsv32.v);
}

#define AW_GLOBAL_DISABLED 255
#define RGBW_MODE_DUAL 3
#define RGBW_MODE_MANUAL_ONLY 4

inline bool requestJSONBufferLock(int) { return false; }
inline void releaseJSONBufferLock() {}
inline bool readObjectFromFile(const char*, const char*, void*) { return false; }
#define JSON_LOCK_LEDGAP 0
#define JSON_LOCK_LEDMAP 0
struct JsonArray { bool isNull() { return true; } size_t size() { return 0; } int operator[](size_t) { return 0; } };
struct JsonObject {};
extern void* pDoc;
struct _WledFS { bool exists(const char*) { return false; } };
static _WledFS WLED_FS;
