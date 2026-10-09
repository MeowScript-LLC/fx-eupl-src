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

internal class JsSimSegment(length1D: Int, val width2D: Int, val height2D: Int, is2D: Boolean) {

    val m = JsSimMath()

    val length1D: Int = length1D
    var is2D: Boolean = is2D

    val pixels1D = IntArray(length1D)
    val pixels2D = IntArray(width2D * height2D)

    val pixels: IntArray get() = if (is2D) pixels2D else pixels1D

    var speed = 128
    var intensity = 128
    var custom1 = 128
    var custom2 = 128
    var custom3 = 16
    var check1 = false
    var check2 = false
    var check3 = false

    val colors = intArrayOf(0xFFAA00, 0x000000, 0x000000)
    var palette = 0

    var step = 0L
    var call = 0
    var aux0 = 0
    var aux1 = 0

    var data: ByteArray? = null
    var dataLen = 0

    var mode = 0
    val map1D2D = 0
    var soundSim = 0
    var scrollText: String = ""

    var state: Any? = null

    private var lastPeakVol: Int? = null
    private var peakHoldUntil: Long? = null

    fun vLength(): Int = if (is2D) width2D * height2D else length1D
    fun vWidth(): Int = width2D
    fun vHeight(): Int = height2D
    fun length1D(): Int = length1D

    fun setPixelColor(iIn: Int, col: Int) {
        var i = iIn
        if (i < 0) return
        if (is2D) {
            i = i and 0xFFFF
            if (i < width2D * height2D) pixels2D[i] = col
        } else if (i < length1D) {
            pixels1D[i] = col
        }
    }

    fun setPixelColor(i: Double, col: Int) = setPixelColor(JsSimMath.i32(i), col)

    fun getPixelColor(iIn: Int): Int {
        var i = iIn
        if (i < 0) return 0
        return if (is2D) {
            i = i and 0xFFFF
            if (i < pixels2D.size) pixels2D[i] else 0
        } else {
            if (i < pixels1D.size) pixels1D[i] else 0
        }
    }

    fun getPixelColor(i: Double): Int = getPixelColor(JsSimMath.i32(i))

    fun blendPixelColor(i: Int, col: Int, blend: Int) = setPixelColor(i, m.color_blend(getPixelColor(i), col, blend))
    fun addPixelColor(i: Int, col: Int) = setPixelColor(i, m.color_add(getPixelColor(i), col))

    fun setPixelColorRaw(i: Int, col: Int) { val b = pixels; if (i >= 0 && i < b.size) b[i] = col }
    fun getPixelColorRaw(i: Int): Int { val b = pixels; return if (i >= 0 && i < b.size) b[i] else 0 }

    fun setPixelColorXY(x: Int, y: Int, col: Int) {
        if (x < 0 || x >= width2D || y < 0 || y >= height2D) return
        pixels2D[x + y * width2D] = col
    }
    fun setPixelColorXY(x: Double, y: Double, col: Int) = setPixelColorXY(JsSimMath.i32(x), JsSimMath.i32(y), col)

    fun getPixelColorXY(x: Int, y: Int): Int {
        if (x < 0 || x >= width2D || y < 0 || y >= height2D) return 0
        return pixels2D[x + y * width2D]
    }
    fun getPixelColorXY(x: Double, y: Double): Int = getPixelColorXY(JsSimMath.i32(x), JsSimMath.i32(y))

    fun addPixelColorXY(x: Int, y: Int, col: Int) = setPixelColorXY(x, y, m.color_add(getPixelColorXY(x, y), col))
    fun blendPixelColorXY(x: Int, y: Int, col: Int, blend: Int) =
        setPixelColorXY(x, y, m.color_blend(getPixelColorXY(x, y), col, blend))
    fun fadePixelColorXY(x: Int, y: Int, fade: Int) =
        setPixelColorXY(x, y, m.fast_color_scale(getPixelColorXY(x, y), 255 - fade))

    fun fill(col: Int) = java.util.Arrays.fill(pixels, col)
    fun fillBlack() = java.util.Arrays.fill(pixels, 0)

    fun blur(amount: Int, smear: Boolean = false) {
        if (is2D) { blur2D(amount, amount, smear); return }
        blur1D(amount, smear)
    }

    private fun blur1D(amount: Int, smear: Boolean) {
        val buf = pixels1D
        val keep = if (smear) 255 else 255 - amount
        val seep = amount shr 1
        var carry = 0
        for (i in 0 until length1D) {
            val cur = buf[i]
            val part = m.fast_color_scale(cur, seep)
            var blurred = m.fast_color_scale(cur, keep)
            blurred = m.color_add(blurred, carry)
            if (i > 0) buf[i - 1] = m.color_add(buf[i - 1], part)
            buf[i] = blurred
            carry = part
        }
    }

    fun blur2D(blurX: Int, blurY: Int, smear: Boolean = false) {
        val w = width2D; val h = height2D; val buf = pixels2D
        val keepX = if (smear) 255 else 255 - blurX
        val seepX = blurX shr 1
        for (y in 0 until h) {
            var carry = 0
            for (x in 0 until w) {
                val idx = x + y * w
                val cur = buf[idx]
                val part = m.fast_color_scale(cur, seepX)
                var blurred = m.fast_color_scale(cur, keepX)
                blurred = m.color_add(blurred, carry)
                if (x > 0) buf[idx - 1] = m.color_add(buf[idx - 1], part)
                buf[idx] = blurred
                carry = part
            }
        }
        val keepY = if (smear) 255 else 255 - blurY
        val seepY = blurY shr 1
        for (x in 0 until w) {
            var carry = 0
            for (y in 0 until h) {
                val idx = x + y * w
                val cur = buf[idx]
                val part = m.fast_color_scale(cur, seepY)
                var blurred = m.fast_color_scale(cur, keepY)
                blurred = m.color_add(blurred, carry)
                if (y > 0) buf[idx - w] = m.color_add(buf[idx - w], part)
                buf[idx] = blurred
                carry = part
            }
        }
    }

    fun blurCols(amount: Int) = blur2D(0, amount, false)
    fun blurRows(amount: Int) = blur2D(amount, 0, false)

    fun fade_out(rate: Int) {
        val buf = pixels
        val r2 = (256 - rate) shr 1
        val mappedRate = 256 / (r2 + 1)
        val bg = colors[1]
        if (mappedRate == 0) return
        val bgR = m.colorR(bg); val bgG = m.colorG(bg); val bgB = m.colorB(bg)
        for (i in buf.indices) {
            val c = buf[i]
            if (c == bg) continue
            var r = m.colorR(c); var g = m.colorG(c); var b = m.colorB(c)
            var dR = (bgR - r) * mappedRate / 256
            var dG = (bgG - g) * mappedRate / 256
            var dB = (bgB - b) * mappedRate / 256
            if (dR == 0 && r != bgR) dR = if (r > bgR) -1 else 1
            if (dG == 0 && g != bgG) dG = if (g > bgG) -1 else 1
            if (dB == 0 && b != bgB) dB = if (b > bgB) -1 else 1
            r += dR; g += dG; b += dB
            buf[i] = m.colorPack(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }
    }

    fun fadeToBlackBy(amount: Int) {
        val buf = pixels
        val scale = 255 - amount
        for (i in buf.indices) buf[i] = m.fast_color_scale(buf[i], scale)
    }

    fun fadeToSecondaryBy(amount: Int) {
        val buf = pixels
        val bg = colors[1]
        for (i in buf.indices) buf[i] = m.color_blend(buf[i], bg, amount)
    }

    fun move(dir: Int, delta: Int = 1, wrap: Boolean = false) {
        if (!is2D) return
        val mx = MOVE_X[dir] * delta
        val my = MOVE_Y[dir] * delta
        if (mx != 0) moveX(mx, wrap)
        if (my != 0) moveY(my, wrap)
    }

    private fun moveX(delta: Int, wrap: Boolean) {
        val w = width2D; val h = height2D; val buf = pixels2D
        for (y in 0 until h) {
            if (delta > 0) {
                for (x in 0 until w) {
                    val src = x + delta
                    buf[x + y * w] = if (src < w) buf[src + y * w] else if (wrap) buf[(src % w) + y * w] else 0
                }
            } else {
                val ad = -delta
                for (x in w - 1 downTo 0) {
                    val src = x - ad
                    buf[x + y * w] = if (src >= 0) buf[src + y * w] else if (wrap) buf[((src % w) + w) % w + y * w] else 0
                }
            }
        }
    }

    private fun moveY(delta: Int, wrap: Boolean) {
        val w = width2D; val h = height2D; val buf = pixels2D
        if (delta > 0) {
            for (y in h - 1 downTo 0) {
                val src = y - delta
                for (x in 0 until w) {
                    buf[x + y * w] = if (src >= 0) buf[x + src * w] else if (wrap) buf[x + (((src % h) + h) % h) * w] else 0
                }
            }
        } else {
            val ad = -delta
            for (y in 0 until h) {
                val src = y + ad
                for (x in 0 until w) {
                    buf[x + y * w] = if (src < h) buf[x + src * w] else if (wrap) buf[x + (src % h) * w] else 0
                }
            }
        }
    }

    fun drawLine(x0In: Int, y0In: Int, x1: Int, y1: Int, col: Int) {
        var x0 = x0In; var y0 = y0In
        val dx = kotlin.math.abs(x1 - x0); val dy = kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        while (true) {
            setPixelColorXY(x0, y0, col)
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x0 += sx }
            if (e2 < dx) { err += dx; y0 += sy }
        }
    }

    fun drawCircle(cx: Int, cy: Int, radius: Int, col: Int) {
        var x = radius; var y = 0; var err = 1 - radius
        while (x >= y) {
            setPixelColorXY(cx + x, cy + y, col); setPixelColorXY(cx + y, cy + x, col)
            setPixelColorXY(cx - y, cy + x, col); setPixelColorXY(cx - x, cy + y, col)
            setPixelColorXY(cx - x, cy - y, col); setPixelColorXY(cx - y, cy - x, col)
            setPixelColorXY(cx + y, cy - x, col); setPixelColorXY(cx + x, cy - y, col)
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
    }

    fun fillCircle(cx: Int, cy: Int, radius: Int, col: Int) {
        val r2 = radius * radius
        for (y in -radius..radius) for (x in -radius..radius) if (x * x + y * y <= r2) setPixelColorXY(cx + x, cy + y, col)
    }

    fun allocateData(len: Int): Boolean {
        if (dataLen >= len) return true
        data = ByteArray(len)
        dataLen = len
        return true
    }

    fun deallocateData() { data = null; dataLen = 0 }

    fun d8(i: Int): Int = data!![i].toInt() and 0xFF
    fun d8(i: Int, v: Int) { data!![i] = v.toByte() }

    fun dU16(off: Int): Int = d8(off) or (d8(off + 1) shl 8)
    fun dU16(off: Int, v: Int) { d8(off, v); d8(off + 1, v shr 8) }
    fun dI16(off: Int): Int = dU16(off).toShort().toInt()
    fun dU32(off: Int): Long = (d8(off) or (d8(off + 1) shl 8) or (d8(off + 2) shl 16) or (d8(off + 3) shl 24)).toLong() and 0xFFFFFFFFL
    fun dU32(off: Int, v: Long) { val x = v.toInt(); d8(off, x); d8(off + 1, x shr 8); d8(off + 2, x shr 16); d8(off + 3, x shr 24) }
    fun dF32(off: Int): Float = java.lang.Float.intBitsToFloat(dU32(off).toInt())
    fun dF32(off: Int, v: Double) = dU32(off, java.lang.Float.floatToRawIntBits(v.toFloat()).toLong() and 0xFFFFFFFFL)

    fun reset() {
        step = 0; call = 0; aux0 = 0; aux1 = 0
        data?.let { java.util.Arrays.fill(it, 0) }
        state = null
        lastPeakVol = null
        peakHoldUntil = null
        fillBlack()
    }

    fun SEGCOLOR(i: Int): Int = colors.getOrElse(i) { 0 }

    var effectDefaultPalette = 0

    fun effectivePalette(): Int {
        if (palette != 0) return palette
        return if (effectDefaultPalette > 0) effectDefaultPalette else 6
    }

    fun color_from_palette(indexIn: Int, mapping: Boolean = true, wrap: Boolean = false, mcol: Int = 255, pbri: Int = 255): Int {
        if (palette == 0 && mcol < 3) {
            return m.color_fade(colors.getOrElse(mcol) { 0 }, pbri, true)
        }
        val effPal = effectivePalette()
        var index = indexIn
        if (mapping) {
            val vLen = vLength()
            index = if (vLen > 0) kotlin.math.min(Math.floorDiv(index * 255, vLen), 255) else 0
        }
        val blendType = if (mapping && !wrap) 2 else 1
        return paletteColor(effPal, index, pbri, blendType)
    }

    fun color_from_palette(index: Double, mapping: Boolean = true, wrap: Boolean = false, mcol: Int = 255, pbri: Int = 255): Int {
        if (palette == 0 && mcol < 3) return m.color_fade(colors.getOrElse(mcol) { 0 }, pbri, true)
        if (mapping) {
            val vLen = vLength()
            val idx = if (vLen > 0) kotlin.math.min(kotlin.math.floor((index * 255) / vLen), 255.0) else 0.0
            return paletteColor(effectivePalette(), JsSimMath.i32(idx), pbri, if (!wrap) 2 else 1)
        }
        return paletteColor(effectivePalette(), JsSimMath.i32(index), pbri, 1)
    }

    fun color_wheel(pos: Int): Int =
        if (palette != 0) color_from_palette(pos, false, false, 0) else m.color_wheel(pos)

    fun ColorFromPalette(index: Int, pbri: Int = 255, blendType: Int = 1): Int =
        paletteColor(effectivePalette(), index, pbri, blendType)

    fun resetPalettes() { randomPalette = null; randomPaletteTime = 0L; dynKeyId = -1 }

    private var randomPalette: IntArray? = null
    private var randomPaletteTime = 0L
    private val dynPalette = IntArray(16)
    private var dynKeyId = -1
    private var dynKey0 = 0; private var dynKey1 = 0; private var dynKey2 = 0

    fun getPalette(paletteId: Int): IntArray {
        if (paletteId <= 5) return dynamicPalette(paletteId)
        return jsTablePalette(paletteId)
    }

    private fun dynamicPalette(id: Int): IntArray {
        val c0 = if (colors[0] != 0) colors[0] else 0xFFAA00
        val c1 = colors[1]; val c2 = colors[2]
        when (id) {
            1 -> {
                val now = m.getNow()
                val rp = randomPalette
                if (rp == null || now - randomPaletteTime > 5000) {
                    val pal = IntArray(16)
                    for (i in 0 until 16) pal[i] = m.color_wheel(kotlin.math.floor(m.mathRandom() * 256).toInt()) and 0xFFFFFF
                    randomPalette = pal
                    randomPaletteTime = now
                    return pal
                }
                return rp
            }
            2, 3, 4, 5 -> {
                if (dynKeyId == id && dynKey0 == c0 && dynKey1 == c1 && dynKey2 == c2) return dynPalette
                val p = dynPalette
                val r0 = (c0 shr 16) and 0xFF; val g0 = (c0 shr 8) and 0xFF; val b0 = c0 and 0xFF
                val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
                when (id) {
                    2 -> for (i in 0 until 16) p[i] = rgb24(r0, g0, b0)
                    3 -> for (i in 0 until 16) {
                        p[i] = if (i <= 5) rgb24(r0, g0, b0)
                        else if (i <= 10) {
                            val s = (i - 5) / 5.0
                            rgb24(JsSimMath.jsRound(r0 + (r1 - r0) * s).toInt(), JsSimMath.jsRound(g0 + (g1 - g0) * s).toInt(),
                                JsSimMath.jsRound(b0 + (b1 - b0) * s).toInt())
                        } else rgb24(r1, g1, b1)
                    }
                    4 -> {
                        val seq = intArrayOf(c2, c2, c1, c1, c1, c0, c0, c0, c2, c2, c1, c1, c0, c0, c2, c2)
                        for (i in 0 until 16) p[i] = seq[i] and 0xFFFFFF
                    }
                    5 -> for (i in 0 until 16) {
                        p[i] = if (c2 != 0) {
                            when { i < 5 -> c0; i < 10 -> c1; i < 15 -> c2; else -> c0 } and 0xFFFFFF
                        } else (if (i < 8) c0 else c1) and 0xFFFFFF
                    }
                }
                dynKeyId = id; dynKey0 = c0; dynKey1 = c1; dynKey2 = c2
                return p
            }
            else -> return jsTablePalette(6)
        }
    }

    private fun rgb24(r: Int, g: Int, b: Int): Int = ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun paletteColor(paletteId: Int, indexIn: Int, brightness: Int = 255, blendType: Int = 1): Int {
        val pal = getPalette(paletteId)
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
            val s = brightness + 1
            r = (r * s) shr 8; g = (g * s) shr 8; b = (b * s) shr 8
        }
        return ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    fun getVolume(): Int = when (soundSim) {
        1 -> {
            val t = m.getNow() % 2000
            when {
                t < 200 -> m.jsRandom8(); t < 400 -> 0; t < 600 -> m.jsRandom8(); t < 800 -> 0
                t < 1000 -> m.jsRandom8(); else -> 0
            }
        }
        2 -> m.beatsin8(10, 0, 255)
        3 -> m.beatsin8(3, 0, 255)
        else -> m.beatsin8(120 / 9, 0, 255)
    }

    fun getFFT(binIn: Int): Int {
        val bin = kotlin.math.max(0, kotlin.math.min(15, binIn))
        return when (soundSim) {
            1 -> {
                val t = m.getNow() % 2000
                when {
                    t < 200 && bin < 5 -> m.jsRandom8(); t < 400 -> 0
                    t < 600 && bin >= 5 && bin < 11 -> m.jsRandom8(); t < 800 -> 0
                    t < 1000 && bin >= 11 -> m.jsRandom8(); else -> 0
                }
            }
            2 -> m.beatsin8(10 + bin, 0, 255)
            3 -> m.beatsin8(14 + bin * 3, 0, 255)
            else -> m.beatsin8(120 / (bin + 1), 0, 255)
        }
    }

    fun getMajorPeak(): Double {
        var maxBin = 0; var maxVal = 0
        for (b in 0 until 16) { val fv = getFFT(b); if (fv > maxVal) { maxVal = fv; maxBin = b } }
        val freq = 80.0 * Math.pow(2.0, maxBin * 6.0 / 15.0)
        return kotlin.math.max(1.0, kotlin.math.min(freq, 11025.0))
    }

    fun getMagnitude(): Double = getVolume() * 16.0

    fun getSamplePeak(): Int {
        val vol = getVolume()
        val lpv = lastPeakVol ?: 0
        val phu = peakHoldUntil ?: 0L
        val now = m.getNow()
        var peak = 0
        if (vol > lpv + 10 && vol > 60) { peak = 1; peakHoldUntil = now + 50 }
        else if (now < phu) peak = 1
        if (peakHoldUntil == null) peakHoldUntil = phu
        lastPeakVol = vol
        return peak
    }

    companion object {
        private val MOVE_X = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        private val MOVE_Y = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

        private val tableCache = arrayOfNulls<IntArray>(NativeEffectPalettes.LAST_ID + 1)

        fun jsTablePalette(id: Int): IntArray {
            val pid = if (NativeEffectPalettes.offsetOf(id) >= 0) id else 11
            tableCache[pid]?.let { return it }
            val off = NativeEffectPalettes.offsetOf(pid)
            val out = IntArray(16) { NativeEffectPalettes.TABLE[off + it] }
            tableCache[pid] = out
            return out
        }
    }
}
