/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FXparticleSystem.cpp, FXparticleSystem.h: Copyright (c) 2024 Damian Schneider.
 * Licensed under the EUPL v. 1.2 or later.
 * Translated from the WLED-MM (MoonModules, https://github.com/MoonModules/WLED-MM)
 * FXparticleSystem.cpp/.h, which is EUPL v1.2 (c) 2025 Damian Schneider.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

import Foundation

@inline(__always) public let PS_P_MAXSPEED: Int32 = 120

@inline(__always) public let PS_P_RADIUS_1D: Int32 = 32
@inline(__always) public let PS_P_HALFRADIUS_1D: Int32 = PS_P_RADIUS_1D >> 1
@inline(__always) public let PS_P_RADIUS_SHIFT_1D: Int32 = 5
@inline(__always) public let PS_P_SURFACE_1D: Int32 = 5
@inline(__always) public let PS_P_MINHARDRADIUS_1D: Int32 = 32
@inline(__always) public let PS_P_MINSURFACEHARDNESS_1D: Int32 = 120

public struct PSColor: Equatable {
    public var r: UInt8
    public var g: UInt8
    public var b: UInt8
    @inlinable public init(_ r: UInt8 = 0, _ g: UInt8 = 0, _ b: UInt8 = 0) {
        self.r = r; self.g = g; self.b = b
    }
    @inlinable public static var black: PSColor { PSColor(0, 0, 0) }
}

@inline(__always)
public func fastColorAdd(_ c1: inout PSColor, _ c2: PSColor, _ scale: UInt8 = 255) {
    var r: UInt32, g: UInt32, b: UInt32
    if scale < 255 {
        let s = UInt32(scale)
        r = UInt32(c1.r) + ((UInt32(c2.r) * s) >> 8)
        g = UInt32(c1.g) + ((UInt32(c2.g) * s) >> 8)
        b = UInt32(c1.b) + ((UInt32(c2.b) * s) >> 8)
    } else {
        r = UInt32(c1.r) + UInt32(c2.r)
        g = UInt32(c1.g) + UInt32(c2.g)
        b = UInt32(c1.b) + UInt32(c2.b)
    }
    let m = max(r, max(g, b))
    if m <= 255 {
        c1.r = UInt8(r); c1.g = UInt8(g); c1.b = UInt8(b)
    } else {
        let newscale = (UInt32(255) << 16) / m
        c1.r = UInt8(min(UInt32(255), (r * newscale) >> 16))
        c1.g = UInt8(min(UInt32(255), (g * newscale) >> 16))
        c1.b = UInt8(min(UInt32(255), (b * newscale) >> 16))
    }
}

@inline(__always)
public func fastColorScale(_ c: inout PSColor, _ scale: UInt8) {
    let s = UInt32(scale)
    c.r = UInt8((UInt32(c.r) * s) >> 8)
    c.g = UInt8((UInt32(c.g) * s) >> 8)
    c.b = UInt8((UInt32(c.b) * s) >> 8)
}

@inline(__always)
public func calcForceDV(_ force: Int8, _ counter: inout UInt8) -> Int32 {
    if force == 0 { return 0 }
    let forceAbs = Int32(abs(Int32(force)))
    if forceAbs < 16 {
        let c = Int32(counter) + forceAbs
        if c > 15 {
            counter = UInt8(c - 16)
            return force < 0 ? -1 : 1
        } else {
            counter = UInt8(c)
            return 0
        }
    } else {
        return Int32(force) / 16
    }
}

@inline(__always)
public func limitSpeed(_ v: Int32) -> Int8 {
    if v > PS_P_MAXSPEED { return Int8(PS_P_MAXSPEED) }
    if v < -PS_P_MAXSPEED { return Int8(-PS_P_MAXSPEED) }
    return Int8(v)
}

public struct PSParticle1D {
    public var x: Int32 = 0
    public var ttl: UInt16 = 0
    public var vx: Int8 = 0
    public var hue: UInt8 = 0
    public init() {}
}

public struct PSParticleFlags1D {
    public var outofbounds: Bool = false
    public var collide: Bool = false
    public var perpetual: Bool = false
    public var reversegrav: Bool = false
    public var forcedirection: Bool = false
    public var fixed: Bool = false
    public var custom1: Bool = false
    public var custom2: Bool = false
    public init() {}
}

public struct PSAdvancedParticle1D {
    public var sat: UInt8 = 255
    public var size: UInt8 = 0
    public var forceCounter: UInt8 = 0
    public init() {}
}

public struct PSSource1D {
    public var minLife: UInt16 = 100
    public var maxLife: UInt16 = 400
    public var source: PSParticle1D = .init()
    public var sourceFlags: PSParticleFlags1D = .init()
    public var variance: Int8 = 0
    public var v: Int8 = 0
    public var sat: UInt8 = 255
    public var size: UInt8 = 0
    public init() {}
}

public struct PSSettings1D {
    public var wrap: Bool = false
    public var bounce: Bool = false
    public var killoutofbounds: Bool = false
    public var useGravity: Bool = false
    public var useCollisions: Bool = false
    public var colorByAge: Bool = false
    public var colorByPosition: Bool = false
    public init() {}
}

public final class ParticleSystem1D {

    public var particles: [PSParticle1D]
    public var particleFlags: [PSParticleFlags1D]
    public var sources: [PSSource1D]
    public var advPartProps: [PSAdvancedParticle1D]?

    public var framebuffer: [PSColor]

    public var settings: PSSettings1D = .init()

    public var paletteSampler: (UInt8) -> PSColor

    public var backgroundColor: PSColor = .black

    public private(set) var maxX: Int32
    public private(set) var maxXpixel: Int32

    public private(set) var numSources: Int
    public var usedParticles: Int

    public var motionBlur: UInt8 = 0
    public var smearBlur: UInt8 = 0
    public var particleSize: UInt8 = 0
    public var wallHardness: UInt8 = 255
    public var gforce: Int8 = 0

    public var emitIndex: Int = 0
    public var collisionStartIdx: Int = 0
    public var gforceCounter: UInt8 = 0
    public var particleHardRadius: Int32 = PS_P_MINHARDRADIUS_1D

    public var psDataEnd: UInt8 = 0

    public init(length: Int,
                particleCount: Int,
                sourceCount: Int,
                advanced: Bool = false,
                paletteSampler: @escaping (UInt8) -> PSColor) {
        self.numSources = sourceCount
        self.usedParticles = particleCount
        self.particles = Array(repeating: PSParticle1D(), count: particleCount)
        self.particleFlags = Array(repeating: PSParticleFlags1D(), count: particleCount)
        self.sources = Array(repeating: PSSource1D(), count: sourceCount)
        self.advPartProps = advanced
            ? Array(repeating: PSAdvancedParticle1D(), count: particleCount)
            : nil
        self.maxX = Int32(length) * PS_P_RADIUS_1D - 1
        self.maxXpixel = Int32(length) - 1
        self.framebuffer = Array(repeating: .black, count: length)
        self.paletteSampler = paletteSampler

        for i in 0..<sourceCount {
            sources[i].source.ttl = 1
            sources[i].sourceFlags = PSParticleFlags1D()
        }
        if advanced {
            for i in 0..<particleCount {
                advPartProps?[i].sat = 255
            }
        }
    }

    public func setMotionBlur(_ amount: UInt8) { self.motionBlur = amount }
    public func setSmearBlur(_ amount: UInt8) { self.smearBlur = amount }
    public func setParticleSize(_ size: UInt8) { self.particleSize = size }
    public func setWallHardness(_ h: UInt8) { self.wallHardness = h }
    public func setWrap(_ enable: Bool) { settings.wrap = enable }
    public func setBounce(_ enable: Bool) { settings.bounce = enable }
    public func setKillOutOfBounds(_ enable: Bool) { settings.killoutofbounds = enable }
    public func setColorByAge(_ enable: Bool) { settings.colorByAge = enable }
    public func setColorByPosition(_ enable: Bool) { settings.colorByPosition = enable }

    public func setGravity(_ force: Int8) {
        if force != 0 {
            gforce = force
            settings.useGravity = true
        } else {
            settings.useGravity = false
        }
    }

    @discardableResult
    public func sprayEmit(_ emitter: PSSource1D) -> Int {
        for _ in 0..<usedParticles {
            emitIndex += 1
            if emitIndex >= usedParticles { emitIndex = 0 }
            if particles[emitIndex].ttl == 0 {
                let v32 = emitter.variance == 0
                    ? 0
                    : Int32.random(in: 0..<(Int32(emitter.variance) << 1))
                particles[emitIndex].vx = limitSpeed(Int32(emitter.v) + v32 - Int32(emitter.variance))
                particles[emitIndex].x = emitter.source.x
                particles[emitIndex].hue = emitter.source.hue
                let lo = UInt16(emitter.minLife)
                let hi = max(UInt16(emitter.maxLife), lo &+ 1)
                particles[emitIndex].ttl = UInt16.random(in: lo..<hi)
                particleFlags[emitIndex].collide = emitter.sourceFlags.collide
                particleFlags[emitIndex].reversegrav = emitter.sourceFlags.reversegrav
                particleFlags[emitIndex].perpetual = emitter.sourceFlags.perpetual
                particleFlags[emitIndex].outofbounds = false
                particleFlags[emitIndex].fixed = false
                if advPartProps != nil {
                    advPartProps![emitIndex].sat = emitter.sat
                    advPartProps![emitIndex].size = emitter.size
                }
                return emitIndex
            }
        }
        return -1
    }

    public func particleMoveUpdate(_ pIdx: Int, options: PSSettings1D? = nil, advIdx: Int? = nil) {
        var part = particles[pIdx]
        var flags = particleFlags[pIdx]
        let opts = options ?? settings
        if part.ttl == 0 {
            particles[pIdx] = part
            particleFlags[pIdx] = flags
            return
        }

        if !flags.perpetual { part.ttl &-= 1 }
        if opts.colorByAge { part.hue = UInt8(min(Int(part.ttl), 255)) }

        var renderRadius: Int32 = PS_P_HALFRADIUS_1D
        var newX: Int32 = part.x &+ Int32(part.vx)
        flags.outofbounds = false

        if let advIdx = advIdx, let adv = advPartProps?[advIdx] {
            if adv.size > 1 {
                particleHardRadius = PS_P_MINHARDRADIUS_1D + Int32(adv.size >> 1)
            } else {
                particleHardRadius = PS_P_MINHARDRADIUS_1D >> 1
            }
            renderRadius = particleHardRadius
        }

        if opts.bounce {
            if newX < particleHardRadius || newX > (maxX - particleHardRadius) {
                var bouncethis = true
                if opts.useGravity {
                    if flags.reversegrav {
                        if newX < particleHardRadius { bouncethis = false }
                    } else if newX > particleHardRadius {
                        bouncethis = false
                    }
                }
                if bouncethis {
                    let neg = -Int32(part.vx)
                    let damped = (neg * Int32(wallHardness)) / 255
                    part.vx = limitSpeed(damped)
                    if newX < particleHardRadius {
                        newX = particleHardRadius
                    } else {
                        newX = maxX - particleHardRadius
                    }
                }
            }
        }

        if !checkBoundsAndWrap(&newX, max: maxX, particleRadius: renderRadius, wrap: opts.wrap) {
            flags.outofbounds = true
            if opts.killoutofbounds {
                var killthis = true
                if opts.useGravity {
                    if flags.reversegrav {
                        if newX < 0 || newX > (maxX << 2) { killthis = false }
                    } else {
                        if newX > 0 && newX < (maxX << 2) { killthis = false }
                    }
                }
                if killthis { part.ttl = 0 }
            }
        }

        if !flags.fixed {
            part.x = newX
        } else {
            part.vx = 0
        }

        particles[pIdx] = part
        particleFlags[pIdx] = flags
    }

    @inline(__always)
    private func checkBoundsAndWrap(_ position: inout Int32, max: Int32, particleRadius: Int32, wrap: Bool) -> Bool {
        if position < 0 || position > max {
            if wrap {
                position = position % (max + 1)
                if position < 0 { position += (max + 1) }
                return true
            } else if position < -particleRadius || position > max + particleRadius {
                return false
            }
        }
        return true
    }

    public func applyForce(particleIndex: Int, force: Int8, counter: inout UInt8) {
        let dv = calcForceDV(force, &counter)
        let next = Int32(particles[particleIndex].vx) + dv
        particles[particleIndex].vx = limitSpeed(next)
    }

    public func applyForce(toSource sourceIndex: Int, force: Int8, counter: inout UInt8) {
        let dv = calcForceDV(force, &counter)
        let next = Int32(sources[sourceIndex].source.vx) + dv
        sources[sourceIndex].source.vx = limitSpeed(next)
    }

    public func applyGravity(particleIndex: Int) {
        let saved = gforceCounter
        let dv = calcForceDV(gforce, &gforceCounter)
        let signed = particleFlags[particleIndex].reversegrav ? -dv : dv
        gforceCounter = saved
        let next = Int32(particles[particleIndex].vx) - signed
        particles[particleIndex].vx = limitSpeed(next)
    }

    public func applyGravityAll() {
        for i in 0..<usedParticles where particles[i].ttl > 0 {
            let dv = calcForceDV(gforce, &gforceCounter)
            let signed = particleFlags[i].reversegrav ? -dv : dv
            particles[i].vx = limitSpeed(Int32(particles[i].vx) - signed)
        }
    }

    public func applyFriction(coefficient: Int32) {
        let friction = 255 - coefficient
        for i in 0..<usedParticles where particles[i].ttl > 0 {
            let next = (Int32(particles[i].vx) * friction) / 255
            particles[i].vx = Int8(clamping: next)
        }
    }

    public func update() {
        if settings.useGravity { applyGravityAll() }

        for i in 0..<usedParticles {
            particleMoveUpdate(i, options: nil, advIdx: advPartProps != nil ? i : nil)
        }

        if settings.colorByPosition {
            let scale = (UInt32(255) << 16) / UInt32(max(1, maxX))
            for i in 0..<usedParticles where particles[i].ttl > 0 {
                let h = (scale &* UInt32(max(0, particles[i].x))) >> 16
                particles[i].hue = UInt8(min(255, Int(h)))
            }
        }

        render()
    }

    public func render() {
        if motionBlur > 0 {
            for x in 0...Int(maxXpixel) {
                fastColorScale(&framebuffer[x], motionBlur)
            }
        } else {
            for x in 0...Int(maxXpixel) { framebuffer[x] = .black }
        }

        let useNoWrap = settings.colorByAge || settings.colorByPosition
        _ = useNoWrap

        for i in 0..<usedParticles {
            let p = particles[i]
            let f = particleFlags[i]
            if p.ttl == 0 || f.outofbounds { continue }

            let brightness = UInt8(min(Int(p.ttl) << 1, 255))
            var baseRGB = paletteSampler(p.hue)

            if let adv = advPartProps?[i], adv.sat < 255 {
                baseRGB = desaturate(baseRGB, sat: adv.sat)
            }

            renderParticle(i, brightness: brightness, color: baseRGB, wrap: settings.wrap)
        }

        if smearBlur > 0 {
            blur1D(&framebuffer, size: Int(maxXpixel) + 1, blur: UInt32(smearBlur), start: 0)
        }

        if backgroundColor != .black {
            for i in 0...Int(maxXpixel) {
                fastColorAdd(&framebuffer[i], backgroundColor, 255)
            }
        }
    }

    @inline(__always)
    public func renderParticle(_ pIdx: Int, brightness: UInt8, color: PSColor, wrap: Bool) {
        var size = Int(particleSize)
        if let adv = advPartProps?[pIdx] {
            size = Int(adv.size)
        }
        let p = particles[pIdx]

        if size == 0 {
            let x = Int(p.x >> PS_P_RADIUS_SHIFT_1D)
            if x >= 0 && x <= Int(maxXpixel) {
                fastColorAdd(&framebuffer[x], color, brightness)
            }
            return
        }

        var pxlIsInFrame = (true, true)
        let xoffset = Int32(p.x) + PS_P_HALFRADIUS_1D
        let dx = xoffset & (PS_P_RADIUS_1D - 1)
        var x = Int(xoffset >> PS_P_RADIUS_SHIFT_1D)
        var pixco = (x - 1, x)

        let bri = Int32(brightness)
        let pxlbri0 = ((PS_P_RADIUS_1D - dx) * bri) >> PS_P_SURFACE_1D
        let pxlbri1 = (dx * bri) >> PS_P_SURFACE_1D

        if size > 1 {
            var renderbuffer = [PSColor](repeating: .black, count: 10)
            fastColorAdd(&renderbuffer[4], color, UInt8(clamping: pxlbri0))
            fastColorAdd(&renderbuffer[5], color, UInt8(clamping: pxlbri1))
            var rendersize = 2
            var offset = 4
            var blurpasses = (size / 64) + 1
            var bitshift = 0
            var sizeRemaining = size
            for i in 0..<blurpasses {
                if i == 2 { bitshift = 1 }
                rendersize += 2
                offset -= 1
                blur1D(&renderbuffer, size: rendersize,
                       blur: UInt32(sizeRemaining << bitshift), start: offset)
                sizeRemaining = sizeRemaining > 64 ? sizeRemaining - 64 : 0
            }
            _ = blurpasses

            let xfbOrig = x - (rendersize >> 1) + 1 - offset
            for xrb in offset..<(rendersize + offset) {
                var xfb = xfbOrig + xrb
                if xfb < 0 || xfb > Int(maxXpixel) {
                    if wrap {
                        let len = Int(maxXpixel) + 1
                        xfb = ((xfb % len) + len) % len
                    } else {
                        continue
                    }
                }
                fastColorAdd(&framebuffer[xfb], renderbuffer[xrb], 255)
            }
            return
        }

        if pixco.0 < 0 {
            if wrap { pixco.0 = Int(maxXpixel) }
            else { pxlIsInFrame.0 = false }
        } else if pixco.1 > Int(maxXpixel) {
            if wrap { pixco.1 = 0 }
            else { pxlIsInFrame.1 = false }
        }
        if pxlIsInFrame.0 && pixco.0 >= 0 && pixco.0 <= Int(maxXpixel) {
            fastColorAdd(&framebuffer[pixco.0], color, UInt8(clamping: pxlbri0))
        }
        if pxlIsInFrame.1 && pixco.1 >= 0 && pixco.1 <= Int(maxXpixel) {
            fastColorAdd(&framebuffer[pixco.1], color, UInt8(clamping: pxlbri1))
        }
        _ = x
    }
}

public func blur1D(_ buffer: inout [PSColor], size: Int, blur: UInt32, start: Int) {
    var carryover = PSColor.black
    let seep = UInt8(min(255, Int(blur) >> 1))
    for x in start..<(start + size) {
        guard x >= 0 && x < buffer.count else { continue }
        var seeppart = buffer[x]
        fastColorScale(&seeppart, seep)
        if x > 0 && (x - 1) < buffer.count {
            fastColorAdd(&buffer[x - 1], seeppart, 255)
            if carryover != .black {
                fastColorAdd(&buffer[x], carryover, 255)
            }
        }
        carryover = seeppart
    }
}

@inline(__always)
private func desaturate(_ c: PSColor, sat: UInt8) -> PSColor {
    if sat >= 255 { return c }
    let lum = (UInt32(c.r) * 76 + UInt32(c.g) * 150 + UInt32(c.b) * 29) >> 8
    let s = UInt32(sat)
    let inv = 255 - s
    let r = (UInt32(c.r) * s + lum * inv) / 255
    let g = (UInt32(c.g) * s + lum * inv) / 255
    let b = (UInt32(c.b) * s + lum * inv) / 255
    return PSColor(UInt8(min(UInt32(255), r)),
                   UInt8(min(UInt32(255), g)),
                   UInt8(min(UInt32(255), b)))
}
