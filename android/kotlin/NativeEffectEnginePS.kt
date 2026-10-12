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
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

package fx

internal class ParticleFx(private val width: Int, private val height: Int) {

    private var randSeed = 0L

    private fun rand(): Int {
        randSeed = randSeed * 6364136223846793005L + 1L
        return (randSeed ushr 33).toInt()
    }

    private fun hwRandom(): Long = rand().toLong() and 0xFFFFFFFFL
    private fun hwRandom(upper: Long): Long { val u = upper and 0xFFFFFFFFL; return if (u != 0L) (rand().toLong() and 0xFFFFFFFFL) % u else 0L }
    private fun hwRandom8(): Int = rand() and 0xFF
    private fun hwRandom8(upper: Long): Int { val u = upper and 0xFFFFFFFFL; return if (u != 0L) ((rand() and 0xFF).toLong() % u).toInt() else 0 }
    private fun hwRandom16(): Int = rand() and 0xFFFF
    private fun hwRandom16(upper: Long): Int { val u = upper and 0xFFFFFFFFL; return if (u != 0L) ((rand() and 0xFFFF).toLong() % u).toInt() else 0 }
    private fun hwRandom16(upper: Int): Int = hwRandom16(upper.toLong() and 0xFFFFFFFFL)
    private fun hwRandom16(lo: Int, hi: Int): Int {
        val range = hi - lo
        if (range <= 0) return lo.toShort().toInt()
        return (lo + hwRandom16(range.toLong())).toShort().toInt()
    }

    private var fastSeed = 1337
    private fun random8(): Int {
        fastSeed = (fastSeed * 2053 + 13849) and 0xFFFF
        return (fastSeed + (fastSeed shr 8)) and 0xFF
    }
    private fun random16(): Int { val hi = random8(); val lo = random8(); return ((hi shl 8) or lo) and 0xFFFF }

    private fun i8(v: Int) = v.toByte().toInt()
    private fun i16(v: Int) = v.toShort().toInt()
    private fun u8(v: Int) = v and 0xFF
    private fun u16(v: Int) = v and 0xFFFF

    private fun map(x: Int, inMin: Int, inMax: Int, outMin: Int, outMax: Int): Int =
        (x - inMin) * (outMax - outMin) / (inMax - inMin) + outMin

    private fun limitSpeed(s: Int): Int = if (s > PS_P_MAXSPEED) PS_P_MAXSPEED else if (s < -PS_P_MAXSPEED) -PS_P_MAXSPEED else s

    private fun sin16(theta: Int): Int = (SIN8[(theta and 0xFFFF) shr 8] - 128) * 256
    private fun cos16(theta: Int): Int = sin16(theta + 16384)
    private fun scale8(i: Int, s: Int): Int = ((i and 0xFF) * (1 + (s and 0xFF))) shr 8
    private fun lerp8by8(a: Int, b: Int, frac: Int): Int = if (b > a) a + scale8(b - a, frac) else a - scale8(a - b, frac)

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

    private fun tristateSquare8(xIn: Int, pulsewidth: Int, attdec: Int): Int {
        var x = xIn and 0xFF
        var a = 127
        if (x > 127) { a = -127; x = (x - 127) and 0xFF }
        if (x < attdec) return i8(x * a / attdec)
        if (x < pulsewidth - attdec) return a
        if (x < pulsewidth) return i8((pulsewidth - x) * a / attdec)
        return 0
    }

    private fun fastColorScale(c: Int, amount: Int): Int {
        val s = 1 + (amount and 0xFF)
        return ((((c shr 16) and 0xFF) * s shr 8) shl 16) or ((((c shr 8) and 0xFF) * s shr 8) shl 8) or ((c and 0xFF) * s shr 8)
    }

    private fun colorAdd(c1: Int, c2: Int): Int {
        val r = minOf(255, ((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF))
        val g = minOf(255, ((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF))
        val b = minOf(255, (c1 and 0xFF) + (c2 and 0xFF))
        return (r shl 16) or (g shl 8) or b
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

    private fun limitSaturation(c: Int, satLimit: Int): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val maxval = maxOf(r, g, b)
        var h = 0; var s = 0; val v: Int
        if (maxval == 0) return spectrum(0, minOf(0, satLimit), 0)
        val minval = minOf(r, g, b)
        v = maxval
        val delta = maxval - minval
        if (delta != 0) {
            s = (255 * delta) / maxval
            var hh = when (maxval) {
                r -> (10923 * (g - b)) / delta
                g -> (10923 * (b - r)) / delta + 21845
                else -> (10923 * (r - g)) / delta + 43690
            }
            if (hh < 0) hh += 65536
            h = hh and 0xFFFF
        }
        s = minOf(s, satLimit)
        return spectrum(h, s and 0xFF, v and 0xFF)
    }

    private fun spectrum(h: Int, s: Int, v: Int): Int {
        if (s == 0) return (v shl 16) or (v shl 8) or v
        val region = (h * 6) ushr 16
        val remainder = (h - region * 10923) * 6
        val p = (v * (255 - s)) shr 8
        val q = ((v * (255 - ((s * remainder) ushr 16))) ushr 8) and 0xFF
        val t = ((v * (255 - ((s * (65535 - remainder)) ushr 16))) ushr 8) and 0xFF
        val pp = p and 0xFF
        return when (region) {
            0 -> (v shl 16) or (t shl 8) or pp
            1 -> (q shl 16) or (v shl 8) or pp
            2 -> (pp shl 16) or (v shl 8) or t
            3 -> (pp shl 16) or (q shl 8) or v
            4 -> (t shl 16) or (pp shl 8) or v
            else -> (v shl 16) or (pp shl 8) or q
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
            4 -> { val seq = intArrayOf(c2, c2, c1, c1, c1, c0, c0, c0, c2, c2, c1, c1, c0, c0, c2, c2); for (i in 0 until 16) pal[i] = seq[i] }
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
    private var now = 0L

    private var speed = 128; private var intensity = 128
    private var custom1 = 128; private var custom2 = 128; private var custom3 = 16
    private var check1 = false; private var check2 = false; private var check3 = false

    private lateinit var fb: IntArray

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

    private val maxPool = MAXPARTICLES + MAXSOURCES + 1
    private val px = IntArray(maxPool)
    private val py = IntArray(maxPool)
    private val ttl = IntArray(maxPool)
    private val vx = IntArray(maxPool)
    private val vy = IntArray(maxPool)
    private val hue = IntArray(maxPool)
    private val sat = IntArray(maxPool)
    private val fOut = BooleanArray(maxPool)
    private val fCollide = BooleanArray(maxPool)
    private val fPerpetual = BooleanArray(maxPool)

    private val aSize = IntArray(MAXPARTICLES)
    private val aForce = IntArray(MAXPARTICLES)
    private val scAsym = IntArray(MAXPARTICLES); private val scAsymDir = IntArray(MAXPARTICLES)
    private val scMax = IntArray(MAXPARTICLES); private val scMin = IntArray(MAXPARTICLES)
    private val scCounter = IntArray(MAXPARTICLES); private val scWobbleCounter = IntArray(MAXPARTICLES)
    private val scGrowSpeed = IntArray(MAXPARTICLES); private val scShrinkSpeed = IntArray(MAXPARTICLES)
    private val scWobbleSpeed = IntArray(MAXPARTICLES)
    private val scGrow = BooleanArray(MAXPARTICLES); private val scShrink = BooleanArray(MAXPARTICLES)
    private val scPulsate = BooleanArray(MAXPARTICLES); private val scWobble = BooleanArray(MAXPARTICLES)

    private val sMinLife = IntArray(MAXSOURCES); private val sMaxLife = IntArray(MAXSOURCES)
    private val sVar = IntArray(MAXSOURCES); private val sVx = IntArray(MAXSOURCES); private val sVy = IntArray(MAXSOURCES)
    private val sSize = IntArray(MAXSOURCES)

    private var numParticles = 0
    private var usedParticles = 0
    private var numSources = 0
    private var advanced = false
    private var sizeControl = false
    private var psValid = false

    private var settings = 0
    private var maxX = 0; private var maxY = 0; private var maxXpixel = 0; private var maxYpixel = 0
    private var emitIndex = 0
    private var collisionHardness = 0
    private var wallHardness = 0
    private var wallRoughness = 0
    private var particleHardRadius = 0
    private var collisionStartIdx = 0
    private var fireIntensity = 0
    private var forceCounter = 0
    private var gforceCounter = 0
    private var gforce = 0
    private var particleSize = 0
    private var motionBlur = 0
    private var smearBlur = 0
    private var perParticleSize = false

    private var lastcall = 0L

    private fun src(j: Int) = numParticles + j
    private val attractorIdx get() = numParticles + numSources

    private fun setBit(bit: Int, on: Boolean) { settings = if (on) settings or bit else settings and bit.inv() }
    private fun has(opts: Int, bit: Int) = (opts and bit) != 0

    private fun initPS(requestedSources: Int, isAdvanced: Boolean, isSizeControl: Boolean): Boolean {
        val adv = isAdvanced || isSizeControl
        val pixels = width * height
        var n = maxOf(4, minOf(pixels, MAXPARTICLES))
        if (adv) n = (n * 10) / 12
        if (isSizeControl) n /= 8
        n = (n + 3) and 3.inv()
        var ns = minOf(pixels / 4, requestedSources)
        ns = maxOf(1, minOf(ns, MAXSOURCES))
        ns = (ns + 3) and 3.inv()
        numParticles = n; numSources = ns
        advanced = adv; sizeControl = isSizeControl
        for (i in 0 until maxPool) {
            px[i] = 0; py[i] = 0; ttl[i] = 0; vx[i] = 0; vy[i] = 0; hue[i] = 0; sat[i] = 0
            fOut[i] = false; fCollide[i] = false; fPerpetual[i] = false
        }
        java.util.Arrays.fill(aSize, 0); java.util.Arrays.fill(aForce, 0)
        for (a in arrayOf(scAsym, scAsymDir, scMax, scMin, scCounter, scWobbleCounter, scGrowSpeed, scShrinkSpeed, scWobbleSpeed)) java.util.Arrays.fill(a, 0)
        for (a in arrayOf(scGrow, scShrink, scPulsate, scWobble)) java.util.Arrays.fill(a, false)
        for (a in arrayOf(sMinLife, sMaxLife, sVar, sVx, sVy, sSize)) java.util.Arrays.fill(a, 0)
        settings = 0; emitIndex = 0; collisionHardness = 0; collisionStartIdx = 0
        fireIntensity = 0; forceCounter = 0; gforceCounter = 0; gforce = 0; lastcall = 0L
        usedParticles = numParticles
        setMatrixSize()
        wallHardness = 255
        wallRoughness = 0
        setGravity(0)
        setParticleSize(1)
        motionBlur = 0; smearBlur = 0
        for (i in 0 until numParticles) sat[i] = 255
        for (j in 0 until numSources) { sat[src(j)] = 255; ttl[src(j)] = 1; fOut[src(j)] = false; fCollide[src(j)] = false; fPerpetual[src(j)] = false }
        perParticleSize = adv
        psValid = true
        return true
    }

    private fun setMatrixSize() {
        maxXpixel = width - 1; maxYpixel = height - 1
        maxX = width * PS_P_RADIUS - 1; maxY = height * PS_P_RADIUS - 1
    }

    private fun setUsedParticles(percentage: Int) {
        usedParticles = maxOf(1, (numParticles * ((percentage and 0xFF) + 1)) shr 8)
    }
    private fun setWallHardness(h: Int) { wallHardness = h and 0xFF }
    private fun setWallRoughness(r: Int) { wallRoughness = r and 0xFF }
    private fun setCollisionHardness(h: Int) { collisionHardness = (h and 0xFF) + 1 }
    private fun setWrapX(e: Boolean) = setBit(WRAPX, e)
    private fun setWrapY(e: Boolean) = setBit(WRAPY, e)
    private fun setBounceX(e: Boolean) = setBit(BOUNCEX, e)
    private fun setBounceY(e: Boolean) = setBit(BOUNCEY, e)
    private fun setKillOutOfBounds(e: Boolean) = setBit(KILLOOB, e)
    private fun setColorByAge(e: Boolean) = setBit(COLORBYAGE, e)
    private fun setMotionBlur(b: Int) { motionBlur = b and 0xFF }
    private fun setSmearBlur(b: Int) { smearBlur = b and 0xFF }
    private fun setParticleSize(sizeIn: Int) {
        val size = sizeIn and 0xFF
        particleSize = size
        particleHardRadius = PS_P_MINHARDRADIUS
        perParticleSize = false
        if (particleSize > 1) particleHardRadius = PS_P_MINHARDRADIUS + ((particleSize * 52) shr 6)
        else if (particleSize == 0) particleHardRadius = PS_P_MINHARDRADIUS shr 1
    }
    private fun setGravity(force: Int = 8) {
        val f = i8(force)
        if (f != 0) { gforce = f; setBit(USEGRAVITY, true) } else setBit(USEGRAVITY, false)
    }
    private fun enableParticleCollisions(enable: Boolean, hardness: Int = 255) {
        setBit(USECOLLISIONS, enable)
        collisionHardness = (hardness and 0xFF) + 1
    }

    private fun sprayEmit(j: Int): Int {
        var success = false
        for (i in 0 until usedParticles) {
            emitIndex++
            if (emitIndex >= usedParticles) emitIndex = 0
            if (ttl[emitIndex] == 0) {
                success = true
                val v = sVar[j]
                var dx = hwRandom16(v shl 1) - v
                var dy = hwRandom16(v shl 1) - v
                if (v > 5) {
                    while (dx * dx + dy * dy > v * v) {
                        dx = hwRandom16(v shl 1) - v
                        dy = hwRandom16(v shl 1) - v
                    }
                }
                val s = src(j)
                vx[emitIndex] = i8(sVx[j] + dx)
                vy[emitIndex] = i8(sVy[j] + dy)
                px[emitIndex] = px[s]
                py[emitIndex] = py[s]
                hue[emitIndex] = hue[s]
                sat[emitIndex] = sat[s]
                fCollide[emitIndex] = fCollide[s]
                ttl[emitIndex] = u16(hwRandom16(sMinLife[j], sMaxLife[j]))
                if (advanced) aSize[emitIndex] = sSize[j]
                break
            }
        }
        return if (success) emitIndex else -1
    }

    private fun flameEmit(j: Int) {
        val idx = sprayEmit(j)
        if (idx > 0) ttl[idx] = u16(ttl[idx] + ttl[src(j)])
    }

    private fun angleEmit(j: Int, angle: Int, speed: Int): Int {
        sVx[j] = i8((cos16(angle) * speed) / 32600)
        sVy[j] = i8((sin16(angle) * speed) / 32600)
        return sprayEmit(j)
    }

    private fun particleMoveUpdate(p: Int, options: Int = settings, adv: Int = -1) {
        if (ttl[p] <= 0) return
        if (!fPerpetual[p]) ttl[p] = u16(ttl[p] - 1)
        if (has(options, COLORBYAGE)) hue[p] = u8(minOf(ttl[p], 255))
        var renderRadius = PS_P_HALFRADIUS - 1 + particleSize
        var newX = px[p] + vx[p]
        var newY = py[p] + vy[p]
        fOut[p] = false
        if (perParticleSize && adv >= 0) {
            renderRadius = PS_P_HALFRADIUS - 1 + aSize[adv]
            particleHardRadius = if (aSize[adv] > 0) PS_P_MINHARDRADIUS + ((aSize[adv] * 52) shr 6) else PS_P_MINHARDRADIUS shr 1
        }
        if (has(options, BOUNCEY)) {
            if ((newY < particleHardRadius) || ((newY > (maxY - particleHardRadius)) && !has(options, USEGRAVITY))) {
                val r = bounce(vy[p], vx[p], newY, maxY); vy[p] = bIn; vx[p] = bPar; newY = r
            }
        }
        val ry = checkBoundsAndWrap(newY, maxY, renderRadius, has(options, WRAPY))
        newY = cbPos
        if (!ry) {
            fOut[p] = true
            if (has(options, KILLOOB)) {
                if (newY < 0) ttl[p] = 0
                else if (!has(options, USEGRAVITY)) ttl[p] = 0
            }
        }
        if (ttl[p] != 0) {
            if (has(options, BOUNCEX)) {
                if ((newX < particleHardRadius) || (newX > (maxX - particleHardRadius))) {
                    val r = bounce(vx[p], vy[p], newX, maxX); vx[p] = bIn; vy[p] = bPar; newX = r
                }
            } else {
                val rx = checkBoundsAndWrap(newX, maxX, renderRadius, has(options, WRAPX))
                newX = cbPos
                if (!rx) {
                    fOut[p] = true
                    if (has(options, KILLOOB)) ttl[p] = 0
                }
            }
        }
        px[p] = i16(newX)
        py[p] = i16(newY)
    }

    private var cbPos = 0
    private fun checkBoundsAndWrap(position: Int, max: Int, radius: Int, wrap: Boolean): Boolean {
        cbPos = position
        if ((position.toLong() and 0xFFFFFFFFL) > max.toLong()) {
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

    private var bIn = 0; private var bPar = 0
    private fun bounce(incomingIn: Int, parallelIn: Int, positionIn: Int, maxPosition: Int): Int {
        var incoming = i8(-incomingIn)
        incoming = i8((incoming * wallHardness + 128) shr 8)
        var parallel = parallelIn
        val position = if (positionIn < particleHardRadius) particleHardRadius else maxPosition - particleHardRadius
        if (wallRoughness != 0) {
            val incAbs = kotlin.math.abs(incoming)
            val total = incAbs + kotlin.math.abs(parallel)
            var donate = ((hwRandom16(incAbs shl 1) - incAbs) * wallRoughness) / 255
            parallel = i8(limitSpeed(parallel + donate))
            donate = i8(total - kotlin.math.abs(parallel))
            incoming = i8(if (incoming > 0) donate else -donate)
        }
        bIn = incoming; bPar = parallel
        return position
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

    private fun applyForce(p: Int, xforce: Int, yforce: Int, counter: Int): Int {
        val dvx = calcForceDv(xforce, counter and 0x0F)
        val xc = dvCounter
        val dvy = calcForceDv(yforce, (counter and 0xFF) shr 4)
        val yc = dvCounter
        var c = xc and 0x0F
        c = c or ((yc shl 4) and 0xF0)
        vx[p] = i8(limitSpeed(vx[p] + dvx))
        vy[p] = i8(limitSpeed(vy[p] + dvy))
        return c
    }

    private fun applyForceAdv(i: Int, xforce: Int, yforce: Int) {
        if (!advanced) return
        aForce[i] = applyForce(i, xforce, yforce, aForce[i])
    }

    private fun applyForceAll(xforce: Int, yforce: Int) {
        var temp = forceCounter
        for (i in 0 until usedParticles) {
            temp = forceCounter
            temp = applyForce(i, xforce, yforce, temp)
        }
        forceCounter = temp
    }

    private fun applyGravityAll() {
        val dv = calcForceDv(gforce, gforceCounter)
        gforceCounter = dvCounter
        if (dv == 0) return
        for (i in 0 until usedParticles) vy[i] = i8(limitSpeed(vy[i] - dv))
    }

    private fun applyGravity(p: Int) {
        val dv = calcForceDv(gforce, gforceCounter)
        vy[p] = i8(limitSpeed(vy[p] - dv))
    }

    private fun applyFrictionAll(coefficient: Int) {
        val friction = 255 - coefficient
        for (i in 0 until usedParticles) {
            vx[i] = i8((vx[i] * friction) / 255)
            vy[i] = i8((vy[i] * friction) / 255)
        }
    }

    private fun pointAttractor(i: Int, a: Int, strength: Int, swallow: Boolean) {
        if (!advanced) return
        val dx = px[a] - px[i]
        val dy = py[a] - py[i]
        var d2 = dx * dx + dy * dy
        if (d2 < 8192) {
            if (swallow) {
                if (ttl[i] > 7) ttl[i] = u16(ttl[i] - 8) else { ttl[i] = 0; return }
            }
            d2 = 2 * PS_P_RADIUS * PS_P_RADIUS
        }
        val force = ((strength and 0xFF) shl 16) / d2
        val xforce = i8((force * dx) / 1024)
        val yforce = i8((force * dy) / 1024)
        applyForceAdv(i, xforce, yforce)
    }

    private fun updateSize(i: Int): Boolean {
        var newsize = aSize[i]
        var counter = scCounter[i]
        var increment = 0
        if (scGrow[i]) increment = scGrowSpeed[i] else if (scShrink[i]) increment = scShrinkSpeed[i]
        if (increment < 9) {
            counter += increment
            if (counter > 7) { counter -= 8; increment = 1 } else increment = 0
            scCounter[i] = counter and 0x0F
        } else {
            increment = (increment - 8) shl 1
        }
        if (scGrow[i]) {
            if (newsize < scMax[i]) {
                newsize += increment
                if (newsize >= scMax[i]) {
                    scGrow[i] = false
                    newsize = scMax[i]
                    if (scPulsate[i]) scShrink[i] = true
                }
            }
        } else if (scShrink[i]) {
            if (newsize > scMin[i]) {
                newsize -= increment
                if (newsize <= scMin[i]) {
                    if (scMin[i] == 0) return false
                    scShrink[i] = false
                    newsize = scMin[i]
                    if (scPulsate[i]) scGrow[i] = true
                }
            }
        }
        aSize[i] = u8(newsize)
        if (scWobble[i]) scAsymDir[i] = u8(scAsymDir[i] + scWobbleSpeed[i])
        return true
    }

    private fun update() {
        if (has(settings, USEGRAVITY)) applyGravityAll()
        if (sizeControl) {
            for (i in 0 until usedParticles) if (!updateSize(i)) ttl[i] = 0
        }
        if (has(settings, USECOLLISIONS)) handleCollisions()
        for (i in 0 until usedParticles) particleMoveUpdate(i, settings, if (advanced) i else -1)
        render()
    }

    private fun updateFire(intens: Int) {
        fireParticleUpdate()
        fireIntensity = if (intens > 0) intens else 1
        render()
    }

    private fun fireParticleUpdate() {
        for (i in 0 until usedParticles) {
            if (ttl[i] > 0) {
                ttl[i] = u16(ttl[i] - 1)
                val newY = py[i] + vy[i] + (ttl[i] shr 2)
                var newX = px[i] + vx[i]
                fOut[i] = false
                if (newY < -PS_P_HALFRADIUS) fOut[i] = true
                else if (newY > maxY + PS_P_HALFRADIUS) ttl[i] = 0
                else {
                    if (newX < 0 || newX > maxX) {
                        if (has(settings, WRAPX)) {
                            newX %= (maxX + 1)
                            if (newX < 0) newX += maxX + 1
                        } else if (newX < -PS_P_HALFRADIUS || newX > maxX + PS_P_HALFRADIUS) {
                            ttl[i] = 0
                        }
                    }
                    px[i] = i16(newX)
                }
                py[i] = i16(newY)
            }
        }
    }

    private fun render() {
        val n = width * height
        if (motionBlur != 0) {
            for (k in 0 until n) fb[k] = fastColorScale(fb[k], motionBlur)
        } else {
            java.util.Arrays.fill(fb, 0, n, 0)
        }
        for (i in 0 until usedParticles) {
            if (ttl[i] == 0 || fOut[i]) continue
            var brightness: Int
            var base: Int
            if (fireIntensity != 0) {
                brightness = ttl[i] * (3 + (fireIntensity shr 5)) + 5
                brightness = minOf(brightness, 255)
                base = colorFromPalette(brightness)
            } else {
                brightness = minOf(ttl[i] shl 1, 255)
                base = colorFromPalette(hue[i])
                if (sat[i] < 255) base = limitSaturation(base, sat[i])
            }
            renderParticle(i, brightness, base, has(settings, WRAPX), has(settings, WRAPY))
        }
        if (smearBlur != 0) blur2D(smearBlur, smearBlur, smear = true)
    }

    private val pxb = IntArray(4); private val pcx = IntArray(4); private val pcy = IntArray(4); private val pvalid = BooleanArray(4)

    private fun renderParticle(i: Int, brightness: Int, color: Int, wrapX: Boolean, wrapY: Boolean) {
        var size = particleSize
        if (perParticleSize && advanced) size = 1 + aSize[i]
        if (size == 0) {
            val x = (px[i] shr PS_P_RADIUS_SHIFT).toLong() and 0xFFFFFFFFL
            val y = (py[i] shr PS_P_RADIUS_SHIFT).toLong() and 0xFFFFFFFFL
            if (x <= maxXpixel && y <= maxYpixel) {
                val idx = (x + (maxYpixel - y) * (maxXpixel + 1)).toInt()
                fb[idx] = fastColorScaleAdd(fb[idx], color, brightness)
            }
            return
        }
        if (size > 1) { renderLargeParticle(size, i, brightness, color, wrapX, wrapY); return }
        pvalid[0] = true; pvalid[1] = true; pvalid[2] = true; pvalid[3] = true
        val xoffset = px[i] + PS_P_HALFRADIUS
        val yoffset = py[i] + PS_P_HALFRADIUS
        val dx = xoffset and (PS_P_RADIUS - 1)
        val dy = yoffset and (PS_P_RADIUS - 1)
        var x = xoffset shr PS_P_RADIUS_SHIFT
        var y = yoffset shr PS_P_RADIUS_SHIFT
        pcx[1] = x; pcx[2] = x
        pcy[2] = y; pcy[3] = y
        x--; y--
        pcx[0] = x; pcx[3] = x
        pcy[0] = y; pcy[1] = y
        val precal1 = PS_P_RADIUS - dx
        val precal2 = (PS_P_RADIUS - dy) * brightness
        val precal3 = dy * brightness
        pxb[0] = u8((precal1 * precal2) shr PS_P_SURFACE)
        pxb[1] = u8((dx * precal2) shr PS_P_SURFACE)
        pxb[2] = u8((dx * precal3) shr PS_P_SURFACE)
        pxb[3] = u8((precal1 * precal3) shr PS_P_SURFACE)
        if (pcx[0] < 0) {
            if (wrapX) { pcx[0] = maxXpixel; pcx[3] = maxXpixel }
            else { pvalid[0] = false; pvalid[3] = false; if (pcx[0] < -1) return }
        } else if (pcx[1] > maxXpixel) {
            if (wrapX) { pcx[1] = 0; pcx[2] = 0 }
            else { pvalid[1] = false; pvalid[2] = false; if (pcx[0] > maxXpixel) return }
        }
        if (pcy[0] < 0) {
            if (wrapY) { pcy[0] = maxYpixel; pcy[1] = maxYpixel }
            else { pvalid[0] = false; pvalid[1] = false; if (pcy[0] < -1) return }
        } else if (pcy[2] > maxYpixel) {
            if (wrapY) { pcy[2] = 0; pcy[3] = 0 }
            else { pvalid[2] = false; pvalid[3] = false; if (pcy[2] > maxYpixel + 1) return }
        }
        for (k in 0 until 4) {
            if (pvalid[k]) {
                val idx = pcx[k] + (maxYpixel - pcy[k]) * (maxXpixel + 1)
                if (idx in fb.indices) fb[idx] = fastColorScaleAdd(fb[idx], color, pxb[k])
            }
        }
    }

    private fun renderLargeParticle(size: Int, i: Int, brightness: Int, color: Int, wrapX: Boolean, wrapY: Boolean) {
        val xs = px[i]; val ys = py[i]
        val xCenter = xs shr PS_P_RADIUS_SHIFT
        val yCenter = ys shr PS_P_RADIUS_SHIFT
        var xsize = size; var ysize = size
        if (sizeControl && scAsym[i] > 0) {
            val sz = aSize[i]
            val asymdir = scAsymDir[i]
            var deviation = (sz * scAsym[i] + 255) shr 8
            deviation = when {
                asymdir < 64 -> (asymdir * deviation) shr 6
                asymdir < 192 -> ((128 - asymdir) * deviation) shr 6
                else -> ((asymdir - 255) * deviation) shr 6
            }
            xsize = minOf(sz - deviation, 255)
            ysize = minOf(sz + deviation, 255)
        }
        val rxSub = xsize + PS_P_RADIUS + 1
        val rySub = ysize + PS_P_RADIUS + 1
        val rxPix = rxSub shr PS_P_RADIUS_SHIFT
        val ryPix = rySub shr PS_P_RADIUS_SHIFT
        val xMin = xCenter - rxPix; val xMax = xCenter + rxPix
        val yMin = yCenter - ryPix; val yMax = yCenter + ryPix
        val matrixX = maxXpixel + 1; val matrixY = maxYpixel + 1
        val rxSq = rxSub.toLong() * rxSub; val rySq = rySub.toLong() * rySub
        for (pyy in yMin..yMax) {
            for (pxx in xMin..xMax) {
                var rx = pxx; var ry = pyy
                if (rx < 0) { if (!wrapX) continue; rx += matrixX } else if (rx > maxXpixel) { if (!wrapX) continue; rx -= matrixX }
                if (ry < 0) { if (!wrapY) continue; ry += matrixY } else if (ry > maxYpixel) { if (!wrapY) continue; ry -= matrixY }
                val dxs = (pxx shl PS_P_RADIUS_SHIFT) - xs + PS_P_HALFRADIUS
                val dys = (pyy shl PS_P_RADIUS_SHIFT) - ys + PS_P_HALFRADIUS
                val pb = ellipseBrightness(dxs, dys, rxSq, rySq, brightness)
                if (pb == 0) continue
                val idx = rx + (maxYpixel - ry) * matrixX
                if (idx in fb.indices) fb[idx] = fastColorScaleAdd(fb[idx], color, pb)
            }
        }
    }

    private fun ellipseBrightness(dx: Int, dy: Int, rxsq: Long, rysq: Long, maxB: Int): Int {
        val dxSq = (dx.toLong() * dx) and 0xFFFFFFFFL
        val dySq = (dy.toLong() * dy) and 0xFFFFFFFFL
        val distSq = (((dxSq shl 8) and 0xFFFFFFFFL) / rxsq + ((dySq shl 8) and 0xFFFFFFFFL) / rysq) and 0xFFFFFFFFL
        if (distSq >= 256) return 0
        val falloff = (256 - distSq).toInt()
        return u8((maxB * falloff) shr 8)
    }

    private val binIndices = IntArray(MAXPARTICLES)

    private fun handleCollisions() {
        var collDistSq = particleHardRadius shl 1
        collDistSq *= collDistSq
        var binWidth = 6 * PS_P_RADIUS
        var overlap = particleHardRadius shl 1
        if (perParticleSize && advanced) overlap = 512
        val maxBinParticles = maxOf(50, (usedParticles + 1) / 2)
        var numBins = (maxX + (binWidth - 1)) / binWidth
        if (usedParticles < maxBinParticles) { numBins = 1; binWidth = maxX + 1 }
        var nextFrameStartIdx = hwRandom16(usedParticles.toLong())
        var pidx = collisionStartIdx
        for (bin in 0 until numBins) {
            var count = 0
            val binStart = bin * binWidth - overlap
            val binEnd = binStart + binWidth + (overlap shl 1)
            for (k in 0 until usedParticles) {
                if (ttl[pidx] > 0) {
                    if (px[pidx] >= binStart && px[pidx] <= binEnd) {
                        if (!fOut[pidx] && fCollide[pidx]) {
                            if (count >= maxBinParticles) { nextFrameStartIdx = pidx; break }
                            binIndices[count++] = pidx
                        }
                    }
                }
                pidx++
                if (pidx >= usedParticles) pidx = 0
            }
            var massratio1 = 0; var massratio2 = 0
            for (a in 0 until count) {
                val ii = binIndices[a]
                for (b in a + 1 until count) {
                    val jj = binIndices[b]
                    if (perParticleSize && advanced) {
                        collDistSq = (PS_P_MINHARDRADIUS shl 1) + (((aSize[ii] + aSize[jj]) * 52) shr 6)
                        collDistSq *= collDistSq
                        var m1 = PS_P_RADIUS + aSize[ii]; var m2 = PS_P_RADIUS + aSize[jj]
                        m1 *= m1; m2 *= m2
                        val total = m1 + m2
                        massratio1 = (m2 shl 8) / total
                        massratio2 = (m1 shl 8) / total
                    }
                    val dx = (px[jj] + vx[jj]) - (px[ii] + vx[ii])
                    if (dx * dx < collDistSq) {
                        val dy = (py[jj] + vy[jj]) - (py[ii] + vy[ii])
                        if (dy * dy < collDistSq) collideParticles(ii, jj, dx, dy, collDistSq, massratio1, massratio2)
                    }
                }
            }
        }
        collisionStartIdx = u16(nextFrameStartIdx)
    }

    private fun collideParticles(p1: Int, p2: Int, dxIn: Int, dyIn: Int, collDistSq: Int, massratio1: Int, massratio2: Int) {
        var dx = dxIn; var dy = dyIn
        var distanceSquared = dx * dx + dy * dy
        var relVx = vx[p2] - vx[p1]
        var relVy = vy[p2] - vy[p1]
        if (distanceSquared == 0) {
            dx = -1
            if (relVx < 0) dx = 1 else if (relVx == 0) relVx = 1
            dy = -1
            if (relVy < 0) dy = 1 else if (relVy == 0) relVy = 1
            distanceSquared = 2
        }
        val dot = dx * relVx + dy * relVy
        if (dot < 0) {
            val surfaceHardness = maxOf(collisionHardness, PS_P_MINSURFACEHARDNESS shr 1)
            val impulse = ((((-dot) shl 15) / distanceSquared) * surfaceHardness) shr 8
            val ximpulse = (impulse * dx) / 32767
            val yimpulse = (impulse * dy) / 32767
            if (massratio1 != 0) {
                val vx1 = vx[p1] - ((ximpulse * massratio1) shr 7)
                val vy1 = vy[p1] - ((yimpulse * massratio1) shr 7)
                val vx2 = vx[p2] + ((ximpulse * massratio2) shr 7)
                val vy2 = vy[p2] + ((yimpulse * massratio2) shr 7)
                vx[p1] = i8(limitSpeed(vx1)); vy[p1] = i8(limitSpeed(vy1))
                vx[p2] = i8(limitSpeed(vx2)); vy[p2] = i8(limitSpeed(vy2))
            } else {
                vx[p1] = i8(vx[p1] - ximpulse); vy[p1] = i8(vy[p1] - yimpulse)
                vx[p2] = i8(vx[p2] + ximpulse); vy[p2] = i8(vy[p2] + yimpulse)
            }
            if (collisionHardness < PS_P_MINSURFACEHARDNESS && (call and 0x07) == 0) {
                val coeff = collisionHardness + (255 - PS_P_MINSURFACEHARDNESS)
                vx[p1] = i8((vx[p1] * coeff) / 255); vy[p1] = i8((vy[p1] * coeff) / 255)
                vx[p2] = i8((vx[p2] * coeff) / 255); vy[p2] = i8((vy[p2] * coeff) / 255)
            }
        }
        if (distanceSquared < collDistSq && (relVx * relVx + relVy * relVy < 50)) {
            val fairlyRandom = (dot and 0x01) != 0
            val push = 1 + ((collDistSq - distanceSquared) shr 13)
            val pushx = i8(if (dx > 0) -push else push)
            val pushy = i8(if (dy > 0) -push else push)
            if (collisionHardness < 5) {
                if (fairlyRandom) {
                    vx[p1] = 0; vy[p1] = 0; vx[p2] = 0; vy[p2] = 0
                    px[p1] = i16(px[p1] + pushx); py[p1] = i16(py[p1] + pushy)
                }
            } else {
                if (fairlyRandom) { vx[p1] = i8(vx[p1] + pushx); vy[p1] = i8(vy[p1] + pushy) }
                else { vx[p2] = i8(vx[p2] - pushx); vy[p2] = i8(vy[p2] - pushy) }
            }
        }
    }

    private fun blur2D(blurX: Int, blurY: Int, smear: Boolean) {
        val cols = width; val rows = height
        if (blurX != 0) {
            val keep = if (smear) 255 else 255 - blurX
            val seep = blurX shr 1
            for (row in 0 until rows) {
                var cur = fb[row * cols]
                var carry = fastColorScale(cur, seep)
                fb[row * cols] = fastColorScale(cur, keep)
                for (x in 1 until cols) {
                    cur = fb[x + row * cols]
                    val part = fastColorScale(cur, seep)
                    cur = fastColorScale(cur, keep)
                    cur = colorAdd(cur, carry)
                    fb[x - 1 + row * cols] = colorAdd(fb[x - 1 + row * cols], part)
                    fb[x + row * cols] = cur
                    carry = part
                }
            }
        }
        if (blurY != 0) {
            val keep = if (smear) 255 else 255 - blurY
            val seep = blurY shr 1
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
    }

    fun render(
        effectId: Int, frame: IntArray, paletteId: Int, col0: Int, col1: Int, col2: Int,
        speed: Int, intensity: Int, custom1: Int, custom2: Int, custom3: Int,
        check1: Boolean, check2: Boolean, check3: Boolean,
    ) {
        fb = frame
        this.speed = speed and 0xFF; this.intensity = intensity and 0xFF
        this.custom1 = custom1 and 0xFF; this.custom2 = custom2 and 0xFF; this.custom3 = custom3 and 0x1F
        this.check1 = check1; this.check2 = check2; this.check3 = check3
        audioTick()
        now += 22
        loadPalette(paletteId, col0 and 0xFFFFFF, col1 and 0xFFFFFF, col2 and 0xFFFFFF)
        when (effectId) {
            187 -> volcano()
            188 -> fire()
            189 -> fireworks()
            190 -> vortex()
            191 -> perlinFx()
            192 -> pit()
            193 -> box()
            194 -> attractor()
            195 -> impact()
            196 -> waterfall()
            197 -> spray()
            198 -> geq()
            199 -> centerGeq()
            200 -> ghostRider()
            201 -> blobs()
            217 -> galaxy()
            else -> { java.util.Arrays.fill(frame, 0); call++; return }
        }
        call++
    }

    private fun vortex() {
        if (call == 0) {
            initPS(8, false, false)
            setMotionBlur(130)
            for (i in 0 until minOf(numSources, 8)) {
                val s = src(i)
                px[s] = i16((maxX + 1) shr 1); py[s] = i16((maxY + 1) shr 1)
                sMaxLife[i] = 900; sMinLife[i] = 800
            }
            setKillOutOfBounds(true)
        }
        setMatrixSize()
        val spraycount = minOf(numSources, 1 + (custom1 shr 5))
        setSmearBlur(if (check1) 90 else 0)
        for (i in 0 until spraycount) {
            val coloroffset = 0xFF / spraycount
            hue[src(i)] = u8(coloroffset * i)
        }
        var direction = check2
        var currentspeed = step
        if (custom2 > 0) {
            var changeinterval = 1040 - (custom2 shl 2)
            direction = (aux1 and 0x01) != 0
            if (check3) changeinterval = 20 + changeinterval + hwRandom16(changeinterval)
            if (Integer.remainderUnsigned(call, changeinterval) == 0) {
                aux1 = u16(aux1 or 0x02)
                aux1 = if (direction) aux1 and 0x01.inv() and 0xFFFF else u16(aux1 or 0x01)
            }
        }
        val targetspeed = (if (direction) 1 else -1) * (speed shl 3)
        val speeddiff = targetspeed - currentspeed
        var speedincrement = speeddiff / 50
        if (speedincrement == 0) {
            if (speeddiff < 0) speedincrement = -1 else if (speeddiff > 0) speedincrement = 1
        }
        currentspeed += speedincrement
        aux0 = u16(aux0 + currentspeed)
        step = currentspeed
        val angleoffset = u16(0xFFFF / spraycount)
        val skip = PS_P_HALFRADIUS / (intensity + 1) + 1
        if (Integer.remainderUnsigned(call, skip) == 0) {
            var j = hwRandom16(spraycount)
            for (i in 0 until spraycount) {
                sVar[j] = i8(custom3 shr 1)
                angleEmit(j, u16(aux0 + angleoffset * j), (intensity shr 2) + 1)
                j = (j + 1) % spraycount
            }
        }
        update()
    }

    private fun fireworks() {
        if (call == 0) {
            initPS(8, false, false)
            setKillOutOfBounds(true)
            setWallHardness(120)
            val nr = minOf(numSources, 8)
            for (j in 0 until nr) {
                ttl[src(j)] = u16(500 * j)
                vy[src(j)] = -1
            }
        }
        setMatrixSize()
        val numRockets = map(speed, 0, 255, 4, minOf(numSources, 8))
        setWrapX(check1)
        setBounceY(check2)
        setGravity(map(custom3, 0, 31, if (check2) 1 else 0, 10))
        setMotionBlur(map(custom2, 0, 255, 0, 245))
        for (j in 0 until numRockets) {
            val s = src(j)
            applyGravity(s)
            particleMoveUpdate(s)
            if (ttl[s] == 0) {
                if (vy[s] > 0) {
                    vy[s] = 0
                } else if (vy[s] < 0) {
                    py[s] = PS_P_RADIUS
                    px[s] = i16(((maxX shr 2) + hwRandom((maxX shr 1).toLong())).toInt())
                    vy[s] = i8(custom3 + hwRandom16(custom1 shr 3) + 5)
                    vx[s] = i8(hwRandom16(7) - 3)
                    sat[s] = 30
                    ttl[s] = u16(hwRandom16(custom1) + (custom1 shr 1))
                    sMaxLife[j] = 40; sMinLife[j] = 10
                    sVx[j] = 0; sVy[j] = -5; sVar[j] = 4
                }
            }
        }
        var emitparticles: Int
        var frequency = 0; var baseangle = 0; var hueincrement = 0
        var speedV = 0; var currentspeed: Int; var percircle = 0
        var counter = 0
        var angle = 0
        var angleincrement = 0
        var circular = false
        for (j in 0 until numRockets) {
            val s = src(j)
            if (vy[s] > 0) emitparticles = 1
            else if (vy[s] < 0) emitparticles = 0
            else {
                hue[s] = u8(hwRandom16())
                sat[s] = u8(hwRandom16(55) + 200)
                sMaxLife[j] = 200; sMinLife[j] = 100
                ttl[s] = u16(hwRandom16((2000 - (speed shl 2)).toLong()) + 550 - (speed shl 1))
                sVar[j] = i8((intensity shr 4) + 5)
                vy[s] = -1
                emitparticles = hwRandom16(intensity shr 2) + (intensity shr 2) + 5
                if ((random16() and 1) != 0) {
                    circular = true
                    speedV = 2 + hwRandom16(3) + (intensity shr 6)
                    currentspeed = speedV
                    angleincrement = 2730 + hwRandom16(5461)
                    angle = u16(hwRandom16())
                    baseangle = angle
                    percircle = 0xFFFF / angleincrement + 1
                    hueincrement = hwRandom16() and 127
                    val circles = 1 + hwRandom16(3) + (intensity shr 6)
                    frequency = hwRandom16() and 127
                    emitparticles = percircle * circles
                    sVar[j] = i8(angle and 1)
                }
            }
            var i = 0
            while (i < emitparticles) {
                if (circular) {
                    val sineMod = 0xEFFF + sin16(u16(((angle * frequency) shr 4) + baseangle))
                    currentspeed = ((speedV / 2) + ((sineMod * speedV) shr 16)) shr 1
                    angleEmit(j, angle, currentspeed)
                    counter++
                    if (counter > percircle) {
                        counter = 0
                        speedV += 3 + (intensity shr 6)
                        hue[s] = u8(hue[s] + hueincrement)
                        sat[s] = u8(100 + hwRandom16(156))
                    }
                    angle = u16(angle + angleincrement)
                } else {
                    sprayEmit(j)
                    if ((j % 3) == 0) hue[s] = u8(hwRandom16())
                }
                i++
            }
            if (i == 0) py[s] = 1000
            circular = false
        }
        if (check3) for (i in 0 until usedParticles) particleMoveUpdate(i, settings, -1)
        update()
    }

    private fun volcano() {
        val volcanoSettings = BOUNCEX
        if (call == 0) {
            initPS(1, false, false)
            setBounceY(true)
            setGravity()
            setKillOutOfBounds(true)
            setMotionBlur(230)
            val ns = minOf(numSources, 1)
            for (i in 0 until ns) {
                val s = src(i)
                hue[s] = u8(hwRandom16())
                px[s] = i16(maxX / (ns + 1) * (i + 1))
                sMaxLife[i] = 300; sMinLife[i] = 250
                fCollide[s] = true; fPerpetual[s] = true
            }
        }
        val numSprays = minOf(numSources, 1)
        if (Integer.remainderUnsigned(call, 11 - (intensity / 25)) == 0) {
            for (i in 0 until numSprays) {
                val s = src(i)
                py[s] = PS_P_RADIUS + 5
                vy[s] = 0
                hue[s] = u8(hue[s] + 1)
                vx[s] = i8(if (vx[s] > 0) (custom1 shr 2) else -(custom1 shr 2))
                sVy[i] = i8(speed shr 2)
                sVx[i] = 0
                sVar[i] = i8(custom3 shr 1)
                sprayEmit(i)
                setWallHardness(255)
                particleMoveUpdate(s, volcanoSettings)
            }
        }
        setMatrixSize()
        setColorByAge(check1)
        setBounceX(check2)
        setWallHardness(custom2)
        if (check3) enableParticleCollisions(true, custom2) else enableParticleCollisions(false)
        update()
    }

    private fun fire() {
        if (call == 0) {
            initPS(width, false, false)
            aux0 = u16(hwRandom16())
        }
        setMatrixSize()
        setWrapX(check2)
        setMotionBlur(if (check1) 170 else 0)
        setSmearBlur(if (!check1) 60 else 0)
        val firespeed = maxOf(100, speed)
        if (speed < 100) {
            val period = (now - lastcall) and 0xFFFFFFFFL
            if (period < map(speed, 0, 99, 50, 10).toLong()) {
                call--
                return
            }
            lastcall = now and 0xFFFFFFFFL
        }
        val spread = (maxX shr 5) * (custom3 + 1)
        val numFlames = minOf(numSources, 4 + ((spread / PS_P_RADIUS) shl 1))
        val percycle = (numFlames * 2) / 3
        for (i in 0 until numFlames) {
            val s = src(i)
            if ((call and 1) != 0 && ttl[s] > 0) {
                ttl[s] = u16(ttl[s] - 1)
            } else {
                px[s] = i16(((maxX shr 1) - (spread shr 1) + hwRandom(spread.toLong())).toInt())
                py[s] = i16(-(PS_P_RADIUS shl 2))
                ttl[s] = u16(20 + hwRandom16((custom1 * custom1) shr 8) / (1 + (firespeed shr 5)))
                sMaxLife[i] = u16(hwRandom16(height shr 1) + 16)
                sMinLife[i] = sMaxLife[i] shr 1
                sVx[i] = i8(hwRandom16(5) - 2)
                sVy[i] = i8((height shr 1) + (firespeed shr 4) + (custom1 shr 4))
                sVar[i] = i8(2 + hwRandom16(2 + (firespeed shr 4)))
            }
        }
        if (Integer.remainderUnsigned(call, 3) == 0) {
            aux0 = u16(aux0 + 1)
            if (Integer.remainderUnsigned(call, 10) == 0) aux1 = u16(aux1 + 1)
            val windspeed = i8(((perlin8(aux0, aux1) - 127) * custom2) shr 7)
            applyForceAll(windspeed, 0)
        }
        step++
        if (check3) {
            if (Integer.remainderUnsigned(call, map(firespeed, 0, 255, 4, 15)) == 0) {
                for (i in 0 until usedParticles) {
                    if (py[i] < maxY / 4) {
                        val curl = perlin8(px[i], py[i], step shl 4) - 127
                        vx[i] = i8(vx[i] + ((curl * (firespeed + 10)) shr 9))
                    }
                }
            }
        }
        if (hwRandom8() < 10 + (intensity shr 2)) {
            for (i in 0 until usedParticles) {
                if (ttl[i] == 0) {
                    ttl[i] = u16(hwRandom16(height) + 30)
                    px[i] = px[src(0)]
                    py[i] = py[src(0)]
                    vx[i] = vx[src(0)]
                    vy[i] = i8((height shr 1) + (firespeed shr 4) + ((30 + (intensity shr 1) + custom1) shr 4))
                    break
                }
            }
        }
        var j = u8(hwRandom16())
        for (i in 0 until percycle) {
            j = (j + 1) % numFlames
            flameEmit(j)
        }
        updateFire(intensity)
    }

    private fun pit() {
        if (call == 0) {
            initPS(0, true, false)
            setKillOutOfBounds(true)
            setGravity()
            setUsedParticles(170)
        }
        setMatrixSize()
        setWrapX(check1)
        setBounceX(check2)
        setBounceY(check3)
        setWallHardness(minOf(custom2, 150))
        if (custom2 > 0) enableParticleCollisions(true, custom2) else enableParticleCollisions(false)
        if (Integer.remainderUnsigned(call, 128 - (intensity shr 1)) == 0 && intensity > 0) {
            for (i in 0 until usedParticles) {
                if (ttl[i] == 0) {
                    ttl[i] = u16(1500 - (speed shl 2) + hwRandom16(500))
                    px[i] = i16(hwRandom(maxX.toLong()).toInt())
                    py[i] = i16(maxY shl 1)
                    vx[i] = i8(i16(hwRandom16(speed shr 1)) - (speed shr 2))
                    vy[i] = i8(map(speed, 0, 255, -5, -100))
                    hue[i] = u8(hwRandom16())
                    fCollide[i] = true
                    sat[i] = u8((custom3 shl 3) + 7)
                    if (custom1 == 255) {
                        perParticleSize = true
                        aSize[i] = u8(hwRandom16(custom1))
                    } else {
                        setParticleSize(custom1)
                        aSize[i] = custom1
                    }
                    break
                }
            }
        }
        var friction = 1 + (if (check1) 1 else 0)
        if (speed < 50) friction = 50 - speed
        if (Integer.remainderUnsigned(call, 6) == 0) applyFrictionAll(friction)
        update()
    }

    private fun waterfall() {
        if (call == 0) {
            initPS(12, false, false)
            setGravity()
            setKillOutOfBounds(true)
            setMotionBlur(190)
            setSmearBlur(30)
            for (i in 0 until numSources) {
                hue[src(i)] = u8(i * 90)
                fCollide[src(i)] = true
                sMaxLife[i] = 400; sMinLife[i] = 150
            }
        }
        setMatrixSize()
        setWrapX(check1)
        setBounceX(check2)
        setBounceY(check3)
        setWallHardness(custom2)
        val numSprays = u8(minOf(numSources, maxOf(maxXpixel / 6, 2)))
        if (custom2 > 0) enableParticleCollisions(true, custom2)
        else { enableParticleCollisions(false); setWallHardness(120) }
        for (i in 0 until numSprays) hue[src(i)] = u8(hue[src(i)] + 1 + hwRandom16(custom1 shr 1))
        if (Integer.remainderUnsigned(call, 12 - (intensity shr 5)) == 0 && intensity > 0) {
            for (i in 0 until numSprays) {
                sVy[i] = i8(-speed shr 3)
                px[src(i)] = i16(map(custom3, 0, 31, 0, (maxXpixel - numSprays) * PS_P_RADIUS) + i * PS_P_RADIUS * 2)
                py[src(i)] = i16(maxY + (PS_P_RADIUS * ((i shl 2) + 4)))
                sVar[i] = i8(custom1 shr 3)
                sprayEmit(i)
            }
        }
        if (Integer.remainderUnsigned(call, 20) == 0) applyFrictionAll(1)
        update()
    }

    private fun box() {
        if (call == 0) {
            initPS(1, true, false)
            setBounceX(true)
            setBounceY(true)
            aux0 = u16(hwRandom16())
        }
        setMatrixSize()
        setWallHardness(minOf(custom2, 200))
        enableParticleCollisions(true, maxOf(2, custom2))
        val maxParticleSize = minOf((width * height) shr 2, 255)
        val currentParticleSize = map(custom3, 0, 31, 0, maxParticleSize)
        setUsedParticles(map(intensity, 0, 255, 2, 153) / (1 + (currentParticleSize shr 4)))
        if (custom3 < 31) setParticleSize(currentParticleSize) else perParticleSize = true
        for (i in 0 until usedParticles) {
            if (ttl[i] < 260) {
                ttl[i] = 260
                px[i] = i16(hwRandom16(maxX))
                py[i] = i16(hwRandom16(maxY))
                hue[i] = hwRandom8()
                fPerpetual[i] = true
                fCollide[i] = true
                aSize[i] = hwRandom8(maxParticleSize.toLong())
                break
            }
        }
        if (Integer.remainderUnsigned(call, ((255 - speed) shr 6) + 1) == 0 && speed > 0) {
            var xg: Int; var yg: Int
            val increment = (speed shr 6) + 1
            if (check2) {
                val sp = tristateSquare8((now shr 7).toInt(), 90, 15) / ((400 - speed) shr 3)
                aux0 = u16(aux0 + sp)
                if (sp == 0) aux0 = 190
            } else aux0 = u16(aux0 - increment)
            if (check1) {
                xg = i16(perlin8(aux0)) - 127
                yg = i16(perlin8(aux0 + 10000)) - 127
                xg = (xg * custom1) / 128
                yg = (yg * custom1) / 128
            } else {
                xg = (custom1 * cos16(u16(aux0 shl 8))) / 0xFFFF
                yg = (custom1 * sin16(u16(aux0 shl 8))) / 0xFFFF
            }
            if (check3) { if (yg > 0) yg = -yg }
            applyForceAll(xg, yg)
        }
        if ((call and 0x0F) == 0) applyFrictionAll(1)
        update()
    }

    private fun perlinFx() {
        if (call == 0) {
            initPS(1, true, false)
            setKillOutOfBounds(true)
            setMotionBlur(230)
            setBounceY(true)
            aux0 = u16(rand())
        }
        setMatrixSize()
        setWrapX(check1)
        setBounceX(!check1)
        setWallHardness(custom1)
        enableParticleCollisions(check3, custom1)
        setUsedParticles(map(intensity, 0, 255, 25, 128))
        setSmearBlur(if (check2) 15 else 0)
        aux0 = u16(aux0 + 1 + (speed shr 5))
        for (i in 0 until usedParticles) {
            if (ttl[i] == 0) {
                ttl[i] = u16(hwRandom16(500) + 200)
                px[i] = i16(hwRandom(maxX.toLong()).toInt())
                py[i] = i16(hwRandom(maxY.toLong()).toInt())
                fCollide[i] = true
            }
            val scale = 16 - ((31 - custom3) shr 1)
            val xnoise = u16(((px[i].toLong() and 0xFFFFFFFFL) / scale).toInt())
            val ynoise = u16(((py[i].toLong() and 0xFFFFFFFFL) / scale).toInt())
            val baseheight = i16(perlin8(xnoise, ynoise, aux0))
            hue[i] = u8(baseheight)
            if (Integer.remainderUnsigned(call, 8) == 0) {
                val xslope = i8(baseheight + i16(perlin8(xnoise - 10, ynoise, aux0)))
                val yslope = i8(baseheight + i16(perlin8(xnoise, ynoise - 10, aux0)))
                applyForceAdv(i, xslope, yslope)
            }
        }
        if (Integer.remainderUnsigned(call, 16 - (custom2 shr 4)) == 0) applyFrictionAll(2)
        update()
    }

    private fun impact() {
        val meteorSettings = BOUNCEY or USEGRAVITY
        if (call == 0) {
            initPS(8, false, false)
            setKillOutOfBounds(true)
            setGravity()
            setBounceY(true)
            setWallRoughness(220)
            val nm = minOf(numSources, 8)
            for (i in 0 until nm) {
                ttl[src(i)] = u16(hwRandom16(10 * i))
                vy[src(i)] = 10
            }
        }
        setMatrixSize()
        setWrapX(check1)
        setBounceX(check2)
        setMotionBlur(custom3 shl 3)
        val hardness = u8(map(custom2, 0, 255, PS_P_MINSURFACEHARDNESS - 2, 255))
        setWallHardness(hardness)
        enableParticleCollisions(check3, hardness)
        val numMeteors = minOf(numSources, 8)
        for (i in 0 until numMeteors) {
            val s = src(i)
            val emit: Int = if (vy[s] < 0) 1 else if (vy[s] > 0) 0 else {
                vy[s] = 10
                map(intensity, 0, 255, 10, hwRandom16(usedParticles shr 2))
            }
            var e = emit
            while (e > 0) { sprayEmit(i); e-- }
        }
        for (i in 0 until numMeteors) {
            val s = src(i)
            if (ttl[s] != 0) {
                ttl[s] = u16(ttl[s] - 1)
                if (vy[s] < 0) {
                    applyGravity(s)
                    particleMoveUpdate(s, meteorSettings)
                    if (py[s] < (PS_P_RADIUS shl 1)) {
                        vy[s] = 0; vx[s] = 0
                        fCollide[s] = true
                        sMaxLife[i] = 1250; sMinLife[i] = 250
                        ttl[s] = u16(hwRandom16((768 - (speed shl 1)).toLong()) + 40)
                        sVy[i] = i8(custom1 shr 2)
                        sVar[i] = i8(custom1 shr 2)
                    }
                }
            } else if (vy[s] > 0) {
                py[s] = i16(maxY + (PS_P_RADIUS shl 2))
                px[s] = i16(hwRandom(maxX.toLong()).toInt())
                vy[s] = i8(-hwRandom16(30) - 30)
                vx[s] = i8(hwRandom16(50) - 25)
                hue[s] = u8(hwRandom16())
                ttl[s] = 500
                fCollide[s] = false
                sMaxLife[i] = 300; sMinLife[i] = 100
                sVy[i] = -9; sVar[i] = 3
            }
        }
        for (i in 0 until usedParticles) if (ttl[i] > 5) ttl[i] = u16(ttl[i] - 5)
        update()
    }

    private fun attractor() {
        val sourceSettings = BOUNCEX or BOUNCEY
        if (call == 0) {
            initPS(1, true, false)
            val s = src(0)
            hue[s] = u8(hwRandom16())
            vx[s] = -7
            fCollide[s] = true
            fPerpetual[s] = true
            sMaxLife[0] = 350; sMinLife[0] = 50
            sVar[0] = 4
            setWallHardness(255)
            setWallRoughness(200)
        }
        setMatrixSize()
        setColorByAge(check1)
        setParticleSize(custom1 shr 1)
        setUsedParticles(map(intensity, 0, 255, 25, 190))
        val a = attractorIdx
        ttl[a] = 100
        if (check2) {
            if (Integer.remainderUnsigned(call, 3) == 0) {
                fOut[a] = false; fCollide[a] = false; fPerpetual[a] = false
                particleMoveUpdate(a, sourceSettings)
            }
        } else {
            px[a] = i16(maxX shr 1)
            py[a] = i16(maxY shr 1)
        }
        if (call == 0) {
            vx[a] = vy[src(0)]
            vy[a] = vx[src(0)]
        }
        if (custom2 > 0) enableParticleCollisions(true, map(custom2, 1, 255, 120, 255)) else enableParticleCollisions(false)
        if (Integer.remainderUnsigned(call, 5) == 0) hue[src(0)] = u8(hue[src(0)] + 1)
        aux0 = u16(aux0 + 256)
        if (Integer.remainderUnsigned(call, 2) == 0) angleEmit(0, aux0, 12)
        else angleEmit(0, u16(aux0 + 0x7FFF), 12)
        val strength = speed
        for (i in 0 until usedParticles) pointAttractor(i, a, strength, check3)
        if (Integer.remainderUnsigned(call, 33 - custom3) == 0) applyFrictionAll(2)
        particleMoveUpdate(src(0), sourceSettings)
        update()
    }

    private fun spray() {
        val hardness = 200
        if (call == 0) {
            initPS(1, false, false)
            setKillOutOfBounds(true)
            setBounceY(true)
            setMotionBlur(200)
            setSmearBlur(10)
            hue[src(0)] = u8(hwRandom16())
            fCollide[src(0)] = true
            sVar[0] = 3
        }
        setMatrixSize()
        setBounceX(!check2)
        setWrapX(check2)
        setWallHardness(hardness)
        setGravity(8 * (if (check1) 1 else 0))
        if (check3) enableParticleCollisions(true, hardness) else enableParticleCollisions(false)
        px[src(0)] = i16(map(custom1, 0, 255, 0, maxX))
        py[src(0)] = i16(map(custom2, 0, 255, 0, maxY))
        val angle = u16((256 - ((custom3 + 1) shl 3)) shl 8)
        if (Integer.remainderUnsigned(call, 11 - (intensity / 25)) == 0) {
            sMaxLife[0] = u16(300 + intensity)
            sMinLife[0] = u16(150 + intensity)
            hue[src(0)] = u8(hue[src(0)] + 1)
            angleEmit(0, angle, speed shr 2)
        }
        update()
    }

    private fun geq() {
        if (call == 0) {
            initPS(1, false, false)
            setKillOutOfBounds(true)
            setUsedParticles(170)
        }
        setMatrixSize()
        setWrapX(check1)
        setBounceX(check2)
        setBounceY(check3)
        setWallHardness(custom2)
        setGravity(custom3 shl 2)
        var i = 0
        val binwidth = (maxX + 1) shr 4
        val threshold = 300 - intensity
        for (bin in 0 until 16) {
            val xposition = binwidth * bin + (binwidth shr 1)
            val emitspeed = u8((fft[bin] * speed) shr 9)
            var emitparticles = 0
            if (fft[bin] > threshold) emitparticles = 1
            else if (fft[bin] > 0) {
                val restvolume = ((threshold - fft[bin]) shr 2) + 2
                if (hwRandom16() % restvolume == 0) emitparticles = 1
            }
            while (i < usedParticles && emitparticles > 0) {
                if (ttl[i] == 0) {
                    ttl[i] = u16(20 + map(intensity, 0, 255, emitspeed shr 1, emitspeed + hwRandom16(emitspeed)))
                    px[i] = i16(xposition + hwRandom16(binwidth) - (binwidth shr 1))
                    py[i] = 0
                    vx[i] = i8(hwRandom16(custom1 shr 1) - (custom1 shr 2))
                    vy[i] = i8(emitspeed)
                    hue[i] = u8((bin shl 4) + hwRandom16(17) - 8)
                    emitparticles--
                }
                i++
            }
        }
        update()
    }

    private fun centerGeq() {
        if (call == 0) {
            initPS(16, false, false)
            val ns = minOf(numSources, 16)
            for (i in 0 until ns) {
                val s = src(i)
                px[s] = i16((maxX + 1) shr 1); py[s] = i16((maxY + 1) shr 1)
                hue[s] = u8(i * 16)
                sMaxLife[i] = 400; sMinLife[i] = 200
            }
            setKillOutOfBounds(true)
        }
        setMatrixSize()
        val numSprays = u8(minOf(numSources, 16))
        val threshold = 300 - intensity
        aux0 = if (check2) u16(aux0 + (custom1 shl 2)) else u16(aux0 - (custom1 shl 2))
        val angleoffset = u16(0xFFFF / numSprays)
        var j = hwRandom16(numSprays)
        for (i in 0 until numSprays) {
            if (Integer.remainderUnsigned(call, 32 - (custom2 shr 3)) == 0 && custom2 > 0) hue[src(j)] = u8(hue[src(j)] + 1 + (custom2 shr 4))
            sVar[j] = i8(custom3 shr 2)
            val emitspeed = i8(5 + ((fft[j] * (speed + 20)) shr 10))
            val emitangle = u16(j * angleoffset + aux0)
            var emit = 0
            if (fft[j] > threshold) emit = 1
            else if (fft[j] > 0) {
                val restvolume = ((threshold - fft[j]) shr 2) + 2
                if (hwRandom16() % restvolume == 0) emit = 1
            }
            if (emit != 0) angleEmit(j, emitangle, emitspeed)
            j = (j + 1) % numSprays
        }
        update()
    }

    private fun ghostRider() {
        var ghostSettings = WRAPX or WRAPY
        if (call == 0) {
            initPS(1, false, false)
            setKillOutOfBounds(true)
            sMaxLife[0] = 260; sMinLife[0] = 250
            px[src(0)] = i16(hwRandom16(maxX))
            py[src(0)] = i16(hwRandom16(maxY))
            step = hwRandom16(MAXANGLESTEP) - (MAXANGLESTEP shr 1)
        }
        if (intensity > 0) {
            if (aux1 != 0) {
                step += intensity shr 3
                if (step > MAXANGLESTEP) aux1 = 0
            } else {
                step -= intensity shr 3
                if (step < -MAXANGLESTEP) aux1 = 1
            }
        }
        setMatrixSize()
        setMotionBlur(custom1)
        sVar[0] = i8(custom3 shr 1)
        if (check1) for (i in 0 until usedParticles) hue[i] = u8(hue[src(0)] + (ttl[i] shl 2))
        ghostSettings = if (check2) ghostSettings or BOUNCEX or BOUNCEY else ghostSettings and (BOUNCEX or BOUNCEY).inv()
        aux0 = u16(aux0 + step)
        val emitangle = u16(aux0 + 32767)
        val sp = map(speed, 0, 255, 12, 64)
        val s = src(0)
        vx[s] = i8((cos16(aux0) * sp) / 32767)
        vy[s] = i8((sin16(aux0) * sp) / 32767)
        ttl[s] = 500
        particleMoveUpdate(s, ghostSettings)
        val head = usedParticles - 1
        px[head] = px[s]; py[head] = py[s]
        ttl[head] = 255
        sat[head] = 0
        angleEmit(0, emitangle, sp)
        angleEmit(0, emitangle, sp)
        if (Integer.remainderUnsigned(call, 11 - (custom2 / 25)) == 0) hue[s] = u8(hue[s] + 1)
        if (custom2 > 190) hue[s] = u8(hue[s] + ((custom2 - 190) shr 2))
        update()
    }

    private fun blobs() {
        if (call == 0) {
            initPS(0, true, true)
            setBounceX(true)
            setBounceY(true)
            setWallHardness(255)
            setWallRoughness(255)
            setCollisionHardness(255)
            perParticleSize = true
        }
        setMatrixSize()
        setUsedParticles(map(intensity, 0, 255, 25, 128))
        setBit(USECOLLISIONS, check2); collisionHardness = 256
        for (i in 0 until usedParticles) {
            if (aux0 != speed || ttl[i] == 0) {
                vx[i] = i8(i8(hwRandom16(speed shr 1)) - (speed shr 2))
                vy[i] = i8(i8(hwRandom16(speed shr 1)) - (speed shr 2))
            }
            if (aux1 != custom1 || ttl[i] == 0) scMax[i] = u8(60 + (custom1 shr 1) + hwRandom16(custom1 shr 2))
            if (ttl[i] == 0) {
                ttl[i] = u16(300 + hwRandom16((u16(custom2) shl 3) + 100))
                px[i] = i16(hwRandom(maxX.toLong()).toInt())
                py[i] = i16(hwRandom16(maxY))
                hue[i] = u8(hwRandom16())
                fCollide[i] = true
                aSize[i] = 0
                scAsym[i] = u8(hwRandom16(220))
                scAsymDir[i] = u8(hwRandom16(255))
                scGrow[i] = true
                scGrowSpeed[i] = (1 + hwRandom16(9)) and 0x0F
                scShrinkSpeed[i] = (1 + hwRandom16(9)) and 0x0F
                scWobbleSpeed[i] = (1 + hwRandom16(3)) and 0x0F
            }
            scPulsate[i] = check3
            scWobble[i] = check1
        }
        aux0 = speed
        aux1 = custom1
        setMotionBlur((custom3 shl 3) + 7)
        update()
    }

    private fun galaxy() {
        val sourceSettings = BOUNCEX or BOUNCEY
        if (call == 0) {
            initPS(1, true, false)
            val s = src(0)
            vx[s] = -4
            px[s] = i16(maxX shr 1); py[s] = i16(maxY shr 1)
            fPerpetual[s] = true
            sMaxLife[0] = 4000; sMinLife[0] = 800
            hue[s] = u8(hwRandom16())
            setWallHardness(255)
            setWallRoughness(200)
        }
        setMatrixSize()
        setParticleSize(custom1)
        setMotionBlur(250 * (if (check3) 1 else 0))
        if (Integer.remainderUnsigned(call, (33 - custom3) shr 1) == 0) hue[src(0)] = u8(hue[src(0)] + 2)
        if (hwRandom8() < (10 + (intensity shr 1))) sprayEmit(0)
        if ((call and 0x3) == 0) particleMoveUpdate(src(0), sourceSettings)
        val centerx = maxX shr 1
        val centery = maxY shr 1
        if (check2) {
            setKillOutOfBounds(true)
            sVar[0] = 7
            px[src(0)] = i16(centerx); py[src(0)] = i16(centery)
        } else {
            setKillOutOfBounds(false)
            sVar[0] = 1
        }
        for (i in 0 until usedParticles) {
            if (ttl[i] == 0) continue
            val dx = centerx - px[i]
            val dy = centery - py[i]
            var distance = kotlin.math.sqrt((dx * dx + dy * dy).toFloat()).toInt()
            if (distance < 20) distance = 20
            if (check2) {
                val speedfactor = 1 + (1 + (speed shr 1)) * distance
                px[i] = i16(px[i] + (-speedfactor * dx) / 400000 - (dy shr 6))
                py[i] = i16(py[i] + (-speedfactor * dy) / 400000 + (dx shr 6))
            } else {
                val speedfactor = 2 + (((50 + speed) shl 6) / distance)
                val tempVx = -speedfactor * dy
                val tempVy = speedfactor * dx
                val vxc = (dx shl 9) / (distance - 19)
                val vyc = (dy shl 9) / (distance - 19)
                px[i] = i16(px[i] + (tempVx + vxc) / 1024)
                py[i] = i16(py[i] + (tempVy + vyc) / 1024)
                if (distance < 128) {
                    if (ttl[i] > 3) ttl[i] = u16(ttl[i] - 4)
                    sat[i] = u8(distance shl 1)
                }
            }
            if (custom3 == 31) hue[i] = u8(ttl[i] shr 2)
            else if (custom3 == 0) hue[i] = u8(map(distance, 20, (maxX + maxY) shr 2, 0, 180))
        }
        update()
    }

    companion object {
        private const val PS_P_MAXSPEED = 120
        private const val PS_P_RADIUS = 64
        private const val PS_P_HALFRADIUS = 32
        private const val PS_P_RADIUS_SHIFT = 6
        private const val PS_P_SURFACE = 12
        private const val PS_P_MINHARDRADIUS = 64
        private const val PS_P_MINSURFACEHARDNESS = 128
        private const val MAXPARTICLES = 2048
        private const val MAXSOURCES = 128
        private const val MAXANGLESTEP = 2200

        private const val WRAPX = 1
        private const val WRAPY = 2
        private const val BOUNCEX = 4
        private const val BOUNCEY = 8
        private const val KILLOOB = 16
        private const val USEGRAVITY = 32
        private const val USECOLLISIONS = 64
        private const val COLORBYAGE = 128

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
