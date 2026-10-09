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

import SwiftUI

extension WLEDEffects1D {

    static func effect_heartbeat(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let bpm: UInt32 = 40 + UInt32(strip.runtime.speed >> 3)
        let msPerBeat: UInt32 = 60000 / bpm
        let secondBeat: UInt32 = msPerBeat / 3

        var briLower = UInt32(strip.runtime.aux1)
        let beatTimer = strip.now &- strip.runtime.step

        briLower = briLower * 2042 / (2048 + UInt32(strip.runtime.intensity))
        strip.runtime.aux1 = UInt16(clamping: briLower)

        if beatTimer > secondBeat && strip.runtime.aux0 == 0 {
            strip.runtime.aux1 = UInt16.max
            strip.runtime.aux0 = 1
        }
        if beatTimer > msPerBeat {
            strip.runtime.aux1 = UInt16.max
            strip.runtime.aux0 = 0
            strip.runtime.step = strip.now
        }

        let blendAmt = UInt8(255 &- UInt8(clamping: Int(strip.runtime.aux1 >> 8)))

        for i in 0..<SEGLEN {
            let palIdx = SEGLEN > 1
                ? UInt8(clamping: (i * 255) / (SEGLEN - 1))
                : UInt8(0)
            let palColor = strip.colorFromPalette(palIdx)
            strip.setPixelColor(i, colorBlend(palColor, strip.runtime.colors[1], blendAmt))
        }

        return FRAMETIME
    }

    // By Andrew Tuline.
    static func effect_twinkleup(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        let prevSeed = _rngState
        seedRandom(535)

        for i in 0..<SEGLEN {
            let ranstart = random8()
            let speedDiv = max(1, Int(256) - Int(strip.runtime.speed))
            let phaseArg = Int(ranstart) &+ 16 &* Int(strip.now) / speedDiv
            let pixBriRaw = sin8(UInt8(truncatingIfNeeded: phaseArg & 0xFF))
            let pixBri: UInt8 = random8() > strip.runtime.intensity ? 0 : pixBriRaw

            let palIdx = UInt8(truncatingIfNeeded: (Int(random8()) &+ Int(strip.now / 100)) & 0xFF)
            let palColor = strip.colorFromPalette(palIdx)
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, pixBri))
        }

        seedRandom(prevSeed)

        return FRAMETIME
    }

    // Slow noise palette by Andrew Tuline.
    static func effect_noisepal(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let scale = 15 + Int(strip.runtime.intensity >> 2)

        let changePaletteMs: UInt32 = 4000 + UInt32(strip.runtime.speed) * 10

        if strip.now &- strip.runtime.step > changePaletteMs {
            strip.runtime.step = strip.now

            strip.runtime.aux1 = UInt16(random8())
        }

        let noiseOffset = Int(strip.runtime.aux0)

        for i in 0..<SEGLEN {
            let index = inoise8(i * scale, noiseOffset + i * scale)
            let palColor = strip.colorFromPalette(index)
            strip.setPixelColor(i, palColor)
        }

        strip.runtime.aux0 &+= UInt16(beatsin8(10, 1, 4, now: strip.now))

        return FRAMETIME
    }

    // By Andrew Tuline.
    static func effect_sinewave(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        let colorIndex = Int(strip.now / 32)

        strip.runtime.step &+= UInt32(strip.runtime.speed) / 16

        let freq = Int(strip.runtime.intensity) / 4

        for i in 0..<SEGLEN {
            let waveArg = UInt8(truncatingIfNeeded: (i * freq + Int(strip.runtime.step)) & 0xFF)
            let pixBri = cubicwave8(waveArg)

            let palIdx = SEGLEN > 0
                ? UInt8(truncatingIfNeeded: (i * colorIndex / max(1, 255)) & 0xFF)
                : UInt8(0)
            let palColor = strip.colorFromPalette(palIdx)
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, pixBri))
        }

        return FRAMETIME
    }

    // By Andrew Tuline.
    static func effect_phasednoise(_ strip: WLEDEffectStrip) -> UInt16 {
        let allfreq: Int = 16
        let cutOff = Int(255 - strip.runtime.intensity)

        let index = UInt8(truncatingIfNeeded: (strip.now / 64) & 0xFF)

        let phaseInc = Int(strip.runtime.speed) / 32
        strip.runtime.step &+= UInt32(phaseInc)
        let phase = Int(strip.runtime.step)

        for i in 0..<strip.segmentLength {
            var modVal = Int(inoise8(i * 10 + i * 10)) / 16
            if modVal == 0 { modVal = 1 }

            let val = (i + 1) * allfreq
            let adjusted = val + phase * ((i % modVal) + 1) / 2
            let bWave = Int(cubicwave8(UInt8(truncatingIfNeeded: adjusted & 0xFF)))
            let bVal = bWave > cutOff ? UInt8(clamping: bWave - cutOff) : UInt8(0)

            let palColor = strip.colorFromPalette(UInt8(truncatingIfNeeded: (Int(index) + i * 256 / max(1, strip.segmentLength)) & 0xFF))
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, bVal))
        }

        return FRAMETIME
    }

    /// By Steve Pomeroy @xxv
    static func effect_dancing_shadows(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let SPOT_TYPE_SOLID = 0
        let SPOT_TYPE_GRADIENT = 1
        let SPOT_TYPE_2X_GRADIENT = 2
        let SPOT_TYPE_2X_DOT = 3
        let SPOT_TYPE_3X_DOT = 4
        let SPOT_TYPE_4X_DOT = 5
        let SPOT_TYPES_COUNT: UInt8 = 6
        let SPOT_MAX_COUNT = 49

        let numSpotlights = max(2, Self.map(Int(strip.runtime.intensity), 0, 255, 2, SPOT_MAX_COUNT))
        let initialize = strip.runtime.aux0 != UInt16(numSpotlights)
        strip.runtime.aux0 = UInt16(numSpotlights)

        let spotSize = 13
        let dataSize = spotSize * numSpotlights
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func getSpeed(_ idx: Int) -> Float {
            let o = idx * spotSize
            let bits = UInt32(strip.runtime.data[o])
                     | (UInt32(strip.runtime.data[o+1]) << 8)
                     | (UInt32(strip.runtime.data[o+2]) << 16)
                     | (UInt32(strip.runtime.data[o+3]) << 24)
            return Float(bitPattern: bits)
        }
        func setSpeed(_ idx: Int, _ v: Float) {
            let o = idx * spotSize
            let bits = v.bitPattern
            strip.runtime.data[o]   = UInt8(bits & 0xFF)
            strip.runtime.data[o+1] = UInt8((bits >> 8) & 0xFF)
            strip.runtime.data[o+2] = UInt8((bits >> 16) & 0xFF)
            strip.runtime.data[o+3] = UInt8((bits >> 24) & 0xFF)
        }
        func getColorIdx(_ idx: Int) -> UInt8 {
            strip.runtime.data[idx * spotSize + 4]
        }
        func setColorIdx(_ idx: Int, _ v: UInt8) {
            strip.runtime.data[idx * spotSize + 4] = v
        }
        func getPosition(_ idx: Int) -> Int16 {
            let o = idx * spotSize + 5
            return Int16(bitPattern: UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o+1]) << 8))
        }
        func setPosition(_ idx: Int, _ v: Int16) {
            let o = idx * spotSize + 5
            let u = UInt16(bitPattern: v)
            strip.runtime.data[o]   = UInt8(u & 0xFF)
            strip.runtime.data[o+1] = UInt8((u >> 8) & 0xFF)
        }
        func getLastUpdate(_ idx: Int) -> UInt32 {
            let o = idx * spotSize + 7
            return UInt32(strip.runtime.data[o])
                 | (UInt32(strip.runtime.data[o+1]) << 8)
                 | (UInt32(strip.runtime.data[o+2]) << 16)
                 | (UInt32(strip.runtime.data[o+3]) << 24)
        }
        func setLastUpdate(_ idx: Int, _ v: UInt32) {
            let o = idx * spotSize + 7
            strip.runtime.data[o]   = UInt8(v & 0xFF)
            strip.runtime.data[o+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[o+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[o+3] = UInt8((v >> 24) & 0xFF)
        }
        func getWidth(_ idx: Int) -> UInt8 {
            strip.runtime.data[idx * spotSize + 11]
        }
        func setWidth(_ idx: Int, _ v: UInt8) {
            strip.runtime.data[idx * spotSize + 11] = v
        }
        func getType(_ idx: Int) -> UInt8 {
            strip.runtime.data[idx * spotSize + 12]
        }
        func setType(_ idx: Int, _ v: UInt8) {
            strip.runtime.data[idx * spotSize + 12] = v
        }

        strip.fill(BLACK)

        let time = strip.now

        for si in 0..<numSpotlights {
            var respawn = false

            if !initialize {
                let spd = getSpeed(si)
                let elapsed = Float(time &- getLastUpdate(si))
                let delta = Int(elapsed * spd * Float(1.0 + Float(strip.runtime.speed)) / 100.0)

                if abs(delta) >= 1 {
                    let pos = getPosition(si)
                    setPosition(si, pos &+ Int16(clamping: delta))
                    setLastUpdate(si, time)
                }

                let pos = Int(getPosition(si))
                let w = Int(getWidth(si))
                let spd2 = getSpeed(si)
                respawn = (spd2 > 0 && pos > SEGLEN + 2)
                       || (spd2 < 0 && pos < -(w + 2))
            }

            if initialize || respawn {
                setColorIdx(si, random8())
                setWidth(si, random8(1, 10))

                let baseSpeed: Float = 1.0 / Float(random8(4, 50))

                if initialize {
                    setPosition(si, Int16(clamping: Int(random16(UInt16(clamping: SEGLEN)))))
                    setSpeed(si, random8(0, 2) != 0 ? baseSpeed : -baseSpeed)
                } else {
                    if random8(0, 2) != 0 {
                        setPosition(si, Int16(clamping: SEGLEN + Int(getWidth(si))))
                        setSpeed(si, -baseSpeed)
                    } else {
                        setPosition(si, Int16(clamping: -Int(getWidth(si))))
                        setSpeed(si, baseSpeed)
                    }
                }

                setLastUpdate(si, time)
                setType(si, random8(0, SPOT_TYPES_COUNT))
            }

            let color = strip.colorFromPalette(getColorIdx(si), 255)
            let start = Int(getPosition(si))
            let w = Int(getWidth(si))
            let spotType = Int(getType(si))

            if w <= 1 {
                if start >= 0 && start < SEGLEN {
                    strip.blendPixelColor(start, color, 128)
                }
            } else {
                switch spotType {
                case SPOT_TYPE_SOLID:
                    for j in 0..<w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            strip.blendPixelColor(start + j, color, 128)
                        }
                    }

                case SPOT_TYPE_GRADIENT:
                    for j in 0..<w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            let blend = cubicwave8(UInt8(clamping: Self.map(j, 0, w - 1, 0, 255)))
                            strip.blendPixelColor(start + j, color, blend)
                        }
                    }

                case SPOT_TYPE_2X_GRADIENT:
                    for j in 0..<w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            let blend = cubicwave8(UInt8(truncatingIfNeeded: (2 * Self.map(j, 0, w - 1, 0, 255)) & 0xFF))
                            strip.blendPixelColor(start + j, color, blend)
                        }
                    }

                case SPOT_TYPE_2X_DOT:
                    var j = 0
                    while j < w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            strip.blendPixelColor(start + j, color, 128)
                        }
                        j += 2
                    }

                case SPOT_TYPE_3X_DOT:
                    var j = 0
                    while j < w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            strip.blendPixelColor(start + j, color, 128)
                        }
                        j += 3
                    }

                case SPOT_TYPE_4X_DOT:
                    var j = 0
                    while j < w {
                        if (start + j) >= 0 && (start + j) < SEGLEN {
                            strip.blendPixelColor(start + j, color, 128)
                        }
                        j += 4
                    }

                default:
                    break
                }
            }
        }

        return FRAMETIME
    }

    // By Stefan Seegel.
    static func effect_washing_machine(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        let speed = tristateSquare8(UInt8(truncatingIfNeeded: (strip.now >> 7) & 0xFF), 90, 15)

        let speedDivisor = max(1, 512 - Int(strip.runtime.speed))
        strip.runtime.step = UInt32(bitPattern: Int32(bitPattern: strip.runtime.step) &+ Int32(Int(speed) * 2048 / speedDivisor))

        for i in 0..<SEGLEN {
            let waveScale = Int(strip.runtime.intensity) / 25 + 1
            let sinArg = (waveScale * 255 * i / max(1, SEGLEN)) + Int(strip.runtime.step >> 7)
            let col = sin8(UInt8(truncatingIfNeeded: sinArg & 0xFF))
            strip.setPixelColor(i, strip.colorFromPalette(col))
        }

        return FRAMETIME
    }

    // Modified and adapted to WLED by Def3nder, based on "Fake TV Light for Engineers" by Phillip Burgess.
    static func effect_tv_simulator(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        let tvSize = 43
        if !strip.runtime.allocateData(tvSize) { return effect_solid(strip) }

        func readU32(_ offset: Int) -> UInt32 {
            UInt32(strip.runtime.data[offset])
            | (UInt32(strip.runtime.data[offset+1]) << 8)
            | (UInt32(strip.runtime.data[offset+2]) << 16)
            | (UInt32(strip.runtime.data[offset+3]) << 24)
        }
        func writeU32(_ offset: Int, _ v: UInt32) {
            strip.runtime.data[offset]   = UInt8(v & 0xFF)
            strip.runtime.data[offset+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[offset+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[offset+3] = UInt8((v >> 24) & 0xFF)
        }
        func readU16(_ offset: Int) -> UInt16 {
            UInt16(strip.runtime.data[offset])
            | (UInt16(strip.runtime.data[offset+1]) << 8)
        }
        func writeU16(_ offset: Int, _ v: UInt16) {
            strip.runtime.data[offset]   = UInt8(v & 0xFF)
            strip.runtime.data[offset+1] = UInt8((v >> 8) & 0xFF)
        }

        let colorSpeed = Self.map(Int(strip.runtime.speed), 0, 255, 1, 20)
        let colorIntensity = Self.map(Int(strip.runtime.intensity), 0, 255, 10, 30)

        let currentSliders = UInt16(strip.runtime.speed) << 8 | UInt16(strip.runtime.intensity)
        if currentSliders != readU16(20) {
            writeU16(20, currentSliders)
            strip.runtime.aux1 = 0
        }

        let sceneStart = readU32(22)
        let sceneDuration = readU32(26)
        if (strip.now &- sceneStart >= sceneDuration) || strip.runtime.aux1 == 0 {
            writeU32(22, strip.now)
            writeU32(26, UInt32(random16(UInt16(clamping: 60 * 250 * colorSpeed), UInt16(clamping: 60 * 750 * colorSpeed))))
            writeU16(30, random16(0, 768))
            strip.runtime.data[32] = random8(100, UInt8(clamping: 130 + colorIntensity))
            strip.runtime.data[33] = random8(200, 240)
            strip.runtime.aux1 = 1
            strip.runtime.aux0 = 0
        }

        if strip.runtime.aux0 == 0 {
            let sceneHue = Int(readU16(30))
            let sceneSat = Int(strip.runtime.data[32])
            let sceneBri = Int(strip.runtime.data[33])

            let j1 = Int(random8(UInt8(clamping: 4 * colorIntensity)))
            var hue: Int
            if random8() < 128 {
                hue = j1 < sceneHue ? sceneHue - j1 : 767 - sceneHue - j1
            } else {
                hue = (j1 + sceneHue) < 767 ? sceneHue + j1 : sceneHue + j1 - 767
            }
            hue = max(0, min(767, hue))

            let j2 = Int(random8(UInt8(clamping: 2 * colorIntensity)))
            let sat = max(0, sceneSat - j2)

            let j3 = Int(random8(100))
            let bri = max(0, sceneBri - j3)

            // Adafruit constant-brightness HSB to RGB.
            let n = (hue >> 8) % 3
            let x = ((hue & 255) * sat >> 8) * bri >> 8
            let s = (256 - sat) * bri >> 8

            var temp = [0, 0, 0, 0, 0]
            temp[0] = s
            temp[3] = s
            temp[1] = x + s
            temp[4] = x + s
            temp[2] = bri - x

            strip.runtime.data[34] = UInt8(clamping: temp[n + 2])
            strip.runtime.data[35] = UInt8(clamping: temp[n + 1])
            strip.runtime.data[36] = UInt8(clamping: temp[n])
        }

        let nr = Int(gamma8(strip.runtime.data[34])) * 257
        let ng = Int(gamma8(strip.runtime.data[35])) * 257
        let nb = Int(gamma8(strip.runtime.data[36])) * 257

        if strip.runtime.aux0 == 0 {
            strip.runtime.aux0 = 1

            let totalTime = UInt32(random16(250, 2500))
            writeU32(0, totalTime)
            let fadeTime = UInt32(random16(0, UInt16(clamping: totalTime)))
            writeU32(4, fadeTime)
            if random8(0, 10) < 3 {
                writeU32(4, 0)
            }

            writeU32(8, strip.now)
        }

        let elapsed = strip.now &- readU32(8)
        writeU32(12, elapsed)

        let totalTime = readU32(0)
        let fadeTime = readU32(4)
        let pr = Int(readU16(37))
        let pg = Int(readU16(39))
        let pb = Int(readU16(41))

        var r_out: Int, g_out: Int, b_out: Int

        if fadeTime > 0 && elapsed < fadeTime {
            r_out = Self.map(Int(elapsed), 0, Int(fadeTime), pr, nr)
            g_out = Self.map(Int(elapsed), 0, Int(fadeTime), pg, ng)
            b_out = Self.map(Int(elapsed), 0, Int(fadeTime), pb, nb)
        } else {
            r_out = nr
            g_out = ng
            b_out = nb
        }

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, UInt8(clamping: r_out >> 8), UInt8(clamping: g_out >> 8), UInt8(clamping: b_out >> 8))
        }

        if elapsed >= totalTime {
            writeU16(37, UInt16(clamping: nr))
            writeU16(39, UInt16(clamping: ng))
            writeU16(41, UInt16(clamping: nb))
            strip.runtime.aux0 = 0
        }

        return FRAMETIME
    }

    private static func tristateSquare8(_ x: UInt8, _ pulsewidth: UInt8, _ attdec: UInt8) -> Int8 {
        var xLocal = x
        var a: Int16 = 127
        if xLocal > 127 {
            a = -127
            xLocal &-= 127
        }

        if xLocal < attdec {
            return Int8(clamping: Int16(xLocal) * a / Int16(attdec))
        } else if xLocal < pulsewidth &- attdec {
            return Int8(clamping: a)
        } else if xLocal < pulsewidth {
            return Int8(clamping: Int16(pulsewidth &- xLocal) * a / Int16(attdec))
        }
        return 0
    }
}
