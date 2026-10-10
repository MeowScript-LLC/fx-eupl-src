/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * Particle system effects by Damian Schneider (DedeHai).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-05.
 */

import SwiftUI

extension EffectSimulationEngine {

    private func particleVolcano2D(fx: Double, fy: Double, W: Double, H: Double,
                                   time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        return psBufferLookup(fx: fx, fy: fy, W: W, H: H, time: time) { buf in
            let emitFrames = max(1.0, 11.0 - intensity / 25.0)
            let emitsPerSec = 60.0 / emitFrames
            let cycle = 4.5
            let N = max(24, min(72, Int(emitsPerSec * cycle)))
            let launchVy = max(0.5, (speed / 4.0) / 64.0 * 60.0) * 0.45
            let grav = launchVy * 0.55
            let coneVar = (custom3 / 31.0 * 15.0) / 64.0 * 60.0 * 0.25
            let drift = (custom1 / 4.0) / 64.0 * 60.0 * 0.25
            let sourceX = drift > 0.01 ? (1.0 + (0.5 + 0.5 * sin(time * drift * 0.2)) * (W - 2.0)) : W * 0.5
            let sourceY = H - 0.5
            let wi = Int(W), hi = Int(H)
            for i in 0..<N {
                let s = Double(i) * 0.61803398875
                let r1 = fract(s * 12.345)
                let r2 = fract(s * 78.233)
                let r3 = fract(s * 43.758)
                let off = (Double(i) / Double(N)) * cycle
                let age = fract((time - off) / cycle + 16.0) * cycle
                let ttl = 1.0 - age / cycle
                if ttl <= 0 { continue }
                let vy = launchVy + (r1 - 0.5) * coneVar
                let vx = (r2 - 0.5) * coneVar
                let py = sourceY - (vy * age - 0.5 * grav * age * age)
                let px = sourceX + vx * age
                if px < 0 || px >= W || py < 0 || py >= H { continue }
                let palPos = fract(off / cycle + r3)
                let c = paletteRGBAt(palPos)
                let bri = min(1.0, ttl * 2.0)
                let pxi = Int(floor(px)), pyi = Int(floor(py))
                let xLo = max(0, pxi - 1), xHi = min(wi - 1, pxi + 1)
                let yLo = max(0, pyi - 1), yHi = min(hi - 1, pyi + 1)
                if xLo > xHi || yLo > yHi { continue }
                for yy in yLo...yHi {
                    for xx in xLo...xHi {
                        let dx = Double(xx) - px
                        let dy = Double(yy) - py
                        let distSq = dx * dx + dy * dy
                        if distSq > 1.96 { continue }
                        let f = max(0.0, 1.0 - sqrt(distSq) / 1.4) * bri
                        let idx = yy * wi + xx
                        buf[idx].r += c.r * f
                        buf[idx].g += c.g * f
                        buf[idx].b += c.b * f
                    }
                }
            }
            psNormalizeBuffer(&buf)
        }
    }

    private func particleFire2D(fx: Double, fy: Double, W: Double, H: Double,
                                time: TimeInterval, ns: Double) -> (r: Double, g: Double, b: Double) {
        return psBufferLookup(fx: fx, fy: fy, W: W, H: H, time: time) { buf in
            let speedV = max(110.0, speed)
            let spread = (custom3 + 1.0) / 32.0
            let halfSpread = (W * 0.5) * spread
            let N = max(40, min(96, Int(8.0 + halfSpread * 4.0)))
            let ttlSec = max(0.4, (H * 0.5 + 16.0) / 60.0)
            let vyBase = (H * 0.5 + speedV / 16.0 + custom1 / 16.0) / 64.0 * 60.0
            let cycle = ttlSec * 1.6
            let wi = Int(W), hi = Int(H)
            for i in 0..<N {
                let s = Double(i) * 0.61803398875
                let r1 = fract(s * 12.345)
                let r2 = fract(s * 78.233)
                let r3 = fract(s * 43.758)
                let spawnX = (W * 0.5 - halfSpread) + r1 * halfSpread * 2.0
                let vy = vyBase + (r2 - 0.5) * (2.0 + speedV / 16.0) / 64.0 * 60.0
                let vx = (r3 - 0.5) * 4.0 / 64.0 * 60.0
                let off = fract(Double(i) / Double(N)) * cycle
                let age = fract((time - off) / cycle + 16.0) * cycle
                let ttl = max(0.0, 1.0 - age / ttlSec)
                if ttl <= 0 { continue }
                let py = (H - 0.5) - vy * age * (1.0 + ttl * 0.6)
                let px = spawnX + vx * age + sin(time * 0.7 + Double(i)) * custom2 / 255.0 * 0.6 * H * 0.1
                if px < 0 || px >= W || py < -1 || py >= H { continue }
                let c = paletteRGBAt(min(1.0, ttl * 1.2))
                let bri = min(1.0, ttl * 0.6 + 0.05)
                let pxi = Int(floor(px)), pyi = Int(floor(py))
                let xLo = max(0, pxi - 1), xHi = min(wi - 1, pxi + 1)
                let yLo = max(0, pyi - 1), yHi = min(hi - 1, pyi + 1)
                if xLo > xHi || yLo > yHi { continue }
                for yy in yLo...yHi {
                    for xx in xLo...xHi {
                        let dx = Double(xx) - px
                        let dy = Double(yy) - py
                        let distSq = dx * dx + dy * dy
                        if distSq > 1.96 { continue }
                        let f = max(0.0, 1.0 - sqrt(distSq) / 1.4) * bri
                        let idx = yy * wi + xx
                        buf[idx].r += c.r * f
                        buf[idx].g += c.g * f
                        buf[idx].b += c.b * f
                    }
                }
            }
            psNormalizeBuffer(&buf)
        }
    }
}
