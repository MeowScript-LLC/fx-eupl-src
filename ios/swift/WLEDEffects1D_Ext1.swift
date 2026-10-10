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

    static func effect_blink(_ strip: WLEDEffectStrip) -> UInt16 {
        blinkBase(strip, color1: strip.runtime.colors[0], color2: strip.runtime.colors[1], strobe: false, doPalette: true)
    }

    static func effect_blink_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        let wheelColor = strip.colorWheel(UInt8(truncatingIfNeeded: strip.runtime.call & 0xFF))
        return blinkBase(strip, color1: wheelColor, color2: strip.runtime.colors[1], strobe: false, doPalette: false)
    }

    static func effect_strobe(_ strip: WLEDEffectStrip) -> UInt16 {
        blinkBase(strip, color1: strip.runtime.colors[0], color2: strip.runtime.colors[1], strobe: true, doPalette: true)
    }

    static func effect_strobe_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        let wheelColor = strip.colorWheel(UInt8(truncatingIfNeeded: strip.runtime.call & 0xFF))
        return blinkBase(strip, color1: wheelColor, color2: strip.runtime.colors[1], strobe: true, doPalette: false)
    }

    private static func blinkBase(_ strip: WLEDEffectStrip, color1: WLEDColor, color2: WLEDColor, strobe: Bool, doPalette: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        var cycleTime = UInt32(255 &- UInt32(strip.runtime.speed)) * 20
        var onTime = UInt32(FRAMETIME)
        if !strobe {
            onTime += (cycleTime * UInt32(strip.runtime.intensity)) >> 8
        }
        cycleTime += UInt32(FRAMETIME) * 2

        let it = strip.now / cycleTime
        let rem = strip.now % cycleTime

        var on = false
        if it != strip.runtime.step || rem <= onTime {
            on = true
        }

        strip.runtime.step = it

        let color = on ? color1 : color2
        if color == color1 && doPalette {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
            }
        } else {
            strip.fill(color)
        }
        return FRAMETIME
    }

    static func effect_color_wipe(_ strip: WLEDEffectStrip) -> UInt16 {
        colorWipeBase(strip, rev: false, useRandomColors: false)
    }

    static func effect_color_wipe_random(_ strip: WLEDEffectStrip) -> UInt16 {
        colorWipeBase(strip, rev: false, useRandomColors: true)
    }

    private static func colorWipeBase(_ strip: WLEDEffectStrip, rev: Bool, useRandomColors: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let cycleTime = UInt32(750) &+ UInt32(255 &- UInt32(strip.runtime.speed)) &* 150
        let perc = strip.now % cycleTime
        var prog = Int((UInt32(perc) &* 65535) / cycleTime)
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
                strip.runtime.aux1 = UInt16(getRandomWheelIndex(UInt8(truncatingIfNeeded: strip.runtime.aux0 & 0xFF)))
                strip.runtime.step = 2
            }
            if strip.runtime.step == 3 {
                strip.runtime.aux0 = UInt16(getRandomWheelIndex(UInt8(truncatingIfNeeded: strip.runtime.aux1 & 0xFF)))
                strip.runtime.step = 0
            }
        }

        let ledIndex = (prog * SEGLEN) >> 15
        var rem = UInt16(truncatingIfNeeded: (prog * SEGLEN) * 2)
        rem /= UInt16(strip.runtime.intensity) + 1
        if rem > 255 { rem = 255 }

        let col1 = useRandomColors ? colorWheel(UInt8(truncatingIfNeeded: strip.runtime.aux1 & 0xFF)) : strip.runtime.colors[1]

        for i in 0..<SEGLEN {
            let index = (rev && back) ? SEGLEN - 1 - i : i
            let col0 = useRandomColors ? colorWheel(UInt8(truncatingIfNeeded: strip.runtime.aux0 & 0xFF)) : strip.colorFromPalette(i: index, mapping: true, wrap: true, colorIndex: 0)

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

    static func effect_random_color(_ strip: WLEDEffectStrip) -> UInt16 {
        let cycleTime = UInt32(200) &+ UInt32(255 &- UInt32(strip.runtime.speed)) &* 50
        let it = strip.now / cycleTime
        let rem = strip.now % cycleTime
        let fadedur = (cycleTime &* UInt32(strip.runtime.intensity)) >> 8

        var fade: UInt32 = 255
        if fadedur > 0 {
            fade = (rem &* 255) / fadedur
            if fade > 255 { fade = 255 }
        }

        if strip.runtime.call == 0 {
            strip.runtime.aux0 = UInt16(random8())
            strip.runtime.step = 2
        }
        if it != strip.runtime.step {
            strip.runtime.aux1 = strip.runtime.aux0
            strip.runtime.aux0 = UInt16(getRandomWheelIndex(UInt8(truncatingIfNeeded: strip.runtime.aux0 & 0xFF)))
            strip.runtime.step = it
        }

        let blended = colorBlend(
            colorWheel(UInt8(truncatingIfNeeded: strip.runtime.aux1 & 0xFF)),
            colorWheel(UInt8(truncatingIfNeeded: strip.runtime.aux0 & 0xFF)),
            UInt8(fade)
        )
        strip.fill(blended)
        return FRAMETIME
    }

    static func effect_colorloop(_ strip: WLEDEffectStrip) -> UInt16 {
        let counter = (UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 2) &+ 2)) & 0xFFFF
        let ctr = UInt8(truncatingIfNeeded: counter >> 8)

        if strip.runtime.intensity < 128 {
            let blend = UInt8(128 &- strip.runtime.intensity)
            strip.fill(colorBlend(colorWheel(ctr), WHITE, blend))
        } else {
            strip.fill(colorWheel(ctr))
        }
        return FRAMETIME
    }

    static func effect_scan(_ strip: WLEDEffectStrip) -> UInt16 {
        scanBase(strip, dual: false)
    }

    static func effect_dual_scan(_ strip: WLEDEffectStrip) -> UInt16 {
        scanBase(strip, dual: true)
    }

    private static func scanBase(_ strip: WLEDEffectStrip, dual: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN <= 1 { return effect_solid(strip) }

        let cycleTime = UInt32(750) &+ UInt32(255 &- UInt32(strip.runtime.speed)) &* 150
        let perc = strip.now % cycleTime
        let prog = Int((UInt32(perc) &* 65535) / cycleTime)
        let size = 1 + ((Int(strip.runtime.intensity) * SEGLEN) >> 9)
        var ledIndex = (prog * ((SEGLEN * 2) - size * 2)) >> 16

        if !strip.runtime.check2 {
            strip.fill(strip.runtime.colors[1])
        }

        var ledOffset = ledIndex - (SEGLEN - size)
        ledOffset = abs(ledOffset)

        if dual {
            for j in ledOffset..<(ledOffset + size) {
                let i2 = SEGLEN - 1 - j
                if i2 >= 0 && i2 < SEGLEN {
                    let colorIdx = strip.runtime.colors[2] != 0 ? 2 : 0
                    strip.setPixelColor(i2, strip.colorFromPalette(i: i2, mapping: true, wrap: true, colorIndex: colorIdx))
                }
            }
        }

        for j in ledOffset..<(ledOffset + size) {
            if j >= 0 && j < SEGLEN {
                strip.setPixelColor(j, strip.colorFromPalette(i: j, mapping: true, wrap: true, colorIndex: 0))
            }
        }
        return FRAMETIME
    }

    static func effect_theater_chase(_ strip: WLEDEffectStrip) -> UInt16 {
        runningBase(strip, color1: strip.runtime.colors[0], color2: strip.runtime.colors[1], theatre: true)
    }

    static func effect_theater_chase_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        let wheelColor = colorWheel(UInt8(truncatingIfNeeded: strip.runtime.step & 0xFF))
        return runningBase(strip, color1: wheelColor, color2: strip.runtime.colors[1], theatre: true)
    }

    private static func runningBase(_ strip: WLEDEffectStrip, color1: WLEDColor, color2: WLEDColor, theatre: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let width = (theatre ? 3 : 1) + Int(strip.runtime.intensity >> 4)
        let cycleTime = UInt32(50) &+ UInt32(255 &- UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime
        let usePalette = (color1 == strip.runtime.colors[0])

        for i in 0..<SEGLEN {
            var col = color2
            var c1 = color1
            if usePalette {
                c1 = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            }
            if theatre {
                if (i % width) == Int(strip.runtime.aux0) {
                    col = c1
                }
            } else {
                let pos = i % (width << 1)
                if (pos < Int(strip.runtime.aux0) - width) || (pos >= Int(strip.runtime.aux0) && pos < Int(strip.runtime.aux0) + width) {
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

    static func effect_running_lights(_ strip: WLEDEffectStrip) -> UInt16 {
        runningLightsBase(strip, saw: false)
    }

    static func effect_saw(_ strip: WLEDEffectStrip) -> UInt16 {
        runningLightsBase(strip, saw: true)
    }

    private static func runningLightsBase(_ strip: WLEDEffectStrip, saw: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let xScale = UInt32(strip.runtime.intensity >> 2)
        let counter = (UInt32(strip.now) &* UInt32(strip.runtime.speed)) >> 9

        for i in 0..<SEGLEN {
            var a = UInt32(i) &* xScale &- counter

            if saw {
                a &= 0xFF
                if a < 16 {
                    a = 192 &+ a &* 8
                } else {
                    a = UInt32(map(Int(a), 16, 255, 64, 192))
                }
                a = 255 &- a
            }

            let s = sin8(UInt8(truncatingIfNeeded: a & 0xFF))
            let palColor = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            let ca = colorBlend(strip.runtime.colors[1], palColor, s)
            strip.setPixelColor(i, ca)
        }
        return FRAMETIME
    }

    static func effect_dissolve_random(_ strip: WLEDEffectStrip) -> UInt16 {
        dissolveBase(strip, color: colorWheel(random8()))
    }

    private static func dissolveBase(_ strip: WLEDEffectStrip, color: WLEDColor) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let dataSize = (SEGLEN + 7) >> 3
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        if strip.runtime.call == 0 {
            for i in 0..<dataSize { strip.runtime.data[i] = 0xFF }
            strip.runtime.aux0 = 1
        }

        for _ in 0...(SEGLEN / 15) {
            if random8() <= strip.runtime.intensity {
                for _ in 0..<10 {
                    let i = Int(random16(UInt16(SEGLEN)))
                    let index = i >> 3
                    let bitNum = i & 0x07
                    let fadeUp = (strip.runtime.data[index] >> bitNum) & 1 != 0

                    if strip.runtime.aux0 != 0 {
                        if fadeUp {
                            if color == strip.runtime.colors[0] {
                                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
                            } else {
                                strip.setPixelColor(i, color)
                            }
                            strip.runtime.data[index] &= ~(1 << bitNum)
                            break
                        }
                    } else {
                        if !fadeUp {
                            strip.setPixelColor(i, strip.runtime.colors[1])
                            strip.runtime.data[index] |= (1 << bitNum)
                            break
                        }
                    }
                }
            }
        }

        if strip.runtime.step > UInt32(255 &- UInt32(strip.runtime.speed)) + 15 {
            strip.runtime.aux0 = strip.runtime.aux0 != 0 ? 0 : 1
            strip.runtime.step = 0
            let fillVal: UInt8 = strip.runtime.aux0 != 0 ? 0xFF : 0
            for i in 0..<dataSize { strip.runtime.data[i] = fillVal }
        } else {
            strip.runtime.step &+= 1
        }
        return FRAMETIME
    }

    static func effect_flash_sparkle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        if !strip.runtime.check2 {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
            }
        }

        if strip.now &- UInt32(strip.runtime.aux0) > strip.runtime.step {
            let threshold = (255 &- strip.runtime.intensity) >> 4
            if random8(threshold) == 0 {
                strip.setPixelColor(Int(random16(UInt16(SEGLEN))), strip.runtime.colors[1])
            }
            strip.runtime.step = strip.now
            strip.runtime.aux0 = UInt16(255 &- strip.runtime.speed)
        }
        return FRAMETIME
    }

    static func effect_hyper_sparkle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        if !strip.runtime.check2 {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
            }
        }

        if strip.now &- UInt32(strip.runtime.aux0) > strip.runtime.step {
            let threshold = (255 &- strip.runtime.intensity) >> 4
            if random8(threshold) == 0 {
                let len = max(1, SEGLEN / 3)
                for _ in 0..<len {
                    strip.setPixelColor(Int(random16(UInt16(SEGLEN))), strip.runtime.colors[1])
                }
            }
            strip.runtime.step = strip.now
            strip.runtime.aux0 = UInt16(255 &- strip.runtime.speed)
        }
        return FRAMETIME
    }

    static func effect_multi_strobe(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1))
        }

        var delay = UInt16(50) &+ 20 &* UInt16(255 &- strip.runtime.speed)
        let count = UInt16(2 * ((UInt16(strip.runtime.intensity) / 10) + 1))

        if strip.runtime.aux1 < count {
            if (strip.runtime.aux1 & 1) == 0 {
                strip.fill(strip.runtime.colors[0])
                delay = 15
            } else {
                delay = 50
            }
        }

        strip.runtime.aux0 = delay

        if strip.now &- UInt32(strip.runtime.aux0) > strip.runtime.step {
            strip.runtime.aux1 &+= 1
            if strip.runtime.aux1 > count { strip.runtime.aux1 = 0 }
            strip.runtime.step = strip.now
        }
        return FRAMETIME
    }
}
