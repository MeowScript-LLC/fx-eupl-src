/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-04-19.
 */

#include "stubs/wled.h"

WS2812FX strip;

uint8_t bri = 128;
uint8_t briOld = 128;
uint8_t briT = 128;
bool useMainSegmentOnly = false;
uint8_t realtimeOverride = 0;
#define REALTIME_OVERRIDE_NONE 0
bool arlsDisableGammaCorrection = true;

void WS2812FX::serviceForWasm() {
    now = millis();
    if (_segments.empty()) return;

    Segment& seg = _segments[0];
    _segment_index = 0;

    if (!seg.isActive()) return;
    if (seg.freeze) return;

    seg.beginDraw(0);
    _currentSegment = &seg;

    if (seg.mode < _mode.size()) {
        _mode[seg.mode]();
        seg.call++;
    }
}

void WS2812FX::service() { serviceForWasm(); }
void WS2812FX::show() {}
void WS2812FX::finalizeInit() {}
void WS2812FX::setBrightness(uint8_t b, bool direct) { _brightness = b; }
void WS2812FX::setCCT(uint16_t k) {}
void WS2812FX::setRange(uint16_t i, uint16_t i2, uint32_t col) {}
void WS2812FX::purgeSegments() {}
void WS2812FX::setMainSegmentId(unsigned n) { _mainSegment = n; }
void WS2812FX::resetSegments() {}
void WS2812FX::makeAutoSegments(bool forceReset) {}
void WS2812FX::fixInvalidSegments() {}
void WS2812FX::setTargetFps(unsigned fps) { _targetFps = fps; }
void WS2812FX::waitForIt() {}
void WS2812FX::restartRuntime() {}
void WS2812FX::setRealtimePixelColor(unsigned i, uint32_t c) {}
void WS2812FX::blendSegment(const Segment& s) const {}
uint16_t WS2812FX::getLengthPhysical() const { return _length; }
bool WS2812FX::checkSegmentAlignment() const { return true; }
bool WS2812FX::deserializeMap(unsigned n) { return true; }

Segment& WS2812FX::getSegment(unsigned id) { return _segments[id < _segments.size() ? id : 0]; }
uint16_t WS2812FX::getLengthTotal() const { return _length; }
uint8_t WS2812FX::getActiveSegmentsNum() const { return _segments.empty() ? 0 : 1; }

uint8_t WS2812FX::getFirstSelectedSegId() const { return 0; }

bool realtimeRespectLedMaps = false;

