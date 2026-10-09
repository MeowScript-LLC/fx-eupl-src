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

import fx.JsSimMath.Companion.u32
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object JsFx1DB3 : JsFxBatch {
    override val ids: IntArray = intArrayOf(
        46, 47, 49, 50, 51, 55, 56, 57, 59, 62, 69, 75, 76, 78, 79, 83, 89, 95, 96, 99,
    )

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            46 -> gradientBase(s, false)
            47 -> gradientBase(s, true)
            49 -> fairy(s)
            50 -> twoDots(s)
            51 -> fairytwinkle(s)
            55 -> triWipe(s)
            56 -> tricolorFade(s)
            57 -> lightning(s)
            59 -> multiComet(s)
            62 -> oscillate(s)
            69 -> fillNoise(s)
            75 -> lake(s)
            76 -> meteor(s)
            78 -> railway(s)
            79 -> ripple(s)
            83 -> solidPattern(s)
            89 -> starburst(s)
            95 -> popcorn(s)
            96 -> drip(s)
            99 -> rippleRainbow(s)
            else -> return false
        }
        return true
    }

    private fun needData(s: JsSimSegment, n: Int): Boolean = s.data == null || s.dataLen < n

    private fun gradientBase(s: JsSimSegment, loading: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val counter = ((m.getNow() * ((s.speed shr 2) + 1)) and 0xFFFFL).toInt()
        var pp = (counter * segLen) shr 16
        if (s.call == 0) pp = 0
        val brd = s.intensity shr 1
        val p1 = pp - segLen
        val p2 = pp + segLen
        for (i in 0 until segLen) {
            var v: Int = if (loading) {
                kotlin.math.abs((if (i > pp) p2 else pp) - i)
            } else {
                min(kotlin.math.abs(pp - i), min(kotlin.math.abs(p1 - i), kotlin.math.abs(p2 - i)))
            }
            v = if (brd > v) (v * 255) / brd else 255
            s.setPixelColor(i, m.color_blend(s.SEGCOLOR(0), s.color_from_palette(i, true, false, 1), v and 0xFF))
        }
    }

    private class FairyState { val flasherBri = IntArray(11) }

    private fun fairy(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val flashersPerZone = 6
        val maxShimmer = 92
        var prng16 = 5100
        for (i in 0 until segLen) {
            prng16 = ((prng16 * 2053) + 1384) and 0xFFFF
            s.setPixelColor(i, s.color_from_palette(prng16 shr 8, false, false, 0))
        }
        if (s.intensity == 0) return
        val flasherDistance = (255 - s.intensity) / 28 + 1
        val numFlashers = segLen / flasherDistance + 1
        val dataSize = numFlashers * 4
        if (needData(s, dataSize)) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0)
        }
        val st = (s.state as? FairyState) ?: FairyState().also { s.state = it }
        val flasherBri = st.flasherBri
        val now16 = (m.getNow() and 0xFFFFL).toInt()
        var zones = numFlashers / flashersPerZone
        if (zones == 0) zones = 1
        var flashersInZone = numFlashers / zones
        for (z in 0 until zones) {
            var flasherBriSum = 0
            val firstFlasher = z * flashersInZone
            if (z == zones - 1) flashersInZone = numFlashers - (flashersInZone * (zones - 1))
            for (f in firstFlasher until firstFlasher + flashersInZone) {
                val off = f * 4
                var stateStart = (s.d8(off) or (s.d8(off + 1) shl 8)) and 0xFFFF
                var stateDur = s.d8(off + 2)
                var stateOn = s.d8(off + 3) != 0
                var stateTime = (now16 - stateStart) and 0xFFFF
                if (stateTime > stateDur * 10) {
                    stateOn = !stateOn
                    stateDur = if (stateOn) 12 + m.jsRandom8(12 + ((255 - s.speed) shr 2))
                    else 20 + m.jsRandom8(6 + ((255 - s.speed) shr 2))
                    stateStart = now16
                    if (stateTime < 255) {
                        stateStart = (stateStart - (255 - stateTime)) and 0xFFFF
                        stateDur += 26 - stateTime / 10
                        stateTime = 255 - stateTime
                    } else {
                        stateTime = 0
                    }
                }
                if (stateTime > 255) stateTime = 255
                flasherBri[f - firstFlasher] = (if (stateOn) stateTime else 255 - stateTime) and 0xFF
                flasherBriSum += flasherBri[f - firstFlasher]
                s.d8(off, stateStart and 0xFF)
                s.d8(off + 1, (stateStart shr 8) and 0xFF)
                s.d8(off + 2, stateDur and 0xFF)
                s.d8(off + 3, if (stateOn) 1 else 0)
            }
            val avgFlasherBri = flasherBriSum / flashersInZone
            val globalPeakBri = 255 - ((avgFlasherBri * maxShimmer) shr 8)
            for (f in firstFlasher until firstFlasher + flashersInZone) {
                val bri = flasherBri[f - firstFlasher] * globalPeakBri / 255
                prng16 = ((prng16 * 2053) + 1384) and 0xFFFF
                val flasherPos = f * flasherDistance
                s.setPixelColor(flasherPos, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(prng16 shr 8, false, false, 0), bri))
                var i = flasherPos + 1
                while (i < flasherPos + flasherDistance && i < segLen) {
                    prng16 = ((prng16 * 2053) + 1384) and 0xFFFF
                    s.setPixelColor(i, s.color_from_palette(prng16 shr 8, false, false, 0, globalPeakBri))
                    i++
                }
            }
        }
    }

    private fun twoDots(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val delay = 1 + (m.FRAMETIME shl 3) / segLen
        val div = m.map(s.speed, 0, 255, delay shl 4, delay)
        if (div <= 0) return
        val it = m.getNow() / div
        val offset = (it % segLen).toInt()
        var width = (segLen * (s.intensity + 1)) shr 9
        if (width == 0) width = 1
        if (!s.check2) s.fill(s.SEGCOLOR(2))
        val color1 = s.SEGCOLOR(0)
        val color2 = if (s.SEGCOLOR(1) == s.SEGCOLOR(2)) color1 else s.SEGCOLOR(1)
        for (i in 0 until width) {
            val indexR = (offset + i) % segLen
            val indexB = (offset + i + (segLen shr 1)) % segLen
            s.setPixelColor(indexR, color1)
            s.setPixelColor(indexB, color2)
        }
    }

    private fun fairytwinkle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val dataSize = segLen * 4
        if (needData(s, dataSize)) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0)
        }
        val now16 = (m.getNow() and 0xFFFFL).toInt()
        var prng16 = 5100
        val riseFallTime = 400 + (255 - s.speed) * 3
        val maxDur = riseFallTime / 100 + ((255 - s.intensity) shr 2) + 13 + ((255 - s.intensity) shr 1)
        for (f in 0 until segLen) {
            val off = f * 4
            var stateStart = (s.d8(off) or (s.d8(off + 1) shl 8)) and 0xFFFF
            var stateDur = s.d8(off + 2)
            var stateOn = s.d8(off + 3) != 0
            var stateTime = (now16 - stateStart) and 0xFFFF
            if (stateTime > stateDur * 100) {
                stateOn = !stateOn
                val init = stateDur == 0
                stateDur = if (stateOn) {
                    riseFallTime / 100 + ((255 - s.intensity) shr 2) + m.jsRandom8(12 + ((255 - s.intensity) shr 1)) + 1
                } else {
                    riseFallTime / 100 + m.jsRandom8(3 + ((255 - s.speed) shr 6)) + 1
                }
                stateStart = now16
                stateTime = 0
                if (init) {
                    stateStart = (stateStart - riseFallTime) and 0xFFFF
                    stateDur = riseFallTime / 100 + m.jsRandom8(12 + ((255 - s.intensity) shr 1)) + 5
                    stateTime = riseFallTime
                }
            }
            if (stateOn && stateDur > maxDur) stateDur = maxDur
            if (stateTime > riseFallTime) stateTime = riseFallTime
            val fadeprog = 255 - (stateTime * 255) / riseFallTime
            val flasherBri = if (stateOn) 255 - m.gamma8(fadeprog) else m.gamma8(fadeprog)
            val lastR = prng16
            var diff = 0
            while (diff < 0x4000) {
                prng16 = ((prng16 * 2053) + 1384) and 0xFFFF
                diff = if (prng16 > lastR) prng16 - lastR else lastR - prng16
            }
            s.setPixelColor(f, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(prng16 shr 8, false, false, 0), flasherBri))
            s.d8(off, stateStart and 0xFF)
            s.d8(off + 1, (stateStart shr 8) and 0xFF)
            s.d8(off + 2, stateDur and 0xFF)
            s.d8(off + 3, if (stateOn) 1 else 0)
        }
    }

    private fun triWipe(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val cycleTime = 1000 + (255 - s.speed) * 200
        val perc = m.getNow() % cycleTime
        val prog = u32(perc.toDouble() * 65535 / cycleTime)
        val ledIndex = ((prog * segLen * 3) and 0xFFFFFFFFL) ushr 16
        var ledOffset = ledIndex
        for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 2))
        if (ledIndex < segLen) {
            for (i in 0 until segLen) s.setPixelColor(i, if (i > ledOffset) s.SEGCOLOR(0) else s.SEGCOLOR(1))
        } else if (ledIndex < segLen * 2L) {
            ledOffset = ledIndex - segLen
            var i = ledOffset + 1
            while (i < segLen) { s.setPixelColor(i.toInt(), s.SEGCOLOR(1)); i++ }
        } else {
            ledOffset = ledIndex - segLen * 2L
            var i = 0L
            while (i <= ledOffset) { s.setPixelColor(i.toInt(), s.SEGCOLOR(0)); i++ }
        }
    }

    private fun tricolorFade(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val counter = ((m.getNow() * ((s.speed shr 3) + 1)) and 0xFFFFL).toInt()
        val prog = (counter * 768) shr 16
        val color1: Int; val color2: Int; val stage: Int
        if (prog < 256) { color1 = s.SEGCOLOR(0); color2 = s.SEGCOLOR(1); stage = 0 }
        else if (prog < 512) { color1 = s.SEGCOLOR(1); color2 = s.SEGCOLOR(2); stage = 1 }
        else { color1 = s.SEGCOLOR(2); color2 = s.SEGCOLOR(0); stage = 2 }
        val stp = prog and 0xFF
        for (i in 0 until segLen) {
            val color = when (stage) {
                2 -> m.color_blend(s.color_from_palette(i, true, false, 2), color2, stp)
                1 -> m.color_blend(color1, s.color_from_palette(i, true, false, 2), stp)
                else -> m.color_blend(color1, color2, stp)
            }
            s.setPixelColor(i, color)
        }
    }

    private fun lightning(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val ledstart = m.random16(segLen)
        val ledlen = 1 + m.random16(segLen - ledstart)
        var bri = 255 / m.random8(1, 3)
        if (s.aux1 == 0) {
            s.aux1 = m.random8(4, 4 + s.intensity / 20)
            s.aux1 *= 2
            bri = 52
            s.aux0 = 200
        }
        if (!s.check2) s.fill(s.SEGCOLOR(1))
        if (s.aux1 > 3 && (s.aux1 and 0x01) == 0) {
            for (i in ledstart until ledstart + ledlen) s.setPixelColor(i, s.color_from_palette(i, true, false, 0, bri))
            s.aux1--
            s.step = m.getNow()
        } else {
            if (m.getNow() - s.step > s.aux0) {
                s.aux1--
                if (s.aux1 < 2) s.aux1 = 0
                s.aux0 = 50 + m.random8(100)
                if (s.aux1 == 2) {
                    s.aux0 = m.random8(255 - s.speed) * 100
                }
                s.step = m.getNow()
            }
        }
    }

    private fun multiComet(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val maxComets = 8
        val cycleTime = 10 + (255 - s.speed)
        val it = m.getNow() / cycleTime
        if (s.step == it) return
        if (needData(s, maxComets * 2)) s.allocateData(maxComets * 2)
        s.fade_out((s.intensity shr 1) + 128)
        for (i in 0 until maxComets) {
            var cometPos = (s.d8(i * 2) or (s.d8(i * 2 + 1) shl 8)) and 0xFFFF
            if (cometPos < segLen) {
                val index = cometPos
                if (s.SEGCOLOR(2) != 0) {
                    s.setPixelColor(index, if (i % 2 != 0) s.color_from_palette(index, true, false, 0) else s.SEGCOLOR(2))
                } else {
                    s.setPixelColor(index, s.color_from_palette(index, true, false, 0))
                }
                cometPos++
            } else {
                if (m.jsRandom16(segLen) == 0) cometPos = 0
            }
            s.d8(i * 2, cometPos and 0xFF)
            s.d8(i * 2 + 1, (cometPos shr 8) and 0xFF)
        }
        s.step = it
    }

    private fun oscillate(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val numOscillators = 3
        if (s.call == 0 || s.data == null) {
            s.allocateData(numOscillators * 6)
            val p0 = segLen / 4
            s.d8(0, p0 and 0xFF); s.d8(1, (p0 shr 8) and 0xFF); s.d8(2, segLen / 8); s.d8(3, 1); s.d8(4, 1)
            val p1 = floor(segLen / 4.0 * 3).toInt()
            s.d8(6, p1 and 0xFF); s.d8(7, (p1 shr 8) and 0xFF); s.d8(8, segLen / 8); s.d8(9, 1); s.d8(10, 2)
            val p2 = floor(segLen / 4.0 * 2).toInt()
            s.d8(12, p2 and 0xFF); s.d8(13, (p2 shr 8) and 0xFF); s.d8(14, segLen / 8); s.d8(15, 255); s.d8(16, 1)
        }
        val cycleTime = 20 + 2 * (255 - s.speed)
        val it = m.getNow() / cycleTime
        for (i in 0 until numOscillators) {
            val off = i * 6
            var pos = s.d8(off) or (s.d8(off + 1) shl 8)
            var dir = if (s.d8(off + 3) == 255) -1 else 1
            var speed = s.d8(off + 4)
            if (it != s.step) pos += dir * speed
            s.d8(off + 2, segLen / (3 + s.intensity / 8))
            if (dir == -1 && (pos < 0 || pos > segLen * 2)) {
                pos = 0
                dir = 1
                speed = if (s.speed > 100) m.random8(2, 4) else m.random8(1, 3)
            }
            if (dir == 1 && pos >= segLen - 1) {
                pos = segLen - 1
                dir = -1
                speed = if (s.speed > 100) m.random8(2, 4) else m.random8(1, 3)
            }
            s.d8(off, pos and 0xFF); s.d8(off + 1, (pos shr 8) and 0xFF)
            s.d8(off + 3, if (dir == -1) 255 else 1)
            s.d8(off + 4, speed)
        }
        for (i in 0 until segLen) {
            var color = 0
            for (j in 0 until numOscillators) {
                val off = j * 6
                val pos = s.d8(off) or (s.d8(off + 1) shl 8)
                val size = s.d8(off + 2)
                if (i >= pos - size && i <= pos + size) {
                    color = if (color == 0) s.SEGCOLOR(j) else m.color_blend(color, s.SEGCOLOR(j), 128)
                }
            }
            s.setPixelColor(i, color)
        }
        s.step = it
    }

    private fun fillNoise(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) s.step = m.random16().toLong()
        for (i in 0 until segLen) {
            val index = m.perlin8((i * segLen).toLong(), s.step + i * segLen)
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0))
        }
        s.step += m.beatsin8_t(s.speed, 1, 6)
    }

    private fun lake(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val sp = s.speed / 10
        val wave1 = m.beatsin8_t(sp + 2, -64, 64)
        val wave2 = m.beatsin8_t(sp + 1, -64, 64)
        val wave3 = m.beatsin8_t(sp + 2, 0, 80)
        for (i in 0 until segLen) {
            val index = m.cos8_t((i * 15 + wave1) and 0xFF) / 2 + m.cubicwave8((i * 23 + wave2) and 0xFF) / 2
            val lum = if (index > wave3) index - wave3 else 0
            s.setPixelColor(i, s.color_from_palette(index, false, false, 0, lum))
        }
    }

    private fun meteor(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (needData(s, segLen)) s.allocateData(segLen)
        val meteorSmooth = s.check3
        val meteorSize = 1 + segLen / 20
        val meteorstart: Int = if (meteorSmooth) {
            m.map(((u32(s.step.toDouble()) ushr 6) and 0xFFL).toInt(), 0, 255, 0, segLen - 1)
        } else {
            val counter = (m.getNow() * ((s.speed shr 2) + 8)) and 0xFFFFFFFFL
            (((counter * segLen) and 0xFFFFFFFFL) ushr 16).toInt()
        }
        val maxVal = if (s.palette == 5 || !s.check1) 240 else 255
        for (i in 0 until segLen) {
            val col: Int
            if (m.random8() <= 255 - s.intensity) {
                if (meteorSmooth) {
                    if (s.d8(i) > 0) {
                        val change = s.d8(i) + 4 - m.random8(24)
                        s.d8(i, m.constrain(change, 0, maxVal))
                    }
                    col = if (s.check1) s.color_from_palette(i, true, false, 0, s.d8(i))
                    else s.color_from_palette(s.d8(i), false, true, 255)
                } else {
                    s.d8(i, m.scale8(s.d8(i), 128 + m.random8(127)))
                    var index = s.d8(i)
                    var idx = 255
                    var bri = if (s.palette == 35 || s.palette == 36) 255 else s.d8(i)
                    if (!s.check1) {
                        idx = 0
                        index = m.map(i, 0, segLen, 0, maxVal)
                        bri = s.d8(i)
                    }
                    col = s.color_from_palette(index, false, false, idx, bri)
                }
                s.setPixelColor(i, col)
            }
        }
        for (j in 0 until meteorSize) {
            val index = (meteorstart + j) % segLen
            if (meteorSmooth) {
                s.d8(index, maxVal)
                val col = if (s.check1) s.color_from_palette(index, true, false, 0, s.d8(index))
                else s.color_from_palette(s.d8(index), false, true, 255)
                s.setPixelColor(index, col)
            } else {
                var idx = 255
                s.d8(index, maxVal)
                var ii = maxVal
                if (!s.check1) {
                    ii = m.map(index, 0, segLen, 0, maxVal)
                    idx = 0
                }
                s.setPixelColor(index, s.color_from_palette(ii, false, false, idx, 255))
            }
        }
        s.step += (s.speed + 1).toLong()
    }

    private fun railway(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val dur = (256 - s.speed) * 40
        val rampdur = (dur * s.intensity) shr 8
        if (s.call == 0) { s.step = 0; s.aux0 = 0 }
        if (s.step > dur) {
            s.step = 0
            s.aux0 = if (s.aux0 != 0) 0 else 1
        }
        var pos = 255
        if (rampdur != 0) {
            val p0 = (s.step * 255) / rampdur
            if (p0 < 255) pos = p0.toInt()
        }
        if (s.aux0 != 0) pos = 255 - pos
        var i = 0
        while (i < segLen) {
            s.setPixelColor(i, s.color_from_palette(255 - pos, false, false, 255))
            if (i < segLen - 1) s.setPixelColor(i + 1, s.color_from_palette(pos, false, false, 255))
            i += 2
        }
        s.step += m.FRAMETIME.toLong()
    }

    private fun rippleBase(s: JsSimSegment, blurAmount: Int) {
        val m = s.m
        val is2D = s.is2D
        val segLen = s.vLength()
        val ibn = 5100
        val maxRipples = min(1 + (segLen shr 2), 100)
        val rippleSize = 4
        if (s.call == 0 || needData(s, maxRipples * rippleSize)) s.allocateData(maxRipples * rippleSize)
        val cols = if (is2D) s.vWidth() else 0
        val rows = if (is2D) s.vHeight() else 0
        for (i in 0 until maxRipples) {
            val off = i * rippleSize
            var ripplestate = s.d8(off)
            if (ripplestate != 0) {
                val rippledecay = (s.speed shr 4) + 1
                val rippleorigin = s.d8(off + 2) or (s.d8(off + 3) shl 8)
                val col = s.color_from_palette(s.d8(off + 1), false, false, 255)
                val propagation = (ripplestate / rippledecay - 1) * (s.speed + 1)
                var propI = propagation shr 8
                val propF = propagation and 0xFF
                val amp = if (ripplestate < 17) m.triwave8((ripplestate - 1) * 8) else m.map(ripplestate, 17, 255, 255, 2)
                if (is2D) {
                    propI = Math.floorDiv(propI, 2)
                    val cx = (rippleorigin shr 8) and 0xFF
                    val cy = rippleorigin and 0xFF
                    val mag = m.scale8(m.sin8((propF shr 2) and 0xFF), amp)
                    if (propI > 0) {
                        val edgeX = min(cx + propI, cols - 1)
                        val blendCol = m.color_blend(s.getPixelColorXY(edgeX, cy), col, mag)
                        s.drawCircle(cx, cy, propI, blendCol)
                    }
                } else {
                    val left = rippleorigin - propI - 1
                    val right = rippleorigin + propI + 2
                    for (v in 0 until 4) {
                        val mag = m.scale8(m.cubicwave8(((propF shr 2) + v * 64) and 0xFF), amp)
                        s.setPixelColor(left + v, m.color_blend(s.getPixelColor(left + v), col, mag))
                        s.setPixelColor(right - v, m.color_blend(s.getPixelColor(right - v), col, mag))
                    }
                }
                ripplestate += rippledecay
                s.d8(off, if (ripplestate > 254) 0 else ripplestate)
            } else {
                val spawnThreshold = if (is2D) (s.intensity shr 3) else s.intensity
                if (m.random16(ibn + 10000) <= spawnThreshold) {
                    s.d8(off, 1)
                    val pos = if (is2D) {
                        val x = m.random8(cols)
                        (x shl 8) or m.random8(rows)
                    } else {
                        m.random16(segLen)
                    }
                    s.d8(off + 2, pos and 0xFF)
                    s.d8(off + 3, (pos shr 8) and 0xFF)
                    s.d8(off + 1, m.random8())
                }
            }
        }
        if (blurAmount != 0) s.blur(blurAmount)
    }

    private fun ripple(s: JsSimSegment) {
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.custom1 != 0 || s.check2) s.fade_out(250) else s.fill(s.SEGCOLOR(1))
        rippleBase(s, s.custom1 shr 1)
    }

    private fun rippleRainbow(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.call == 0) {
            s.aux0 = m.random8()
            s.aux1 = m.random8()
        }
        if (s.aux0 == s.aux1) s.aux1 = m.random8()
        else if (s.aux1 > s.aux0) s.aux0++
        else s.aux0--
        s.fill(m.color_blend(s.color_wheel(s.aux0), 0, 235))
        rippleBase(s, 0)
    }

    private fun solidPattern(s: JsSimSegment) {
        val segLen = s.vLength()
        val lit = 1 + s.speed
        val unlit = 1 + s.intensity
        var drawingLit = true
        var cnt = 0
        for (i in 0 until segLen) {
            s.setPixelColor(i, if (drawingLit) s.color_from_palette(i, true, false, 0) else s.SEGCOLOR(1))
            cnt++
            if (cnt >= (if (drawingLit) lit else unlit)) {
                cnt = 0
                drawingLit = !drawingLit
            }
        }
    }

    private fun starSet(s: JsSimSegment, off: Int, color: Long, birth: Long, last: Long, vel: Double, pos: Int) {
        s.dU32(off, color)
        s.dU32(off + 4, birth)
        s.dU32(off + 8, last)
        s.dF32(off + 12, vel)
        s.dU16(off + 16, pos)
    }

    private fun starburst(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxFrag = 10
        val starSize = 60
        var numStars = 1 + (segLen shr 3)
        if (numStars > 42) numStars = 42
        val dataSize = starSize * numStars
        if (s.call == 0 || needData(s, dataSize)) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0)
        }
        val it = m.getNow()
        val particleIgnition = 250.0
        val particleFadeTime = 1500.0

        for (j in 0 until numStars) {
            val off = j * starSize
            if (m.random8(max(1, 144 - (s.speed shr 1))) == 0 && s.dU32(off + 4) == 0L) {
                val startPos = m.random16(segLen - 1)
                val multiplier = m.random8() / 255.0
                val starColor = s.color_wheel(m.random8())
                val vel = 375.0 * (m.random8() / 255.0) * multiplier
                starSet(s, off, starColor.toLong() and 0xFFFFFFFFL, it, it, vel, startPos)
                val num = m.random8(3, 6 + (s.intensity shr 5))
                for (i in 0 until maxFrag) s.dF32(off + 20 + i * 4, if (i < num) startPos.toDouble() else -1.0)
            }
        }

        if (!s.check2) s.fill(s.SEGCOLOR(1))

        for (j in 0 until numStars) {
            val off = j * starSize
            if (s.dU32(off + 4) != 0L) {
                val dt = (it - s.dU32(off + 8)) / 1000.0
                for (i in 0 until maxFrag) {
                    val frag = s.dF32(off + 20 + i * 4).toDouble()
                    if (frag > 0) {
                        val iVar = i shr 1
                        s.dF32(off + 20 + i * 4, frag + s.dF32(off + 12).toDouble() * dt * iVar / 3.0)
                    }
                }
                val v = s.dF32(off + 12).toDouble()
                val newVel = v - 3 * v * dt
                starSet(s, off, s.dU32(off), s.dU32(off + 4), it, newVel, s.dU16(off + 16))
            }
            val color = s.dU32(off).toInt()
            val cr = m.colorR(color); val cg = m.colorG(color); val cb = m.colorB(color)
            var fade = 0.0
            val age = it - s.dU32(off + 4)
            val c: Int
            if (age < particleIgnition) {
                c = m.color_blend(0xFFFFFF, m.colorPack(cr, cg, cb), floor(254.5 * (age / particleIgnition)).toInt())
            } else {
                if (age > particleIgnition + particleFadeTime) {
                    fade = 1.0
                    starSet(s, off, s.dU32(off), 0L, s.dU32(off + 8), s.dF32(off + 12).toDouble(), s.dU16(off + 16))
                    c = s.SEGCOLOR(1)
                } else {
                    val fadeAge = age - particleIgnition
                    fade = fadeAge / particleFadeTime
                    c = m.color_blend(m.colorPack(cr, cg, cb), s.SEGCOLOR(1), floor(254.5 * fade).toInt())
                }
            }
            val particleSize = (1.0 - fade) * 2.0
            for (index in 0 until maxFrag * 2) {
                val mirrored = index and 0x1
                val fi = index shr 1
                val frag = s.dF32(off + 20 + fi * 4).toDouble()
                if (frag > 0) {
                    var loc = frag
                    if (mirrored != 0) loc -= (loc - s.dU16(off + 16)) * 2
                    val pStart = max(0.0, floor(loc - particleSize))
                    var pEnd = floor(loc + particleSize)
                    if (pStart == pEnd) pEnd++
                    if (pEnd > segLen) pEnd = segLen.toDouble()
                    var p = pStart
                    while (p < pEnd) { s.setPixelColor(p, c); p++ }
                }
            }
        }
    }

    private fun popcorn(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxNumPopcorn = 21
        val sparkSize = 12
        if (s.call == 0 || needData(s, maxNumPopcorn * sparkSize)) s.allocateData(maxNumPopcorn * sparkSize)
        val hasCol2 = s.SEGCOLOR(2) != 0
        if (!s.check2) s.fill(if (hasCol2) 0 else s.SEGCOLOR(1))
        var gravity = -0.0001 - (s.speed / 200000.0)
        gravity *= segLen
        var numPopcorn = s.intensity * maxNumPopcorn / 255
        if (numPopcorn == 0) numPopcorn = 1
        for (i in 0 until numPopcorn) {
            val o = i * sparkSize
            var pos = s.dF32(o).toDouble()
            var vel = s.dF32(o + 4).toDouble()
            if (pos >= 0.0) {
                pos += vel
                vel += gravity
                s.dF32(o, pos)
                s.dF32(o + 4, vel)
            } else {
                if (m.random8() < 2) {
                    pos = 0.01
                    var peakHeight = 128 + m.random8(128)
                    peakHeight = (peakHeight * (segLen - 1)) shr 8
                    vel = kotlin.math.sqrt(-2.0 * gravity * peakHeight)
                    val newColIndex: Int
                    if (s.palette != 0) {
                        newColIndex = m.random8()
                    } else {
                        var col = m.random8(0, 3)
                        if (s.SEGCOLOR(2) == 0 || s.SEGCOLOR(col) == 0) col = 0
                        newColIndex = col
                    }
                    s.d8(o + 8, newColIndex)
                    s.dF32(o, pos)
                    s.dF32(o + 4, vel)
                }
            }
            pos = s.dF32(o).toDouble()
            if (pos >= 0.0) {
                val ci = s.d8(o + 8)
                var col = m.color_wheel(ci)
                if (s.palette == 0 && ci < 3) col = s.SEGCOLOR(ci)
                val ledIndex = floor(pos)
                if (ledIndex < segLen) s.setPixelColor(ledIndex, col)
            }
        }
    }

    private fun drip(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxNumDrops = 4
        val sparkSize = 16
        if (s.call == 0 || needData(s, maxNumDrops * sparkSize)) s.allocateData(maxNumDrops * sparkSize)
        if (!s.check2) s.fill(s.SEGCOLOR(1))
        val numDrops = 1 + (s.intensity shr 6)
        var gravity = -0.0005 - (s.speed / 50000.0)
        gravity *= max(1, segLen - 1)
        val sourcedrop = 12
        for (j in 0 until numDrops) {
            val o = j * sparkSize
            var pos = s.dF32(o).toDouble()
            var vel = s.dF32(o + 4).toDouble()
            var col = s.dF32(o + 8).toDouble()
            var colIndex = s.d8(o + 12)
            if (colIndex == 0) {
                pos = (segLen - 1).toDouble()
                vel = 0.0
                col = sourcedrop.toDouble()
                colIndex = 1
                s.dF32(o, pos); s.dF32(o + 4, vel); s.dF32(o + 8, col)
                s.d8(o + 12, colIndex)
            }
            s.setPixelColor(segLen - 1, m.color_blend(0, s.SEGCOLOR(0), min(255, sourcedrop)))
            if (colIndex == 1) {
                if (col > 255) col = 255.0
                s.setPixelColor(floor(pos), m.color_blend(0, s.SEGCOLOR(0), min(255.0, floor(col)).toInt()))
                col += m.map(s.speed, 0, 255, 1, 6)
                if (m.random8() < floor(col / 10)) {
                    colIndex = 2
                    col = 255.0
                }
                s.dF32(o + 8, col)
                s.d8(o + 12, colIndex)
            }
            if (colIndex > 1) {
                if (pos > 0) {
                    pos += vel
                    if (pos < 0) pos = 0.0
                    vel += gravity
                    for (ii in 1 until 7 - colIndex) {
                        val drawPos = m.constrain(floor(pos) + ii, 0.0, (segLen - 1).toDouble())
                        s.setPixelColor(drawPos, m.color_blend(0, s.SEGCOLOR(0), min(255.0, floor(col / ii)).toInt()))
                    }
                    if (colIndex > 2) {
                        s.setPixelColor(0, m.color_blend(s.SEGCOLOR(0), 0, min(255.0, floor(col)).toInt()))
                    }
                } else {
                    if (colIndex > 2) {
                        colIndex = 0
                        col = sourcedrop.toDouble()
                    } else {
                        if (colIndex == 2) {
                            vel = -vel / 4
                            pos += vel
                        }
                        col = (sourcedrop * 2).toDouble()
                        colIndex = 5
                    }
                }
                s.dF32(o, pos); s.dF32(o + 4, vel); s.dF32(o + 8, col)
                s.d8(o + 12, colIndex)
            }
        }
    }
}
