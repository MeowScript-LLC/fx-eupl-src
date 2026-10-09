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
import fx.JsSimMath.Companion.jsTrunc

internal object JsFx1DB1 : JsFxBatch {
    override val ids: IntArray = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 13, 14, 15, 16, 17, 23, 24, 26,
        36, 37, 38, 40, 41, 42, 43, 45, 52, 60, 63, 64, 65,
    )

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            0 -> solid(s)
            1 -> blink(s, s.SEGCOLOR(0), s.SEGCOLOR(1), strobe = false, doPalette = true)
            2 -> breath(s)
            3 -> colorWipe(s, rev = false, useRandomColors = false)
            4 -> colorWipe(s, rev = false, useRandomColors = true)
            5 -> randomColors(s)
            6 -> colorWipe(s, rev = true, useRandomColors = false)
            8 -> colorloop(s)
            9 -> rainbowCycle(s)
            10 -> scan(s, dual = false)
            11 -> scan(s, dual = true)
            13 -> running(s, s.SEGCOLOR(0), s.SEGCOLOR(1), theatre = true)
            14 -> running(s, s.color_wheel((s.step and 0xFF).toInt()), s.SEGCOLOR(1), theatre = true)
            15 -> runningBase(s, saw = false, dual = false)
            16 -> runningBase(s, saw = true, dual = false)
            17 -> twinkle(s)
            23 -> blink(s, s.SEGCOLOR(0), s.SEGCOLOR(1), strobe = true, doPalette = true)
            24 -> blink(s, s.color_wheel(s.call and 0xFF), s.SEGCOLOR(1), strobe = true, doPalette = false)
            26 -> blink(s, s.color_wheel(s.call and 0xFF), s.SEGCOLOR(1), strobe = false, doPalette = false)
            36 -> colorWipe(s, rev = true, useRandomColors = true)
            37 -> running(s, s.SEGCOLOR(0), s.SEGCOLOR(1), theatre = false)
            38 -> aurora(s)
            40 -> scanner(s)
            41 -> comet(s)
            42 -> fireworks(s)
            43 -> rain(s)
            45 -> fireFlicker(s)
            52 -> runningBase(s, saw = false, dual = true)
            60 -> {
                val prev = s.check1
                s.check1 = true
                try { scanner(s) } finally { s.check1 = prev }
            }
            63 -> pride2015(s)
            64 -> juggle(s)
            65 -> palette(s)
            else -> return false
        }
        return true
    }

    private fun solid(s: JsSimSegment) = s.fill(s.SEGCOLOR(0))

    fun blink(s: JsSimSegment, color1: Int, color2: Int, strobe: Boolean, doPalette: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        var cycleTime = (255 - s.speed) * 20
        var onTime = m.FRAMETIME
        if (!strobe) onTime += (cycleTime * s.intensity) shr 8
        cycleTime += m.FRAMETIME * 2
        val it = Math.floorDiv(m.getNow(), cycleTime.toLong())
        val rem = m.getNow() % cycleTime
        var on = false
        if (it != s.step || rem <= onTime) on = true
        s.step = it
        val color = if (on) color1 else color2
        if (color == color1 && doPalette) {
            for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        } else {
            s.fill(color)
        }
    }

    private fun breath(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var v = 0
        var counter = ((m.getNow() * ((s.speed shr 3) + 10)) and 0xFFFF).toInt()
        counter = (counter shr 2) + (counter shr 4)
        if (counter < 16384) {
            if (counter > 8192) counter = 8192 - (counter - 8192)
            v = Math.floorDiv(m.sin16(counter), 103)
        }
        val lum = (30 + v) and 0xFF
        for (i in 0 until segLen) {
            s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i, true, false, 0), lum))
        }
    }

    private fun colorWipe(s: JsSimSegment, rev: Boolean, useRandomColors: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val cycleTime = 750 + (255 - s.speed) * 150
        val perc = m.getNow() % cycleTime
        var prog = ((perc * 65535) / cycleTime).toInt()
        val back = prog > 32767
        if (back) {
            prog -= 32767
            if (s.step == 0L) s.step = 1
        } else {
            if (s.step == 2L) s.step = 3
        }

        if (useRandomColors) {
            if (s.call == 0) {
                s.aux0 = m.random8()
                s.step = 3
            }
            if (s.step == 1L) {
                s.aux1 = m.get_random_wheel_index(s.aux0)
                s.step = 2
            }
            if (s.step == 3L) {
                s.aux0 = m.get_random_wheel_index(s.aux1)
                s.step = 0
            }
        }

        val ledIndex = (prog * segLen) shr 15
        var rem = ((prog * segLen) * 2) and 0xFFFF
        rem /= (s.intensity + 1)
        if (rem > 255) rem = 255

        val col1 = if (useRandomColors) s.color_wheel(s.aux1) else s.SEGCOLOR(1)
        for (i in 0 until segLen) {
            val index = if (rev && back) segLen - 1 - i else i
            val col0 = if (useRandomColors) s.color_wheel(s.aux0) else s.color_from_palette(index, true, false, 0)
            if (i < ledIndex) {
                s.setPixelColor(index, if (back) col1 else col0)
            } else {
                s.setPixelColor(index, if (back) col0 else col1)
                if (i == ledIndex) s.setPixelColor(index, m.color_blend(if (back) col0 else col1, if (back) col1 else col0, rem))
            }
        }
    }

    private fun randomColors(s: JsSimSegment) {
        val m = s.m
        val cycleTime = 200 + (255 - s.speed) * 50
        val it = m.getNow() / cycleTime
        val rem = m.getNow() % cycleTime
        val fadedur = (cycleTime * s.intensity) shr 8
        var fade = 255
        if (fadedur != 0) {
            fade = ((rem * 255) / fadedur).toInt().let { if (it > 255) 255 else it }
        }
        if (s.call == 0) {
            s.aux0 = m.random8()
            s.step = 2
        }
        if (it != s.step) {
            s.aux1 = s.aux0
            s.aux0 = m.get_random_wheel_index(s.aux0)
            s.step = it
        }
        s.fill(m.color_blend(s.color_wheel(s.aux1), s.color_wheel(s.aux0), fade))
    }

    private fun colorloop(s: JsSimSegment) {
        val m = s.m
        var counter = ((m.getNow() * ((s.speed shr 2) + 2)) and 0xFFFF).toInt()
        counter = counter shr 8
        if (s.intensity < 128) {
            s.fill(m.color_blend(s.color_wheel(counter), 0xFFFFFF, 128 - s.intensity))
        } else {
            s.fill(s.color_wheel(counter))
        }
    }

    private fun rainbowCycle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var counter = ((m.getNow() * ((s.speed shr 2) + 2)) and 0xFFFF).toInt()
        counter = counter shr 8
        for (i in 0 until segLen) {
            val index = ((i * (16 shl (s.intensity / 29))) / segLen + counter) and 0xFF
            s.setPixelColor(i, s.color_wheel(index))
        }
    }

    private fun scan(s: JsSimSegment, dual: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val cycleTime = 750 + (255 - s.speed) * 150
        val perc = m.getNow() % cycleTime
        val prog = ((perc * 65535) / cycleTime).toInt()
        val size = 1 + ((s.intensity * segLen) shr 9)
        val ledIndex = (prog * ((segLen * 2) - size * 2)) shr 16

        if (!s.check2) s.fill(s.SEGCOLOR(1))

        var ledOffset = ledIndex - (segLen - size)
        ledOffset = kotlin.math.abs(ledOffset)

        if (dual) {
            for (j in ledOffset until ledOffset + size) {
                val i2 = segLen - 1 - j
                s.setPixelColor(i2, s.color_from_palette(i2, true, false, if (s.SEGCOLOR(2) != 0) 2 else 0))
            }
        }
        for (j in ledOffset until ledOffset + size) {
            s.setPixelColor(j, s.color_from_palette(j, true, false, 0))
        }
    }

    private fun running(s: JsSimSegment, color1: Int, color2: Int, theatre: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        val width = (if (theatre) 3 else 1) + (s.intensity shr 4)
        val cycleTime = 50 + (255 - s.speed)
        val it = m.getNow() / cycleTime
        val usePalette = color1 == s.SEGCOLOR(0)

        for (i in 0 until segLen) {
            var col = color2
            var c1 = color1
            if (usePalette) c1 = s.color_from_palette(i, true, false, 0)
            if (theatre) {
                if ((i % width) == s.aux0) col = c1
            } else {
                val pos = i % (width shl 1)
                if ((pos < s.aux0 - width) || ((pos >= s.aux0) && (pos < s.aux0 + width))) col = c1
            }
            s.setPixelColor(i, col)
        }

        if (it != s.step) {
            s.aux0 = (s.aux0 + 1) % (if (theatre) width else (width shl 1))
            s.step = it
        }
    }

    private fun sinGap(m: JsSimMath, v: Int): Int {
        if ((v and 0x100) != 0) return 0
        return m.sin8((v + 192) and 0xFF)
    }

    private fun runningBase(s: JsSimSegment, saw: Boolean, dual: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        val xScale = s.intensity shr 2
        val counter = (m.getNow() * s.speed).toInt() shr 9

        for (i in 0 until segLen) {
            var a = i * xScale - counter
            if (saw) {
                a = a and 0xFF
                a = if (a < 16) 192 + a * 8 else m.map(a, 16, 255, 64, 192)
                a = 255 - a
            }
            val sv = if (dual) sinGap(m, a) else m.sin8(a and 0xFF)
            var ca = m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i, true, false, 0), sv)
            if (dual) {
                val b = (segLen - 1 - i) * xScale - counter
                val t = sinGap(m, b)
                val cb = m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i, true, false, 2), t)
                ca = m.color_blend(ca, cb, 127)
            }
            s.setPixelColor(i, ca)
        }
    }

    private fun twinkle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        s.fade_out(224)
        val cycleTime = 20 + (255 - s.speed) * 5
        val it = m.getNow() / cycleTime
        if (it != s.step) {
            val maxOn = m.map(s.intensity, 0, 255, 1, segLen)
            if (s.aux0 >= maxOn) {
                s.aux0 = 0
                s.aux1 = m.jsRandom16()
            }
            s.aux0++
            s.step = it
        }
        var prng16 = s.aux1
        for (i in 0 until s.aux0) {
            prng16 = ((prng16 * 2053) + 13849) and 0xFFFF
            val p = segLen * prng16
            val j = p shr 16
            s.setPixelColor(j, s.color_from_palette(j, true, false, 0))
        }
    }

    private class AuroraState(n: Int) {
        val count = n
        val alive = BooleanArray(n)
        val age = IntArray(n)
        val ttl = IntArray(n)
        val center = LongArray(n)
        val width = IntArray(n) { 1 }
        val basealpha = IntArray(n)
        val speedFactor = IntArray(n)
        val goingleft = BooleanArray(n)
        val basecolor = IntArray(n)
        val ageFactor = IntArray(n)
        val waveStart = IntArray(n)
        val waveEnd = IntArray(n)
        fun clear() {
            java.util.Arrays.fill(alive, false); java.util.Arrays.fill(age, 0); java.util.Arrays.fill(ttl, 0)
            java.util.Arrays.fill(center, 0L); java.util.Arrays.fill(width, 1); java.util.Arrays.fill(basealpha, 0)
            java.util.Arrays.fill(speedFactor, 0); java.util.Arrays.fill(goingleft, false); java.util.Arrays.fill(basecolor, 0)
            java.util.Arrays.fill(ageFactor, 0); java.util.Arrays.fill(waveStart, 0); java.util.Arrays.fill(waveEnd, 0)
        }
    }

    private fun aurora(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val wMaxCount = 20
        val wMaxSpeed = 6
        val wWidthFactor = 6
        val awShift = 16
        val awScale = 1 shl awShift

        val waveCount = m.map(s.intensity, 0, 255, 2, wMaxCount)
        s.aux1 = waveCount

        var st = s.state as? AuroraState
        if (s.call == 0 || st == null || st.count != waveCount) {
            if (st == null || st.count != waveCount) { st = AuroraState(waveCount); s.state = st } else st.clear()
        }
        val w = st

        for (i in 0 until waveCount) {
            val step = w.speedFactor[i].toLong() * s.speed
            w.center[i] += if (w.goingleft[i]) -step else step
            w.age[i]++
            if (w.age[i] > w.ttl[i]) {
                w.alive[i] = false
            } else {
                val widthScaled = (w.width[i] shl awShift).toLong()
                val segScaled = (segLen shl awShift).toLong()
                if (w.goingleft[i]) {
                    if (w.center[i] < -widthScaled) w.alive[i] = false
                } else {
                    if (w.center[i] > segScaled + widthScaled) w.alive[i] = false
                }
            }
            if (!w.alive[i]) {
                w.ttl[i] = 500 + m.jsRandom16(1001)
                val palIdx = m.jsRandom8()
                w.basecolor[i] = s.color_from_palette(palIdx, false, false, m.jsRandom8(0, 3))
                w.basealpha[i] = (m.jsRandom8(60, 100).toLong() * awScale / 100).toInt()
                w.age[i] = 0
                w.width[i] = m.jsRandom16(segLen / 20, kotlin.math.max(1, segLen / wWidthFactor)) + 1
                w.center[i] = ((m.jsRandom8(101).toLong() * awScale) / 100) * segLen
                w.goingleft[i] = (m.jsRandom8() and 1) == 1
                w.speedFactor[i] = ((m.jsRandom8(10, 31).toLong() * wMaxSpeed * awScale) / (100 * 255)).toInt()
                w.alive[i] = true
            }
            val halfTtl = w.ttl[i] shr 1
            if (halfTtl == 0) { w.ageFactor[i] = 0; continue }
            w.ageFactor[i] = if (w.age[i] < halfTtl) {
                ((w.age[i].toLong() * awScale) / halfTtl).toInt()
            } else {
                Math.floorDiv((w.ttl[i] - w.age[i]).toLong() * awScale, halfTtl.toLong()).toInt()
            }
            if (w.ageFactor[i] >= awScale) w.ageFactor[i] = awScale - 1
            val centerLed = w.center[i].toInt() shr awShift
            w.waveStart[i] = centerLed - w.width[i]
            w.waveEnd[i] = centerLed + w.width[i]
        }

        var backlight = 0
        if (s.SEGCOLOR(0) != 0) backlight++
        if (s.SEGCOLOR(1) != 0) backlight++
        if (s.SEGCOLOR(2) != 0) backlight++
        backlight = m.gamma8inv(backlight)

        for (i in 0 until segLen) {
            var mr = backlight; var mg = backlight; var mb = backlight
            for (j in 0 until waveCount) {
                val rgb = auroraColor(m, w, j, i, awShift, awScale)
                mr = kotlin.math.min(255, mr + m.colorR(rgb))
                mg = kotlin.math.min(255, mg + m.colorG(rgb))
                mb = kotlin.math.min(255, mb + m.colorB(rgb))
            }
            s.setPixelColor(i, m.colorPack(mr, mg, mb))
        }
    }

    private fun auroraColor(m: JsSimMath, w: AuroraState, j: Int, ledIndex: Int, awShift: Int, awScale: Int): Int {
        if (ledIndex < w.waveStart[j] || ledIndex > w.waveEnd[j]) return 0
        var offset = (ledIndex.toLong() shl awShift) - w.center[j]
        if (offset < 0) offset = -offset
        val offsetFactor = offset / w.width[j]
        if (offsetFactor > awScale) return 0
        var bf = awScale - offsetFactor
        bf = ((bf * w.ageFactor[j]) and 0xFFFFFFFFL) ushr awShift
        bf = ((bf * w.basealpha[j]) and 0xFFFFFFFFL) ushr awShift
        val c = w.basecolor[j]
        return m.colorPack(
            ((m.colorR(c) * bf).toInt()) shr awShift,
            ((m.colorG(c) * bf).toInt()) shr awShift,
            ((m.colorB(c) * bf).toInt()) shr awShift,
        )
    }

    private class ScannerState(val fb: DoubleArray)

    private fun scanner(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return

        val speed = m.FRAMETIME * m.map(s.speed, 0, 255, 96, 2)
        val pixelsPerCall = Math.floorDiv(segLen, speed)

        val frames = if (pixelsPerCall > 0) 1 else Math.floorDiv(speed, segLen)
        val fadesPerPixel = frames + 1
        val trailFrac = s.intensity / 255.0
        val targetPixels = kotlin.math.max(3.0, trailFrac * segLen * 3.0)
        val nOff = targetPixels * fadesPerPixel
        val decayScale = StrictMath.exp(-5.541 / nOff)

        val buf = s.pixels
        val bufLen = buf.size
        var st = s.state as? ScannerState
        if (st == null || st.fb.size != bufLen * 3) {
            st = ScannerState(DoubleArray(bufLen * 3))
            s.state = st
            for (i in 0 until bufLen) {
                val c = buf[i]
                st.fb[i * 3] = m.colorR(c).toDouble()
                st.fb[i * 3 + 1] = m.colorG(c).toDouble()
                st.fb[i * 3 + 2] = m.colorB(c).toDouble()
            }
        }
        val fb = st.fb

        val bg = s.colors[1]
        val bgR = m.colorR(bg).toDouble()
        val bgG = m.colorG(bg).toDouble()
        val bgB = m.colorB(bg).toDouble()

        for (i in 0 until bufLen) {
            val fi = i * 3
            var r = fb[fi]; var g = fb[fi + 1]; var b = fb[fi + 2]
            if (r == bgR && g == bgG && b == bgB) continue
            r = bgR + (r - bgR) * decayScale
            g = bgG + (g - bgG) * decayScale
            b = bgB + (b - bgB) * decayScale
            if (kotlin.math.abs(r - bgR) < 0.5 && kotlin.math.abs(g - bgG) < 0.5 && kotlin.math.abs(b - bgB) < 0.5) {
                r = bgR; g = bgG; b = bgB
            }
            fb[fi] = r; fb[fi + 1] = g; fb[fi + 2] = b
            buf[i] = m.colorPack(
                jsRound(r).coerceIn(0.0, 255.0).toInt(),
                jsRound(g).coerceIn(0.0, 255.0).toInt(),
                jsRound(b).coerceIn(0.0, 255.0).toInt(),
            )
        }

        if (s.step > m.getNow()) return

        var index = s.aux1 + pixelsPerCall
        if (pixelsPerCall == 0) {
            if (s.step < frames) { s.step++; return }
            s.step = 0
            index = s.aux1 + 1
        }

        if (index > segLen) {
            s.aux0 = if (s.aux0 != 0) 0 else 1
            s.aux1 = 0
            if (s.aux0 != 0 || s.check2) s.step = m.getNow() + s.custom1 * 25L
            else s.step = 0
        } else {
            for (i in s.aux1 until index) {
                val j = if (s.aux0 != 0) i else segLen - 1 - i
                val c = s.color_from_palette(j, true, false, 0)
                s.setPixelColor(j, c)
                if (j >= 0 && j * 3 + 2 < fb.size) {
                    fb[j * 3] = m.colorR(c).toDouble()
                    fb[j * 3 + 1] = m.colorG(c).toDouble()
                    fb[j * 3 + 2] = m.colorB(c).toDouble()
                }
                if (s.check1) {
                    val mirror = segLen - 1 - j
                    val mc = if (s.SEGCOLOR(2) != 0) s.SEGCOLOR(2) else c
                    s.setPixelColor(mirror, mc)
                    if (mirror >= 0 && mirror * 3 + 2 < fb.size) {
                        fb[mirror * 3] = m.colorR(mc).toDouble()
                        fb[mirror * 3 + 1] = m.colorG(mc).toDouble()
                        fb[mirror * 3 + 2] = m.colorB(mc).toDouble()
                    }
                }
            }
            s.aux1 = index
        }
    }

    private fun comet(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val counter = ((m.getNow() * ((s.speed shr 2) + 1)) and 0xFFFF).toInt()
        val index = (counter * segLen) shr 16
        if (s.call == 0) s.aux0 = index

        s.fade_out(s.intensity)

        s.setPixelColor(index, s.color_from_palette(index, true, false, 0))
        if (index > s.aux0) {
            for (i in s.aux0 until index) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        } else if (index < s.aux0 && index < 10) {
            for (i in 0 until index) s.setPixelColor(i, s.color_from_palette(i, true, false, 0))
        }
        s.aux0 = index
    }

    private fun fireworksSparks(s: JsSimSegment, is2D: Boolean, width: Int, height: Int) {
        val m = s.m
        if (s.call == 0) {
            s.aux0 = 0xFFFF
            s.aux1 = 0xFFFF
        }
        s.fade_out(128)

        if (s.step == 0L) {
            val area = width * height
            val valid1 = s.aux0 < area
            val valid2 = s.aux1 < area
            var sv1 = 0; var sv2 = 0
            if (valid1) {
                sv1 = if (is2D) s.getPixelColorXY(Math.floorMod(s.aux0, width), Math.floorDiv(s.aux0, width).toInt())
                else s.getPixelColor(s.aux0)
            }
            if (valid2) {
                sv2 = if (is2D) s.getPixelColorXY(Math.floorMod(s.aux1, width), Math.floorDiv(s.aux1, width).toInt())
                else s.getPixelColor(s.aux1)
            }
            s.blur(16)
            if (valid1) {
                if (is2D) s.setPixelColorXY(jsRemI(s.aux0, width), Math.floorDiv(s.aux0, width), sv1)
                else s.setPixelColor(s.aux0, sv1)
            }
            if (valid2) {
                if (is2D) s.setPixelColorXY(jsRemI(s.aux1, width), Math.floorDiv(s.aux1, width), sv2)
                else s.setPixelColor(s.aux1, sv2)
            }
        }

        val numSparks = kotlin.math.max(1, width / 20)
        for (i in 0 until numSparks) {
            if (m.jsRandom8(129 - (s.intensity shr 1)) == 0) {
                val index = m.jsRandom16(width * height)
                val x = index % width
                val y = index / width
                val col = s.color_from_palette(m.jsRandom8(), false, false, 0)
                if (is2D) s.setPixelColorXY(x, y, col) else s.setPixelColor(index, col)
                s.aux1 = s.aux0
                s.aux0 = index
            }
        }
    }

    private fun jsRemI(a: Int, b: Int): Int = a % b

    private fun fireworks(s: JsSimSegment) {
        val segLen = s.vLength()
        if (segLen <= 1) return
        val is2D = s.is2D
        val width = if (is2D) s.vWidth() else segLen
        val height = if (is2D) s.vHeight() else 1
        fireworksSparks(s, is2D, width, height)
    }

    private fun rain(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val is2D = s.is2D
        val width = if (is2D) s.vWidth() else segLen
        val height = if (is2D) s.vHeight() else 1

        s.step = s.step + m.FRAMETIME
        if (s.call != 0 && s.step > m.SPEED_FORMULA_L(s.speed, segLen)) {
            s.step = 1
            if (is2D) {
                s.move(6, 1, true)
                s.aux0 = (s.aux0 % width) + (Math.floorDiv(s.aux0, width) + 1) * width
                s.aux1 = (s.aux1 % width) + (Math.floorDiv(s.aux1, width) + 1) * width
            } else {
                val ctemp = s.getPixelColor(0)
                for (i in 0 until segLen - 1) s.setPixelColor(i, s.getPixelColor(i + 1))
                s.setPixelColor(segLen - 1, ctemp)
                s.aux0 = (s.aux0 + 1) and 0xFFFF
                s.aux1 = (s.aux1 + 1) and 0xFFFF
            }
            if (s.aux0 == 0) s.aux0 = 0xFFFF
            if (s.aux1 == 0) s.aux1 = 0xFFFF
            if (s.aux0 >= width * height) s.aux0 = 0
            if (s.aux1 >= width * height) s.aux1 = 0
        }
        fireworksSparks(s, is2D, width, height)
    }

    private fun fireFlicker(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val cycleTime = 40 + (255 - s.speed)
        val it = m.getNow() / cycleTime
        if (s.step == it) return

        val c0 = s.SEGCOLOR(0)
        val w = m.colorW(c0); val r = m.colorR(c0); val g = m.colorG(c0); val b = m.colorB(c0)
        var lum = if (s.palette == 0) kotlin.math.max(w, kotlin.math.max(r, kotlin.math.max(g, b))) else 255
        lum /= ((256 - s.intensity) / 16 + 1)
        for (i in 0 until segLen) {
            val flicker = m.jsRandom8(lum)
            if (s.palette == 0) {
                s.setPixelColor(i, m.colorPack(
                    kotlin.math.max(r - flicker, 0), kotlin.math.max(g - flicker, 0),
                    kotlin.math.max(b - flicker, 0), kotlin.math.max(w - flicker, 0),
                ))
            } else {
                s.setPixelColor(i, s.color_from_palette(i, true, false, 0, 255 - flicker))
            }
        }
        s.step = it
    }

    private fun pride2015(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val duration = 10 + s.speed
        var sPseudotime = s.step
        var sHue16 = s.aux0

        val sat8 = m.beatsin88(87, 220, 250)
        val brightdepth = m.beatsin88(341, 96, 224)
        val brightnessthetainc16 = m.beatsin88(203, 25 * 256, 40 * 256)
        val msmultiplier = m.beatsin88(147, 23, 60)
        val hueinc16 = m.beatsin88(113, 1, 3000)

        sPseudotime = (sPseudotime + duration.toLong() * msmultiplier) and 0xFFFFFFFFL
        sHue16 = (sHue16 + duration * m.beatsin88(400, 5, 9)) and 0xFFFF
        var brightnesstheta16 = (sPseudotime and 0xFFFFFFFFL)
        var hue16 = sHue16

        for (i in 0 until segLen) {
            hue16 = (hue16 + hueinc16) and 0xFFFF
            val hue8 = (hue16 shr 8) and 0xFF
            brightnesstheta16 = (brightnesstheta16 + brightnessthetainc16) and 0xFFFF
            val b16 = (m.sin16(brightnesstheta16.toInt()) + 32768) and 0xFFFF
            val bri16 = (b16.toLong() * b16 / 65536).toInt()
            var bri8 = (bri16.toLong() * brightdepth / 65536).toInt()
            bri8 += (255 - brightdepth)
            bri8 = kotlin.math.min(255, kotlin.math.max(0, bri8))
            val newcolor = m.hsv2rgb(hue8, sat8, bri8)
            val gammaColor = m.gamma32inv(newcolor)
            s.blendPixelColor(i, gammaColor, 64)
        }
        s.step = sPseudotime
        s.aux0 = sHue16
    }

    private fun juggle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fadeToBlackBy(192 - (3 * s.intensity) / 4)
        var dothue = 0
        for (i in 0 until 8) {
            val index = m.beatsin88((16 + s.speed) * (i + 7), 0, segLen - 1)
            val existing = s.getPixelColor(index)
            val col = if (s.palette == 0) m.hsv2rgb(dothue, 220, 255)
            else s.color_from_palette(dothue, false, false, 0, 255)
            val r = kotlin.math.min(255, m.colorR(existing) or m.colorR(col))
            val g = kotlin.math.min(255, m.colorG(existing) or m.colorG(col))
            val b = kotlin.math.min(255, m.colorB(existing) or m.colorB(col))
            s.setPixelColor(index, m.colorPack(r, g, b))
            dothue += 32
        }
    }

    private fun palette(s: JsSimSegment) {
        val m = s.m
        val is2D = s.is2D
        val cols = if (is2D) s.vWidth() else s.vLength()
        val rows = if (is2D) s.vHeight() else 1
        val inputShift = s.speed
        val inputSize = s.intensity
        val inputRotation = s.custom1
        val inputAnimateShift = s.check1
        val inputAnimateRotation = s.check2
        val inputAssumeSquare = s.check3

        val mPi256 = Math.PI / 256.0
        val mTwoPi = Math.PI * 2.0
        val theta: Double = if (!inputAnimateRotation) {
            (inputRotation + 128) * mPi256
        } else {
            ((m.getNow() * ((inputRotation shr 4) + 1)) and 0xFFFF).toDouble() * (mTwoPi / 0xFFFF)
        }
        val sinTheta = StrictMath.sin(theta)
        val cosTheta = StrictMath.cos(theta)

        val maxX = kotlin.math.max(1, cols - 1).toDouble()
        val maxY = kotlin.math.max(1, rows - 1).toDouble()
        val maxXIn = if (inputAssumeSquare) maxX else 1.0
        val maxYIn = if (inputAssumeSquare) maxY else 1.0
        val maxXOut = if (!inputAssumeSquare) maxX else 1.0
        val maxYOut = if (!inputAssumeSquare) maxY else 1.0
        val centerX = maxXOut / 2.0
        val centerY = maxYOut / 2.0

        val scale = kotlin.math.abs(sinTheta) + (kotlin.math.abs(cosTheta) * maxYOut / maxXOut)

        val paletteOffset = if (!inputAnimateShift) inputShift
        else (((m.getNow() * ((inputShift shr 3) + 1)) and 0xFFFF).toInt() shr 8)

        val yTo = if (is2D) maxY.toInt() else 0
        for (y in 0..yTo) {
            val ytCosTheta = (cosTheta * (y - centerY * maxYIn)) / (maxYIn * scale)
            for (x in 0 until cols) {
                val xtSinTheta = (sinTheta * (x - centerX * maxXIn)) / (maxXIn * scale)
                val sourceX = xtSinTheta + ytCosTheta + centerX
                var colorIndex = jsTrunc((kotlin.math.min(kotlin.math.max(sourceX, 0.0), maxXOut) * 255) / maxXOut).toInt()
                colorIndex = if (inputSize <= 128) {
                    Math.floorDiv(colorIndex * inputSize, 128)
                } else {
                    Math.floorDiv((inputSize - 112) * colorIndex, 16)
                }
                colorIndex -= paletteOffset
                if (is2D) s.setPixelColorXY(x, y, s.color_wheel(colorIndex and 0xFF))
                else s.setPixelColor(x, s.color_wheel(colorIndex and 0xFF))
            }
        }
    }

    @Suppress("unused") private fun keepImport(x: Double) = i32(x)
}
