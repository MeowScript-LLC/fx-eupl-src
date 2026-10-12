/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * FXparticleSystem.cpp, FXparticleSystem.h: Copyright (c) 2024 Damian Schneider.
 * Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-09-25.
 */

package fx

internal class ParticleFx1D(private val length: Int, private val matrixWidth: Int = 0) {

    private val matrix = matrixWidth > 1 && length / matrixWidth > 1

    private var randSeed = 0L

    private fun rand(): Int {
        randSeed = randSeed * 6364136223846793005L + 1L
        return (randSeed ushr 33).toInt()
    }

    private fun hwRandom(): Int = rand()
    private fun hwRandom(upper: Int): Int {
        val u = upper.toLong() and 0xFFFFFFFFL
        return if (u != 0L) (rand().toLong() % u).toInt() else 0
    }
    private fun hwRandom8(): Int = rand() and 0xFF
    private fun hwRandom16(): Int = rand() and 0xFFFF
    private fun hwRandom16(upper: Int): Int {
        val u = upper.toLong() and 0xFFFFFFFFL
        return if (u != 0L) ((rand() and 0xFFFF).toLong() % u).toInt() else 0
    }
    private fun hwRandom16(lo: Int, hi: Int): Int {
        val range = hi - lo
        if (range <= 0) return lo.toShort().toInt()
        return (lo + hwRandom16(range)).toShort().toInt()
    }

    private fun i8(v: Int) = v.toByte().toInt()
    private fun u8(v: Int) = v and 0xFF
    private fun u16(v: Int) = v and 0xFFFF

    private fun map(x: Int, inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int =
        (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

    private fun limitSpeed(s: Int): Int = if (s > PS_P_MAXSPEED) PS_P_MAXSPEED else if (s < -PS_P_MAXSPEED) -PS_P_MAXSPEED else s
    private fun udiv(a: Int, b: Int): Int = Integer.divideUnsigned(a, b)
    private fun urem(a: Int, b: Int): Int = Integer.remainderUnsigned(a, b)
    private fun ult(a: Int, b: Int): Boolean = Integer.compareUnsigned(a, b) < 0
    private fun ule(a: Int, b: Int): Boolean = Integer.compareUnsigned(a, b) <= 0

    private fun sin16(theta: Int): Int = (SIN8[(theta and 0xFFFF) shr 8] - 128) * 256
    private fun cos8(theta: Int): Int = SIN8[(theta + 64) and 0xFF]
    private fun scale8(i: Int, s: Int): Int = ((i and 0xFF) * (1 + (s and 0xFF))) shr 8
    private fun lerp8by8(a: Int, b: Int, frac: Int): Int = if (b > a) a + scale8(b - a, frac) else a - scale8(a - b, frac)

    private fun cubicwave8(inV: Int): Int {
        val i = inV and 0xFF
        var n = (i shl 1) and 0xFF
        if ((i and 0x80) != 0) n = n.inv() and 0xFF
        return ((n * n * (3 * 256 - 2 * n)) ushr 16) and 0xFF
    }

    private fun noiseHash(xIn: Int): Int {
        var x = xIn
        x = ((x ushr 16) xor x) * 0x45d9f3b
        x = ((x ushr 16) xor x) * 0x45d9f3b
        x = (x ushr 16) xor x
        return x and 0xFF
    }

    private fun perlin8(xIn: Int, yIn: Int = 0, zIn: Int = 0): Int {
        val x = xIn and 0xFFFF; val y = yIn and 0xFFFF; val z = zIn and 0xFFFF
        val xi = x shr 8; val yi = y shr 8; val zi = z shr 8
        val xf = x and 0xFF; val yf = y and 0xFF
        val hz = noiseHash(zi)
        val a = noiseHash(xi + noiseHash(yi + hz))
        val b = noiseHash(xi + 1 + noiseHash(yi + hz))
        val c = noiseHash(xi + noiseHash(yi + 1 + hz))
        val d = noiseHash(xi + 1 + noiseHash(yi + 1 + hz))
        val ab = lerp8by8(a, b, xf) and 0xFF
        val cd = lerp8by8(c, d, xf) and 0xFF
        return lerp8by8(ab, cd, yf) and 0xFF
    }

    private fun sqrt32bw(n: Int): Int = kotlin.math.sqrt((n.toLong() and 0xFFFFFFFFL).toFloat()).toInt()

    private fun fastColorScale(c: Int, amount: Int): Int {
        val s = 1 + (amount and 0xFF)
        return ((((c ushr 24) and 0xFF) * s shr 8) shl 24) or
            ((((c shr 16) and 0xFF) * s shr 8) shl 16) or ((((c shr 8) and 0xFF) * s shr 8) shl 8) or ((c and 0xFF) * s shr 8)
    }

    private fun colorAdd(c1: Int, c2: Int): Int {
        val w = minOf(255, ((c1 ushr 24) and 0xFF) + ((c2 ushr 24) and 0xFF))
        val r = minOf(255, ((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF))
        val g = minOf(255, ((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF))
        val b = minOf(255, (c1 and 0xFF) + (c2 and 0xFF))
        return (w shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun fastColorScaleAdd(c1: Int, c2: Int, scale: Int): Int {
        val maskRB = 0x00FF00FFL; val maskG = 0x0000FF00L
        val sc = (scale and 0xFF).toLong()
        var rb = c2.toLong() and maskRB
        var g = c2.toLong() and maskG
        rb = ((rb * sc) shr 8) and maskRB
        g = ((g * sc) shr 8) and maskG
        rb = (c1.toLong() and maskRB) + rb
        g = (c1.toLong() and maskG) + g
        if (((rb or (g shr 8)) and 0x01000100L) != 0L) {
            g = g shr 8
            var maxVal = rb shr 16
            maxVal = if ((rb and 0xFFFF) > maxVal) rb and 0xFFFF else maxVal
            maxVal = if (g > maxVal) g else maxVal
            val sf = (255L shl 8) / maxVal
            rb = ((rb * sf) shr 8) and maskRB
            g = (g * sf) and maskG
        }
        return (rb or g).toInt()
    }

    private fun setSaturation(c: Int, sat: Int): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val maxval = if (r > g) (if (r > b) r else b) else (if (g > b) g else b)
        var h = 0; var v = 0
        if (maxval != 0) {
            val minval = if (r < g) (if (r < b) r else b) else (if (g < b) g else b)
            v = maxval
            val delta = maxval - minval
            if (delta != 0) {
                var hh = when (maxval) {
                    r -> (10923 * (g - b)) / delta
                    g -> (10923 * (b - r)) / delta + 21845
                    else -> (10923 * (r - g)) / delta + 43690
                }
                if (hh < 0) hh += 65536
                h = hh and 0xFFFF
            }
        }
        val s = sat and 0xFF
        if (s == 0) return (v shl 16) or (v shl 8) or v
        val region = (h * 6) ushr 16
        val remainder = (h - region * 10923) * 6
        val p = (v * (255 - s)) shr 8
        val q = (v * (255 - ((s * remainder) ushr 16))) ushr 8
        val t = (v * (255 - ((s * (65535 - remainder)) ushr 16))) ushr 8
        val pp = p and 0xFF; val qq = q and 0xFF; val tt = t and 0xFF
        return when (region) {
            0 -> (v shl 16) or (tt shl 8) or pp
            1 -> (qq shl 16) or (v shl 8) or pp
            2 -> (pp shl 16) or (v shl 8) or tt
            3 -> (pp shl 16) or (qq shl 8) or v
            4 -> (tt shl 16) or (pp shl 8) or v
            else -> (v shl 16) or (pp shl 8) or qq
        }
    }

    private val pal = IntArray(16)

    private fun loadPalette(palIn: Int, c0: Int, c1: Int, c2: Int) {
        var p = palIn
        if (p >= 74) p = 0
        if (p == 0) p = 6
        when (p) {
            1 -> for (i in 0 until 16) pal[i] = RGBY[i shr 2]
            2 -> java.util.Arrays.fill(pal, c0)
            3 -> for (i in 0 until 16) pal[i] = if (i < 8) c0 else c1
            4 -> for (i in 0 until 16) pal[i] = when (i) { 0, 1, 8, 9, 14, 15 -> c2; 2, 3, 4, 10, 11 -> c1; else -> c0 }
            5 -> if (c2 != 0) {
                for (i in 0 until 16) pal[i] = when { i < 5 -> c0; i < 10 -> c1; i < 15 -> c2; else -> c0 }
            } else for (i in 0 until 16) pal[i] = if (i < 8) c0 else c1
            else -> {
                val off = (p - PsWasmPalettes.FIRST_ID) * 16
                for (i in 0 until 16) pal[i] = PsWasmPalettes.TABLE[off + i]
            }
        }
    }

    private fun colorFromPalette(indexIn: Int): Int {
        val index = indexIn and 0xFF
        val hi4 = index shr 4; val lo4 = index and 0x0F
        val e = pal[hi4]
        if (lo4 == 0) return e
        val n = pal[(hi4 + 1) and 0x0F]
        val f2 = lo4 shl 4
        val r = lerp8by8((e shr 16) and 0xFF, (n shr 16) and 0xFF, f2) and 0xFF
        val g = lerp8by8((e shr 8) and 0xFF, (n shr 8) and 0xFF, f2) and 0xFF
        val b = lerp8by8(e and 0xFF, n and 0xFF, f2) and 0xFF
        return (r shl 16) or (g shl 8) or b
    }

    private var call = 0
    private var step = 0
    private var aux0 = 0
    private var aux1 = 0
    private var now = 0
    private var lastEffect = -1

    private var speed = 128; private var intensity = 128
    private var custom1 = 128; private var custom2 = 128; private var custom3 = 16
    private var check1 = false; private var check2 = false; private var check3 = false
    private var bgColor = 0

    private val fb = IntArray(maxOf(1, length))

    private val fft = IntArray(16)
    private var audioMs = 0L

    private fun audioTick() {
        audioMs += 22
        if (audioMs == 22L) {
            internalSynth((now + 22).toFloat() * 0.001f)
            return
        }
        for (b in 0 until 16) {
            val bpm = maxOf(1, 120 / (b + 1))
            val ph = (audioMs * bpm / 60000.0) * Math.PI * 2
            val s = (StrictMath.sin(ph) + 1) * 0.5
            fft[b] = kotlin.math.floor(s * 255).toInt() and 0xFF
        }
    }

    private fun internalSynth(t: Float) {
        val twoPi = (2.0 * Math.PI).toFloat()
        val beatPhase = t * 2.4f
        var beatEnv = 0.5f + 0.5f * kotlin.math.sin((beatPhase * twoPi).toDouble()).toFloat()
        beatEnv = beatEnv * beatEnv * beatEnv
        for (b in 0 until 16) {
            val bandPhase = t * (0.6f + 0.18f * b) + b * 0.91f
            val bandEnv = 0.5f + 0.5f * kotlin.math.sin((bandPhase * twoPi).toDouble()).toFloat()
            val bassWeight = if (b < 4) 1.0f else 0.55f
            val v = bassWeight * (0.30f * bandEnv + 0.70f * beatEnv * bandEnv)
            fft[b] = (v * 230.0f).toInt().coerceIn(0, 255)
        }
    }

    private val maxParticles = ((maxOf(10, minOf(length, MAXPARTICLES_1D)) + 3) and 3.inv())
    private val maxPool = maxParticles + MAXSOURCES_1D
    private val px = IntArray(maxPool)
    private val ttl = IntArray(maxPool)
    private val vx = IntArray(maxPool)
    private val hue = IntArray(maxPool)
    private val flags = IntArray(maxPool)

    private val aSat = IntArray(maxParticles)
    private val aSize = IntArray(maxParticles)
    private val aForce = IntArray(maxParticles)

    private val sMinLife = IntArray(MAXSOURCES_1D); private val sMaxLife = IntArray(MAXSOURCES_1D)
    private val sVar = IntArray(MAXSOURCES_1D); private val sV = IntArray(MAXSOURCES_1D)
    private val sSat = IntArray(MAXSOURCES_1D); private val sSize = IntArray(MAXSOURCES_1D)

    private var numParticles = 0
    private var usedParticles = 0
    private var numSources = 0
    private var advanced = false
    private var perParticleSize = false
    private var psValid = false

    private var settings = 0
    private var maxX = 0; private var maxXpixel = 0
    private var emitIndex = 0
    private var collisionHardness = 0
    private var particleHardRadius = 0
    private var wallHardness = 0
    private var gforceCounter = 0
    private var gforce = 0
    private var forceCounter = 0
    private var collisionStartIdx = 0
    private var particleSize = 0
    private var motionBlur = 0
    private var smearBlur = 0

    private var padVx = 0
    private var padHue = 0

    private var fxData0 = 0
    private var fxData1 = 0

    private val binIndices = IntArray(maxOf(50, (maxParticles + 1) / 4) + 1)
    private val springForce = IntArray(maxParticles)

    private fun src(j: Int) = numParticles + j

    private fun has(opts: Int, bit: Int) = (opts and bit) != 0
    private fun setBit(bit: Int, on: Boolean) { settings = if (on) settings or bit else settings and bit.inv() }
    private fun flag(i: Int, bit: Int) = (flags[i] and bit) != 0
    private fun setFlag(i: Int, bit: Int, on: Boolean) { flags[i] = if (on) flags[i] or bit else flags[i] and bit.inv() }

    private fun initPS(requestedSources: Int, fraction: Int = 255, isAdvanced: Boolean = false): Boolean {
        if (length <= 1) return false
        var n = minOf(length, MAXPARTICLES_1D)
        if (isAdvanced) n = (n * 8) / (8 + 3)
        n = (n * (fraction + 1)) shr 8
        n = if (n < 10) 10 else n
        n = (n + 3) and 3.inv()
        var ns = maxOf(1, minOf(requestedSources, MAXSOURCES_1D))
        ns = (ns + 3) and 3.inv()
        java.util.Arrays.fill(px, 0); java.util.Arrays.fill(ttl, 0); java.util.Arrays.fill(vx, 0)
        java.util.Arrays.fill(hue, 0); java.util.Arrays.fill(flags, 0)
        java.util.Arrays.fill(aSat, 0); java.util.Arrays.fill(aSize, 0); java.util.Arrays.fill(aForce, 0)
        for (a in arrayOf(sMinLife, sMaxLife, sVar, sV, sSat, sSize)) java.util.Arrays.fill(a, 0)
        java.util.Arrays.fill(fb, 0)
        settings = 0; gforceCounter = 0; forceCounter = 0; collisionHardness = 0; gforce = 0
        padVx = 0; padHue = 0; fxData0 = 0; fxData1 = 0
        numSources = ns
        numParticles = n
        usedParticles = n
        advanced = isAdvanced
        setSize()
        wallHardness = 255
        setGravity(0)
        setParticleSize(0)
        motionBlur = 0; smearBlur = 0
        emitIndex = 0; collisionStartIdx = 0
        for (j in 0 until ns) { ttl[src(j)] = 1; flags[src(j)] = 0 }
        perParticleSize = isAdvanced
        if (isAdvanced) for (i in 0 until n) aSat[i] = 255
        psValid = true
        return true
    }

    private fun setSize() { maxXpixel = length - 1; maxX = length * PS_P_RADIUS_1D - 1 }
    private fun setUsedParticles(percentage: Int) { usedParticles = maxOf(1, (numParticles * ((percentage and 0xFF) + 1)) shr 8) }
    private fun setWallHardness(h: Int) { wallHardness = h and 0xFF }
    private fun setWrap(e: Boolean) = setBit(WRAP, e)
    private fun setBounce(e: Boolean) = setBit(BOUNCE, e)
    private fun setKillOutOfBounds(e: Boolean) = setBit(KILLOOB, e)
    private fun setColorByAge(e: Boolean) = setBit(COLORBYAGE, e)
    private fun setColorByPosition(e: Boolean) = setBit(COLORBYPOS, e)
    private fun setMotionBlur(b: Int) { motionBlur = b and 0xFF }
    private fun setSmearBlur(b: Int) { smearBlur = b and 0xFF }
    private fun setParticleSize(sizeIn: Int) {
        particleSize = sizeIn and 0xFF
        particleHardRadius = PS_P_MINHARDRADIUS_1D
        perParticleSize = false
        if (particleSize > 1) particleHardRadius = PS_P_MINHARDRADIUS_1D + ((particleSize * 52) shr 6)
        else if (particleSize == 0) particleHardRadius = PS_P_MINHARDRADIUS_1D shr 1
    }
    private fun setGravity(forceIn: Int) {
        val f = i8(forceIn)
        if (f != 0) { gforce = f; setBit(USEGRAVITY, true) } else setBit(USEGRAVITY, false)
    }
    private fun enableParticleCollisions(enable: Boolean, hardness: Int = 255) {
        setBit(USECOLLISIONS, enable)
        collisionHardness = hardness and 0xFF
    }

    private fun sprayEmit(j: Int): Int {
        for (k in 0 until usedParticles) {
            emitIndex++
            if (emitIndex >= usedParticles) emitIndex = 0
            val e = emitIndex
            if (ttl[e] == 0) {
                val v = sVar[j]
                vx[e] = i8(sV[j] + hwRandom16(v shl 1) - v)
                val s = src(j)
                px[e] = px[s]
                hue[e] = hue[s]
                ttl[e] = u16(hwRandom16(sMinLife[j], sMaxLife[j]))
                setFlag(e, F_COLLIDE, flag(s, F_COLLIDE))
                setFlag(e, F_REVERSEGRAV, flag(s, F_REVERSEGRAV))
                setFlag(e, F_PERPETUAL, flag(s, F_PERPETUAL))
                if (advanced) { aSat[e] = sSat[j]; aSize[e] = sSize[j] }
                return e
            }
        }
        return -1
    }

    private var cbPos = 0
    private fun checkBoundsAndWrap(position: Int, max: Int, radius: Int, wrap: Boolean): Boolean {
        cbPos = position
        if (Integer.compareUnsigned(position, max) > 0) {
            if (wrap) {
                var p = position % (max + 1)
                if (p < 0) p += max + 1
                cbPos = p
            } else if (position < -radius || position > max + radius) {
                return false
            }
        }
        return true
    }

    private fun particleMoveUpdate(p: Int, options: Int = settings, adv: Int = -1) {
        if (ttl[p] <= 0) return
        if (!flag(p, F_PERPETUAL)) ttl[p] = u16(ttl[p] - 1)
        if (has(options, COLORBYAGE)) hue[p] = minOf(ttl[p], 255)
        var renderRadius = PS_P_HALFRADIUS_1D - 1 + particleSize
        var newX = px[p] + vx[p]
        setFlag(p, F_OOB, false)
        if (perParticleSize && adv >= 0) {
            renderRadius = PS_P_HALFRADIUS_1D - 1 + aSize[adv]
            particleHardRadius = if (aSize[adv] > 1) PS_P_MINHARDRADIUS_1D + ((aSize[adv] * 52) shr 6) else PS_P_MINHARDRADIUS_1D shr 1
        }
        if (has(options, BOUNCE)) {
            if (newX < particleHardRadius || newX > maxX - particleHardRadius) {
                var bounceThis = true
                if (has(options, USEGRAVITY)) {
                    if (flag(p, F_REVERSEGRAV)) { if (newX < particleHardRadius) bounceThis = false }
                    else if (newX > particleHardRadius) bounceThis = false
                }
                if (bounceThis) {
                    vx[p] = i8(-vx[p])
                    vx[p] = i8((vx[p] * wallHardness) / 255)
                    newX = if (newX < particleHardRadius) particleHardRadius else maxX - particleHardRadius
                }
            }
        }
        val inBounds = checkBoundsAndWrap(newX, maxX, renderRadius, has(options, WRAP))
        newX = cbPos
        if (!inBounds) {
            setFlag(p, F_OOB, true)
            if (has(options, KILLOOB)) {
                var kill = true
                if (has(options, USEGRAVITY)) {
                    if (flag(p, F_REVERSEGRAV)) { if (newX < 0 || newX > (maxX shl 2)) kill = false }
                    else if (newX > 0 && newX < (maxX shl 2)) kill = false
                }
                if (kill) ttl[p] = 0
            }
        }
        if (!flag(p, F_FIXED)) px[p] = newX else vx[p] = 0
    }

    private var dvCounter = 0
    private fun calcForceDv(forceIn: Int, counterIn: Int): Int {
        dvCounter = counterIn
        val force = i8(forceIn)
        if (force == 0) return 0
        val fa = kotlin.math.abs(force)
        var dv = 0
        if (fa < 16) {
            dvCounter = u8(dvCounter + fa)
            if (dvCounter > 15) {
                dvCounter = u8(dvCounter - 16)
                dv = if (force < 0) -1 else 1
            }
        } else dv = force / 16
        return dv
    }

    private fun applyForce(p: Int, xforce: Int, counter: Int): Int {
        val dv = calcForceDv(xforce, counter)
        vx[p] = i8(limitSpeed(vx[p] + dv))
        return dvCounter
    }

    private fun applyForceAll(xforce: Int) {
        val dv = calcForceDv(xforce, forceCounter)
        forceCounter = dvCounter
        for (i in 0 until usedParticles) vx[i] = i8(limitSpeed(vx[i] + dv))
    }

    private fun applyGravityAll() {
        val dvRaw = calcForceDv(gforce, gforceCounter)
        gforceCounter = dvCounter
        for (i in 0 until usedParticles) {
            val dv = if (flag(i, F_REVERSEGRAV)) -dvRaw else dvRaw
            vx[i] = i8(limitSpeed(vx[i] - dv))
        }
    }

    private fun applyFriction(coefficient: Int) {
        val friction = 255 - coefficient
        for (i in 0 until usedParticles) if (ttl[i] != 0) vx[i] = i8((vx[i] * friction) / 255)
    }

    private fun update() {
        if (has(settings, USEGRAVITY)) applyGravityAll()
        if (has(settings, USECOLLISIONS)) {
            handleCollisions()
            if (perParticleSize) handleCollisions()
        }
        for (i in 0 until usedParticles) particleMoveUpdate(i, settings, if (advanced) i else -1)
        if (has(settings, COLORBYPOS)) {
            val scale = (255 shl 16) / maxX
            for (i in 0 until usedParticles) hue[i] = u8((scale * px[i]) ushr 16)
        }
        render()
    }

    private fun render() {
        if (motionBlur != 0) {
            for (x in 0..maxXpixel) fb[x] = fastColorScale(fb[x], motionBlur)
        } else {
            java.util.Arrays.fill(fb, 0, maxXpixel + 1, 0)
        }
        val wrap = has(settings, WRAP)
        for (i in 0 until usedParticles) {
            if (ttl[i] == 0 || flag(i, F_OOB)) continue
            val brightness = minOf(ttl[i] shl 1, 255)
            var base = colorFromPalette(hue[i])
            if (advanced && aSat[i] < 255) base = setSaturation(base, aSat[i])
            renderParticle(i, brightness, base, wrap)
        }
        if (smearBlur != 0) { if (matrix) blur2D(smearBlur) else blur1D(smearBlur) }
        if (bgColor != 0) for (i in 0..maxXpixel) fb[i] = fastColorScaleAdd(fb[i], bgColor, 255)
    }

    private fun blur1D(amount: Int) {
        val keep = 255
        val seep = amount shr 1
        var cur = fb[0]
        var carry = fastColorScale(cur, seep)
        fb[0] = fastColorScale(cur, keep)
        for (i in 1 until length) {
            cur = fb[i]
            val part = fastColorScale(cur, seep)
            cur = fastColorScale(cur, keep)
            cur = colorAdd(cur, carry)
            fb[i - 1] = colorAdd(fb[i - 1], part)
            fb[i] = cur
            carry = part
        }
    }

    private fun blur2D(amount: Int) {
        val cols = matrixWidth; val rows = length / matrixWidth
        val keep = 255
        val seep = amount shr 1
        for (row in 0 until rows) {
            val o = row * cols
            var cur = fb[o]
            var carry = fastColorScale(cur, seep)
            fb[o] = fastColorScale(cur, keep)
            for (x in 1 until cols) {
                cur = fb[o + x]
                val part = fastColorScale(cur, seep)
                cur = fastColorScale(cur, keep)
                cur = colorAdd(cur, carry)
                fb[o + x - 1] = colorAdd(fb[o + x - 1], part)
                fb[o + x] = cur
                carry = part
            }
        }
        for (col in 0 until cols) {
            var cur = fb[col]
            var carry = fastColorScale(cur, seep)
            fb[col] = fastColorScale(cur, keep)
            for (y in 1 until rows) {
                cur = fb[col + y * cols]
                val part = fastColorScale(cur, seep)
                cur = fastColorScale(cur, keep)
                cur = colorAdd(cur, carry)
                fb[col + (y - 1) * cols] = colorAdd(fb[col + (y - 1) * cols], part)
                fb[col + y * cols] = cur
                carry = part
            }
        }
    }

    private fun renderParticle(i: Int, brightness: Int, color: Int, wrap: Boolean) {
        var size = particleSize
        if (perParticleSize && advanced) size = 1 + aSize[i]
        if (size == 0) {
            val x = px[i] shr PS_P_RADIUS_SHIFT_1D
            if (x in 0..maxXpixel) fb[x] = fastColorScaleAdd(fb[x], color, brightness)
            return
        }
        if (size > 1) { renderLargeParticle(size, i, brightness, color, wrap); return }
        var in0 = true; var in1 = true
        val xoffset = px[i] + PS_P_HALFRADIUS_1D
        val dx = xoffset and (PS_P_RADIUS_1D - 1)
        val x = xoffset shr PS_P_RADIUS_SHIFT_1D
        var p1 = x
        var p0 = x - 1
        val b0 = ((PS_P_RADIUS_1D - dx) * brightness) shr PS_P_SURFACE_1D
        val b1 = (dx * brightness) shr PS_P_SURFACE_1D
        if (p0 < 0) {
            if (wrap) p0 = maxXpixel
            else { in0 = false; if (p0 < -1) return }
        } else if (p1 > maxXpixel) {
            if (wrap) p1 = 0
            else { in1 = false; if (p0 > maxXpixel) return }
        }
        if (in0) fb[p0] = fastColorScaleAdd(fb[p0], color, b0)
        if (in1) fb[p1] = fastColorScaleAdd(fb[p1], color, b1)
    }

    private fun renderLargeParticle(size: Int, i: Int, brightness: Int, color: Int, wrap: Boolean) {
        val xSub = px[i]
        val xCenter = xSub shr PS_P_RADIUS_SHIFT_1D
        val rSub = size + PS_P_RADIUS_1D + 1
        val rPix = rSub shr PS_P_RADIUS_SHIFT_1D
        val xMin = xCenter - rPix - 1
        val xMax = xCenter + rPix + 1
        val matrixX = maxXpixel + 1
        val rxSq = rSub * rSub
        for (pxx in xMin..xMax) {
            var rx = pxx
            if (rx < 0) { if (!wrap) continue; rx += matrixX }
            else if (rx > maxXpixel) { if (!wrap) continue; rx -= matrixX }
            var dxSq = (pxx shl PS_P_RADIUS_SHIFT_1D) - xSub + PS_P_HALFRADIUS_1D
            dxSq *= dxSq
            val distSq = (dxSq shl 8) / rxSq
            val pb = if (Integer.compareUnsigned(distSq, 256) >= 0) 0 else u8(((256 - distSq) * brightness) shr 8)
            if (rx in 0..maxXpixel) fb[rx] = fastColorScaleAdd(fb[rx], color, pb)
        }
    }

    private fun handleCollisions() {
        val collisionDistance = particleHardRadius shl 1
        var checkDistSq = maxOf(2 * PS_P_MAXSPEED, collisionDistance)
        if (perParticleSize && advanced) checkDistSq = maxOf(2 * PS_P_MAXSPEED, (512 * 52) shr 6)
        checkDistSq *= checkDistSq
        var binWidth = 64 * PS_P_RADIUS_1D
        var overlap = collisionDistance + 2 * PS_P_MAXSPEED
        if (perParticleSize && advanced) overlap = 512
        val maxBinParticles = maxOf(50, (usedParticles + 1) / 4)
        var numBins = (maxX + (binWidth - 1)) / binWidth
        if (usedParticles < maxBinParticles) { numBins = 1; binWidth = maxX + 1 }
        var nextFrameStartIdx = hwRandom16(usedParticles)
        var pidx = collisionStartIdx
        for (bin in 0 until numBins) {
            var count = 0
            val binStart = bin * binWidth - overlap
            val binEnd = binStart + binWidth + (overlap shl 1)
            for (k in 0 until usedParticles) {
                if (ttl[pidx] > 0) {
                    if (px[pidx] >= binStart && px[pidx] <= binEnd) {
                        if (!flag(pidx, F_OOB) && flag(pidx, F_COLLIDE)) {
                            if (count >= maxBinParticles) { nextFrameStartIdx = pidx; break }
                            binIndices[count++] = pidx and 0xFFFF
                        }
                    }
                }
                pidx++
                if (pidx >= usedParticles) pidx = 0
            }
            for (a in 0 until count) {
                val ii = binIndices[a]
                for (b in a + 1 until count) {
                    val jj = binIndices[b]
                    val dx = px[jj] - px[ii]
                    if (ule(dx * dx, checkDistSq)) collideParticles(ii, jj, dx, collisionDistance)
                }
            }
        }
        collisionStartIdx = u16(nextFrameStartIdx)
    }

    private fun collideParticles(p1: Int, p2: Int, dx: Int, collDistIn: Int) {
        var cd = collDistIn
        var massratio1 = 0; var massratio2 = 0
        if (perParticleSize && advanced) {
            cd = (PS_P_MINHARDRADIUS_1D * 2) + (((aSize[p1] + aSize[p2]) * 52) shr 6)
            val mass1 = PS_P_RADIUS_1D + aSize[p1]
            val mass2 = PS_P_RADIUS_1D + aSize[p2]
            val total = mass1 + mass2 - 2
            massratio1 = (mass2 shl 8) / total
            massratio2 = (mass1 shl 8) / total
        }
        val dv = vx[p2] - vx[p1]
        val absdv = kotlin.math.abs(dv)
        val dot = dx * dv
        val dxAbs = kotlin.math.abs(dx)
        if (dot < 0) {
            val lookahead = cd + absdv
            if (ule(dxAbs, lookahead)) {
                if (flag(p1, F_FIXED)) {
                    vx[p2] = i8(-(vx[p2] * collisionHardness) / 255)
                    px[p2] = px[p1] + (if (dx < 0) -cd else cd)
                    return
                } else if (flag(p2, F_FIXED)) {
                    vx[p1] = i8(-(vx[p1] * collisionHardness) / 255)
                    px[p1] = px[p2] + (if (dx < 0) cd else -cd)
                    return
                }
                val surfaceHardness = maxOf(collisionHardness, PS_P_MINSURFACEHARDNESS_1D)
                val impulse = (dv * surfaceHardness) / 255
                if (massratio1 != 0) {
                    val v1 = vx[p1] + ((impulse * massratio1) shr 7)
                    val v2 = vx[p2] - ((impulse * massratio2) shr 7)
                    vx[p1] = i8(limitSpeed(v1))
                    vx[p2] = i8(limitSpeed(v2))
                } else {
                    vx[p1] = i8(vx[p1] + impulse)
                    vx[p2] = i8(vx[p2] - impulse)
                }
                if (collisionHardness < PS_P_MINSURFACEHARDNESS_1D && (call and 0x07) == 0) {
                    val coeff = collisionHardness + (250 - PS_P_MINSURFACEHARDNESS_1D)
                    vx[p1] = i8(udiv(vx[p1] * coeff, 255))
                    vx[p2] = i8(udiv(vx[p2] * coeff, 255))
                }
            } else return
        }
        if (ult(dxAbs, cd)) {
            var push = 1 + ((cd - dxAbs) ushr 3)
            var addSpeed = 1
            if (dx < 0) { push = -push; addSpeed = -addSpeed }
            if (absdv < 4) {
                vx[p1] = i8(vx[p1] - addSpeed)
                vx[p2] = i8(vx[p2] + addSpeed)
            }
            if ((dot and 0x01) != 0) px[p1] -= push else px[p2] += push
        }
    }

    fun render(
        effectId: Int, frame: IntArray, paletteId: Int, col0: Int, col1: Int, col2: Int,
        speed: Int, intensity: Int, custom1: Int, custom2: Int, custom3: Int,
        check1: Boolean, check2: Boolean, check3: Boolean,
    ) {
        if (effectId != lastEffect) reset(effectId)
        this.speed = speed and 0xFF; this.intensity = intensity and 0xFF
        this.custom1 = custom1 and 0xFF; this.custom2 = custom2 and 0xFF; this.custom3 = custom3 and 0x1F
        this.check1 = check1; this.check2 = check2; this.check3 = check3
        bgColor = col1 and 0xFFFFFF
        audioTick()
        now += 22
        loadPalette(paletteId, col0 and 0xFFFFFF, col1 and 0xFFFFFF, col2 and 0xFFFFFF)
        val ok = when (effectId) {
            202 -> drip()
            203 -> pinball()
            204 -> dancingShadows()
            205 -> fireworks1D()
            206 -> sparkler()
            207 -> hourglass()
            208 -> spray1D()
            209 -> balance()
            210 -> chase()
            211 -> starburst()
            212 -> geq1D()
            213 -> fire1D()
            214 -> sonicStream()
            215 -> sonicBoom()
            216 -> springy()
            in 187..201, 217 -> false
            else -> { java.util.Arrays.fill(fb, 0); true }
        }
        if (!ok) java.util.Arrays.fill(fb, col0 and 0xFFFFFF)
        call++
        val n = minOf(frame.size, length)
        for (i in 0 until n) frame[i] = fb[i] and 0xFFFFFF
    }

    private fun reset(effectId: Int) {
        lastEffect = effectId
        randSeed = 0L
        call = 0; step = 0; aux0 = 0; aux1 = 0; now = 0
        audioMs = 0L
        psValid = false
        java.util.Arrays.fill(fb, 0)
    }

    private fun begin(requestedSources: Int, fraction: Int = 255, isAdvanced: Boolean = false): Boolean {
        if (call == 0) { if (!initPS(requestedSources, fraction, isAdvanced)) { psValid = false; return false } }
        if (!psValid) return false
        setSize()
        return true
    }

    private fun drip(): Boolean {
        if (call == 0) {
            if (!begin(4)) return false
            setKillOutOfBounds(true)
            hue[src(0)] = u8(hwRandom16())
            aux1 = 0xFFFF
        } else if (!begin(4)) return false
        setBounce(true)
        setWallHardness(50)
        setMotionBlur(custom2)
        setGravity(custom3 shr 1)
        setParticleSize(if (check3) 1 else 0)
        if (check2) enableParticleCollisions(true) else enableParticleCollisions(false)
        val s = src(0)
        setFlag(s, F_COLLIDE, false)
        if (check1) {
            if (custom1 == 0) setBounce(false)
            sVar[0] = 5
            sV[0] = i8(-(8 + (speed shr 2)))
            sMinLife[0] = 30
            sMaxLife[0] = 200
            px[s] = hwRandom(maxX)
        } else {
            sVar[0] = 0
            sV[0] = i8(-(speed shr 1))
            sMinLife[0] = 3000
            sMaxLife[0] = 3000
            px[s] = maxX - PS_P_RADIUS_1D
        }
        if (aux1 != intensity) aux0 = 1
        aux1 = intensity
        if (urem(call, aux0) == 0) {
            val interval = 300 / (intensity + 1)
            aux0 = u16(interval + hwRandom(interval + 5))
            hue[s] = hwRandom8()
            sprayEmit(0)
        }
        for (i in 0 until usedParticles) {
            if (ttl[i] != 0 && !flag(i, F_COLLIDE)) {
                if (custom1 > 0 && px[i] < (PS_P_RADIUS_1D shl 1)) {
                    ttl[i] = 0
                    sMaxLife[0] = 80
                    sMinLife[0] = 20
                    sVar[0] = i8(10 + (custom1 shr 3))
                    sV[0] = 0
                    hue[s] = hue[i]
                    px[s] = PS_P_RADIUS_1D
                    setFlag(s, F_COLLIDE, true)
                    for (j in 0 until 2 + (custom1 shr 2)) sprayEmit(0)
                }
            }
            if (check1) { if (hue[i] < 245) hue[i] = u8(hue[i] + 8) }
            if (speed > 200) particleMoveUpdate(i, settings, -1)
        }
        update()
        return true
    }

    private fun pinball(): Boolean {
        if (call == 0) {
            if (!begin(1, 128, true)) return false
            setFlag(src(0), F_COLLIDE, true)
            px[src(0)] = -1000
            aux0 = 1
            aux1 = 5000
        } else if (!begin(1, 128, true)) return false
        setGravity(map(custom3, 0, 31, 0, 8))
        setBounce(custom3 != 0)
        setMotionBlur(custom2)
        enableParticleCollisions(check1, 255)
        setColorByPosition(check3)
        var maxParticles = maxOf(20, intensity / (1 + ((if (check2) 1 else 0) * (custom1 shr 5))))
        if (custom1 < 255) setParticleSize(custom1)
        else { perParticleSize = true; maxParticles *= 2 }
        setUsedParticles(maxParticles)
        var updateBalls = false
        val c2i = if (check2) 1 else 0
        if (aux1 != speed + intensity + c2i + custom1 + usedParticles) {
            step = call
            updateBalls = true
            sMaxLife[0] = if (custom3 != 0) 1000 else 0xFFFF
            sMinLife[0] = sMaxLife[0] shr 1
        }
        if (check2) {
            setGravity(0)
            setWallHardness(255)
            var speedSum = 0
            for (i in 0 until usedParticles) {
                ttl[i] = 500
                if (updateBalls) {
                    setFlag(i, F_COLLIDE, true)
                    if (px[i] == 0) {
                        px[i] = hwRandom16(maxX)
                        vx[i] = if ((hwRandom16() and 0x01) != 0) 1 else -1
                    }
                    hue[i] = hwRandom8()
                    aSat[i] = 255
                    aSize[i] = hwRandom8()
                }
                speedSum += kotlin.math.abs(vx[i])
            }
            val avgSpeed = speedSum / usedParticles
            val setSpeed = 2 + (speed shr 2)
            if (avgSpeed < setSpeed) {
                for (k in 0 until setSpeed - avgSpeed) {
                    val idx = hwRandom16(usedParticles)
                    if (kotlin.math.abs(vx[idx]) < PS_P_MAXSPEED) vx[idx] = i8(vx[idx] + if (vx[idx] >= 0) 1 else -1)
                }
            } else if (avgSpeed > setSpeed + 8) applyFriction(1)
        } else {
            setWallHardness(220)
            sVar[0] = i8(speed shr 3)
            val newSpeed = 2 + (speed shr 1) - (speed shr 3)
            sV[0] = i8(newSpeed)
            for (i in 0 until usedParticles) {
                if (ttl[i] < 50) ttl[i] = 0
                else if (vx[i] == 0 && px[i] < (PS_P_RADIUS_1D + custom1)) ttl[i] = u16(ttl[i] - 50)
                if (updateBalls) {
                    if (custom3 == 0) vx[i] = i8(if (vx[i] > 0) newSpeed else -newSpeed)
                }
            }
            if (Integer.compareUnsigned(call, step) > 0) {
                val interval = 260 - intensity
                step += interval + hwRandom16(interval)
                hue[src(0)] = u8(hwRandom16())
                sSat[0] = 255
                sSize[0] = hwRandom8()
                sprayEmit(0)
            }
        }
        aux1 = u16(speed + intensity + c2i + custom1 + usedParticles)
        update()
        return true
    }

    private fun dancingShadows(): Boolean {
        if (call == 0) {
            if (!begin(1)) return false
            sMaxLife[0] = 1000
            sMinLife[0] = sMaxLife[0]
        } else if (!begin(1)) return false
        setMotionBlur(custom1)
        setSmearBlur(if (check1) 120 else 0)
        setParticleSize(if (check3) 1 else 0)
        setColorByPosition(check2)
        setUsedParticles(map(intensity, 0, 255, 10, 255))
        var dead = 0
        for (i in 0 until usedParticles) {
            if ((call and 0x07) == 0 && flag(i, F_OOB)) {
                if (vx[i] * px[i] > 0) ttl[i] = 0
            }
            setFlag(i, F_PERPETUAL, true)
            if (urem(call, 32 / (1 + (custom2 shr 3))) == 0) hue[i] = u8(hue[i] + 2 + (custom2 shr 5))
            if (aux0 != speed) vx[i] = i8(if (vx[i] > 0) speed shr 3 else (-speed) shr 3)
            if (ttl[i] == 0) dead++
        }
        aux0 = speed
        if (dead > 5 && (call and 0x03) == 0) {
            val type = hwRandom16(SPOT_TYPES_COUNT)
            var spd = i8(2 + hwRandom16(2 + (speed shr 1)) + (speed shr 4))
            val width = hwRandom16(1, 10)
            var ttlv = 300
            var position: Int
            if ((hwRandom() and 0x01) != 0) {
                position = maxXpixel
                spd = i8(-spd)
            } else position = -width
            sV[0] = spd
            hue[src(0)] = hwRandom8()
            var i = 0
            while (i < width) {
                if (width > 1) {
                    when (type) {
                        1 -> { ttlv = cubicwave8(map(i, 0, width - 1, 0, 255)); ttlv = (ttlv * ttlv) shr 8 }
                        2 -> { ttlv = cubicwave8(2 * map(i, 0, width - 1, 0, 255)); ttlv = (ttlv * ttlv) shr 8 }
                        3 -> { if (i > 0) position++; i++ }
                        4 -> { if (i > 0) position += 2; i += 2 }
                        5 -> { if (i > 0) position += 3; i += 3 }
                    }
                }
                px[src(0)] = position * PS_P_RADIUS_1D
                val partIdx = sprayEmit(0)
                if (partIdx >= 0) ttl[partIdx] = u16(ttlv)
                else smearBlur = ttlv and 0xFF
                position++
                i++
            }
        }
        update()
        return true
    }

    private fun fireworks1D(): Boolean {
        if (call == 0) {
            if (!begin(4, 150, true)) return false
            setKillOutOfBounds(true)
            setFlag(src(0), F_CUSTOM1, true)
        } else if (!begin(4, 150, true)) return false
        val s = src(0)
        setMotionBlur(custom2)
        val gravity = 1 + (speed shr 3)
        setGravity(if (speed != 0) gravity else 0)
        setParticleSize(if (check3) 1 else 0)
        if (flag(s, F_CUSTOM1)) {
            ttl[s] = u16(ttl[s] - 1)
            if (ttl[s] == 0) {
                aux0 = if (hwRandom8() < custom1) 1 else 0
                setFlag(s, F_CUSTOM1, false)
                hue[s] = u8(hwRandom16())
                val c2i = if (check2) 1 else 0
                sVar[0] = i8(10 * c2i)
                sV[0] = i8(-10 * c2i)
                sMinLife[0] = 180
                sMaxLife[0] = if (check2) 700 else 240
                px[s] = aux0 * maxX
                val sp = kotlin.math.sqrt(((gravity * ((maxX shr 2) + hwRandom16(maxX shr 1))) shr 4).toDouble()).toLong().toInt()
                vx[s] = i8(if (ult(sp, 127)) sp else 127)
                ttl[s] = 4000
                sSat[0] = 30
                setFlag(s, F_REVERSEGRAV, false)
                if (aux0 != 0) {
                    setFlag(s, F_REVERSEGRAV, true)
                    vx[s] = i8(-vx[s])
                    sV[0] = i8(-sV[0])
                }
            }
        } else {
            var rocketGravity = -gravity
            var currentSpeed = vx[s]
            if (aux0 != 0) { rocketGravity = -rocketGravity; currentSpeed = -currentSpeed }
            fxData0 = applyForce(s, rocketGravity, fxData0)
            particleMoveUpdate(s, settings, -1)
            particleMoveUpdate(s, settings, -1)
            val rocketHeight = if (aux0 != 0) maxX - px[s] else px[s]
            if (currentSpeed < 0 && ttl[s] > 50) ttl[s] = u16(50 - gravity)
            if (ttl[s] < 2) {
                setFlag(s, F_CUSTOM1, true)
                sVar[0] = i8(5 + udiv(((maxX shr 1) + rocketHeight) * (20 + (intensity shl 1)), maxX shl 2))
                sMinLife[0] = 1200
                sMaxLife[0] = 2600
                ttl[s] = u16(100 + hwRandom16(64 - (speed shr 2)))
                sSat[0] = if (custom3 < 16) u8(10 + (custom3 shl 4)) else 255
                sSize[0] = if (check3) u8(hwRandom16(intensity)) else 0
                var explosionSize = 8 + (maxXpixel shr 2) + (px[s] shr (PS_P_RADIUS_SHIFT_1D - 1))
                explosionSize += hwRandom16((explosionSize * intensity) ushr 8)
                setColorByAge(false)
                setColorByPosition(false)
                var e = 0
                while (ult(e, explosionSize)) {
                    val idx = sprayEmit(0)
                    if (custom3 > 23) {
                        if (custom3 == 31) {
                            setColorByAge(check1)
                            setColorByPosition(!check1)
                        } else {
                            val v = if (idx >= 0) vx[idx] else i8(padVx)
                            val h = u8(map(kotlin.math.abs(v), 0, sVar[0], 0, 16 + hwRandom16(200)))
                            if (idx >= 0) hue[idx] = u8(h + hue[s]) else padHue = u8(h + hue[s])
                        }
                    } else {
                        if (check1) hue[s] = u8(hwRandom16())
                    }
                    e++
                }
            }
        }
        if ((call and 0x01) == 0 && !flag(s, F_CUSTOM1)) sprayEmit(0)
        if ((call and 0x03) == 0) applyFriction(1)
        update()
        for (i in 0 until usedParticles) ttl[i] = if (ttl[i] > 20) ttl[i] - 20 else 0
        return true
    }

    private fun sparkler(): Boolean {
        if (!begin(16, 128, true)) return false
        val sparklerSettings = if (check2) BOUNCE else WRAP
        var numSparklers = numSources
        setMotionBlur(custom2)
        setParticleSize(if (check3) 60 else 0)
        for (i in 0 until numSparklers) {
            val s = src(i)
            hue[s] = u8(hwRandom16())
            sVar[i] = 0
            sMinLife[i] = u16(150 + intensity)
            sMaxLife[i] = u16(250 + (intensity shl 1))
            val sp = speed shr 1
            if (check1) sVar[i] = i8(intensity shr 3)
            vx[s] = i8(if (vx[s] > 0) sp else -sp)
            ttl[s] = 400
            sSat[i] = custom1
            if (speed == 255) px[s] = hwRandom16(maxX)
            else particleMoveUpdate(s, sparklerSettings, -1)
        }
        numSparklers = minOf(1 + (custom3 shr 1), numSparklers)
        if (aux0 != custom3) {
            for (i in 1 until numSparklers) {
                px[src(i)] = urem(px[src(0)] + udiv(maxX, numSparklers) * i, maxX)
            }
        }
        aux0 = custom3
        for (i in 0 until numSparklers) {
            if (urem(hwRandom(), (271 - intensity) shr 4) == 0) sprayEmit(i)
        }
        update()
        val dec = 64 - (intensity shr 2)
        for (i in 0 until usedParticles) ttl[i] = if (ttl[i] > dec) ttl[i] - dec else 0
        return true
    }

    private fun hourglassTarget(i: Int): Int = if (flag(i, F_REVERSEGRAV)) maxX - i * PS_P_RADIUS_1D - PS_P_RADIUS_1D / 2
    else (usedParticles - i) * PS_P_RADIUS_1D - PS_P_RADIUS_1D / 2

    private fun hourglass(): Boolean {
        if (call == 0) {
            if (!begin(0, 255, false)) return false
            setBounce(true)
            setWallHardness(100)
        } else if (!begin(0, 255, false)) return false
        setUsedParticles(1 + ((intensity * 255) shr 8))
        setMotionBlur(custom2)
        setGravity(map(custom3, 0, 31, 1, 30))
        enableParticleCollisions(true, 64)
        val colorMode = custom1 shr 5
        if (intensity != fxData0) {
            fxData0 = intensity
            for (i in 0 until usedParticles) {
                setFlag(i, F_REVERSEGRAV, true)
                fxData1 = 0
                aux1 = 1
            }
            aux0 = u16(usedParticles - 1)
        }
        for (i in 0 until usedParticles - 1) {
            if (px[i] < px[i + 1] && !flag(i, F_FIXED) && !flag(i + 1, F_FIXED)) {
                val t = px[i]; px[i] = px[i + 1]; px[i + 1] = t
            }
        }
        for (i in 0 until usedParticles) {
            if (!flag(i, F_FIXED) && kotlin.math.abs(vx[i]) < 5) {
                val tp = hourglassTarget(i)
                val below = if (flag(i, F_REVERSEGRAV)) px[i] > tp else px[i] < tp
                val close = kotlin.math.abs(tp - px[i]) < PS_P_RADIUS_1D
                if (below || close) {
                    px[i] = tp
                    setFlag(i, F_FIXED, true)
                }
            }
            if (colorMode == 7) setColorByPosition(true)
            else {
                setColorByPosition(false)
                val baseHue = u8((custom1 and 0x1F) shl 3)
                when (colorMode) {
                    0 -> hue[i] = 120
                    1 -> hue[i] = baseHue
                    2, 3 -> hue[i] = u8(((custom1 and 0x1F) shl 1) + (i % 3) * 74)
                    4 -> hue[i] = u8(baseHue + (i * 255) / usedParticles)
                    5 -> hue[i] = u8(baseHue + (i * 1024) / usedParticles)
                    6 -> hue[i] = u8(i + (now ushr 3))
                }
            }
            if (check1 && !flag(i, F_REVERSEGRAV)) hue[i] = u8(hue[i] + 120)
        }
        if (aux1 == 1) {
            for (i in 0 until usedParticles) {
                setFlag(i, F_COLLIDE, true)
                setFlag(i, F_PERPETUAL, true)
                ttl[i] = 260
                px[i] = hourglassTarget(i)
                setFlag(i, F_FIXED, true)
            }
        }
        if (aux1 == 0) {
            if (Integer.compareUnsigned(now, step) >= 0) {
                step = if (check3 && fxData1 != 0) now + 100 else now + maxOf(100, speed * 100)
                if (aux0 < usedParticles) {
                    setFlag(aux0, F_REVERSEGRAV, fxData1 != 0)
                    setFlag(aux0, F_FIXED, false)
                } else {
                    fxData1 = if (fxData1 != 0) 0 else 1
                    aux1 = u16((if (check2) 1 else 0) * length + 100)
                }
                aux0 = if (fxData1 == 0) u16(aux0 - 1) else u16(aux0 + 1)
            }
        } else if (check2) aux1 = u16(aux1 - 1)
        update()
        return true
    }

    private fun spray1D(): Boolean {
        if (call == 0) {
            if (!begin(1)) return false
            setKillOutOfBounds(true)
            setWallHardness(150)
            setParticleSize(1)
        } else if (!begin(1)) return false
        setBounce(check2)
        setMotionBlur(custom2)
        val gravity = -(custom3 - 16)
        setGravity(kotlin.math.abs(gravity))
        val s = src(0)
        hue[s] = u8(aux0)
        sVar[0] = 20
        sMinLife[0] = 200
        sMaxLife[0] = 400
        px[s] = map(custom1, 0, 255, 0, maxX)
        sV[0] = i8(map(speed, 0, 255, -127 + sVar[0], 127 - sVar[0]))
        setFlag(s, F_REVERSEGRAV, gravity < 0)
        if (urem(hwRandom(), 1 + ((255 - intensity) shr 3)) == 0) {
            sprayEmit(0)
            aux0 = u16(aux0 + 1)
        }
        setColorByAge(check1)
        setColorByPosition(check3)
        val rg = flag(s, F_REVERSEGRAV)
        for (i in 0 until usedParticles) setFlag(i, F_REVERSEGRAV, rg)
        update()
        return true
    }

    private fun balance(): Boolean {
        if (call == 0) {
            if (!begin(1, 128)) return false
            setParticleSize(1)
        } else if (!begin(1, 128)) return false
        setMotionBlur(custom2)
        setBounce(!check2)
        setWrap(check2)
        val hardness = if (custom1 > 0) u8(map(custom1, 0, 255, 50, 250)) else 200
        enableParticleCollisions(custom1 != 0, hardness)
        setWallHardness(200)
        setUsedParticles(map(intensity, 0, 255, 10, 255))
        if (usedParticles > aux1) {
            for (i in 0 until usedParticles) {
                px[i] = i * PS_P_RADIUS_1D
                ttl[i] = 300
                setFlag(i, F_PERPETUAL, true)
                setFlag(i, F_COLLIDE, true)
            }
        }
        aux1 = u16(usedParticles)
        for (i in 0 until usedParticles - 1) {
            if (px[i] > px[i + 1]) {
                if (check2) { if (px[i] - px[i + 1] > 3 * PS_P_RADIUS_1D) continue }
                val t = px[i]; px[i] = px[i + 1]; px[i + 1] = t
            }
        }
        if (urem(call, ((255 - speed) shr 6) + 1) == 0) {
            val increment = (speed shr 6) + 1
            aux0 = u16(aux0 + increment)
            var xg = if (check3) perlin8(aux0) - 128 else cos8(aux0) - 128
            xg = (xg * ((custom3 + 1) shl 2)) / 128
            applyForceAll(xg)
        }
        val ri = hwRandom16(usedParticles)
        vx[ri] = i8((vx[ri] * 200) / 255)
        if ((call and 0x0F) == 0 && custom3 > 4) applyFriction(1)
        setColorByPosition(check1)
        if (!check1) for (i in 0 until usedParticles) hue[i] = u8((1024 * i) / usedParticles)
        update()
        return true
    }

    private fun chase(): Boolean {
        if (call == 0) {
            if (!begin(1, 191, true)) return false
            aux0 = 0xFFFF
            fxData0 = 1
            fxData1 = 1
        } else if (!begin(1, 191, true)) return false
        setColorByPosition(check3)
        setMotionBlur(7 + (custom3 shl 3))
        var numP = 1 + map(intensity, 0, 255, 0, udiv(usedParticles, 1 + (custom1 shr 5)))
        numP = minOf(numP, usedParticles)
        var hueStep = 1 + (udiv(custom2 shl 19, numP) ushr 16)
        val settingsSum = speed + intensity + custom1 + custom2 + (if (check1) 1 else 0) + (if (check2) 1 else 0) + (if (check3) 1 else 0)
        if (aux0 != settingsSum) {
            if (check1) step = aSize[0] / 2 + udiv(maxX, numP)
            else {
                step = udiv(maxX + (PS_P_RADIUS_1D shl 6), numP)
                step = udiv(step, PS_P_RADIUS_1D) * PS_P_RADIUS_1D
            }
            for (i in 0 until usedParticles) {
                aSat[i] = 255
                px[i] = (i - 1) * step
                vx[i] = i8(speed shr 2)
                aSize[i] = custom1
                hue[i] = if (custom2 < 255) u8(i * hueStep) else u8(hwRandom16())
            }
            aux0 = u16(settingsSum)
        }
        if (check1) {
            hueStep = 1 + ((maxOf(hueStep, 3) * (sin16(now * 3) + 32767)) shr 15)
        }
        for (i in usedParticles - 1 downTo 0) {
            if (px[i] > maxX + PS_P_RADIUS_1D + aSize[i]) {
                val next = (i + 1) % usedParticles
                px[i] = px[next] - step
                if (check1) aSize[i] = u8(maxOf(1 + (custom1 shr 1), (sin16(now shl 1) + 32767) shr 8))
                hue[i] = if (custom2 < 255) u8(hue[next] - hueStep) else u8(hwRandom16())
            }
            ttl[i] = 300
        }
        if (check1) {
            if (fxData1 == 0) fxData1 = 1
            if (fxData0 == 0) fxData0 = 1
            if (Integer.compareUnsigned(step, (aSize[0] + PS_P_RADIUS_1D * 4) + udiv(maxX, numP)) >= 0) fxData1 = -1
            else if (Integer.compareUnsigned(step, (aSize[0] shr 1) + udiv(maxX, numP)) <= 0) fxData1 = 1
            if (aux1 > 512) fxData0 = -1
            else if (aux1 < 50) fxData0 = 1
            if (urem(call, 1024 / (1 + (speed shr 2))) == 0) aux1 = u16(aux1 + fxData0)
            var globalHueStep = 0
            if (urem(call, 1 + ((sin16(now) + 32767) shr 12)) == 0) globalHueStep = 2
            if ((call and 0x1F) == 0) step += fxData1
            for (i in 0 until usedParticles) {
                hue[i] = u8(hue[i] - globalHueStep)
                vx[i] = i8(1 + (speed shr 2) + (((sin16(now ushr 1) + 32767) * (speed shr 2)) shr 16))
            }
        }
        update()
        return true
    }

    private fun starburst(): Boolean {
        if (call == 0) {
            if (!begin(1, 200, true)) return false
            setKillOutOfBounds(true)
            enableParticleCollisions(true, 200)
            ttl[src(0)] = 1
            sSat[0] = 0
        } else if (!begin(1, 200, true)) return false
        val s = src(0)
        setMotionBlur(custom2)
        setGravity((if (check1) 1 else 0) * 8)
        val old = ttl[s]
        ttl[s] = u16(old - 1)
        if (old == 0) {
            val explosionSize = 4 + hwRandom16(intensity shr 2)
            hue[s] = u8(hwRandom16())
            sVar[0] = i8(10 + (explosionSize shl 1))
            sMinLife[0] = 150
            sMaxLife[0] = 300
            px[s] = hwRandom(maxX)
            ttl[s] = u16(10 + hwRandom16(255 - speed))
            sSize[0] = custom1
            setFlag(s, F_COLLIDE, check3)
            for (e in 0 until explosionSize) {
                if (check2) hue[s] = u8(hwRandom16())
                sprayEmit(0)
            }
        }
        for (i in 0 until usedParticles) {
            if (aSize[i] != 0) aSize[i]--
            if (aSat[i] < 250) aSat[i] = u8(aSat[i] + 2 + (custom3 shr 3))
        }
        if (urem(call, 5) == 0) applyFriction(1)
        update()
        return true
    }

    private fun geq1D(): Boolean {
        if (!begin(16, 255, true)) return false
        val ns = numSources
        setMotionBlur(custom2)
        val spacing = udiv(maxX, ns)
        for (i in 0 until ns) {
            hue[src(i)] = u8(i * 16)
            sVar[i] = i8(speed shr 2)
            sMinLife[i] = u16(180 + (intensity shr 1))
            sMaxLife[i] = u16(240 + intensity)
            sSat[i] = 255
            sSize[i] = custom1
            px[src(i)] = (spacing shr 1) + spacing * i
        }
        for (i in 0 until usedParticles) ttl[i] = if (ttl[i] > 20) ttl[i] - 20 else 0
        var bin = hwRandom16(ns)
        val threshold = 300 - intensity
        for (i in 0 until ns) {
            bin++
            bin = urem(bin, ns)
            var emit = false
            if (fft[bin] > threshold) emit = true
            else if (fft[bin] > 0) {
                val rest = ((threshold - fft[bin]) shr 2) + 2
                if (urem(hwRandom(), rest) == 0) emit = true
            }
            if (emit) sprayEmit(bin)
        }
        update()
        return true
    }

    private fun fire1D(): Boolean {
        if (call == 0) {
            if (!begin(5)) return false
            setKillOutOfBounds(true)
            setParticleSize(1)
        } else if (!begin(5)) return false
        setMotionBlur(128 + (custom2 shr 1))
        setColorByAge(true)
        var emit = 1
        var j = hwRandom16()
        for (i in 0 until 3) {
            val s = src(i)
            if (ttl[s] > 50) ttl[s] = u16(ttl[s] - 10)
            else ttl[s] = u16(100 + hwRandom16(200))
        }
        for (i in 0 until numSources) {
            j = urem(j + 1, numSources)
            px[src(j)] = 0
            sVar[j] = i8(2 + (speed shr 4))
            if (j > 2) {
                sMinLife[j] = u16(150 + intensity + (j shl 2))
                sMaxLife[j] = u16(200 + intensity + (j shl 3))
                sV[j] = i8(speed shr (2 + (j shl 1)))
                if (emit != 0) { emit--; sprayEmit(j) }
            } else {
                sMinLife[j] = u16(ttl[src(j)] + intensity)
                sMaxLife[j] = u16(sMinLife[j] + 50)
                sV[j] = i8(speed shr 2)
                if ((call and 0x01) != 0) sprayEmit(j)
            }
        }
        for (i in 0 until usedParticles) {
            px[i] += ttl[i] shr 7
            if (ttl[i] > 3 + ((255 - custom1) shr 1)) ttl[i] = u16(ttl[i] - map(custom1, 0, 255, 1, 3))
        }
        update()
        return true
    }

    private fun midsSqrt(): Int = sqrt32bw(fft[5] + fft[6] + fft[7] + fft[8] + fft[9] + fft[10])

    private fun sonicStream(): Boolean {
        if (call == 0) {
            if (!begin(1, 255, true)) return false
            setKillOutOfBounds(true)
            px[src(0)] = 0
            sVar[0] = 0
        } else if (!begin(1, 255, true)) return false
        val s = src(0)
        setMotionBlur(20 + (custom2 shr 1))
        setSmearBlur(200)
        sV[0] = i8(5 + (speed shr 2))
        val baseBin = custom3 shr 1
        var loudness = fft[baseBin]
        if (baseBin > 12) loudness = loudness shl 2
        var threshold = 140 - (intensity shr 1)
        if (check2) {
            step = (step * 31500 + loudness * (32768 - 31500)) ushr 15
            threshold = 20 + (threshold shr 1) + step
        }
        val hueIncrement = custom1 shr 3
        sSat[0] = if (custom1 > 0) 255 else 0
        setColorByPosition(custom1 == 255)
        for (i in 0 until usedParticles) {
            if (!flag(s, F_PERPETUAL)) {
                ttl[i] = if (ttl[i] > 2) ttl[i] - 2 else 0
            }
            if (check1) {
                val mids = midsSqrt()
                hue[i] = u8(hue[i] + ((mids * perlin8(px[i] shl 2, step shl 2)) shr 9))
            }
        }
        if (ult(threshold, loudness)) {
            aux0 = u16(aux0 + hueIncrement)
            sMinLife[0] = u16(100 + ((intensity * loudness * loudness) ushr 13))
            sMaxLife[0] = sMinLife[0]
            hue[s] = u8(aux0)
            sSize[0] = speed
            if (px[aux1] > 3 * PS_P_RADIUS_1D || ttl[aux1] == 0) {
                val idx = sprayEmit(0)
                if (idx >= 0) aux1 = u16(idx)
            }
        } else loudness = 0
        update()
        if (check3) {
            setFlag(s, F_PERPETUAL, true)
            applyFriction(1)
            val moveStep = ((speed + 2) * loudness) shr 10
            if (moveStep != 0) {
                for (i in 0 until usedParticles) {
                    if (ttl[i] != 0) {
                        px[i] += moveStep
                        vx[i] = i8(10 + (speed shr 4))
                    }
                }
            }
        } else {
            setFlag(s, F_PERPETUAL, false)
            for (i in 0 until usedParticles) {
                if (vx[i] == 0) vx[i] = sV[0]
                particleMoveUpdate(i, settings, i)
            }
        }
        return true
    }

    private fun sonicBoom(): Boolean {
        if (call == 0) {
            if (!begin(1, 255, true)) return false
            setKillOutOfBounds(true)
        } else if (!begin(1, 255, true)) return false
        val s = src(0)
        setMotionBlur(180 * (if (check3) 1 else 0))
        setSmearBlur(64 * (if (check3) 1 else 0))
        sVar[0] = i8(map(speed, 0, 255, 10, 127))
        val baseBin = custom3 shr 1
        var loudness = fft[baseBin]
        if (baseBin > 12) loudness = loudness shl 2
        var threshold = 150 - (intensity shr 1)
        if (check2) {
            step = (step * 31500 + loudness * (32768 - 31500)) ushr 15
            threshold = 20 + (threshold shr 1) + step
        }
        for (i in 0 until usedParticles) {
            if (check1) {
                val mids = midsSqrt()
                hue[i] = u8(hue[i] + ((mids * perlin8(px[i] shl 2, step shl 2)) shr 9))
            }
            if (ttl[i] > 16) ttl[i] -= 16
        }
        if (ult(threshold, loudness)) {
            if (aux1 == 0) {
                if (custom2 < 128) px[s] = map(custom2, 0, 127, 0, maxX)
                else if (custom2 < 255) {
                    val st = maxX / ((270 - custom2) shr 3)
                    px[s] = (px[s] + st) % maxX
                    if (px[s] < st) px[s] = st shr 1
                } else px[s] = hwRandom(maxX)
                sSat[0] = if (custom1 > 0) 255 else 0
                if (custom1 == 255) aux0 = u16(map(px[s], 0, maxX, 0, 255))
                else if (custom1 > 0) aux0 = u16(aux0 + (custom1 shr 1))
            }
            aux1 = 1
            sMinLife[0] = 200
            sMaxLife[0] = u16(sMinLife[0] + ((intensity * loudness * loudness) ushr 13))
            hue[s] = u8(aux0)
            var explosionSize = 4 + (maxXpixel shr 2)
            explosionSize = hwRandom16((explosionSize * loudness) ushr 10)
            for (e in 0 until explosionSize) sprayEmit(0)
        } else aux1 = 0
        update()
        return true
    }

    private fun springy(): Boolean {
        if (call == 0) {
            if (!begin(1, 128, true)) return false
            aux0 = 0xFFFF; aux1 = 0xFFFF
        } else if (!begin(1, 128, true)) return false
        val c2i = if (check2) 1 else 0
        setMotionBlur(220 * (if (check1) 1 else 0))
        setSmearBlur(50)
        setUsedParticles(map(custom1, 0, 255, 30 shr c2i, 255 shr (c2i * 2)))
        val used = usedParticles
        val springLength = udiv(maxX, used)
        val springK = map(speed, 0, 255, 5, 35)
        val settingsSum = custom1 + c2i
        setParticleSize(if (check2) 120 else 1)
        if (aux0 != settingsSum) {
            for (i in 0 until used) {
                aSat[i] = 255
                px[i] = (i + 1) * udiv(maxX, used)
            }
            aux0 = u16(settingsSum)
        }
        val dxLimit = (2 + ((255 - speed) shr 5)) * springLength
        val sf = springForce
        java.util.Arrays.fill(sf, 0, used, 0)
        if (px[0] < -springLength) px[0] = -springLength
        else if (px[0] > dxLimit) px[0] = dxLimit
        sf[0] += ((springLength shr 1) - px[0]) * springK
        for (i in 1 until used) {
            if (px[i] < px[i - 1]) { val t = px[i]; px[i] = px[i - 1]; px[i - 1] = t }
            var dx = px[i] - px[i - 1]
            if (dx > dxLimit) {
                px[i] = px[i - 1] + dxLimit
                dx = dxLimit
            }
            val dxLeft = springLength - dx
            sf[i] += dxLeft * springK
            sf[i - 1] -= dxLeft * springK
            if (i == used - 1) {
                if (px[i] >= maxX + springLength) px[i] = maxX + springLength
                val dxRight = (springLength shr 1) - (maxX - px[i])
                sf[i] -= dxRight * springK
            }
        }
        val dampen = urem(call, 9 - (speed shr 5)) == 0
        for (i in 0 until used) {
            sf[i] = sf[i] / 64
            val maxForce = 120
            sf[i] = if (sf[i] > maxForce) maxForce else if (sf[i] < -maxForce) -maxForce else sf[i]
            aForce[i] = applyForce(i, sf[i], aForce[i])
            if (dampen) {
                if (kotlin.math.abs(vx[i]) < 3 && kotlin.math.abs(sf[i]) < (springK shr 2)) vx[i] = i8((vx[i] * 254) / 256)
            }
            ttl[i] = 300
        }
        if (urem(call, 65 - ((intensity * (1 + (speed shr 3))) shr 7)) == 0) applyFriction(intensity shr 2)
        for (i in 1 until used - 1) {
            val restPosition = (springLength shr 1) + i * springLength
            val dx = restPosition - px[i]
            aForce[i] = applyForce(i, if (dx > 0) 1 else if (dx < 0) -1 else 0, aForce[i])
        }
        if (check3) {
            val baseBin = map(custom3, 0, 31, 0, 14)
            val loudness = fft[baseBin] + fft[baseBin + 1]
            if (loudness > 80) {
                val mid = used shr 1
                val offset = (maxX shr 1) - px[mid]
                if (kotlin.math.abs(offset) < (maxX shr 5)) vx[mid] = i8((if (vx[mid] > 0) 1 else -1) * (loudness shr 3))
            }
        } else {
            if (custom3 <= 10) {
                if (Integer.compareUnsigned(now, step) > 0) {
                    val sp = if (custom3 > 5) custom3 - 6 else custom3
                    step = now + 7500 - ((speed shl 3) + (sp shl 10))
                    val amplitude = 40 + (custom1 shr 2)
                    val index = if (custom3 > 5) used / 2 else 0
                    vx[index] = i8(vx[index] + amplitude)
                }
            } else if (custom3 <= 30) {
                val index = if (custom3 > 20) used / 2 else 0
                var restPosition = 0
                if (index > 0) restPosition = maxX shr 1
                var amplitude = 5 + (custom1 shr 2)
                val sp = custom3 - 10 - (if (index != 0) 10 else 0)
                val phase = now * ((1 + (speed shr 4)) * sp)
                if (check2) amplitude = amplitude shl 1
                px[index] = restPosition + ((sin16(phase) * amplitude) shr 12)
            } else {
                if (hwRandom16() < 656) {
                    var amplitude = 60
                    if (check2) amplitude = amplitude shl 1
                    val mid = used shr 1
                    vx[mid] = i8(vx[mid] + hwRandom16(amplitude shl 1) - amplitude)
                }
            }
        }
        for (i in 0 until used) {
            if (custom2 == 255) {
                var speedClr = (i8(kotlin.math.abs(vx[i])) shr 2) shl 4
                if (speedClr > 240) speedClr = 240
                hue[i] = u8(speedClr)
            } else if (custom2 > 0) {
                hue[i] = u8(i * (custom2 shr 2))
            } else {
                var deviation = if (i == 0) springLength / 2 - px[i]
                else if (i == used - 1) springLength / 2 - (maxX - px[i])
                else {
                    val leftDx = px[i] - px[i - 1]
                    val rightDx = px[i + 1] - px[i]
                    var avg = (leftDx + rightDx) shr 1
                    if (avg < 0) avg = 0
                    springLength - avg
                }
                deviation = if (deviation < -127) -127 else if (deviation > 112) 112 else deviation
                hue[i] = u8(127 + deviation)
            }
        }
        update()
        return true
    }

    companion object {
        private const val PS_P_MAXSPEED = 120
        private const val PS_P_RADIUS_1D = 32
        private const val PS_P_HALFRADIUS_1D = 16
        private const val PS_P_RADIUS_SHIFT_1D = 5
        private const val PS_P_SURFACE_1D = 5
        private const val PS_P_MINHARDRADIUS_1D = 32
        private const val PS_P_MINSURFACEHARDNESS_1D = 120
        private const val MAXPARTICLES_1D = 2600
        private const val MAXSOURCES_1D = 64
        private const val SPOT_TYPES_COUNT = 6

        private const val WRAP = 1
        private const val BOUNCE = 2
        private const val KILLOOB = 4
        private const val USEGRAVITY = 8
        private const val USECOLLISIONS = 16
        private const val COLORBYAGE = 32
        private const val COLORBYPOS = 64

        private const val F_OOB = 1
        private const val F_COLLIDE = 2
        private const val F_PERPETUAL = 4
        private const val F_REVERSEGRAV = 8
        private const val F_FIXED = 32
        private const val F_CUSTOM1 = 64

        private val RGBY = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00)

        private val SIN8 = intArrayOf(
            128, 131, 134, 137, 140, 143, 146, 149, 152, 155, 158, 162, 165, 167, 170, 173,
            176, 179, 182, 185, 188, 190, 193, 196, 198, 201, 203, 206, 208, 211, 213, 215,
            218, 220, 222, 224, 226, 228, 230, 232, 234, 235, 237, 239, 240, 242, 243, 244,
            246, 247, 248, 249, 250, 251, 251, 252, 253, 253, 254, 254, 254, 255, 255, 255,
            255, 255, 255, 255, 254, 254, 254, 253, 253, 252, 251, 251, 250, 249, 248, 247,
            246, 244, 243, 242, 240, 239, 237, 235, 234, 232, 230, 228, 226, 224, 222, 220,
            218, 215, 213, 211, 208, 206, 203, 201, 198, 196, 193, 190, 188, 185, 182, 179,
            176, 173, 170, 167, 165, 162, 158, 155, 152, 149, 146, 143, 140, 137, 134, 131,
            128, 124, 121, 118, 115, 112, 109, 106, 103, 100, 97, 93, 90, 88, 85, 82,
            79, 76, 73, 70, 67, 65, 62, 59, 57, 54, 52, 49, 47, 44, 42, 40,
            37, 35, 33, 31, 29, 27, 25, 23, 21, 20, 18, 16, 15, 13, 12, 11,
            9, 8, 7, 6, 5, 4, 4, 3, 2, 2, 1, 1, 1, 0, 0, 0,
            0, 0, 0, 0, 1, 1, 1, 2, 2, 3, 4, 4, 5, 6, 7, 8,
            9, 11, 12, 13, 15, 16, 18, 20, 21, 23, 25, 27, 29, 31, 33, 35,
            37, 40, 42, 44, 47, 49, 52, 54, 57, 59, 62, 65, 67, 70, 73, 76,
            79, 82, 85, 88, 90, 93, 97, 100, 103, 106, 109, 112, 115, 118, 121, 124,
        )
    }
}
