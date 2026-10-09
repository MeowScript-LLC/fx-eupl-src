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
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

'use strict';

const Effects2D = {};

const M2D = () => window.WLEDMath;

Effects2D[114] = function rotozoomer(seg) {
  const m = M2D();
  const w = seg.vWidth();
  const h = seg.vHeight();

  const dataSize = 4 + w * h;
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
    seg._rotoAngle = 0.0;
  }

  const ms = Math.floor(m.getNow() / 15);

  const plasma = seg.data;
  for (let j = 0; j < h; j++) {
    const index = j * w;
    for (let i = 0; i < w; i++) {
      if (seg.check1) {
        plasma[index + i] = ((i * 4 ^ j * 4) + Math.floor(ms / 6)) & 0xFF;
      } else {
        plasma[index + i] = m.inoise8(i * 40, j * 40, ms) & 0xFF;
      }
    }
  }

  const a = seg._rotoAngle;
  const f = (m.sin_t(a / 2) + ((128 - seg.intensity) / 128.0) + 1.1) / 1.5;
  const kosinus = m.cos_t(a) * f;
  const sinus = m.sin_t(a) * f;

  for (let i = 0; i < w; i++) {
    const u1 = i * kosinus;
    const v1 = i * sinus;
    for (let j = 0; j < h; j++) {
      const u = m.abs8(Math.floor(u1 - j * sinus)) % w;
      const v = m.abs8(Math.floor(v1 + j * kosinus)) % h;
      seg.setPixelColorXY(i, j, seg.color_from_palette(plasma[v * w + u], false, false, 255));
    }
  }

  seg._rotoAngle -= 0.03 + (seg.speed - 128) * 0.0002;
  if (seg._rotoAngle < -6283.18530718) seg._rotoAngle += 6283.18530718;
};

Effects2D[146] = function noise2D(seg) {
  const m = M2D();
  const w = seg.vWidth();
  const h = seg.vHeight();
  const scale = seg.intensity + 2;

  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const pixelHue8 = m.perlin8(x * scale, y * scale, Math.floor(m.getNow() / (16 - (seg.speed >> 4))));
      seg.setPixelColorXY(x, y, seg.ColorFromPalette(pixelHue8));
    }
  }
};

Effects2D[149] = function firenoise(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg.fillBlack();
  }

  const xscale = seg.intensity * 4;
  const yscale = seg.speed * 8;

  const firePal = [
    0x000000, 0x000000, 0x000000, 0x000000,
    0xFF0000, 0xFF0000, 0xFF0000, 0xFF8C00,
    0xFF8C00, 0xFF8C00, 0xFFA500, 0xFFA500,
    0xFFFF00, 0xFFA500, 0xFFFF00, 0xFFFF00
  ];

  for (let j = 0; j < cols; j++) {
    for (let i = 0; i < rows; i++) {
      const indexx = m.perlin8(Math.floor(j * yscale * rows / 255), Math.floor(i * xscale + m.getNow() / 4));
      const palIdx = Math.min(Math.floor(i * indexx / 11), 225);
      const bri = Math.floor(i * 255 / rows);

      if (seg.check1) {
        seg.setPixelColorXY(j, i, seg.ColorFromPalette(palIdx, bri));
      } else {
        const entry = palIdx >> 4;
        const blend = (palIdx & 0x0F) << 4;
        const c0 = firePal[Math.min(entry, 15)];
        const c1 = firePal[Math.min(entry + 1, 15)];
        let col = m.color_blend(c0, c1, blend);
        col = m.fast_color_scale(col, bri);
        seg.setPixelColorXY(j, i, col);
      }
    }
  }
};

Effects2D[150] = function squaredSwirl(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const kBorderWidth = 2;

  seg.fadeToBlackBy(1 + Math.floor(seg.intensity / 5));
  seg.blur(seg.custom3 >> 1);

  const i = m.beatsin8(19, kBorderWidth, cols - kBorderWidth);
  const j = m.beatsin8(22, kBorderWidth, cols - kBorderWidth);
  const k = m.beatsin8(17, kBorderWidth, cols - kBorderWidth);
  const mm = m.beatsin8(18, kBorderWidth, rows - kBorderWidth);
  const n = m.beatsin8(15, kBorderWidth, rows - kBorderWidth);
  const p = m.beatsin8(20, kBorderWidth, rows - kBorderWidth);

  seg.addPixelColorXY(i, mm, seg.ColorFromPalette(Math.floor(m.getNow() / 29), 255));
  seg.addPixelColorXY(j, n, seg.ColorFromPalette(Math.floor(m.getNow() / 41), 255));
  seg.addPixelColorXY(k, p, seg.ColorFromPalette(Math.floor(m.getNow() / 73), 255));
};

Effects2D[152] = function dna(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  seg.fadeToBlackBy(64);
  for (let i = 0; i < cols; i++) {
    const spd = Math.floor(seg.speed / 8);
    seg.setPixelColorXY(i,
      m.beatsin8(spd, 0, rows - 1, 0, i * 4),
      seg.ColorFromPalette(i * 5 + Math.floor(m.getNow() / 17), m.beatsin8(5, 55, 255, 0, i * 10))
    );
    seg.setPixelColorXY(i,
      m.beatsin8(spd, 0, rows - 1, 0, i * 4 + 128),
      seg.ColorFromPalette(i * 5 + 128 + Math.floor(m.getNow() / 17), m.beatsin8(5, 55, 255, 0, i * 10 + 128))
    );
  }
  seg.blur(Math.floor(seg.intensity / (8 - (seg.check1 ? 2 : 0))), seg.check1);
};

Effects2D[153] = function matrix(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const XY = (x, y) => (x % cols) + (y % rows) * cols;

  const dataSize = (seg.vLength() + 7) >> 3;
  if (!seg.allocateData(dataSize)) return;

  if (seg.call === 0) {
    seg.fill(0);
    seg.step = 0;
  }

  const fade = m.map(seg.custom1, 0, 255, 50, 250);
  const speed = (256 - seg.speed) >> m.map(Math.min(rows, 150), 0, 150, 0, 3);

  let spawnColor, trailColor;
  if (seg.check1) {
    spawnColor = seg.SEGCOLOR(0);
    trailColor = seg.SEGCOLOR(1);
  } else {
    spawnColor = m.colorPack(175, 255, 175, 0);
    trailColor = m.colorPack(27, 130, 39, 0);
  }

  let emptyScreen = true;
  if (m.getNow() - seg.step >= speed) {
    seg.step = m.getNow();
    seg.fadeToBlackBy(fade);
    for (let row = rows - 1; row >= 0; row--) {
      for (let col = 0; col < cols; col++) {
        const idx = XY(col, row);
        const index = idx >> 3;
        const bitNum = idx & 0x07;
        if ((seg.data[index] >> bitNum) & 1) {
          seg.setPixelColorXY(col, row, trailColor);
          seg.data[index] &= ~(1 << bitNum);
          if (row < rows - 1) {
            seg.setPixelColorXY(col, row + 1, spawnColor);
            const newIdx = XY(col, row + 1);
            const newIndex = newIdx >> 3;
            const newBitNum = newIdx & 0x07;
            seg.data[newIndex] |= (1 << newBitNum);
            emptyScreen = false;
          }
        }
      }
    }

    if (m.jsRandom8() <= seg.intensity || emptyScreen) {
      const spawnX = m.jsRandom8(0, cols);
      seg.setPixelColorXY(spawnX, 0, spawnColor);
      const spIdx = XY(spawnX, 0);
      const spIndex = spIdx >> 3;
      const spBitNum = spIdx & 0x07;
      seg.data[spIndex] |= (1 << spBitNum);
    }
  }
};

Effects2D[154] = function metaballs(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const speed = 0.25 * (1 + (seg.speed >> 6));

  const x2 = m.map(m.perlin8(Math.floor(m.getNow() * speed), 25355, 685), 0, 255, 0, cols - 1);
  const y2 = m.map(m.perlin8(Math.floor(m.getNow() * speed), 355, 11685), 0, 255, 0, rows - 1);

  const x3 = m.map(m.perlin8(Math.floor(m.getNow() * speed), 55355, 6685), 0, 255, 0, cols - 1);
  const y3 = m.map(m.perlin8(Math.floor(m.getNow() * speed), 25355, 22685), 0, 255, 0, rows - 1);

  const x1 = m.beatsin8(Math.floor(23 * speed), 0, cols - 1);
  const y1 = m.beatsin8(Math.floor(28 * speed), 0, rows - 1);

  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      let dx = Math.abs(x - x1);
      let dy = Math.abs(y - y1);
      let dist = 2 * m.sqrt32_bw(dx * dx + dy * dy);

      dx = Math.abs(x - x2);
      dy = Math.abs(y - y2);
      dist += m.sqrt32_bw(dx * dx + dy * dy);

      dx = Math.abs(x - x3);
      dy = Math.abs(y - y3);
      dist += m.sqrt32_bw(dx * dx + dy * dy);

      const color = dist ? Math.floor(1000 / dist) : 255;

      if (color > 0 && color < 60) {
        seg.setPixelColorXY(x, y, seg.color_from_palette(m.map(color * 9, 9, 531, 0, 255), false, false, 0));
      } else {
        seg.setPixelColorXY(x, y, seg.color_from_palette(0, false, false, 0));
      }
    }
  }
  seg.setPixelColorXY(x1, y1, 0xFFFFFF);
  seg.setPixelColorXY(x2, y2, 0xFFFFFF);
  seg.setPixelColorXY(x3, y3, 0xFFFFFF);
};

Effects2D[164] = function drift(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const colsCenter = (cols >> 1) + (cols % 2);
  const rowsCenter = (rows >> 1) + (rows % 2);

  seg.fadeToBlackBy(128);
  const maxDim = Math.max(cols, rows) / 2;
  const t = Math.floor(m.getNow() / (32 - (seg.speed >> 3)));
  const t_20 = Math.floor(t / 20);

  for (let i = 1.0; i < maxDim; i += 0.25) {
    const angle = m.radians(t * (maxDim - i));
    const mySin = Math.floor(m.sin_t(angle) * i);
    const myCos = Math.floor(m.cos_t(angle) * i);
    seg.setPixelColorXY(colsCenter + mySin, rowsCenter + myCos,
      seg.ColorFromPalette(Math.floor(i * 20 + t_20), 255));
    if (seg.check1) {
      seg.setPixelColorXY(colsCenter + myCos, rowsCenter + mySin,
        seg.ColorFromPalette(Math.floor(i * 20 + t_20), 255));
    }
  }
  seg.blur(seg.intensity >> (3 - (seg.check2 ? 1 : 0)), seg.check2);
};

Effects2D[172] = function gameOfLife(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const maxIndex = cols * rows;

  const ALIVE = 1, FADED = 2, TOGGLE = 4, EDGE = 8, OSC_CHK = 16, SHIP_CHK = 32;

  if (seg.call === 0 || !seg.data || seg.data.length < maxIndex) {
    seg.allocateData(maxIndex);
    seg.data.fill(0);
    seg.aux0 = 0;
    seg.aux1 = 0;
  }

  let generation = seg.aux0;
  let gliderLength = seg.aux1;
  const mutate = !!seg.check3;
  const blur = m.map(seg.custom1, 0, 255, 255, 4);
  const bgColor = seg.SEGCOLOR(1);
  let birthColor = seg.color_from_palette(128, false, false, 255);

  let setup = seg.call === 0;
  if (setup) {
    let a = rows, b = cols;
    while (b) { const t = b; b = a % b; a = t; }
    gliderLength = ((cols * rows / a) << 2) & 0xFFFF;
    seg.aux1 = gliderLength;
  }

  if (Math.abs(m.getNow() - seg.step) > 2000) seg.step = 0;
  let paused = seg.step > m.getNow();

  if ((!paused && generation === 0) || setup) {
    seg.step = m.getNow() + 1280;
    generation = 1;
    paused = true;
    for (let i = 0; i < maxIndex; i++) seg.data[i] = 0;
    for (let i = 0; i < maxIndex; i++) {
      const isAlive = !m.jsRandom8(3);
      if (isAlive) seg.data[i] |= ALIVE;
      if (!isAlive) seg.data[i] |= FADED;
      const x = i % cols, y = Math.floor(i / cols);
      if (x === 0 || x === cols - 1 || y === 0 || y === rows - 1) seg.data[i] |= EDGE;
      seg.setPixelColor(i, isAlive ? seg.color_from_palette(m.jsRandom8(), false, false, 0) : bgColor);
    }
  }

  if (paused || (m.getNow() - seg.step < Math.floor(1000 / m.map(seg.speed, 0, 255, 1, 42)))) {
    for (let i = maxIndex - 1; i >= 0; i--) {
      if (!(seg.data[i] & ALIVE)) {
        const cellColor = seg.getPixelColor(i);
        if (cellColor !== bgColor) {
          if (seg.data[i] & FADED) {
            seg.setPixelColor(i, bgColor);
          } else {
            let blended = m.color_blend(cellColor, bgColor, 2);
            if (blended === cellColor) { blended = bgColor; seg.data[i] |= FADED; }
            seg.setPixelColor(i, blended);
          }
        }
      }
    }
    seg.aux0 = generation;
    return;
  }

  const updateOscillator = generation % 16 === 0;
  const updateSpaceship  = gliderLength && generation % gliderLength === 0;
  let repeatingOscillator = true, repeatingSpaceship = true, emptyGrid = true;

  let cIndex = maxIndex - 1;
  for (let y = rows - 1; y >= 0; y--) {
    for (let x = cols - 1; x >= 0; x--, cIndex--) {
      const cell = seg.data[cIndex];
      const cellAlive = cell & ALIVE;

      if (cellAlive) emptyGrid = false;
      if (((cell & OSC_CHK) ? 1 : 0) !== (cellAlive ? 1 : 0)) repeatingOscillator = false;
      if (((cell & SHIP_CHK) ? 1 : 0) !== (cellAlive ? 1 : 0)) repeatingSpaceship = false;
      if (updateOscillator) {
        if (cellAlive) seg.data[cIndex] |= OSC_CHK; else seg.data[cIndex] &= ~OSC_CHK;
      }
      if (updateSpaceship) {
        if (cellAlive) seg.data[cIndex] |= SHIP_CHK; else seg.data[cIndex] &= ~SHIP_CHK;
      }

      let neighbors = 0, aliveParents = 0;
      const parentIdx = [0, 0, 0];
      for (let di = -1; di <= 1; di++) {
        for (let dj = -1; dj <= 1; dj++) {
          if (di === 0 && dj === 0) continue;
          let nX = x + dj, nY = y + di;
          if (cell & EDGE) {
            nX = (nX + cols) % cols;
            nY = (nY + rows) % rows;
          }
          if (nX < 0 || nX >= cols || nY < 0 || nY >= rows) continue;
          const nIndex = nX + nY * cols;
          const neighbor = seg.data[nIndex];
          if (neighbor & ALIVE) {
            neighbors++;
            if (!(neighbor & TOGGLE) && neighbors < 4) {
              parentIdx[aliveParents++] = nIndex;
            }
          }
        }
      }

      let needsColor = false;
      let newColor = 0;

      if (cellAlive && (neighbors < 2 || neighbors > 3)) {
        seg.data[cIndex] |= TOGGLE;
        if (blur === 255) seg.data[cIndex] |= FADED;
        newColor = (seg.data[cIndex] & FADED) ? bgColor : m.color_blend(seg.getPixelColor(cIndex), bgColor, blur);
        needsColor = true;
      } else if (!cellAlive) {
        const mutationRoll = mutate ? m.jsRandom8(128) : 1;
        if ((neighbors === 3 && mutationRoll) || (mutate && neighbors === 2 && !mutationRoll)) {
          seg.data[cIndex] |= TOGGLE;
          seg.data[cIndex] &= ~FADED;
          if (aliveParents) {
            const parentIndex = parentIdx[m.jsRandom8(aliveParents)];
            birthColor = seg.getPixelColor(parentIndex);
          }
          newColor = birthColor;
          needsColor = true;
        } else if (!(seg.data[cIndex] & FADED)) {
          const cc = seg.getPixelColor(cIndex);
          let blended = m.color_blend(cc, bgColor, blur);
          if (blended === cc) { blended = bgColor; seg.data[cIndex] |= FADED; }
          newColor = blended;
          needsColor = true;
        }
      }

      if (needsColor) seg.setPixelColor(cIndex, newColor);
    }
  }

  for (let i = maxIndex - 1; i >= 0; i--) {
    if (seg.data[i] & TOGGLE) seg.data[i] ^= ALIVE;
    seg.data[i] &= ~TOGGLE;
  }

  if (repeatingOscillator || repeatingSpaceship || emptyGrid) {
    generation = 0;
    seg.step += 1024;
  } else {
    generation++;
    seg.step = m.getNow();
  }
  seg.aux0 = generation;
};

Effects2D[173] = function tartan(seg) {
  // By Elliott Kember, modified by Andrew Tuline.
  const m = M2D();
  const w = seg.vWidth();
  const h = seg.vHeight();

  if (seg.call === 0) seg.fill(0);

  const offsetX = m.beatsin16(3, -360, 360);
  const offsetY = m.beatsin16(2, -360, 360);
  const sharpness = (seg.custom3 >> 3);

  for (let x = 0; x < w; x++) {
    for (let y = 0; y < h; y++) {
      let hue = ((x * m.beatsin16(10, 1, 10) + offsetY) & 0xFF);
      let intensity = m.sin8(Math.floor(x * seg.speed / 2 + offsetX) & 0xFFFF);
      let bri = intensity;
      for (let i = 0; i < sharpness; i++) intensity *= bri;
      intensity = Math.floor(intensity / Math.pow(256, sharpness)) & 0xFF;
      seg.setPixelColorXY(x, y, seg.ColorFromPalette(hue, intensity));

      hue = (y * 3 + offsetX) & 0xFF;
      intensity = m.sin8(Math.floor(y * seg.intensity / 2 + offsetY) & 0xFFFF);
      bri = intensity;
      for (let i = 0; i < sharpness; i++) intensity *= bri;
      intensity = Math.floor(intensity / Math.pow(256, sharpness)) & 0xFF;
      seg.addPixelColorXY(x, y, seg.ColorFromPalette(hue, intensity));
    }
  }
};

// By Andrew Tuline.
Effects2D[176] = function lissajous(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  seg.fadeToBlackBy(seg.intensity);
  const phase = (Math.floor(m.getNow() * (1 + seg.custom3) / 32)) & 0xFFFF;

  for (let i = 0; i < 256; i++) {
    let xlocn = m.sin8((Math.floor(phase / 2) + Math.floor((i * seg.speed) / 32)) & 0xFF);
    let ylocn = m.cos8((Math.floor(phase / 2) + i * 2) & 0xFF);
    xlocn = (cols < 2) ? 1 : Math.floor((m.map(2 * xlocn, 0, 511, 0, 2 * (cols - 1)) + 1) / 2);
    ylocn = (rows < 2) ? 1 : Math.floor((m.map(2 * ylocn, 0, 511, 0, 2 * (rows - 1)) + 1) / 2);
    seg.setPixelColorXY(xlocn, ylocn, seg.color_from_palette(Math.floor(m.getNow() / 100 + i), false, false, 0));
  }
  seg.blur(seg.custom1 >> (1 + (seg.check1 ? 3 : 0)), seg.check1);
};

// By Stepko, modified by Andrew Tuline.
Effects2D[177] = function frizzles(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  seg.fadeToBlackBy(16 + (seg.check1 ? 10 : 0));
  for (let i = 8; i > 0; i--) {
    seg.addPixelColorXY(
      m.beatsin8(Math.floor(seg.speed / 8) + i, 0, cols - 1),
      m.beatsin8(Math.floor(seg.intensity / 8) - i, 0, rows - 1),
      seg.ColorFromPalette(m.beatsin8(12, 0, 255), 255)
    );
  }
  seg.blur(seg.custom1 >> (3 + (seg.check1 ? 1 : 0)), seg.check1);
};

Effects2D[180] = function hiphotic(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const a = Math.floor(m.getNow() / ((seg.custom3 >> 1) + 1));

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      seg.setPixelColorXY(x, y, seg.color_from_palette(
        m.sin8(m.cos8(Math.floor(x * seg.speed / 16 + a / 3)) + m.sin8(Math.floor(y * seg.intensity / 16 + a / 4)) + a),
        false, false, 0
      ));
    }
  }
};

Effects2D[124] = function distortionWaves(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  let speed = Math.floor(seg.speed / 32);
  let scale = Math.floor(seg.intensity / 32);
  if (seg.check2) scale += Math.floor(192 / (cols + rows));

  const a  = Math.floor(m.getNow() / 32);
  const a2 = Math.floor(a / 2);
  const a3 = Math.floor(a / 3);
  const colsScaled = cols * scale;
  const rowsScaled = rows * scale;

  const cx  = m.beatsin16(10 - speed, 0, colsScaled);
  const cy  = m.beatsin16(12 - speed, 0, rowsScaled);
  const cx1 = m.beatsin16(13 - speed, 0, colsScaled);
  const cy1 = m.beatsin16(15 - speed, 0, rowsScaled);
  const cx2 = m.beatsin16(17 - speed, 0, colsScaled);
  const cy2 = m.beatsin16(14 - speed, 0, rowsScaled);

  let xoffs = 0;
  for (let x = 0; x < cols; x++) {
    xoffs += scale;
    let yoffs = 0;

    for (let y = 0; y < rows; y++) {
      yoffs += scale;

      let rdistort, gdistort, bdistort;
      if (seg.check3) {
        rdistort = m.cos8(((x + y) * 8 + a2) & 255) >> 1;
        gdistort = m.cos8(((x + y) * 8 + a3 + 32) & 255) >> 1;
        bdistort = m.cos8(((x + y) * 8 + a + 64) & 255) >> 1;
      } else {
        rdistort = m.cos8((m.cos8(((x << 3) + a) & 255) + m.cos8(((y << 3) - a2) & 255) + a3) & 255) >> 1;
        gdistort = m.cos8((m.cos8(((x << 3) - a2) & 255) + m.cos8(((y << 3) + a3) & 255) + a + 32) & 255) >> 1;
        bdistort = m.cos8((m.cos8(((x << 3) + a3) & 255) + m.cos8(((y << 3) - a) & 255) + a2 + 64) & 255) >> 1;
      }

      let valueR = (rdistort + ((a  - (((xoffs - cx)  * (xoffs - cx)  + (yoffs - cy)  * (yoffs - cy))  >> 7)) << 1)) & 0xFF;
      let valueG = (gdistort + ((a2 - (((xoffs - cx1) * (xoffs - cx1) + (yoffs - cy1) * (yoffs - cy1)) >> 7)) << 1)) & 0xFF;
      let valueB = (bdistort + ((a3 - (((xoffs - cx2) * (xoffs - cx2) + (yoffs - cy2) * (yoffs - cy2)) >> 7)) << 1)) & 0xFF;

      valueR = m.cos8(valueR);
      valueG = m.cos8(valueG);
      valueB = m.cos8(valueB);

      if (seg.palette === 0) {
        seg.setPixelColorXY(x, y, m.colorPack(valueR, valueG, valueB));
      } else {
        const brightness = Math.floor((valueR + valueG + valueB) / 3);
        if (seg.check1) {
          seg.setPixelColorXY(x, y, seg.ColorFromPalette(brightness, 255, 2));
        } else {
          const hsv = m.rgb2hsv_packed(m.colorPack(valueR >> 2, valueG >> 2, valueB >> 2));
          seg.setPixelColorXY(x, y, seg.ColorFromPalette(hsv.h, brightness));
        }
      }
    }
  }

  if (!seg.check1 && seg.palette) {
    seg.blur(200, true);
  }
};

Effects2D[126] = function octopus(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const XY = (x, y) => (x % cols) + (y % rows) * cols;
  const mapp = Math.floor(180 / Math.max(cols, rows));

  if (seg.call === 0 || !seg._octoMap || seg._octoAux0 !== cols || seg._octoAux1 !== rows
      || seg._octoOffX !== seg.custom1 || seg._octoOffY !== seg.custom2) {
    seg._octoStep = 0;
    seg._octoAux0 = cols;
    seg._octoAux1 = rows;
    seg._octoOffX = seg.custom1;
    seg._octoOffY = seg.custom2;
    seg._octoMap = new Array(cols * rows);

    const C_X = Math.floor(cols / 2) + Math.floor((seg.custom1 - 128) * cols / 255);
    const C_Y = Math.floor(rows / 2) + Math.floor((seg.custom2 - 128) * rows / 255);

    for (let x = 0; x < cols; x++) {
      for (let y = 0; y < rows; y++) {
        const dx = x - C_X;
        const dy = y - C_Y;
        seg._octoMap[XY(x, y)] = {
          angle: Math.floor(40.7436 * m.atan2_t(dy, dx)) & 0xFF,
          radius: Math.floor(Math.sqrt(dx * dx + dy * dy) * mapp) & 0xFF
        };
      }
    }
  }

  seg._octoStep += Math.floor(seg.speed / 32) + 1;
  const step = seg._octoStep;

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      const angle = seg._octoMap[XY(x, y)].angle;
      const radius = seg._octoMap[XY(x, y)].radius;
      let intens = m.sin8(
        (m.sin8((((angle * 4 - radius) >> 2) + (step >> 1)) & 0xFF) + radius - step + angle * ((seg.custom3 >> 2) + 1)) & 0xFF
      );
      intens = m.map((intens * intens) & 0xFFFF, 0, 65535, 0, 255);
      seg.setPixelColorXY(x, y, seg.ColorFromPalette(((step >> 1) - radius) & 0xFF, intens));
    }
  }
};

// By ldirko, modified by Andrew Tuline.
Effects2D[162] = function pulser(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  seg.fadeToBlackBy(8 - (seg.intensity >> 5));
  const a = Math.floor(m.getNow() / (18 - Math.floor(seg.speed / 16)));
  const x = Math.floor(a / 14) % cols;
  const y = m.map((m.sin8((a * 5) & 0xFF) + m.sin8((a * 4) & 0xFF) + m.sin8((a * 2) & 0xFF)), 0, 765, rows - 1, 0);
  seg.setPixelColorXY(x, y, seg.ColorFromPalette(m.map(y, 0, rows - 1, 0, 255), 255));
  seg.blur(seg.intensity >> 4);
};

// By ldirko, modified by Andrew Tuline.
Effects2D[181] = function sindots(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) seg.fill(0);

  seg.fadeToBlackBy((seg.custom1 >> 3) + (seg.check1 ? 24 : 0));

  const t1 = (Math.floor(m.getNow() / (257 - seg.speed))) & 0xFF;
  const t2 = (Math.floor(m.sin8(t1) / 4) * 2) & 0xFF;
  for (let i = 0; i < 13; i++) {
    const x = Math.floor(m.sin8((t1 + i * Math.floor(seg.intensity / 8)) & 0xFF) * (cols - 1) / 255);
    const y = Math.floor(m.sin8((t2 + i * Math.floor(seg.intensity / 8)) & 0xFF) * (rows - 1) / 255);
    seg.setPixelColorXY(x, y, seg.ColorFromPalette(Math.floor(i * 255 / 13), 255));
  }
  seg.blur(seg.custom2 >> (3 + (seg.check1 ? 1 : 0)), seg.check1);
};

// By Kostyantyn Matviyevskyy, modified by Andrew Tuline and @dedehai.
Effects2D[174] = function polarLights(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg.fill(0);
    seg.step = 0;
  }

  const adjustHeight = m.map(rows, 8, 32, 28, 12);
  const adjScale = m.map(cols, 8, 64, 310, 63);
  const _scale = m.map(seg.intensity, 0, 255, 30, adjScale);
  const _speed = m.map(seg.speed, 0, 255, 128, 16);

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      seg.step = (seg.step + 1) >>> 0;
      let palindex = m.qsub8(
        m.perlin8((seg.step % 2) + x * _scale, y * 16 + seg.step % 16, Math.floor(seg.step / _speed)),
        Math.floor(Math.abs(rows / 2.0 - y) * adjustHeight)
      );
      const palbrightness = palindex;
      if (seg.check1) palindex = 255 - palindex;
      seg.setPixelColorXY(x, y, seg.color_from_palette(palindex, false, false, 255, palbrightness));
    }
  }
};

// stepko (c)05.02.21, adapted by Blaz Kristan (AKA blazoncek)
Effects2D[118] = function spaceships(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const tb = m.getNow() >>> 12;
  if (tb > (seg.step || 0)) {
    let dir = (seg.aux0 || 0) + 1;
    dir += m.jsRandom8(0, 3) - 1;
    if (dir > 7)      seg.aux0 = 0;
    else if (dir < 0)  seg.aux0 = 7;
    else               seg.aux0 = dir;
    seg.step = tb + m.jsRandom8(0, 4);
  }

  seg.fadeToBlackBy(m.map(seg.speed, 0, 255, 248, 16));
  seg.move(seg.aux0 || 0, 1);

  for (let i = 0; i < 8; i++) {
    const x = m.beatsin8(12 + i, 2, cols - 3);
    const y = m.beatsin8(15 + i, 2, rows - 3);
    const color = seg.ColorFromPalette(m.beatsin8(12 + i, 0, 255), 255);
    seg.addPixelColorXY(x, y, color);
    if (cols > 24 || rows > 24) {
      seg.addPixelColorXY(x + 1, y, color);
      seg.addPixelColorXY(x - 1, y, color);
      seg.addPixelColorXY(x, y + 1, color);
      seg.addPixelColorXY(x, y - 1, color);
    }
  }
  seg.blur(seg.intensity >> 3, seg.check1);
};

Effects2D[119] = function crazyBees(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const MAX_BEES = 5;

  const n = Math.min(MAX_BEES, Math.floor((rows * cols) / 256) + 1);

  const BEE_SIZE = 10;
  if (!seg.allocateData(BEE_SIZE * MAX_BEES)) return;

  if (seg.call === 0) {
    for (let i = 0; i < n; i++) {
      const off = i * BEE_SIZE;
      seg.data[off + 0] = m.jsRandom8(0, cols);
      seg.data[off + 1] = m.jsRandom8(0, rows);
      const aimX = m.jsRandom8(0, cols);
      const aimY = m.jsRandom8(0, rows);
      const hue = m.jsRandom8();
      seg.data[off + 2] = aimX;
      seg.data[off + 3] = aimY;
      seg.data[off + 4] = hue;
      seg.data[off + 5] = Math.abs(aimX - seg.data[off + 0]);
      seg.data[off + 6] = Math.abs(aimY - seg.data[off + 1]);
      seg.data[off + 7] = seg.data[off + 0] < aimX ? 1 : 255;
      seg.data[off + 8] = seg.data[off + 1] < aimY ? 1 : 255;
      if (!seg._beeErrors) seg._beeErrors = new Int8Array(MAX_BEES);
      seg._beeErrors[i] = seg.data[off + 5] - seg.data[off + 6];
    }
  }
  if (!seg._beeErrors) seg._beeErrors = new Int8Array(MAX_BEES);

  function beeAimed(seg, off, w, h) {
    const aimX  = m.jsRandom8(0, w);
    const aimY  = m.jsRandom8(0, h);
    const hue   = m.jsRandom8();
    seg.data[off + 2] = aimX;
    seg.data[off + 3] = aimY;
    seg.data[off + 4] = hue;
    seg.data[off + 5] = Math.abs(aimX - seg.data[off + 0]);
    seg.data[off + 6] = Math.abs(aimY - seg.data[off + 1]);
    seg.data[off + 7] = seg.data[off + 0] < aimX ? 1 : 255;
    seg.data[off + 8] = seg.data[off + 1] < aimY ? 1 : 255;
    seg._beeErrors[Math.floor(off / BEE_SIZE)] = seg.data[off + 5] - seg.data[off + 6];
  }

  if (m.getNow() > seg.step) {
    seg.step = m.getNow() + (m.FRAMETIME * 16 / ((seg.speed >> 4) + 1));
    seg.fadeToBlackBy(32 + ((seg.check1 * seg.intensity) / 25));
    seg.blur(Math.floor(seg.intensity / (2 + seg.check1 * 9)), !!seg.check1);
    for (let i = 0; i < n; i++) {
      const off = i * BEE_SIZE;
      const posX   = seg.data[off + 0];
      const posY   = seg.data[off + 1];
      const aimX   = seg.data[off + 2];
      const aimY   = seg.data[off + 3];
      const hue    = seg.data[off + 4];
      const deltaX = seg.data[off + 5];
      const deltaY = seg.data[off + 6];
      const signX  = seg.data[off + 7] === 1 ? 1 : -1;
      const signY  = seg.data[off + 8] === 1 ? 1 : -1;

      const flowerColor = seg.color_from_palette(hue, false, true, 255);
      seg.addPixelColorXY(aimX + 1, aimY, flowerColor);
      seg.addPixelColorXY(aimX, aimY + 1, flowerColor);
      seg.addPixelColorXY(aimX - 1, aimY, flowerColor);
      seg.addPixelColorXY(aimX, aimY - 1, flowerColor);

      if (posX !== aimX || posY !== aimY) {
        seg.setPixelColorXY(posX, posY, m.hsv2rgb(hue, 60, 255));
        const error2 = seg._beeErrors[i] * 2;
        if (error2 > -deltaY) {
          seg._beeErrors[i] -= deltaY;
          seg.data[off + 0] = posX + signX;
        }
        if (error2 < deltaX) {
          seg._beeErrors[i] += deltaX;
          seg.data[off + 1] = posY + signY;
        }
      } else {
        beeAimed(seg, off, cols, rows);
      }
    }
  }
};

// stepko (c)2021, adapted by Blaz Kristan (AKA blazoncek)
Effects2D[120] = function ghostRider(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const LIGHTERS_AM = 64;

  const maxLighters = Math.min(cols + rows, LIGHTERS_AM);

  if (seg.aux0 !== cols || seg.aux1 !== rows || !seg._ghost) {
    seg.aux0 = cols;
    seg.aux1 = rows;
    seg._ghost = {
      gPosX: Math.floor(cols / 2) * 10,
      gPosY: Math.floor(rows / 2) * 10,
      gAngle: (Math.random() * 65536) >>> 0,
      angleSpeed: m.jsRandom8(0, 20) - 10,
      Vspeed: 5,
      lightersPosX: new Uint16Array(LIGHTERS_AM),
      lightersPosY: new Uint16Array(LIGHTERS_AM),
      Angle: new Uint16Array(LIGHTERS_AM),
      time: new Uint16Array(LIGHTERS_AM),
      reg: new Uint8Array(LIGHTERS_AM)
    };
    const g = seg._ghost;
    for (let i = 0; i < maxLighters; i++) {
      g.lightersPosX[i] = g.gPosX;
      g.lightersPosY[i] = g.gPosY + i;
      g.time[i] = i * 2;
      g.reg[i] = 0;
    }
  }

  if (m.getNow() > (seg.step || 0)) {
    seg.step = m.getNow() + Math.floor(1024 / (cols + rows));

    seg.fadeToBlackBy((seg.speed >> 2) + 64);

    const g = seg._ghost;

    seg.wu_pixel(Math.floor(g.gPosX * 256 / 10), Math.floor(g.gPosY * 256 / 10), 0xFFFFFF);

    g.gPosX += Math.floor(g.Vspeed * m.sin_t(m.radians(g.gAngle)));
    g.gPosY += Math.floor(g.Vspeed * m.cos_t(m.radians(g.gAngle)));
    g.gAngle = (g.gAngle + g.angleSpeed) & 0xFFFF;
    if (g.gPosX < 0)               g.gPosX = (cols - 1) * 10;
    if (g.gPosX > (cols - 1) * 10) g.gPosX = 0;
    if (g.gPosY < 0)               g.gPosY = (rows - 1) * 10;
    if (g.gPosY > (rows - 1) * 10) g.gPosY = 0;

    for (let i = 0; i < maxLighters; i++) {
      g.time[i] += m.jsRandom8(5, 20);
      if (g.time[i] >= 255 ||
        g.lightersPosX[i] <= 0 ||
        g.lightersPosX[i] >= (cols - 1) * 10 ||
        g.lightersPosY[i] <= 0 ||
        g.lightersPosY[i] >= (rows - 1) * 10) {
        g.reg[i] = 1;
      }
      if (g.reg[i]) {
        g.lightersPosY[i] = g.gPosY;
        g.lightersPosX[i] = g.gPosX;
        g.Angle[i] = (g.gAngle + m.jsRandom8(0, 20) - 10) & 0xFFFF;
        g.time[i] = 0;
        g.reg[i] = 0;
      } else {
        g.lightersPosX[i] = (g.lightersPosX[i] + Math.floor(-7 * m.sin_t(m.radians(g.Angle[i])))) & 0xFFFF;
        g.lightersPosY[i] = (g.lightersPosY[i] + Math.floor(-7 * m.cos_t(m.radians(g.Angle[i])))) & 0xFFFF;
      }
      seg.wu_pixel(
        Math.floor(g.lightersPosX[i] * 256 / 10),
        Math.floor(g.lightersPosY[i] * 256 / 10),
        seg.ColorFromPalette((256 - g.time[i]) & 0xFF)
      );
    }
    seg.blur(seg.intensity >> 3);
  }
};

Effects2D[121] = function blobs(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const MAX_BLOBS = 8;

  const Amount = (seg.intensity >> 5) + 1;

  if (seg.aux0 !== cols || seg.aux1 !== rows || !seg._blobs) {
    seg.aux0 = cols;
    seg.aux1 = rows;
    seg._blobs = [];
    for (let i = 0; i < MAX_BLOBS; i++) {
      const blob = {};
      blob.r     = m.jsRandom8(1, cols > 8 ? Math.floor(cols / 4) : 2);
      blob.sX    = m.jsRandom8(3, cols) / (256 - seg.speed);
      blob.sY    = m.jsRandom8(3, rows) / (256 - seg.speed);
      blob.x     = m.jsRandom8(0, cols - 1);
      blob.y     = m.jsRandom8(0, rows - 1);
      blob.color = m.jsRandom8();
      blob.grow  = (blob.r < 1.0);
      if (blob.sX === 0) blob.sX = 1;
      if (blob.sY === 0) blob.sY = 1;
      seg._blobs.push(blob);
    }
  }

  seg.fadeToBlackBy((seg.custom2 >> 3) + 1);

  for (let i = 0; i < Amount; i++) {
    const blob = seg._blobs[i];
    if (seg.step < m.getNow()) blob.color = (blob.color + 4) & 0xFF;

    if (blob.grow) {
      blob.r += (Math.abs(blob.sX) > Math.abs(blob.sY) ? Math.abs(blob.sX) : Math.abs(blob.sY)) * 0.05;
      if (blob.r >= Math.min(cols / 4.0, 2.0)) {
        blob.grow = false;
      }
    } else {
      blob.r -= (Math.abs(blob.sX) > Math.abs(blob.sY) ? Math.abs(blob.sX) : Math.abs(blob.sY)) * 0.05;
      if (blob.r < 1.0) {
        blob.grow = true;
      }
    }
    const c = seg.color_from_palette(blob.color, false, false, 0);
    if (blob.r > 1.0) seg.fillCircle(Math.round(blob.x), Math.round(blob.y), Math.round(blob.r), c);
    else               seg.setPixelColorXY(Math.round(blob.x), Math.round(blob.y), c);

    if (blob.x + blob.r >= cols - 1) blob.x += (blob.sX * ((cols - 1 - blob.x) / blob.r + 0.005));
    else if (blob.x - blob.r <= 0)   blob.x += (blob.sX * (blob.x / blob.r + 0.005));
    else                              blob.x += blob.sX;
    if (blob.y + blob.r >= rows - 1) blob.y += (blob.sY * ((rows - 1 - blob.y) / blob.r + 0.005));
    else if (blob.y - blob.r <= 0)   blob.y += (blob.sY * (blob.y / blob.r + 0.005));
    else                              blob.y += blob.sY;
    if (blob.x < 0.01) {
      blob.sX = m.jsRandom8(3, cols) / (256 - seg.speed);
      blob.x  = 0.01;
    } else if (blob.x > cols - 1.01) {
      blob.sX = m.jsRandom8(3, cols) / (256 - seg.speed);
      blob.sX = -blob.sX;
      blob.x  = cols - 1.01;
    }
    if (blob.y < 0.01) {
      blob.sY = m.jsRandom8(3, rows) / (256 - seg.speed);
      blob.y  = 0.01;
    } else if (blob.y > rows - 1.01) {
      blob.sY = m.jsRandom8(3, rows) / (256 - seg.speed);
      blob.sY = -blob.sY;
      blob.y  = rows - 1.01;
    }
  }
  seg.blur(seg.custom1 >> 2);

  if (seg.step < m.getNow()) seg.step = m.getNow() + 2000;
};

// stepko (c)2021, adapted by Blaz Kristan (AKA blazoncek), improved by @dedehai
Effects2D[123] = function driftRose(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const CX = ((cols - cols % 2) / 2.0) - 0.5;
  const CY = ((rows - rows % 2) / 2.0) - 0.5;
  const L = Math.min(cols, rows) / 2.0;

  seg.fadeToBlackBy(32 + (seg.speed >> 3));
  for (let i = 1; i < 37; i++) {
    const angle = m.radians(i * 10);
    const x = Math.floor((CX + (m.sin_t(angle) * (m.beatsin8(i, 0, Math.floor(L * 2)) - L))) * 255);
    const y = Math.floor((CY + (m.cos_t(angle) * (m.beatsin8(i, 0, Math.floor(L * 2)) - L))) * 255);
    if (seg.palette === 0) {
      seg.wu_pixel(x, y, m.hsv2rgb(i * 10, 255, 255));
    } else {
      seg.wu_pixel(x, y, seg.ColorFromPalette(i * 10));
    }
  }
  seg.blur(seg.intensity >> 4, seg.check1);
};

Effects2D[125] = function soap(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  const XY = (x, y) => x + y * cols;

  const segSize = cols * rows;
  if (seg.call === 0 || !seg._soapNoise || seg._soapCols !== cols || seg._soapRows !== rows) {
    seg._soapCols = cols;
    seg._soapRows = rows;
    seg._soapNoise = new Uint8Array(segSize);
    seg._soapPixels = new Uint32Array(segSize);
    seg._soapCoord = new Uint32Array(3);
    seg._soapCoord[0] = (Math.random() * 0xFFFFFFFF) >>> 0;
    seg._soapCoord[1] = (Math.random() * 0xFFFFFFFF) >>> 0;
    seg._soapCoord[2] = (Math.random() * 0xFFFFFFFF) >>> 0;
  }

  const noise3d    = seg._soapNoise;
  const pixels     = seg._soapPixels;
  const noisecoord = seg._soapCoord;
  const scale32_x  = Math.floor(160000 / cols);
  const scale32_y  = Math.floor(160000 / rows);
  const mov        = Math.floor(Math.min(cols, rows) * ((seg.speed|0) + 2) / 2);
  const smoothness = Math.min(250, (seg.intensity|0));

  if (seg.call !== 0) {
    for (let i = 0; i < 3; i++) noisecoord[i] = (noisecoord[i] + mov) >>> 0;
  }

  for (let i = 0; i < cols; i++) {
    const ioffset = scale32_x * (i - Math.floor(cols / 2));
    for (let j = 0; j < rows; j++) {
      const joffset = scale32_y * (j - Math.floor(rows / 2));
      const data = m.inoise16(noisecoord[0] + ioffset, noisecoord[1] + joffset, noisecoord[2]) >> 8;
      noise3d[XY(i, j)] = (m.scale8(noise3d[XY(i, j)], smoothness) + m.scale8(data & 0xFF, 255 - smoothness)) & 0xFF;
    }
  }

  if (seg.call === 0 || seg.aux0 !== cols || seg.aux1 !== rows) {
    seg.aux0 = cols;
    seg.aux1 = rows;
    for (let i = 0; i < cols; i++) {
      for (let j = 0; j < rows; j++) {
        const c = seg.ColorFromPalette((~noise3d[XY(i, j)] & 0xFF) * 3);
        seg.setPixelColorXY(i, j, c);
        pixels[XY(i, j)] = c;
      }
    }
  }

  const ledsbuff = seg._soapLedsbuff && seg._soapLedsbuff.length >= Math.max(cols, rows)
    ? seg._soapLedsbuff
    : (seg._soapLedsbuff = new Uint32Array(Math.max(cols, rows)));

  function soapPass(isRow) {
    const tRC = isRow ? rows : cols;
    const tCR = isRow ? cols : rows;
    const amplitude = Math.max(1, (tCR - 8) >> 3) * (1 + ((seg.custom1|0) >> 5));

    for (let i = 0; i < tRC; i++) {
      const noiseIdx = isRow ? i * cols : i;
      let amount = (noise3d[noiseIdx] - 128) * amplitude;
      let delta    = Math.abs(amount) >> 8;
      let fraction = Math.abs(amount) & 255;

      for (let j = 0; j < tCR; j++) {
        let zD, zF;
        if (amount < 0) {
          zD = j - delta;
          zF = zD - 1;
        } else {
          zD = j + delta;
          zF = zD + 1;
        }
        let yA = Math.abs(zD) % tCR;
        let yB = Math.abs(zF) % tCR;
        let xA = i, xB = i;
        if (isRow) {
          let tmp;
          tmp = xA; xA = yA; yA = tmp;
          tmp = xB; xB = yB; yB = tmp;
        }
        const indxA = XY(xA, yA);
        const indxB = XY(xB, yB);
        let PixelA, PixelB;
        if (zD >= 0 && zD < tCR) PixelA = pixels[indxA];
        else                      PixelA = seg.ColorFromPalette((~noise3d[indxA] & 0xFF) * 3);
        if (zF >= 0 && zF < tCR) PixelB = pixels[indxB];
        else                      PixelB = seg.ColorFromPalette((~noise3d[indxB] & 0xFF) * 3);

        const easeA = m.ease8InOutApprox(255 - fraction);
        const easeB = m.ease8InOutApprox(fraction);
        const rA = (m.colorR(PixelA) * easeA) >> 8, gA = (m.colorG(PixelA) * easeA) >> 8, bA = (m.colorB(PixelA) * easeA) >> 8;
        const rB = (m.colorR(PixelB) * easeB) >> 8, gB = (m.colorG(PixelB) * easeB) >> 8, bB = (m.colorB(PixelB) * easeB) >> 8;
        ledsbuff[j] = m.colorPack(Math.min(255, rA + rB), Math.min(255, gA + gB), Math.min(255, bA + bB));
      }
      for (let j = 0; j < tCR; j++) {
        let ii = i, jj = j;
        if (isRow) { let tmp = ii; ii = jj; jj = tmp; }
        pixels[XY(ii, jj)] = ledsbuff[j];
        seg.setPixelColorXY(ii, jj, ledsbuff[j]);
      }
    }
  }

  soapPass(true);
  soapPass(false);
};

Effects2D[127] = function wavingCell(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const t  = ((m.getNow() * (seg.speed + 1)) >> 3) >>> 0;
  const aX = Math.floor(seg.custom1 / 16) + 9;
  const aY = Math.floor(seg.custom2 / 16) + 1;
  const aZ = seg.custom3 + 1;

  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      const wave = (m.sin8(((x * aX) + m.sin8((((y << 8) + t) * aY) >> 8)) & 0xFF) + m.cos8((y * aZ) & 0xFF)) & 0xFF;
      const colorIndex = (wave + (t >> (8 - (seg.check2 * 3)))) & 0xFF;
      seg.setPixelColorXY(x, y, seg.ColorFromPalette(colorIndex));
    }
  }
  seg.blur(seg.intensity);
};

// By ldirko, modified by Andrew Tuline.
Effects2D[167] = function coloredBursts(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg.aux0 = 0;
  }

  const dot = !!seg.check3;
  const grad = !!seg.check1;

  const numLines = Math.floor(seg.intensity / 16) + 1;

  seg.aux0++;
  seg.fadeToBlackBy(40 - (seg.check2 ? 8 : 0));

  for (let i = 0; i < numLines; i++) {
    const x1 = m.beatsin8(2 + Math.floor(seg.speed / 16), 0, cols - 1);
    const x2 = m.beatsin8(1 + Math.floor(seg.speed / 16), 0, rows - 1);
    const y1 = m.beatsin8(5 + Math.floor(seg.speed / 16), 0, cols - 1, 0, i * 24);
    const y2 = m.beatsin8(3 + Math.floor(seg.speed / 16), 0, rows - 1, 0, i * 48 + 64);
    const color = seg.ColorFromPalette(Math.floor(i * 255 / numLines) + (seg.aux0 & 0xFF), 255);

    const xsteps = m.abs8(x1 - y1) + 1;
    const ysteps = m.abs8(x2 - y2) + 1;
    const steps = xsteps >= ysteps ? xsteps : ysteps;

    for (let j = 1; j <= steps; j++) {
      const rate = Math.floor(j * 255 / steps) & 0xFF;
      const dx = m.lerp8by8(x1, y1, rate);
      const dy = m.lerp8by8(x2, y2, rate);
      seg.addPixelColorXY(dx, dy, color);
      if (grad) seg.fadePixelColorXY(dx, dy, rate);
    }

    if (dot) {
      seg.setPixelColorXY(x1, x2, 0xFFFFFF);
      seg.setPixelColorXY(y1, y2, 0x2F4F4F);
    }
  }
  seg.blur(seg.custom3 >> 1, seg.check2);
};

// An animated Julia set by Andrew Tuline.
Effects2D[168] = function julia(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg._juliaXcen = 0.0;
    seg._juliaYcen = 0.0;
    seg._juliaXymag = 1.0;

    seg.custom1 = 128;
    seg.custom2 = 128;
    seg.custom3 = 16;
    seg.intensity = 24;
  }

  seg._juliaXcen  = seg._juliaXcen  + (seg.custom1 - 128) / 100000.0;
  seg._juliaYcen  = seg._juliaYcen  + (seg.custom2 - 128) / 100000.0;
  seg._juliaXymag = seg._juliaXymag + ((seg.custom3 - 16) << 3) / 100000.0;
  if (seg._juliaXymag < 0.01) seg._juliaXymag = 0.01;
  if (seg._juliaXymag > 1.0)  seg._juliaXymag = 1.0;

  let xmin = seg._juliaXcen - seg._juliaXymag;
  let xmax = seg._juliaXcen + seg._juliaXymag;
  let ymin = seg._juliaYcen - seg._juliaXymag;
  let ymax = seg._juliaYcen + seg._juliaXymag;

  xmin = m.constrain(xmin, -1.2, 1.2);
  xmax = m.constrain(xmax, -1.2, 1.2);
  ymin = m.constrain(ymin, -0.8, 1.0);
  ymax = m.constrain(ymax, -0.8, 1.0);

  let maxIterations = Math.floor(seg.intensity / 2);
  const maxCalc = 16.0;

  let reAl = -0.94299;
  let imAg = 0.3162;

  reAl += m.sin16((m.getNow() * 34) & 0xFFFF) / 655340.0;
  imAg += m.sin16((m.getNow() * 26) & 0xFFFF) / 655340.0;

  const dx = (xmax - xmin) / cols;
  const dy = (ymax - ymin) / rows;

  let yy = ymin;
  for (let j = 0; j < rows; j++) {
    let xx = xmin;
    for (let i = 0; i < cols; i++) {
      let a = xx;
      let b = yy;
      let iter = 0;

      while (iter < maxIterations) {
        const aa = a * a;
        const bb = b * b;
        const len = aa + bb;
        if (len > maxCalc) break;
        b = 2 * a * b + imAg;
        a = aa - bb + reAl;
        iter++;
      }

      if (iter === maxIterations) {
        seg.setPixelColorXY(i, j, 0);
      } else {
        seg.setPixelColorXY(i, j, seg.color_from_palette(Math.floor(iter * 255 / maxIterations), false, false, 0));
      }
      xx += dx;
    }
    yy += dy;
  }
  if (seg.check1) seg.blur(100, true);
};

Effects2D[175] = function swirl(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg.fill(0);
  }

  const borderWidth = 2;

  seg.blur(seg.custom1);

  const i  = m.beatsin8(Math.floor(27 * seg.speed / 255), borderWidth, cols - borderWidth);
  const j  = m.beatsin8(Math.floor(41 * seg.speed / 255), borderWidth, rows - borderWidth);
  const ni = (cols - 1) - i;
  const nj = (cols - 1) - j;

  const volumeSmth = seg.getVolume();
  const volumeRaw  = Math.floor(volumeSmth);

  const bri = Math.floor(volumeRaw * seg.intensity / 64);
  seg.addPixelColorXY( i,  j, seg.ColorFromPalette(Math.floor(m.getNow() / 11 + volumeSmth * 4), bri));
  seg.addPixelColorXY( j,  i, seg.ColorFromPalette(Math.floor(m.getNow() / 13 + volumeSmth * 4), bri));
  seg.addPixelColorXY(ni, nj, seg.ColorFromPalette(Math.floor(m.getNow() / 17 + volumeSmth * 4), bri));
  seg.addPixelColorXY(nj, ni, seg.ColorFromPalette(Math.floor(m.getNow() / 29 + volumeSmth * 4), bri));
  seg.addPixelColorXY( i, nj, seg.ColorFromPalette(Math.floor(m.getNow() / 37 + volumeSmth * 4), bri));
  seg.addPixelColorXY(ni,  j, seg.ColorFromPalette(Math.floor(m.getNow() / 41 + volumeSmth * 4), bri));
};

Effects2D[178] = function plasmaBall(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  seg.fadeToBlackBy(seg.custom1 >> 2);
  const t = Math.floor((m.getNow() * 8) / (256 - seg.speed));
  for (let i = 0; i < cols; i++) {
    const thisVal = m.perlin8(i * 30, t, t);
    const thisMax = m.map(thisVal, 0, 255, 0, cols - 1);
    for (let j = 0; j < rows; j++) {
      const thisVal_ = m.perlin8(t, j * 30, t);
      const thisMax_ = m.map(thisVal_, 0, 255, 0, rows - 1);
      const x = (i + thisMax_ - Math.floor(cols / 2));
      const y = (j + thisMax - Math.floor(cols / 2));
      const cx = (i + thisMax_);
      const cy = (j + thisMax);

      const cond = ((x - y > -2) && (x - y < 2)) ||
                   ((cols - 1 - x - y) > -2 && (cols - 1 - x - y < 2)) ||
                   (cols - cx === 0) ||
                   (cols - 1 - cx === 0) ||
                   ((rows - cy === 0) ||
                   (rows - 1 - cy === 0));
      seg.addPixelColorXY(i, j, cond ? seg.ColorFromPalette(m.beat8(5), thisVal) : 0);
    }
  }
  seg.blur(seg.custom2 >> 5);
};

// By ldirko, modified by Andrew Tuline.
Effects2D[182] = function dnaSpiral(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (seg.call === 0) {
    seg.fill(0);
  }

  const speeds = Math.floor(seg.speed / 2) + 7;
  const freq = Math.floor(seg.intensity / 8);

  const ms = Math.floor(m.getNow() / 20);
  seg.fadeToBlackBy(135);

  for (let i = 0; i < rows; i++) {
    let x  = m.beatsin8(speeds, 0, cols - 1, 0, i * freq) + m.beatsin8(speeds - 7, 0, cols - 1, 0, i * freq + 128);
    let x1 = m.beatsin8(speeds, 0, cols - 1, 0, 128 + i * freq) + m.beatsin8(speeds - 7, 0, cols - 1, 0, 128 + 64 + i * freq);
    const hue = ((Math.floor(i * 128 / rows)) + ms) & 0xFF;

    if ((i + Math.floor(ms / 8)) & 3) {
      x = Math.floor(x / 2);
      x1 = Math.floor(x1 / 2);
      const steps = m.abs8(x - x1) + 1;
      const positive = (x1 >= x);

      for (let k = 1; k <= steps; k++) {
        const rate = Math.floor(k * 255 / steps);
        const dx = positive ? (x + k - 1) : (x - k + 1);
        seg.addPixelColorXY(dx, i, seg.ColorFromPalette(hue, 255));
        seg.fadePixelColorXY(dx, i, rate);
      }
      seg.setPixelColorXY(x, i, 0x2F4F4F);
      seg.setPixelColorXY(x1, i, 0xFFFFFF);
    }
  }
  seg.blur(Math.floor(seg.custom1 * 3 / (6 + (seg.check1 ? 1 : 0))), seg.check1);
};

// By Stepko, modified by Andrew Tuline.
Effects2D[183] = function blackHole(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  let x, y;

  seg.fadeToBlackBy(16 + (seg.speed >> 3));
  const t = Math.floor(m.getNow() / 128);

  for (let i = 0; i < 8; i++) {
    x = m.beatsin8(seg.custom1 >> 3, 0, cols - 1, 0, ((i % 2) ? 128 : 0) + t * i);
    y = m.beatsin8(seg.intensity >> 3, 0, rows - 1, 0, ((i % 2) ? 192 : 64) + t * i);
    seg.addPixelColorXY(x, y, seg.color_from_palette(i * 32, false, false, seg.check1 ? 0 : 255));
  }
  for (let i = 0; i < 4; i++) {
    x = m.beatsin8(seg.custom2 >> 3, Math.floor(cols / 4), cols - 1 - Math.floor(cols / 4), 0, ((i % 2) ? 128 : 0) + t * i);
    y = m.beatsin8(seg.custom3, Math.floor(rows / 4), rows - 1 - Math.floor(rows / 4), 0, ((i % 2) ? 192 : 64) + t * i);
    seg.addPixelColorXY(x, y, seg.color_from_palette(255 - i * 64, false, false, seg.check1 ? 0 : 255));
  }
  seg.setPixelColorXY(Math.floor(cols / 2), Math.floor(rows / 2), 0xFFFFFF);
  if (seg.check3) seg.blur(16, cols * rows < 100);
};

Effects2D[139] = function geq(seg) {
  const m = M2D();
  const NUM_BANDS  = m.map(seg.custom1, 0, 255, 1, 16);
  const CENTER_BIN = m.map(seg.custom3, 0, 31, 0, 15);
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  if (!seg._prevBar || seg._prevBar.length < cols) {
    seg._prevBar = new Uint16Array(cols);
  }

  const fftResult = new Uint8Array(16);
  for (let i = 0; i < 16; i++) fftResult[i] = seg.getFFT(i);

  if (seg.call === 0) seg._prevBar.fill(0);

  let rippleTime = false;
  if (m.getNow() - seg.step >= (256 - seg.intensity)) {
    seg.step = m.getNow();
    rippleTime = true;
  }

  const fadeoutDelay = Math.floor((256 - seg.speed) / 64);
  if ((fadeoutDelay <= 1) || ((seg.call % fadeoutDelay) === 0)) seg.fadeToBlackBy(seg.speed);

  for (let x = 0; x < cols; x++) {
    let band = m.map(x, 0, cols, 0, NUM_BANDS);
    if (NUM_BANDS < 16) {
      const startBin = m.constrain(CENTER_BIN - Math.floor(NUM_BANDS / 2), 0, 15 - NUM_BANDS + 1);
      if (NUM_BANDS <= 1)
        band = CENTER_BIN;
      else
        band = m.map(band, 0, NUM_BANDS - 1, startBin, startBin + NUM_BANDS - 1);
    }
    band = m.constrain(band, 0, 15);
    let colorIndex = band * 17;
    const barHeight = m.map(fftResult[band], 0, 255, 0, rows);
    if (barHeight > seg._prevBar[x]) seg._prevBar[x] = barHeight;

    let ledColor = 0;
    for (let y = 0; y < barHeight; y++) {
      if (seg.check1)
        colorIndex = m.map(y, 0, rows - 1, 0, 255);
      ledColor = seg.color_from_palette(colorIndex, false, false, 0);
      seg.setPixelColorXY(x, rows - 1 - y, ledColor);
    }
    if (seg._prevBar[x] > 0)
      seg.setPixelColorXY(x, rows - seg._prevBar[x], (seg.SEGCOLOR(2) !== 0) ? seg.SEGCOLOR(2) : ledColor);

    if (rippleTime && seg._prevBar[x] > 0) seg._prevBar[x]--;
  }
};

Effects2D[160] = function funkyPlank(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  let NUMB_BANDS = m.map(seg.custom1, 0, 255, 1, 16);
  let barWidth = Math.floor(cols / NUMB_BANDS);
  let bandInc = 1;
  if (barWidth === 0) {
    barWidth = 1;
    bandInc = Math.floor(NUMB_BANDS / cols);
  }

  const fftResult = new Uint8Array(16);
  for (let i = 0; i < 16; i++) fftResult[i] = seg.getFFT(i);

  if (seg.call === 0) {
    seg.fill(0);
  }

  const secondHand = (Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) + 1) & 0xFF;
  if (seg.aux0 !== secondHand) {
    seg.aux0 = secondHand;

    let b = 0;
    for (let band = 0; band < NUMB_BANDS; band += bandInc, b++) {
      const hue = fftResult[band % 16];
      const v = m.map(fftResult[band % 16], 0, 255, 10, 255);
      for (let w = 0; w < barWidth; w++) {
        const xpos = (barWidth * b) + w;
        seg.setPixelColorXY(xpos, 0, m.hsv2rgb(hue, 255, v));
      }
    }

    for (let i = rows - 1; i > 0; i--) {
      for (let j = cols - 1; j >= 0; j--) {
        seg.setPixelColorXY(j, i, seg.getPixelColorXY(j, i - 1));
      }
    }
  }
};

Effects2D[165] = function waverly(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const volumeSmth = seg.getVolume();

  seg.fadeToBlackBy(seg.speed);

  const t = Math.floor(m.getNow() / 2);
  for (let i = 0; i < cols; i++) {
    let thisVal = Math.floor((1 + seg.intensity / 64) * m.perlin8(i * 45, t, t) / 2);
    thisVal = Math.floor(thisVal / 32);
    thisVal = Math.floor(thisVal * volumeSmth);
    const thisMax = m.map(thisVal, 0, 512, 0, rows);

    for (let j = 0; j < thisMax; j++) {
      const palIdx = m.map(j, 0, thisMax, 250, 0);
      const color = seg.ColorFromPalette(palIdx, 255);
      seg.addPixelColorXY(i, j, color);
      seg.addPixelColorXY((cols - 1) - i, (rows - 1) - j, color);
    }
  }
  if (seg.check3) seg.blur(16, cols * rows < 100);
};

Effects2D[166] = function sunRadiation(seg) {
  // By: ldirko, modified by: Andrew Tuline
  // https://editor.soulmatelights.com/gallery/599-sun-radiation
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();
  if (cols === 0 || rows === 0) return;

  const bw = cols + 2;

  const t = Math.floor(m.getNow() / 4) >>> 0;

  const someVal = (seg.speed >> 2) & 0xFF;

  const bump = new Uint8Array(bw * (rows + 2));
  let index = 0;
  for (let j = 0; j < rows + 2; j++) {
    for (let i = 0; i < bw; i++) {
      const noise = m.inoise8(i * someVal, j * someVal, t);
      const signed16 = (noise - 127) >> 2;
      bump[index++] = signed16 & 0xFF;
    }
  }

  let yindex = bw + 1;
  let vly = -(Math.floor(rows / 2) + 1);
  for (let y = 0; y < rows; y++) {
    ++vly;
    let vlx = -(Math.floor(cols / 2) + 1);
    for (let x = 0; x < cols; x++) {
      ++vlx;
      const nx = bump[x + yindex + 1] - bump[x + yindex - 1];
      const ny = bump[x + yindex + bw] - bump[x + yindex - bw];

      const rawX = vlx * 7 - nx;
      const rawY = vly * 7 - ny;
      const i8x = ((rawX & 0xFF) << 24) >> 24;
      const i8y = ((rawY & 0xFF) << 24) >> 24;
      const difx = Math.abs(i8x);
      const dify = Math.abs(i8y);

      let temp = difx * difx + dify * dify;
      let col = 255 - ((temp / 8) | 0);
      if (col < 0) col = 0;

      const divisor = 3.0 - seg.intensity / 128.0;
      const heatVal = Math.min(255, Math.max(0, Math.floor(col / divisor)));
      seg.setPixelColorXY(x, y, m.HeatColor(heatVal));
    }
    yindex += bw;
  }
};

// Based on the Adafruit GFX 5x7 font (BSD License, see NOTICE).
const _scrollFont5x7 = {
  ' ': [0x00,0x00,0x00,0x00,0x00], '!': [0x00,0x00,0x5F,0x00,0x00],
  '"': [0x00,0x07,0x00,0x07,0x00], '#': [0x14,0x7F,0x14,0x7F,0x14],
  '$': [0x24,0x2A,0x7F,0x2A,0x12], '%': [0x23,0x13,0x08,0x64,0x62],
  '&': [0x36,0x49,0x55,0x22,0x50], "'": [0x00,0x05,0x03,0x00,0x00],
  '(': [0x00,0x1C,0x22,0x41,0x00], ')': [0x00,0x41,0x22,0x1C,0x00],
  '*': [0x14,0x08,0x3E,0x08,0x14], '+': [0x08,0x08,0x3E,0x08,0x08],
  ',': [0x00,0x50,0x30,0x00,0x00], '-': [0x08,0x08,0x08,0x08,0x08],
  '.': [0x00,0x60,0x60,0x00,0x00], '/': [0x20,0x10,0x08,0x04,0x02],
  '0': [0x3E,0x51,0x49,0x45,0x3E], '1': [0x00,0x42,0x7F,0x40,0x00],
  '2': [0x42,0x61,0x51,0x49,0x46], '3': [0x21,0x41,0x45,0x4B,0x31],
  '4': [0x18,0x14,0x12,0x7F,0x10], '5': [0x27,0x45,0x45,0x45,0x39],
  '6': [0x3C,0x4A,0x49,0x49,0x30], '7': [0x01,0x71,0x09,0x05,0x03],
  '8': [0x36,0x49,0x49,0x49,0x36], '9': [0x06,0x49,0x49,0x29,0x1E],
  ':': [0x00,0x36,0x36,0x00,0x00], ';': [0x00,0x56,0x36,0x00,0x00],
  '<': [0x08,0x14,0x22,0x41,0x00], '=': [0x14,0x14,0x14,0x14,0x14],
  '>': [0x00,0x41,0x22,0x14,0x08], '?': [0x02,0x01,0x51,0x09,0x06],
  '@': [0x32,0x49,0x79,0x41,0x3E],
  'A': [0x7E,0x11,0x11,0x11,0x7E], 'B': [0x7F,0x49,0x49,0x49,0x36],
  'C': [0x3E,0x41,0x41,0x41,0x22], 'D': [0x7F,0x41,0x41,0x22,0x1C],
  'E': [0x7F,0x49,0x49,0x49,0x41], 'F': [0x7F,0x09,0x09,0x09,0x01],
  'G': [0x3E,0x41,0x49,0x49,0x7A], 'H': [0x7F,0x08,0x08,0x08,0x7F],
  'I': [0x00,0x41,0x7F,0x41,0x00], 'J': [0x20,0x40,0x41,0x3F,0x01],
  'K': [0x7F,0x08,0x14,0x22,0x41], 'L': [0x7F,0x40,0x40,0x40,0x40],
  'M': [0x7F,0x02,0x0C,0x02,0x7F], 'N': [0x7F,0x04,0x08,0x10,0x7F],
  'O': [0x3E,0x41,0x41,0x41,0x3E], 'P': [0x7F,0x09,0x09,0x09,0x06],
  'Q': [0x3E,0x41,0x51,0x21,0x5E], 'R': [0x7F,0x09,0x19,0x29,0x46],
  'S': [0x46,0x49,0x49,0x49,0x31], 'T': [0x01,0x01,0x7F,0x01,0x01],
  'U': [0x3F,0x40,0x40,0x40,0x3F], 'V': [0x1F,0x20,0x40,0x20,0x1F],
  'W': [0x3F,0x40,0x38,0x40,0x3F], 'X': [0x63,0x14,0x08,0x14,0x63],
  'Y': [0x07,0x08,0x70,0x08,0x07], 'Z': [0x61,0x51,0x49,0x45,0x43],
  'a': [0x20,0x54,0x54,0x54,0x78], 'b': [0x7F,0x48,0x44,0x44,0x38],
  'c': [0x38,0x44,0x44,0x44,0x20], 'd': [0x38,0x44,0x44,0x48,0x7F],
  'e': [0x38,0x54,0x54,0x54,0x18], 'f': [0x08,0x7E,0x09,0x01,0x02],
  'g': [0x0C,0x52,0x52,0x52,0x3E], 'h': [0x7F,0x08,0x04,0x04,0x78],
  'i': [0x00,0x44,0x7D,0x40,0x00], 'j': [0x20,0x40,0x44,0x3D,0x00],
  'k': [0x7F,0x10,0x28,0x44,0x00], 'l': [0x00,0x41,0x7F,0x40,0x00],
  'm': [0x7C,0x04,0x18,0x04,0x78], 'n': [0x7C,0x08,0x04,0x04,0x78],
  'o': [0x38,0x44,0x44,0x44,0x38], 'p': [0x7C,0x14,0x14,0x14,0x08],
  'q': [0x08,0x14,0x14,0x18,0x7C], 'r': [0x7C,0x08,0x04,0x04,0x08],
  's': [0x48,0x54,0x54,0x54,0x20], 't': [0x04,0x3F,0x44,0x40,0x20],
  'u': [0x3C,0x40,0x40,0x20,0x7C], 'v': [0x1C,0x20,0x40,0x20,0x1C],
  'w': [0x3C,0x40,0x30,0x40,0x3C], 'x': [0x44,0x28,0x10,0x28,0x44],
  'y': [0x0C,0x50,0x50,0x50,0x3C], 'z': [0x44,0x64,0x54,0x4C,0x44],
};

function _wledDateTimeText() {
  const d = new Date();
  const mon = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getMonth()];
  const two = (v) => (v < 10 ? '0' : '') + v;
  return mon + ' ' + d.getDate() + ', ' + d.getFullYear() + ' ' + d.getHours() + ':' + two(d.getMinutes()) + ':' + two(d.getSeconds());
}

Effects2D[122] = function scrollingText(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  const text = seg._scrollText || _wledDateTimeText();
  const fontHeight = 7;
  const letterSpacing = 1;
  const glyphWidth = 5;

  let totalTextWidth = text.length * (glyphWidth + letterSpacing) - letterSpacing;

  let yoffset = m.map(seg.intensity, 0, 255, -Math.floor(rows / 2), Math.floor(rows / 2));

  if (totalTextWidth <= cols) {
    const speed = m.map(seg.speed, 0, 255, 5000, 1000);
    const frac = (m.getNow() % speed) + 1;
    if (seg.intensity === 255) {
      yoffset = Math.floor((2 * frac * rows) / speed) - rows;
    } else if (seg.intensity === 0) {
      yoffset = rows - Math.floor((2 * frac * rows) / speed);
    }
  }

  if ((seg.step || 0) < m.getNow()) {
    if (seg.check3) {
      if (!seg.aux0) seg.aux0 = totalTextWidth + cols - 1;
      else seg.aux0--;
    } else {
      seg.aux0 = ((seg.aux0 || 0) + 1) % (totalTextWidth + cols);
    }
    seg.aux1 = (((seg.aux1 || 0) + 1) & 0xFF);
    seg.step = m.getNow() + m.map(seg.speed, 0, 255, 250, 50);
  }

  seg.fade_out(255 - (seg.custom1 >> 4));
  let col1 = seg.color_from_palette(seg.aux1 || 0, false, false, 0);
  let col2 = 0;

  if (seg.check1) {
    if (seg.palette === 0) {
      col1 = seg.SEGCOLOR(0);
      col2 = seg.SEGCOLOR(2);
    }
  } else {
    col2 = col1;
  }

  let currentXOffset = 0;
  for (let c = 0; c < text.length; c++) {
    const ch = text[c];
    const glyphData = _scrollFont5x7[ch] || _scrollFont5x7[' '];
    const drawX = cols - (seg.aux0 || 0) + currentXOffset;
    if (drawX >= cols) break;
    const advance = glyphWidth + letterSpacing;

    if (drawX + advance < 0) {
      currentXOffset += advance;
      continue;
    }

    const drawY = yoffset + Math.floor((rows - fontHeight) / 2);

    for (let gx = 0; gx < glyphWidth; gx++) {
      const colBits = glyphData[gx];
      const sx = drawX + gx;
      if (sx < 0 || sx >= cols) continue;
      for (let gy = 0; gy < fontHeight; gy++) {
        const sy = drawY + gy;
        if (sy < 0 || sy >= rows) continue;
        if ((colBits >> gy) & 1) {
          let pixelCol;
          if (col2 === 0 && col1 !== 0 && seg.check1 && seg.palette !== 0) {
            const palIdx = m.map(gy, 0, fontHeight - 1, 0, 255);
            pixelCol = seg.ColorFromPalette(palIdx);
          } else if (col1 !== col2 && col2 !== 0) {
            const blend = Math.floor(gy * 255 / Math.max(1, fontHeight - 1));
            pixelCol = m.color_blend(col1, col2, blend);
          } else {
            pixelCol = col1;
          }
          seg.setPixelColorXY(sx, sy, pixelCol);
        }
      }
    }
    currentXOffset += advance;
  }
};

const _akemi = [
  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,2,2,3,3,3,3,3,3,2,2,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,2,3,3,0,0,0,0,0,0,3,3,2,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,2,3,0,0,0,6,5,5,4,0,0,0,3,2,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,2,3,0,0,6,6,5,5,5,5,4,4,0,0,3,2,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,2,3,0,6,5,5,5,5,5,5,5,5,4,0,3,2,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,2,3,0,6,5,5,5,5,5,5,5,5,5,5,4,0,3,2,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,3,2,0,6,5,5,5,5,5,5,5,5,5,5,4,0,2,3,0,0,0,0,0,0,0,
  0,0,0,0,0,0,3,2,3,6,5,5,7,7,5,5,5,5,7,7,5,5,4,3,2,3,0,0,0,0,0,0,
  0,0,0,0,0,2,3,1,3,6,5,1,7,7,7,5,5,1,7,7,7,5,4,3,1,3,2,0,0,0,0,0,
  0,0,0,0,0,8,3,1,3,6,5,1,7,7,7,5,5,1,7,7,7,5,4,3,1,3,8,0,0,0,0,0,
  0,0,0,0,0,8,3,1,3,6,5,5,1,1,5,5,5,5,1,1,5,5,4,3,1,3,8,0,0,0,0,0,
  0,0,0,0,0,2,3,1,3,6,5,5,5,5,5,5,5,5,5,5,5,5,4,3,1,3,2,0,0,0,0,0,
  0,0,0,0,0,0,3,2,3,6,5,5,5,5,5,5,5,5,5,5,5,5,4,3,2,3,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,6,5,5,5,5,5,7,7,5,5,5,5,5,4,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,5,4,0,0,0,0,0,0,0,0,0,
  1,0,0,0,0,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,5,4,0,0,0,0,0,0,0,0,2,
  0,2,2,2,0,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,5,4,0,0,0,0,0,2,2,2,0,
  0,0,0,3,2,0,0,0,6,5,4,4,4,4,4,4,4,4,4,4,4,4,4,4,0,0,0,2,2,0,0,0,
  0,0,0,3,2,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,5,5,5,4,0,0,0,2,3,0,0,0,
  0,0,0,0,3,2,0,0,0,0,3,3,0,3,3,0,0,3,3,0,3,3,0,0,0,0,2,2,0,0,0,0,
  0,0,0,0,3,2,0,0,0,0,3,2,0,3,2,0,0,3,2,0,3,2,0,0,0,0,2,3,0,0,0,0,
  0,0,0,0,0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0,0,2,3,0,0,0,0,0,
  0,0,0,0,0,3,2,2,2,2,0,0,0,3,2,0,0,3,2,0,0,0,3,2,2,2,3,0,0,0,0,0,
  0,0,0,0,0,0,3,3,3,0,0,0,0,3,2,0,0,3,2,0,0,0,0,3,3,3,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,0,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
];

Effects2D[186] = function akemi(seg) {
  const m = M2D();
  const cols = seg.vWidth();
  const rows = seg.vHeight();

  let counter = ((m.getNow() * ((seg.speed >> 2) + 2)) & 0xFFFF) >>> 0;
  counter = counter >> 8;

  const lightFactor  = 0.15;
  const normalFactor = 0.4;

  const fftResult = new Uint8Array(16);
  for (let i = 0; i < 16; i++) {
    fftResult[i] = seg.getFFT(i);
  }
  const base = fftResult[0] / 255.0;

  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      let color = 0;

      const faceColor32 = seg.color_wheel(counter);
      let faceR = (faceColor32 >> 16) & 0xFF;
      let faceG = (faceColor32 >> 8) & 0xFF;
      let faceB = faceColor32 & 0xFF;

      const seg1 = seg.SEGCOLOR(1);
      const armsBase = seg1 > 0 ? seg1 : 0xFFE0A0;
      let armsR = (armsBase >> 16) & 0xFF;
      let armsG = (armsBase >> 8) & 0xFF;
      let armsB = armsBase & 0xFF;

      const ak = _akemi[Math.floor((y * 32) / rows) * 32 + Math.floor((x * 32) / cols)];
      switch (ak) {
        case 3:
          color = m.colorPack(Math.floor(armsR * lightFactor), Math.floor(armsG * lightFactor), Math.floor(armsB * lightFactor));
          break;
        case 2:
          color = m.colorPack(Math.floor(armsR * normalFactor), Math.floor(armsG * normalFactor), Math.floor(armsB * normalFactor));
          break;
        case 1:
          color = armsBase;
          break;
        case 6:
          color = m.colorPack(Math.floor(faceR * lightFactor), Math.floor(faceG * lightFactor), Math.floor(faceB * lightFactor));
          break;
        case 5:
          color = m.colorPack(Math.floor(faceR * normalFactor), Math.floor(faceG * normalFactor), Math.floor(faceB * normalFactor));
          break;
        case 4:
          color = faceColor32;
          break;
        case 7: {
          const seg2 = seg.SEGCOLOR(2);
          color = seg2 > 0 ? seg2 : 0xFFFFFF;
          break;
        }
        case 8:
          if (base > 0.4) {
            color = m.colorPack(Math.floor(0xFF * base), Math.floor(0xA5 * base), Math.floor(0x00 * base));
          } else {
            color = armsBase;
          }
          break;
        default:
          color = 0;
          break;
      }

      if (seg.intensity > 128 && fftResult[0] > 128) {
        seg.setPixelColorXY(x, 0, 0);
        seg.setPixelColorXY(x, y + 1, color);
      } else {
        seg.setPixelColorXY(x, y, color);
      }
    }
  }

  const xMax = Math.floor(cols / 8);
  for (let x = 0; x < xMax; x++) {
    let band = m.map(x, 0, Math.max(xMax, 4), 0, 15);
    band = m.constrain(band, 0, 15);
    const barHeight = m.map(fftResult[band], 0, 255, 0, Math.floor(17 * rows / 32));
    const barColor = seg.color_from_palette(band * 35, false, false, 0);

    for (let y = 0; y < barHeight; y++) {
      seg.setPixelColorXY(x, Math.floor(rows / 2) - y, barColor);
      seg.setPixelColorXY(cols - 1 - x, Math.floor(rows / 2) - y, barColor);
    }
  }
};

if (typeof window !== 'undefined') {
  window.Effects2D = Effects2D;
}
