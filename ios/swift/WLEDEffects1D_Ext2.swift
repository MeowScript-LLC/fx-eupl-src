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

    static let effectsBatch2: [Int: WLEDEffectFunc] = [
        28: effect_chase_color,
        29: effect_chase_random,
        30: effect_chase_rainbow,
        31: effect_chase_flash,
        32: effect_chase_flash_random,
        34: effect_colorful,
        35: effect_traffic_light,
        36: effect_color_sweep_random,
        37: effect_running_color,
        38: effect_aurora,
        39: effect_running_random,
        40: effect_larson_scanner,
        41: effect_comet,
        43: effect_rain,
        46: effect_gradient,
        47: effect_loading,
        48: effect_rollingballs,
        50: effect_two_dots,
    ]

    private static func chase(
        _ strip: WLEDEffectStrip,
        color1In: WLEDColor,
        color2: WLEDColor,
        color3: WLEDColor,
        doPalette: Bool,
        chaseRandom: Bool = false
    ) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let counter = UInt16(truncatingIfNeeded: strip.now &* (UInt32(strip.runtime.speed >> 2) &+ 1))
        let a = Int((UInt32(counter) &* UInt32(SEGLEN)) >> 16)

        var color1 = color1In

        if chaseRandom {
            if a < Int(strip.runtime.step & 0xFFFF) {
                strip.runtime.aux1 = strip.runtime.aux0
                strip.runtime.aux0 = UInt16(getRandomWheelIndex(UInt8(strip.runtime.aux0 & 0xFF)))
            }
            color1 = colorWheel(UInt8(strip.runtime.aux0 & 0xFF))
        }
        strip.runtime.step = UInt32(a)

        let size = 1 + ((Int(strip.runtime.intensity) * SEGLEN) >> 10)

        var b = a + size
        if b > SEGLEN { b -= SEGLEN }
        var c = b + size
        if c > SEGLEN { c -= SEGLEN }

        if doPalette {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1))
            }
        } else {
            strip.fill(color1)
        }

        if chaseRandom {
            let oldColor = colorWheel(UInt8(strip.runtime.aux1 & 0xFF))
            for i in a..<SEGLEN {
                strip.setPixelColor(i, oldColor)
            }
        }

        if a < b {
            for i in a..<b {
                strip.setPixelColor(i, color2)
            }
        } else {
            for i in a..<SEGLEN {
                strip.setPixelColor(i, color2)
            }
            for i in 0..<b {
                strip.setPixelColor(i, color2)
            }
        }

        if b < c {
            for i in b..<c {
                strip.setPixelColor(i, color3)
            }
        } else {
            for i in b..<SEGLEN {
                strip.setPixelColor(i, color3)
            }
            for i in 0..<c {
                strip.setPixelColor(i, color3)
            }
        }

        return FRAMETIME
    }

    static func effect_chase_color(_ strip: WLEDEffectStrip) -> UInt16 {
        let col2 = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : strip.runtime.colors[0]
        return chase(strip,
                     color1In: strip.runtime.colors[1],
                     color2: col2,
                     color3: strip.runtime.colors[0],
                     doPalette: true)
    }

    static func effect_chase_random(_ strip: WLEDEffectStrip) -> UInt16 {
        let col2 = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : strip.runtime.colors[0]
        return chase(strip,
                     color1In: strip.runtime.colors[1],
                     color2: col2,
                     color3: strip.runtime.colors[0],
                     doPalette: false,
                     chaseRandom: true)
    }

    static func effect_chase_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        var colorSep = 256 / SEGLEN
        if colorSep == 0 { colorSep = 1 }
        let colorIndex = strip.runtime.call & 0xFF
        let stepVal = Int(strip.runtime.step & 0xFFFF)
        let color = colorWheel(UInt8(truncatingIfNeeded: (stepVal * colorSep + Int(colorIndex)) & 0xFF))

        return chase(strip,
                     color1In: color,
                     color2: strip.runtime.colors[0],
                     color3: strip.runtime.colors[1],
                     doPalette: false)
    }

    static func effect_chase_flash(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let FLASH_COUNT: UInt16 = 4
        let now = strip.now
        var advance = true
        let flashStep = strip.runtime.aux1 % ((FLASH_COUNT * 2) + 1)

        if now < UInt32(strip.runtime.step) {
            advance = false
        } else {
            strip.runtime.aux1 &+= 1
        }

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
        }

        let index = Int(strip.runtime.aux0)
        let n = index
        let m = (index + 1) % SEGLEN

        var delay = UInt32(10) + ((UInt32(30) * UInt32(255 - UInt32(strip.runtime.speed))) / UInt32(SEGLEN))

        if flashStep < (FLASH_COUNT * 2) {
            if flashStep % 2 == 0 {
                strip.setPixelColor(n, strip.runtime.colors[1])
                strip.setPixelColor(m, strip.runtime.colors[1])
                delay = 20
            } else {
                delay = 30
            }
        } else if advance {
            strip.runtime.aux0 = UInt16(m)
        }

        if advance {
            strip.runtime.step = now &+ delay
        }

        return FRAMETIME
    }

    static func effect_chase_flash_random(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let FLASH_COUNT: UInt32 = 4
        let now = strip.now
        var advance = true

        if now < UInt32(strip.runtime.step) {
            if strip.runtime.call > 0 {
                strip.runtime.call &-= 1
            }
            advance = false
        }

        let flashStep = strip.runtime.call % ((FLASH_COUNT * 2) + 1)

        for i in 0..<Int(strip.runtime.aux1) {
            strip.setPixelColor(i, colorWheel(UInt8(strip.runtime.aux0 & 0xFF)))
        }

        var delay = UInt32(1) + ((UInt32(10) * UInt32(255 - UInt32(strip.runtime.speed))) / UInt32(SEGLEN))

        if flashStep < (FLASH_COUNT * 2) {
            let n = Int(strip.runtime.aux1)
            let m = (Int(strip.runtime.aux1) + 1) % SEGLEN
            if flashStep % 2 == 0 {
                strip.setPixelColor(n, strip.runtime.colors[0])
                strip.setPixelColor(m, strip.runtime.colors[0])
                delay = 20
            } else {
                strip.setPixelColor(n, colorWheel(UInt8(strip.runtime.aux0 & 0xFF)))
                strip.setPixelColor(m, strip.runtime.colors[1])
                delay = 30
            }
        } else if advance {
            strip.runtime.aux1 = UInt16((Int(strip.runtime.aux1) + 1) % SEGLEN)

            if strip.runtime.aux1 == 0 {
                strip.runtime.aux0 = UInt16(getRandomWheelIndex(UInt8(strip.runtime.aux0 & 0xFF)))
            }
        }

        if advance {
            strip.runtime.step = now &+ delay
        }

        return FRAMETIME
    }

    static func effect_colorful(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        var numColors: Int = 4
        var cols = [WLEDColor](repeating: 0, count: 9)
        cols[0] = 0x00FF0000; cols[1] = 0x00EEBB00; cols[2] = 0x0000EE00; cols[3] = 0x000077CC

        if strip.runtime.intensity > 160 || strip.runtime.palette != 0 {
            if strip.runtime.palette == 0 {
                numColors = 3
                for i in 0..<3 { cols[i] = strip.runtime.colors[i] }
            } else {
                var fac: Int = 80
                if strip.runtime.palette == 52 { numColors = 5; fac = 61 }
                for i in 0..<numColors {
                    cols[i] = strip.colorFromPalette(UInt8(clamping: i * fac))
                }
            }
        } else if strip.runtime.intensity < 80 {
            cols[0] = 0x00FF8040
            cols[1] = 0x00E5D241
            cols[2] = 0x0077FF77
            cols[3] = 0x0077F0F0
        }

        for i in numColors..<(numColors * 2 - 1) {
            cols[i] = cols[i - numColors]
        }

        let cycleTime = UInt32(50) + (UInt32(8) * UInt32(255 - UInt32(strip.runtime.speed)))
        let it = strip.now / cycleTime
        if it != strip.runtime.step {
            if strip.runtime.speed > 0 { strip.runtime.aux0 &+= 1 }
            if Int(strip.runtime.aux0) >= numColors { strip.runtime.aux0 = 0 }
            strip.runtime.step = it
        }

        var i = 0
        while i < SEGLEN {
            for j in 0..<numColors {
                strip.setPixelColor(i + j, cols[Int(strip.runtime.aux0) + j])
            }
            i += numColors
        }

        return FRAMETIME
    }

    static func effect_traffic_light(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1))
        }

        var mdelay: UInt32 = 500
        var i = 0
        while i < SEGLEN - 2 {
            switch strip.runtime.aux0 {
            case 0:
                strip.setPixelColor(i, 0x00FF0000)
                mdelay = 150 + (100 * UInt32(255 - UInt32(strip.runtime.speed)))
            case 1:
                strip.setPixelColor(i, 0x00FF0000)
                mdelay = 150 + (20 * UInt32(255 - UInt32(strip.runtime.speed)))
                strip.setPixelColor(i + 1, 0x00EECC00)
            case 2:
                strip.setPixelColor(i + 2, 0x0000FF00)
                mdelay = 150 + (100 * UInt32(255 - UInt32(strip.runtime.speed)))
            case 3:
                strip.setPixelColor(i + 1, 0x00EECC00)
                mdelay = 150 + (20 * UInt32(255 - UInt32(strip.runtime.speed)))
            default:
                break
            }
            i += 3
        }

        if strip.now &- strip.runtime.step > mdelay {
            strip.runtime.aux0 &+= 1
            if strip.runtime.aux0 == 1 && strip.runtime.intensity > 140 {
                strip.runtime.aux0 = 2
            }
            if strip.runtime.aux0 > 3 { strip.runtime.aux0 = 0 }
            strip.runtime.step = strip.now
        }

        return FRAMETIME
    }

    static func effect_color_sweep_random(_ strip: WLEDEffectStrip) -> UInt16 {
        colorWipeHelper(strip, rev: true, useRandomColors: true)
    }

    private static func colorWipeHelper(_ strip: WLEDEffectStrip, rev: Bool, useRandomColors: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let cycleTime = UInt32(750) + UInt32(255 - UInt32(strip.runtime.speed)) * 150
        let perc = strip.now % cycleTime
        var prog = Int((UInt32(perc) * 65535) / cycleTime)
        let back = prog > 32767
        if back {
            prog -= 32767
            if strip.runtime.step == 0 { strip.runtime.step = 1 }
        } else {
            if strip.runtime.step == 2 { strip.runtime.step = 3 }
        }

        if useRandomColors {
            if strip.runtime.call == 0 {
                strip.runtime.aux0 = UInt16(random8())
                strip.runtime.step = 3
            }
            if strip.runtime.step == 1 {
                strip.runtime.aux1 = UInt16(getRandomWheelIndex(UInt8(strip.runtime.aux0 & 0xFF)))
                strip.runtime.step = 2
            }
            if strip.runtime.step == 3 {
                strip.runtime.aux0 = UInt16(getRandomWheelIndex(UInt8(strip.runtime.aux1 & 0xFF)))
                strip.runtime.step = 0
            }
        }

        let ledIndex = (prog * SEGLEN) >> 15
        var rem = (prog * SEGLEN) * 2
        rem /= (Int(strip.runtime.intensity) + 1)
        if rem > 255 { rem = 255 }

        let col1 = useRandomColors ? colorWheel(UInt8(strip.runtime.aux1 & 0xFF)) : strip.runtime.colors[1]
        for i in 0..<SEGLEN {
            let index = (rev && back) ? SEGLEN - 1 - i : i
            let col0 = useRandomColors ? colorWheel(UInt8(strip.runtime.aux0 & 0xFF)) : strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0)

            if i < ledIndex {
                strip.setPixelColor(index, back ? col1 : col0)
            } else {
                strip.setPixelColor(index, back ? col0 : col1)
                if i == ledIndex {
                    strip.setPixelColor(index, colorBlend(back ? col0 : col1, back ? col1 : col0, UInt16(rem)))
                }
            }
        }
        return FRAMETIME
    }

    static func effect_running_color(_ strip: WLEDEffectStrip) -> UInt16 {
        runningHelper(strip, color1: strip.runtime.colors[0], color2: strip.runtime.colors[1], theatre: false)
    }

    private static func runningHelper(
        _ strip: WLEDEffectStrip,
        color1: WLEDColor,
        color2: WLEDColor,
        theatre: Bool
    ) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let width = (theatre ? 3 : 1) + Int(strip.runtime.intensity >> 4)
        let cycleTime = UInt32(50) + UInt32(255 - UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime
        let usePalette = (color1 == strip.runtime.colors[0])

        for i in 0..<SEGLEN {
            var col = color2
            var c1 = color1
            if usePalette {
                c1 = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            }
            if theatre {
                if (i % width) == Int(strip.runtime.aux0) { col = c1 }
            } else {
                let pos = i % (width << 1)
                if (pos < Int(strip.runtime.aux0) - width) ||
                   ((pos >= Int(strip.runtime.aux0)) && (pos < Int(strip.runtime.aux0) + width)) {
                    col = c1
                }
            }
            strip.setPixelColor(i, col)
        }

        if it != strip.runtime.step {
            strip.runtime.aux0 = UInt16((Int(strip.runtime.aux0) + 1) % (theatre ? width : (width << 1)))
            strip.runtime.step = it
        }

        return FRAMETIME
    }

    private static let AW_SHIFT: Int = 16
    private static let AW_SCALE: UInt32 = 1 << 16
    private static let W_MAX_COUNT: Int = 20
    private static let W_MAX_SPEED: Int = 6
    private static let W_WIDTH_FACTOR: Int = 6

    private static let AURORA_WAVE_SIZE = 28

    private static func auroraReadI32(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int) -> Int32 {
        let o = base + offset
        return Int32(bitPattern:
            UInt32(strip.runtime.data[o]) |
            (UInt32(strip.runtime.data[o+1]) << 8) |
            (UInt32(strip.runtime.data[o+2]) << 16) |
            (UInt32(strip.runtime.data[o+3]) << 24)
        )
    }

    private static func auroraWriteI32(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int, _ v: Int32) {
        let o = base + offset
        let u = UInt32(bitPattern: v)
        strip.runtime.data[o]   = UInt8(u & 0xFF)
        strip.runtime.data[o+1] = UInt8((u >> 8) & 0xFF)
        strip.runtime.data[o+2] = UInt8((u >> 16) & 0xFF)
        strip.runtime.data[o+3] = UInt8((u >> 24) & 0xFF)
    }

    private static func auroraReadU32(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int) -> UInt32 {
        let o = base + offset
        return UInt32(strip.runtime.data[o]) |
               (UInt32(strip.runtime.data[o+1]) << 8) |
               (UInt32(strip.runtime.data[o+2]) << 16) |
               (UInt32(strip.runtime.data[o+3]) << 24)
    }

    private static func auroraWriteU32(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int, _ v: UInt32) {
        let o = base + offset
        strip.runtime.data[o]   = UInt8(v & 0xFF)
        strip.runtime.data[o+1] = UInt8((v >> 8) & 0xFF)
        strip.runtime.data[o+2] = UInt8((v >> 16) & 0xFF)
        strip.runtime.data[o+3] = UInt8((v >> 24) & 0xFF)
    }

    private static func auroraReadU16(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int) -> UInt16 {
        let o = base + offset
        return UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o+1]) << 8)
    }

    private static func auroraWriteU16(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int, _ v: UInt16) {
        let o = base + offset
        strip.runtime.data[o]   = UInt8(v & 0xFF)
        strip.runtime.data[o+1] = UInt8(v >> 8)
    }

    private static func auroraReadI16(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int) -> Int16 {
        Int16(bitPattern: auroraReadU16(strip, base, offset))
    }

    private static func auroraWriteI16(_ strip: WLEDEffectStrip, _ base: Int, _ offset: Int, _ v: Int16) {
        auroraWriteU16(strip, base, offset, UInt16(bitPattern: v))
    }

    private static func auroraInitWave(_ strip: WLEDEffectStrip, _ base: Int, segLen: Int, color: WLEDColor) {
        let ttl = UInt16(random16(500, 1501))
        auroraWriteU16(strip, base, 8, ttl)
        auroraWriteU16(strip, base, 10, 0)
        let minW = max(1, segLen / 20)
        let maxW = max(minW + 1, segLen / W_WIDTH_FACTOR)
        let w = UInt16(random16(UInt16(minW), UInt16(maxW))) + 1
        auroraWriteU16(strip, base, 12, w)
        let ba = UInt16(random8(60, 100)) * UInt16(AW_SCALE / 100)
        auroraWriteU16(strip, base, 14, ba)
        let sf = UInt16((UInt32(random8(10, 31)) * UInt32(W_MAX_SPEED)) << AW_SHIFT / (100 * 255))
        auroraWriteU16(strip, base, 16, sf)
        let center = Int32((UInt32(random8(101)) << AW_SHIFT) / 100) * Int32(segLen)
        auroraWriteI32(strip, base, 0, center)
        auroraWriteU32(strip, base, 4, 0)
        auroraWriteI16(strip, base, 18, 0)
        auroraWriteI16(strip, base, 20, 0)
        strip.runtime.data[base + 22] = random8() & 0x01
        strip.runtime.data[base + 23] = 1
        strip.runtime.data[base + 24] = r(color)
        strip.runtime.data[base + 25] = g(color)
        strip.runtime.data[base + 26] = b(color)
        strip.runtime.data[base + 27] = WLEDEffects1D.w(color)
    }

    private static func auroraUpdateCached(_ strip: WLEDEffectStrip, _ base: Int) {
        let ttl = UInt32(auroraReadU16(strip, base, 8))
        let age = UInt32(auroraReadU16(strip, base, 10))
        let halfTtl = ttl >> 1
        guard halfTtl > 0 else {
            auroraWriteU32(strip, base, 4, 0)
            return
        }

        var ageFactor: UInt32
        if age < halfTtl {
            ageFactor = (age << UInt32(AW_SHIFT)) / halfTtl
        } else {
            ageFactor = ((ttl - age) << UInt32(AW_SHIFT)) / halfTtl
        }
        if ageFactor >= AW_SCALE { ageFactor = AW_SCALE - 1 }
        auroraWriteU32(strip, base, 4, ageFactor)

        let center = auroraReadI32(strip, base, 0)
        let centerLed = Int16(center >> Int32(AW_SHIFT))
        let width = Int16(auroraReadU16(strip, base, 12))
        auroraWriteI16(strip, base, 18, centerLed - width)
        auroraWriteI16(strip, base, 20, centerLed + width)
    }

    private static func auroraUpdateWave(_ strip: WLEDEffectStrip, _ base: Int, segLen: Int, speed: UInt8) {
        let speedFactor = Int32(auroraReadU16(strip, base, 16))
        let goingLeft = strip.runtime.data[base + 22] != 0
        var center = auroraReadI32(strip, base, 0)
        let step = speedFactor * Int32(speed)
        center += goingLeft ? -step : step
        auroraWriteI32(strip, base, 0, center)

        var age = auroraReadU16(strip, base, 10)
        age &+= 1
        auroraWriteU16(strip, base, 10, age)

        let ttl = auroraReadU16(strip, base, 8)
        if age > ttl {
            strip.runtime.data[base + 23] = 0
        } else {
            let width = UInt32(auroraReadU16(strip, base, 12))
            let widthScaled = Int32(width << UInt32(AW_SHIFT))
            let segLenScaled = Int32(UInt32(segLen) << UInt32(AW_SHIFT))

            if goingLeft {
                if center < -widthScaled {
                    strip.runtime.data[base + 23] = 0
                }
            } else {
                if center > segLenScaled + widthScaled {
                    strip.runtime.data[base + 23] = 0
                }
            }
        }
    }

    private static func auroraGetColor(_ strip: WLEDEffectStrip, _ base: Int, ledIndex: Int) -> WLEDColor {
        let waveStart = Int(auroraReadI16(strip, base, 18))
        let waveEnd = Int(auroraReadI16(strip, base, 20))

        if ledIndex < waveStart || ledIndex > waveEnd { return 0 }

        let center = auroraReadI32(strip, base, 0)
        let ledScaled = Int32(ledIndex) << Int32(AW_SHIFT)
        var offset = ledScaled - center
        if offset < 0 { offset = -offset }

        let width = UInt32(auroraReadU16(strip, base, 12))
        guard width > 0 else { return 0 }
        let offsetFactor = UInt32(offset) / width
        if offsetFactor > AW_SCALE { return 0 }

        let ageFactor = auroraReadU32(strip, base, 4)
        let basealpha = UInt32(auroraReadU16(strip, base, 14))

        var brightnessFactor = AW_SCALE - offsetFactor
        brightnessFactor = (brightnessFactor * ageFactor) >> UInt32(AW_SHIFT)
        brightnessFactor = (brightnessFactor * basealpha) >> UInt32(AW_SHIFT)

        let br = UInt8(clamping: (UInt32(strip.runtime.data[base + 24]) * brightnessFactor) >> UInt32(AW_SHIFT))
        let bg = UInt8(clamping: (UInt32(strip.runtime.data[base + 25]) * brightnessFactor) >> UInt32(AW_SHIFT))
        let bb = UInt8(clamping: (UInt32(strip.runtime.data[base + 26]) * brightnessFactor) >> UInt32(AW_SHIFT))
        let bw = UInt8(clamping: (UInt32(strip.runtime.data[base + 27]) * brightnessFactor) >> UInt32(AW_SHIFT))

        return rgbw32(br, bg, bb, bw)
    }

    private static func colorAdd(_ c1: WLEDColor, _ c2: WLEDColor) -> WLEDColor {
        let rr = min(255, UInt32(r(c1)) + UInt32(r(c2)))
        let gg = min(255, UInt32(g(c1)) + UInt32(g(c2)))
        let bb = min(255, UInt32(b(c1)) + UInt32(b(c2)))
        let ww = min(255, UInt32(w(c1)) + UInt32(w(c2)))
        return rgbw32(UInt8(rr), UInt8(gg), UInt8(bb), UInt8(ww))
    }

    static func effect_aurora(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let waveCount = max(2, min(W_MAX_COUNT, self.map(Int(strip.runtime.intensity), 0, 255, 2, W_MAX_COUNT)))
        strip.runtime.aux1 = UInt16(waveCount)

        let dataSize = AURORA_WAVE_SIZE * waveCount
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        for i in 0..<waveCount {
            let base = i * AURORA_WAVE_SIZE
            auroraUpdateWave(strip, base, segLen: SEGLEN, speed: strip.runtime.speed)

            if strip.runtime.data[base + 23] == 0 {
                let palColor = strip.colorFromPalette(random8(), random8(0, 3))
                auroraInitWave(strip, base, segLen: SEGLEN, color: palColor)
            }

            auroraUpdateCached(strip, base)
        }

        var backlight: UInt8 = 0
        if strip.runtime.colors[0] != 0 { backlight &+= 1 }
        if strip.runtime.colors[1] != 0 { backlight &+= 1 }
        if strip.runtime.colors[2] != 0 { backlight &+= 1 }

        for i in 0..<SEGLEN {
            var mixed = rgbw32(backlight, backlight, backlight, 0)

            for j in 0..<waveCount {
                let base = j * AURORA_WAVE_SIZE
                let waveColor = auroraGetColor(strip, base, ledIndex: i)
                mixed = colorAdd(mixed, waveColor)
            }

            strip.setPixelColor(i, mixed)
        }

        return FRAMETIME
    }

    static func effect_running_random(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let cycleTime = UInt32(25) + (UInt32(3) * UInt32(255 - UInt32(strip.runtime.speed)))
        let it = strip.now / cycleTime

        if strip.runtime.call == 0 {
            strip.runtime.aux0 = random16()
        }

        let zoneSize = Int((255 - Int(strip.runtime.intensity)) >> 4) + 1
        var prng16 = strip.runtime.aux0

        let z_init = Int(it) % zoneSize
        let nzone = (z_init == 0 && it != UInt32(strip.runtime.aux1))
        var z = z_init

        for i in stride(from: SEGLEN - 1, through: 0, by: -1) {
            if nzone || z >= zoneSize {
                let lastrand = prng16 >> 8
                var diff: Int16 = 0
                while abs(Int(diff)) < 42 {
                    prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 13849)
                    diff = Int16(Int(prng16 >> 8) - Int(lastrand))
                }
                if nzone && z == z_init {
                    strip.runtime.aux0 = prng16
                }
                z = 0
            }
            strip.setPixelColor(i, colorWheel(UInt8(prng16 >> 8)))
            z += 1
        }

        strip.runtime.aux1 = UInt16(truncatingIfNeeded: it & 0xFFFF)
        return FRAMETIME
    }

    static func effect_larson_scanner(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let speed = UInt32(FRAMETIME) * UInt32(self.map(Int(strip.runtime.speed), 0, 255, 96, 2))
        let pixels = Int(UInt32(SEGLEN) / speed)

        strip.fadeOut(255 &- strip.runtime.intensity)

        if strip.runtime.step > UInt32(strip.now) { return FRAMETIME }

        var index = Int(strip.runtime.aux1) + pixels

        if pixels == 0 {
            let frames = Int(speed / UInt32(SEGLEN))
            if strip.runtime.step < UInt32(frames) {
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
                strip.runtime.step = UInt32(strip.now) &+ UInt32(strip.runtime.custom1) * 25
            } else {
                strip.runtime.step = 0
            }
        } else {
            for i in Int(strip.runtime.aux1)..<index {
                let j = (strip.runtime.aux0 != 0) ? i : SEGLEN - 1 - i
                let c = strip.colorFromPalette(i: j, mapping: true, wrap: true, colorIndex: 0)
                strip.setPixelColor(j, c)
                if strip.runtime.check1 {
                    let mirrorC = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : c
                    strip.setPixelColor(SEGLEN - 1 - j, mirrorC)
                }
            }
            strip.runtime.aux1 = UInt16(index)
        }

        return FRAMETIME
    }

    static func effect_comet(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let counter = (strip.now &* (UInt32(strip.runtime.speed >> 2) &+ 1)) & 0xFFFF
        let index = Int((UInt32(counter) &* UInt32(SEGLEN)) >> 16)

        if strip.runtime.call == 0 { strip.runtime.aux0 = UInt16(index) }

        strip.fadeOut(strip.runtime.intensity)

        strip.setPixelColor(index, strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0))

        if index > Int(strip.runtime.aux0) {
            for i in Int(strip.runtime.aux0)..<index {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
            }
        } else if index < Int(strip.runtime.aux0) && index < 10 {
            for i in 0..<index {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
            }
        }

        strip.runtime.aux0 = UInt16(index + 1)

        return FRAMETIME
    }

    static func effect_rain(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let speedFormulaL = UInt32(5) + (UInt32(50) * UInt32(255 - UInt32(strip.runtime.speed))) / UInt32(SEGLEN)

        strip.runtime.step &+= UInt32(FRAMETIME)
        if strip.runtime.call > 0 && strip.runtime.step > speedFormulaL {
            strip.runtime.step = 1
            let ctemp = strip.getPixelColor(0)
            for i in 0..<(SEGLEN - 1) {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
            strip.setPixelColor(SEGLEN - 1, ctemp)
            strip.runtime.aux0 &+= 1
            strip.runtime.aux1 &+= 1
            if strip.runtime.aux0 == 0 { strip.runtime.aux0 = UInt16.max }
            if strip.runtime.aux1 == 0 { strip.runtime.aux0 = UInt16.max }
            if strip.runtime.aux0 >= UInt16(SEGLEN) { strip.runtime.aux0 = 0 }
            if strip.runtime.aux1 >= UInt16(SEGLEN) { strip.runtime.aux1 = 0 }
        }

        if strip.runtime.call == 0 {
            strip.runtime.aux0 = UInt16.max
            strip.runtime.aux1 = UInt16.max
        }
        strip.fadeOut(128)

        let valid1 = strip.runtime.aux0 < UInt16(SEGLEN)
        let valid2 = strip.runtime.aux1 < UInt16(SEGLEN)
        var sv1: WLEDColor = 0, sv2: WLEDColor = 0
        if valid1 { sv1 = strip.getPixelColor(Int(strip.runtime.aux0)) }
        if valid2 { sv2 = strip.getPixelColor(Int(strip.runtime.aux1)) }

        strip.blur(16)

        if valid1 { strip.setPixelColor(Int(strip.runtime.aux0), sv1) }
        if valid2 { strip.setPixelColor(Int(strip.runtime.aux1), sv2) }

        for _ in 0..<max(1, SEGLEN / 20) {
            if random8(UInt8(clamping: 129 - Int(strip.runtime.intensity >> 1))) == 0 {
                let idx = Int(random16(UInt16(SEGLEN)))
                let col = strip.colorFromPalette(random8())
                strip.setPixelColor(idx, col)
                strip.runtime.aux1 = strip.runtime.aux0
                strip.runtime.aux0 = UInt16(idx)
            }
        }

        return FRAMETIME
    }

    static func effect_gradient(_ strip: WLEDEffectStrip) -> UInt16 {
        gradientBase(strip, loading: false)
    }

    static func effect_loading(_ strip: WLEDEffectStrip) -> UInt16 {
        gradientBase(strip, loading: true)
    }

    private static func gradientBase(_ strip: WLEDEffectStrip, loading: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let counter = UInt16(truncatingIfNeeded: strip.now &* (UInt32(strip.runtime.speed >> 2) &+ 1))
        var pp = Int((UInt32(counter) &* UInt32(SEGLEN)) >> 16)
        if strip.runtime.call == 0 { pp = 0 }

        let brd = loading ? 1 + Int(strip.runtime.intensity) / 2 : 1 + Int(strip.runtime.intensity) / 4
        let p1 = pp - SEGLEN
        let p2 = pp + SEGLEN

        for i in 0..<SEGLEN {
            var val: Int
            if loading {
                val = abs((i > pp ? p2 : pp) - i)
            } else {
                val = min(abs(pp - i), min(abs(p1 - i), abs(p2 - i)))
            }
            val = brd > val ? (val * 255) / brd : 255
            let palColor = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1)
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[0], palColor, UInt8(clamping: val)))
        }

        return FRAMETIME
    }

    private static let RBALL_SIZE = 16
    private static let RBALL_MAX = 16

    private static func rballReadFloat(_ strip: WLEDEffectStrip, _ base: Int) -> Float {
        var v: Float = 0
        withUnsafeMutableBytes(of: &v) { ptr in
            for i in 0..<4 { ptr[i] = strip.runtime.data[base + i] }
        }
        return v
    }

    private static func rballWriteFloat(_ strip: WLEDEffectStrip, _ base: Int, _ v: Float) {
        var vv = v
        withUnsafeBytes(of: &vv) { ptr in
            for i in 0..<4 { strip.runtime.data[base + i] = ptr[i] }
        }
    }

    private static func rballReadU32(_ strip: WLEDEffectStrip, _ base: Int) -> UInt32 {
        UInt32(strip.runtime.data[base]) |
        (UInt32(strip.runtime.data[base+1]) << 8) |
        (UInt32(strip.runtime.data[base+2]) << 16) |
        (UInt32(strip.runtime.data[base+3]) << 24)
    }

    private static func rballWriteU32(_ strip: WLEDEffectStrip, _ base: Int, _ v: UInt32) {
        strip.runtime.data[base]   = UInt8(v & 0xFF)
        strip.runtime.data[base+1] = UInt8((v >> 8) & 0xFF)
        strip.runtime.data[base+2] = UInt8((v >> 16) & 0xFF)
        strip.runtime.data[base+3] = UInt8((v >> 24) & 0xFF)
    }

    static func effect_rollingballs(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        let dataSize = RBALL_SIZE * RBALL_MAX
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let numBalls = Int(strip.runtime.intensity) / 16 + 1
        let hasCol2 = strip.runtime.colors[2] != 0

        if strip.runtime.call == 0 {
            strip.fill(hasCol2 ? BLACK : strip.runtime.colors[1])
            for i in 0..<RBALL_MAX {
                let base = i * RBALL_SIZE
                rballWriteU32(strip, base + 0, strip.now)
                let vel = 20.0 * Float(random16(1000, 10000)) / 10000.0
                rballWriteFloat(strip, base + 8, random8() < 128 ? -vel : vel)
                rballWriteFloat(strip, base + 12, Float(random16(0, 10000)) / 10000.0)
                rballWriteFloat(strip, base + 4, Float(random16(1000, 10000)) / 10000.0)
            }
        }

        let cfac = Float(scale8(8, 255 &- strip.runtime.speed) + 1) * 20000.0

        if strip.runtime.check3 {
            strip.fadeOut(250)
        } else {
            if !strip.runtime.check2 {
                strip.fill(hasCol2 ? BLACK : strip.runtime.colors[1])
            }
        }

        for i in 0..<numBalls {
            let base = i * RBALL_SIZE
            let lastBounce = rballReadU32(strip, base + 0)
            var mass = rballReadFloat(strip, base + 4)
            var velocity = rballReadFloat(strip, base + 8)
            var height = rballReadFloat(strip, base + 12)

            let timeSinceLastUpdate = Float(strip.now &- lastBounce) / cfac
            var thisHeight = height + velocity * timeSinceLastUpdate

            if thisHeight < -0.5 || thisHeight > 1.5 {
                thisHeight = Float(random16(0, 10000)) / 10000.0
                height = thisHeight
                rballWriteFloat(strip, base + 12, height)
                rballWriteU32(strip, base + 0, strip.now)
            }

            if (thisHeight <= 0.0 && velocity < 0.0) || (thisHeight >= 1.0 && velocity > 0.0) {
                velocity = -velocity
                rballWriteFloat(strip, base + 8, velocity)
                rballWriteU32(strip, base + 0, strip.now)
                height = thisHeight
                rballWriteFloat(strip, base + 12, height)
            }

            if strip.runtime.check1 {
                for j in (i+1)..<numBalls {
                    let baseJ = j * RBALL_SIZE
                    let velJ = rballReadFloat(strip, baseJ + 8)
                    if velJ != velocity {
                        let heightJ = rballReadFloat(strip, baseJ + 12)
                        let lastBounceJ = rballReadU32(strip, baseJ + 0)
                        let massJ = rballReadFloat(strip, baseJ + 4)

                        let tcollided = (cfac * (height - heightJ) +
                            velocity * Float(lastBounceJ &- lastBounce)) / (velJ - velocity)

                        if tcollided > 2.0 && tcollided < Float(strip.now &- lastBounceJ) {
                            height = height + velocity * (tcollided + Float(lastBounceJ &- lastBounce)) / cfac
                            rballWriteFloat(strip, baseJ + 12, height)
                            rballWriteFloat(strip, base + 12, height)

                            let newLastBounce = UInt32(tcollided + 0.5) &+ lastBounceJ
                            rballWriteU32(strip, base + 0, newLastBounce)
                            rballWriteU32(strip, baseJ + 0, newLastBounce)

                            let vtmp = velocity
                            velocity = ((mass - massJ) * vtmp + 2.0 * massJ * velJ) / (mass + massJ)
                            let newVelJ = ((massJ - mass) * velJ + 2.0 * mass * vtmp) / (mass + massJ)
                            rballWriteFloat(strip, base + 8, velocity)
                            rballWriteFloat(strip, baseJ + 8, newVelJ)

                            mass = rballReadFloat(strip, base + 4)

                            thisHeight = height + velocity * Float(strip.now &- rballReadU32(strip, base + 0)) / cfac
                        }
                    }
                }
            }

            var color = strip.runtime.colors[0]
            if strip.runtime.palette != 0 {
                color = strip.colorFromPalette(UInt8(clamping: i * 255 / numBalls))
            } else if hasCol2 {
                color = strip.runtime.colors[i % 3]
            }

            if thisHeight < 0.0 { thisHeight = 0.0 }
            if thisHeight > 1.0 { thisHeight = 1.0 }
            let pos = Int((thisHeight * Float(SEGLEN - 1)).rounded())
            strip.setPixelColor(pos, color)

            rballWriteU32(strip, base + 0, strip.now)
            rballWriteFloat(strip, base + 12, thisHeight)
        }

        return FRAMETIME
    }

    static func effect_two_dots(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let delay = 1 + (Int(FRAMETIME) << 3) / SEGLEN
        let it = strip.now / UInt32(self.map(Int(strip.runtime.speed), 0, 255, delay << 4, delay))
        let offset = Int(it) % SEGLEN
        var width = (SEGLEN * (Int(strip.runtime.intensity) + 1)) >> 9
        if width == 0 { width = 1 }

        if !strip.runtime.check2 {
            strip.fill(strip.runtime.colors[2])
        }

        let color1 = strip.runtime.colors[0]
        let color2 = (strip.runtime.colors[1] == strip.runtime.colors[2]) ? color1 : strip.runtime.colors[1]

        for i in 0..<width {
            let indexR = (offset + i) % SEGLEN
            let indexB = (offset + i + (SEGLEN >> 1)) % SEGLEN
            strip.setPixelColor(indexR, color1)
            strip.setPixelColor(indexB, color2)
        }

        return FRAMETIME
    }
}

