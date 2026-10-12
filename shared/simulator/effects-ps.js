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
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-06.
 */

'use strict';

(function () {
  if (typeof window === 'undefined') return;
  if (!window.Effects2D) window.Effects2D = {};
  if (!window.Effects1D) window.Effects1D = {};
  const E2 = window.Effects2D;
  const E1 = window.Effects1D;

  function fract(x) { return x - Math.floor(x); }
  function getT() { return (window.WLEDMath ? window.WLEDMath.getNow() : performance.now()) / 1000; }

  function pcol(seg, pos) {
    pos = ((pos % 1) + 1) % 1;
    const idx = Math.max(0, Math.min(255, Math.floor(pos * 255)));
    return seg.color_from_palette(idx, false, true, 0) >>> 0;
  }

  function fadeF(c, f) {
    if (f >= 1) return c >>> 0;
    if (f <= 0) return 0;
    const amt = Math.max(0, Math.min(255, Math.floor(f * 255)));
    return window.WLEDMath.color_fade(c, amt, true) >>> 0;
  }

  function splat2D(seg, w, h, px, py, color, bri) {
    const M = window.WLEDMath;
    const x0 = Math.floor(px), y0 = Math.floor(py);
    for (let dy = -1; dy <= 1; dy++) {
      const yy = y0 + dy; if (yy < 0 || yy >= h) continue;
      for (let dx = -1; dx <= 1; dx++) {
        const xx = x0 + dx; if (xx < 0 || xx >= w) continue;
        const dd = Math.hypot(xx + 0.5 - px, yy + 0.5 - py);
        const f = Math.max(0, 1 - dd / 1.4) * bri;
        if (f <= 0) continue;
        const scaled = M.color_fade(color, Math.min(255, Math.floor(f * 255)), true);
        seg.addPixelColorXY(xx, yy, scaled);
      }
    }
  }

  E2[187] = function psVolcano(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity, c1 = seg.custom1, c3 = seg.custom3;
    const emitFrames = Math.max(1, 11 - ix / 25);
    const emitsPerSec = 60 / emitFrames;
    const cycle = 4.5;
    const N = Math.max(24, Math.min(72, Math.floor(emitsPerSec * cycle)));
    const launchVy = Math.max(0.5, (sx / 4) / 64 * 60) * 0.45;
    const grav = launchVy * 0.55;
    const coneVar = (c3 / 31 * 15) / 64 * 60 * 0.25;
    const drift = (c1 / 4) / 64 * 60 * 0.25;
    const sourceX = drift > 0.01 ? (1 + (0.5 + 0.5 * Math.sin(t * drift * 0.2)) * (w - 2)) : w * 0.5;
    const sourceY = h - 0.5;
    for (let i = 0; i < N; i++) {
      const s = i * 0.61803398875;
      const r1 = fract(s * 12.345), r2 = fract(s * 78.233), r3 = fract(s * 43.758);
      const off = (i / N) * cycle;
      const age = fract((t - off) / cycle + 16) * cycle;
      const ttl = 1 - age / cycle; if (ttl <= 0) continue;
      const vy = launchVy + (r1 - 0.5) * coneVar;
      const vx = (r2 - 0.5) * coneVar;
      const py = sourceY - (vy * age - 0.5 * grav * age * age);
      const px = sourceX + vx * age;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      const palPos = fract(off / cycle + r3);
      const c = pcol(seg, palPos);
      const bri = Math.min(1, ttl * 2);
      splat2D(seg, w, h, px, py, c, bri);
    }
  };

  E2[188] = function psFire(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, c1 = seg.custom1, c2 = seg.custom2, c3 = seg.custom3;
    const speed = Math.max(110, sx);
    const spread = (c3 + 1) / 32;
    const halfSpread = (w * 0.5) * spread;
    const N = Math.max(40, Math.min(96, Math.floor(8 + halfSpread * 4)));
    const ttlSec = Math.max(0.4, (h * 0.5 + 16) / 60);
    const vyBase = (h * 0.5 + speed / 16 + c1 / 16) / 64 * 60;
    const cycle = ttlSec * 1.6;
    for (let i = 0; i < N; i++) {
      const s = i * 0.61803398875;
      const r1 = fract(s * 12.345), r2 = fract(s * 78.233), r3 = fract(s * 43.758);
      const spawnX = (w * 0.5 - halfSpread) + r1 * halfSpread * 2;
      const vy = vyBase + (r2 - 0.5) * (2 + speed / 16) / 64 * 60;
      const vx = (r3 - 0.5) * 4 / 64 * 60;
      const off = fract(i / N) * cycle;
      const age = fract((t - off) / cycle + 16) * cycle;
      const ttl = Math.max(0, 1 - age / ttlSec); if (ttl <= 0) continue;
      const py = (h - 0.5) - vy * age * (1 + ttl * 0.6);
      const px = spawnX + vx * age + Math.sin(t * 0.7 + i) * c2 / 255 * 0.6 * h * 0.1;
      if (px < 0 || px >= w || py < -1 || py >= h) continue;
      const c = pcol(seg, Math.min(1, ttl * 1.2));
      const bri = Math.min(1, ttl * 0.6 + 0.05);
      splat2D(seg, w, h, px, py, c, bri);
    }
  };

  E2[189] = function psFireworks2D(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const ix = seg.intensity, c1 = seg.custom1, c3 = seg.custom3;
    const cycle = 2.2;
    const N = Math.max(2, Math.floor(ix / 40) + 1);
    for (let n = 0; n < N; n++) {
      const off = n * cycle / N + (n * 0.317);
      const phase = fract((t - off) / cycle);
      const cx = fract(n * 0.6180339) * w;
      const flareTop = h * 0.35 + fract(n * 1.97) * h * 0.2;
      const launchEnd = 0.45;
      if (phase < launchEnd) {
        const py = h - (phase / launchEnd) * (h - flareTop);
        const xx = Math.floor(cx), yy = Math.floor(py);
        if (xx >= 0 && xx < w && yy >= 0 && yy < h) {
          seg.addPixelColorXY(xx, yy, (255 << 16) | (200 << 8) | 100);
        }
      } else {
        const explFrac = (phase - launchEnd) / (1 - launchEnd);
        const radius = explFrac * (h * 0.4 + c1 / 255 * 4);
        const sparks = Math.max(8, Math.floor(c3 * 0.8 + 10));
        const fade = 1 - explFrac;
        for (let s = 0; s < sparks; s++) {
          const ang = (s / sparks) * Math.PI * 2;
          const px = cx + Math.cos(ang) * radius;
          const py = flareTop + Math.sin(ang) * radius + explFrac * explFrac * 4;
          if (px < 0 || px >= w || py < 0 || py >= h) continue;
          const c = pcol(seg, fract(s / sparks + n * 0.3));
          seg.addPixelColorXY(Math.floor(px), Math.floor(py), fadeF(c, fade));
        }
      }
    }
  };

  E2[190] = function psVortex(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity, c1 = seg.custom1;
    const cx = w * 0.5, cy = h * 0.5;
    const N = Math.floor(ix / 4) + 10;
    const spin = sx / 255 * 4;
    const tighten = c1 / 255;
    for (let i = 0; i < N; i++) {
      const s = i * 0.6180339;
      const r = Math.sqrt(fract(s * 7.21)) * Math.min(w, h) * 0.5;
      const baseAng = fract(s * 3.31) * Math.PI * 2;
      const ang = baseAng + t * spin * (0.5 + tighten * 0.5) + r * 0.1 * tighten;
      const px = cx + Math.cos(ang) * r;
      const py = cy + Math.sin(ang) * r;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      const c = pcol(seg, fract(s + t * 0.1));
      seg.addPixelColorXY(Math.floor(px), Math.floor(py), c);
    }
  };

  E2[191] = function psPerlin(seg) {
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, c1 = seg.custom1, c2 = seg.custom2;
    const sc = 0.15 + (c1 / 255) * 0.3;
    const sp = sx / 255 * 0.4;
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const v = Math.sin(x * sc + t * sp) + Math.sin(y * sc * 1.3 + t * sp * 0.7) + Math.sin((x + y) * sc * 0.7 + t * sp * 1.1);
        const pos = fract((v + 3) / 6 + c2 / 255);
        seg.setPixelColorXY(x, y, pcol(seg, pos));
      }
    }
  };

  E2[192] = function psBallpit(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const ix = seg.intensity, c1 = seg.custom1;
    const N = Math.max(10, Math.floor(ix / 8) + 10);
    const radius = 0.6 + (c1 / 255) * 1.2;
    for (let i = 0; i < N; i++) {
      const s = i * 0.7193;
      const sx = fract(s * 11.13);
      const seedH = fract(s * 5.27);
      const cycle = 4.5;
      const phase = fract(t / cycle + s);
      const age = phase * cycle;
      const floorY = h - 1 - (i % 3) * 0.7;
      const fallY = Math.min(floorY, 0.5 * 1.5 * age * age * (h * 0.6) - 1);
      const bx = sx * (w - 1) + (seedH - 0.5) * 0.6 * Math.min(1, age * 0.5);
      const c = pcol(seg, fract(seedH + t * 0.04));
      const xx = Math.floor(bx), yy = Math.floor(fallY);
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const x2 = xx + dx, y2 = yy + dy;
        if (x2 < 0 || x2 >= w || y2 < 0 || y2 >= h) continue;
        const dd = Math.hypot(x2 + 0.5 - bx, y2 + 0.5 - fallY);
        const f = Math.max(0, 1 - dd / (radius * 1.2));
        if (f <= 0) continue;
        seg.addPixelColorXY(x2, y2, fadeF(c, f));
      }
    }
  };

  E2[193] = function psBox(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, c1 = seg.custom1;
    const sz = 2 + Math.floor(c1 / 255 * 4);
    const tt = t * (0.4 + sx / 255);
    const cx = (0.5 + 0.4 * Math.sin(tt)) * w;
    const cy = (0.5 + 0.4 * Math.cos(tt * 1.3)) * h;
    const c = pcol(seg, fract(t * 0.15));
    for (let dy = -sz; dy <= sz; dy++) for (let dx = -sz; dx <= sz; dx++) {
      if (Math.abs(dx) < sz && Math.abs(dy) < sz) continue;
      const xx = Math.floor(cx) + dx, yy = Math.floor(cy) + dy;
      if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
      seg.addPixelColorXY(xx, yy, c);
    }
  };

  E2[194] = function psAttractor(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const N = Math.max(20, Math.floor(ix / 3));
    const cx = w * 0.5, cy = h * 0.5;
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      const a = fract(s * 3.7) * Math.PI * 2 + t * (sx / 255 * 2);
      const r = fract(s * 7.13) * Math.min(w, h) * 0.45 + Math.sin(t + i * 0.3) * 2;
      const px = cx + Math.cos(a) * r, py = cy + Math.sin(a) * r;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      const c = pcol(seg, fract(s + t * 0.05));
      seg.addPixelColorXY(Math.floor(px), Math.floor(py), c);
    }
  };

  E2[195] = function psImpact(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const N = Math.max(8, Math.floor(ix / 16));
    const cycle = Math.max(0.8, 3 - sx / 128);
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      const phase = fract(t / cycle + s);
      const x = fract(s * 11.7) * (w - 1);
      const yMax = h - 1;
      const y = yMax - Math.abs(Math.sin(phase * Math.PI)) * yMax;
      const xx = Math.floor(x), yy = Math.floor(y);
      const c = pcol(seg, fract(s + phase));
      if (xx >= 0 && xx < w && yy >= 0 && yy < h) {
        seg.addPixelColorXY(xx, yy, c);
      }
      if (phase > 0.95) {
        const half = fadeF(c, 0.5);
        for (let dx = -2; dx <= 2; dx++) {
          const xs = xx + dx; if (xs < 0 || xs >= w) continue;
          seg.addPixelColorXY(xs, yMax, half);
        }
      }
    }
  };

  E2[196] = function psWaterfall(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const sp = Math.max(0.2, sx / 100);
    const N = Math.max(10, Math.floor(ix / 8));
    for (let i = 0; i < N; i++) {
      const s = i * 0.6180339;
      const lane = Math.floor(fract(s * 7.13) * w);
      const startOff = fract(s * 3.7);
      const yy = Math.floor(fract(t * sp + startOff) * h);
      const c = pcol(seg, fract(s + t * 0.1));
      if (yy >= 0 && yy < h) {
        seg.addPixelColorXY(lane, yy, c);
        if (yy > 0) seg.addPixelColorXY(lane, yy - 1, fadeF(c, 0.4));
      }
    }
  };

  E2[197] = function psSpray(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity, c1 = seg.custom1;
    const N = Math.max(20, Math.floor(ix / 4));
    const sourceX = 0, sourceY = h - 1;
    const sp = sx / 255 * 4 + 1;
    const angSpread = (c1 / 255) * 0.6;
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      const ang = Math.PI * 0.25 + (fract(s * 5.7) - 0.5) * angSpread;
      const cycle = 2.5;
      const off = fract(s * 1.91) * cycle;
      const age = fract((t - off) / cycle + 16) * cycle;
      const px = sourceX + Math.cos(-ang) * sp * age * 4;
      const py = sourceY + Math.sin(-ang) * sp * age * 4 + 0.5 * age * age * 4;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      const c = pcol(seg, fract(s + age * 0.5));
      seg.addPixelColorXY(Math.floor(px), Math.floor(py), c);
    }
  };

  E2[198] = function psGEQ2D(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const bars = 16;
    const colsPerBar = Math.max(1, Math.floor(w / bars));
    for (let b = 0; b < bars; b++) {
      const amp = (0.5 + 0.5 * Math.sin(t * (1 + b * 0.13) + b)) * (0.4 + 0.6 * Math.sin(t * 0.5 + b * 0.7));
      const barH = Math.floor(Math.abs(amp) * h);
      for (let y = h - 1; y > h - 1 - barH; y--) {
        for (let dx = 0; dx < colsPerBar; dx++) {
          const xx = b * colsPerBar + dx; if (xx >= w) continue;
          const yFrac = (h - 1 - y) / Math.max(1, h - 1);
          const r = yFrac < 0.5 ? 255 : Math.floor(255 * (1 - (yFrac - 0.5) * 2));
          const g = yFrac < 0.5 ? Math.floor(255 * (yFrac * 2)) : 255;
          seg.addPixelColorXY(xx, y, ((r & 0xFF) << 16) | ((g & 0xFF) << 8));
        }
      }
    }
  };

  E2[199] = function psGEQNova(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const cx = w * 0.5, cy = h * 0.5;
    const arms = 16;
    const maxR = Math.min(w, h) * 0.45;
    for (let a = 0; a < arms; a++) {
      const amp = (0.4 + 0.6 * Math.abs(Math.sin(t * (1 + a * 0.11) + a * 0.5)));
      const r = amp * maxR;
      const ang = (a / arms) * Math.PI * 2 + t * 0.3;
      const c = pcol(seg, fract(a / arms + t * 0.05));
      for (let s = 0; s < r; s += 0.5) {
        const px = cx + Math.cos(ang) * s, py = cy + Math.sin(ang) * s;
        const xx = Math.floor(px), yy = Math.floor(py);
        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
        const f = 1 - s / r;
        seg.addPixelColorXY(xx, yy, fadeF(c, f));
      }
    }
    const core = (255 << 16) | (255 << 8) | 200;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const xx = Math.floor(cx) + dx, yy = Math.floor(cy) + dy;
      if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
      seg.addPixelColorXY(xx, yy, core);
    }
  };

  E2[200] = function psGhostRider(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    if (!seg._psGhostTrail) seg._psGhostTrail = [];
    const trail = seg._psGhostTrail;
    const x = w * 0.5 + Math.cos(t * 0.7) * w * 0.4;
    const y = h * 0.5 + Math.sin(t * 1.1) * h * 0.4;
    trail.push({ x, y });
    if (trail.length > 6) trail.shift();
    for (let i = 0; i < trail.length; i++) {
      const tr = trail[i];
      const fade = (i + 1) / trail.length;
      const hue = (t * 0.2 + i * 0.15) % 1;
      const r = hue < 0.5 ? Math.floor(255 * hue * 2) : Math.floor(255 * (1 - (hue - 0.5) * 2));
      const g = 0;
      const b = 255;
      const xx = Math.floor(tr.x), yy = Math.floor(tr.y);
      if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
      const col = ((Math.floor(r * fade) & 0xFF) << 16) | ((g & 0xFF) << 8) | (Math.floor(b * fade) & 0xFF);
      seg.addPixelColorXY(xx, yy, col);
    }
  };

  E2[201] = function psBlobs(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const ix = seg.intensity, c1 = seg.custom1;
    const N = Math.max(2, Math.min(7, Math.floor(ix / 40) + 2));
    const sz = 1 + (c1 / 255) * 2;
    const blobs = [];
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      blobs.push({
        x: w * 0.5 + Math.cos(t * 0.5 + s * 7) * w * 0.3,
        y: h * 0.5 + Math.sin(t * 0.7 + s * 3) * h * 0.3,
        hue: fract(s + t * 0.1)
      });
    }
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      let best = 0, bestHue = 0;
      for (const b of blobs) {
        const dd = Math.hypot(x + 0.5 - b.x, y + 0.5 - b.y);
        const v = Math.max(0, 1 - (dd * dd) / (sz * sz * 4));
        if (v > best) { best = v; bestHue = b.hue; }
      }
      if (best > 0.05) {
        const c = pcol(seg, bestHue);
        seg.setPixelColorXY(x, y, fadeF(c, best));
      }
    }
  };

  E2[217] = function psGalaxy(seg) {
    seg.fill(0);
    const w = seg.vWidth(), h = seg.vHeight();
    const t = getT();
    const cx = w * 0.5, cy = h * 0.5;
    const N = 120;
    const spin = t * 0.4;
    const maxR = Math.min(w, h) * 0.5;
    for (let i = 0; i < N; i++) {
      const s = i * 0.6180339;
      const r = fract(s * 7.13) * maxR;
      const arm = Math.floor(fract(s * 3) * 3);
      const ang = fract(s * 1.71) * Math.PI * 2 + spin + r * 0.4 + arm * Math.PI * 2 / 3;
      const px = cx + Math.cos(ang) * r, py = cy + Math.sin(ang) * r;
      if (px < 0 || px >= w || py < 0 || py >= h) continue;
      const c = pcol(seg, r / maxR);
      seg.addPixelColorXY(Math.floor(px), Math.floor(py), c);
    }
    const core = (255 << 16) | (240 << 8) | 200;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const xx = Math.floor(cx) + dx, yy = Math.floor(cy) + dy;
      if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
      seg.addPixelColorXY(xx, yy, core);
    }
  };

  E1[202] = function psDripDrop(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const N = Math.max(3, Math.floor(ix / 20));
    const sp = sx / 100 + 0.3;
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      const phase = fract(t * sp / 3 + s);
      const x = Math.floor(phase * w);
      const c = pcol(seg, fract(s + t * 0.1));
      if (x >= 0 && x < w) seg.addPixelColor(x, c);
      if (x > 0) seg.addPixelColor(x - 1, fadeF(c, 0.5));
    }
  };

  E1[203] = function psPinball(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const N = Math.max(3, Math.floor(ix / 40) + 2);
    const sp = sx / 100 + 0.3;
    for (let i = 0; i < N; i++) {
      const phase = fract(t * sp * 0.7 + i * 0.31);
      const x = Math.floor((phase < 0.5 ? phase * 2 : (1 - phase) * 2) * (w - 1));
      const col = (i % 2 === 0)
        ? ((255 << 16) | (80 << 8) | 80)
        : ((80 << 16) | (140 << 8) | 255);
      if (x >= 0 && x < w) seg.setPixelColor(x, col);
    }
  };

  E1[204] = function psDancingShadows(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    for (let x = 0; x < w; x++) {
      const v = Math.sin((x * 0.3) + t * 0.7) + Math.sin((x * 0.13) - t * 0.5);
      const f = Math.max(0, (v + 1) / 3);
      const c = pcol(seg, fract(x / w + t * 0.05));
      seg.setPixelColor(x, fadeF(c, f));
    }
  };

  E1[205] = function psFireworks1D(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const c3 = seg.custom3;
    const cycle = 2.5;
    const N = 2;
    for (let n = 0; n < N; n++) {
      const off = n * cycle / N;
      const phase = fract((t + off) / cycle);
      const cx = fract(n * 0.41 + t * 0.07) * w;
      if (phase < 0.45) {
        const x = Math.floor(cx);
        if (x >= 0 && x < w) seg.setPixelColor(x, (255 << 16) | (200 << 8) | 100);
      } else {
        const explFrac = (phase - 0.45) / 0.55;
        const radius = explFrac * w * 0.25;
        const fade = 1 - explFrac;
        const sparks = Math.floor(c3 * 0.5 + 8);
        for (let s = 0; s < sparks; s++) {
          const x = Math.floor(cx + (s / sparks - 0.5) * 2 * radius);
          if (x < 0 || x >= w) continue;
          const c = pcol(seg, fract(s / sparks + n * 0.3));
          seg.addPixelColor(x, fadeF(c, fade));
        }
      }
    }
  };

  E1[206] = function psSparkler(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const ix = seg.intensity;
    const N = Math.max(3, Math.floor(ix / 16));
    const col = (255 << 16) | (240 << 8) | 200;
    for (let i = 0; i < N; i++) {
      const s = fract(i * 0.618 + Math.floor(t * 8) * 0.137);
      const x = Math.floor(s * w);
      if (x >= 0 && x < w) seg.setPixelColor(x, col);
    }
  };

  E1[207] = function psHourglass(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const cyc = 12;
    const dir = Math.floor(t / cyc) % 2;
    const phase = fract(t / cyc);
    const pile = Math.floor(phase * w * 0.45);
    for (let x = 0; x < pile; x++) {
      const xx = dir === 0 ? x : w - 1 - x;
      const c = pcol(seg, x / w);
      seg.setPixelColor(xx, c);
    }
    const streamX = Math.floor(dir === 0 ? (w * 0.5 - phase * w * 0.5) : (w * 0.5 + phase * w * 0.5));
    if (streamX >= 0 && streamX < w) seg.setPixelColor(streamX, (255 << 16) | (200 << 8) | 80);
  };

  E1[208] = function psSpray1D(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const sx = seg.speed, ix = seg.intensity;
    const N = Math.max(8, Math.floor(ix / 8));
    const sp = sx / 100;
    for (let i = 0; i < N; i++) {
      const s = i * 0.618;
      const cycle = 2;
      const off = fract(s * 1.13) * cycle;
      const age = fract((t - off) / cycle + 16) * cycle;
      const x = Math.floor(age * sp * w * 0.3);
      if (x < 0 || x >= w) continue;
      const c = pcol(seg, fract(s));
      seg.addPixelColor(x, c);
    }
  };

  E1[209] = function psBalance(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const sx = seg.speed;
    const cx = w * 0.5;
    const phase = Math.sin(t * sx / 100);
    for (let x = 0; x < w; x++) {
      const d = (x - cx) / cx;
      const tilted = d * phase;
      const f = Math.max(0, 1 - Math.abs(d - tilted * 0.4) * 1.5);
      const c = pcol(seg, fract(d * 0.5 + 0.5));
      seg.setPixelColor(x, fadeF(c, f));
    }
  };

  E1[210] = function psChase(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const sx = seg.speed;
    const sp = sx / 100 + 0.5;
    for (let i = 0; i < 3; i++) {
      const x = Math.floor(fract(t * sp * 0.3 + i * 0.33) * w);
      const c = pcol(seg, i * 0.3);
      if (x >= 0 && x < w) seg.setPixelColor(x, c);
      for (let tail = 1; tail <= 3; tail++) {
        const xt = ((x - tail) % w + w) % w;
        seg.addPixelColor(xt, fadeF(c, 1 / (tail * 2)));
      }
    }
  };

  E1[211] = function psStarburst(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const N = 2;
    for (let n = 0; n < N; n++) {
      const cycle = 1.8;
      const off = n * cycle / N;
      const phase = fract((t + off) / cycle);
      const cx = fract(n * 0.41 + Math.floor((t + off) / cycle) * 0.31) * w;
      const radius = phase * w * 0.2;
      const fade = 1 - phase;
      const rInt = Math.floor(radius);
      if (rInt <= 0) continue;
      for (let s = -rInt; s <= rInt; s++) {
        const x = Math.floor(cx) + s; if (x < 0 || x >= w) continue;
        const c = pcol(seg, fract(s / radius * 0.5 + 0.5));
        seg.addPixelColor(x, fadeF(c, fade));
      }
    }
  };

  E1[212] = function psGEQ1D(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const bars = 16;
    const colsPerBar = Math.max(1, Math.floor(w / bars));
    for (let b = 0; b < bars; b++) {
      const amp = Math.abs(Math.sin(t * (1 + b * 0.13) + b));
      const litCols = Math.floor(amp * colsPerBar);
      for (let c = 0; c < litCols; c++) {
        const x = b * colsPerBar + c; if (x >= w) continue;
        const yFrac = c / colsPerBar;
        const r = yFrac < 0.5 ? 255 : Math.floor(255 * (1 - (yFrac - 0.5) * 2));
        const g = yFrac < 0.5 ? Math.floor(255 * (yFrac * 2)) : 255;
        seg.setPixelColor(x, ((r & 0xFF) << 16) | ((g & 0xFF) << 8));
      }
    }
  };

  E1[213] = function psFire1D(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    for (let x = 0; x < w; x++) {
      const heat = Math.max(0, (Math.sin(x * 0.3 + t * 2) + Math.sin(x * 0.13 - t * 1.7)) / 2 + 0.4);
      seg.setPixelColor(x, pcol(seg, Math.min(1, heat)));
    }
  };

  E1[214] = function psSonicStream(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    for (let x = 0; x < w; x++) {
      const f = Math.max(0, Math.sin(x * 0.4 - t * 4) * 0.5 + 0.5);
      const c = pcol(seg, f);
      seg.setPixelColor(x, fadeF(c, f));
    }
  };

  E1[215] = function psSonicBoom(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    const c2 = seg.custom2;
    const cycle = 1.5;
    const launchX = Math.floor((c2 / 255) * w);
    const phase = fract(t / cycle);
    const radius = phase * w * 0.5;
    const fade = 1 - phase;
    const rInt = Math.floor(radius);
    if (rInt <= 0) return;
    for (let s = -rInt; s <= rInt; s++) {
      const x = launchX + s; if (x < 0 || x >= w) continue;
      const f = fade * (1 - Math.abs(s) / radius);
      if (f <= 0) continue;
      const c = pcol(seg, Math.abs(s) / radius);
      seg.setPixelColor(x, fadeF(c, f));
    }
  };

  E1[216] = function psSpringy(seg) {
    seg.fill(0);
    const w = seg.vLength();
    const t = getT();
    for (let x = 0; x < w; x++) {
      const wave = Math.sin(x * 0.5 + t * 3) * Math.exp(-Math.abs(x - w * 0.5) / (w * 0.3));
      const f = Math.abs(wave);
      const c = pcol(seg, fract(x / w));
      seg.setPixelColor(x, fadeF(c, f));
    }
  };

})();
