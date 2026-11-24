/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-11-01.
 */

import UIKit

extension EffectSimulationEngine {

    typealias RGB = (r: Double, g: Double, b: Double)

    /// The colour-built palettes 0 to 5, as the firmware builds them
    /// (FX_fcn.cpp loadPalette): 1 Random Cycle, 2 Color 1, 3 Colors 1&2
    /// (prim, prim, sec, sec), 4 Color Gradient (ter, sec, prim), 5 Colors Only.
    /// 0 has no effect default here, so it shows the colours. nil above 5.
    static func dynamicPaletteRGB(_ paletteId: Int, prim: RGB, sec: RGB, ter: RGB) -> [RGB]? {
        switch paletteId {
        case 0:
            return [prim, sec, ter]
        case 1:
            return (0..<8).map { i in
                let c = UIColor(hue: CGFloat(Double(i) / 8.0), saturation: 1, brightness: 1, alpha: 1)
                var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                c.getRed(&r, green: &g, blue: &b, alpha: &a)
                return (Double(r), Double(g), Double(b))
            }
        case 2:
            return [prim]
        case 3:
            return [prim, prim, sec, sec]
        case 4:
            return [ter, sec, prim]
        case 5:
            let terOff = ter.r == 0 && ter.g == 0 && ter.b == 0
            return terOff ? Array(repeating: prim, count: 8) + Array(repeating: sec, count: 8)
                : Array(repeating: prim, count: 5) + Array(repeating: sec, count: 5) + Array(repeating: ter, count: 5) + [prim]
        default:
            return nil
        }
    }
}
