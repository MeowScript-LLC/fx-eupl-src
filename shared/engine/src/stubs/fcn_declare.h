/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-08.
 */

#pragma once

#include <cstdint>

um_data_t* simulateSound(uint8_t si);

uint32_t adjust_color(uint32_t c, uint8_t amt = 255, uint8_t cctFromRgb = 0, bool correctWB = false);

uint32_t hw_random(uint32_t upperlimit);
int32_t  hw_random(int32_t lowerlimit, int32_t upperlimit);
uint8_t  hw_random8(uint32_t upperlimit);
uint8_t  hw_random8(uint32_t lowerlimit, uint32_t upperlimit);
uint16_t hw_random16(uint32_t upperlimit);
int16_t  hw_random16(int32_t lowerlimit, int32_t upperlimit);

