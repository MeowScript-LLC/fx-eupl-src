/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * FX_2Dfcn.cpp: Copyright (c) 2022 Blaz Kristan. Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

package fx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object NativeEffectEngine {

    private class Ctx(
        val seglen: Int,
        val now: Long,
        val paletteId: Int,
        val col0: Int,
        val col1: Int,
        val col2: Int,
        val speed: Int,
        val intensity: Int,
        val custom1: Int = 128,
        val custom2: Int = 128,
        val custom3: Int = 16,
        val defaultPal: Int = 0,
    ) {
        var palCache: IntArray? = null
    }

    private const val FRAMETIME = 17L

    private fun rgb(r: Int, g: Int, b: Int): Int =
        ((r.coerceIn(0, 255)) shl 16) or ((g.coerceIn(0, 255)) shl 8) or (b.coerceIn(0, 255))

    private fun cR(c: Int) = (c ushr 16) and 0xFF
    private fun cG(c: Int) = (c ushr 8) and 0xFF
    private fun cB(c: Int) = c and 0xFF

    private fun colorBlend(c1: Int, c2: Int, blend: Int): Int {
        val b = blend.coerceIn(0, 255)
        if (b == 0) return c1 and 0xFFFFFF
        if (b == 255) return c2 and 0xFFFFFF
        val ib = 255 - b
        val r = (cR(c1) * ib + cR(c2) * b) shr 8
        val g = (cG(c1) * ib + cG(c2) * b) shr 8
        val bl = (cB(c1) * ib + cB(c2) * b) shr 8
        return rgb(r, g, bl)
    }

    private fun colorAdd(c1: Int, c2: Int): Int =
        rgb(min(255, cR(c1) + cR(c2)), min(255, cG(c1) + cG(c2)), min(255, cB(c1) + cB(c2)))

    private fun colorFade(c: Int, amount: Int): Int {
        if (c == 0 || amount == 0) return 0
        if (amount >= 255) return c and 0xFFFFFF
        return rgb((cR(c) * amount) shr 8, (cG(c) * amount) shr 8, (cB(c) * amount) shr 8)
    }

    private val sin8Table = IntArray(256) {
        max(0, min(255, round(127.5 + 127.5 * sin(it * 2.0 * PI / 256.0)).toInt()))
    }
    private val cos8Table = IntArray(256) {
        max(0, min(255, round(127.5 + 127.5 * cos(it * 2.0 * PI / 256.0)).toInt()))
    }
    private val gamma8invTable = IntArray(256) {
        round((it / 255.0).pow(1.0 / 2.8) * 255).toInt()
    }

    private fun sin8(x: Int): Int = sin8Table[x and 0xFF]
    private fun cos8(x: Int): Int = cos8Table[x and 0xFF]
    private fun gamma8inv(v: Int): Int = gamma8invTable[v and 0xFF]

    private fun sin16(theta: Int): Int {
        val t = theta and 0xFFFF
        val angle = (t / 65536.0) * 2.0 * PI
        return round(sin(angle) * 32767.0).toInt()
    }

    private fun triwave8(x: Int): Int {
        val v = x and 0xFF
        return if (v < 128) v * 2 else (255 - v) * 2
    }

    private fun triwave16(x: Int): Int {
        val v = x and 0xFFFF
        return if (v < 32768) v * 2 else (65535 - v) * 2
    }

    private fun scale8(value: Int, scale: Int): Int = (value * (1 + scale)) shr 8
    private fun scale16(value: Int, scale: Int): Int = ((value.toLong() * scale.toLong()) ushr 16).toInt()

    private fun qadd8(a: Int, b: Int): Int { val t = a + b; return if (t > 255) 255 else t }
    private fun qsub8(a: Int, b: Int): Int { val r = a - b; return if (r < 0) 0 else r }

    private fun ease8InOutCubic(xIn: Int): Int {
        var x = xIn and 0xFF
        if (x < 128) return (x * x * x) shr 14
        x = 255 - x
        return 255 - ((x * x * x) shr 14)
    }

    private fun cubicwave8(x: Int): Int = ease8InOutCubic(triwave8(x))

    private fun mapInt(x: Int, inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int {
        val denom = (inMax - inMin)
        if (denom == 0) return outMin
        return ((x - inMin).toLong() * (outMax - outMin) / denom).toInt() + outMin
    }

    private fun beat88(now: Long, bpm88: Int): Int {
        val elapsed = now and 0xFFFFFFFFL
        val prod = (elapsed * (bpm88.toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        val prod2 = (prod * 280L) and 0xFFFFFFFFL
        return ((prod2 ushr 16) and 0xFFFFL).toInt()
    }

    private fun beat16(now: Long, bpm: Int): Int = beat88(now, (bpm * 256) and 0xFFFF)
    private fun beat8(now: Long, bpm: Int): Int = (beat16(now, bpm) shr 8) and 0xFF

    private fun beatsin8(now: Long, bpm: Int, lowest: Int = 0, highest: Int = 255, phase: Int = 0): Int {
        val b = beat8(now, bpm)
        val s = sin8(b + phase)
        return lowest + ((s * (highest - lowest)) shr 8)
    }

    private fun beatsin16(now: Long, bpm: Int, lowest: Int = 0, highest: Int = 65535, phase: Int = 0): Int {
        val b = beat16(now, bpm)
        val raw = (sin16(b + phase) + 32768) and 0xFFFF
        return lowest + ((raw.toLong() * (highest - lowest + 1)) / 65536L).toInt()
    }

    private fun beatsin88(now: Long, bpm88: Int, lowest: Int = 0, highest: Int = 65535): Int {
        val b = beat88(now, bpm88)
        val raw = (sin16(b) + 32768) and 0xFFFF
        return lowest + ((raw.toLong() * (highest - lowest + 1)) / 65536L).toInt()
    }

    private fun hash32(seedIn: Int): Int {
        var h = seedIn
        h = h xor (h ushr 16)
        h *= -0x7ee3623b
        h = h xor (h ushr 13)
        h *= -0x3361d2af
        h = h xor (h ushr 16)
        return h
    }

    private fun rnd8(key: Int): Int = hash32(key) and 0xFF
    private fun rnd16(key: Int): Int = hash32(key) and 0xFFFF
    private fun rndRange(key: Int, lo: Int, hi: Int): Int {
        if (hi <= lo) return lo
        return lo + (hash32(key).ushr(8) % (hi - lo))
    }

    private fun hsv2rgb(hIn: Int, sIn: Int, vIn: Int): Int {
        val h = hIn and 0xFF
        val s = sIn.coerceIn(0, 255)
        val v = vIn.coerceIn(0, 255)
        if (s == 0) return rgb(v, v, v)

        val offset = h and 0x1F
        val offset8 = (offset shl 3) and 0xFF
        val third = ((offset8 * 86) shr 8) and 0xFF

        var r: Int; var g: Int; var b: Int
        if ((h and 0x80) == 0) {
            if ((h and 0x40) == 0) {
                if ((h and 0x20) == 0) { r = 255 - third; g = third; b = 0 }
                else { r = 171; g = 85 + third; b = 0 }
            } else {
                if ((h and 0x20) == 0) { r = (171 - ((third shl 1) and 0xFF)) and 0xFF; g = 170 + third; b = 0 }
                else { r = 0; g = 255 - third; b = third }
            }
        } else {
            if ((h and 0x40) == 0) {
                if ((h and 0x20) == 0) { r = 0; g = (171 - ((third shl 1) and 0xFF)) and 0xFF; b = (85 + ((third shl 1) and 0xFF)) and 0xFF }
                else { r = third; g = 0; b = 255 - third }
            } else {
                if ((h and 0x20) == 0) { r = 85 + third; g = 0; b = (171 - ((third shl 1) and 0xFF)) and 0xFF }
                else { r = 170 + third; g = 0; b = 85 - third }
            }
        }

        if (s != 255) {
            var desat = 255 - s
            desat = ((desat * (1 + desat)) shr 8) and 0xFF
            val sat = 255 - desat
            r = (((r and 0xFF) * (1 + sat)) shr 8) + desat
            g = (((g and 0xFF) * (1 + sat)) shr 8) + desat
            b = (((b and 0xFF) * (1 + sat)) shr 8) + desat
        }
        if (v != 255) {
            r = ((r and 0xFF) * (1 + v)) shr 8
            g = ((g and 0xFF) * (1 + v)) shr 8
            b = ((b and 0xFF) * (1 + v)) shr 8
        }
        return rgb(r and 0xFF, g and 0xFF, b and 0xFF)
    }

    private fun colorWheelRaw(pos: Int): Int = hsv2rgb(pos and 0xFF, 255, 255)

    private fun quadwave8(x: Int): Int = sin8((x + 64) and 0xFF)

    private val gamma8Table = IntArray(256) {
        round((it / 255.0).pow(2.8) * 255).toInt().coerceIn(0, 255)
    }
    private fun gamma8(v: Int): Int = gamma8Table[v and 0xFF]

    private fun gamma32inv(c: Int): Int = rgb(gamma8inv(cR(c)), gamma8inv(cG(c)), gamma8inv(cB(c)))

    private fun colorFadeVideo(c: Int, amount: Int): Int {
        if (c == 0 || amount == 0) return 0
        val sc = amount + 1
        var r = (cR(c) * sc) shr 8
        var g = (cG(c) * sc) shr 8
        var b = (cB(c) * sc) shr 8
        if (cR(c) != 0 && r == 0) r = 1
        if (cG(c) != 0 && g == 0) g = 1
        if (cB(c) != 0 && b == 0) b = 1
        return rgb(r, g, b)
    }

    private fun getAverageLight(c: Int): Int = (cR(c) + cG(c) + cB(c)) / 3

    private val perlinPerm = intArrayOf(
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,
        37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
        57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,134,139,48,27,166,
        77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54,
        65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,200,196,135,130,116,188,159,86,
        164,100,109,198,173,186,3,64,52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,
        207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,
        221,153,101,155,167,43,172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,
        218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,107,
        49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,
        222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180)

    private fun phash(i: Int): Int = perlinPerm[i and 0xFF]
    private fun ease8(t: Int): Int { val ti = t and 0xFF; return ((ti * ti * (3 * 256 - 2 * ti)) ushr 16) and 0xFF }

    private fun perlin8(xIn: Int, yIn: Int = 0): Int {
        val x = xIn; val y = yIn
        val cellX = x shr 4; val cellY = y shr 4
        val fracX = x and 0x0F; val fracY = y and 0x0F
        val u = ease8((fracX * 17).coerceIn(0, 255))
        val v = ease8((fracY * 17).coerceIn(0, 255))
        val n00 = phash(cellX + phash(cellY))
        val n10 = phash(cellX + 1 + phash(cellY))
        val n01 = phash(cellX + phash(cellY + 1))
        val n11 = phash(cellX + 1 + phash(cellY + 1))
        val nx0 = n00 + (n10 - n00) * u / 255
        val nx1 = n01 + (n11 - n01) * u / 255
        return (nx0 + (nx1 - nx0) * v / 255).coerceIn(0, 255)
    }

    private fun perlin16(xIn: Int, yIn: Int = 0, zIn: Int = 0): Int {
        val n1 = perlin8(xIn shr 4, yIn shr 4)
        val n2 = perlin8((xIn shr 4) + 97, (yIn shr 4) + 131)
        return ((n1 shl 8) or (n2 xor perlin8(zIn shr 4))) and 0xFFFF
    }

    private fun synthVolume(now: Long): Int {
        val base = beatsin8(now, 18, 30, 210)
        val accent = beatsin8(now, 47, 0, 60)
        return (base + accent / 2).coerceIn(0, 255)
    }
    private fun synthSamplePeak(now: Long): Boolean {
        return (now % 320L) < 24L
    }
    private fun synthMajorPeak(now: Long): Double {
        val t = beatsin16(now, 7, 0, 65535)
        return 80.0 + (t / 65535.0) * 7900.0
    }
    private fun synthMagnitude(now: Long): Double = 200.0 + beatsin8(now, 23, 0, 700 - 200).toDouble()
    private fun synthFFT(now: Long, bin: Int): Int {
        return beatsin8(now, 9 + bin * 3, 0, 255, phase = bin * 16)
    }

    private fun randomWheelIndex(prevIndex: Int, salt: Int): Int {
        var idx = 0
        var k = salt
        var d = 0
        var guard = 0
        while (d < 42 && guard < 32) {
            idx = rnd8(k)
            val x = abs(prevIndex - idx) and 0xFF
            val y = 255 - x
            d = min(x, y)
            k = hash32(k)
            guard++
        }
        return idx
    }

    private fun gradientToPalette(stops: Array<IntArray>): IntArray {
        val out = IntArray(48)
        for (i in 0 until 16) {
            val pos = (i * 255) / 15
            var lo = stops[0]
            var hi = stops[stops.size - 1]
            for (j in 0 until stops.size - 1) {
                if (stops[j][0] <= pos && stops[j + 1][0] >= pos) { lo = stops[j]; hi = stops[j + 1]; break }
            }
            val range = hi[0] - lo[0]
            val t = if (range > 0) (pos - lo[0]).toDouble() / range else 0.0
            out[i * 3] = round(lo[1] + (hi[1] - lo[1]) * t).toInt()
            out[i * 3 + 1] = round(lo[2] + (hi[2] - lo[2]) * t).toInt()
            out[i * 3 + 2] = round(lo[3] + (hi[3] - lo[3]) * t).toInt()
        }
        return out
    }

    private fun entriesToPalette(entries: Array<IntArray>): IntArray {
        val out = IntArray(48)
        for (i in 0 until 16) {
            out[i * 3] = entries[i][0]; out[i * 3 + 1] = entries[i][1]; out[i * 3 + 2] = entries[i][2]
        }
        return out
    }

    private val palParty = entriesToPalette(arrayOf(
        intArrayOf(155, 0, 213), intArrayOf(189, 0, 184), intArrayOf(218, 0, 146), intArrayOf(243, 0, 92),
        intArrayOf(244, 85, 0), intArrayOf(220, 143, 0), intArrayOf(213, 180, 0), intArrayOf(213, 213, 0),
        intArrayOf(213, 155, 0), intArrayOf(239, 102, 0), intArrayOf(249, 0, 68), intArrayOf(225, 0, 134),
        intArrayOf(196, 0, 176), intArrayOf(163, 0, 207), intArrayOf(118, 0, 232), intArrayOf(0, 50, 252),
    ))
    private val palCloud = entriesToPalette(arrayOf(
        intArrayOf(0, 0, 255), intArrayOf(0, 0, 139), intArrayOf(0, 0, 139), intArrayOf(0, 0, 139),
        intArrayOf(0, 0, 139), intArrayOf(0, 0, 139), intArrayOf(0, 0, 139), intArrayOf(0, 0, 139),
        intArrayOf(0, 0, 255), intArrayOf(0, 0, 139), intArrayOf(135, 206, 235), intArrayOf(135, 206, 235),
        intArrayOf(173, 216, 230), intArrayOf(255, 255, 255), intArrayOf(173, 216, 230), intArrayOf(135, 206, 235),
    ))
    private val palLava = entriesToPalette(arrayOf(
        intArrayOf(0, 0, 0), intArrayOf(128, 0, 0), intArrayOf(0, 0, 0), intArrayOf(128, 0, 0),
        intArrayOf(139, 0, 0), intArrayOf(139, 0, 0), intArrayOf(128, 0, 0), intArrayOf(139, 0, 0),
        intArrayOf(139, 0, 0), intArrayOf(139, 0, 0), intArrayOf(255, 0, 0), intArrayOf(255, 165, 0),
        intArrayOf(255, 255, 255), intArrayOf(255, 165, 0), intArrayOf(255, 0, 0), intArrayOf(139, 0, 0),
    ))
    private val palOcean = entriesToPalette(arrayOf(
        intArrayOf(25, 25, 112), intArrayOf(0, 0, 139), intArrayOf(25, 25, 112), intArrayOf(0, 0, 128),
        intArrayOf(0, 0, 139), intArrayOf(0, 0, 205), intArrayOf(46, 139, 87), intArrayOf(0, 128, 128),
        intArrayOf(95, 158, 160), intArrayOf(0, 0, 255), intArrayOf(0, 139, 139), intArrayOf(100, 149, 237),
        intArrayOf(127, 255, 212), intArrayOf(46, 139, 87), intArrayOf(0, 255, 255), intArrayOf(135, 206, 250),
    ))
    private val palForest = entriesToPalette(arrayOf(
        intArrayOf(0, 100, 0), intArrayOf(0, 100, 0), intArrayOf(85, 107, 47), intArrayOf(0, 100, 0),
        intArrayOf(0, 128, 0), intArrayOf(34, 139, 34), intArrayOf(107, 142, 35), intArrayOf(0, 128, 0),
        intArrayOf(46, 139, 87), intArrayOf(102, 205, 170), intArrayOf(50, 205, 50), intArrayOf(154, 205, 50),
        intArrayOf(144, 238, 144), intArrayOf(124, 252, 0), intArrayOf(102, 205, 170), intArrayOf(34, 139, 34),
    ))
    private val palRainbow = entriesToPalette(arrayOf(
        intArrayOf(255, 0, 0), intArrayOf(235, 112, 0), intArrayOf(213, 155, 0), intArrayOf(213, 186, 0),
        intArrayOf(213, 213, 0), intArrayOf(156, 235, 0), intArrayOf(0, 255, 0), intArrayOf(0, 235, 112),
        intArrayOf(0, 213, 155), intArrayOf(0, 156, 212), intArrayOf(0, 0, 255), intArrayOf(112, 0, 235),
        intArrayOf(155, 0, 213), intArrayOf(186, 0, 187), intArrayOf(213, 0, 155), intArrayOf(235, 0, 114),
    ))
    private val palRainbowBands = entriesToPalette(arrayOf(
        intArrayOf(255, 0, 0), intArrayOf(0, 0, 0), intArrayOf(213, 155, 0), intArrayOf(0, 0, 0),
        intArrayOf(213, 213, 0), intArrayOf(0, 0, 0), intArrayOf(0, 255, 0), intArrayOf(0, 0, 0),
        intArrayOf(0, 213, 155), intArrayOf(0, 0, 0), intArrayOf(0, 0, 255), intArrayOf(0, 0, 0),
        intArrayOf(155, 0, 213), intArrayOf(0, 0, 0), intArrayOf(213, 0, 155), intArrayOf(0, 0, 0),
    ))

    private val palSunset = gradientToPalette(arrayOf(
        intArrayOf(0, 181, 0, 0), intArrayOf(22, 218, 85, 0), intArrayOf(51, 255, 170, 0),
        intArrayOf(85, 211, 85, 77), intArrayOf(135, 167, 0, 169), intArrayOf(198, 73, 0, 188), intArrayOf(255, 0, 0, 207)))
    private val palFire = gradientToPalette(arrayOf(
        intArrayOf(0, 0, 0, 0), intArrayOf(46, 77, 0, 0), intArrayOf(96, 177, 0, 0), intArrayOf(108, 196, 38, 9),
        intArrayOf(119, 215, 76, 19), intArrayOf(146, 235, 115, 29), intArrayOf(174, 255, 153, 41), intArrayOf(188, 255, 178, 41),
        intArrayOf(202, 255, 204, 41), intArrayOf(218, 255, 230, 41), intArrayOf(234, 255, 255, 41), intArrayOf(244, 255, 255, 143),
        intArrayOf(255, 255, 255, 255)))
    private val palIcefire = gradientToPalette(arrayOf(
        intArrayOf(0, 0, 0, 0), intArrayOf(59, 0, 51, 117), intArrayOf(119, 0, 102, 255), intArrayOf(149, 38, 153, 255),
        intArrayOf(180, 86, 204, 255), intArrayOf(217, 167, 230, 255), intArrayOf(255, 255, 255, 255)))
    private val palAprilNight = gradientToPalette(arrayOf(
        intArrayOf(0, 1, 5, 45), intArrayOf(10, 1, 5, 45), intArrayOf(25, 5, 169, 175), intArrayOf(40, 1, 5, 45),
        intArrayOf(61, 1, 5, 45), intArrayOf(76, 45, 175, 31), intArrayOf(91, 1, 5, 45), intArrayOf(112, 1, 5, 45),
        intArrayOf(127, 249, 150, 5), intArrayOf(143, 1, 5, 45), intArrayOf(162, 1, 5, 45), intArrayOf(178, 255, 92, 0),
        intArrayOf(193, 1, 5, 45), intArrayOf(214, 1, 5, 45), intArrayOf(229, 223, 45, 72), intArrayOf(244, 1, 5, 45),
        intArrayOf(255, 1, 5, 45)))
    private val palAurora = gradientToPalette(arrayOf(
        intArrayOf(0, 1, 5, 45), intArrayOf(64, 0, 200, 23), intArrayOf(128, 0, 255, 0), intArrayOf(170, 0, 243, 45),
        intArrayOf(200, 0, 135, 7), intArrayOf(255, 1, 5, 45)))
    private val palTemperature = gradientToPalette(arrayOf(
        intArrayOf(0, 20, 92, 171), intArrayOf(28, 6, 142, 211), intArrayOf(56, 16, 181, 239), intArrayOf(84, 86, 204, 200),
        intArrayOf(113, 182, 229, 125), intArrayOf(141, 241, 240, 22), intArrayOf(170, 251, 199, 4), intArrayOf(198, 243, 114, 15),
        intArrayOf(226, 213, 30, 29), intArrayOf(255, 151, 38, 35)))
    private val palSakura = gradientToPalette(arrayOf(
        intArrayOf(0, 196, 19, 10), intArrayOf(65, 255, 69, 45), intArrayOf(130, 223, 45, 72),
        intArrayOf(195, 255, 82, 103), intArrayOf(255, 223, 13, 17)))
    private val palAtlantica = gradientToPalette(arrayOf(
        intArrayOf(0, 0, 28, 112), intArrayOf(50, 32, 96, 255), intArrayOf(100, 0, 243, 45), intArrayOf(150, 12, 95, 82),
        intArrayOf(200, 25, 190, 95), intArrayOf(255, 40, 170, 80)))
    private val palMagenta = gradientToPalette(arrayOf(
        intArrayOf(0, 0, 0, 0), intArrayOf(42, 0, 0, 117), intArrayOf(84, 0, 0, 255), intArrayOf(127, 113, 0, 255),
        intArrayOf(170, 255, 0, 255), intArrayOf(212, 255, 128, 255), intArrayOf(255, 255, 255, 255)))
    private val palYelblu = gradientToPalette(arrayOf(
        intArrayOf(0, 0, 0, 255), intArrayOf(63, 0, 128, 255), intArrayOf(127, 0, 255, 255), intArrayOf(191, 113, 255, 117),
        intArrayOf(255, 255, 255, 0)))

    private fun buildDynamicPalette(ctx: Ctx, id: Int): IntArray {
        val out = IntArray(48)
        val col0 = if (ctx.col0 != 0) ctx.col0 else 0xFFAA00
        val r0 = cR(col0); val g0 = cG(col0); val b0 = cB(col0)
        val r1 = cR(ctx.col1); val g1 = cG(ctx.col1); val b1 = cB(ctx.col1)
        val r2 = cR(ctx.col2); val g2 = cG(ctx.col2); val b2 = cB(ctx.col2)
        when (id) {
            0, 4 -> {
                val seq = intArrayOf(2, 2, 1, 1, 1, 0, 0, 0, 2, 2, 1, 1, 0, 0, 2, 2)
                for (i in 0 until 16) {
                    when (seq[i]) {
                        0 -> { out[i * 3] = r0; out[i * 3 + 1] = g0; out[i * 3 + 2] = b0 }
                        1 -> { out[i * 3] = r1; out[i * 3 + 1] = g1; out[i * 3 + 2] = b1 }
                        else -> { out[i * 3] = r2; out[i * 3 + 1] = g2; out[i * 3 + 2] = b2 }
                    }
                }
            }
            1 -> for (i in 0 until 16) {
                val bucket = (ctx.now / 5000).toInt()
                val c = colorWheelRaw(rnd8(bucket * 16 + i))
                out[i * 3] = cR(c); out[i * 3 + 1] = cG(c); out[i * 3 + 2] = cB(c)
            }
            2 -> for (i in 0 until 16) { out[i * 3] = r0; out[i * 3 + 1] = g0; out[i * 3 + 2] = b0 }
            3 -> for (i in 0 until 16) {
                val t = when { i <= 5 -> 0.0; i <= 10 -> (i - 5) / 5.0; else -> 1.0 }
                out[i * 3] = round(r0 + (r1 - r0) * t).toInt()
                out[i * 3 + 1] = round(g0 + (g1 - g0) * t).toInt()
                out[i * 3 + 2] = round(b0 + (b1 - b0) * t).toInt()
            }
            5 -> for (i in 0 until 16) {
                if (ctx.col2 != 0) {
                    when { i < 5 -> { out[i*3]=r0; out[i*3+1]=g0; out[i*3+2]=b0 }
                        i < 10 -> { out[i*3]=r1; out[i*3+1]=g1; out[i*3+2]=b1 }
                        i < 15 -> { out[i*3]=r2; out[i*3+1]=g2; out[i*3+2]=b2 }
                        else -> { out[i*3]=r0; out[i*3+1]=g0; out[i*3+2]=b0 } }
                } else {
                    if (i < 8) { out[i*3]=r0; out[i*3+1]=g0; out[i*3+2]=b0 }
                    else { out[i*3]=r1; out[i*3+1]=g1; out[i*3+2]=b1 }
                }
            }
        }
        return out
    }

    private val jsPaletteCache = arrayOfNulls<IntArray>(NativeEffectPalettes.LAST_ID + 1)

    private fun jsPalette(id: Int): IntArray {
        val pid = if (NativeEffectPalettes.offsetOf(id) >= 0) id else 11
        jsPaletteCache[pid]?.let { return it }
        val off = NativeEffectPalettes.offsetOf(pid)
        val out = IntArray(48)
        for (i in 0 until 16) {
            val c = NativeEffectPalettes.TABLE[off + i]
            out[i * 3] = (c shr 16) and 0xFF; out[i * 3 + 1] = (c shr 8) and 0xFF; out[i * 3 + 2] = c and 0xFF
        }
        jsPaletteCache[pid] = out
        return out
    }

    private fun getPalette(ctx: Ctx): IntArray {
        ctx.palCache?.let { return it }
        val effective = when {
            ctx.paletteId != 0 -> ctx.paletteId
            ctx.defaultPal > 0 -> ctx.defaultPal
            else -> 6
        }
        val pal = if (effective in 1..5) {
            buildDynamicPalette(ctx, effective)
        } else {
            jsPalette(effective)
        }
        ctx.palCache = pal
        return pal
    }

    private fun colorFromPaletteRaw(pal: IntArray, posIn: Int, brightness: Int, noWrap: Boolean): Int {
        var index = posIn and 0xFF
        if (noWrap) index = (index * (1 + 0xF0)) shr 8
        val hi4 = (index shr 4) and 0xF
        val lo4 = index and 0x0F
        var r = pal[hi4 * 3]; var g = pal[hi4 * 3 + 1]; var b = pal[hi4 * 3 + 2]
        if (lo4 > 0) {
            val n = (hi4 + 1) and 0xF
            val f2 = lo4 shl 4
            val f1 = 256 - f2
            r = (r * f1 + pal[n * 3] * f2) shr 8
            g = (g * f1 + pal[n * 3 + 1] * f2) shr 8
            b = (b * f1 + pal[n * 3 + 2] * f2) shr 8
        }
        if (brightness < 255) {
            val s = brightness + 1
            r = (r * s) shr 8; g = (g * s) shr 8; b = (b * s) shr 8
        }
        return rgb(r, g, b)
    }

    private fun paletteColor(ctx: Ctx, pos0to255: Int, brightness: Int = 255, noWrap: Boolean = true): Int =
        colorFromPaletteRaw(getPalette(ctx), pos0to255, brightness, noWrap)

    private fun colorFromPalette(ctx: Ctx, index: Int, mapping: Boolean, wrap: Boolean, mcol: Int, pbri: Int = 255): Int {
        if (ctx.paletteId == 0 && mcol < 3) {
            val c = when (mcol) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
            return colorFade(c, pbri)
        }
        var idx = index
        if (mapping) idx = if (ctx.seglen > 0) min((index * 255) / ctx.seglen, 255) else 0
        val noWrap = mapping && !wrap
        return colorFromPaletteRaw(getPalette(ctx), idx, pbri, noWrap)
    }

    private fun segColorWheel(ctx: Ctx, pos: Int): Int =
        if (ctx.paletteId != 0) colorFromPalette(ctx, pos, mapping = false, wrap = false, mcol = 0)
        else colorWheelRaw(pos)

    private fun fxSolid(ctx: Ctx, i: Int): Int = ctx.col0

    private fun fxBlink(ctx: Ctx, i: Int, strobe: Boolean): Int {
        var cycleTime = (255 - ctx.speed) * 20L
        var onTime = FRAMETIME
        if (!strobe) onTime += (cycleTime * ctx.intensity) shr 8
        cycleTime += FRAMETIME * 2
        if (cycleTime <= 0) cycleTime = 1
        val rem = ctx.now % cycleTime
        val on = rem <= onTime
        return if (on) colorFromPalette(ctx, i, mapping = true, wrap = false, mcol = 0)
        else ctx.col1
    }

    private fun fxBreathe(ctx: Ctx, i: Int): Int {
        var counter = ((ctx.now * ((ctx.speed shr 3) + 10)) and 0xFFFF).toInt()
        counter = (counter shr 2) + (counter shr 4)
        var v = 0
        if (counter < 16384) {
            if (counter > 8192) counter = 8192 - (counter - 8192)
            v = sin16(counter) / 103
        }
        val lum = (30 + v) and 0xFF
        return colorBlend(ctx.col1, colorFromPalette(ctx, i, true, false, 0), lum)
    }

    private fun fxColorWipe(ctx: Ctx, i: Int, rev: Boolean, randomCol: Boolean): Int {
        if (ctx.seglen <= 1) return colorFromPalette(ctx, i, true, false, 0)
        val cycleTime = 750L + (255 - ctx.speed) * 150L
        val perc = ctx.now % cycleTime
        var prog = ((perc * 65535) / cycleTime).toInt()
        val back = prog > 32767
        if (back) prog -= 32767

        val ledIndex = (prog * ctx.seglen) shr 15
        var rem = ((prog * ctx.seglen) * 2) and 0xFFFF
        rem /= (ctx.intensity + 1)
        if (rem > 255) rem = 255

        val cycle = ctx.now / cycleTime
        val col1: Int
        val col0base: Int
        if (randomCol) {
            val a0 = rnd8((cycle * 2).toInt())
            val a1 = randomWheelIndex(a0, (cycle * 2 + 1).toInt())
            col1 = colorWheelRaw(a1)
            col0base = colorWheelRaw(a0)
        } else { col1 = ctx.col1; col0base = 0 }

        val loopI = if (rev && back) ctx.seglen - 1 - i else i
        val col0 = if (randomCol) col0base else colorFromPalette(ctx, i, true, false, 0)

        return when {
            loopI < ledIndex -> if (back) col1 else col0
            loopI > ledIndex -> if (back) col0 else col1
            else -> colorBlend(if (back) col0 else col1, if (back) col1 else col0, rem)
        }
    }

    private fun fxRandomColors(ctx: Ctx, i: Int): Int {
        val cycleTime = 200L + (255 - ctx.speed) * 50L
        val it = (ctx.now / cycleTime).toInt()
        val rem = ctx.now % cycleTime
        val fadedur = (cycleTime * ctx.intensity) shr 8
        var fade = 255
        if (fadedur > 0) { fade = ((rem * 255) / fadedur).toInt(); if (fade > 255) fade = 255 }
        val a = randomWheelIndex(rnd8(it - 1), it)
        val aPrev = randomWheelIndex(rnd8(it - 2), it - 1)
        return colorBlend(segColorWheel(ctx, aPrev), segColorWheel(ctx, a), fade)
    }

    private fun fxDynamic(ctx: Ctx, i: Int): Int {
        val cycleTime = 50L + (255 - ctx.speed) * 15L
        val it = (ctx.now / cycleTime).toInt()
        var value = rnd8(i * 131 + 7)
        if (ctx.speed != 0) {
            val start = max(1, it - 64)
            for (iter in start..it) {
                if (rnd8(iter * 9173 + i * 31) <= ctx.intensity) value = rnd8(iter * 4099 + i * 131 + 7)
            }
        }
        return segColorWheel(ctx, value)
    }

    private fun fxColorloop(ctx: Ctx, i: Int): Int {
        var counter = ((ctx.now * ((ctx.speed shr 2) + 2)) and 0xFFFF).toInt()
        counter = counter shr 8
        return if (ctx.intensity < 128) colorBlend(segColorWheel(ctx, counter), 0xFFFFFF, 128 - ctx.intensity)
        else segColorWheel(ctx, counter)
    }

    private fun fxRainbow(ctx: Ctx, i: Int): Int {
        var counter = ((ctx.now * ((ctx.speed shr 2) + 2)) and 0xFFFF).toInt()
        counter = counter shr 8
        val mult = 16 shl (ctx.intensity / 29)
        val index = (((i * mult) / ctx.seglen) + counter) and 0xFF
        return segColorWheel(ctx, index)
    }

    private fun fxScan(ctx: Ctx, i: Int, dual: Boolean): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val cycleTime = 750L + (255 - ctx.speed) * 150L
        val perc = ctx.now % cycleTime
        val prog = ((perc * 65535) / cycleTime).toInt()
        val size = 1 + ((ctx.intensity * ctx.seglen) shr 9)
        val ledIndex = (prog * ((ctx.seglen * 2) - size * 2)) shr 16
        var ledOffset = ledIndex - (ctx.seglen - size)
        ledOffset = abs(ledOffset)

        if (i in ledOffset until (ledOffset + size)) {
            return colorFromPalette(ctx, i, true, false, 0)
        }
        if (dual) {
            val mirror = ctx.seglen - 1 - i
            if (mirror in ledOffset until (ledOffset + size)) {
                return colorFromPalette(ctx, i, true, false, if (ctx.col2 != 0) 2 else 0)
            }
        }
        return ctx.col1
    }

    private fun fxFade(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * ((ctx.speed shr 3) + 10)) and 0xFFFF).toInt()
        val lum = triwave16(counter) shr 8
        return colorBlend(ctx.col1, colorFromPalette(ctx, i, true, false, 0), lum)
    }

    private fun fxRunning(ctx: Ctx, i: Int, theatre: Boolean, usePalette: Boolean): Int {
        val width = (if (theatre) 3 else 1) + (ctx.intensity shr 4)
        val cycleTime = 50L + (255 - ctx.speed)
        val it = ctx.now / cycleTime
        val period = if (theatre) width else (width shl 1)
        val aux0 = (it % period).toInt()

        val c1 = if (usePalette) colorFromPalette(ctx, i, true, false, 0) else ctx.col0
        val on: Boolean = if (theatre) {
            (i % width) == aux0
        } else {
            val pos = i % (width shl 1)
            (pos < aux0 - width) || (pos >= aux0 && pos < aux0 + width)
        }
        return if (on) c1 else ctx.col1
    }

    private fun fxTheaterRainbow(ctx: Ctx, i: Int): Int {
        val width = 3 + (ctx.intensity shr 4)
        val cycleTime = 50L + (255 - ctx.speed)
        val it = ctx.now / cycleTime
        val aux0 = (it % width).toInt()
        val step = (it and 0xFF).toInt()
        val on = (i % width) == aux0
        return if (on) segColorWheel(ctx, step) else ctx.col1
    }

    private fun fxRunningBase(ctx: Ctx, i: Int, saw: Boolean, dual: Boolean): Int {
        val xScale = ctx.intensity shr 2
        val counter = ((ctx.now * ctx.speed) shr 9).toInt()

        fun sinGap(v: Int): Int = if ((v and 0x100) != 0) 0 else sin8((v + 192) and 0xFF)

        var a = (i * xScale - counter)
        if (saw) {
            a = a and 0xFF
            a = if (a < 16) 192 + a * 8 else mapInt(a, 16, 255, 64, 192)
            a = 255 - a
        }
        val s = if (dual) sinGap(a and 0x1FF) else sin8(a and 0xFF)
        var ca = colorBlend(ctx.col1, colorFromPalette(ctx, i, true, false, 0), s)
        if (dual) {
            val b = ((ctx.seglen - 1 - i) * xScale - counter) and 0x1FF
            val t = sinGap(b)
            val cb = colorBlend(ctx.col1, colorFromPalette(ctx, i, true, false, 2), t)
            ca = colorBlend(ca, cb, 127)
        }
        return ca
    }

    private fun fxRunningDual(ctx: Ctx, i: Int): Int = fxRunningBase(ctx, i, saw = false, dual = true)

    private fun fxTwinkle(ctx: Ctx, i: Int): Int {
        val cycleTime = 20L + (255 - ctx.speed) * 5L
        val it = ctx.now / cycleTime
        val maxOn = mapInt(ctx.intensity, 0, 255, 1, ctx.seglen).coerceAtLeast(1)
        val group = it / maxOn
        var prng = rnd16(group.toInt() * 2654435761.toInt())
        val countThisCycle = ((it % maxOn) + 1).toInt()
        var lit = false
        for (k in 0 until countThisCycle) {
            prng = ((prng * 2053) + 13849) and 0xFFFF
            val j = (ctx.seglen * prng) shr 16
            if (j == i) { lit = true }
        }
        return if (lit) colorFromPalette(ctx, i, true, false, 0) else ctx.col1
    }

    private fun fxSparkle(ctx: Ctx, i: Int): Int {
        val cycleTime = 10L + (255 - ctx.speed) * 2L
        val it = (ctx.now / cycleTime).toInt()
        val sparklePos = rnd16(it) % ctx.seglen
        return if (i == sparklePos) ctx.col0 else colorFromPalette(ctx, i, true, false, 1)
    }

    private fun fxAndroid(ctx: Ctx, i: Int): Int {
        val tickMs = 3L + (8L * (255 - ctx.speed)) / ctx.seglen
        val ticks = (ctx.now / max(1L, tickMs))
        val maxSize = ((ctx.intensity * ctx.seglen) / 255).coerceAtLeast(2)
        val span = maxSize - 2
        val phase = if (span > 0) (ticks % (2L * span)).toInt() else 0
        val size = if (span > 0) (if (phase < span) 2 + phase else 2 + (2 * span - phase)) else 2
        val start = (ticks % ctx.seglen).toInt()
        val end = (start + size) % ctx.seglen
        val inBand = if (start < end) (i in start until end) else (i >= start || i < end)
        return if (inBand) ctx.col0 else colorFromPalette(ctx, i, true, false, 1)
    }

    private fun fxChase(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * ((ctx.speed shr 2) + 1)) and 0xFFFF).toInt()
        val a = (counter * ctx.seglen) shr 16
        val size = 1 + ((ctx.intensity * ctx.seglen) shr 10)
        var b = a + size; if (b > ctx.seglen) b -= ctx.seglen
        var c = b + size; if (c > ctx.seglen) c -= ctx.seglen

        val color2 = if (ctx.col2 != 0) ctx.col2 else ctx.col0
        val color3 = ctx.col0

        fun inRegion(lo: Int, hi: Int): Boolean = if (lo < hi) (i in lo until hi) else (i >= lo || i < hi)
        if (inRegion(b, c)) return color3
        if (inRegion(a, b)) return color2
        return colorFromPalette(ctx, i, true, false, 1)
    }

    private fun fxColorful(ctx: Ctx, i: Int): Int {
        var numColors = 4
        val cols = IntArray(9)
        cols[0] = 0xFF0000; cols[1] = 0xEEBB00; cols[2] = 0x00EE00; cols[3] = 0x0077CC
        if (ctx.intensity > 160 || ctx.paletteId != 0) {
            if (ctx.paletteId == 0) { numColors = 3; cols[0] = ctx.col0; cols[1] = ctx.col1; cols[2] = ctx.col2 }
            else { val fac = 80; for (k in 0 until numColors) cols[k] = colorFromPalette(ctx, k * fac, false, true, 255) }
        } else if (ctx.intensity < 80) {
            cols[0] = 0xFF8040; cols[1] = 0xE5D241; cols[2] = 0x77FF77; cols[3] = 0x77F0F0
        }
        for (k in numColors until numColors * 2 - 1) cols[k] = cols[k - numColors]
        val cycleTime = 50L + (8L * (255 - ctx.speed))
        val it = ctx.now / cycleTime
        val aux0 = if (ctx.speed > 0) (it % numColors).toInt() else 0
        val j = i % numColors
        return cols[aux0 + j]
    }

    private fun fxFire2012(ctx: Ctx, i: Int): Int {
        val it = (ctx.now shr 5)
        val cool = 100 - (ctx.speed / 4)
        var heat = 0
        val sparkSpan = max(3, ctx.seglen / 4)
        val sparkBoost = (17 + ctx.custom3) * 2
        for (s in 0 until 6) {
            val sparkTick = it - s
            val sparkPos = (rnd8((sparkTick * 2749 + s).toInt())) % sparkSpan
            val sparkHeat = (96 + sparkBoost) + (rnd8((sparkTick * 6151 + s).toInt()) % 96)
            val dist = abs(i - sparkPos)
            val falloff = sparkHeat - dist * (cool / 4 + 6) - (s * 18)
            if (falloff > heat) heat = falloff
        }
        val grad = qsub8(180 - (i * 200 / ctx.seglen), (ctx.intensity / 8))
        if (grad > heat) heat = grad
        heat = heat.coerceIn(0, 240)
        val flick = (sin8((i * 13 + (ctx.now / 12).toInt()) and 0xFF) - 128) / 6
        heat = (heat + flick).coerceIn(0, 240)
        val pal = if (ctx.paletteId == 0) palFire else getPalette(ctx)
        return colorFromPaletteRaw(pal, heat, 255, false)
    }

    private fun fxLarsonScanner(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val period = 400L + (255 - ctx.speed) * 30L
        val phase = ((ctx.now % (period * 2)).toDouble() / period)
        val tri = if (phase < 1.0) phase else 2.0 - phase
        val head = tri * (ctx.seglen - 1)
        val dist = abs(i - head)
        val trail = max(1.5, (ctx.intensity / 255.0) * ctx.seglen * 0.5)
        val bri = (255.0 * exp(-dist / (trail * 0.4))).toInt().coerceIn(0, 255)
        if (bri < 2) return ctx.col1
        val c = colorFromPalette(ctx, i, true, false, 0)
        return colorBlend(ctx.col1, c, bri)
    }

    private fun fxTetrix(ctx: Ctx, i: Int): Int {
        val brickSize = max(1, 1 + (ctx.intensity shr 5))
        val dropMs = 80L + (255 - ctx.speed) * 6L
        val maxBricks = ctx.seglen / brickSize
        val totalCycle = dropMs * (maxBricks + 1)
        val phase = ctx.now % totalCycle
        val bricksLanded = (phase / dropMs).toInt().coerceAtMost(maxBricks)

        val stackBottom = ctx.seglen - bricksLanded * brickSize
        if (i >= stackBottom) {
            val brickIdx = (ctx.seglen - 1 - i) / brickSize
            return segColorWheel(ctx, (brickIdx * 37) and 0xFF)
        }
        val fallProg = (phase % dropMs).toDouble() / dropMs
        val fallTarget = stackBottom - brickSize
        if (fallTarget >= 0) {
            val fallPos = (fallProg * fallTarget).toInt()
            if (i in fallPos until (fallPos + brickSize)) {
                return segColorWheel(ctx, (bricksLanded * 37) and 0xFF)
            }
        }
        return ctx.col1
    }

    private fun fxFireworks(ctx: Ctx, i: Int): Int {
        val cycleTime = 20L + (255 - ctx.speed)
        val it = ctx.now / cycleTime
        var best = 0
        var bestColor = ctx.col1
        for (age in 0 until 8) {
            val tick = it - age
            for (s in 0 until 3) {
                val gate = rnd8((tick * 7919 + s * 131).toInt())
                if (gate > ctx.intensity / 2) continue
                val pos = rnd16((tick * 104729 + s * 9973).toInt()) % ctx.seglen
                if (pos == i) {
                    val bri = 255 - age * 32
                    if (bri > best) {
                        best = bri
                        bestColor = segColorWheel(ctx, rnd8((tick * 13 + s).toInt()))
                    }
                }
            }
        }
        return if (best > 0) colorBlend(ctx.col1, bestColor, best) else ctx.col1
    }

    private fun fxPride2015(ctx: Ctx, i: Int): Int {
        val now = ctx.now
        val sat8 = beatsin88(now, 87, 220, 250)
        val brightdepth = beatsin88(now, 341, 96, 224)
        val brightnessthetainc16 = beatsin88(now, 203, 25 * 256, 40 * 256)
        val hueinc16 = beatsin88(now, 113, 1, 3000)

        val duration = 10 + ctx.speed
        val frames = now / FRAMETIME
        val msmultiplier = beatsin88(now, 147, 23, 60)
        val sPseudotime = ((frames * duration * msmultiplier) and 0xFFFFFFFFL)
        val sHue16 = ((frames * duration * beatsin88(now, 400, 5, 9)) and 0xFFFFL).toInt()

        val hue16 = (sHue16 + hueinc16 * (i + 1)) and 0xFFFF
        val hue8 = (hue16 shr 8) and 0xFF
        val brightnesstheta16 = ((sPseudotime + brightnessthetainc16.toLong() * (i + 1)) and 0xFFFFL).toInt()
        val b16 = (sin16(brightnesstheta16) + 32768) and 0xFFFF
        val bri16 = (b16 * b16) / 65536
        val bri8 = ((bri16 * brightdepth) / 65536 + (255 - brightdepth)).coerceIn(0, 255)
        val color = hsv2rgb(hue8, sat8, bri8)
        return rgb(gamma8inv(cR(color)), gamma8inv(cG(color)), gamma8inv(cB(color)))
    }

    private fun fxJuggle(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return 0
        var out = 0
        var dothue = 0
        for (k in 0 until 8) {
            val pos = beatsin16(ctx.now, (16 + ctx.speed) * (k + 7), 0, ctx.seglen - 1)
            if (pos == i) {
                val col = if (ctx.paletteId == 0) hsv2rgb(dothue, 220, 255)
                else colorFromPalette(ctx, dothue, false, false, 0)
                out = rgb(cR(out) or cR(col), cG(out) or cG(col), cB(out) or cB(col))
            }
            dothue += 32
        }
        return if (out == 0) ctx.col1.let { if (it == 0) 0 else colorFade(it, 40) } else out
    }

    private fun fxPalette(ctx: Ctx, i: Int): Int {
        val cols = ctx.seglen
        val inputShift = ctx.speed
        val inputSize = ctx.intensity
        val maxX = max(1, cols - 1)
        var colorIndex = (i.toLong() * 255 / maxX).toInt()
        colorIndex = if (inputSize <= 128) (colorIndex * inputSize) / 128
        else ((inputSize - 112) * colorIndex) / 16
        colorIndex += ctx.custom1
        val paletteOffset = (((ctx.now * ((inputShift shr 3) + 1)) and 0xFFFF) shr 8).toInt()
        colorIndex -= paletteOffset
        return segColorWheel(ctx, colorIndex and 0xFF)
    }

    private fun fxFireFlicker(ctx: Ctx, i: Int): Int {
        val cycleTime = 40L + (255 - ctx.speed)
        val it = (ctx.now / cycleTime).toInt()
        val r = cR(ctx.col0); val g = cG(ctx.col0); val b = cB(ctx.col0)
        var lum = if (ctx.paletteId == 0) max(r, max(g, b)) else 255
        lum /= (((256 - ctx.intensity) / 16) + 1)
        val flicker = rnd8(it * 9176 + i * 131) % (lum + 1)
        return if (ctx.paletteId == 0) {
            rgb(max(r - flicker, 0), max(g - flicker, 0), max(b - flicker, 0))
        } else {
            colorFromPalette(ctx, i, true, false, 0, 255 - flicker)
        }
    }

    private fun fxColorwaves(ctx: Ctx, i: Int): Int {
        val now = ctx.now
        val brightdepth = beatsin88(now, 341, 96, 224)
        val brightnessthetainc16 = beatsin88(now, 203, 25 * 256, 40 * 256)
        val hueinc16 = (beatsin88(now, 113, 60, 300) * ctx.intensity * 10) / 255
        val duration = 10 + ctx.speed
        val frames = now / FRAMETIME
        val msmultiplier = beatsin88(now, 147, 23, 60)
        val sPseudotime = ((frames * duration * msmultiplier) and 0xFFFFFFFFL)
        val sHue16 = ((frames * duration * beatsin88(now, 400, 5, 9)) and 0xFFFFL).toInt()

        val hue16 = (sHue16 + hueinc16 * (i + 1)) and 0xFFFF
        val hue8 = (hue16 shr 8) and 0xFF
        val brightnesstheta16 = ((sPseudotime + brightnessthetainc16.toLong() * (i + 1)) and 0xFFFFL).toInt()
        val b16 = (sin16(brightnesstheta16) + 32768) and 0xFFFF
        val bri16 = (b16 * b16) / 65536
        val bri8 = ((bri16 * brightdepth) / 65536 + (255 - brightdepth)).coerceIn(0, 255)
        return colorFromPaletteRaw(getPalette(ctx), hue8, bri8, false)
    }

    private fun fxBpm(ctx: Ctx, i: Int): Int {
        val now = ctx.now
        val bpm = 62
        val beat = beatsin8(now, bpm, 64, 255)
        val hue = (ctx.now / 50).toInt()
        val idx = (hue + i * 2) and 0xFF
        val bri = (beat - i * 10).coerceIn(0, 255)
        return colorFromPaletteRaw(getPalette(ctx), idx, bri, false)
    }

    private fun fxSinelon(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return 0
        val pos = beatsin16(ctx.now, ctx.speed / 10, 0, ctx.seglen - 1)
        val dist = abs(i - pos)
        val trail = max(1.0, (255 - ctx.intensity) / 16.0)
        val bri = (255.0 * exp(-dist / trail)).toInt().coerceIn(0, 255)
        if (bri < 2) return ctx.col1.let { if (it == 0) 0 else colorFade(it, 30) }
        val c = colorFromPalette(ctx, pos, true, false, 0)
        return colorBlend(if (ctx.col1 == 0) 0 else ctx.col1, c, bri)
    }

    private fun fxPlasma(ctx: Ctx, i: Int): Int {
        val thisPhase = beatsin8(ctx.now, 6, 0, 128) - 64
        val thatPhase = beatsin8(ctx.now, 7, 0, 128) - 64
        val colorIndex = (cubicwave8(((i * (2 + 3 * (ctx.speed shr 5)) + thisPhase)) and 0xFF) / 2) +
            (cos8(((i * (1 + 2 * (ctx.speed shr 5)) + thatPhase)) and 0xFF) / 2)
        val thisBright = qsub8(colorIndex and 0xFF, beatsin8(ctx.now, 7, 0, 128 - (ctx.intensity shr 1)))
        return colorFromPalette(ctx, colorIndex and 0xFF, false, false, 0, thisBright)
    }

    private fun fxHeartbeat(ctx: Ctx, i: Int): Int {
        val bpm = 40 + (ctx.speed shr 3)
        val msPerBeat = 60000L / bpm
        val secondBeat = msPerBeat / 3
        val beatTimer = ctx.now % msPerBeat
        val pulse: Int = when {
            beatTimer < FRAMETIME -> 255
            beatTimer in secondBeat until (secondBeat + FRAMETIME) -> 200
            else -> {
                val decay = if (beatTimer < secondBeat) beatTimer else beatTimer - secondBeat
                (255.0 * exp(-decay.toDouble() / (60.0 + ctx.intensity))).toInt().coerceIn(0, 255)
            }
        }
        return colorBlend(colorFromPalette(ctx, i, true, false, 0), ctx.col1, 255 - pulse)
    }

    private fun fxPacifica(ctx: Ctx, i: Int): Int {
        val deltat = (ctx.now shr 2) + ((ctx.now * ctx.speed) shr 7)
        val t = deltat

        fun layer(pal: IntArray?, ciStart: Int, wavescale: Int, bri: Int, ioff: Int): Int {
            var ci = ciStart and 0xFFFF
            var waveangle = ioff and 0xFFFF
            val wavescaleHalf = ((wavescale shr 1) + 20) and 0xFFFF
            waveangle = (waveangle + ((120 + ctx.intensity) * i)) and 0xFFFF
            val s16 = (sin16(waveangle) + 32768) and 0xFFFF
            val cs = (scale16(s16, wavescaleHalf) + wavescaleHalf) and 0xFFFF
            ci = (ci + cs * i) and 0xFFFF
            val sindex16 = (sin16(ci) + 32768) and 0xFFFF
            val sindex8 = scale16(sindex16, 240) and 0xFF
            return if (pal != null) colorFromPaletteRaw(pal, sindex8, bri, false)
            else colorFromPalette(ctx, sindex8, false, false, 0, bri)
        }

        val useCustom = ctx.paletteId != 0
        val p1 = if (useCustom) null else pacificaP1
        val p2 = if (useCustom) null else pacificaP2
        val p3 = if (useCustom) null else pacificaP3

        val ciS1 = (beat16(t, 11) + beat16(t, 13)) and 0xFFFF
        val ciS2 = (-(beat16(t, 8) + beat16(t, 11))) and 0xFFFF
        val ciS3 = (-(beat16(t, 5))) and 0xFFFF
        val ciS4 = (-(beat16(t, 4))) and 0xFFFF

        var cr = 2; var cg = 6; var cb = 10
        val l1 = layer(p1, ciS1, beatsin16(t, 3, 11 * 256, 14 * 256), beatsin8(t, 10, 70, 130), (-beat16(t, 301)) and 0xFFFF)
        val l2 = layer(p2, ciS2, beatsin16(t, 4, 6 * 256, 9 * 256), beatsin8(t, 17, 40, 80), beat16(t, 401))
        val l3 = layer(p3, ciS3, 6 * 256, beatsin8(t, 9, 10, 38), (-beat16(t, 503)) and 0xFFFF)
        val l4 = layer(p3, ciS4, 5 * 256, beatsin8(t, 8, 10, 28), beat16(t, 601))
        for (lc in intArrayOf(l1, l2, l3, l4)) {
            cr = min(255, cr + cR(lc)); cg = min(255, cg + cG(lc)); cb = min(255, cb + cB(lc))
        }
        val basethreshold = beatsin8(t, 9, 55, 65)
        val wave = (beat8(t, 7) + i * 7) and 0xFF
        val threshold = scale8(sin8(wave), 20) + basethreshold
        val avg = (cr + cg + cb) shr 2
        if (avg > threshold) {
            val overage = avg - threshold
            val overage2 = qadd8(overage, overage)
            cr = min(255, cr + overage); cg = min(255, cg + overage2); cb = min(255, cb + qadd8(overage2, overage2))
        }
        cb = scale8(cb, 145); cg = scale8(cg, 200)
        cr = cr or 2; cg = cg or 5; cb = cb or 7
        return rgb(cr, cg, cb)
    }

    private val pacificaP1 = run {
        val hex = intArrayOf(0x000507,0x000409,0x00030B,0x00030D,0x000210,0x000212,0x000114,0x000117,
            0x000019,0x00001C,0x000026,0x000031,0x00003B,0x000046,0x14554B,0x28AA50)
        IntArray(48).also { for (k in 0 until 16) { it[k*3]=(hex[k] ushr 16) and 0xFF; it[k*3+1]=(hex[k] ushr 8) and 0xFF; it[k*3+2]=hex[k] and 0xFF } }
    }
    private val pacificaP2 = run {
        val hex = intArrayOf(0x000507,0x000409,0x00030B,0x00030D,0x000210,0x000212,0x000114,0x000117,
            0x000019,0x00001C,0x000026,0x000031,0x00003B,0x000046,0x0C5F52,0x19BE5F)
        IntArray(48).also { for (k in 0 until 16) { it[k*3]=(hex[k] ushr 16) and 0xFF; it[k*3+1]=(hex[k] ushr 8) and 0xFF; it[k*3+2]=hex[k] and 0xFF } }
    }
    private val pacificaP3 = run {
        val hex = intArrayOf(0x000208,0x00030E,0x000514,0x00061A,0x000820,0x000927,0x000B2D,0x000C33,
            0x000E39,0x001040,0x001450,0x001860,0x001C70,0x002080,0x1040BF,0x2060FF)
        IntArray(48).also { for (k in 0 until 16) { it[k*3]=(hex[k] ushr 16) and 0xFF; it[k*3+1]=(hex[k] ushr 8) and 0xFF; it[k*3+2]=hex[k] and 0xFF } }
    }

    private fun fxSine(ctx: Ctx, i: Int): Int {
        val colorIndex = (ctx.now / 32).toInt()
        val step = (ctx.now / FRAMETIME).toInt() * (ctx.speed / 16)
        val freq = ctx.intensity / 4
        val pixBri = cubicwave8((i * freq + step) and 0xFF)
        val palPos = ((i * colorIndex) / 255) and 0xFF
        return colorBlend(ctx.col1, colorFromPalette(ctx, palPos, false, false, 0), pixBri)
    }

    private fun fxFlow(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * ctx.speed) shr 14).toInt()
        val zones = 1 + ((ctx.intensity * max(1, ctx.seglen / 6)) shr 8)
        val index = ((i * 255 * zones / ctx.seglen) - counter) and 0xFF
        return segColorWheel(ctx, index)
    }

    private fun fxFlowStripe(ctx: Ctx, i: Int): Int {
        val hl = ctx.seglen * 10 / 13
        val hue = (ctx.now / (1500 - ctx.speed * 5 / 2).coerceAtLeast(1)).toInt()
        val t = (ctx.now / 100).toInt()
        val c = (i * (ctx.intensity / 8) + t) % max(1, hl)
        val b = (c * 255 / max(1, hl))
        return hsv2rgb((hue + i * 2) and 0xFF, 255, 255).let { colorFade(it, 255 - b) }
    }

    private fun fxWavesins(ctx: Ctx, i: Int): Int {
        val bri = sin8(((ctx.now / 4).toInt() + i * ctx.intensity) and 0xFF)
        val idx = beatsin8(
            ctx.now, ctx.speed,
            ctx.custom1, (ctx.custom1 + ctx.custom2).coerceAtMost(255),
            phase = (i * (ctx.custom3 shl 3)) and 0xFF,
        )
        return colorFromPalette(ctx, idx, false, false, 0, bri)
    }

    private fun trailBri(dist: Double, trailLen: Double): Int =
        (255.0 * exp(-dist / max(0.5, trailLen))).toInt().coerceIn(0, 255)

    private fun fadeBg(ctx: Ctx, amt: Int = 30): Int = if (ctx.col1 == 0) 0 else colorFade(ctx.col1, amt)

    private fun fxDissolve(ctx: Ctx, i: Int, randomCol: Boolean): Int {
        val period = (255 - ctx.speed) + 15
        val full = period.coerceAtLeast(1).toLong()
        val cyc = ctx.now / (full * 2L)
        val phaseT = (ctx.now % (full * 2L)).toDouble() / (full * 2L)
        val filling = phaseT < 0.5
        val frac = if (filling) phaseT * 2.0 else (phaseT - 0.5) * 2.0
        val flipAt = (rnd8(i * 2654435761.toInt() + cyc.toInt() * 40503) / 255.0)
        val on = if (filling) frac > flipAt else frac < flipAt
        if (!on) return ctx.col1
        val color = if (randomCol) colorWheelRaw(rnd8(i * 7919 + cyc.toInt() * 13))
        else colorFromPalette(ctx, i, true, false, 0)
        return color
    }

    private fun fxSparkleDark(ctx: Ctx, i: Int): Int {
        val bg = colorFromPalette(ctx, i, true, false, 0)
        val cycle = (255 - ctx.speed).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycle).toInt()
        val pos = rnd16(it) % ctx.seglen
        return if (i == pos && (rnd8(it * 31) and 0x0F) == 0) ctx.col1 else bg
    }

    private fun fxSparklePlus(ctx: Ctx, i: Int): Int {
        val bg = colorFromPalette(ctx, i, true, false, 0)
        val cycle = (255 - ctx.speed).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycle).toInt()
        val len = max(1, ctx.seglen / 3)
        var lit = false
        if ((rnd8(it * 17) and 0x0F) == 0) {
            for (k in 0 until len) { if (rnd16(it * 131 + k) % ctx.seglen == i) { lit = true; break } }
        }
        return if (lit) ctx.col1 else bg
    }

    private fun fxStrobeRainbow(ctx: Ctx, i: Int): Int {
        var cycleTime = (255 - ctx.speed) * 20L + FRAMETIME * 2
        if (cycleTime <= 0) cycleTime = 1
        val it = ctx.now / cycleTime
        val rem = ctx.now % cycleTime
        val on = rem <= FRAMETIME
        return if (on) segColorWheel(ctx, (it and 0xFF).toInt()) else ctx.col1
    }

    private fun fxMultiStrobe(ctx: Ctx, i: Int): Int {
        val bg = colorFromPalette(ctx, i, true, false, 1)
        val count = 2 * ((ctx.intensity / 10) + 1)
        val baseDelay = 50 + 20 * (255 - ctx.speed)
        val unit = (baseDelay + 50L).coerceAtLeast(1)
        val phase = ((ctx.now / 35L) % (count + 2).toLong()).toInt()
        val flashing = phase < count && (phase and 1) == 0
        return if (flashing) ctx.col0 else bg
    }

    private fun fxBlinkRainbow(ctx: Ctx, i: Int): Int {
        var cycleTime = (255 - ctx.speed) * 20L
        var onTime = FRAMETIME + ((cycleTime * ctx.intensity) shr 8)
        cycleTime += FRAMETIME * 2
        if (cycleTime <= 0) cycleTime = 1
        val it = ctx.now / cycleTime
        val rem = ctx.now % cycleTime
        return if (rem <= onTime) segColorWheel(ctx, (it and 0xFF).toInt()) else ctx.col1
    }

    private fun chaseRegions(ctx: Ctx, i: Int, color1: Int, color2: Int, color3: Int, doPalette: Boolean): Int {
        val counter = ((ctx.now * ((ctx.speed shr 2) + 1)) and 0xFFFF).toInt()
        val a = (counter * ctx.seglen) shr 16
        val size = 1 + ((ctx.intensity * ctx.seglen) shr 10)
        var b = a + size; if (b > ctx.seglen) b -= ctx.seglen
        var c = b + size; if (c > ctx.seglen) c -= ctx.seglen
        fun inRegion(lo: Int, hi: Int): Boolean = if (lo < hi) (i in lo until hi) else (i >= lo || i < hi)
        if (inRegion(a, b)) return color2
        if (inRegion(b, c)) return color3
        return if (doPalette) colorFromPalette(ctx, i, true, false, 1) else color1
    }

    private fun fxChaseRandom(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * ((ctx.speed shr 2) + 1)) and 0xFFFF).toInt()
        val a = (counter * ctx.seglen) shr 16
        val cyc = (ctx.now / max(1L, (255 - ctx.speed + 1).toLong()))
        val lead = colorWheelRaw(randomWheelIndex(rnd8(cyc.toInt()), cyc.toInt() + 1))
        val res = chaseRegions(ctx, i, lead, if (ctx.col2 != 0) ctx.col2 else ctx.col0, ctx.col0, false)
        return if (i >= a) lead else res
    }

    private fun fxChaseRainbow(ctx: Ctx, i: Int): Int {
        var colorSep = 256 / max(1, ctx.seglen); if (colorSep == 0) colorSep = 1
        val step = (ctx.now / max(1L, (255 - ctx.speed + 1).toLong())).toInt()
        val color = segColorWheel(ctx, (step * colorSep) and 0xFF)
        return chaseRegions(ctx, i, color, ctx.col0, ctx.col1, false)
    }

    private fun fxChaseFlash(ctx: Ctx, i: Int): Int {
        val bg = colorFromPalette(ctx, i, true, false, 0)
        val FLASH_COUNT = 4
        val unit = (10L + (30L * (255 - ctx.speed)) / ctx.seglen).coerceAtLeast(1)
        val totalSteps = FLASH_COUNT * 2 + 1
        val tick = (ctx.now / unit)
        val head = (tick / totalSteps % ctx.seglen).toInt()
        val flashStep = (tick % totalSteps).toInt()
        val mp1 = (head + 1) % ctx.seglen
        if (flashStep < FLASH_COUNT * 2 && (flashStep and 1) == 0) {
            if (i == head || i == mp1) return ctx.col1
        }
        return bg
    }

    private fun fxChaseFlashRandom(ctx: Ctx, i: Int): Int {
        val FLASH_COUNT = 4
        val unit = (1L + (10L * (255 - ctx.speed)) / ctx.seglen).coerceAtLeast(1)
        val totalSteps = FLASH_COUNT * 2 + 1
        val tick = (ctx.now / unit)
        val head = (tick / totalSteps % ctx.seglen).toInt()
        val flashStep = (tick % totalSteps).toInt()
        val cyc = (tick / (totalSteps.toLong() * ctx.seglen))
        val wheel = randomWheelIndex(rnd8(cyc.toInt()), cyc.toInt() + 7)
        if (i < head) return colorWheelRaw(wheel)
        val mp1 = (head + 1) % ctx.seglen
        if (flashStep < FLASH_COUNT * 2) {
            if (flashStep and 1 == 0) { if (i == head || i == mp1) return ctx.col0 }
            else { if (i == head) return colorWheelRaw(wheel); if (i == mp1) return ctx.col1 }
        }
        return ctx.col1
    }

    private fun fxRainbowRunner(ctx: Ctx, i: Int): Int {
        val n = (ctx.now / max(1L, (255 - ctx.speed + 1).toLong())).toInt() % ctx.seglen
        val mp1 = (n + 1) % ctx.seglen
        val call = (ctx.now / FRAMETIME).toInt() and 0xFF
        val color2 = segColorWheel(ctx, ((n * 256 / max(1, ctx.seglen)) + call) and 0xFF)
        val color3 = segColorWheel(ctx, ((mp1 * 256 / max(1, ctx.seglen)) + call) and 0xFF)
        return chaseRegions(ctx, i, ctx.col0, color2, color3, false)
    }

    private fun fxTrafficLight(ctx: Ctx, i: Int): Int {
        val bg = colorFromPalette(ctx, i, true, false, 1)
        if (ctx.seglen <= 1) return bg
        val mdelay = (150L + 100L * (255 - ctx.speed)).coerceAtLeast(1)
        val state = ((ctx.now / mdelay) % 4L).toInt()
        val group = i % 3
        val groupStart = (i / 3) * 3
        if (i >= ctx.seglen - 2) return bg
        return when (state) {
            0 -> if (group == 0) 0xFF0000 else bg
            1 -> when (group) { 0 -> 0xFF0000; 1 -> 0xEECC00; else -> bg }
            2 -> if (group == 2) 0x00FF00 else bg
            else -> if (group == 1) 0xEECC00 else bg
        }
    }

    private fun fxAurora(ctx: Ctx, i: Int): Int {
        val waveCount = mapInt(ctx.intensity, 0, 255, 2, 20).coerceIn(2, 20)
        var mr = 0; var mg = 0; var mb = 0
        for (w in 0 until waveCount) {
            val width = max(1, mapInt(rnd8(w * 131 + 9), 0, 255, ctx.seglen / 20, ctx.seglen / 6) + 1)
            val period = 4000L + rnd16(w * 17) % 6000L
            val phase = ((ctx.now + rnd16(w * 4099) % period) % period).toDouble() / period
            val goingLeft = (rnd8(w * 7) and 1) == 1
            val travel = if (goingLeft) (1.0 - phase) else phase
            val center = travel * (ctx.seglen + 2 * width) - width
            val dist = abs(i - center)
            if (dist > width) continue
            val ageEnv = sin8(((ctx.now / 16 + w * 40) and 0xFF).toInt())
            val brightnessFactor = ((1.0 - dist / width) * (ageEnv / 255.0) * 0.85)
            val base = colorFromPalette(ctx, rnd8(w * 9173), false, false, rnd8(w * 31) % 3)
            mr = min(255, mr + (cR(base) * brightnessFactor).toInt())
            mg = min(255, mg + (cG(base) * brightnessFactor).toInt())
            mb = min(255, mb + (cB(base) * brightnessFactor).toInt())
        }
        return rgb(mr, mg, mb)
    }

    private fun fxRunningRandom(ctx: Ctx, i: Int): Int {
        val cycleTime = (25 + 3 * (255 - ctx.speed)).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycleTime).toInt()
        val zoneSize = ((255 - ctx.intensity) shr 4) + 1
        val zone = (i + it) / zoneSize
        val wheel = randomWheelIndex(rnd8(zone * 2654435761.toInt()), zone * 40503 + 1)
        return segColorWheel(ctx, wheel)
    }

    private fun fxComet(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val counter = ((ctx.now * ((ctx.speed shr 2) + 1)) and 0xFFFF).toInt()
        val head = (counter * ctx.seglen) shr 16
        val trail = max(1.0, (255 - ctx.intensity) / 12.0)
        val dist = (head - i).toDouble()
        if (dist < -0.5) return fadeBg(ctx, 20)
        val bri = trailBri(dist, trail)
        if (bri < 2) return fadeBg(ctx, 20)
        return colorBlend(0, colorFromPalette(ctx, i, true, false, 0), bri)
    }

    private fun fxRain(ctx: Ctx, i: Int): Int {
        val shiftMs = (150L + (255 - ctx.speed) * 8L).coerceAtLeast(1)
        val shift = (ctx.now / shiftMs).toInt()
        var best = 0
        var bestColor = ctx.col1
        for (age in 0 until 12) {
            val tick = (ctx.now / shiftMs) - age
            for (s in 0 until max(1, ctx.seglen / 20)) {
                if (rnd8((tick * 7919 + s * 131).toInt()) > (ctx.intensity shr 1)) continue
                val spawn = rnd16((tick * 104729 + s * 9973).toInt()) % ctx.seglen
                val pos = (spawn - age + ctx.seglen * 4) % ctx.seglen
                if (pos == i) {
                    val bri = 255 - age * 22
                    if (bri > best) { best = bri; bestColor = colorFromPalette(ctx, rnd8((tick * 13 + s).toInt()), false, false, 0) }
                }
            }
        }
        return if (best > 0) colorBlend(ctx.col1, bestColor, best) else ctx.col1
    }

    private fun fxGradient(ctx: Ctx, i: Int, loading: Boolean): Int {
        if (ctx.seglen <= 1) return colorFromPalette(ctx, i, true, false, 1)
        val counter = ((ctx.now * ((ctx.speed shr 2) + 1)) and 0xFFFF).toInt()
        val pp = (counter * ctx.seglen) shr 16
        val brd = (ctx.intensity shr 1).coerceAtLeast(1)
        val p1 = pp - ctx.seglen; val p2 = pp + ctx.seglen
        var v = if (loading) abs((if (i > pp) p2 else pp) - i)
        else min(abs(pp - i), min(abs(p1 - i), abs(p2 - i)))
        v = if (brd > v) (v * 255) / brd else 255
        return colorBlend(ctx.col0, colorFromPalette(ctx, i, true, false, 1), v and 0xFF)
    }

    private fun fxRollingBalls(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val numBalls = (ctx.intensity / 16) + 1
        val hasCol2 = ctx.col2 != 0
        var out = if (hasCol2) 0 else ctx.col1
        val cfac = (scale8(8, 255 - ctx.speed) + 1) * 1000.0
        for (b in 0 until numBalls) {
            val vel = (10.0 * (1.0 + rnd8(b * 131) / 255.0)) * (if ((rnd8(b * 31) and 1) == 0) -1 else 1)
            val h0 = rnd8(b * 7919) / 255.0
            val t = (ctx.now / cfac) * abs(vel) + h0
            var ph = t % 2.0; if (ph > 1.0) ph = 2.0 - ph
            val pos = (ph * (ctx.seglen - 1)).toInt()
            if (pos == i) {
                out = when {
                    ctx.paletteId != 0 -> colorFromPalette(ctx, b * 255 / numBalls, false, false, 0)
                    hasCol2 -> when (b % 3) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
                    else -> ctx.col0
                }
            }
        }
        return out
    }

    private fun fxFairy(ctx: Ctx, i: Int): Int {
        val base = colorFromPalette(ctx, rnd8(i * 2053 + 1384) , false, false, 0)
        if (ctx.intensity == 0) return base
        val flasherDistance = ((255 - ctx.intensity) / 28) + 1
        if (i % flasherDistance != 0) return colorFade(base, mapInt(beatsin8(ctx.now, 4, 0, 255, phase = i), 0, 255, 183, 255))
        val cyc = (ctx.now / 600L).toInt()
        val onPhase = beatsin8(ctx.now, 6 + (i and 7), 0, 255, phase = i * 37)
        return colorBlend(ctx.col1, base, onPhase)
    }

    private fun fxTwoDots(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col2
        val delay = 1 + ((FRAMETIME shl 3) / ctx.seglen)
        val per = mapInt(ctx.speed, 0, 255, (delay shl 4).toInt(), delay.toInt()).coerceAtLeast(1).toLong()
        val offset = ((ctx.now / per) % ctx.seglen).toInt()
        var width = (ctx.seglen * (ctx.intensity + 1)) shr 9; if (width == 0) width = 1
        val color1 = ctx.col0
        val color2 = if (ctx.col1 == ctx.col2) color1 else ctx.col1
        for (k in 0 until width) {
            if ((offset + k) % ctx.seglen == i) return color1
            if ((offset + k + (ctx.seglen shr 1)) % ctx.seglen == i) return color2
        }
        return ctx.col2
    }

    private fun fxFairytwinkle(ctx: Ctx, i: Int): Int {
        val base = colorFromPalette(ctx, rnd8(i * 2053 + 4099), false, false, 0)
        val bpm = 3 + (255 - ctx.speed) / 40
        val phase = beatsin8(ctx.now, bpm, 0, 255, phase = rnd8(i * 131))
        val gate = rnd8(i * 9173) <= ctx.intensity
        val bri = if (gate) phase else phase / 4
        return colorBlend(ctx.col1, base, bri)
    }

    private fun fxTricolorChase(ctx: Ctx, i: Int): Int {
        val cycleTime = (50 + ((255 - ctx.speed) shl 1)).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycleTime).toInt()
        val width = 1 + (ctx.intensity shr 4)
        val idx = ((it + (ctx.seglen - 1 - i)) % (width * 3))
        return when {
            idx > (width shl 1) - 1 -> colorFromPalette(ctx, ctx.seglen - 1 - i, true, false, 1)
            idx > width - 1 -> ctx.col0
            else -> ctx.col2
        }
    }

    private fun fxTriWipe(ctx: Ctx, i: Int): Int {
        val cycleTime = (1000 + (255 - ctx.speed) * 200).toLong()
        val perc = ctx.now % cycleTime
        val prog = ((perc * 65535) / cycleTime).toInt()
        val ledIndex = ((prog.toLong() * ctx.seglen * 3) ushr 16).toInt()
        val basePal = colorFromPalette(ctx, i, true, false, 2)
        return when {
            ledIndex < ctx.seglen -> if (i > ledIndex) ctx.col0 else ctx.col1
            ledIndex < ctx.seglen * 2 -> { val off = ledIndex - ctx.seglen; if (i > off) ctx.col1 else basePal }
            else -> { val off = ledIndex - ctx.seglen * 2; if (i <= off) ctx.col0 else basePal }
        }
    }

    private fun fxTricolorFade(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * ((ctx.speed shr 3) + 1)) and 0xFFFF).toInt()
        val prog = (counter * 768) shr 16
        val stp = prog and 0xFF
        val pal = colorFromPalette(ctx, i, true, false, 2)
        return when {
            prog < 256 -> colorBlend(ctx.col0, ctx.col1, stp)
            prog < 512 -> colorBlend(ctx.col1, pal, stp)
            else -> colorBlend(pal, ctx.col0, stp)
        }
    }

    private fun fxLightning(ctx: Ctx, i: Int): Int {
        val strikeMs = (300L + rnd16((ctx.now / 700L).toInt()) % 1200L)
        val strikeIdx = (ctx.now / 350L).toInt()
        val sub = (ctx.now / 40L).toInt() % 8
        if (sub and 1 == 1) return ctx.col1
        val start = rnd16(strikeIdx * 7919) % ctx.seglen
        val len = 1 + rnd16(strikeIdx * 104729) % max(1, ctx.seglen - start)
        if (i in start until (start + len)) {
            val bri = if (sub == 0) 52 else (255 / (1 + rnd8(strikeIdx * 13 + sub) % 3))
            return colorFromPalette(ctx, i, true, false, 0, bri)
        }
        return ctx.col1
    }

    private fun fxIcu(ctx: Ctx, i: Int): Int {
        val bg = if (ctx.col1 == 0) 0 else ctx.col1
        val space = (ctx.intensity shr 3) + 2
        val reach = max(1, ctx.seglen - ctx.seglen / space)
        val cycle = 1200L + (255 - ctx.speed) * 12L
        val ph = (ctx.now % cycle).toDouble() / cycle
        if (ph in 0.46..0.52) return bg
        val tri = if (ph < 0.5) ph * 2 else 2 - ph * 2
        val dest = (tri * reach).toInt()
        val eye2 = dest + ctx.seglen / space
        val pindex = mapInt(dest, 0, reach, 0, 255)
        val col = colorFromPalette(ctx, pindex, false, false, 0)
        return if (i == dest || i == eye2) col else bg
    }

    private fun fxMultiComet(ctx: Ctx, i: Int): Int {
        val cycleTime = (10 + (255 - ctx.speed)).coerceAtLeast(1).toLong()
        var best = fadeBg(ctx, (ctx.intensity shr 1) + 128 - 128)
        var bestBri = 0
        for (cmt in 0 until 8) {
            val span = ctx.seglen + ctx.seglen / 2
            val offset = (cmt * span / 8)
            val head = ((ctx.now / cycleTime).toInt() + offset) % span
            val dist = (head - i).toDouble()
            if (dist < -0.5 || head >= ctx.seglen) continue
            val bri = trailBri(dist, max(1.0, ctx.seglen / 8.0))
            if (bri > bestBri) {
                bestBri = bri
                val col = if (ctx.col2 != 0 && (cmt and 1) == 0) ctx.col2 else colorFromPalette(ctx, i, true, false, 0)
                best = colorBlend(0, col, bri)
            }
        }
        return best
    }

    private fun fxDualLarsonScanner(ctx: Ctx, i: Int): Int {
        val a = fxLarsonScanner(ctx, i)
        val mirror = ctx.seglen - 1 - i
        val b = fxLarsonScanner(ctx, mirror)
        return rgb(max(cR(a), cR(b)), max(cG(a), cG(b)), max(cB(a), cB(b)))
    }

    private fun fxStream2(ctx: Ctx, i: Int): Int {
        val cycleTime = (25 + 3 * (255 - ctx.speed)).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycleTime).toInt()
        val key = (i + it)
        val cr = if (rnd8(key * 6 + 1) % 6 != 0) rnd8(key * 3) else rnd8(key * 17)
        val cg = if (rnd8(key * 6 + 2) % 6 != 0) rnd8(key * 5 + 1) else rnd8(key * 19)
        val cb = if (rnd8(key * 6 + 3) % 6 != 0) rnd8(key * 7 + 2) else rnd8(key * 23)
        return rgb(cr, cg, cb)
    }

    private fun fxOscillate(ctx: Ctx, i: Int): Int {
        val cycleTime = (20 + 2 * (255 - ctx.speed)).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycleTime)
        val size = ctx.seglen / (3 + ctx.intensity / 8)
        var color = 0
        val specs = arrayOf(intArrayOf(1, 1), intArrayOf(3, 2), intArrayOf(2, 1))
        for (j in 0 until 3) {
            val sp = specs[j][1]
            val span = (ctx.seglen - 1).coerceAtLeast(1)
            val raw = ((it * sp) % (2L * span)).toInt()
            val pos = if (raw <= span) raw else 2 * span - raw
            if (i >= pos - size && i <= pos + size) {
                val seg = when (j) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
                color = if (color == 0) seg else colorBlend(color, seg, 128)
            }
        }
        return color
    }

    private fun fxFillNoise(ctx: Ctx, i: Int): Int {
        val step = (ctx.now / 42L).toInt() + beatsin8(ctx.now, ctx.speed, 1, 6) * (ctx.now / 50L).toInt()
        val index = perlin8(i * ctx.seglen, step + i * ctx.seglen)
        return colorFromPalette(ctx, index, false, false, 0)
    }

    private fun fxNoise16(ctx: Ctx, i: Int, mode: Int): Int {
        return when (mode) {
            1 -> { val scale = 320; val step = (ctx.now * (1 + (ctx.speed shr 4)) / 16L).toInt()
                val shiftX = beatsin8(ctx.now, 11); val shiftY = step / 42
                val noise = perlin16((i + shiftX) * scale, (i + shiftY) * scale, step) shr 8
                colorFromPalette(ctx, sin8((noise * 3) and 0xFF), false, false, 0) }
            2 -> { val scale = 1000; val step = (ctx.now * (1 + (ctx.speed shr 1)) / 16L).toInt()
                val shiftX = step shr 6
                val noise = perlin16((i + shiftX) * scale, 0, 4223) shr 8
                colorFromPalette(ctx, sin8((noise * 3) and 0xFF), false, false, 0, noise) }
            3 -> { val scale = 800; val step = (ctx.now * (1 + ctx.speed) / 16L).toInt()
                val noise = perlin16((i + 4223) * scale, (i + 1234) * scale, step * 8) shr 8
                colorFromPalette(ctx, sin8((noise * 3) and 0xFF), false, false, 0, noise) }
            else -> { val stp = ((ctx.now * ctx.speed) shr 7).toInt()
                val index = perlin16(i shl 12, stp)
                colorFromPalette(ctx, index and 0xFF, false, false, 0) }
        }
    }

    private fun fxColortwinkles(ctx: Ctx, i: Int): Int {
        val fadeUp = 8 + (ctx.speed shr 2)
        val period = (1200L - ctx.speed * 3L).coerceAtLeast(200)
        val offset = (rnd16(i * 2654435761.toInt()) % period)
        val gate = rnd8(i * 9173) <= ctx.intensity
        if (!gate) return 0
        val ph = ((ctx.now + offset) % period).toDouble() / period
        val tri = if (ph < 0.5) ph * 2 else 2 - ph * 2
        val bri = (tri * 255).toInt()
        if (bri < 4) return 0
        return colorFromPalette(ctx, rnd8(i * 131), false, false, 0, bri)
    }

    private fun fxLake(ctx: Ctx, i: Int): Int {
        val sp = ctx.speed / 10
        val wave1 = beatsin8(ctx.now, sp + 2, 0, 128) - 64
        val wave2 = beatsin8(ctx.now, sp + 1, 0, 128) - 64
        val wave3 = beatsin8(ctx.now, sp + 2, 0, 80)
        val index = (cos8((i * 15 + wave1) and 0xFF) / 2) + (cubicwave8((i * 23 + wave2) and 0xFF) / 2)
        val lum = if (index > wave3) index - wave3 else 0
        return colorFromPalette(ctx, index and 0xFF, false, false, 0, lum)
    }

    private fun fxMeteor(ctx: Ctx, i: Int): Int {
        val meteorSize = 1 + ctx.seglen / 20
        val counter = ((ctx.now * ((ctx.speed shr 2) + 8)) and 0xFFFFFFFFL)
        val start = ((counter * ctx.seglen) ushr 16).toInt() % ctx.seglen
        val behind = (start - i + ctx.seglen) % ctx.seglen
        if (behind < meteorSize) {
            return colorFromPalette(ctx, mapInt(i, 0, ctx.seglen, 0, 240), false, false, 255)
        }
        val maxVal = 240
        val decay = (maxVal - behind * (255 / max(1, ctx.seglen)) - (rnd8(i * 13 + (ctx.now / 64L).toInt()) shr 2))
        if (decay <= 0) return 0
        if (rnd8(i * 31 + (ctx.now / 80L).toInt()) > 255 - ctx.intensity) {
            return colorFromPalette(ctx, mapInt(i, 0, ctx.seglen, 0, maxVal), false, false, 255, decay.coerceIn(0, 255))
        }
        return 0
    }

    private fun fxCopySegment(ctx: Ctx, i: Int): Int =
        colorFromPalette(ctx, i * 255 / max(1, ctx.seglen), false, false, 0)

    private fun fxRailway(ctx: Ctx, i: Int): Int {
        val dur = ((256 - ctx.speed) * 40).toLong()
        val rampdur = ((dur * ctx.intensity) shr 8)
        val cyc = ctx.now % (dur * 2L)
        val back = cyc >= dur
        val t = if (back) cyc - dur else cyc
        var pos = 255
        if (rampdur != 0L) { val p0 = ((t * 255) / rampdur).toInt(); if (p0 < 255) pos = p0 }
        if (back) pos = 255 - pos
        return if (i and 1 == 0) colorFromPalette(ctx, 255 - pos, false, false, 255)
        else colorFromPalette(ctx, pos, false, false, 255)
    }

    private fun fxRipple(ctx: Ctx, i: Int, rainbow: Boolean): Int {
        val bg = if (rainbow) colorBlend(colorWheelRaw((ctx.now / 60L and 0xFF).toInt()), 0, 235) else ctx.col1
        val maxRipples = min(1 + (ctx.seglen shr 2), 16)
        var out = bg
        var bestMag = 0
        val rippleLifeMs = 1400L + (255 - ctx.speed) * 6L
        for (r in 0 until maxRipples) {
            val slotPeriod = rippleLifeMs
            val phase = ((ctx.now + (r * slotPeriod / maxRipples)) % slotPeriod)
            val cyc = ((ctx.now + (r * slotPeriod / maxRipples)) / slotPeriod).toInt()
            if (rnd8(cyc * 131 + r * 17) > (ctx.intensity / 2 + 40)) continue
            val origin = rnd16(cyc * 7919 + r * 9973) % ctx.seglen
            val prog = (phase.toDouble() / slotPeriod)
            val radius = prog * ctx.seglen * 0.5
            val amp = ((1.0 - prog) * 255).toInt()
            val dist = abs(i - origin).toDouble()
            val ring = abs(dist - radius)
            val ringWidth = 2.0 + (ctx.custom1 shr 1) / 32.0
            if (ring < ringWidth) {
                val mag = (amp * (1.0 - ring / ringWidth)).toInt().coerceIn(0, 255)
                if (mag > bestMag) {
                    bestMag = mag
                    val col = if (rainbow) colorFromPalette(ctx, rnd8(cyc * 13 + r), false, false, 255)
                    else colorFromPalette(ctx, rnd8(cyc * 13 + r), false, false, 255)
                    out = colorBlend(bg, col, mag)
                }
            }
        }
        return out
    }

    private fun fxTwinklefox(ctx: Ctx, i: Int, cat: Boolean): Int {
        var bg = ctx.col1
        val bglight = getAverageLight(bg)
        bg = when { bglight > 64 -> colorFade(bg, 16); bglight > 16 -> colorFade(bg, 64); else -> colorFade(bg, 86) }
        val backgroundBrightness = getAverageLight(bg)
        val aux0 = if (ctx.speed > 100) 3 + ((255 - ctx.speed) shr 3) else 22 + ((100 - ctx.speed) shr 1)
        var prng = 11337
        for (k in 0..i) {
            prng = ((prng * 2053) + 1384) and 0xFFFF
            val clockOffset = prng
            prng = ((prng * 2053) + 1384) and 0xFFFF
            if (k < i) continue
            val speedMul = ((((prng and 0xFF) shr 4) + (prng and 0x0F)) and 0x0F) + 0x08
            val myclock30 = ((((ctx.now * speedMul) shr 3) + clockOffset) and 0xFFFFFFFFL)
            val unique8 = (prng shr 8) and 0xFF
            val ticks = (myclock30 / max(1, aux0))
            val fastcycle8 = (ticks and 0xFF).toInt()
            var slow = (((ticks shr 8).toInt() + unique8) and 0xFFFF)
            slow = (slow + sin8(slow and 0xFF)) and 0xFFFF
            slow = ((slow * 2053) + 1384) and 0xFFFF
            val slowcycle8 = ((slow and 0xFF) + ((slow shr 8) and 0xFF)) and 0xFF
            val density = (ctx.intensity shr 5) + 1
            var bright = 0
            if (((slowcycle8 and 0x0E) / 2) < density) {
                bright = if (cat) 255 - fastcycle8 else {
                    if (fastcycle8 < 86) fastcycle8 * 3 else { val p2 = fastcycle8 - 86; 255 - (p2 + (p2 shr 1)) }
                }
            }
            val hue = (slowcycle8 - unique8) and 0xFF
            var c = if (bright > 0) colorFromPaletteRaw(getPalette(ctx), hue, bright.coerceIn(0, 255), false) else 0
            if (bright > 0 && fastcycle8 >= 128) {
                val cooling = (fastcycle8 - 128) shr 4
                c = rgb(cR(c), qsub8(cG(c), cooling), qsub8(cB(c), cooling * 2))
            }
            val cbright = getAverageLight(c)
            val delta = cbright - backgroundBrightness
            return when { delta >= 32 || bg == 0 -> c; delta > 0 -> colorBlend(bg, c, min(255, delta * 8)); else -> bg }
        }
        return bg
    }

    private fun fxHalloweenEyes(ctx: Ctx, i: Int): Int {
        val bg = ctx.col1
        if (ctx.seglen <= 1) return bg
        val eyeSpace = max(2, ctx.seglen shr 5)
        val eyeWidth = max(1, eyeSpace shr 1)
        val eyeLength = 2 * eyeWidth + eyeSpace
        if (eyeLength >= ctx.seglen) return bg
        val period = 1200L + (255 - ctx.speed).coerceAtLeast(0) * 30L + ctx.intensity * 40L
        val cyc = (ctx.now / period).toInt()
        val ph = (ctx.now % period).toDouble() / period
        if (ph > 0.62) return bg
        if (ph in 0.30..0.34 && (rnd8(cyc * 53) and 0x07) == 0) return bg
        val startPos = rnd16(cyc * 7919) % max(1, ctx.seglen - eyeLength - 1)
        val start2 = startPos + eyeWidth + eyeSpace
        val eyeColor = colorFromPalette(ctx, rnd8(cyc * 131), false, false, 0)
        val fadeIn = (ph / 0.2).coerceAtMost(1.0)
        val c = colorBlend(bg, eyeColor, (fadeIn * 255).toInt())
        if (i in startPos until (startPos + eyeWidth)) return c
        if (i in start2 until (start2 + eyeWidth)) return c
        return bg
    }

    private fun fxSolidPattern(ctx: Ctx, i: Int): Int {
        val lit = 1 + ctx.speed
        val unlit = 1 + ctx.intensity
        val period = lit + unlit
        val phase = i % period
        return if (phase < lit) colorFromPalette(ctx, i, true, false, 0) else ctx.col1
    }

    private fun fxSolidPatternTri(ctx: Ctx, i: Int): Int {
        val blockSize = max(1, ctx.intensity / 32 + 1)
        return when ((i / blockSize) % 3) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
    }

    private fun fxSpots(ctx: Ctx, i: Int, fade: Boolean): Int {
        val threshold = if (fade) {
            val counter = ((ctx.now * ((ctx.speed shr 2) + 8)) and 0xFFFF).toInt()
            val t = triwave16(counter)
            (t shr 1) + (t shr 2)
        } else (255 - ctx.speed) shl 8
        val maxZones = ctx.seglen shr 2
        val zones = 1 + ((ctx.intensity * maxZones) shr 8)
        val zoneLen = ctx.seglen / max(1, zones)
        if (zoneLen <= 0) return ctx.col1
        val offset = (ctx.seglen - zones * zoneLen) shr 1
        val rel = i - offset
        if (rel < 0 || rel >= zones * zoneLen) return ctx.col1
        val within = rel % zoneLen
        val wave = triwave16((within * 0xFFFF) / zoneLen)
        if (wave > threshold) {
            val s = ((wave - threshold) * 255 / max(1, 0xFFFF - threshold)).coerceIn(0, 255)
            return colorBlend(colorFromPalette(ctx, i, true, false, 0), ctx.col1, 255 - s)
        }
        return ctx.col1
    }

    private fun fxGlitter(ctx: Ctx, i: Int): Int {
        var counter = 0
        if (ctx.speed != 0) counter = (((ctx.now * ((ctx.speed shr 3) + 1)) and 0xFFFF).toInt()) shr 8
        val colorIndex = (i * 255 / max(1, ctx.seglen)) - counter
        val bg = colorFromPalette(ctx, colorIndex and 0xFF, false, true, 255)
        val it = (ctx.now / FRAMETIME).toInt()
        if (ctx.intensity > rnd8(it) && rnd16(it) % ctx.seglen == i) {
            return if (ctx.col2 != 0) ctx.col2 else 0xFFFFFF
        }
        return bg
    }

    private fun fxCandle(ctx: Ctx, i: Int, multi: Boolean): Int {
        val valrange = ctx.intensity
        val offset = 255 - valrange
        val rate = when { ctx.speed > 252 -> 18; ctx.speed > 99 -> 9; ctx.speed > 49 -> 6; else -> 4 }
        val phase = if (multi) i * 53 else 0
        val s = offset + (beatsin8(ctx.now, rate, 0, valrange / 2, phase = phase) +
            beatsin8(ctx.now, rate + 2, 0, valrange / 2, phase = phase + 64))
        return colorBlend(ctx.col1, colorFromPalette(ctx, i, true, false, 0), s.coerceIn(0, 255))
    }

    private fun fxStarburst(ctx: Ctx, i: Int): Int {
        val numStars = min(42, 1 + (ctx.seglen shr 3))
        var out = ctx.col1
        var bestBri = 0
        val lifeMs = 1750L
        for (s in 0 until numStars) {
            val period = lifeMs + rnd16(s * 131) % 1500L
            val phase = ((ctx.now + s * period / numStars) % period)
            val cyc = ((ctx.now + s * period / numStars) / period).toInt()
            if (rnd8(cyc * 53 + s) > (ctx.speed shr 1) + 20) continue
            val center = rnd16(cyc * 7919 + s * 17) % ctx.seglen
            val age = phase.toDouble() / period
            val maxSpread = (ctx.intensity / 8 + 3).toDouble()
            val spread = age * maxSpread
            val dist = abs(i - center).toDouble()
            if (dist <= spread) {
                val fade = (1.0 - age)
                val bri = (fade * 255).toInt()
                if (bri > bestBri) {
                    bestBri = bri
                    val starColor = colorWheelRaw(rnd8(cyc * 13 + s))
                    out = if (age < 0.14) colorBlend(0xFFFFFF, starColor, (age / 0.14 * 254).toInt())
                    else colorBlend(starColor, ctx.col1, ((age - 0.14) / 0.86 * 254).toInt())
                }
            }
        }
        return out
    }

    private fun fxExplodingFireworks(ctx: Ctx, i: Int): Int {
        val cycle = 2600L + (255 - ctx.speed) * 6L
        val ph = (ctx.now % cycle).toDouble() / cycle
        val cyc = (ctx.now / cycle).toInt()
        val launchSide = if (rnd8(cyc * 31) < ctx.intensity) 1 else 0
        if (ph < 0.45) {
            val rise = ph / 0.45
            val peak = (0.3 + rnd8(cyc * 17) / 255.0 * 0.6)
            val pos = (rise * peak * (ctx.seglen - 1))
            val drawPos = if (launchSide != 0) (ctx.seglen - 1 - pos.toInt()) else pos.toInt()
            return if (i == drawPos) 0xFFFFFF else colorFade(ctx.col1, 6)
        } else {
            val peak = (0.3 + rnd8(cyc * 17) / 255.0 * 0.6)
            val center = (peak * (ctx.seglen - 1)).let { if (launchSide != 0) ctx.seglen - 1 - it else it }.toInt()
            val expProg = (ph - 0.45) / 0.55
            val spread = expProg * ctx.seglen * 0.45
            val dist = abs(i - center).toDouble()
            if (dist <= spread + 1) {
                val ring = abs(dist - spread)
                if (ring < 2.0) {
                    val bri = ((1.0 - expProg) * 255 * (1.0 - ring / 2.0)).toInt().coerceIn(0, 255)
                    val col = if (ctx.paletteId != 0) colorWheelRaw(rnd8(cyc * 7 + (i and 7))) else ctx.col0
                    return colorBlend(0, col, bri)
                }
            }
            return colorFade(ctx.col1, 6)
        }
    }

    private fun fxBouncingBalls(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val numBalls = (ctx.intensity * 15 / 255) + 1
        val hasCol2 = ctx.col2 != 0
        var out = if (hasCol2) 0 else ctx.col1
        val tScale = ((255 - ctx.speed) / 64.0 + 1)
        for (b in 0 until numBalls) {
            val startV = 0.8 + rnd8(b * 131) / 255.0 * 0.4
            val period = (900.0 + b * 40) * tScale
            val ph = ((ctx.now + rnd16(b * 17) % period.toLong()) % period.toLong()).toDouble() / period
            val height = 4.0 * ph * (1.0 - ph)
            val pos = (height * (ctx.seglen - 1)).toInt().coerceIn(0, ctx.seglen - 1)
            if (pos == i) {
                out = when {
                    ctx.paletteId != 0 -> colorWheelRaw(b * (256 / max(numBalls, 8)))
                    hasCol2 -> when (b % 3) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
                    else -> ctx.col0
                }
            }
        }
        return out
    }

    private fun fxSinelonDual(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return 0
        val pos = beatsin16(ctx.now, ctx.speed / 10, 0, ctx.seglen - 1)
        val trail = max(1.0, (255 - ctx.intensity) / 16.0)
        val d1 = abs(i - pos).toDouble()
        val d2 = abs(i - (ctx.seglen - 1 - pos)).toDouble()
        val b1 = trailBri(d1, trail)
        val b2 = trailBri(d2, trail)
        val c1 = colorFromPalette(ctx, pos, true, false, 0)
        val c2 = if (ctx.col2 != 0) ctx.col2 else colorFromPalette(ctx, pos, true, false, 0)
        val r1 = if (b1 < 2) 0 else colorBlend(0, c1, b1)
        val r2 = if (b2 < 2) 0 else colorBlend(0, c2, b2)
        return rgb(max(cR(r1), cR(r2)), max(cG(r1), cG(r2)), max(cB(r1), cB(r2)))
    }

    private fun fxSinelonRainbow(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return 0
        val pos = beatsin16(ctx.now, ctx.speed / 10, 0, ctx.seglen - 1)
        val trail = max(1.0, (255 - ctx.intensity) / 16.0)
        val bri = trailBri(abs(i - pos).toDouble(), trail)
        if (bri < 2) return fadeBg(ctx, 30)
        val c = colorWheelRaw((pos and 0x07) * 32)
        return colorBlend(0, c, bri)
    }

    private fun fxPopcorn(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val hasCol2 = ctx.col2 != 0
        var out = if (hasCol2) 0 else ctx.col1
        val numPop = max(1, ctx.intensity * 21 / 255)
        val tScale = (0.0001 + ctx.speed / 200000.0) * ctx.seglen
        for (p in 0 until numPop) {
            val period = (700.0 + rnd8(p * 131) / 255.0 * 1400.0)
            val phaseOff = rnd16(p * 17) % period.toLong()
            val active = (rnd8(((ctx.now + phaseOff) / period.toLong()).toInt() * 53 + p) < 60)
            if (!active) continue
            val ph = ((ctx.now + phaseOff) % period.toLong()).toDouble() / period
            val peak = 0.5 + rnd8(p * 7) / 255.0 * 0.5
            val height = 4.0 * ph * (1.0 - ph) * peak
            val pos = (height * (ctx.seglen - 1)).toInt()
            if (pos == i && pos in 0 until ctx.seglen) {
                out = if (ctx.paletteId != 0) colorWheelRaw(rnd8(p * 19))
                else { val ci = rnd8(p * 3) % 3; if (hasCol2) when (ci) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 } else ctx.col0 }
            }
        }
        return out
    }

    private fun fxDrip(ctx: Ctx, i: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        var out = ctx.col1
        if (i == ctx.seglen - 1) return colorBlend(0, ctx.col0, 200)
        val numDrops = 1 + (ctx.intensity shr 6)
        for (d in 0 until numDrops) {
            val period = (1400.0 + d * 250 + (255 - ctx.speed) * 6)
            val phaseOff = rnd16(d * 131) % period.toLong()
            val ph = ((ctx.now + phaseOff) % period.toLong()).toDouble() / period
            val fall = ph * ph
            val pos = ((1.0 - fall) * (ctx.seglen - 1)).toInt()
            for (ii in 1 until 5) {
                if (pos + ii == i) {
                    val bri = (255 / ii).coerceIn(0, 255)
                    out = colorBlend(out, colorBlend(0, ctx.col0, bri), 200)
                }
            }
            if (pos == i) out = ctx.col0
        }
        return out
    }

    private fun fxPercent(ctx: Ctx, i: Int): Int {
        var percent = ctx.intensity.coerceIn(0, 200)
        val target = if (percent < 100) ctx.seglen * percent / 100 else ctx.seglen * (200 - percent) / 100
        val size = if (ctx.speed == 255) 255 else 1 + ((ctx.speed * ctx.seglen) shr 11)
        val anim = ((ctx.now / 40L).toInt() * size) % (ctx.seglen + 1)
        val active = min(target, anim)
        return if (percent <= 100) {
            if (i < active) colorFromPalette(ctx, i, true, false, 0) else ctx.col1
        } else {
            if (i < ctx.seglen - active) ctx.col1 else colorFromPalette(ctx, i, true, false, 0)
        }
    }

    private fun fxSolidGlitter(ctx: Ctx, i: Int): Int {
        val it = (ctx.now / FRAMETIME).toInt()
        if (ctx.intensity > rnd8(it) && rnd16(it) % ctx.seglen == i) {
            return if (ctx.col2 != 0) ctx.col2 else 0xFFFFFF
        }
        return ctx.col0
    }

    private fun fxSunrise(ctx: Ctx, i: Int): Int {
        val stage: Int = if (ctx.speed > 120) {
            triwave16((((ctx.now shr 1) * (((ctx.speed - 120) shr 1) + 1)) and 0xFFFF).toInt())
        } else if (ctx.speed > 0) {
            val cycle = (ctx.speed.coerceAtMost(60) * 6000L)
            var s = mapInt((ctx.now % cycle).toInt(), 0, cycle.toInt(), 0, 0xFFFF)
            if (ctx.speed > 60) s = 0xFFFF - s
            s
        } else 0xFFFF
        val half = ctx.seglen / 2
        val idx = if (i <= half) i else ctx.seglen - 1 - i
        var wave = triwave16((idx * stage / max(1, ctx.seglen)) and 0xFFFF)
        wave = (wave shr 8) + ((wave * ctx.intensity) shr 15)
        return if (wave > 240) colorFromPalette(ctx, 240, false, true, 255)
        else colorFromPalette(ctx, wave, false, true, 255)
    }

    private fun fxPhased(ctx: Ctx, i: Int, noise: Boolean): Int {
        val allfreq = 16
        val cutOff = 255 - ctx.intensity
        val index = (((ctx.now / 64L).toInt()) + (i * 256 / max(1, ctx.seglen))) and 0xFF
        val phase = (ctx.now.toDouble() * ctx.speed / 32.0 / 16.0)
        val modVal = if (noise) max(1, perlin8(i * 20) shr 4) else 5
        var v = (i + 1) * allfreq + phase * ((i % modVal) + 1) / 2
        var b = cubicwave8(v.toInt() and 0xFF)
        b = if (b > cutOff) b - cutOff else 0
        return colorBlend(ctx.col1, colorFromPalette(ctx, index, false, false, 0), b)
    }

    private fun fxTwinkleup(ctx: Ctx, i: Int): Int {
        val ranstart = rnd8(i * 1103515245 + 12345)
        var pixBri = sin8((ranstart + (16 * ctx.now / (256 - ctx.speed)).toInt()) and 0xFF)
        if (rnd8(i * 2654435761.toInt() + 7) > ctx.intensity) pixBri = 0
        val palIdx = (rnd8(i * 131) + (ctx.now / 100L).toInt()) and 0xFF
        return colorBlend(ctx.col1, colorFromPalette(ctx, palIdx, false, false, 0), pixBri)
    }

    private fun fxNoisePal(ctx: Ctx, i: Int): Int {
        val scale = 15 + (ctx.intensity shr 2)
        val aux0 = (ctx.now / 100L).toInt() * beatsin8(ctx.now, 10, 1, 4)
        val index = perlin8(i * scale, aux0 + i * scale)
        val bucket = (ctx.now / (4000L + ctx.speed * 10L)).toInt()
        val baseHue = rnd8(bucket * 131)
        val hue = (baseHue + index) and 0xFF
        return if (ctx.paletteId > 0) colorFromPaletteRaw(getPalette(ctx), index, 255, false)
        else hsv2rgb(hue, 255, 128 + (index shr 1))
    }

    private fun fxChunchun(ctx: Ctx, i: Int): Int {
        val counter = ((ctx.now * (6 + (ctx.speed shr 4))) and 0xFFFFFFFFL)
        val numBirds = 2 + (ctx.seglen shr 3)
        val span = ((ctx.intensity shl 8) / numBirds)
        var out = fadeBg(ctx, 4)
        for (b in 0 until numBirds) {
            val cnt = ((counter - b.toLong() * span) and 0xFFFFL).toInt()
            val megumin = (sin16(cnt) + 0x8000) and 0xFFFF
            var bird = (megumin * ctx.seglen) ushr 16
            bird = bird.coerceIn(0, ctx.seglen - 1)
            if (bird == i) out = colorFromPalette(ctx, b * 255 / numBirds, false, false, 0)
        }
        return out
    }

    private fun fxDancingShadows(ctx: Ctx, i: Int): Int {
        val num = mapInt(ctx.intensity, 0, 255, 2, 49)
        var out = 0
        for (s in 0 until num) {
            val width = 1 + rnd8(s * 131) % 9
            val speedDiv = 4 + rnd8(s * 17) % 46
            val dir = if (rnd8(s * 7) and 1 == 0) 1 else -1
            val total = ctx.seglen + 2 * width
            val travel = ((ctx.now / speedDiv) * (1 + ctx.speed) / 100L).toInt()
            var position = (if (dir > 0) travel else -travel) % total
            position = ((position % total) + total) % total - width
            val type = rnd8(s * 9173) % 6
            val rel = i - position
            if (rel in 0 until width) {
                val color = colorFromPalette(ctx, rnd8(s * 31), false, false, 255)
                val blend = when (type) {
                    0 -> 128
                    1 -> cubicwave8(mapInt(rel, 0, max(1, width - 1), 0, 255))
                    2 -> cubicwave8((2 * mapInt(rel, 0, max(1, width - 1), 0, 255)) and 0xFF)
                    3 -> if (rel % 2 == 0) 128 else 0
                    4 -> if (rel % 3 == 0) 128 else 0
                    else -> if (rel % 4 == 0) 128 else 0
                }
                if (blend > 0) out = colorBlend(out, color, blend)
            }
        }
        return out
    }

    private fun fxWashingMachine(ctx: Ctx, i: Int): Int {
        val x0 = ((ctx.now shr 7) and 0xFF).toInt()
        var a = 127; var x = x0
        if (x > 127) { a = -127; x -= 127 }
        val sp = when { x < 15 -> x * a / 15; x < 75 -> a; x < 90 -> (90 - x) * a / 15; else -> 0 }
        val step = ((ctx.now * sp * 2048L / max(1, 512 - ctx.speed)) / 1000L).toInt()
        val col = sin8((((ctx.intensity / 25 + 1) * 255 * i / max(1, ctx.seglen)) + (step shr 7)) and 0xFF)
        return colorFromPalette(ctx, col, false, false, 3)
    }

    private fun fxBlends(ctx: Ctx, i: Int): Int {
        val shift = (((ctx.now * ((ctx.speed shr 3) + 1)) shr 8)).toInt()
        val qw = quadwave8(((i + 1) * 16) and 0xFF)
        return colorFromPalette(ctx, (shift + qw + i * 3) and 0xFF, false, false, 255)
    }

    private fun fxTvSimulator(ctx: Ctx, i: Int): Int {
        val colorSpeed = mapInt(ctx.speed, 0, 255, 1, 20)
        val sceneDur = 600L + (255 - ctx.speed) * 20L
        val scene = (ctx.now / sceneDur).toInt()
        val hue = rnd16(scene * 131) % 768
        val sat = 100 + rnd8(scene * 17) % 40
        val sub = (ctx.now / 80L).toInt()
        val briBase = 200 + rnd8(scene * 7) % 40
        val bri = (briBase - rnd8(sub * 13 + scene) % 80).coerceIn(0, 255)
        val h = ((hue + rnd8(sub * 19) % (4 * colorSpeed)) % 768)
        return hsv2rgb((h * 255 / 768) and 0xFF, sat.coerceIn(0, 255), bri)
    }

    private fun fxDynamicSmooth(ctx: Ctx, i: Int): Int {
        val cycleTime = (50 + (255 - ctx.speed) * 15).coerceAtLeast(1).toLong()
        val it = (ctx.now / cycleTime).toInt()
        var value = rnd8(i * 131 + 7)
        if (ctx.speed != 0) {
            val start = max(1, it - 48)
            for (iter in start..it) {
                if (rnd8(iter * 9173 + i * 31) <= ctx.intensity) value = rnd8(iter * 4099 + i * 131 + 7)
            }
        }
        return segColorWheel(ctx, value)
    }

    private fun fxShimmer(ctx: Ctx, i: Int): Int {
        val radius = (ctx.custom1 * ctx.seglen shr 7) + 1
        val traversal = (ctx.seglen + 2 * radius)
        val traversalTime = 200L + (255 - ctx.speed) * 80L
        val ph = (ctx.now % traversalTime).toDouble() / traversalTime
        val position = (ph * traversal - radius)
        val dist = abs(position - i)
        if (dist < radius) {
            var color = colorFromPalette(ctx, i * 255 / max(1, ctx.seglen), false, false, 0)
            if (ctx.custom2 != 0) {
                val modVal = (perlin16(i * ctx.custom2 shl 7, ((ctx.now * ctx.custom3) shl 5).toInt()) shr 8) and 0xFF
                color = colorFadeVideo(color, modVal)
            }
            val blend = (dist / radius * 255).toInt().coerceIn(0, 255)
            return colorBlend(color, ctx.col1, blend)
        }
        return ctx.col1
    }

    private fun fxAudioPixels(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        val it = (ctx.now / FRAMETIME).toInt()
        val count = ctx.intensity / 8
        for (k in 0 until count) {
            if (rnd16(it * 131 + k) % ctx.seglen == i) {
                return colorBlend(ctx.col1, colorFromPalette(ctx, (vol + k * 4) and 0xFF, false, false, 0), vol)
            }
        }
        return fadeBg(ctx, 64 + (ctx.speed shr 1) - 64)
    }

    private fun fxPixelwave(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        val mid = ctx.seglen / 2
        val dist = abs(i - mid)
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val emitted = (ctx.now / (waveSpeed.toLong())).toInt()
        val age = (emitted - dist)
        if (age < 0) return 0
        val volAtEmit = synthVolume(ctx.now - dist.toLong() * waveSpeed)
        val pixBri = min(255, volAtEmit * ctx.intensity / 64) and 0xFF
        val fade = (pixBri - age * 12).coerceIn(0, 255)
        if (fade < 2) return 0
        return colorBlend(ctx.col1, colorFromPalette(ctx, ((ctx.now - dist.toLong() * waveSpeed) and 0xFF).toInt(), false, false, 0), fade)
    }

    private fun fxJuggles(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        val numBalls = ctx.intensity / 32 + 1
        var out = fadeBg(ctx, 224 - 224)
        for (b in 0 until numBalls) {
            val pos = beatsin16(ctx.now, ctx.speed / 4 + b * 2, 0, ctx.seglen - 1)
            if (pos == i) out = colorBlend(ctx.col1, colorFromPalette(ctx, (ctx.now / 4L).toInt() + b * 2, false, false, 0), vol)
        }
        return out
    }

    private fun fxMatripix(ctx: Ctx, i: Int): Int {
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val k = ctx.seglen - 1
        val age = k - i
        val tEmit = ctx.now - age.toLong() * (waveSpeed.toLong() / 2 + 1)
        val vol = synthVolume(tEmit)
        val pixBri = min(255, vol * ctx.intensity / 64) and 0xFF
        return colorBlend(ctx.col1, colorFromPalette(ctx, (tEmit and 0xFF).toInt(), false, false, 0), pixBri)
    }

    private fun fxGravcenterBase(ctx: Ctx, i: Int, mode: Int): Int {
        if (ctx.seglen <= 1) return ctx.col1
        val vol = synthVolume(ctx.now)
        var avg = vol * ctx.intensity / 255.0
        val half = ctx.seglen / 2
        val tempsamp: Int
        if (mode == 2) { avg *= 0.25; tempsamp = (avg * 2.0 * (ctx.seglen - 1) / 64.0).toInt().coerceIn(0, ctx.seglen - 1) }
        else { avg *= 0.125; tempsamp = (avg * 2.0 * (ctx.seglen / 2.0) / 32.0).toInt().coerceIn(0, half) }
        val topLED = (beatsin8(ctx.now, 12, 0, tempsamp.coerceAtLeast(0)).coerceIn(0, ctx.seglen - 1))
        if (mode == 2) {
            if (i < tempsamp) {
                val index = perlin8((i * avg + ctx.now).toInt(), (5000 + i * avg).toInt())
                return colorBlend(ctx.col1, colorFromPalette(ctx, index, false, false, 0), min(255, (avg * 8).toInt()))
            }
            if (i == topLED && topLED > 0) return colorFromPalette(ctx, (ctx.now and 0xFF).toInt(), false, false, 0)
            return 0
        } else {
            val rel = i - half
            val arel = abs(rel)
            if (arel < tempsamp) {
                val index = when (mode) {
                    1 -> ((avg * 24 + ctx.now / 200).toInt() and 0xFF)
                    3 -> (((kotlin.math.log10(synthMajorPeak(ctx.now)) - (4.04238 - 1.78)) * 255).toInt()) and 0xFF
                    else -> perlin8((arel * avg + ctx.now).toInt(), (5000 + arel * avg).toInt())
                }
                if (mode == 0) return colorBlend(ctx.col1, colorFromPalette(ctx, index, false, false, 0), min(255, (avg * 8).toInt()))
                return colorFromPalette(ctx, index, false, false, 0)
            }
            if (arel == topLED && topLED >= 0) {
                return if (mode == 0) colorFromPalette(ctx, (ctx.now and 0xFF).toInt(), false, false, 0) else 0x808080
            }
            return 0
        }
    }

    private fun fxPlasmoid(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        val thisPhase = (beatsin8(ctx.now, 6, 0, 8) - 4) * (ctx.now / 30L).toInt()
        val thatPhase = (beatsin8(ctx.now, 7, 0, 8) - 4) * (ctx.now / 30L).toInt()
        var thisbright = cubicwave8(((i * (1 + 3 * ctx.speed / 32) + thisPhase)) and 0xFF) / 2
        thisbright += cos8(((i * (97 + 5 * ctx.speed / 32) + thatPhase)) and 0xFF) / 2
        val colorIndex = thisbright
        if (vol * ctx.intensity / 64 < thisbright) thisbright = 0
        return colorBlend(ctx.col1, colorFromPalette(ctx, colorIndex and 0xFF, false, false, 0), thisbright.coerceIn(0, 255))
    }

    private fun fxPuddles(ctx: Ctx, i: Int, peak: Boolean): Int {
        val vol = synthVolume(ctx.now)
        val samplePeak = synthSamplePeak(ctx.now)
        val it = (ctx.now / 90L).toInt()
        val pos = rnd16(it) % ctx.seglen
        var size = 0
        if (peak) { if (samplePeak) size = vol * ctx.intensity / 256 / 4 + 1 }
        else { if (vol > 1) size = vol * ctx.intensity / 256 / 8 + 1 }
        if (pos + size >= ctx.seglen) size = ctx.seglen - pos
        if (i in pos until (pos + size)) return colorFromPalette(ctx, (ctx.now and 0xFF).toInt(), false, false, 0)
        return fadeBg(ctx, 30)
    }

    private fun fxMidnoise(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        var t2 = vol * ctx.intensity / 256.0
        t2 *= ctx.intensity / 128.0
        var maxLen = (t2 * (ctx.seglen / 2) / 127.0).toInt()
        if (maxLen > ctx.seglen / 2) maxLen = ctx.seglen / 2
        val half = ctx.seglen / 2
        if (i in (half - maxLen) until (half + maxLen)) {
            val aux0 = beatsin8(ctx.now, 5, 0, 10) * (ctx.now / 60L).toInt()
            val aux1 = beatsin8(ctx.now, 4, 0, 10) * (ctx.now / 60L).toInt()
            val index = perlin8((i * vol + aux0), aux1 + i * vol)
            return colorFromPalette(ctx, index, false, false, 0)
        }
        return 0
    }

    private fun fxNoisemeter(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        val t2 = vol * 2.0 * ctx.intensity / 255.0
        var maxLen = (t2 * ctx.seglen / 255.0).toInt().coerceIn(0, ctx.seglen)
        if (i < maxLen) {
            val aux0 = beatsin8(ctx.now, 5, 0, 10) * (ctx.now / 60L).toInt()
            val aux1 = beatsin8(ctx.now, 4, 0, 10) * (ctx.now / 60L).toInt()
            val index = perlin8((i * vol + aux0), aux1 + i * vol)
            return colorFromPalette(ctx, index, false, false, 0)
        }
        return fadeBg(ctx, 20)
    }

    private fun fxFreqwave(ctx: Ctx, i: Int): Int {
        val mid = ctx.seglen / 2
        val dist = abs(i - mid)
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val tEmit = ctx.now - dist.toLong() * waveSpeed
        if (tEmit < 0) return 0
        var peak = synthMajorPeak(tEmit)
        if (peak < 80) return 0
        val lower = 80 + 3 * ctx.custom1
        val upper = 80 + 42 * ctx.custom2
        val hueIdx = if (lower != upper) mapInt(peak.toInt(), lower, upper, 0, 255) and 0xFF
        else peak.toInt() and 0xFF
        val vol = synthVolume(tEmit)
        val sensitivity = mapInt((if (ctx.custom3 == 0) 1 else ctx.custom3), 1, 31, 1, 10)
        val b = min(255, vol * ctx.intensity / 256 * sensitivity)
        return hsv2rgb(hueIdx, 240, b)
    }

    private fun fxFreqmatrix(ctx: Ctx, i: Int): Int {
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val tEmit = ctx.now - i.toLong() * waveSpeed
        if (tEmit < 0) return 0
        var peak = synthMajorPeak(tEmit)
        if (peak < 80) return 0
        val lower = 80 + 3 * ctx.custom1
        val upper = 80 + 42 * ctx.custom2
        val hueIdx = if (lower != upper) mapInt(peak.toInt(), lower, upper, 0, 255) and 0xFF
        else peak.toInt() and 0xFF
        val vol = synthVolume(tEmit)
        val sensitivity = mapInt(ctx.custom3, 0, 31, 1, 10)
        val b = min(255, vol * ctx.intensity * sensitivity / 256)
        return hsv2rgb(hueIdx, 240, b)
    }

    private fun fxWaterfall(ctx: Ctx, i: Int): Int {
        val k = ctx.seglen - 1
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val age = k - i
        val tEmit = ctx.now - age.toLong() * (waveSpeed.toLong() / 2 + 1)
        if (synthSamplePeak(tEmit)) return hsv2rgb(92, 92, 92)
        val peak = synthMajorPeak(tEmit)
        var pixCol = ((kotlin.math.log10(peak) - 2.26) * 150).toInt()
        if (peak < 182.0) pixCol = 0
        val mag = min(255, synthMagnitude(tEmit).toInt())
        return colorBlend(ctx.col1, colorFromPalette(ctx, (pixCol + ctx.intensity) and 0xFF, false, false, 0), mag)
    }

    private fun fxFreqpixels(ctx: Ctx, i: Int): Int {
        val peak = synthMajorPeak(ctx.now)
        var pixCol = ((kotlin.math.log10(peak) - 1.78) * 255.0 / (4.04238 - 1.78)).toInt()
        if (peak < 61.0) pixCol = 0
        val mag = min(255, (synthMagnitude(ctx.now) / 16.0).toInt())
        val it = (ctx.now / FRAMETIME).toInt()
        for (k in 0 until (ctx.intensity / 32 + 1)) {
            if (rnd16(it * 131 + k) % ctx.seglen == i) {
                return colorBlend(ctx.col1, colorFromPalette(ctx, (ctx.intensity + pixCol) and 0xFF, false, false, 0), mag)
            }
        }
        return fadeBg(ctx, 30)
    }

    private fun fxNoisefire(ctx: Ctx, i: Int): Int {
        val vol = synthVolume(ctx.now)
        var index = perlin8((i * ctx.speed / 64), (ctx.now * ctx.speed / 64L * ctx.seglen / 255L).toInt())
        index = ((255 - i * 256 / ctx.seglen) * index / max(1, 256 - ctx.intensity)).coerceIn(0, 255)
        val bri = min(255, vol * 2)
        return colorFade(colorFromPaletteRaw(palFire, index, 255, false), bri)
    }

    private fun fxNoisemove(ctx: Ctx, i: Int): Int {
        val numBins = mapInt(ctx.intensity, 0, 255, 0, 16)
        var out = 0
        for (b in 0 until numBins) {
            val locn = perlin16((ctx.now * ctx.speed).toInt() + b * 50000, (ctx.now * ctx.speed).toInt())
            val pixloc = mapInt(locn, 7500, 58000, 0, ctx.seglen - 1).coerceIn(0, ctx.seglen - 1)
            if (pixloc == i) {
                val fftVal = synthFFT(ctx.now, b % 16)
                out = colorBlend(ctx.col1, colorFromPalette(ctx, b * 64, false, false, 0), min(255, fftVal * 4))
            }
        }
        return out
    }

    private fun fxPerlinMove(ctx: Ctx, i: Int): Int {
        var out = fadeBg(ctx, (ctx.custom1 / 4).coerceIn(8, 80))
        for (k in 0 until (ctx.intensity / 16 + 1)) {
            val locn = perlin16((ctx.now * 128 / (260 - ctx.speed)).toInt() + k * 15000, (ctx.now * 128 / (260 - ctx.speed)).toInt())
            val pixloc = mapInt(locn, 50 * 256, 192 * 256, 0, ctx.seglen - 1).coerceIn(0, ctx.seglen - 1)
            if (pixloc == i) out = colorFromPalette(ctx, pixloc % 255, false, false, 0)
        }
        return out
    }

    private fun fxRipplePeak(ctx: Ctx, i: Int): Int {
        val MAXSTEPS = 16
        var out = fadeBg(ctx, 20)
        var bestBri = 0
        val maxR = ctx.intensity / 16
        for (r in 0 until maxR) {
            val period = MAXSTEPS * 60L
            val phase = ((ctx.now + r * period / max(1, maxR)) % period)
            val cyc = ((ctx.now + r * period / max(1, maxR)) / period).toInt()
            if (!synthSamplePeak((ctx.now + r * 40))) {
                if (rnd8(cyc * 53 + r) > 60) continue
            }
            val origin = rnd16(cyc * 7919 + r) % ctx.seglen
            val step = (phase / 60L).toInt()
            if (step >= MAXSTEPS) continue
            val peak = synthMajorPeak((ctx.now + r * 40))
            val colorIdx = if (peak > 1) (kotlin.math.log10(peak) * 128).toInt() and 0xFF else 0
            val col = colorFromPalette(ctx, colorIdx, false, false, 0)
            val bri = min(255, 2 * 255 / max(1, step))
            if (i == (origin + step) % ctx.seglen || i == (origin - step + ctx.seglen) % ctx.seglen) {
                if (bri > bestBri) { bestBri = bri; out = colorBlend(ctx.col1, col, bri) }
            }
        }
        return out
    }

    private fun fxPacman(ctx: Ctx, i: Int): Int {
        val YELLOW = 0xFFFF00; val RED = 0xFF0000; val BLUE = 0x0000FF
        val ghostColors = intArrayOf(RED, 0xB000B0, 0x00FFFF, 0xFF8800)
        val numGhosts = mapInt(ctx.custom3, 0, 31, 2, 8).coerceIn(2, 8)
        if (ctx.seglen <= 16 + 2 * numGhosts) return 0
        val stepMs = mapInt(ctx.speed, 0, 255, 120, 12).coerceAtLeast(1).toLong()
        val travel = (ctx.now / stepMs).toInt()
        val span = ctx.seglen - 2
        val raw = travel % (2 * span)
        val pacPos = if (raw < span) raw else 2 * span - raw
        val goingRight = raw < span
        if (i == pacPos) return YELLOW
        for (g in 1..numGhosts) {
            val gp = pacPos + (if (goingRight) -2 * g else 2 * g)
            if (gp == i && gp in 0 until ctx.seglen) return ghostColors[(g - 1) % 4]
        }
        if (i == ctx.seglen - 1 && (travel / 10) % 2 == 0) return 0xFFCC00
        return 0
    }

    private fun fxFreqmap(ctx: Ctx, i: Int): Int {
        val peak = synthMajorPeak(ctx.now)
        var locn = ((kotlin.math.log10(peak) - 1.78) * ctx.seglen / (4.04238 - 1.78)).toInt()
        locn = locn.coerceIn(0, ctx.seglen - 1)
        var pixCol = ((kotlin.math.log10(peak) - 1.78) * 255.0 / (4.04238 - 1.78)).toInt()
        if (peak < 61.0) pixCol = 0
        val mag = min(255, (synthMagnitude(ctx.now) / 4.0).toInt())
        if (i == locn) return colorBlend(ctx.col1, colorFromPalette(ctx, (ctx.intensity + pixCol) and 0xFF, false, false, 0), mag)
        return fadeBg(ctx, 20)
    }

    private fun fxDjLight(ctx: Ctx, i: Int): Int {
        val mid = ctx.seglen / 2
        val dist = abs(i - mid)
        val waveSpeed = (256 - ctx.speed).coerceAtLeast(1)
        val tEmit = ctx.now - dist.toLong() * (waveSpeed.toLong() / 2 + 1)
        if (tEmit < 0) return 0
        val r = synthFFT(tEmit, 15) / 2
        val g = synthFFT(tEmit, 5) / 2
        val b = synthFFT(tEmit, 0) / 2
        val fadeBri = mapInt(synthFFT(tEmit, 4), 0, 255, 255, 4)
        return colorFade(rgb(r, g, b), 255 - fadeBri)
    }

    private fun fxBlurz(ctx: Ctx, i: Int): Int {
        val unit = (5L + 50L * (255 - ctx.speed) / ctx.seglen).coerceAtLeast(1)
        var out = fadeBg(ctx, ctx.speed / 8)
        var bestBri = 0
        for (age in 0 until 6) {
            val tick = (ctx.now / unit) - age
            val segLoc = rnd16(tick.toInt() * 7919) % ctx.seglen
            val band = (tick.toInt()) % 16
            val fftVal = synthFFT(ctx.now - age * unit, band)
            val palIdx = 2 * fftVal * 240 / max(1, ctx.seglen - 1)
            val bri = ((2 * fftVal) and 0xFF) - age * 30
            val dist = abs(i - segLoc)
            if (dist <= 1 && bri > bestBri) {
                bestBri = bri
                val col = colorBlend(ctx.col1, colorFromPalette(ctx, palIdx and 0xFF, false, false, 0), bri.coerceIn(0, 255))
                out = if (dist == 0) col else colorBlend(out, col, 90)
            }
        }
        return out
    }

    private fun fxRocktaves(ctx: Ctx, i: Int): Int {
        var peak = synthMajorPeak(ctx.now)
        val mag = synthMagnitude(ctx.now) / 16.0
        var volTemp = (32.0 + mag * 1.5).toInt()
        if (mag < 48) volTemp = 0
        if (mag > 144) volTemp = 255
        volTemp = volTemp.coerceIn(0, 255)
        var octCount = 0
        var frTemp = peak
        while (frTemp > 249) { octCount++; frTemp /= 2 }
        frTemp -= 132.0
        frTemp = abs(frTemp * 2.1)
        val beat = beatsin8(ctx.now, 8 + octCount * 4, 0, 255, phase = octCount * 8)
        val pos = mapInt(beat, 0, 255, 0, ctx.seglen - 1).coerceIn(0, ctx.seglen - 1)
        if (i == pos) return colorBlend(ctx.col1, colorFromPalette(ctx, frTemp.toInt() and 0xFF, false, false, 0), volTemp)
        return fadeBg(ctx, 16)
    }

    private fun fxColorClouds(ctx: Ctx, i: Int): Int {
        val volSpeed = 1 + ctx.speed
        val hueSpeed = 1 + ctx.intensity
        val volSqueeze = 8 + ctx.custom1
        val hueSqueeze = ctx.custom2
        val volCutoff = 12500 + ctx.custom3 * 900
        val volSaturate = 52000
        val volT = (ctx.now * volSpeed / 8L).toInt()
        val hueT = (ctx.now * hueSpeed / 8L).toInt()
        val hueOffset = (beat88(ctx.now, 64) shr 8) and 0xFF
        val volX = i * volSqueeze * 64
        var vol = perlin16(volX, volT)
        vol = mapInt(vol, volCutoff, volSaturate, 0, 255).coerceIn(0, 255)
        val hueX = i * hueSqueeze * 8
        var hue = (perlin16(hueX, hueT) shr 7) and 0xFF
        hue = (hue + hueOffset) and 0xFF
        val pixel = if (ctx.paletteId != 0) colorFromPalette(ctx, hue, false, true, 0, vol) else hsv2rgb(hue, 255, vol)
        return if (cR(pixel) + cG(pixel) + cB(pixel) <= 2) 0 else pixel
    }

    private fun pHashGrad(h: Int): Int = (h and 0x03) - 2

    private fun pGrad2D(x0: Int, dx: Int, y0: Int, dy: Int): Int {
        var h = (x0 * 0x27D4EB2D) xor (y0 * 0xB5297A4D.toInt())
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        val g1 = pHashGrad(h)
        val g2 = pHashGrad(h ushr 1)
        return (g1 * dx + g2 * dy) shr 2
    }

    private fun pGrad3D(x0: Int, dx: Int, y0: Int, dy: Int, z0: Int, dz: Int): Int {
        var h = (x0 * 0x27D4EB2D) xor (y0 * 0xB5297A4D.toInt()) xor (z0 * 0x1B56C4E9)
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        val g1 = pHashGrad(h)
        val g2 = pHashGrad(h ushr 2)
        val g3 = pHashGrad(h ushr 3)
        return ((g1 * dx + g2 * dy + g3 * dz) * 85) shr 9
    }

    private fun pSmooth(t: Int): Int {
        val tSq = (t.toLong() * t) ushr 16
        val factor = 0x30000L - (t.toLong() shl 1)
        return ((tSq * factor) ushr 18).toInt()
    }

    private fun pLerp(a: Int, b: Int, t: Int): Int = (a + (((b - a).toLong() * t) shr 14)).toInt()

    private fun pRaw2D(xIn: Long, yIn: Long, is16: Boolean): Int {
        val x = xIn and 0xFFFFFFFFL
        val y = yIn and 0xFFFFFFFFL
        val x0 = (x ushr 16).toInt()
        val y0 = (y ushr 16).toInt()
        var x1 = x0 + 1
        var y1 = y0 + 1
        if (is16) { x1 = x1 and 0xFF; y1 = y1 and 0xFF }
        val dx0 = (x and 0xFFFFL).toInt()
        val dy0 = (y and 0xFFFFL).toInt()
        val dx1 = dx0 - 0x10000
        val dy1 = dy0 - 0x10000
        val g00 = pGrad2D(x0, dx0, y0, dy0)
        val g10 = pGrad2D(x1, dx1, y0, dy0)
        val g01 = pGrad2D(x0, dx0, y1, dy1)
        val g11 = pGrad2D(x1, dx1, y1, dy1)
        val tx = pSmooth(dx0)
        val ty = pSmooth(dy0)
        val nx0 = pLerp(g00, g10, tx)
        val nx1 = pLerp(g01, g11, tx)
        return pLerp(nx0, nx1, ty)
    }

    private fun pRaw3D(xIn: Long, yIn: Long, zIn: Long, is16: Boolean): Int {
        val x = xIn and 0xFFFFFFFFL
        val y = yIn and 0xFFFFFFFFL
        val z = zIn and 0xFFFFFFFFL
        val x0 = (x ushr 16).toInt()
        val y0 = (y ushr 16).toInt()
        val z0 = (z ushr 16).toInt()
        var x1 = x0 + 1
        var y1 = y0 + 1
        var z1 = z0 + 1
        if (is16) { x1 = x1 and 0xFF; y1 = y1 and 0xFF; z1 = z1 and 0xFF }
        val dx0 = (x and 0xFFFFL).toInt()
        val dy0 = (y and 0xFFFFL).toInt()
        val dz0 = (z and 0xFFFFL).toInt()
        val dx1 = dx0 - 0x10000
        val dy1 = dy0 - 0x10000
        val dz1 = dz0 - 0x10000
        val g000 = pGrad3D(x0, dx0, y0, dy0, z0, dz0)
        val g001 = pGrad3D(x0, dx0, y0, dy0, z1, dz1)
        val g010 = pGrad3D(x0, dx0, y1, dy1, z0, dz0)
        val g011 = pGrad3D(x0, dx0, y1, dy1, z1, dz1)
        val g100 = pGrad3D(x1, dx1, y0, dy0, z0, dz0)
        val g101 = pGrad3D(x1, dx1, y0, dy0, z1, dz1)
        val g110 = pGrad3D(x1, dx1, y1, dy1, z0, dz0)
        val g111 = pGrad3D(x1, dx1, y1, dy1, z1, dz1)
        val tx = pSmooth(dx0)
        val ty = pSmooth(dy0)
        val tz = pSmooth(dz0)
        val nx0 = pLerp(g000, g100, tx)
        val nx1 = pLerp(g010, g110, tx)
        val nx2 = pLerp(g001, g101, tx)
        val nx3 = pLerp(g011, g111, tx)
        val ny0 = pLerp(nx0, nx1, ty)
        val ny1 = pLerp(nx2, nx3, ty)
        return pLerp(ny0, ny1, tz)
    }

    private fun pnoise8xy(xIn: Int, yIn: Int): Int {
        val x = (xIn.toLong() and 0xFFFFL) shl 8
        val y = (yIn.toLong() and 0xFFFFL) shl 8
        return ((((pRaw2D(x, y, true) * 1620) shr 10) + 32771) shr 8) and 0xFF
    }

    private fun pnoise8(xIn: Int, yIn: Int, zIn: Int): Int {
        val x = (xIn.toLong() and 0xFFFFL) shl 8
        val y = (yIn.toLong() and 0xFFFFL) shl 8
        val z = (zIn.toLong() and 0xFFFFL) shl 8
        return ((((pRaw3D(x, y, z, true) * 2015) shr 10) + 33168) shr 8) and 0xFF
    }

    private fun pnoise16(x: Long, y: Long, z: Long): Int =
        (((pRaw3D(x, y, z, false) * 1731) shr 10) + 33147) and 0xFFFF

    private fun heatColor(temperature: Int): Int {
        val t = temperature.coerceIn(0, 255)
        val t192 = ((t / 255.0) * 191).roundToInt()
        val ramp = (t192 and 0x3F) shl 2
        return when {
            t192 and 0x80 != 0 -> rgb(255, 255, ramp)
            t192 and 0x40 != 0 -> rgb(255, ramp, 0)
            else -> rgb(ramp, 0, 0)
        }
    }

    private fun fastColorScale(c: Int, scale: Int): Int {
        if (c == 0 || scale <= 0) return 0
        if (scale >= 255) return c and 0xFFFFFF
        val rb = ((((c.toLong() and 0x00FF00FFL) * scale) shr 8) and 0x00FF00FFL).toInt()
        val g = ((((c shr 8) and 0xFF) * scale) shr 8) and 0xFF
        return rb or (g shl 8)
    }

    private fun ease8InOutApprox(xIn: Int): Int {
        var x = xIn and 0xFF
        if (x < 64) return x shr 1
        if (x > 191) return 255 - ((255 - x) shr 1)
        x -= 64
        return (x + (x shr 1) + 32) and 0xFF
    }

    private fun lerp8by8(a: Int, b: Int, frac: Int): Int = (a + (((b - a) * frac) shr 8)) and 0xFF

    private fun rgbToHue(c: Int): Int {
        val r = cR(c); val g = cG(c); val b = cB(c)
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        if (d <= 0) return 0
        val h = when (mx) {
            r -> ((g - b) * 255 / d + 256 * 6) % (256 * 6) / 6
            g -> 85 + (b - r) * 255 / d / 6
            else -> 171 + (r - g) * 255 / d / 6
        }
        return h and 0xFF
    }

    private val FIRE_PAL_2D = intArrayOf(
        0x000000, 0x000000, 0x000000, 0x000000,
        0xFF0000, 0xFF0000, 0xFF0000, 0xFF8C00,
        0xFF8C00, 0xFF8C00, 0xFFA500, 0xFFA500,
        0xFFFF00, 0xFFA500, 0xFFFF00, 0xFFFF00,
    )

    private val MOVE_DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val MOVE_DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    private val AKEMI_GRID: ByteArray = (
        "00000000000000000000000000000000" +
            "00000000000002222220000000000000" +
            "00000000000223333332200000000000" +
            "00000000002330000003320000000000" +
            "00000000023000655400032000000000" +
            "00000000230066555544003200000000" +
            "00000000230655555555403200000000" +
            "00000002306555555555540320000000" +
            "00000003206555555555540230000000" +
            "00000032365577555577554323000000" +
            "00000231365177755177754313200000" +
            "00000831365177755177754313800000" +
            "00000831365511555511554313800000" +
            "00000231365555555555554313200000" +
            "00000032365555555555554323000000" +
            "00000000065555577555554000000000" +
            "00000000065555555555554000000000" +
            "10000000065555555555554000000002" +
            "02220000065555555555554000002220" +
            "00032000654444444444444400022000" +
            "00032000655555555555555400023000" +
            "00003200003303300330330000220000" +
            "00003200003203200320320000230000" +
            "00000320032003200320032002300000" +
            "00000322220003200320003222300000" +
            "00000033300003200320000333000000" +
            "00000000000003200320000000000000" +
            "00000000000003200320000000000000" +
            "00000000000003200320000000000000" +
            "00000000000003200320000000000000" +
            "00000000000003200000000000000000" +
            "00000000000003200000000000000000"
        ).let { s -> ByteArray(s.length) { i -> (s[i] - '0').toByte() } }

    private val GLYPHS_5X7: Map<Char, IntArray> = hashMapOf(
        ' ' to intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00), '!' to intArrayOf(0x00, 0x00, 0x5F, 0x00, 0x00),
        '"' to intArrayOf(0x00, 0x07, 0x00, 0x07, 0x00), '#' to intArrayOf(0x14, 0x7F, 0x14, 0x7F, 0x14),
        '$' to intArrayOf(0x24, 0x2A, 0x7F, 0x2A, 0x12), '%' to intArrayOf(0x23, 0x13, 0x08, 0x64, 0x62),
        '&' to intArrayOf(0x36, 0x49, 0x55, 0x22, 0x50), '\'' to intArrayOf(0x00, 0x05, 0x03, 0x00, 0x00),
        '(' to intArrayOf(0x00, 0x1C, 0x22, 0x41, 0x00), ')' to intArrayOf(0x00, 0x41, 0x22, 0x1C, 0x00),
        '*' to intArrayOf(0x14, 0x08, 0x3E, 0x08, 0x14), '+' to intArrayOf(0x08, 0x08, 0x3E, 0x08, 0x08),
        ',' to intArrayOf(0x00, 0x50, 0x30, 0x00, 0x00), '-' to intArrayOf(0x08, 0x08, 0x08, 0x08, 0x08),
        '.' to intArrayOf(0x00, 0x60, 0x60, 0x00, 0x00), '/' to intArrayOf(0x20, 0x10, 0x08, 0x04, 0x02),
        '0' to intArrayOf(0x3E, 0x51, 0x49, 0x45, 0x3E), '1' to intArrayOf(0x00, 0x42, 0x7F, 0x40, 0x00),
        '2' to intArrayOf(0x42, 0x61, 0x51, 0x49, 0x46), '3' to intArrayOf(0x21, 0x41, 0x45, 0x4B, 0x31),
        '4' to intArrayOf(0x18, 0x14, 0x12, 0x7F, 0x10), '5' to intArrayOf(0x27, 0x45, 0x45, 0x45, 0x39),
        '6' to intArrayOf(0x3C, 0x4A, 0x49, 0x49, 0x30), '7' to intArrayOf(0x01, 0x71, 0x09, 0x05, 0x03),
        '8' to intArrayOf(0x36, 0x49, 0x49, 0x49, 0x36), '9' to intArrayOf(0x06, 0x49, 0x49, 0x29, 0x1E),
        ':' to intArrayOf(0x00, 0x36, 0x36, 0x00, 0x00), ';' to intArrayOf(0x00, 0x56, 0x36, 0x00, 0x00),
        '<' to intArrayOf(0x08, 0x14, 0x22, 0x41, 0x00), '=' to intArrayOf(0x14, 0x14, 0x14, 0x14, 0x14),
        '>' to intArrayOf(0x00, 0x41, 0x22, 0x14, 0x08), '?' to intArrayOf(0x02, 0x01, 0x51, 0x09, 0x06),
        '@' to intArrayOf(0x32, 0x49, 0x79, 0x41, 0x3E),
        'A' to intArrayOf(0x7E, 0x11, 0x11, 0x11, 0x7E), 'B' to intArrayOf(0x7F, 0x49, 0x49, 0x49, 0x36),
        'C' to intArrayOf(0x3E, 0x41, 0x41, 0x41, 0x22), 'D' to intArrayOf(0x7F, 0x41, 0x41, 0x22, 0x1C),
        'E' to intArrayOf(0x7F, 0x49, 0x49, 0x49, 0x41), 'F' to intArrayOf(0x7F, 0x09, 0x09, 0x09, 0x01),
        'G' to intArrayOf(0x3E, 0x41, 0x49, 0x49, 0x7A), 'H' to intArrayOf(0x7F, 0x08, 0x08, 0x08, 0x7F),
        'I' to intArrayOf(0x00, 0x41, 0x7F, 0x41, 0x00), 'J' to intArrayOf(0x20, 0x40, 0x41, 0x3F, 0x01),
        'K' to intArrayOf(0x7F, 0x08, 0x14, 0x22, 0x41), 'L' to intArrayOf(0x7F, 0x40, 0x40, 0x40, 0x40),
        'M' to intArrayOf(0x7F, 0x02, 0x0C, 0x02, 0x7F), 'N' to intArrayOf(0x7F, 0x04, 0x08, 0x10, 0x7F),
        'O' to intArrayOf(0x3E, 0x41, 0x41, 0x41, 0x3E), 'P' to intArrayOf(0x7F, 0x09, 0x09, 0x09, 0x06),
        'Q' to intArrayOf(0x3E, 0x41, 0x51, 0x21, 0x5E), 'R' to intArrayOf(0x7F, 0x09, 0x19, 0x29, 0x46),
        'S' to intArrayOf(0x46, 0x49, 0x49, 0x49, 0x31), 'T' to intArrayOf(0x01, 0x01, 0x7F, 0x01, 0x01),
        'U' to intArrayOf(0x3F, 0x40, 0x40, 0x40, 0x3F), 'V' to intArrayOf(0x1F, 0x20, 0x40, 0x20, 0x1F),
        'W' to intArrayOf(0x3F, 0x40, 0x38, 0x40, 0x3F), 'X' to intArrayOf(0x63, 0x14, 0x08, 0x14, 0x63),
        'Y' to intArrayOf(0x07, 0x08, 0x70, 0x08, 0x07), 'Z' to intArrayOf(0x61, 0x51, 0x49, 0x45, 0x43),
        'a' to intArrayOf(0x20, 0x54, 0x54, 0x54, 0x78), 'b' to intArrayOf(0x7F, 0x48, 0x44, 0x44, 0x38),
        'c' to intArrayOf(0x38, 0x44, 0x44, 0x44, 0x20), 'd' to intArrayOf(0x38, 0x44, 0x44, 0x48, 0x7F),
        'e' to intArrayOf(0x38, 0x54, 0x54, 0x54, 0x18), 'f' to intArrayOf(0x08, 0x7E, 0x09, 0x01, 0x02),
        'g' to intArrayOf(0x0C, 0x52, 0x52, 0x52, 0x3E), 'h' to intArrayOf(0x7F, 0x08, 0x04, 0x04, 0x78),
        'i' to intArrayOf(0x00, 0x44, 0x7D, 0x40, 0x00), 'j' to intArrayOf(0x20, 0x40, 0x44, 0x3D, 0x00),
        'k' to intArrayOf(0x7F, 0x10, 0x28, 0x44, 0x00), 'l' to intArrayOf(0x00, 0x41, 0x7F, 0x40, 0x00),
        'm' to intArrayOf(0x7C, 0x04, 0x18, 0x04, 0x78), 'n' to intArrayOf(0x7C, 0x08, 0x04, 0x04, 0x78),
        'o' to intArrayOf(0x38, 0x44, 0x44, 0x44, 0x38), 'p' to intArrayOf(0x7C, 0x14, 0x14, 0x14, 0x08),
        'q' to intArrayOf(0x08, 0x14, 0x14, 0x18, 0x7C), 'r' to intArrayOf(0x7C, 0x08, 0x04, 0x04, 0x08),
        's' to intArrayOf(0x48, 0x54, 0x54, 0x54, 0x20), 't' to intArrayOf(0x04, 0x3F, 0x44, 0x40, 0x20),
        'u' to intArrayOf(0x3C, 0x40, 0x40, 0x20, 0x7C), 'v' to intArrayOf(0x1C, 0x20, 0x40, 0x20, 0x1C),
        'w' to intArrayOf(0x3C, 0x40, 0x30, 0x40, 0x3C), 'x' to intArrayOf(0x44, 0x28, 0x10, 0x28, 0x44),
        'y' to intArrayOf(0x0C, 0x50, 0x50, 0x50, 0x3C), 'z' to intArrayOf(0x44, 0x64, 0x54, 0x4C, 0x44),
    )

    class Renderer2D(val width: Int, val height: Int) {
        private val n = width * height
        private val frame = IntArray(n)

        private var lastEffect = -1
        private var lastTime = Long.MIN_VALUE
        private var call = 0L
        private var step = 0L
        private var aux0 = 0
        private var aux1 = 0
        private val rng = java.util.Random(0x2D5EED)

        private lateinit var ctx: Ctx
        private var pal: IntArray = palRainbow
        private var text: String = ""

        private var plasmaBuf: ByteArray? = null
        private var rotoAngle = 0.0
        private var matrixBits: ByteArray? = null
        private var golCells: ByteArray? = null
        private var golGeneration = 0
        private var golGlider = 0
        private val golParents = IntArray(3)
        private var octoAngle: ByteArray? = null
        private var octoRadius: ByteArray? = null
        private var octoStep = 0
        private var soapNoise: ByteArray? = null
        private var soapPix: IntArray? = null
        private var soapBuf: IntArray? = null
        private var soapCoord: LongArray? = null
        private var sunBump: ByteArray? = null
        private var geqPrev: IntArray? = null
        private var polarStep = 0L
        private var bees: Array<Bee>? = null
        private var ghost: Ghost? = null
        private var blobs: Array<Blob>? = null
        private var juliaX = 0.0
        private var juliaY = 0.0
        private var juliaMag = 1.0
        private var juliaIx = 24

        private class Bee {
            var posX = 0; var posY = 0; var aimX = 0; var aimY = 0; var hue = 0
            var deltaX = 0; var deltaY = 0; var signX = 1; var signY = 1; var error = 0
        }

        private class Ghost(size: Int) {
            var gPosX = 0; var gPosY = 0; var gAngle = 0; var angleSpeed = 0
            val posX = IntArray(size); val posY = IntArray(size)
            val angle = IntArray(size); val time = IntArray(size); val reg = BooleanArray(size)
        }

        private class Blob {
            var r = 0f; var sX = 0f; var sY = 0f; var x = 0f; var y = 0f
            var color = 0; var grow = false
        }

        private var check1 = false
        private var check2 = false
        private var check3 = false

        private fun setXY(x: Int, y: Int, c: Int) {
            if (x < 0 || x >= width || y < 0 || y >= height) return
            frame[x + y * width] = c and 0xFFFFFF
        }

        private fun getXY(x: Int, y: Int): Int =
            if (x < 0 || x >= width || y < 0 || y >= height) 0 else frame[x + y * width]

        private fun addXY(x: Int, y: Int, c: Int) {
            if (c == 0) return
            setXY(x, y, colorAdd(getXY(x, y), c))
        }

        private fun fadeXY(x: Int, y: Int, fade: Int) =
            setXY(x, y, fastColorScale(getXY(x, y), 255 - fade))

        private fun fadeToBlackBy(amount: Int) {
            val scale = 255 - amount.coerceIn(0, 255)
            for (i in 0 until n) frame[i] = fastColorScale(frame[i], scale)
        }

        private fun fadeOutToBg(rate: Int) {
            val r2 = (256 - rate) shr 1
            val mappedRate = 256 / (r2 + 1)
            if (mappedRate == 0) return
            val bg = ctx.col1
            val bgR = cR(bg); val bgG = cG(bg); val bgB = cB(bg)
            for (i in 0 until n) {
                val c = frame[i]
                if (c == bg) continue
                var r = cR(c); var g = cG(c); var b = cB(c)
                var dR = (bgR - r) * mappedRate / 256
                var dG = (bgG - g) * mappedRate / 256
                var dB = (bgB - b) * mappedRate / 256
                if (dR == 0 && r != bgR) dR = if (r > bgR) -1 else 1
                if (dG == 0 && g != bgG) dG = if (g > bgG) -1 else 1
                if (dB == 0 && b != bgB) dB = if (b > bgB) -1 else 1
                r += dR; g += dG; b += dB
                frame[i] = rgb(r, g, b)
            }
        }

        private fun blurFrame(amount: Int, smear: Boolean = false) = blur2D(amount, amount, smear)

        private fun blur2D(blurX: Int, blurY: Int, smear: Boolean = false) {
            val bx = blurX.coerceIn(0, 255)
            val by = blurY.coerceIn(0, 255)
            if (bx > 0) {
                val keep = if (smear) 255 else 255 - bx
                val seep = bx shr 1
                for (y in 0 until height) {
                    var carry = 0
                    var i = y * width
                    for (x in 0 until width) {
                        val cur = frame[i]
                        val part = fastColorScale(cur, seep)
                        var blurred = fastColorScale(cur, keep)
                        blurred = colorAdd(blurred, carry)
                        if (x > 0) frame[i - 1] = colorAdd(frame[i - 1], part)
                        frame[i] = blurred
                        carry = part
                        i++
                    }
                }
            }
            if (by > 0) {
                val keep = if (smear) 255 else 255 - by
                val seep = by shr 1
                for (x in 0 until width) {
                    var carry = 0
                    var i = x
                    for (y in 0 until height) {
                        val cur = frame[i]
                        val part = fastColorScale(cur, seep)
                        var blurred = fastColorScale(cur, keep)
                        blurred = colorAdd(blurred, carry)
                        if (y > 0) frame[i - width] = colorAdd(frame[i - width], part)
                        frame[i] = blurred
                        carry = part
                        i += width
                    }
                }
            }
        }

        private fun move2D(dir: Int, delta: Int) {
            val mx = MOVE_DX[dir and 7] * delta
            val my = MOVE_DY[dir and 7] * delta
            if (mx > 0) {
                for (y in 0 until height) for (x in 0 until width) {
                    val src = x + mx
                    frame[x + y * width] = if (src < width) frame[src + y * width] else 0
                }
            } else if (mx < 0) {
                for (y in 0 until height) for (x in width - 1 downTo 0) {
                    val src = x + mx
                    frame[x + y * width] = if (src >= 0) frame[src + y * width] else 0
                }
            }
            if (my > 0) {
                for (x in 0 until width) for (y in 0 until height) {
                    val src = y + my
                    frame[x + y * width] = if (src < height) frame[x + src * width] else 0
                }
            } else if (my < 0) {
                for (x in 0 until width) for (y in height - 1 downTo 0) {
                    val src = y + my
                    frame[x + y * width] = if (src >= 0) frame[x + src * width] else 0
                }
            }
        }

        private fun wuPixel(x: Int, y: Int, c: Int) {
            val xi = x shr 8; val yi = y shr 8
            val xf = x and 0xFF; val yf = y and 0xFF
            val ixf = 255 - xf; val iyf = 255 - yf
            addXY(xi, yi, colorFade(c, (ixf * iyf) shr 8))
            addXY(xi + 1, yi, colorFade(c, (xf * iyf) shr 8))
            addXY(xi, yi + 1, colorFade(c, (ixf * yf) shr 8))
            addXY(xi + 1, yi + 1, colorFade(c, (xf * yf) shr 8))
        }

        private fun fillCircle(cx: Int, cy: Int, radius: Int, c: Int) {
            if (radius <= 0) { setXY(cx, cy, c); return }
            val r2 = radius * radius
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= r2) setXY(cx + dx, cy + dy, c)
            }
        }

        private fun cfp(index: Int, mapping: Boolean, wrap: Boolean, mcol: Int, pbri: Int = 255): Int {
            if (ctx.paletteId == 0 && mcol < 3) {
                val c = when (mcol) { 0 -> ctx.col0; 1 -> ctx.col1; else -> ctx.col2 }
                return colorFadeVideo(c, pbri.coerceIn(0, 255))
            }
            var idx = index and 0xFF
            if (mapping) idx = min(index * 255 / n, 255)
            return colorFromPaletteRaw(pal, idx, min(pbri, 255), mapping && !wrap)
        }

        private fun cfpd(index: Int, pbri: Int = 255, noWrap: Boolean = false): Int =
            colorFromPaletteRaw(pal, index and 0xFF, min(pbri, 255), noWrap)

        private fun colorWheel2D(pos: Int): Int =
            if (ctx.paletteId != 0) cfp(pos, mapping = false, wrap = false, mcol = 0) else colorWheelRaw(pos)

        private fun vol(): Int = beatsin8(ctx.now, 13, 0, 255)
        private fun fft(bin: Int): Int = beatsin8(ctx.now, 120 / (bin.coerceIn(0, 15) + 1), 0, 255)

        private fun rnd(bound: Int): Int = if (bound <= 0) 0 else rng.nextInt(bound)
        private fun rnd8(): Int = rng.nextInt(256)

        private fun fx2Rotozoomer() {
            val pl = plasmaBuf ?: ByteArray(n).also { plasmaBuf = it }
            val ms = (ctx.now / 15).toInt()
            for (j in 0 until height) {
                val index = j * width
                for (i in 0 until width) {
                    pl[index + i] = (pnoise8(i * 40, j * 40, ms) and 0xFF).toByte()
                }
            }
            val a = rotoAngle
            val f = (sin(a / 2) + ((128 - ctx.intensity) / 128.0) + 1.1) / 1.5
            val kosinus = cos(a) * f
            val sinus = sin(a) * f
            for (i in 0 until width) {
                val u1 = i * kosinus
                val v1 = i * sinus
                for (j in 0 until height) {
                    val u = (abs(floor(u1 - j * sinus).toInt()) and 0xFF) % width
                    val v = (abs(floor(v1 + j * kosinus).toInt()) and 0xFF) % height
                    setXY(i, j, cfp(pl[v * width + u].toInt() and 0xFF, false, false, 255))
                }
            }
            rotoAngle -= 0.03 + (ctx.speed - 128) * 0.0002
            if (rotoAngle < -6283.18530718) rotoAngle += 6283.18530718
        }

        private fun fx2Spaceships() {
            val tb = ctx.now ushr 12
            if (tb > step) {
                var dir = aux0 + 1
                dir += rnd(3) - 1
                aux0 = when { dir > 7 -> 0; dir < 0 -> 7; else -> dir }
                step = tb + rnd(4)
            }
            fadeToBlackBy(mapInt(ctx.speed, 0, 255, 248, 16))
            move2D(aux0, 1)
            for (i in 0 until 8) {
                val x = beatsin8(ctx.now, 12 + i, 2, width - 3)
                val y = beatsin8(ctx.now, 15 + i, 2, height - 3)
                val color = cfpd(beatsin8(ctx.now, 12 + i, 0, 255), 255)
                addXY(x, y, color)
                if (width > 24 || height > 24) {
                    addXY(x + 1, y, color); addXY(x - 1, y, color)
                    addXY(x, y + 1, color); addXY(x, y - 1, color)
                }
            }
            blurFrame(ctx.intensity shr 3)
        }

        private fun fx2CrazyBees() {
            val nb = min(5, n / 256 + 1)
            val arr = bees ?: Array(5) { Bee() }.also { a ->
                for (i in 0 until nb) {
                    a[i].posX = rnd(width); a[i].posY = rnd(height)
                    beeAim(a[i])
                }
                bees = a
            }
            if (ctx.now > step) {
                step = ctx.now + FRAMETIME * 16 / ((ctx.speed shr 4) + 1)
                fadeToBlackBy(32)
                blurFrame(ctx.intensity / 2)
                for (i in 0 until nb) {
                    val b = arr[i]
                    val flower = cfp(b.hue, mapping = false, wrap = true, mcol = 255)
                    addXY(b.aimX + 1, b.aimY, flower)
                    addXY(b.aimX, b.aimY + 1, flower)
                    addXY(b.aimX - 1, b.aimY, flower)
                    addXY(b.aimX, b.aimY - 1, flower)
                    if (b.posX != b.aimX || b.posY != b.aimY) {
                        setXY(b.posX, b.posY, hsv2rgb(b.hue, 60, 255))
                        val error2 = b.error * 2
                        if (error2 > -b.deltaY) { b.error -= b.deltaY; b.posX += b.signX }
                        if (error2 < b.deltaX) { b.error += b.deltaX; b.posY += b.signY }
                    } else {
                        beeAim(b)
                    }
                }
            }
        }

        private fun beeAim(b: Bee) {
            b.aimX = rnd(width); b.aimY = rnd(height); b.hue = rnd8()
            b.deltaX = abs(b.aimX - b.posX)
            b.deltaY = abs(b.aimY - b.posY)
            b.signX = if (b.posX < b.aimX) 1 else -1
            b.signY = if (b.posY < b.aimY) 1 else -1
            b.error = b.deltaX - b.deltaY
        }

        private fun fx2GhostRider() {
            val maxL = min(width + height, 64)
            val g = ghost ?: Ghost(64).also {
                it.gPosX = (width / 2) * 10
                it.gPosY = (height / 2) * 10
                it.gAngle = rnd(65536)
                it.angleSpeed = rnd(20) - 10
                for (i in 0 until maxL) {
                    it.posX[i] = it.gPosX; it.posY[i] = it.gPosY + i; it.time[i] = i * 2
                }
                ghost = it
            }
            if (ctx.now > step) {
                step = ctx.now + 1024L / (width + height)
                fadeToBlackBy((ctx.speed shr 2) + 64)
                wuPixel(g.gPosX * 256 / 10, g.gPosY * 256 / 10, 0xFFFFFF)
                g.gPosX += floor(5 * sin(Math.toRadians(g.gAngle.toDouble()))).toInt()
                g.gPosY += floor(5 * cos(Math.toRadians(g.gAngle.toDouble()))).toInt()
                g.gAngle = (g.gAngle + g.angleSpeed) and 0xFFFF
                if (g.gPosX < 0) g.gPosX = (width - 1) * 10
                if (g.gPosX > (width - 1) * 10) g.gPosX = 0
                if (g.gPosY < 0) g.gPosY = (height - 1) * 10
                if (g.gPosY > (height - 1) * 10) g.gPosY = 0
                for (i in 0 until maxL) {
                    g.time[i] += rnd(15) + 5
                    if (g.time[i] >= 255 ||
                        g.posX[i] <= 0 || g.posX[i] >= (width - 1) * 10 ||
                        g.posY[i] <= 0 || g.posY[i] >= (height - 1) * 10
                    ) g.reg[i] = true
                    if (g.reg[i]) {
                        g.posY[i] = g.gPosY
                        g.posX[i] = g.gPosX
                        g.angle[i] = (g.gAngle + rnd(20) - 10) and 0xFFFF
                        g.time[i] = 0
                        g.reg[i] = false
                    } else {
                        g.posX[i] = (g.posX[i] + floor(-7 * sin(Math.toRadians(g.angle[i].toDouble()))).toInt()) and 0xFFFF
                        g.posY[i] = (g.posY[i] + floor(-7 * cos(Math.toRadians(g.angle[i].toDouble()))).toInt()) and 0xFFFF
                    }
                    wuPixel(g.posX[i] * 256 / 10, g.posY[i] * 256 / 10, cfpd((256 - g.time[i]) and 0xFF))
                }
                blurFrame(ctx.intensity shr 3)
            }
        }

        private fun fx2Blobs() {
            val amount = (ctx.intensity shr 5) + 1
            val arr = blobs ?: Array(8) { Blob() }.also { a ->
                for (b in a) {
                    val rMax = if (width > 8) width / 4 else 2
                    b.r = (rnd(max(1, rMax - 1)) + 1).toFloat()
                    b.sX = (rnd(max(1, width - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.sY = (rnd(max(1, height - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.x = rnd(max(1, width - 1)).toFloat()
                    b.y = rnd(max(1, height - 1)).toFloat()
                    b.color = rnd8()
                    b.grow = b.r < 1f
                    if (b.sX == 0f) b.sX = 1f
                    if (b.sY == 0f) b.sY = 1f
                }
                blobs = a
            }
            fadeToBlackBy((ctx.custom2 shr 3) + 1)
            for (i in 0 until amount) {
                val b = arr[i]
                if (step < ctx.now) b.color = (b.color + 4) and 0xFF
                val spd = max(abs(b.sX), abs(b.sY)) * 0.05f
                if (b.grow) {
                    b.r += spd
                    if (b.r >= min(width / 4f, 2f)) b.grow = false
                } else {
                    b.r -= spd
                    if (b.r < 1f) b.grow = true
                }
                val c = cfp(b.color, false, false, 0)
                if (b.r > 1f) fillCircle(b.x.roundToInt(), b.y.roundToInt(), b.r.roundToInt(), c)
                else setXY(b.x.roundToInt(), b.y.roundToInt(), c)
                if (b.x + b.r >= width - 1) b.x += b.sX * ((width - 1 - b.x) / b.r + 0.005f)
                else if (b.x - b.r <= 0) b.x += b.sX * (b.x / b.r + 0.005f)
                else b.x += b.sX
                if (b.y + b.r >= height - 1) b.y += b.sY * ((height - 1 - b.y) / b.r + 0.005f)
                else if (b.y - b.r <= 0) b.y += b.sY * (b.y / b.r + 0.005f)
                else b.y += b.sY
                if (b.x < 0.01f) {
                    b.sX = (rnd(max(1, width - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.x = 0.01f
                } else if (b.x > width - 1.01f) {
                    b.sX = -(rnd(max(1, width - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.x = width - 1.01f
                }
                if (b.y < 0.01f) {
                    b.sY = (rnd(max(1, height - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.y = 0.01f
                } else if (b.y > height - 1.01f) {
                    b.sY = -(rnd(max(1, height - 3)) + 3).toFloat() / (256 - ctx.speed)
                    b.y = height - 1.01f
                }
            }
            blurFrame(ctx.custom1 shr 2)
            if (step < ctx.now) step = ctx.now + 2000
        }

        private fun fx2ScrollingText() {
            val glyphW = 5; val fontH = 7; val spacing = 1
            val totalW = text.length * (glyphW + spacing) - spacing
            var yoffset = mapInt(ctx.intensity, 0, 255, -(height / 2), height / 2)
            if (totalW <= width) {
                val spd = mapInt(ctx.speed, 0, 255, 5000, 1000)
                val frac = (ctx.now % spd).toInt() + 1
                if (ctx.intensity == 255) yoffset = 2 * frac * height / spd - height
                else if (ctx.intensity == 0) yoffset = height - 2 * frac * height / spd
            }
            if (step < ctx.now) {
                aux0 = (aux0 + 1) % (totalW + width)
                aux1 = (aux1 + 1) and 0xFF
                step = ctx.now + mapInt(ctx.speed, 0, 255, 250, 50)
            }
            fadeOutToBg(255 - (ctx.custom1 shr 4))
            val col1 = cfp(aux1, false, false, 0)
            var xoff = 0
            for (ch in text) {
                val glyph = GLYPHS_5X7[ch] ?: GLYPHS_5X7.getValue(' ')
                val drawX = width - aux0 + xoff
                if (drawX >= width) break
                val advance = glyphW + spacing
                if (drawX + advance < 0) { xoff += advance; continue }
                val drawY = yoffset + (height - fontH) / 2
                for (gx in 0 until glyphW) {
                    val bits = glyph[gx]
                    val sx = drawX + gx
                    if (sx < 0 || sx >= width) continue
                    for (gy in 0 until fontH) {
                        val sy = drawY + gy
                        if (sy < 0 || sy >= height) continue
                        if ((bits shr gy) and 1 != 0) setXY(sx, sy, col1)
                    }
                }
                xoff += advance
            }
        }

        private fun fx2DriftRose() {
            val cxF = ((width - width % 2) / 2f) - 0.5f
            val cyF = ((height - height % 2) / 2f) - 0.5f
            val l = min(width, height) / 2f
            fadeToBlackBy(32 + (ctx.speed shr 3))
            for (i in 1 until 37) {
                val angle = Math.toRadians((i * 10).toDouble())
                val amp = beatsin8(ctx.now, i, 0, (l * 2).toInt()) - l
                val x = floor((cxF + sin(angle) * amp) * 255).toInt()
                val y = floor((cyF + cos(angle) * amp) * 255).toInt()
                val c = if (ctx.paletteId == 0) hsv2rgb(i * 10, 255, 255) else cfpd(i * 10, 255)
                wuPixel(x, y, c)
            }
            blurFrame(ctx.intensity shr 4)
        }

        private fun fx2DistortionWaves() {
            val speedShift = ctx.speed / 32
            val scale = ctx.intensity / 32
            val a = (ctx.now / 32).toInt()
            val a2 = a / 2
            val a3 = a / 3
            val colsScaled = width * scale
            val rowsScaled = height * scale
            val cx = beatsin16(ctx.now, 10 - speedShift, 0, colsScaled)
            val cy = beatsin16(ctx.now, 12 - speedShift, 0, rowsScaled)
            val cx1 = beatsin16(ctx.now, 13 - speedShift, 0, colsScaled)
            val cy1 = beatsin16(ctx.now, 15 - speedShift, 0, rowsScaled)
            val cx2 = beatsin16(ctx.now, 17 - speedShift, 0, colsScaled)
            val cy2 = beatsin16(ctx.now, 14 - speedShift, 0, rowsScaled)
            var xoffs = 0
            for (x in 0 until width) {
                xoffs += scale
                var yoffs = 0
                for (y in 0 until height) {
                    yoffs += scale
                    val rd = cos8((cos8(((x shl 3) + a) and 255) + cos8(((y shl 3) - a2) and 255) + a3) and 255) shr 1
                    val gd = cos8((cos8(((x shl 3) - a2) and 255) + cos8(((y shl 3) + a3) and 255) + a + 32) and 255) shr 1
                    val bd = cos8((cos8(((x shl 3) + a3) and 255) + cos8(((y shl 3) - a) and 255) + a2 + 64) and 255) shr 1
                    var valueR = (rd + ((a - (((xoffs - cx) * (xoffs - cx) + (yoffs - cy) * (yoffs - cy)) shr 7)) shl 1)) and 0xFF
                    var valueG = (gd + ((a2 - (((xoffs - cx1) * (xoffs - cx1) + (yoffs - cy1) * (yoffs - cy1)) shr 7)) shl 1)) and 0xFF
                    var valueB = (bd + ((a3 - (((xoffs - cx2) * (xoffs - cx2) + (yoffs - cy2) * (yoffs - cy2)) shr 7)) shl 1)) and 0xFF
                    valueR = cos8(valueR)
                    valueG = cos8(valueG)
                    valueB = cos8(valueB)
                    if (ctx.paletteId == 0) {
                        setXY(x, y, rgb(valueR, valueG, valueB))
                    } else {
                        val brightness = (valueR + valueG + valueB) / 3
                        val hue = rgbToHue(rgb(valueR shr 2, valueG shr 2, valueB shr 2))
                        setXY(x, y, cfpd(hue, brightness))
                    }
                }
            }
            if (ctx.paletteId != 0) blur2D(200, 200, smear = true)
        }

        private fun fx2Soap() {
            val noise = soapNoise ?: ByteArray(n).also { soapNoise = it }
            val pix = soapPix ?: IntArray(n).also { soapPix = it }
            val buf = soapBuf ?: IntArray(max(width, height)).also { soapBuf = it }
            val coord = soapCoord ?: LongArray(3) { rng.nextLong() and 0xFFFFFFFFL }.also { soapCoord = it }
            val scaleX = 160000 / width
            val scaleY = 160000 / height
            val mov = min(width, height) * (ctx.speed + 2) / 2
            val smooth = min(250, ctx.intensity)
            if (call != 0L) for (i in 0..2) coord[i] = (coord[i] + mov) and 0xFFFFFFFFL
            for (i in 0 until width) {
                val ioff = scaleX.toLong() * (i - width / 2)
                for (j in 0 until height) {
                    val joff = scaleY.toLong() * (j - height / 2)
                    val data = pnoise16(coord[0] + ioff, coord[1] + joff, coord[2]) shr 8
                    val k = i + j * width
                    noise[k] = ((scale8(noise[k].toInt() and 0xFF, smooth) + scale8(data and 0xFF, 255 - smooth)) and 0xFF).toByte()
                }
            }
            if (call == 0L) {
                for (k in 0 until n) {
                    val c = cfpd(((noise[k].toInt().inv() and 0xFF) * 3) and 0xFF)
                    frame[k] = c
                    pix[k] = c
                }
            }
            soapPass(true, noise, pix, buf)
            soapPass(false, noise, pix, buf)
        }

        private fun soapPass(isRow: Boolean, noise: ByteArray, pix: IntArray, buf: IntArray) {
            val tRC = if (isRow) height else width
            val tCR = if (isRow) width else height
            val amplitude = max(1, (tCR - 8) shr 3) * (1 + (ctx.custom1 shr 5))
            for (i in 0 until tRC) {
                val noiseIdx = if (isRow) i * width else i
                val amount = ((noise[noiseIdx].toInt() and 0xFF) - 128) * amplitude
                val delta = abs(amount) shr 8
                val fraction = abs(amount) and 255
                for (j in 0 until tCR) {
                    val zD: Int; val zF: Int
                    if (amount < 0) { zD = j - delta; zF = zD - 1 } else { zD = j + delta; zF = zD + 1 }
                    var yA = abs(zD) % tCR
                    var yB = abs(zF) % tCR
                    var xA = i; var xB = i
                    if (isRow) {
                        var t = xA; xA = yA; yA = t
                        t = xB; xB = yB; yB = t
                    }
                    val indxA = xA + yA * width
                    val indxB = xB + yB * width
                    val pixelA = if (zD in 0 until tCR) pix[indxA]
                    else cfpd(((noise[indxA].toInt().inv() and 0xFF) * 3) and 0xFF)
                    val pixelB = if (zF in 0 until tCR) pix[indxB]
                    else cfpd(((noise[indxB].toInt().inv() and 0xFF) * 3) and 0xFF)
                    val easeA = ease8InOutApprox(255 - fraction)
                    val easeB = ease8InOutApprox(fraction)
                    val rr = min(255, ((cR(pixelA) * easeA) shr 8) + ((cR(pixelB) * easeB) shr 8))
                    val gg = min(255, ((cG(pixelA) * easeA) shr 8) + ((cG(pixelB) * easeB) shr 8))
                    val bb = min(255, ((cB(pixelA) * easeA) shr 8) + ((cB(pixelB) * easeB) shr 8))
                    buf[j] = rgb(rr, gg, bb)
                }
                for (j in 0 until tCR) {
                    var ii = i; var jj = j
                    if (isRow) { val t = ii; ii = jj; jj = t }
                    pix[ii + jj * width] = buf[j]
                    frame[ii + jj * width] = buf[j]
                }
            }
        }

        private fun fx2Octopus() {
            val mapp = 180 / max(width, height)
            var angles = octoAngle
            var radii = octoRadius
            if (angles == null || radii == null) {
                angles = ByteArray(n); radii = ByteArray(n)
                val cX = width / 2 + (ctx.custom1 - 128) * width / 255
                val cY = height / 2 + (ctx.custom2 - 128) * height / 255
                for (x in 0 until width) for (y in 0 until height) {
                    val dx = x - cX
                    val dy = y - cY
                    val k = (x % width) + (y % height) * width
                    angles[k] = ((40.7436 * kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toInt() and 0xFF).toByte()
                    radii[k] = ((sqrt((dx * dx + dy * dy).toDouble()) * mapp).toInt() and 0xFF).toByte()
                }
                octoAngle = angles; octoRadius = radii
                octoStep = 0
            }
            octoStep += ctx.speed / 32 + 1
            val stp = octoStep
            for (x in 0 until width) for (y in 0 until height) {
                val k = x + y * width
                val angle = angles[k].toInt() and 0xFF
                val radius = radii[k].toInt() and 0xFF
                var intens = sin8(sin8((((angle * 4 - radius) shr 2) + (stp shr 1)) and 0xFF) + radius - stp + angle * ((ctx.custom3 shr 2) + 1))
                intens = mapInt(intens * intens, 0, 65535, 0, 255)
                setXY(x, y, cfpd(((stp shr 1) - radius) and 0xFF, intens))
            }
        }

        private fun fx2WavingCell() {
            val t = (ctx.now * (ctx.speed + 1)) shr 3
            val aX = ctx.custom1 / 16 + 9
            val aY = ctx.custom2 / 16 + 1
            val aZ = ctx.custom3 + 1
            for (x in 0 until width) for (y in 0 until height) {
                val inner = sin8((((y.toLong() shl 8) + t) * aY shr 8).toInt())
                val wave = (sin8((x * aX + inner) and 0xFF) + cos8((y * aZ) and 0xFF)) and 0xFF
                val colorIndex = (wave + (t shr 8).toInt()) and 0xFF
                setXY(x, y, cfpd(colorIndex))
            }
            blurFrame(ctx.intensity)
        }

        private fun fx2Geq() {
            val numBands = mapInt(ctx.custom1, 0, 255, 1, 16)
            val centerBin = mapInt(ctx.custom3, 0, 31, 0, 15)
            val prev = geqPrev ?: IntArray(width).also { geqPrev = it }
            if (call == 0L) java.util.Arrays.fill(prev, 0)
            var rippleTime = false
            if (ctx.now - step >= (256 - ctx.intensity)) {
                step = ctx.now
                rippleTime = true
            }
            val fadeoutDelay = (256 - ctx.speed) / 64
            if (fadeoutDelay <= 1 || call % fadeoutDelay == 0L) fadeToBlackBy(ctx.speed)
            for (x in 0 until width) {
                var band = mapInt(x, 0, width, 0, numBands)
                if (numBands < 16) {
                    val startBin = (centerBin - numBands / 2).coerceIn(0, 15 - numBands + 1)
                    band = if (numBands <= 1) centerBin
                    else mapInt(band, 0, numBands - 1, startBin, startBin + numBands - 1)
                }
                band = band.coerceIn(0, 15)
                val colorIndex = band * 17
                val barHeight = mapInt(fft(band), 0, 255, 0, height)
                if (barHeight > prev[x]) prev[x] = barHeight
                var ledColor = 0
                for (y in 0 until barHeight) {
                    ledColor = cfp(colorIndex, false, false, 0)
                    setXY(x, height - 1 - y, ledColor)
                }
                if (prev[x] > 0) setXY(x, height - prev[x], if (ctx.col2 != 0) ctx.col2 else ledColor)
                if (rippleTime && prev[x] > 0) prev[x]--
            }
        }

        private fun fx2Noise() {
            val scale = ctx.intensity + 2
            val z = (ctx.now / (16 - (ctx.speed shr 4))).toInt()
            for (y in 0 until height) for (x in 0 until width) {
                setXY(x, y, cfpd(pnoise8(x * scale, y * scale, z)))
            }
        }

        private fun fx2Firenoise() {
            val xscale = ctx.intensity * 4
            val yscale = ctx.speed * 8
            for (j in 0 until width) for (i in 0 until height) {
                val indexx = pnoise8xy(j * yscale * height / 255, i * xscale + (ctx.now / 4).toInt())
                val palIdx = min(i * indexx / 11, 225)
                val bri = i * 255 / height
                val entry = palIdx shr 4
                val blend = (palIdx and 0x0F) shl 4
                val c0 = FIRE_PAL_2D[min(entry, 15)]
                val c1 = FIRE_PAL_2D[min(entry + 1, 15)]
                setXY(j, i, fastColorScale(colorBlend(c0, c1, blend), bri))
            }
        }

        private fun fx2SquaredSwirl() {
            val kBorder = 2
            fadeToBlackBy(1 + ctx.intensity / 5)
            blurFrame(ctx.custom3 shr 1)
            val i = beatsin8(ctx.now, 19, kBorder, width - kBorder)
            val j = beatsin8(ctx.now, 22, kBorder, width - kBorder)
            val k = beatsin8(ctx.now, 17, kBorder, width - kBorder)
            val m = beatsin8(ctx.now, 18, kBorder, height - kBorder)
            val nn = beatsin8(ctx.now, 15, kBorder, height - kBorder)
            val p = beatsin8(ctx.now, 20, kBorder, height - kBorder)
            addXY(i, m, cfpd((ctx.now / 29).toInt() and 0xFF, 255))
            addXY(j, nn, cfpd((ctx.now / 41).toInt() and 0xFF, 255))
            addXY(k, p, cfpd((ctx.now / 73).toInt() and 0xFF, 255))
        }

        private fun fx2Dna() {
            fadeToBlackBy(64)
            val spd = ctx.speed / 8
            for (i in 0 until width) {
                setXY(
                    i, beatsin8(ctx.now, spd, 0, height - 1, i * 4),
                    cfpd((i * 5 + (ctx.now / 17).toInt()) and 0xFF, beatsin8(ctx.now, 5, 55, 255, i * 10)),
                )
                setXY(
                    i, beatsin8(ctx.now, spd, 0, height - 1, i * 4 + 128),
                    cfpd((i * 5 + 128 + (ctx.now / 17).toInt()) and 0xFF, beatsin8(ctx.now, 5, 55, 255, i * 10 + 128)),
                )
            }
            blurFrame(ctx.intensity / 8)
        }

        private fun fx2Matrix() {
            val bits = matrixBits ?: ByteArray((n + 7) shr 3).also { matrixBits = it }
            if (call == 0L) {
                java.util.Arrays.fill(frame, 0)
                java.util.Arrays.fill(bits, 0)
                step = 0
            }
            val fade = mapInt(ctx.custom1, 0, 255, 50, 250)
            val speedMs = (256 - ctx.speed) shr mapInt(min(height, 150), 0, 150, 0, 3)
            val spawnColor = rgb(175, 255, 175)
            val trailColor = rgb(27, 130, 39)
            if (ctx.now - step >= speedMs) {
                step = ctx.now
                fadeToBlackBy(fade)
                var emptyScreen = true
                for (row in height - 1 downTo 0) {
                    for (col in 0 until width) {
                        val idx = col + row * width
                        val byteIdx = idx shr 3
                        val bitNum = idx and 0x07
                        if ((bits[byteIdx].toInt() shr bitNum) and 1 != 0) {
                            setXY(col, row, trailColor)
                            bits[byteIdx] = (bits[byteIdx].toInt() and (1 shl bitNum).inv()).toByte()
                            if (row < height - 1) {
                                setXY(col, row + 1, spawnColor)
                                val newIdx = col + (row + 1) * width
                                bits[newIdx shr 3] = (bits[newIdx shr 3].toInt() or (1 shl (newIdx and 0x07))).toByte()
                                emptyScreen = false
                            }
                        }
                    }
                }
                if (rnd8() <= ctx.intensity || emptyScreen) {
                    val spawnX = rnd(width)
                    setXY(spawnX, 0, spawnColor)
                    bits[spawnX shr 3] = (bits[spawnX shr 3].toInt() or (1 shl (spawnX and 0x07))).toByte()
                }
            }
        }

        private fun fx2Metaballs() {
            val spd = 0.25f * (1 + (ctx.speed shr 6))
            val tt = floor(ctx.now * spd.toDouble()).toLong().toInt()
            val x2 = mapInt(pnoise8(tt, 25355, 685), 0, 255, 0, width - 1)
            val y2 = mapInt(pnoise8(tt, 355, 11685), 0, 255, 0, height - 1)
            val x3 = mapInt(pnoise8(tt, 55355, 6685), 0, 255, 0, width - 1)
            val y3 = mapInt(pnoise8(tt, 25355, 22685), 0, 255, 0, height - 1)
            val x1 = beatsin8(ctx.now, (23 * spd).toInt(), 0, width - 1)
            val y1 = beatsin8(ctx.now, (28 * spd).toInt(), 0, height - 1)
            for (y in 0 until height) for (x in 0 until width) {
                var dx = abs(x - x1)
                var dy = abs(y - y1)
                var dist = 2 * sqrt((dx * dx + dy * dy).toDouble()).toInt()
                dx = abs(x - x2); dy = abs(y - y2)
                dist += sqrt((dx * dx + dy * dy).toDouble()).toInt()
                dx = abs(x - x3); dy = abs(y - y3)
                dist += sqrt((dx * dx + dy * dy).toDouble()).toInt()
                val color = if (dist != 0) 1000 / dist else 255
                if (color in 1..59) setXY(x, y, cfp(mapInt(color * 9, 9, 531, 0, 255), false, false, 0))
                else setXY(x, y, cfp(0, false, false, 0))
            }
            setXY(x1, y1, 0xFFFFFF)
            setXY(x2, y2, 0xFFFFFF)
            setXY(x3, y3, 0xFFFFFF)
        }

        private fun fx2FunkyPlank() {
            val numBands = mapInt(ctx.custom1, 0, 255, 1, 16)
            var barWidth = width / numBands
            var bandInc = 1
            if (barWidth == 0) {
                barWidth = 1
                bandInc = numBands / width
            }
            if (call == 0L) java.util.Arrays.fill(frame, 0)
            val secondHand = ((ctx.now * 1000 / (256 - ctx.speed) / 500) + 1).toInt() and 0xFF
            if (aux0 != secondHand) {
                aux0 = secondHand
                var b = 0
                var band = 0
                while (band < numBands) {
                    val hue = fft(band % 16)
                    val v = mapInt(hue, 0, 255, 10, 255)
                    for (w in 0 until barWidth) setXY(barWidth * b + w, 0, hsv2rgb(hue, 255, v))
                    band += bandInc
                    b++
                }
                for (i in height - 1 downTo 1) for (j in width - 1 downTo 0) setXY(j, i, getXY(j, i - 1))
            }
        }

        private fun fx2Pulser() {
            fadeToBlackBy(8 - (ctx.intensity shr 5))
            val a = (ctx.now / (18 - ctx.speed / 16)).toInt()
            val x = (a / 14) % width
            val y = mapInt(sin8(a * 5) + sin8(a * 4) + sin8(a * 2), 0, 765, height - 1, 0)
            setXY(x, y, cfpd(mapInt(y, 0, height - 1, 0, 255), 255))
            blurFrame(ctx.intensity shr 4)
        }

        private fun fx2Drift() {
            val colsCenter = (width shr 1) + (width % 2)
            val rowsCenter = (height shr 1) + (height % 2)
            fadeToBlackBy(128)
            val maxDim = max(width, height) / 2f
            val t = (ctx.now / (32 - (ctx.speed shr 3))).toInt()
            val t20 = t / 20
            var i = 1.0f
            while (i < maxDim) {
                val angle = Math.toRadians((t * (maxDim - i)).toDouble())
                val mySin = floor(sin(angle) * i).toInt()
                val myCos = floor(cos(angle) * i).toInt()
                setXY(colsCenter + mySin, rowsCenter + myCos, cfpd(((i * 20 + t20).toInt()) and 0xFF, 255))
                i += 0.25f
            }
            blurFrame(ctx.intensity shr 3)
        }

        private fun fx2Waverly() {
            val volumeSmth = vol()
            fadeToBlackBy(ctx.speed)
            val t = (ctx.now / 2).toInt()
            for (i in 0 until width) {
                var thisVal = (1 + ctx.intensity / 64) * pnoise8(i * 45, t, t) / 2
                thisVal /= 32
                thisVal *= volumeSmth
                val thisMax = mapInt(thisVal, 0, 512, 0, height)
                for (j in 0 until thisMax) {
                    val color = cfpd(mapInt(j, 0, thisMax, 250, 0), 255)
                    addXY(i, j, color)
                    addXY(width - 1 - i, height - 1 - j, color)
                }
            }
        }

        private fun fx2SunRadiation() {
            val bw = width + 2
            val bump = sunBump ?: ByteArray(bw * (height + 2)).also { sunBump = it }
            val t = (ctx.now / 4).toInt()
            val someVal = (ctx.speed shr 2) and 0xFF
            var index = 0
            for (j in 0 until height + 2) {
                for (i in 0 until bw) {
                    val noise = pnoise8(i * someVal, j * someVal, t)
                    val signed16 = (noise - 127) shr 2
                    bump[index++] = (signed16 and 0xFF).toByte()
                }
            }
            var yindex = bw + 1
            var vly = -(height / 2 + 1)
            for (y in 0 until height) {
                ++vly
                var vlx = -(width / 2 + 1)
                for (x in 0 until width) {
                    ++vlx
                    val nx = (bump[x + yindex + 1].toInt() and 0xFF) - (bump[x + yindex - 1].toInt() and 0xFF)
                    val ny = (bump[x + yindex + bw].toInt() and 0xFF) - (bump[x + yindex - bw].toInt() and 0xFF)
                    val i8x = ((vlx * 7 - nx) and 0xFF).toByte().toInt()
                    val i8y = ((vly * 7 - ny) and 0xFF).toByte().toInt()
                    val difx = abs(i8x)
                    val dify = abs(i8y)
                    val temp = difx * difx + dify * dify
                    var col = 255 - temp / 8
                    if (col < 0) col = 0
                    val divisor = 3.0 - ctx.intensity / 128.0
                    val heatVal = (col / divisor).toInt().coerceIn(0, 255)
                    setXY(x, y, heatColor(heatVal))
                }
                yindex += bw
            }
        }

        private fun fx2ColoredBursts() {
            val numLines = ctx.intensity / 16 + 1
            aux0++
            fadeToBlackBy(40)
            for (i in 0 until numLines) {
                val x1 = beatsin8(ctx.now, 2 + ctx.speed / 16, 0, width - 1)
                val x2 = beatsin8(ctx.now, 1 + ctx.speed / 16, 0, height - 1)
                val y1 = beatsin8(ctx.now, 5 + ctx.speed / 16, 0, width - 1, i * 24)
                val y2 = beatsin8(ctx.now, 3 + ctx.speed / 16, 0, height - 1, i * 48 + 64)
                val color = cfpd((i * 255 / numLines + aux0) and 0xFF, 255)
                val xsteps = (abs(x1 - y1) and 0xFF) + 1
                val ysteps = (abs(x2 - y2) and 0xFF) + 1
                val steps = max(xsteps, ysteps)
                for (j in 1..steps) {
                    val rate = (j * 255 / steps) and 0xFF
                    val dx = lerp8by8(x1, y1, rate)
                    val dy = lerp8by8(x2, y2, rate)
                    addXY(dx, dy, color)
                }
            }
            blurFrame(ctx.custom3 shr 1)
        }

        private fun fx2Julia() {
            if (call == 0L) {
                juliaX = 0.0; juliaY = 0.0; juliaMag = 1.0; juliaIx = 24
            }
            if (ctx.intensity != 128) juliaIx = ctx.intensity
            juliaX += (ctx.custom1 - 128) / 100000.0
            juliaY += (ctx.custom2 - 128) / 100000.0
            juliaMag += ((ctx.custom3 - 16) shl 3) / 100000.0
            juliaMag = juliaMag.coerceIn(0.01, 1.0)
            val xmin = (juliaX - juliaMag).coerceIn(-1.2, 1.2)
            val xmax = (juliaX + juliaMag).coerceIn(-1.2, 1.2)
            val ymin = (juliaY - juliaMag).coerceIn(-0.8, 1.0)
            val ymax = (juliaY + juliaMag).coerceIn(-0.8, 1.0)
            val maxIterations = max(2, juliaIx / 2)
            val maxCalc = 16.0
            val reAl = -0.94299 + sin16(((ctx.now * 34) and 0xFFFFL).toInt()) / 655340.0
            val imAg = 0.3162 + sin16(((ctx.now * 26) and 0xFFFFL).toInt()) / 655340.0
            val dx = (xmax - xmin) / width
            val dy = (ymax - ymin) / height
            var yy = ymin
            for (j in 0 until height) {
                var xx = xmin
                for (i in 0 until width) {
                    var a = xx
                    var b = yy
                    var iter = 0
                    while (iter < maxIterations) {
                        val aa = a * a
                        val bb = b * b
                        if (aa + bb > maxCalc) break
                        b = 2 * a * b + imAg
                        a = aa - bb + reAl
                        iter++
                    }
                    setXY(i, j, if (iter == maxIterations) 0 else cfp(iter * 255 / maxIterations, false, false, 0))
                    xx += dx
                }
                yy += dy
            }
        }

        private fun fx2GameOfLife() {
            val alive = 1; val faded = 2; val toggle = 4; val edge = 8; val oscChk = 16; val shipChk = 32
            val cells = golCells ?: ByteArray(n).also { golCells = it; golGeneration = 0 }
            var generation = golGeneration
            val golBlur = mapInt(ctx.custom1, 0, 255, 255, 4)
            val bgColor = ctx.col1
            var birthColor = cfp(128, false, false, 255)
            val setup = call == 0L
            if (setup) {
                var a = height
                var b = width
                while (b != 0) { val t = b; b = a % b; a = t }
                golGlider = ((width * height / a) shl 2) and 0xFFFF
            }
            if (abs(ctx.now - step) > 2000) step = 0
            var paused = step > ctx.now
            if ((!paused && generation == 0) || setup) {
                step = ctx.now + 1280
                generation = 1
                paused = true
                java.util.Arrays.fill(cells, 0)
                for (i in 0 until n) {
                    val isAlive = rnd(3) == 0
                    cells[i] = (cells[i].toInt() or (if (isAlive) alive else faded)).toByte()
                    val x = i % width
                    val y = i / width
                    if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                        cells[i] = (cells[i].toInt() or edge).toByte()
                    }
                    frame[i] = if (isAlive) cfp(rnd8(), false, false, 0) else bgColor
                }
            }
            if (paused || (ctx.now - step < 1000 / mapInt(ctx.speed, 0, 255, 1, 42))) {
                for (i in n - 1 downTo 0) {
                    val cell = cells[i].toInt()
                    if (cell and alive == 0) {
                        val cc = frame[i]
                        if (cc != bgColor) {
                            if (cell and faded != 0) {
                                frame[i] = bgColor
                            } else {
                                var blended = colorBlend(cc, bgColor, 2)
                                if (blended == cc) {
                                    blended = bgColor
                                    cells[i] = (cell or faded).toByte()
                                }
                                frame[i] = blended
                            }
                        }
                    }
                }
                golGeneration = generation
                return
            }
            val updateOscillator = generation % 16 == 0
            val updateSpaceship = golGlider != 0 && generation % golGlider == 0
            var repeatingOscillator = true
            var repeatingSpaceship = true
            var emptyGrid = true
            var cIndex = n - 1
            for (y in height - 1 downTo 0) {
                for (x in width - 1 downTo 0) {
                    var cell = cells[cIndex].toInt()
                    val cellAlive = cell and alive != 0
                    if (cellAlive) emptyGrid = false
                    if ((cell and oscChk != 0) != cellAlive) repeatingOscillator = false
                    if ((cell and shipChk != 0) != cellAlive) repeatingSpaceship = false
                    if (updateOscillator) {
                        cell = if (cellAlive) cell or oscChk else cell and oscChk.inv()
                    }
                    if (updateSpaceship) {
                        cell = if (cellAlive) cell or shipChk else cell and shipChk.inv()
                    }
                    cells[cIndex] = cell.toByte()
                    var neighbors = 0
                    var aliveParents = 0
                    for (di in -1..1) {
                        for (dj in -1..1) {
                            if (di == 0 && dj == 0) continue
                            var nX = x + dj
                            var nY = y + di
                            if (cell and edge != 0) {
                                nX = (nX + width) % width
                                nY = (nY + height) % height
                            }
                            if (nX < 0 || nX >= width || nY < 0 || nY >= height) continue
                            val neighbor = cells[nX + nY * width].toInt()
                            if (neighbor and alive != 0) {
                                neighbors++
                                if (neighbor and toggle == 0 && neighbors < 4 && aliveParents < 3) {
                                    golParents[aliveParents++] = nX + nY * width
                                }
                            }
                        }
                    }
                    var needsColor = false
                    var newColor = 0
                    if (cellAlive && (neighbors < 2 || neighbors > 3)) {
                        cell = cell or toggle
                        if (golBlur == 255) cell = cell or faded
                        cells[cIndex] = cell.toByte()
                        newColor = if (cell and faded != 0) bgColor else colorBlend(frame[cIndex], bgColor, golBlur)
                        needsColor = true
                    } else if (!cellAlive) {
                        if (neighbors == 3) {
                            cell = (cell or toggle) and faded.inv()
                            cells[cIndex] = cell.toByte()
                            if (aliveParents > 0) birthColor = frame[golParents[rnd(aliveParents)]]
                            newColor = birthColor
                            needsColor = true
                        } else if (cell and faded == 0) {
                            val cc = frame[cIndex]
                            var blended = colorBlend(cc, bgColor, golBlur)
                            if (blended == cc) {
                                blended = bgColor
                                cells[cIndex] = (cell or faded).toByte()
                            }
                            newColor = blended
                            needsColor = true
                        }
                    }
                    if (needsColor) frame[cIndex] = newColor
                    cIndex--
                }
            }
            for (i in n - 1 downTo 0) {
                var cell = cells[i].toInt()
                if (cell and toggle != 0) cell = cell xor alive
                cells[i] = (cell and toggle.inv()).toByte()
            }
            if (repeatingOscillator || repeatingSpaceship || emptyGrid) {
                generation = 0
                step += 1024
            } else {
                generation++
                step = ctx.now
            }
            golGeneration = generation
        }

        private fun fx2Tartan() {
            if (call == 0L) java.util.Arrays.fill(frame, 0)
            val offsetX = beatsin16(ctx.now, 3, -360, 360)
            val offsetY = beatsin16(ctx.now, 2, -360, 360)
            val sharpness = ctx.custom3 shr 3
            for (x in 0 until width) {
                for (y in 0 until height) {
                    var hue = (x * beatsin16(ctx.now, 10, 1, 10) + offsetY) and 0xFF
                    var intensity = sin8(x * ctx.speed / 2 + offsetX).toLong()
                    var bri = intensity
                    for (s in 0 until sharpness) intensity *= bri
                    intensity = intensity shr (8 * sharpness)
                    setXY(x, y, cfpd(hue, (intensity and 0xFF).toInt()))
                    hue = (y * 3 + offsetX) and 0xFF
                    intensity = sin8(y * ctx.intensity / 2 + offsetY).toLong()
                    bri = intensity
                    for (s in 0 until sharpness) intensity *= bri
                    intensity = intensity shr (8 * sharpness)
                    addXY(x, y, cfpd(hue, (intensity and 0xFF).toInt()))
                }
            }
        }

        private fun fx2PolarLights() {
            if (call == 0L) {
                java.util.Arrays.fill(frame, 0)
                polarStep = 0
            }
            val adjustHeight = mapF(height.toFloat(), 8f, 32f, 28f, 12f)
            val adjScale = mapInt(width, 8, 64, 310, 63)
            val scale = mapInt(ctx.intensity, 0, 255, 30, adjScale)
            val speed = mapInt(ctx.speed, 0, 255, 128, 16)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    polarStep++
                    val noise = pnoise8(
                        ((polarStep % 2).toInt()) + x * scale,
                        y * 16 + (polarStep % 16).toInt(),
                        (polarStep / speed).toInt(),
                    )
                    var palindex = qsub8(noise, (abs(height / 2.0f - y) * adjustHeight).toInt())
                    val palbrightness = palindex
                    setXY(x, y, cfp(palindex, false, false, 255, palbrightness))
                }
            }
        }

        private fun fx2Swirl() {
            if (call == 0L) java.util.Arrays.fill(frame, 0)
            val borderWidth = 2
            blurFrame(ctx.custom1)
            val i = beatsin8(ctx.now, 27 * ctx.speed / 255, borderWidth, width - borderWidth)
            val j = beatsin8(ctx.now, 41 * ctx.speed / 255, borderWidth, height - borderWidth)
            val ni = (width - 1) - i
            val nj = (width - 1) - j
            val volumeSmth = vol()
            val bri = min(255, volumeSmth * ctx.intensity / 64)
            addXY(i, j, cfpd(((ctx.now / 11).toInt() + volumeSmth * 4) and 0xFF, bri))
            addXY(j, i, cfpd(((ctx.now / 13).toInt() + volumeSmth * 4) and 0xFF, bri))
            addXY(ni, nj, cfpd(((ctx.now / 17).toInt() + volumeSmth * 4) and 0xFF, bri))
            addXY(nj, ni, cfpd(((ctx.now / 29).toInt() + volumeSmth * 4) and 0xFF, bri))
            addXY(i, nj, cfpd(((ctx.now / 37).toInt() + volumeSmth * 4) and 0xFF, bri))
            addXY(ni, j, cfpd(((ctx.now / 41).toInt() + volumeSmth * 4) and 0xFF, bri))
        }

        private fun fx2Lissajous() {
            fadeToBlackBy(ctx.intensity)
            val phase = ((ctx.now * (1 + ctx.custom3) / 32) and 0xFFFFL).toInt()
            for (i in 0 until 256) {
                var xlocn = sin8((phase / 2 + (i * ctx.speed) / 32) and 0xFF)
                var ylocn = cos8((phase / 2 + i * 2) and 0xFF)
                xlocn = if (width < 2) 1 else (mapInt(2 * xlocn, 0, 511, 0, 2 * (width - 1)) + 1) / 2
                ylocn = if (height < 2) 1 else (mapInt(2 * ylocn, 0, 511, 0, 2 * (height - 1)) + 1) / 2
                setXY(xlocn, ylocn, cfp(((ctx.now / 100).toInt() + i) and 0xFF, false, false, 0))
            }
            blurFrame(ctx.custom1 shr 1)
        }

        private fun fx2Frizzles() {
            fadeToBlackBy(16)
            for (i in 8 downTo 1) {
                addXY(
                    beatsin8(ctx.now, ctx.speed / 8 + i, 0, width - 1),
                    beatsin8(ctx.now, ctx.intensity / 8 - i, 0, height - 1),
                    cfpd(beatsin8(ctx.now, 12, 0, 255), 255),
                )
            }
            blurFrame(ctx.custom1 shr 3)
        }

        private fun fx2PlasmaBall() {
            fadeToBlackBy(ctx.custom1 shr 2)
            val t = (ctx.now * 8 / (256 - ctx.speed)).toInt()
            for (i in 0 until width) {
                val thisVal = pnoise8(i * 30, t, t)
                val thisMax = mapInt(thisVal, 0, 255, 0, width - 1)
                for (j in 0 until height) {
                    val thisVal2 = pnoise8(t, j * 30, t)
                    val thisMax2 = mapInt(thisVal2, 0, 255, 0, height - 1)
                    val x = i + thisMax2 - width / 2
                    val y = j + thisMax - width / 2
                    val cx = i + thisMax2
                    val cy = j + thisMax
                    val cond = (x - y > -2 && x - y < 2) ||
                        (width - 1 - x - y > -2 && width - 1 - x - y < 2) ||
                        (width - cx == 0) || (width - 1 - cx == 0) ||
                        (height - cy == 0) || (height - 1 - cy == 0)
                    if (cond) addXY(i, j, cfpd(beat8(ctx.now, 5), thisVal))
                }
            }
            blurFrame(ctx.custom2 shr 5)
        }

        private fun fx2Hiphotic() {
            val a = (ctx.now / ((ctx.custom3 shr 1) + 1)).toInt()
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val v = sin8(cos8(x * ctx.speed / 16 + a / 3) + sin8(y * ctx.intensity / 16 + a / 4) + a)
                    setXY(x, y, cfp(v, false, false, 0))
                }
            }
        }

        private fun fx2Sindots() {
            if (call == 0L) java.util.Arrays.fill(frame, 0)
            fadeToBlackBy(ctx.custom1 shr 3)
            val t1 = (ctx.now / (257 - ctx.speed)).toInt() and 0xFF
            val t2 = (sin8(t1) / 4 * 2) and 0xFF
            for (i in 0 until 13) {
                val x = sin8((t1 + i * (ctx.intensity / 8)) and 0xFF) * (width - 1) / 255
                val y = sin8((t2 + i * (ctx.intensity / 8)) and 0xFF) * (height - 1) / 255
                setXY(x, y, cfpd(i * 255 / 13, 255))
            }
            blurFrame(ctx.custom2 shr 3)
        }

        private fun fx2DnaSpiral() {
            if (call == 0L) java.util.Arrays.fill(frame, 0)
            val speeds = ctx.speed / 2 + 7
            val freq = ctx.intensity / 8
            val ms = (ctx.now / 20).toInt()
            fadeToBlackBy(135)
            for (i in 0 until height) {
                var x = beatsin8(ctx.now, speeds, 0, width - 1, i * freq) +
                    beatsin8(ctx.now, speeds - 7, 0, width - 1, i * freq + 128)
                var x1 = beatsin8(ctx.now, speeds, 0, width - 1, 128 + i * freq) +
                    beatsin8(ctx.now, speeds - 7, 0, width - 1, 128 + 64 + i * freq)
                val hue = (i * 128 / height + ms) and 0xFF
                if ((i + ms / 8) and 3 != 0) {
                    x /= 2
                    x1 /= 2
                    val steps = (abs(x - x1) and 0xFF) + 1
                    val positive = x1 >= x
                    for (k in 1..steps) {
                        val rate = k * 255 / steps
                        val dx = if (positive) x + k - 1 else x - k + 1
                        addXY(dx, i, cfpd(hue, 255))
                        fadeXY(dx, i, rate)
                    }
                    setXY(x, i, 0x2F4F4F)
                    setXY(x1, i, 0xFFFFFF)
                }
            }
            blurFrame(ctx.custom1 * 3 / 6)
        }

        private fun fx2BlackHole() {
            fadeToBlackBy(16 + (ctx.speed shr 3))
            val t = (ctx.now / 128).toInt()
            for (i in 0 until 8) {
                val x = beatsin8(ctx.now, ctx.custom1 shr 3, 0, width - 1, (if (i % 2 != 0) 128 else 0) + t * i)
                val y = beatsin8(ctx.now, ctx.intensity shr 3, 0, height - 1, (if (i % 2 != 0) 192 else 64) + t * i)
                addXY(x, y, cfp(i * 32, false, false, 255))
            }
            for (i in 0 until 4) {
                val x = beatsin8(ctx.now, ctx.custom2 shr 3, width / 4, width - 1 - width / 4, (if (i % 2 != 0) 128 else 0) + t * i)
                val y = beatsin8(ctx.now, ctx.custom3, height / 4, height - 1 - height / 4, (if (i % 2 != 0) 192 else 64) + t * i)
                addXY(x, y, cfp(255 - i * 64, false, false, 255))
            }
            setXY(width / 2, height / 2, 0xFFFFFF)
        }

        private fun fx2Akemi() {
            var counter = ((ctx.now * ((ctx.speed shr 2) + 2)) and 0xFFFFL).toInt()
            counter = counter shr 8
            val lightFactor = 0.15f
            val normalFactor = 0.4f
            val fft0 = fft(0)
            val base = fft0 / 255f
            val faceColor = colorWheel2D(counter)
            val fr = cR(faceColor); val fg = cG(faceColor); val fb = cB(faceColor)
            val armsBase = if (ctx.col1 > 0) ctx.col1 else 0xFFE0A0
            val ar = cR(armsBase); val ag = cG(armsBase); val ab = cB(armsBase)
            val eyeColor = if (ctx.col2 > 0) ctx.col2 else 0xFFFFFF
            val dance = ctx.intensity > 128 && fft0 > 128
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val ak = AKEMI_GRID[(y * 32 / height) * 32 + (x * 32 / width)].toInt()
                    val color = when (ak) {
                        3 -> rgb((ar * lightFactor).toInt(), (ag * lightFactor).toInt(), (ab * lightFactor).toInt())
                        2 -> rgb((ar * normalFactor).toInt(), (ag * normalFactor).toInt(), (ab * normalFactor).toInt())
                        1 -> armsBase
                        6 -> rgb((fr * lightFactor).toInt(), (fg * lightFactor).toInt(), (fb * lightFactor).toInt())
                        5 -> rgb((fr * normalFactor).toInt(), (fg * normalFactor).toInt(), (fb * normalFactor).toInt())
                        4 -> faceColor
                        7 -> eyeColor
                        8 -> if (base > 0.4f) rgb((0xFF * base).toInt(), (0xA5 * base).toInt(), 0) else armsBase
                        else -> 0
                    }
                    if (dance) {
                        setXY(x, 0, 0)
                        setXY(x, y + 1, color)
                    } else {
                        setXY(x, y, color)
                    }
                }
            }
            val xMax = width / 8
            for (x in 0 until xMax) {
                val band = mapInt(x, 0, max(xMax, 4), 0, 15).coerceIn(0, 15)
                val barHeight = mapInt(fft(band), 0, 255, 0, 17 * height / 32)
                val barColor = cfp(band * 35, false, false, 0)
                for (y in 0 until barHeight) {
                    setXY(x, height / 2 - y, barColor)
                    setXY(width - 1 - x, height / 2 - y, barColor)
                }
            }
        }

        private fun mapF(x: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float =
            if (inMax == inMin) outMin else outMin + (x - inMin) * (outMax - outMin) / (inMax - inMin)
    }
}
