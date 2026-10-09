/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * FX_2Dfcn.cpp: Copyright (c) 2022 Blaz Kristan. Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

import Foundation
import ObjectiveC

private func beatsin8(_ bpm: Int, _ lo: Int = 0, _ hi: Int = 255, _ timebase: UInt32 = 0, _ offset: Int = 0) -> UInt8 {
    let b = UInt8(clamping: bpm), l = UInt8(clamping: lo), h = UInt8(clamping: hi), o = UInt8(clamping: offset)
    let beat = UInt16(WLEDEffects1D.beat8(b, now: timebase)) &+ UInt16(o)
    let s = WLEDEffects1D.sin8(UInt8(beat & 0xFF))
    return UInt8(UInt16(l) + (UInt16(h >= l ? h &- l : 0) * UInt16(s)) / 255)
}

private func beatsin16(_ bpm: Int, _ lo: Int = 0, _ hi: Int = 65535, _ timebase: UInt32 = 0, _ offset: Int = 0) -> UInt16 {
    let beat = WLEDEffects1D.beat16(UInt16(clamping: bpm), now: timebase) &+ UInt16(clamping: offset)
    let sinVal = WLEDEffects1D.sin16(beat)
    let range = Int32(hi) - Int32(lo)
    return UInt16(clamping: Int32(lo) + (range * (Int32(sinVal) + 32768)) / 65536)
}

private func beat8(_ bpm: Int, _ timebase: UInt32 = 0) -> UInt8 {
    WLEDEffects1D.beat8(UInt8(clamping: bpm), now: timebase)
}

private func inoise8(_ x: Int, _ y: Int, _ z: Int = 0) -> UInt8 {
    WLEDEffects1D.inoise8(x, y)
}

private func inoise16(_ x: Int, _ y: Int, _ z: Int = 0) -> Int {
    Int(WLEDEffects1D.inoise16(UInt32(bitPattern: Int32(x)), UInt32(bitPattern: Int32(y)), UInt32(bitPattern: Int32(z))))
}

private func lerp8by8(_ a: Int, _ b: Int, _ frac: Int) -> UInt8 {
    UInt8(clamping: Int(a) + ((Int(b) - Int(a)) * Int(frac)) / 256)
}

private func ease8InOutApprox(_ i: Int) -> UInt8 {
    if i < 128 { return UInt8((UInt16(i) * UInt16(i)) >> 7) }
    let j = 255 &- i
    return 255 &- UInt8((UInt16(j) * UInt16(j)) >> 7)
}

private func sqrt16(_ x: Int) -> UInt8 {
    UInt8(clamping: Int(Double(max(0, x)).squareRoot()))
}

private func abs8(_ x: Int) -> UInt8 {
    UInt8(clamping: abs(x))
}

private func constrainF(_ v: Float, _ lo: Float, _ hi: Float) -> Float {
    min(max(v, lo), hi)
}

private func cos8(_ x: Int) -> UInt8 {
    WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: x &+ 64))
}

private let DARKSLATEGRAY: WLEDColor = (0x2F << 16) | (0x4F << 8) | 0x4F

private func colorFromPalette16(_ palette: [(UInt8, UInt8, UInt8)], _ index: Int, _ bri: UInt8 = 255) -> WLEDColor {
    guard !palette.isEmpty else { return 0 }
    let idx = Int(index & 0xFF) * (palette.count - 1) / 255
    let clamped = min(idx, palette.count - 1)
    let (r, g, b) = palette[clamped]
    let br = UInt16(bri)
    return (UInt32(UInt16(r) * br / 255) << 16) | (UInt32(UInt16(g) * br / 255) << 8) | UInt32(UInt16(b) * br / 255)
}

private let firePalette: [(UInt8, UInt8, UInt8)] = [
    (0, 0, 0), (32, 0, 0), (64, 0, 0), (128, 0, 0),
    (192, 32, 0), (255, 64, 0), (255, 128, 0), (255, 192, 0),
    (255, 224, 32), (255, 240, 64), (255, 248, 128), (255, 252, 192),
    (255, 255, 224), (255, 255, 240), (255, 255, 248), (255, 255, 255)
]

private let auroraPalette: [(UInt8, UInt8, UInt8)] = [
    (1, 5, 45), (1, 5, 45), (5, 30, 45), (0, 70, 60),
    (0, 100, 80), (0, 128, 100), (20, 155, 100), (50, 180, 100),
    (80, 200, 100), (100, 220, 80), (130, 235, 60), (160, 245, 40),
    (200, 252, 20), (230, 255, 10), (245, 255, 5), (255, 255, 0)
]

enum WLEDEffects2D {

    static let effects: [Int: WLEDEffectFunc] = [
        114: effect_rotozoomer,
        118: effect_spaceships,
        119: effect_crazyBees,
        120: effect_ghostRider,
        121: effect_blobs,
        122: effect_scrollingText,
        123: effect_driftRose,
        124: effect_distortionWaves,
        125: effect_soap,
        126: effect_octopus,
        127: effect_wavingCell,
        128: effect_pixels,
        129: effect_pixelwave,
        130: effect_juggles,
        131: effect_matripix,
        132: effect_gravimeter,
        133: effect_plasmoid,
        134: effect_puddles,
        135: effect_midnoise,
        136: effect_noisemeter,
        137: effect_freqwave,
        138: effect_freqmatrix,
        139: effect_2DGEQ,
        140: effect_waterfall,
        141: effect_freqpixels,
        142: effect_binmap,
        143: effect_noisefire,
        144: effect_puddlepeak,
        145: effect_noisemove,
        146: effect_noise2D,
        147: effect_perlinMove,
        148: effect_ripplePeak,
        149: effect_firenoise,
        150: effect_squaredSwirl,
        151: effect_pacman,
        152: effect_dna,
        153: effect_matrix,
        154: effect_metaballs,
        155: effect_freqmap,
        156: effect_gravcenter,
        157: effect_gravcentric,
        158: effect_gravfreq,
        159: effect_djlight,
        160: effect_funkyPlank,
        161: effect_shimmer,
        162: effect_pulser,
        163: effect_blurz,
        164: effect_drift,
        165: effect_waverly,
        166: effect_sunRadiation,
        167: effect_coloredBursts,
        168: effect_julia,
        172: effect_gameOfLife,
        173: effect_tartan,
        174: effect_polarLights,
        175: effect_swirl,
        176: effect_lissajous,
        177: effect_frizzles,
        178: effect_plasmaBall,
        179: effect_flowStripe,
        180: effect_hiphotic,
        181: effect_sindots,
        182: effect_dnaSpiral,
        183: effect_blackHole,
    ]

    static let FRAMETIME: UInt16 = WLEDEffects1D.FRAMETIME
    static let BLACK: WLEDColor = WLEDEffects1D.BLACK
    static let WHITE: WLEDColor = WLEDEffects1D.WHITE

    static func effect_octopus(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let mapp: UInt8 = UInt8(clamping: 180 / max(1, max(cols, rows)))

        let mapSize = cols * rows * 2
        let dataSize = mapSize + 2
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        let offsXOld = strip.runtime.data[mapSize]
        let offsYOld = strip.runtime.data[mapSize + 1]

        if strip.runtime.call == 0 ||
           strip.runtime.aux0 != UInt16(cols) ||
           strip.runtime.aux1 != UInt16(rows) ||
           strip.runtime.custom1 != offsXOld ||
           strip.runtime.custom2 != offsYOld {
            strip.runtime.step = 0
            strip.runtime.aux0 = UInt16(cols)
            strip.runtime.aux1 = UInt16(rows)
            strip.runtime.data[mapSize] = strip.runtime.custom1
            strip.runtime.data[mapSize + 1] = strip.runtime.custom2
            let cX = cols / 2 + (Int(strip.runtime.custom1) - 128) * cols / 255
            let cY = rows / 2 + (Int(strip.runtime.custom2) - 128) * rows / 255
            for x in 0..<cols {
                for y in 0..<rows {
                    let dx = Float(x - cX)
                    let dy = Float(y - cY)
                    let angle = UInt8(truncatingIfNeeded: Int(40.7436 * atan2(Double(dy), Double(dx))))
                    let radius = UInt8(clamping: Int(sqrtf(dx * dx + dy * dy) * Float(mapp)))
                    let idx = (y * cols + x) * 2
                    strip.runtime.data[idx] = angle
                    strip.runtime.data[idx + 1] = radius
                }
            }
        }

        strip.runtime.step &+= UInt32(strip.runtime.speed) / 32 + 1
        for x in 0..<cols {
            for y in 0..<rows {
                let idx = (y * cols + x) * 2
                let angle = strip.runtime.data[idx]
                let radius = strip.runtime.data[idx + 1]
                let inner = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded:
                    (Int(angle) * 4 - Int(radius)) / 4 + Int(strip.runtime.step / 2)))
                let intensity32 = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded:
                    Int(inner) + Int(radius) - Int(strip.runtime.step) + Int(angle) * (Int(strip.runtime.custom3) / 4 + 1))))
                let mapped = WLEDEffects1D.map(intensity32 * intensity32, 0, 65025, 0, 255)
                let palIdx = UInt8(truncatingIfNeeded: (Int(strip.runtime.step) / 2 - Int(radius)) & 0xFF)
                let c = strip.colorFromPalette(palIdx, UInt8(clamping: mapped))
                strip.setPixelColorXY(x, y, c)
            }
        }
        return FRAMETIME
    }

    static func effect_squaredSwirl(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        let kBorderWidth: Int = 2
        strip.fadeToBlackBy(24)
        strip.blur2D(strip.runtime.custom3 >> 1)

        let now = strip.now
        let i = Int(beatsin8(19, kBorderWidth, cols - kBorderWidth, now))
        let j = Int(beatsin8(22, kBorderWidth, cols - kBorderWidth, now))
        let k = Int(beatsin8(17, kBorderWidth, cols - kBorderWidth, now))
        let m = Int(beatsin8(18, kBorderWidth, rows - kBorderWidth, now))
        let n = Int(beatsin8(15, kBorderWidth, rows - kBorderWidth, now))
        let p = Int(beatsin8(20, kBorderWidth, rows - kBorderWidth, now))

        strip.addPixelColorXY(i, m, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 29)))
        strip.addPixelColorXY(j, n, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 41)))
        strip.addPixelColorXY(k, p, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 73)))

        return FRAMETIME
    }

    static func effect_dna(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(64)
        for i in 0..<cols {
            let y1 = Int(beatsin8(Int(strip.runtime.speed) / 8, 0, rows - 1, now, i * 4))
            let bri1 = beatsin8(5, 55, 255, now, i * 10)
            let palIdx1 = UInt8(truncatingIfNeeded: (i * 5 + Int(now / 17)) & 0xFF)
            strip.setPixelColorXY(i, y1, strip.colorFromPalette(palIdx1, bri1))

            let y2 = Int(beatsin8(Int(strip.runtime.speed) / 8, 0, rows - 1, now, i * 4 + 128))
            let bri2 = beatsin8(5, 55, 255, now, i * 10 + 128)
            let palIdx2 = UInt8(truncatingIfNeeded: (i * 5 + 128 + Int(now / 17)) & 0xFF)
            strip.setPixelColorXY(i, y2, strip.colorFromPalette(palIdx2, bri2))
        }
        strip.blur2D(strip.runtime.intensity >> 3)
        return FRAMETIME
    }

    static func effect_matrix(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        let dataSize = (cols * rows + 7) >> 3
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
            strip.runtime.step = 0
        }

        let fade = UInt8(clamping: WLEDEffects1D.map(Int(strip.runtime.custom1), 0, 255, 50, 250))
        let speed = UInt32(clamping: (256 - Int(strip.runtime.speed)) >> WLEDEffects1D.map(min(rows, 150), 0, 150, 0, 3))

        var spawnColor: WLEDColor
        var trailColor: WLEDColor
        if strip.runtime.check1 {
            spawnColor = strip.runtime.colors[0]
            trailColor = strip.runtime.colors[1]
        } else {
            spawnColor = WLEDEffects1D.rgbw32(175, 255, 175)
            trailColor = WLEDEffects1D.rgbw32(27, 130, 39)
        }

        var emptyScreen = true
        if strip.now &- strip.runtime.step >= speed {
            strip.runtime.step = strip.now
            strip.fadeToBlackBy(fade)

            for row in stride(from: rows - 1, through: 0, by: -1) {
                for col in 0..<cols {
                    let xyIdx = row * cols + col
                    let index = xyIdx >> 3
                    let bitNum = xyIdx & 0x07
                    if (strip.runtime.data[index] >> bitNum) & 1 != 0 {
                        strip.setPixelColorXY(col, row, trailColor)
                        strip.runtime.data[index] &= ~UInt8(1 << bitNum)
                        if row < rows - 1 {
                            strip.setPixelColorXY(col, row + 1, spawnColor)
                            let nextXY = (row + 1) * cols + col
                            let ni = nextXY >> 3
                            let nb = nextXY & 0x07
                            strip.runtime.data[ni] |= UInt8(1 << nb)
                            emptyScreen = false
                        }
                    }
                }
            }

            if WLEDEffects1D.random8() <= strip.runtime.intensity || emptyScreen {
                let spawnX = Int(WLEDEffects1D.random8(UInt8(clamping: cols)))
                strip.setPixelColorXY(spawnX, 0, spawnColor)
                let spawnXY = spawnX
                let si = spawnXY >> 3
                let sb = spawnXY & 0x07
                strip.runtime.data[si] |= UInt8(1 << sb)
            }
        }
        return FRAMETIME
    }

    static func effect_metaballs(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let speed: Float = 0.25 * Float(1 + (Int(strip.runtime.speed) >> 6))
        let speedNow = Float(now) * speed

        let x2 = WLEDEffects1D.map(Int(inoise8(Int(speedNow), 25355, 685)), 0, 255, 0, cols - 1)
        let y2 = WLEDEffects1D.map(Int(inoise8(Int(speedNow), 355, 11685)), 0, 255, 0, rows - 1)
        let x3 = WLEDEffects1D.map(Int(inoise8(Int(speedNow), 55355, 6685)), 0, 255, 0, cols - 1)
        let y3 = WLEDEffects1D.map(Int(inoise8(Int(speedNow), 25355, 22685)), 0, 255, 0, rows - 1)
        let x1 = Int(beatsin8(Int(23.0 * speed), 0, cols - 1, now))
        let y1 = Int(beatsin8(Int(28.0 * speed), 0, rows - 1, now))

        for y in 0..<rows {
            for x in 0..<cols {
                var dx = abs(x - x1)
                var dy = abs(y - y1)
                var dist = Int(2 * sqrt16(Int(dx * dx + dy * dy)))

                dx = abs(x - x2)
                dy = abs(y - y2)
                dist += Int(sqrt16(Int(dx * dx + dy * dy)))

                dx = abs(x - x3)
                dy = abs(y - y3)
                dist += Int(sqrt16(Int(dx * dx + dy * dy)))

                let colorVal = dist != 0 ? 1000 / dist : 255
                if colorVal > 0 && colorVal < 60 {
                    let mapped = WLEDEffects1D.map(colorVal * 9, 9, 531, 0, 255)
                    strip.setPixelColorXY(x, y, strip.colorFromPalette(UInt8(clamping: mapped)))
                } else {
                    strip.setPixelColorXY(x, y, strip.colorFromPalette(0))
                }
            }
        }
        strip.setPixelColorXY(x1, y1, WHITE)
        strip.setPixelColorXY(x2, y2, WHITE)
        strip.setPixelColorXY(x3, y3, WHITE)

        return FRAMETIME
    }

    static func effect_drift(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let colsCenter = (cols >> 1) + (cols % 2)
        let rowsCenter = (rows >> 1) + (rows % 2)

        strip.fadeToBlackBy(128)
        let maxDim = Float(max(cols, rows)) / 2.0
        let divisor = max(1, 32 - Int(strip.runtime.speed >> 3))
        let t = Int(strip.now) / divisor
        let t_20 = t / 20

        var i: Float = 1.0
        while i < maxDim {
            let angle = Float(t) * Float(maxDim - i) * Float.pi / 180.0
            let mySin = Int(sinf(angle) * i)
            let myCos = Int(cosf(angle) * i)
            let palIdx = UInt8(truncatingIfNeeded: (Int(i * 20) + t_20) & 0xFF)
            let c = strip.colorFromPalette(palIdx)
            strip.setPixelColorXY(colsCenter + mySin, rowsCenter + myCos, c)
            if strip.runtime.check1 {
                strip.setPixelColorXY(colsCenter + myCos, rowsCenter + mySin, c)
            }
            i += 0.25
        }
        strip.blur2D(strip.runtime.intensity >> 3)
        return FRAMETIME
    }

    static func effect_driftRose(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let CX = Float(cols - cols % 2) / 2.0 - 0.5
        let CY = Float(rows - rows % 2) / 2.0 - 0.5
        let L = Float(min(cols, rows)) / 2.0
        let now = strip.now

        strip.fadeToBlackBy(UInt8(clamping: 32 + Int(strip.runtime.speed >> 3)))
        for i in 1..<37 {
            let angle = Float(i) * 10.0 * Float.pi / 180.0
            let bval = Float(Int(beatsin8(i, 0, Int(L * 2), now)) - Int(L))
            let px = (CX + sinf(angle) * bval) * 255.0
            let py = (CY + cosf(angle) * bval) * 255.0
            let c = WLEDEffects1D.hsvToRgb(UInt8(truncatingIfNeeded: (i * 10) & 0xFF), 255, 255)
            strip.wuPixel(Int(max(0, Int(px))), max(0, Int(py)), c)
        }
        strip.blur2D(strip.runtime.intensity >> 4)
        return FRAMETIME
    }

    static func effect_distortionWaves(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let speed = Int(strip.runtime.speed) / 32
        let scale = Int(strip.runtime.intensity) / 32

        let a = Int(now) / 32
        let a2 = a / 2
        let a3 = a / 3

        let cx  = Int(beatsin8(10 - speed, 0, cols - 1, now)) * scale
        let cy  = Int(beatsin8(12 - speed, 0, rows - 1, now)) * scale
        let cx1 = Int(beatsin8(13 - speed, 0, cols - 1, now)) * scale
        let cy1 = Int(beatsin8(15 - speed, 0, rows - 1, now)) * scale
        let cx2 = Int(beatsin8(17 - speed, 0, cols - 1, now)) * scale
        let cy2 = Int(beatsin8(14 - speed, 0, rows - 1, now)) * scale

        var xoffs = 0
        for x in 0..<cols {
            xoffs += scale
            var yoffs = 0
            for y in 0..<rows {
                yoffs += scale

                let rdistort = Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded:
                    (Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((x << 3) + a) & 0xFF))) +
                     Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((y << 3) - a2) & 0xFF))) + a3) & 0xFF))) >> 1

                let gdistort = Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded:
                    (Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((x << 3) - a2) & 0xFF))) +
                     Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((y << 3) + a3) & 0xFF))) + a + 32) & 0xFF))) >> 1

                let bdistort = Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded:
                    (Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((x << 3) + a3) & 0xFF))) +
                     Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: ((y << 3) - a) & 0xFF))) + a2 + 64) & 0xFF))) >> 1

                let valueR = UInt8(truncatingIfNeeded: (rdistort + 2 * (a - (((xoffs - cx) * (xoffs - cx) + (yoffs - cy) * (yoffs - cy)) >> 7))) & 0xFF)
                let valueG = UInt8(truncatingIfNeeded: (gdistort + 2 * (a2 - (((xoffs - cx1) * (xoffs - cx1) + (yoffs - cy1) * (yoffs - cy1)) >> 7))) & 0xFF)
                let valueB = UInt8(truncatingIfNeeded: (bdistort + 2 * (a3 - (((xoffs - cx2) * (xoffs - cx2) + (yoffs - cy2) * (yoffs - cy2)) >> 7))) & 0xFF)

                let fR = WLEDEffects1D.gamma8(WLEDEffects1D.cos8(valueR))
                let fG = WLEDEffects1D.gamma8(WLEDEffects1D.cos8(valueG))
                let fB = WLEDEffects1D.gamma8(WLEDEffects1D.cos8(valueB))

                strip.setPixelColorXY(x, y, WLEDEffects1D.rgbw32(fR, fG, fB))
            }
        }
        return FRAMETIME
    }

    static func effect_firenoise(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
        }

        let xscale = Int(strip.runtime.intensity) * 4
        let yscale = Int(strip.runtime.speed) * 8
        let usePalette = strip.runtime.check1

        for j in 0..<cols {
            for i in 0..<rows {
                let indexx = Int(inoise8(Int(j) * Int(yscale) * rows / max(1, 255), i * xscale + Int(strip.now / 4), 0))
                let palIdx = UInt8(clamping: min(i * indexx >> 4, 255))
                let bri = UInt8(clamping: i * 255 / max(1, cols))
                let c: WLEDColor
                if usePalette {
                    c = strip.colorFromPalette(palIdx, bri)
                } else {
                    c = colorFromPalette16(firePalette, Int(palIdx), bri)
                }
                strip.setPixelColorXY(j, i, c)
            }
        }
        return FRAMETIME
    }

    static func effect_noise2D(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let scale = Int(strip.runtime.intensity) + 2

        for y in 0..<rows {
            for x in 0..<cols {
                let pixelHue = inoise8(Int(x) * Int(scale), y * scale, Int(strip.now) / max(1, 16 - Int(strip.runtime.speed) / 16))
                strip.setPixelColorXY(x, y, strip.colorFromPalette(pixelHue))
            }
        }
        return FRAMETIME
    }

    static func effect_julia(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        if !strip.runtime.allocateData(12) { return WLEDEffects1D.effect_solid(strip) }

        func readFloat(_ offset: Int) -> Float {
            var v: Float = 0
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[offset + i] }
            }
            return v
        }
        func writeFloat(_ offset: Int, _ v: Float) {
            var vv = v
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[offset + i] = ptr[i] }
            }
        }

        if strip.runtime.call == 0 {
            writeFloat(0, 0.0)
            writeFloat(4, 0.0)
            writeFloat(8, 1.0)
            strip.runtime.custom1 = 128
            strip.runtime.custom2 = 128
            strip.runtime.custom3 = 16
            strip.runtime.intensity = 24
        }

        var xcen = readFloat(0)
        var ycen = readFloat(4)
        var xymag = readFloat(8)

        xcen += Float(Int(strip.runtime.custom1) - 128) / 100000.0
        ycen += Float(Int(strip.runtime.custom2) - 128) / 100000.0
        xymag += Float((Int(strip.runtime.custom3) - 16) << 3) / 100000.0
        if xymag < 0.01 { xymag = 0.01 }
        if xymag > 1.0 { xymag = 1.0 }

        writeFloat(0, xcen)
        writeFloat(4, ycen)
        writeFloat(8, xymag)

        let xmin = constrainF(xcen - xymag, -1.2, 1.2)
        let xmax = constrainF(xcen + xymag, -1.2, 1.2)
        let ymin = constrainF(ycen - xymag, -0.8, 1.0)
        let ymax = constrainF(ycen + xymag, -0.8, 1.0)

        let maxIterations = max(1, Int(strip.runtime.intensity) / 2)
        let maxCalc: Float = 16.0

        var reAl: Float = -0.94299
        var imAg: Float = 0.3162

        reAl += Float(WLEDEffects1D.sin16(UInt16(truncatingIfNeeded: (UInt32(strip.now) &* 34) & 0xFFFF))) / 655340.0
        imAg += Float(WLEDEffects1D.sin16(UInt16(truncatingIfNeeded: (UInt32(strip.now) &* 26) & 0xFFFF))) / 655340.0

        let dx = (xmax - xmin) / Float(max(1, cols))
        let dy = (ymax - ymin) / Float(max(1, rows))

        var py = ymin
        for j in 0..<rows {
            var px = xmin
            for i in 0..<cols {
                var a = px
                var b = py
                var iter = 0
                while iter < maxIterations {
                    let aa = a * a
                    let bb = b * b
                    if aa + bb > maxCalc { break }
                    b = 2 * a * b + imAg
                    a = aa - bb + reAl
                    iter += 1
                }
                if iter == maxIterations {
                    strip.setPixelColorXY(i, j, BLACK)
                } else {
                    let palIdx = UInt8(clamping: iter * 255 / maxIterations)
                    strip.setPixelColorXY(i, j, strip.colorFromPalette(palIdx))
                }
                px += dx
            }
            py += dy
        }
        return FRAMETIME
    }

    static func effect_gameOfLife(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let pixelCount = cols * rows
        let dataSize = pixelCount * 3 + 4
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        let backgroundColor = strip.runtime.colors[1]

        if strip.runtime.call == 0 || strip.now &- strip.runtime.step > 3000 {
            strip.runtime.step = strip.now
            strip.runtime.aux0 = 0

            for x in 0..<cols {
                for y in 0..<rows {
                    if WLEDEffects1D.random8() % 2 == 0 {
                        strip.setPixelColorXY(x, y, backgroundColor)
                    } else {
                        strip.setPixelColorXY(x, y, strip.colorFromPalette(WLEDEffects1D.random8()))
                    }
                }
            }
            for i in 0..<(pixelCount * 3 + 4) { strip.runtime.data[i] = 0 }
        } else {
            let frameDelay = UInt32(WLEDEffects1D.FRAMETIME_FIXED) * UInt32(WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 64, 4))
            if strip.now &- strip.runtime.step < frameDelay {
                return FRAMETIME
            }
        }

        let bgR = WLEDEffects1D.r(backgroundColor)
        let bgG = WLEDEffects1D.g(backgroundColor)
        let bgB = WLEDEffects1D.b(backgroundColor)

        for x in 0..<cols {
            for y in 0..<rows {
                let c = strip.getPixelColorXY(x, y)
                let idx = (y * cols + x) * 3
                strip.runtime.data[idx] = WLEDEffects1D.r(c)
                strip.runtime.data[idx + 1] = WLEDEffects1D.g(c)
                strip.runtime.data[idx + 2] = WLEDEffects1D.b(c)
            }
        }

        for x in 0..<cols {
            for y in 0..<rows {
                var neighbors = 0
                var domR: Int = 0, domG: Int = 0, domB: Int = 0
                var domCount = 0

                for di in -1...1 {
                    for dj in -1...1 {
                        if di == 0 && dj == 0 { continue }
                        var xx = x + di
                        var yy = y + dj
                        if xx < 0 { xx = cols - 1 } else if xx >= cols { xx = 0 }
                        if yy < 0 { yy = rows - 1 } else if yy >= rows { yy = 0 }

                        let pidx = (yy * cols + xx) * 3
                        let pr = strip.runtime.data[pidx]
                        let pg = strip.runtime.data[pidx + 1]
                        let pb = strip.runtime.data[pidx + 2]
                        if pr != bgR || pg != bgG || pb != bgB {
                            neighbors += 1
                            domR += Int(pr)
                            domG += Int(pg)
                            domB += Int(pb)
                            domCount += 1
                        }
                    }
                }

                let cidx = (y * cols + x) * 3
                let cr = strip.runtime.data[cidx]
                let cg = strip.runtime.data[cidx + 1]
                let cb = strip.runtime.data[cidx + 2]
                let isAlive = cr != bgR || cg != bgG || cb != bgB

                if isAlive && neighbors < 2 {
                    strip.setPixelColorXY(x, y, backgroundColor)
                } else if isAlive && neighbors > 3 {
                    strip.setPixelColorXY(x, y, backgroundColor)
                } else if !isAlive && neighbors == 3 {
                    if domCount > 0 && WLEDEffects1D.random8() < 128 {
                        let avgR = UInt8(clamping: domR / domCount)
                        let avgG = UInt8(clamping: domG / domCount)
                        let avgB = UInt8(clamping: domB / domCount)
                        strip.setPixelColorXY(x, y, WLEDEffects1D.rgbw32(avgR, avgG, avgB))
                    }
                } else if !isAlive && neighbors == 2 && WLEDEffects1D.random8() >= 128 {
                    strip.setPixelColorXY(x, y, strip.colorFromPalette(WLEDEffects1D.random8()))
                }
            }
        }

        var crc: UInt16 = 0
        for i in 0..<(pixelCount * 3) {
            crc = crc &+ UInt16(strip.runtime.data[i])
        }
        let crcOff = pixelCount * 3
        let prevCRC = UInt16(strip.runtime.data[crcOff]) | (UInt16(strip.runtime.data[crcOff + 1]) << 8)
        if crc != prevCRC {
            strip.runtime.step = strip.now
        }
        strip.runtime.data[crcOff] = UInt8(crc & 0xFF)
        strip.runtime.data[crcOff + 1] = UInt8(crc >> 8)

        return FRAMETIME
    }

    static func effect_tartan(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
        }

        let offsetX = beatsin16(3, -360, 360, now)
        let offsetY = beatsin16(2, -360, 360, now)
        let sharpness = Int(strip.runtime.custom3) / 8

        for x in 0..<cols {
            for y in 0..<rows {
                let bs1: Int = Int(beatsin16(10, 1, 10, now))
                let rawHue1: Int = (Int(x) * bs1 + Int(offsetY)) & 0xFF
                let hue1 = UInt8(rawHue1)
                let spd: Int = Int(strip.runtime.speed)
                let xOff1: Int = Int(x) * spd / 2 + Int(offsetX)
                let bri1 = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: xOff1 & 0xFF))
                var intensity1: Int = Int(bri1)
                for _ in 0..<sharpness { intensity1 = intensity1 * Int(bri1) >> 8 }
                let c1 = strip.colorFromPalette(hue1, UInt8(clamping: intensity1))
                strip.setPixelColorXY(x, y, c1)

                let rawHue2: Int = (Int(y) * 3 + Int(offsetX)) & 0xFF
                let hue2 = UInt8(rawHue2)
                let intens: Int = Int(strip.runtime.intensity)
                let yOff2: Int = Int(y) * intens / 2 + Int(offsetY)
                let bri2 = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: yOff2 & 0xFF))
                var intensity2 = Int(bri2)
                for _ in 0..<sharpness { intensity2 = intensity2 * Int(bri2) >> 8 }
                strip.addPixelColorXY(x, y, strip.colorFromPalette(hue2, UInt8(clamping: intensity2)))
            }
        }
        return FRAMETIME
    }

    static func effect_polarLights(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
            strip.runtime.step = 0
        }

        let adjustHeight = Float(WLEDEffects1D.map(rows, 8, 32, 28, 12))
        let adjScale = WLEDEffects1D.map(cols, 8, 64, 310, 63)
        let _scale = WLEDEffects1D.map(Int(strip.runtime.intensity), 0, 255, 30, adjScale)
        let _speed = max(1, WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 128, 16))

        for x in 0..<cols {
            for y in 0..<rows {
                strip.runtime.step &+= 1
                let noiseVal = inoise8(
                    Int(strip.runtime.step % 2) + x * _scale,
                    y * 16 + Int(strip.runtime.step % 16),
                    Int(strip.runtime.step) / _speed
                )
                let sub = UInt8(clamping: Int(fabsf(Float(rows) / 2.0 - Float(y)) * adjustHeight))
                let palIdx = WLEDEffects1D.qsub8(noiseVal, sub)
                strip.setPixelColorXY(x, y, colorFromPalette16(auroraPalette, Int(palIdx)))
            }
        }
        return FRAMETIME
    }

    static func effect_swirl(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
        }

        strip.blur2D(strip.runtime.custom1)

        let speedFactor = max(1, Int(strip.runtime.speed) * 27 / 255)
        let speedFactor2 = max(1, Int(strip.runtime.speed) * 41 / 255)
        let i  = Int(beatsin8(speedFactor, 2, cols - 2, now))
        let j  = Int(beatsin8(speedFactor2, 2, rows - 2, now))
        let ni = cols - 1 - i
        let nj = cols - 1 - j

        let volumeBri = UInt8(clamping: 128 * Int(strip.runtime.intensity) / 64)

        strip.addPixelColorXY(i,  j,  strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 11), volumeBri))
        strip.addPixelColorXY(j,  i,  strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 13), volumeBri))
        strip.addPixelColorXY(ni, nj, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 17), volumeBri))
        strip.addPixelColorXY(nj, ni, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 29), volumeBri))
        strip.addPixelColorXY(i,  nj, strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 37), volumeBri))
        strip.addPixelColorXY(ni, j,  strip.colorFromPalette(UInt8(truncatingIfNeeded: now / 41), volumeBri))

        return FRAMETIME
    }

    static func effect_lissajous(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(strip.runtime.intensity)
        let phase = UInt32(now &* (1 + UInt32(strip.runtime.custom3))) / 32

        for i in 0..<256 {
            let xlocn = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (Int(phase / 2) + i * Int(strip.runtime.speed) / 32) & 0xFF))
            let ylocn = WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: (Int(phase / 2) + i * 2) & 0xFF))

            let xMapped: Int
            if cols < 2 {
                xMapped = 1
            } else {
                xMapped = (WLEDEffects1D.map(Int(xlocn) * 2, 0, 511, 0, 2 * (cols - 1)) + 1) / 2
            }
            let yMapped: Int
            if rows < 2 {
                yMapped = 1
            } else {
                yMapped = (WLEDEffects1D.map(Int(ylocn) * 2, 0, 511, 0, 2 * (rows - 1)) + 1) / 2
            }

            let palIdx = UInt8(truncatingIfNeeded: (Int(now / 100) + i) & 0xFF)
            strip.setPixelColorXY(xMapped, yMapped, strip.colorFromPalette(palIdx))
        }
        return FRAMETIME
    }

    static func effect_frizzles(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(16)
        for i in stride(from: 8, through: 1, by: -1) {
            let x = beatsin8(Int(strip.runtime.speed) / 8 + i, 0, cols - 1, now)
            let y = beatsin8(Int(strip.runtime.intensity) / 8 - i, 0, rows - 1, now)
            let c = strip.colorFromPalette(beatsin8(12, 0, 255, now))
            strip.addPixelColorXY(Int(x), Int(y), c)
        }
        strip.blur2D(strip.runtime.custom1 >> 3)
        return FRAMETIME
    }

    static func effect_plasmaBall(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(strip.runtime.custom1 >> 2)
        let t = Int((UInt32(now) &* 8) / max(1, UInt32(256 - UInt32(strip.runtime.speed))))

        for i in 0..<cols {
            let thisVal = Int(inoise8(Int(i) * Int(30), t, t))
            let thisMax = WLEDEffects1D.map(thisVal, 0, 255, 0, cols - 1)
            for j in 0..<rows {
                let thisVal_ = Int(inoise8(t, j * 30, t))
                let thisMax_ = WLEDEffects1D.map(thisVal_, 0, 255, 0, rows - 1)
                let x = i + thisMax_ - cols / 2
                let y = j + thisMax - cols / 2
                let cx = i + thisMax_
                let cy = j + thisMax

                let isLine = ((x - y > -2) && (x - y < 2)) ||
                             ((cols - 1 - x - y > -2) && (cols - 1 - x - y < 2)) ||
                             (cols - cx == 0) ||
                             (cols - 1 - cx == 0) ||
                             (rows - cy == 0) ||
                             (rows - 1 - cy == 0)

                if isLine {
                    let b8 = beat8(5, now)
                    strip.addPixelColorXY(i, j, strip.colorFromPalette(b8, UInt8(clamping: thisVal)))
                }
            }
        }
        strip.blur2D(strip.runtime.custom2 >> 5)
        return FRAMETIME
    }

    static func effect_hiphotic(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let a = Int(strip.now) / max(1, Int(strip.runtime.custom3 >> 1) + 1)

        for x in 0..<cols {
            for y in 0..<rows {
                let inner = Int(WLEDEffects1D.cos8(UInt8(truncatingIfNeeded: (x * Int(strip.runtime.speed) / 16 + a / 3) & 0xFF)))
                let outer = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (y * Int(strip.runtime.intensity) / 16 + a / 4) & 0xFF)))
                let palIdx = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (inner + outer + a) & 0xFF))
                strip.setPixelColorXY(x, y, strip.colorFromPalette(palIdx))
            }
        }
        return FRAMETIME
    }

    static func effect_sindots(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
        }

        strip.fadeToBlackBy(strip.runtime.custom1 >> 3)

        let t1 = UInt8(truncatingIfNeeded: Int(now) / max(1, 257 - Int(strip.runtime.speed)))
        let t2 = WLEDEffects1D.sin8(t1) / 4 * 2

        for i in 0..<13 {
            let x = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (Int(t1) + i * Int(strip.runtime.intensity) / 8) & 0xFF))) * max(1, cols - 1) / 255
            let y = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (Int(t2) + i * Int(strip.runtime.intensity) / 8) & 0xFF))) * max(1, rows - 1) / 255
            let palIdx = UInt8(clamping: i * 255 / 13)
            strip.setPixelColorXY(x, y, strip.colorFromPalette(palIdx))
        }
        strip.blur2D(strip.runtime.custom2 >> 3)
        return FRAMETIME
    }

    static func effect_dnaSpiral(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
        }

        let speeds = Int(strip.runtime.speed) / 2 + 7
        let freq = Int(strip.runtime.intensity) / 8
        let ms = Int(now / 20)

        strip.fadeToBlackBy(135)

        for i in 0..<rows {
            let x  = Int(beatsin8(speeds, 0, cols - 1, now, i * freq)) + Int(beatsin8(max(1, speeds - 7), 0, cols - 1, now, i * freq + 128))
            let x1 = Int(beatsin8(speeds, 0, cols - 1, now, 128 + i * freq)) + Int(beatsin8(max(1, speeds - 7), 0, cols - 1, now, 128 + 64 + i * freq))
            let hue = UInt8(truncatingIfNeeded: (i * 128 / max(1, rows) + ms) & 0xFF)

            if (i + ms / 8) & 3 != 0 {
                let halfX = x / 2
                let halfX1 = x1 / 2
                let steps = max(1, abs8(halfX - halfX1) + 1)
                let positive = halfX1 >= halfX
                for k in 1...steps {
                    let dx = positive ? (Int(halfX) + Int(k) - 1) : (Int(halfX) - Int(k) + 1)
                    strip.addPixelColorXY(dx, i, strip.colorFromPalette(hue))
                    let rate = UInt8(clamping: k * 255 / steps)
                    strip.fadePixelColorXY(dx, i, rate)
                }
                strip.setPixelColorXY(halfX, i, DARKSLATEGRAY)
                strip.setPixelColorXY(halfX1, i, WHITE)
            }
        }
        return FRAMETIME
    }

    static func effect_blackHole(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(UInt8(clamping: 16 + Int(strip.runtime.speed >> 3)))
        let t = Int(now / 128)

        for i in 0..<8 {
            let phaseX = (i % 2 != 0 ? 128 : 0) + t * i
            let phaseY = (i % 2 != 0 ? 192 : 64) + t * i
            let x = Int(beatsin8(Int(strip.runtime.custom1) >> 3, 0, cols - 1, now, phaseX))
            let y = Int(beatsin8(Int(strip.runtime.intensity) >> 3, 0, rows - 1, now, phaseY))
            let palIdx = UInt8(truncatingIfNeeded: (i * 32) & 0xFF)
            let bri: UInt8 = strip.runtime.check1 ? 255 : 255
            strip.addPixelColorXY(x, y, strip.colorFromPalette(palIdx, bri))
        }

        for i in 0..<4 {
            let phaseX = (i % 2 != 0 ? 128 : 0) + t * i
            let phaseY = (i % 2 != 0 ? 192 : 64) + t * i
            let x = Int(beatsin8(Int(strip.runtime.custom2) >> 3, cols / 4, cols - 1 - cols / 4, now, phaseX))
            let y = Int(beatsin8(Int(strip.runtime.custom3), rows / 4, rows - 1 - rows / 4, now, phaseY))
            let palIdx = UInt8(truncatingIfNeeded: (255 - i * 64) & 0xFF)
            strip.addPixelColorXY(x, y, strip.colorFromPalette(palIdx))
        }

        strip.setPixelColorXY(cols / 2, rows / 2, WHITE)

        if strip.runtime.check3 {
            strip.blur2D(16)
        }

        return FRAMETIME
    }

    static func effect_coloredBursts(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.runtime.aux0 = 0
        }

        let dot = strip.runtime.check3
        let grad = strip.runtime.check1
        let numLines = Int(strip.runtime.intensity) / 16 + 1

        strip.runtime.aux0 &+= 1
        strip.fadeToBlackBy(40)

        for i in 0..<numLines {
            let x1 = beatsin8(2 + Int(strip.runtime.speed) / 16, 0, cols - 1, now)
            let x2 = beatsin8(1 + Int(strip.runtime.speed) / 16, 0, rows - 1, now)
            let y1 = beatsin8(5 + Int(strip.runtime.speed) / 16, 0, cols - 1, now, i * 24)
            let y2 = beatsin8(3 + Int(strip.runtime.speed) / 16, 0, rows - 1, now, i * 48 + 64)

            let palIdx = UInt8(truncatingIfNeeded: (i * 255 / max(1, numLines) + Int(strip.runtime.aux0 & 0xFF)) & 0xFF)
            let color = strip.colorFromPalette(palIdx)

            let xsteps = abs(Int(x1) - Int(y1)) + 1
            let ysteps = abs(Int(x2) - Int(y2)) + 1
            let steps = max(xsteps, ysteps)

            for j in 1...steps {
                let rate = UInt8(clamping: j * 255 / steps)
                let dx = lerp8by8(Int(x1), Int(y1), Int(rate))
                let dy = lerp8by8(Int(x2), Int(y2), Int(rate))
                strip.addPixelColorXY(Int(dx), Int(dy), color)
                if grad {
                    strip.fadePixelColorXY(Int(dx), Int(dy), rate)
                }
            }

            if dot {
                strip.setPixelColorXY(Int(x1), Int(x2), WHITE)
                strip.setPixelColorXY(Int(y1), Int(y2), DARKSLATEGRAY)
            }
        }

        if strip.runtime.custom3 > 0 {
            strip.blur2D(strip.runtime.custom3 / 2)
        }

        return FRAMETIME
    }

    static func effect_crazyBees(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let MAX_BEES = 5
        let n = min(MAX_BEES, (rows * cols) / 256 + 1)

        let beeSize = 10
        let dataSize = beeSize * MAX_BEES
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        func beeOff(_ i: Int) -> Int { i * beeSize }

        func aimed(_ i: Int) {
            let off = beeOff(i)
            let posX = strip.runtime.data[off]
            let posY = strip.runtime.data[off + 1]
            strip.runtime.data[off + 2] = WLEDEffects1D.random8(UInt8(clamping: cols))
            strip.runtime.data[off + 3] = WLEDEffects1D.random8(UInt8(clamping: rows))
            strip.runtime.data[off + 4] = WLEDEffects1D.random8()
            let aimX = strip.runtime.data[off + 2]
            let aimY = strip.runtime.data[off + 3]
            let dX = UInt8(clamping: abs(Int(aimX) - Int(posX)))
            let dY = UInt8(clamping: abs(Int(aimY) - Int(posY)))
            strip.runtime.data[off + 5] = dX
            strip.runtime.data[off + 6] = dY
            strip.runtime.data[off + 7] = posX < aimX ? 1 : 255
            strip.runtime.data[off + 8] = posY < aimY ? 1 : 255
            strip.runtime.data[off + 9] = UInt8(bitPattern: Int8(clamping: Int(dX) - Int(dY)))
        }

        if strip.runtime.call == 0 {
            for i in 0..<n {
                let off = beeOff(i)
                strip.runtime.data[off] = WLEDEffects1D.random8(UInt8(clamping: cols))
                strip.runtime.data[off + 1] = WLEDEffects1D.random8(UInt8(clamping: rows))
                aimed(i)
            }
        }

        if strip.now > strip.runtime.step {
            strip.runtime.step = strip.now &+ UInt32(WLEDEffects1D.FRAMETIME) * 16 / (UInt32(strip.runtime.speed >> 4) + 1)

            strip.fadeToBlackBy(32)

            for i in 0..<n {
                let off = beeOff(i)
                let posX = strip.runtime.data[off]
                let posY = strip.runtime.data[off + 1]
                let aimX = strip.runtime.data[off + 2]
                let aimY = strip.runtime.data[off + 3]
                let hue = strip.runtime.data[off + 4]

                let beeColor = WLEDEffects1D.hsvToRgb(hue, 255, 255)
                let bodyColor = WLEDEffects1D.hsvToRgb(hue, 60, 255)

                strip.addPixelColorXY(Int(aimX) + 1, Int(aimY), beeColor)
                strip.addPixelColorXY(Int(aimX), Int(aimY) + 1, beeColor)
                strip.addPixelColorXY(Int(aimX) - 1, Int(aimY), beeColor)
                strip.addPixelColorXY(Int(aimX), Int(aimY) - 1, beeColor)

                if posX != aimX || posY != aimY {
                    strip.setPixelColorXY(Int(posX), Int(posY), bodyColor)
                    let deltaX = Int(strip.runtime.data[off + 5])
                    let deltaY = Int(strip.runtime.data[off + 6])
                    let signX: Int = strip.runtime.data[off + 7] == 1 ? 1 : -1
                    let signY: Int = strip.runtime.data[off + 8] == 1 ? 1 : -1
                    var error = Int8(bitPattern: strip.runtime.data[off + 9])
                    let error2 = Int(error) * 2
                    if error2 > -deltaY {
                        error = Int8(clamping: Int(error) - deltaY)
                        strip.runtime.data[off] = UInt8(clamping: Int(posX) + signX)
                    }
                    if error2 < deltaX {
                        error = Int8(clamping: Int(error) + deltaX)
                        strip.runtime.data[off + 1] = UInt8(clamping: Int(posY) + signY)
                    }
                    strip.runtime.data[off + 9] = UInt8(bitPattern: error)
                } else {
                    aimed(i)
                }
            }
            strip.blur2D(strip.runtime.intensity >> 4)
        }
        return FRAMETIME
    }

    static func effect_ghostRider(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let maxLighters = min(cols + rows, 64)

        let headerSize = 8
        let lighterSize = 9
        let dataSize = headerSize + lighterSize * maxLighters
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        func readI16(_ off: Int) -> Int16 {
            Int16(bitPattern: UInt16(strip.runtime.data[off]) | (UInt16(strip.runtime.data[off + 1]) << 8))
        }
        func writeI16(_ off: Int, _ v: Int16) {
            strip.runtime.data[off] = UInt8(truncatingIfNeeded: v)
            strip.runtime.data[off + 1] = UInt8(truncatingIfNeeded: v >> 8)
        }
        func readU16(_ off: Int) -> UInt16 {
            UInt16(strip.runtime.data[off]) | (UInt16(strip.runtime.data[off + 1]) << 8)
        }
        func writeU16(_ off: Int, _ v: UInt16) {
            strip.runtime.data[off] = UInt8(v & 0xFF)
            strip.runtime.data[off + 1] = UInt8(v >> 8)
        }

        if strip.runtime.aux0 != UInt16(cols) || strip.runtime.aux1 != UInt16(rows) {
            strip.runtime.aux0 = UInt16(cols)
            strip.runtime.aux1 = UInt16(rows)

            let aSpeed = Int8(truncatingIfNeeded: Int(WLEDEffects1D.random8(0, 20)) - 10)
            strip.runtime.data[6] = UInt8(bitPattern: aSpeed)
            writeU16(4, WLEDEffects1D.random16())
            strip.runtime.data[7] = 5
            writeI16(0, Int16(cols / 2 * 10))
            writeI16(2, Int16(rows / 2 * 10))

            for i in 0..<maxLighters {
                let lOff = headerSize + i * lighterSize
                writeI16(lOff, readI16(0))
                writeI16(lOff + 2, readI16(2) &+ Int16(i))
                writeU16(lOff + 4, 0)
                writeU16(lOff + 6, UInt16(i * 2))
                strip.runtime.data[lOff + 8] = 0
            }
        }

        if strip.now > strip.runtime.step {
            strip.runtime.step = strip.now &+ UInt32(1024 / max(1, cols + rows))

            strip.fadeToBlackBy(UInt8(clamping: Int(strip.runtime.speed >> 2) + 64))

            var gPosX = readI16(0)
            var gPosY = readI16(2)
            var gAngle = readU16(4)
            let angleSpeed = Int8(bitPattern: strip.runtime.data[6])
            let Vspeed = Int(strip.runtime.data[7])

            let mainPx = UInt32(clamping: Int(gPosX) * 256 / 10)
            let mainPy = UInt32(clamping: Int(gPosY) * 256 / 10)
            strip.wuPixel(Int(mainPx), Int(mainPy), WHITE)

            let gAngleRad = Float(gAngle) * Float.pi / 180.0
            gPosX &+= Int16(Float(Vspeed) * sinf(gAngleRad))
            gPosY &+= Int16(Float(Vspeed) * cosf(gAngleRad))
            gAngle = UInt16(bitPattern: Int16(bitPattern: gAngle) &+ Int16(angleSpeed))

            if gPosX < 0 { gPosX = Int16((cols - 1) * 10) }
            if gPosX > Int16((cols - 1) * 10) { gPosX = 0 }
            if gPosY < 0 { gPosY = Int16((rows - 1) * 10) }
            if gPosY > Int16((rows - 1) * 10) { gPosY = 0 }

            writeI16(0, gPosX)
            writeI16(2, gPosY)
            writeU16(4, gAngle)

            for i in 0..<maxLighters {
                let lOff = headerSize + i * lighterSize
                var time = readU16(lOff + 6)
                time &+= UInt16(WLEDEffects1D.random8(5, 20))

                var posX = readI16(lOff)
                var posY = readI16(lOff + 2)
                let angle = readU16(lOff + 4)
                var reg = strip.runtime.data[lOff + 8] != 0

                if time >= 255 || posX <= 0 || posX >= Int16((cols - 1) * 10) ||
                   posY <= 0 || posY >= Int16((rows - 1) * 10) {
                    reg = true
                }

                if reg {
                    writeI16(lOff, gPosX)
                    writeI16(lOff + 2, gPosY)
                    let newAngle = UInt16(bitPattern: Int16(bitPattern: gAngle) &+ Int16(WLEDEffects1D.random8(0, 20)) &- 10)
                    writeU16(lOff + 4, newAngle)
                    writeU16(lOff + 6, 0)
                    strip.runtime.data[lOff + 8] = 0
                    time = 0
                } else {
                    let lAngleRad = Float(angle) * Float.pi / 180.0
                    posX &+= Int16(-7.0 * sinf(lAngleRad))
                    posY &+= Int16(-7.0 * cosf(lAngleRad))
                    writeI16(lOff, posX)
                    writeI16(lOff + 2, posY)
                    writeU16(lOff + 6, time)
                }

                let lPosX = readI16(lOff)
                let lPosY = readI16(lOff + 2)
                let lTime = readU16(lOff + 6)
                let palIdx = UInt8(truncatingIfNeeded: (256 - Int(lTime)) & 0xFF)
                let lpx = UInt32(clamping: Int(lPosX) * 256 / 10)
                let lpy = UInt32(clamping: Int(lPosY) * 256 / 10)
                strip.wuPixel(Int(lpx), Int(lpy), strip.colorFromPalette(palIdx))
            }
            strip.blur2D(strip.runtime.intensity >> 3)
        }
        return FRAMETIME
    }

    static func effect_blobs(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let MAX_BLOBS = 8

        let blobSize = 22
        let dataSize = blobSize * MAX_BLOBS
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        let amount = min(MAX_BLOBS, Int(strip.runtime.intensity >> 5) + 1)

        func blobOff(_ i: Int) -> Int { i * blobSize }

        func readF(_ off: Int) -> Float {
            var v: Float = 0
            withUnsafeMutableBytes(of: &v) { ptr in
                for j in 0..<4 { ptr[j] = strip.runtime.data[off + j] }
            }
            return v
        }
        func writeF(_ off: Int, _ v: Float) {
            var vv = v
            withUnsafeBytes(of: &vv) { ptr in
                for j in 0..<4 { strip.runtime.data[off + j] = ptr[j] }
            }
        }

        let speedDiv = max(1, 256 - Int(strip.runtime.speed))

        if strip.runtime.aux0 != UInt16(cols) || strip.runtime.aux1 != UInt16(rows) {
            strip.runtime.aux0 = UInt16(cols)
            strip.runtime.aux1 = UInt16(rows)
            for i in 0..<MAX_BLOBS {
                let off = blobOff(i)
                let r = Float(WLEDEffects1D.random8(1, UInt8(clamping: cols > 8 ? cols / 4 : 2)))
                writeF(off + 0, Float(WLEDEffects1D.random8(0, UInt8(clamping: max(1, cols - 1)))))
                writeF(off + 4, Float(WLEDEffects1D.random8(0, UInt8(clamping: max(1, rows - 1)))))
                var sX = Float(WLEDEffects1D.random8(3, UInt8(clamping: cols))) / Float(speedDiv)
                var sY = Float(WLEDEffects1D.random8(3, UInt8(clamping: rows))) / Float(speedDiv)
                if sX == 0 { sX = 1 }
                if sY == 0 { sY = 1 }
                writeF(off + 8, sX)
                writeF(off + 12, sY)
                writeF(off + 16, r)
                strip.runtime.data[off + 20] = r < 1.0 ? 1 : 0
                strip.runtime.data[off + 21] = WLEDEffects1D.random8()
            }
        }

        strip.fadeToBlackBy(UInt8(clamping: Int(strip.runtime.custom2 >> 3) + 1))

        for i in 0..<amount {
            let off = blobOff(i)
            if strip.runtime.step < strip.now {
                strip.runtime.data[off + 21] = strip.runtime.data[off + 21] &+ 4
            }

            var r = readF(off + 16)
            var grow = strip.runtime.data[off + 20] != 0
            var sX = readF(off + 8)
            var sY = readF(off + 12)
            let maxSpeed = max(fabsf(sX), fabsf(sY))

            if grow {
                r += maxSpeed * 0.05
                if r >= min(Float(cols) / 4.0, 2.0) { grow = false }
            } else {
                r -= maxSpeed * 0.05
                if r < 1.0 { grow = true }
            }
            strip.runtime.data[off + 20] = grow ? 1 : 0
            writeF(off + 16, r)

            let c = strip.colorFromPalette(strip.runtime.data[off + 21])
            var bx = readF(off + 0)
            var by = readF(off + 4)

            if r > 1.0 {
                strip.fillCircle(Int(roundf(bx)), Int(roundf(by)), Int(roundf(r)), c)
            } else {
                strip.setPixelColorXY(Int(roundf(bx)), Int(roundf(by)), c)
            }

            if bx + r >= Float(cols - 1) {
                bx += sX * ((Float(cols - 1) - bx) / max(0.01, r) + 0.005)
            } else if bx - r <= 0 {
                bx += sX * (bx / max(0.01, r) + 0.005)
            } else {
                bx += sX
            }
            if by + r >= Float(rows - 1) {
                by += sY * ((Float(rows - 1) - by) / max(0.01, r) + 0.005)
            } else if by - r <= 0 {
                by += sY * (by / max(0.01, r) + 0.005)
            } else {
                by += sY
            }

            if bx < 0.01 {
                sX = Float(WLEDEffects1D.random8(3, UInt8(clamping: cols))) / Float(speedDiv)
                bx = 0.01
            } else if bx > Float(cols) - 1.01 {
                sX = -(Float(WLEDEffects1D.random8(3, UInt8(clamping: cols))) / Float(speedDiv))
                bx = Float(cols) - 1.01
            }
            if by < 0.01 {
                sY = Float(WLEDEffects1D.random8(3, UInt8(clamping: rows))) / Float(speedDiv)
                by = 0.01
            } else if by > Float(rows) - 1.01 {
                sY = -(Float(WLEDEffects1D.random8(3, UInt8(clamping: rows))) / Float(speedDiv))
                by = Float(rows) - 1.01
            }

            writeF(off + 0, bx)
            writeF(off + 4, by)
            writeF(off + 8, sX)
            writeF(off + 12, sY)
        }
        strip.blur2D(strip.runtime.custom1 >> 2)

        if strip.runtime.step < strip.now {
            strip.runtime.step = strip.now &+ 2000
        }
        return FRAMETIME
    }

    static func effect_soap(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        let noiseSize = cols * rows
        let dataSize = noiseSize + 12
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        let scale32_x = UInt32(160000 / max(1, cols))
        let scale32_y = UInt32(160000 / max(1, rows))
        let mov = UInt32(min(cols, rows)) &* UInt32(Int(strip.runtime.speed) + 2) / 2
        let smoothness = min(250, Int(strip.runtime.intensity))

        func readU32(_ off: Int) -> UInt32 {
            UInt32(strip.runtime.data[off]) | (UInt32(strip.runtime.data[off+1]) << 8) |
            (UInt32(strip.runtime.data[off+2]) << 16) | (UInt32(strip.runtime.data[off+3]) << 24)
        }
        func writeU32(_ off: Int, _ v: UInt32) {
            strip.runtime.data[off] = UInt8(v & 0xFF)
            strip.runtime.data[off+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[off+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[off+3] = UInt8((v >> 24) & 0xFF)
        }

        let nxOff = noiseSize
        let nyOff = noiseSize + 4
        let nzOff = noiseSize + 8

        if strip.runtime.call == 0 {
            writeU32(nxOff, UInt32(WLEDEffects1D.random16()))
            writeU32(nyOff, UInt32(WLEDEffects1D.random16()))
            writeU32(nzOff, UInt32(WLEDEffects1D.random16()))
        } else {
            writeU32(nxOff, readU32(nxOff) &+ mov)
            writeU32(nyOff, readU32(nyOff) &+ mov)
            writeU32(nzOff, readU32(nzOff) &+ mov)
        }

        let noise32_x = readU32(nxOff)
        let noise32_y = readU32(nyOff)
        let noise32_z = readU32(nzOff)

        for i in 0..<cols {
            let ioffset = Int32(scale32_x) * Int32(i - cols / 2)
            for j in 0..<rows {
                let joffset = Int32(scale32_y) * Int32(j - rows / 2)
                let data = inoise16(
                    Int(Int32(bitPattern: UInt32(noise32_x)) &+ Int32(ioffset)),
                    Int(Int32(bitPattern: UInt32(noise32_y)) &+ Int32(joffset)),
                    Int(noise32_z)
                ) >> 8
                let idx = j * cols + i
                let old = strip.runtime.data[idx]
                strip.runtime.data[idx] = UInt8(clamping:
                    (Int(old) * smoothness + Int(data) * (255 - smoothness)) / 255)
            }
        }

        if strip.runtime.call == 0 ||
           strip.runtime.aux0 != UInt16(cols) ||
           strip.runtime.aux1 != UInt16(rows) {
            strip.runtime.aux0 = UInt16(cols)
            strip.runtime.aux1 = UInt16(rows)
            for i in 0..<cols {
                for j in 0..<rows {
                    let nIdx = j * cols + i
                    let palIdx = UInt8(truncatingIfNeeded: (~strip.runtime.data[nIdx]) &* 3)
                    strip.setPixelColorXY(i, j, strip.colorFromPalette(palIdx))
                }
            }
        }

        let amplitudeH = cols >= 16 ? (cols - 8) / 8 : 1
        var ledsbuff = [WLEDColor](repeating: 0, count: max(cols, rows))
        for y in 0..<rows {
            let nIdx = y * cols
            let amount = (Int(strip.runtime.data[nIdx]) - 128) * 2 * amplitudeH
            let delta = abs(amount) >> 8
            let fraction = abs(amount) & 255
            for x in 0..<cols {
                let zD: Int
                let zF: Int
                if amount < 0 {
                    zD = x - delta
                    zF = zD - 1
                } else {
                    zD = x + delta
                    zF = zD + 1
                }
                var pixelA: WLEDColor = BLACK
                if zD >= 0 && zD < cols {
                    pixelA = strip.getPixelColorXY(zD, y)
                } else {
                    let safeIdx = min(max(abs(zD) % max(1, cols), 0), cols - 1)
                    let palIdx = UInt8(truncatingIfNeeded: (~strip.runtime.data[y * cols + safeIdx]) &* 3)
                    pixelA = strip.colorFromPalette(palIdx)
                }
                var pixelB: WLEDColor = BLACK
                if zF >= 0 && zF < cols {
                    pixelB = strip.getPixelColorXY(zF, y)
                } else {
                    let safeIdx = min(max(abs(zF) % max(1, cols), 0), cols - 1)
                    let palIdx = UInt8(truncatingIfNeeded: (~strip.runtime.data[y * cols + safeIdx]) &* 3)
                    pixelB = strip.colorFromPalette(palIdx)
                }
                let easeA = ease8InOutApprox(255 - fraction)
                let easeB = ease8InOutApprox(fraction)
                let blendA = WLEDEffects1D.nscale8Video(pixelA, easeA)
                let blendB = WLEDEffects1D.nscale8Video(pixelB, easeB)
                let rr = min(255, UInt16(WLEDEffects1D.r(blendA)) + UInt16(WLEDEffects1D.r(blendB)))
                let gg = min(255, UInt16(WLEDEffects1D.g(blendA)) + UInt16(WLEDEffects1D.g(blendB)))
                let bb = min(255, UInt16(WLEDEffects1D.b(blendA)) + UInt16(WLEDEffects1D.b(blendB)))
                ledsbuff[x] = WLEDEffects1D.rgbw32(UInt8(rr), UInt8(gg), UInt8(bb))
            }
            for x in 0..<cols { strip.setPixelColorXY(x, y, ledsbuff[x]) }
        }

        let amplitudeV = rows >= 16 ? (rows - 8) / 8 : 1
        for x in 0..<cols {
            let nIdx = x
            let amount = (Int(strip.runtime.data[nIdx]) - 128) * 2 * amplitudeV
            let delta = abs(amount) >> 8
            let fraction = abs(amount) & 255
            for y in 0..<rows {
                let zD: Int
                let zF: Int
                if amount < 0 {
                    zD = y - delta
                    zF = zD - 1
                } else {
                    zD = y + delta
                    zF = zD + 1
                }
                var pixelA: WLEDColor = BLACK
                if zD >= 0 && zD < rows {
                    pixelA = strip.getPixelColorXY(x, zD)
                } else {
                    let safeIdx = min(max(abs(zD) % max(1, rows), 0), rows - 1)
                    let palIdx = UInt8(truncatingIfNeeded: (~strip.runtime.data[safeIdx * cols + x]) &* 3)
                    pixelA = strip.colorFromPalette(palIdx)
                }
                var pixelB: WLEDColor = BLACK
                if zF >= 0 && zF < rows {
                    pixelB = strip.getPixelColorXY(x, zF)
                } else {
                    let safeIdx = min(max(abs(zF) % max(1, rows), 0), rows - 1)
                    let palIdx = UInt8(truncatingIfNeeded: (~strip.runtime.data[safeIdx * cols + x]) &* 3)
                    pixelB = strip.colorFromPalette(palIdx)
                }
                let easeA = ease8InOutApprox(255 - fraction)
                let easeB = ease8InOutApprox(fraction)
                let blendA = WLEDEffects1D.nscale8Video(pixelA, easeA)
                let blendB = WLEDEffects1D.nscale8Video(pixelB, easeB)
                let rr = min(255, UInt16(WLEDEffects1D.r(blendA)) + UInt16(WLEDEffects1D.r(blendB)))
                let gg = min(255, UInt16(WLEDEffects1D.g(blendA)) + UInt16(WLEDEffects1D.g(blendB)))
                let bb = min(255, UInt16(WLEDEffects1D.b(blendA)) + UInt16(WLEDEffects1D.b(blendB)))
                ledsbuff[y] = WLEDEffects1D.rgbw32(UInt8(rr), UInt8(gg), UInt8(bb))
            }
            for y in 0..<rows { strip.setPixelColorXY(x, y, ledsbuff[y]) }
        }

        return FRAMETIME
    }
}
