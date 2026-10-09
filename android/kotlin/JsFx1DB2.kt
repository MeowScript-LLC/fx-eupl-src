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

internal object JsFx1DB2 : JsFxBatch {
    override val ids: IntArray = intArrayOf(
        66, 67, 68, 74, 80, 85, 86, 87, 88, 102, 91, 92, 97, 100, 101, 108, 110,
        7, 12, 20, 27, 28, 29, 30, 33, 34, 44,
    )

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            66 -> fire2012(s)
            67 -> colorwaves(s)
            68 -> bpm(s)
            74 -> colortwinkle(s)
            80 -> twinklefox(s)
            85 -> spotsBase(s, (255 - s.speed) shl 8)
            86 -> spotsFade(s)
            87 -> glitter(s)
            88 -> candle(s, false)
            102 -> candle(s, true)
            91 -> bouncingBalls(s)
            92 -> sinelon(s)
            97 -> plasma(s)
            100 -> heartbeat(s)
            101 -> pacifica(s)
            108 -> sine(s)
            110 -> flow(s)
            7 -> dynamic(s)
            12 -> fade(s)
            20 -> sparkle(s)
            27 -> android(s)
            28 -> chase(s, s.SEGCOLOR(1), if (s.SEGCOLOR(2) != 0) s.SEGCOLOR(2) else s.SEGCOLOR(0), s.SEGCOLOR(0), true)
            29 -> chase(s, s.SEGCOLOR(1), if (s.SEGCOLOR(2) != 0) s.SEGCOLOR(2) else s.SEGCOLOR(0), s.SEGCOLOR(0), false)
            30 -> chaseRainbow(s)
            33 -> rainbowRunner(s)
            34 -> colorful(s)
            44 -> tetrix(s)
            else -> return false
        }
        return true
    }

    private fun Long.i(): Int = this.toInt()

    private class Fire2012State { var step: Long = -1L  }

    private fun fire2012(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (s.data == null || s.dataLen < segLen) {
            s.allocateData(segLen)
            java.util.Arrays.fill(s.data!!, 0)
        }
        val st = (s.state as? Fire2012State) ?: Fire2012State().also { s.state = it }
        val it = (m.getNow().i() shr 5).toLong() and 0xFFFFFFFFL
        val ignition = maxOf(3, segLen / 10)
        val newStep = it != st.step

        for (i in 0 until segLen) {
            val cool = if (newStep) m.jsRandom8(((20 + s.speed / 3) * 16) / segLen + 2) else m.jsRandom8(4)
            val minTemp = if (i < ignition) (ignition - i) / 4 + 16 else 0
            val temp = m.qsub8(s.d8(i), cool)
            s.d8(i, if (temp < minTemp) minTemp else temp)
        }

        if (newStep) {
            for (k in segLen - 1 downTo 2) {
                s.d8(k, (s.d8(k - 1) + (s.d8(k - 2) shl 1)) / 3)
            }
            if (m.jsRandom8() <= s.intensity) {
                val y = m.jsRandom8(ignition)
                val boost = ((17 + s.custom3) * (ignition - y / 2)) / ignition
                s.d8(y, m.qadd8(s.d8(y), m.jsRandom8(96 + 2 * boost, 207 + boost)))
            }
        }

        for (j in 0 until segLen) {
            s.setPixelColor(j, s.ColorFromPalette(minOf(s.d8(j), 240), 255, 0))
        }
        if (newStep) st.step = it
    }

    private fun colorwaves(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val duration = 10 + s.speed
        var sPseudotime = s.step
        var sHue16 = s.aux0

        val brightdepth = m.beatsin88(341, 96, 224)
        val brightnessthetainc16 = m.beatsin88(203, 25 * 256, 40 * 256)
        val msmultiplier = m.beatsin88(147, 23, 60)
        val hueinc16 = ((m.beatsin88(113, 60, 300).toLong() * s.intensity * 10) / 255).toInt()

        sPseudotime = (sPseudotime + duration.toLong() * msmultiplier) and 0xFFFFFFFFL
        sHue16 = (sHue16 + duration * m.beatsin88(400, 5, 9)) and 0xFFFF
        var brightnesstheta16 = sPseudotime
        var hue16 = sHue16

        for (i in 0 until segLen) {
            hue16 = (hue16 + hueinc16) and 0xFFFF
            val h16128 = (hue16 shr 7) and 0x1FF
            val hue8 = if ((h16128 and 0x100) != 0) (255 - (h16128 shr 1)) and 0xFF else (h16128 shr 1) and 0xFF

            brightnesstheta16 = (brightnesstheta16 + brightnessthetainc16) and 0xFFFF
            val b16 = (m.sin16(brightnesstheta16.i()) + 32768) and 0xFFFF
            val bri16 = ((b16.toLong() * b16) / 65536).toInt()
            var bri8 = ((bri16.toLong() * brightdepth) / 65536).toInt()
            bri8 += 255 - brightdepth
            bri8 = minOf(255, maxOf(0, bri8))
            s.blendPixelColor(i, s.color_from_palette(hue8, false, false, 0, bri8), 128)
        }
        s.step = sPseudotime
        s.aux0 = sHue16
    }

    private fun bpm(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val stp = (m.getNow() / 20).i() and 0xFF
        val beat = m.beatsin8(s.speed, 64, 255)
        for (i in 0 until segLen) {
            s.setPixelColor(i, s.color_from_palette((stp + i * 2) and 0xFF, false, false, 0, (beat - stp + i * 10) and 0xFF))
        }
    }

    private fun colortwinkle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val dataSize = (segLen + 7) shr 3
        if (s.data == null || s.dataLen < dataSize) {
            s.allocateData(dataSize)
            java.util.Arrays.fill(s.data!!, 0)
        }
        if (m.getNow() - s.step < m.FRAMETIME_FIXED) return
        s.step = m.getNow()

        val fadeUpAmount = 8 + (s.speed shr 2)
        val fadeDownAmount = 8 + (s.speed shr 3)

        for (i in 0 until segLen) {
            val cur = s.getPixelColor(i)
            val index = i shr 3
            val bitNum = i and 0x07
            val fadeUp = (s.d8(index) shr bitNum) and 1
            if (fadeUp != 0) {
                val incrementalColor = m.color_fade(cur, fadeUpAmount, true)
                var col = m.color_add(cur, incrementalColor)
                if (m.colorR(col) == 255 || m.colorG(col) == 255 || m.colorB(col) == 255) {
                    s.d8(index, s.d8(index) and (1 shl bitNum).inv())
                }
                if (col == cur) col = m.color_add(col, col)
                s.setPixelColor(i, col)
            } else {
                s.setPixelColor(i, m.color_fade(cur, 255 - fadeDownAmount, false))
            }
        }

        for (j in 0..(segLen / 50)) {
            if (m.jsRandom8() <= s.intensity) {
                for (times in 0 until 5) {
                    val i = m.jsRandom16(segLen)
                    if (s.getPixelColor(i) == 0) {
                        val index = i shr 3
                        val bitNum = i and 0x07
                        s.d8(index, s.d8(index) or (1 shl bitNum))
                        s.setPixelColor(i, s.ColorFromPalette(m.jsRandom8(), 64, 0))
                        break
                    }
                }
            }
        }
    }

    private fun twinklefox(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var prng16 = 11337

        s.aux0 = if (s.speed > 100) 3 + ((255 - s.speed) shr 3) else 22 + ((100 - s.speed) shr 1)

        var bg = s.SEGCOLOR(1)
        val bglight = m.getAverageLight(bg)
        bg = when {
            bglight > 64 -> m.color_fade(bg, 16, true)
            bglight > 16 -> m.color_fade(bg, 64, true)
            else -> m.color_fade(bg, 86, true)
        }
        bg = m.gamma32inv(bg)
        val backgroundBrightness = m.getAverageLight(bg)
        val now = m.getNow()
        val divisor = maxOf(1, s.aux0).toLong()
        val twinkleDensity = (s.intensity shr 5) + 1

        for (i in 0 until segLen) {
            prng16 = (prng16 * 2053 + 1384) and 0xFFFF
            val myclockoffset16 = prng16
            prng16 = (prng16 * 2053 + 1384) and 0xFFFF
            val myspeedmultiplierQ53 = ((((prng16 and 0xFF) shr 4) + (prng16 and 0x0F)) and 0x0F) + 0x08
            val myclock30 = (((now * myspeedmultiplierQ53).i() shr 3) + myclockoffset16).toLong() and 0xFFFFFFFFL
            val myunique8 = (prng16 shr 8) and 0xFF

            val salt = myunique8
            val ticks = myclock30 / divisor
            val fastcycle8 = ticks.i() and 0xFF
            var slowcycle16 = ((ticks.i() shr 8) + salt) and 0xFFFF
            slowcycle16 = (slowcycle16 + m.sin8(slowcycle16 and 0xFF)) and 0xFFFF
            slowcycle16 = (slowcycle16 * 2053 + 1384) and 0xFFFF
            val slowcycle8 = ((slowcycle16 and 0xFF) + ((slowcycle16 shr 8) and 0xFF)) and 0xFF

            var bright = 0
            if (((slowcycle8 and 0x0E) shr 1) < twinkleDensity) {
                val ph = fastcycle8
                bright = if (ph < 86) ph * 3 else { val ph2 = ph - 86; 255 - (ph2 + (ph2 shr 1)) }
            }

            val hue = (slowcycle8 - salt) and 0xFF
            var c: Int
            if (bright > 0) {
                c = s.ColorFromPalette(hue, bright, 0)
                if (!s.check1 && fastcycle8 >= 128) {
                    val cooling = (fastcycle8 - 128) shr 4
                    val cr = m.colorR(c); var cg = m.colorG(c); var cb = m.colorB(c)
                    cg = m.qsub8(cg, cooling)
                    cb = m.qsub8(cb, cooling * 2)
                    c = m.colorPack(cr, cg, cb)
                }
            } else {
                c = 0
            }

            val cbright = m.getAverageLight(c)
            val deltabright = cbright - backgroundBrightness
            if (deltabright >= 32 || bg == 0) {
                s.setPixelColor(i, c)
            } else if (deltabright > 0) {
                s.setPixelColor(i, m.color_blend(bg, c, minOf(255, deltabright * 8)))
            } else {
                s.setPixelColor(i, bg)
            }
        }
    }

    private fun spotsBase(s: JsSimSegment, threshold: Int) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        if (!s.check2) s.fill(s.SEGCOLOR(1))

        val maxZones = segLen shr 2
        val zones = 1 + ((s.intensity * maxZones) shr 8)
        val zoneLen = segLen / zones
        val offset = (segLen - zones * zoneLen) shr 1

        for (z in 0 until zones) {
            val pos = offset + z * zoneLen
            for (i in 0 until zoneLen) {
                val wave = m.triwave16(((i.toLong() * 0xFFFF) / zoneLen).i())
                if (wave > threshold) {
                    val index = pos + i
                    val sv = (((wave - threshold).toLong() * 255) / (0xFFFF - threshold)).i()
                    s.setPixelColor(index, m.color_blend(s.color_from_palette(index, true, false, 0), s.SEGCOLOR(1), 255 - sv))
                }
            }
        }
    }

    private fun spotsFade(s: JsSimSegment) {
        val m = s.m
        val counter = ((m.getNow() * ((s.speed shr 2) + 8)) and 0xFFFF).i()
        val t = m.triwave16(counter)
        val tr = (t shr 1) + (t shr 2)
        spotsBase(s, tr)
    }

    private fun glitter(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (!s.check2) {
            var counter = 0
            if (s.speed != 0) {
                counter = ((m.getNow() * ((s.speed shr 3) + 1)) and 0xFFFF).i()
                counter = counter shr 8
            }
            val noWrap = s.speed == 0
            for (i in 0 until segLen) {
                var colorIndex = (i * 255) / segLen - counter
                if (noWrap) colorIndex = m.map(colorIndex and 0xFF, 0, 255, 0, 240)
                s.setPixelColor(i, s.color_from_palette(colorIndex and 0xFF, false, true, 255))
            }
        }
        val glitterCol = if (s.SEGCOLOR(2) != 0) s.SEGCOLOR(2) else -1
        if (s.intensity > m.random8()) s.setPixelColor(m.random16(segLen), glitterCol)
    }

    private fun candle(s: JsSimSegment, multi: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        if (multi && segLen > 1) {
            val dataSize = 4 + maxOf(1, segLen - 1) * 3
            if (s.call == 0 || s.data == null || s.dataLen < dataSize) s.allocateData(dataSize)
        } else {
            if (s.call == 0 || s.data == null || s.dataLen < 4) s.allocateData(4)
        }

        val lastcall = s.dU32(0)
        if (m.getNow() - lastcall < m.FRAMETIME_FIXED) return
        s.dU32(0, m.getNow() and 0xFFFFFFFFL)

        val valrange = s.intensity
        val rndval = valrange shr 1

        var speedFactor = 4
        if (s.speed > 252) speedFactor = 1
        else if (s.speed > 99) speedFactor = 2
        else if (s.speed > 49) speedFactor = 3

        val numCandles = if (multi) segLen else 1

        for (i in 0 until numCandles) {
            var d = 0
            var sv: Int
            var sTarget: Double
            var fadeStep: Int
            if (i == 0) {
                sv = s.aux0
                sTarget = s.aux1.toDouble()
                fadeStep = s.step.i()
            } else {
                d = (i - 1) * 3
                sv = s.d8(4 + d)
                sTarget = s.d8(4 + d + 1).toDouble()
                fadeStep = s.d8(4 + d + 2)
            }

            if (fadeStep == 0) {
                sv = 128
                sTarget = (130 + m.random8(4)).toDouble()
                fadeStep = 1
            }

            var newTarget = false
            if (sTarget > sv.toDouble()) {
                sv = m.qadd8(sv, fadeStep)
                if (sv.toDouble() >= sTarget) newTarget = true
            } else {
                sv = m.qsub8(sv, fadeStep)
                if (sv.toDouble() <= sTarget) newTarget = true
            }

            if (newTarget) {
                sTarget = rnd8(m, rndval) + rnd8(m, rndval)
                if (sTarget < (rndval shr 1).toDouble()) sTarget = (rndval shr 1) + rnd8(m, rndval)
                val offset = 255 - valrange
                sTarget += offset
                val dif = if (sTarget > sv.toDouble()) sTarget - sv else sv - sTarget
                fadeStep = i32(dif) shr speedFactor
                if (fadeStep == 0) fadeStep = 1
            }

            if (i > 0) {
                s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i, true, false, 0), sv and 0xFF))
                s.d8(4 + d, sv and 0xFF)
                s.d8(4 + d + 1, i32(sTarget) and 0xFF)
                s.d8(4 + d + 2, fadeStep and 0xFF)
            } else {
                for (j in 0 until segLen) {
                    s.setPixelColor(j, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(j, true, false, 0), sv and 0xFF))
                }
                s.aux0 = sv
                s.aux1 = i32(sTarget)
                s.step = fadeStep.toLong()
            }
        }
    }

    private fun rnd8(m: JsSimMath, lim: Int): Double {
        val r = m.random8()
        return if (lim == 0) Double.NaN else (r % lim).toDouble()
    }

    private class BallsState {
        val lastBounceTime = DoubleArray(16)
        val impactVelocity = FloatArray(16)
        val height = FloatArray(16)
    }

    private fun bouncingBalls(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        val maxNumBalls = 16
        if (s.call == 0 || s.data == null || s.dataLen < maxNumBalls * 16) s.allocateData(maxNumBalls * 16)
        val st = (s.state as? BallsState) ?: BallsState().also { s.state = it }

        if (!s.check2) s.fill(if (s.SEGCOLOR(2) != 0) 0 else s.SEGCOLOR(1))

        val numBalls = (s.intensity * (maxNumBalls - 1)) / 255 + 1
        val gravity = -9.81
        val hasCol2 = s.SEGCOLOR(2) != 0
        val time = m.getNow().toDouble()

        if (s.call == 0) {
            for (i in 0 until maxNumBalls) {
                st.lastBounceTime[i] = time
                st.impactVelocity[i] = 0f
                st.height[i] = 0f
            }
        }

        for (i in 0 until numBalls) {
            val lastBounceTime = st.lastBounceTime[i]
            var impactVelocity = st.impactVelocity[i].toDouble()
            var height: Double

            val timeSinceLastBounce = (time - lastBounceTime) / ((255 - s.speed) / 64.0 + 1)
            val timeSec = timeSinceLastBounce / 1000.0
            height = (0.5 * gravity * timeSec + impactVelocity) * timeSec

            if (height <= 0.0) {
                height = 0.0
                val dampening = 0.9 - i.toDouble() / (numBalls * numBalls)
                impactVelocity = dampening * impactVelocity
                st.lastBounceTime[i] = time
                if (impactVelocity < 0.015) {
                    impactVelocity = Math.sqrt(2.0 * 9.81) * m.random8(5, 11) / 10.0
                }
                st.impactVelocity[i] = impactVelocity.toFloat()
            } else if (height > 1.0) {
                continue
            }
            st.height[i] = height.toFloat()

            var color = s.SEGCOLOR(0)
            if (s.palette != 0) {
                color = m.color_wheel(Math.floor(i * (256.0 / maxOf(numBalls, 8))).toInt())
            } else if (hasCol2) {
                color = s.SEGCOLOR(i % 3)
            }
            val pos = jsRound(height * (segLen - 1))
            if (pos >= 0 && pos < segLen) s.setPixelColor(pos, color)
        }
    }

    private fun sinelon(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        s.fade_out(s.intensity)
        val pos = m.beatsin16(s.speed / 10, 0, segLen - 1)
        if (s.call == 0) s.aux0 = pos
        val color1 = s.color_from_palette(pos, true, false, 0)
        s.setPixelColor(pos, color1)
        if (s.aux0 != pos) {
            if (s.aux0 < pos) {
                for (i in s.aux0 until pos) s.setPixelColor(i, color1)
            } else {
                var i = s.aux0
                while (i > pos) { s.setPixelColor(i, color1); i-- }
            }
            s.aux0 = pos
        }
    }

    private fun plasma(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.call == 0) s.aux0 = m.random8(0, 2)
        val thisPhase = m.beatsin8_t(6 + s.aux0, -64, 64)
        val thatPhase = m.beatsin8_t(7 + s.aux0, -64, 64)
        val sp = s.speed shr 5
        for (i in 0 until segLen) {
            val colorIndex = m.cubicwave8((i * (2 + 3 * sp) + thisPhase) and 0xFF) / 2 +
                m.cos8_t((i * (1 + 2 * sp) + thatPhase) and 0xFF) / 2
            val thisBright = m.qsub8(colorIndex, m.beatsin8_t(7, 0, 128 - (s.intensity shr 1)))
            s.setPixelColor(i, s.color_from_palette(colorIndex, false, false, 0, thisBright))
        }
    }

    private fun heartbeat(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val bpm = 40 + (s.speed shr 3)
        val msPerBeat = 60000 / bpm
        val secondBeat = msPerBeat / 3
        var briLower = s.aux1
        val beatTimer = m.getNow() - s.step

        briLower = ((briLower.toLong() * 2042) / (2048 + s.intensity)).toInt()
        s.aux1 = briLower

        if (beatTimer > secondBeat && s.aux0 == 0) {
            s.aux1 = 65535
            s.aux0 = 1
        }
        if (beatTimer > msPerBeat) {
            s.aux1 = 65535
            s.aux0 = 0
            s.step = m.getNow()
        }
        for (i in 0 until segLen) {
            s.setPixelColor(i, m.color_blend(s.color_from_palette(i, true, false, 0), s.SEGCOLOR(1), 255 - (s.aux1 shr 8)))
        }
    }

    private val PAC1 = intArrayOf(
        0x000507, 0x000409, 0x00030B, 0x00030D, 0x000210, 0x000212, 0x000114, 0x000117,
        0x000019, 0x00001C, 0x000026, 0x000031, 0x00003B, 0x000046, 0x14554B, 0x28AA50,
    )
    private val PAC2 = intArrayOf(
        0x000507, 0x000409, 0x00030B, 0x00030D, 0x000210, 0x000212, 0x000114, 0x000117,
        0x000019, 0x00001C, 0x000026, 0x000031, 0x00003B, 0x000046, 0x0C5F52, 0x19BE5F,
    )
    private val PAC3 = intArrayOf(
        0x000208, 0x00030E, 0x000514, 0x00061A, 0x000820, 0x000927, 0x000B2D, 0x000C33,
        0x000E39, 0x001040, 0x001450, 0x001860, 0x001C70, 0x002080, 0x1040BF, 0x2060FF,
    )

    private fun colorAt(pal: IntArray, indexIn: Int, brightness: Int, blendType: Int): Int {
        var index = indexIn and 0xFF
        if (blendType == 2) index = (index * (1 + 0xF0)) shr 8
        val hi4 = (index shr 4) and 0xF
        val lo4 = index and 0x0F
        val e1 = pal[hi4]
        var r = (e1 shr 16) and 0xFF; var g = (e1 shr 8) and 0xFF; var b = e1 and 0xFF
        if (lo4 > 0 && blendType != 0) {
            val e2 = pal[(hi4 + 1) and 0xF]
            val f2 = lo4 shl 4
            val f1 = 256 - f2
            r = (r * f1 + ((e2 shr 16) and 0xFF) * f2) shr 8
            g = (g * f1 + ((e2 shr 8) and 0xFF) * f2) shr 8
            b = (b * f1 + (e2 and 0xFF) * f2) shr 8
        }
        if (brightness < 255) {
            val sc = brightness + 1
            r = (r * sc) shr 8; g = (g * sc) shr 8; b = (b * sc) shr 8
        }
        return ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private fun pacificaOneLayer(
        s: JsSimSegment, pal: IntArray?, pixelIdx: Int, cistart: Int, wavescale: Int, bri: Int, ioff: Int,
    ): Int {
        val m = s.m
        var ci = cistart and 0xFFFF
        var waveangle = ioff and 0xFFFF
        val wavescaleHalf = ((wavescale shr 1) + 20) and 0xFFFF
        waveangle = (waveangle + (120 + s.intensity) * pixelIdx) and 0xFFFF
        val s16 = (m.sin16(waveangle) + 32768) and 0xFFFF
        val cs = (m.scale16(s16, wavescaleHalf) + wavescaleHalf) and 0xFFFF
        ci = (ci + cs * pixelIdx) and 0xFFFF
        val sindex16 = (m.sin16(ci) + 32768) and 0xFFFF
        val sindex8 = m.scale16(sindex16, 240) and 0xFF
        return if (pal != null) colorAt(pal, sindex8, bri, 1)
        else s.color_from_palette(sindex8, false, false, 0, bri)
    }

    private fun pacifica(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var pal1: IntArray? = PAC1
        var pal2: IntArray? = PAC2
        var pal3: IntArray? = PAC3
        if (s.palette != 0) { pal1 = null; pal2 = null; pal3 = null }

        var sCIStart1 = s.aux0
        var sCIStart2 = s.aux1
        var sCIStart3 = s.step.i() and 0xFFFF
        var sCIStart4 = (s.step.i() shr 16) and 0xFFFF

        val deltams = (m.FRAMETIME shr 2) + ((m.FRAMETIME * s.speed) shr 7)
        val nowOld = m.getNow()
        val deltat = (nowOld.i() shr 2).toLong() + ((nowOld * s.speed).i() shr 7).toLong()
        m.setNow(deltat.i().toLong())

        val speedfactor1 = m.beatsin16(3, 179, 269)
        val speedfactor2 = m.beatsin16(4, 179, 269)
        val deltams1 = (deltams * speedfactor1) / 256
        val deltams2 = (deltams * speedfactor2) / 256
        val deltams21 = (deltams1 + deltams2) / 2

        sCIStart1 = (sCIStart1 + deltams1 * m.beatsin88(1011, 10, 13)) and 0xFFFF
        sCIStart2 = (sCIStart2 - deltams21 * m.beatsin88(777, 8, 11)) and 0xFFFF
        sCIStart3 = (sCIStart3 - deltams1 * m.beatsin88(501, 5, 7)) and 0xFFFF
        sCIStart4 = (sCIStart4 - deltams2 * m.beatsin88(257, 4, 6)) and 0xFFFF

        s.aux0 = sCIStart1 and 0xFFFF
        s.aux1 = sCIStart2 and 0xFFFF
        s.step = (((sCIStart4 and 0xFFFF) shl 16) or (sCIStart3 and 0xFFFF)).toLong()

        val basethreshold = m.beatsin8(9, 55, 65)
        var wave = m.beat8(7)

        val ws1 = m.beatsin16(3, 11 * 256, 14 * 256); val b1 = m.beatsin8(10, 70, 130); val o1 = (0 - m.beat16(301)) and 0xFFFF
        val ws2 = m.beatsin16(4, 6 * 256, 9 * 256); val b2 = m.beatsin8(17, 40, 80); val o2 = m.beat16(401)
        val b3 = m.beatsin8(9, 10, 38); val o3 = (0 - m.beat16(503)) and 0xFFFF
        val b4 = m.beatsin8(8, 10, 28); val o4 = m.beat16(601)

        for (i in 0 until segLen) {
            var cr = 2; var cg = 6; var cb = 10
            val l1 = pacificaOneLayer(s, pal1, i, sCIStart1, ws1, b1, o1)
            val l2 = pacificaOneLayer(s, pal2, i, sCIStart2, ws2, b2, o2)
            val l3 = pacificaOneLayer(s, pal3, i, sCIStart3, 6 * 256, b3, o3)
            val l4 = pacificaOneLayer(s, pal3, i, sCIStart4, 5 * 256, b4, o4)
            cr = minOf(255, cr + m.colorR(l1)); cg = minOf(255, cg + m.colorG(l1)); cb = minOf(255, cb + m.colorB(l1))
            cr = minOf(255, cr + m.colorR(l2)); cg = minOf(255, cg + m.colorG(l2)); cb = minOf(255, cb + m.colorB(l2))
            cr = minOf(255, cr + m.colorR(l3)); cg = minOf(255, cg + m.colorG(l3)); cb = minOf(255, cb + m.colorB(l3))
            cr = minOf(255, cr + m.colorR(l4)); cg = minOf(255, cg + m.colorG(l4)); cb = minOf(255, cb + m.colorB(l4))

            val threshold = m.scale8(m.sin8(wave), 20) + basethreshold
            wave = (wave + 7) and 0xFF
            val avg = (cr + cg + cb) shr 2
            if (avg > threshold) {
                val overage = avg - threshold
                val overage2 = m.qadd8(overage, overage)
                cr = minOf(255, cr + overage)
                cg = minOf(255, cg + overage2)
                cb = minOf(255, cb + m.qadd8(overage2, overage2))
            }
            cb = m.scale8(cb, 145)
            cg = m.scale8(cg, 200)
            cr = cr or 2
            cg = cg or 5
            cb = cb or 7
            s.setPixelColor(i, m.colorPack(cr, cg, cb))
        }
        m.setNow(nowOld)
    }

    private fun sine(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val colorIndex = m.getNow() / 32
        s.step = s.step + s.speed / 16
        val freq = s.intensity / 4
        for (i in 0 until segLen) {
            val pixBri = m.cubicwave8((i * freq + s.step).i() and 0xFF)
            s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1),
                s.color_from_palette(((i * colorIndex) / 255).i() and 0xFF, false, false, 0), pixBri))
        }
    }

    private fun flow(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        var counter = 0
        if (s.speed != 0) {
            counter = (m.getNow() * ((s.speed shr 2) + 1)).i()
            counter = counter shr 8
        }
        val maxZones = segLen / 6
        var zones = (s.intensity * maxZones) shr 8
        if ((zones and 0x01) != 0) zones++
        if (zones < 2) zones = 2
        val zoneLen = segLen / zones
        val requiredZones = Math.ceil(segLen.toDouble() / zoneLen).toInt()
        zones = requiredZones + 2
        val offset = (segLen - zones * zoneLen) / 2

        for (z in 0 until zones) {
            val pos = offset + z * zoneLen
            for (i in 0 until zoneLen) {
                val colorIndex = ((i * 255) / zoneLen - counter) and 0xFF
                val led = if ((z and 0x01) != 0) i else (zoneLen - 1) - i
                s.setPixelColor(pos + led, s.color_from_palette(colorIndex, false, true, 255))
            }
        }
    }

    private fun dynamic(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.data == null || s.dataLen < segLen) s.allocateData(segLen)
        if (s.call == 0) {
            for (i in 0 until segLen) s.d8(i, m.random8())
        }
        val cycleTime = 50 + (255 - s.speed) * 15
        val it = m.getNow() / cycleTime
        if (it != s.step && s.speed != 0) {
            for (i in 0 until segLen) {
                if (m.random8() <= s.intensity) s.d8(i, m.random8())
            }
            s.step = it
        }
        if (s.check1) {
            for (i in 0 until segLen) s.blendPixelColor(i, s.color_wheel(s.d8(i)), 16)
        } else {
            for (i in 0 until segLen) s.setPixelColor(i, s.color_wheel(s.d8(i)))
        }
    }

    private fun fade(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val counter = (m.getNow() * ((s.speed shr 3) + 10)) and 0xFFFFFFFFL
        val lum = m.triwave16((counter and 0xFFFF).i()) shr 8
        for (i in 0 until segLen) {
            s.setPixelColor(i, m.color_blend(s.SEGCOLOR(1), s.color_from_palette(i, true, false, 0), lum))
        }
    }

    private fun sparkle(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (!s.check2) {
            for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 1))
        }
        val cycleTime = 10 + (255 - s.speed) * 2
        val it = m.getNow() / cycleTime
        if (it != s.step) {
            s.aux0 = m.random16(segLen)
            s.step = it
        }
        s.setPixelColor(s.aux0, s.SEGCOLOR(0))
    }

    private fun android(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (s.data == null || s.dataLen < 4) {
            s.allocateData(4)
            s.d8(0, 0); s.d8(1, 0); s.d8(2, 0); s.d8(3, 0)
        }
        var counter = ((s.d8(0) shl 24) or (s.d8(1) shl 16) or (s.d8(2) shl 8) or s.d8(3)).toLong() and 0xFFFFFFFFL
        var size = s.aux1 shr 1
        var shrinking = s.aux1 and 0x01

        if (m.getNow() >= s.step) {
            s.step = m.getNow() + 3 + (8 * (255 - s.speed)) / segLen
            if (size > (s.intensity * segLen) / 255) {
                shrinking = 1
            } else if (size < 2) {
                shrinking = 0
            }
            if (shrinking == 0) {
                if (counter % 3 == 1L) s.aux0++ else size++
            } else {
                s.aux0++
                if (counter % 3 != 1L) size--
            }
            s.aux1 = (size shl 1) or shrinking
            counter = (counter + 1) and 0xFFFFFFFFL
            val c = counter.i()
            s.d8(0, (c shr 24) and 0xFF)
            s.d8(1, (c shr 16) and 0xFF)
            s.d8(2, (c shr 8) and 0xFF)
            s.d8(3, c and 0xFF)
            if (s.aux0 >= segLen) s.aux0 = 0
        }

        val start = s.aux0
        val end = (s.aux0 + size) % segLen
        for (i in 0 until segLen) {
            if ((start < end && i >= start && i < end) || (start >= end && (i >= start || i < end))) {
                s.setPixelColor(i, s.SEGCOLOR(0))
            } else {
                s.setPixelColor(i, s.color_from_palette(i, true, false, 1))
            }
        }
    }

    private fun chase(s: JsSimSegment, color1In: Int, color2: Int, color3: Int, doPalette: Boolean) {
        val m = s.m
        val segLen = s.vLength()
        var color1 = color1In
        val counter = ((m.getNow() * ((s.speed shr 2) + 1)) and 0xFFFF).i()
        val a = ((counter.toLong() * segLen).i()) shr 16

        val chaseRandom = s.mode == 29
        if (chaseRandom) {
            if (a < s.step) {
                s.aux1 = s.aux0
                s.aux0 = m.get_random_wheel_index(s.aux0)
            }
            color1 = s.color_wheel(s.aux0)
        }
        s.step = a.toLong()

        val size = 1 + ((s.intensity * segLen) shr 10)
        var b = a + size
        if (b > segLen) b -= segLen
        var c = b + size
        if (c > segLen) c -= segLen

        if (doPalette) {
            for (i in 0 until segLen) s.setPixelColor(i, s.color_from_palette(i, true, false, 1))
        } else {
            s.fill(color1)
        }

        if (chaseRandom) {
            color1 = s.color_wheel(s.aux1)
            for (i in a until segLen) s.setPixelColor(i, color1)
        }

        if (a < b) {
            for (i in a until b) s.setPixelColor(i, color2)
        } else {
            for (i in a until segLen) s.setPixelColor(i, color2)
            for (i in 0 until b) s.setPixelColor(i, color2)
        }
        if (b < c) {
            for (i in b until c) s.setPixelColor(i, color3)
        } else {
            for (i in b until segLen) s.setPixelColor(i, color3)
            for (i in 0 until c) s.setPixelColor(i, color3)
        }
    }

    private fun chaseRainbow(s: JsSimSegment) {
        val segLen = s.vLength()
        var colorSep = 256 / segLen
        if (colorSep == 0) colorSep = 1
        val colorIndex = s.call and 0xFF
        val color = s.color_wheel((s.step * colorSep + colorIndex).i() and 0xFF)
        chase(s, color, s.SEGCOLOR(0), s.SEGCOLOR(1), false)
    }

    private fun rainbowRunner(s: JsSimSegment) {
        val segLen = s.vLength()
        val n = s.step
        val mp1 = (s.step + 1) % segLen
        val l = maxOf(1, segLen).toLong()
        val color2 = s.color_wheel(((n * 256) / l + (s.call and 0xFF)).i() and 0xFF)
        val color3 = s.color_wheel(((mp1 * 256) / l + (s.call and 0xFF)).i() and 0xFF)
        chase(s, s.SEGCOLOR(0), color2, color3, false)
    }

    private class ColorfulState { val cols = IntArray(9) }

    private fun colorful(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        val st = (s.state as? ColorfulState) ?: ColorfulState().also { s.state = it }
        var numColors = 4
        val cols = st.cols
        java.util.Arrays.fill(cols, 0)
        cols[0] = 0x00FF0000; cols[1] = 0x00EEBB00; cols[2] = 0x0000EE00; cols[3] = 0x000077CC

        if (s.intensity > 160 || s.palette != 0) {
            if (s.palette == 0) {
                numColors = 3
                for (i in 0 until 3) cols[i] = s.SEGCOLOR(i)
            } else {
                var fac = 80
                if (s.palette == 52) { numColors = 5; fac = 61 }
                for (i in 0 until numColors) cols[i] = s.color_from_palette(i * fac, false, true, 255)
            }
        } else if (s.intensity < 80) {
            cols[0] = 0x00FF8040; cols[1] = 0x00E5D241
            cols[2] = 0x0077FF77; cols[3] = 0x0077F0F0
        }
        for (i in numColors until numColors * 2 - 1) cols[i] = cols[i - numColors]

        val cycleTime = 50 + 8 * (255 - s.speed)
        val it = m.getNow() / cycleTime
        if (it != s.step) {
            if (s.speed > 0) s.aux0++
            if (s.aux0 >= numColors) s.aux0 = 0
            s.step = it
        }
        var i = 0
        while (i < segLen) {
            for (j in 0 until numColors) {
                val k = s.aux0 + j
                s.setPixelColor(i + j, if (k in 0..8) cols[k] else 0)
            }
            i += numColors
        }
    }

    private class TetrixDrop {
        var pos = 0.0
        var speed = 0.0
        var col = 0
        var brick = 0
        var stack = 0
        var step = 0L
    }

    private fun tetrix(s: JsSimSegment) {
        val m = s.m
        val segLen = s.vLength()
        if (segLen <= 1) return
        var drop = s.state as? TetrixDrop
        if (s.call == 0 || drop == null) {
            drop = TetrixDrop()
            s.state = drop
        }
        if (s.call == 0) {
            drop.stack = 0
            drop.step = m.getNow() + 2000
            if (s.check1) drop.col = 0
        }

        if (drop.step == 0L) {
            var speed = if (s.speed != 0) s.speed else m.jsRandom8(1, 255)
            speed = m.map(speed, 1, 255, 5000, 250)
            drop.speed = (segLen.toDouble() * m.FRAMETIME) / speed
            drop.pos = segLen.toDouble()
            if (!s.check1) drop.col = m.jsRandom8(0, 15) shl 4
            drop.step = 1
            drop.brick = (if (s.intensity != 0) (s.intensity shr 5) + 1 else m.jsRandom8(1, 5)) * (1 + (segLen shr 6))
        }

        if (drop.step == 1L) {
            if ((m.jsRandom8() shr 6) != 0) drop.step = 2
        }

        if (drop.step == 2L) {
            if (drop.pos > drop.stack) {
                drop.pos -= drop.speed
                if (drop.pos < drop.stack) drop.pos = drop.stack.toDouble()
                val fp = Math.floor(drop.pos).toInt()
                for (i in fp until segLen) {
                    val col = if (i < fp + drop.brick) s.color_from_palette(drop.col, false, false, 0) else s.SEGCOLOR(1)
                    s.setPixelColor(i, col)
                }
            } else {
                drop.step = 0
                drop.stack += drop.brick
                if (drop.stack >= segLen) drop.step = m.getNow() + 2000
            }
        }

        if (drop.step > 2) {
            drop.brick = 0
            if (drop.step > m.getNow()) {
                for (i in 0 until segLen) s.blendPixelColor(i, s.SEGCOLOR(1), 25)
            } else {
                drop.stack = 0
                drop.step = 0
                if (s.check1) drop.col += 8
            }
        }
    }

    @Suppress("unused") private fun keepImports(x: Double) = jsTrunc(x)
}
