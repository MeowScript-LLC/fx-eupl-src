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

import Foundation

private extension WLEDEffectStrip {
    func addPixelColor(_ index: Int, _ color: WLEDColor) {
        guard index >= 0 && index < leds.count else { return }
        let existing = leds[index]
        let r = min(255, UInt32(WLEDEffects1D.r(existing)) + UInt32(WLEDEffects1D.r(color)))
        let g = min(255, UInt32(WLEDEffects1D.g(existing)) + UInt32(WLEDEffects1D.g(color)))
        let b = min(255, UInt32(WLEDEffects1D.b(existing)) + UInt32(WLEDEffects1D.b(color)))
        leds[index] = WLEDEffects1D.rgbw32(UInt8(r), UInt8(g), UInt8(b))
    }

    func fadePixelColor(_ index: Int, _ fade: UInt8) {
        guard index >= 0 && index < leds.count else { return }
        let c = leds[index]
        let f = UInt32(fade)
        let r = (UInt32(WLEDEffects1D.r(c)) * f) >> 8
        let g = (UInt32(WLEDEffects1D.g(c)) * f) >> 8
        let b = (UInt32(WLEDEffects1D.b(c)) * f) >> 8
        leds[index] = WLEDEffects1D.rgbw32(UInt8(r), UInt8(g), UInt8(b))
    }

    func move(_ dir: Int, _ delta: Int, wrap: Bool = false) {
        guard matrixWidth > 0 && matrixHeight > 0 && delta > 0 else { return }
        switch dir {
        case 0: moveX(delta, wrap: wrap)
        case 1: moveX(delta, wrap: wrap); moveY(delta, wrap: wrap)
        case 2: moveY(delta, wrap: wrap)
        case 3: moveX(-delta, wrap: wrap); moveY(delta, wrap: wrap)
        case 4: moveX(-delta, wrap: wrap)
        case 5: moveX(-delta, wrap: wrap); moveY(-delta, wrap: wrap)
        case 6: moveY(-delta, wrap: wrap)
        case 7: moveX(delta, wrap: wrap); moveY(-delta, wrap: wrap)
        default: break
        }
    }

    func moveX(_ delta: Int, wrap: Bool) {
        let vW = matrixWidth
        let vH = matrixHeight
        let absDelta = abs(delta)
        guard absDelta < vW else { return }
        var row = [WLEDColor](repeating: 0, count: vW)
        let newDelta: Int
        var start = 0
        var stop = vW
        if wrap {
            newDelta = ((delta % vW) + vW) % vW
        } else {
            if delta < 0 { start = absDelta }
            stop = vW - absDelta
            newDelta = delta > 0 ? delta : 0
        }
        for y in 0..<vH {
            for x in 0..<stop {
                var srcX = x + newDelta
                if wrap { srcX = srcX % vW }
                if srcX >= 0 && srcX < vW {
                    row[x] = leds[y * vW + srcX]
                }
            }
            for x in 0..<stop {
                let dx = x + start
                if dx >= 0 && dx < vW {
                    leds[y * vW + dx] = row[x]
                }
            }
        }
    }

    func moveY(_ delta: Int, wrap: Bool) {
        let vW = matrixWidth
        let vH = matrixHeight
        let absDelta = abs(delta)
        guard absDelta < vH else { return }
        var col = [WLEDColor](repeating: 0, count: vH)
        let newDelta: Int
        var start = 0
        var stop = vH
        if wrap {
            newDelta = ((delta % vH) + vH) % vH
        } else {
            if delta < 0 { start = absDelta }
            stop = vH - absDelta
            newDelta = delta > 0 ? delta : 0
        }
        for x in 0..<vW {
            for y in 0..<stop {
                var srcY = y + newDelta
                if wrap { srcY = srcY % vH }
                if srcY >= 0 && srcY < vH {
                    col[y] = leds[srcY * vW + x]
                }
            }
            for y in 0..<stop {
                let dy = y + start
                if dy >= 0 && dy < vH {
                    leds[dy * vW + x] = col[y]
                }
            }
        }
    }
}

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

private func inoise8(_ x: Int, _ y: Int = 0, _ z: Int = 0) -> UInt8 {
    WLEDEffects1D.inoise8(x, y)
}

private func inoise16(_ x: Int, _ y: Int = 0, _ z: Int = 0) -> Int {
    Int(WLEDEffects1D.inoise16(UInt32(bitPattern: Int32(x)), UInt32(bitPattern: Int32(y)), UInt32(bitPattern: Int32(z))))
}

private func cos8(_ x: Int) -> UInt8 {
    WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: x &+ 64))
}

private func abs8(_ x: Int) -> UInt8 {
    UInt8(clamping: abs(x))
}

private func heatColor(_ temperature: UInt8) -> WLEDColor {
    let t192 = UInt8(clamping: Int(temperature) > 0 ? (Int(temperature) * 191 / 255 + 1) : 0)
    var r: UInt8 = 0, g: UInt8 = 0, b: UInt8 = 0
    if t192 > 0 && t192 <= 64 {
        r = t192 &* 4; g = 0; b = 0
    } else if t192 <= 128 {
        r = 255; g = (t192 &- 64) &* 4; b = 0
    } else {
        r = 255; g = 255; b = (t192 &- 128) &* 4
    }
    return WLEDEffects1D.rgbw32(r, g, b)
}

private func perlin8(_ x: Int, _ y: Int = 0, _ z: Int = 0) -> UInt8 {
    inoise8(x, y, z)
}

private func perlin16(_ x: Int, _ y: Int = 0) -> UInt16 {
    UInt16(clamping: inoise16(x, y))
}

private let noiseFirePalette: [(UInt8, UInt8, UInt8)] = [
    (2, 0, 0), (4, 0, 0), (8, 0, 0), (8, 0, 0),
    (16, 0, 0), (255, 0, 0), (255, 0, 0), (255, 0, 0),
    (255, 140, 0), (255, 140, 0), (255, 165, 0), (255, 165, 0),
    (255, 255, 0), (255, 165, 0), (255, 255, 0), (255, 255, 0)
]

private func colorFromPalette16NF(_ palette: [(UInt8, UInt8, UInt8)], _ index: Int, _ bri: UInt8 = 255) -> WLEDColor {
    guard !palette.isEmpty else { return 0 }
    let idx = Int(index & 0xFF) * (palette.count - 1) / 255
    let clamped = min(idx, palette.count - 1)
    let (r, g, b) = palette[max(0, clamped)]
    let br = UInt16(bri)
    return (UInt32(UInt16(r) * br / 255) << 16) | (UInt32(UInt16(g) * br / 255) << 8) | UInt32(UInt16(b) * br / 255)
}

extension WLEDEffects2D {

    static let effectsExt1: [Int: WLEDEffectFunc] = [
        118: effect_spaceships,
        122: effect_scrollingText,
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
        147: effect_perlinMove,
        148: effect_ripplePeak,
        151: effect_pacman,
        155: effect_freqmap,
        156: effect_gravcenter,
        157: effect_gravcentric,
        158: effect_gravfreq,
        159: effect_djlight,
        160: effect_funkyPlank,
        161: effect_shimmer,
        162: effect_pulser,
        163: effect_blurz,
        165: effect_waverly,
        166: effect_sunRadiation,
        179: effect_flowStripe,
    ]

    static func effect_spaceships(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let tb = now >> 12
        if tb > strip.runtime.step {
            var dir = Int(strip.runtime.aux0) + 1
            dir += Int(WLEDEffects1D.random8(3)) - 1
            if dir > 7 { strip.runtime.aux0 = 0 }
            else if dir < 0 { strip.runtime.aux0 = 7 }
            else { strip.runtime.aux0 = UInt16(dir) }
            strip.runtime.step = tb &+ UInt32(WLEDEffects1D.random8(4))
        }

        strip.fadeToBlackBy(UInt8(clamping: WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 248, 16)))
        strip.move(Int(strip.runtime.aux0), 1)

        for i in 0..<8 {
            let x = Int(beatsin8(12 + i, 2, cols - 3, now))
            let y = Int(beatsin8(15 + i, 2, rows - 3, now))
            let palIdx = beatsin8(12 + i, 0, 255, now)
            let color = strip.colorFromPalette(palIdx)
            strip.addPixelColorXY(x, y, color)
            if cols > 24 || rows > 24 {
                strip.addPixelColorXY(x + 1, y, color)
                strip.addPixelColorXY(x - 1, y, color)
                strip.addPixelColorXY(x, y + 1, color)
                strip.addPixelColorXY(x, y - 1, color)
            }
        }
        strip.blur(strip.runtime.intensity >> 3)
        return FRAMETIME
    }

    private static let font5x7: [[UInt8]] = [
        [0,0,0,0,0],[0,0,95,0,0],[0,7,0,7,0],[20,127,20,127,20],[36,42,127,42,18],[35,19,8,100,98],
        [54,73,85,34,80],[0,5,3,0,0],[0,28,34,65,0],[0,65,34,28,0],[20,8,62,8,20],[8,8,62,8,8],
        [0,80,48,0,0],[8,8,8,8,8],[0,96,96,0,0],[32,16,8,4,2],[62,81,73,69,62],[0,66,127,64,0],
        [66,97,81,73,70],[33,65,69,75,49],[24,20,18,127,16],[39,69,69,69,57],[60,74,73,73,48],
        [1,113,9,5,3],[54,73,73,73,54],[6,73,73,41,30],[0,54,54,0,0],[0,86,54,0,0],
        [8,20,34,65,0],[20,20,20,20,20],[0,65,34,20,8],[2,1,81,9,6],[50,73,121,65,62],
        [126,17,17,17,126],[127,73,73,73,54],[62,65,65,65,34],[127,65,65,34,28],[127,73,73,73,65],
        [127,9,9,9,1],[62,65,73,73,122],[127,8,8,8,127],[0,65,127,65,0],[32,64,65,63,1],
        [127,8,20,34,65],[127,64,64,64,64],[127,2,12,2,127],[127,4,8,16,127],[62,65,65,65,62],
        [127,9,9,9,6],[62,65,81,33,94],[127,9,25,41,70],[70,73,73,73,49],[1,1,127,1,1],
        [63,64,64,64,63],[31,32,64,32,31],[63,64,48,64,63],[99,20,8,20,99],[7,8,112,8,7],
        [97,81,73,69,67],[0,127,65,65,0],[2,4,8,16,32],[0,65,65,127,0],[4,2,1,2,4],
        [64,64,64,64,64],[0,1,2,4,0],[32,84,84,84,120],[127,72,68,68,56],[56,68,68,68,32],
        [56,68,68,72,127],[56,84,84,84,24],[8,126,9,1,2],[12,82,82,82,62],[127,8,4,4,120],
        [0,68,125,64,0],[32,64,68,61,0],[127,16,40,68,0],[0,65,127,64,0],[124,4,24,4,120],
        [124,8,4,4,120],[56,68,68,68,56],[124,20,20,20,8],[8,20,20,24,124],[124,8,4,4,8],
        [72,84,84,84,36],[4,63,68,64,32],[60,64,64,32,124],[28,32,64,32,28],[60,64,48,64,60],
        [68,40,16,40,68],[12,80,80,80,60],[68,100,84,76,68],[0,8,54,65,0],[0,0,127,0,0],
        [0,65,54,8,0],[16,8,8,16,8]
    ]

    static func effect_scrollingText(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now
        let text = WLEDEffects1D.wledDateTimeText()
        let charW = 6
        let totalW = text.count * charW

        let scrollSpeed = UInt32(WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 150, 10))
        if now &- strip.runtime.step >= scrollSpeed {
            strip.runtime.step = now
            strip.runtime.aux0 = strip.runtime.aux0 &+ 1
            if strip.runtime.aux0 >= UInt16(totalW + cols) { strip.runtime.aux0 = 0 }
        }

        strip.fill(WLEDEffects1D.BLACK)
        let scroll = Int(strip.runtime.aux0)
        let yOff = max(0, (rows - 7) / 2)

        for (ci, ch) in text.enumerated() {
            let idx = max(0, min(94, Int(ch.asciiValue ?? 32) - 32))
            let glyph = font5x7[idx]
            for col in 0..<5 {
                let sx = ci * charW + col - scroll + cols
                guard sx >= 0 && sx < cols else { continue }
                let bits = glyph[col]
                for row in 0..<7 {
                    let sy = yOff + row
                    guard sy < rows else { continue }
                    if (bits >> row) & 1 != 0 {
                        let palIdx = UInt8(truncatingIfNeeded: (ci * 37 + col * 7 + Int(now / 100)) & 0xFF)
                        strip.setPixelColorXY(sx, sy, strip.colorFromPalette(palIdx))
                    }
                }
            }
        }
        return FRAMETIME
    }

    static func effect_wavingCell(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight

        let t = (strip.now &* UInt32(strip.runtime.speed &+ 1)) >> 3
        let aX = UInt32(strip.runtime.custom1) / 16 + 9
        let aY = UInt32(strip.runtime.custom2) / 16 + 1
        let aZ = UInt32(strip.runtime.custom3) + 1

        for x in 0..<cols {
            for y in 0..<rows {
                let inner = WLEDEffects1D.sin8(UInt8(truncatingIfNeeded:
                    (Int(UInt32(y) << 8 &+ t) * Int(aY)) >> 8))
                let wave = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded:
                    (Int(UInt32(x) * aX) + Int(inner)) & 0xFF))) +
                    Int(cos8(Int(UInt32(y) * aZ)))
                let flowShift = strip.runtime.check2 ? 3 : 0
                let colorIndex = UInt8(truncatingIfNeeded: (wave + Int(t >> UInt32(8 - flowShift))) & 0xFF)
                strip.setPixelColorXY(x, y, strip.colorFromPalette(colorIndex))
            }
        }
        strip.blur(strip.runtime.intensity)
        return FRAMETIME
    }

    static func effect_pixels(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let volumeSmth = Float(beatsin8(10, 20, 200, now)) * Float(strip.runtime.intensity) / 255.0

        strip.fadeOut(64 + (strip.runtime.speed >> 1))

        let numPixels = Int(strip.runtime.intensity) / 8
        for i in 0..<numPixels {
            let segLoc = Int(WLEDEffects1D.random16(UInt16(segLen)))
            let palIdx = UInt8(truncatingIfNeeded: (Int(now / 4) + i * 4) & 0xFF)
            let bri = UInt8(clamping: Int(volumeSmth))
            strip.setPixelColor(segLoc, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), bri))
        }
        return FRAMETIME
    }

    static func effect_pixelwave(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let volumeRaw = Int(beatsin8(12, 10, 180, now))

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500 + 1) % 16)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let pixBri = UInt8(clamping: volumeRaw * Int(strip.runtime.intensity) / 64)
            let mid = segLen / 2
            let palIdx = UInt8(truncatingIfNeeded: now & 0xFF)
            strip.setPixelColor(mid, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), pixBri))

            for i in stride(from: segLen - 1, through: mid + 1, by: -1) {
                strip.setPixelColor(i, strip.getPixelColor(i - 1))
            }
            for i in 0..<mid {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
        }
        return FRAMETIME
    }

    static func effect_juggles(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        strip.fadeOut(224)

        let volumeSmth = Float(beatsin8(8, 40, 200, now))
        let sampleAgc = UInt8(clamping: Int(volumeSmth))

        let numBalls = Int(strip.runtime.intensity) / 32 + 1
        for i in 0..<numBalls {
            let pos = Int(beatsin16(Int(strip.runtime.speed) / 4 + i * 2, 0, segLen - 1, now))
            let palIdx = UInt8(truncatingIfNeeded: (Int(now / 4) + i * 2) & 0xFF)
            let c = WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), sampleAgc)
            strip.setPixelColor(pos, c)
        }
        return FRAMETIME
    }

    static func effect_matripix(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let volumeRaw = Int(beatsin8(14, 10, 160, now))

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500) % 16)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let pixBri = UInt8(clamping: volumeRaw * Int(strip.runtime.intensity) / 64)
            let k = segLen - 1

            for i in 0..<k {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
            let palIdx = UInt8(truncatingIfNeeded: now & 0xFF)
            strip.setPixelColor(k, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), pixBri))
        }
        return FRAMETIME
    }

    static func effect_gravimeter(_ strip: WLEDEffectStrip) -> UInt16 {
        return gravcenterBase(strip, mode: 2)
    }

    static func effect_plasmoid(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if !strip.runtime.allocateData(4) { return WLEDEffects1D.effect_solid(strip) }

        let volumeSmth = Float(beatsin8(9, 30, 200, now))

        strip.fadeToBlackBy(32)

        var thisphase = Int16(bitPattern: UInt16(strip.runtime.data[0]) | (UInt16(strip.runtime.data[1]) << 8))
        var thatphase = Int16(bitPattern: UInt16(strip.runtime.data[2]) | (UInt16(strip.runtime.data[3]) << 8))

        thisphase &+= Int16(bitPattern: UInt16(beatsin8(6, 252, 4, now)))
        thatphase &+= Int16(bitPattern: UInt16(beatsin8(7, 252, 4, now)))

        for i in 0..<segLen {
            let val1 = WLEDEffects1D.cubicwave8(UInt8(truncatingIfNeeded: ((i * (1 + 3 * Int(strip.runtime.speed) / 32)) + Int(thisphase)) & 0xFF))
            let val2 = cos8((i * (97 + 5 * Int(strip.runtime.speed) / 32) + Int(thatphase)) & 0xFF)
            var thisbright = UInt8(clamping: Int(val1) / 2 + Int(val2) / 2)

            let colorIndex = thisbright
            if volumeSmth * Float(strip.runtime.intensity) / 64.0 < Float(thisbright) {
                thisbright = 0
            }

            let c = WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(colorIndex), thisbright)
            strip.addPixelColor(i, c)
        }

        strip.runtime.data[0] = UInt8(truncatingIfNeeded: thisphase)
        strip.runtime.data[1] = UInt8(truncatingIfNeeded: thisphase >> 8)
        strip.runtime.data[2] = UInt8(truncatingIfNeeded: thatphase)
        strip.runtime.data[3] = UInt8(truncatingIfNeeded: thatphase >> 8)

        return FRAMETIME
    }

    static func effect_puddles(_ strip: WLEDEffectStrip) -> UInt16 {
        return puddlesBase(strip, peakDetect: false)
    }

    static func effect_midnoise(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let volumeSmth = Float(beatsin8(7, 30, 180, now))

        strip.fadeOut(strip.runtime.speed)
        strip.fadeOut(strip.runtime.speed)

        var tmpSound2 = volumeSmth * Float(strip.runtime.intensity) / 256.0
        tmpSound2 *= Float(strip.runtime.intensity) / 128.0

        let maxLen = min(segLen / 2, Int(tmpSound2))
        let halfSeg = segLen / 2

        for i in max(0, halfSeg - maxLen)..<min(segLen, halfSeg + maxLen) {
            let index = perlin8(Int(Float(i) * volumeSmth) + Int(strip.runtime.aux0), Int(strip.runtime.aux1) + Int(Float(i) * volumeSmth))
            strip.setPixelColor(i, strip.colorFromPalette(index))
        }

        strip.runtime.aux0 = strip.runtime.aux0 &+ UInt16(beatsin8(5, 0, 10, now))
        strip.runtime.aux1 = strip.runtime.aux1 &+ UInt16(beatsin8(4, 0, 10, now))
        return FRAMETIME
    }

    static func effect_noisemeter(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        let volumeSmth = Float(beatsin8(9, 20, 180, now))
        let volumeRaw = Int(beatsin8(11, 10, 200, now))

        let fadeRate = UInt8(clamping: WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 200, 254))
        strip.fadeOut(fadeRate)

        let tmpSound2 = Float(volumeRaw) * 2.0 * Float(strip.runtime.intensity) / 255.0
        let maxLen = min(segLen, max(0, Int(tmpSound2)))

        for i in 0..<maxLen {
            let index = perlin8(Int(Float(i) * volumeSmth) + Int(strip.runtime.aux0),
                                Int(strip.runtime.aux1) + Int(Float(i) * volumeSmth))
            strip.setPixelColor(i, strip.colorFromPalette(index))
        }

        strip.runtime.aux0 = strip.runtime.aux0 &+ UInt16(beatsin8(5, 0, 10, now))
        strip.runtime.aux1 = strip.runtime.aux1 &+ UInt16(beatsin8(4, 0, 10, now))
        return FRAMETIME
    }

    static func effect_freqwave(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let simFreq = Float(beatsin8(3, 80, 255, now))
        let volumeSmth = Float(beatsin8(7, 30, 200, now))

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500) % 16)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let sensitivity = max(1, WLEDEffects1D.map(Int(strip.runtime.custom3), 0, 31, 1, 10))
            let pixVal = min(255.0, volumeSmth * Float(strip.runtime.intensity) / 256.0 * Float(sensitivity))
            let intensity = pixVal / 255.0

            let hue = UInt8(clamping: Int(simFreq))
            let bri = UInt8(clamping: Int(255.0 * intensity))
            let color = WLEDEffects1D.hsvToRgb(hue, 240, bri)

            let mid = segLen / 2
            strip.setPixelColor(mid, color)

            for i in stride(from: segLen - 1, through: mid + 1, by: -1) {
                strip.setPixelColor(i, strip.getPixelColor(i - 1))
            }
            for i in 0..<mid {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
        }
        return FRAMETIME
    }

    static func effect_freqmatrix(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let simFreq = Float(beatsin8(4, 80, 255, now))
        let volumeSmth = Float(beatsin8(8, 20, 200, now))

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500) % 16)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let sensitivity = max(1, WLEDEffects1D.map(Int(strip.runtime.custom3), 0, 31, 1, 10))
            let pixVal = min(255, Int(volumeSmth * Float(strip.runtime.intensity) * Float(sensitivity) / 256.0))
            let intensity = Float(pixVal) / 255.0

            let hue = UInt8(clamping: Int(simFreq))
            let bri = UInt8(clamping: Int(255.0 * intensity))
            let color: WLEDColor = bri > 10 ? WLEDEffects1D.hsvToRgb(hue, 240, bri) : BLACK

            strip.setPixelColor(0, color)
            for i in stride(from: segLen - 1, through: 1, by: -1) {
                strip.setPixelColor(i, strip.getPixelColor(i - 1))
            }
        }
        return FRAMETIME
    }

    static func effect_2DGEQ(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let NUM_BANDS = WLEDEffects1D.map(Int(strip.runtime.custom1), 0, 255, 1, 16)

        let dataSize = cols * 2
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        if strip.runtime.call == 0 {
            for i in 0..<cols {
                strip.runtime.data[i * 2] = 0
                strip.runtime.data[i * 2 + 1] = 0
            }
        }

        var fftSim = [UInt8](repeating: 0, count: 16)
        for band in 0..<16 {
            let bpm = 3 + band * 2
            let phase = band * 37
            fftSim[band] = beatsin8(bpm, 0, 255, now, phase)
        }

        var rippleTime = false
        if now &- strip.runtime.step >= UInt32(256 - Int(strip.runtime.intensity)) {
            strip.runtime.step = now
            rippleTime = true
        }

        let fadeoutDelay = max(1, (256 - Int(strip.runtime.speed)) / 64)
        if fadeoutDelay <= 1 || (Int(strip.runtime.call) % fadeoutDelay == 0) {
            strip.fadeToBlackBy(strip.runtime.speed)
        }

        for x in 0..<cols {
            var band = WLEDEffects1D.map(x, 0, cols, 0, NUM_BANDS)
            band = min(max(band, 0), 15)
            let colorIndex = band * 17

            let barHeight = WLEDEffects1D.map(Int(fftSim[band]), 0, 255, 0, rows)
            let prevH = Int(strip.runtime.data[x * 2]) | (Int(strip.runtime.data[x * 2 + 1]) << 8)
            var prevBarHeight = prevH
            if barHeight > prevBarHeight { prevBarHeight = barHeight }

            var ledColor: WLEDColor = BLACK
            for y in 0..<barHeight {
                var ci = colorIndex
                if strip.runtime.check1 {
                    ci = WLEDEffects1D.map(y, 0, max(1, rows - 1), 0, 255)
                }
                ledColor = strip.colorFromPalette(UInt8(clamping: ci))
                strip.setPixelColorXY(x, rows - 1 - y, ledColor)
            }

            if prevBarHeight > 0 {
                let peakColor = strip.runtime.colors[2] != BLACK ? strip.runtime.colors[2] : ledColor
                strip.setPixelColorXY(x, rows - prevBarHeight, peakColor)
            }

            if rippleTime && prevBarHeight > 0 { prevBarHeight -= 1 }
            strip.runtime.data[x * 2] = UInt8(prevBarHeight & 0xFF)
            strip.runtime.data[x * 2 + 1] = UInt8((prevBarHeight >> 8) & 0xFF)
        }
        return FRAMETIME
    }

    static func effect_waterfall(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let simFreq = Float(beatsin8(5, 80, 220, now))
        let magnitude = Float(beatsin8(9, 20, 180, now))

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500 + 1) % 16)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let pixCol = UInt8(clamping: Int((log10f(max(1, simFreq)) - 2.26) * 150))
            let k = segLen - 1
            let bri = UInt8(clamping: Int(magnitude))
            let palIdx = UInt8(truncatingIfNeeded: (Int(pixCol) + Int(strip.runtime.intensity)) & 0xFF)

            strip.setPixelColor(k, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), bri))

            for i in 0..<k {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
        }
        return FRAMETIME
    }

    static func effect_freqpixels(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        let simFreq = Float(beatsin8(4, 80, 220, now))
        let magnitude = Float(beatsin8(10, 20, 160, now))

        let fadeRate = UInt8(clamping: WLEDEffects1D.map(Int(strip.runtime.speed) * Int(strip.runtime.speed), 0, 65025, 1, 255))
        let fadeoutDelay = max(1, (256 - Int(strip.runtime.speed)) / 64)
        if fadeoutDelay <= 1 || (Int(strip.runtime.call) % fadeoutDelay == 0) {
            strip.fadeOut(fadeRate)
        }

        let pixCol = UInt8(clamping: Int((log10f(max(1, simFreq)) - 1.78) * 255.0 / (3.71 - 1.78)))
        let numPixels = Int(strip.runtime.intensity) / 32 + 1
        for _ in 0..<numPixels {
            let locn = Int(WLEDEffects1D.random16(UInt16(segLen)))
            let palIdx = UInt8(truncatingIfNeeded: (Int(strip.runtime.intensity) + Int(pixCol)) & 0xFF)
            strip.setPixelColor(locn, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), UInt8(clamping: Int(magnitude))))
        }
        return FRAMETIME
    }

    static func effect_binmap(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        strip.fadeToBlackBy(UInt8(clamping: 224 + Int(strip.runtime.speed) / 8))

        for band in 0..<16 {
            let bpm = 3 + band * 2
            let val = beatsin8(bpm, 0, 255, now, band * 41)
            let pos = WLEDEffects1D.map(band, 0, 15, 0, max(0, segLen - 1))
            let palIdx = UInt8(clamping: band * 16)
            let bri = val
            strip.setPixelColor(pos, strip.colorFromPalette(palIdx, bri))
        }
        return FRAMETIME
    }

    static func effect_noisefire(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let volumeSmth = Float(beatsin8(8, 40, 220, now))

        for i in 0..<segLen {
            let index = perlin8(i * Int(strip.runtime.speed) / 64,
                                Int(now) * Int(strip.runtime.speed) / 64 * segLen / 255)
            var scaled = (255 - i * 256 / max(1, segLen)) * Int(index) / max(1, 256 - Int(strip.runtime.intensity))
            if scaled > 255 { scaled = 255 }
            if scaled < 0 { scaled = 0 }
            let bri = UInt8(clamping: Int(volumeSmth * 2))
            strip.setPixelColor(i, colorFromPalette16NF(noiseFirePalette, scaled, bri))
        }
        return FRAMETIME
    }

    static func effect_puddlepeak(_ strip: WLEDEffectStrip) -> UInt16 {
        return puddlesBase(strip, peakDetect: true)
    }

    static func effect_noisemove(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        let fadeoutDelay = max(1, (256 - Int(strip.runtime.speed)) / 96)
        if fadeoutDelay <= 1 || (Int(strip.runtime.call) % fadeoutDelay == 0) {
            strip.fadeToBlackBy(UInt8(clamping: 4 + Int(strip.runtime.speed) / 4))
        }

        let numBins = WLEDEffects1D.map(Int(strip.runtime.intensity), 0, 255, 0, 16)
        for i in 0..<numBins {
            let locn16 = perlin16(Int(now) * Int(strip.runtime.speed) + i * 50000,
                                  Int(now) * Int(strip.runtime.speed))
            let locn = WLEDEffects1D.map(Int(locn16), 7500, 58000, 0, max(0, segLen - 1))
            let clampedLocn = min(max(locn, 0), segLen - 1)
            let fftVal = beatsin8(5 + i * 3, 0, 255, now, i * 47)
            let palIdx = UInt8(clamping: i * 64)
            let bri = UInt8(clamping: Int(fftVal) * 4)
            strip.setPixelColor(clampedLocn, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), min(bri, 255)))
        }
        return FRAMETIME
    }

    static func effect_perlinMove(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        strip.fadeOut(255 &- strip.runtime.custom1)

        let numPixels = Int(strip.runtime.intensity) / 16 + 1
        for i in 0..<numPixels {
            let locn16 = perlin16(Int(now) * 128 / max(1, 260 - Int(strip.runtime.speed)) + i * 15000,
                                  Int(now) * 128 / max(1, 260 - Int(strip.runtime.speed)))
            let pixloc = WLEDEffects1D.map(Int(locn16), 50 * 256, 192 * 256, 0, segLen - 1)
            let clampedLoc = min(max(pixloc, 0), segLen - 1)
            strip.setPixelColor(clampedLoc, strip.colorFromPalette(UInt8(truncatingIfNeeded: clampedLoc & 0xFF)))
        }
        return FRAMETIME
    }

    static func effect_ripplePeak(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let now = strip.now

        let maxRipples = 16
        let rippleSize = 4
        let dataSize = rippleSize * maxRipples
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        let samplePeak = (now / 300) != ((now &- UInt32(FRAMETIME)) / 300)

        strip.fadeOut(240)
        strip.fadeOut(240)

        let numRipples = Int(strip.runtime.intensity) / 16
        for i in 0..<numRipples {
            let off = i * rippleSize
            let pos = Int(strip.runtime.data[off]) | (Int(strip.runtime.data[off + 1]) << 8)
            let color = strip.runtime.data[off + 2]
            var state = strip.runtime.data[off + 3]

            if samplePeak && state == 254 { state = 255 }

            switch state {
            case 254: break
            case 255:
                let newPos = Int(WLEDEffects1D.random16(UInt16(segLen)))
                strip.runtime.data[off] = UInt8(newPos & 0xFF)
                strip.runtime.data[off + 1] = UInt8((newPos >> 8) & 0xFF)
                strip.runtime.data[off + 2] = WLEDEffects1D.random8()
                strip.runtime.data[off + 3] = 0
            case 16:
                strip.runtime.data[off + 3] = 254
            case 0:
                strip.setPixelColor(pos, strip.colorFromPalette(color))
                strip.runtime.data[off + 3] = 1
            default:
                let stateInt = Int(state)
                let bri = UInt8(clamping: min(255, 2 * 255 / max(1, stateInt)))
                let c = WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(color), bri)
                let posPlus = (pos + stateInt + segLen) % segLen
                let posMinus = (pos - stateInt + segLen) % segLen
                strip.setPixelColor(posPlus, c)
                strip.setPixelColor(posMinus, c)
                strip.runtime.data[off + 3] = state &+ 1
            }
        }
        return FRAMETIME
    }

    static func effect_pacman(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 16 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let YELLOW: WLEDColor = 0x00FFFF00
        let RED: WLEDColor = 0x00FF0000
        let BLUE: WLEDColor = 0x000000FF
        let CYAN: WLEDColor = 0x0000FFFF
        let PURPLEISH: WLEDColor = 0x00B000B0
        let ORANGEISH: WLEDColor = 0x00FF8800
        let WHITEISH: WLEDColor = 0x00999999
        let ORANGEYELLOW: WLEDColor = 0x00FFCC00
        let ghostColors: [WLEDColor] = [RED, PURPLEISH, CYAN, ORANGEISH]

        let numGhosts = WLEDEffects1D.map(Int(strip.runtime.custom3), 0, 31, 2, 8)
        let numPowerDots = min(segLen / 10, WLEDEffects1D.map(Int(strip.runtime.intensity), 0, 255, 1, max(1, segLen / 10)))

        let charSize = 11
        let totalChars = 1 + numGhosts + numPowerDots
        let dataSize = charSize * totalChars
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        func charOff(_ i: Int) -> Int { i * charSize }
        func readPos(_ i: Int) -> Int {
            let o = charOff(i)
            return Int(Int16(bitPattern: UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o + 1]) << 8)))
        }
        func writePos(_ i: Int, _ v: Int) {
            let o = charOff(i)
            let sv = Int16(clamping: v)
            strip.runtime.data[o] = UInt8(truncatingIfNeeded: sv)
            strip.runtime.data[o + 1] = UInt8(truncatingIfNeeded: sv >> 8)
        }
        func readTopPos(_ i: Int) -> Int {
            let o = charOff(i) + 2
            return Int(Int16(bitPattern: UInt16(strip.runtime.data[o]) | (UInt16(strip.runtime.data[o + 1]) << 8)))
        }
        func writeTopPos(_ i: Int, _ v: Int) {
            let o = charOff(i) + 2
            let sv = Int16(clamping: v)
            strip.runtime.data[o] = UInt8(truncatingIfNeeded: sv)
            strip.runtime.data[o + 1] = UInt8(truncatingIfNeeded: sv >> 8)
        }
        func direction(_ i: Int) -> Bool { strip.runtime.data[charOff(i) + 4] != 0 }
        func setDirection(_ i: Int, _ v: Bool) { strip.runtime.data[charOff(i) + 4] = v ? 1 : 0 }
        func isBlue(_ i: Int) -> Bool { strip.runtime.data[charOff(i) + 5] != 0 }
        func setBlue(_ i: Int, _ v: Bool) { strip.runtime.data[charOff(i) + 5] = v ? 1 : 0 }
        func readColor(_ i: Int) -> WLEDColor {
            let o = charOff(i) + 6
            return UInt32(strip.runtime.data[o]) | (UInt32(strip.runtime.data[o+1]) << 8) |
                   (UInt32(strip.runtime.data[o+2]) << 16) | (UInt32(strip.runtime.data[o+3]) << 24)
        }
        func writeColor(_ i: Int, _ c: WLEDColor) {
            let o = charOff(i) + 6
            strip.runtime.data[o] = UInt8(c & 0xFF)
            strip.runtime.data[o+1] = UInt8((c >> 8) & 0xFF)
            strip.runtime.data[o+2] = UInt8((c >> 16) & 0xFF)
            strip.runtime.data[o+3] = UInt8((c >> 24) & 0xFF)
        }
        func isEaten(_ i: Int) -> Bool { strip.runtime.data[charOff(i) + 10] != 0 }
        func setEaten(_ i: Int, _ v: Bool) { strip.runtime.data[charOff(i) + 10] = v ? 1 : 0 }

        if strip.runtime.call == 0 {
            writeColor(0, YELLOW); writePos(0, 0); writeTopPos(0, 0); setDirection(0, true); setBlue(0, false)
            for i in 1...numGhosts {
                writeColor(i, ghostColors[(i - 1) % 4])
                writePos(i, -2 * (i + 1))
                setDirection(i, true); setBlue(i, false)
            }
            for i in 0..<numPowerDots {
                let ci = i + numGhosts + 1
                writeColor(ci, ORANGEYELLOW); setEaten(ci, false)
                let dotPos = i == 0 ? segLen - 1 : 10 + i * (segLen - 10) / max(1, numPowerDots)
                writePos(ci, dotPos)
            }
        }

        if now > strip.runtime.step { strip.runtime.step = now; strip.runtime.aux1 = strip.runtime.aux1 &+ 1 }

        if !strip.runtime.check2 { strip.fill(BLACK) }

        if strip.runtime.check1 {
            let step = strip.runtime.check3 ? 1 : 2
            var i = segLen - 1
            while i > readTopPos(0) {
                strip.setPixelColor(i, WHITEISH)
                i -= step
            }
        }

        let blink = (strip.runtime.aux1 % 10) < 5
        for i in 0..<numPowerDots {
            let ci = i + numGhosts + 1
            if !isEaten(ci) {
                let pos = readPos(ci)
                if pos >= 0 && pos < segLen {
                    strip.setPixelColor(pos, blink ? ORANGEYELLOW : BLACK)
                }
            }
        }

        let pacPos = readPos(0)
        for j in 0..<numPowerDots {
            let ci = j + numGhosts + 1
            if pacPos == readPos(ci) && !isEaten(ci) {
                for k in 0...numGhosts { setDirection(k, false) }
                for k in 1...numGhosts { writeColor(k, BLUE); setBlue(k, true) }
                setEaten(ci, true)
                break
            }
        }

        if isBlue(1) && readPos(0) <= 0 {
            for k in 0...numGhosts { setDirection(k, true) }
            for k in 1...numGhosts {
                writeColor(k, ghostColors[(k - 1) % 4]); setBlue(k, false)
            }
            if isEaten(numGhosts + 1) {
                for k in 0..<numPowerDots { setEaten(k + numGhosts + 1, false) }
                writeTopPos(0, 0)
            }
        }

        let moveRate = max(1, WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 15, 1))
        if Int(strip.runtime.aux1) % moveRate == 0 {
            writePos(0, readPos(0) + (direction(0) ? 1 : -1))
            for k in 1...numGhosts {
                writePos(k, readPos(k) + (direction(k) ? 1 : -1))
            }
        }

        let pp = readPos(0)
        if pp >= 0 && pp < segLen { strip.setPixelColor(pp, readColor(0)) }
        if readTopPos(0) < pp { writeTopPos(0, pp) }

        for k in 1...numGhosts {
            let gp = readPos(k)
            if gp >= 0 && gp < segLen { strip.setPixelColor(gp, readColor(k)) }
        }

        strip.blur(strip.runtime.custom2 >> 1)
        return FRAMETIME
    }

    static func effect_freqmap(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let simFreq = Float(beatsin8(4, 80, 255, now))
        let magnitude = Float(beatsin8(10, 20, 200, now))

        if strip.runtime.call == 0 { strip.fill(BLACK) }
        let fadeoutDelay = max(1, (256 - Int(strip.runtime.speed)) / 32)
        if fadeoutDelay <= 1 || (Int(strip.runtime.call) % fadeoutDelay == 0) {
            strip.fadeOut(strip.runtime.speed)
        }

        let logFreq = log10f(max(1, simFreq))
        let locn = Int((logFreq - 1.78) * Float(segLen) / (3.71 - 1.78))
        let clampedLocn = min(max(locn, 0), segLen - 1)

        let pixCol = UInt8(clamping: Int((logFreq - 1.78) * 255.0 / (3.71 - 1.78)))
        let bri = UInt8(clamping: Int(magnitude))
        let palIdx = UInt8(truncatingIfNeeded: (Int(strip.runtime.intensity) + Int(pixCol)) & 0xFF)
        strip.setPixelColor(clampedLocn, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), bri))
        return FRAMETIME
    }

    static func effect_gravcenter(_ strip: WLEDEffectStrip) -> UInt16 {
        return gravcenterBase(strip, mode: 0)
    }

    static func effect_gravcentric(_ strip: WLEDEffectStrip) -> UInt16 {
        return gravcenterBase(strip, mode: 1)
    }

    static func effect_gravfreq(_ strip: WLEDEffectStrip) -> UInt16 {
        return gravcenterBase(strip, mode: 3)
    }

    static func effect_djlight(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        let mid = segLen / 2
        let now = strip.now

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let fft0 = beatsin8(5, 0, 200, now, 0)
        let fft5 = beatsin8(8, 0, 200, now, 50)
        let fft15 = beatsin8(12, 0, 200, now, 100)
        let fft4 = beatsin8(7, 0, 200, now, 30)

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500 + 1) % 64)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            let r = UInt8(clamping: Int(fft15) / 2)
            let g = UInt8(clamping: Int(fft5) / 2)
            let b = UInt8(clamping: Int(fft0) / 2)
            let fade = UInt8(clamping: WLEDEffects1D.map(Int(fft4), 0, 255, 255, 4))
            let brightness = UInt8(clamping: (Int(r) + Int(g) + Int(b)) / 3)
            let scaledBri = UInt8(clamping: Int(brightness) * Int(fade) / 255)
            let color = WLEDEffects1D.rgbw32(
                UInt8(clamping: Int(r) * Int(scaledBri) / max(1, Int(brightness))),
                UInt8(clamping: Int(g) * Int(scaledBri) / max(1, Int(brightness))),
                UInt8(clamping: Int(b) * Int(scaledBri) / max(1, Int(brightness)))
            )

            strip.setPixelColor(mid, color)
            for i in stride(from: segLen - 1, through: mid + 1, by: -1) {
                strip.setPixelColor(i, strip.getPixelColor(i - 1))
            }
            for i in 0..<mid {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
        }
        return FRAMETIME
    }

    static func effect_funkyPlank(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let NUMB_BANDS = WLEDEffects1D.map(Int(strip.runtime.custom1), 0, 255, 1, 16)
        let barWidth = max(1, cols / max(1, NUMB_BANDS))
        let bandInc = barWidth == 1 && NUMB_BANDS > cols ? max(1, NUMB_BANDS / cols) : 1

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        var fftSim = [UInt8](repeating: 0, count: 16)
        for band in 0..<16 {
            fftSim[band] = beatsin8(4 + band * 2, 0, 255, now, band * 31)
        }

        let secondHand = UInt8(truncatingIfNeeded: (now / UInt32(max(1, 256 - Int(strip.runtime.speed))) / 500 + 1) % 64)
        if strip.runtime.aux0 != UInt16(secondHand) {
            strip.runtime.aux0 = UInt16(secondHand)

            var b = 0
            var band = 0
            while band < NUMB_BANDS {
                let hue = fftSim[band % 16]
                let v = UInt8(clamping: WLEDEffects1D.map(Int(fftSim[band % 16]), 0, 255, 10, 255))
                for w in 0..<barWidth {
                    let xpos = barWidth * b + w
                    if xpos < cols {
                        strip.setPixelColorXY(xpos, 0, WLEDEffects1D.hsvToRgb(hue, 255, v))
                    }
                }
                band += bandInc
                b += 1
            }

            for i in stride(from: rows - 1, through: 1, by: -1) {
                for j in 0..<cols {
                    strip.setPixelColorXY(j, i, strip.getPixelColorXY(j, i - 1))
                }
            }
        }
        return FRAMETIME
    }

    static func effect_shimmer(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 0 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        if !strip.runtime.allocateData(4) { return WLEDEffects1D.effect_solid(strip) }

        let radius = Int(Int(strip.runtime.custom1) * segLen >> 7) + 1
        let traversalDistance = (segLen + 2 * radius) << 8
        let traversalTime = 200 + (255 - Int(strip.runtime.speed)) * 80
        let speed = (traversalDistance << 5) / max(1, traversalTime)
        var position = Int32(strip.runtime.step)

        let inputstate = (UInt16(strip.runtime.intensity) << 8) | UInt16(strip.runtime.custom1)

        if strip.runtime.call == 0 || inputstate != strip.runtime.aux1 {
            position = Int32(-(radius << 8))
            strip.runtime.aux0 = 0
            strip.runtime.aux1 = inputstate
        }

        if strip.runtime.speed > 0 {
            let lastTime = UInt32(strip.runtime.data[0]) | (UInt32(strip.runtime.data[1]) << 8) |
                           (UInt32(strip.runtime.data[2]) << 16) | (UInt32(strip.runtime.data[3]) << 24)
            let deltaTime = Int((now &- lastTime) & 0x7F)

            strip.runtime.data[0] = UInt8(now & 0xFF)
            strip.runtime.data[1] = UInt8((now >> 8) & 0xFF)
            strip.runtime.data[2] = UInt8((now >> 16) & 0xFF)
            strip.runtime.data[3] = UInt8((now >> 24) & 0xFF)

            if strip.runtime.aux0 > 0 {
                let a0 = Int(strip.runtime.aux0)
                strip.runtime.aux0 = UInt16(max(0, a0 - deltaTime))
            } else {
                let step = 1 + (speed * deltaTime) >> 5
                position += Int32(step)
                let endPosition = Int32((segLen + radius) << 8)
                if position > endPosition {
                    strip.runtime.aux0 = UInt16(clamping: Int(strip.runtime.intensity) * 236)
                    position = Int32(-(radius << 8))
                }
                strip.runtime.step = UInt32(bitPattern: Int32(position))
            }

            if strip.runtime.check2 {
                position = Int32(segLen << 8) - position
            }
        } else {
            position = Int32(segLen << 7)
        }

        for i in 0..<segLen {
            let dist = abs(Int(position) - (i << 8))
            if dist < (radius << 8) {
                let color = strip.colorFromPalette(UInt8(clamping: i * 255 / max(1, segLen)))
                let blend = UInt8(clamping: dist / max(1, radius))
                strip.setPixelColor(i, WLEDEffects1D.colorBlend(color, strip.runtime.colors[1], blend))
            } else {
                strip.setPixelColor(i, strip.runtime.colors[1])
            }
        }
        return FRAMETIME
    }

    static func effect_pulser(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        strip.fadeToBlackBy(UInt8(clamping: 8 - Int(strip.runtime.intensity >> 5)))
        let a = Int(now) / max(1, 18 - Int(strip.runtime.speed) / 16)
        let x = (a / 14) % max(1, cols)
        let sinSum = Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (a * 5) & 0xFF))) +
                     Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (a * 4) & 0xFF))) +
                     Int(WLEDEffects1D.sin8(UInt8(truncatingIfNeeded: (a * 2) & 0xFF)))
        let y = WLEDEffects1D.map(sinSum, 0, 765, max(0, rows - 1), 0)
        let palIdx = UInt8(clamping: WLEDEffects1D.map(y, 0, max(1, rows - 1), 0, 255))
        strip.setPixelColorXY(x, y, strip.colorFromPalette(palIdx))
        strip.blur(strip.runtime.intensity >> 4)
        return FRAMETIME
    }

    static func effect_blurz(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        if strip.runtime.call == 0 {
            strip.fill(BLACK)
            strip.runtime.aux0 = 0
        }

        let fadeoutDelay = max(1, (256 - Int(strip.runtime.speed)) / 32)
        if fadeoutDelay <= 1 || (Int(strip.runtime.call) % fadeoutDelay == 0) {
            strip.fadeOut(strip.runtime.speed)
        }

        let fftVal = beatsin8(6 + Int(strip.runtime.aux0 % 16) * 2, 0, 255, now, Int(strip.runtime.aux0) * 37)
        let speedFormula = UInt32(5 + 50 * UInt32(255 - UInt32(strip.runtime.speed)) / max(1, UInt32(segLen)))

        strip.runtime.step &+= UInt32(FRAMETIME)
        if strip.runtime.step > speedFormula {
            let segLoc = Int(WLEDEffects1D.random16(UInt16(segLen)))
            let palIdx = UInt8(clamping: 2 * Int(fftVal) * 240 / max(1, segLen - 1))
            let bri = UInt8(clamping: 2 * Int(fftVal))
            strip.setPixelColor(segLoc, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(palIdx), bri))
            strip.runtime.aux0 = (strip.runtime.aux0 &+ 1) % 16
            strip.runtime.step = 1
            strip.blur(strip.runtime.intensity)
        }
        return FRAMETIME
    }

    static func effect_waverly(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let volumeSmth = Float(beatsin8(8, 40, 200, now))

        strip.fadeToBlackBy(strip.runtime.speed)

        let t = Int(now) / 2
        for i in 0..<cols {
            var thisVal = Int(1 + Int(strip.runtime.intensity) / 64) * Int(perlin8(i * 45, t, t)) / 2
            thisVal = thisVal / 32
            thisVal = Int(Float(thisVal) * volumeSmth)
            let thisMax = WLEDEffects1D.map(thisVal, 0, 512, 0, rows)

            for j in 0..<thisMax {
                let palIdx = UInt8(clamping: WLEDEffects1D.map(j, 0, max(1, thisMax), 250, 0))
                let c = strip.colorFromPalette(palIdx)
                strip.addPixelColorXY(i, j, c)
                strip.addPixelColorXY(cols - 1 - i, rows - 1 - j, c)
            }
        }
        if strip.runtime.check3 {
            strip.blur2D(16)
        }
        return FRAMETIME
    }

    static func effect_sunRadiation(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let bumpSize = (cols + 2) * (rows + 2)
        if !strip.runtime.allocateData(bumpSize) { return WLEDEffects1D.effect_solid(strip) }

        if strip.runtime.call == 0 { strip.fill(BLACK) }

        let t = Int(now) / 4
        let someVal = Int(strip.runtime.speed) / 4
        var index = 0
        for _ in 0..<(rows + 2) {
            for _ in 0..<(cols + 2) {
                let col = (Int(Int16(bitPattern: UInt16(perlin8(index / (rows + 2) * someVal, (index % (rows + 2)) * someVal, t)))) - 0x7F) / 3
                strip.runtime.data[index] = UInt8(bitPattern: Int8(clamping: col))
                index += 1
            }
        }

        var yindex = cols + 3
        var vly = -(rows / 2 + 1)
        for y in 0..<rows {
            vly += 1
            var vlx = -(cols / 2 + 1)
            for x in 0..<cols {
                vlx += 1
                let nx = Int(Int8(bitPattern: strip.runtime.data[x + yindex + 1])) -
                         Int(Int8(bitPattern: strip.runtime.data[x + yindex - 1]))
                let ny = Int(Int8(bitPattern: strip.runtime.data[x + yindex + cols + 2])) -
                         Int(Int8(bitPattern: strip.runtime.data[x + yindex - (cols + 2)]))
                let difx = abs(vlx * 7 - nx)
                let dify = abs(vly * 7 - ny)
                let temp = difx * difx + dify * dify
                var col = 255 - temp / 8
                if col < 0 { col = 0 }
                let briScale = max(1.0, 3.0 - Float(strip.runtime.intensity) / 128.0)
                strip.setPixelColorXY(x, y, heatColor(UInt8(clamping: Int(Float(col) / briScale))))
            }
            yindex += cols + 2
        }
        return FRAMETIME
    }

    static func effect_flowStripe(_ strip: WLEDEffectStrip) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let hl = segLen * 10 / 13
        let hue = UInt8(truncatingIfNeeded: Int(now) / max(1, Int(strip.runtime.speed) + 1))
        let t = Int(now) / max(1, Int(strip.runtime.intensity) / 8 + 1)

        for i in 0..<segLen {
            var c = (abs(Int(i) - hl) * 127) / max(1, hl)
            c = Int(WLEDEffects1D.sin8(UInt8(clamping: c)))
            c = Int(WLEDEffects1D.sin8(UInt8(clamping: c / 2 + t)))
            let b = WLEDEffects1D.sin8(UInt8(clamping: c + t / 8))
            let palIdx = UInt8(truncatingIfNeeded: (Int(b) + Int(hue)) & 0xFF)
            strip.setPixelColor(i, strip.colorFromPalette(palIdx))
        }
        return FRAMETIME
    }

    static func effect_rotozoomer(_ strip: WLEDEffectStrip) -> UInt16 {
        let cols = strip.virtualWidth
        let rows = strip.virtualHeight
        let now = strip.now

        let plasmaSize = cols * rows
        let dataSize = plasmaSize + 4
        if !strip.runtime.allocateData(dataSize) { return WLEDEffects1D.effect_solid(strip) }

        func readAngle() -> Float {
            var v: Float = 0
            withUnsafeMutableBytes(of: &v) { ptr in
                for i in 0..<4 { ptr[i] = strip.runtime.data[plasmaSize + i] }
            }
            return v
        }
        func writeAngle(_ v: Float) {
            var vv = v
            withUnsafeBytes(of: &vv) { ptr in
                for i in 0..<4 { strip.runtime.data[plasmaSize + i] = ptr[i] }
            }
        }

        let ms = Int(now) / 15

        for j in 0..<rows {
            let index = j * cols
            for i in 0..<cols {
                if strip.runtime.check1 {
                    strip.runtime.data[index + i] = UInt8(truncatingIfNeeded: ((i * 4) ^ (j * 4)) + ms / 6)
                } else {
                    strip.runtime.data[index + i] = inoise8(i * 40, j * 40, ms)
                }
            }
        }

        var angle = readAngle()
        let f = (sinf(angle / 2.0) + (Float(128 - Int(strip.runtime.intensity)) / 128.0) + 1.1) / 1.5
        let kosinus = cosf(angle) * f
        let sinus = sinf(angle) * f

        for i in 0..<cols {
            let u1 = Float(i) * kosinus
            let v1 = Float(i) * sinus
            for j in 0..<rows {
                let rawU = Int(abs8(Int(u1 - Float(j) * sinus)))
                let rawV = Int(abs8(Int(v1 + Float(j) * kosinus)))
                let u = rawU % max(1, cols)
                let v = rawV % max(1, rows)
                let srcIdx = v * cols + u
                let palIdx = srcIdx < plasmaSize ? strip.runtime.data[srcIdx] : 0
                strip.setPixelColorXY(i, j, strip.colorFromPalette(palIdx))
            }
        }

        angle -= 0.03 + Float(Int(strip.runtime.speed) - 128) * 0.0002
        if angle < -6283.185 { angle += 6283.185 }
        writeAngle(angle)

        return FRAMETIME
    }

    private static func gravcenterBase(_ strip: WLEDEffectStrip, mode: Int) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 0 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        if !strip.runtime.allocateData(8) { return WLEDEffects1D.effect_solid(strip) }

        func readI32(_ off: Int) -> Int32 {
            Int32(bitPattern: UInt32(strip.runtime.data[off]) | (UInt32(strip.runtime.data[off+1]) << 8) |
                  (UInt32(strip.runtime.data[off+2]) << 16) | (UInt32(strip.runtime.data[off+3]) << 24))
        }
        func writeI32(_ off: Int, _ v: Int32) {
            strip.runtime.data[off] = UInt8(truncatingIfNeeded: v)
            strip.runtime.data[off+1] = UInt8(truncatingIfNeeded: v >> 8)
            strip.runtime.data[off+2] = UInt8(truncatingIfNeeded: v >> 16)
            strip.runtime.data[off+3] = UInt8(truncatingIfNeeded: v >> 24)
        }

        let volumeSmth = Float(beatsin8(8, 30, 200, now))

        switch mode {
        case 1: strip.fadeOut(253)
        case 2: strip.fadeOut(249)
        case 3: strip.fadeOut(250)
        default: strip.fadeOut(251)
        }

        var segmentSampleAvg = volumeSmth * Float(strip.runtime.intensity) / 255.0

        let tempsamp: Int
        if mode == 2 {
            segmentSampleAvg *= 0.25
            let mapped = segmentSampleAvg * 2.0 * Float(segLen - 1) / 64.0
            tempsamp = min(segLen - 1, max(0, Int(mapped)))
        } else {
            segmentSampleAvg *= 0.125
            let mapped = segmentSampleAvg * 2.0 * Float(segLen) / 2.0 / 32.0
            tempsamp = min(segLen / 2, max(0, Int(mapped)))
        }

        let gravity = max(1, 8 - Int(strip.runtime.speed) / 32)
        let offset = mode == 2 ? 0 : 1

        var topLED = Int(readI32(0))
        var gravCounter = Int(readI32(4))

        if tempsamp >= topLED {
            topLED = tempsamp - offset
        } else if gravCounter % gravity == 0 {
            topLED -= 1
        }

        let half = segLen / 2

        if mode == 1 {
            for i in 0..<tempsamp {
                let index = UInt8(clamping: Int(segmentSampleAvg * 24) + Int(now / 200))
                let c = strip.colorFromPalette(index)
                strip.setPixelColor(i + half, c)
                strip.setPixelColor(half - 1 - i, c)
            }
            if topLED >= 0 {
                let gray: WLEDColor = 0x00808080
                strip.setPixelColor(topLED + half, gray)
                strip.setPixelColor(half - 1 - topLED, gray)
            }
        } else if mode == 2 {
            for i in 0..<tempsamp {
                let idx = perlin8(Int(Float(i) * segmentSampleAvg) + Int(now), 5000 + Int(Float(i) * segmentSampleAvg))
                let bri = UInt8(clamping: Int(segmentSampleAvg * 8))
                strip.setPixelColor(i, WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(idx), bri))
            }
            if topLED > 0 {
                strip.setPixelColor(topLED, strip.colorFromPalette(UInt8(truncatingIfNeeded: now & 0xFF)))
            }
        } else if mode == 3 {
            let simFreqIdx = UInt8(clamping: Int(beatsin8(5, 50, 220, now)))
            for i in 0..<tempsamp {
                let c = strip.colorFromPalette(simFreqIdx)
                strip.setPixelColor(i + half, c)
                strip.setPixelColor(half - 1 - i, c)
            }
            if topLED >= 0 {
                let gray: WLEDColor = 0x00808080
                strip.setPixelColor(topLED + half, gray)
                strip.setPixelColor(half - 1 - topLED, gray)
            }
        } else {
            for i in 0..<tempsamp {
                let idx = perlin8(Int(Float(i) * segmentSampleAvg) + Int(now), 5000 + Int(Float(i) * segmentSampleAvg))
                let bri = UInt8(clamping: Int(segmentSampleAvg * 8))
                let c = WLEDEffects1D.colorBlend(strip.runtime.colors[1], strip.colorFromPalette(idx), bri)
                strip.setPixelColor(i + half, c)
                strip.setPixelColor(half - 1 - i, c)
            }
            if topLED >= 0 {
                let palC = strip.colorFromPalette(UInt8(truncatingIfNeeded: now & 0xFF))
                strip.setPixelColor(topLED + half, palC)
                strip.setPixelColor(half - 1 - topLED, palC)
            }
        }

        gravCounter = (gravCounter + 1) % gravity
        writeI32(0, Int32(topLED))
        writeI32(4, Int32(gravCounter))
        return FRAMETIME
    }

    private static func puddlesBase(_ strip: WLEDEffectStrip, peakDetect: Bool) -> UInt16 {
        let segLen = strip.segmentLength
        guard segLen > 1 else { return WLEDEffects1D.effect_solid(strip) }
        let now = strip.now

        let fadeVal = UInt8(clamping: WLEDEffects1D.map(Int(strip.runtime.speed), 0, 255, 224, 254))
        let pos = Int(WLEDEffects1D.random16(UInt16(segLen)))
        strip.fadeOut(fadeVal)

        let volumeSmth = Float(beatsin8(9, 20, 180, now))
        let volumeRaw = Int(beatsin8(11, 10, 200, now))

        var size = 0
        if peakDetect {
            let samplePeak = (now / 400) != ((now &- UInt32(FRAMETIME)) / 400)
            if samplePeak {
                size = Int(volumeSmth * Float(strip.runtime.intensity) / 256.0 / 4.0) + 1
                if pos + size >= segLen { size = segLen - pos }
            }
        } else {
            if volumeRaw > 1 {
                size = volumeRaw * Int(strip.runtime.intensity) / 256 / 8 + 1
                if pos + size >= segLen { size = segLen - pos }
            }
        }

        for i in 0..<size {
            strip.setPixelColor(pos + i, strip.colorFromPalette(UInt8(truncatingIfNeeded: now & 0xFF)))
        }
        return FRAMETIME
    }
}
