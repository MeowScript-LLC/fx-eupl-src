/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-09-25.
 */

package fx

import fx.JsSimMath.Companion.i32
import fx.JsSimMath.Companion.jsRound
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object JsFx1DB4 : JsFxBatch {
    override val ids: IntArray = intArrayOf(
        98, 104, 107, 111, 113, 115, 147, 128, 129, 132, 156, 157, 158, 116, 179, 184, 218, 219,
        130, 131, 133, 134, 144, 135, 136, 137, 138, 140, 141, 143, 145,
    )

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            98 -> percent(s)
            104 -> sunrise(s)
            107 -> noisePal(s)
            111 -> chunchun(s)
            113 -> washingMachine(s)
            115 -> blends(s)
            147 -> perlinMove(s)
            128 -> pixels(s)
            129 -> pixelwave(s)
            132 -> gravcenterBase(s, 2)
            156 -> gravcenterBase(s, 0)
            157 -> gravcenterBase(s, 1)
            158 -> gravcenterBase(s, 3)
            116 -> tvSimulator(s)
            179 -> flowStripe(s)
            184 -> wavesins(s)
            218 -> colorClouds(s)
            219 -> slowTransition(s)
            130 -> juggles(s)
            131 -> matripix(s)
            133 -> plasmoid(s)
            134 -> puddlesBase(s, false)
            144 -> puddlesBase(s, true)
            135 -> midnoise(s)
            136 -> noisemeter(s)
            137 -> freqwave(s)
            138 -> freqmatrix(s)
            140 -> waterfall(s)
            141 -> freqpixels(s)
            143 -> noisefire(s)
            145 -> noisemove(s)
            else -> return false
        }
        return true
    }

    private class Blends(val pixels: IntArray)
    private class PixVals { val v = IntArray(32) }
    private class Grav { var topLED = 0; var gravityCounter = 0 }
    private class TvSim {
        var totalTime = 0; var fadeTime = 0; var startTime = 0L; var elapsed = 0L
        var sliderValues = 0
        var sceeneStart = 0L; var sceeneDuration = 0
        var sceeneColorHue = 0; var sceeneColorSat = 0; var sceeneColorBri = 0
        var actualColorR = 0; var actualColorG = 0; var actualColorB = 0
        var pr = 0; var pg = 0; var pb = 0
        val temp = IntArray(5)
    }
    private class StData(var startTime: Long, var stepsDone: Long)
    private class MatPixels(val px: IntArray)
    private class Plas { var thisphase = 0; var thatphase = 0 }
    private class Waterfall(val px: IntArray)
    private class FirePal(val pal: IntArray)

    private fun floorD(x: Double): Double = floor(x)

    private fun percent(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var percent = s.intensity
        percent = m.constrain(percent, 0, 200)
        val activeLeds = if (percent < 100) jsRound(segLen * percent / 100.0).toInt()
        else jsRound(segLen * (200 - percent) / 100.0).toInt()
        var size = 1 + ((s.speed * segLen) shr 11)
        if (s.speed == 255) size = 255
        if (percent <= 100) {
            for (i in 0 until segLen) {
                if (i < s.aux1) {
                    if (s.check1) s.setPixelColor(i, s.color_from_palette(m.map(percent, 0, 100, 0, 255), false, false, 0))
                    else s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
                } else {
                    s.setPixelColor(i, s.SEGCOLOR(1))
                }
            }
        } else {
            for (i in 0 until segLen) {
                if (i < (segLen - s.aux1)) {
                    s.setPixelColor(i, s.SEGCOLOR(1))
                } else {
                    if (s.check1) s.setPixelColor(i, s.color_from_palette(m.map(percent, 100, 200, 255, 0), false, false, 0))
                    else s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
                }
            }
        }
        if (activeLeds > s.aux1) {
            s.aux1 += size
            if (s.aux1 > activeLeds) s.aux1 = activeLeds
        } else if (activeLeds < s.aux1) {
            if (s.aux1 > size) s.aux1 -= size else s.aux1 = 0
            if (s.aux1 < activeLeds) s.aux1 = activeLeds
        }
    }

    private fun sunrise(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.call == 0 || s.speed != s.aux0) {
            s.step = m.getNow()
            s.aux0 = s.speed
        }
        s.fill(0)
        var stage = 0xFFFF
        val s10SinceStart = Math.floorDiv(m.getNow() - s.step, 100L)
        if (s.speed > 120) {
            val counter = (i32(m.getNow().toDouble()) shr 1).toLong() * (((s.speed - 120) shr 1) + 1)
            stage = m.triwave16((counter and 0xFFFF).toInt())
        } else if (s.speed != 0) {
            var durMins = s.speed
            if (durMins > 60) durMins -= 60
            val s10Target = durMins * 600
            val s10 = min(s10SinceStart, s10Target.toLong())
            stage = m.map(s10.toDouble(), 0.0, s10Target.toDouble(), 0.0, 65535.0).toInt()
            if (s.speed > 60) stage = 0xFFFF - stage
        }
        for (i in 0..(segLen / 2)) {
            var wave = m.triwave16((Math.floorDiv(i.toLong() * stage, segLen.toLong()) and 0xFFFF).toInt())
            wave = (wave shr 8) + ((wave * s.intensity) shr 15)
            val c = if (wave > 240) s.color_from_palette(240, false, true, 255)
            else s.color_from_palette(wave, false, true, 255)
            s.setPixelColor(i, c)
            s.setPixelColor(segLen - i - 1, c)
        }
    }

    private fun noisePalFill(s: JsSimSegment, base: Int) {
        val m = s.m
        val baseI = m.random8()
        val a00 = (baseI + m.random8(0, 64)) and 0xFF; val a01 = 255; val a02 = m.random8(128, 255)
        val a10 = (baseI + 128) and 0xFF; val a11 = 255; val a12 = m.random8(128, 255)
        val a20 = (baseI + m.random8(0, 92)) and 0xFF; val a21 = 192; val a22 = m.random8(128, 255)
        val a30 = (baseI + m.random8(0, 92)) and 0xFF; val a31 = 255; val a32 = m.random8(128, 255)
        val anchors = arrayOf(intArrayOf(a00, a01, a02), intArrayOf(a10, a11, a12), intArrayOf(a20, a21, a22), intArrayOf(a30, a31, a32))
        for (i in 0 until 16) {
            val ai = i * 4 / 16.0
            val a0 = floor(ai).toInt() % 4
            val a1 = (a0 + 1) % 4
            val frac = ai - floor(ai)
            val off = base + i * 3
            for (c in 0 until 3) {
                s.d8(off + c, i32(floor(anchors[a0][c] * (1 - frac) + anchors[a1][c] * frac)))
            }
        }
    }

    private fun noisePal(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val scale = 15 + (s.intensity shr 2)
        val dataSize = 96 * 2
        if (s.call == 0 || s.data == null || s.dataLen < dataSize) {
            s.allocateData(dataSize)
            for (p in 0 until 2) noisePalFill(s, p * 96)
            s.step = m.getNow()
            s.aux0 = 0
        }
        val changePaletteMs = 4000 + s.speed * 10
        if (m.getNow() - s.step > changePaletteMs) {
            s.step = m.getNow()
            noisePalFill(s, 96)
        }
        for (i in 0 until 48) {
            val cur = s.d8(i)
            val tgt = s.d8(96 + i)
            if (cur < tgt) s.d8(i, min(cur + 48, tgt))
            else if (cur > tgt) s.d8(i, max(cur - 48, tgt))
        }
        val useSEGPALETTE = s.palette > 0
        for (i in 0 until segLen) {
            val index = m.perlin8(i * scale, s.aux0 + i * scale)
            if (useSEGPALETTE) {
                s.setPixelColor(i, s.ColorFromPalette(index, 255, 1))
            } else {
                val fi = (index / 256.0) * 15.99
                val i0 = floor(fi).toInt() % 16
                val i1 = (i0 + 1) % 16
                val frac = fi - floor(fi)
                val h = floor(s.d8(i0 * 3) * (1 - frac) + s.d8(i1 * 3) * frac).toInt() and 0xFF
                val sat = floor(s.d8(i0 * 3 + 1) * (1 - frac) + s.d8(i1 * 3 + 1) * frac).toInt()
                val v = floor(s.d8(i0 * 3 + 2) * (1 - frac) + s.d8(i1 * 3 + 2) * frac).toInt()
                s.setPixelColor(i, m.hsv2rgb(h, sat, v))
            }
        }
        s.aux0 += m.beatsin8(10, 1, 4)
    }

    private fun chunchun(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(254)
        val counter = (m.getNow() * (6 + (s.speed shr 4))) and 0xFFFFFFFFL
        val numBirds = 2 + (segLen shr 3)
        val span = (s.intensity shl 8) / numBirds
        var cnt = counter
        for (i in 0 until numBirds) {
            cnt = (cnt - span) and 0xFFFFFFFFL
            val megumin = (m.sin16((cnt and 0xFFFF).toInt()) + 0x8000) and 0xFFFF
            var bird = (megumin * segLen) ushr 16
            bird = m.constrain(bird, 0, segLen - 1)
            s.setPixelColor(bird, s.color_from_palette((i * 255) / numBirds, false, false, 0))
        }
    }

    private fun washingMachine(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val x0 = (i32(m.getNow().toDouble()) shr 7) and 0xFF
        var a = 127
        var x = x0
        if (x > 127) { a = -127; x -= 127 }
        val speed = when {
            x < 15 -> Math.floorDiv(x * a, 15)
            x < 75 -> a
            x < 90 -> Math.floorDiv((90 - x) * a, 15)
            else -> 0
        }
        s.step = (s.step + Math.floorDiv(speed * 2048, 512 - s.speed)) and 0xFFFFFFFFL
        val stepSh = i32(s.step.toDouble()) shr 7
        for (i in 0 until segLen) {
            val col = m.sin8((i32(floor((s.intensity / 25.0 + 1) * 255 * i / segLen)) + stepSh) and 0xFF)
            s.setPixelColor(i, s.color_from_palette(col, false, false, 3))
        }
    }

    private fun blends(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val pixelLen = if (segLen > 255) 255 else segLen
        var st = s.state as? Blends
        if (s.call == 0 || st == null || st.pixels.size < pixelLen + 1) {
            st = Blends(IntArray(pixelLen + 1)); s.state = st
        }
        val pixels = st.pixels
        val blendSpeed = m.map(s.intensity, 0, 255, 10, 128)
        var shift = (i32((m.getNow() * ((s.speed shr 3) + 1)).toDouble()) shr 8).toLong() and 0xFFFFFFFFL
        for (i in 0 until pixelLen) {
            val qw = m.quadwave8(((i + 1) * 16) and 0xFF)
            pixels[i] = m.color_blend(pixels[i], s.color_from_palette(((shift + qw) and 0xFF).toInt(), false, false, 255), blendSpeed)
            shift = (shift + 3) and 0xFFFFFFFFL
        }
        var offset = 0
        for (i in 0 until segLen) {
            s.setPixelColor(i, pixels[offset])
            offset++
            if (offset >= pixelLen) offset = 0
        }
    }

    private fun perlinMove(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(255 - s.custom1)
        for (i in 0 until s.intensity / 16 + 1) {
            val t = Math.floorDiv(m.getNow() * 128, (260 - s.speed).toLong())
            val locn = m.perlin16(t + i * 15000, t)
            val pixloc = m.map(locn, 50 * 256, 192 * 256, 0, segLen - 1)
            s.setPixelColor(pixloc, s.color_from_palette(pixloc % 255, false, false, 0))
        }
    }

    private fun pixels(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val st = (s.state as? PixVals) ?: PixVals().also { s.state = it }
        val vol = s.getVolume()
        st.v[(m.getNow() % 32).toInt()] = vol and 0xFF
        s.fade_out(64 + (s.speed shr 1))
        for (i in 0 until s.intensity / 8) {
            val segLoc = m.random16(segLen)
            s.setPixelColor(segLoc, m.color_blend(s.SEGCOLOR(1),
                s.color_from_palette(st.v[i % 32] + i * 4, false, false, 0), vol))
        }
    }

    private fun secondHandBase(s: JsSimSegment): Long =
        floor(s.m.getNow() * 1000.0 / (256 - s.speed) / 500.0).toLong()

    private fun pixelwave(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.call == 0) s.fill(0)
        val vol = s.getVolume()
        val secondHand = ((secondHandBase(s) + 1) % 16).toInt()
        if (secondHand != s.aux0) {
            s.aux0 = secondHand
            val pixBri = i32(min(255.0, floor(vol * s.intensity / 64.0))) and 0xFF
            s.setPixelColor(segLen / 2, m.color_blend(s.SEGCOLOR(1),
                s.color_from_palette((m.getNow() and 0xFF).toInt(), false, false, 0), pixBri))
            for (i in segLen - 1 downTo segLen / 2 + 1) s.setPixelColor(i, s.getPixelColor(i - 1))
            for (i in 0 until segLen / 2) s.setPixelColor(i, s.getPixelColor(i + 1))
        }
    }

    private fun gravcenterBase(s: JsSimSegment, mode: Int) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val g = (s.state as? Grav) ?: Grav().also { s.state = it }
        val vol = s.getVolume()
        when (mode) {
            1 -> s.fade_out(253)
            2 -> s.fade_out(249)
            3 -> s.fade_out(250)
            else -> s.fade_out(251)
        }
        var ssa = vol * s.intensity / 255.0
        val tempsamp: Int
        if (mode == 2) {
            ssa *= 0.25
            tempsamp = m.constrain(floor(ssa * 2.0 * (segLen - 1) / 64.0).toInt(), 0, segLen - 1)
        } else {
            ssa *= 0.125
            tempsamp = m.constrain(floor(ssa * 2.0 * (segLen / 2.0) / 32.0).toInt(), 0, segLen / 2)
        }
        val gravity = 8 - s.speed / 32
        val offset = if (mode == 2) 0 else 1
        if (tempsamp >= g.topLED) g.topLED = tempsamp - offset
        else if (g.gravityCounter % gravity == 0) g.topLED--

        val half = segLen / 2
        val now = m.getNow()
        when (mode) {
            1 -> {
                for (i in 0 until tempsamp) {
                    val index = i32(floor(ssa * 24 + now / 200.0)) and 0xFF
                    s.setPixelColor(i + half, s.color_from_palette(index, false, false, 0))
                    s.setPixelColor(half - 1 - i, s.color_from_palette(index, false, false, 0))
                }
                if (g.topLED >= 0) {
                    s.setPixelColor(g.topLED + half, 0x808080)
                    s.setPixelColor(half - 1 - g.topLED, 0x808080)
                }
            }
            2 -> {
                for (i in 0 until tempsamp) {
                    val index = m.perlin8(floor(i * ssa + now).toLong(), floor(5000 + i * ssa).toLong())
                    s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(index, false, false, 0),
                        min(255, floor(ssa * 8).toInt())))
                }
                if (g.topLED > 0) s.setPixelColor(g.topLED, s.color_from_palette((now and 0xFF).toInt(), false, false, 0))
            }
            3 -> {
                val maxFreqLog10 = 4.04238
                var peak = s.getMajorPeak()
                if (peak < 1) peak = 1.0
                val freqIndex = i32(floor((Math.log10(peak) - (maxFreqLog10 - 1.78)) * 255)) and 0xFF
                for (i in 0 until tempsamp) {
                    s.setPixelColor(i + half, s.color_from_palette(freqIndex, false, false, 0))
                    s.setPixelColor(half - i - 1, s.color_from_palette(freqIndex, false, false, 0))
                }
                if (g.topLED >= 0) {
                    s.setPixelColor(g.topLED + half, 0x808080)
                    s.setPixelColor(half - 1 - g.topLED, 0x808080)
                }
            }
            else -> {
                for (i in 0 until tempsamp) {
                    val index = m.perlin8(floor(i * ssa + now).toLong(), floor(5000 + i * ssa).toLong())
                    val col = m.color_blend(s.SEGCOLOR(1), s.color_from_palette(index, false, false, 0),
                        min(255, floor(ssa * 8).toInt()))
                    s.setPixelColor(i + half, col)
                    s.setPixelColor(half - i - 1, col)
                }
                if (g.topLED >= 0) {
                    s.setPixelColor(g.topLED + half, s.color_from_palette((now and 0xFF).toInt(), false, false, 0))
                    s.setPixelColor(half - 1 - g.topLED, s.color_from_palette((now and 0xFF).toInt(), false, false, 0))
                }
            }
        }
        g.gravityCounter = (g.gravityCounter + 1) % gravity
    }

    private fun tvSimulator(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val now = m.getNow()
        var tv = s.state as? TvSim
        if (tv == null) {
            tv = TvSim(); s.state = tv
            s.aux0 = 0
            s.aux1 = 0
        }
        val colorSpeed = m.map(s.speed, 0, 255, 1, 20)
        val colorIntensity = m.map(s.intensity, 0, 255, 10, 30)
        val sliderVal = (s.speed shl 8) or s.intensity
        if (sliderVal != tv.sliderValues) {
            tv.sliderValues = sliderVal
            s.aux1 = 0
        }
        if ((now - tv.sceeneStart) >= tv.sceeneDuration || s.aux1 == 0) {
            tv.sceeneStart = now
            tv.sceeneDuration = m.random16(60 * 250 * colorSpeed, 60 * 750 * colorSpeed)
            tv.sceeneColorHue = m.random16(0, 768)
            tv.sceeneColorSat = m.random8(100, 130 + colorIntensity)
            tv.sceeneColorBri = m.random8(200, 240)
            s.aux1 = 1
            s.aux0 = 0
        }
        if (s.aux0 == 0) {
            var j = m.random8(4 * colorIntensity)
            val hue = if (m.random8() < 128) {
                if (j < tv.sceeneColorHue) tv.sceeneColorHue - j else 767 - tv.sceeneColorHue - j
            } else {
                if ((j + tv.sceeneColorHue) < 767) tv.sceeneColorHue + j else tv.sceeneColorHue + j - 767
            }
            j = m.random8(2 * colorIntensity)
            val sat = max(0, tv.sceeneColorSat - j)
            j = m.random8(100)
            val bri = max(0, tv.sceeneColorBri - j)
            val temp = tv.temp
            java.util.Arrays.fill(temp, 0)
            val n = Math.floorDiv(hue, 256) % 3
            val x = ((((hue and 255) * sat) shr 8) * bri) shr 8
            val sv = ((256 - sat) * bri) shr 8
            temp[0] = sv and 0xFF; temp[3] = sv and 0xFF
            temp[1] = (x + sv) and 0xFF; temp[4] = (x + sv) and 0xFF
            temp[2] = (bri - x) and 0xFF
            tv.actualColorR = temp[n + 2]
            tv.actualColorG = temp[n + 1]
            tv.actualColorB = temp[n]
        }
        val nr = m.gamma8(tv.actualColorR) * 257
        val ng = m.gamma8(tv.actualColorG) * 257
        val nb = m.gamma8(tv.actualColorB) * 257
        if (s.aux0 == 0) {
            s.aux0 = 1
            tv.totalTime = m.random16(250, 2500)
            tv.fadeTime = m.random16(0, tv.totalTime)
            if (m.random8(10) < 3) tv.fadeTime = 0
            tv.startTime = now
        }
        tv.elapsed = now - tv.startTime
        val r: Int; val g: Int; val b: Int
        if (tv.elapsed < tv.fadeTime) {
            val e = tv.elapsed.toDouble(); val ft = tv.fadeTime.toDouble()
            r = m.map(e, 0.0, ft, tv.pr.toDouble(), nr.toDouble()).toInt()
            g = m.map(e, 0.0, ft, tv.pg.toDouble(), ng.toDouble()).toInt()
            b = m.map(e, 0.0, ft, tv.pb.toDouble(), nb.toDouble()).toInt()
        } else {
            r = nr; g = ng; b = nb
        }
        val col = m.colorPack(r shr 8, g shr 8, b shr 8)
        for (i in 0 until segLen) s.setPixelColor(i, col)
        if (tv.elapsed >= tv.totalTime) {
            tv.pr = nr; tv.pg = ng; tv.pb = nb
            s.aux0 = 0
        }
    }

    private fun flowStripe(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val hl = segLen * 10 / 13
        val now = m.getNow()
        val hue = (now / (s.speed + 1)).toInt() and 0xFF
        val t = now / (s.intensity / 8 + 1)
        for (i in 0 until segLen) {
            var c = (kotlin.math.abs(i - hl) * 127) / max(1, hl)
            c = m.sin8(c and 0xFF)
            c = m.sin8(((c / 2) + t).toInt() and 0xFF)
            val b = m.sin8((c + t / 8).toInt() and 0xFF)
            s.setPixelColor(i, s.color_from_palette((b + hue) and 0xFF, false, true, 3))
        }
    }

    private fun wavesins(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        for (i in 0 until segLen) {
            val bri = m.sin8(((m.getNow() / 4) + i.toLong() * s.intensity).toInt() and 0xFF)
            val idx = m.beatsin8(s.speed, s.custom1, s.custom1 + s.custom2, 0L, i * (s.custom3 shl 3))
            s.setPixelColor(i, s.color_from_palette(idx, false, false, 0, bri))
        }
    }

    private fun colorClouds(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) {
            s.aux0 = m.random16()
            s.aux1 = m.random16()
        }
        val volX0 = s.aux0
        val hueX0 = s.aux1
        val hueOffset0 = (volX0 + hueX0) and 0xFF
        val cozy = s.check3
        val volSpeed = 1 + s.speed
        val hueSpeed = 1 + s.intensity
        val volSqueeze = 8 + s.custom1
        val hueSqueeze = s.custom2
        val volCutoff = 12500 + s.custom3 * 900
        val volSaturate = 52000
        val now = m.getNow()
        val volT = Math.floorDiv(now * volSpeed, 8L)
        val hueT = Math.floorDiv(now * hueSpeed, 8L)
        val hueOffset = (m.beat88(64) shr 8) and 0xFF
        for (i in 0 until segLen) {
            val volX = i * volSqueeze * 64
            var vol = m.perlin16((volX0 + volX).toLong(), volT)
            vol = m.map(vol, volCutoff, volSaturate, 0, 255)
            vol = m.constrain(vol, 0, 255)
            val hueX = i * hueSqueeze * 8
            var hue = (m.perlin16((hueX0 + hueX).toLong(), hueT) shr 7) and 0xFF
            hue = (hue + hueOffset0 + hueOffset) and 0xFF
            if (cozy) hue = m.cos8((128 + hue / 2) and 0xFF)
            var pixel = if (s.palette != 0) s.color_from_palette(hue, false, true, 0, vol) else m.hsv2rgb(hue, 255, vol)
            if (m.colorR(pixel) + m.colorG(pixel) + m.colorB(pixel) <= 2) pixel = 0
            s.setPixelColor(i, pixel)
        }
    }

    private fun slowTransition(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val totalSteps = if (s.check2) 16 * 255 else 255
        val duration = if (s.speed == 0) 10000L else s.speed * 60000L
        var st = s.state as? StData
        if (s.call == 0 || st == null) {
            st = StData(m.getNow(), 0xFFFF); s.state = st
            s.aux1 = s.speed
        }
        if (s.aux1 != s.speed) {
            st.startTime = m.getNow()
            st.stepsDone = 0
            s.aux1 = s.speed
        }
        val elapsed = m.getNow() - st.startTime
        val expectedSteps = min(totalSteps.toLong(), Math.floorDiv(elapsed * totalSteps, duration))
        if (st.stepsDone < expectedSteps) st.stepsDone = expectedSteps
        for (i in 0 until segLen) {
            val paletteIndex = (i * 255) / segLen
            s.setPixelColor(i, s.ColorFromPalette(paletteIndex, 255, 0))
        }
    }

    private fun juggles(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        s.fade_out(224)
        val vol = s.getVolume()
        val agc = min(255, max(0, vol))
        val numBalls = s.intensity / 32 + 1
        for (i in 0 until numBalls) {
            val pos = m.beatsin16_t(s.speed / 4 + i * 2, 0, segLen - 1)
            s.setPixelColor(pos, m.color_blend(s.SEGCOLOR(1),
                s.color_from_palette((m.getNow() / 4).toInt() + i * 2, false, false, 0), agc))
        }
    }

    private fun matripix(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        s.soundSim = 1
        var st = s.state as? MatPixels
        if (s.call == 0 || st == null) { st = MatPixels(IntArray(segLen)); s.state = st }
        val pixels = st.px
        val vol = s.getVolume()
        val secondHand = (secondHandBase(s) % 16).toInt()
        if (secondHand != s.aux0) {
            s.aux0 = secondHand
            val pixBri = i32(min(255.0, floor(vol * s.intensity / 64.0))) and 0xFF
            val k = segLen - 1
            for (i in 0 until k) {
                pixels[i] = pixels[i + 1]
                s.setPixelColor(i, pixels[i])
            }
            pixels[k] = m.color_blend(s.SEGCOLOR(1),
                s.color_from_palette((m.getNow() and 0xFF).toInt(), false, false, 0), pixBri)
            s.setPixelColor(k, pixels[k])
        }
    }

    private fun plasmoid(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val vol = s.getVolume()
        val p = (s.state as? Plas) ?: Plas().also { s.state = it }
        s.fadeToBlackBy(32)
        p.thisphase += m.beatsin8(6, -4, 4)
        p.thatphase += m.beatsin8(7, -4, 4)
        val mul1 = 1 + (3 * s.speed) / 32
        val mul2 = 97 + (5 * s.speed) / 32
        for (i in 0 until segLen) {
            var thisbright = m.cubicwave8((i * mul1 + p.thisphase) and 0xFF) / 2
            thisbright += m.cos8((i * mul2 + p.thatphase) and 0xFF) / 2
            val colorIndex = thisbright
            if (vol * s.intensity / 64.0 < thisbright) thisbright = 0
            s.addPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(colorIndex, false, false, 0), thisbright))
        }
    }

    private fun puddlesBase(s: JsSimSegment, peakdetect: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        var size = 0
        val fadeVal = m.map(s.speed, 0, 255, 224, 254)
        val pos = m.random16(segLen)
        s.fade_out(fadeVal)
        val volumeRaw = s.getVolume()
        val samplePeak = s.getSamplePeak()
        val volumeSmth = s.getVolume()
        if (peakdetect) {
            if (samplePeak == 1) {
                size = floor(volumeSmth * s.intensity / 256.0 / 4.0).toInt() + 1
                if (pos + size >= segLen) size = segLen - pos
            }
        } else {
            if (volumeRaw > 1) {
                size = floor(volumeRaw * s.intensity / 256.0 / 8.0).toInt() + 1
                if (pos + size >= segLen) size = segLen - pos
            }
        }
        for (i in 0 until size) {
            s.setPixelColor(pos + i, s.color_from_palette((m.getNow() and 0xFF).toInt(), false, false, 0))
        }
    }

    private fun midnoise(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(s.speed)
        s.fade_out(s.speed)
        val vol = s.getVolume()
        var tmpSound2 = vol * s.intensity / 256.0
        tmpSound2 *= s.intensity / 128.0
        var maxLen = floor(tmpSound2 * (segLen / 2.0) / 127.0).toInt()
        if (maxLen > segLen / 2) maxLen = segLen / 2
        for (i in segLen / 2 - maxLen until segLen / 2 + maxLen) {
            val index = m.perlin8(i * vol + s.aux0, s.aux1 + i * vol)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0))
        }
        s.aux0 += m.beatsin8(5, 0, 10)
        s.aux1 += m.beatsin8(4, 0, 10)
    }

    private fun noisemeter(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val vol = s.getVolume()
        val fadeRate = m.map(s.speed, 0, 255, 200, 254)
        s.fade_out(fadeRate)
        val tmpSound2 = vol * 2.0 * s.intensity / 255.0
        var maxLen = floor(tmpSound2 * segLen / 255.0).toInt()
        if (maxLen < 0) maxLen = 0
        if (maxLen > segLen) maxLen = segLen
        for (i in 0 until maxLen) {
            val index = m.perlin8(i * vol + s.aux0, s.aux1 + i * vol)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0))
        }
        s.aux0 += m.beatsin8(5, 0, 10)
        s.aux1 += m.beatsin8(4, 0, 10)
    }

    private fun freqwave(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) s.fill(0)
        val maxFrequency = 11025
        var peak = s.getMajorPeak()
        val volumeSmth = s.getVolume()
        val secondHand = (secondHandBase(s) % 16).toInt()
        if (s.aux0 != secondHand) {
            s.aux0 = secondHand
            val c3 = if (s.custom3 != 0) s.custom3 else 1
            val sensitivity = m.mapf(c3.toDouble(), 1.0, 31.0, 1.0, 10.0)
            val pixVal = min(255.0, volumeSmth * s.intensity / 256.0 * sensitivity)
            val intensity = pixVal / 255.0
            var color: Int
            if (peak > maxFrequency) peak = 1.0
            if (peak < 80) {
                color = 0
            } else {
                val upperLimit = 80 + 42 * s.custom2
                val lowerLimit = 80 + 3 * s.custom1
                val i = if (lowerLimit != upperLimit) m.map(floor(peak).toInt(), lowerLimit, upperLimit, 0, 255) and 0xFF
                else floor(peak).toInt() and 0xFF
                val b = min(255, floor(255.0 * intensity).toInt())
                color = m.hsv2rgb(i, 240, b)
            }
            s.setPixelColor(segLen / 2, color)
            for (i in segLen - 1 downTo segLen / 2 + 1) s.setPixelColor(i, s.getPixelColor(i - 1))
            for (i in 0 until segLen / 2) s.setPixelColor(i, s.getPixelColor(i + 1))
        }
    }

    private fun freqmatrix(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) s.fill(0)
        val maxFrequency = 11025
        var peak = s.getMajorPeak()
        val volumeSmth = s.getVolume()
        val secondHand = (secondHandBase(s) % 16).toInt()
        if (s.aux0 != secondHand) {
            s.aux0 = secondHand
            val sensitivity = m.map(s.custom3, 0, 31, 1, 10)
            var pixVal = floor(volumeSmth * s.intensity * sensitivity / 256.0).toInt()
            if (pixVal > 255) pixVal = 255
            val intensity = m.map(pixVal, 0, 255, 0, 100) / 100.0
            var color: Int
            if (peak > maxFrequency) peak = 1.0
            if (peak < 80) {
                color = 0
            } else {
                val upperLimit = 80 + 42 * s.custom2
                val lowerLimit = 80 + 3 * s.custom1
                val i = if (lowerLimit != upperLimit) m.map(floor(peak).toInt(), lowerLimit, upperLimit, 0, 255) and 0xFF
                else floor(peak).toInt() and 0xFF
                var b = floor(255 * intensity).toInt()
                if (b > 255) b = 255
                color = m.hsv2rgb(i, 240, b)
            }
            s.setPixelColor(0, color)
            for (i in segLen - 1 downTo 1) s.setPixelColor(i, s.getPixelColor(i - 1))
        }
    }

    private fun waterfall(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var st = s.state as? Waterfall
        if (s.call == 0 || st == null || st.px.size < segLen) {
            st = Waterfall(IntArray(segLen)); s.state = st
            s.aux0 = 255
        }
        val pixels = st.px
        val peak = s.getMajorPeak()
        val samplePeak = s.getSamplePeak()
        val myMagnitude = s.getMagnitude() / 8.0
        val secondHand = ((secondHandBase(s) + 1) % 16).toInt()
        if (s.aux0 != secondHand) {
            s.aux0 = secondHand
            var pixCol = i32(floor((Math.log10(peak) - 2.26) * 150))
            if (peak < 182.0) pixCol = 0
            val k = segLen - 1
            if (samplePeak != 0) {
                pixels[k] = m.hsv2rgb(92, 92, 92)
            } else {
                pixels[k] = m.color_blend(s.SEGCOLOR(1), s.color_from_palette((pixCol + s.intensity) and 0xFF, false, false, 0),
                    i32(min(255.0, floor(myMagnitude))) and 0xFF)
            }
            s.setPixelColor(k, pixels[k])
            for (i in 0 until k) {
                pixels[i] = pixels[i + 1]
                s.setPixelColor(i, pixels[i])
            }
        }
    }

    private fun freqpixels(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val maxFreqLog10 = 4.04238
        val peak = s.getMajorPeak()
        val myMagnitude = s.getMagnitude() / 16.0
        val fadeRate = m.map(s.speed * s.speed, 0, 65535, 1, 255)
        val fadeoutDelay = (256 - s.speed) / 64
        if (fadeoutDelay <= 1 || (s.call % fadeoutDelay) == 0) s.fade_out(fadeRate)
        var pixCol = i32(floor((Math.log10(peak) - 1.78) * 255.0 / (maxFreqLog10 - 1.78)))
        if (peak < 61.0) pixCol = 0
        for (i in 0 until s.intensity / 32 + 1) {
            val locn = m.random16(segLen)
            s.setPixelColor(locn, m.color_blend(s.SEGCOLOR(1), s.color_from_palette((s.intensity + pixCol) and 0xFF, false, false, 0),
                i32(min(255.0, floor(myMagnitude))) and 0xFF))
        }
    }

    private fun noisefire(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val vol = s.getVolume()
        if (s.call == 0) s.fill(0)
        val fp = (s.state as? FirePal) ?: FirePal(intArrayOf(
            m.hsv2rgb(0, 255, 2), m.hsv2rgb(0, 255, 4), m.hsv2rgb(0, 255, 8), m.hsv2rgb(0, 255, 8),
            m.hsv2rgb(0, 255, 16), 0xFF0000, 0xFF0000, 0xFF0000,
            0xFF8C00, 0xFF8C00, 0xFFA500, 0xFFA500,
            0xFFFF00, 0xFFA500, 0xFFFF00, 0xFFFF00,
        )).also { s.state = it }
        val now = m.getNow()
        for (i in 0 until segLen) {
            var index = m.perlin8(floor(i * s.speed / 64.0).toLong(), floor(now * s.speed / 64.0 * segLen / 255.0).toLong())
            index = floor((255 - i * 256.0 / segLen) * index / (256 - s.intensity)).toInt()
            index = m.constrain(index, 0, 255)
            val palIdx = (index * 15) / 255
            val bri = min(255, vol * 2)
            s.setPixelColor(i, m.color_fade(fp.pal[m.constrain(palIdx, 0, 15)], bri))
        }
    }

    private fun noisemove(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val fadeoutDelay = (256 - s.speed) / 96
        if (fadeoutDelay <= 1 || (s.call % fadeoutDelay) == 0) s.fadeToBlackBy(4 + s.speed / 4)
        val numBins = m.map(s.intensity, 0, 255, 0, 16)
        val now = m.getNow()
        for (i in 0 until numBins) {
            val t = now * s.speed
            val locn = m.perlin16(t + i * 50000L, t)
            val pixloc = m.map(locn, 7500, 58000, 0, segLen - 1)
            val fftVal = s.getFFT(i % 16)
            s.setPixelColor(pixloc, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i * 64, false, false, 0), min(255, fftVal * 4)))
        }
    }

    @Suppress("unused") private fun keep(x: Double) = floorD(x)
}
