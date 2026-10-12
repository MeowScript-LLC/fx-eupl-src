/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * FX_2Dfcn.cpp: Copyright (c) 2022 Blaz Kristan. Licensed under the EUPL v. 1.2 or later.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

package fx

import fx.JsSimMath.Companion.i32
import fx.JsSimMath.Companion.jsRound
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object JsFx2DB7 : JsFxBatch {
    override val ids: IntArray = intArrayOf(119, 120, 121, 125, 126, 127, 152, 153, 164, 172, 174, 182)
    override val twoD: Boolean get() = true

    override fun run(id: Int, s: JsSimSegment): Boolean {
        when (id) {
            119 -> crazyBees(s)
            120 -> ghostRider(s)
            121 -> blobs(s)
            125 -> soap(s)
            126 -> octopus(s)
            127 -> wavingCell(s)
            152 -> dna(s)
            153 -> matrix(s)
            164 -> drift(s)
            172 -> gameOfLife(s)
            174 -> polarLights(s)
            182 -> dnaSpiral(s)
            else -> return false
        }
        return true
    }

    private fun wuPixel(s: JsSimSegment, x: Int, y: Int, col: Int) {
        val m = s.m
        val xi = x shr 8
        val yi = y shr 8
        val xf = x and 0xFF
        val yf = y and 0xFF
        val ixf = 255 - xf
        val iyf = 255 - yf
        s.addPixelColorXY(xi, yi, m.color_fade(col, (ixf * iyf) shr 8))
        s.addPixelColorXY(xi + 1, yi, m.color_fade(col, (xf * iyf) shr 8))
        s.addPixelColorXY(xi, yi + 1, m.color_fade(col, (ixf * yf) shr 8))
        s.addPixelColorXY(xi + 1, yi + 1, m.color_fade(col, (xf * yf) shr 8))
    }

    private fun fastColorScaleD(c: Int, scale: Double): Int {
        val rb = i32((c.toLong() and 0x00FF00FFL).toDouble() * scale) shr 8
        val gw = i32(((c shr 8).toLong() and 0x00FF00FFL).toDouble() * scale) shr 8
        return ((gw and 0x00FF00FF) shl 8) or (rb and 0x00FF00FF)
    }

    private fun fadeToBlackByD(s: JsSimSegment, amount: Double) {
        val buf = s.pixels
        val scale = 255 - amount
        for (i in buf.indices) buf[i] = fastColorScaleD(buf[i], scale)
    }

    private class BeeState {
        val errors = ByteArray(5)
        var step = 0.0
    }

    private const val BEE_SIZE = 10
    private const val MAX_BEES = 5

    private fun crazyBees(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()
        val n = min(MAX_BEES, (rows * cols) / 256 + 1)
        if (!s.allocateData(BEE_SIZE * MAX_BEES)) return

        var st = s.state as? BeeState
        if (s.call == 0) {
            for (i in 0 until n) {
                val off = i * BEE_SIZE
                s.d8(off + 0, m.jsRandom8(0, cols))
                s.d8(off + 1, m.jsRandom8(0, rows))
                val aimX = m.jsRandom8(0, cols)
                val aimY = m.jsRandom8(0, rows)
                val hue = m.jsRandom8()
                s.d8(off + 2, aimX)
                s.d8(off + 3, aimY)
                s.d8(off + 4, hue)
                s.d8(off + 5, abs(aimX - s.d8(off + 0)))
                s.d8(off + 6, abs(aimY - s.d8(off + 1)))
                s.d8(off + 7, if (s.d8(off + 0) < aimX) 1 else 255)
                s.d8(off + 8, if (s.d8(off + 1) < aimY) 1 else 255)
                if (st == null) { st = BeeState(); s.state = st }
                st.errors[i] = (s.d8(off + 5) - s.d8(off + 6)).toByte()
            }
        }
        if (st == null) { st = BeeState(); s.state = st }
        val errs = st.errors

        val c1 = if (s.check1) 1 else 0
        if (m.getNow().toDouble() > st.step) {
            st.step = m.getNow() + (m.FRAMETIME * 16.0 / ((s.speed shr 4) + 1))
            fadeToBlackByD(s, 32 + ((c1 * s.intensity) / 25.0))
            s.blur(s.intensity / (2 + c1 * 9), s.check1)
            for (i in 0 until n) {
                val off = i * BEE_SIZE
                val posX = s.d8(off + 0)
                val posY = s.d8(off + 1)
                val aimX = s.d8(off + 2)
                val aimY = s.d8(off + 3)
                val hue = s.d8(off + 4)
                val deltaX = s.d8(off + 5)
                val deltaY = s.d8(off + 6)
                val signX = if (s.d8(off + 7) == 1) 1 else -1
                val signY = if (s.d8(off + 8) == 1) 1 else -1

                val flowerColor = s.color_from_palette(hue, false, true, 255)
                s.addPixelColorXY(aimX + 1, aimY, flowerColor)
                s.addPixelColorXY(aimX, aimY + 1, flowerColor)
                s.addPixelColorXY(aimX - 1, aimY, flowerColor)
                s.addPixelColorXY(aimX, aimY - 1, flowerColor)

                if (posX != aimX || posY != aimY) {
                    s.setPixelColorXY(posX, posY, m.hsv2rgb(hue, 60, 255))
                    val error2 = errs[i] * 2
                    if (error2 > -deltaY) {
                        errs[i] = (errs[i] - deltaY).toByte()
                        s.d8(off + 0, posX + signX)
                    }
                    if (error2 < deltaX) {
                        errs[i] = (errs[i] + deltaX).toByte()
                        s.d8(off + 1, posY + signY)
                    }
                } else {
                    val nAimX = m.jsRandom8(0, cols)
                    val nAimY = m.jsRandom8(0, rows)
                    val nHue = m.jsRandom8()
                    s.d8(off + 2, nAimX)
                    s.d8(off + 3, nAimY)
                    s.d8(off + 4, nHue)
                    s.d8(off + 5, abs(nAimX - s.d8(off + 0)))
                    s.d8(off + 6, abs(nAimY - s.d8(off + 1)))
                    s.d8(off + 7, if (s.d8(off + 0) < nAimX) 1 else 255)
                    s.d8(off + 8, if (s.d8(off + 1) < nAimY) 1 else 255)
                    errs[off / BEE_SIZE] = (s.d8(off + 5) - s.d8(off + 6)).toByte()
                }
            }
        }
    }

    private const val LIGHTERS_AM = 64

    private class GhostState(cols: Int, rows: Int, m: JsSimMath) {
        var gPosX = (cols / 2) * 10
        var gPosY = (rows / 2) * 10
        var gAngle = JsSimMath.u32(m.mathRandom() * 65536).toInt()
        val angleSpeed = m.jsRandom8(0, 20) - 10
        val vSpeed = 5
        val lightersPosX = IntArray(LIGHTERS_AM)
        val lightersPosY = IntArray(LIGHTERS_AM)
        val angle = IntArray(LIGHTERS_AM)
        val time = IntArray(LIGHTERS_AM)
        val reg = IntArray(LIGHTERS_AM)
    }

    private fun ghostRider(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()
        val maxLighters = min(cols + rows, LIGHTERS_AM)

        var g = s.state as? GhostState
        if (s.aux0 != cols || s.aux1 != rows || g == null) {
            s.aux0 = cols
            s.aux1 = rows
            g = GhostState(cols, rows, m)
            s.state = g
            for (i in 0 until maxLighters) {
                g.lightersPosX[i] = g.gPosX and 0xFFFF
                g.lightersPosY[i] = (g.gPosY + i) and 0xFFFF
                g.time[i] = (i * 2) and 0xFFFF
                g.reg[i] = 0
            }
        }

        if (m.getNow() > s.step) {
            s.step = m.getNow() + 1024 / (cols + rows)
            s.fadeToBlackBy((s.speed shr 2) + 64)

            wuPixel(s, Math.floorDiv(g.gPosX * 256, 10), Math.floorDiv(g.gPosY * 256, 10), 0xFFFFFF)

            g.gPosX += floor(g.vSpeed * s.m.sin_t(m.radians(g.gAngle.toDouble()))).toInt()
            g.gPosY += floor(g.vSpeed * s.m.cos_t(m.radians(g.gAngle.toDouble()))).toInt()
            g.gAngle = (g.gAngle + g.angleSpeed) and 0xFFFF
            if (g.gPosX < 0) g.gPosX = (cols - 1) * 10
            if (g.gPosX > (cols - 1) * 10) g.gPosX = 0
            if (g.gPosY < 0) g.gPosY = (rows - 1) * 10
            if (g.gPosY > (rows - 1) * 10) g.gPosY = 0

            for (i in 0 until maxLighters) {
                g.time[i] = (g.time[i] + m.jsRandom8(5, 20)) and 0xFFFF
                if (g.time[i] >= 255 ||
                    g.lightersPosX[i] <= 0 ||
                    g.lightersPosX[i] >= (cols - 1) * 10 ||
                    g.lightersPosY[i] <= 0 ||
                    g.lightersPosY[i] >= (rows - 1) * 10
                ) {
                    g.reg[i] = 1
                }
                if (g.reg[i] != 0) {
                    g.lightersPosY[i] = g.gPosY and 0xFFFF
                    g.lightersPosX[i] = g.gPosX and 0xFFFF
                    g.angle[i] = (g.gAngle + m.jsRandom8(0, 20) - 10) and 0xFFFF
                    g.time[i] = 0
                    g.reg[i] = 0
                } else {
                    g.lightersPosX[i] = (g.lightersPosX[i] + floor(-7 * m.sin_t(m.radians(g.angle[i].toDouble()))).toInt()) and 0xFFFF
                    g.lightersPosY[i] = (g.lightersPosY[i] + floor(-7 * m.cos_t(m.radians(g.angle[i].toDouble()))).toInt()) and 0xFFFF
                }
                wuPixel(
                    s,
                    (g.lightersPosX[i] * 256) / 10,
                    (g.lightersPosY[i] * 256) / 10,
                    s.ColorFromPalette((256 - g.time[i]) and 0xFF),
                )
            }
            s.blur(s.intensity shr 3)
        }
    }

    private const val MAX_BLOBS = 8

    private class Blob {
        var r = 0.0; var sX = 0.0; var sY = 0.0; var x = 0.0; var y = 0.0
        var color = 0; var grow = false
    }

    private class BlobState { val blobs = Array(MAX_BLOBS) { Blob() } }

    private fun blobs(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()
        val amount = (s.intensity shr 5) + 1

        var st = s.state as? BlobState
        if (s.aux0 != cols || s.aux1 != rows || st == null) {
            s.aux0 = cols
            s.aux1 = rows
            st = BlobState()
            s.state = st
            for (i in 0 until MAX_BLOBS) {
                val blob = st.blobs[i]
                blob.r = m.jsRandom8(1, if (cols > 8) cols / 4 else 2).toDouble()
                blob.sX = m.jsRandom8(3, cols) / (256.0 - s.speed)
                blob.sY = m.jsRandom8(3, rows) / (256.0 - s.speed)
                blob.x = m.jsRandom8(0, cols - 1).toDouble()
                blob.y = m.jsRandom8(0, rows - 1).toDouble()
                blob.color = m.jsRandom8()
                blob.grow = blob.r < 1.0
                if (blob.sX == 0.0) blob.sX = 1.0
                if (blob.sY == 0.0) blob.sY = 1.0
            }
        }

        s.fadeToBlackBy((s.custom2 shr 3) + 1)

        for (i in 0 until amount) {
            val blob = st.blobs[i]
            if (s.step < m.getNow()) blob.color = (blob.color + 4) and 0xFF

            val dr = (if (abs(blob.sX) > abs(blob.sY)) abs(blob.sX) else abs(blob.sY)) * 0.05
            if (blob.grow) {
                blob.r += dr
                if (blob.r >= min(cols / 4.0, 2.0)) blob.grow = false
            } else {
                blob.r -= dr
                if (blob.r < 1.0) blob.grow = true
            }
            val c = s.color_from_palette(blob.color, false, false, 0)
            val rx = jsRound(blob.x).toInt(); val ry = jsRound(blob.y).toInt()
            if (blob.r > 1.0) s.fillCircle(rx, ry, jsRound(blob.r).toInt(), c)
            else s.setPixelColorXY(rx, ry, c)

            if (blob.x + blob.r >= cols - 1) blob.x += (blob.sX * ((cols - 1 - blob.x) / blob.r + 0.005))
            else if (blob.x - blob.r <= 0) blob.x += (blob.sX * (blob.x / blob.r + 0.005))
            else blob.x += blob.sX
            if (blob.y + blob.r >= rows - 1) blob.y += (blob.sY * ((rows - 1 - blob.y) / blob.r + 0.005))
            else if (blob.y - blob.r <= 0) blob.y += (blob.sY * (blob.y / blob.r + 0.005))
            else blob.y += blob.sY

            if (blob.x < 0.01) {
                blob.sX = m.jsRandom8(3, cols) / (256.0 - s.speed)
                blob.x = 0.01
            } else if (blob.x > cols - 1.01) {
                blob.sX = m.jsRandom8(3, cols) / (256.0 - s.speed)
                blob.sX = -blob.sX
                blob.x = cols - 1.01
            }
            if (blob.y < 0.01) {
                blob.sY = m.jsRandom8(3, rows) / (256.0 - s.speed)
                blob.y = 0.01
            } else if (blob.y > rows - 1.01) {
                blob.sY = m.jsRandom8(3, rows) / (256.0 - s.speed)
                blob.sY = -blob.sY
                blob.y = rows - 1.01
            }
        }
        s.blur(s.custom1 shr 2)

        if (s.step < m.getNow()) s.step = m.getNow() + 2000
    }

    private class SoapState(val cols: Int, val rows: Int) {
        val noise = IntArray(cols * rows)
        val pixels = IntArray(cols * rows)
        val coord = LongArray(3)
        val ledsbuff = IntArray(max(cols, rows))
    }

    private fun soap(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        var st = s.state as? SoapState
        if (s.call == 0 || st == null || st.cols != cols || st.rows != rows) {
            st = SoapState(cols, rows)
            s.state = st
            st.coord[0] = JsSimMath.u32(m.mathRandom() * 0xFFFFFFFFL.toDouble())
            st.coord[1] = JsSimMath.u32(m.mathRandom() * 0xFFFFFFFFL.toDouble())
            st.coord[2] = JsSimMath.u32(m.mathRandom() * 0xFFFFFFFFL.toDouble())
        }
        val noise3d = st.noise
        val pixels = st.pixels
        val noisecoord = st.coord
        val scale32x = 160000 / cols
        val scale32y = 160000 / rows
        val mov = (min(cols, rows) * (s.speed + 2)) / 2
        val smoothness = min(250, s.intensity)

        if (s.call != 0) {
            for (i in 0 until 3) noisecoord[i] = (noisecoord[i] + mov) and 0xFFFFFFFFL
        }

        for (i in 0 until cols) {
            val ioffset = scale32x.toLong() * (i - cols / 2)
            for (j in 0 until rows) {
                val joffset = scale32y.toLong() * (j - rows / 2)
                val data = m.inoise16(noisecoord[0] + ioffset, noisecoord[1] + joffset, noisecoord[2]) shr 8
                val k = i + j * cols
                noise3d[k] = (m.scale8(noise3d[k], smoothness) + m.scale8(data and 0xFF, 255 - smoothness)) and 0xFF
            }
        }

        if (s.call == 0 || s.aux0 != cols || s.aux1 != rows) {
            s.aux0 = cols
            s.aux1 = rows
            for (i in 0 until cols) {
                for (j in 0 until rows) {
                    val c = s.ColorFromPalette((noise3d[i + j * cols].inv() and 0xFF) * 3)
                    s.setPixelColorXY(i, j, c)
                    pixels[i + j * cols] = c
                }
            }
        }

        soapPass(s, st, true)
        soapPass(s, st, false)
    }

    private fun soapPass(s: JsSimSegment, st: SoapState, isRow: Boolean) {
        val m = s.m
        val cols = st.cols
        val rows = st.rows
        val noise3d = st.noise
        val pixels = st.pixels
        val ledsbuff = st.ledsbuff
        val tRC = if (isRow) rows else cols
        val tCR = if (isRow) cols else rows
        val amplitude = max(1, (tCR - 8) shr 3) * (1 + (s.custom1 shr 5))

        for (i in 0 until tRC) {
            val noiseIdx = if (isRow) i * cols else i
            val amount = (noise3d[noiseIdx] - 128) * amplitude
            val delta = abs(amount) shr 8
            val fraction = abs(amount) and 255

            for (j in 0 until tCR) {
                val zD: Int; val zF: Int
                if (amount < 0) { zD = j - delta; zF = zD - 1 } else { zD = j + delta; zF = zD + 1 }
                var yA = abs(zD) % tCR
                var yB = abs(zF) % tCR
                var xA = i; var xB = i
                if (isRow) {
                    var tmp = xA; xA = yA; yA = tmp
                    tmp = xB; xB = yB; yB = tmp
                }
                val indxA = xA + yA * cols
                val indxB = xB + yB * cols
                val pixelA = if (zD >= 0 && zD < tCR) pixels[indxA]
                else s.ColorFromPalette((noise3d[indxA].inv() and 0xFF) * 3)
                val pixelB = if (zF >= 0 && zF < tCR) pixels[indxB]
                else s.ColorFromPalette((noise3d[indxB].inv() and 0xFF) * 3)

                val easeA = m.ease8InOutApprox(255 - fraction)
                val easeB = m.ease8InOutApprox(fraction)
                val rA = (m.colorR(pixelA) * easeA) shr 8; val gA = (m.colorG(pixelA) * easeA) shr 8; val bA = (m.colorB(pixelA) * easeA) shr 8
                val rB = (m.colorR(pixelB) * easeB) shr 8; val gB = (m.colorG(pixelB) * easeB) shr 8; val bB = (m.colorB(pixelB) * easeB) shr 8
                ledsbuff[j] = m.colorPack(min(255, rA + rB), min(255, gA + gB), min(255, bA + bB))
            }
            for (j in 0 until tCR) {
                var ii = i; var jj = j
                if (isRow) { val tmp = ii; ii = jj; jj = tmp }
                pixels[ii + jj * cols] = ledsbuff[j]
                s.setPixelColorXY(ii, jj, ledsbuff[j])
            }
        }
    }

    private class OctoState(val cols: Int, val rows: Int, val offX: Int, val offY: Int) {
        var step = 0
        val angle = IntArray(cols * rows)
        val radius = IntArray(cols * rows)
    }

    private fun octopus(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()
        val mapp = 180 / max(cols, rows)

        var st = s.state as? OctoState
        if (s.call == 0 || st == null || st.cols != cols || st.rows != rows ||
            st.offX != s.custom1 || st.offY != s.custom2
        ) {
            st = OctoState(cols, rows, s.custom1, s.custom2)
            s.state = st
            val cX = cols / 2 + floor((s.custom1 - 128) * cols / 255.0).toInt()
            val cY = rows / 2 + floor((s.custom2 - 128) * rows / 255.0).toInt()
            for (x in 0 until cols) {
                for (y in 0 until rows) {
                    val dx = x - cX
                    val dy = y - cY
                    val k = x + y * cols
                    st.angle[k] = i32(floor(40.7436 * m.atan2_t(dy.toDouble(), dx.toDouble()))) and 0xFF
                    st.radius[k] = i32(floor(sqrt((dx * dx + dy * dy).toDouble()) * mapp)) and 0xFF
                }
            }
        }

        st.step += s.speed / 32 + 1
        val step = st.step

        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val k = x + y * cols
                val angle = st.angle[k]
                val radius = st.radius[k]
                var intens = m.sin8(
                    (m.sin8((((angle * 4 - radius) shr 2) + (step shr 1)) and 0xFF) + radius - step + angle * ((s.custom3 shr 2) + 1)) and 0xFF,
                )
                intens = m.map((intens * intens) and 0xFFFF, 0, 65535, 0, 255)
                s.setPixelColorXY(x, y, s.ColorFromPalette(((step shr 1) - radius) and 0xFF, intens))
            }
        }
    }

    private fun wavingCell(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        val t = (i32((m.getNow() * (s.speed + 1)).toDouble()) shr 3).toLong() and 0xFFFFFFFFL
        val aX = s.custom1 / 16 + 9
        val aY = s.custom2 / 16 + 1
        val aZ = s.custom3 + 1
        val c2 = if (s.check2) 1 else 0
        val tShift = t.toInt() shr (8 - c2 * 3)

        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val inner = (((y.toLong() shl 8) + t) * aY).toInt() shr 8
                val wave = (m.sin8(((x * aX) + m.sin8(inner)) and 0xFF) + m.cos8((y * aZ) and 0xFF)) and 0xFF
                val colorIndex = (wave + tShift) and 0xFF
                s.setPixelColorXY(x, y, s.ColorFromPalette(colorIndex))
            }
        }
        s.blur(s.intensity)
    }

    private fun dna(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        s.fadeToBlackBy(64)
        for (i in 0 until cols) {
            val spd = s.speed / 8
            val t17 = (m.getNow() / 17).toInt()
            s.setPixelColorXY(
                i,
                m.beatsin8(spd, 0, rows - 1, 0, i * 4),
                s.ColorFromPalette(i * 5 + t17, m.beatsin8(5, 55, 255, 0, i * 10)),
            )
            s.setPixelColorXY(
                i,
                m.beatsin8(spd, 0, rows - 1, 0, i * 4 + 128),
                s.ColorFromPalette(i * 5 + 128 + t17, m.beatsin8(5, 55, 255, 0, i * 10 + 128)),
            )
        }
        s.blur(s.intensity / (8 - (if (s.check1) 2 else 0)), s.check1)
    }

    private fun matrix(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        val dataSize = (s.vLength() + 7) shr 3
        if (!s.allocateData(dataSize)) return

        if (s.call == 0) {
            s.fill(0)
            s.step = 0
        }

        val fade = m.map(s.custom1, 0, 255, 50, 250)
        val speed = (256 - s.speed) shr m.map(min(rows, 150), 0, 150, 0, 3)

        val spawnColor: Int
        val trailColor: Int
        if (s.check1) {
            spawnColor = s.SEGCOLOR(0)
            trailColor = s.SEGCOLOR(1)
        } else {
            spawnColor = m.colorPack(175, 255, 175, 0)
            trailColor = m.colorPack(27, 130, 39, 0)
        }

        var emptyScreen = true
        if (m.getNow() - s.step >= speed) {
            s.step = m.getNow()
            s.fadeToBlackBy(fade)
            for (row in rows - 1 downTo 0) {
                for (col in 0 until cols) {
                    val idx = (col % cols) + (row % rows) * cols
                    val index = idx shr 3
                    val bitNum = idx and 0x07
                    if (((s.d8(index) shr bitNum) and 1) != 0) {
                        s.setPixelColorXY(col, row, trailColor)
                        s.d8(index, s.d8(index) and (1 shl bitNum).inv())
                        if (row < rows - 1) {
                            s.setPixelColorXY(col, row + 1, spawnColor)
                            val newIdx = (col % cols) + ((row + 1) % rows) * cols
                            val newIndex = newIdx shr 3
                            val newBitNum = newIdx and 0x07
                            s.d8(newIndex, s.d8(newIndex) or (1 shl newBitNum))
                            emptyScreen = false
                        }
                    }
                }
            }

            if (m.jsRandom8() <= s.intensity || emptyScreen) {
                val spawnX = m.jsRandom8(0, cols)
                s.setPixelColorXY(spawnX, 0, spawnColor)
                val spIdx = spawnX % cols
                val spIndex = spIdx shr 3
                val spBitNum = spIdx and 0x07
                s.d8(spIndex, s.d8(spIndex) or (1 shl spBitNum))
            }
        }
    }

    private fun drift(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        val colsCenter = (cols shr 1) + (cols % 2)
        val rowsCenter = (rows shr 1) + (rows % 2)

        s.fadeToBlackBy(128)
        val maxDim = max(cols, rows) / 2.0
        val t = m.getNow() / (32 - (s.speed shr 3))
        val t20 = t / 20

        var i = 1.0
        while (i < maxDim) {
            val angle = m.radians(t * (maxDim - i))
            val mySin = floor(m.sin_t(angle) * i).toInt()
            val myCos = floor(m.cos_t(angle) * i).toInt()
            s.setPixelColorXY(colsCenter + mySin, rowsCenter + myCos, s.ColorFromPalette(floor(i * 20 + t20).toInt(), 255))
            if (s.check1) {
                s.setPixelColorXY(colsCenter + myCos, rowsCenter + mySin, s.ColorFromPalette(floor(i * 20 + t20).toInt(), 255))
            }
            i += 0.25
        }
        s.blur(s.intensity shr (3 - (if (s.check2) 1 else 0)), s.check2)
    }

    private const val ALIVE = 1
    private const val FADED = 2
    private const val TOGGLE = 4
    private const val EDGE = 8
    private const val OSC_CHK = 16
    private const val SHIP_CHK = 32

    private class GolState { val parentIdx = IntArray(3) }

    private fun gameOfLife(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()
        val maxIndex = cols * rows

        if (s.call == 0 || s.data == null || s.dataLen < maxIndex) {
            s.allocateData(maxIndex)
            java.util.Arrays.fill(s.data!!, 0, s.dataLen, 0)
            s.aux0 = 0
            s.aux1 = 0
        }
        val st = (s.state as? GolState) ?: GolState().also { s.state = it }

        var generation = s.aux0
        var gliderLength = s.aux1
        val mutate = s.check3
        val blur = m.map(s.custom1, 0, 255, 255, 4)
        val bgColor = s.SEGCOLOR(1)
        var birthColor = s.color_from_palette(128, false, false, 255)

        val setup = s.call == 0
        if (setup) {
            var a = rows; var b = cols
            while (b != 0) { val t = b; b = a % b; a = t }
            gliderLength = (((cols * rows) / a) shl 2) and 0xFFFF
            s.aux1 = gliderLength
        }

        if (abs(m.getNow() - s.step) > 2000) s.step = 0
        var paused = s.step > m.getNow()

        if ((!paused && generation == 0) || setup) {
            s.step = m.getNow() + 1280
            generation = 1
            paused = true
            for (i in 0 until maxIndex) s.d8(i, 0)
            for (i in 0 until maxIndex) {
                val isAlive = m.jsRandom8(3) == 0
                if (isAlive) s.d8(i, s.d8(i) or ALIVE)
                if (!isAlive) s.d8(i, s.d8(i) or FADED)
                val x = i % cols; val y = i / cols
                if (x == 0 || x == cols - 1 || y == 0 || y == rows - 1) s.d8(i, s.d8(i) or EDGE)
                s.setPixelColor(i, if (isAlive) s.color_from_palette(m.jsRandom8(), false, false, 0) else bgColor)
            }
        }

        if (paused || (m.getNow() - s.step < 1000 / m.map(s.speed, 0, 255, 1, 42))) {
            for (i in maxIndex - 1 downTo 0) {
                if ((s.d8(i) and ALIVE) == 0) {
                    val cellColor = s.getPixelColor(i)
                    if (cellColor != bgColor) {
                        if ((s.d8(i) and FADED) != 0) {
                            s.setPixelColor(i, bgColor)
                        } else {
                            var blended = m.color_blend(cellColor, bgColor, 2)
                            if (blended == cellColor) { blended = bgColor; s.d8(i, s.d8(i) or FADED) }
                            s.setPixelColor(i, blended)
                        }
                    }
                }
            }
            s.aux0 = generation
            return
        }

        val updateOscillator = generation % 16 == 0
        val updateSpaceship = gliderLength != 0 && generation % gliderLength == 0
        var repeatingOscillator = true; var repeatingSpaceship = true; var emptyGrid = true
        val parentIdx = st.parentIdx

        var cIndex = maxIndex - 1
        for (y in rows - 1 downTo 0) {
            var x = cols - 1
            while (x >= 0) {
                val cell = s.d8(cIndex)
                val cellAlive = cell and ALIVE

                if (cellAlive != 0) emptyGrid = false
                if ((if ((cell and OSC_CHK) != 0) 1 else 0) != (if (cellAlive != 0) 1 else 0)) repeatingOscillator = false
                if ((if ((cell and SHIP_CHK) != 0) 1 else 0) != (if (cellAlive != 0) 1 else 0)) repeatingSpaceship = false
                if (updateOscillator) {
                    if (cellAlive != 0) s.d8(cIndex, s.d8(cIndex) or OSC_CHK) else s.d8(cIndex, s.d8(cIndex) and OSC_CHK.inv())
                }
                if (updateSpaceship) {
                    if (cellAlive != 0) s.d8(cIndex, s.d8(cIndex) or SHIP_CHK) else s.d8(cIndex, s.d8(cIndex) and SHIP_CHK.inv())
                }

                var neighbors = 0; var aliveParents = 0
                parentIdx[0] = 0; parentIdx[1] = 0; parentIdx[2] = 0
                for (di in -1..1) {
                    for (dj in -1..1) {
                        if (di == 0 && dj == 0) continue
                        var nX = x + dj; var nY = y + di
                        if ((cell and EDGE) != 0) {
                            nX = (nX + cols) % cols
                            nY = (nY + rows) % rows
                        }
                        if (nX < 0 || nX >= cols || nY < 0 || nY >= rows) continue
                        val nIndex = nX + nY * cols
                        val neighbor = s.d8(nIndex)
                        if ((neighbor and ALIVE) != 0) {
                            neighbors++
                            if ((neighbor and TOGGLE) == 0 && neighbors < 4) {
                                parentIdx[aliveParents++] = nIndex
                            }
                        }
                    }
                }

                var needsColor = false
                var newColor = 0

                if (cellAlive != 0 && (neighbors < 2 || neighbors > 3)) {
                    s.d8(cIndex, s.d8(cIndex) or TOGGLE)
                    if (blur == 255) s.d8(cIndex, s.d8(cIndex) or FADED)
                    newColor = if ((s.d8(cIndex) and FADED) != 0) bgColor else m.color_blend(s.getPixelColor(cIndex), bgColor, blur)
                    needsColor = true
                } else if (cellAlive == 0) {
                    val mutationRoll = if (mutate) m.jsRandom8(128) else 1
                    if ((neighbors == 3 && mutationRoll != 0) || (mutate && neighbors == 2 && mutationRoll == 0)) {
                        s.d8(cIndex, s.d8(cIndex) or TOGGLE)
                        s.d8(cIndex, s.d8(cIndex) and FADED.inv())
                        if (aliveParents != 0) {
                            val parentIndex = parentIdx[m.jsRandom8(aliveParents)]
                            birthColor = s.getPixelColor(parentIndex)
                        }
                        newColor = birthColor
                        needsColor = true
                    } else if ((s.d8(cIndex) and FADED) == 0) {
                        val cc = s.getPixelColor(cIndex)
                        var blended = m.color_blend(cc, bgColor, blur)
                        if (blended == cc) { blended = bgColor; s.d8(cIndex, s.d8(cIndex) or FADED) }
                        newColor = blended
                        needsColor = true
                    }
                }

                if (needsColor) s.setPixelColor(cIndex, newColor)
                x--; cIndex--
            }
        }

        for (i in maxIndex - 1 downTo 0) {
            if ((s.d8(i) and TOGGLE) != 0) s.d8(i, s.d8(i) xor ALIVE)
            s.d8(i, s.d8(i) and TOGGLE.inv())
        }

        if (repeatingOscillator || repeatingSpaceship || emptyGrid) {
            generation = 0
            s.step += 1024
        } else {
            generation++
            s.step = m.getNow()
        }
        s.aux0 = generation
    }

    private fun polarLights(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        if (s.call == 0) {
            s.fill(0)
            s.step = 0
        }

        val adjustHeight = m.map(rows, 8, 32, 28, 12)
        val adjScale = m.map(cols, 8, 64, 310, 63)
        val scale = m.map(s.intensity, 0, 255, 30, adjScale)
        val speed = m.map(s.speed, 0, 255, 128, 16)

        for (x in 0 until cols) {
            for (y in 0 until rows) {
                s.step = (s.step + 1) and 0xFFFFFFFFL
                val st = s.step
                var palindex = m.qsub8(
                    m.perlin8((st % 2) + x.toLong() * scale, y * 16L + st % 16, Math.floorDiv(st, speed.toLong())),
                    floor(abs(rows / 2.0 - y) * adjustHeight).toInt(),
                )
                val palbrightness = palindex
                if (s.check1) palindex = 255 - palindex
                s.setPixelColorXY(x, y, s.color_from_palette(palindex, false, false, 255, palbrightness))
            }
        }
    }

    private fun dnaSpiral(s: JsSimSegment) {
        val m = s.m
        val cols = s.vWidth()
        val rows = s.vHeight()

        if (s.call == 0) s.fill(0)

        val speeds = s.speed / 2 + 7
        val freq = s.intensity / 8
        val ms = m.getNow() / 20
        s.fadeToBlackBy(135)

        for (i in 0 until rows) {
            var x = m.beatsin8(speeds, 0, cols - 1, 0, i * freq) + m.beatsin8(speeds - 7, 0, cols - 1, 0, i * freq + 128)
            var x1 = m.beatsin8(speeds, 0, cols - 1, 0, 128 + i * freq) + m.beatsin8(speeds - 7, 0, cols - 1, 0, 128 + 64 + i * freq)
            val hue = ((i * 128 / rows) + ms).toInt() and 0xFF

            if (((i + ms / 8) and 3L) != 0L) {
                x /= 2
                x1 /= 2
                val steps = m.abs8(x - x1) + 1
                val positive = x1 >= x
                for (k in 1..steps) {
                    val rate = k * 255 / steps
                    val dx = if (positive) (x + k - 1) else (x - k + 1)
                    s.addPixelColorXY(dx, i, s.ColorFromPalette(hue, 255))
                    s.fadePixelColorXY(dx, i, rate)
                }
                s.setPixelColorXY(x, i, 0x2F4F4F)
                s.setPixelColorXY(x1, i, 0xFFFFFF)
            }
        }
        s.blur(s.custom1 * 3 / (6 + (if (s.check1) 1 else 0)), s.check1)
    }
}
