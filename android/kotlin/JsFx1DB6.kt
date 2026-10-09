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
import fx.JsSimMath.Companion.u32

internal object JsFx1DB6 : JsFxBatch {
    override val ids: IntArray = intArrayOf(77, 81, 82, 84, 90, 93, 94, 103, 105, 106, 109, 112, 117, 161)

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            77 -> copySegment(s)
            81 -> twinklecat(s)
            82 -> halloweenEyes(s)
            84 -> solidPatternTri(s)
            90 -> fireworks1D(s)
            93 -> sinelonDual(s)
            94 -> sinelonRainbow(s)
            103 -> solidGlitter(s)
            105 -> phased(s, noise = false)
            106 -> twinkleup(s)
            109 -> phased(s, noise = true)
            112 -> dancingShadows(s)
            117 -> dynamicSmooth(s)
            161 -> shimmer(s)
            else -> return false
        }
        return true
    }

    private fun copySegment(s: JsSimSegment) {
        val segLen = s.vLength()
        for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette((i * 255) / segLen, false))
    }

    private fun twinklecat(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var prng = 11337
        if (s.speed > 100) s.aux0 = 3 + ((255 - s.speed) shr 3) else s.aux0 = 22 + ((100 - s.speed) shr 1)

        var bg = s.SEGCOLOR(1)
        val bglight = m.getAverageLight(bg)
        bg = if (bglight > 64) m.color_fade(bg, 16, true)
        else if (bglight > 16) m.color_fade(bg, 64, true)
        else m.color_fade(bg, 86, true)
        bg = m.gamma32inv(bg)
        val backgroundBrightness = m.getAverageLight(bg)
        val now = m.getNow()
        val divisor = kotlin.math.max(1, s.aux0).toLong()

        for (i in 0 until segLen) {
            prng = ((prng * 2053) + 1384) and 0xFFFF
            val myclockoffset16 = prng
            prng = ((prng * 2053) + 1384) and 0xFFFF
            val mult = ((((prng and 0xFF) shr 4) + (prng and 0x0F)) and 0x0F) + 0x08
            val prod = (now * mult).toInt()
            val myclock30 = ((prod shr 3).toLong() + myclockoffset16) and 0xFFFFFFFFL
            val myunique8 = (prng shr 8) and 0xFF

            val ticks = myclock30 / divisor
            val fastcycle8 = (ticks and 0xFF).toInt()
            var slowcycle16 = ((ticks.toInt() shr 8) + myunique8) and 0xFFFF
            slowcycle16 = (slowcycle16 + m.sin8(slowcycle16 and 0xFF)) and 0xFFFF
            slowcycle16 = ((slowcycle16 * 2053) + 1384) and 0xFFFF
            val slowcycle8 = ((slowcycle16 and 0xFF) + ((slowcycle16 shr 8) and 0xFF)) and 0xFF

            val twinkleDensity = (s.intensity shr 5) + 1
            var bright = 0
            if (((slowcycle8 and 0x0E) / 2) < twinkleDensity) {
                val ph = fastcycle8
                bright = 255 - ph
                if (s.check2) bright = ph
            }
            val hue = (slowcycle8 - myunique8) and 0xFF
            var c: Int
            if (bright > 0) {
                c = s.ColorFromPalette(hue, bright, 0)
                if (!s.check1) {
                    if (fastcycle8 >= 128) {
                        val cooling = (fastcycle8 - 128) shr 4
                        val cr = m.colorR(c); var cg = m.colorG(c); var cb = m.colorB(c)
                        cg = m.qsub8(cg, cooling)
                        cb = m.qsub8(cb, cooling * 2)
                        c = m.colorPack(cr, cg, cb)
                    }
                }
            } else {
                c = 0
            }
            val cbright = m.getAverageLight(c)
            val deltabright = cbright - backgroundBrightness
            if (deltabright >= 32 || bg == 0) s.setPixelColor(i, c)
            else if (deltabright > 0) s.setPixelColor(i, m.color_blend(bg, c, kotlin.math.min(255, deltabright * 8)))
            else s.setPixelColor(i, bg)
        }
    }

    private fun halloweenEyes(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val is2D = s.is2D
        val initializeOn = 0; val stateOn = 1; val stateBlink = 2; val initializeOff = 3; val stateOff = 4; val stateCount = 5

        val maxWidth = if (is2D) s.vWidth() else segLen
        val eyeSpace = kotlin.math.max(2, if (is2D) (s.vWidth() shr 4) else (segLen shr 5))
        val eyeWidth = eyeSpace shr 1
        val eyeLength = (2 * eyeWidth) + eyeSpace
        if (eyeLength >= maxWidth) return

        if (s.call == 0 || s.data == null || s.data!!.size < 16) {
            s.allocateData(16)
            java.util.Arrays.fill(s.data!!, 0.toByte())
        }

        var state = s.d8(0) % stateCount
        var dataColor = s.d8(1)
        var startPos = s.dU16(2)
        var duration = kotlin.math.max(1, s.dU16(4))
        var eyeY = s.dU16(6)
        var startTime = s.dU32(8)
        var blinkEndTime = s.dU32(12)

        if (!s.check2) s.fill(s.SEGCOLOR(1))

        val now = m.getNow()
        val elapsedTime = (now - startTime) and 0xFFFFFFFFL

        var fallOn = false
        var fallOff = false
        when (state) {
            initializeOn -> {
                startPos = m.random16(0, kotlin.math.max(1, maxWidth - eyeLength - 1))
                dataColor = m.random8()
                if (is2D) eyeY = m.random16(s.vHeight() - 1)
                duration = 128 + m.random16(s.intensity * 64)
                state = stateOn
                fallOn = true
            }
            stateOn -> fallOn = true
            stateBlink -> {
                if (now >= blinkEndTime) state = stateOn
            }
            initializeOff -> {
                val eyeOffTimeBase = s.speed * 128
                duration = eyeOffTimeBase + m.random16(eyeOffTimeBase)
                state = stateOff
                fallOff = true
            }
            stateOff -> fallOff = true
            else -> state = initializeOn
        }
        if (fallOn) {
            val start2ndEye = startPos + eyeWidth + eyeSpace
            duration = kotlin.math.min(duration, 128 + (s.intensity * 64))
            val fadeInAnimationState = kotlin.math.floor((elapsedTime * 256 * 8).toDouble() / kotlin.math.max(1, duration))
            val backgroundColor = s.SEGCOLOR(1)
            val eyeColor = s.color_from_palette(dataColor, false, false, 0)
            var c = eyeColor
            if (fadeInAnimationState < 256) {
                c = m.color_blend(backgroundColor, eyeColor, fadeInAnimationState.toInt() and 0xFF)
            } else if (elapsedTime > 1024) {
                val remainingTime = if (elapsedTime >= duration) 0L else (duration - elapsedTime)
                if (remainingTime > 1024) {
                    if (m.random8() < 4) {
                        c = backgroundColor
                        state = stateBlink
                        blinkEndTime = now + m.random8(8, 128)
                    }
                }
            }
            if (c != backgroundColor) {
                for (ei in 0 until eyeWidth) {
                    if (is2D) {
                        s.setPixelColorXY(startPos + ei, eyeY, c)
                        s.setPixelColorXY(start2ndEye + ei, eyeY, c)
                    } else {
                        s.setPixelColor(startPos + ei, c)
                        s.setPixelColor(start2ndEye + ei, c)
                    }
                }
            }
        }
        if (fallOff) {
            val eyeOffBase = s.speed * 128
            duration = kotlin.math.min(duration, 2 * eyeOffBase)
        }

        if (elapsedTime > duration) {
            state = if (state == initializeOn || state == stateOn || state == stateBlink) initializeOff else initializeOn
            startTime = now
        }

        s.d8(0, state)
        s.d8(1, dataColor)
        s.dU16(2, startPos and 0xFFFF)
        s.dU16(4, duration and 0xFFFF)
        s.dU16(6, eyeY and 0xFFFF)
        s.dU32(8, startTime and 0xFFFFFFFFL)
        s.dU32(12, blinkEndTime and 0xFFFFFFFFL)
    }

    private fun solidPatternTri(s: JsSimSegment) {
        val segLen = s.vLength()
        val blockSize = kotlin.math.max(1, s.intensity / 32 + 1)
        for (i in 0 until segLen) s.setPixelColor(i, s.SEGCOLOR((i / blockSize) % 3))
    }

    private const val SPARK_BYTES = 20

    private fun fPos(s: JsSimSegment, i: Int) = s.dF32(i * SPARK_BYTES).toDouble()
    private fun fPos(s: JsSimSegment, i: Int, v: Double) = s.dF32(i * SPARK_BYTES, v)
    private fun fPosX(s: JsSimSegment, i: Int) = s.dF32(i * SPARK_BYTES + 4).toDouble()
    private fun fPosX(s: JsSimSegment, i: Int, v: Double) = s.dF32(i * SPARK_BYTES + 4, v)
    private fun fVel(s: JsSimSegment, i: Int) = s.dF32(i * SPARK_BYTES + 8).toDouble()
    private fun fVel(s: JsSimSegment, i: Int, v: Double) = s.dF32(i * SPARK_BYTES + 8, v)
    private fun fVelX(s: JsSimSegment, i: Int) = s.dF32(i * SPARK_BYTES + 12).toDouble()
    private fun fVelX(s: JsSimSegment, i: Int, v: Double) = s.dF32(i * SPARK_BYTES + 12, v)
    private fun fCol(s: JsSimSegment, i: Int) = s.dU16(i * SPARK_BYTES + 16)
    private fun fCol(s: JsSimSegment, i: Int, v: Int) = s.dU16(i * SPARK_BYTES + 16, v and 0xFFFF)
    private fun fColIdx(s: JsSimSegment, i: Int) = s.d8(i * SPARK_BYTES + 18)
    private fun fColIdx(s: JsSimSegment, i: Int, v: Int) = s.d8(i * SPARK_BYTES + 18, v and 0xFF)

    private fun fireworks1D(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val is2D = s.is2D
        val cols = if (is2D) s.vWidth() else 1
        val rows = if (is2D) s.vHeight() else segLen

        val maxSparks = 53
        val numSparks = kotlin.math.min(5 + ((rows * cols) shr 1), maxSparks)
        val dataSize = SPARK_BYTES * numSparks + 4

        if (s.data == null || s.dataLen < dataSize) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0.toByte())
            s.aux0 = 0
        }
        val dgOff = SPARK_BYTES * numSparks
        if (dataSize != s.aux1) {
            s.dF32(dgOff, 0.0)
            s.aux0 = 0
            s.aux1 = dataSize
        }

        s.fade_out(252)

        var gravity = -0.0004 - (s.speed / 800000.0)
        gravity *= rows

        if (s.aux0 < 2) {
            if (s.aux0 == 0) {
                fPos(s, 0, 0.0)
                if (is2D) {
                    fPosX(s, 0, m.random16(2, kotlin.math.max(3, cols - 3)).toDouble())
                } else {
                    fPosX(s, 0, if (s.intensity > m.random8()) 1.0 else 0.0)
                }
                var peakHeight = 75 + m.random8(180)
                peakHeight = (peakHeight * (rows - 1)) shr 8
                fVel(s, 0, kotlin.math.sqrt(-2.0 * gravity * peakHeight))
                fVelX(s, 0, if (is2D) (m.random8(9) - 4) / 64.0 else 0.0)
                fCol(s, 0, 255)
                s.aux0 = 1
            }
            if (fVel(s, 0) > 12 * gravity) {
                if (is2D) {
                    val bri = kotlin.math.min(255, kotlin.math.max(0, fCol(s, 0)))
                    s.setPixelColorXY(
                        kotlin.math.floor(fPosX(s, 0)).toInt(),
                        rows - kotlin.math.floor(fPos(s, 0)).toInt() - 1,
                        m.colorPack(bri, bri, bri),
                    )
                } else {
                    val drawPos = if (JsSimMath.jsTrunc(fPosX(s, 0)) != 0.0) rows - kotlin.math.floor(fPos(s, 0)).toInt() - 1
                    else kotlin.math.floor(fPos(s, 0)).toInt()
                    if (drawPos >= 0 && drawPos < segLen) {
                        val bri = kotlin.math.min(255, kotlin.math.max(0, fCol(s, 0)))
                        s.setPixelColor(drawPos, m.colorPack(bri, bri, bri))
                    }
                }
                fPos(s, 0, fPos(s, 0) + fVel(s, 0))
                fPos(s, 0, kotlin.math.max(0.0, kotlin.math.min((rows - 1).toDouble(), fPos(s, 0))))
                if (is2D) {
                    fPosX(s, 0, fPosX(s, 0) + fVelX(s, 0))
                    fPosX(s, 0, kotlin.math.max(0.0, kotlin.math.min((cols - 1).toDouble(), fPosX(s, 0))))
                }
                fVel(s, 0, fVel(s, 0) + gravity)
                fCol(s, 0, kotlin.math.max(0, fCol(s, 0) - 2))
            } else {
                s.aux0 = 2
            }
        } else if (s.aux0 < 4) {
            var nSparks = kotlin.math.floor(fPos(s, 0)).toInt() + m.random8(4)
            nSparks = kotlin.math.max(nSparks, 4)
            nSparks = kotlin.math.min(nSparks, numSparks)

            if (s.aux0 == 2) {
                for (i in 1 until nSparks) {
                    fPos(s, i, fPos(s, 0))
                    fPosX(s, i, fPosX(s, 0))
                    fVel(s, i, (m.random16(20001) / 10000.0) - 0.9)
                    if (rows < 32) fVel(s, i, fVel(s, i) * 0.5)
                    fVelX(s, i, if (is2D) (m.random16(20001) / 10000.0) - 1.0 else 0.0)
                    fCol(s, i, 345)
                    fColIdx(s, i, m.random8())
                    fVel(s, i, fVel(s, i) * (fPos(s, 0) / rows))
                    fVelX(s, i, fVelX(s, i) * (if (is2D) fPosX(s, 0) / cols else 0.0))
                    fVel(s, i, fVel(s, i) * (-gravity * 50))
                }
                s.dF32(dgOff, gravity / 2)
                s.aux0 = 3
            }

            if (fCol(s, 1) > 4) {
                for (i in 1 until nSparks) {
                    fPos(s, i, fPos(s, i) + fVel(s, i))
                    fPosX(s, i, fPosX(s, i) + fVelX(s, i))
                    fVel(s, i, fVel(s, i) + s.dF32(dgOff).toDouble())
                    if (is2D) fVelX(s, i, fVelX(s, i) + s.dF32(dgOff).toDouble())
                    if (fCol(s, i) > 3) fCol(s, i, fCol(s, i) - 4)

                    val pos = fPos(s, i)
                    if (pos > 0 && pos < rows) {
                        val posX = fPosX(s, i)
                        if (is2D && !(posX >= 0 && posX < cols)) continue
                        val prog = fCol(s, i)
                        val spColor = if (s.palette != 0) s.color_wheel(fColIdx(s, i)) else s.SEGCOLOR(0)
                        var c = 0
                        if (prog > 300) {
                            c = m.color_blend(spColor, 0xFFFFFF, kotlin.math.min(255, (prog - 300) * 5))
                        } else if (prog > 45) {
                            c = m.color_blend(0, spColor, kotlin.math.min(255, prog - 45))
                            val cooling = (300 - prog) shr 5
                            val cr = m.colorR(c); var cg = m.colorG(c); var cb = m.colorB(c)
                            cg = m.qsub8(cg, cooling)
                            cb = m.qsub8(cb, cooling * 2)
                            c = m.colorPack(cr, cg, cb)
                        }
                        if (is2D) {
                            s.setPixelColorXY(kotlin.math.floor(posX).toInt(), rows - kotlin.math.floor(pos).toInt() - 1, c)
                        } else {
                            val drawPos = if (JsSimMath.jsTrunc(posX) != 0.0) rows - kotlin.math.floor(pos).toInt() - 1
                            else kotlin.math.floor(pos).toInt()
                            if (drawPos >= 0 && drawPos < segLen) s.setPixelColor(drawPos, c)
                        }
                    }
                }
                if (s.check3) s.blur(16)
                s.dF32(dgOff, s.dF32(dgOff).toDouble() * 0.8)
            } else {
                s.aux0 = 6 + m.random8(10)
            }
        } else {
            s.aux0--
            if (s.aux0 < 4) s.aux0 = 0
        }
    }

    private fun sinelonDual(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(s.intensity)
        val pos = m.beatsin16(s.speed / 10, 0, segLen - 1)
        if (s.call == 0) s.aux0 = pos
        val color1 = s.color_from_palette(pos, true, false, 0)
        var color2 = s.SEGCOLOR(2)
        if (color2 == 0) color2 = s.color_from_palette(pos, true, false, 0)
        s.setPixelColor(pos, color1)
        s.setPixelColor(segLen - 1 - pos, color2)
        if (s.aux0 != pos) {
            if (s.aux0 < pos) {
                var i = s.aux0
                while (i < pos) { s.setPixelColor(i, color1); s.setPixelColor(segLen - 1 - i, color2); i++ }
            } else {
                var i = s.aux0
                while (i > pos) { s.setPixelColor(i, color1); s.setPixelColor(segLen - 1 - i, color2); i-- }
            }
            s.aux0 = pos
        }
    }

    private fun sinelonRainbow(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(s.intensity)
        val pos = m.beatsin16(s.speed / 10, 0, segLen - 1)
        if (s.call == 0) s.aux0 = pos
        val color1 = s.color_wheel((pos and 0x07) * 32)
        s.setPixelColor(pos, color1)
        if (s.aux0 != pos) {
            if (s.aux0 < pos) {
                var i = s.aux0
                while (i < pos) { s.setPixelColor(i, color1); i++ }
            } else {
                var i = s.aux0
                while (i > pos) { s.setPixelColor(i, color1); i-- }
            }
            s.aux0 = pos
        }
    }

    private fun solidGlitter(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        s.fill(s.SEGCOLOR(0))
        val col = if (s.SEGCOLOR(2) != 0) s.SEGCOLOR(2) else 0xFFFFFF
        if (s.intensity > m.random8()) s.setPixelColor(m.random16(segLen), col)
    }

    private class PhasedState { var phase = 0.0 }

    private fun phased(s: JsSimSegment, noise: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        val allfreq = 16
        var st = s.state as? PhasedState
        if (st == null) { st = PhasedState(); s.state = st }
        if (s.call == 0) st.phase = 0.0
        val cutOff = 255 - s.intensity
        var index = m.getNow() / 64
        st.phase += s.speed / 32.0
        val bg = s.SEGCOLOR(1)
        for (i in 0 until segLen) {
            var modVal = 5
            if (noise) {
                modVal = m.perlin8(i * 10 + i * 10) shr 4
                if (modVal == 0) modVal = 1
            }
            var v = ((i + 1) * allfreq).toDouble()
            if (modVal != 0) v += st.phase * ((i % modVal) + 1) / 2
            var b = m.cubicwave8(i32(kotlin.math.floor(v)) and 0xFF)
            b = if (b > cutOff) (b - cutOff) else 0
            s.setPixelColor(i, m.color_blend(bg, s.color_from_palette((index and 0xFF).toInt(), false, false, 0), b))
            index += 256 / segLen
            if (segLen > 256) index++
        }
    }

    private fun twinkleup(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var prngState = 535
        fun prngRandom8(): Int {
            val d = prngState.toDouble() * 1103515245.0 + 12345.0
            prngState = d.toLong().toInt() and 0x7FFFFFFF
            return (prngState shr 16) and 0xFF
        }
        val now = m.getNow()
        val t16 = kotlin.math.floor(16.0 * now / (256 - s.speed))
        val t100 = now / 100
        for (i in 0 until segLen) {
            val ranstart = prngRandom8()
            var pixBri = m.sin8(i32(ranstart + t16) and 0xFF)
            if (prngRandom8() > s.intensity) pixBri = 0
            val palIdx = ((prngRandom8() + t100) and 0xFF).toInt()
            s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(palIdx, false, false, 0), pixBri))
        }
    }

    private fun dancingShadows(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val spotMax = 49
        val spotTypes = 6
        val numSpotlights = m.map(s.intensity, 0, 255, 2, spotMax)
        val initialize = s.aux0 != numSpotlights
        s.aux0 = numSpotlights
        val spotSize = 16
        val dataSize = spotSize * numSpotlights
        if (s.call == 0 || s.data == null || s.data!!.size < dataSize) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0.toByte())
            s.aux0 = 0
        }
        s.fill(0)
        val now = m.getNow()
        for (i in 0 until numSpotlights) {
            val off = i * spotSize
            var speed = s.dF32(off).toDouble()
            var colorIdx = s.d8(off + 4)
            var position = s.dI16(off + 6)
            var lastUpdate = s.dU32(off + 8)
            var width = s.d8(off + 12)
            var type = s.d8(off + 13)
            var respawn = false
            if (!initialize) {
                val delta = kotlin.math.floor((now - lastUpdate).toDouble() * speed * ((1.0 + s.speed) / 100.0))
                if (kotlin.math.abs(delta) >= 1) {
                    position += delta.toInt()
                    lastUpdate = now
                }
                respawn = (speed > 0 && position > segLen + 2) || (speed < 0 && position < -(width + 2))
            }
            if (initialize || respawn) {
                colorIdx = m.random8()
                width = m.random8(1, 10)
                speed = 1.0 / m.random8(4, 50)
                if (initialize) {
                    position = m.random16(segLen)
                    speed *= if (m.random8(2) != 0) 1.0 else -1.0
                } else {
                    if (m.random8(2) != 0) {
                        position = segLen + width
                        speed *= -1.0
                    } else {
                        position = -width
                    }
                }
                lastUpdate = now
                type = m.random8(spotTypes)
            }
            s.dF32(off, speed)
            s.d8(off + 4, colorIdx)
            s.dU16(off + 6, position)
            s.dU32(off + 8, lastUpdate and 0xFFFFFFFFL)
            s.d8(off + 12, width)
            s.d8(off + 13, type)

            val color = s.color_from_palette(colorIdx, false, false, 255)
            val start = position
            if (width <= 1) {
                if (start >= 0 && start < segLen) s.blendPixelColor(start, color, 128)
            } else {
                when (type) {
                    0 -> for (j in 0 until width) if (start + j in 0 until segLen) s.blendPixelColor(start + j, color, 128)
                    1 -> for (j in 0 until width) if (start + j in 0 until segLen)
                        s.blendPixelColor(start + j, color, m.cubicwave8(m.map(j, 0, width - 1, 0, 255)))
                    2 -> for (j in 0 until width) if (start + j in 0 until segLen)
                        s.blendPixelColor(start + j, color, m.cubicwave8((2 * m.map(j, 0, width - 1, 0, 255)) and 0xFF))
                    3 -> { var j = 0; while (j < width) { if (start + j in 0 until segLen) s.blendPixelColor(start + j, color, 128); j += 2 } }
                    4 -> { var j = 0; while (j < width) { if (start + j in 0 until segLen) s.blendPixelColor(start + j, color, 128); j += 3 } }
                    5 -> { var j = 0; while (j < width) { if (start + j in 0 until segLen) s.blendPixelColor(start + j, color, 128); j += 4 } }
                }
            }
        }
    }

    private fun dynamicSmooth(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0 || s.data == null) {
            s.allocateData(segLen)
            for (i in 0 until segLen) s.d8(i, m.random8())
        }
        val cycleTime = 50 + (255 - s.speed) * 15
        val it = m.getNow() / cycleTime
        if (it != s.step && s.speed != 0) {
            for (i in 0 until segLen) if (m.random8() <= s.intensity) s.d8(i, m.random8())
            s.step = it
        }
        for (i in 0 until segLen) s.blendPixelColor(i, s.color_wheel(s.d8(i)), 16)
    }

    private class ShimState { var lastTime = 0L }

    private fun shimmer(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val now = m.getNow()
        var sd = s.state as? ShimState
        if (s.call == 0 || sd == null) { sd = ShimState(); sd.lastTime = now; s.state = sd }

        val radius = ((s.custom1 * segLen) shr 7) + 1
        val traversalDistance = (segLen + 2 * radius) shl 8
        val traversalTime = 200 + (255 - s.speed) * 80
        val speed = (traversalDistance shl 5) / traversalTime
        var position = s.step.toInt()
        val inputstate = (s.intensity shl 8) or s.custom1

        if (s.call == 0 || inputstate != s.aux1) {
            position = -(radius shl 8)
            s.aux0 = 0
            sd.lastTime = now
            s.aux1 = inputstate
        }
        if (s.speed != 0) {
            val deltaTime = ((now - sd.lastTime) and 0x7F).toInt()
            sd.lastTime = now
            if (s.aux0 > 0) {
                s.aux0 = if (s.aux0 > deltaTime) s.aux0 - deltaTime else 0
            } else {
                val step = 1 + ((speed * deltaTime) shr 5)
                position += step
                val endposition = (segLen + radius) shl 8
                if (position > endposition) {
                    s.aux0 = s.intensity * 236
                    if (s.check3) s.aux0 = m.random16(s.aux0 + 1000)
                    position = -(radius shl 8)
                }
                s.step = position.toLong()
            }
            if (s.check2) position = (segLen shl 8) - position
        } else {
            position = segLen shl 7
        }

        val bg = s.SEGCOLOR(1)
        for (i in 0 until segLen) {
            val dist = kotlin.math.abs(position - (i shl 8))
            if (dist < (radius shl 8)) {
                var color = s.color_from_palette((i * 255) / segLen, false, false, 0)
                val blend = dist / radius
                if (s.custom2 != 0) {
                    val modVal: Int
                    val tNow = (now * s.custom3).toInt() shl 5
                    modVal = if (s.check1) {
                        ((m.sin16(((i * s.custom2) shl 6) + tNow) shr 8) + 128) and 0xFF
                    } else {
                        (m.perlin16((i * s.custom2) shl 7, tNow) shr 8) and 0xFF
                    }
                    color = m.color_fade(color, modVal, true)
                }
                s.setPixelColor(i, m.color_blend(color, bg, blend))
            } else {
                s.setPixelColor(i, bg)
            }
        }
    }

    @Suppress("unused") private fun keepImport(x: Double) = u32(x)
}
