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
 * Modified by MeowScript LLC in 2026, last modified on 2026-07-26.
 */

'use strict';

class Segment {
  constructor(length1D = 150, width2D = 32, height2D = 16) {
    this._length1D = length1D;
    this._width2D = width2D;
    this._height2D = height2D;
    this._is2D = false;

    this.pixels1D = new Uint32Array(length1D);
    this.pixels2D = new Uint32Array(width2D * height2D);

    this.speed = 128;
    this.intensity = 128;
    this.custom1 = 128;
    this.custom2 = 128;
    this.custom3 = 16;
    this.check1 = false;
    this.check2 = false;
    this.check3 = false;

    this.colors = [0xFFAA00, 0x000000, 0x000000];

    this.palette = 0;

    this.reverse = false;
    this.mirror = false;
    this.reverse_y = false;
    this.mirror_y = false;
    this.transpose = false;

    this.step = 0;
    this.call = 0;
    this.aux0 = 0;
    this.aux1 = 0;
    this.data = null;
    this._dataLen = 0;

    this.mode = 0;

    this.map1D2D = 0;

    this.soundSim = 0;

    this.offset = 0;
  }

  get is2D() { return this._is2D; }
  set is2D(v) { this._is2D = v; }

  vLength() {
    if (this._is2D) {
      switch (this.map1D2D) {
        case 1: return this._height2D;
        case 2: return Math.ceil(Math.sqrt(this._width2D * this._width2D + this._height2D * this._height2D));
        case 3: return Math.max(this._width2D, this._height2D);
        default: return this._width2D * this._height2D;
      }
    }
    return this._length1D;
  }

  vWidth() { return this._width2D; }
  vHeight() { return this._height2D; }
  length1D() { return this._length1D; }

  get pixels() {
    return this._is2D ? this.pixels2D : this.pixels1D;
  }

  setPixelColor(i, col) {
    i = i | 0;
    if (i < 0) return;
    const buf = this._is2D ? this.pixels2D : this.pixels1D;
    const len = this._is2D ? this._width2D * this._height2D : this._length1D;

    if (this._is2D) {
      const vW = this._width2D;
      const vH = this._height2D;

      const vStrip = (i >> 16) & 0xFFFF;
      i = i & 0xFFFF;

      switch (this.map1D2D) {
        case 0:
          if (i < len) buf[i] = col >>> 0;
          break;

        case 1:
          if (vStrip > 0) {
            const x = vStrip - 1;
            const y = vH - 1 - i;
            if (x >= 0 && x < vW && y >= 0 && y < vH) {
              buf[x + y * vW] = col >>> 0;
            }
          } else {
            const y = vH - 1 - i;
            if (y >= 0 && y < vH) {
              for (let x = 0; x < vW; x++) {
                buf[x + y * vW] = col >>> 0;
              }
            }
          }
          break;

        default:
          if (i < len) buf[i] = col >>> 0;
          break;
      }
    } else {
      if (i < len) buf[i] = col >>> 0;
    }
  }

  getPixelColor(i) {
    i = i | 0;
    if (i < 0) return 0;
    const buf = this._is2D ? this.pixels2D : this.pixels1D;
    const len = buf.length;

    if (this._is2D) {
      const vW = this._width2D;
      const vH = this._height2D;
      const vStrip = (i >> 16) & 0xFFFF;
      i = i & 0xFFFF;

      switch (this.map1D2D) {
        case 0:
          return i < len ? buf[i] : 0;
        case 1: {
          const x = vStrip > 0 ? vStrip - 1 : 0;
          const y = vH - 1 - i;
          if (x >= 0 && x < vW && y >= 0 && y < vH) {
            return buf[x + y * vW];
          }
          return 0;
        }
        default:
          return i < len ? buf[i] : 0;
      }
    }
    return i < len ? buf[i] : 0;
  }

  blendPixelColor(i, col, blend) {
    const existing = this.getPixelColor(i);
    this.setPixelColor(i, WLEDMath.color_blend(existing, col, blend));
  }

  addPixelColor(i, col) {
    const existing = this.getPixelColor(i);
    this.setPixelColor(i, WLEDMath.color_add(existing, col));
  }

  setPixelColorRaw(i, col) {
    const buf = this.pixels;
    if (i >= 0 && i < buf.length) buf[i] = col >>> 0;
  }

  getPixelColorRaw(i) {
    const buf = this.pixels;
    return (i >= 0 && i < buf.length) ? buf[i] : 0;
  }

  setPixelColorXY(x, y, col) {
    x = x | 0;
    y = y | 0;
    if (x < 0 || x >= this._width2D || y < 0 || y >= this._height2D) return;
    this.pixels2D[x + y * this._width2D] = col >>> 0;
  }

  getPixelColorXY(x, y) {
    x = x | 0;
    y = y | 0;
    if (x < 0 || x >= this._width2D || y < 0 || y >= this._height2D) return 0;
    return this.pixels2D[x + y * this._width2D];
  }

  addPixelColorXY(x, y, col) {
    const existing = this.getPixelColorXY(x, y);
    this.setPixelColorXY(x, y, WLEDMath.color_add(existing, col));
  }

  blendPixelColorXY(x, y, col, blend) {
    const existing = this.getPixelColorXY(x, y);
    this.setPixelColorXY(x, y, WLEDMath.color_blend(existing, col, blend));
  }

  fadePixelColorXY(x, y, fade) {
    const existing = this.getPixelColorXY(x, y);
    this.setPixelColorXY(x, y, WLEDMath.fast_color_scale(existing, 255 - fade));
  }

  setPixelColorXY_AA(fx, fy, col) {
    const x = Math.floor(fx);
    const y = Math.floor(fy);
    const xFrac = Math.round((fx - x) * 255);
    const yFrac = Math.round((fy - y) * 255);
    const ixFrac = 255 - xFrac;
    const iyFrac = 255 - yFrac;

    const w00 = (ixFrac * iyFrac) >> 8;
    const w10 = (xFrac * iyFrac) >> 8;
    const w01 = (ixFrac * yFrac) >> 8;
    const w11 = (xFrac * yFrac) >> 8;

    this.addPixelColorXY(x, y, WLEDMath.color_fade(col, w00));
    this.addPixelColorXY(x + 1, y, WLEDMath.color_fade(col, w10));
    this.addPixelColorXY(x, y + 1, WLEDMath.color_fade(col, w01));
    this.addPixelColorXY(x + 1, y + 1, WLEDMath.color_fade(col, w11));
  }

  wu_pixel(x, y, col) {
    const xi = (x >> 8) | 0;
    const yi = (y >> 8) | 0;
    const xf = x & 0xFF;
    const yf = y & 0xFF;
    const ixf = 255 - xf;
    const iyf = 255 - yf;

    this.addPixelColorXY(xi, yi, WLEDMath.color_fade(col, (ixf * iyf) >> 8));
    this.addPixelColorXY(xi + 1, yi, WLEDMath.color_fade(col, (xf * iyf) >> 8));
    this.addPixelColorXY(xi, yi + 1, WLEDMath.color_fade(col, (ixf * yf) >> 8));
    this.addPixelColorXY(xi + 1, yi + 1, WLEDMath.color_fade(col, (xf * yf) >> 8));
  }

  fill(col) {
    const buf = this.pixels;
    buf.fill(col >>> 0);
  }

  fillBlack() {
    this.pixels.fill(0);
  }

  blur(amount, smear = false) {
    if (this._is2D) {
      this.blur2D(amount, amount, smear);
      return;
    }
    this._blur1D(amount, smear);
  }

  _blur1D(amount, smear = false) {
    const M = WLEDMath;
    const len = this._length1D;
    const buf = this.pixels1D;
    const keep = smear ? 255 : 255 - amount;
    const seep = amount >> 1;
    let carryover = 0;

    for (let i = 0; i < len; i++) {
      const cur = buf[i];
      const part = M.fast_color_scale(cur, seep);
      let blurred = M.fast_color_scale(cur, keep);
      blurred = M.color_add(blurred, carryover);

      if (i > 0) {
        buf[i - 1] = M.color_add(buf[i - 1], part);
      }
      buf[i] = blurred;
      carryover = part;
    }
  }

  blur2D(blurX, blurY, smear = false) {
    const M = WLEDMath;
    const w = this._width2D;
    const h = this._height2D;
    const buf = this.pixels2D;

    const keepX = smear ? 255 : 255 - blurX;
    const seepX = blurX >> 1;
    for (let y = 0; y < h; y++) {
      let carryover = 0;
      for (let x = 0; x < w; x++) {
        const idx = x + y * w;
        const cur = buf[idx];
        const part = M.fast_color_scale(cur, seepX);
        let blurred = M.fast_color_scale(cur, keepX);
        blurred = M.color_add(blurred, carryover);
        if (x > 0) {
          buf[idx - 1] = M.color_add(buf[idx - 1], part);
        }
        buf[idx] = blurred;
        carryover = part;
      }
    }

    const keepY = smear ? 255 : 255 - blurY;
    const seepY = blurY >> 1;
    for (let x = 0; x < w; x++) {
      let carryover = 0;
      for (let y = 0; y < h; y++) {
        const idx = x + y * w;
        const cur = buf[idx];
        const part = M.fast_color_scale(cur, seepY);
        let blurred = M.fast_color_scale(cur, keepY);
        blurred = M.color_add(blurred, carryover);
        if (y > 0) {
          buf[idx - w] = M.color_add(buf[idx - w], part);
        }
        buf[idx] = blurred;
        carryover = part;
      }
    }
  }

  blurCols(amount) {
    this.blur2D(0, amount, false);
  }

  blurRows(amount) {
    this.blur2D(amount, 0, false);
  }

  fade_out(rate) {
    const M = WLEDMath;
    const buf = this.pixels;
    const len = buf.length;

    const r2 = (256 - rate) >> 1;
    const mappedRate = (256 / (r2 + 1)) | 0;
    const bg = this.colors[1];

    if (mappedRate === 0) return;

    const bgR = M.colorR(bg);
    const bgG = M.colorG(bg);
    const bgB = M.colorB(bg);

    for (let i = 0; i < len; i++) {
      const c = buf[i];
      if (c === bg) continue;

      let r = M.colorR(c);
      let g = M.colorG(c);
      let b = M.colorB(c);

      let dR = Math.trunc((bgR - r) * mappedRate / 256);
      let dG = Math.trunc((bgG - g) * mappedRate / 256);
      let dB = Math.trunc((bgB - b) * mappedRate / 256);

      if (dR === 0 && r !== bgR) dR = r > bgR ? -1 : 1;
      if (dG === 0 && g !== bgG) dG = g > bgG ? -1 : 1;
      if (dB === 0 && b !== bgB) dB = b > bgB ? -1 : 1;

      r += dR;
      g += dG;
      b += dB;

      buf[i] = M.colorPack(
        Math.max(0, Math.min(255, r)),
        Math.max(0, Math.min(255, g)),
        Math.max(0, Math.min(255, b))
      );
    }
  }

  fadeToBlackBy(amount) {
    const M = WLEDMath;
    const buf = this.pixels;
    const scale = 255 - amount;
    for (let i = 0; i < buf.length; i++) {
      buf[i] = M.fast_color_scale(buf[i], scale);
    }
  }

  fadeToSecondaryBy(amount) {
    const M = WLEDMath;
    const buf = this.pixels;
    const bg = this.colors[1];
    for (let i = 0; i < buf.length; i++) {
      buf[i] = M.color_blend(buf[i], bg, amount);
    }
  }

  move(dir, delta = 1, wrap = false) {
    if (!this._is2D) return;

    const moveX = [1, 1, 0, -1, -1, -1, 0, 1][dir] * delta;
    const moveY = [0, -1, -1, -1, 0, 1, 1, 1][dir] * delta;

    if (moveX !== 0) this._moveX(moveX, wrap);
    if (moveY !== 0) this._moveY(moveY, wrap);
  }

  _moveX(delta, wrap) {
    const w = this._width2D;
    const h = this._height2D;
    const buf = this.pixels2D;

    for (let y = 0; y < h; y++) {
      if (delta > 0) {
        for (let x = 0; x < w; x++) {
          const src = x + delta;
          if (src < w) {
            buf[x + y * w] = buf[src + y * w];
          } else if (wrap) {
            buf[x + y * w] = buf[(src % w) + y * w];
          } else {
            buf[x + y * w] = 0;
          }
        }
      } else {
        const absDelta = -delta;
        for (let x = w - 1; x >= 0; x--) {
          const src = x - absDelta;
          if (src >= 0) {
            buf[x + y * w] = buf[src + y * w];
          } else if (wrap) {
            buf[x + y * w] = buf[((src % w) + w) % w + y * w];
          } else {
            buf[x + y * w] = 0;
          }
        }
      }
    }
  }

  _moveY(delta, wrap) {
    const w = this._width2D;
    const h = this._height2D;
    const buf = this.pixels2D;

    if (delta > 0) {
      for (let y = h - 1; y >= 0; y--) {
        const src = y - delta;
        for (let x = 0; x < w; x++) {
          if (src >= 0) {
            buf[x + y * w] = buf[x + src * w];
          } else if (wrap) {
            buf[x + y * w] = buf[x + (((src % h) + h) % h) * w];
          } else {
            buf[x + y * w] = 0;
          }
        }
      }
    } else {
      const absDelta = -delta;
      for (let y = 0; y < h; y++) {
        const src = y + absDelta;
        for (let x = 0; x < w; x++) {
          if (src < h) {
            buf[x + y * w] = buf[x + src * w];
          } else if (wrap) {
            buf[x + y * w] = buf[x + (src % h) * w];
          } else {
            buf[x + y * w] = 0;
          }
        }
      }
    }
  }

  drawLine(x0, y0, x1, y1, col) {
    const dx = Math.abs(x1 - x0);
    const dy = Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1;
    const sy = y0 < y1 ? 1 : -1;
    let err = dx - dy;

    while (true) {
      this.setPixelColorXY(x0, y0, col);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x0 += sx; }
      if (e2 < dx) { err += dx; y0 += sy; }
    }
  }

  drawCircle(cx, cy, radius, col) {
    let x = radius;
    let y = 0;
    let err = 1 - radius;

    while (x >= y) {
      this.setPixelColorXY(cx + x, cy + y, col);
      this.setPixelColorXY(cx + y, cy + x, col);
      this.setPixelColorXY(cx - y, cy + x, col);
      this.setPixelColorXY(cx - x, cy + y, col);
      this.setPixelColorXY(cx - x, cy - y, col);
      this.setPixelColorXY(cx - y, cy - x, col);
      this.setPixelColorXY(cx + y, cy - x, col);
      this.setPixelColorXY(cx + x, cy - y, col);

      y++;
      if (err < 0) {
        err += 2 * y + 1;
      } else {
        x--;
        err += 2 * (y - x) + 1;
      }
    }
  }

  fillCircle(cx, cy, radius, col) {
    const r2 = radius * radius;
    for (let y = -radius; y <= radius; y++) {
      for (let x = -radius; x <= radius; x++) {
        if (x * x + y * y <= r2) {
          this.setPixelColorXY(cx + x, cy + y, col);
        }
      }
    }
  }

  allocateData(len) {
    if (this._dataLen >= len) return true;
    this.data = new Uint8Array(len);
    this._dataLen = len;
    return true;
  }

  deallocateData() {
    this.data = null;
    this._dataLen = 0;
  }

  reset() {
    this.step = 0;
    this.call = 0;
    this.aux0 = 0;
    this.aux1 = 0;
    if (this.data) this.data.fill(0);
    this._scannerFade = null;
    this._auroraWaves = null;
    this._pacificaPals = null;
    this._soapNoise = null;
    this._ghost = null;
    this._blobs = null;
    this._octoMap = null;
    this._matPixels = null;
    this._fire2012Step = null;
    this._firePal = null;
    this._stData = null;
    this._tvSim = null;
    this._rotoAngle = null;
    this._phasedPhase = null;
    this._juliaXcen = null;
    this._juliaYcen = null;
    this._juliaXymag = null;
    this._tetrixDrop = null;
    this._shimData = null;
    this._gravData = null;
    this._lastPeakVol = null;
    this._peakHoldUntil = null;
    this._pixVals = null;
    this._plasData = null;
    this._pac = null;
    this._octoStep = null;
    this._octoAux0 = null;
    this._octoAux1 = null;
    this._octoOffX = null;
    this._octoOffY = null;
    this._soapCols = null;
    this._soapRows = null;
    this._soapPixels = null;
    this._soapCoord = null;
    this._soapLedsbuff = null;
    this._prevBar = null;
    this._beeErrors = null;
    this.fillBlack();
  }

  SEGCOLOR(i) {
    return this.colors[i] || 0;
  }

  getVolume() {
    const m = WLEDMath;
    switch (this.soundSim) {
      default:
      case 0: return m.beatsin8(Math.floor(120 / 9), 0, 255);
      case 1: { const t = m.getNow() % 2000;
          if (t < 200) return m.jsRandom8();
          if (t < 400) return 0;
          if (t < 600) return m.jsRandom8();
          if (t < 800) return 0;
          if (t < 1000) return m.jsRandom8();
          return 0; }
      case 2: return m.beatsin8(10, 0, 255);
      case 3: return m.beatsin8(3, 0, 255);
    }
  }

  getFFT(bin) {
    const m = WLEDMath;
    bin = Math.max(0, Math.min(15, bin));
    switch (this.soundSim) {
      default:
      case 0: return m.beatsin8(Math.floor(120 / (bin + 1)), 0, 255);
      case 1: { const t = m.getNow() % 2000;
          if (t < 200 && bin < 5) return m.jsRandom8();
          if (t < 400) return 0;
          if (t < 600 && bin >= 5 && bin < 11) return m.jsRandom8();
          if (t < 800) return 0;
          if (t < 1000 && bin >= 11) return m.jsRandom8();
          return 0; }
      case 2: return m.beatsin8(10 + bin, 0, 255);
      case 3: return m.beatsin8(14 + bin * 3, 0, 255);
    }
  }

  getMajorPeak() {
    const m = WLEDMath;
    let maxBin = 0, maxVal = 0;
    for (let b = 0; b < 16; b++) {
      const fv = this.getFFT(b);
      if (fv > maxVal) { maxVal = fv; maxBin = b; }
    }
    const freq = 80.0 * Math.pow(2.0, maxBin * 6.0 / 15.0);
    return Math.max(1.0, Math.min(freq, 11025.0));
  }

  getMagnitude() {
    return this.getVolume() * 16.0;
  }

  getSamplePeak() {
    const vol = this.getVolume();
    if (this._lastPeakVol === undefined) this._lastPeakVol = 0;
    if (this._peakHoldUntil === undefined) this._peakHoldUntil = 0;
    const m = WLEDMath;
    const now = m.getNow();
    let peak = 0;
    if (vol > this._lastPeakVol + 10 && vol > 60) {
      peak = 1;
      this._peakHoldUntil = now + 50;
    } else if (now < this._peakHoldUntil) {
      peak = 1;
    }
    this._lastPeakVol = vol;
    return peak;
  }

  color_from_palette(index, mapping = true, wrap = false, mcol = 255, pbri = 255) {
    const M = (typeof WLEDMath !== 'undefined') ? WLEDMath : window.WLEDMath;

    if (this.palette === 0 && mcol < 3) {
      const color = this.colors[mcol] || 0;
      return M.color_fade(color, pbri, true);
    }
    const effPal = this._effectivePalette();

    if (mapping) {
      const vLen = this.vLength();
      index = (vLen > 0) ? Math.min(Math.floor((index * 255) / vLen), 255) : 0;
    }

    const blendType = (mapping && !wrap) ? 2 : 1;
    if (typeof window !== 'undefined' && window.WLEDPalettes) {
      return window.WLEDPalettes.colorFromPalette(effPal, index, pbri, this.colors, blendType);
    }
    return M.color_wheel(index);
  }

  _effectivePalette() {
    if (this.palette !== 0) return this.palette;
    if (typeof EFFECTS !== 'undefined' && EFFECTS[this.mode] && EFFECTS[this.mode].defaults) {
      const p = EFFECTS[this.mode].defaults.pal;
      if (p !== undefined && p > 0) return p;
    }
    return 6;
  }

  color_wheel(pos) {
    const M = (typeof WLEDMath !== 'undefined') ? WLEDMath : window.WLEDMath;
    if (this.palette) return this.color_from_palette(pos, false, false, 0);
    return M.color_wheel(pos);
  }

  ColorFromPalette(index, pbri = 255, blendType = 1) {
    if (typeof window !== 'undefined' && window.WLEDPalettes) {
      return window.WLEDPalettes.colorFromPalette(this._effectivePalette(), index, pbri, this.colors, blendType);
    }
    const M = (typeof WLEDMath !== 'undefined') ? WLEDMath : window.WLEDMath;
    return M.color_wheel(index);
  }
}

if (typeof window !== 'undefined') {
  window.Segment = Segment;
}
