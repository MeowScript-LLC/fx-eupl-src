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
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-05.
 */

import SwiftUI

extension EffectSimulationEngine {

    private func octopus2D(fx: Double, fy: Double, W: Double, H: Double,
                           time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let cx = W / 2 + (custom1 - 128) * W / 255
        let cy = H / 2 + (custom2 - 128) * H / 255
        let dx = fx - cx, dy = fy - cy
        let dist = sqrt(dx * dx + dy * dy)
        let angle = atan2(dy, dx)
        let mapp = 180.0 / max(W, H)
        let radius = dist * mapp
        let legs = custom3 / 4 + 1
        let step = time * (speed / 32 + 1) * 8
        let innerSin = sin((angle * 4 - radius) / 4 * .pi / 128 + step / 2)
        let outerSin = sin(innerSin * 128 + radius - step + angle * legs * .pi / 128)
        let intensityVal = (outerSin + 1) / 2
        let bri = intensityVal * intensityVal
        let palPos = fmod(abs(step / 2 - radius) / 255, 1.0)
        let c = paletteRGBAt(palPos)
        return (c.r * bri, c.g * bri, c.b * bri)
    }

    private func metaballs2D(fx: Double, fy: Double, W: Double, H: Double,
                             time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let numBalls = 3 + Int(ni * 3)
        var totalField = 0.0
        for i in 0..<numBalls {
            let phase = time * ns * (0.5 + Double(i) * 0.3) + Double(i) * 1.7
            let bx = (sin(phase) + 1) / 2 * W
            let by = (cos(phase * 0.7 + Double(i)) + 1) / 2 * H
            let dx = fx - bx, dy = fy - by
            let dist = max(0.5, sqrt(dx * dx + dy * dy))
            let radius = W * (0.08 + ni * 0.15)
            totalField += radius / dist
        }
        let threshold = 1.5 + (1.0 - ni) * 1.5
        let clamped = min(1.0, totalField / threshold)
        return paletteRGBAt(fmod(clamped, 1.0))
    }

    private func tartan2D(fx: Double, fy: Double, W: Double, H: Double,
                          time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let offsetX = beatsin16(bpm: 3, lo: -360, hi: 360, time: time)
        let offsetY = beatsin16(bpm: 2, lo: -360, hi: 360, time: time)
        let sharpness = Int(custom3) / 8

        let hue1 = fx * beatsin16(bpm: 10, lo: 1, hi: 10, time: time) + offsetY
        var bri1 = sin8(fmod(abs(fx * speed / 2 + offsetX), 256)) / 255.0
        var intens1 = bri1
        for _ in 0..<sharpness { intens1 *= bri1 }

        let hue2 = fy * 3 + offsetX
        var bri2 = sin8(fmod(abs(fy * intensity / 2 + offsetY), 256)) / 255.0
        var intens2 = bri2
        for _ in 0..<sharpness { intens2 *= bri2 }

        let c1 = paletteRGBAt(fmod(abs(hue1) / 256, 1.0))
        let c2 = paletteRGBAt(fmod(abs(hue2) / 256, 1.0))
        return (min(1, c1.r * intens1 + c2.r * intens2),
                min(1, c1.g * intens1 + c2.g * intens2),
                min(1, c1.b * intens1 + c2.b * intens2))
    }

    private func julia2D(fx: Double, fy: Double, W: Double, H: Double,
                         time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let xymag = max(0.01, min(1.0, Double(custom3) / 31.0))
        let xcen = (custom1 - 128) / 100.0
        let ycen = (custom2 - 128) / 100.0
        let xmin = max(-1.2, xcen - xymag)
        let xmax = min(1.2, xcen + xymag)
        let ymin = max(-0.8, ycen - xymag)
        let ymax = min(1.0, ycen + xymag)

        let dx = (xmax - xmin) / W
        let dy = (ymax - ymin) / H
        let x = xmin + fx * dx
        let y = ymin + fy * dy

        let maxIter = max(4, Int(intensity / 2))

        let reAl = -0.94299 + sin(time * 34 / 1000) * 0.015
        let imAg = 0.3162 + sin(time * 26 / 1000) * 0.015

        var a = x, b = y
        var iter = 0
        while iter < maxIter {
            let aa = a * a, bb = b * b
            if aa + bb > 16 { break }
            b = 2 * a * b + imAg
            a = aa - bb + reAl
            iter += 1
        }
        if iter == maxIter { return (0, 0, 0) }
        let pos = Double(iter) / Double(maxIter)
        return paletteRGBAt(pos)
    }

    private func dna2D(fx: Double, fy: Double, W: Double, H: Double,
                       time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let yNorm = fy / H
        let phase = yNorm * .pi * 4 + time * ns * 3
        let strand1X = W / 2 + sin(phase) * W * 0.3
        let strand2X = W / 2 - sin(phase) * W * 0.3

        let d1 = abs(fx - strand1X)
        let d2 = abs(fx - strand2X)

        let rungSpacing = max(2.0, H / (4 + intensity / 32))
        let rungPhase = fmod(fy + time * ns * 10, rungSpacing)
        let isRung = rungPhase < 1.5

        if d1 < 1.5 {
            return paletteRGBAt(0.0)
        } else if d2 < 1.5 {
            return paletteRGBAt(0.5)
        } else if isRung {
            let minX = min(strand1X, strand2X)
            let maxX = max(strand1X, strand2X)
            if fx >= minX && fx <= maxX {
                let rungPos = (fx - minX) / max(1, maxX - minX)
                return paletteRGBAt(fmod(rungPos + yNorm, 1.0))
            }
        }
        return (0, 0, 0)
    }

    private func matrixRain2D(x: Int, y: Int, W: Double, H: Double,
                              time: TimeInterval, ns: Double,
                              fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let colSeed = hashRand(Double(x) * 127.1, 311.7)
        let fallSpeed = (0.5 + colSeed * 1.5) * ns * 8
        let headY = fmod(time * fallSpeed + colSeed * H, H * 1.5) - H * 0.25
        let dist = headY - Double(y)

        if dist < 0 || dist > H * 0.6 { return (0, 0, 0) }

        let trailFade = 0.3 + (custom1 / 255.0) * 0.5
        let tailLen = H * (trailFade + intensity / 510.0)
        let bri: Double
        if dist < 1 {
            bri = 1.0
        } else if dist < tailLen {
            bri = max(0, 1 - dist / tailLen) * 0.7
        } else {
            return (0, 0, 0)
        }
        let charFlicker = hashRand(Double(x) * 43.758 + Double(y) * 12.989, floor(time * 8))
        let flickerBri = bri * (0.6 + charFlicker * 0.4)

        if paletteId > 5 {
            let c = paletteRGBAt(fmod(dist / tailLen, 1.0))
            return (c.r * flickerBri, c.g * flickerBri, c.b * flickerBri)
        }
        return (flickerBri * 0.1, flickerBri * 0.9, flickerBri * 0.15)
    }

    private func lissajous2D(x: Int, y: Int, W: Double, H: Double,
                             time: TimeInterval, ns: Double,
                             fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        if let fb = fb {
            let existing = fb.getPixel(x: x, y: y)
            let fadeFactor = max(0.5, 1.0 - intensity / 255.0 * 0.3)
            let faded = (existing.r * fadeFactor, existing.g * fadeFactor, existing.b * fadeFactor)

            let a = 3.0 + intensity / 64.0
            let b = 2.0
            let c3spd = 1.0 + custom3
            let phase = time * c3spd / 32.0
            let dotX = sin8(fmod(phase / 2 + Double(0) * speed / 32, 256)) * (W - 1) / 255
            let dotY = cos8(fmod(phase / 2, 256)) * (H - 1) / 255

            if abs(Double(x) - dotX) < 1.2 && abs(Double(y) - dotY) < 1.2 {
                let c = paletteRGBAt(fmod(time * ns * 0.1, 1.0))
                fb.setPixel(x: x, y: y, color: c)
                return c
            }
            fb.setPixel(x: x, y: y, color: faded)
            return faded
        }
        let a = 3.0 + intensity / 64.0
        let bParam = 2.0
        let delta = time * ns * 0.5
        var bestBri = 0.0
        var bestPos = 0.0
        for step in 0..<20 {
            let t = time * ns - Double(step) * 0.02
            let dotX = (sin(a * t + delta) + 1) / 2 * (W - 1)
            let dotY = (sin(bParam * t) + 1) / 2 * (H - 1)
            let dist = sqrt(pow(Double(x) - dotX, 2) + pow(Double(y) - dotY, 2))
            if dist < 1.5 {
                let bri = max(0, 1 - Double(step) / 20.0)
                if bri > bestBri { bestBri = bri; bestPos = Double(step) / 20.0 }
            }
        }
        if bestBri > 0.01 {
            let c = paletteRGBAt(fmod(bestPos + time * ns * 0.1, 1.0))
            return (c.r * bestBri, c.g * bestBri, c.b * bestBri)
        }
        return (0, 0, 0)
    }

    private func pulser2D(fx: Double, fy: Double, W: Double, H: Double,
                          time: TimeInterval, ns: Double,
                          fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let cx = W / 2, cy = H / 2
        let ix = Int(fx), iy = Int(fy)
        let dist = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))
        let maxDist = sqrt(cx * cx + cy * cy)
        let pulseRadius = (sin(time * ns * 2) + 1) / 2 * maxDist
        let ringDist = abs(dist - pulseRadius)
        let ringWidth = 1.0 + ni * 2.0
        if ringDist < ringWidth {
            let bri = max(0, 1 - ringDist / ringWidth)
            let c = paletteRGBAt(fmod(dist / maxDist + time * ns * 0.1, 1.0))
            let result = (c.r * bri, c.g * bri, c.b * bri)
            fb?.setPixel(x: ix, y: iy, color: result)
            return result
        }
        if let fb = fb {
            let existing = fb.getPixel(x: ix, y: iy)
            let fadeFactor = 0.8 + ni * 0.15
            let faded = (existing.r * fadeFactor, existing.g * fadeFactor, existing.b * fadeFactor)
            fb.setPixel(x: ix, y: iy, color: faded)
            return faded
        }
        return (0, 0, 0)
    }

    private func swirl2D(fx: Double, fy: Double, W: Double, H: Double,
                         time: TimeInterval, ns: Double,
                         fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let bw = 2.0
        let blurFade = max(0.5, 1.0 - custom1 / 255.0 * 0.3)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            var rr = e.r * blurFade, gg = e.g * blurFade, bb = e.b * blurFade
            let spd = speed / 255.0
            let dotI = beatsin8(bpm: 27 * spd, lo: bw, hi: W - 1 - bw, time: time)
            let dotJ = beatsin8(bpm: 41 * spd, lo: bw, hi: H - 1 - bw, time: time)
            let ni = Double(Int(W) - 1) - dotI
            let nj = Double(Int(W) - 1) - dotJ
            let dots: [(dx: Double, dy: Double, palT: Double)] = [
                (dotI, dotJ, 11), (dotJ, dotI, 13), (ni, nj, 17),
                (nj, ni, 29), (dotI, nj, 37), (ni, dotJ, 41)
            ]
            let dotBri = intensity / 255.0 * 0.8 + 0.2
            for dot in dots {
                let dd = sqrt((fx - dot.dx) * (fx - dot.dx) + (fy - dot.dy) * (fy - dot.dy))
                if dd < 1.5 {
                    let bri = max(0, 1 - dd / 1.5) * dotBri
                    let cc = paletteRGBAt(fmod(time * 1000 / dot.palT / 255, 1.0))
                    rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri)
                }
            }
            let result = (rr, gg, bb); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        let spd = speed / 255.0
        let dotI = beatsin8(bpm: 27 * spd, lo: bw, hi: W - 1 - bw, time: time)
        let dotJ = beatsin8(bpm: 41 * spd, lo: bw, hi: H - 1 - bw, time: time)
        let ni = Double(Int(W) - 1) - dotI; let nj = Double(Int(W) - 1) - dotJ
        let dots: [(dx: Double, dy: Double, palT: Double)] = [
            (dotI, dotJ, 11), (dotJ, dotI, 13), (ni, nj, 17),
            (nj, ni, 29), (dotI, nj, 37), (ni, dotJ, 41)
        ]
        var bestBri = 0.0, bestPal = 0.0
        let dotBri = intensity / 255.0 * 0.8 + 0.2
        for dot in dots {
            let dd = sqrt((fx - dot.dx) * (fx - dot.dx) + (fy - dot.dy) * (fy - dot.dy))
            if dd < 1.5 { let bri = max(0, 1 - dd / 1.5) * dotBri; if bri > bestBri { bestBri = bri
                bestPal = fmod(time * 1000 / dot.palT / 255, 1.0) } }
        }
        if bestBri > 0.01 { let cc = paletteRGBAt(bestPal); return (cc.r * bestBri, cc.g * bestBri, cc.b * bestBri) }
        return (0, 0, 0)
    }

    private func blackHole2D(fx: Double, fy: Double, W: Double, H: Double,
                             time: TimeInterval, ns: Double,
                             fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let cx = W / 2, cy = H / 2
        let ix = Int(fx), iy = Int(fy)
        let t = time * 1000.0 / 128.0
        let fadeFactor = max(0.5, 1.0 - (16 + speed / 8) / 255.0)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            var rr = e.r * fadeFactor, gg = e.g * fadeFactor, bb = e.b * fadeFactor
            for i in 0..<8 {
                let xPh: Double = (i % 2 == 1) ? 128 : 0
                let yPh: Double = (i % 2 == 1) ? 192 : 64
                let sx = beatsin8(bpm: custom1 / 8, lo: 0, hi: W - 1, time: time, phase: xPh + t * Double(i))
                let sy = beatsin8(bpm: intensity / 8, lo: 0, hi: H - 1, time: time, phase: yPh + t * Double(i))
                let dd = sqrt((fx - sx) * (fx - sx) + (fy - sy) * (fy - sy))
                if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); let cc = paletteRGBAt(fmod(Double(i) * 32 / 255, 1.0))
                    rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri) }
            }
            for i in 0..<4 {
                let xPh: Double = (i % 2 == 1) ? 128 : 0
                let yPh: Double = (i % 2 == 1) ? 192 : 64
                let sx = beatsin8(bpm: custom2 / 8, lo: W / 4, hi: W * 3 / 4, time: time, phase: xPh + t * Double(i))
                let sy = beatsin8(bpm: custom3, lo: H / 4, hi: H * 3 / 4, time: time, phase: yPh + t * Double(i))
                let dd = sqrt((fx - sx) * (fx - sx) + (fy - sy) * (fy - sy))
                if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); let cc = paletteRGBAt(fmod((255 - Double(i) * 64) / 255, 1.0))
                    rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri) }
            }
            let dC = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))
            if dC < 1.0 { rr = 1; gg = 1; bb = 1 }
            let result = (rr, gg, bb); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var rr = 0.0, gg = 0.0, bb = 0.0
        for i in 0..<8 {
            let xPh: Double = (i % 2 == 1) ? 128 : 0; let yPh: Double = (i % 2 == 1) ? 192 : 64
            let sx = beatsin8(bpm: custom1 / 8, lo: 0, hi: W - 1, time: time, phase: xPh + t * Double(i))
            let sy = beatsin8(bpm: intensity / 8, lo: 0, hi: H - 1, time: time, phase: yPh + t * Double(i))
            let dd = sqrt((fx - sx) * (fx - sx) + (fy - sy) * (fy - sy))
            if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); let cc = paletteRGBAt(fmod(Double(i) * 32 / 255, 1.0))
                rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri) }
        }
        for i in 0..<4 {
            let xPh: Double = (i % 2 == 1) ? 128 : 0; let yPh: Double = (i % 2 == 1) ? 192 : 64
            let sx = beatsin8(bpm: custom2 / 8, lo: W / 4, hi: W * 3 / 4, time: time, phase: xPh + t * Double(i))
            let sy = beatsin8(bpm: custom3, lo: H / 4, hi: H * 3 / 4, time: time, phase: yPh + t * Double(i))
            let dd = sqrt((fx - sx) * (fx - sx) + (fy - sy) * (fy - sy))
            if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); let cc = paletteRGBAt(fmod((255 - Double(i) * 64) / 255, 1.0))
                rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri) }
        }
        let dC = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))
        if dC < 1.0 { return (1, 1, 1) }
        return (rr, gg, bb)
    }

    private func frizzle2D(fx: Double, fy: Double, W: Double, H: Double,
                           time: TimeInterval, ns: Double,
                           fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let fadeFactor = max(0.5, 1.0 - (16 + (option1 ? 10 : 0)) / 255.0 - custom1 / 1024.0)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            var rr = e.r * fadeFactor, gg = e.g * fadeFactor, bb = e.b * fadeFactor
            for i in stride(from: 8, through: 1, by: -1) {
                let dx = beatsin8(bpm: speed / 8 + Double(i), lo: 0, hi: W - 1, time: time)
                let dy = beatsin8(bpm: intensity / 8 - Double(i), lo: 0, hi: H - 1, time: time)
                let dd = sqrt((fx - dx) * (fx - dx) + (fy - dy) * (fy - dy))
                if dd < 1.5 {
                    let bri = max(0, 1 - dd / 1.5)
                    let cc = paletteRGBAt(fmod(beatsin8(bpm: 12, time: time) / 255, 1.0))
                    rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri)
                }
            }
            let result = (rr, gg, bb); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var bestBri = 0.0, bestPal = 0.0
        for i in stride(from: 8, through: 1, by: -1) {
            let dx = beatsin8(bpm: speed / 8 + Double(i), lo: 0, hi: W - 1, time: time)
            let dy = beatsin8(bpm: intensity / 8 - Double(i), lo: 0, hi: H - 1, time: time)
            let dd = sqrt((fx - dx) * (fx - dx) + (fy - dy) * (fy - dy))
            if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); if bri > bestBri { bestBri = bri
                bestPal = fmod(beatsin8(bpm: 12, time: time) / 255, 1.0) } }
        }
        if bestBri > 0.01 { let cc = paletteRGBAt(bestPal); return (cc.r * bestBri, cc.g * bestBri, cc.b * bestBri) }
        return (0, 0, 0)
    }

    private func squaredSwirl2D(fx: Double, fy: Double, W: Double, H: Double,
                                time: TimeInterval, ns: Double,
                                fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let bw = 2.0
        let fadeFactor = max(0.5, 1.0 - (1 + intensity / 5) / 255.0)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            var rr = e.r * fadeFactor, gg = e.g * fadeFactor, bb = e.b * fadeFactor
            let dots: [(bpmX: Double, bpmY: Double, palDiv: Double)] = [
                (19, 18, 29), (22, 15, 41), (17, 20, 73)
            ]
            for dot in dots {
                let dx = beatsin8(bpm: dot.bpmX, lo: bw, hi: W - 1 - bw, time: time)
                let dy = beatsin8(bpm: dot.bpmY, lo: bw, hi: H - 1 - bw, time: time)
                let dd = sqrt((fx - dx) * (fx - dx) + (fy - dy) * (fy - dy))
                if dd < 1.5 {
                    let bri = max(0, 1 - dd / 1.5)
                    let cc = paletteRGBAt(fmod(time * 1000 / dot.palDiv / 255, 1.0))
                    rr = min(1, rr + cc.r * bri); gg = min(1, gg + cc.g * bri); bb = min(1, bb + cc.b * bri)
                }
            }
            let result = (rr, gg, bb); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var bestBri = 0.0, bestPal = 0.0
        let dots: [(bpmX: Double, bpmY: Double, palDiv: Double)] = [
            (19, 18, 29), (22, 15, 41), (17, 20, 73)
        ]
        for dot in dots {
            let dx = beatsin8(bpm: dot.bpmX, lo: bw, hi: W - 1 - bw, time: time)
            let dy = beatsin8(bpm: dot.bpmY, lo: bw, hi: H - 1 - bw, time: time)
            let dd = sqrt((fx - dx) * (fx - dx) + (fy - dy) * (fy - dy))
            if dd < 1.5 { let bri = max(0, 1 - dd / 1.5); if bri > bestBri { bestBri = bri
                bestPal = fmod(time * 1000 / dot.palDiv / 255, 1.0) } }
        }
        if bestBri > 0.01 { let cc = paletteRGBAt(bestPal); return (cc.r * bestBri, cc.g * bestBri, cc.b * bestBri) }
        return (0, 0, 0)
    }

    private func gameOfLife2D(x: Int, y: Int, width: Int, height: Int,
                              time: TimeInterval, ns: Double,
                              fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        guard let fb = fb else {
            let alive = hashRand(Double(x) * 127.1 + Double(y) * 311.7, floor(time * ns * 2)) > 0.5
            if alive {
                let c = paletteRGBAt(fmod(Double(x + y) / Double(width + height), 1.0))
                return c
            }
            return (0, 0, 0)
        }

        let genRate = max(1.0, 6.0 - ns * 4)
        let currentGen = Int(time / (genRate / 12.0))

        if fb.generation == 0 {
            let density = 0.15 + (intensity / 255.0) * 0.4
            let palette = cachedPaletteRGB
            fb.seedRandom(density: density, palette: palette)
        }

        if x == 0 && y == 0 && currentGen > fb.generation {
            var snapshot = Array(repeating: Array(repeating: false, count: width), count: height)
            for sy in 0..<height {
                for sx in 0..<width {
                    let p = fb.getPixel(x: sx, y: sy)
                    snapshot[sy][sx] = (p.r + p.g + p.b) > 0.1
                }
            }

            var totalAlive = 0

            let palette = cachedPaletteRGB
            for sy in 0..<height {
                for sx in 0..<width {
                    var neighbors = 0
                    for dy in -1...1 {
                        for dx in -1...1 {
                            if dx == 0 && dy == 0 { continue }
                            let nx = (sx + dx + width) % width
                            let ny = (sy + dy + height) % height
                            if snapshot[ny][nx] { neighbors += 1 }
                        }
                    }
                    let alive = snapshot[sy][sx]
                    let mutate = option3 && hashRand(Double(sx * 127 + sy * 311), Double(currentGen)) < 0.01
                    let willLive = alive ? (neighbors == 2 || neighbors == 3) : (neighbors == 3 || (mutate && neighbors == 2))
                    if willLive {
                        let gen = fmod(Double(currentGen) * 0.05, 1.0)
                        let pos = fmod(gen + Double(neighbors) / 8.0, 1.0)
                        let c = palette.isEmpty ? (r: 0.0, g: 1.0, b: 0.0) :
                            palette[Int(pos * Double(palette.count - 1)) % palette.count]
                        fb.setPixel(x: sx, y: sy, color: c)
                        totalAlive += 1
                    } else {
                        fb.setPixel(x: sx, y: sy, color: (0, 0, 0))
                    }
                }
            }

            let totalCells = width * height
            if totalAlive < totalCells / 20 {
                let density = 0.15 + (intensity / 255.0) * 0.4
                fb.seedRandom(density: density, palette: palette)
            } else {
                fb.incrementGeneration()
            }
        }

        return fb.getPixel(x: x, y: y)
    }

    @inline(__always)
    private func fract(_ x: Double) -> Double { x - floor(x) }

    private func sin8(_ theta: Double) -> Double {
        (sin(theta / 255.0 * 2.0 * .pi) + 1.0) / 2.0 * 255.0
    }

    private func cos8(_ theta: Double) -> Double { sin8(theta + 64) }

    private func beatsin8(bpm: Double, lo: Double = 0, hi: Double = 255,
                          time: TimeInterval, phase: Double = 0) -> Double {
        let beat = fmod(time * bpm / 60.0, 1.0) * 255.0
        return lo + sin8(beat + phase) / 255.0 * (hi - lo)
    }

    private func beatsin16(bpm: Double, lo: Double = 0, hi: Double = 65535,
                           time: TimeInterval, phase: Double = 0) -> Double {
        let beat = fmod(time * bpm / 60.0, 1.0) * 65535.0
        let sinVal = (sin((beat + phase) / 65535.0 * 2.0 * .pi) + 1.0) / 2.0
        return lo + sinVal * (hi - lo)
    }

    private func perlin8_2d(_ x: Double, _ y: Double) -> Double {
        let n1 = sin(x * 0.0625 + y * 0.0937) * cos(y * 0.0729 - x * 0.0531)
        let n2 = sin(x * 0.1113 - y * 0.0812) * cos(x * 0.0622 + y * 0.0879)
        let n3 = sin((x + y) * 0.0537) * cos((x - y) * 0.0741)
        return ((n1 + n2 + n3) / 3.0 + 1.0) / 2.0 * 255.0
    }

    private func perlin8_3d(_ x: Double, _ y: Double, _ z: Double) -> Double {
        let n1 = sin(x * 0.0625 + z * 0.0937) * cos(y * 0.0729 - z * 0.0531)
        let n2 = sin(y * 0.1113 + z * 0.0812) * cos(x * 0.0622 - z * 0.0379)
        let n3 = sin((x + y) * 0.0537 + z * 0.073) * cos((x - y) * 0.0441 - z * 0.057)
        let n4 = sin(x * 0.043 - y * 0.067 + z * 0.0831)
        return ((n1 + n2 + n3 + n4) / 4.0 + 1.0) / 2.0 * 255.0
    }

    private func hueToRGB(_ h: Double) -> (r: Double, g: Double, b: Double) {
        let hue = fmod(h, 1.0) * 6.0
        let x = 1.0 - abs(fmod(hue, 2.0) - 1.0)
        switch Int(hue) {
        case 0: return (1, x, 0); case 1: return (x, 1, 0)
        case 2: return (0, 1, x); case 3: return (0, x, 1)
        case 4: return (x, 0, 1); default: return (1, 0, x)
        }
    }

    private func waverly2D(fx: Double, fy: Double, W: Double, H: Double,
                           time: TimeInterval, ns: Double,
                           fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy), rows = Int(H)
        if let fb = fb {
            let fadeFactor = 1.0 - (speed / 255.0) * 0.15
            let e = fb.getPixel(x: ix, y: iy)
            let fR = e.r * fadeFactor, fG = e.g * fadeFactor, fB = e.b * fadeFactor
            let t = time * 500.0 * ns
            let noiseVal = perlin8_3d(fx * 45, t, t)
            let simVol = (sin(time * ns * 2.0) + 1.0) / 2.0 * 128.0 + 64.0
            let thisVal = (1.0 + intensity / 64.0) * noiseVal / 2.0 / 32.0 * simVol
            let thisMax = min(Int(thisVal * Double(rows) / 512.0), rows)
            if iy < thisMax {
                let palPos = thisMax > 0 ? Double(iy) / Double(thisMax) : 0
                let c = paletteRGBAt(1.0 - palPos)
                let result = (min(1, fR + c.r), min(1, fG + c.g), min(1, fB + c.b))
                fb.setPixel(x: ix, y: iy, color: result); return result
            }
            if rows - 1 - iy < thisMax {
                let palPos = thisMax > 0 ? Double(rows - 1 - iy) / Double(thisMax) : 0
                let c = paletteRGBAt(1.0 - palPos)
                let result = (min(1, fR + c.r * 0.5), min(1, fG + c.g * 0.5), min(1, fB + c.b * 0.5))
                fb.setPixel(x: ix, y: iy, color: result); return result
            }
            let faded = (fR, fG, fB)
            fb.setPixel(x: ix, y: iy, color: faded); return faded
        }
        let t = time * 500.0 * ns
        let noiseVal = perlin8_3d(fx * 45, t, t)
        let simVol = (sin(time * ns * 2.0) + 1.0) / 2.0 * 128.0 + 64.0
        let thisMax = Int((1.0 + intensity / 64.0) * noiseVal / 2.0 / 32.0 * simVol * H / 512.0)
        let j = Int(fy)
        if j < thisMax || (Int(H) - 1 - j) < thisMax {
            let pp = thisMax > 0 ? Double(min(j, Int(H) - 1 - j)) / Double(thisMax) : 0
            return paletteRGBAt(1.0 - pp)
        }
        return (0, 0, 0)
    }

    private func ripple2D(fx: Double, fy: Double, W: Double, H: Double,
                          time: TimeInterval, ns: Double,
                          fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let numRipples = max(2, Int(ni * 8) + 2)
        let ix = Int(fx), iy = Int(fy)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            let ff = 0.96 - (speed / 255.0) * 0.04
            var tR = e.r * ff, tG = e.g * ff, tB = e.b * ff
            for i in 0..<numRipples {
                let period = 2.5 + Double(i) * 0.7
                let age = fmod(time * ns + Double(i) * period / 6.0, period)
                let cx = hashRand(Double(i) * 127.1 + floor(time * ns / period) * 311.7, 43.0) * W
                let cy = hashRand(Double(i) * 269.5 + floor(time * ns / period) * 183.3, 71.0) * H
                let dist = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))
                let radius = age * (1.0 + speed / 64.0) * max(W, H) * 0.15
                let rd = abs(dist - radius)
                if rd < 1.2 && radius > 0.5 {
                    let na = min(1.0, age / period)
                    let amp = na < 0.25 ? na / 0.25 : max(0, 1.0 - (na - 0.25) / 0.75)
                    let mag = amp * (1.0 - rd / 1.2)
                    let c = paletteRGBAt(hashRand(Double(i) * 37.3, floor(time * ns / period) * 19.7))
                    tR = min(1, tR + c.r * mag); tG = min(1, tG + c.g * mag); tB = min(1, tB + c.b * mag)
                }
            }
            let result = (tR, tG, tB); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var total = 0.0, palIdx = 0.0
        for i in 0..<numRipples {
            let period = 2.5 + Double(i) * 0.7
            let age = fmod(time * ns + Double(i) * period / Double(numRipples), period)
            let cx = hashRand(Double(i) * 127.1 + floor(time * ns / period) * 311.7, 43.0) * W
            let cy = hashRand(Double(i) * 269.5 + floor(time * ns / period) * 183.3, 71.0) * H
            let dist = sqrt((fx - cx) * (fx - cx) + (fy - cy) * (fy - cy))
            let radius = age * (1.0 + speed / 64.0) * max(W, H) * 0.15
            let rd = abs(dist - radius)
            if rd < 1.2 && radius > 0.5 {
                let na = min(1.0, age / period)
                total += (na < 0.25 ? na / 0.25 : max(0, 1.0 - (na - 0.25) / 0.75)) * (1.0 - rd / 1.2)
                palIdx = hashRand(Double(i) * 37.3, floor(time * ns / period) * 19.7)
            }
        }
        if total > 0.05 { let c = paletteRGBAt(palIdx); let t = min(1.0, total); return (c.r * t, c.g * t, c.b * t) }
        return (0, 0, 0)
    }

    private func plasmaBall2D(fx: Double, fy: Double, W: Double, H: Double,
                              time: TimeInterval, ns: Double,
                              fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let ix = Int(fx), iy = Int(fy)
        let cols = Int(W), rows = Int(H)
        let t = time * 8.0 / max(1, (256 - speed) / 32)
        let noiseX = perlin8_2d(fx * 30, t), noiseY = perlin8_2d(t, fy * 30)
        let maxX = noiseX / 255.0 * (W - 1), maxY = noiseY / 255.0 * (H - 1)
        let xd = fx + maxY - W / 2, yd = fy + maxX - W / 2
        let cxP = fx + maxY, cyP = fy + maxX
        let lineWidth = 1.0 + ni * 2.0
        let isLine = abs(xd - yd) < lineWidth || abs(Double(cols-1) - xd - yd) < lineWidth ||
                     abs(Double(cols) - cxP) < lineWidth * 0.5 || abs(Double(cols-1) - cxP) < lineWidth * 0.5 ||
                     abs(Double(rows) - cyP) < lineWidth * 0.5 || abs(Double(rows-1) - cyP) < lineWidth * 0.5
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            let pfade = max(0.5, 1.0 - (custom1 / 4) / 255.0)
            let pR = e.r * pfade, pG = e.g * pfade, pB = e.b * pfade
            if isLine {
                let c = paletteRGBAt(fmod(time * 5.0 / 60.0, 1.0))
                let bri = max(noiseX, noiseY) / 255.0
                let result = (min(1, pR+c.r*bri), min(1, pG+c.g*bri), min(1, pB+c.b*bri))
                fb.setPixel(x: ix, y: iy, color: result); return result
            }
            let pfaded = (pR, pG, pB)
            fb.setPixel(x: ix, y: iy, color: pfaded); return pfaded
        }
        if isLine {
            let c = paletteRGBAt(fmod(time * 5.0 / 60.0, 1.0))
            let bri = max(noiseX, noiseY) / 255.0
            return (c.r * bri, c.g * bri, c.b * bri)
        }
        return (0, 0, 0)
    }

    private func noise2D(fx: Double, fy: Double, W: Double, H: Double,
                         time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let scale = intensity + 2
        let zSpeed = max(1.0, 16.0 - speed / 16.0)
        return paletteRGBAt(perlin8_3d(fx * scale, fy * scale, time * 1000.0 / zSpeed) / 255.0)
    }

    private func distortionWaves2D(fx: Double, fy: Double, W: Double, H: Double,
                                   time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let sp = speed / 32.0, sc = intensity / 32.0
        let a = time * ns * 30.0, a2 = a / 2, a3 = a / 3
        let cSc = W * sc, rSc = H * sc
        let dwCx = beatsin16(bpm: 10-sp, lo: 0, hi: cSc, time: time)
        let dwCy = beatsin16(bpm: 12-sp, lo: 0, hi: rSc, time: time)
        let cx1 = beatsin16(bpm: 13-sp, lo: 0, hi: cSc, time: time)
        let cy1 = beatsin16(bpm: 15-sp, lo: 0, hi: rSc, time: time)
        let cx2 = beatsin16(bpm: 17-sp, lo: 0, hi: cSc, time: time)
        let cy2 = beatsin16(bpm: 14-sp, lo: 0, hi: rSc, time: time)
        let xo = fx * sc, yo = fy * sc
        let rd = cos8(fmod(abs(cos8(fmod(abs(fx*8+a),256))+cos8(fmod(abs(fy*8-a2),256))+a3),256))/2
        let gd = cos8(fmod(abs(cos8(fmod(abs(fx*8-a2),256))+cos8(fmod(abs(fy*8+a3),256))+a+32),256))/2
        let bd = cos8(fmod(abs(cos8(fmod(abs(fx*8+a3),256))+cos8(fmod(abs(fy*8-a),256))+a2+64),256))/2
        let drR = xo-dwCx, drY = yo-dwCy, dgR = xo-cx1, dgY = yo-cy1, dbR = xo-cx2, dbY = yo-cy2
        let vR = cos8(fmod(abs(rd+fmod(abs(a-(drR*drR+drY*drY)/128),256)*2),256))/255
        let vG = cos8(fmod(abs(gd+fmod(abs(a2-(dgR*dgR+dgY*dgY)/128),256)*2),256))/255
        let vB = cos8(fmod(abs(bd+fmod(abs(a3-(dbR*dbR+dbY*dbY)/128),256)*2),256))/255
        if paletteId > 0 { return paletteRGBAt((vR+vG+vB)/3) }
        return (vR, vG, vB)
    }

    private func firenoise2D(fx: Double, fy: Double, W: Double, H: Double,
                             time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let rows = H
        let noiseIdx = perlin8_2d(fx * speed * 8 * rows / 255, fy * intensity * 4 + time * ns * 250)
        let palPos = min(fy * noiseIdx / 11, 225) / 255
        let bri = fy / rows
        if paletteId <= 5 {
            let p = palPos
            if p < 0.25 { return (p/0.25*0.8*bri, 0, 0) }
            if p < 0.5 { let t=(p-0.25)/0.25; return ((0.8+t*0.2)*bri, t*0.35*bri, 0) }
            if p < 0.75 { let t=(p-0.5)/0.25; return (bri, (0.35+t*0.3)*bri, 0) }
            let t=(p-0.75)/0.25; return (bri, (0.65+t*0.35)*bri, t*0.3*bri)
        }
        let c = paletteRGBAt(palPos); return (c.r*bri, c.g*bri, c.b*bri)
    }

    private func rippleRainbow2D(fx: Double, fy: Double, W: Double, H: Double,
                                 time: TimeInterval, ns: Double,
                                 fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let numRipples = max(2, Int(ni * 7) + 2)
        let hs = fmod(time * ns * 0.15, 1.0)
        let bg = hueToRGB(hs)
        var r0 = bg.r * 0.08, g0 = bg.g * 0.08, b0 = bg.b * 0.08
        for i in 0..<numRipples {
            let period = 2.8 + Double(i) * 0.8
            let age = fmod(time * ns + Double(i) * period / Double(numRipples), period)
            let cx = hashRand(Double(i)*127.1+floor(time*ns/period)*311.7, 43)*W
            let cy = hashRand(Double(i)*269.5+floor(time*ns/period)*183.3, 71)*H
            let dist = sqrt((fx-cx)*(fx-cx)+(fy-cy)*(fy-cy))
            let radius = age * (1+speed/64) * max(W,H) * 0.15
            let rd = abs(dist - radius)
            if rd < 1.2 && radius > 0.5 {
                let na = min(1, age/period)
                let amp = na < 0.25 ? na/0.25 : max(0, 1-(na-0.25)/0.75)
                let mag = amp * (1 - rd/1.2)
                let c = paletteRGBAt(fmod(hashRand(Double(i)*37.3, floor(time*ns/period)*19.7)+hs, 1))
                r0 = min(1, r0+c.r*mag); g0 = min(1, g0+c.g*mag); b0 = min(1, b0+c.b*mag)
            }
        }
        return (r0, g0, b0)
    }

    private func palette2D(fx: Double, fy: Double, W: Double, H: Double,
                           time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let scale = 0.5 + (intensity / 255.0) * 3.0
        return paletteRGBAt(fmod((fx/W + fy/H) / 2 * scale + time * ns * 0.2, 1.0))
    }

    private func sindots2D(fx: Double, fy: Double, W: Double, H: Double,
                           time: TimeInterval, ns: Double,
                           fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let t1 = time * 1000.0 / max(2, 257 - speed), t2 = sin8(fmod(t1, 256)) / 4 * 2
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            let fadeRate = max(0.5, 1.0 - ((custom1 / 8) + (option1 ? 24 : 0)) / 255.0)
            var r = e.r * fadeRate, g = e.g * fadeRate, b = e.b * fadeRate
            for i in 0..<13 {
                let dx = sin8(fmod(t1+Double(i)*intensity/8, 256))*(W-1)/255
                let dy = sin8(fmod(t2+Double(i)*intensity/8, 256))*(H-1)/255
                let d = sqrt((fx-dx)*(fx-dx)+(fy-dy)*(fy-dy))
                if d < 1.5 { let bri=max(0,1-d/1.5); let c=paletteRGBAt(Double(i)/13)
                    r=min(1,r+c.r*bri); g=min(1,g+c.g*bri); b=min(1,b+c.b*bri) }
            }
            let result = (r,g,b); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var bestBri = 0.0, bestPal = 0.0
        for i in 0..<13 {
            let dx = sin8(fmod(t1+Double(i)*intensity/8, 256))*(W-1)/255
            let dy = sin8(fmod(t2+Double(i)*intensity/8, 256))*(H-1)/255
            let d = sqrt((fx-dx)*(fx-dx)+(fy-dy)*(fy-dy))
            if d < 1.5 { let bri=max(0,1-d/1.5); if bri>bestBri { bestBri=bri; bestPal=Double(i)/13 } }
        }
        if bestBri > 0.01 { let c=paletteRGBAt(bestPal); return (c.r*bestBri, c.g*bestBri, c.b*bestBri) }
        return (0, 0, 0)
    }

    private func scrollingText2D(fx: Double, fy: Double, W: Double, H: Double,
                                 time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let text = displayText.isEmpty ? "" : displayText

        let scale = 1.0 + (custom2 / 255.0) * 2.0
        let renderCharW = Double(6) * scale
        let renderCharH = Double(7) * scale
        let effectiveScale = renderCharH > H ? (H / 7.0) : scale
        let eCharW = 6.0 * effectiveScale
        let eCharH = 7.0 * effectiveScale
        let totalTextW = Double(text.count) * eCharW

        let rotStep = Int(custom3) / 8 % 4
        var px = fx, py = fy, rH = H
        if rotStep == 1 { px = fy; py = W - 1 - fx; rH = W }
        else if rotStep == 2 { px = W - 1 - fx; py = H - 1 - fy }
        else if rotStep == 3 { px = H - 1 - fy; py = fx; rH = W }

        let yNorm = (intensity - 128.0) / 128.0
        let topM = (rH - eCharH) / 2.0 + yNorm * (rH / 2.0)

        let rowF = py - topM
        if rowF < 0 || rowF >= eCharH { return textBgColor() }
        let glyphRow = Int(rowF / effectiveScale)
        if glyphRow < 0 || glyphRow >= 7 { return textBgColor() }

        let scrollRange = totalTextW + W
        let rawScroll = fmod(time * ns * max(W, totalTextW) * 0.35, scrollRange)
        let scrollPos = option3 ? (scrollRange - rawScroll) : rawScroll
        let textXF = px - W + scrollPos
        if textXF < 0 || textXF >= totalTextW {
            if custom1 > 5 {
                let trailLen = custom1 / 255.0 * W * 0.8
                let distPastEnd = textXF >= totalTextW ? (textXF - totalTextW) : -textXF
                if distPastEnd > 0 && distPastEnd < trailLen {
                    let fade = (1.0 - distPastEnd / trailLen) * 0.4
                    return textFgColor(fade)
                }
            }
            return textBgColor()
        }

        let charIdx = Int(textXF / eCharW)
        let colF = fmod(textXF, eCharW)
        let glyphCol = Int(colF / effectiveScale)
        guard charIdx >= 0, charIdx < text.count, glyphCol >= 0, glyphCol < 5 else {
            return textBgColor()
        }

        let ch = text[text.index(text.startIndex, offsetBy: charIdx)]
        let glyph = Self.pixelFont(ch)
        if (glyph[glyphRow] >> (4 - glyphCol)) & 1 == 1 {
            if option1 {
                return paletteRGBAt(fmod(Double(charIdx) / Double(max(1, text.count)), 1.0))
            }
            if paletteId > 0 {
                return paletteRGBAt(fmod(Double(charIdx) / Double(max(1, text.count)) + time * ns * 0.03, 1.0))
            }
            return textFgColor(1.0)
        }

        if option2 {
            let bgPos = fmod(px / W + py / H + time * ns * 0.05, 1.0)
            let c = paletteRGBAt(bgPos)
            return (c.r * 0.15, c.g * 0.15, c.b * 0.15)
        }
        return textBgColor()
    }

    private func textFgColor(_ brightness: Double) -> (r: Double, g: Double, b: Double) {
        let fg = colors[0]
        if fg.count >= 3 && (fg[0] + fg[1] + fg[2]) > 0 {
            return (Double(fg[0]) / 255.0 * brightness, Double(fg[1]) / 255.0 * brightness, Double(fg[2]) / 255.0 * brightness)
        }
        return (brightness, brightness, brightness)
    }

    private func textBgColor() -> (r: Double, g: Double, b: Double) {
        if colors.count > 1 {
            let bg = colors[1]
            if bg.count >= 3 && (bg[0] + bg[1] + bg[2]) > 0 {
                return (Double(bg[0]) / 255.0 * 0.12, Double(bg[1]) / 255.0 * 0.12, Double(bg[2]) / 255.0 * 0.12)
            }
        }
        return (0, 0, 0)
    }

    private static func pixelFont(_ ch: Character) -> [UInt8] {
        switch ch {
        case "a": return [0b00000,0b00000,0b01110,0b00001,0b01111,0b10001,0b01111]
        case "b": return [0b10000,0b10000,0b10110,0b11001,0b10001,0b10001,0b11110]
        case "c": return [0b00000,0b00000,0b01110,0b10000,0b10000,0b10001,0b01110]
        case "d": return [0b00001,0b00001,0b01101,0b10011,0b10001,0b10001,0b01111]
        case "e": return [0b00000,0b00000,0b01110,0b10001,0b11111,0b10000,0b01110]
        case "f": return [0b00110,0b01001,0b01000,0b11100,0b01000,0b01000,0b01000]
        case "g": return [0b00000,0b01111,0b10001,0b10001,0b01111,0b00001,0b01110]
        case "h": return [0b10000,0b10000,0b10110,0b11001,0b10001,0b10001,0b10001]
        case "i": return [0b00100,0b00000,0b01100,0b00100,0b00100,0b00100,0b01110]
        case "j": return [0b00010,0b00000,0b00110,0b00010,0b00010,0b10010,0b01100]
        case "k": return [0b10000,0b10000,0b10010,0b10100,0b11000,0b10100,0b10010]
        case "l": return [0b01100,0b00100,0b00100,0b00100,0b00100,0b00100,0b01110]
        case "m": return [0b00000,0b00000,0b11010,0b10101,0b10101,0b10001,0b10001]
        case "n": return [0b00000,0b00000,0b10110,0b11001,0b10001,0b10001,0b10001]
        case "o": return [0b00000,0b00000,0b01110,0b10001,0b10001,0b10001,0b01110]
        case "p": return [0b00000,0b00000,0b11110,0b10001,0b11110,0b10000,0b10000]
        case "q": return [0b00000,0b00000,0b01101,0b10011,0b01111,0b00001,0b00001]
        case "r": return [0b00000,0b00000,0b10110,0b11001,0b10000,0b10000,0b10000]
        case "s": return [0b00000,0b00000,0b01110,0b10000,0b01110,0b00001,0b11110]
        case "t": return [0b01000,0b01000,0b11100,0b01000,0b01000,0b01001,0b00110]
        case "u": return [0b00000,0b00000,0b10001,0b10001,0b10001,0b10011,0b01101]
        case "v": return [0b00000,0b00000,0b10001,0b10001,0b10001,0b01010,0b00100]
        case "w": return [0b00000,0b00000,0b10001,0b10001,0b10101,0b10101,0b01010]
        case "x": return [0b00000,0b00000,0b10001,0b01010,0b00100,0b01010,0b10001]
        case "y": return [0b00000,0b00000,0b10001,0b10001,0b01111,0b00001,0b01110]
        case "z": return [0b00000,0b00000,0b11111,0b00010,0b00100,0b01000,0b11111]
        default: break
        }
        switch ch.uppercased().first ?? "?" {
        case "A": return [0b01110,0b10001,0b10001,0b11111,0b10001,0b10001,0b10001]
        case "B": return [0b11110,0b10001,0b10001,0b11110,0b10001,0b10001,0b11110]
        case "C": return [0b01110,0b10001,0b10000,0b10000,0b10000,0b10001,0b01110]
        case "D": return [0b11110,0b10001,0b10001,0b10001,0b10001,0b10001,0b11110]
        case "E": return [0b11111,0b10000,0b10000,0b11110,0b10000,0b10000,0b11111]
        case "F": return [0b11111,0b10000,0b10000,0b11110,0b10000,0b10000,0b10000]
        case "G": return [0b01110,0b10001,0b10000,0b10111,0b10001,0b10001,0b01110]
        case "H": return [0b10001,0b10001,0b10001,0b11111,0b10001,0b10001,0b10001]
        case "I": return [0b01110,0b00100,0b00100,0b00100,0b00100,0b00100,0b01110]
        case "J": return [0b00111,0b00010,0b00010,0b00010,0b00010,0b10010,0b01100]
        case "K": return [0b10001,0b10010,0b10100,0b11000,0b10100,0b10010,0b10001]
        case "L": return [0b10000,0b10000,0b10000,0b10000,0b10000,0b10000,0b11111]
        case "M": return [0b10001,0b11011,0b10101,0b10101,0b10001,0b10001,0b10001]
        case "N": return [0b10001,0b11001,0b10101,0b10011,0b10001,0b10001,0b10001]
        case "O": return [0b01110,0b10001,0b10001,0b10001,0b10001,0b10001,0b01110]
        case "P": return [0b11110,0b10001,0b10001,0b11110,0b10000,0b10000,0b10000]
        case "Q": return [0b01110,0b10001,0b10001,0b10001,0b10101,0b10010,0b01101]
        case "R": return [0b11110,0b10001,0b10001,0b11110,0b10100,0b10010,0b10001]
        case "S": return [0b01110,0b10001,0b10000,0b01110,0b00001,0b10001,0b01110]
        case "T": return [0b11111,0b00100,0b00100,0b00100,0b00100,0b00100,0b00100]
        case "U": return [0b10001,0b10001,0b10001,0b10001,0b10001,0b10001,0b01110]
        case "V": return [0b10001,0b10001,0b10001,0b10001,0b10001,0b01010,0b00100]
        case "W": return [0b10001,0b10001,0b10001,0b10101,0b10101,0b11011,0b10001]
        case "X": return [0b10001,0b10001,0b01010,0b00100,0b01010,0b10001,0b10001]
        case "Y": return [0b10001,0b10001,0b01010,0b00100,0b00100,0b00100,0b00100]
        case "Z": return [0b11111,0b00001,0b00010,0b00100,0b01000,0b10000,0b11111]
        case "0": return [0b01110,0b10011,0b10101,0b10101,0b10101,0b11001,0b01110]
        case "1": return [0b00100,0b01100,0b00100,0b00100,0b00100,0b00100,0b01110]
        case "2": return [0b01110,0b10001,0b00001,0b00110,0b01000,0b10000,0b11111]
        case "3": return [0b01110,0b10001,0b00001,0b00110,0b00001,0b10001,0b01110]
        case "4": return [0b00010,0b00110,0b01010,0b10010,0b11111,0b00010,0b00010]
        case "5": return [0b11111,0b10000,0b11110,0b00001,0b00001,0b10001,0b01110]
        case "6": return [0b01110,0b10000,0b10000,0b11110,0b10001,0b10001,0b01110]
        case "7": return [0b11111,0b00001,0b00010,0b00100,0b01000,0b01000,0b01000]
        case "8": return [0b01110,0b10001,0b10001,0b01110,0b10001,0b10001,0b01110]
        case "9": return [0b01110,0b10001,0b10001,0b01111,0b00001,0b00001,0b01110]
        case " ": return [0b00000,0b00000,0b00000,0b00000,0b00000,0b00000,0b00000]
        case "!": return [0b00100,0b00100,0b00100,0b00100,0b00100,0b00000,0b00100]
        case ".": return [0b00000,0b00000,0b00000,0b00000,0b00000,0b00000,0b00100]
        case "-": return [0b00000,0b00000,0b00000,0b11111,0b00000,0b00000,0b00000]
        case ":": return [0b00000,0b00100,0b00000,0b00000,0b00000,0b00100,0b00000]
        case "+": return [0b00000,0b00100,0b00100,0b11111,0b00100,0b00100,0b00000]
        case "#": return [0b01010,0b11111,0b01010,0b01010,0b11111,0b01010,0b00000]
        case "<": return [0b00010,0b00100,0b01000,0b10000,0b01000,0b00100,0b00010]
        case ">": return [0b01000,0b00100,0b00010,0b00001,0b00010,0b00100,0b01000]
        default:  return [0b01110,0b10001,0b00010,0b00100,0b00100,0b00000,0b00100]
        }
    }

    private func coloredBursts2D(fx: Double, fy: Double, W: Double, H: Double,
                                 time: TimeInterval, ns: Double,
                                 fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let nL = Int(intensity / 16) + 1, ho = fmod(time * ns * 30, 256)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            let cbFade = 0.90 - (option2 ? 0.08 : 0.0)
            var r = e.r*cbFade, g = e.g*cbFade, b = e.b*cbFade
            for i in 0..<nL {
                let bp = speed / 16
                let x1 = beatsin8(bpm: 2+bp, lo: 0, hi: W-1, time: time)
                let x2 = beatsin8(bpm: 1+bp, lo: 0, hi: H-1, time: time)
                let y1 = beatsin8(bpm: 5+bp, lo: 0, hi: W-1, time: time, phase: Double(i)*24)
                let y2 = beatsin8(bpm: 3+bp, lo: 0, hi: H-1, time: time, phase: Double(i)*48+64)
                let ll = sqrt((y1-x1)*(y1-x1)+(y2-x2)*(y2-x2))
                if ll < 0.5 { continue }
                let ldx = y1-x1, ldy = y2-x2
                let t = max(0, min(1, ((fx-x1)*ldx+(fy-x2)*ldy)/(ll*ll)))
                let dl = sqrt((fx-(x1+t*ldx))*(fx-(x1+t*ldx))+(fy-(x2+t*ldy))*(fy-(x2+t*ldy)))
                if dl < 1 { let bri = 1-dl; let c = paletteRGBAt(fmod(Double(i)/Double(nL)+ho/256, 1))
                    r=min(1,r+c.r*bri); g=min(1,g+c.g*bri); b=min(1,b+c.b*bri) }
            }
            let result = (r,g,b); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var tR = 0.0, tG = 0.0, tB = 0.0
        for i in 0..<nL {
            let bp = speed / 16
            let x1 = beatsin8(bpm: 2+bp, lo: 0, hi: W-1, time: time)
            let x2 = beatsin8(bpm: 1+bp, lo: 0, hi: H-1, time: time)
            let y1 = beatsin8(bpm: 5+bp, lo: 0, hi: W-1, time: time, phase: Double(i)*24)
            let y2 = beatsin8(bpm: 3+bp, lo: 0, hi: H-1, time: time, phase: Double(i)*48+64)
            let ll = sqrt((y1-x1)*(y1-x1)+(y2-x2)*(y2-x2))
            if ll < 0.5 { continue }
            let ldx = y1-x1, ldy = y2-x2
            let t = max(0, min(1, ((fx-x1)*ldx+(fy-x2)*ldy)/(ll*ll)))
            let dl = sqrt((fx-(x1+t*ldx))*(fx-(x1+t*ldx))+(fy-(x2+t*ldy))*(fy-(x2+t*ldy)))
            if dl < 1 { let bri = 1-dl; let c = paletteRGBAt(fmod(Double(i)/Double(nL)+ho/256, 1))
                tR=min(1,tR+c.r*bri); tG=min(1,tG+c.g*bri); tB=min(1,tB+c.b*bri) }
        }
        return (tR, tG, tB)
    }

    private func hiphotic2D(fx: Double, fy: Double, W: Double, H: Double,
                            time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        let speedDiv = max(1.0, (custom3 / 2) + 1)
        let a = time * 1000.0 / speedDiv
        let v = sin8(fmod(abs(cos8(fmod(abs(fx*speed/16+a/3), 256)) + sin8(fmod(abs(fy*intensity/16+a/4), 256)) + a), 256))
        return paletteRGBAt(v / 255)
    }

    private func drift2D(fx: Double, fy: Double, W: Double, H: Double,
                         time: TimeInterval, ns: Double,
                         fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ix = Int(fx), iy = Int(fy)
        let cC = Double(Int(W)/2) + (Int(W)%2 == 1 ? 1 : 0)
        let rC = Double(Int(H)/2) + (Int(H)%2 == 1 ? 1 : 0)
        let maxD = max(W, H) / 2
        let t = time * ns * 8, t20 = t / 20
        let ni = intensity / 255.0
        let dotSize = 0.8 + ni * 1.0
        let fadeFactor = max(0.3, 0.7 - ni * 0.4)
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            var r = e.r * fadeFactor, g = e.g * fadeFactor, b = e.b * fadeFactor
            var i = 1.0
            while i < maxD {
                let ang = (t * (maxD - i)) * .pi / 180
                let sx = cC + sin(ang) * i, sy = rC + cos(ang) * i
                let d = sqrt((fx-sx)*(fx-sx)+(fy-sy)*(fy-sy))
                if d < dotSize { let bri=max(0,1-d/dotSize); let c=paletteRGBAt(fmod((i*20+t20)/255,1))
                    r=min(1,r+c.r*bri); g=min(1,g+c.g*bri); b=min(1,b+c.b*bri) }
                if option1 {
                    let d2 = sqrt((fx-(cC+cos(ang)*i))*(fx-(cC+cos(ang)*i))+(fy-(rC+sin(ang)*i))*(fy-(rC+sin(ang)*i)))
                    if d2 < dotSize { let bri=max(0,1-d2/dotSize); let c=paletteRGBAt(fmod((i*20+t20)/255,1))
                        r=min(1,r+c.r*bri); g=min(1,g+c.g*bri); b=min(1,b+c.b*bri) }
                }
                i += 0.25
            }
            let result = (r,g,b); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var best = 0.0, bPal = 0.0; var i = 1.0
        while i < maxD {
            let ang = (t * (maxD - i)) * .pi / 180
            let d = sqrt((fx-(cC+sin(ang)*i))*(fx-(cC+sin(ang)*i))+(fy-(rC+cos(ang)*i))*(fy-(rC+cos(ang)*i)))
            if d < dotSize { let bri=max(0,1-d/dotSize); if bri>best { best=bri; bPal=fmod((i*20+t20)/255,1) } }
            i += 0.25
        }
        if best > 0.01 { let c=paletteRGBAt(bPal); return (c.r*best, c.g*best, c.b*best) }
        return (0, 0, 0)
    }

    private func driftRose2D(fx: Double, fy: Double, W: Double, H: Double,
                             time: TimeInterval, ns: Double,
                             fb: MatrixFrameBuffer?) -> (r: Double, g: Double, b: Double) {
        let ni = intensity / 255.0
        let ix = Int(fx), iy = Int(fy)
        let CX = (W - fmod(W, 2)) / 2 - 0.5, CY = (H - fmod(H, 2)) / 2 - 0.5
        let L = min(W, H) / 2
        let dotSize = 0.8 + ni * 1.0
        if let fb = fb {
            let e = fb.getPixel(x: ix, y: iy)
            let ff = max(0.5, 1 - (32 + speed / 8) / 255)
            var r = e.r * ff, g = e.g * ff, b = e.b * ff
            for i in 1..<37 {
                let ang = Double(i) * 10 * .pi / 180
                let rad = beatsin8(bpm: Double(i), lo: 0, hi: L*2, time: time) - L
                let px = CX + sin(ang) * rad, py = CY + cos(ang) * rad
                let d = sqrt((fx-px)*(fx-px)+(fy-py)*(fy-py))
                if d < dotSize { let bri=max(0,1-d/dotSize); let c=paletteRGBAt(fmod(Double(i)*10/360,1))
                    r=min(1,r+c.r*bri); g=min(1,g+c.g*bri); b=min(1,b+c.b*bri) }
            }
            let result = (r,g,b); fb.setPixel(x: ix, y: iy, color: result); return result
        }
        var best = 0.0, bPal = 0.0
        for i in 1..<37 {
            let ang = Double(i) * 10 * .pi / 180
            let rad = beatsin8(bpm: Double(i), lo: 0, hi: L*2, time: time) - L
            let d = sqrt((fx-(CX+sin(ang)*rad))*(fx-(CX+sin(ang)*rad))+(fy-(CY+cos(ang)*rad))*(fy-(CY+cos(ang)*rad)))
            if d < dotSize { let bri=max(0,1-d/dotSize); if bri>best { best=bri; bPal=fmod(Double(i)*10/360,1) } }
        }
        if best > 0.01 { let c=paletteRGBAt(bPal); return (c.r*best, c.g*best, c.b*best) }
        return (0, 0, 0)
    }

    private func namedEffect(index: Int, total: Int, time: TimeInterval,
                             ns: Double, pri: [Int], sec: [Int]) -> Color? {
        let name = effectName.lowercased()
        let n = Double(index) / Double(max(1, total - 1))
        let T = Double(total)

        if name.contains("strobe mega") {
            let cycleMs = max(100, Double(255 - Int(speed)) * 20)
            let burstCount = max(1, Int(intensity / 64) + 1)
            let flashMs = max(80, cycleMs * 0.12)
            let burstLen = Double(burstCount) * flashMs * 2
            let phase = fmod(time * 1000, cycleMs)
            if phase < burstLen {
                let flashPhase = fmod(phase, flashMs * 2)
                return flashPhase < flashMs ? effectPrimary(n, pri) : Color.clear
            }
            return Color.clear
        }
        if name.contains("strobe rainbow") {
            let cycleMs = max(160, Double(255 - Int(speed)) * 20)
            let flashMs = max(80, cycleMs * 0.25)
            let phase = fmod(time * 1000, cycleMs)
            if phase < flashMs {
                let hue = fmod(floor(time * 1000 / cycleMs) / 20, 1.0)
                let flashBri = 0.3 + (intensity / 255.0) * 0.7
                return paletteOrHue(hue, brightness: flashBri)
            }
            return Color.clear
        }
        if name.contains("strobe") {
            let cycleMs = max(160, Double(255 - Int(speed)) * 20)
            let flashMs = max(80, cycleMs * 0.25)
            let flashBri = 0.3 + (intensity / 255.0) * 0.7
            let phase = fmod(time * 1000, cycleMs)
            return phase < flashMs ? effectPrimary(n, pri).opacity(flashBri) : Color.clear
        }
        if name.contains("blink rainbow") {
            let cycleMs = max(100, Double(255 - Int(speed)) * 20)
            let duty = intensity / 255
            let phase = fmod(time * 1000, cycleMs)
            let hue = fmod(floor(time * 1000 / cycleMs) / 20, 1.0)
            return phase / cycleMs < duty ? paletteOrHue(hue) : colorFromRGB(sec)
        }
        if name == "blink" {
            let cycleMs = max(100, Double(255 - Int(speed)) * 20)
            let duty = intensity / 255
            let phase = fmod(time * 1000, cycleMs)
            return phase / cycleMs < duty ? effectPrimary(n, pri) : colorFromRGB(sec)
        }

        if name.contains("heartbeat") {
            let bpm = 40.0 + floor(speed / 8.0)
            let msPerBeat = 60000.0 / bpm
            let secondBeat = msPerBeat / 3.0
            let beatTimer = fmod(time * 1000, msPerBeat)
            let decayPerFrame = 2042.0 / (2048.0 + intensity)
            let frameTime = 24.0
            let decayPerMs = pow(decayPerFrame, 1.0 / frameTime)
            let bri: Double
            if beatTimer < secondBeat {
                bri = pow(decayPerMs, beatTimer)
            } else {
                bri = pow(decayPerMs, beatTimer - secondBeat)
            }
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("breath") {
            let rate = floor(speed / 8.0) + 10.0
            let ni = intensity / 255.0
            let nowMs = time * 1000.0
            let counter16 = fmod(nowMs * rate, 65536.0)
            let scaled = counter16 * 5.0 / 16.0
            let lum: Double
            if scaled >= 16384 {
                lum = 30.0 / 255.0
            } else {
                let tri = scaled > 8192 ? 8192.0 - (scaled - 8192.0) : scaled
                let sinVal = sin(tri * .pi / 32768.0) * 32767.0
                let varVal = max(0, sinVal / 103.0)
                lum = (30.0 + varVal) / 255.0
            }
            let minBri = 0.3 * (1.0 - ni)
            let scaledLum = minBri + lum * (1.0 - minBri)
            return effectPrimary(n, pri).opacity(scaledLum)
        }
        if name.contains("tri fade") {
            let ni = intensity / 255.0
            let ter = colors.count > 2 ? (colors[2].count >= 3 ? colors[2] : [0, 0, 255]) : [0, 0, 255]
            let rate = floor(speed / 8.0) + 1.0
            let nowMs = time * 1000.0
            let counter16 = fmod(nowMs * rate, 65536.0)
            let prog = (counter16 * 768.0) / 65536.0
            let c1: [Int]
            let c2: [Int]
            var blendAmt: Double
            if prog < 256 {
                c1 = pri; c2 = sec; blendAmt = prog / 256.0
            } else if prog < 512 {
                c1 = sec; c2 = ter; blendAmt = (prog - 256.0) / 256.0
            } else {
                c1 = ter; c2 = pri; blendAmt = (prog - 512.0) / 256.0
            }
            let shaped = pow(blendAmt, 0.3 + ni * 1.7)
            blendAmt = blendAmt * (1.0 - ni) + shaped * ni
            return blendRGB(c1, c2, blendAmt)
        }
        if name == "fade" {
            let ni = intensity / 255.0
            let rate = floor(speed / 8.0) + 10.0
            let nowMs = time * 1000.0
            let counter16 = fmod(nowMs * rate, 65536.0)
            let triVal: Double
            if counter16 < 32768 {
                triVal = counter16 * 2.0
            } else {
                triVal = 65535.0 - (counter16 - 32768.0) * 2.0
            }
            let rawBlend = triVal / 65535.0
            let minBlend = 0.3 * (1.0 - ni)
            let blend = minBlend + rawBlend * (1.0 - minBlend * 2)
            return blendRGB(sec, pri, max(0, min(1, blend)))
        }
        if name.contains("oscillate") {
            let cycleTimeMs = 20.0 + 2.0 * (255.0 - speed)
            let tick = time * 1000.0 / cycleTimeMs
            let barSpeeds: [Double] = [1, 2, 1]
            let barStartPos: [Double] = [T / 4, T * 3 / 4, T / 2]
            let barStartDir: [Double] = [1, 1, -1]
            let barSize = T / (3.0 + intensity / 8.0)
            var totalBri = 0.0
            for bar in 0..<3 {
                let spd = barSpeeds[bar]
                let bounceLen = max(1, total - 1)
                let cycleTicks = Double(bounceLen * 2) / spd
                let rawProgress = fmod(tick / cycleTicks, 1.0)
                let startOffset = barStartPos[bar] / T
                let dirSign = barStartDir[bar]
                var phase = fmod(rawProgress * dirSign + startOffset + 2.0, 1.0)
                phase = phase * 2.0
                let triPos: Double
                if phase < 1.0 {
                    triPos = phase * (T - 1)
                } else {
                    triPos = (2.0 - phase) * (T - 1)
                }
                let dist = abs(Double(index) - triPos)
                totalBri += max(0, 1 - dist / barSize)
            }
            return effectPrimary(n, pri).opacity(min(1, totalBri))
        }

        if name.contains("twinkle fox") || name.contains("twinklefox") {
            let prng = hashRand(Double(index) * 2053, 1384)
            let clockSpeed = 0.3 + prng * 1.5
            let phase = fmod(time * clockSpeed * ns + prng * 6.28, 6.28)
            let frac = phase / 6.28
            let bri: Double
            if frac < 0.33 { bri = frac / 0.33 }
            else if frac < 0.45 { bri = 1.0 }
            else { bri = max(0, 1 - (frac - 0.45) / 0.55) }
            let density = max(1, Int(intensity / 32) + 1)
            let slot = Int(fmod(prng * 8, 8))
            guard slot < density else { return Color.clear }
            let warm = (1 - bri) * 0.3
            let r = min(1.0, Double(pri[0]) / 255 + warm)
            let g = Double(pri.count > 1 ? pri[1] : 128) / 255 * (1 - warm * 0.5)
            let b = Double(pri.count > 2 ? pri[2] : 0) / 255 * (1 - warm)
            return Color(red: r, green: g, blue: b).opacity(bri)
        }
        if name.contains("twinkle cat") || name.contains("twinklecat") {
            let prng = hashRand(Double(index) * 2053, 1384)
            let clockSpeed = 0.3 + prng * 1.5
            let phase = fmod(time * clockSpeed * ns + prng * 6.28, 6.28)
            let frac = phase / 6.28
            let bri = frac < 0.05 ? 1.0 : max(0, 1 - (frac - 0.05) / 0.95)
            let density = max(1, Int(intensity / 32) + 1)
            let slot = Int(fmod(prng * 8, 8))
            guard slot < density else { return Color.clear }
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("twinkle up") {
            let prng = hashRand(Double(index) * 2053, 1384)
            let clockSpeed = 0.4 + prng * 1.2
            let phase = fmod(time * clockSpeed * ns + prng * 5, 1.0)
            let bri = phase < 0.8 ? phase / 0.8 : max(0, 1 - (phase - 0.8) / 0.2)
            let density = max(1, Int(intensity / 32) + 1)
            guard Int(fmod(prng * 8, 8)) < density else { return Color.clear }
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("fairy twinkle") {
            let riseFallTime = (400.0 + (255.0 - speed) * 3.0) / 1000.0
            let prng = hashRand(Double(index), 4.1414)
            let onDur = riseFallTime + (255.0 - intensity) / 4.0 * prng / 1000.0 + 0.1
            let offDur = riseFallTime + (3.0 + (255.0 - speed) / 64.0) * prng / 100.0 + 0.05
            let cycle = onDur + offDur
            let t = fmod(time + prng * cycle, cycle)
            let bri: Double
            if t < offDur {
                let fadeProg = min(1, t / riseFallTime)
                bri = 1.0 - fadeProg
            } else {
                let fadeProg = min(1, (t - offDur) / riseFallTime)
                bri = fadeProg
            }
            return paletteColorAt(prng).opacity(max(0.08, bri))
        }
        if name.contains("fairy") {
            guard intensity > 0 else { return paletteColorAt(hashRand(Double(index), 78.233)).opacity(0.4) }
            let flasherDist = Int((255.0 - intensity) / 28.0) + 1
            let isFlasher = index % flasherDist == 0
            let prng = hashRand(Double(index), 78.233)
            if isFlasher {
                let onDur = 0.12 + (12.0 + (255.0 - speed) / 4.0) * prng / 100.0
                let offDur = 0.20 + (6.0 + (255.0 - speed) / 4.0) * prng / 100.0
                let cycle = onDur + offDur
                let t = fmod(time + prng * cycle, cycle)
                let fadeDur = min(onDur, offDur) * 0.5
                let bri: Double
                if t < onDur {
                    bri = t < fadeDur ? t / fadeDur : 1.0
                } else {
                    let off = t - onDur
                    bri = off < fadeDur ? 1.0 - off / fadeDur : 0.0
                }
                return paletteColorAt(prng).opacity(max(0.05, bri))
            }
            return paletteColorAt(prng).opacity(0.35)
        }
        if name.contains("colortwinkle") {
            let prng = hashRand(Double(index), 4.1414)
            let fadeSpeed = 8 + speed / 32
            let cycle = 512 / fadeSpeed
            let t = fmod(time * ns * 5 + prng * cycle, cycle)
            let frac = t / cycle
            let bri = frac < 0.5 ? frac * 2 : (1 - frac) * 2
            let density = max(1, Int(intensity / 32) + 1)
            guard Int(fmod(prng * 8, 8)) < density else { return Color.clear }
            return paletteColorAt(prng).opacity(bri)
        }
        if name.contains("twinkle") && !name.contains("color") {
            let prng = hashRand(Double(index), 4.1414)
            let density = max(1, Int(intensity / 32) + 1)
            guard Int(fmod(prng * 8, 8)) < density else { return Color.clear }
            let clockSpeed = 0.5 + prng * 2
            let phase = fmod(time * clockSpeed * ns + prng * 6.28, 6.28)
            let bri = max(0, sin(phase))
            return paletteColorAt(prng).opacity(bri)
        }

        if name.contains("lightning") {
            let numFlashes = max(2, 4 + Int(intensity / 20))
            let strikePause = max(0.2, Double(255 - Int(speed)) * 0.05)
            let flashDur = 0.08 * Double(numFlashes)
            let cycleDur = strikePause + flashDur
            let slot = floor(time / cycleDur)
            let t = fmod(time, cycleDur)
            let center = hashRand(slot, 1.0) * T
            let width = hashRand(slot, 2.0) * T * 0.4 + T * 0.1
            let dist = abs(Double(index) - center)
            guard dist < width else { return colorFromRGB(sec) }
            if t < flashDur {
                let subFlash = Int(t / 0.04)
                let isOn = subFlash < numFlashes * 2 && subFlash % 2 == 0
                if isOn {
                    let bri = max(0.3, 1 - dist / width)
                    return effectPrimary(n, pri).opacity(bri)
                }
            }
            return colorFromRGB(sec)
        }
        if name.contains("glitter") {
            let hue = fmod(n + time * ns * 0.1, 1.0)
            let base = paletteOrHue(hue, saturation: 0.9, brightness: 0.7)
            let sparkle = hashRand(Double(index), floor(time * min(ns * 8, 12)))
            let threshold = 0.02 + (intensity / 255) * 0.15
            return sparkle < threshold ? Color.white : base
        }
        if name == "sparkle dark" || name.contains("sparkle+") || name.contains("hyper sparkle") {
            let ni = intensity / 255.0
            let baseCount = name.contains("+") || name.contains("hyper") ? max(1, total / 3) : 1
            let numDark = max(1, Int(Double(baseCount) * (0.3 + ni * 0.7)))
            let frame = floor(time * 1000 / max(80, 10 + Double(255 - Int(speed)) * 2))
            var isDark = false
            for d in 0..<numDark {
                let darkPos = Int(hashRand(frame, Double(d + 1)) * T)
                if index == darkPos { isDark = true; break }
            }
            return isDark ? Color.clear : effectPrimary(n, pri)
        }
        if name.contains("sparkle") {
            let ni = intensity / 255.0
            let numSparkles = max(1, Int(ni * 5) + 1)
            let frame = floor(time * 1000 / max(80, 10 + Double(255 - Int(speed)) * 2))
            for s in 0..<numSparkles {
                let sparklePos = Int(hashRand(frame + Double(s) * 100, 1.0) * T) % total
                if index == sparklePos {
                    return effectPrimary(n, pri)
                }
            }
            return paletteColorAt(n).opacity(0.4)
        }

        if name.contains("sinelon rainbow") {
            let pos = (sin(time * ns * 1.5) + 1) / 2 * (T - 1)
            let dist = abs(Double(index) - pos)
            let trail = 2 + intensity / 255 * 8
            let bri = max(0, 1 - dist / trail)
            guard bri > 0.01 else { return Color.clear }
            let hue = fmod(n + time * 0.1, 1.0)
            return paletteOrHue(hue, saturation: 0.9, brightness: bri * bri)
        }
        if name.contains("sinelon dual") {
            let pos1 = (sin(time * ns * 1.5) + 1) / 2 * (T - 1)
            let pos2 = (sin(time * ns * 1.3 + .pi) + 1) / 2 * (T - 1)
            let trail = 2 + intensity / 255 * 8
            let b1 = max(0, 1 - abs(Double(index) - pos1) / trail)
            let b2 = max(0, 1 - abs(Double(index) - pos2) / trail)
            let bri = max(b1, b2)
            guard bri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bri * bri)
        }
        if name.contains("sinelon") {
            let pos = (sin(time * ns * 1.5) + 1) / 2 * (T - 1)
            let trail = 2 + intensity / 255 * 8
            let bri = max(0, 1 - abs(Double(index) - pos) / trail)
            guard bri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bri * bri)
        }
        if name.contains("larson dual") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let cycleSteps = max(1, (total - 1) * 2)
            let step1 = Int(time / stepDelay) % cycleSteps
            let step2 = (Int(time / stepDelay) + cycleSteps / 2) % cycleSteps
            let bounce1 = Double(step1 < (total - 1) ? step1 : cycleSteps - step1)
            let bounce2 = Double(step2 < (total - 1) ? step2 : cycleSteps - step2)
            let trail = 2 + intensity / 255 * T * 0.3
            let b1 = max(0, 1 - abs(Double(index) - bounce1) / trail)
            let b2 = max(0, 1 - abs(Double(index) - bounce2) / trail)
            return effectPrimary(n, pri).opacity(max(b1, b2))
        }
        if name.contains("larson") || name.contains("knight") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let cycleSteps = max(1, (total - 1) * 2)
            let step = Int(time / stepDelay) % cycleSteps
            let bounce = Double(step < (total - 1) ? step : cycleSteps - step)
            let trail = 2 + intensity / 255 * T * 0.3
            let dist = abs(Double(index) - bounce)
            return effectPrimary(n, pri).opacity(max(0, 1 - dist / trail))
        }
        if name.contains("scan dual") || name.contains("dual scan") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let cycleSteps = max(1, (total - 1) * 2)
            let step = Int(time / stepDelay) % cycleSteps
            let pos = Double(step < (total - 1) ? step : cycleSteps - step)
            let s1 = pos
            let s2 = T - 1 - pos
            let w = max(1, intensity / 255 * T * 0.1)
            let b = max(abs(Double(index) - s1) < w ? 1.0 : 0.0,
                        abs(Double(index) - s2) < w ? 1.0 : 0.0)
            return b > 0 ? effectPrimary(n, pri) : Color.clear
        }
        if name == "scan" || name.contains("scanner") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let cycleSteps = max(1, (total - 1) * 2)
            let step = Int(time / stepDelay) % cycleSteps
            let bounce = Double(step < (total - 1) ? step : cycleSteps - step)
            let w = max(1, intensity / 255 * T * 0.1)
            return abs(Double(index) - bounce) < w ? effectPrimary(n, pri) : Color.clear
        }
        if name.contains("icu") {
            let space = max(2, Int(intensity / 8) + 2)
            let moveDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let jumpInterval = moveDelay * Double(max(2, total / space))
            let pauseDur = 0.5 + hashRand(floor(time / jumpInterval), 2.0) * 1.0
            let cycleDur = jumpInterval + pauseDur + 0.2
            let slot = floor(time / cycleDur)
            let t = fmod(time, cycleDur)
            let eyePos1 = hashRand(slot, 1.0) * Double(max(1, total - total / space))
            let eyePos2 = eyePos1 + Double(total / space)
            let blinkPhase = t / cycleDur
            let blinkOn = blinkPhase < 0.15 || blinkPhase > 0.25
            let isEye = abs(Double(index) - eyePos1) < 1 || abs(Double(index) - eyePos2) < 1
            return (isEye && blinkOn) ? effectPrimary(n, pri) : colorFromRGB(sec)
        }

        if name.contains("theater rainbow") || name.contains("theatre rainbow") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let width = 3 + Int(intensity / 16)
            let step = Int(time / stepDelay) % width
            if (index + step) % width == 0 {
                let hue = fmod(n + time * 0.1, 1.0)
                return paletteOrHue(hue)
            }
            return Color.clear
        }
        if name.contains("theater") || name.contains("theatre") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let width = 3 + Int(intensity / 16)
            let step = Int(time / stepDelay) % width
            return (index + step) % width == 0 ? effectPrimary(n, pri) : Color.clear
        }
        if name.contains("running dual") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let w = max(2.0, Double(Int(intensity / 16) + 1))
            let wave1 = (sin(Double(index) * w / T * .pi * 2 - counter) + 1) / 2
            let wave2 = (sin(Double(index) * w / T * .pi * 2 + counter) + 1) / 2
            let combined = (wave1 + wave2) / 2
            return blendRGB(sec, pri, combined)
        }
        if name.contains("running 2") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let blockW = max(2, Int(intensity / 16) + 1)
            let offset = Int(time / stepDelay)
            return (index + offset) / blockW % 2 == 0 ? effectPrimary(n, pri) : colorFromRGB(sec)
        }
        if name.contains("running") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let w = max(2.0, Double(Int(intensity / 4)))
            let angle = Double(index) * w / T * .pi * 2 - counter
            let bri = (sin(angle) + 1) / 2
            return blendRGB(sec, pri, bri)
        }
        if name.contains("chase rainbow") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let pos = fmod(counter, T)
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = fmod(T + Double(index) - pos, T)
            if dist < Double(size) {
                return colorFromRGB(sec)
            }
            let hue = fmod(n + time * 0.05, 1.0)
            return paletteOrHue(hue)
        }
        if name.contains("chase flash") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let pos = fmod(counter, T)
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = fmod(T + Double(index) - pos, T)
            if dist < Double(size) {
                let flashOn = fmod(time * 6, 1.0) < 0.5
                return flashOn ? Color.white : colorFromRGB(sec)
            }
            return effectPrimary(n, pri)
        }
        if name.contains("chase random") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let pos = fmod(counter, T)
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = fmod(T + Double(index) - pos, T)
            if dist < Double(size) {
                return colorFromRGB(sec)
            }
            let hue = hashRand(Double(index), floor(time * 0.5))
            return paletteOrHue(hue, saturation: 0.9, brightness: 0.8)
        }
        if name.contains("chase") && !name.contains("theater") && !name.contains("theatre") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let pos = fmod(counter, T)
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = fmod(T + Double(index) - pos, T)
            if dist < Double(size) { return colorFromRGB(sec) }
            return effectPrimary(n, pri)
        }
        if name.contains("android") {
            let ni = intensity / 255.0
            let maxSize = max(3, Int(ni * T / 2))
            let period = T * 0.3
            let bounce = abs(fmod(time * ns * 5, period * 2) - period) / period
            let barCenter = bounce * (T - 1)
            let sizePhase = abs(fmod(time * ns * 3, 2.0) - 1.0)
            let barSize = 2 + sizePhase * Double(maxSize - 2)
            let dist = abs(Double(index) - barCenter)
            let bri = max(0, 1 - dist / (barSize / 2))
            return bri > 0 ? effectPrimary(n, pri).opacity(bri) : Color.clear
        }

        if name.contains("meteor smooth") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let pos = fmod(time / stepDelay, T)
            let distBehind = fmod(T + pos - Double(index), T)
            let baseTrail = 2 + intensity / 255 * T * 0.3
            guard distBehind < baseTrail else { return Color.clear }
            let retention = 0.5 + hashRand(Double(index), 0.5) * 0.5
            let bri = pow(max(0, 1 - distBehind / baseTrail), 1 / retention)
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("meteor") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let pos = fmod(time / stepDelay, T)
            let distBehind = fmod(T + pos - Double(index), T)
            let baseTrail = 2 + intensity / 255 * T * 0.3
            guard distBehind < baseTrail else { return Color.clear }
            let decaySlot = floor(time * 2)
            let keep = hashRand(Double(index) * 43.758, decaySlot) > (1 - intensity / 255)
            if !keep && distBehind > 3 { return Color.clear }
            let bri = max(0, 1 - distBehind / baseTrail)
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("comet dual") || name.contains("multi comet") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let pos1 = fmod(time / stepDelay, T)
            let pos2 = fmod(T - fmod(time / stepDelay, T), T)
            let trail = 2 + intensity / 255 * T * 0.3
            let d1 = fmod(T + pos1 - Double(index), T)
            let d2 = fmod(T + Double(index) - pos2, T)
            let b1 = d1 < trail ? max(0, 1 - d1 / trail) : 0
            let b2 = d2 < trail ? max(0, 1 - d2 / trail) : 0
            return effectPrimary(n, pri).opacity(max(b1, b2))
        }
        if name.contains("comet") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let pos = fmod(time / stepDelay, T)
            let distBehind = fmod(T + pos - Double(index), T)
            let trail = 2 + intensity / 255 * T * 0.4
            let bri = distBehind < trail ? max(0, 1 - distBehind / trail) : 0
            return effectPrimary(n, pri).opacity(bri)
        }

        if name.contains("fire flicker") {
            let flickerDepth = intensity / 255
            let bri = (1 - flickerDepth * 0.7) + hashRand(Double(index), floor(time * min(ns * 8, 12))) * flickerDepth * 0.7
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("candle multi") {
            let target = 0.5 + hashRand(Double(index) * 43.758, floor(time * 5)) * 0.5
            let smoothT = fmod(time * (2 + speed / 128) + hashRand(Double(index), 4.14) * 3, 1.0)
            let flicker = target + sin(smoothT * .pi * 2) * (intensity / 510)
            return effectPrimary(n, pri).opacity(max(0.3, min(1, flicker)))
        }
        if name.contains("candle") {
            let candleSize = max(3, Int(intensity / 255 * T / 3))
            let center = total / 2
            guard abs(index - center) <= candleSize / 2 else { return Color.clear }
            let target = 0.5 + hashRand(Double(index) * 43.758, floor(time * 5)) * 0.5
            let smoothT = fmod(time * (2 + speed / 128), 1.0)
            let flicker = target + sin(smoothT * .pi * 2) * 0.15
            return effectPrimary(n, pri).opacity(max(0.3, min(1, flicker)))
        }
        if name.contains("fire 2012") || name.contains("lava") || name == "fire" {
            let invertedN = 1 - n
            let cool = speed / 255
            let spark = intensity / 255
            let baseHeat = invertedN * invertedN * (1 - cool * 0.4)
            let tq1 = floor(time * 8)
            let tq2 = floor(time * 4)
            let tq3 = floor(time * 12)
            let driftIdx = min(total - 1, index + 1)
            let driftIdx2 = min(total - 1, index + 2)
            let h1 = hashRand(Double(index), tq1) * 0.25
            let h2 = hashRand(Double(driftIdx) + 500, tq2) * 0.2
            let h3 = hashRand(Double(driftIdx2) + 1000, tq3) * 0.15
            let noise = (h1 + h2 + h3) - 0.2
            let sparkHash = hashRand(Double(index) + 2000, tq3)
            let sparkBoost = (invertedN > 0.65 && sparkHash < spark * 0.4) ? 0.3 + sparkHash * 0.4 : 0
            let heat = max(0, min(1, baseHeat + noise + sparkBoost))
            if paletteId > 5, let stops = PaletteColorData.stops[paletteId], !stops.isEmpty {
                return samplePaletteStops(stops, at: heat * 255)
            }
            if heat < 0.33 {
                return Color(red: heat / 0.33, green: 0, blue: 0)
            } else if heat < 0.66 {
                let ht = (heat - 0.33) / 0.33
                return Color(red: 1, green: ht * 0.8, blue: 0)
            } else {
                let ht = (heat - 0.66) / 0.34
                return Color(red: 1, green: 0.8 + ht * 0.2, blue: ht * 0.3)
            }
        }

        if name.contains("bouncing ball") {
            let numBalls = max(1, Int(intensity / 255 * 7) + 1)
            var bestBri = 0.0
            let gravity = 9.81 * 2
            for ball in 0..<numBalls {
                let h = hashRand(Double(effectId * 100 + ball), 78.233)
                let cor = max(0.6, 0.95 - Double(ball) * 0.05)
                let period = 2.0 + h * 2.5
                let localTime = fmod(time * (0.5 + speed / 128) + h * period, period)
                let v0 = sqrt(2 * gravity * (T - 1))
                var tRemain = localTime
                var velocity = v0
                var height = 0.0
                for _ in 0..<10 {
                    let tUp = velocity / gravity
                    let tBounce = tUp * 2
                    if tRemain < tBounce {
                        height = velocity * tRemain - 0.5 * gravity * tRemain * tRemain
                        break
                    }
                    tRemain -= tBounce
                    velocity *= cor
                    if velocity < 0.5 { height = 0; break }
                }
                let ballPos = max(0, min(T - 1, height))
                let dist = abs(Double(index) - ballPos)
                bestBri = max(bestBri, dist < 1.5 ? max(0, 1 - dist) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if name.contains("drip") {
            let numDrops = 1 + Int(intensity / 64)
            var bestBri = 0.0
            for d in 0..<numDrops {
                let h = hashRand(Double(effectId * 100 + d), 78.233)
                let cycleDur = 2.5 + h * 2
                let t = fmod(time * ns + h * cycleDur, cycleDur)
                let formPhase = 0.3
                if t / cycleDur < formPhase {
                    let formBri = t / (cycleDur * formPhase)
                    if abs(Double(index) - (T - 1)) < 1 { bestBri = max(bestBri, formBri) }
                } else {
                    let fallT = (t - cycleDur * formPhase) / (cycleDur * (1 - formPhase))
                    let fallPos = (T - 1) * (1 - fallT * fallT)
                    let dist = abs(Double(index) - fallPos)
                    bestBri = max(bestBri, dist < 1.5 ? max(0, 1 - dist) * max(0, 1 - fallT * 0.3) : 0)
                }
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if name.contains("popcorn") {
            let numKernels = max(1, Int(intensity / 32) + 1)
            var bestBri = 0.0
            for k in 0..<numKernels {
                let h = hashRand(Double(effectId * 100 + k), 78.233)
                let peakH = 0.5 + h * 0.5
                let cycleDur = 1.5 + h * 1.5
                let t = fmod(time * ns + h * cycleDur, cycleDur)
                let tNorm = t / cycleDur
                let height = 4 * tNorm * (1 - tNorm) * peakH
                let kPos = height * (T - 1)
                let dist = abs(Double(index) - kPos)
                bestBri = max(bestBri, dist < 1 ? max(0, 1 - dist) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if name.contains("firework starburst") || name.contains("explod") {
            let cycleDur = 2.0 / max(0.1, ns)
            let t = fmod(time, cycleDur)
            let burstPhase = t / cycleDur
            let center = hashRand(floor(time / cycleDur), 1.0) * T
            let numFrags = 5 + Int(intensity / 32)
            var bestBri = 0.0
            for f in 0..<numFrags {
                let vel = (hashRand(Double(f), 12.0) - 0.5) * 2 * T * 0.5
                let fragPos = center + vel * burstPhase
                let dist = abs(Double(index) - fragPos)
                bestBri = max(bestBri, dist < 1.5 ? max(0, 1 - dist) * max(0, 1 - burstPhase) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if (name.contains("firework 1d") || name.contains("fireworks 1d"))
            && !name.contains("ps fireworks 1d")
            && !name.contains("psfireworks1d") {
            let ni = intensity / 255.0
            let cycleDur = 3.0 / max(0.1, ns)
            let t = fmod(time, cycleDur)
            let phase = t / cycleDur
            if phase < 0.3 {
                let rocketPos = (phase / 0.3) * T * 0.7
                let trailW = 0.5 + ni * 2.0
                let dist = abs(Double(index) - rocketPos)
                return dist < trailW ? effectPrimary(n, pri).opacity(max(0, 1 - dist / trailW)) : Color.clear
            } else {
                let burstPhase = (phase - 0.3) / 0.7
                let center = T * 0.7
                let radius = burstPhase * T * (0.2 + ni * 0.3)
                let ringWidth = 1.5 + ni * 3.0
                let dist = abs(abs(Double(index) - center) - radius)
                let bri = max(0, 1 - dist / ringWidth) * max(0, 1 - burstPhase)
                return effectPrimary(n, pri).opacity(bri)
            }
        }
        if name.contains("firework") {
            let numBlobs = 3 + Int(intensity / 64)
            var bestBri = 0.0
            for b in 0..<numBlobs {
                let h = hashRand(Double(b), floor(time * 0.5))
                let pos = h * T
                let age = fmod(time * ns + Double(b) * 0.7, 2.0)
                let bri = max(0, 1 - age / 2) * max(0, 1 - abs(Double(index) - pos) / 3)
                bestBri = max(bestBri, bri)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }

        if name.contains("ripple rainbow") {
            let bgHue = fmod(time * 0.05, 1.0)
            let numR = 3 + Int(intensity / 64)
            var ringBri = 0.0
            for r in 0..<numR {
                let center = hashRand(Double(r), floor(time * 0.3 + Double(r))) * T
                let age = fmod(time * ns + Double(r) * 1.3, 3.0)
                let radius = age * ns * T * 0.12
                let dist = abs(abs(Double(index) - center) - radius)
                ringBri = max(ringBri, max(0, 1 - dist / 2) * max(0, 1 - age / 3))
            }
            if ringBri > 0.1 {
                return effectPrimary(n, pri).opacity(ringBri)
            }
            return paletteOrHue(bgHue, saturation: 0.3, brightness: 0.2)
        }
        if name.contains("ripple") {
            let numR = 3 + Int(intensity / 64)
            var bestBri = 0.0
            for r in 0..<numR {
                let center = hashRand(Double(r), floor(time * 0.3 + Double(r))) * T
                let age = fmod(time * ns + Double(r) * 1.3, 3.0)
                let radius = age * ns * T * 0.12
                let dist = abs(abs(Double(index) - center) - radius)
                bestBri = max(bestBri, max(0, 1 - dist / 2) * max(0, 1 - age / 3))
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if name.contains("pacifica") || name.contains("ocean") {
            let waveScale = 0.05 + intensity / 255.0 * 0.3
            var tR = 0.0, tG = 0.0, tB = 0.0
            let layers: [(spd: Double, scl: Double, bri: Double)] = [
                (0.8, 1.0, 0.15), (-0.6, 0.7, 0.12), (-0.4, 1.3, 0.10), (-0.3, 1.8, 0.08)
            ]
            for l in layers {
                let angle = (n * waveScale) * .pi * 2 * l.scl + time * l.spd * ns
                let wave = (sin(angle) + 1) / 2
                tR += wave * l.bri * 0.1
                tG += wave * l.bri * 0.6 + wave * wave * l.bri * 0.3
                tB += wave * l.bri * 0.8 + wave * wave * l.bri * 0.2
            }
            let avg = (tR + tG + tB) / 3
            if avg > 0.3 { let boost = (avg - 0.3) * 0.5; tR += boost; tG += boost; tB += boost }
            return Color(red: min(1, tR), green: min(1, tG), blue: min(1, tB))
        }
        if name.contains("lake") {
            let ni = intensity / 255.0
            let sp = speed / 10
            let wave1 = sin(time * (sp + 2) / 60.0 * .pi * 2) * 64
            let wave2 = sin(time * (sp + 1) / 60.0 * .pi * 2) * 64
            let wave3 = (sin(time * (sp + 2) / 60.0 * .pi * 2) + 1) / 2 * (40 + (1 - ni) * 60)
            let waveFreq1 = 15.0 + ni * 10.0
            let waveFreq2 = 23.0 + ni * 15.0
            let idx1 = (cos(fmod(Double(index) * waveFreq1 + wave1, 256) / 256 * .pi * 2) + 1) / 2 * 128
            let idx2 = (cos(fmod(Double(index) * waveFreq2 + wave2, 256) / 256 * .pi * 2) + 1) / 2 * 128
            let colorIndex = idx1 + idx2
            let lum = colorIndex > wave3 ? (colorIndex - wave3) / 255.0 : 0
            return paletteColorAt(fmod(colorIndex / 255.0, 1.0)).opacity(max(0.05, lum))
        }
        if name.contains("washing") {
            let nowShifted = UInt32(time * 1000) >> 7
            let x = UInt8(truncatingIfNeeded: nowShifted)
            let triSpeed: Double
            if x < 128 {
                let pw: UInt8 = 90, att: UInt8 = 15
                if x < att { triSpeed = Double(x) * 127.0 / Double(att) }
                else if x < pw - att { triSpeed = 127.0 }
                else if x < pw { triSpeed = Double(pw - x) * 127.0 / Double(att) }
                else { triSpeed = 0 }
            } else {
                let xAdj = x - 127
                let pw: UInt8 = 90, att: UInt8 = 15
                if xAdj < att { triSpeed = Double(xAdj) * -127.0 / Double(att) }
                else if xAdj < pw - att { triSpeed = -127.0 }
                else if xAdj < pw { triSpeed = Double(pw - xAdj) * -127.0 / Double(att) }
                else { triSpeed = 0 }
            }
            let stepRate = triSpeed * 2048.0 / max(1, 512.0 - speed)
            let step = time * stepRate * 10
            let freq = Double(Int(intensity / 25) + 1) * 255.0
            let col = sin(((freq * Double(index) / T) + step / 128.0) * .pi * 2 / 256.0) * 0.5 + 0.5
            return paletteColorAt(fmod(abs(col), 1.0))
        }

        if name.contains("colorloop") || name == "color loop" {
            let rate = Double((Int(speed) >> 2) + 2)
            let counter = fmod(time * 1000 * rate, 65536.0)
            let hue = fmod(counter / 256.0, 256.0) / 256.0
            let sat = intensity < 128 ? Double(intensity) / 128.0 : 1.0
            return paletteOrHue(hue, saturation: sat, brightness: 1)
        }
        if name.contains("pride") {
            let ni = intensity / 255.0
            let duration = 10.0 + speed
            let msmultiplier = (sin(time * 0.147) + 1) / 2 * 37 + 23
            let hueInc = (sin(time * 0.113) + 1) * 0.5 * 3 / T
            var hue = fmod(time * duration * 0.001 * (sin(time * 0.4) + 1) * 4.5 / T + Double(index) * hueInc, 1.0)
            hue = fmod(hue, 1.0)
            let brightDepth = ((sin(time * 0.341) + 1) / 4 + 0.25) * (0.5 + ni * 0.5)
            let brightnessThetaInc = (sin(time * 0.203) + 1) / 2 * (10 + ni * 20) + 20
            let bPhase = Double(index) * brightnessThetaInc / 256.0 + time * duration * msmultiplier * 0.000001
            let sinBri = sin(bPhase * .pi * 2)
            let bri = sinBri * sinBri * brightDepth + (1 - brightDepth)
            let sat = (sin(time * 0.087) + 1) / 2 * 0.03 + (0.76 + ni * 0.1)
            return paletteOrHue(hue, saturation: sat, brightness: max(0.1, bri))
        }
        if name.contains("aurora") {
            let numW = min(8, 2 + Int(intensity / 255 * 18))
            var tR = 0.0, tG = 0.0, tB = 0.0
            for w in 0..<numW {
                let h = hashRand(Double(effectId * 100 + w), 78.233)
                let wWidth = T / 6 + h * T / 3
                let wSpeed = (0.1 + h * 0.5) * ns
                let dir = h > 0.5 ? 1.0 : -1.0
                let center = fmod(abs(h * T + time * wSpeed * dir * T * 0.1), T)
                let lifetime = 1.5 + h * 2
                let age = fmod(time + h * lifetime, lifetime)
                let ageFactor = age < lifetime / 2 ? age / (lifetime / 2) : (lifetime - age) / (lifetime / 2)
                let offset = abs(Double(index) - center)
                let wrappedOff = min(offset, T - offset)
                let bri = max(0, 1 - wrappedOff / wWidth) * ageFactor * (0.6 + h * 0.4)
                let hueBase = fmod(h * 0.4 + 0.4 + time * 0.01, 1.0)
                let (cr, cg, cb) = paletteOrHSBComponents(hueBase, saturation: 0.7, brightness: bri)
                tR += cr; tG += cg; tB += cb
            }
            if tR + tG + tB < 0.01 { return Color.clear }
            let mx = max(tR, max(tG, tB))
            let s = mx > 1 ? 1 / mx : 1.0
            return Color(red: tR * s, green: tG * s, blue: tB * s)
        }
        if name.contains("colorful") {
            let cycleTime = max(0.05, (50.0 + 8.0 * (255.0 - speed)) / 1000.0)
            let frame = time / cycleTime
            let offset = Int(frame) % max(1, total)
            let stripe = abs((index + offset) / max(1, total / 4 + 1)) % 4
            if intensity > 160 || paletteId > 5 {
                return paletteColorAt(Double(stripe) / 4.0 + fmod(frame * 0.01, 1.0))
            } else if intensity < 80 {
                let hues: [Double] = [0.0, 0.08, 0.33, 0.66]
                return paletteOrHue(hues[stripe], saturation: 0.4, brightness: 1)
            } else {
                let hues: [Double] = [0.0, 0.08, 0.33, 0.66]
                return paletteOrHue(hues[stripe], saturation: 0.9, brightness: 1)
            }
        }
        if name == "rainbow" || name == "rainbow cycle" {
            let rate = Double((Int(speed) >> 2) + 2)
            let counter = fmod(time * 1000 * rate / 256.0, 256.0)
            let sizeShift = max(0, min(8, Int(intensity / 29)))
            let scale = Double(16 << sizeShift)
            let colorIndex = fmod(Double(index) * scale / T + counter, 256.0) / 256.0
            return paletteOrHue(colorIndex)
        }

        if name.contains("plasma") {
            let wf = 2.0 + Double(Int(speed / 50)) * 3
            let thisPhase = sin(time * 0.6) * 64
            let thatPhase = sin(time * 0.7) * 64
            let w1 = (1 + sin((Double(index) * wf + thisPhase) / T * .pi * 2)) / 2
            let cubic = w1 * w1 * (3 - 2 * w1)
            let w2 = (1 + cos((Double(index) * wf / 2 + thatPhase) / T * .pi * 2)) / 2
            let combined = (cubic + w2) / 2
            let minBri = intensity / 510
            let bri = max(minBri, combined * (1 - minBri) + minBri)
            return paletteColorAt(combined).opacity(max(0.1, bri))
        }
        if name.contains("phased noise") || name.contains("phased") {
            let ni = intensity / 255.0
            let freq1 = 2.0 + ni * 6.0
            let freq2 = 3.0 + ni * 8.0
            let f1 = sin(n * .pi * freq1 + time * ns * 2) * 0.5 + 0.5
            let f2 = sin(n * .pi * freq2 - time * ns * 1.7) * 0.5 + 0.5
            let noise = hashRand(Double(index) * 0.1, floor(time * 2)) * (0.1 + ni * 0.3)
            let combined = (f1 + f2) / 2 + noise
            return paletteColorAt(fmod(combined, 1.0))
        }
        if name.contains("noise") {
            let ni = intensity / 255.0
            let step = time * (1 + speed / 16)
            let noiseScale = 0.15 + ni * 0.5
            let x = (Double(index) + sin(time * 0.11) * 20) * noiseScale
            let n1 = sin(x + step) * 0.5 + 0.5
            let n2 = sin(x * 1.7 + step * 0.7) * 0.3
            let idx = fmod(abs(sin((n1 + n2) * 3)), 1.0)
            return paletteColorAt(idx)
        }

        if name.contains("dissolve random") {
            let cycleTime = max(0.5, (Double(255 - Int(speed)) + 15.0) / 30.0)
            let dissolveSpeed = max(0.3, intensity / 128.0)
            let progress = fmod(time * dissolveSpeed / cycleTime, 2.0)
            let p = progress < 1 ? progress : 2 - progress
            let pixelOrder = hashRand(Double(index), 4.1414)
            let dissolved = pixelOrder < p
            if dissolved {
                let hue = hashRand(Double(index), floor(time * dissolveSpeed / cycleTime))
                return paletteOrHue(hue, saturation: 0.9, brightness: 0.9)
            }
            return colorFromRGB(sec)
        }
        if name.contains("dissolve") {
            let cycleTime = max(0.5, (Double(255 - Int(speed)) + 15.0) / 30.0)
            let dissolveSpeed = max(0.3, intensity / 128.0)
            let progress = fmod(time * dissolveSpeed / cycleTime, 2.0)
            let p = progress < 1 ? progress : 2 - progress
            let pixelOrder = hashRand(Double(index), 4.1414)
            return pixelOrder < p ? effectPrimary(n, pri) : colorFromRGB(sec)
        }
        if name.contains("wipe random") || name.contains("sweep random") {
            let ni = intensity / 255.0
            let edgeWidth = max(1.0, 1.0 + ni * T * 0.15)
            let cycleDur = 0.75 + Double(255 - Int(speed)) * 0.15
            let prog = fmod(time, cycleDur) / cycleDur
            let back = prog > 0.5
            let halfProg = back ? (prog - 0.5) * 2 : prog * 2
            let ledIndex = halfProg * T
            let hue = hashRand(1.0, floor(time / cycleDur))
            let distFromEdge = Double(index) - ledIndex
            let edgeBlend = max(0, min(1, 0.5 - distFromEdge / edgeWidth))
            if back {
                return edgeBlend > 0.01 ? colorFromRGB(sec).opacity(edgeBlend) : paletteOrHue(hue, saturation: 0.9, brightness: 0.9)
            }
            return edgeBlend > 0.01 ? paletteOrHue(hue, saturation: 0.9, brightness: max(0.1, edgeBlend)) : colorFromRGB(sec)
        }
        if name.contains("sweep") {
            let ni = intensity / 255.0
            let edgeWidth = max(1.0, 1.0 + ni * T * 0.15)
            let cycleDur = 0.75 + Double(255 - Int(speed)) * 0.15
            let prog = fmod(time, cycleDur) / cycleDur
            let back = prog > 0.5
            let halfProg = back ? (prog - 0.5) * 2.0 : prog * 2.0
            let ledIndex = halfProg * T
            let effectiveIndex = back ? (T - 1.0 - Double(index)) : Double(index)
            let distFromEdge = effectiveIndex - ledIndex
            let edgeBlend = max(0, min(1, 0.5 - distFromEdge / edgeWidth))
            if back {
                return edgeBlend > 0.5 ? colorFromRGB(sec) : effectPrimary(n, pri).opacity(max(0.1, 1.0 - edgeBlend))
            }
            return edgeBlend > 0.5 ? effectPrimary(n, pri) : colorFromRGB(sec).opacity(max(0.1, 1.0 - edgeBlend))
        }
        if name.contains("wipe") {
            let ni = intensity / 255.0
            let edgeWidth = max(1.0, 1.0 + ni * T * 0.15)
            let cycleDur = 0.75 + Double(255 - Int(speed)) * 0.15
            let prog = fmod(time, cycleDur) / cycleDur
            let back = prog > 0.5
            let halfProg = back ? (prog - 0.5) * 2.0 : prog * 2.0
            let ledIndex = halfProg * T
            let distFromEdge = Double(index) - ledIndex
            let edgeBlend = max(0, min(1, 0.5 - distFromEdge / edgeWidth))
            if back {
                return edgeBlend > 0.5 ? colorFromRGB(sec) : effectPrimary(n, pri).opacity(max(0.1, 1.0 - edgeBlend))
            }
            return edgeBlend > 0.5 ? effectPrimary(n, pri) : colorFromRGB(sec).opacity(max(0.1, 1.0 - edgeBlend))
        }

        if name.contains("juggle") {
            var tR = 0.0, tG = 0.0, tB = 0.0
            let trailLen = max(1.0, T * Double(255 - Int(192 - intensity * 3 / 4)) / 255)
            for dot in 0..<8 {
                let freq = Double(16 + Int(speed)) * Double(dot + 7)
                let pos = (sin(time * freq / 1000 * .pi * 2) + 1) / 2 * (T - 1)
                let dist = abs(Double(index) - pos)
                let bri = max(0, 1 - dist / trailLen)
                let hue = Double(dot) / 8
                let (dr, dg, db) = paletteOrHSBComponents(hue, saturation: 0.86, brightness: bri)
                tR += dr; tG += dg; tB += db
            }
            if tR + tG + tB < 0.01 { return Color.clear }
            return Color(red: min(1, tR), green: min(1, tG), blue: min(1, tB))
        }
        if name.contains("palette") && !name.contains("noise") {
            let ni = intensity / 255.0
            let scale = 0.5 + ni * 3.0
            return paletteColorAt(fmod(n * scale + time * ns * 0.15, 1.0))
        }
        if name.contains("colorwave") {
            let duration = 10.0 + speed
            let hueInc = (sin(time * 0.113) + 1) * 0.5 * 300.0 * intensity * 10.0 / 255.0 / (T * 256.0)
            let palPos = fmod(time * duration * 0.001 + Double(index) * hueInc, 1.0)
            let brightDepth = (sin(time * 0.341) + 1) / 4.0 + 0.25
            let bPhase = Double(index) * (sin(time * 0.203) + 1) * 0.08 + time * duration * 0.0005
            let sinBri = sin(bPhase * .pi * 2)
            let bri = sinBri * sinBri * brightDepth + (1 - brightDepth)
            return paletteColorAt(palPos).opacity(max(0.1, bri))
        }
        if name.contains("bpm") {
            let ni = intensity / 255.0
            let bpm = 40 + speed / 4
            let beat = sin(time * bpm / 60 * .pi * 2)
            let pixelSpread = 2.0 + ni * 18.0
            let pos = fmod(n * pixelSpread / 20.0 + beat * 0.5, 1.0)
            let bri = (beat + 1) / 2 * (0.3 + ni * 0.4) + (0.3 + (1 - ni) * 0.3)
            return paletteColorAt(pos).opacity(bri)
        }
        if name.contains("railway") {
            let dur = (256.0 - speed) * 40.0 / 1000.0
            let rampdur = dur * intensity / 256.0
            let cycleTime = dur * 2
            let t = fmod(time, max(0.01, cycleTime))
            let halfT = fmod(t, max(0.01, dur))
            let direction = t >= dur
            var pos: Double
            if rampdur > 0.001 {
                pos = min(255.0, halfT / rampdur * 255.0)
            } else {
                pos = 255.0
            }
            if direction { pos = 255.0 - pos }
            let palPos = (index % 2 == 0) ? (255.0 - pos) : pos
            return paletteColorAt(palPos / 255.0)
        }
        if name.contains("saw") {
            let xScale = max(1.0, intensity / 4.0)
            let counter = time * 1000.0 * speed / 512.0
            let a = fmod(Double(index) * xScale - counter, 256.0)
            let normalized = fmod(abs(a), 256.0)
            let sawVal: Double
            if normalized < 16 {
                sawVal = (192.0 + normalized * 8.0) / 255.0
            } else {
                sawVal = (64.0 + (normalized - 16.0) / 239.0 * 128.0) / 255.0
            }
            let bri = 1.0 - sawVal
            return blendRGB(sec, pri, bri)
        }
        if name.contains("gradient") && !name.contains("palette") {
            let counter = time * 1000.0 * (Double((Int(speed) >> 2) + 1))
            let pp = fmod(counter / 65536.0 * T, T)
            let brd = max(1.0, intensity / 4.0)
            let p1 = pp - T
            let p2 = pp + T
            let dist = min(abs(pp - Double(index)), min(abs(p1 - Double(index)), abs(p2 - Double(index))))
            let val = dist < brd ? dist / brd : 1.0
            return blendRGB(pri, sec, val)
        }
        if name.contains("spots fade") {
            let spacing = max(2, total / (3 + Int(intensity / 64)))
            let dist = Double(index % spacing)
            let spotCenter = Double(spacing) / 2
            let spotDist = abs(dist - spotCenter) / spotCenter
            let counter = fmod(time * 1000.0 * (Double((Int(speed) >> 2) + 8)), 65536.0)
            let pulse = (sin(counter / 65536.0 * .pi * 2) + 1) / 2
            let bri = max(0, 1 - spotDist) * pulse
            return effectPrimary(n, pri).opacity(bri)
        }
        if name.contains("spots") {
            let spacing = max(2, total / (3 + Int(intensity / 64)))
            return index % spacing == 0 ? effectPrimary(n, pri) : colorFromRGB(sec)
        }
        if name.contains("traffic") {
            let rawSpeed = speed
            let longDur  = (150.0 + 100.0 * (255.0 - rawSpeed)) / 1000.0
            let shortDur = (150.0 +  20.0 * (255.0 - rawSpeed)) / 1000.0
            let usStyle = intensity > 140

            let totalCycle: Double
            if usStyle {
                totalCycle = longDur + longDur + shortDur
            } else {
                totalCycle = longDur + shortDur + longDur + shortDur
            }

            let phase = fmod(time, max(0.1, totalCycle))
            if usStyle {
                if phase < longDur {
                    return Color(red: 0.9, green: 0, blue: 0)
                } else if phase < longDur + longDur {
                    return Color(red: 0, green: 0.9, blue: 0)
                } else {
                    return Color(red: 0.9, green: 0.7, blue: 0)
                }
            } else {
                if phase < longDur {
                    return Color(red: 0.9, green: 0, blue: 0)
                } else if phase < longDur + shortDur {
                    return Color(red: 0.9, green: 0.4, blue: 0)
                } else if phase < longDur + shortDur + longDur {
                    return Color(red: 0, green: 0.9, blue: 0)
                } else {
                    return Color(red: 0.9, green: 0.7, blue: 0)
                }
            }
        }
        if name.contains("sunrise") || name.contains("sunset") {
            let isSunset = name.contains("sunset")
            let counter: Double
            if speed > 120 {
                let rate = ((speed - 120) / 2 + 1)
                counter = fmod(time * rate * 0.5, 65536)
            } else {
                counter = fmod(time * ns * 8, 65536)
            }
            let stage = counter < 32768 ? counter * 2 : 65535 - (counter - 32768) * 2
            let adjustedStage = isSunset ? 65535 - stage : stage
            let iPos = Double(index) / T
            let halfPos = iPos < 0.5 ? iPos : 1.0 - iPos
            let wave16 = halfPos * adjustedStage / 32768.0
            let wave = min(1.0, (wave16 / 256.0) + (wave16 * intensity / 32768.0))
            let palPos = min(240.0 / 255.0, wave)
            return paletteColorAt(palPos)
        }
        if name.contains("dynamic smooth") {
            let cycleTime = max(0.05, (50.0 + (255.0 - speed) * 15.0) / 1000.0)
            let frame = floor(time / cycleTime)
            let hue = hashRand(Double(index), frame)
            let target = hashRand(Double(index), frame + 1)
            let blend = fmod(time / cycleTime, 1.0)
            let changeProb = hashRand(Double(index) + 500, frame)
            let h = changeProb < intensity / 255.0 ? hue + (target - hue) * blend : hue
            return paletteOrHue(h, saturation: 0.8, brightness: 0.9)
        }
        if name.contains("dynamic") || name.contains("random color") {
            let cycleTime = max(0.05, (50.0 + (255.0 - speed) * 15.0) / 1000.0)
            let frame = floor(time / cycleTime)
            let changeProb = hashRand(Double(index) + 500, frame)
            let hue: Double
            if changeProb < intensity / 255.0 {
                hue = hashRand(Double(index), frame)
            } else {
                hue = hashRand(Double(index), frame - 1)
            }
            return paletteOrHue(hue, saturation: 0.8, brightness: 0.9)
        }
        if name.contains("halloween eyes") {
            let numEyes = max(1, Int(intensity / 64) + 1)
            let jumpInterval = max(0.5, 1.0 + (255.0 - speed) * 0.02)
            let slot = floor(time / jumpInterval)
            var bestBri = 0.0
            for e in 0..<numEyes {
                let eyePos = hashRand(slot + Double(e) * 100, 1.0) * (T - 6) + 3
                let spacing = 2 + hashRand(slot + Double(e) * 200, 2.0) * 3
                let blinkPhase = fmod(time / jumpInterval + hashRand(Double(e), 3.0), 1.0)
                let visible = blinkPhase < 0.15 || (blinkPhase > 0.25 && blinkPhase < 0.85)
                let isEye = abs(Double(index) - eyePos) < 1 || abs(Double(index) - (eyePos + spacing)) < 1
                if isEye && visible { bestBri = 1.0 }
            }
            return bestBri > 0 ? effectPrimary(n, pri) : Color.clear
        }
        if name.contains("blends") {
            let ni = intensity / 255.0
            let waveFreq = 8.0 + ni * 24.0
            let shift = time * 1000.0 * (Double((Int(speed) >> 3) + 1)) / 256.0
            let quadVal = abs(fmod(Double(index + 1) * waveFreq + shift, 256.0) - 128.0) / 128.0
            return paletteColorAt(fmod(quadVal + shift / 256.0, 1.0))
        }
        if name.contains("loading") {
            let counter = time * 1000.0 * (Double((Int(speed) >> 2) + 1))
            let pp = fmod(counter / 65536.0 * T, T)
            let brd = max(1.0, intensity / 2.0)
            let ahead = Double(index) > pp ? Double(index) - pp : pp - Double(index) + T
            let val = ahead < brd ? ahead / brd : 1.0
            return blendRGB(pri, sec, val)
        }
        if name.contains("two dots") || name.contains("two dot") {
            let dotWidth = max(1, Int(T * (intensity + 1) / 512))
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let offset = fmod(time / stepDelay, T)
            let ter = colors.count > 2 ? (colors[2].count >= 3 ? colors[2] : [0, 0, 0]) : [0, 0, 0]
            for w in 0..<dotWidth {
                let indexR = Int(fmod(offset + Double(w), T))
                let indexB = Int(fmod(offset + Double(w) + T / 2, T))
                if index == indexR { return effectPrimary(n, pri) }
                if index == indexB { return colorFromRGB(sec) }
            }
            return colorFromRGB(ter)
        }
        if name.contains("solid glitter") {
            let sparkle = hashRand(Double(index), floor(time * min(ns * 8, 12)))
            let threshold = 0.02 + (intensity / 255) * 0.12
            return sparkle < threshold ? Color.white : effectPrimary(n, pri)
        }
        if name.contains("chunchun") {
            let numBirds = 2 + total / 8
            let counter0 = time * 1000 * Double(6 + (Int(speed) >> 4))
            let span = (intensity * 256.0) / max(1, Double(numBirds))
            var bestBri = 0.0
            var bestColor = 0.0
            for i in 0..<numBirds {
                let counter = counter0 - Double(i) * span
                let sinVal = sin(counter * .pi * 2 / 65536.0)
                let megumin = (sinVal + 1.0) / 2.0
                let birdPos = megumin * (T - 1)
                let dist = abs(Double(index) - birdPos)
                if dist < 1.5 {
                    let bri = max(0, 1 - dist)
                    if bri > bestBri {
                        bestBri = bri
                        bestColor = Double(i) / max(1, Double(numBirds))
                    }
                }
            }
            guard bestBri > 0.01 else { return Color.clear }
            return paletteColorAt(bestColor).opacity(bestBri)
        }
        if name.contains("dancing shadow") {
            let numSpots = max(2, 2 + Int(intensity / 16))
            var totalBri = 0.0
            for s in 0..<numSpots {
                let h = hashRand(Double(effectId * 100 + s), 78.233)
                let spotWidth = 1 + h * 9
                let spotSpeed = (1.0 / (4.0 + h * 46.0)) * (1.0 + speed / 100.0)
                let dir = h > 0.5 ? 1.0 : -1.0
                let pos = fmod(abs(h * T + time * spotSpeed * dir * T * 0.05), T)
                let dist = abs(Double(index) - pos)
                if dist < spotWidth {
                    totalBri += max(0, 1 - dist / spotWidth)
                }
            }
            return paletteColorAt(n).opacity(min(1, totalBri))
        }
        if name.contains("rain") && !name.contains("rainbow") {
            let numDrops = max(1, Int(intensity / 32) + 2)
            var bestBri = 0.0
            for d in 0..<numDrops {
                let cycleDur = max(0.5, 2.5 / max(0.2, ns))
                let h = hashRand(Double(d), floor(time / cycleDur + Double(d)))
                let startPos = h * T
                let age = fmod(time * ns + Double(d) * 0.8, cycleDur)
                let pos = startPos + age * T * 0.1
                let dist = abs(Double(index) - fmod(pos, T))
                bestBri = max(bestBri, max(0, 1 - dist / 3) * max(0, 1 - age / cycleDur))
            }
            guard bestBri > 0.01 else { return Color.clear }
            return paletteColorAt(hashRand(Double(index), floor(time * 0.5))).opacity(bestBri)
        }
        if name.contains("tetrix") || name.contains("tetris") {
            let ni = intensity / 255.0
            let blockSize = 1.0 + ni * 4.0
            let stackHeight = fmod(time * ns * 0.5, 1.0) * T * 0.5
            let fallingPos = T - fmod(time * ns * 5, T - stackHeight)
            if Double(index) < stackHeight {
                return effectPrimary(n, pri)
            }
            if abs(Double(index) - fallingPos) < blockSize {
                let bri = max(0, 1 - abs(Double(index) - fallingPos) / blockSize)
                return colorFromRGB(sec).opacity(bri)
            }
            return Color.clear
        }
        if name.contains("percent") || name.contains("progress") {
            let pct = intensity / 255
            let edgeWidth = max(0.01, speed / 255.0 * 0.2)
            let dist = n - pct
            if dist < -edgeWidth { return effectPrimary(n, pri) }
            if dist > edgeWidth { return Color.clear }
            let blend = (dist + edgeWidth) / (edgeWidth * 2)
            return effectPrimary(n, pri).opacity(max(0, 1.0 - blend))
        }

        if name.contains("circus") {
            let ni = intensity / 255.0
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let segW = max(1, Int(T / (6 + ni * 12)))
            let offset = Int(time / stepDelay)
            let seg = abs(index + offset) % (segW * 3)
            if seg < segW { return effectPrimary(n, pri) }
            if seg < segW * 2 { return colorFromRGB(sec) }
            return .white
        }
        if name.contains("flow stripe") {
            let hl = T * 10.0 / 13.0
            let hue = time * 1000 / (speed + 1)
            let t = time * 1000 / (intensity / 8 + 1)
            let c1 = abs(Double(index) - hl) * 127.0 / max(1, hl)
            let s1 = (sin(c1 * .pi / 128.0) * 0.5 + 0.5) * 255.0
            let s2 = (sin((s1 / 2 + t) * .pi / 128.0) * 0.5 + 0.5) * 255.0
            let b = (sin((s2 + t / 8) * .pi / 128.0) * 0.5 + 0.5) * 255.0
            return paletteColorAt(fmod((b + hue) / 256.0, 1.0))
        }
        if name.contains("flow") {
            var counter = 0.0
            if speed > 0 {
                let rawCounter = time * 1000 * (Double((Int(speed) >> 2) + 1))
                counter = fmod(rawCounter / 256.0, 256.0)
            }
            let maxZones = max(1, total / 6)
            var zones = Int(intensity * Double(maxZones) / 256.0)
            if zones % 2 != 0 { zones += 1 }
            if zones < 2 { zones = 2 }
            let zoneLen = max(1, total / zones)
            let offset = (total - zones * zoneLen) / 2
            let relIdx = index - offset
            guard relIdx >= 0 && relIdx < zones * zoneLen else { return Color.clear }
            let zone = relIdx / zoneLen
            let posInZone = relIdx % zoneLen
            let colorIndex = fmod(Double(posInZone) * 255.0 / Double(zoneLen) - counter + 512, 256.0) / 256.0
            let led = zone % 2 == 0 ? posInZone : (zoneLen - 1 - posInZone)
            let _ = led
            return paletteColorAt(fmod(abs(colorIndex), 1.0))
        }
        if name.contains("lighthouse") {
            let counter = time * 1000.0 * (Double((Int(speed) >> 2) + 8))
            let beamPos = fmod(counter / 65536.0 * T, T)
            let dist = abs(Double(index) - beamPos)
            let wrappedDist = min(dist, T - dist)
            let trailLen = max(2.0, T * intensity / 512.0)
            let bri = max(0, 1 - wrappedDist / trailLen)
            guard bri > 0.01 else { return Color.clear }
            return paletteColorAt(n).opacity(bri)
        }
        if name.contains("pixels") {
            let cycleTime = max(0.05, (50.0 + (255.0 - speed) * 5.0) / 1000.0)
            let tq = floor(time / cycleTime)
            let px = hashRand(Double(index) + 300, tq)
            let threshold = intensity / 255.0
            guard px < threshold else { return Color.clear }
            return paletteColorAt(hashRand(Double(index), tq))
        }
        if name.contains("sine") && !name.contains("sinelon") {
            let freq = max(1.0, intensity / 4.0)
            let step = time * 1000.0 * speed / 16.0
            let angle = fmod(Double(index) * freq + step, 256.0)
            let normalized = fmod(abs(angle), 256.0) / 256.0
            let wave = normalized < 0.5
                ? 4.0 * normalized * normalized * (3.0 - 2.0 * normalized * 2.0)
                : 4.0 * (1.0 - normalized) * (1.0 - normalized) * (3.0 - 2.0 * (1.0 - normalized) * 2.0)
            let bri = wave
            return blendRGB(sec, pri, bri)
        }
        if name.contains("drift") && !name.contains("star") {
            let waveComplexity = 1.0 + intensity / 64.0
            let pos = fmod(n + time * ns * 0.05 + sin(time * 0.3 + n * waveComplexity) * 0.15, 1.0)
            return paletteColorAt(pos)
        }
        if name.contains("puddle") {
            let numPuddles = max(1, Int(intensity / 32) + 1)
            var bestBri = 0.0
            for p in 0..<numPuddles {
                let cycleDur = max(0.5, 2.0 / max(0.1, ns))
                let t = fmod(time + hashRand(Double(p), 78.233) * cycleDur, cycleDur)
                let tq = floor((time + hashRand(Double(p), 78.233) * cycleDur) / cycleDur)
                let center = hashRand(tq + Double(p) * 100, 42.0) * T
                let age = t / cycleDur
                let radius = age * T * 0.3
                let dist = abs(Double(index) - center)
                let ringDist = abs(dist - radius)
                if ringDist < 2 {
                    bestBri = max(bestBri, max(0, 1 - age) * max(0, 1 - ringDist / 2))
                }
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }

        if name.contains("chase 3") || name.contains("tricolor chase") {
            let cycleTime = (50.0 + (255.0 - speed) * 2.0) / 1000.0
            let it = time / cycleTime
            let width = max(1, 1 + Int(intensity / 16))
            let idx = (Int(it) + index) % (width * 3)
            let ter = colors.count > 2 ? (colors[2].count >= 3 ? colors[2] : [0, 0, 255]) : [0, 0, 255]
            if idx >= width * 2 { return paletteColorAt(n) }
            if idx >= width { return colorFromRGB(sec) }
            return colorFromRGB(ter)
        }
        if name.contains("chase flash rnd") || name.contains("chase flash random") {
            let stepDelay = (5.0 + (50.0 * (255.0 - speed)) / 255.0) * 0.033
            let counter = time / stepDelay
            let pos = fmod(counter, T)
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = fmod(T + Double(index) - pos, T)
            if dist < Double(size) {
                let flashOn = fmod(time * 6, 1.0) < 0.5
                let hue = hashRand(1.0, floor(time / stepDelay / T))
                return flashOn ? Color.white : paletteOrHue(hue)
            }
            return effectPrimary(n, pri)
        }
        if name.contains("rainbow runner") {
            let rate = Double((Int(speed) >> 2) + 1)
            let counter = fmod(time * 1000 * rate, 65536.0)
            let a = Int(fmod(counter * T / 65536.0, T))
            let size = max(1, 1 + Int(intensity * T / 1024))
            let dist = (index - a + total) % total
            if dist < size || dist >= total - size {
                return effectPrimary(n, pri)
            }
            let colorSep = max(1, 256 / total)
            let hue = fmod(Double(index * colorSep) / 256.0 + counter / 65536.0, 1.0)
            return paletteOrHue(hue)
        }
        if name.contains("stream 2") || name.contains("random chase") {
            let ni = intensity / 255.0
            let cycleTime = (10.0 + (255.0 - speed)) / 1000.0
            let frame = floor(time / cycleTime)
            let baseHue = hashRand(0.5, floor(frame / max(1, T)))
            let variation = hashRand(Double(index) + frame * 0.01, floor(frame / max(1, T)))
            let hue = baseHue + (variation - 0.5) * ni
            return paletteOrHue(hue, saturation: 0.5 + ni * 0.4, brightness: 0.9)
        }
        if name == "stream" || name.contains("running random") {
            let cycleTime = (25.0 + 3.0 * (255.0 - speed)) / 1000.0
            let it = time / cycleTime
            let zoneSize = max(1, Int((255.0 - intensity) / 16.0) + 1)
            let zone = (index + Int(it)) / zoneSize
            let hue = hashRand(Double(zone), 78.233)
            return paletteOrHue(hue)
        }
        if name.contains("solid pattern tri") {
            let segSize = max(1, Int(intensity / 32) + 1)
            let scrollOffset = Int(time * (1.0 + speed / 32.0))
            let ter = colors.count > 2 ? (colors[2].count >= 3 ? colors[2] : [0, 0, 255]) : [0, 0, 255]
            let seg = ((index + scrollOffset) / segSize) % 3
            if seg == 0 { return effectPrimary(n, pri) }
            if seg == 1 { return colorFromRGB(sec) }
            return colorFromRGB(ter)
        }
        if name.contains("solid pattern") {
            let lit = 1 + Int(speed)
            let unlit = 1 + Int(intensity)
            let cycle = lit + unlit
            let pos = index % cycle
            return pos < lit ? effectPrimary(n, pri) : colorFromRGB(sec)
        }
        if name.contains("tri wipe") || name.contains("tricolor wipe") {
            let ni = intensity / 255.0
            let edgeW = max(0.5, 0.5 + ni * T * 0.1)
            let cycleTime = 1.0 + (255.0 - speed) * 0.2
            let prog = fmod(time, cycleTime) / cycleTime
            let ledIndex = prog * T * 3.0
            let ter = colors.count > 2 ? (colors[2].count >= 3 ? colors[2] : [0, 0, 255]) : [0, 0, 255]
            if ledIndex < T {
                let d = Double(index) - ledIndex
                let blend = max(0, min(1, d / edgeW * 0.5 + 0.5))
                return blend < 0.5 ? colorFromRGB(sec) : effectPrimary(n, pri).opacity(max(0.2, blend))
            } else if ledIndex < T * 2 {
                let offset = ledIndex - T
                let d = Double(index) - offset
                let blend = max(0, min(1, d / edgeW * 0.5 + 0.5))
                return blend > 0.5 ? colorFromRGB(sec).opacity(max(0.2, blend)) : colorFromRGB(ter)
            } else {
                let offset = ledIndex - T * 2
                let d = Double(index) - offset
                let blend = max(0, min(1, d / edgeW * 0.5 + 0.5))
                return blend < 0.5 ? effectPrimary(n, pri) : colorFromRGB(ter).opacity(max(0.2, blend))
            }
        }
        if name.contains("fill noise") {
            let ni = intensity / 255.0
            let noiseScale = 0.005 + ni * 0.02
            let step = time * (1 + speed / 16)
            let noiseVal = sin(Double(index) * Double(total) * noiseScale + step * Double(index) * noiseScale)
            let idx = fmod(abs(noiseVal) * 128 + 128, 256.0) / 256.0
            return paletteColorAt(idx)
        }
        if name.contains("noise 1") {
            let ni = intensity / 255.0
            let scale = 160.0 + ni * 320.0
            let step = time * (1 + speed / 16)
            let shiftX = sin(time * 11.0 / 60.0) * 128
            let shiftY = step / 42
            let realX = (Double(index) + shiftX) * scale
            let realY = (Double(index) + shiftY) * scale
            let noise = sin(realX * 0.0001 + realY * 0.0001 + step * 0.001) * 0.5 + 0.5
            let idx = fmod(abs(sin(noise * 3 * 255 * .pi / 128)) * 0.5 + 0.5, 1.0)
            return paletteColorAt(idx)
        }
        if name.contains("noise 2") {
            let ni = intensity / 255.0
            let scale = 500.0 + ni * 1000.0
            let step = time * (1 + speed / 2)
            let shiftX = step / 64
            let realX = (Double(index) + shiftX) * scale
            let noise = sin(realX * 0.00001 + 4223 * 0.00001) * 0.5 + 0.5
            let idx = fmod(abs(sin(noise * 3 * 255 * .pi / 128)) * 0.5 + 0.5, 1.0)
            let bri = noise
            return paletteColorAt(idx).opacity(max(0.1, bri))
        }
        if name.contains("noise 3") {
            let ni = intensity / 255.0
            let scale = 400.0 + ni * 800.0
            let step = time * (1 + speed)
            let realX = (Double(index) + 4223) * scale
            let realY = (Double(index) + 1234) * scale
            let realZ = step * 8
            let noise = sin(realX * 0.00001 + realY * 0.00001 + realZ * 0.001) * 0.5 + 0.5
            let idx = fmod(abs(sin(noise * 3 * 255 * .pi / 128)) * 0.5 + 0.5, 1.0)
            let bri = noise
            return paletteColorAt(idx).opacity(max(0.1, bri))
        }
        if name.contains("noise 4") {
            let ni = intensity / 255.0
            let scale = 2048.0 + ni * 4096.0
            let stp = time * speed / 128.0
            let noiseVal = sin(Double(index) * scale * 0.001 + stp * 10)
            let idx = fmod(abs(noiseVal) * 0.5 + 0.5, 1.0)
            return paletteColorAt(idx)
        }
        if name.contains("noise pal") || name.contains("noisepal") {
            let scale = 15.0 + intensity / 4
            let step = time * (1 + speed / 32)
            let noiseVal = sin(Double(index) * scale * 0.1 + step + Double(index) * scale * 0.1)
            let idx = fmod(abs(noiseVal) * 0.5 + 0.5, 1.0)
            return paletteColorAt(idx)
        }
        if name.contains("color cloud") {
            let volSpeed = 1 + speed
            let hueSpeed = 1 + intensity
            let t = time * volSpeed / 8
            let hueT = time * hueSpeed / 8
            let vol = sin(Double(index) * 3.2 + t * 0.3) * 0.3 +
                      sin(Double(index) * 1.7 + t * 0.5) * 0.3 +
                      sin(Double(index) * 0.8 + t * 0.7) * 0.2
            let bri = max(0, min(1, (vol + 0.3) * 1.5))
            let hue = sin(Double(index) * 0.5 + hueT * 0.2) * 0.5 + 0.5
            guard bri > 0.02 else { return Color.clear }
            return paletteColorAt(fmod(hue, 1.0)).opacity(bri)
        }
        if name.contains("perlin move") || name.contains("perlinmove") {
            let numPixels = Int(intensity / 16) + 1
            var bestBri = 0.0
            for i in 0..<numPixels {
                let noisePos = sin(time * 128 / (260 - speed) + Double(i) * 15) * 0.5 + 0.5
                let pixloc = noisePos * (T - 1)
                let dist = abs(Double(index) - pixloc)
                bestBri = max(bestBri, dist < 1.5 ? max(0, 1 - dist) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return paletteColorAt(n).opacity(bestBri)
        }
        if name.contains("wavesins") || name.contains("wavesin") {
            let bri = (sin(time * 250 + Double(index) * intensity * .pi / 128) + 1) / 2
            let colorIdx = sin(time * speed * .pi / 128 + Double(index) * 0.3) * 0.5 + 0.5
            return paletteColorAt(fmod(colorIdx, 1.0)).opacity(max(0.05, bri))
        }
        if name.contains("shimmer") {
            let radius = max(1.0, T * 0.15)
            let traversalTime = (200.0 + (255.0 - speed) * 80.0) / 1000.0
            let pauseTime = intensity * 236.0 / 1000.0
            let cycleDur = traversalTime + pauseTime
            let t = fmod(time, cycleDur)
            if t > traversalTime {
                return colorFromRGB(sec)
            }
            let progress = t / traversalTime
            let pos = progress * (T + 2 * radius) - radius
            let dist = abs(pos - Double(index))
            guard dist < radius else { return colorFromRGB(sec) }
            let blend = dist / radius
            let c = paletteColorAt(Double(index) / T)
            return c.opacity(max(0, 1 - blend))
        }
        if name.contains("tv simulator") || name.contains("tv sim") {
            let sceneDur = max(0.3, (255.0 - speed) / 50.0 + 0.5)
            let scene = floor(time / sceneDur)
            let hue = hashRand(scene, 42.0)
            let sat = 0.3 + hashRand(scene, 78.0) * intensity / 255.0 * 0.7
            let bri = 0.4 + hashRand(scene, 12.0) * 0.6
            let progress = fmod(time, sceneDur) / sceneDur
            let fadeIn = min(1, progress * 5)
            let spatialVar = 1.0 - abs(n - 0.5) * 2 * (1 - intensity / 510)
            return paletteOrHue(hue, saturation: sat, brightness: bri * fadeIn * spatialVar)
        }
        if name.contains("plasmoid") {
            let numPixels = max(1, Int(intensity / 32) + 1)
            var bestBri = 0.0
            for i in 0..<numPixels {
                let phase = time * ns * 2 + Double(i) * speed / 128.0
                let pos = (sin(phase * 1.5) + 1) / 2 * (T - 1)
                let dist = abs(Double(index) - pos)
                bestBri = max(bestBri, dist < 2 ? max(0, 1 - dist / 2) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return paletteColorAt(fmod(n + time * 0.05, 1.0)).opacity(bestBri)
        }
        if name.contains("rolling ball") {
            let numBalls = max(1, Int(intensity / 16) + 1)
            var bestBri = 0.0
            for ball in 0..<numBalls {
                let h = hashRand(Double(effectId * 100 + ball), 78.233)
                let velocity = (h - 0.5) * 2 * (0.5 + speed / 255)
                let period = 2.0 / max(0.1, abs(velocity))
                let t = fmod(time + h * period, period)
                let phase = fmod(t * abs(velocity), 2.0)
                let height = phase < 1 ? phase : 2 - phase
                let ballPos = height * (T - 1)
                let dist = abs(Double(index) - ballPos)
                bestBri = max(bestBri, dist < 1.5 ? max(0, 1 - dist) : 0)
            }
            guard bestBri > 0.01 else { return Color.clear }
            return effectPrimary(n, pri).opacity(bestBri)
        }
        if name.contains("polar") {
            let numW = min(6, 2 + Int(intensity / 255 * 12))
            var tR = 0.0, tG = 0.0, tB = 0.0
            for w in 0..<numW {
                let h = hashRand(Double(effectId * 100 + w), 78.233)
                let wWidth = T / 5 + h * T / 4
                let wSpeed = (0.05 + h * 0.3) * ns
                let dir = h > 0.5 ? 1.0 : -1.0
                let center = fmod(abs(h * T + time * wSpeed * dir * T * 0.08), T)
                let offset = abs(Double(index) - center)
                let wrappedOff = min(offset, T - offset)
                let bri = max(0, 1 - wrappedOff / wWidth) * (0.5 + h * 0.5)
                let hueBase = fmod(h * 0.3 + 0.35 + time * 0.008, 1.0)
                let (cr, cg, cb) = paletteOrHSBComponents(hueBase, saturation: 0.8, brightness: bri)
                tR += cr; tG += cg; tB += cb
            }
            if tR + tG + tB < 0.01 { return Color.clear }
            let mx = max(tR, max(tG, tB))
            let s = mx > 1 ? 1 / mx : 1.0
            return Color(red: tR * s, green: tG * s, blue: tB * s)
        }

        return nil
    }

    private func colorFromRGB(_ rgb: [Int]) -> Color {
        guard rgb.count >= 3 else { return .white }
        return Color(red: Double(rgb[0]) / 255, green: Double(rgb[1]) / 255, blue: Double(rgb[2]) / 255)
    }

    private func effectPrimary(_ position: Double, _ primary: [Int]) -> Color {
        if paletteId > 5, let stops = PaletteColorData.stops[paletteId], !stops.isEmpty {
            return samplePaletteStops(stops, at: position * 255)
        }
        return colorFromRGB(primary)
    }

    private func paletteOrHue(_ position: Double, saturation: Double = 1, brightness: Double = 1) -> Color {
        if paletteId > 5 {
            return paletteColorAt(fmod(abs(position), 1.0)).opacity(brightness)
        }
        return Color(hue: fmod(abs(position), 1.0), saturation: saturation, brightness: brightness)
    }

    private func paletteOrHSBComponents(_ position: Double, saturation: Double = 1, brightness: Double = 1) -> (Double, Double, Double) {
        if paletteId > 5 {
            let rgb = paletteRGBAt(fmod(abs(position), 1.0))
            return (rgb.r * brightness, rgb.g * brightness, rgb.b * brightness)
        }
        return hsbComponents(position, saturation, brightness)
    }

    func paletteRGBAt(_ position: Double) -> (r: Double, g: Double, b: Double) {
        let palette = cachedPaletteRGB
        guard !palette.isEmpty else { return (1, 1, 1) }
        guard palette.count > 1 else { return palette[0] }
        let pos = max(0, min(1, position))
        let scaled = pos * Double(palette.count - 1)
        let idx = Int(scaled)
        let fraction = scaled - Double(idx)
        guard idx >= 0, idx < palette.count else { return palette[0] }
        if idx >= palette.count - 1 { return palette[palette.count - 1] }
        let c1 = palette[idx], c2 = palette[idx + 1]
        let t = max(0, min(1, fraction))
        return (c1.r + (c2.r - c1.r) * t, c1.g + (c2.g - c1.g) * t, c1.b + (c2.b - c1.b) * t)
    }

    private func blendRGB(_ c1: [Int], _ c2: [Int], _ t: Double) -> Color {
        guard c1.count >= 3, c2.count >= 3 else { return colorFromRGB(c1.count >= 3 ? c1 : [255, 128, 0]) }
        let clamped = max(0, min(1, t))
        return Color(
            red: (Double(c1[0]) + (Double(c2[0]) - Double(c1[0])) * clamped) / 255,
            green: (Double(c1[1]) + (Double(c2[1]) - Double(c1[1])) * clamped) / 255,
            blue: (Double(c1[2]) + (Double(c2[2]) - Double(c1[2])) * clamped) / 255
        )
    }

    private func hsbComponents(_ h: Double, _ s: Double, _ b: Double) -> (Double, Double, Double) {
        let c = b * s
        let hNorm = h - floor(h)
        let x = c * (1 - abs(fmod(hNorm * 6, 2) - 1))
        let m = b - c
        let (r, g, bl): (Double, Double, Double)
        switch abs(Int(hNorm * 6)) % 6 {
        case 0: (r, g, bl) = (c, x, 0)
        case 1: (r, g, bl) = (x, c, 0)
        case 2: (r, g, bl) = (0, c, x)
        case 3: (r, g, bl) = (0, x, c)
        case 4: (r, g, bl) = (x, 0, c)
        default: (r, g, bl) = (c, 0, x)
        }
        return (r + m, g + m, bl + m)
    }

    private func paletteColorAt(_ position: Double) -> Color {
        let palette = cachedPaletteRGB
        guard !palette.isEmpty else { return .white }
        guard palette.count > 1 else {
            let c = palette[0]
            return Color(red: c.r, green: c.g, blue: c.b)
        }
        let pos = max(0, min(1, position))
        let scaled = pos * Double(palette.count - 1)
        let idx = Int(scaled)
        let fraction = scaled - Double(idx)
        guard idx >= 0, idx < palette.count else {
            let c = palette[0]
            return Color(red: c.r, green: c.g, blue: c.b)
        }
        if idx >= palette.count - 1 {
            let c = palette[palette.count - 1]
            return Color(red: c.r, green: c.g, blue: c.b)
        }
        let c1 = palette[idx], c2 = palette[idx + 1]
        let t = max(0, min(1, fraction))
        return Color(
            red: c1.r + (c2.r - c1.r) * t,
            green: c1.g + (c2.g - c1.g) * t,
            blue: c1.b + (c2.b - c1.b) * t
        )
    }
}

final class MatrixFrameBuffer {
    private(set) var width: Int
    private(set) var height: Int
    private var pixels: [[(r: Double, g: Double, b: Double)]]
    private(set) var generation: Int = 0

    init(width: Int, height: Int) {
        self.width = width
        self.height = height
        self.pixels = Array(repeating: Array(repeating: (0.0, 0.0, 0.0), count: width), count: height)
    }

    func resize(width: Int, height: Int) {
        guard width != self.width || height != self.height else { return }
        self.width = width
        self.height = height
        self.pixels = Array(repeating: Array(repeating: (0.0, 0.0, 0.0), count: width), count: height)
        generation = 0
    }

    func getPixel(x: Int, y: Int) -> (r: Double, g: Double, b: Double) {
        guard x >= 0, x < width, y >= 0, y < height else { return (0, 0, 0) }
        return pixels[y][x]
    }

    func setPixel(x: Int, y: Int, color: (r: Double, g: Double, b: Double)) {
        guard x >= 0, x < width, y >= 0, y < height else { return }
        pixels[y][x] = color
    }

    func fade(by factor: Double) {
        for y in 0..<height {
            for x in 0..<width {
                let p = pixels[y][x]
                pixels[y][x] = (p.r * factor, p.g * factor, p.b * factor)
            }
        }
    }

    func clear() {
        for y in 0..<height {
            for x in 0..<width {
                pixels[y][x] = (0, 0, 0)
            }
        }
    }

    func seedRandom(density: Double, palette: [(r: Double, g: Double, b: Double)]) {
        for y in 0..<height {
            for x in 0..<width {
                let seed = Double(generation) * 127.1 + Double(x) * 12.9898 + Double(y) * 78.233
                let hash = abs(sin(seed) * 43758.5453)
                    .truncatingRemainder(dividingBy: 1.0)
                if hash < density {
                    let colorIdx = Int(hash * Double(max(1, palette.count))) % max(1, palette.count)
                    pixels[y][x] = palette[colorIdx]
                } else {
                    pixels[y][x] = (0, 0, 0)
                }
            }
        }
        generation += 1
    }

    func incrementGeneration() {
        generation += 1
    }
}
