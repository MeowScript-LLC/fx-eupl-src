/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 * FXparticleSystem.cpp, FXparticleSystem.h: Copyright (c) 2024 Damian Schneider.
 * Licensed under the EUPL v. 1.2 or later.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-04-30.
 */

import Foundation

public final class PSFireworksHandler {

    public let ps: ParticleSystem1D

    public private(set) var callCount: UInt32 = 0

    public private(set) var aux0: UInt8 = 0

    private var initialized: Bool = false

    public init(ledCount: Int, paletteSampler: @escaping (UInt8) -> PSColor) {
        let count = max(80, ledCount * 8)
        self.ps = ParticleSystem1D(
            length: ledCount,
            particleCount: count,
            sourceCount: 4,
            advanced: true,
            paletteSampler: paletteSampler
        )
        ps.setKillOutOfBounds(true)
        ps.sources[0].sourceFlags.custom1 = true
        ps.sources[0].source.ttl = 1
        self.initialized = true
    }

    public func step(
        speed: UInt8,
        intensity: UInt8,
        c1: UInt8,
        c2: UInt8,
        c3: UInt8,
        o1: Bool,
        o2: Bool,
        o3: Bool
    ) {
        callCount &+= 1

        ps.setMotionBlur(c2)
        let gravity: Int32 = 1 + (Int32(speed) >> 3)
        ps.setGravity(speed > 0 ? Int8(min(127, gravity)) : 0)

        if ps.sources[0].sourceFlags.custom1 {
            if ps.sources[0].source.ttl > 0 {
                ps.sources[0].source.ttl &-= 1
            }
            if ps.sources[0].source.ttl == 0 {
                aux0 = (UInt8.random(in: 0..<255) < c1) ? 1 : 0

                ps.sources[0].sourceFlags.custom1 = false
                ps.sources[0].source.hue = UInt8.random(in: 0...255)
                ps.sources[0].variance = 10
                ps.sources[0].v = -10
                ps.sources[0].minLife = 30
                ps.sources[0].maxLife = o2 ? 400 : 60
                ps.sources[0].source.x = 0

                let half = Int32(ps.maxX) >> 1
                let quarter = Int32(ps.maxX) >> 2
                let randPart = Int32.random(in: 0..<max(1, half))
                let inside = (gravity &* (quarter &+ randPart)) >> 4
                let speedPx = UInt32(Double(max(0, inside)).squareRoot())
                ps.sources[0].source.vx = Int8(min(speedPx, 127))

                ps.sources[0].source.ttl = 4000
                ps.sources[0].sat = 30
                ps.sources[0].size = o3 ? 1 : 0
                ps.sources[0].sourceFlags.reversegrav = false

                if aux0 != 0 {
                    ps.sources[0].sourceFlags.reversegrav = true
                    ps.sources[0].source.x = ps.maxX
                    ps.sources[0].source.vx = -ps.sources[0].source.vx
                    ps.sources[0].v = -ps.sources[0].v
                }
            }
        }
        else {
            var rocketGravity: Int8 = -Int8(min(127, gravity))
            var currentSpeed = Int32(ps.sources[0].source.vx)
            if aux0 != 0 {
                rocketGravity = -rocketGravity
                currentSpeed = -currentSpeed
            }
            ps.applyForce(toSource: 0, force: rocketGravity, counter: &ps.psDataEnd)
            applyMoveToSource0()
            applyMoveToSource0()

            let rocketX = Int32(ps.sources[0].source.x)
            let rocketHeight: UInt32 = aux0 != 0
                ? UInt32(max(0, ps.maxX - rocketX))
                : UInt32(max(0, rocketX))

            if currentSpeed < 0 && ps.sources[0].source.ttl > 50 {
                let life = min(UInt32(50), rocketHeight >> UInt32(PS_P_RADIUS_SHIFT_1D + 3))
                ps.sources[0].source.ttl = UInt16(life)
            }

            if ps.sources[0].source.ttl < 2 {
                ps.sources[0].sourceFlags.custom1 = true
                let halfMaxX = Int32(ps.maxX) >> 1
                let denom = Int32(ps.maxX) << 2
                let varVal = 5 + (((halfMaxX + Int32(rocketHeight))
                                    * (200 + Int32(intensity))) / max(1, denom))
                ps.sources[0].variance = Int8(min(127, max(1, varVal)))
                ps.sources[0].minLife = 600
                ps.sources[0].maxLife = 1300
                ps.sources[0].source.ttl = UInt16(100 + Int.random(in: 0..<max(1, 64 - (Int(speed) >> 2))))
                ps.sources[0].sat = c3 < 16 ? UInt8(10 + (Int(c3) << 4)) : 255
                ps.sources[0].size = o3 ? UInt8.random(in: 0..<max(1, intensity)) : 0

                let xPart = Int32(ps.sources[0].source.x) >> Int32(PS_P_RADIUS_SHIFT_1D - 1)
                var explosionSize = Int32(8) + (Int32(ps.maxXpixel) >> 2) + xPart
                let extra = (explosionSize * Int32(intensity)) >> 8
                explosionSize += Int32.random(in: 0...max(1, extra))

                for _ in 0..<Int(explosionSize) {
                    let idx = ps.sprayEmit(ps.sources[0])
                    if idx < 0 { break }
                    if c3 > 23 {
                        if c3 == 31 {
                            ps.setColorByAge(o1)
                            ps.setColorByPosition(!o1)
                        } else {
                            let absvx = UInt32(abs(Int32(ps.particles[idx].vx)))
                            let varU = UInt32(max(1, ps.sources[0].variance))
                            let mappedMax = UInt32(16 + Int.random(in: 0..<200))
                            let mapped = (absvx * mappedMax) / varU
                            let hue = UInt8(truncatingIfNeeded: Int(mapped) &+ Int(ps.sources[0].source.hue))
                            ps.particles[idx].hue = hue
                        }
                    } else {
                        if o1 {
                            ps.sources[0].source.hue = UInt8.random(in: 0...255)
                        }
                    }
                }
            }
        }

        if (callCount & 0x1) == 0
            && !ps.sources[0].sourceFlags.custom1
            && ps.sources[0].source.ttl > 50 {
            ps.sprayEmit(ps.sources[0])
        }

        if (callCount & 0x3) == 0 {
            ps.applyFriction(coefficient: 1)
        }

        ps.update()

        for i in 0..<ps.usedParticles {
            if ps.particles[i].ttl > 10 {
                ps.particles[i].ttl &-= 10
            } else {
                ps.particles[i].ttl = 0
            }
        }
    }

    private func applyMoveToSource0() {
        var srcParticle = ps.sources[0].source
        var srcFlags = ps.sources[0].sourceFlags
        var fakeFlags = PSParticleFlags1D()
        fakeFlags.perpetual = srcFlags.perpetual
        fakeFlags.reversegrav = srcFlags.reversegrav
        if srcParticle.ttl > 0 {
            if !fakeFlags.perpetual { srcParticle.ttl &-= 1 }
            let newX = Int32(srcParticle.x) + Int32(srcParticle.vx)
            srcParticle.x = newX
        }
        ps.sources[0].source = srcParticle
        ps.sources[0].sourceFlags.outofbounds = srcFlags.outofbounds
        _ = srcFlags
    }

    @inline(__always)
    public func color(at led: Int) -> PSColor {
        guard led >= 0 && led < ps.framebuffer.count else { return .black }
        return ps.framebuffer[led]
    }

    @inline(__always)
    public var ledCount: Int { ps.framebuffer.count }
}
