/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

import Foundation

typealias WLEDColor = UInt32

typealias WLEDEffectFunc = (WLEDEffectStrip) -> UInt16

final class WLEDEffectRuntime {
    var colors: [WLEDColor] = [0x00FFAA00, 0x00000000, 0x00000000]

    var speed: UInt8 = 128
    var intensity: UInt8 = 128
    var custom1: UInt8 = 128
    var custom2: UInt8 = 128
    var custom3: UInt8 = 16

    var check1: Bool = false
    var check2: Bool = false
    var check3: Bool = false

    var palette: UInt8 = 0

    var call: UInt32 = 0

    var step: UInt32 = 0
    var aux0: UInt16 = 0
    var aux1: UInt16 = 0

    private var _data: [UInt8] = []
    var data: SafeBuffer {
        get { SafeBuffer(storage: self) }
    }

    @discardableResult
    func allocateData(_ size: Int) -> Bool {
        if _data.count < size {
            _data = [UInt8](repeating: 0, count: max(size, 256))
        }
        return true
    }

    struct SafeBuffer {
        let storage: WLEDEffectRuntime

        subscript(index: Int) -> UInt8 {
            get {
                guard index >= 0 && index < storage._data.count else { return 0 }
                return storage._data[index]
            }
            nonmutating set {
                if index < 0 { return }
                if index >= storage._data.count {
                    storage._data.append(contentsOf: [UInt8](repeating: 0, count: index - storage._data.count + 64))
                }
                storage._data[index] = newValue
            }
        }

        var count: Int { storage._data.count }
    }
}

final class WLEDEffectStrip {
    var leds: [WLEDColor]

    var segmentLength: Int { leds.count }

    var now: UInt32

    var frameTime: UInt16 = 24

    var runtime: WLEDEffectRuntime

    var brightness: UInt8 = 255

    var paletteBlend: UInt8 = 0

    var paletteColors: [WLEDColor] = []

    var matrixWidth: Int = 0
    var matrixHeight: Int = 0

    var is2D: Bool { matrixWidth > 0 && matrixHeight > 0 }

    var virtualWidth: Int { matrixWidth > 0 ? matrixWidth : segmentLength }
    var virtualHeight: Int { matrixHeight > 0 ? matrixHeight : 1 }

    init(ledCount: Int, now: UInt32 = 0) {
        self.leds = [WLEDColor](repeating: 0, count: ledCount)
        self.now = now
        self.runtime = WLEDEffectRuntime()
        rebuildPalette()
    }

    init(width: Int, height: Int, now: UInt32 = 0) {
        self.matrixWidth = width
        self.matrixHeight = height
        self.leds = [WLEDColor](repeating: 0, count: width * height)
        self.now = now
        self.runtime = WLEDEffectRuntime()
        rebuildPalette()
    }

    func setPixelColorXY(_ x: Int, _ y: Int, _ color: WLEDColor) {
        guard matrixWidth > 0 && matrixHeight > 0 else { return }
        guard x >= 0 && x < matrixWidth && y >= 0 && y < matrixHeight else { return }
        leds[y * matrixWidth + x] = color
    }

    func setPixelColorXY(_ x: Int, _ y: Int, _ r: UInt8, _ g: UInt8, _ b: UInt8, _ w: UInt8 = 0) {
        setPixelColorXY(x, y, WLEDEffects1D.rgbw32(r, g, b, w))
    }

    func getPixelColorXY(_ x: Int, _ y: Int) -> WLEDColor {
        guard matrixWidth > 0 && matrixHeight > 0 else { return 0 }
        guard x >= 0 && x < matrixWidth && y >= 0 && y < matrixHeight else { return 0 }
        return leds[y * matrixWidth + x]
    }

    func blendPixelColorXY(_ x: Int, _ y: Int, _ color: WLEDColor, _ blend: UInt8) {
        guard matrixWidth > 0 && matrixHeight > 0 else { return }
        guard x >= 0 && x < matrixWidth && y >= 0 && y < matrixHeight else { return }
        let idx = y * matrixWidth + x
        leds[idx] = WLEDEffects1D.colorBlend(leds[idx], color, UInt16(blend))
    }

    func blur2d(_ amount: UInt8) {
        guard matrixWidth > 0 && matrixHeight > 0 else { return }
        let keep = UInt16(255 &- amount)
        let seep = UInt16(amount) >> 1

        for y in 0..<matrixHeight {
            var carryover = WLEDColor(0)
            for x in 0..<matrixWidth {
                let idx = y * matrixWidth + x
                let cur = leds[idx]
                let r = UInt8((UInt16(WLEDEffects1D.r(cur)) * keep + UInt16(WLEDEffects1D.r(carryover)) * seep) >> 8)
                let g = UInt8((UInt16(WLEDEffects1D.g(cur)) * keep + UInt16(WLEDEffects1D.g(carryover)) * seep) >> 8)
                let b = UInt8((UInt16(WLEDEffects1D.b(cur)) * keep + UInt16(WLEDEffects1D.b(carryover)) * seep) >> 8)
                leds[idx] = WLEDEffects1D.rgbw32(r, g, b, 0)
                carryover = cur
            }
        }

        for x in 0..<matrixWidth {
            var carryover = WLEDColor(0)
            for y in 0..<matrixHeight {
                let idx = y * matrixWidth + x
                let cur = leds[idx]
                let r = UInt8((UInt16(WLEDEffects1D.r(cur)) * keep + UInt16(WLEDEffects1D.r(carryover)) * seep) >> 8)
                let g = UInt8((UInt16(WLEDEffects1D.g(cur)) * keep + UInt16(WLEDEffects1D.g(carryover)) * seep) >> 8)
                let b = UInt8((UInt16(WLEDEffects1D.b(cur)) * keep + UInt16(WLEDEffects1D.b(carryover)) * seep) >> 8)
                leds[idx] = WLEDEffects1D.rgbw32(r, g, b, 0)
                carryover = cur
            }
        }
    }

    func drawLine(_ x0: Int, _ y0: Int, _ x1: Int, _ y1: Int, _ color: WLEDColor) {
        var x = x0, y = y0
        let dx = abs(x1 - x0), dy = abs(y1 - y0)
        let sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1
        var err = dx - dy
        while true {
            setPixelColorXY(x, y, color)
            if x == x1 && y == y1 { break }
            let e2 = 2 * err
            if e2 > -dy { err -= dy; x += sx }
            if e2 < dx { err += dx; y += sy }
        }
    }

    func setPixelColor(_ index: Int, _ color: WLEDColor) {
        guard index >= 0 && index < leds.count else { return }
        leds[index] = color
    }

    func setPixelColor(_ index: Int, _ r: UInt8, _ g: UInt8, _ b: UInt8, _ w: UInt8 = 0) {
        setPixelColor(index, WLEDEffects1D.rgbw32(r, g, b, w))
    }

    func getPixelColor(_ index: Int) -> WLEDColor {
        guard index >= 0 && index < leds.count else { return 0 }
        return leds[index]
    }

    func fill(_ color: WLEDColor) {
        for i in 0..<leds.count {
            leds[i] = color
        }
    }

    func fadeOut(_ rate: UInt8) {
        let mappedScale = Int(rate) + 1
        for i in 0..<leds.count {
            let c = leds[i]
            let r = UInt8((Int(WLEDEffects1D.r(c)) * mappedScale) >> 8)
            let g = UInt8((Int(WLEDEffects1D.g(c)) * mappedScale) >> 8)
            let b = UInt8((Int(WLEDEffects1D.b(c)) * mappedScale) >> 8)
            let w = UInt8((Int(WLEDEffects1D.w(c)) * mappedScale) >> 8)
            leds[i] = WLEDEffects1D.rgbw32(r, g, b, w)
        }
    }

    func fadeToBlackBy(_ amount: UInt8) {
        let keepFrac = 255 &- amount
        fadeOut(keepFrac)
    }

    func blur(_ amount: UInt8) {
        let keep = UInt16(255 &- amount)
        let seep = UInt16(amount) >> 1
        var carryover = WLEDColor(0)
        for i in 0..<leds.count {
            let cur = leds[i]
            let r = UInt8((UInt16(WLEDEffects1D.r(cur)) * keep + UInt16(WLEDEffects1D.r(carryover)) * seep) >> 8)
            let g = UInt8((UInt16(WLEDEffects1D.g(cur)) * keep + UInt16(WLEDEffects1D.g(carryover)) * seep) >> 8)
            let b = UInt8((UInt16(WLEDEffects1D.b(cur)) * keep + UInt16(WLEDEffects1D.b(carryover)) * seep) >> 8)
            leds[i] = WLEDEffects1D.rgbw32(r, g, b, 0)
            carryover = cur
        }
    }

    func blendPixelColor(_ index: Int, _ color: WLEDColor, _ blend: UInt8) {
        guard index >= 0 && index < leds.count else { return }
        leds[index] = WLEDEffects1D.colorBlend(leds[index], color, blend)
    }

    func colorWheel(_ pos: UInt8) -> WLEDColor {
        WLEDEffects1D.colorWheel(pos)
    }

    func colorFromPalette(_ index: UInt8, _ bri: UInt8 = 255) -> WLEDColor {
        let c = paletteColors.isEmpty ? colorWheel(index) : paletteColors[Int(index)]
        if bri == 255 { return c }
        let scale = UInt16(bri) + 1
        let r = UInt8((UInt16(WLEDEffects1D.r(c)) * scale) >> 8)
        let g = UInt8((UInt16(WLEDEffects1D.g(c)) * scale) >> 8)
        let b = UInt8((UInt16(WLEDEffects1D.b(c)) * scale) >> 8)
        return WLEDEffects1D.rgbw32(r, g, b, 0)
    }

    func colorFromPalette(i: Int, mapping: Bool, wrap: Bool, colorIndex: Int, bri: UInt8 = 255) -> WLEDColor {
        if runtime.palette == 0 && colorIndex >= 0 && colorIndex < 3 {
            let base = runtime.colors[colorIndex]
            if bri == 255 { return base }
            let scale = UInt16(bri) + 1
            let r = UInt8((UInt16(WLEDEffects1D.r(base)) * scale) >> 8)
            let g = UInt8((UInt16(WLEDEffects1D.g(base)) * scale) >> 8)
            let b = UInt8((UInt16(WLEDEffects1D.b(base)) * scale) >> 8)
            return WLEDEffects1D.rgbw32(r, g, b, 0)
        }
        var idx: UInt8
        if mapping && segmentLength > 0 {
            idx = UInt8(clamping: (i * 255) / max(1, segmentLength - 1))
        } else {
            idx = UInt8(truncatingIfNeeded: i)
        }
        return colorFromPalette(idx, bri)
    }

    func rebuildPalette() {
        let palId = Int(runtime.palette)

        if palId >= 6, let stops = PaletteColorData.stops[palId], !stops.isEmpty {
            paletteColors = (0..<256).map { i in
                Self.interpolateGradientStops(stops, at: UInt8(i))
            }
        } else if palId == 0 {
            if let partyStops = PaletteColorData.stops[6], !partyStops.isEmpty {
                paletteColors = (0..<256).map { i in
                    Self.interpolateGradientStops(partyStops, at: UInt8(i))
                }
            } else {
                paletteColors = (0..<256).map { WLEDEffects1D.colorWheel(UInt8($0)) }
            }
        } else if palId == 2 {
            paletteColors = [WLEDColor](repeating: runtime.colors[0], count: 256)
        } else if palId <= 5 {
            let c0 = runtime.colors[0]
            let c1 = runtime.colors[1]
            let c2 = runtime.colors[2]
            let anchors: [(pos: Int, color: WLEDColor)]
            switch palId {
            case 1:
                anchors = [(0, c0), (85, c1), (170, c2), (255, c0)]
            case 3:
                anchors = [(0, c0), (85, c0), (170, c1), (255, c1)]
            case 4:
                anchors = [(0, c2), (128, c1), (255, c0)]
            case 5:
                anchors = [(0, c0), (63, c0), (64, c1), (127, c1), (128, c2), (191, c2), (192, c0), (255, c0)]
            default:
                anchors = [(0, c0), (85, c0), (170, c1), (255, c2)]
            }
            paletteColors = (0..<256).map { i in
                Self.interpolateAnchors(anchors, at: i)
            }
        } else {
            paletteColors = (0..<256).map { WLEDEffects1D.colorWheel(UInt8($0)) }
        }
    }

    private static func interpolateGradientStops(_ stops: [PaletteGradientStop], at pos: UInt8) -> WLEDColor {
        let p = Int(pos)
        guard stops.count > 1 else {
            let s = stops[0]
            return WLEDEffects1D.rgbw32(s.r, s.g, s.b, 0)
        }
        var lo = stops[0], hi = stops[stops.count - 1]
        for i in 0..<(stops.count - 1) {
            if Int(stops[i].position) <= p && Int(stops[i + 1].position) >= p {
                lo = stops[i]
                hi = stops[i + 1]
                break
            }
        }
        let range = Int(hi.position) - Int(lo.position)
        guard range > 0 else { return WLEDEffects1D.rgbw32(lo.r, lo.g, lo.b, 0) }
        let t = UInt16(p - Int(lo.position))
        let inv = UInt16(range) - t
        let denom = UInt16(range)
        let r = UInt8((UInt16(lo.r) * inv + UInt16(hi.r) * t) / denom)
        let g = UInt8((UInt16(lo.g) * inv + UInt16(hi.g) * t) / denom)
        let b = UInt8((UInt16(lo.b) * inv + UInt16(hi.b) * t) / denom)
        return WLEDEffects1D.rgbw32(r, g, b, 0)
    }

    private static func interpolateAnchors(_ anchors: [(pos: Int, color: WLEDColor)], at index: Int) -> WLEDColor {
        guard anchors.count > 1 else { return anchors.first?.color ?? 0 }
        var lo = anchors[0], hi = anchors[anchors.count - 1]
        for i in 0..<(anchors.count - 1) {
            if anchors[i].pos <= index && anchors[i + 1].pos >= index {
                lo = anchors[i]
                hi = anchors[i + 1]
                break
            }
        }
        let range = hi.pos - lo.pos
        guard range > 0 else { return lo.color }
        let t = UInt16(index - lo.pos)
        let inv = UInt16(range) - t
        let denom = UInt16(range)
        let r = UInt8((UInt16(WLEDEffects1D.r(lo.color)) * inv + UInt16(WLEDEffects1D.r(hi.color)) * t) / denom)
        let g = UInt8((UInt16(WLEDEffects1D.g(lo.color)) * inv + UInt16(WLEDEffects1D.g(hi.color)) * t) / denom)
        let b = UInt8((UInt16(WLEDEffects1D.b(lo.color)) * inv + UInt16(WLEDEffects1D.b(hi.color)) * t) / denom)
        return WLEDEffects1D.rgbw32(r, g, b, 0)
    }
}

enum WLEDEffects1D {

    static func wledDateTimeText(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
        let mon = months[max(0, min(11, (c.month ?? 1) - 1))]
        return String(format: "%@ %d, %d %d:%02d:%02d", mon, c.day ?? 1, c.year ?? 1970, c.hour ?? 0, c.minute ?? 0, c.second ?? 0)
    }

    static let effects: [Int: WLEDEffectFunc] = [
        0: effect_solid,
        1: effect_blink,
        2: effect_breathe,
        3: effect_color_wipe,
        4: effect_color_wipe_random,
        5: effect_random_color,
        6: effect_sweep,
        7: effect_dynamic,
        8: effect_colorloop,
        9: effect_rainbow,
        10: effect_scan,
        11: effect_dual_scan,
        12: effect_fade,
        13: effect_theater_chase,
        14: effect_theater_chase_rainbow,
        15: effect_running_lights,
        16: effect_saw,
        17: effect_twinkle,
        18: effect_dissolve,
        19: effect_dissolve_random,
        20: effect_sparkle,
        21: effect_flash_sparkle,
        22: effect_hyper_sparkle,
        23: effect_strobe,
        24: effect_strobe_rainbow,
        25: effect_multi_strobe,
        26: effect_blink_rainbow,
        27: effect_android,
        28: effect_chase_color,
        29: effect_chase_random,
        30: effect_chase_rainbow,
        31: effect_chase_flash,
        32: effect_chase_flash_random,
        33: effect_rainbow_runner,
        34: effect_colorful,
        35: effect_traffic_light,
        36: effect_color_sweep_random,
        37: effect_running_color,
        38: effect_aurora,
        39: effect_running_random,
        40: effect_larson_scanner,
        41: effect_comet,
        42: effect_fireworks_rain,
        43: effect_rain,
        44: effect_tetrix,
        45: effect_fire_flicker,
        46: effect_gradient,
        47: effect_loading,
        48: effect_rollingballs,
        49: effect_fairy,
        50: effect_two_dots,
        51: effect_fairytwinkle,
        52: effect_running_dual,
        54: effect_tricolor_chase,
        55: effect_tricolor_wipe,
        56: effect_tricolor_fade,
        57: effect_lightning,
        58: effect_icu,
        59: effect_multi_comet,
        60: effect_dual_larson_scanner,
        61: effect_random_chase,
        62: effect_oscillate,
        63: effect_pride_2015,
        64: effect_juggle,
        65: effect_palette,
        66: effect_fire_2012,
        67: effect_colorwaves,
        68: effect_bpm,
        69: effect_fillnoise8,
        70: effect_noise16_1,
        71: effect_noise16_2,
        72: effect_noise3,
        73: effect_noise4,
        74: effect_colortwinkles,
        75: effect_lake,
        76: effect_meteor,
        78: effect_railway,
        79: effect_ripple,
        80: effect_twinklefox,
        81: effect_twinklecat,
        82: effect_halloween_eyes,
        83: effect_solid_pattern,
        84: effect_solid_pattern_tri,
        85: effect_spots,
        86: effect_spots_fade,
        87: effect_glitter,
        88: effect_candle,
        89: effect_starburst,
        90: effect_exploding_fireworks,
        91: effect_bouncingballs,
        92: effect_sinelon,
        93: effect_sinelon_dual,
        94: effect_sinelon_rainbow,
        95: effect_popcorn,
        96: effect_drip,
        97: effect_plasma,
        98: effect_percent,
        99: effect_ripple_rainbow,
        100: effect_heartbeat,
        101: effect_pacifica,
        102: effect_candle_multi,
        103: effect_solid_glitter,
        104: effect_sunrise,
        105: effect_phased,
        106: effect_twinkleup,
        107: effect_noisepal,
        108: effect_sinewave,
        109: effect_phasednoise,
        110: effect_flow,
        111: effect_chunchun,
        112: effect_dancing_shadows,
        113: effect_washing_machine,
        115: effect_blends,
        116: effect_tv_simulator,
        117: effect_dynamic_smooth,
    ]

    static func rgbw32(_ r: UInt8, _ g: UInt8, _ b: UInt8, _ w: UInt8 = 0) -> WLEDColor {
        (UInt32(w) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
    }

    @inline(__always) static func r(_ c: WLEDColor) -> UInt8 { UInt8((c >> 16) & 0xFF) }
    @inline(__always) static func g(_ c: WLEDColor) -> UInt8 { UInt8((c >> 8) & 0xFF) }
    @inline(__always) static func b(_ c: WLEDColor) -> UInt8 { UInt8(c & 0xFF) }
    @inline(__always) static func w(_ c: WLEDColor) -> UInt8 { UInt8((c >> 24) & 0xFF) }

    static func colorBlend(_ color1: WLEDColor, _ color2: WLEDColor, _ amt: UInt16) -> WLEDColor {
        if amt == 0 { return color1 }
        if amt == 255 { return color2 }
        let a = amt + 1
        let ia = 256 - a
        let r1 = UInt16(self.r(color1)), g1 = UInt16(self.g(color1)), b1 = UInt16(self.b(color1)), w1 = UInt16(self.w(color1))
        let r2 = UInt16(self.r(color2)), g2 = UInt16(self.g(color2)), b2 = UInt16(self.b(color2)), w2 = UInt16(self.w(color2))
        let rr = UInt8((r1 * ia + r2 * a) >> 8)
        let gg = UInt8((g1 * ia + g2 * a) >> 8)
        let bb = UInt8((b1 * ia + b2 * a) >> 8)
        let ww = UInt8((w1 * ia + w2 * a) >> 8)
        return rgbw32(rr, gg, bb, ww)
    }

    static func colorBlend(_ c1: WLEDColor, _ c2: WLEDColor, _ amt: UInt8) -> WLEDColor {
        colorBlend(c1, c2, UInt16(amt))
    }

    static func colorWheel(_ pos: UInt8) -> WLEDColor {
        var p = pos
        if p < 85 {
            return rgbw32(255 &- p &* 3, p &* 3, 0)
        } else if p < 170 {
            p &-= 85
            return rgbw32(0, 255 &- p &* 3, p &* 3)
        } else {
            p &-= 170
            return rgbw32(p &* 3, 0, 255 &- p &* 3)
        }
    }

    static func sin8(_ x: UInt8) -> UInt8 {
        let angle = Double(x) * .pi * 2.0 / 256.0
        return UInt8(clamping: Int((sin(angle) * 127.5) + 127.5))
    }

    static func sin8(_ x: Int) -> UInt8 {
        sin8(UInt8(truncatingIfNeeded: x & 0xFF))
    }

    static func sin8(_ x: UInt32) -> UInt8 {
        sin8(UInt8(truncatingIfNeeded: x & 0xFF))
    }

    static func cos8(_ x: UInt8) -> UInt8 {
        sin8(x &+ 64)
    }

    static func sin16(_ theta: UInt16) -> Int16 {
        let angle = Double(theta) * .pi * 2.0 / 65536.0
        return Int16(clamping: Int(sin(angle) * 32767.0))
    }

    static func sin16(_ theta: UInt32) -> Int16 {
        sin16(UInt16(truncatingIfNeeded: theta & 0xFFFF))
    }

    static func triwave16(_ input: UInt16) -> UInt16 {
        if input < 0x8000 {
            return input &* 2
        }
        return 0xFFFF &- (input &- 0x8000) &* 2
    }

    static func triwave16(_ input: UInt32) -> UInt16 {
        triwave16(UInt16(truncatingIfNeeded: input & 0xFFFF))
    }

    static func triwave8(_ x: UInt8) -> UInt8 {
        if x < 128 {
            return x &* 2
        }
        return 255 &- (x &- 128) &* 2
    }

    static func cubicwave8(_ input: UInt8) -> UInt8 {
        let tri = triwave8(input)
        let t = UInt16(tri)
        let sq = (t * t) >> 8
        let cb = (sq * t) >> 8
        return UInt8(clamping: Int((3 * sq - 2 * cb)))
    }

    static func quadwave8(_ x: UInt8) -> UInt8 {
        sin8(x &+ 64)
    }

    static func beat88(_ bpm88: UInt16, now: UInt32) -> UInt16 {
        UInt16(truncatingIfNeeded: (now &* UInt32(bpm88) &* 280) >> 16)
    }

    static func beat16(_ bpm: UInt16, now: UInt32) -> UInt16 {
        beat88(bpm &* 256, now: now)
    }

    static func beat8(_ bpm: UInt8, now: UInt32) -> UInt8 {
        UInt8(truncatingIfNeeded: beat16(UInt16(bpm), now: now) >> 8)
    }

    static func beatsin8(_ bpm: UInt8, _ lo: UInt8 = 0, _ hi: UInt8 = 255, now: UInt32? = nil) -> UInt8 {
        let timeMs = now ?? 0
        let beat = beat8(bpm, now: timeMs)
        let s = sin8(beat)
        let range = UInt16(hi) - UInt16(lo)
        return lo &+ UInt8((UInt16(s) &* range) >> 8)
    }

    static func beatsin88(_ bpm88: UInt16, _ lo: UInt16, _ hi: UInt16, now: UInt32) -> UInt16 {
        let beat = beat88(bpm88, now: now)
        let s = UInt32(Int32(sin16(beat)) + 32768)
        let range = UInt32(hi) - UInt32(lo)
        return lo &+ UInt16(truncatingIfNeeded: (s &* range) >> 16)
    }

    static func beatsin16(_ bpm: UInt16, _ lo: UInt16, _ hi: UInt16, now: UInt32) -> UInt16 {
        beatsin88(bpm &* 256, lo, hi, now: now)
    }

    static func qadd8(_ a: UInt8, _ b: UInt8) -> UInt8 {
        let sum = UInt16(a) + UInt16(b)
        return sum > 255 ? 255 : UInt8(sum)
    }

    static func qsub8(_ a: UInt8, _ b: UInt8) -> UInt8 {
        a > b ? a &- b : 0
    }

    static func scale8(_ a: UInt8, _ b: UInt8) -> UInt8 {
        UInt8((UInt16(a) * UInt16(b)) >> 8)
    }

    static func scale16(_ a: UInt16, _ b: UInt16) -> UInt16 {
        UInt16((UInt32(a) * UInt32(b)) >> 16)
    }

    private static let perm: [UInt8] = [
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,
        37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
        57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,134,139,48,27,166,
        77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54,
        65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,200,196,135,130,116,188,159,86,
        164,100,109,198,173,186,3,64,52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,
        207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,
        221,153,101,155,167,43,172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,
        218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,107,
        49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,
        222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
    ]

    @inline(__always)
    private static func phash(_ i: Int) -> Int { Int(perm[i & 0xFF]) }

    @inline(__always)
    private static func ease8(_ t: UInt8) -> UInt8 {
        let ti = UInt16(t)
        return UInt8((ti &* ti &* (3 * 256 - 2 * ti)) >> 16)
    }

    static func inoise8(_ x: Int, _ y: Int = 0) -> UInt8 {
        let cellX = x >> 4, cellY = y >> 4
        let fracX = UInt8(x & 0x0F), fracY = UInt8(y & 0x0F)
        let u = ease8(UInt8(clamping: Int(fracX) * 17))
        let v = ease8(UInt8(clamping: Int(fracY) * 17))
        let n00 = phash(cellX &+ phash(cellY))
        let n10 = phash(cellX &+ 1 &+ phash(cellY))
        let n01 = phash(cellX &+ phash(cellY &+ 1))
        let n11 = phash(cellX &+ 1 &+ phash(cellY &+ 1))
        let nx0 = n00 + (n10 - n00) * Int(u) / 255
        let nx1 = n01 + (n11 - n01) * Int(u) / 255
        return UInt8(clamping: nx0 + (nx1 - nx0) * Int(v) / 255)
    }

    static func inoise16(_ x: UInt32, _ y: UInt32 = 0, _ z: UInt32 = 0) -> UInt16 {
        let n1 = UInt16(inoise8(Int(x >> 4), Int(y >> 4)))
        let n2 = UInt16(inoise8(Int((x >> 4) &+ 97), Int((y >> 4) &+ 131)))
        return (n1 << 8) | (n2 ^ UInt16(inoise8(Int(z >> 4))))
    }

    static func sinGap(_ input: UInt16) -> UInt8 {
        if input & 0x100 != 0 { return 0 }
        return sin8(UInt8(truncatingIfNeeded: (input &+ 192) & 0xFF))
    }

    static func gamma8(_ x: UInt8) -> UInt8 {
        let v = Double(x) / 255.0
        return UInt8(clamping: Int(pow(v, 2.2) * 255.0 + 0.5))
    }

    static func map(_ x: Int, _ inMin: Int, _ inMax: Int, _ outMin: Int, _ outMax: Int) -> Int {
        let inRange = inMax - inMin
        guard inRange != 0 else { return outMin }
        return (x - inMin) * (outMax - outMin) / inRange + outMin
    }

    static var _rngState: UInt32 = 12345

    static func seedRandom(_ seed: UInt32) { _rngState = seed == 0 ? 1 : seed }

    static func random8() -> UInt8 {
        _rngState = _rngState &* 1103515245 &+ 12345
        return UInt8(truncatingIfNeeded: (_rngState >> 16) & 0xFF)
    }

    static func random8(_ max: UInt8) -> UInt8 {
        guard max > 0 else { return 0 }
        return random8() % max
    }

    static func random8(_ min: UInt8, _ max: UInt8) -> UInt8 {
        guard max > min else { return min }
        return min &+ (random8() % (max &- min))
    }

    static func random16() -> UInt16 {
        return UInt16(random8()) << 8 | UInt16(random8())
    }

    static func random16(_ max: UInt16) -> UInt16 {
        guard max > 0 else { return 0 }
        return random16() % max
    }

    static func random16(_ min: UInt16, _ max: UInt16) -> UInt16 {
        guard max > min else { return min }
        return min &+ (random16() % (max &- min))
    }

    static func getRandomWheelIndex(_ prev: UInt8) -> UInt8 {
        var r = random8()
        while abs(Int16(r) - Int16(prev)) < 42 {
            r = random8()
        }
        return r
    }

    static func getAverageLight(_ c: WLEDColor) -> UInt8 {
        let sum = UInt16(r(c)) + UInt16(g(c)) + UInt16(b(c))
        return UInt8(sum / 3)
    }

    static func hsvToRgb(_ h: UInt8, _ s: UInt8, _ v: UInt8) -> WLEDColor {
        if s == 0 { return rgbw32(v, v, v) }
        let region = Int(h) / 43
        let remainder = (Int(h) - region * 43) * 6
        let p = UInt8((Int(v) * (255 - Int(s))) >> 8)
        let q = UInt8((Int(v) * (255 - (Int(s) * remainder >> 8))) >> 8)
        let t = UInt8((Int(v) * (255 - (Int(s) * (255 - remainder) >> 8))) >> 8)
        switch region {
        case 0:  return rgbw32(v, t, p)
        case 1:  return rgbw32(q, v, p)
        case 2:  return rgbw32(p, v, t)
        case 3:  return rgbw32(p, q, v)
        case 4:  return rgbw32(t, p, v)
        default: return rgbw32(v, p, q)
        }
    }

    static func nscale8Video(_ c: WLEDColor, _ scale: UInt8) -> WLEDColor {
        let cr = r(c), cg = g(c), cb = b(c)
        let nr = UInt8(max(cr > 0 ? 1 : 0, Int(UInt16(cr) * UInt16(scale)) >> 8))
        let ng = UInt8(max(cg > 0 ? 1 : 0, Int(UInt16(cg) * UInt16(scale)) >> 8))
        let nb = UInt8(max(cb > 0 ? 1 : 0, Int(UInt16(cb) * UInt16(scale)) >> 8))
        return rgbw32(nr, ng, nb)
    }

    static let FRAMETIME: UInt16 = 24
    static let FRAMETIME_FIXED: UInt16 = 24
    static let BLACK: WLEDColor = 0x00000000
    static let WHITE: WLEDColor = 0x00FFFFFF
    static let ULTRAWHITE: WLEDColor = 0xFFFFFFFF

    static func effect_solid(_ strip: WLEDEffectStrip) -> UInt16 {
        strip.fill(strip.runtime.colors[0])
        return 350
    }

    static func effect_breathe(_ strip: WLEDEffectStrip) -> UInt16 {
        var variable: UInt32 = 0
        let counter = (UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 3) &+ 10)) & 0xFFFF
        var cnt = (counter >> 2) &+ (counter >> 4)
        if cnt < 16384 {
            if cnt > 8192 { cnt = 8192 &- (cnt &- 8192) }
            let s = sin16(UInt16(truncatingIfNeeded: cnt))
            variable = UInt32(max(0, Int32(s))) / 103
        }
        let lum = UInt8(clamping: 30 + Int(variable))
        for i in 0..<strip.segmentLength {
            let palColor = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, lum))
        }
        return FRAMETIME
    }

    static func effect_sweep(_ strip: WLEDEffectStrip) -> UInt16 {
        colorWipe(strip, rev: true, useRandomColors: false)
    }

    private static func colorWipe(_ strip: WLEDEffectStrip, rev: Bool, useRandomColors: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        let cycleTime = UInt32(750) + UInt32(255 - UInt32(strip.runtime.speed)) * 150
        let perc = strip.now % cycleTime
        let prog = Int((UInt32(perc) * 65535) / cycleTime)
        let back = prog > 32767
        var p = prog
        if back {
            p -= 32767
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

        let ledIndex = (p * SEGLEN) >> 15
        var rem = (p * SEGLEN) * 2
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

    static func effect_dynamic(_ strip: WLEDEffectStrip) -> UInt16 {
        dynamicBase(strip, smooth: false)
    }

    private static func dynamicBase(_ strip: WLEDEffectStrip, smooth: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if !strip.runtime.allocateData(SEGLEN) { return effect_solid(strip) }

        if strip.runtime.call == 0 {
            for i in 0..<SEGLEN { strip.runtime.data[i] = random8() }
        }

        let cycleTime = UInt32(50) + UInt32(255 - UInt32(strip.runtime.speed)) * 15
        let it = strip.now / cycleTime
        if it != strip.runtime.step && strip.runtime.speed != 0 {
            for i in 0..<SEGLEN {
                if random8() <= strip.runtime.intensity {
                    strip.runtime.data[i] = random8()
                }
            }
            strip.runtime.step = it
        }

        if smooth {
            for i in 0..<SEGLEN {
                let target = colorWheel(strip.runtime.data[i])
                strip.blendPixelColor(i, target, 16)
            }
        } else {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, colorWheel(strip.runtime.data[i]))
            }
        }
        return FRAMETIME
    }

    static func effect_dynamic_smooth(_ strip: WLEDEffectStrip) -> UInt16 {
        dynamicBase(strip, smooth: true)
    }

    static func effect_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
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

    static func effect_fade(_ strip: WLEDEffectStrip) -> UInt16 {
        let counter = UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 3) &+ 10)
        let lum = UInt8(triwave16(UInt16(truncatingIfNeeded: counter & 0xFFFF)) >> 8)

        for i in 0..<strip.segmentLength {
            let palColor = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, lum))
        }
        return FRAMETIME
    }

    static func effect_twinkle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        strip.fadeOut(224)

        let cycleTime = UInt32(20) + UInt32(255 - UInt32(strip.runtime.speed)) * 5
        let it = strip.now / cycleTime
        if it != strip.runtime.step {
            let maxOn = max(1, map(Int(strip.runtime.intensity), 0, 255, 1, SEGLEN))
            if Int(strip.runtime.aux0) >= maxOn {
                strip.runtime.aux0 = 0
                strip.runtime.aux1 = random16()
            }
            strip.runtime.aux0 &+= 1
            strip.runtime.step = it
        }

        var prng16 = strip.runtime.aux1
        for _ in 0..<strip.runtime.aux0 {
            prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 13849)
            let p = UInt32(SEGLEN) &* UInt32(prng16)
            let j = Int(p >> 16)
            let palColor = strip.colorFromPalette(i: j, mapping: true, wrap: true, colorIndex: 0)
            strip.setPixelColor(j, palColor)
        }
        return FRAMETIME
    }

    static func effect_dissolve(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let color: WLEDColor = strip.runtime.check1 ? colorWheel(random8()) : strip.runtime.colors[0]
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

        if strip.runtime.step > UInt32(255 - UInt32(strip.runtime.speed)) + 15 {
            strip.runtime.aux0 = strip.runtime.aux0 != 0 ? 0 : 1
            strip.runtime.step = 0
            let fillVal: UInt8 = strip.runtime.aux0 != 0 ? 0xFF : 0
            for i in 0..<dataSize { strip.runtime.data[i] = fillVal }
        } else {
            strip.runtime.step &+= 1
        }
        return FRAMETIME
    }

    static func effect_sparkle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if !strip.runtime.check2 {
            for i in 0..<SEGLEN {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1))
            }
        }

        let cycleTime = UInt32(10) + UInt32(255 - UInt32(strip.runtime.speed)) * 2
        let it = strip.now / cycleTime
        if it != strip.runtime.step {
            strip.runtime.aux0 = random16(UInt16(SEGLEN))
            strip.runtime.step = it
        }
        strip.setPixelColor(Int(strip.runtime.aux0), strip.runtime.colors[0])
        return FRAMETIME
    }

    static func effect_android(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        for i in 0..<SEGLEN {
            strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 1))
        }

        if strip.runtime.aux1 > UInt16((UInt32(strip.runtime.intensity) * UInt32(SEGLEN)) / 255) {
            strip.runtime.aux0 = 1
        } else {
            if strip.runtime.aux1 < 2 { strip.runtime.aux0 = 0 }
        }

        var a = Int(strip.runtime.step & 0xFFFF)

        if strip.runtime.aux0 == 0 {
            if strip.runtime.call % 3 == 1 { a += 1 }
            else { strip.runtime.aux1 &+= 1 }
        } else {
            a += 1
            if strip.runtime.call % 3 != 1 { strip.runtime.aux1 &-= 1 }
        }

        if a >= SEGLEN { a = 0 }

        let aux1 = Int(strip.runtime.aux1)
        if a + aux1 < SEGLEN {
            for i in a..<(a + aux1) {
                strip.setPixelColor(i, strip.runtime.colors[0])
            }
        } else {
            for i in a..<SEGLEN {
                strip.setPixelColor(i, strip.runtime.colors[0])
            }
            for i in 0..<(aux1 - (SEGLEN - a)) {
                strip.setPixelColor(i, strip.runtime.colors[0])
            }
        }
        strip.runtime.step = UInt32(a)

        return UInt16(3 + ((8 * UInt32(255 - UInt32(strip.runtime.speed))) / UInt32(SEGLEN)))
    }

    static func effect_rainbow_runner(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        let counter = UInt16(truncatingIfNeeded: strip.now &* (UInt32(strip.runtime.speed >> 2) &+ 1))
        let a = (UInt32(counter) &* UInt32(SEGLEN)) >> 16
        strip.runtime.step = a

        let size = 1 + ((Int(strip.runtime.intensity) * SEGLEN) >> 10)
        var b = Int(a) + size
        if b > SEGLEN { b -= SEGLEN }
        var c = b + size
        if c > SEGLEN { c -= SEGLEN }

        strip.fill(strip.runtime.colors[0])

        let callByte = UInt8(truncatingIfNeeded: strip.runtime.call & 0xFF)
        let n = Int(a)
        let m = (n + 1) % SEGLEN
        let color2 = colorWheel(UInt8(truncatingIfNeeded: (n * 256 / SEGLEN + Int(callByte)) & 0xFF))
        let color3 = colorWheel(UInt8(truncatingIfNeeded: (m * 256 / SEGLEN + Int(callByte)) & 0xFF))

        drawChaseSection(strip, from: Int(a), to: b, color: color2)
        drawChaseSection(strip, from: b, to: c, color: color3)

        return FRAMETIME
    }

    private static func drawChaseSection(_ strip: WLEDEffectStrip, from a: Int, to b: Int, color: WLEDColor) {
        let SEGLEN = strip.segmentLength
        if a < b {
            for i in a..<b { strip.setPixelColor(i, color) }
        } else {
            for i in a..<SEGLEN { strip.setPixelColor(i, color) }
            for i in 0..<b { strip.setPixelColor(i, color) }
        }
    }

    static func effect_fireworks_rain(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        strip.runtime.step &+= UInt32(FRAMETIME)
        let speedFormula = UInt32(5) + (UInt32(50) * UInt32(255 - UInt32(strip.runtime.speed))) / UInt32(SEGLEN)
        if strip.runtime.call > 0 && strip.runtime.step > speedFormula {
            strip.runtime.step = 1
            let ctemp = strip.getPixelColor(0)
            for i in 0..<(SEGLEN - 1) {
                strip.setPixelColor(i, strip.getPixelColor(i + 1))
            }
            strip.setPixelColor(SEGLEN - 1, ctemp)
            strip.runtime.aux0 &+= 1
            strip.runtime.aux1 &+= 1
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
                let index = Int(random16(UInt16(SEGLEN)))
                let col = strip.colorFromPalette(random8())
                strip.setPixelColor(index, col)
                strip.runtime.aux1 = strip.runtime.aux0
                strip.runtime.aux0 = UInt16(index)
            }
        }
        return FRAMETIME
    }

    static func effect_tetrix(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        let dataSize = 17
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

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
        func readU16(_ offset: Int) -> UInt16 {
            UInt16(strip.runtime.data[offset]) | (UInt16(strip.runtime.data[offset+1]) << 8)
        }
        func writeU16(_ offset: Int, _ v: UInt16) {
            strip.runtime.data[offset] = UInt8(v & 0xFF)
            strip.runtime.data[offset+1] = UInt8(v >> 8)
        }
        func readU32(_ offset: Int) -> UInt32 {
            UInt32(strip.runtime.data[offset]) | (UInt32(strip.runtime.data[offset+1]) << 8) |
            (UInt32(strip.runtime.data[offset+2]) << 16) | (UInt32(strip.runtime.data[offset+3]) << 24)
        }
        func writeU32(_ offset: Int, _ v: UInt32) {
            strip.runtime.data[offset] = UInt8(v & 0xFF)
            strip.runtime.data[offset+1] = UInt8((v >> 8) & 0xFF)
            strip.runtime.data[offset+2] = UInt8((v >> 16) & 0xFF)
            strip.runtime.data[offset+3] = UInt8((v >> 24) & 0xFF)
        }

        if strip.runtime.call == 0 {
            writeU16(11, 0)
            writeU32(13, strip.now + 2000)
            if strip.runtime.check1 { strip.runtime.data[8] = 0 }
        }

        let dstep = readU32(13)
        let stack = Int(readU16(11))

        if dstep == 0 {
            var speed = strip.runtime.speed
            if speed == 0 { speed = random8(1, 255) }
            let spd = self.map(Int(speed), 1, 255, 5000, 250)
            let dropSpeed = Float(SEGLEN) * Float(FRAMETIME) / Float(spd)
            writeFloat(0, Float(SEGLEN))
            writeFloat(4, dropSpeed)
            if !strip.runtime.check1 {
                strip.runtime.data[8] = random8(0, 15) << 4
            }
            writeU32(13, 1)
            let brickSize = (strip.runtime.intensity != 0 ? Int(strip.runtime.intensity >> 5) + 1 : Int(random8(1, 5))) * (1 + (SEGLEN >> 6))
            writeU16(9, UInt16(brickSize))
        }

        let curStep = readU32(13)
        if curStep == 1 {
            if random8() >> 6 != 0 {
                writeU32(13, 2)
            }
        }

        if readU32(13) == 2 {
            let pos = readFloat(0)
            let spd = readFloat(4)
            let brick = Int(readU16(9))
            if pos > Float(stack) {
                let newPos = pos - spd
                writeFloat(0, newPos < Float(stack) ? Float(stack) : newPos)
                let curPos = Int(readFloat(0))
                for i in curPos..<SEGLEN {
                    let col: WLEDColor = i < curPos + brick ? strip.colorFromPalette(strip.runtime.data[8]) : strip.runtime.colors[1]
                    strip.setPixelColor(i, col)
                }
            } else {
                writeU32(13, 0)
                let newStack = stack + brick
                writeU16(11, UInt16(newStack))
                if newStack >= SEGLEN {
                    writeU32(13, strip.now + 2000)
                }
            }
        }

        if readU32(13) > 2 {
            writeU16(9, 0)
            if readU32(13) > strip.now {
                for i in 0..<SEGLEN {
                    strip.blendPixelColor(i, strip.runtime.colors[1], 25)
                }
            } else {
                writeU16(11, 0)
                writeU32(13, 0)
                if strip.runtime.check1 {
                    strip.runtime.data[8] &+= 8
                }
            }
        }
        return FRAMETIME
    }

    static func effect_fire_flicker(_ strip: WLEDEffectStrip) -> UInt16 {
        let cycleTime = UInt32(40) + UInt32(255 - UInt32(strip.runtime.speed))
        let it = strip.now / cycleTime
        if strip.runtime.step == it { return FRAMETIME }

        let col0 = strip.runtime.colors[0]
        let cw = w(col0), cr = r(col0), cg = g(col0), cb = b(col0)
        var lum = strip.runtime.palette == 0 ? max(cw, max(cr, max(cg, cb))) : UInt8(255)
        lum /= UInt8(clamping: Int((256 - Int(strip.runtime.intensity)) / 16) + 1)

        for i in 0..<strip.segmentLength {
            let flicker = random8(lum)
            if strip.runtime.palette == 0 {
                strip.setPixelColor(i, qsub8(cr, flicker), qsub8(cg, flicker), qsub8(cb, flicker), qsub8(cw, flicker))
            } else {
                let bri = 255 &- flicker
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0, bri: bri))
            }
        }
        strip.runtime.step = it
        return FRAMETIME
    }

    static func effect_fairy(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength

        var prng16: UInt16 = 5100
        for i in 0..<SEGLEN {
            prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
            strip.setPixelColor(i, strip.colorFromPalette(UInt8(prng16 >> 8)))
        }

        if strip.runtime.intensity == 0 { return FRAMETIME }

        let flasherDistance = Int((255 - Int(strip.runtime.intensity)) / 28) + 1
        let numFlashers = (SEGLEN / flasherDistance) + 1

        let now16 = UInt16(truncatingIfNeeded: strip.now & 0xFFFF)
        let flashersPerZone = 6
        let zones = max(1, numFlashers / flashersPerZone)
        let flashersInZone = numFlashers / zones

        for z in 0..<zones {
            var flasherBriSum: Int = 0
            let firstFlasher = z * flashersInZone
            let fiz = z == zones - 1 ? numFlashers - flashersInZone * (zones - 1) : flashersInZone

            var flasherBris = [UInt8](repeating: 0, count: max(fiz, 1))
            for f in 0..<fiz {
                let fIdx = firstFlasher + f
                let phase = UInt16(truncatingIfNeeded: UInt32(fIdx) &* 7919 &+ 1013)
                let stateTime = now16 &- phase
                let cycle = UInt16(200 + Int(255 - strip.runtime.speed))
                let inCycle = stateTime % (cycle * 2)
                let bri: UInt8 = inCycle < cycle ? UInt8(clamping: Int(inCycle) * 255 / max(1, Int(cycle))) : UInt8(clamping: 255 - Int(inCycle - cycle) * 255 / max(1, Int(cycle)))
                flasherBris[f] = bri
                flasherBriSum += Int(bri)
            }

            let avgBri = flasherBriSum / max(1, fiz)
            let globalPeakBri = 255 - ((avgBri * 92) >> 8)

            for f in 0..<fiz {
                let bri = UInt8(clamping: (Int(flasherBris[f]) * globalPeakBri) / 255)
                prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
                let flasherPos = (firstFlasher + f) * flasherDistance
                if flasherPos < SEGLEN {
                    strip.setPixelColor(flasherPos, colorBlend(strip.runtime.colors[1], strip.colorFromPalette(UInt8(prng16 >> 8)), bri))
                }
                for j in (flasherPos + 1)..<min(flasherPos + flasherDistance, SEGLEN) {
                    prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
                    strip.setPixelColor(j, strip.colorFromPalette(UInt8(prng16 >> 8), UInt8(clamping: globalPeakBri)))
                }
            }
        }
        return FRAMETIME
    }

    static func effect_fairytwinkle(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let dataSize = SEGLEN * 4
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let now16 = UInt16(truncatingIfNeeded: strip.now & 0xFFFF)
        var prng16: UInt16 = 5100
        let riseFallTime = UInt32(400) + UInt32(255 - UInt32(strip.runtime.speed)) * 3

        for f in 0..<SEGLEN {
            let d = f * 4
            var stateStart = UInt16(strip.runtime.data[d]) | (UInt16(strip.runtime.data[d+1]) << 8)
            var stateDur = strip.runtime.data[d+2]
            var stateOn = strip.runtime.data[d+3] != 0

            var stateTime = UInt32(now16 &- stateStart)
            if stateTime > UInt32(stateDur) * 100 {
                stateOn = !stateOn
                let isInit = stateDur == 0
                if stateOn {
                    stateDur = UInt8(clamping: Int(riseFallTime / 100) + Int((255 - strip.runtime.intensity) >> 2) + Int(random8(12 + UInt8((255 - Int(strip.runtime.intensity)) >> 1))) + 1)
                } else {
                    stateDur = UInt8(clamping: Int(riseFallTime / 100) + Int(random8(3 + UInt8((255 - Int(strip.runtime.speed)) >> 6))) + 1)
                }
                stateStart = now16
                stateTime = 0
                if isInit {
                    stateStart &-= UInt16(truncatingIfNeeded: riseFallTime)
                    stateTime = riseFallTime
                }
            }
            if stateTime > riseFallTime { stateTime = riseFallTime }
            let fadeprog = UInt8(clamping: 255 - Int(stateTime * 255 / max(1, riseFallTime)))
            let flasherBri = stateOn ? (255 &- gamma8(fadeprog)) : gamma8(fadeprog)

            strip.runtime.data[d] = UInt8(stateStart & 0xFF)
            strip.runtime.data[d+1] = UInt8(stateStart >> 8)
            strip.runtime.data[d+2] = stateDur
            strip.runtime.data[d+3] = stateOn ? 1 : 0

            let lastR = prng16
            var diff: UInt16 = 0
            while diff < 0x4000 {
                prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
                diff = prng16 > lastR ? prng16 &- lastR : lastR &- prng16
            }
            strip.setPixelColor(f, colorBlend(strip.runtime.colors[1], strip.colorFromPalette(UInt8(prng16 >> 8)), flasherBri))
        }
        return FRAMETIME
    }

    static func effect_running_dual(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let xScale = UInt32(strip.runtime.intensity >> 2)
        let counter = (UInt32(strip.now) &* UInt32(strip.runtime.speed)) >> 9

        for i in 0..<SEGLEN {
            let a = UInt32(i) &* xScale &- counter
            let s = sinGap(UInt16(truncatingIfNeeded: a & 0xFFFF))
            let palColor = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0)
            var ca = colorBlend(strip.runtime.colors[1], palColor, s)

            let bVal = UInt32(SEGLEN - 1 - i) &* xScale &- counter
            let t = sinGap(UInt16(truncatingIfNeeded: bVal & 0xFFFF))
            let palColor2 = strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 2)
            let cb = colorBlend(strip.runtime.colors[1], palColor2, t)
            ca = colorBlend(ca, cb, UInt8(127))

            strip.setPixelColor(i, ca)
        }
        return FRAMETIME
    }

    static func effect_lightning(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        let ledstart = Int(random16(UInt16(SEGLEN)))
        let ledlen = 1 + Int(random16(UInt16(SEGLEN - ledstart)))
        var bri: UInt8 = 255 / random8(1, 3)

        if strip.runtime.aux1 == 0 {
            strip.runtime.aux1 = UInt16(random8(4, 4 + strip.runtime.intensity / 20))
            strip.runtime.aux1 *= 2
            bri = 52
            strip.runtime.aux0 = 200
        }

        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }

        if strip.runtime.aux1 > 3 && (strip.runtime.aux1 & 0x01) == 0 {
            for i in ledstart..<min(ledstart + ledlen, SEGLEN) {
                strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0, bri: bri))
            }
            strip.runtime.aux1 &-= 1
            strip.runtime.step = UInt32(strip.now)
        } else {
            if strip.now - UInt32(strip.runtime.step) > UInt32(strip.runtime.aux0) {
                strip.runtime.aux1 &-= 1
                if strip.runtime.aux1 < 2 { strip.runtime.aux1 = 0 }
                strip.runtime.aux0 = UInt16(50 + UInt16(random8(100)))
                if strip.runtime.aux1 == 2 {
                    strip.runtime.aux0 = UInt16(UInt16(random8(UInt8(clamping: 255 - Int(strip.runtime.speed)))) * 100)
                }
                strip.runtime.step = UInt32(strip.now)
            }
        }
        return FRAMETIME
    }

    static func effect_pride_2015(_ strip: WLEDEffectStrip) -> UInt16 {
        let duration = UInt32(10 + UInt32(strip.runtime.speed))
        var sPseudotime = strip.runtime.step
        var sHue16 = UInt32(strip.runtime.aux0)

        let sat8 = beatsin88(87, 220, 250, now: strip.now)
        let brightdepth = beatsin88(341, 96, 224, now: strip.now)
        let brightnessthetainc16 = beatsin88(203, 25 * 256, 40 * 256, now: strip.now)
        let msmultiplier = beatsin88(147, 23, 60, now: strip.now)

        var hue16 = sHue16
        let hueinc16 = beatsin88(113, 1, 3000, now: strip.now)

        sPseudotime &+= duration &* UInt32(msmultiplier)
        sHue16 &+= duration &* UInt32(beatsin88(400, 5, 9, now: strip.now))
        var brightnesstheta16 = sPseudotime

        for i in 0..<strip.segmentLength {
            hue16 &+= UInt32(hueinc16)
            let hue8 = UInt8(truncatingIfNeeded: (hue16 >> 8) & 0xFF)

            brightnesstheta16 &+= UInt32(brightnessthetainc16)
            let b16 = UInt32(Int32(sin16(UInt16(truncatingIfNeeded: brightnesstheta16))) + 32768)
            let bri16 = (b16 &* b16) / 65536
            var bri8 = UInt8(truncatingIfNeeded: (bri16 &* UInt32(brightdepth)) / 65536)
            bri8 &+= UInt8(clamping: 255 - Int(brightdepth))

            let newColor = hsvToRgb(hue8, UInt8(clamping: Int(sat8)), bri8)
            strip.blendPixelColor(i, newColor, 64)
        }
        strip.runtime.step = sPseudotime
        strip.runtime.aux0 = UInt16(truncatingIfNeeded: sHue16 & 0xFFFF)
        return FRAMETIME
    }

    static func effect_palette(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let inputShift = strip.runtime.speed
        let inputSize = strip.runtime.intensity
        let animateShift = strip.runtime.check1

        let paletteOffset: Int
        if !animateShift {
            paletteOffset = Int(inputShift)
        } else {
            paletteOffset = Int(((UInt32(strip.now) &* (UInt32(inputShift >> 3) + 1)) & 0xFFFF) >> 8)
        }

        for x in 0..<SEGLEN {
            var colorIndex = (x * 255) / max(1, SEGLEN - 1)
            if inputSize <= 128 {
                colorIndex = (colorIndex * Int(inputSize)) / 128
            } else {
                colorIndex = ((Int(inputSize) - 112) * colorIndex) / 16
            }
            colorIndex -= paletteOffset
            strip.setPixelColor(x, strip.colorFromPalette(UInt8(truncatingIfNeeded: colorIndex & 0xFF)))
        }
        return FRAMETIME
    }

    static func effect_fire_2012(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }
        if !strip.runtime.allocateData(SEGLEN) { return effect_solid(strip) }

        let it = strip.now >> 5
        let ignition = max(3, SEGLEN / 10)

        for i in 0..<SEGLEN {
            let cool: UInt8 = (it != strip.runtime.step) ?
                random8(UInt8(clamping: (((20 + Int(strip.runtime.speed) / 3) * 16) / SEGLEN) + 2)) :
                random8(4)
            let minTemp: UInt8 = i < ignition ? UInt8(clamping: (ignition - i) / 4 + 16) : 0
            let temp = qsub8(strip.runtime.data[i], cool)
            strip.runtime.data[i] = temp < minTemp ? minTemp : temp
        }

        if it != strip.runtime.step {
            for k in stride(from: SEGLEN - 1, through: 2, by: -1) {
                strip.runtime.data[k] = UInt8(clamping: (Int(strip.runtime.data[k-1]) + Int(strip.runtime.data[k-2]) * 2) / 3)
            }
            if random8() <= strip.runtime.intensity {
                let y = Int(random8(UInt8(clamping: ignition)))
                let boost = UInt8(clamping: (17 + Int(strip.runtime.custom3)) * (ignition - y / 2) / ignition)
                strip.runtime.data[y] = qadd8(strip.runtime.data[y], random8(UInt8(clamping: 96 + 2 * Int(boost)), UInt8(clamping: min(255, 207 + Int(boost)))))
            }
        }

        for j in 0..<SEGLEN {
            let heat = min(strip.runtime.data[j], 240)
            strip.setPixelColor(j, heatColor(heat))
        }

        if it != strip.runtime.step { strip.runtime.step = it }
        return FRAMETIME
    }

    private static func heatColor(_ temperature: UInt8) -> WLEDColor {
        let t192 = UInt8(clamping: Int(temperature) * 191 / 255)
        let heatramp = (t192 & 0x3F) << 2
        if t192 & 0x80 != 0 {
            return rgbw32(255, 255, heatramp)
        } else if t192 & 0x40 != 0 {
            return rgbw32(255, heatramp, 0)
        } else {
            return rgbw32(heatramp, 0, 0)
        }
    }

    static func effect_colorwaves(_ strip: WLEDEffectStrip) -> UInt16 {
        let duration = UInt32(10 + UInt32(strip.runtime.speed))
        var sPseudotime = strip.runtime.step
        var sHue16 = UInt32(strip.runtime.aux0)

        let brightdepth = beatsin88(341, 96, 224, now: strip.now)
        let brightnessthetainc16 = beatsin88(203, 25 * 256, 40 * 256, now: strip.now)
        let msmultiplier = beatsin88(147, 23, 60, now: strip.now)

        var hue16 = sHue16
        let hueinc16 = UInt32(beatsin88(113, 60, 300, now: strip.now)) * UInt32(strip.runtime.intensity) * 10 / 255

        sPseudotime &+= duration &* UInt32(msmultiplier)
        sHue16 &+= duration &* UInt32(beatsin88(400, 5, 9, now: strip.now))
        var brightnesstheta16 = sPseudotime

        for i in 0..<strip.segmentLength {
            hue16 &+= hueinc16
            var hue8 = UInt8(truncatingIfNeeded: (hue16 >> 8) & 0xFF)
            let h16_128 = hue16 >> 7
            if h16_128 & 0x100 != 0 {
                hue8 = 255 &- UInt8(truncatingIfNeeded: (h16_128 >> 1) & 0xFF)
            } else {
                hue8 = UInt8(truncatingIfNeeded: (h16_128 >> 1) & 0xFF)
            }

            brightnesstheta16 &+= UInt32(brightnessthetainc16)
            let b16 = UInt32(Int32(sin16(UInt16(truncatingIfNeeded: brightnesstheta16))) + 32768)
            let bri16 = (b16 &* b16) / 65536
            var bri8 = UInt8(truncatingIfNeeded: (bri16 &* UInt32(brightdepth)) / 65536)
            bri8 &+= UInt8(clamping: 255 - Int(brightdepth))

            let c = strip.colorFromPalette(hue8, bri8)
            strip.blendPixelColor(i, c, 128)
        }
        strip.runtime.step = sPseudotime
        strip.runtime.aux0 = UInt16(truncatingIfNeeded: sHue16 & 0xFFFF)
        return FRAMETIME
    }

    static func effect_noise3(_ strip: WLEDEffectStrip) -> UInt16 {
        let scale: UInt32 = 800
        strip.runtime.step &+= 1 &+ UInt32(strip.runtime.speed)

        for i in 0..<strip.segmentLength {
            let shiftX: UInt32 = 4223
            let shiftY: UInt32 = 1234
            let realX = (UInt32(i) &+ shiftX) &* scale
            let realY = (UInt32(i) &+ shiftY) &* scale
            let realZ = strip.runtime.step &* 8
            let noise = inoise16(realX, realY, realZ) >> 8
            let index = sin8(UInt8(truncatingIfNeeded: (UInt16(noise) &* 3) & 0xFF))
            strip.setPixelColor(i, strip.colorFromPalette(index, UInt8(truncatingIfNeeded: noise & 0xFF)))
        }
        return FRAMETIME
    }

    static func effect_noise4(_ strip: WLEDEffectStrip) -> UInt16 {
        let stp = (UInt32(strip.now) &* UInt32(strip.runtime.speed)) >> 7
        for i in 0..<strip.segmentLength {
            let index = inoise16(UInt32(i) << 12, stp)
            strip.setPixelColor(i, strip.colorFromPalette(UInt8(truncatingIfNeeded: index & 0xFF)))
        }
        return FRAMETIME
    }

    static func effect_colortwinkles(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let dataSize = (SEGLEN + 7) >> 3
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let fadeUpAmount = UInt8(clamping: 8 + Int(strip.runtime.speed >> 2))
        let fadeDownAmount = UInt8(clamping: 8 + Int(strip.runtime.speed >> 3))

        for i in 0..<SEGLEN {
            let c = strip.getPixelColor(i)
            var cr = r(c), cg = g(c), cb = b(c)
            let index = i >> 3
            let bitNum = i & 0x07
            let fadeUp = (strip.runtime.data[index] >> bitNum) & 1 != 0

            if fadeUp {
                let incR = UInt8(max(cr > 0 ? 1 : 0, Int(UInt16(cr) * UInt16(fadeUpAmount)) >> 8))
                let incG = UInt8(max(cg > 0 ? 1 : 0, Int(UInt16(cg) * UInt16(fadeUpAmount)) >> 8))
                let incB = UInt8(max(cb > 0 ? 1 : 0, Int(UInt16(cb) * UInt16(fadeUpAmount)) >> 8))
                cr = qadd8(cr, incR)
                cg = qadd8(cg, incG)
                cb = qadd8(cb, incB)
                if cr == 255 || cg == 255 || cb == 255 {
                    strip.runtime.data[index] &= ~(UInt8(1) << bitNum)
                }
                strip.setPixelColor(i, cr, cg, cb)
            } else {
                cr = scale8(cr, 255 &- fadeDownAmount)
                cg = scale8(cg, 255 &- fadeDownAmount)
                cb = scale8(cb, 255 &- fadeDownAmount)
                strip.setPixelColor(i, cr, cg, cb)
            }
        }

        for _ in 0...(SEGLEN / 50) {
            if random8() <= strip.runtime.intensity {
                for _ in 0..<5 {
                    let i = Int(random16(UInt16(SEGLEN)))
                    if strip.getPixelColor(i) == 0 {
                        let col = strip.colorFromPalette(random8(), 64)
                        let index = i >> 3
                        let bitNum = i & 0x07
                        strip.runtime.data[index] |= UInt8(1) << bitNum
                        strip.setPixelColor(i, col)
                        break
                    }
                }
            }
        }
        return FRAMETIME_FIXED
    }

    static func effect_ripple(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }
        if !strip.runtime.check2 { strip.fill(strip.runtime.colors[1]) }
        else { strip.fadeOut(250) }
        return rippleBase(strip)
    }

    private static func rippleBase(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let maxRipples = min(1 + (SEGLEN >> 2), 100)
        let dataSize = 4 * maxRipples
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        for i in 0..<maxRipples {
            let d = i * 4
            var ripplestate = UInt32(strip.runtime.data[d])
            if ripplestate != 0 {
                let rippledecay = UInt32((strip.runtime.speed >> 4) + 1)
                let rippleorigin = Int(UInt16(strip.runtime.data[d+2]) | (UInt16(strip.runtime.data[d+3]) << 8))
                let col = strip.colorFromPalette(strip.runtime.data[d+1])
                let propagation = ((ripplestate / rippledecay).saturatingSubtract(1)) &* (UInt32(strip.runtime.speed) + 1)
                let propI = Int(propagation >> 8)
                let propF = propagation & 0xFF
                let amp: UInt8 = ripplestate < 17 ? triwave8(UInt8(clamping: Int(ripplestate - 1) * 8)) : UInt8(clamping: self.map(Int(ripplestate), 17, 255, 255, 2))

                for v in 0..<4 {
                    let mag = scale8(cubicwave8(UInt8(clamping: Int(propF >> 2) + v * 64)), amp)
                    let left = rippleorigin - propI - 1 + v
                    let right = rippleorigin + propI + 2 - v
                    if left >= 0 && left < SEGLEN {
                        strip.setPixelColor(left, colorBlend(strip.getPixelColor(left), col, mag))
                    }
                    if right >= 0 && right < SEGLEN {
                        strip.setPixelColor(right, colorBlend(strip.getPixelColor(right), col, mag))
                    }
                }

                ripplestate &+= rippledecay
                strip.runtime.data[d] = ripplestate > 254 ? 0 : UInt8(ripplestate)
            } else {
                if random16(5100 + 10000) <= UInt16(strip.runtime.intensity) {
                    strip.runtime.data[d] = 1
                    let pos = random16(UInt16(SEGLEN))
                    strip.runtime.data[d+2] = UInt8(pos & 0xFF)
                    strip.runtime.data[d+3] = UInt8(pos >> 8)
                    strip.runtime.data[d+1] = random8()
                }
            }
        }
        return FRAMETIME
    }

    static func effect_twinklefox(_ strip: WLEDEffectStrip) -> UInt16 {
        twinklefoxBase(strip, cat: false)
    }

    static func twinklefoxBase(_ strip: WLEDEffectStrip, cat: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        var prng16: UInt16 = 11337

        if strip.runtime.speed > 100 {
            strip.runtime.aux0 = UInt16(3 + ((255 - Int(strip.runtime.speed)) >> 3))
        } else {
            strip.runtime.aux0 = UInt16(22 + ((100 - Int(strip.runtime.speed)) >> 1))
        }

        let bg = strip.runtime.colors[1]
        let bgR = r(bg), bgG = g(bg), bgB = b(bg)
        let bglight = (UInt16(bgR) + UInt16(bgG) + UInt16(bgB)) / 3
        var bgScale: UInt8 = 86
        if bglight > 64 { bgScale = 16 }
        else if bglight > 16 { bgScale = 64 }
        let scaledBgR = UInt8((UInt16(bgR) * UInt16(bgScale)) >> 8)
        let scaledBgG = UInt8((UInt16(bgG) * UInt16(bgScale)) >> 8)
        let scaledBgB = UInt8((UInt16(bgB) * UInt16(bgScale)) >> 8)
        let scaledBg = rgbw32(scaledBgR, scaledBgG, scaledBgB)
        let backgroundBrightness = (UInt16(scaledBgR) + UInt16(scaledBgG) + UInt16(scaledBgB)) / 3

        for i in 0..<SEGLEN {
            prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
            let myclockoffset16 = prng16
            prng16 = UInt16(truncatingIfNeeded: UInt32(prng16) &* 2053 &+ 1384)
            let myspeedmultiplierQ5_3 = UInt32(((((prng16 & 0xFF) >> 4) + (prng16 & 0x0F)) & 0x0F) + 0x08)
            let myclock30 = (UInt32(strip.now) &* myspeedmultiplierQ5_3 >> 3) &+ UInt32(myclockoffset16)
            let myunique8 = UInt8(prng16 >> 8)

            let ticks = myclock30 / UInt32(max(1, strip.runtime.aux0))
            let fastcycle8 = UInt8(truncatingIfNeeded: ticks & 0xFF)
            var slowcycle16 = UInt16(truncatingIfNeeded: (ticks >> 8)) &+ UInt16(myunique8)
            slowcycle16 &+= UInt16(sin8(UInt8(truncatingIfNeeded: slowcycle16 & 0xFF)))
            slowcycle16 = UInt16(truncatingIfNeeded: UInt32(slowcycle16) &* 2053 &+ 1384)
            let slowcycle8 = UInt8(truncatingIfNeeded: (slowcycle16 & 0xFF) &+ (slowcycle16 >> 8))

            let twinkleDensity = (strip.runtime.intensity >> 5) + 1
            var bright: UInt8 = 0
            if ((slowcycle8 & 0x0E) / 2) < twinkleDensity {
                if cat {
                    bright = 255 &- fastcycle8
                } else {
                    if fastcycle8 < 86 {
                        bright = UInt8(clamping: Int(fastcycle8) * 3)
                    } else {
                        let ph = fastcycle8 &- 86
                        bright = UInt8(clamping: 255 - Int(ph) - Int(ph / 2))
                    }
                }
            }

            let hue = slowcycle8 &- myunique8
            var cr: UInt8 = 0, cg: UInt8 = 0, cb: UInt8 = 0
            if bright > 0 {
                let c = strip.colorFromPalette(hue, bright)
                cr = r(c); cg = g(c); cb = b(c)
                if !strip.runtime.check1 && fastcycle8 >= 128 {
                    let cooling = (fastcycle8 &- 128) >> 4
                    cg = qsub8(cg, cooling)
                    cb = qsub8(cb, cooling &* 2)
                }
            }

            let cbright = Int((UInt16(cr) + UInt16(cg) + UInt16(cb)) / 3)
            let deltabright = cbright - Int(backgroundBrightness)
            if deltabright >= 32 || scaledBg == 0 {
                strip.setPixelColor(i, cr, cg, cb)
            } else if deltabright > 0 {
                strip.setPixelColor(i, colorBlend(scaledBg, rgbw32(cr, cg, cb), UInt8(clamping: deltabright * 8)))
            } else {
                strip.setPixelColor(i, scaledBgR, scaledBgG, scaledBgB)
            }
        }
        return FRAMETIME
    }

    static func effect_solid_pattern(_ strip: WLEDEffectStrip) -> UInt16 {
        let lit = 1 + Int(strip.runtime.speed)
        let unlit = 1 + Int(strip.runtime.intensity)
        var drawingLit = true
        var cnt = 0

        for i in 0..<strip.segmentLength {
            let color: WLEDColor = drawingLit ? strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0) : strip.runtime.colors[1]
            strip.setPixelColor(i, color)
            cnt += 1
            if cnt >= (drawingLit ? lit : unlit) {
                cnt = 0
                drawingLit = !drawingLit
            }
        }
        return FRAMETIME
    }

    static func effect_solid_pattern_tri(_ strip: WLEDEffectStrip) -> UInt16 {
        let segSize = Int(strip.runtime.intensity >> 5) + 1
        var currSeg = 0
        var currSegCount = 0

        for i in 0..<strip.segmentLength {
            let colorIdx = currSeg % 3
            strip.setPixelColor(i, strip.runtime.colors[colorIdx])
            currSegCount += 1
            if currSegCount >= segSize {
                currSeg += 1
                currSegCount = 0
            }
        }
        return FRAMETIME
    }

    static func effect_candle(_ strip: WLEDEffectStrip) -> UInt16 {
        candleBase(strip, multi: false)
    }

    static func effect_candle_multi(_ strip: WLEDEffectStrip) -> UInt16 {
        candleBase(strip, multi: true)
    }

    private static func candleBase(_ strip: WLEDEffectStrip, multi: Bool) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if multi && SEGLEN > 1 {
            let dataSize = max(1, SEGLEN - 1) * 3
            if !strip.runtime.allocateData(dataSize) { return candleBase(strip, multi: false) }
        }

        let valrange = Int(strip.runtime.intensity)
        let rndval = valrange >> 1

        var speedFactor = 4
        if strip.runtime.speed > 252 { speedFactor = 1 }
        else if strip.runtime.speed > 99 { speedFactor = 2 }
        else if strip.runtime.speed > 49 { speedFactor = 3 }

        let numCandles = multi ? SEGLEN : 1

        for ci in 0..<numCandles {
            var s: Int, sTarget: Int, fadeStep: Int
            let d: Int

            if ci > 0 {
                d = (ci - 1) * 3
                s = Int(strip.runtime.data[d])
                sTarget = Int(strip.runtime.data[d + 1])
                fadeStep = Int(strip.runtime.data[d + 2])
            } else {
                d = -1
                s = Int(strip.runtime.aux0)
                sTarget = Int(strip.runtime.aux1)
                fadeStep = Int(strip.runtime.step)
            }

            if fadeStep == 0 {
                s = 128; sTarget = 130 + Int(random8(4)); fadeStep = 1
            }

            var newTarget = false
            if sTarget > s {
                s = Int(qadd8(UInt8(clamping: s), UInt8(clamping: fadeStep)))
                if s >= sTarget { newTarget = true }
            } else {
                s = Int(qsub8(UInt8(clamping: s), UInt8(clamping: fadeStep)))
                if s <= sTarget { newTarget = true }
            }

            if newTarget {
                sTarget = Int(random8(UInt8(clamping: rndval))) + Int(random8(UInt8(clamping: rndval)))
                if sTarget < rndval >> 1 { sTarget = (rndval >> 1) + Int(random8(UInt8(clamping: rndval))) }
                sTarget += 255 - valrange

                let dif = abs(sTarget - s)
                fadeStep = dif >> speedFactor
                if fadeStep == 0 { fadeStep = 1 }
            }

            if ci > 0 {
                let palColor = strip.colorFromPalette(i: ci, mapping: true, wrap: true, colorIndex: 0)
                strip.setPixelColor(ci, colorBlend(strip.runtime.colors[1], palColor, UInt8(clamping: s)))
                strip.runtime.data[d] = UInt8(clamping: s)
                strip.runtime.data[d + 1] = UInt8(clamping: sTarget)
                strip.runtime.data[d + 2] = UInt8(clamping: fadeStep)
            } else {
                for j in 0..<SEGLEN {
                    let palColor = strip.colorFromPalette(i: j, mapping: true, wrap: true, colorIndex: 0)
                    strip.setPixelColor(j, colorBlend(strip.runtime.colors[1], palColor, UInt8(clamping: s)))
                }
                strip.runtime.aux0 = UInt16(s)
                strip.runtime.aux1 = UInt16(sTarget)
                strip.runtime.step = UInt32(fadeStep)
            }
        }
        return FRAMETIME_FIXED
    }

    static func effect_plasma(_ strip: WLEDEffectStrip) -> UInt16 {
        if strip.runtime.call == 0 {
            strip.runtime.aux0 = UInt16(random8(0, 2))
        }
        let aux0 = UInt8(strip.runtime.aux0 & 0xFF)
        let thisPhase = Int8(truncatingIfNeeded: beatsin8(6 &+ aux0, 0, 255, now: strip.now)) - 64
        let thatPhase = Int8(truncatingIfNeeded: beatsin8(7 &+ aux0, 0, 255, now: strip.now)) - 64

        for i in 0..<strip.segmentLength {
            let factor1 = 2 + 3 * Int(strip.runtime.speed >> 5)
            let factor2 = 1 + 2 * Int(strip.runtime.speed >> 5)
            let colorIndex = UInt8(truncatingIfNeeded:
                (Int(cubicwave8(UInt8(truncatingIfNeeded: (i * factor1 + Int(thisPhase)) & 0xFF))) / 2 +
                 Int(cos8(UInt8(truncatingIfNeeded: (i * factor2 + Int(thatPhase)) & 0xFF))) / 2) & 0xFF)
            let beat = beatsin8(7, 0, UInt8(clamping: 128 - Int(strip.runtime.intensity >> 1)), now: strip.now)
            let thisBright = qsub8(colorIndex, beat)
            strip.setPixelColor(i, strip.colorFromPalette(colorIndex, thisBright))
        }
        return FRAMETIME
    }

    static func effect_percent(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        var percent = Int(strip.runtime.intensity)
        percent = min(max(percent, 0), 200)
        let activeLeds: Int
        if percent < 100 {
            activeLeds = Int(roundf(Float(SEGLEN) * Float(percent) / 100.0))
        } else {
            activeLeds = Int(roundf(Float(SEGLEN) * Float(200 - percent) / 100.0))
        }

        let size = strip.runtime.speed == 255 ? 255 : (1 + ((Int(strip.runtime.speed) * SEGLEN) >> 11))

        if percent <= 100 {
            for i in 0..<SEGLEN {
                if i < Int(strip.runtime.aux1) {
                    if strip.runtime.check1 {
                        strip.setPixelColor(i, strip.colorFromPalette(UInt8(clamping: self.map(percent, 0, 100, 0, 255))))
                    } else {
                        strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
                    }
                } else {
                    strip.setPixelColor(i, strip.runtime.colors[1])
                }
            }
        } else {
            for i in 0..<SEGLEN {
                if i < (SEGLEN - Int(strip.runtime.aux1)) {
                    strip.setPixelColor(i, strip.runtime.colors[1])
                } else {
                    if strip.runtime.check1 {
                        strip.setPixelColor(i, strip.colorFromPalette(UInt8(clamping: self.map(percent, 100, 200, 255, 0))))
                    } else {
                        strip.setPixelColor(i, strip.colorFromPalette(i: i, mapping: true, wrap: true, colorIndex: 0))
                    }
                }
            }
        }

        if activeLeds > Int(strip.runtime.aux1) {
            strip.runtime.aux1 &+= UInt16(size)
            if strip.runtime.aux1 > UInt16(activeLeds) { strip.runtime.aux1 = UInt16(activeLeds) }
        } else if activeLeds < Int(strip.runtime.aux1) {
            if strip.runtime.aux1 > UInt16(size) { strip.runtime.aux1 &-= UInt16(size) }
            else { strip.runtime.aux1 = 0 }
            if strip.runtime.aux1 < UInt16(activeLeds) { strip.runtime.aux1 = UInt16(activeLeds) }
        }
        return FRAMETIME
    }

    static func effect_ripple_rainbow(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }
        if strip.runtime.call == 0 {
            strip.runtime.aux0 = UInt16(random8())
            strip.runtime.aux1 = UInt16(random8())
        }
        if strip.runtime.aux0 == strip.runtime.aux1 {
            strip.runtime.aux1 = UInt16(random8())
        } else if strip.runtime.aux1 > strip.runtime.aux0 {
            strip.runtime.aux0 &+= 1
        } else {
            strip.runtime.aux0 &-= 1
        }
        let bg = colorBlend(colorWheel(UInt8(strip.runtime.aux0 & 0xFF)), BLACK, UInt8(235))
        strip.fill(bg)
        return rippleBase(strip)
    }

    static func effect_pacifica(_ strip: WLEDEffectStrip) -> UInt16 {
        var sCIStart1 = UInt32(strip.runtime.aux0)
        var sCIStart2 = UInt32(strip.runtime.aux1)
        var sCIStart3 = strip.runtime.step & 0xFFFF
        var sCIStart4 = strip.runtime.step >> 16

        let deltams = UInt32(FRAMETIME >> 2) + (UInt32(FRAMETIME) * UInt32(strip.runtime.speed)) / 128
        let speedfactor1 = beatsin16(3, 179, 269, now: strip.now)
        let speedfactor2 = beatsin16(4, 179, 269, now: strip.now)
        let deltams1 = (deltams &* UInt32(speedfactor1)) / 256
        let deltams2 = (deltams &* UInt32(speedfactor2)) / 256
        let deltams21 = (deltams1 &+ deltams2) / 2

        sCIStart1 &+= deltams1 &* UInt32(beatsin88(1011, 10, 13, now: strip.now))
        sCIStart2 = sCIStart2 &- deltams21 &* UInt32(beatsin88(777, 8, 11, now: strip.now))
        sCIStart3 = sCIStart3 &- deltams1 &* UInt32(beatsin88(501, 5, 7, now: strip.now))
        sCIStart4 = sCIStart4 &- deltams2 &* UInt32(beatsin88(257, 4, 6, now: strip.now))

        strip.runtime.aux0 = UInt16(truncatingIfNeeded: sCIStart1)
        strip.runtime.aux1 = UInt16(truncatingIfNeeded: sCIStart2)
        strip.runtime.step = (sCIStart4 << 16) | (sCIStart3 & 0xFFFF)

        let basethreshold = UInt8(beatsin8(9, 55, 65, now: strip.now))
        var wave = beat8(7, now: strip.now)

        for i in 0..<strip.segmentLength {
            let waveangle1 = UInt16(truncatingIfNeeded: UInt32(120 + UInt32(strip.runtime.intensity)) &* UInt32(i))
            let s1 = UInt16(Int32(sin16(waveangle1)) + 32768)
            let ci1 = UInt32(sCIStart1) &+ UInt32(i) &* UInt32(s1 >> 2)

            let waveangle2 = UInt16(truncatingIfNeeded: UInt32(80 + UInt32(strip.runtime.intensity)) &* UInt32(i))
            let s2 = UInt16(Int32(sin16(waveangle2)) + 32768)
            let ci2 = UInt32(sCIStart2) &+ UInt32(i) &* UInt32(s2 >> 2)

            let idx1 = UInt8(truncatingIfNeeded: (ci1 >> 8) & 0xFF)
            let idx2 = UInt8(truncatingIfNeeded: (ci2 >> 8) & 0xFF)

            var cr: UInt8 = 2, cg: UInt8 = 6, cb: UInt8 = 10
            cr = qadd8(cr, UInt8(clamping: Int(sin8(idx1)) >> 4))
            cg = qadd8(cg, UInt8(clamping: Int(sin8(idx1 &+ 64)) >> 2))
            cb = qadd8(cb, sin8(idx1 &+ 128))
            cr = qadd8(cr, UInt8(clamping: Int(sin8(idx2)) >> 4))
            cg = qadd8(cg, UInt8(clamping: Int(sin8(idx2 &+ 64)) >> 2))
            cb = qadd8(cb, UInt8(clamping: Int(sin8(idx2 &+ 128)) >> 1))

            let threshold = UInt8(clamping: Int(scale8(sin8(wave), 20)) + Int(basethreshold))
            wave &+= 7
            let l = (UInt16(cr) + UInt16(cg) + UInt16(cb)) / 3
            if l > UInt16(threshold) {
                let overage = UInt8(clamping: Int(l) - Int(threshold))
                let overage2 = qadd8(overage, overage)
                cr = qadd8(cr, overage)
                cg = qadd8(cg, overage2)
                cb = qadd8(cb, qadd8(overage2, overage2))
            }

            cb = scale8(cb, 145)
            cg = scale8(cg, 200)
            cr = max(cr, 2)
            cg = max(cg, 5)
            cb = max(cb, 7)

            strip.setPixelColor(i, cr, cg, cb)
        }
        return FRAMETIME
    }

    static func effect_solid_glitter(_ strip: WLEDEffectStrip) -> UInt16 {
        strip.fill(strip.runtime.colors[0])
        if strip.runtime.intensity > random8() {
            let glitterCol = strip.runtime.colors[2] != 0 ? strip.runtime.colors[2] : ULTRAWHITE
            strip.setPixelColor(Int(random16(UInt16(strip.segmentLength))), glitterCol)
        }
        return FRAMETIME
    }

    static func effect_sunrise(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        if strip.runtime.call == 0 || strip.runtime.speed != UInt8(strip.runtime.aux0 & 0xFF) {
            strip.runtime.step = strip.now
            strip.runtime.aux0 = UInt16(strip.runtime.speed)
        }

        strip.fill(BLACK)
        var stage: UInt16 = 0xFFFF

        let s10SinceStart = (strip.now &- strip.runtime.step) / 100

        if strip.runtime.speed > 120 {
            let counter = (strip.now >> 1) &* (UInt32((strip.runtime.speed &- 120) >> 1) + 1)
            stage = triwave16(UInt16(truncatingIfNeeded: counter & 0xFFFF))
        } else if strip.runtime.speed > 0 {
            var durMins = Int(strip.runtime.speed)
            if durMins > 60 { durMins -= 60 }
            let s10Target = UInt32(durMins * 600)
            let elapsed = min(s10SinceStart, s10Target)
            stage = UInt16(clamping: self.map(Int(elapsed), 0, Int(s10Target), 0, 0xFFFF))
            if strip.runtime.speed > 60 { stage = 0xFFFF &- stage }
        }

        for i in 0...(SEGLEN / 2) {
            let wave = triwave16(UInt16(clamping: (i * Int(stage)) / SEGLEN))
            var w = Int(wave >> 8) + ((Int(wave) * Int(strip.runtime.intensity)) >> 15)
            w = min(w, 240)

            let cr: UInt8, cg: UInt8, cb: UInt8
            if w < 60 {
                cr = UInt8(clamping: w * 4); cg = 0; cb = 0
            } else if w < 120 {
                cr = 255; cg = UInt8(clamping: (w - 60) * 4); cb = 0
            } else if w < 180 {
                cr = 255; cg = 255; cb = UInt8(clamping: (w - 120) * 4)
            } else {
                cr = 255; cg = 255; cb = 255
            }
            let c = rgbw32(cr, cg, cb)
            strip.setPixelColor(i, c)
            strip.setPixelColor(SEGLEN - i - 1, c)
        }
        return FRAMETIME
    }

    static func effect_phased(_ strip: WLEDEffectStrip) -> UInt16 {
        let allfreq: Int = 16
        let cutOff = Int(255 - strip.runtime.intensity)
        let modVal: Int = 5

        let index = UInt8(truncatingIfNeeded: (strip.now / 64) & 0xFF)

        let phaseInc = Int(strip.runtime.speed) / 32
        strip.runtime.step &+= UInt32(phaseInc)
        let phase = Int(strip.runtime.step)

        for i in 0..<strip.segmentLength {
            let val = (i + 1) * allfreq
            let m = modVal == 0 ? 1 : modVal
            let adjusted = val + phase * ((i % m) + 1) / 2
            let bWave = Int(cubicwave8(UInt8(truncatingIfNeeded: adjusted & 0xFF)))
            let bVal = bWave > cutOff ? UInt8(clamping: bWave - cutOff) : UInt8(0)

            let palColor = strip.colorFromPalette(UInt8(truncatingIfNeeded: (Int(index) + i * 256 / max(1, strip.segmentLength)) & 0xFF))
            strip.setPixelColor(i, colorBlend(strip.runtime.colors[1], palColor, bVal))
        }
        return FRAMETIME
    }

    static func effect_flow(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        var counter: Int = 0
        if strip.runtime.speed != 0 {
            counter = Int((UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 2) + 1)) >> 8)
        }

        var maxZones = SEGLEN / 6
        if maxZones < 1 { maxZones = 1 }
        var zones = (Int(strip.runtime.intensity) * maxZones) >> 8
        if zones & 0x01 != 0 { zones += 1 }
        if zones < 2 { zones = 2 }
        let zoneLen = SEGLEN / zones
        let offset = (SEGLEN - zones * zoneLen) >> 1

        let bgIdx = UInt8(truncatingIfNeeded: (-counter) & 0xFF)
        strip.fill(strip.colorFromPalette(bgIdx))

        for z in 0..<zones {
            let pos = offset + z * zoneLen
            for i in 0..<zoneLen {
                let colorIndex = (i * 255 / max(1, zoneLen)) - counter
                let led = (z & 0x01 != 0) ? i : (zoneLen - 1 - i)
                strip.setPixelColor(pos + led, strip.colorFromPalette(UInt8(truncatingIfNeeded: colorIndex & 0xFF)))
            }
        }
        return FRAMETIME
    }

    static func effect_chunchun(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        if SEGLEN == 1 { return effect_solid(strip) }

        strip.fadeOut(254)
        var counter = UInt32(strip.now) &* (6 + UInt32(strip.runtime.speed >> 4))
        let numBirds = 2 + (SEGLEN >> 3)
        let span = (UInt32(strip.runtime.intensity) << 8) / UInt32(numBirds)

        for i in 0..<numBirds {
            counter &-= span
            let megumin = UInt32(Int32(sin16(UInt16(truncatingIfNeeded: counter & 0xFFFF))) + 0x8000)
            var bird = Int((megumin &* UInt32(SEGLEN)) >> 16)
            bird = min(max(bird, 0), SEGLEN - 1)
            let c = strip.colorFromPalette(UInt8(clamping: (i * 255) / numBirds))
            strip.setPixelColor(bird, c)
        }
        return FRAMETIME
    }

    static func effect_blends(_ strip: WLEDEffectStrip) -> UInt16 {
        let SEGLEN = strip.segmentLength
        let pixelLen = min(SEGLEN, 255)
        let dataSize = 4 * (pixelLen + 1)
        if !strip.runtime.allocateData(dataSize) { return effect_solid(strip) }

        let blendSpeed = UInt8(clamping: self.map(Int(strip.runtime.intensity), 0, 255, 10, 128))
        var shift = Int((UInt32(strip.now) &* (UInt32(strip.runtime.speed >> 3) + 1)) >> 8)

        func readPixel(_ idx: Int) -> WLEDColor {
            let o = idx * 4
            guard o + 3 < strip.runtime.data.count else { return 0 }
            return UInt32(strip.runtime.data[o]) |
                   (UInt32(strip.runtime.data[o+1]) << 8) |
                   (UInt32(strip.runtime.data[o+2]) << 16) |
                   (UInt32(strip.runtime.data[o+3]) << 24)
        }
        func writePixel(_ idx: Int, _ c: WLEDColor) {
            let o = idx * 4
            guard o + 3 < strip.runtime.data.count else { return }
            strip.runtime.data[o] = UInt8(c & 0xFF)
            strip.runtime.data[o+1] = UInt8((c >> 8) & 0xFF)
            strip.runtime.data[o+2] = UInt8((c >> 16) & 0xFF)
            strip.runtime.data[o+3] = UInt8((c >> 24) & 0xFF)
        }

        for i in 0..<pixelLen {
            let target = strip.colorFromPalette(UInt8(truncatingIfNeeded: (shift + Int(quadwave8(UInt8(clamping: (i + 1) * 16)))) & 0xFF))
            let blended = colorBlend(readPixel(i), target, blendSpeed)
            writePixel(i, blended)
            shift += 3
        }

        var offset = 0
        for i in 0..<SEGLEN {
            strip.setPixelColor(i, readPixel(offset))
            offset += 1
            if offset > pixelLen { offset = 0 }
        }
        return FRAMETIME
    }
}

private extension UInt32 {
    func saturatingSubtract(_ other: UInt32) -> UInt32 {
        self > other ? self &- other : 0
    }
}

extension WLEDEffectStrip {
    func blur2D(_ amount: UInt8) { blur2d(amount) }
    
    func addPixelColorXY(_ x: Int, _ y: Int, _ color: WLEDColor) {
        guard matrixWidth > 0 && matrixHeight > 0 else { return }
        guard x >= 0 && x < matrixWidth && y >= 0 && y < matrixHeight else { return }
        let idx = y * matrixWidth + x
        let existing = leds[idx]
        let r = min(255, UInt32((existing >> 16) & 0xFF) + UInt32((color >> 16) & 0xFF))
        let g = min(255, UInt32((existing >> 8) & 0xFF) + UInt32((color >> 8) & 0xFF))
        let b = min(255, UInt32(existing & 0xFF) + UInt32(color & 0xFF))
        leds[idx] = (r << 16) | (g << 8) | b
    }

    func fadePixelColorXY(_ x: Int, _ y: Int, _ fade: UInt8) {
        guard matrixWidth > 0 && matrixHeight > 0 else { return }
        guard x >= 0 && x < matrixWidth && y >= 0 && y < matrixHeight else { return }
        let idx = y * matrixWidth + x
        let c = leds[idx]
        let f = UInt32(fade)
        let r = ((c >> 16) & 0xFF) * f / 256
        let g = ((c >> 8) & 0xFF) * f / 256
        let b = (c & 0xFF) * f / 256
        leds[idx] = (r << 16) | (g << 8) | b
    }

    func fillCircle(_ cx: Int, _ cy: Int, _ radius: Int, _ color: WLEDColor) {
        for y in max(0, cy - radius)...min(matrixHeight - 1, cy + radius) {
            for x in max(0, cx - radius)...min(matrixWidth - 1, cx + radius) {
                let dx = x - cx, dy = y - cy
                if dx * dx + dy * dy <= radius * radius {
                    setPixelColorXY(x, y, color)
                }
            }
        }
    }

    func wuPixel(_ x: Int, _ y: Int, _ color: WLEDColor) {
        let xI = x >> 8, yI = y >> 8
        let xF = UInt8(x & 0xFF), yF = UInt8(y & 0xFF)
        let ixF = 255 &- xF, iyF = 255 &- yF
        addPixelColorXY(xI, yI, scaleColor(color, UInt8((UInt16(ixF) * UInt16(iyF)) >> 8)))
        addPixelColorXY(xI + 1, yI, scaleColor(color, UInt8((UInt16(xF) * UInt16(iyF)) >> 8)))
        addPixelColorXY(xI, yI + 1, scaleColor(color, UInt8((UInt16(ixF) * UInt16(yF)) >> 8)))
        addPixelColorXY(xI + 1, yI + 1, scaleColor(color, UInt8((UInt16(xF) * UInt16(yF)) >> 8)))
    }

    private func scaleColor(_ c: WLEDColor, _ s: UInt8) -> WLEDColor {
        let scale = UInt32(s)
        let r = ((c >> 16) & 0xFF) * scale / 256
        let g = ((c >> 8) & 0xFF) * scale / 256
        let b = (c & 0xFF) * scale / 256
        return (r << 16) | (g << 8) | b
    }

    func XY(_ x: Int, _ y: Int) -> Int {
        let cx = min(max(x, 0), matrixWidth - 1)
        let cy = min(max(y, 0), matrixHeight - 1)
        return cy * matrixWidth + cx
    }
}
