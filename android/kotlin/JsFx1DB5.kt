/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

package fx

import fx.JsSimMath.Companion.i32
import fx.JsSimMath.Companion.jsRound

internal object JsFx1DB5 : JsFxBatch {
    override val ids: IntArray = intArrayOf(
        148, 151, 155, 159, 163, 185, 18, 19, 21, 22, 25, 31, 32, 35, 39, 48, 54, 58, 61, 70, 71, 72, 73,
    )

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            148 -> ripplePeak(s)
            151 -> pacman(s)
            155 -> freqmap(s)
            159 -> djLight(s)
            163 -> blurz(s)
            185 -> rocktaves(s)
            18 -> dissolveFx(s)
            19 -> dissolveRnd(s)
            21 -> sparkleDark(s)
            22 -> sparklePlus(s)
            25 -> strobeMega(s)
            31 -> chaseFlash(s)
            32 -> chaseFlashRnd(s)
            35 -> trafficLight(s)
            39 -> runningRandom(s)
            48 -> rollingBalls(s)
            54 -> chaseTricolor(s)
            58 -> icu(s)
            61 -> stream2(s)
            70 -> noise1(s)
            71 -> noise2(s)
            72 -> noise3(s)
            73 -> noise4(s)
            else -> return false
        }
        return true
    }

    private fun random8ModIsZero(m: JsSimMath, lim: Int): Boolean {
        val r = m.random8()
        if (lim == 0) return false
        return r % lim == 0
    }

    private fun ripplePeak(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val maxSteps = 16
        val maxRipples = 16
        if (s.call == 0 || s.data == null || s.dataLen < maxRipples * 4) {
            s.allocateData(maxRipples * 4)
            for (i in 0 until maxRipples) s.d8(i * 4, 254)
        }
        val samplePeak = s.getSamplePeak()
        val fftMajorPeak = s.getMajorPeak()
        s.fade_out(240)
        s.fade_out(240)
        for (i in 0 until s.intensity / 16) {
            val off = i * 4
            if (samplePeak != 0) s.d8(off, 255)
            val state = s.d8(off)
            if (state == 254) continue
            if (state == 255) {
                s.d8(off + 2, m.random16(segLen) and 0xFF)
                s.d8(off + 3, (m.random16(segLen) shr 8) and 0xFF)
                var colorVal = 0
                if (fftMajorPeak > 1) colorVal = i32(kotlin.math.floor(StrictMath.log10(fftMajorPeak) * 128))
                s.d8(off + 1, colorVal and 0xFF)
                s.d8(off, 0)
            } else if (state == 0) {
                val pos = s.d8(off + 2) or (s.d8(off + 3) shl 8)
                if (pos < segLen) s.setPixelColor(pos, s.color_from_palette(s.d8(off + 1), false, false, 0))
                s.d8(off, s.d8(off) + 1)
            } else if (state >= maxSteps) {
                s.d8(off, 254)
            } else {
                val pos = s.d8(off + 2) or (s.d8(off + 3) shl 8)
                val col = s.color_from_palette(s.d8(off + 1), false, false, 0)
                val bri = kotlin.math.min(255, (2 * 255) / state)
                s.setPixelColor((pos + state + segLen) % segLen, m.color_blend(s.SEGCOLOR(1), col, bri))
                s.setPixelColor((pos - state + segLen) % segLen, m.color_blend(s.SEGCOLOR(1), col, bri))
                s.d8(off, s.d8(off) + 1)
            }
        }
    }

    private class PacState(n: Int) {
        val pos = IntArray(n)
        val topPos = IntArray(n)
        val color = IntArray(n)
        val direction = BooleanArray(n)
        val blue = BooleanArray(n)
        val eaten = BooleanArray(n)
        var counter = 0L
    }

    private fun pacman(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val yellow = 0xFFFF00; val red = 0xFF0000; val blueC = 0x0000FF
        val purpleish = 0xB000B0; val orangeish = 0xFF8800; val whiteish = 0x999999; val orangeyellow = 0xFFCC00
        val ghostColorArr = intArrayOf(red, purpleish, 0x00FFFF, orangeish)
        val numGhosts = m.map(s.custom3, 0, 31, 2, 8)
        val numPowerDots = kotlin.math.max(1, m.map(s.intensity, 0, 255, 1, kotlin.math.min(segLen / 10, 255)))
        val smearMode = s.check2

        if (segLen <= 16 + 2 * numGhosts) return

        if (s.call == 0 || s.state !is PacState) {
            val p = PacState(numGhosts + 1 + numPowerDots)
            p.pos[0] = 0; p.topPos[0] = 0; p.color[0] = yellow; p.direction[0] = true
            for (i in 1..numGhosts) {
                p.pos[i] = -2 * (i + 1); p.topPos[i] = 0; p.color[i] = ghostColorArr[(i - 1) % 4]; p.direction[i] = true
            }
            for (i in 0 until numPowerDots) {
                val k = numGhosts + 1 + i
                p.pos[k] = 0; p.color[k] = orangeyellow; p.direction[k] = true
            }
            p.pos[numGhosts + 1] = segLen - 1
            s.state = p
        }
        val p = s.state as PacState
        if (m.getNow() > s.step) { s.step = m.getNow(); p.counter++ }

        if (!smearMode) s.fill(0)

        val everyXLeds = ((segLen - 10) * 256) / numPowerDots
        for (i in 1 until numPowerDots) {
            p.pos[numGhosts + 1 + i] = 10 + (i * everyXLeds) / 256
        }

        if (p.counter % 10 == 0L) {
            val dotColor = if (p.color[numGhosts + 1] == orangeyellow) 0 else orangeyellow
            for (i in 0 until numPowerDots) p.color[numGhosts + 1 + i] = dotColor
        }

        if (s.check1) {
            val step = if (s.check3) 1 else 2
            var i = segLen - 1
            while (i > p.topPos[0]) { s.setPixelColor(i, whiteish); i -= step }
        }

        for (i in 0 until numPowerDots) {
            val d = numGhosts + 1 + i
            if (!p.eaten[d] && p.pos[d] >= 0 && p.pos[d] < segLen) s.setPixelColor(p.pos[d], p.color[d])
        }

        for (j in 0 until numPowerDots) {
            val dot = numGhosts + 1 + j
            if (p.pos[0] == p.pos[dot] && !p.eaten[dot]) {
                for (i in 0..numGhosts) p.direction[i] = false
                for (i in 1..numGhosts) { p.color[i] = blueC; p.blue[i] = true }
                p.eaten[dot] = true
                break
            }
        }

        if (p.blue[1] && p.pos[0] <= 0) {
            for (i in 0..numGhosts) p.direction[i] = true
            for (i in 1..numGhosts) { p.color[i] = ghostColorArr[(i - 1) % 4]; p.blue[i] = false }
            if (p.eaten[numGhosts + 1]) {
                for (i in 0 until numPowerDots) p.eaten[numGhosts + 1 + i] = false
                p.topPos[0] = 0
            }
        }

        val maxBlinkPos = kotlin.math.max(20, p.topPos[0])
        val startBlink = if (segLen < 64) segLen / 3 else m.map(s.custom1, 0, 255, 20, maxBlinkPos)
        if (p.counter % 15 == 0L && p.blue[1] && p.pos[0] <= startBlink) {
            val gc = if (p.color[1] == blueC) whiteish else blueC
            for (i in 1..numGhosts) p.color[i] = gc
        }

        val stepDiv = m.map(s.speed, 0, 255, 15, 1)
        if (p.counter % stepDiv == 0L) {
            p.pos[0] += if (p.direction[0]) 1 else -1
            for (i in 1..numGhosts) p.pos[i] += if (p.direction[i]) 1 else -1
        }

        if (p.pos[0] >= 0 && p.pos[0] < segLen) s.setPixelColor(p.pos[0], p.color[0])
        for (i in 1..numGhosts) {
            if (p.pos[i] >= 0 && p.pos[i] < segLen) s.setPixelColor(p.pos[i], p.color[i])
        }
        if (p.topPos[0] < p.pos[0]) p.topPos[0] = p.pos[0]

        s.blur(s.custom2 / 2)
    }

    private fun freqmap(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxFreqLog10 = 4.04238
        val fftMajorPeak = s.getMajorPeak()
        val myMagnitude = s.getMagnitude() / 4.0

        if (s.call == 0) s.fill(0)
        val fadeoutDelay = (256 - s.speed) / 32
        if (fadeoutDelay <= 1 || (s.call % fadeoutDelay) == 0) s.fade_out(s.speed)

        val lg = StrictMath.log10(fftMajorPeak)
        var locn = kotlin.math.floor((lg - 1.78) * segLen / (maxFreqLog10 - 1.78))
        if (locn < 1) locn = 0.0
        if (locn >= segLen) locn = (segLen - 1).toDouble()

        var pixCol = kotlin.math.floor((lg - 1.78) * 255.0 / (maxFreqLog10 - 1.78))
        if (fftMajorPeak < 61.0) pixCol = 0.0

        val bright = i32(kotlin.math.min(255.0, kotlin.math.floor(myMagnitude))) and 0xFF
        val palIdx = i32(s.intensity + pixCol) and 0xFF
        s.setPixelColor(locn, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(palIdx, false, false, 0), bright))
    }

    private fun djLight(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val mid = segLen / 2
        if (s.call == 0) s.fill(0)

        val secondHand = (kotlin.math.floor(m.getNow() * 1000.0 / (256 - s.speed) / 500.0).toLong() + 1) % 64
        if (s.aux0.toLong() != secondHand) {
            s.aux0 = secondHand.toInt()
            val r = s.getFFT(15) / 2
            val g = s.getFFT(5) / 2
            val b = s.getFFT(0) / 2
            val fadeBri = m.map(s.getFFT(4), 0, 255, 255, 4)
            val color = m.color_fade(m.colorPack(r, g, b), 255 - fadeBri)
            s.setPixelColor(mid, color)
            var i = segLen - 1
            while (i > mid) { s.setPixelColor(i, s.getPixelColor(i - 1)); i-- }
            for (k in 0 until mid) s.setPixelColor(k, s.getPixelColor(k + 1))
        }
    }

    private fun blurz(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.call == 0) { s.fill(0); s.aux0 = 0 }
        val fadeoutDelay = (256 - s.speed) / 32
        if (fadeoutDelay <= 1 || (s.call % fadeoutDelay) == 0) s.fade_out(s.speed)

        val speedFormulaL = 5 + (50 * (255 - s.speed)) / segLen
        s.step += m.FRAMETIME
        if (s.step > speedFormulaL) {
            val segLoc = m.random16(segLen)
            val fftBand = s.aux0 % 16
            val fftVal = s.getFFT(fftBand)
            val palIdx = (2 * fftVal * 240) / kotlin.math.max(1, segLen - 1)
            val bri = (2 * fftVal) and 0xFF
            val col = m.color_blend(s.SEGCOLOR(1), s.color_from_palette(palIdx, false, false, 0), bri)
            s.setPixelColor(segLoc, col)
            s.aux0 = (s.aux0 + 1) % 16
            s.step = 1
            s.blur(s.intensity)
        }
    }

    private fun rocktaves(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        s.fadeToBlackBy(16)
        val fftMajorPeak = s.getMajorPeak()
        val myMagnitude = s.getMagnitude() / 16.0
        var frTemp = fftMajorPeak
        var octCount = 0
        var volTemp = kotlin.math.floor(32.0 + myMagnitude * 1.5)
        if (myMagnitude < 48) volTemp = 0.0
        if (myMagnitude > 144) volTemp = 255.0
        volTemp = kotlin.math.min(255.0, volTemp)
        while (frTemp > 249) { octCount++; frTemp /= 2 }
        frTemp -= 132.0
        frTemp = kotlin.math.abs(frTemp * 2.1)
        val beat = m.beatsin8(8 + octCount * 4, 0, 255, 0L, octCount * 8)
        val i = m.constrain(m.map(beat, 0, 255, 0, segLen - 1), 0, segLen - 1)
        val palIdx = i32(kotlin.math.floor(frTemp)) and 0xFF
        s.addPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(palIdx, false, false, 0), volTemp.toInt()))
    }

    private fun dGet(s: JsSimSegment, idx: Int): Int {
        val off = idx * 4
        return (s.d8(off) shl 24) or (s.d8(off + 1) shl 16) or (s.d8(off + 2) shl 8) or s.d8(off + 3)
    }

    private fun dSet(s: JsSimSegment, idx: Int, c: Int) {
        val off = idx * 4
        s.d8(off, (c shr 24) and 0xFF); s.d8(off + 1, (c shr 16) and 0xFF)
        s.d8(off + 2, (c shr 8) and 0xFF); s.d8(off + 3, c and 0xFF)
    }

    private fun dissolve(s: JsSimSegment, color: Int) {
        val m = s.m
        val segLen = s.vLength()
        val dataSize = segLen * 4
        if (s.call == 0 || s.data == null || s.dataLen < dataSize) {
            s.allocateData(dataSize)
            val c1 = s.SEGCOLOR(1)
            for (i in 0 until segLen) dSet(s, i, c1)
            s.aux0 = 1
        }
        val c1 = s.SEGCOLOR(1)
        for (j in 0..(segLen / 15)) {
            if (m.random8() <= s.intensity) {
                for (times in 0 until 10) {
                    val i = m.random16(segLen)
                    if (s.aux0 != 0) {
                        if (dGet(s, i) == c1) {
                            dSet(s, i, if (color == s.SEGCOLOR(0)) s.color_from_palette(i, true, false, 0) else color)
                            break
                        }
                    } else {
                        if (dGet(s, i) != c1) { dSet(s, i, c1); break }
                    }
                }
            }
        }
        var incompletePixels = 0
        for (i in 0 until segLen) {
            val px = dGet(s, i)
            s.setPixelColor(i, px)
            if (s.check2) {
                if (s.aux0 != 0) { if (px == c1) incompletePixels++ } else { if (px != c1) incompletePixels++ }
            }
        }
        if (s.step > (255 - s.speed) + 15) {
            s.aux0 = if (s.aux0 != 0) 0 else 1
            s.step = 0
        } else {
            if (s.check2) { if (incompletePixels == 0) s.step++ } else s.step++
        }
    }

    private fun dissolveFx(s: JsSimSegment) {
        val m = s.m
        dissolve(s, if (s.check1) s.color_wheel(m.random8()) else s.SEGCOLOR(0))
    }

    private fun dissolveRnd(s: JsSimSegment) = dissolve(s, s.color_wheel(s.m.random8()))

    private fun sparkleDark(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (!s.check2) for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        if (m.getNow() - s.aux0 > s.step) {
            if (random8ModIsZero(m, (255 - s.intensity) shr 4)) {
                s.setPixelColor(m.random16(segLen), s.SEGCOLOR(1))
            }
            s.step = m.getNow()
            s.aux0 = 255 - s.speed
        }
    }

    private fun sparklePlus(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (!s.check2) for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        if (m.getNow() - s.aux0 > s.step) {
            if (random8ModIsZero(m, (255 - s.intensity) shr 4)) {
                val len = kotlin.math.max(1, segLen / 3)
                for (i in 0 until len) s.setPixelColor(m.random16(segLen), s.SEGCOLOR(1))
            }
            s.step = m.getNow()
            s.aux0 = 255 - s.speed
        }
    }

    private fun strobeMega(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 1))
        s.aux0 = 50 + 20 * (255 - s.speed)
        val count = 2 * (s.intensity / 10 + 1)
        if (s.aux1 < count) {
            if ((s.aux1 and 1) == 0) { s.fill(s.SEGCOLOR(0)); s.aux0 = 15 } else s.aux0 = 50
        }
        if (m.getNow() - s.aux0 > s.step) {
            s.aux1++
            if (s.aux1 > count) s.aux1 = 0
            s.step = m.getNow()
        }
    }

    private fun chaseFlash(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val flashCount = 4
        var advance = true
        val flashStep = s.aux1 % ((flashCount * 2) + 1)
        if (m.getNow() < s.step) advance = false else s.aux1++
        for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        val index = s.aux0
        val n = index
        val mp1 = (index + 1) % segLen
        var delay = 10 + (30 * (255 - s.speed)) / segLen
        if (flashStep < flashCount * 2) {
            if (flashStep % 2 == 0) {
                s.setPixelColor(n, s.SEGCOLOR(1)); s.setPixelColor(mp1, s.SEGCOLOR(1)); delay = 20
            } else delay = 30
        } else if (advance) {
            s.aux0 = mp1
        }
        if (advance) s.step = m.getNow() + delay
    }

    private fun chaseFlashRnd(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val flashCount = 4
        var advance = true
        if (m.getNow() < s.step) { s.call--; advance = false }
        val flashStep = s.call % ((flashCount * 2) + 1)
        for (i in 0 until s.aux1) s.setPixelColor(i, s.color_wheel(s.aux0))
        var delay = 1 + (10 * (255 - s.speed)) / segLen
        if (flashStep < flashCount * 2) {
            val n = s.aux1
            val mp1 = (s.aux1 + 1) % segLen
            if (flashStep % 2 == 0) {
                s.setPixelColor(n, s.SEGCOLOR(0)); s.setPixelColor(mp1, s.SEGCOLOR(0)); delay = 20
            } else {
                s.setPixelColor(n, s.color_wheel(s.aux0)); s.setPixelColor(mp1, s.SEGCOLOR(1)); delay = 30
            }
        } else if (advance) {
            s.aux1 = (s.aux1 + 1) % segLen
            if (s.aux1 == 0) s.aux0 = m.get_random_wheel_index(s.aux0)
        }
        if (advance) s.step = m.getNow() + delay
    }

    private fun trafficLight(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 1))
        var mdelay = 500
        var i = 0
        while (i < segLen - 2) {
            when (s.aux0) {
                0 -> { s.setPixelColor(i, 0x00FF0000); mdelay = 150 + (100 * (255 - s.speed)) }
                1 -> {
                    s.setPixelColor(i, 0x00FF0000); mdelay = 150 + (20 * (255 - s.speed))
                    s.setPixelColor(i + 1, 0x00EECC00)
                }
                2 -> { s.setPixelColor(i + 2, 0x0000FF00); mdelay = 150 + (100 * (255 - s.speed)) }
                3 -> { s.setPixelColor(i + 1, 0x00EECC00); mdelay = 150 + (20 * (255 - s.speed)) }
            }
            i += 3
        }
        if (m.getNow() - s.step > mdelay) {
            s.aux0++
            if (s.aux0 == 1 && s.intensity > 140) s.aux0 = 2
            if (s.aux0 > 3) s.aux0 = 0
            s.step = m.getNow()
        }
    }

    private fun runningRandom(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val cycleTime = 25 + (3 * (255 - s.speed))
        val it = m.getNow() / cycleTime
        if (s.call == 0) s.aux0 = m.jsRandom16()
        val zoneSize = ((255 - s.intensity) shr 4) + 1
        var prng16 = s.aux0
        var z = it % zoneSize
        var nzone = (z == 0L && it != s.aux1.toLong())
        for (i in segLen - 1 downTo 0) {
            if (nzone || z >= zoneSize) {
                val lastrand = prng16 shr 8
                var diff = 0
                while (kotlin.math.abs(diff) < 42) {
                    prng16 = ((prng16 * 2053) + 13849) and 0xFFFF
                    diff = (((prng16 shr 8) - lastrand) shl 16) shr 16
                }
                if (nzone) { s.aux0 = prng16; nzone = false }
                z = 0
            }
            s.setPixelColor(i, s.color_wheel(prng16 shr 8))
            z++
        }
        s.aux1 = it.toInt()
    }

    private fun dF64(s: JsSimSegment, off: Int): Double =
        java.lang.Double.longBitsToDouble(s.dU32(off) or (s.dU32(off + 4) shl 32))

    private fun dF64(s: JsSimSegment, off: Int, v: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(v)
        s.dU32(off, bits and 0xFFFFFFFFL)
        s.dU32(off + 4, bits ushr 32)
    }

    private fun f32(s: JsSimSegment, off: Int): Double = s.dF32(off).toDouble()

    private fun rollingBalls(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxNumBalls = 16
        val ballSize = 20
        val dataSize = ballSize * maxNumBalls
        if (s.data == null || s.dataLen < dataSize) s.allocateData(dataSize)

        val numBalls = s.intensity / 16 + 1
        val hasCol2 = s.SEGCOLOR(2) != 0
        val now = m.getNow().toDouble()

        if (s.call == 0) {
            if (hasCol2) s.fill(0) else s.fill(s.SEGCOLOR(1))
            for (i in 0 until maxNumBalls) {
                val b = i * ballSize
                dF64(s, b, now)
                val vel = 20.0 * (m.random16(1000, 10000) / 10000.0)
                s.dF32(b + 8, if (m.random8() < 128) -vel else vel)
                s.dF32(b + 12, m.random16(0, 10000) / 10000.0)
                s.dF32(b + 16, m.random16(1000, 10000) / 10000.0)
            }
        }

        val cfac = (m.scale8(8, 255 - s.speed) + 1) * 20000.0

        if (s.check3) {
            s.fade_out(250)
        } else {
            if (!s.check2) s.fill(if (hasCol2) 0 else s.SEGCOLOR(1))
        }

        for (i in 0 until numBalls) {
            val b = i * ballSize
            val lastBounce = dF64(s, b)
            var velocity = f32(s, b + 8)
            var height = f32(s, b + 12)
            val mass = f32(s, b + 16)

            val timeSinceLastUpdate = (now - lastBounce) / cfac
            var thisHeight = height + velocity * timeSinceLastUpdate

            if (thisHeight < -0.5 || thisHeight > 1.5) {
                height = m.random16(0, 10000) / 10000.0
                thisHeight = height
                dF64(s, b, now)
                s.dF32(b + 12, height)
            }

            if ((thisHeight <= 0.0 && velocity < 0.0) || (thisHeight >= 1.0 && velocity > 0.0)) {
                velocity = -velocity
                dF64(s, b, now)
                s.dF32(b + 8, velocity)
                s.dF32(b + 12, thisHeight)
                height = thisHeight
            }

            if (s.check1) {
                for (j in i + 1 until numBalls) {
                    val bj = j * ballSize
                    val velJ = f32(s, bj + 8)
                    if (velJ != velocity) {
                        val heightJ = f32(s, bj + 12)
                        val lastBounceJ = dF64(s, bj)
                        val massJ = f32(s, bj + 16)
                        val tcollided = (cfac * (height - heightJ) + velocity * (lastBounceJ - lastBounce)) / (velJ - velocity)
                        if (tcollided > 2.0 && tcollided < (now - lastBounceJ)) {
                            val newHeight = height + velocity * (tcollided + (lastBounceJ - lastBounce)) / cfac
                            val newBounceTime = jsRound(tcollided + 0.5) + lastBounceJ
                            s.dF32(b + 12, newHeight)
                            s.dF32(bj + 12, newHeight)
                            dF64(s, b, newBounceTime)
                            dF64(s, bj, newBounceTime)
                            val vtmp = velocity
                            velocity = ((mass - massJ) * vtmp + 2.0 * massJ * velJ) / (mass + massJ)
                            val newVelJ = ((massJ - mass) * velJ + 2.0 * mass * vtmp) / (mass + massJ)
                            s.dF32(b + 8, velocity)
                            s.dF32(bj + 8, newVelJ)
                            thisHeight = newHeight + velocity * (now - newBounceTime) / cfac
                            height = f32(s, b + 12)
                        }
                    }
                }
            }

            var color = s.SEGCOLOR(0)
            if (s.palette != 0) {
                color = s.color_from_palette((i * 255) / numBalls, false, false, 0)
            } else if (hasCol2) {
                color = s.SEGCOLOR(i % 3)
            }

            if (thisHeight < 0.0) thisHeight = 0.0
            if (thisHeight > 1.0) thisHeight = 1.0
            val pos = jsRound(thisHeight * (segLen - 1))
            s.setPixelColor(pos, color)
            dF64(s, b, now)
            s.dF32(b + 12, thisHeight)
        }
    }

    private fun chaseTricolor(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val color1 = s.SEGCOLOR(2)
        val color2 = s.SEGCOLOR(0)
        val cycleTime = 50 + ((255 - s.speed) shl 1)
        val it = m.getNow() / cycleTime
        val width = 1 + (s.intensity shr 4)
        var index = (it % (width * 3)).toInt()
        for (i in 0 until segLen) {
            if (index > (width * 3) - 1) index = 0
            var color = color1
            if (index > (width shl 1) - 1) {
                color = s.color_from_palette(i, true, false, 1)
            } else if (index > width - 1) {
                color = color2
            }
            s.setPixelColor(segLen - i - 1, color)
            index++
        }
    }

    private fun icu(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val now16 = (m.getNow() and 0xFFFF).toInt()
        var dest = s.aux1
        val space = (s.intensity shr 3) + 2
        val stepI = s.step.toInt()
        var state = (stepI shr 16) and 0xFFFF
        var nextUpdate = stepI and 0xFFFF

        val pindex = m.map(dest, 0, kotlin.math.max(1, segLen - segLen / space), 0, 255)
        val col = s.color_from_palette(pindex, false, false, 0)
        val bgcol = if (s.check2) 0 else s.SEGCOLOR(1)
        s.fill(bgcol)

        if (state != 1) {
            if (dest < segLen) s.setPixelColor(dest, col)
            val eye2pos = dest + segLen / space
            if (eye2pos < segLen) s.setPixelColor(eye2pos, col)
            if (state == 3) {
                if (s.aux0 > s.aux1) dest++ else if (s.aux0 < s.aux1) dest--
                if (dest >= 0 && dest < segLen) s.setPixelColor(dest, col)
                val nextEye2 = dest + segLen / space
                if (nextEye2 >= 0 && nextEye2 < segLen) s.setPixelColor(nextEye2, col)
            }
        }

        if ((((now16 - nextUpdate) shl 16) shr 16) >= 0) {
            var st = state
            if (st == 0) {
                state++
                if (m.jsRandom8(6) == 0) {
                    nextUpdate = (now16 + 200) and 0xFFFF
                    st = -1
                } else st = 1
            }
            when (st) {
                1 -> { nextUpdate = (now16 + 500 + m.jsRandom16(1000)) and 0xFFFF; state = 2 }
                2 -> {
                    s.aux0 = m.jsRandom16(kotlin.math.max(1, segLen - segLen / space))
                    nextUpdate = now16
                    state = 3
                }
                -1 -> {}
                else -> {
                    s.aux1 = dest
                    nextUpdate = (now16 + m.SPEED_FORMULA_L(s.speed, segLen)) and 0xFFFF
                    if (s.aux0 == dest) {
                        nextUpdate = (now16 + 500 + m.jsRandom16(1000)) and 0xFFFF
                        state = 0
                    }
                }
            }
        }
        s.step = (((state and 0xFFFF) shl 16) or (nextUpdate and 0xFFFF)).toLong()
    }

    private class Prng { var seed = 0 }

    private fun prngRandom8(p: Prng, mod: Int = -1): Int {
        val d = p.seed.toDouble() * 1103515245.0 + 12345.0
        p.seed = i32(d) and 0x7FFFFFFF
        val v = (p.seed shr 16) and 0xFF
        if (mod > 0) return v % mod
        return v
    }

    private fun stream2(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) {
            val r = m.jsRandom8(); val g = m.jsRandom8(); val b = m.jsRandom8()
            s.step = (((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)).toLong()
            s.aux0 = m.jsRandom16()
        }
        val cycleTime = 25 + 3 * (255 - s.speed)
        val it = m.getNow() / cycleTime
        var color = s.step.toInt()
        val p = (s.state as? Prng) ?: Prng().also { s.state = it }
        p.seed = s.aux0
        for (i in segLen - 1 downTo 0) {
            val cr = if (prngRandom8(p, 6) != 0) (color shr 16) and 0xFF else prngRandom8(p)
            val cg = if (prngRandom8(p, 6) != 0) (color shr 8) and 0xFF else prngRandom8(p)
            val cb = if (prngRandom8(p, 6) != 0) color and 0xFF else prngRandom8(p)
            color = ((cr and 0xFF) shl 16) or ((cg and 0xFF) shl 8) or (cb and 0xFF)
            s.setPixelColor(i, color)
            if (i == segLen - 1 && s.aux1 != (it and 0xFFFF).toInt()) {
                s.step = color.toLong()
                s.aux0 = p.seed
            }
        }
        s.aux1 = (it and 0xFFFF).toInt()
    }

    private fun noise1(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val scale = 320L
        s.step = s.step + (1 + (s.speed shr 4))
        for (i in 0 until segLen) {
            val shiftX = m.beatsin8_t(11)
            val shiftY = s.step / 42
            val realX = (i + shiftX) * scale
            val realY = (i + shiftY) * scale
            val realZ = s.step
            val noise = m.perlin16(realX and 0xFFFFFFFFL, realY and 0xFFFFFFFFL, realZ and 0xFFFFFFFFL) shr 8
            val index = m.sin8_t((noise * 3) and 0xFF)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0))
        }
    }

    private fun noise2(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val scale = 1000L
        s.step = s.step + (1 + (s.speed shr 1))
        for (i in 0 until segLen) {
            val shiftX = s.step.toInt() shr 6
            val realX = (i + shiftX) * scale
            val noise = m.perlin16(realX and 0xFFFFFFFFL, 0L, 4223L) shr 8
            val index = m.sin8_t((noise * 3) and 0xFF)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0, noise))
        }
    }

    private fun noise3(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val scale = 800L
        s.step = s.step + (1 + s.speed)
        for (i in 0 until segLen) {
            val realX = (i + 4223) * scale
            val realY = (i + 1234) * scale
            val realZ = s.step * 8
            val noise = m.perlin16(realX and 0xFFFFFFFFL, realY and 0xFFFFFFFFL, realZ and 0xFFFFFFFFL) shr 8
            val index = m.sin8_t((noise * 3) and 0xFF)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0, noise))
        }
    }

    private fun noise4(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val stp = ((m.getNow() * s.speed).toInt() shr 7).toLong() and 0xFFFFFFFFL
        for (i in 0 until segLen) {
            val index = m.perlin16((i shl 12).toLong() and 0xFFFFFFFFL, stp)
            s.setPixelColor(i, s.color_from_palette(index and 0xFF, false, false, 0))
        }
    }
}
