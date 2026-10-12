/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-09-25.
 */

package fx

import java.util.SplittableRandom

internal class JsSimMath {

    @get:JvmName("nowMs") @set:JvmName("nowMs")
    var now: Long = 0L

    @Suppress("PropertyName")
    var FRAMETIME: Int = 22

    val FRAMETIME_FIXED: Int = 1000 / 58

    fun getNow(): Long = now
    fun setNow(ms: Long) { now = ms }

    var randSeed: Int = 1

    val mathRandom: SplittableRandom = SplittableRandom(0x5EED5EEDL)

    fun mathRandom(): Double = mathRandom.nextDouble()

    fun random8(): Int {
        val d = randSeed.toDouble() * 1103515245.0 + 12345.0
        randSeed = d.toLong().toInt() and 0x7FFFFFFF
        return (randSeed shr 16) and 0xFF
    }

    fun random8(lim: Int): Int { val r = random8(); return if (lim == 0) 0 else r % lim }

    fun random8(lo: Int, hi: Int): Int { val r = random8(); return if (hi - lo == 0) lo else lo + r % (hi - lo) }

    fun random16(): Int { val a = random8(); val b = random8(); return (a shl 8) or b }
    fun random16(lim: Int): Int { val r = random16(); return if (lim == 0) 0 else r % lim }
    fun random16(lo: Int, hi: Int): Int { val r = random16(); return if (hi - lo == 0) lo else lo + r % (hi - lo) }

    fun jsRandom8(): Int = (mathRandom() * 256).toInt()
    fun jsRandom8(lim: Int): Int = kotlin.math.floor(mathRandom() * lim).toInt()
    fun jsRandom8(lo: Int, hi: Int): Int = lo + kotlin.math.floor(mathRandom() * (hi - lo)).toInt()
    fun jsRandom16(): Int = (mathRandom() * 65536).toInt()
    fun jsRandom16(lim: Int): Int = kotlin.math.floor(mathRandom() * lim).toInt()
    fun jsRandom16(lo: Int, hi: Int): Int = lo + kotlin.math.floor(mathRandom() * (hi - lo)).toInt()

    fun sin8(x: Int): Int = SIN8[x and 0xFF]
    fun cos8(x: Int): Int = COS8[x and 0xFF]
    fun sin8_t(x: Int): Int = SIN8[x and 0xFF]
    fun cos8_t(x: Int): Int = COS8[x and 0xFF]
    fun sin8(x: Double): Int = SIN8[i32(x) and 0xFF]
    fun cos8(x: Double): Int = COS8[i32(x) and 0xFF]

    fun sin_t(x: Double): Double = StrictMath.sin(x)
    fun cos_t(x: Double): Double = StrictMath.cos(x)
    fun atan2_t(y: Double, x: Double): Double = StrictMath.atan2(y, x)
    fun radians(deg: Double): Double = deg * Math.PI / 180.0

    fun sin16(theta: Int): Int = SIN16[theta and 0xFFFF].toInt()
    fun sin16(theta: Double): Int = sin16(i32(theta))
    fun cos16(theta: Int): Int = sin16(theta + 16384)
    fun cos16(theta: Double): Int = sin16(theta + 16384)

    fun triwave8(x: Int): Int { val v = x and 0xFF; return if (v < 128) v * 2 else (255 - v) * 2 }
    fun triwave16(x: Int): Int { val v = x and 0xFFFF; return if (v < 32768) v * 2 else (65535 - v) * 2 }
    fun triwave16(x: Long): Int = triwave16(x.toInt())
    fun cubicwave8(x: Int): Int = ease8InOutCubic(triwave8(x))
    fun quadwave8(x: Int): Int = sin8(x)

    fun ease8InOutCubic(xIn: Int): Int {
        var x = xIn and 0xFF
        if (x < 128) return (x * x * x) shr 14
        x = 255 - x
        return 255 - ((x * x * x) shr 14)
    }

    fun ease8InOutApprox(xIn: Int): Int {
        var x = xIn and 0xFF
        if (x < 64) return x shr 1
        if (x > 191) return 255 - ((255 - x) shr 1)
        x -= 64
        return (x + (x shr 1) + 32) and 0xFF
    }

    fun beat88(bpm88: Int, timebase: Long = 0L): Int {
        val elapsed = (now - timebase).toInt()
        val p = (elapsed * bpm88) * 280
        return (p ushr 16) and 0xFFFF
    }
    fun beat16(bpm: Int, timebase: Long = 0L): Int = beat88((bpm * 256) and 0xFFFF, timebase)
    fun beat16(bpm: Double, timebase: Long = 0L): Int = beat88(i32(bpm * 256) and 0xFFFF, timebase)
    fun beat8(bpm: Int, timebase: Long = 0L): Int = (beat16(bpm, timebase) shr 8) and 0xFF
    fun beat8(bpm: Double, timebase: Long = 0L): Int = (beat16(bpm, timebase) shr 8) and 0xFF

    fun beatsin8(bpm: Int, lowest: Int = 0, highest: Int = 255, timebase: Long = 0L, phaseShift: Int = 0): Int {
        val b = beat8(bpm, timebase)
        val s = sin8(b + phaseShift)
        return lowest + ((s * (highest - lowest)) shr 8)
    }
    fun beatsin8(bpm: Double, lowest: Int = 0, highest: Int = 255, timebase: Long = 0L, phaseShift: Int = 0): Int {
        val b = beat8(bpm, timebase)
        val s = sin8(b + phaseShift)
        return lowest + ((s * (highest - lowest)) shr 8)
    }
    fun beatsin8_t(bpm: Int, lowest: Int = 0, highest: Int = 255, timebase: Long = 0L, phaseShift: Int = 0): Int =
        beatsin8(bpm, lowest, highest, timebase, phaseShift)

    fun beatsin16(bpm: Int, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int {
        val b = beat16(bpm, timebase)
        val raw = (sin16(b + phaseShift) + 32768) and 0xFFFF
        return lowest + Math.floorDiv(raw.toLong() * (highest - lowest + 1), 65536L).toInt()
    }
    fun beatsin16(bpm: Double, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int {
        val b = beat16(bpm, timebase)
        val raw = (sin16(b + phaseShift) + 32768) and 0xFFFF
        return lowest + Math.floorDiv(raw.toLong() * (highest - lowest + 1), 65536L).toInt()
    }
    fun beatsin16_t(bpm: Int, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int =
        beatsin16(bpm, lowest, highest, timebase, phaseShift)

    fun beatsin88(bpm88: Int, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int {
        val b = beat88(bpm88, timebase)
        val raw = (sin16(b + phaseShift) + 32768) and 0xFFFF
        return lowest + Math.floorDiv(raw.toLong() * (highest - lowest + 1), 65536L).toInt()
    }
    fun beatsin88(bpm88: Double, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int =
        beatsin88(i32(bpm88), lowest, highest, timebase, phaseShift)
    fun beatsin88_t(bpm88: Int, lowest: Int = 0, highest: Int = 65535, timebase: Long = 0L, phaseShift: Int = 0): Int =
        beatsin88(bpm88, lowest, highest, timebase, phaseShift)
    fun beat16_t(bpm: Int, timebase: Long = 0L): Int = beat16(bpm, timebase)
    fun beat8_t(bpm: Int, timebase: Long = 0L): Int = beat8(bpm, timebase)

    fun scale8(value: Int, scale: Int): Int = (value * (1 + scale)) shr 8
    fun scale16(value: Int, scale: Int): Int = (((value.toLong() * scale.toLong()).toInt()) ushr 16)

    fun map(x: Int, inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int {
        val den = (inMax - inMin).toDouble()
        val v = (x - inMin).toDouble() * (outMax - outMin) / den
        return jsTrunc(v).toInt() + outMin
    }
    fun map(x: Double, inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double =
        jsTrunc((x - inMin) * (outMax - outMin) / (inMax - inMin)) + outMin
    fun mapf(x: Double, inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double =
        (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

    fun constrain(x: Int, lo: Int, hi: Int): Int = kotlin.math.min(hi, kotlin.math.max(lo, x))
    fun constrain(x: Double, lo: Double, hi: Double): Double = kotlin.math.min(hi, kotlin.math.max(lo, x))
    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun qadd8(a: Int, b: Int): Int { val t = a + b; return if (t > 255) 255 else t }
    fun qsub8(a: Int, b: Int): Int { val r = a - b; return if (r < 0) 0 else r }
    fun lerp8by8(a: Int, b: Int, frac: Int): Int = (a + (((b - a) * frac) shr 8)) and 0xFF
    fun sqrt32_bw(v: Double): Int = kotlin.math.floor(kotlin.math.sqrt(kotlin.math.max(0.0, v))).toInt()
    fun sqrt32_bw(v: Int): Int = sqrt32_bw(v.toDouble())
    fun abs8(x: Int): Int = kotlin.math.abs(x) and 0xFF

    fun SPEED_FORMULA_L(speed: Int, seglen: Int): Int = 5 + (50 * (255 - speed)) / kotlin.math.max(1, seglen)

    fun tristate_square8(t: Long, tPulse: Int, tPause: Int): Int {
        val period = tPulse + tPause
        val cycle = period * 4
        val pos = t % cycle
        if (pos < tPulse) return 255
        if (pos < tPulse + tPause) return 128
        if (pos < 2 * tPulse + tPause) return 0
        return 128
    }

    fun colorPack(r: Int, g: Int, b: Int, w: Int = 0): Int =
        ((w and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun colorR(c: Int): Int = (c shr 16) and 0xFF
    fun colorG(c: Int): Int = (c shr 8) and 0xFF
    fun colorB(c: Int): Int = c and 0xFF
    fun colorW(c: Int): Int = (c shr 24) and 0xFF

    fun color_blend(c1: Int, c2: Int, blend: Int): Int {
        if (blend == 0) return c1
        if (blend == 255) return c2
        val ib = 255 - blend
        val r = (colorR(c1) * ib + colorR(c2) * blend) shr 8
        val g = (colorG(c1) * ib + colorG(c2) * blend) shr 8
        val b = (colorB(c1) * ib + colorB(c2) * blend) shr 8
        val w = (colorW(c1) * ib + colorW(c2) * blend) shr 8
        return colorPack(r, g, b, w)
    }

    fun color_add(c1: Int, c2: Int, preserveCR: Boolean = false): Int {
        if (preserveCR) {
            val r = colorR(c1) + colorR(c2); val g = colorG(c1) + colorG(c2)
            val b = colorB(c1) + colorB(c2); val w = colorW(c1) + colorW(c2)
            val mx = kotlin.math.max(kotlin.math.max(r, g), kotlin.math.max(b, w))
            if (mx > 255) {
                val scale = 255.0 / mx
                return colorPack(jsRound(r * scale).toInt(), jsRound(g * scale).toInt(),
                    jsRound(b * scale).toInt(), jsRound(w * scale).toInt())
            }
            return colorPack(r, g, b, w)
        }
        return colorPack(
            kotlin.math.min(255, colorR(c1) + colorR(c2)), kotlin.math.min(255, colorG(c1) + colorG(c2)),
            kotlin.math.min(255, colorB(c1) + colorB(c2)), kotlin.math.min(255, colorW(c1) + colorW(c2)),
        )
    }

    fun color_fade(c: Int, amount: Int, video: Boolean = false): Int {
        if (c == 0 || amount == 0) return 0
        if (amount == 255) return c
        val r = colorR(c); val g = colorG(c); val b = colorB(c); val w = colorW(c)
        return if (video) {
            val maxc = (kotlin.math.max(r, kotlin.math.max(g, b)) shr 2) + 1
            val nr = ((r * amount + 0x7F) shr 8) or (if (r > maxc) 1 else 0)
            val ng = ((g * amount + 0x7F) shr 8) or (if (g > maxc) 1 else 0)
            val nb = ((b * amount + 0x7F) shr 8) or (if (b > maxc) 1 else 0)
            val nw = ((w * amount + 0x7F) shr 8) or (if (w != 0) 1 else 0)
            colorPack(nr, ng, nb, nw)
        } else {
            colorPack((r * amount) shr 8, (g * amount) shr 8, (b * amount) shr 8, (w * amount) shr 8)
        }
    }

    fun fast_color_scale(c: Int, scale: Int): Int {
        val rb = (((c.toLong() and 0x00FF00FFL) * scale).toInt()) shr 8
        val gw = ((((c shr 8).toLong() and 0x00FF00FFL) * scale).toInt()) shr 8
        return ((gw and 0x00FF00FF) shl 8) or (rb and 0x00FF00FF)
    }

    fun color_wheel(pos: Int): Int = hsv2rgb(pos and 0xFF, 255, 255)

    fun get_random_wheel_index(prevIndex: Int): Int {
        var idx = 0; var d = 0
        while (d < 42) {
            idx = random8()
            val x = kotlin.math.abs(prevIndex - idx) and 0xFF
            val y = 255 - x
            d = kotlin.math.min(x, y)
        }
        return idx
    }

    fun hsv2rgb(hIn: Int, sIn: Int, vIn: Int): Int {
        val h = hIn and 0xFF
        val s = kotlin.math.min(255, kotlin.math.max(0, sIn))
        val v = kotlin.math.min(255, kotlin.math.max(0, vIn))
        if (s == 0) return colorPack(v, v, v)
        val offset = h and 0x1F
        val offset8 = (offset shl 3) and 0xFF
        val third = ((offset8 * 86) shr 8) and 0xFF
        var r: Int; var g: Int; var b: Int
        if ((h and 0x80) == 0) {
            if ((h and 0x40) == 0) {
                if ((h and 0x20) == 0) { r = 255 - third; g = third; b = 0 } else { r = 171; g = 85 + third; b = 0 }
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
            val satscale = 255 - desat
            r = (((r and 0xFF) * (1 + satscale)) shr 8) + desat
            g = (((g and 0xFF) * (1 + satscale)) shr 8) + desat
            b = (((b and 0xFF) * (1 + satscale)) shr 8) + desat
        }
        if (v != 255) {
            r = ((r and 0xFF) * (1 + v)) shr 8
            g = ((g and 0xFF) * (1 + v)) shr 8
            b = ((b and 0xFF) * (1 + v)) shr 8
        }
        return colorPack(r and 0xFF, g and 0xFF, b and 0xFF)
    }

    fun HeatColor(temperatureIn: Int): Int {
        val temperature = kotlin.math.min(255, kotlin.math.max(0, temperatureIn))
        val t192 = jsRound((temperature / 255.0) * 191).toInt()
        val heatrampShift = (t192 and 0x3F) shl 2
        return when {
            (t192 and 0x80) != 0 -> colorPack(255, 255, heatrampShift)
            (t192 and 0x40) != 0 -> colorPack(255, heatrampShift, 0)
            else -> colorPack(heatrampShift, 0, 0)
        }
    }

    fun rgb2hsv_packed(color: Int): Int {
        val r = colorR(color); val g = colorG(color); val b = colorB(color)
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        val d = mx - mn
        var h = 0
        val s = if (mx == 0) 0 else kotlin.math.floor(d * 255.0 / mx).toInt()
        if (d > 0) {
            h = when (mx) {
                r -> kotlin.math.floor(jsMod((g - b) * 255.0 / d + 256 * 6, 256.0 * 6) / 6).toInt()
                g -> kotlin.math.floor(85 + (b - r) * 255.0 / d / 6).toInt()
                else -> kotlin.math.floor(171 + (r - g) * 255.0 / d / 6).toInt()
            }
        }
        return ((h and 0xFF) shl 16) or ((s and 0xFF) shl 8) or (mx and 0xFF)
    }

    fun gamma8(v: Int): Int = GAMMA8[v and 0xFF]
    fun gamma8inv(v: Int): Int = GAMMA8INV[v and 0xFF]
    fun gamma32inv(c: Int): Int =
        colorPack(gamma8inv(colorR(c)), gamma8inv(colorG(c)), gamma8inv(colorB(c)), gamma8inv(colorW(c)))

    fun getAverageLight(c: Int): Int =
        kotlin.math.max(kotlin.math.max(colorR(c), colorG(c)), kotlin.math.max(colorB(c), colorW(c)))

    private fun hashGrad(h: Int): Int = (h and 0x03) - 2

    private fun grad1D(x0: Int, dx: Int): Int {
        var h = x0 * 0x27D4EB2D
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        h = h xor (h ushr 7)
        return (hashGrad(h) * dx) shr 1
    }

    private fun grad2D(x0: Int, dx: Int, y0: Int, dy: Int): Int {
        var h = (x0 * 0x27D4EB2D) xor (y0 * 0xB5297A4D.toInt())
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        val g1 = hashGrad(h)
        val g2 = hashGrad(h ushr 1)
        return (g1 * dx + g2 * dy) shr 2
    }

    private fun grad3D(x0: Int, dx: Int, y0: Int, dy: Int, z0: Int, dz: Int): Int {
        var h = (x0 * 0x27D4EB2D) xor (y0 * 0xB5297A4D.toInt()) xor (z0 * 0x1B56C4E9)
        h = h xor (h ushr 15)
        h *= 0x92C3412B.toInt()
        h = h xor (h ushr 13)
        val g1 = hashGrad(h)
        val g2 = hashGrad(h ushr 2)
        val g3 = hashGrad(h ushr 3)
        return ((g1 * dx + g2 * dy + g3 * dz) * 85) shr 9
    }

    private fun smoothstep(t: Int): Int {
        val tSq = ((t.toLong() * t.toLong()).toInt()) ushr 16
        val factor = 0x30000 - (t shl 1)
        return ((tSq.toLong() * factor.toLong()).toInt()) ushr 18
    }

    private fun plerp(a: Int, b: Int, t: Int): Int = a + (((b - a).toLong() * t.toLong()).toInt() shr 14)

    private fun perlin1Draw(x: Int, is16: Boolean): Int {
        val x0 = x ushr 16
        var x1 = x0 + 1
        if (is16) x1 = x1 and 0xFF
        val dx0 = x and 0xFFFF
        val dx1 = dx0 - 0x10000
        return plerp(grad1D(x0, dx0), grad1D(x1, dx1), smoothstep(dx0))
    }

    private fun perlin2Draw(x: Int, y: Int, is16: Boolean): Int {
        val x0 = x ushr 16; val y0 = y ushr 16
        var x1 = x0 + 1; var y1 = y0 + 1
        if (is16) { x1 = x1 and 0xFF; y1 = y1 and 0xFF }
        val dx0 = x and 0xFFFF; val dy0 = y and 0xFFFF
        val dx1 = dx0 - 0x10000; val dy1 = dy0 - 0x10000
        val g00 = grad2D(x0, dx0, y0, dy0); val g10 = grad2D(x1, dx1, y0, dy0)
        val g01 = grad2D(x0, dx0, y1, dy1); val g11 = grad2D(x1, dx1, y1, dy1)
        val tx = smoothstep(dx0); val ty = smoothstep(dy0)
        return plerp(plerp(g00, g10, tx), plerp(g01, g11, tx), ty)
    }

    private fun perlin3Draw(x: Int, y: Int, z: Int, is16: Boolean): Int {
        val x0 = x ushr 16; val y0 = y ushr 16; val z0 = z ushr 16
        var x1 = x0 + 1; var y1 = y0 + 1; var z1 = z0 + 1
        if (is16) { x1 = x1 and 0xFF; y1 = y1 and 0xFF; z1 = z1 and 0xFF }
        val dx0 = x and 0xFFFF; val dy0 = y and 0xFFFF; val dz0 = z and 0xFFFF
        val dx1 = dx0 - 0x10000; val dy1 = dy0 - 0x10000; val dz1 = dz0 - 0x10000
        val g000 = grad3D(x0, dx0, y0, dy0, z0, dz0); val g001 = grad3D(x0, dx0, y0, dy0, z1, dz1)
        val g010 = grad3D(x0, dx0, y1, dy1, z0, dz0); val g011 = grad3D(x0, dx0, y1, dy1, z1, dz1)
        val g100 = grad3D(x1, dx1, y0, dy0, z0, dz0); val g101 = grad3D(x1, dx1, y0, dy0, z1, dz1)
        val g110 = grad3D(x1, dx1, y1, dy1, z0, dz0); val g111 = grad3D(x1, dx1, y1, dy1, z1, dz1)
        val tx = smoothstep(dx0); val ty = smoothstep(dy0); val tz = smoothstep(dz0)
        val nx0 = plerp(g000, g100, tx); val nx1 = plerp(g010, g110, tx)
        val nx2 = plerp(g001, g101, tx); val nx3 = plerp(g011, g111, tx)
        val ny0 = plerp(nx0, nx1, ty); val ny1 = plerp(nx2, nx3, ty)
        return plerp(ny0, ny1, tz)
    }

    fun perlin16(x: Long): Int = (((perlin1Draw(x.toInt(), false) * 1159) shr 10) + 32803) and 0xFFFF
    fun perlin16(x: Long, y: Long): Int = (((perlin2Draw(x.toInt(), y.toInt(), false) * 1537) shr 10) + 32725) and 0xFFFF
    fun perlin16(x: Long, y: Long, z: Long): Int =
        (((perlin3Draw(x.toInt(), y.toInt(), z.toInt(), false) * 1731) shr 10) + 33147) and 0xFFFF

    fun perlin8(x: Long): Int = ((((perlin1Draw(((x.toInt() and 0xFFFF) shl 8), true) * 1353) shr 10) + 32769) shr 8) and 0xFF
    fun perlin8(x: Long, y: Long): Int =
        ((((perlin2Draw((x.toInt() and 0xFFFF) shl 8, (y.toInt() and 0xFFFF) shl 8, true) * 1620) shr 10) + 32771) shr 8) and 0xFF
    fun perlin8(x: Long, y: Long, z: Long): Int =
        ((((perlin3Draw((x.toInt() and 0xFFFF) shl 8, (y.toInt() and 0xFFFF) shl 8, (z.toInt() and 0xFFFF) shl 8, true) * 2015) shr 10) + 33168) shr 8) and 0xFF
    fun perlin8(x: Int): Int = perlin8(x.toLong())
    fun perlin8(x: Int, y: Int): Int = perlin8(x.toLong(), y.toLong())
    fun perlin8(x: Int, y: Int, z: Int): Int = perlin8(x.toLong(), y.toLong(), z.toLong())
    fun perlin16(x: Int): Int = perlin16(x.toLong())
    fun perlin16(x: Int, y: Int): Int = perlin16(x.toLong(), y.toLong())
    fun perlin16(x: Int, y: Int, z: Int): Int = perlin16(x.toLong(), y.toLong(), z.toLong())
    fun inoise8(x: Long): Int = perlin8(x)
    fun inoise8(x: Long, y: Long): Int = perlin8(x, y)
    fun inoise8(x: Long, y: Long, z: Long): Int = perlin8(x, y, z)
    fun inoise16(x: Long): Int = perlin16(x)
    fun inoise16(x: Long, y: Long): Int = perlin16(x, y)
    fun inoise16(x: Long, y: Long, z: Long): Int = perlin16(x, y, z)

    companion object {
        private val SIN8 = IntArray(256) {
            kotlin.math.max(0, kotlin.math.min(255, jsRound(127.5 + 127.5 * StrictMath.sin(it * 2 * Math.PI / 256)).toInt()))
        }
        private val COS8 = IntArray(256) {
            kotlin.math.max(0, kotlin.math.min(255, jsRound(127.5 + 127.5 * StrictMath.cos(it * 2 * Math.PI / 256)).toInt()))
        }
        private val SIN16 = ShortArray(65536) { t ->
            jsRound(StrictMath.sin((t / 65536.0) * 2 * Math.PI) * 32767).toInt().toShort()
        }
        private val GAMMA8 = IntArray(256) { jsRound(StrictMath.pow(it / 255.0, 2.8) * 255).toInt() }
        private val GAMMA8INV = IntArray(256) { jsRound(StrictMath.pow(it / 255.0, 1.0 / 2.8) * 255).toInt() }

        fun jsRound(x: Double): Double = kotlin.math.floor(x + 0.5)

        fun jsTrunc(x: Double): Double = if (x < 0) kotlin.math.ceil(x) else kotlin.math.floor(x)

        fun jsMod(a: Double, b: Double): Double = a % b

        fun i32(x: Double): Int {
            if (x.isNaN() || x.isInfinite()) return 0
            val t = jsTrunc(x)
            return if (kotlin.math.abs(t) < 9.0E18) t.toLong().toInt()
            else java.math.BigDecimal(t).toBigInteger().toInt()
        }

        fun u32(x: Double): Long = i32(x).toLong() and 0xFFFFFFFFL
    }
}
