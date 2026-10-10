/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-04-19.
 */

import SwiftUI

extension WLEDEffects1D {

    static func effect_tricolor_chase(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let color1 = strip.runtime.colors[2]
        let color2 = strip.runtime.colors[0]

        let cycleTime = UInt32(50) + UInt32(255 - UInt32(strip.runtime.speed)) << 1
        let it = strip.now / cycleTime
        let width = Int(1 + (strip.runtime.intensity >> 4))
        var index = Int(it % UInt32(width * 3))

        for i in 0..<SEGLEN {
            if index > (width * 3) - 1 { index = 0 }

            var color: WLEDColor = color1
            if index > (width << 1) - 1 {
                color = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1)
            } else if index > width - 1 {
                color = color2
            }

            strip.setPixelColor(SEGLEN - i - 1, color)
            index += 1
        }
        return FRAMETIME
    }

    static func effect_tricolor_wipe(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let cycleTime = UInt32(1000) + UInt32(255 - UInt32(strip.runtime.speed)) * 200
        let perc = strip.now % cycleTime
        let prog = Int((UInt32(perc) * 65535) / cycleTime)
        let ledIndex = (prog * SEGLEN * 3) >> 16
        var ledOffset = ledIndex

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 2))
        }

        if ledIndex < SEGLEN {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, i > ledOffset ? strip.runtime.colors[0] : strip.runtime.colors[1])
            }
        } else if ledIndex < SEGLEN * 2 {
            ledOffset = ledIndex - SEGLEN
            for i in (ledOffset + 1)..<SEGLEN {
                strip.setPixelColor(i, strip.runtime.colors[1])
            }
        } else {
            ledOffset = ledIndex - SEGLEN * 2
            for i in 0...ledOffset {
                if i < SEGLEN {
                    strip.setPixelColor(i, strip.runtime.colors[0])
                }
            }
        }
        return FRAMETIME
    }

    static func effect_tricolor_fade(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let counter = UInt16(truncatingIfNeeded: UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 3) &+ 1))
        let prog = (UInt32(counter) &* 768) >> 16

        var color1: WLEDColor = 0
        var color2: WLEDColor = 0
        var stage: Int = 0

        if prog < 256 {
            color1 = strip.runtime.colors[0]
            color2 = strip.runtime.colors[1]
            stage = 0
        } else if prog < 512 {
            color1 = strip.runtime.colors[1]
            color2 = strip.runtime.colors[2]
            stage = 1
        } else {
            color1 = strip.runtime.colors[2]
            color2 = strip.runtime.colors[0]
            stage = 2
        }

        let stp = UInt8(truncatingIfNeeded: prog & 0xFF)

        for i in 0..<SEGLEN {
            let color: WLEDColor
            if stage == 2 {
                color = colorBlend(strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 2), color2, stp)
            } else if stage == 1 {
                color = colorBlend(color1, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 2), stp)
            } else {
                color = colorBlend(color1, color2, stp)
            }
            strip.setPixelColor(i, color)
        }
        return FRAMETIME
    }

    static func effect_icu(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let now16 = UInt16(truncatingIfNeeded: strip.now & 0xFFFF)
        var dest = Int(strip.runtime.aux1)
        let space = Int(strip.runtime.intensity >> 3) + 2

        var state = UInt16(strip.runtime.step >> 16)
        var nextUpdate = UInt16(strip.runtime.step & 0xFFFF)

        let maxDest = max(1, SEGLEN - SEGLEN / space)
        let pindex = UInt8(clamping: self.map(dest, 0, maxDest, 0, 255))
        let col = strip.colorFromPalette(pindex)
        let bgcol: WLEDColor = strip.runtime.check2 ? BLACK : strip.runtime.colors[1]
        strip.fill(bgcol)

        if state != 1 {
            strip.setPixelColor(dest, col)
            let secondEye = dest + SEGLEN / space
            if secondEye < SEGLEN { strip.setPixelColor(secondEye, col) }

            if state == 3 {
                if strip.runtime.aux0 > strip.runtime.aux1 {
                    dest += 1
                } else if strip.runtime.aux0 < strip.runtime.aux1 {
                    dest -= 1
                }
                if dest >= 0 && dest < SEGLEN { strip.setPixelColor(dest, col) }
                let se2 = dest + SEGLEN / space
                if se2 >= 0 && se2 < SEGLEN { strip.setPixelColor(se2, col) }
            }
        }

        let diff = Int16(bitPattern: now16 &- nextUpdate)
        if diff >= 0 {
            switch state {
            case 0:
                state &+= 1
                if random8(6) == 0 {
                    nextUpdate = now16 &+ 200
                    break
                }
                fallthrough
            case 1:
                nextUpdate = now16 &+ 500 &+ random16(1000)
                state &+= 1
            case 2:
                strip.runtime.aux0 = random16(UInt16(max(1, SEGLEN - SEGLEN / space)))
                nextUpdate = now16
                state &+= 1
            default:
                strip.runtime.aux1 = UInt16(clamping: dest)
                let speedFormulaL = UInt16(5 + (50 * Int(255 - Int(strip.runtime.speed))) / max(1, SEGLEN))
                nextUpdate = now16 &+ speedFormulaL
                if strip.runtime.aux0 == UInt16(clamping: dest) {
                    nextUpdate = now16 &+ 500 &+ random16(1000)
                    state = 0
                }
            }
        }

        strip.runtime.step = (UInt32(state) << 16) | UInt32(nextUpdate)
        return FRAMETIME
    }

    static func effect_multi_comet(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxComets = 8
        let cycleTime = UInt32(10) + UInt32(255 - UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime
        if strip.runtime.step == it { return FRAMETIME }

        let dataSize = 2 * maxComets
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        strip.fadeOut(UInt8(clamping: Int(strip.runtime.intensity) / 2 + 128))

        func readComet(_ idx: Int) -> UInt16 {
            let o = idx * 2
            return UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o + 1]) << 8)
        }
        func writeComet(_ idx: Int, _ v: UInt16) {
            let o = idx * 2
            strip.runtime.data[o] = UInt8(v & 0xFF)
            strip.runtime.data[o + 1] = UInt8(v >> 8)
        }

        for i in 0..<maxComets {
            let cometPos = readComet(i)
            if cometPos < UInt16(SEGLEN) {
                let index = Int(cometPos)
                if strip.runtime.colors[2] != 0 {
                    let c: WLEDColor = (i % 2 != 0) ?
                        strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0) :
                        strip.runtime.colors[2]
                    strip.setPixelColor(index, c)
                } else {
                    strip.setPixelColor(index, strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0))
                }
                writeComet(i, cometPos &+ 1)
            } else {
                if random16(UInt16(SEGLEN)) == 0 {
                    writeComet(i, 0)
                }
            }
        }

        strip.runtime.step = it
        return FRAMETIME
    }

    static func effect_dual_larson_scanner(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let speed = Int(FRAMETIME) * self.map(Int(strip.runtime.speed), 0, 255, 96, 2)
        let pixels = SEGLEN / max(1, speed)

        strip.fadeOut(255 &- strip.runtime.intensity)

        if strip.runtime.step > strip.now { return FRAMETIME }

        var index = Int(strip.runtime.aux1) + pixels
        if pixels == 0 {
            let frames = UInt32(speed / max(1, SEGLEN))
            if strip.runtime.step < frames {
                strip.runtime.step &+= 1
                return FRAMETIME
            }
            strip.runtime.step = 0
            index += 1
        }

        if index > SEGLEN {
            strip.runtime.aux0 = strip.runtime.aux0 != 0 ? 0 : 1
            strip.runtime.aux1 = 0
            if strip.runtime.aux0 != 0 || strip.runtime.check2 {
                strip.runtime.step = strip.now &+ UInt32(strip.runtime.custom1) &* 25
            } else {
                strip.runtime.step = 0
            }
        } else {
            for i in Int(strip.runtime.aux1)..<index {
                if i >= SEGLEN { break }
                let j = strip.runtime.aux0 != 0 ? i : SEGLEN - 1 - i
                let c = strip.colorFromPalette(i: j, mapping: true, wrap: true, colorIndex: 0)
                strip.setPixelColor(j, c)
                let mirror = SEGLEN - 1 - j
                let mirrorCol = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : c
                strip.setPixelColor(mirror, mirrorCol)
            }
            strip.runtime.aux1 = UInt16(clamping: index)
        }
        return FRAMETIME
    }

    static func effect_random_chase(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        if strip.runtime.call == 0 {
            strip.runtime.step = rgbw32(random8(), random8(), random8())
            strip.runtime.aux0 = random16()
        }

        let cycleTime = UInt32(25) + 3 &* UInt32(255 - UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime
        var color = strip.runtime.step

        var prng = UInt32(strip.runtime.aux0) | (UInt32(strip.runtime.aux0) << 16)
        func localRandom8() -> UInt8 {
            prng = prng &* 1103515245 &+ 12345
            return UInt8(truncatingIfNeeded: (prng >> 16) & 0xFF)
        }

        for i in stride(from: SEGLEN - 1, through: 0, by: -1) {
            let cr: UInt8 = localRandom8() % 6 != 0 ? UInt8((color >> 16) & 0xFF) : localRandom8()
            let cg: UInt8 = localRandom8() % 6 != 0 ? UInt8((color >> 8) & 0xFF) : localRandom8()
            let cb: UInt8 = localRandom8() % 6 != 0 ? UInt8(color & 0xFF) : localRandom8()
            color = rgbw32(cr, cg, cb)
            strip.setPixelColor(i, color)
            if i == SEGLEN - 1 && strip.runtime.aux1 != UInt16(it & 0xFFFF) {
                strip.runtime.step = color
                strip.runtime.aux0 = UInt16(truncatingIfNeeded: prng & 0xFFFF)
            }
        }

        strip.runtime.aux1 = UInt16(it & 0xFFFF)
        return FRAMETIME
    }

    static func effect_oscillate(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let numOscillators = 3
        let dataSize = numOscillators * 5
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func readOsc(_ idx: Int) -> (pos: UInt16, size: UInt8, dir: Int8, speed: UInt8) {
            let o = idx * 5
            let pos = UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o + 1]) << 8)
            let size = strip.runtime.data[o + 2]
            let dir = Int8(bitPattern: strip.runtime.data[o + 3])
            let speed = strip.runtime.data[o + 4]
            return (pos, size, dir, speed)
        }
        func writeOsc(_ idx: Int, pos: UInt16, size: UInt8, dir: Int8, speed: UInt8) {
            let o = idx * 5
            strip.runtime.data[o] = UInt8(pos & 0xFF)
            strip.runtime.data[o + 1] = UInt8(pos >> 8)
            strip.runtime.data[o + 2] = size
            strip.runtime.data[o + 3] = UInt8(bitPattern: dir)
            strip.runtime.data[o + 4] = speed
        }

        if strip.runtime.call == 0 {
            writeOsc(0, pos: UInt16(SEGLEN / 4), size: UInt8(clamping: SEGLEN / 8), dir: 1, speed: 1)
            writeOsc(1, pos: UInt16(SEGLEN / 4 * 3), size: UInt8(clamping: SEGLEN / 8), dir: 1, speed: 2)
            writeOsc(2, pos: UInt16(SEGLEN / 4 * 2), size: UInt8(clamping: SEGLEN / 8), dir: -1, speed: 1)
        }

        let cycleTime = UInt32(20) + 2 &* UInt32(255 - UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime

        for j in 0..<numOscillators {
            var (pos, _, dir, speed) = readOsc(j)
            let size = UInt8(clamping: SEGLEN / (3 + Int(strip.runtime.intensity / 8)))

            if it != strip.runtime.step {
                let movement = Int16(dir) * Int16(speed)
                pos = UInt16(truncatingIfNeeded: Int32(pos) &+ Int32(movement))
            }

            if dir == -1 && pos > UInt16(SEGLEN << 1) {
                pos = 0
                dir = 1
                speed = strip.runtime.speed > 100 ? random8(2, 4) : random8(1, 3)
            }
            if dir == 1 && pos >= UInt16(SEGLEN - 1) {
                pos = UInt16(SEGLEN - 1)
                dir = -1
                speed = strip.runtime.speed > 100 ? random8(2, 4) : random8(1, 3)
            }

            writeOsc(j, pos: pos, size: size, dir: dir, speed: speed)
        }

        for i in 0..<SEGLEN {
            var color: WLEDColor = BLACK
            for j in 0..<numOscillators {
                let (pos, _, _, _) = readOsc(j)
                let size = UInt8(clamping: SEGLEN / (3 + Int(strip.runtime.intensity / 8)))
                let oscPos = Int(pos)
                if i >= oscPos - Int(size) && i <= oscPos + Int(size) {
                    let segColor = strip.runtime.colors[j]
                    color = (color == BLACK) ? segColor : colorBlend(color, segColor, UInt8(128))
                }
            }
            strip.setPixelColor(i, color)
        }

        strip.runtime.step = it
        return FRAMETIME
    }

    static func effect_juggle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        strip.fadeToBlackBy(UInt8(clamping: 192 - (3 * Int(strip.runtime.intensity) / 4)))

        var dothue: UInt8 = 0
        for i in 0..<8 {
            let bpm88 = UInt16(16 + UInt16(strip.runtime.speed)) * UInt16(i + 7)
            let index = Int(beatsin88(bpm88, 0, UInt16(SEGLEN - 1), now: strip.now))
            let existing = strip.getPixelColor(index)

            let newCol: WLEDColor
            if strip.runtime.palette == 0 {
                newCol = hsvToRgb(dothue, 220, 255)
            } else {
                newCol = strip.colorFromPalette(dothue)
            }
            let combined = rgbw32(
                max(r(existing), r(newCol)),
                max(g(existing), g(newCol)),
                max(b(existing), b(newCol))
            )
            strip.setPixelColor(index, combined)
            dothue &+= 32
        }
        return FRAMETIME
    }

    static func effect_bpm(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let stp = UInt8(truncatingIfNeeded: (strip.now / 20) & 0xFF)
        let beat = beatsin8(strip.runtime.speed, 64, 255, now: strip.now)

        for i in 0..<SEGLEN {
            let palIdx = UInt8(truncatingIfNeeded: (Int(stp) + i * 2) & 0xFF)
            let briVal = Int(beat) - Int(stp) + (i * 10)
            let bri = UInt8(clamping: briVal & 0xFF)
            strip.setPixelColor(i, strip.colorFromPalette(palIdx, bri))
        }
        return FRAMETIME
    }

    static func effect_fillnoise8(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if strip.runtime.call == 0 {
            strip.runtime.step = UInt32(random16())
        }

        for i in 0..<SEGLEN {
            let index = inoise8(i * SEGLEN, Int(strip.runtime.step) + i * SEGLEN)
            strip.setPixelColor(i, strip.colorFromPalette(index))
        }
        strip.runtime.step &+= UInt32(beatsin8(strip.runtime.speed, 1, 6, now: strip.now))
        return FRAMETIME
    }

    static func effect_noise16_1(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let scale: UInt32 = 320
        strip.runtime.step &+= 1 &+ UInt32(strip.runtime.speed >> 4)

        for i in 0..<SEGLEN {
            let shift_x = UInt32(beatsin8(11, 0, 255, now: strip.now))
            let shift_y = strip.runtime.step / 42
            let real_x = (UInt32(i) &+ shift_x) &* scale
            let real_y = (UInt32(i) &+ shift_y) &* scale
            let real_z = strip.runtime.step
            let noise = inoise16(real_x, real_y, real_z) >> 8
            let index = sin8(UInt8(truncatingIfNeeded: (UInt16(noise) &* 3) & 0xFF))
            strip.setPixelColor(i, strip.colorFromPalette(index))
        }
        return FRAMETIME
    }

    static func effect_noise16_2(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let scale: UInt32 = 1000
        strip.runtime.step &+= 1 &+ UInt32(strip.runtime.speed >> 1)

        for i in 0..<SEGLEN {
            let shift_x = strip.runtime.step >> 6
            let real_x = (UInt32(i) &+ shift_x) &* scale
            let noise = inoise16(real_x, 0, 4223) >> 8
            let index = sin8(UInt8(truncatingIfNeeded: (UInt16(noise) &* 3) & 0xFF))
            strip.setPixelColor(i, strip.colorFromPalette(index, UInt8(truncatingIfNeeded: noise & 0xFF)))
        }
        return FRAMETIME
    }

    static func effect_lake(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let sp = Int(strip.runtime.speed) / 10

        let wave1 = Int(Int8(bitPattern: beatsin8(UInt8(clamping: sp + 2), 0, 128, now: strip.now))) - 64
        let wave2 = Int(Int8(bitPattern: beatsin8(UInt8(clamping: sp + 1), 0, 128, now: strip.now))) - 64
        let wave3 = Int(beatsin8(UInt8(clamping: sp + 2), 0, 80, now: strip.now))

        for i in 0..<SEGLEN {
            let index = Int(cos8(UInt8(truncatingIfNeeded: (i * 15 + wave1) & 0xFF))) / 2 +
                         Int(cubicwave8(UInt8(truncatingIfNeeded: (i * 23 + wave2) & 0xFF))) / 2
            let lum: UInt8 = index > wave3 ? UInt8(clamping: index - wave3) : 0
            strip.setPixelColor(i, strip.colorFromPalette(UInt8(clamping: index), lum))
        }
        return FRAMETIME
    }

    static func effect_meteor(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }
        if !strip.runtime.allocateData(SEGLEN) { return effect_solid(strip) }

        let meteorSize = 1 + SEGLEN / 20

        let counter = UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 2) &+ 8)
        let meteorstart = Int((counter &* UInt32(SEGLEN)) >> 16)

        let maxVal = 240

        for i in 0..<SEGLEN {
            if random8() <= 255 &- strip.runtime.intensity {
                strip.runtime.data[i] = scale8(strip.runtime.data[i], 128 &+ random8(127))
                let bri = strip.runtime.data[i]
                let idx: UInt8
                if !strip.runtime.check1 {
                    idx = UInt8(clamping: self.map(i, 0, SEGLEN, 0, maxVal))
                } else {
                    idx = bri
                }
                let col = strip.colorFromPalette(idx, bri)
                strip.setPixelColor(i, col)
            }
        }

        for j in 0..<meteorSize {
            let index = (meteorstart + j) % SEGLEN
            strip.runtime.data[index] = UInt8(clamping: maxVal)
            let idx: UInt8
            if !strip.runtime.check1 {
                idx = UInt8(clamping: self.map(index, 0, SEGLEN, 0, maxVal))
            } else {
                idx = UInt8(clamping: maxVal)
            }
            let col = strip.colorFromPalette(idx, 255)
            strip.setPixelColor(index, col)
        }

        strip.runtime.step &+= UInt32(strip.runtime.speed) &+ 1
        return FRAMETIME
    }

    static func effect_railway(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let dur = Int(256 - Int(strip.runtime.speed)) * 40
        let rampdur = (dur * Int(strip.runtime.intensity)) >> 8

        if strip.runtime.step > UInt32(dur) {
            strip.runtime.step = 0
            strip.runtime.aux0 = strip.runtime.aux0 != 0 ? 0 : 1
        }

        var pos: Int = 255
        if rampdur != 0 {
            let p0 = min(255, Int(strip.runtime.step) * 255 / rampdur)
            if p0 < 255 { pos = p0 }
        }
        if strip.runtime.aux0 != 0 { pos = 255 - pos }

        var i = 0
        while i < SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(UInt8(clamping: 255 - pos)))
            if i < SEGLEN - 1 {
                strip.setPixelColor(i + 1, strip.colorFromPalette(UInt8(clamping: pos)))
            }
            i += 2
        }
        strip.runtime.step &+= UInt32(FRAMETIME)
        return FRAMETIME
    }

    static func effect_twinklecat(_ strip: WLEDEffectStrip) -> UInt16 {
        twinklefoxBase(strip, cat: true)
    }

    static func effect_halloween_eyes(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxWidth = SEGLEN
        let eyeSpace = max(2, SEGLEN >> 5)
        let eyeWidth = eyeSpace / 2
        let eyeLength = 2 * eyeWidth + eyeSpace
        if eyeLength >= maxWidth { return effect_solid(strip) }

        let dataSize = 14
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let stInitOn: UInt8 = 0, stOn: UInt8 = 1, stBlink: UInt8 = 2
        let stInitOff: UInt8 = 3, stOff: UInt8 = 4

        func readU16d(_ off: Int) -> UInt16 {
            UInt16(strip.runtime.data[off]) | (UInt16(strip.runtime.data[off + 1]) << 8)
        }
        func writeU16d(_ off: Int, _ v: UInt16) {
            strip.runtime.data[off] = UInt8(v & 0xFF)
            strip.runtime.data[off + 1] = UInt8(v >> 8)
        }
        func readU32d(_ off: Int) -> UInt32 {
            UInt32(strip.runtime.data[off]) | (UInt32(strip.runtime.data[off + 1]) << 8) |
            (UInt32(strip.runtime.data[off + 2]) << 16) | (UInt32(strip.runtime.data[off + 3]) << 24)
        }
        func writeU32d(_ off: Int, _ v: UInt32) {
            strip.runtime.data[off] = UInt8(v & 0xFF)
            strip.runtime.data[off + 1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[off + 2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[off + 3] = UInt8((v >> 24) & 0xFF)
        }

        var state = strip.runtime.data[0] % 5
        let startPos = readU16d(2)
        var duration = max(1, readU16d(4))
        let startTime = readU32d(6)
        let elapsedTime = strip.now &- startTime

        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }

        switch state {
        case stInitOn:
            writeU16d(2, random16(UInt16(maxWidth - eyeLength - 1)))
            strip.runtime.data[1] = random8()
            let dur = UInt16(128) &+ random16(UInt16(UInt32(strip.runtime.intensity) * 64))
            writeU16d(4, dur)
            duration = max(1, dur)
            state = stOn
            strip.runtime.data[0] = state
            writeU32d(6, strip.now)
            fallthrough

        case stOn:
            let sp = readU16d(2)
            let start2ndEye = Int(sp) + eyeWidth + eyeSpace
            duration = min(duration, UInt16(128) &+ UInt16(UInt32(strip.runtime.intensity) &* 64))
            writeU16d(4, duration)

            let fadeInState = UInt32(elapsedTime) &* (256 * 8) / UInt32(duration)
            let bgColor = strip.runtime.colors[1]
            let eyeColor = strip.colorFromPalette(strip.runtime.data[1])
            var c = eyeColor

            if fadeInState < 256 {
                c = colorBlend(bgColor, eyeColor, UInt8(clamping: Int(fadeInState)))
            } else if elapsedTime > 1024 {
                let remaining = elapsedTime >= UInt32(duration) ? UInt32(0) : UInt32(duration) &- elapsedTime
                if remaining > 1024 {
                    if random8() < 4 {
                        c = bgColor
                        state = stBlink
                        strip.runtime.data[0] = state
                        let blinkEnd = strip.now &+ UInt32(random8(8, 128))
                        writeU32d(10, blinkEnd)
                    }
                }
            }

            if c != bgColor {
                for k in 0..<eyeWidth {
                    strip.setPixelColor(Int(sp) + k, c)
                    if start2ndEye + k < SEGLEN {
                        strip.setPixelColor(start2ndEye + k, c)
                    }
                }
            }

        case stBlink:
            let blinkEnd = readU32d(10)
            if strip.now >= blinkEnd {
                state = stOn
                strip.runtime.data[0] = state
            }

        case stInitOff:
            let eyeOffBase = UInt32(strip.runtime.speed) &* 128
            let dur = UInt16(clamping: eyeOffBase &+ UInt32(random16(UInt16(clamping: eyeOffBase))))
            writeU16d(4, dur)
            duration = max(1, dur)
            state = stOff
            strip.runtime.data[0] = state
            writeU32d(6, strip.now)

        case stOff:
            let eyeOffBase = UInt32(strip.runtime.speed) &* 128
            duration = min(duration, UInt16(clamping: 2 &* eyeOffBase))
            writeU16d(4, duration)

        default:
            state = stInitOn
            strip.runtime.data[0] = state
        }

        if elapsedTime > UInt32(duration) {
            switch state {
            case stInitOn, stOn, stBlink:
                state = stInitOff
            default:
                state = stInitOn
            }
            strip.runtime.data[0] = state
            writeU32d(6, strip.now)
        }

        return FRAMETIME
    }

    static func effect_spots(_ strip: WLEDEffectStrip) -> UInt16 {
        spotsBase(strip, threshold: UInt16(255 - UInt16(strip.runtime.speed)) << 8)
    }

    static func effect_spots_fade(_ strip: WLEDEffectStrip) -> UInt16 {
        let counter = UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 2) &+ 8)
        let t = triwave16(UInt16(truncatingIfNeeded: counter & 0xFFFF))
        let tr = (UInt16(t) >> 1) + (UInt16(t) >> 2)
        return spotsBase(strip, threshold: tr)
    }

    private static func spotsBase(_ strip: WLEDEffectStrip, threshold: UInt16) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }

        let maxZones = SEGLEN >> 2
        let zones = max(1, 1 + (Int(strip.runtime.intensity) * maxZones) >> 8)
        let zoneLen = SEGLEN / zones
        let offset = (SEGLEN - zones * zoneLen) >> 1

        for z in 0..<zones {
            let pos = offset + z * zoneLen
            for i in 0..<zoneLen {
                let wave = triwave16(UInt16(clamping: (i * 0xFFFF) / max(1, zoneLen)))
                if wave > threshold {
                    let index = pos + i
                    if index < SEGLEN {
                        let s = Int(wave - threshold) * 255 / max(1, Int(0xFFFF - Int(threshold)))
                        let palColor = strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0)
                        strip.setPixelColor(index, colorBlend(palColor, strip.runtime.colors[1], UInt8(clamping: 255 - s)))
                    }
                }
            }
        }
        return FRAMETIME
    }

    static func effect_glitter(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        if !strip.runtime.check2 {
            var counter: Int = 0
            if strip.runtime.speed != 0 {
                counter = Int(((UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 3) &+ 1)) & 0xFFFF) >> 8)
            }

            for i in 0..<SEGLEN {
                let colorIndex = (i * 255 / max(1, SEGLEN)) - counter
                strip.setPixelColor(i, strip.colorFromPalette(UInt8(truncatingIfNeeded: colorIndex & 0xFF)))
            }
        }

        let glitterCol = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : ULTRAWHITE
        if strip.runtime.intensity > random8() {
            strip.setPixelColor(Int(random16(UInt16(SEGLEN))), glitterCol)
        }
        return FRAMETIME
    }

    static func effect_starburst(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxFrag = 10
        let starSize = 18 + maxFrag * 4
        let numStars = min(1 + (SEGLEN >> 3), 42)
        let dataSize = starSize * numStars
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let it = strip.now

        func readStarFloat(_ star: Int, _ off: Int) -> Float {
            var v: Float = 0
            let base = star * starSize + off
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeStarFloat(_ star: Int, _ off: Int, _ v: Float) {
            var vv = v
            let base = star * starSize + off
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }
        func readStarU32(_ star: Int, _ off: Int) -> UInt32 {
            let base = star * starSize + off
            return UInt32(strip.runtime.data[base]) | (UInt32(strip.runtime.data[base+1]) << 8) |
                   (UInt32(strip.runtime.data[base+2]) << 16) | (UInt32(strip.runtime.data[base+3]) << 24)
        }
        func writeStarU32(_ star: Int, _ off: Int, _ v: UInt32) {
            let base = star * starSize + off
            strip.runtime.data[base] = UInt8(v & 0xFF)
            strip.runtime.data[base+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[base+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[base+3] = UInt8((v >> 24) & 0xFF)
        }
        func readStarU16(_ star: Int, _ off: Int) -> UInt16 {
            let base = star * starSize + off
            return UInt16(strip.runtime.data[base]) | (UInt16(strip.runtime.data[base+1]) << 8)
        }
        func writeStarU16(_ star: Int, _ off: Int, _ v: UInt16) {
            let base = star * starSize + off
            strip.runtime.data[base] = UInt8(v & 0xFF)
            strip.runtime.data[base+1] = UInt8(v >> 8)
        }

        let oBirth = 0, oLast = 4, oVel = 8, oPos = 12, oColor = 14, oFrags = 18

        let maxSpeed: Float = 375.0
        let particleIgnition: Float = 250.0
        let particleFadeTime: Float = 1500.0

        for j in 0..<numStars {
            let birth = readStarU32(j, oBirth)
            if random8(UInt8(clamping: 144 - Int(strip.runtime.speed >> 1))) == 0 && birth == 0 {
                let startPos = UInt16(random16(UInt16(SEGLEN - 1)))
                let multiplier = Float(random8()) / 255.0

                writeStarU32(j, oColor, colorWheel(random8()))
                writeStarU16(j, oPos, startPos)
                writeStarFloat(j, oVel, maxSpeed * Float(random8()) / 255.0 * multiplier)
                writeStarU32(j, oBirth, it)
                writeStarU32(j, oLast, it)

                let num = Int(random8(3, UInt8(clamping: 6 + Int(strip.runtime.intensity >> 5))))
                for i in 0..<maxFrag {
                    writeStarFloat(j, oFrags + i * 4, i < num ? Float(startPos) : -1.0)
                }
            }
        }

        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }

        for j in 0..<numStars {
            let birth = readStarU32(j, oBirth)
            if birth != 0 {
                let last = readStarU32(j, oLast)
                var vel = readStarFloat(j, oVel)
                let dt = Float(it &- last) / 1000.0

                for i in 0..<maxFrag {
                    var frag = readStarFloat(j, oFrags + i * 4)
                    if frag > 0 {
                        let varI = i >> 1
                        frag += vel * dt * Float(varI) / 3.0
                        writeStarFloat(j, oFrags + i * 4, frag)
                    }
                }

                writeStarU32(j, oLast, it)
                vel -= 3.0 * vel * dt
                writeStarFloat(j, oVel, vel)
            }

            let starColor = readStarU32(j, oColor)
            let birth2 = readStarU32(j, oBirth)
            let pos = readStarU16(j, oPos)

            var fade: Float = 0.0
            var age = Float(it &- birth2)
            var cr = r(starColor), cg = g(starColor), cb = b(starColor)

            if age < particleIgnition {
                let blendAmt = UInt8(clamping: Int(254.5 * (age / particleIgnition)))
                let blended = colorBlend(WHITE, starColor, blendAmt)
                cr = r(blended); cg = g(blended); cb = b(blended)
            } else {
                if age > particleIgnition + particleFadeTime {
                    fade = 1.0
                    writeStarU32(j, oBirth, 0)
                    cr = r(strip.runtime.colors[1])
                    cg = g(strip.runtime.colors[1])
                    cb = b(strip.runtime.colors[1])
                } else {
                    age -= particleIgnition
                    fade = age / particleFadeTime
                    let blendAmt = UInt8(clamping: Int(254.5 * fade))
                    let blended = colorBlend(starColor, strip.runtime.colors[1], blendAmt)
                    cr = r(blended); cg = g(blended); cb = b(blended)
                }
            }

            let particleSize = (1.0 - fade) * 2.0
            let c = rgbw32(cr, cg, cb)

            for index in 0..<(maxFrag * 2) {
                let mirrored = (index & 1) != 0
                let i = index >> 1
                let frag = readStarFloat(j, oFrags + i * 4)
                if frag > 0 {
                    var loc = frag
                    if mirrored { loc -= (loc - Float(pos)) * 2.0 }
                    let startP = max(0, Int(loc - particleSize))
                    var endP = Int(loc + particleSize)
                    if startP == endP { endP += 1 }
                    if endP > SEGLEN { endP = SEGLEN }
                    for p in startP..<endP {
                        strip.setPixelColor(p, c)
                    }
                }
            }
        }
        return FRAMETIME
    }

    static func effect_exploding_fireworks(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }
        let rows = SEGLEN

        let sparkSize = 19
        let numSparks = min(5 + (rows / 2), 213)
        let dataSize = sparkSize * numSparks + 4
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func readSparkFloat(_ spark: Int, _ off: Int) -> Float {
            var v: Float = 0
            let base = spark * sparkSize + off
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeSparkFloat(_ spark: Int, _ off: Int, _ v: Float) {
            var vv = v
            let base = spark * sparkSize + off
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }
        func readSparkU16(_ spark: Int, _ off: Int) -> UInt16 {
            let base = spark * sparkSize + off
            return UInt16(strip.runtime.data[base]) | (UInt16(strip.runtime.data[base+1]) << 8)
        }
        func writeSparkU16(_ spark: Int, _ off: Int, _ v: UInt16) {
            let base = spark * sparkSize + off
            strip.runtime.data[base] = UInt8(v & 0xFF)
            strip.runtime.data[base+1] = UInt8(v >> 8)
        }
        func readSparkU8(_ spark: Int, _ off: Int) -> UInt8 {
            strip.runtime.data[spark * sparkSize + off]
        }
        func writeSparkU8(_ spark: Int, _ off: Int, _ v: UInt8) {
            strip.runtime.data[spark * sparkSize + off] = v
        }

        func readDyingGravity() -> Float {
            var v: Float = 0
            let base = sparkSize * numSparks
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeDyingGravity(_ v: Float) {
            var vv = v
            let base = sparkSize * numSparks
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }

        let oPos = 0, oPosX = 4, oVel = 8, oCol = 16, oColIdx = 18

        if strip.runtime.aux1 != UInt16(clamping: dataSize) {
            writeDyingGravity(0.0)
            strip.runtime.aux0 = 0
            strip.runtime.aux1 = UInt16(clamping: dataSize)
        }

        strip.fadeOut(252)

        let gravity: Float = (-0.0004 - Float(strip.runtime.speed) / 800000.0) * Float(rows)

        if strip.runtime.aux0 < 2 {
            if strip.runtime.aux0 == 0 {
                writeSparkFloat(0, oPos, 0)
                let posXVal: Float = strip.runtime.intensity > random8() ? 1.0 : 0.0
                writeSparkFloat(0, oPosX, posXVal)
                let peakHeight = Int(75 + Int(random8(180)))
                let peakScaled = (peakHeight * (rows - 1)) >> 8
                let flareVel = sqrtf(-2.0 * gravity * Float(peakScaled))
                writeSparkFloat(0, oVel, flareVel)
                writeSparkU16(0, oCol, 255)
                strip.runtime.aux0 = 1
            }

            let flareVel = readSparkFloat(0, oVel)
            if flareVel > 12.0 * gravity {
                let flarePos = readSparkFloat(0, oPos)
                let flarePosX = readSparkFloat(0, oPosX)
                let colVal = readSparkU16(0, oCol)
                let bri = UInt8(clamping: Int(colVal))

                if flarePosX > 0 {
                    let idx = rows - Int(flarePos) - 1
                    if idx >= 0 && idx < SEGLEN { strip.setPixelColor(idx, bri, bri, bri) }
                } else {
                    let idx = Int(flarePos)
                    if idx >= 0 && idx < SEGLEN { strip.setPixelColor(idx, bri, bri, bri) }
                }

                var newPos = flarePos + flareVel
                newPos = max(0, min(newPos, Float(rows - 1)))
                writeSparkFloat(0, oPos, newPos)
                writeSparkFloat(0, oVel, flareVel + gravity)
                writeSparkU16(0, oCol, colVal > 2 ? colVal &- 2 : 0)
            } else {
                strip.runtime.aux0 = 2
            }
        } else if strip.runtime.aux0 < 4 {
            let flarePos = readSparkFloat(0, oPos)
            var nSparks = max(4, Int(flarePos) + Int(random8(4)))
            nSparks = min(nSparks, numSparks)

            if strip.runtime.aux0 == 2 {
                for i in 1..<nSparks {
                    writeSparkFloat(i, oPos, flarePos)
                    var sparkVel = (Float(random16(20001)) / 10000.0) - 0.9
                    if rows < 32 { sparkVel *= 0.5 }
                    writeSparkFloat(i, oVel, sparkVel)
                    writeSparkU16(i, oCol, 345)
                    writeSparkU8(i, oColIdx, random8())
                    let scaledVel = readSparkFloat(i, oVel) * flarePos / Float(rows)
                    writeSparkFloat(i, oVel, scaledVel * (-gravity * 50.0))
                }
                writeDyingGravity(gravity / 2.0)
                strip.runtime.aux0 = 3
            }

            let spark1Col = readSparkU16(1, oCol)
            if spark1Col > 4 {
                var dyingGrav = readDyingGravity()
                for i in 1..<nSparks {
                    var sPos = readSparkFloat(i, oPos)
                    var sVel = readSparkFloat(i, oVel)
                    var sCol = readSparkU16(i, oCol)

                    sPos += sVel
                    sVel += dyingGrav
                    if sCol > 3 { sCol &-= 4 }

                    writeSparkFloat(i, oPos, sPos)
                    writeSparkFloat(i, oVel, sVel)
                    writeSparkU16(i, oCol, sCol)

                    if sPos > 0 && sPos < Float(rows) {
                        let prog = Int(sCol)
                        let flarePosX = readSparkFloat(0, oPosX)
                        let spColor: WLEDColor = strip.runtime.palette != 0 ?
                            colorWheel(readSparkU8(i, oColIdx)) : strip.runtime.colors[0]

                        var c: WLEDColor = BLACK
                        if prog > 300 {
                            c = colorBlend(spColor, WHITE, UInt8(clamping: (prog - 300) * 5))
                        } else if prog > 45 {
                            c = colorBlend(BLACK, spColor, UInt8(clamping: prog - 45))
                            let cooling = UInt8(clamping: (300 - prog) >> 5)
                            let cg2 = qsub8(g(c), cooling)
                            let cb2 = qsub8(b(c), cooling &* 2)
                            c = rgbw32(r(c), cg2, cb2)
                        }

                        if flarePosX > 0 {
                            let idx = rows - Int(sPos) - 1
                            if idx >= 0 && idx < SEGLEN { strip.setPixelColor(idx, c) }
                        } else {
                            let idx = Int(sPos)
                            if idx >= 0 && idx < SEGLEN { strip.setPixelColor(idx, c) }
                        }
                    }
                }
                dyingGrav *= 0.8
                writeDyingGravity(dyingGrav)
            } else {
                strip.runtime.aux0 = UInt16(6 + Int(random8(10)))
            }
        } else {
            strip.runtime.aux0 &-= 1
            if strip.runtime.aux0 < 4 {
                strip.runtime.aux0 = 0
            }
        }
        return FRAMETIME
    }

    static func effect_bouncingballs(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxNumBalls = 16
        let ballSize = 12
        let dataSize = ballSize * maxNumBalls
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func readBallFloat(_ ball: Int, _ off: Int) -> Float {
            var v: Float = 0
            let base = ball * ballSize + off
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeBallFloat(_ ball: Int, _ off: Int, _ v: Float) {
            var vv = v
            let base = ball * ballSize + off
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }
        func readBallU32(_ ball: Int, _ off: Int) -> UInt32 {
            let base = ball * ballSize + off
            return UInt32(strip.runtime.data[base]) | (UInt32(strip.runtime.data[base+1]) << 8) |
                   (UInt32(strip.runtime.data[base+2]) << 16) | (UInt32(strip.runtime.data[base+3]) << 24)
        }
        func writeBallU32(_ ball: Int, _ off: Int, _ v: UInt32) {
            let base = ball * ballSize + off
            strip.runtime.data[base] = UInt8(v & 0xFF)
            strip.runtime.data[base+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[base+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[base+3] = UInt8((v >> 24) & 0xFF)
        }

        let oLastBounce = 0, oImpactVel = 4, oHeight = 8

        let hasCol2 = strip.runtime.colors[2] != 0
        if !strip.runtime.check2 {
            strip.fill(hasCol2 ? BLACK : strip.runtime.colors[1])
        }

        let numBalls = max(1, (Int(strip.runtime.intensity) * (maxNumBalls - 1)) / 255 + 1)
        let gravity: Float = -9.81
        let time = strip.now

        if strip.runtime.call == 0 {
            for i in 0..<maxNumBalls {
                writeBallU32(i, oLastBounce, time)
                writeBallFloat(i, oImpactVel, 0)
                writeBallFloat(i, oHeight, 0)
            }
        }

        for i in 0..<numBalls {
            let lastBounce = readBallU32(i, oLastBounce)
            var impactVel = readBallFloat(i, oImpactVel)

            let timeSinceLastBounce = Float(time &- lastBounce) / (Float(255 - Int(strip.runtime.speed)) / 64.0 + 1.0)
            let timeSec = timeSinceLastBounce / 1000.0
            var height = (0.5 * gravity * timeSec + impactVel) * timeSec

            if height <= 0.0 {
                height = 0.0
                let dampening = 0.9 - Float(i) / Float(numBalls * numBalls)
                impactVel = dampening * impactVel
                writeBallU32(i, oLastBounce, time)

                if impactVel < 0.015 {
                    let impactStart = sqrtf(-2.0 * gravity) * Float(random8(5, 11)) / 10.0
                    impactVel = impactStart
                }
                writeBallFloat(i, oImpactVel, impactVel)
            } else if height > 1.0 {
                writeBallFloat(i, oHeight, height)
                continue
            }

            writeBallFloat(i, oHeight, height)

            var color: WLEDColor = strip.runtime.colors[0]
            if strip.runtime.palette != 0 {
                color = colorWheel(UInt8(clamping: i * (256 / max(numBalls, 8))))
            } else if hasCol2 {
                color = strip.runtime.colors[i % 3]
            }

            let pos = Int(roundf(Float(height) * Float(SEGLEN - 1)))
            if pos >= 0 && pos < SEGLEN {
                strip.setPixelColor(pos, color)
            }
        }
        return FRAMETIME
    }

    static func effect_sinelon(_ strip: WLEDEffectStrip) -> UInt16 {
        sinelonBase(strip, dual: false, rainbow: false)
    }

    static func effect_sinelon_dual(_ strip: WLEDEffectStrip) -> UInt16 {
        sinelonBase(strip, dual: true, rainbow: false)
    }

    static func effect_sinelon_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        sinelonBase(strip, dual: false, rainbow: true)
    }

    private static func sinelonBase(_ strip: WLEDEffectStrip, dual: Bool, rainbow: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        strip.fadeOut(strip.runtime.intensity)

        let pos = Int(beatsin16(UInt16(strip.runtime.speed) / 10, 0, UInt16(SEGLEN - 1), now: strip.now))
        if strip.runtime.call == 0 { strip.runtime.aux0 = UInt16(pos) }

        var color1 = strip.colorFromPalette(i: pos, mapping: true, wrap: false, colorIndex: 0)
        var color2 = strip.runtime.colors[2]
        if rainbow {
            color1 = colorWheel(UInt8(truncatingIfNeeded: (pos & 0x07) * 32))
        }

        strip.setPixelColor(pos, color1)
        if dual {
            if color2 == 0 { color2 = strip.colorFromPalette(i: pos, mapping: true, wrap: false, colorIndex: 0) }
            if rainbow { color2 = color1 }
            strip.setPixelColor(SEGLEN - 1 - pos, color2)
        }

        if strip.runtime.aux0 != UInt16(pos) {
            let prevPos = Int(strip.runtime.aux0)
            if prevPos < pos {
                for i in prevPos..<pos {
                    strip.setPixelColor(i, color1)
                    if dual { strip.setPixelColor(SEGLEN - 1 - i, color2) }
                }
            } else {
                for i in stride(from: prevPos, to: pos, by: -1) {
                    strip.setPixelColor(i, color1)
                    if dual { strip.setPixelColor(SEGLEN - 1 - i, color2) }
                }
            }
            strip.runtime.aux0 = UInt16(pos)
        }
        return FRAMETIME
    }

    static func effect_popcorn(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxPopcorn = 21
        let kernelSize = 9
        let dataSize = kernelSize * maxPopcorn
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func readKernelFloat(_ k: Int, _ off: Int) -> Float {
            var v: Float = 0
            let base = k * kernelSize + off
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeKernelFloat(_ k: Int, _ off: Int, _ v: Float) {
            var vv = v
            let base = k * kernelSize + off
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }

        let oPos = 0, oVel = 4, oColIdx = 8

        let hasCol2 = strip.runtime.colors[2] != 0
        if !strip.runtime.check2 {
            strip.fill(hasCol2 ? BLACK : strip.runtime.colors[1])
        }

        var gravity: Float = -0.0001 - Float(strip.runtime.speed) / 200000.0
        gravity *= Float(SEGLEN)

        let numPopcorn = max(1, Int(strip.runtime.intensity) * maxPopcorn / 255)

        for i in 0..<numPopcorn {
            var pos = readKernelFloat(i, oPos)
            var vel = readKernelFloat(i, oVel)

            if pos >= 0 {
                pos += vel
                vel += gravity
                writeKernelFloat(i, oPos, pos)
                writeKernelFloat(i, oVel, vel)
            } else {
                if random8() < 2 {
                    writeKernelFloat(i, oPos, 0.01)
                    let peakHeight = Int(128 + Int(random8(128)))
                    let peakScaled = (peakHeight * (SEGLEN - 1)) >> 8
                    let popVel = sqrtf(-2.0 * gravity * Float(peakScaled))
                    writeKernelFloat(i, oVel, popVel)

                    if strip.runtime.palette != 0 {
                        strip.runtime.data[i * kernelSize + oColIdx] = random8()
                    } else {
                        var col = random8(0, 3)
                        if strip.runtime.colors[2] == 0 || strip.runtime.colors[Int(col)] == 0 { col = 0 }
                        strip.runtime.data[i * kernelSize + oColIdx] = col
                    }
                    pos = 0.01
                }
            }

            if readKernelFloat(i, oPos) >= 0 {
                let colIdx = strip.runtime.data[i * kernelSize + oColIdx]
                var col: WLEDColor
                if strip.runtime.palette == 0 && colIdx < 3 {
                    col = strip.runtime.colors[Int(colIdx)]
                } else {
                    col = colorWheel(colIdx)
                }
                let ledIndex = Int(readKernelFloat(i, oPos))
                if ledIndex >= 0 && ledIndex < SEGLEN {
                    strip.setPixelColor(ledIndex, col)
                }
            }
        }
        return FRAMETIME
    }

    static func effect_drip(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let maxNumDrops = 4
        let dropSize = 13
        let dataSize = dropSize * maxNumDrops
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        func readDropFloat(_ d: Int, _ off: Int) -> Float {
            var v: Float = 0
            let base = d * dropSize + off
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
            }
            return v
        }
        func writeDropFloat(_ d: Int, _ off: Int, _ v: Float) {
            var vv = v
            let base = d * dropSize + off
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
            }
        }

        let oPos = 0, oVel = 4, oCol = 8, oColIdx = 12

        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }

        let numDrops = 1 + Int(strip.runtime.intensity >> 6)

        var gravity: Float = -0.0005 - Float(strip.runtime.speed) / 50000.0
        gravity *= Float(max(1, SEGLEN - 1))
        let sourcedrop: Float = 12.0

        for j in 0..<numDrops {
            let colIndex = strip.runtime.data[j * dropSize + oColIdx]
            var pos = readDropFloat(j, oPos)
            var vel = readDropFloat(j, oVel)
            var col = readDropFloat(j, oCol)

            if colIndex == 0 {
                writeDropFloat(j, oPos, Float(SEGLEN - 1))
                writeDropFloat(j, oVel, 0)
                writeDropFloat(j, oCol, sourcedrop)
                strip.runtime.data[j * dropSize + oColIdx] = 1
                pos = Float(SEGLEN - 1)
                vel = 0
                col = sourcedrop
            }

            strip.setPixelColor(SEGLEN - 1, colorBlend(BLACK, strip.runtime.colors[0], UInt8(clamping: Int(sourcedrop))))

            let state = strip.runtime.data[j * dropSize + oColIdx]

            if state == 1 {
                let briClamped = min(col, 255)
                strip.setPixelColor(Int(pos), colorBlend(BLACK, strip.runtime.colors[0], UInt8(clamping: Int(briClamped))))
                col += Float(self.map(Int(strip.runtime.speed), 0, 255, 1, 6))
                writeDropFloat(j, oCol, col)

                if random8() < UInt8(clamping: Int(col) / 10) {
                    strip.runtime.data[j * dropSize + oColIdx] = 2
                    writeDropFloat(j, oCol, 255)
                }
            }

            if strip.runtime.data[j * dropSize + oColIdx] > 1 {
                if pos > 0 {
                    pos += vel
                    if pos < 0 { pos = 0 }
                    vel += gravity
                    writeDropFloat(j, oPos, pos)
                    writeDropFloat(j, oVel, vel)

                    let currentState = Int(strip.runtime.data[j * dropSize + oColIdx])
                    let spread = max(1, 7 - currentState)
                    let colF = readDropFloat(j, oCol)
                    for k in 1..<spread {
                        let pix = min(Int(pos) + k, SEGLEN - 1)
                        let bri = UInt8(clamping: Int(colF) / k)
                        strip.setPixelColor(pix, colorBlend(BLACK, strip.runtime.colors[0], bri))
                    }

                    if currentState > 2 {
                        strip.setPixelColor(0, colorBlend(strip.runtime.colors[0], BLACK, UInt8(clamping: Int(colF))))
                    }
                } else {
                    let currentState = Int(strip.runtime.data[j * dropSize + oColIdx])
                    if currentState > 2 {
                        strip.runtime.data[j * dropSize + oColIdx] = 0
                        writeDropFloat(j, oCol, sourcedrop)
                    } else {
                        if currentState == 2 {
                            vel = -vel / 4.0
                            pos += vel
                            writeDropFloat(j, oVel, vel)
                            writeDropFloat(j, oPos, pos)
                        }
                        writeDropFloat(j, oCol, sourcedrop * 2.0)
                        strip.runtime.data[j * dropSize + oColIdx] = 5
                    }
                }
            }
        }
        return FRAMETIME
    }
}
