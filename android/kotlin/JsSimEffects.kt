/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-09-25.
 */

package fx

internal interface JsFxBatch {
    val ids: IntArray
    val twoD: Boolean get() = false
    fun run(id: Int, s: JsSimSegment): Boolean
}

internal object JsSimEffects {
    private val batches: Array<JsFxBatch> = arrayOf(JsFx1DB1, JsFx1DB2, JsFx1DB3, JsFx1DB4, JsFx1DB5, JsFx1DB6, JsFx2DB7)

    private val owner: Array<JsFxBatch?> = arrayOfNulls<JsFxBatch>(256).also { tbl ->
        for (b in batches) for (id in b.ids) if (id in tbl.indices) tbl[id] = b
    }

    fun has(id: Int): Boolean = id in owner.indices && owner[id] != null

    private val JS_2D_ONLY = intArrayOf(
        114, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 139, 146, 149,
        150, 152, 153, 154, 160, 162, 164, 165, 166, 167, 168, 172, 173, 174,
        175, 176, 177, 178, 180, 181, 182, 183, 186,
    )

    fun isJs2DOnly(id: Int): Boolean = JS_2D_ONLY.contains(id)

    fun is2DOnly(id: Int): Boolean = id in owner.indices && owner[id]?.twoD == true

    fun run(id: Int, s: JsSimSegment): Boolean {
        if (!s.is2D && isJs2DOnly(id)) {
            s.fill(if (s.colors[0] != 0) s.colors[0] else 0xFFAA00)
            return true
        }
        val b = (if (id in owner.indices) owner[id] else null) ?: return false
        if (b.twoD && !s.is2D) {
            s.fill(if (s.colors[0] != 0) s.colors[0] else 0xFFAA00)
            return true
        }
        return b.run(id, s)
    }
}

internal class JsSimRenderer(val length1D: Int, val width: Int, val height: Int, val is2D: Boolean) {
    val seg = JsSimSegment(if (is2D) width * height else length1D, width, height, is2D)
    private var currentEffect = -1

    fun render(
        effectId: Int, paletteId: Int, effectDefaultPalette: Int,
        c0: Int, c1: Int, c2: Int, speed: Int, intensity: Int,
        custom1: Int, custom2: Int, custom3: Int,
        check1: Boolean, check2: Boolean, check3: Boolean,
        scrollText: String = "",
    ): IntArray {
        val s = seg
        if (effectId != currentEffect) {
            currentEffect = effectId
            s.reset()
            s.deallocateData()
            s.m.randSeed = 1
            s.m.now = 0L
            s.resetPalettes()
            s.mode = effectId
            s.soundSim = if (effectId == 131) 1 else 0
        }
        s.speed = speed; s.intensity = intensity; s.palette = paletteId
        s.effectDefaultPalette = effectDefaultPalette
        s.custom1 = custom1; s.custom2 = custom2; s.custom3 = custom3
        s.check1 = check1; s.check2 = check2; s.check3 = check3
        s.colors[0] = c0; s.colors[1] = c1; s.colors[2] = c2
        s.scrollText = scrollText
        s.m.FRAMETIME = 22
        s.m.setNow(s.m.getNow() + 22)
        try {
            JsSimEffects.run(effectId, s)
        } catch (e: Exception) {
        }
        s.call++
        return s.pixels
    }
}
