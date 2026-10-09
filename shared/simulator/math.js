/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * Parts based on FastLED (https://github.com/FastLED/FastLED),
 * Copyright (c) 2013 FastLED, MIT License (see NOTICE).
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

'use strict';

const _sin8Table = new Uint8Array(256);
const _cos8Table = new Uint8Array(256);

(function buildTables() {
  for (let i = 0; i < 256; i++) {
    _sin8Table[i] = Math.max(0, Math.min(255, Math.round(127.5 + 127.5 * Math.sin(i * 2 * Math.PI / 256))));
    _cos8Table[i] = Math.max(0, Math.min(255, Math.round(127.5 + 127.5 * Math.cos(i * 2 * Math.PI / 256))));
  }
})();

function sin8(x) {
  return _sin8Table[x & 0xFF];
}

function cos8(x) {
  return _cos8Table[x & 0xFF];
}

const sin8_t = sin8;
const cos8_t = cos8;

function sin_t(x) { return Math.sin(x); }
function cos_t(x) { return Math.cos(x); }
function atan2_t(y, x) { return Math.atan2(y, x); }

function radians(deg) { return deg * Math.PI / 180.0; }

function sin16(theta) {
  theta = theta & 0xFFFF;
  const angle = (theta / 65536) * 2 * Math.PI;
  return Math.round(Math.sin(angle) * 32767);
}

function cos16(theta) {
  return sin16(theta + 16384);
}

function triwave8(x) {
  x = x & 0xFF;
  if (x < 128) return x * 2;
  return (255 - x) * 2;
}

function triwave16(x) {
  x = x & 0xFFFF;
  if (x < 32768) return x * 2;
  return (65535 - x) * 2;
}

function cubicwave8(x) {
  return ease8InOutCubic(triwave8(x));
}

function quadwave8(x) {
  return sin8(x);
}

let _now = 0;

function setNow(ms) {
  _now = ms;
}

function getNow() {
  return _now;
}

function beat8(bpm, timebase = 0) {
  return (beat16(bpm, timebase) >> 8) & 0xFF;
}

function beat16(bpm, timebase = 0) {
  return beat88((bpm * 256) & 0xFFFF, timebase);
}

function beat88(bpm88, timebase = 0) {
  const elapsed = ((_now - timebase) >>> 0);
  return (Math.imul(Math.imul(elapsed, bpm88), 280) >>> 16) & 0xFFFF;
}

function beatsin8(bpm, lowest = 0, highest = 255, timebase = 0, phaseShift = 0) {
  const b = beat8(bpm, timebase);
  const s = sin8(b + phaseShift);
  return lowest + ((s * (highest - lowest)) >> 8);
}

function beatsin16(bpm, lowest = 0, highest = 65535, timebase = 0, phaseShift = 0) {
  const b = beat16(bpm, timebase);
  const raw = (sin16(b + phaseShift) + 32768) & 0xFFFF;
  return lowest + Math.floor((raw * (highest - lowest + 1)) / 65536);
}

function beatsin88(bpm88, lowest = 0, highest = 65535, timebase = 0, phaseShift = 0) {
  const b = beat88(bpm88, timebase);
  const raw = (sin16(b + phaseShift) + 32768) & 0xFFFF;
  return lowest + Math.floor((raw * (highest - lowest + 1)) / 65536);
}

const beatsin8_t = beatsin8;
const beatsin16_t = beatsin16;

function sqrt32_bw(val) {
  return Math.floor(Math.sqrt(Math.max(0, val)));
}

function abs8(x) {
  return Math.abs(x) & 0xFF;
}

function rgb2hsv_packed(color) {
  const r = colorR(color);
  const g = colorG(color);
  const b = colorB(color);
  const mx = Math.max(r, g, b);
  const mn = Math.min(r, g, b);
  const d = mx - mn;
  let h = 0;
  const s = mx === 0 ? 0 : Math.floor(d * 255 / mx);
  const v = mx;
  if (d > 0) {
    if (mx === r) h = Math.floor(((g - b) * 255 / d + 256 * 6) % (256 * 6) / 6);
    else if (mx === g) h = Math.floor(85 + (b - r) * 255 / d / 6);
    else h = Math.floor(171 + (r - g) * 255 / d / 6);
  }
  return { h: h & 0xFF, s: s & 0xFF, v: v & 0xFF };
}

// Fixed-point integer Perlin noise by @dedehai.

function _perlinHashGrad(h) {
  return ((h & 0x03) - 2) | 0;
}

function _perlinGradient1D(x0, dx) {
  let h = Math.imul(x0 | 0, 0x27D4EB2D) >>> 0;
  h = (h ^ (h >>> 15)) >>> 0;
  h = Math.imul(h | 0, 0x92C3412B | 0) >>> 0;
  h = (h ^ (h >>> 13)) >>> 0;
  h = (h ^ (h >>> 7)) >>> 0;
  return (_perlinHashGrad(h) * dx) >> 1;
}

function _perlinGradient2D(x0, dx, y0, dy) {
  let h = (Math.imul(x0 | 0, 0x27D4EB2D) ^ Math.imul(y0 | 0, 0xB5297A4D | 0)) >>> 0;
  h = (h ^ (h >>> 15)) >>> 0;
  h = Math.imul(h | 0, 0x92C3412B | 0) >>> 0;
  h = (h ^ (h >>> 13)) >>> 0;
  const g1 = _perlinHashGrad(h);
  const g2 = _perlinHashGrad(h >>> 1);
  return (g1 * dx + g2 * dy) >> 2;
}

function _perlinGradient3D(x0, dx, y0, dy, z0, dz) {
  let h = (Math.imul(x0 | 0, 0x27D4EB2D)
         ^ Math.imul(y0 | 0, 0xB5297A4D | 0)
         ^ Math.imul(z0 | 0, 0x1B56C4E9)) >>> 0;
  h = (h ^ (h >>> 15)) >>> 0;
  h = Math.imul(h | 0, 0x92C3412B | 0) >>> 0;
  h = (h ^ (h >>> 13)) >>> 0;
  const g1 = _perlinHashGrad(h);
  const g2 = _perlinHashGrad(h >>> 2);
  const g3 = _perlinHashGrad(h >>> 3);
  return ((g1 * dx + g2 * dy + g3 * dz) * 85) >> 9;
}

function _perlinSmoothstep(t) {
  const tSq = (t * t) >>> 16;
  const factor = (0x30000 - (t << 1)) | 0;
  return (tSq * factor) >>> 18;
}

function _perlinLerp(a, b, t) {
  return (a + (((b - a) * t) >> 14)) | 0;
}

function perlin1D_raw(x, is16bit) {
  x = x >>> 0;
  const x0 = x >>> 16;
  let x1 = (x0 + 1) >>> 0;
  if (is16bit) x1 = x1 & 0xFF;
  const dx0 = (x & 0xFFFF) | 0;
  const dx1 = dx0 - 0x10000;
  const g0 = _perlinGradient1D(x0, dx0);
  const g1 = _perlinGradient1D(x1, dx1);
  const tx = _perlinSmoothstep(dx0);
  return _perlinLerp(g0, g1, tx);
}

function perlin2D_raw(x, y, is16bit) {
  x = x >>> 0; y = y >>> 0;
  const x0 = x >>> 16;
  const y0 = y >>> 16;
  let x1 = (x0 + 1) >>> 0;
  let y1 = (y0 + 1) >>> 0;
  if (is16bit) { x1 = x1 & 0xFF; y1 = y1 & 0xFF; }
  const dx0 = (x & 0xFFFF) | 0;
  const dy0 = (y & 0xFFFF) | 0;
  const dx1 = dx0 - 0x10000;
  const dy1 = dy0 - 0x10000;
  const g00 = _perlinGradient2D(x0, dx0, y0, dy0);
  const g10 = _perlinGradient2D(x1, dx1, y0, dy0);
  const g01 = _perlinGradient2D(x0, dx0, y1, dy1);
  const g11 = _perlinGradient2D(x1, dx1, y1, dy1);
  const tx = _perlinSmoothstep(dx0);
  const ty = _perlinSmoothstep(dy0);
  const nx0 = _perlinLerp(g00, g10, tx);
  const nx1 = _perlinLerp(g01, g11, tx);
  return _perlinLerp(nx0, nx1, ty);
}

function perlin3D_raw(x, y, z, is16bit) {
  x = x >>> 0; y = y >>> 0; z = z >>> 0;
  const x0 = x >>> 16;
  const y0 = y >>> 16;
  const z0 = z >>> 16;
  let x1 = (x0 + 1) >>> 0;
  let y1 = (y0 + 1) >>> 0;
  let z1 = (z0 + 1) >>> 0;
  if (is16bit) { x1 = x1 & 0xFF; y1 = y1 & 0xFF; z1 = z1 & 0xFF; }
  const dx0 = (x & 0xFFFF) | 0;
  const dy0 = (y & 0xFFFF) | 0;
  const dz0 = (z & 0xFFFF) | 0;
  const dx1 = dx0 - 0x10000;
  const dy1 = dy0 - 0x10000;
  const dz1 = dz0 - 0x10000;
  const g000 = _perlinGradient3D(x0, dx0, y0, dy0, z0, dz0);
  const g001 = _perlinGradient3D(x0, dx0, y0, dy0, z1, dz1);
  const g010 = _perlinGradient3D(x0, dx0, y1, dy1, z0, dz0);
  const g011 = _perlinGradient3D(x0, dx0, y1, dy1, z1, dz1);
  const g100 = _perlinGradient3D(x1, dx1, y0, dy0, z0, dz0);
  const g101 = _perlinGradient3D(x1, dx1, y0, dy0, z1, dz1);
  const g110 = _perlinGradient3D(x1, dx1, y1, dy1, z0, dz0);
  const g111 = _perlinGradient3D(x1, dx1, y1, dy1, z1, dz1);
  const tx = _perlinSmoothstep(dx0);
  const ty = _perlinSmoothstep(dy0);
  const tz = _perlinSmoothstep(dz0);
  const nx0 = _perlinLerp(g000, g100, tx);
  const nx1 = _perlinLerp(g010, g110, tx);
  const nx2 = _perlinLerp(g001, g101, tx);
  const nx3 = _perlinLerp(g011, g111, tx);
  const ny0 = _perlinLerp(nx0, nx1, ty);
  const ny1 = _perlinLerp(nx2, nx3, ty);
  return _perlinLerp(ny0, ny1, tz);
}

function perlin16(x, y, z) {
  if (y === undefined) return (((perlin1D_raw(x >>> 0, false) * 1159) >> 10) + 32803) & 0xFFFF;
  if (z === undefined) return (((perlin2D_raw(x >>> 0, y >>> 0, false) * 1537) >> 10) + 32725) & 0xFFFF;
  return (((perlin3D_raw(x >>> 0, y >>> 0, z >>> 0, false) * 1731) >> 10) + 33147) & 0xFFFF;
}

function perlin8(x, y, z) {
  if (y === undefined) return ((((perlin1D_raw(((x & 0xFFFF) << 8) >>> 0, true) * 1353) >> 10) + 32769) >> 8) & 0xFF;
  if (z === undefined) return ((((perlin2D_raw(((x & 0xFFFF) << 8) >>> 0, ((y & 0xFFFF) << 8) >>> 0, true) * 1620) >> 10) + 32771) >> 8) & 0xFF;
  return ((((perlin3D_raw(((x & 0xFFFF) << 8) >>> 0, ((y & 0xFFFF) << 8) >>> 0, ((z & 0xFFFF) << 8) >>> 0, true) * 2015) >> 10) + 33168) >> 8) & 0xFF;
}

const inoise8  = perlin8;
const inoise16 = perlin16;

function colorPack(r, g, b, w = 0) {
  return ((w & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
}

function colorR(c) { return (c >> 16) & 0xFF; }
function colorG(c) { return (c >> 8) & 0xFF; }
function colorB(c) { return c & 0xFF; }
function colorW(c) { return (c >> 24) & 0xFF; }

function color_blend(c1, c2, blend) {
  if (blend === 0) return c1;
  if (blend === 255) return c2;
  const ib = 255 - blend;
  const r = (colorR(c1) * ib + colorR(c2) * blend) >> 8;
  const g = (colorG(c1) * ib + colorG(c2) * blend) >> 8;
  const b = (colorB(c1) * ib + colorB(c2) * blend) >> 8;
  const w = (colorW(c1) * ib + colorW(c2) * blend) >> 8;
  return colorPack(r, g, b, w);
}

function color_add(c1, c2, preserveCR = false) {
  if (preserveCR) {
    const r = colorR(c1) + colorR(c2);
    const g = colorG(c1) + colorG(c2);
    const b = colorB(c1) + colorB(c2);
    const w = colorW(c1) + colorW(c2);
    const mx = Math.max(r, g, b, w);
    if (mx > 255) {
      const scale = 255 / mx;
      return colorPack(
        Math.round(r * scale),
        Math.round(g * scale),
        Math.round(b * scale),
        Math.round(w * scale)
      );
    }
    return colorPack(r, g, b, w);
  }
  return colorPack(
    Math.min(255, colorR(c1) + colorR(c2)),
    Math.min(255, colorG(c1) + colorG(c2)),
    Math.min(255, colorB(c1) + colorB(c2)),
    Math.min(255, colorW(c1) + colorW(c2))
  );
}

function color_fade(c, amount, video) {
  if (c === 0 || amount === 0) return 0;
  if (amount === 255) return c;

  let r = colorR(c);
  let g = colorG(c);
  let b = colorB(c);
  let w = colorW(c);

  if (video) {
    const maxc = (Math.max(r, Math.max(g, b)) >> 2) + 1;
    const nr = ((r * amount + 0x7F) >> 8) | (r > maxc ? 1 : 0);
    const ng = ((g * amount + 0x7F) >> 8) | (g > maxc ? 1 : 0);
    const nb = ((b * amount + 0x7F) >> 8) | (b > maxc ? 1 : 0);
    const nw = ((w * amount + 0x7F) >> 8) | (w ? 1 : 0);
    return colorPack(nr, ng, nb, nw);
  } else {
    return colorPack((r * amount) >> 8, (g * amount) >> 8, (b * amount) >> 8, (w * amount) >> 8);
  }
}

function fast_color_scale(c, scale) {
  const rb = ((c & 0x00FF00FF) * scale) >> 8;
  const gw = (((c >> 8) & 0x00FF00FF) * scale) >> 8;
  return ((gw & 0x00FF00FF) << 8) | (rb & 0x00FF00FF);
}

function color_wheel(pos) {
  return hsv2rgb(pos & 0xFF, 255, 255);
}

function get_random_wheel_index(prevIndex) {
  let idx = 0, x = 0, y = 0, d = 0;
  while (d < 42) {
    idx = random8();
    x = Math.abs(prevIndex - idx) & 0xFF;
    y = 255 - x;
    d = Math.min(x, y);
  }
  return idx;
}

function hsv2rgb(h, s, v) {
  h = h & 0xFF;
  s = Math.min(255, Math.max(0, s));
  v = Math.min(255, Math.max(0, v));

  if (s === 0) return colorPack(v, v, v);

  const offset = h & 0x1F;
  const offset8 = (offset << 3) & 0xFF;
  const third = ((offset8 * 86) >> 8) & 0xFF;

  let r, g, b;

  if (!(h & 0x80)) {
    if (!(h & 0x40)) {
      if (!(h & 0x20)) {
        r = 255 - third;
        g = third;
        b = 0;
      } else {
        r = 171;
        g = 85 + third;
        b = 0;
      }
    } else {
      if (!(h & 0x20)) {
        r = (171 - ((third << 1) & 0xFF)) & 0xFF;
        g = 170 + third;
        b = 0;
      } else {
        r = 0;
        g = 255 - third;
        b = third;
      }
    }
  } else {
    if (!(h & 0x40)) {
      if (!(h & 0x20)) {
        r = 0;
        g = (171 - ((third << 1) & 0xFF)) & 0xFF;
        b = (85 + ((third << 1) & 0xFF)) & 0xFF;
      } else {
        r = third;
        g = 0;
        b = 255 - third;
      }
    } else {
      if (!(h & 0x20)) {
        r = 85 + third;
        g = 0;
        b = (171 - ((third << 1) & 0xFF)) & 0xFF;
      } else {
        r = 170 + third;
        g = 0;
        b = 85 - third;
      }
    }
  }

  if (s !== 255) {
    let desat = 255 - s;
    desat = ((desat * (1 + desat)) >> 8) & 0xFF;
    const satscale = 255 - desat;
    r = (((r & 0xFF) * (1 + satscale)) >> 8) + desat;
    g = (((g & 0xFF) * (1 + satscale)) >> 8) + desat;
    b = (((b & 0xFF) * (1 + satscale)) >> 8) + desat;
  }

  if (v !== 255) {
    r = (((r & 0xFF) * (1 + v)) >> 8);
    g = (((g & 0xFF) * (1 + v)) >> 8);
    b = (((b & 0xFF) * (1 + v)) >> 8);
  }

  return colorPack(r & 0xFF, g & 0xFF, b & 0xFF);
}

function HeatColor(temperature) {
  temperature = Math.min(255, Math.max(0, temperature));
  let r, g, b;

  const t192 = Math.round((temperature / 255.0) * 191);

  const heatramp = t192 & 0x3F;
  const heat_ramp_shift = heatramp << 2;

  if (t192 & 0x80) {
    r = 255;
    g = 255;
    b = heat_ramp_shift;
  } else if (t192 & 0x40) {
    r = 255;
    g = heat_ramp_shift;
    b = 0;
  } else {
    r = heat_ramp_shift;
    g = 0;
    b = 0;
  }

  return colorPack(r, g, b);
}

let _randSeed = 1;

function random8(min, max) {
  if (min === undefined) {
    _randSeed = (_randSeed * 1103515245 + 12345) & 0x7FFFFFFF;
    return (_randSeed >> 16) & 0xFF;
  }
  if (max === undefined) {
    return random8() % min;
  }
  return min + (random8() % (max - min));
}

function random16(min, max) {
  if (min === undefined) {
    return (random8() << 8) | random8();
  }
  if (max === undefined) {
    return random16() % min;
  }
  return min + (random16() % (max - min));
}

function jsRandom8(min, max) {
  if (min === undefined) return Math.floor(Math.random() * 256);
  if (max === undefined) return Math.floor(Math.random() * min);
  return min + Math.floor(Math.random() * (max - min));
}

function jsRandom16(min, max) {
  if (min === undefined) return Math.floor(Math.random() * 65536);
  if (max === undefined) return Math.floor(Math.random() * min);
  return min + Math.floor(Math.random() * (max - min));
}

function scale8(value, scale) {
  return (value * (1 + scale)) >> 8;
}

function scale16(value, scale) {
  return (value * scale) >>> 16;
}

function map(x, in_min, in_max, out_min, out_max) {
  return Math.trunc((x - in_min) * (out_max - out_min) / (in_max - in_min)) + out_min;
}

function mapf(x, in_min, in_max, out_min, out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

function constrain(x, min, max) {
  return Math.min(max, Math.max(min, x));
}

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function qadd8(a, b) {
  const t = a + b;
  return t > 255 ? 255 : t;
}

function qsub8(a, b) {
  const r = a - b;
  return r < 0 ? 0 : r;
}

function lerp8by8(a, b, frac) {
  return (a + (((b - a) * frac) >> 8)) & 0xFF;
}

function ease8InOutCubic(x) {
  x = x & 0xFF;
  if (x < 128) {
    return (x * x * x) >> 14;
  }
  x = 255 - x;
  return 255 - ((x * x * x) >> 14);
}

function ease8InOutApprox(x) {
  x = x & 0xFF;
  if (x < 64)  return x >> 1;
  if (x > 191) return 255 - ((255 - x) >> 1);
  x -= 64;
  return (x + (x >> 1) + 32) & 0xFF;
}

const WLED_FPS = 58;
const FRAMETIME_FIXED = Math.floor(1000 / WLED_FPS);
let FRAMETIME = FRAMETIME_FIXED;

function SPEED_FORMULA_L(speed, seglen) {
  return 5 + Math.floor((50 * (255 - speed)) / Math.max(1, seglen));
}

function tristate_square8(t, tPulse, tPause) {
  const period = tPulse + tPause;
  const cycle = period * 4;
  const pos = t % cycle;

  if (pos < tPulse) return 255;
  if (pos < tPulse + tPause) return 128;
  if (pos < 2 * tPulse + tPause) return 0;
  return 128;
}

const _gamma8 = new Uint8Array(256);
const _gamma8inv = new Uint8Array(256);
(function buildGammaTables() {
  for (let i = 0; i < 256; i++) {
    _gamma8[i] = Math.round(Math.pow(i / 255.0, 2.8) * 255);
  }
  for (let i = 0; i < 256; i++) {
    _gamma8inv[i] = Math.round(Math.pow(i / 255.0, 1.0 / 2.8) * 255);
  }
})();

function gamma8(v) { return _gamma8[v & 0xFF]; }
function gamma8inv(v) { return _gamma8inv[v & 0xFF]; }

function gamma32inv(c) {
  return colorPack(
    gamma8inv(colorR(c)),
    gamma8inv(colorG(c)),
    gamma8inv(colorB(c)),
    gamma8inv(colorW(c))
  );
}

function getAverageLight(c) {
  return Math.max(colorR(c), Math.max(colorG(c), Math.max(colorB(c), colorW(c))));
}

const beatsin88_t = beatsin88;
const beat16_t = beat16;
const beat8_t = beat8;

if (typeof window !== 'undefined') {
  window.WLEDMath = {
    sin8, cos8, sin8_t, cos8_t, sin16, cos16,
    sin_t, cos_t, atan2_t, radians,
    triwave8, triwave16, cubicwave8, quadwave8,
    beat8, beat16, beat88, beatsin8, beatsin16, beatsin88,
    beatsin8_t, beatsin16_t, beatsin88_t, beat16_t, beat8_t,
    inoise8, inoise16, perlin8, perlin16,
    sqrt32_bw, abs8,
    colorPack, colorR, colorG, colorB, colorW,
    color_blend, color_add, color_fade, fast_color_scale,
    color_wheel, get_random_wheel_index,
    hsv2rgb, HeatColor, rgb2hsv_packed,
    random8, random16, jsRandom8, jsRandom16,
    scale8, scale16, map, mapf, constrain, lerp,
    qadd8, qsub8, lerp8by8,
    ease8InOutCubic, ease8InOutApprox, tristate_square8,
    gamma8, gamma8inv, gamma32inv, getAverageLight,
    setNow, getNow,
    WLED_FPS, FRAMETIME_FIXED, SPEED_FORMULA_L,
    get FRAMETIME() { return FRAMETIME; },
    set FRAMETIME(v) { FRAMETIME = v; },
  };
}
