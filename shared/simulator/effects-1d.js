/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-04-19.
 */

'use strict';

const Effects1D = {};

const M = () => window.WLEDMath;

Effects1D[0] = function solid(seg) {
  seg.fill(seg.SEGCOLOR(0));
};

function _blink(seg, color1, color2, strobe, do_palette) {
  const m = M();
  const SEGLEN = seg.vLength();
  let cycleTime = (255 - seg.speed) * 20;
  let onTime = m.FRAMETIME;
  if (!strobe) onTime += ((cycleTime * seg.intensity) >> 8);
  cycleTime += m.FRAMETIME * 2;
  const it = Math.floor(m.getNow() / cycleTime);
  const rem = m.getNow() % cycleTime;

  let on = false;
  if (it !== seg.step
      || rem <= onTime) {
    on = true;
  }

  seg.step = it;

  const color = on ? color1 : color2;
  if (color === color1 && do_palette) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
    }
  } else {
    seg.fill(color);
  }
}

Effects1D[1] = function mode_blink(seg) {
  _blink(seg, seg.SEGCOLOR(0), seg.SEGCOLOR(1), false, true);
};

Effects1D[2] = function mode_breath(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  let v = 0;
  let counter = (m.getNow() * ((seg.speed >> 3) + 10)) & 0xFFFF;
  counter = (counter >> 2) + (counter >> 4);
  if (counter < 16384) {
    if (counter > 8192) counter = 8192 - (counter - 8192);
    v = Math.floor(m.sin16(counter) / 103);
  }

  const lum = (30 + v) & 0xFF;
  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i, true, false, 0), lum));
  }
};

function _color_wipe(seg, rev, useRandomColors) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const cycleTime = 750 + (255 - seg.speed) * 150;
  const perc = m.getNow() % cycleTime;
  let prog = Math.floor((perc * 65535) / cycleTime);
  let back = (prog > 32767);
  if (back) {
    prog -= 32767;
    if (seg.step === 0) seg.step = 1;
  } else {
    if (seg.step === 2) seg.step = 3;
  }

  if (useRandomColors) {
    if (seg.call === 0) {
      seg.aux0 = m.random8();
      seg.step = 3;
    }
    if (seg.step === 1) {
      seg.aux1 = m.get_random_wheel_index(seg.aux0);
      seg.step = 2;
    }
    if (seg.step === 3) {
      seg.aux0 = m.get_random_wheel_index(seg.aux1);
      seg.step = 0;
    }
  }

  let ledIndex = (prog * SEGLEN) >> 15;
  let rem = ((prog * SEGLEN) * 2) & 0xFFFF;
  rem = Math.floor(rem / (seg.intensity + 1));
  if (rem > 255) rem = 255;

  const col1 = useRandomColors ? seg.color_wheel(seg.aux1) : seg.SEGCOLOR(1);
  for (let i = 0; i < SEGLEN; i++) {
    const index = (rev && back) ? SEGLEN - 1 - i : i;
    const col0 = useRandomColors ? seg.color_wheel(seg.aux0) : seg.color_from_palette(index, true, false, 0);

    if (i < ledIndex) {
      seg.setPixelColor(index, back ? col1 : col0);
    } else {
      seg.setPixelColor(index, back ? col0 : col1);
      if (i === ledIndex) seg.setPixelColor(index, m.color_blend(back ? col0 : col1, back ? col1 : col0, rem));
    }
  }
}

Effects1D[3] = function mode_color_wipe(seg) {
  _color_wipe(seg, false, false);
};

Effects1D[5] = function randomColors(seg) {
  const m = M();
  const cycleTime = 200 + (255 - seg.speed) * 50;
  const it = Math.floor(m.getNow() / cycleTime);
  const rem = m.getNow() % cycleTime;
  const fadedur = (cycleTime * seg.intensity) >> 8;

  let fade = 255;
  if (fadedur) {
    fade = Math.floor((rem * 255) / fadedur);
    if (fade > 255) fade = 255;
  }

  if (seg.call === 0) {
    seg.aux0 = m.random8();
    seg.step = 2;
  }
  if (it !== seg.step) {
    seg.aux1 = seg.aux0;
    seg.aux0 = m.get_random_wheel_index(seg.aux0);
    seg.step = it;
  }

  seg.fill(m.color_blend(seg.color_wheel(seg.aux1), seg.color_wheel(seg.aux0), fade));
};

Effects1D[8] = function colorloop(seg) {
  const m = M();
  let counter = (m.getNow() * ((seg.speed >> 2) + 2)) & 0xFFFF;
  counter = counter >> 8;

  if (seg.intensity < 128) {
    seg.fill(m.color_blend(seg.color_wheel(counter), 0xFFFFFF, 128 - seg.intensity));
  } else {
    seg.fill(seg.color_wheel(counter));
  }
};

Effects1D[9] = function mode_rainbow_cycle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  let counter = (m.getNow() * ((seg.speed >> 2) + 2)) & 0xFFFF;
  counter = counter >> 8;

  for (let i = 0; i < SEGLEN; i++) {
    const index = (Math.floor(i * (16 << Math.floor(seg.intensity / 29)) / SEGLEN) + counter) & 0xFF;
    seg.setPixelColor(i, seg.color_wheel(index));
  }
};

function _scan(seg, dual) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const cycleTime = 750 + (255 - seg.speed) * 150;
  const perc = m.getNow() % cycleTime;
  const prog = Math.floor((perc * 65535) / cycleTime);
  const size = 1 + ((seg.intensity * SEGLEN) >> 9);
  const ledIndex = (prog * ((SEGLEN * 2) - size * 2)) >> 16;

  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  let led_offset = ledIndex - (SEGLEN - size);
  led_offset = Math.abs(led_offset);

  if (dual) {
    for (let j = led_offset; j < led_offset + size; j++) {
      const i2 = SEGLEN - 1 - j;
      seg.setPixelColor(i2, seg.color_from_palette(i2, true, false, seg.SEGCOLOR(2) ? 2 : 0));
    }
  }

  for (let j = led_offset; j < led_offset + size; j++) {
    seg.setPixelColor(j, seg.color_from_palette(j, true, false, 0));
  }
}

Effects1D[10] = function mode_scan(seg) {
  _scan(seg, false);
};

function _running(seg, color1, color2, theatre) {
  const m = M();
  const SEGLEN = seg.vLength();
  const width = (theatre ? 3 : 1) + (seg.intensity >> 4);
  const cycleTime = 50 + (255 - seg.speed);
  const it = Math.floor(m.getNow() / cycleTime);
  const usePalette = (color1 === seg.SEGCOLOR(0));

  for (let i = 0; i < SEGLEN; i++) {
    let col = color2;
    let c1 = color1;
    if (usePalette) c1 = seg.color_from_palette(i, true, false, 0);
    if (theatre) {
      if ((i % width) === seg.aux0) col = c1;
    } else {
      const pos = (i % (width << 1));
      if ((pos < seg.aux0 - width) || ((pos >= seg.aux0) && (pos < seg.aux0 + width))) col = c1;
    }
    seg.setPixelColor(i, col);
  }

  if (it !== seg.step) {
    seg.aux0 = (seg.aux0 + 1) % (theatre ? width : (width << 1));
    seg.step = it;
  }
}

Effects1D[13] = function mode_theater_chase(seg) {
  _running(seg, seg.SEGCOLOR(0), seg.SEGCOLOR(1), true);
};

function _running_base(seg, saw, dual) {
  const m = M();
  const SEGLEN = seg.vLength();
  const x_scale = seg.intensity >> 2;
  const counter = (m.getNow() * seg.speed) >> 9;

  function sin_gap(v) {
    if (v & 0x100) return 0;
    return m.sin8((v + 192) & 0xFF);
  }

  for (let i = 0; i < SEGLEN; i++) {
    let a = (i * x_scale - counter) & 0xFFFFFFFF;
    if (saw) {
      a &= 0xFF;
      if (a < 16) {
        a = 192 + a * 8;
      } else {
        a = m.map(a, 16, 255, 64, 192);
      }
      a = 255 - a;
    }
    const s = dual ? sin_gap(a) : m.sin8(a & 0xFF);
    let ca = m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i, true, false, 0), s);
    if (dual) {
      const b = ((SEGLEN - 1 - i) * x_scale - counter) & 0xFFFFFFFF;
      const t = sin_gap(b);
      const cb = m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i, true, false, 2), t);
      ca = m.color_blend(ca, cb, 127);
    }
    seg.setPixelColor(i, ca);
  }
}

Effects1D[15] = function mode_running_lights(seg) {
  _running_base(seg, false, false);
};

Effects1D[17] = function mode_twinkle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  seg.fade_out(224);

  const cycleTime = 20 + (255 - seg.speed) * 5;
  const it = Math.floor(m.getNow() / cycleTime);
  if (it !== seg.step) {
    const maxOn = m.map(seg.intensity, 0, 255, 1, SEGLEN);
    if (seg.aux0 >= maxOn) {
      seg.aux0 = 0;
      seg.aux1 = m.jsRandom16();
    }
    seg.aux0++;
    seg.step = it;
  }

  let PRNG16 = seg.aux1;
  for (let i = 0; i < seg.aux0; i++) {
    PRNG16 = ((PRNG16 * 2053) + 13849) & 0xFFFF;
    const p = (SEGLEN * PRNG16) >>> 0;
    const j = p >> 16;
    seg.setPixelColor(j, seg.color_from_palette(j, true, false, 0));
  }
};

Effects1D[38] = function aurora(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const W_MAX_COUNT = 20;
  const W_MAX_SPEED = 6;
  const W_WIDTH_FACTOR = 6;
  const AW_SHIFT = 16;
  const AW_SCALE = (1 << AW_SHIFT);

  const waveCount = m.map(seg.intensity, 0, 255, 2, W_MAX_COUNT);
  seg.aux1 = waveCount;

  if (seg.call === 0 || !seg._auroraWaves || seg._auroraWaves.length !== waveCount) {
    seg._auroraWaves = [];
    for (let i = 0; i < waveCount; i++) {
      seg._auroraWaves.push({ alive: false, age: 0, ttl: 0, center: 0, width: 1,
        basealpha: 0, speed_factor: 0, goingleft: false,
        basecolor: 0, ageFactor_cached: 0, wave_start: 0, wave_end: 0 });
    }
  }

  const waves = seg._auroraWaves;

  function initWave(w) {
    w.ttl = 500 + m.jsRandom16(1001);
    w.basecolor = seg.color_from_palette(m.jsRandom8(), false, false, m.jsRandom8(0, 3));
    w.basealpha = Math.floor(m.jsRandom8(60, 100) * AW_SCALE / 100);
    w.age = 0;
    w.width = m.jsRandom16(Math.floor(SEGLEN / 20), Math.max(1, Math.floor(SEGLEN / W_WIDTH_FACTOR))) + 1;
    w.center = Math.floor((m.jsRandom8(101) * AW_SCALE / 100)) * SEGLEN;
    w.goingleft = (m.jsRandom8() & 1) === 1;
    w.speed_factor = Math.floor((m.jsRandom8(10, 31) * W_MAX_SPEED * AW_SCALE) / (100 * 255));
    w.alive = true;
  }

  function updateCachedValues(w) {
    const half_ttl = w.ttl >> 1;
    if (half_ttl === 0) { w.ageFactor_cached = 0; return; }
    if (w.age < half_ttl) {
      w.ageFactor_cached = Math.floor((w.age * AW_SCALE) / half_ttl);
    } else {
      w.ageFactor_cached = Math.floor(((w.ttl - w.age) * AW_SCALE) / half_ttl);
    }
    if (w.ageFactor_cached >= AW_SCALE) w.ageFactor_cached = AW_SCALE - 1;

    const center_led = w.center >> AW_SHIFT;
    w.wave_start = center_led - w.width;
    w.wave_end = center_led + w.width;
  }

  function getColorForLED(w, ledIndex) {
    if (ledIndex < w.wave_start || ledIndex > w.wave_end) return 0;
    let offset = (ledIndex << AW_SHIFT) - w.center;
    if (offset < 0) offset = -offset;
    const offsetFactor = Math.floor(offset / w.width);
    if (offsetFactor > AW_SCALE) return 0;
    let brightness_factor = AW_SCALE - offsetFactor;
    brightness_factor = (brightness_factor * w.ageFactor_cached) >>> AW_SHIFT;
    brightness_factor = (brightness_factor * w.basealpha) >>> AW_SHIFT;

    const br = m.colorR(w.basecolor);
    const bg = m.colorG(w.basecolor);
    const bb = m.colorB(w.basecolor);
    return m.colorPack(
      (br * brightness_factor) >> AW_SHIFT,
      (bg * brightness_factor) >> AW_SHIFT,
      (bb * brightness_factor) >> AW_SHIFT
    );
  }

  function updateWave(w) {
    const step = w.speed_factor * seg.speed;
    w.center += w.goingleft ? -step : step;
    w.age++;

    if (w.age > w.ttl) {
      w.alive = false;
    } else {
      const width_scaled = w.width << AW_SHIFT;
      const segment_length_scaled = SEGLEN << AW_SHIFT;
      if (w.goingleft) {
        if (w.center < -width_scaled) w.alive = false;
      } else {
        if (w.center > segment_length_scaled + width_scaled) w.alive = false;
      }
    }
  }

  for (let i = 0; i < waveCount; i++) {
    updateWave(waves[i]);
    if (!waves[i].alive) {
      initWave(waves[i]);
    }
    updateCachedValues(waves[i]);
  }

  let backlight = 0;
  if (seg.SEGCOLOR(0)) backlight++;
  if (seg.SEGCOLOR(1)) backlight++;
  if (seg.SEGCOLOR(2)) backlight++;
  backlight = m.gamma8inv(backlight);

  for (let i = 0; i < SEGLEN; i++) {
    let mr = backlight, mg = backlight, mb = backlight;
    for (let j = 0; j < waveCount; j++) {
      const rgb = getColorForLED(waves[j], i);
      mr = Math.min(255, mr + m.colorR(rgb));
      mg = Math.min(255, mg + m.colorG(rgb));
      mb = Math.min(255, mb + m.colorB(rgb));
    }
    seg.setPixelColor(i, m.colorPack(mr, mg, mb));
  }
};

Effects1D[40] = function scanner(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const speed = m.FRAMETIME * m.map(seg.speed, 0, 255, 96, 2);
  const pixelsPerCall = Math.floor(SEGLEN / speed);

  const frames = pixelsPerCall > 0 ? 1 : Math.floor(speed / SEGLEN);
  const fadesPerPixel = frames + 1;
  const trailFrac = seg.intensity / 255;
  const targetPixels = Math.max(3, trailFrac * SEGLEN * 3.0);
  const nOff = targetPixels * fadesPerPixel;
  const decayScale = Math.exp(-5.541 / nOff);

  const bufLen = seg.pixels.length;
  if (!seg._scannerFade || seg._scannerFade.length !== bufLen * 3) {
    seg._scannerFade = new Float64Array(bufLen * 3);
    for (let i = 0; i < bufLen; i++) {
      const c = seg.pixels[i];
      seg._scannerFade[i * 3]     = m.colorR(c);
      seg._scannerFade[i * 3 + 1] = m.colorG(c);
      seg._scannerFade[i * 3 + 2] = m.colorB(c);
    }
  }
  const fb = seg._scannerFade;

  const bg = seg.colors[1];
  const bgR = m.colorR(bg);
  const bgG = m.colorG(bg);
  const bgB = m.colorB(bg);
  const buf = seg.pixels;

  for (let i = 0; i < bufLen; i++) {
    const fi = i * 3;
    let r = fb[fi], g = fb[fi + 1], b = fb[fi + 2];

    if (r === bgR && g === bgG && b === bgB) continue;

    r = bgR + (r - bgR) * decayScale;
    g = bgG + (g - bgG) * decayScale;
    b = bgB + (b - bgB) * decayScale;

    if (Math.abs(r - bgR) < 0.5 && Math.abs(g - bgG) < 0.5 && Math.abs(b - bgB) < 0.5) {
      r = bgR; g = bgG; b = bgB;
    }

    fb[fi] = r; fb[fi + 1] = g; fb[fi + 2] = b;

    buf[i] = m.colorPack(
      Math.max(0, Math.min(255, Math.round(r))),
      Math.max(0, Math.min(255, Math.round(g))),
      Math.max(0, Math.min(255, Math.round(b)))
    );
  }

  if (seg.step > m.getNow()) return;

  let index = (seg.aux1 || 0) + pixelsPerCall;
  if (pixelsPerCall === 0) {
    if (seg.step < frames) { seg.step++; return; }
    seg.step = 0;
    index = (seg.aux1 || 0) + 1;
  }

  if (index > SEGLEN) {
    seg.aux0 = seg.aux0 ? 0 : 1;
    seg.aux1 = 0;
    if (seg.aux0 || seg.check2) seg.step = m.getNow() + seg.custom1 * 25;
    else seg.step = 0;
  } else {
    for (let i = (seg.aux1 || 0); i < index; i++) {
      const j = seg.aux0 ? i : SEGLEN - 1 - i;
      const c = seg.color_from_palette(j, true, false, 0);
      seg.setPixelColor(j, c);
      fb[j * 3]     = m.colorR(c);
      fb[j * 3 + 1] = m.colorG(c);
      fb[j * 3 + 2] = m.colorB(c);
      if (seg.check1) {
        const mirror = SEGLEN - 1 - j;
        const mc = seg.SEGCOLOR(2) ? seg.SEGCOLOR(2) : c;
        seg.setPixelColor(mirror, mc);
        fb[mirror * 3]     = m.colorR(mc);
        fb[mirror * 3 + 1] = m.colorG(mc);
        fb[mirror * 3 + 2] = m.colorB(mc);
      }
    }
    seg.aux1 = index;
  }
};

Effects1D[41] = function mode_comet(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const counter = (m.getNow() * ((seg.speed >> 2) + 1)) & 0xFFFF;
  let index = (counter * SEGLEN) >> 16;
  if (seg.call === 0) seg.aux0 = index;

  seg.fade_out(seg.intensity);

  seg.setPixelColor(index, seg.color_from_palette(index, true, false, 0));
  if (index > seg.aux0) {
    for (let i = seg.aux0; i < index; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
    }
  } else if (index < seg.aux0 && index < 10) {
    for (let i = 0; i < index; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
    }
  }
  seg.aux0 = index++;
};

Effects1D[42] = function fireworks(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const is2D = seg._is2D;
  const width = is2D ? seg.vWidth() : SEGLEN;
  const height = is2D ? seg.vHeight() : 1;

  if (seg.call === 0) {
    seg.aux0 = 0xFFFF;
    seg.aux1 = 0xFFFF;
  }
  seg.fade_out(128);

  if (!seg.step) {
    const valid1 = (seg.aux0 < width * height);
    const valid2 = (seg.aux1 < width * height);
    let sv1 = 0, sv2 = 0;
    if (valid1) {
      if (is2D) { sv1 = seg.getPixelColorXY(seg.aux0 % width, Math.floor(seg.aux0 / width)); }
      else { sv1 = seg.getPixelColor(seg.aux0); }
    }
    if (valid2) {
      if (is2D) { sv2 = seg.getPixelColorXY(seg.aux1 % width, Math.floor(seg.aux1 / width)); }
      else { sv2 = seg.getPixelColor(seg.aux1); }
    }
    seg.blur(16);
    if (valid1) {
      if (is2D) seg.setPixelColorXY(seg.aux0 % width, Math.floor(seg.aux0 / width), sv1);
      else seg.setPixelColor(seg.aux0, sv1);
    }
    if (valid2) {
      if (is2D) seg.setPixelColorXY(seg.aux1 % width, Math.floor(seg.aux1 / width), sv2);
      else seg.setPixelColor(seg.aux1, sv2);
    }
  }

  const numSparks = Math.max(1, Math.floor(width / 20));
  for (let i = 0; i < numSparks; i++) {
    if (m.jsRandom8(129 - (seg.intensity >> 1)) === 0) {
      const index = m.jsRandom16(width * height);
      const x = index % width;
      const y = Math.floor(index / width);
      const col = seg.color_from_palette(m.jsRandom8(), false, false, 0);
      if (is2D) seg.setPixelColorXY(x, y, col);
      else seg.setPixelColor(index, col);
      seg.aux1 = seg.aux0;
      seg.aux0 = index;
    }
  }
};

Effects1D[43] = function rain(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const is2D = seg._is2D;
  const width = is2D ? seg.vWidth() : SEGLEN;
  const height = is2D ? seg.vHeight() : 1;

  seg.step = (seg.step || 0) + m.FRAMETIME;
  if (seg.call && seg.step > m.SPEED_FORMULA_L(seg.speed, SEGLEN)) {
    seg.step = 1;
    if (is2D) {
      seg.move(6, 1, true);
      seg.aux0 = (seg.aux0 % width) + (Math.floor(seg.aux0 / width) + 1) * width;
      seg.aux1 = (seg.aux1 % width) + (Math.floor(seg.aux1 / width) + 1) * width;
    } else {
      const ctemp = seg.getPixelColor(0);
      for (let i = 0; i < SEGLEN - 1; i++) {
        seg.setPixelColor(i, seg.getPixelColor(i + 1));
      }
      seg.setPixelColor(SEGLEN - 1, ctemp);
      seg.aux0 = ((seg.aux0 || 0) + 1) & 0xFFFF;
      seg.aux1 = ((seg.aux1 || 0) + 1) & 0xFFFF;
    }
    if (seg.aux0 === 0) seg.aux0 = 0xFFFF;
    if (seg.aux1 === 0) seg.aux1 = 0xFFFF;
    if (seg.aux0 >= width * height) seg.aux0 = 0;
    if (seg.aux1 >= width * height) seg.aux1 = 0;
  }

  if (seg.call === 0) {
    seg.aux0 = 0xFFFF;
    seg.aux1 = 0xFFFF;
  }
  seg.fade_out(128);

  if (!seg.step) {
    let x, y;
    const valid1 = (seg.aux0 < width * height);
    const valid2 = (seg.aux1 < width * height);
    let sv1 = 0, sv2 = 0;
    if (valid1) {
      if (is2D) { x = seg.aux0 % width; y = Math.floor(seg.aux0 / width); sv1 = seg.getPixelColorXY(x, y); }
      else sv1 = seg.getPixelColor(seg.aux0);
    }
    if (valid2) {
      if (is2D) { x = seg.aux1 % width; y = Math.floor(seg.aux1 / width); sv2 = seg.getPixelColorXY(x, y); }
      else sv2 = seg.getPixelColor(seg.aux1);
    }
    seg.blur(16);
    if (valid1) {
      if (is2D) seg.setPixelColorXY(seg.aux0 % width, Math.floor(seg.aux0 / width), sv1);
      else seg.setPixelColor(seg.aux0, sv1);
    }
    if (valid2) {
      if (is2D) seg.setPixelColorXY(seg.aux1 % width, Math.floor(seg.aux1 / width), sv2);
      else seg.setPixelColor(seg.aux1, sv2);
    }
  }

  const numSparks = Math.max(1, Math.floor(width / 20));
  for (let i = 0; i < numSparks; i++) {
    if (m.jsRandom8(129 - (seg.intensity >> 1)) === 0) {
      const index = m.jsRandom16(width * height);
      const x = index % width;
      const y = Math.floor(index / width);
      const col = seg.color_from_palette(m.jsRandom8(), false, false, 0);
      if (is2D) seg.setPixelColorXY(x, y, col);
      else seg.setPixelColor(index, col);
      seg.aux1 = seg.aux0;
      seg.aux0 = index;
    }
  }
};

Effects1D[45] = function mode_fire_flicker(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const cycleTime = 40 + (255 - seg.speed);
  const it = Math.floor(m.getNow() / cycleTime);
  if (seg.step === it) return;

  const w = m.colorW(seg.SEGCOLOR(0));
  const r = m.colorR(seg.SEGCOLOR(0));
  const g = m.colorG(seg.SEGCOLOR(0));
  const b = m.colorB(seg.SEGCOLOR(0));
  let lum = (seg.palette === 0) ? Math.max(w, Math.max(r, Math.max(g, b))) : 255;
  lum = Math.floor(lum / (Math.floor((256 - seg.intensity) / 16) + 1));
  for (let i = 0; i < SEGLEN; i++) {
    const flicker = m.jsRandom8(lum);
    if (seg.palette === 0) {
      seg.setPixelColor(i, m.colorPack(
        Math.max(r - flicker, 0),
        Math.max(g - flicker, 0),
        Math.max(b - flicker, 0),
        Math.max(w - flicker, 0)
      ));
    } else {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0, 255 - flicker));
    }
  }

  seg.step = it;
};

Effects1D[63] = function pride2015(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const duration = 10 + seg.speed;
  let sPseudotime = seg.step || 0;
  let sHue16 = seg.aux0 || 0;

  const sat8 = m.beatsin88(87, 220, 250);
  const brightdepth = m.beatsin88(341, 96, 224);
  const brightnessthetainc16 = m.beatsin88(203, 25 * 256, 40 * 256);
  const msmultiplier = m.beatsin88(147, 23, 60);

  const hueinc16 = m.beatsin88(113, 1, 3000);

  sPseudotime = (sPseudotime + duration * msmultiplier) >>> 0;
  sHue16 = (sHue16 + duration * m.beatsin88(400, 5, 9)) & 0xFFFF;
  let brightnesstheta16 = sPseudotime;

  let hue16 = sHue16;

  for (let i = 0; i < SEGLEN; i++) {
    hue16 = (hue16 + hueinc16) & 0xFFFF;
    const hue8 = (hue16 >> 8) & 0xFF;

    brightnesstheta16 = (brightnesstheta16 + brightnessthetainc16) & 0xFFFF;
    const b16 = (m.sin16(brightnesstheta16) + 32768) & 0xFFFF;
    const bri16 = Math.floor((b16 * b16) / 65536);
    let bri8 = Math.floor((bri16 * brightdepth) / 65536);
    bri8 += (255 - brightdepth);
    bri8 = Math.min(255, Math.max(0, bri8));

    const newcolor = m.hsv2rgb(hue8, sat8, bri8);
    const gammaColor = m.gamma32inv(newcolor);
    seg.blendPixelColor(i, gammaColor, 64);
  }

  seg.step = sPseudotime;
  seg.aux0 = sHue16;
};

Effects1D[64] = function juggle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  seg.fadeToBlackBy(192 - Math.floor(3 * seg.intensity / 4));
  let dothue = 0;
  for (let i = 0; i < 8; i++) {
    const index = m.beatsin88((16 + seg.speed) * (i + 7), 0, SEGLEN - 1);
    const existing = seg.getPixelColor(index);

    let col;
    if (seg.palette === 0) {
      col = m.hsv2rgb(dothue, 220, 255);
    } else {
      col = seg.color_from_palette(dothue, false, false, 0, 255);
    }
    const r = Math.min(255, m.colorR(existing) | m.colorR(col));
    const g = Math.min(255, m.colorG(existing) | m.colorG(col));
    const b = Math.min(255, m.colorB(existing) | m.colorB(col));
    seg.setPixelColor(index, m.colorPack(r, g, b));
    dothue += 32;
  }
};

Effects1D[65] = function mode_palette(seg) {
  const m = M();
  const is2D = seg._is2D;
  const cols = is2D ? seg.vWidth() : seg.vLength();
  const rows = is2D ? seg.vHeight() : 1;
  const inputShift = seg.speed;
  const inputSize = seg.intensity;
  const inputRotation = seg.custom1;
  const inputAnimateShift = seg.check1;
  const inputAnimateRotation = seg.check2;
  const inputAssumeSquare = seg.check3;

  const M_PI_256 = Math.PI / 256.0;
  const M_TWOPI = Math.PI * 2.0;
  let theta;
  if (!inputAnimateRotation) {
    theta = (inputRotation + 128) * M_PI_256;
  } else {
    theta = ((m.getNow() * ((inputRotation >> 4) + 1)) & 0xFFFF) * (M_TWOPI / 0xFFFF);
  }
  const sinTheta = Math.sin(theta);
  const cosTheta = Math.cos(theta);

  const maxX = Math.max(1, cols - 1);
  const maxY = Math.max(1, rows - 1);
  const maxXIn = inputAssumeSquare ? maxX : 1.0;
  const maxYIn = inputAssumeSquare ? maxY : 1.0;
  const maxXOut = !inputAssumeSquare ? maxX : 1.0;
  const maxYOut = !inputAssumeSquare ? maxY : 1.0;
  const centerX = maxXOut / 2.0;
  const centerY = maxYOut / 2.0;

  const scale = Math.abs(sinTheta) + (Math.abs(cosTheta) * maxYOut / maxXOut);

  const paletteOffset = (!inputAnimateShift)
    ? inputShift
    : (((m.getNow() * ((inputShift >> 3) + 1)) & 0xFFFF) >> 8);

  const yFrom = is2D ? 0 : 0;
  const yTo = is2D ? maxY : 0;
  for (let y = yFrom; y <= yTo; y++) {
    const ytCosTheta = (cosTheta * (y - centerY * maxYIn)) / (maxYIn * scale);
    for (let x = 0; x < cols; x++) {
      const xtSinTheta = (sinTheta * (x - centerX * maxXIn)) / (maxXIn * scale);
      const sourceX = xtSinTheta + ytCosTheta + centerX;
      let colorIndex = Math.trunc((Math.min(Math.max(sourceX, 0), maxXOut) * 255) / maxXOut);

      if (inputSize <= 128) {
        colorIndex = Math.floor((colorIndex * inputSize) / 128);
      } else {
        colorIndex = Math.floor(((inputSize - 112) * colorIndex) / 16);
      }

      colorIndex -= paletteOffset;

      if (is2D) {
        seg.setPixelColorXY(x, y, seg.color_wheel(colorIndex & 0xFF));
      } else {
        seg.setPixelColor(x, seg.color_wheel(colorIndex & 0xFF));
      }
    }
  }
};

Effects1D[66] = function fire2012(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (!seg.data || seg._dataLen < SEGLEN) {
    seg.allocateData(SEGLEN);
    seg.data.fill(0);
  }

  const heat = seg.data;
  const it = (m.getNow() >> 5) >>> 0;
  const ignition = Math.max(3, Math.floor(SEGLEN / 10));

  for (let i = 0; i < SEGLEN; i++) {
    let cool;
    if (it !== seg._fire2012Step) {
      cool = m.jsRandom8(Math.floor(((20 + Math.floor(seg.speed / 3)) * 16) / SEGLEN) + 2);
    } else {
      cool = m.jsRandom8(4);
    }
    const minTemp = (i < ignition) ? Math.floor((ignition - i) / 4) + 16 : 0;
    let temp = m.qsub8(heat[i], cool);
    heat[i] = temp < minTemp ? minTemp : temp;
  }

  if (it !== seg._fire2012Step) {
    for (let k = SEGLEN - 1; k > 1; k--) {
      heat[k] = Math.floor((heat[k - 1] + (heat[k - 2] << 1)) / 3);
    }

    if (m.jsRandom8() <= seg.intensity) {
      const y = m.jsRandom8(ignition);
      const boost = Math.floor((17 + seg.custom3) * (ignition - Math.floor(y / 2)) / ignition);
      heat[y] = m.qadd8(heat[y], m.jsRandom8(96 + 2 * boost, 207 + boost));
    }
  }

  for (let j = 0; j < SEGLEN; j++) {
    seg.setPixelColor(j, seg.ColorFromPalette(Math.min(heat[j], 240), 255, 0));
  }

  if (it !== seg._fire2012Step) {
    seg._fire2012Step = it;
  }
};

Effects1D[67] = function colorwaves(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const duration = 10 + seg.speed;
  let sPseudotime = seg.step || 0;
  let sHue16 = seg.aux0 || 0;

  const sat8 = 255;
  const brightdepth = m.beatsin88(341, 96, 224);
  const brightnessthetainc16 = m.beatsin88(203, 25 * 256, 40 * 256);
  const msmultiplier = m.beatsin88(147, 23, 60);

  const hueinc16 = Math.floor(m.beatsin88(113, 60, 300) * seg.intensity * 10 / 255);

  sPseudotime = (sPseudotime + duration * msmultiplier) >>> 0;
  sHue16 = (sHue16 + duration * m.beatsin88(400, 5, 9)) & 0xFFFF;
  let brightnesstheta16 = sPseudotime;

  let hue16 = sHue16;

  for (let i = 0; i < SEGLEN; i++) {
    hue16 = (hue16 + hueinc16) & 0xFFFF;
    const h16_128 = (hue16 >> 7) & 0x1FF;
    const hue8 = (h16_128 & 0x100) ? ((255 - (h16_128 >> 1)) & 0xFF) : ((h16_128 >> 1) & 0xFF);

    brightnesstheta16 = (brightnesstheta16 + brightnessthetainc16) & 0xFFFF;
    const b16 = (m.sin16(brightnesstheta16) + 32768) & 0xFFFF;
    const bri16 = Math.floor((b16 * b16) / 65536);
    let bri8 = Math.floor((bri16 * brightdepth) / 65536);
    bri8 += (255 - brightdepth);
    bri8 = Math.min(255, Math.max(0, bri8));

    seg.blendPixelColor(i, seg.color_from_palette(hue8, false, false, 0, bri8), 128);
  }

  seg.step = sPseudotime;
  seg.aux0 = sHue16;
};

Effects1D[68] = function mode_bpm(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const stp = Math.floor(m.getNow() / 20) & 0xFF;
  const beat = m.beatsin8(seg.speed, 64, 255);

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette((stp + (i * 2)) & 0xFF, false, false, 0, (beat - stp + (i * 10)) & 0xFF));
  }
};

Effects1D[74] = function mode_colortwinkle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const dataSize = (SEGLEN + 7) >> 3;
  if (!seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
  }

  if (m.getNow() - seg.step < m.FRAMETIME_FIXED) return;
  seg.step = m.getNow();

  const fadeUpAmount = 8 + (seg.speed >> 2);
  const fadeDownAmount = 8 + (seg.speed >> 3);

  for (let i = 0; i < SEGLEN; i++) {
    const cur = seg.getPixelColor(i);
    const index = i >> 3;
    const bitNum = i & 0x07;
    const fadeUp = (seg.data[index] >> bitNum) & 1;

    if (fadeUp) {
      const incrementalColor = m.color_fade(cur, fadeUpAmount, true);
      let col = m.color_add(cur, incrementalColor);

      if (m.colorR(col) === 255 || m.colorG(col) === 255 || m.colorB(col) === 255) {
        seg.data[index] &= ~(1 << bitNum);
      }

      if (col === cur) {
        col = m.color_add(col, col);
      }
      seg.setPixelColor(i, col);
    } else {
      const col = m.color_fade(cur, 255 - fadeDownAmount, false);
      seg.setPixelColor(i, col);
    }
  }

  for (let j = 0; j <= Math.floor(SEGLEN / 50); j++) {
    if (m.jsRandom8() <= seg.intensity) {
      for (let times = 0; times < 5; times++) {
        const i = m.jsRandom16(SEGLEN);
        if (seg.getPixelColor(i) === 0) {
          const index = i >> 3;
          const bitNum = i & 0x07;
          seg.data[index] |= (1 << bitNum);
          seg.setPixelColor(i, seg.ColorFromPalette(m.jsRandom8(), 64, 0));
          break;
        }
      }
    }
  }
};

Effects1D[80] = function twinklefox(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const cat = false;

  let PRNG16 = 11337;
  function nextPRNG() {
    PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
    return PRNG16;
  }

  if (seg.speed > 100) seg.aux0 = 3 + ((255 - seg.speed) >> 3);
  else seg.aux0 = 22 + ((100 - seg.speed) >> 1);

  let bg = seg.SEGCOLOR(1);
  let bglight = m.getAverageLight(bg);
  if (bglight > 64) {
    bg = m.color_fade(bg, 16, true);
  } else if (bglight > 16) {
    bg = m.color_fade(bg, 64, true);
  } else {
    bg = m.color_fade(bg, 86, true);
  }
  bg = m.gamma32inv(bg);
  const backgroundBrightness = m.getAverageLight(bg);

  for (let i = 0; i < SEGLEN; i++) {
    nextPRNG();
    const myclockoffset16 = PRNG16;
    nextPRNG();
    const myspeedmultiplierQ5_3 = ((((PRNG16 & 0xFF) >> 4) + (PRNG16 & 0x0F)) & 0x0F) + 0x08;
    const myclock30 = (((m.getNow() * myspeedmultiplierQ5_3) >> 3) + myclockoffset16) >>> 0;
    const myunique8 = (PRNG16 >> 8) & 0xFF;

    const ms = myclock30;
    const salt = myunique8;
    const ticks = Math.floor(ms / Math.max(1, seg.aux0));
    const fastcycle8 = ticks & 0xFF;
    let slowcycle16 = ((ticks >> 8) + salt) & 0xFFFF;
    slowcycle16 = (slowcycle16 + m.sin8(slowcycle16 & 0xFF)) & 0xFFFF;
    slowcycle16 = ((slowcycle16 * 2053) + 1384) & 0xFFFF;
    const slowcycle8 = ((slowcycle16 & 0xFF) + ((slowcycle16 >> 8) & 0xFF)) & 0xFF;

    const twinkleDensity = (seg.intensity >> 5) + 1;

    let bright = 0;
    if ((Math.floor((slowcycle8 & 0x0E) / 2)) < twinkleDensity) {
      const ph = fastcycle8;
      if (ph < 86) {
        bright = ph * 3;
      } else {
        const ph2 = ph - 86;
        bright = 255 - (ph2 + (ph2 >> 1));
      }
    }

    const hue = (slowcycle8 - salt) & 0xFF;
    let c;
    if (bright > 0) {
      c = seg.ColorFromPalette(hue, bright, 0);
      if (!seg.check1) {
        if (fastcycle8 >= 128) {
          const cooling = (fastcycle8 - 128) >> 4;
          let cr = m.colorR(c), cg = m.colorG(c), cb = m.colorB(c);
          cg = m.qsub8(cg, cooling);
          cb = m.qsub8(cb, cooling * 2);
          c = m.colorPack(cr, cg, cb);
        }
      }
    } else {
      c = 0;
    }

    const cbright = m.getAverageLight(c);
    const deltabright = cbright - backgroundBrightness;
    if (deltabright >= 32 || bg === 0) {
      seg.setPixelColor(i, c);
    } else if (deltabright > 0) {
      seg.setPixelColor(i, m.color_blend(bg, c, Math.min(255, deltabright * 8)));
    } else {
      seg.setPixelColor(i, bg);
    }
  }
};

function _spots_base(seg, threshold) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  const maxZones = SEGLEN >> 2;
  const zones = 1 + ((seg.intensity * maxZones) >> 8);
  const zoneLen = Math.floor(SEGLEN / zones);
  const offset = (SEGLEN - zones * zoneLen) >> 1;

  for (let z = 0; z < zones; z++) {
    const pos = offset + z * zoneLen;
    for (let i = 0; i < zoneLen; i++) {
      const wave = m.triwave16(Math.floor((i * 0xFFFF) / zoneLen));
      if (wave > threshold) {
        const index = pos + i;
        const s = Math.floor((wave - threshold) * 255 / (0xFFFF - threshold));
        seg.setPixelColor(index, m.color_blend(seg.color_from_palette(index, true, false, 0), seg.SEGCOLOR(1), 255 - s));
      }
    }
  }
}

Effects1D[85] = function mode_spots(seg) {
  _spots_base(seg, (255 - seg.speed) << 8);
};

Effects1D[87] = function glitter(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.check2) {
    let counter = 0;
    if (seg.speed !== 0) {
      counter = (m.getNow() * ((seg.speed >> 3) + 1)) & 0xFFFF;
      counter = counter >> 8;
    }

    const noWrap = (seg.speed === 0);
    for (let i = 0; i < SEGLEN; i++) {
      let colorIndex = Math.floor(i * 255 / SEGLEN) - counter;
      if (noWrap) colorIndex = m.map(colorIndex & 0xFF, 0, 255, 0, 240);
      seg.setPixelColor(i, seg.color_from_palette(colorIndex & 0xFF, false, true, 255));
    }
  }

  const glitterCol = seg.SEGCOLOR(2) ? seg.SEGCOLOR(2) : 0xFFFFFFFF;
  if (seg.intensity > m.random8()) seg.setPixelColor(m.random16(SEGLEN), glitterCol);
};

Effects1D._candle = function(seg, multi) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (multi && SEGLEN > 1) {
    const dataSize = 4 + Math.max(1, SEGLEN - 1) * 3;
    if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
      seg.allocateData(dataSize);
    }
  } else {
    if (seg.call === 0 || !seg.data || seg.data.length < 4) {
      seg.allocateData(4);
    }
  }

  const buf = seg.data.buffer;
  const lastcallView = new DataView(buf, 0);
  const lastcall = lastcallView.getUint32(0, true);

  if (m.getNow() - lastcall < m.FRAMETIME_FIXED) return;
  lastcallView.setUint32(0, m.getNow(), true);

  const valrange = seg.intensity;
  const rndval = valrange >> 1;

  let speedFactor = 4;
  if (seg.speed > 252) speedFactor = 1;
  else if (seg.speed > 99) speedFactor = 2;
  else if (seg.speed > 49) speedFactor = 3;

  const numCandles = multi ? SEGLEN : 1;

  for (let i = 0; i < numCandles; i++) {
    let d = 0;
    let s, s_target, fadeStep;

    if (i === 0) {
      s = seg.aux0 || 0;
      s_target = seg.aux1 || 0;
      fadeStep = seg.step || 0;
    } else {
      d = (i - 1) * 3;
      s = seg.data[4 + d];
      s_target = seg.data[4 + d + 1];
      fadeStep = seg.data[4 + d + 2];
    }

    if (fadeStep === 0) {
      s = 128;
      s_target = 130 + m.random8(4);
      fadeStep = 1;
    }

    let newTarget = false;
    if (s_target > s) {
      s = m.qadd8(s, fadeStep);
      if (s >= s_target) newTarget = true;
    } else {
      s = m.qsub8(s, fadeStep);
      if (s <= s_target) newTarget = true;
    }

    if (newTarget) {
      s_target = m.random8(rndval) + m.random8(rndval);
      if (s_target < (rndval >> 1)) s_target = (rndval >> 1) + m.random8(rndval);
      const offset = 255 - valrange;
      s_target += offset;

      const dif = (s_target > s) ? s_target - s : s - s_target;
      fadeStep = dif >> speedFactor;
      if (fadeStep === 0) fadeStep = 1;
    }

    if (i > 0) {
      seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i, true, false, 0), s & 0xFF));
      seg.data[4 + d] = s & 0xFF;
      seg.data[4 + d + 1] = s_target & 0xFF;
      seg.data[4 + d + 2] = fadeStep & 0xFF;
    } else {
      for (let j = 0; j < SEGLEN; j++) {
        seg.setPixelColor(j, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(j, true, false, 0), s & 0xFF));
      }
      seg.aux0 = s;
      seg.aux1 = s_target;
      seg.step = fadeStep;
    }
  }
};

Effects1D[88] = function candle(seg) {
  Effects1D._candle(seg, false);
};

Effects1D[91] = function bouncingBalls(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const maxNumBalls = 16;
  const ballSize = 16;

  if (seg.call === 0 || !seg.data || seg.data.length < maxNumBalls * ballSize) {
    seg.allocateData(maxNumBalls * ballSize);
  }

  if (!seg.check2) seg.fill(seg.SEGCOLOR(2) ? 0 : seg.SEGCOLOR(1));

  const numBalls = Math.floor(seg.intensity * (maxNumBalls - 1) / 255) + 1;
  const gravity = -9.81;
  const hasCol2 = seg.SEGCOLOR(2) !== 0;
  const time = m.getNow();

  const buf = seg.data.buffer;

  if (seg.call === 0) {
    for (let i = 0; i < maxNumBalls; i++) {
      const dv = new DataView(buf, i * ballSize);
      dv.setFloat64(0, time, true);
      dv.setFloat32(8, 0, true);
      dv.setFloat32(12, 0, true);
    }
  }

  for (let i = 0; i < numBalls; i++) {
    const dv = new DataView(buf, i * ballSize);
    const lastBounceTime = dv.getFloat64(0, true);
    let impactVelocity = dv.getFloat32(8, true);
    let height;

    const timeSinceLastBounce = (time - lastBounceTime) / ((255 - seg.speed) / 64 + 1);
    const timeSec = timeSinceLastBounce / 1000.0;
    height = (0.5 * gravity * timeSec + impactVelocity) * timeSec;

    if (height <= 0.0) {
      height = 0.0;
      const dampening = 0.9 - i / (numBalls * numBalls);
      impactVelocity = dampening * impactVelocity;
      dv.setFloat64(0, time, true);

      if (impactVelocity < 0.015) {
        const impactVelocityStart = Math.sqrt(2.0 * 9.81) * m.random8(5, 11) / 10.0;
        impactVelocity = impactVelocityStart;
      }
      dv.setFloat32(8, impactVelocity, true);
    } else if (height > 1.0) {
      continue;
    }

    dv.setFloat32(12, height, true);

    let color = seg.SEGCOLOR(0);
    if (seg.palette) {
      color = m.color_wheel(Math.floor(i * (256 / Math.max(numBalls, 8))));
    } else if (hasCol2) {
      color = seg.SEGCOLOR(i % 3);
    }

    const pos = Math.round(height * (SEGLEN - 1));
    if (pos >= 0 && pos < SEGLEN) seg.setPixelColor(pos, color);
  }
};

Effects1D[92] = function sinelon(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  seg.fade_out(seg.intensity);

  const pos = m.beatsin16(Math.floor(seg.speed / 10), 0, SEGLEN - 1);
  if (seg.call === 0) seg.aux0 = pos;
  const color1 = seg.color_from_palette(pos, true, false, 0);
  seg.setPixelColor(pos, color1);

  if (seg.aux0 !== pos) {
    if (seg.aux0 < pos) {
      for (let i = seg.aux0; i < pos; i++) {
        seg.setPixelColor(i, color1);
      }
    } else {
      for (let i = seg.aux0; i > pos; i--) {
        seg.setPixelColor(i, color1);
      }
    }
    seg.aux0 = pos;
  }
};

Effects1D[97] = function plasma(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0) {
    seg.aux0 = m.random8(0, 2);
  }
  const thisPhase = m.beatsin8_t(6 + seg.aux0, -64, 64);
  const thatPhase = m.beatsin8_t(7 + seg.aux0, -64, 64);

  for (let i = 0; i < SEGLEN; i++) {
    const colorIndex = Math.floor(m.cubicwave8((i * (2 + 3 * (seg.speed >> 5)) + thisPhase) & 0xFF) / 2)
                      + Math.floor(m.cos8_t((i * (1 + 2 * (seg.speed >> 5)) + thatPhase) & 0xFF) / 2);
    const thisBright = m.qsub8(colorIndex, m.beatsin8_t(7, 0, 128 - (seg.intensity >> 1)));
    seg.setPixelColor(i, seg.color_from_palette(colorIndex, false, false, 0, thisBright));
  }
};

Effects1D[100] = function heartbeat(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const bpm = 40 + (seg.speed >> 3);
  const msPerBeat = Math.floor(60000 / bpm);
  const secondBeat = Math.floor(msPerBeat / 3);
  let bri_lower = seg.aux1 || 0;
  const beatTimer = m.getNow() - (seg.step || 0);

  bri_lower = Math.floor(bri_lower * 2042 / (2048 + seg.intensity));
  seg.aux1 = bri_lower;

  if (beatTimer > secondBeat && !seg.aux0) {
    seg.aux1 = 65535;
    seg.aux0 = 1;
  }
  if (beatTimer > msPerBeat) {
    seg.aux1 = 65535;
    seg.aux0 = 0;
    seg.step = m.getNow();
  }

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, m.color_blend(seg.color_from_palette(i, true, false, 0), seg.SEGCOLOR(1), 255 - (seg.aux1 >> 8)));
  }
};

Effects1D[101] = function pacifica(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg._pacificaPals) {
    function hexToRGB(hex) {
      return [(hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF];
    }
    const p1data = [0x000507, 0x000409, 0x00030B, 0x00030D, 0x000210, 0x000212, 0x000114, 0x000117,
                    0x000019, 0x00001C, 0x000026, 0x000031, 0x00003B, 0x000046, 0x14554B, 0x28AA50];
    const p2data = [0x000507, 0x000409, 0x00030B, 0x00030D, 0x000210, 0x000212, 0x000114, 0x000117,
                    0x000019, 0x00001C, 0x000026, 0x000031, 0x00003B, 0x000046, 0x0C5F52, 0x19BE5F];
    const p3data = [0x000208, 0x00030E, 0x000514, 0x00061A, 0x000820, 0x000927, 0x000B2D, 0x000C33,
                    0x000E39, 0x001040, 0x001450, 0x001860, 0x001C70, 0x002080, 0x1040BF, 0x2060FF];
    seg._pacificaPals = [
      window.CRGBPalette16.fromEntries(p1data.map(hexToRGB)),
      window.CRGBPalette16.fromEntries(p2data.map(hexToRGB)),
      window.CRGBPalette16.fromEntries(p3data.map(hexToRGB))
    ];
  }

  let pal1 = seg._pacificaPals[0], pal2 = seg._pacificaPals[1], pal3 = seg._pacificaPals[2];
  if (seg.palette) {
    pal1 = null; pal2 = null; pal3 = null;
  }

  function pacifica_one_layer(pal, pixelIdx, cistart, wavescale, bri, ioff) {
    let ci = cistart & 0xFFFF;
    let waveangle = ioff & 0xFFFF;
    const wavescale_half = ((wavescale >> 1) + 20) & 0xFFFF;

    waveangle = (waveangle + ((120 + seg.intensity) * pixelIdx)) & 0xFFFF;
    const s16 = (m.sin16(waveangle) + 32768) & 0xFFFF;
    const cs = (m.scale16(s16, wavescale_half) + wavescale_half) & 0xFFFF;
    ci = (ci + cs * pixelIdx) & 0xFFFF;
    const sindex16 = (m.sin16(ci) + 32768) & 0xFFFF;
    const sindex8 = m.scale16(sindex16, 240) & 0xFF;

    if (pal) {
      return pal.colorAt(sindex8, bri, 1);
    } else {
      return seg.color_from_palette(sindex8, false, false, 0, bri);
    }
  }

  let sCIStart1 = seg.aux0 || 0;
  let sCIStart2 = seg.aux1 || 0;
  let sCIStart3 = (seg.step || 0) & 0xFFFF;
  let sCIStart4 = ((seg.step || 0) >> 16) & 0xFFFF;

  const deltams = (m.FRAMETIME >> 2) + ((m.FRAMETIME * seg.speed) >> 7);
  const deltat = (m.getNow() >> 2) + ((m.getNow() * seg.speed) >> 7);

  const nowOld = m.getNow();
  m.setNow(deltat & 0xFFFFFFFF);

  const speedfactor1 = m.beatsin16(3, 179, 269);
  const speedfactor2 = m.beatsin16(4, 179, 269);
  const deltams1 = Math.floor((deltams * speedfactor1) / 256);
  const deltams2 = Math.floor((deltams * speedfactor2) / 256);
  const deltams21 = Math.floor((deltams1 + deltams2) / 2);

  sCIStart1 = (sCIStart1 + deltams1 * m.beatsin88(1011, 10, 13)) & 0xFFFF;
  sCIStart2 = (sCIStart2 - deltams21 * m.beatsin88(777, 8, 11)) & 0xFFFF;
  sCIStart3 = (sCIStart3 - deltams1 * m.beatsin88(501, 5, 7)) & 0xFFFF;
  sCIStart4 = (sCIStart4 - deltams2 * m.beatsin88(257, 4, 6)) & 0xFFFF;

  seg.aux0 = sCIStart1 & 0xFFFF;
  seg.aux1 = sCIStart2 & 0xFFFF;
  seg.step = ((sCIStart4 & 0xFFFF) << 16) | (sCIStart3 & 0xFFFF);

  const basethreshold = m.beatsin8(9, 55, 65);
  let wave = m.beat8(7);

  for (let i = 0; i < SEGLEN; i++) {
    let cr = 2, cg = 6, cb = 10;

    const l1 = pacifica_one_layer(pal1, i, sCIStart1, m.beatsin16(3, 11 * 256, 14 * 256), m.beatsin8(10, 70, 130), (0 - m.beat16(301)) & 0xFFFF);
    const l2 = pacifica_one_layer(pal2, i, sCIStart2, m.beatsin16(4, 6 * 256, 9 * 256), m.beatsin8(17, 40, 80), m.beat16(401));
    const l3 = pacifica_one_layer(pal3, i, sCIStart3, 6 * 256, m.beatsin8(9, 10, 38), (0 - m.beat16(503)) & 0xFFFF);
    const l4 = pacifica_one_layer(pal3, i, sCIStart4, 5 * 256, m.beatsin8(8, 10, 28), m.beat16(601));

    for (const lc of [l1, l2, l3, l4]) {
      cr = Math.min(255, cr + m.colorR(lc));
      cg = Math.min(255, cg + m.colorG(lc));
      cb = Math.min(255, cb + m.colorB(lc));
    }

    const threshold = m.scale8(m.sin8(wave), 20) + basethreshold;
    wave = (wave + 7) & 0xFF;
    const avg = (cr + cg + cb) >> 2;
    if (avg > threshold) {
      const overage = avg - threshold;
      const overage2 = m.qadd8(overage, overage);
      cr = Math.min(255, cr + overage);
      cg = Math.min(255, cg + overage2);
      cb = Math.min(255, cb + m.qadd8(overage2, overage2));
    }

    cb = m.scale8(cb, 145);
    cg = m.scale8(cg, 200);
    cr = cr | 2;
    cg = cg | 5;
    cb = cb | 7;

    seg.setPixelColor(i, m.colorPack(cr, cg, cb));
  }

  m.setNow(nowOld);
};

Effects1D[108] = function sine(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const colorIndex = Math.floor(m.getNow() / 32);
  seg.step = (seg.step || 0) + Math.floor(seg.speed / 16);
  const freq = Math.floor(seg.intensity / 4);

  for (let i = 0; i < SEGLEN; i++) {
    const pixBri = m.cubicwave8((i * freq + seg.step) & 0xFF);
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(Math.floor(i * colorIndex / 255) & 0xFF, false, false, 0), pixBri));
  }
};

Effects1D[110] = function flow(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let counter = 0;
  if (seg.speed !== 0) {
    counter = m.getNow() * ((seg.speed >> 2) + 1);
    counter = counter >> 8;
  }

  let maxZones = Math.floor(SEGLEN / 6);
  let zones = (seg.intensity * maxZones) >> 8;
  if (zones & 0x01) zones++;
  if (zones < 2) zones = 2;
  let zoneLen = Math.floor(SEGLEN / zones);
  const requiredZones = Math.ceil(SEGLEN / zoneLen);
  zones = requiredZones + 2;
  const offset = Math.trunc((SEGLEN - zones * zoneLen) / 2);

  for (let z = 0; z < zones; z++) {
    const pos = offset + z * zoneLen;
    for (let i = 0; i < zoneLen; i++) {
      const colorIndex = (Math.floor(i * 255 / zoneLen) - counter) & 0xFF;
      let led = (z & 0x01) ? i : (zoneLen - 1) - i;
      if (seg.reverse) led = (zoneLen - 1) - led;
      seg.setPixelColor(pos + led, seg.color_from_palette(colorIndex, false, true, 255));
    }
  }
};

Effects1D[4] = function mode_color_wipe_random(seg) {
  _color_wipe(seg, false, true);
};

Effects1D[6] = function mode_color_sweep(seg) {
  _color_wipe(seg, true, false);
};

Effects1D[7] = function dynamic(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.data || seg._dataLen < SEGLEN) {
    seg.allocateData(SEGLEN);
  }

  if (seg.call === 0) {
    for (let i = 0; i < SEGLEN; i++) seg.data[i] = m.random8();
  }

  const cycleTime = 50 + (255 - seg.speed) * 15;
  const it = Math.floor(m.getNow() / cycleTime);
  if (it !== seg.step && seg.speed !== 0) {
    for (let i = 0; i < SEGLEN; i++) {
      if (m.random8() <= seg.intensity) seg.data[i] = m.random8();
    }
    seg.step = it;
  }

  if (seg.check1) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.blendPixelColor(i, seg.color_wheel(seg.data[i]), 16);
    }
  } else {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_wheel(seg.data[i]));
    }
  }
};

Effects1D[11] = function mode_dual_scan(seg) {
  _scan(seg, true);
};

Effects1D[12] = function fade(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const counter = (m.getNow() * ((seg.speed >> 3) + 10)) >>> 0;
  const lum = m.triwave16(counter & 0xFFFF) >> 8;

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i, true, false, 0), lum));
  }
};

Effects1D[14] = function mode_theater_chase_rainbow(seg) {
  const m = M();
  _running(seg, seg.color_wheel(seg.step & 0xFF), seg.SEGCOLOR(1), true);
};

Effects1D[16] = function mode_saw(seg) {
  _running_base(seg, true, false);
};

Effects1D[20] = function sparkle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.check2) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 1));
    }
  }

  const cycleTime = 10 + (255 - seg.speed) * 2;
  const it = Math.floor(m.getNow() / cycleTime);

  if (it !== seg.step) {
    seg.aux0 = m.random16(SEGLEN);
    seg.step = it;
  }

  seg.setPixelColor(seg.aux0, seg.SEGCOLOR(0));
};

Effects1D[23] = function mode_strobe(seg) {
  _blink(seg, seg.SEGCOLOR(0), seg.SEGCOLOR(1), true, true);
};

Effects1D[27] = function android(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.data || seg._dataLen < 4) {
    seg.allocateData(4);
    seg.data[0] = 0; seg.data[1] = 0; seg.data[2] = 0; seg.data[3] = 0;
  }

  let counter = ((seg.data[0] << 24) | (seg.data[1] << 16) | (seg.data[2] << 8) | seg.data[3]) >>> 0;

  let size = seg.aux1 >> 1;
  let shrinking = seg.aux1 & 0x01;

  if (m.getNow() >= seg.step) {
    seg.step = m.getNow() + 3 + Math.floor((8 * (255 - seg.speed)) / SEGLEN);

    if (size > Math.floor((seg.intensity * SEGLEN) / 255)) {
      shrinking = 1;
    } else if (size < 2) {
      shrinking = 0;
    }

    if (!shrinking) {
      if ((counter % 3) === 1) {
        seg.aux0++;
      } else {
        size++;
      }
    } else {
      seg.aux0++;
      if ((counter % 3) !== 1) {
        size--;
      }
    }

    seg.aux1 = (size << 1) | shrinking;
    counter = (counter + 1) >>> 0;

    seg.data[0] = (counter >> 24) & 0xFF;
    seg.data[1] = (counter >> 16) & 0xFF;
    seg.data[2] = (counter >> 8) & 0xFF;
    seg.data[3] = counter & 0xFF;

    if (seg.aux0 >= SEGLEN) seg.aux0 = 0;
  }

  const start = seg.aux0;
  const end = (seg.aux0 + size) % SEGLEN;

  for (let i = 0; i < SEGLEN; i++) {
    if ((start < end && i >= start && i < end) || (start >= end && (i >= start || i < end))) {
      seg.setPixelColor(i, seg.SEGCOLOR(0));
    } else {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 1));
    }
  }
};

function _chase(seg, color1, color2, color3, do_palette) {
  const m = M();
  const SEGLEN = seg.vLength();

  let counter = (m.getNow() * ((seg.speed >> 2) + 1)) & 0xFFFF;
  let a = (counter * SEGLEN) >> 16;

  const chase_random = (seg.mode === 29);
  if (chase_random) {
    if (a < seg.step) {
      seg.aux1 = seg.aux0;
      seg.aux0 = m.get_random_wheel_index(seg.aux0);
    }
    color1 = seg.color_wheel(seg.aux0);
  }
  seg.step = a;

  const size = 1 + ((seg.intensity * SEGLEN) >> 10);

  let b = a + size;
  if (b > SEGLEN) b -= SEGLEN;
  let c = b + size;
  if (c > SEGLEN) c -= SEGLEN;

  if (do_palette) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 1));
    }
  } else {
    seg.fill(color1);
  }

  if (chase_random) {
    color1 = seg.color_wheel(seg.aux1);
    for (let i = a; i < SEGLEN; i++) {
      seg.setPixelColor(i, color1);
    }
  }

  if (a < b) {
    for (let i = a; i < b; i++) seg.setPixelColor(i, color2);
  } else {
    for (let i = a; i < SEGLEN; i++) seg.setPixelColor(i, color2);
    for (let i = 0; i < b; i++) seg.setPixelColor(i, color2);
  }

  if (b < c) {
    for (let i = b; i < c; i++) seg.setPixelColor(i, color3);
  } else {
    for (let i = b; i < SEGLEN; i++) seg.setPixelColor(i, color3);
    for (let i = 0; i < c; i++) seg.setPixelColor(i, color3);
  }
}

Effects1D[28] = function chase(seg) {
  _chase(seg, seg.SEGCOLOR(1), seg.SEGCOLOR(2) ? seg.SEGCOLOR(2) : seg.SEGCOLOR(0), seg.SEGCOLOR(0), true);
};

Effects1D[34] = function colorful(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let numColors = 4;
  const cols = new Array(9).fill(0);
  cols[0] = 0x00FF0000; cols[1] = 0x00EEBB00; cols[2] = 0x0000EE00; cols[3] = 0x000077CC;

  if (seg.intensity > 160 || seg.palette) {
    if (!seg.palette) {
      numColors = 3;
      for (let i = 0; i < 3; i++) cols[i] = seg.SEGCOLOR(i);
    } else {
      let fac = 80;
      if (seg.palette === 52) { numColors = 5; fac = 61; }
      for (let i = 0; i < numColors; i++) {
        cols[i] = seg.color_from_palette(i * fac, false, true, 255);
      }
    }
  } else if (seg.intensity < 80) {
    cols[0] = 0x00FF8040; cols[1] = 0x00E5D241;
    cols[2] = 0x0077FF77; cols[3] = 0x0077F0F0;
  }
  for (let i = numColors; i < numColors * 2 - 1; i++) cols[i] = cols[i - numColors];

  const cycleTime = 50 + (8 * (255 - seg.speed));
  const it = Math.floor(m.getNow() / cycleTime);
  if (it !== seg.step) {
    if (seg.speed > 0) seg.aux0++;
    if (seg.aux0 >= numColors) seg.aux0 = 0;
    seg.step = it;
  }

  for (let i = 0; i < SEGLEN; i += numColors) {
    for (let j = 0; j < numColors; j++) {
      seg.setPixelColor(i + j, cols[seg.aux0 + j]);
    }
  }
};

Effects1D[37] = function mode_running_color(seg) {
  _running(seg, seg.SEGCOLOR(0), seg.SEGCOLOR(1), false);
};

Effects1D[44] = function mode_tetrix(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (seg.call === 0 || !seg._tetrixDrop) {
    seg._tetrixDrop = { pos: 0, speed: 0, col: 0, brick: 0, stack: 0, step: 0 };
  }
  const drop = seg._tetrixDrop;

  if (seg.call === 0) {
    drop.stack = 0;
    drop.step = m.getNow() + 2000;
    if (seg.check1) drop.col = 0;
  }

  if (drop.step === 0) {
    let speed = seg.speed ? seg.speed : m.jsRandom8(1, 255);
    speed = m.map(speed, 1, 255, 5000, 250);
    drop.speed = (SEGLEN * m.FRAMETIME) / speed;
    drop.pos   = SEGLEN;
    if (!seg.check1) drop.col = m.jsRandom8(0, 15) << 4;
    drop.step  = 1;
    drop.brick = (seg.intensity ? (seg.intensity >> 5) + 1 : m.jsRandom8(1, 5)) * (1 + (SEGLEN >> 6));
  }

  if (drop.step === 1) {
    if (m.jsRandom8() >> 6) {
      drop.step = 2;
    }
  }

  if (drop.step === 2) {
    if (drop.pos > drop.stack) {
      drop.pos -= drop.speed;
      if (drop.pos < drop.stack) drop.pos = drop.stack;
      for (let i = Math.floor(drop.pos); i < SEGLEN; i++) {
        const col = i < Math.floor(drop.pos) + drop.brick
          ? seg.color_from_palette(drop.col, false, false, 0)
          : seg.SEGCOLOR(1);
        seg.setPixelColor(i, col);
      }
    } else {
      drop.step = 0;
      drop.stack += drop.brick;
      if (drop.stack >= SEGLEN) drop.step = m.getNow() + 2000;
    }
  }

  if (drop.step > 2) {
    drop.brick = 0;
    if (drop.step > m.getNow()) {
      for (let i = 0; i < SEGLEN; i++) seg.blendPixelColor(i, seg.SEGCOLOR(1), 25);
    } else {
      drop.stack = 0;
      drop.step = 0;
      if (seg.check1) drop.col += 8;
    }
  }
};

function _gradient_base(seg, loading) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  let counter = (m.getNow() * ((seg.speed >> 2) + 1)) & 0xFFFF;
  let pp = (counter * SEGLEN) >> 16;
  if (seg.call === 0) pp = 0;

  const brd = seg.intensity >> 1;

  const p1 = pp - SEGLEN;
  const p2 = pp + SEGLEN;

  for (let i = 0; i < SEGLEN; i++) {
    let val;
    if (loading) {
      val = Math.abs(((i > pp) ? p2 : pp) - i);
    } else {
      val = Math.min(Math.abs(pp - i), Math.min(Math.abs(p1 - i), Math.abs(p2 - i)));
    }
    val = (brd > val) ? Math.floor((val * 255) / brd) : 255;
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(0), seg.color_from_palette(i, true, false, 1), val & 0xFF));
  }
}

Effects1D[46] = function mode_gradient(seg) {
  _gradient_base(seg, false);
};

Effects1D[49] = function mode_fairy(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const FLASHERS_PER_ZONE = 6;
  const MAX_SHIMMER = 92;

  let PRNG16 = 5100;
  for (let i = 0; i < SEGLEN; i++) {
    PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
    seg.setPixelColor(i, seg.color_from_palette(PRNG16 >> 8, false, false, 0));
  }

  if (seg.intensity === 0) return;
  const flasherDistance = Math.floor((255 - seg.intensity) / 28) + 1;
  const numFlashers = Math.floor(SEGLEN / flasherDistance) + 1;

  const dataSize = numFlashers * 4;
  if (!seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
  }

  function getFlasher(f) {
    const off = f * 4;
    return {
      stateStart: (seg.data[off] | (seg.data[off + 1] << 8)) & 0xFFFF,
      stateDur: seg.data[off + 2],
      stateOn: !!seg.data[off + 3]
    };
  }
  function setFlasher(f, fl) {
    const off = f * 4;
    seg.data[off] = fl.stateStart & 0xFF;
    seg.data[off + 1] = (fl.stateStart >> 8) & 0xFF;
    seg.data[off + 2] = fl.stateDur & 0xFF;
    seg.data[off + 3] = fl.stateOn ? 1 : 0;
  }

  const now16 = m.getNow() & 0xFFFF;

  let zones = Math.floor(numFlashers / FLASHERS_PER_ZONE);
  if (!zones) zones = 1;
  let flashersInZone = Math.floor(numFlashers / zones);
  const flasherBri = new Uint8Array(FLASHERS_PER_ZONE * 2 - 1);

  for (let z = 0; z < zones; z++) {
    let flasherBriSum = 0;
    const firstFlasher = z * flashersInZone;
    if (z === zones - 1) flashersInZone = numFlashers - (flashersInZone * (zones - 1));

    for (let f = firstFlasher; f < firstFlasher + flashersInZone; f++) {
      const fl = getFlasher(f);
      let stateTime = (now16 - fl.stateStart) & 0xFFFF;
      if (stateTime > fl.stateDur * 10) {
        fl.stateOn = !fl.stateOn;
        if (fl.stateOn) {
          fl.stateDur = 12 + m.jsRandom8(12 + ((255 - seg.speed) >> 2));
        } else {
          fl.stateDur = 20 + m.jsRandom8(6 + ((255 - seg.speed) >> 2));
        }
        fl.stateStart = now16;
        if (stateTime < 255) {
          fl.stateStart = (fl.stateStart - (255 - stateTime)) & 0xFFFF;
          fl.stateDur += 26 - Math.floor(stateTime / 10);
          stateTime = 255 - stateTime;
        } else {
          stateTime = 0;
        }
      }
      if (stateTime > 255) stateTime = 255;
      flasherBri[f - firstFlasher] = fl.stateOn ? stateTime : 255 - stateTime;
      flasherBriSum += flasherBri[f - firstFlasher];
      setFlasher(f, fl);
    }
    const avgFlasherBri = Math.floor(flasherBriSum / flashersInZone);
    const globalPeakBri = 255 - ((avgFlasherBri * MAX_SHIMMER) >> 8);

    for (let f = firstFlasher; f < firstFlasher + flashersInZone; f++) {
      const bri = Math.floor(flasherBri[f - firstFlasher] * globalPeakBri / 255);
      PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
      const flasherPos = f * flasherDistance;
      seg.setPixelColor(flasherPos, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(PRNG16 >> 8, false, false, 0), bri));
      for (let i = flasherPos + 1; i < flasherPos + flasherDistance && i < SEGLEN; i++) {
        PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
        seg.setPixelColor(i, seg.color_from_palette(PRNG16 >> 8, false, false, 0, globalPeakBri));
      }
    }
  }
};

Effects1D[50] = function mode_two_dots(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const delay = 1 + Math.floor((m.FRAMETIME << 3) / SEGLEN);
  const it = Math.floor(m.getNow() / m.map(seg.speed, 0, 255, delay << 4, delay));
  const offset = it % SEGLEN;
  let width = ((SEGLEN * (seg.intensity + 1)) >> 9);
  if (!width) width = 1;
  if (!seg.check2) seg.fill(seg.SEGCOLOR(2));
  const color1 = seg.SEGCOLOR(0);
  const color2 = (seg.SEGCOLOR(1) === seg.SEGCOLOR(2)) ? color1 : seg.SEGCOLOR(1);
  for (let i = 0; i < width; i++) {
    const indexR = (offset + i) % SEGLEN;
    const indexB = (offset + i + (SEGLEN >> 1)) % SEGLEN;
    seg.setPixelColor(indexR, color1);
    seg.setPixelColor(indexB, color2);
  }
};

Effects1D[51] = function mode_fairytwinkle(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const dataSize = SEGLEN * 4;
  if (!seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
  }

  const now16 = m.getNow() & 0xFFFF;
  let PRNG16 = 5100;

  const riseFallTime = 400 + (255 - seg.speed) * 3;
  const maxDur = Math.floor(riseFallTime / 100) + ((255 - seg.intensity) >> 2) + 13 + ((255 - seg.intensity) >> 1);

  for (let f = 0; f < SEGLEN; f++) {
    const off = f * 4;
    let stateStart = (seg.data[off] | (seg.data[off + 1] << 8)) & 0xFFFF;
    let stateDur = seg.data[off + 2];
    let stateOn = !!seg.data[off + 3];

    let stateTime = (now16 - stateStart) & 0xFFFF;
    if (stateTime > stateDur * 100) {
      stateOn = !stateOn;
      const init = !stateDur;
      if (stateOn) {
        stateDur = Math.floor(riseFallTime / 100) + ((255 - seg.intensity) >> 2) + m.jsRandom8(12 + ((255 - seg.intensity) >> 1)) + 1;
      } else {
        stateDur = Math.floor(riseFallTime / 100) + m.jsRandom8(3 + ((255 - seg.speed) >> 6)) + 1;
      }
      stateStart = now16;
      stateTime = 0;
      if (init) {
        stateStart = (stateStart - riseFallTime) & 0xFFFF;
        stateDur = Math.floor(riseFallTime / 100) + m.jsRandom8(12 + ((255 - seg.intensity) >> 1)) + 5;
        stateTime = riseFallTime;
      }
    }
    if (stateOn && stateDur > maxDur) stateDur = maxDur;
    if (stateTime > riseFallTime) stateTime = riseFallTime;
    const fadeprog = 255 - Math.floor((stateTime * 255) / riseFallTime);
    const flasherBri = stateOn ? (255 - m.gamma8(fadeprog)) : m.gamma8(fadeprog);

    let lastR = PRNG16;
    let diff = 0;
    while (diff < 0x4000) {
      PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
      diff = (PRNG16 > lastR) ? PRNG16 - lastR : lastR - PRNG16;
    }
    seg.setPixelColor(f, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(PRNG16 >> 8, false, false, 0), flasherBri));

    seg.data[off] = stateStart & 0xFF;
    seg.data[off + 1] = (stateStart >> 8) & 0xFF;
    seg.data[off + 2] = stateDur & 0xFF;
    seg.data[off + 3] = stateOn ? 1 : 0;
  }
};

Effects1D[52] = function mode_running_dual(seg) {
  _running_base(seg, false, true);
};

Effects1D[55] = function triWipe(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const cycleTime = 1000 + (255 - seg.speed) * 200;
  const perc = m.getNow() % cycleTime;
  const prog = (perc * 65535 / cycleTime) >>> 0;
  let ledIndex = (prog * SEGLEN * 3) >>> 16;
  let ledOffset = ledIndex;

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette(i, true, false, 2));
  }

  if (ledIndex < SEGLEN) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, (i > ledOffset) ? seg.SEGCOLOR(0) : seg.SEGCOLOR(1));
    }
  } else if (ledIndex < SEGLEN * 2) {
    ledOffset = ledIndex - SEGLEN;
    for (let i = ledOffset + 1; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.SEGCOLOR(1));
    }
  } else {
    ledOffset = ledIndex - SEGLEN * 2;
    for (let i = 0; i <= ledOffset; i++) {
      seg.setPixelColor(i, seg.SEGCOLOR(0));
    }
  }
};

Effects1D[56] = function mode_tricolor_fade(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const counter = (m.getNow() * ((seg.speed >> 3) + 1)) & 0xFFFF;
  const prog = (counter * 768) >> 16;

  let color1 = 0, color2 = 0;
  let stage = 0;

  if (prog < 256) {
    color1 = seg.SEGCOLOR(0);
    color2 = seg.SEGCOLOR(1);
    stage = 0;
  } else if (prog < 512) {
    color1 = seg.SEGCOLOR(1);
    color2 = seg.SEGCOLOR(2);
    stage = 1;
  } else {
    color1 = seg.SEGCOLOR(2);
    color2 = seg.SEGCOLOR(0);
    stage = 2;
  }

  const stp = prog & 0xFF;

  for (let i = 0; i < SEGLEN; i++) {
    let color;
    if (stage === 2) {
      color = m.color_blend(seg.color_from_palette(i, true, false, 2), color2, stp);
    } else if (stage === 1) {
      color = m.color_blend(color1, seg.color_from_palette(i, true, false, 2), stp);
    } else {
      color = m.color_blend(color1, color2, stp);
    }
    seg.setPixelColor(i, color);
  }
};

Effects1D[57] = function lightning(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const ledstart = m.random16(SEGLEN);
  const ledlen = 1 + m.random16(SEGLEN - ledstart);
  let bri = Math.floor(255 / m.random8(1, 3));

  if (seg.aux1 === 0) {
    seg.aux1 = m.random8(4, 4 + Math.floor(seg.intensity / 20));
    seg.aux1 *= 2;
    bri = 52;
    seg.aux0 = 200;
  }

  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  if (seg.aux1 > 3 && !(seg.aux1 & 0x01)) {
    for (let i = ledstart; i < ledstart + ledlen; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0, bri));
    }
    seg.aux1--;
    seg.step = m.getNow();
  } else {
    if (m.getNow() - seg.step > seg.aux0) {
      seg.aux1--;
      if (seg.aux1 < 2) seg.aux1 = 0;

      seg.aux0 = 50 + m.random8(100);
      if (seg.aux1 === 2) {
        seg.aux0 = m.random8(255 - seg.speed) * 100;
      }
      seg.step = m.getNow();
    }
  }
};

Effects1D[59] = function mode_multi_comet(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const MAX_COMETS = 8;
  const cycleTime = 10 + (255 - seg.speed);
  const it = Math.floor(m.getNow() / cycleTime);
  if (seg.step === it) return;
  if (!seg.data || seg._dataLen < MAX_COMETS * 2) {
    seg.allocateData(MAX_COMETS * 2);
  }
  seg.fade_out((seg.intensity >> 1) + 128);
  for (let i = 0; i < MAX_COMETS; i++) {
    let cometPos = (seg.data[i * 2] | (seg.data[i * 2 + 1] << 8)) & 0xFFFF;
    if (cometPos < SEGLEN) {
      const index = cometPos;
      if (seg.SEGCOLOR(2) !== 0) {
        seg.setPixelColor(index, (i % 2) ? seg.color_from_palette(index, true, false, 0) : seg.SEGCOLOR(2));
      } else {
        seg.setPixelColor(index, seg.color_from_palette(index, true, false, 0));
      }
      cometPos++;
    } else {
      if (!m.jsRandom16(SEGLEN)) {
        cometPos = 0;
      }
    }
    seg.data[i * 2] = cometPos & 0xFF;
    seg.data[i * 2 + 1] = (cometPos >> 8) & 0xFF;
  }
  seg.step = it;
};

Effects1D[62] = function oscillate(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const numOscillators = 3;

  if (seg.call === 0 || !seg.data) {
    seg.allocateData(numOscillators * 6);
    const d = seg.data;
    const p0 = Math.floor(SEGLEN / 4);
    d[0] = p0 & 0xFF; d[1] = (p0 >> 8) & 0xFF; d[2] = Math.floor(SEGLEN / 8); d[3] = 1; d[4] = 1;
    const p1 = Math.floor(SEGLEN / 4 * 3);
    d[6] = p1 & 0xFF; d[7] = (p1 >> 8) & 0xFF; d[8] = Math.floor(SEGLEN / 8); d[9] = 1; d[10] = 2;
    const p2 = Math.floor(SEGLEN / 4 * 2);
    d[12] = p2 & 0xFF; d[13] = (p2 >> 8) & 0xFF; d[14] = Math.floor(SEGLEN / 8); d[15] = 255; d[16] = 1;
  }

  const cycleTime = 20 + 2 * (255 - seg.speed);
  const it = Math.floor(m.getNow() / cycleTime);
  const d = seg.data;

  for (let i = 0; i < numOscillators; i++) {
    const off = i * 6;
    let pos = d[off] | (d[off + 1] << 8);
    let dir = d[off + 3] === 255 ? -1 : 1;
    let speed = d[off + 4];

    if (it !== seg.step) {
      pos += dir * speed;
    }

    d[off + 2] = Math.floor(SEGLEN / (3 + Math.floor(seg.intensity / 8)));

    if (dir === -1 && (pos < 0 || pos > SEGLEN * 2)) {
      pos = 0;
      dir = 1;
      speed = seg.speed > 100 ? m.random8(2, 4) : m.random8(1, 3);
    }
    if (dir === 1 && pos >= SEGLEN - 1) {
      pos = SEGLEN - 1;
      dir = -1;
      speed = seg.speed > 100 ? m.random8(2, 4) : m.random8(1, 3);
    }

    d[off] = pos & 0xFF; d[off + 1] = (pos >> 8) & 0xFF;
    d[off + 3] = dir === -1 ? 255 : 1;
    d[off + 4] = speed;
  }

  for (let i = 0; i < SEGLEN; i++) {
    let color = 0;
    for (let j = 0; j < numOscillators; j++) {
      const off = j * 6;
      const pos = d[off] | (d[off + 1] << 8);
      const size = d[off + 2];
      if (i >= pos - size && i <= pos + size) {
        color = (color === 0) ? seg.SEGCOLOR(j) : m.color_blend(color, seg.SEGCOLOR(j), 128);
      }
    }
    seg.setPixelColor(i, color);
  }

  seg.step = it;
};

Effects1D[69] = function fillNoise(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0) seg.step = m.random16();

  for (let i = 0; i < SEGLEN; i++) {
    const index = m.perlin8(i * SEGLEN, seg.step + i * SEGLEN);
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0));
  }
  seg.step += m.beatsin8_t(seg.speed, 1, 6);
};

Effects1D[75] = function lake(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const sp = Math.floor(seg.speed / 10);
  const wave1 = m.beatsin8_t(sp + 2, -64, 64);
  const wave2 = m.beatsin8_t(sp + 1, -64, 64);
  const wave3 = m.beatsin8_t(sp + 2, 0, 80);

  for (let i = 0; i < SEGLEN; i++) {
    const index = Math.floor(m.cos8_t((i * 15 + wave1) & 0xFF) / 2) + Math.floor(m.cubicwave8((i * 23 + wave2) & 0xFF) / 2);
    const lum = (index > wave3) ? index - wave3 : 0;
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0, lum));
  }
};

Effects1D[76] = function meteor(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (!seg.data || seg.data.length < SEGLEN) {
    seg.allocateData(SEGLEN);
  }

  const meteorSmooth = seg.check3;
  const trail = seg.data;
  const meteorSize = 1 + Math.floor(SEGLEN / 20);

  let meteorstart;
  if (meteorSmooth) {
    meteorstart = m.map((seg.step >>> 6) & 0xFF, 0, 255, 0, SEGLEN - 1);
  } else {
    const counter = (m.getNow() * ((seg.speed >> 2) + 8)) >>> 0;
    meteorstart = (counter * SEGLEN) >>> 16;
  }

  const maxVal = (seg.palette === 5 || !seg.check1) ? 240 : 255;

  for (let i = 0; i < SEGLEN; i++) {
    let col;
    if (m.random8() <= 255 - seg.intensity) {
      if (meteorSmooth) {
        if (trail[i] > 0) {
          let change = trail[i] + 4 - m.random8(24);
          trail[i] = m.constrain(change, 0, maxVal);
        }
        col = seg.check1 ? seg.color_from_palette(i, true, false, 0, trail[i]) : seg.color_from_palette(trail[i], false, true, 255);
      } else {
        trail[i] = m.scale8(trail[i], 128 + m.random8(127));
        let index = trail[i];
        let idx = 255;
        let bri = (seg.palette === 35 || seg.palette === 36) ? 255 : trail[i];
        if (!seg.check1) {
          idx = 0;
          index = m.map(i, 0, SEGLEN, 0, maxVal);
          bri = trail[i];
        }
        col = seg.color_from_palette(index, false, false, idx, bri);
      }
      seg.setPixelColor(i, col);
    }
  }

  for (let j = 0; j < meteorSize; j++) {
    const index = (meteorstart + j) % SEGLEN;
    if (meteorSmooth) {
      trail[index] = maxVal;
      const col = seg.check1 ? seg.color_from_palette(index, true, false, 0, trail[index]) : seg.color_from_palette(trail[index], false, true, 255);
      seg.setPixelColor(index, col);
    } else {
      let idx = 255;
      trail[index] = maxVal;
      let ii = maxVal;
      if (!seg.check1) {
        ii = m.map(index, 0, SEGLEN, 0, maxVal);
        idx = 0;
      }
      const col = seg.color_from_palette(ii, false, false, idx, 255);
      seg.setPixelColor(index, col);
    }
  }

  seg.step += seg.speed + 1;
};

Effects1D[78] = function railway(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const dur = (256 - seg.speed) * 40;
  const rampdur = (dur * seg.intensity) >> 8;

  if (seg.call === 0) { seg.step = 0; seg.aux0 = 0; }

  if (seg.step > dur) {
    seg.step = 0;
    seg.aux0 = seg.aux0 ? 0 : 1;
  }

  let pos = 255;
  if (rampdur !== 0) {
    const p0 = Math.floor((seg.step * 255) / rampdur);
    if (p0 < 255) pos = p0;
  }
  if (seg.aux0) pos = 255 - pos;

  for (let i = 0; i < SEGLEN; i += 2) {
    seg.setPixelColor(i, seg.color_from_palette(255 - pos, false, false, 255));
    if (i < SEGLEN - 1) {
      seg.setPixelColor(i + 1, seg.color_from_palette(pos, false, false, 255));
    }
  }

  seg.step += m.FRAMETIME;
};

function _ripple_base(seg, blurAmount) {
  if (typeof blurAmount === 'undefined') blurAmount = 0;
  const m = M();
  const is2D = seg._is2D;
  const SEGLEN = seg.vLength();
  const IBN = 5100;
  const MAX_RIPPLES = 100;
  const maxRipples = Math.min(1 + (SEGLEN >> 2), MAX_RIPPLES);
  const rippleSize = 4;
  if (seg.call === 0 || !seg.data || seg.data.length < maxRipples * rippleSize) {
    seg.allocateData(maxRipples * rippleSize);
  }
  const cols = is2D ? seg.vWidth() : 0;
  const rows = is2D ? seg.vHeight() : 0;
  for (let i = 0; i < maxRipples; i++) {
    const off = i * rippleSize;
    let ripplestate = seg.data[off];
    if (ripplestate) {
      const rippledecay = (seg.speed >> 4) + 1;
      const rippleorigin = seg.data[off + 2] | (seg.data[off + 3] << 8);
      const col = seg.color_from_palette(seg.data[off + 1], false, false, 255);
      const propagation = ((Math.floor(ripplestate / rippledecay) - 1) * (seg.speed + 1)) >>> 0;
      let propI = propagation >> 8;
      const propF = propagation & 0xFF;
      const amp = (ripplestate < 17) ? m.triwave8((ripplestate - 1) * 8) : m.map(ripplestate, 17, 255, 255, 2);

      if (is2D) {
        propI = Math.floor(propI / 2);
        const cx = (rippleorigin >> 8) & 0xFF;
        const cy = rippleorigin & 0xFF;
        const mag = m.scale8(m.sin8((propF >> 2) & 0xFF), amp);
        if (propI > 0) {
          const edgeX = Math.min(cx + propI, cols - 1);
          const blendCol = m.color_blend(seg.getPixelColorXY(edgeX, cy), col, mag);
          seg.drawCircle(cx, cy, propI, blendCol);
        }
      } else {
        const left = rippleorigin - propI - 1;
        const right = rippleorigin + propI + 2;
        for (let v = 0; v < 4; v++) {
          const mag = m.scale8(m.cubicwave8(((propF >> 2) + v * 64) & 0xFF), amp);
          seg.setPixelColor(left + v, m.color_blend(seg.getPixelColor(left + v), col, mag));
          seg.setPixelColor(right - v, m.color_blend(seg.getPixelColor(right - v), col, mag));
        }
      }
      ripplestate += rippledecay;
      seg.data[off] = (ripplestate > 254) ? 0 : ripplestate;
    } else {
      const spawnThreshold = is2D ? (seg.intensity >> 3) : seg.intensity;
      if (m.random16(IBN + 10000) <= spawnThreshold) {
        seg.data[off] = 1;
        let pos;
        if (is2D) {
          pos = (m.random8(cols) << 8) | m.random8(rows);
        } else {
          pos = m.random16(SEGLEN);
        }
        seg.data[off + 2] = pos & 0xFF;
        seg.data[off + 3] = (pos >> 8) & 0xFF;
        seg.data[off + 1] = m.random8();
      }
    }
  }
  if (blurAmount) seg.blur(blurAmount);
}

Effects1D[79] = function ripple(seg) {
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  if (seg.custom1 || seg.check2) seg.fade_out(250);
  else seg.fill(seg.SEGCOLOR(1));
  _ripple_base(seg, seg.custom1 >> 1);
};

Effects1D[83] = function solidPattern(seg) {
  const SEGLEN = seg.vLength();
  const lit = 1 + seg.speed;
  const unlit = 1 + seg.intensity;
  let drawingLit = true;
  let cnt = 0;

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, drawingLit ? seg.color_from_palette(i, true, false, 0) : seg.SEGCOLOR(1));
    cnt++;
    if (cnt >= (drawingLit ? lit : unlit)) {
      cnt = 0;
      drawingLit = !drawingLit;
    }
  }
};

Effects1D[86] = function mode_spots_fade(seg) {
  const m = M();
  const counter = (m.getNow() * ((seg.speed >> 2) + 8)) & 0xFFFF;
  const t = m.triwave16(counter);
  const tr = (t >> 1) + (t >> 2);
  _spots_base(seg, tr);
};

Effects1D[89] = function fireworksStarburst(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const STARBURST_MAX_FRAG = 10;
  const STAR_SIZE = 60;
  let numStars = 1 + (SEGLEN >> 3);
  const maxStars = 42;
  if (numStars > maxStars) numStars = maxStars;

  const dataSize = STAR_SIZE * numStars;
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
  }

  const it = m.getNow();
  const buf = seg.data.buffer;

  function getStar(j) {
    const off = j * STAR_SIZE;
    const dv = new DataView(buf, off, STAR_SIZE);
    return {
      get color() { return dv.getUint32(0, true); },
      get birth() { return dv.getUint32(4, true); },
      get last()  { return dv.getUint32(8, true); },
      get vel()   { return dv.getFloat32(12, true); },
      get pos()   { return dv.getUint16(16, true); },
      getFragment(i) { return dv.getFloat32(20 + i * 4, true); },
      setFragment(i, v) { dv.setFloat32(20 + i * 4, v, true); },
      set(color, birth, last, vel, pos) {
        dv.setUint32(0, color, true);
        dv.setUint32(4, birth, true);
        dv.setUint32(8, last, true);
        dv.setFloat32(12, vel, true);
        dv.setUint16(16, pos, true);
      }
    };
  }

  const maxSpeed = 375.0;
  const particleIgnition = 250.0;
  const particleFadeTime = 1500.0;

  for (let j = 0; j < numStars; j++) {
    const star = getStar(j);
    if (m.random8(Math.max(1, 144 - (seg.speed >> 1))) === 0 && star.birth === 0) {
      const startPos = m.random16(SEGLEN - 1);
      const multiplier = m.random8() / 255.0;
      const starColor = seg.color_wheel(m.random8());
      const vel = maxSpeed * (m.random8() / 255.0) * multiplier;
      star.set(starColor, it, it, vel, startPos);
      const num = m.random8(3, 6 + (seg.intensity >> 5));
      for (let i = 0; i < STARBURST_MAX_FRAG; i++) {
        star.setFragment(i, (i < num) ? startPos : -1);
      }
    }
  }

  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  for (let j = 0; j < numStars; j++) {
    const star = getStar(j);
    if (star.birth !== 0) {
      const dt = (it - star.last) / 1000.0;

      for (let i = 0; i < STARBURST_MAX_FRAG; i++) {
        const frag = star.getFragment(i);
        if (frag > 0) {
          const iVar = i >> 1;
          star.setFragment(i, frag + star.vel * dt * iVar / 3.0);
        }
      }
      const newVel = star.vel - 3 * star.vel * dt;
      star.set(star.color, star.birth, it, newVel, star.pos);
    }

    const cr = m.colorR(star.color), cg = m.colorG(star.color), cb = m.colorB(star.color);

    let fade = 0.0;
    const age = it - star.birth;
    let c;

    if (age < particleIgnition) {
      c = m.color_blend(0xFFFFFF, m.colorPack(cr, cg, cb), Math.floor(254.5 * (age / particleIgnition)));
    } else {
      if (age > particleIgnition + particleFadeTime) {
        fade = 1.0;
        star.set(star.color, 0, star.last, star.vel, star.pos);
        c = seg.SEGCOLOR(1);
      } else {
        const fadeAge = age - particleIgnition;
        fade = fadeAge / particleFadeTime;
        c = m.color_blend(m.colorPack(cr, cg, cb), seg.SEGCOLOR(1), Math.floor(254.5 * fade));
      }
    }

    const particleSize = (1.0 - fade) * 2.0;

    for (let index = 0; index < STARBURST_MAX_FRAG * 2; index++) {
      const mirrored = index & 0x1;
      const fi = index >> 1;
      const frag = star.getFragment(fi);
      if (frag > 0) {
        let loc = frag;
        if (mirrored) loc -= (loc - star.pos) * 2;
        const pStart = Math.max(0, Math.floor(loc - particleSize));
        let pEnd = Math.floor(loc + particleSize);
        if (pStart === pEnd) pEnd++;
        if (pEnd > SEGLEN) pEnd = SEGLEN;
        for (let p = pStart; p < pEnd; p++) {
          seg.setPixelColor(p, c);
        }
      }
    }
  }
};

Effects1D[95] = function popcorn(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const maxNumPopcorn = 21;
  const sparkSize = 12;

  if (seg.call === 0 || !seg.data || seg.data.length < maxNumPopcorn * sparkSize) {
    seg.allocateData(maxNumPopcorn * sparkSize);
  }

  const hasCol2 = seg.SEGCOLOR(2) !== 0;
  if (!seg.check2) seg.fill(hasCol2 ? 0 : seg.SEGCOLOR(1));

  let gravity = -0.0001 - (seg.speed / 200000.0);
  gravity *= SEGLEN;

  let numPopcorn = Math.floor(seg.intensity * maxNumPopcorn / 255);
  if (numPopcorn === 0) numPopcorn = 1;

  const buf = seg.data.buffer;

  for (let i = 0; i < numPopcorn; i++) {
    const dv = new DataView(buf, i * sparkSize);
    let pos = dv.getFloat32(0, true);
    let vel = dv.getFloat32(4, true);
    const colIndex = seg.data[i * sparkSize + 8];

    if (pos >= 0.0) {
      pos += vel;
      vel += gravity;
      dv.setFloat32(0, pos, true);
      dv.setFloat32(4, vel, true);
    } else {
      if (m.random8() < 2) {
        pos = 0.01;
        let peakHeight = 128 + m.random8(128);
        peakHeight = (peakHeight * (SEGLEN - 1)) >> 8;
        vel = Math.sqrt(-2.0 * gravity * peakHeight);

        let newColIndex;
        if (seg.palette) {
          newColIndex = m.random8();
        } else {
          let col = m.random8(0, 3);
          if (!seg.SEGCOLOR(2) || !seg.SEGCOLOR(col)) col = 0;
          newColIndex = col;
        }
        seg.data[i * sparkSize + 8] = newColIndex;
        dv.setFloat32(0, pos, true);
        dv.setFloat32(4, vel, true);
      }
    }

    pos = dv.getFloat32(0, true);
    if (pos >= 0.0) {
      const ci = seg.data[i * sparkSize + 8];
      let col = m.color_wheel(ci);
      if (!seg.palette && ci < 3) col = seg.SEGCOLOR(ci);
      const ledIndex = Math.floor(pos);
      if (ledIndex < SEGLEN) seg.setPixelColor(ledIndex, col);
    }
  }
};

Effects1D[96] = function drip(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const maxNumDrops = 4;
  const sparkSize = 16;

  if (seg.call === 0 || !seg.data || seg.data.length < maxNumDrops * sparkSize) {
    seg.allocateData(maxNumDrops * sparkSize);
  }

  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  const numDrops = 1 + (seg.intensity >> 6);
  let gravity = -0.0005 - (seg.speed / 50000.0);
  gravity *= Math.max(1, SEGLEN - 1);
  const sourcedrop = 12;
  const buf = seg.data.buffer;

  for (let j = 0; j < numDrops; j++) {
    const dv = new DataView(buf, j * sparkSize);
    let pos = dv.getFloat32(0, true);
    let vel = dv.getFloat32(4, true);
    let col = dv.getFloat32(8, true);
    let colIndex = seg.data[j * sparkSize + 12];

    if (colIndex === 0) {
      pos = SEGLEN - 1;
      vel = 0;
      col = sourcedrop;
      colIndex = 1;
      dv.setFloat32(0, pos, true);
      dv.setFloat32(4, vel, true);
      dv.setFloat32(8, col, true);
      seg.data[j * sparkSize + 12] = colIndex;
    }

    seg.setPixelColor(SEGLEN - 1, m.color_blend(0, seg.SEGCOLOR(0), Math.min(255, sourcedrop)));

    if (colIndex === 1) {
      if (col > 255) col = 255;
      seg.setPixelColor(Math.floor(pos), m.color_blend(0, seg.SEGCOLOR(0), Math.min(255, Math.floor(col))));
      col += m.map(seg.speed, 0, 255, 1, 6);

      if (m.random8() < Math.floor(col / 10)) {
        colIndex = 2;
        col = 255;
      }
      dv.setFloat32(8, col, true);
      seg.data[j * sparkSize + 12] = colIndex;
    }

    if (colIndex > 1) {
      if (pos > 0) {
        pos += vel;
        if (pos < 0) pos = 0;
        vel += gravity;

        for (let ii = 1; ii < 7 - colIndex; ii++) {
          const drawPos = m.constrain(Math.floor(pos) + ii, 0, SEGLEN - 1);
          seg.setPixelColor(drawPos, m.color_blend(0, seg.SEGCOLOR(0), Math.min(255, Math.floor(col / ii))));
        }

        if (colIndex > 2) {
          seg.setPixelColor(0, m.color_blend(seg.SEGCOLOR(0), 0, Math.min(255, Math.floor(col))));
        }
      } else {
        if (colIndex > 2) {
          colIndex = 0;
          col = sourcedrop;
        } else {
          if (colIndex === 2) {
            vel = -vel / 4;
            pos += vel;
          }
          col = sourcedrop * 2;
          colIndex = 5;
        }
      }
      dv.setFloat32(0, pos, true);
      dv.setFloat32(4, vel, true);
      dv.setFloat32(8, col, true);
      seg.data[j * sparkSize + 12] = colIndex;
    }
  }
};

Effects1D[98] = function percent(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let percent = seg.intensity;
  percent = m.constrain(percent, 0, 200);
  const active_leds = (percent < 100) ? Math.round(SEGLEN * percent / 100.0)
                                      : Math.round(SEGLEN * (200 - percent) / 100.0);

  let size = 1 + ((seg.speed * SEGLEN) >> 11);
  if (seg.speed === 255) size = 255;

  if (percent <= 100) {
    for (let i = 0; i < SEGLEN; i++) {
      if (i < seg.aux1) {
        if (seg.check1)
          seg.setPixelColor(i, seg.color_from_palette(m.map(percent, 0, 100, 0, 255), false, false, 0));
        else
          seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
      } else {
        seg.setPixelColor(i, seg.SEGCOLOR(1));
      }
    }
  } else {
    for (let i = 0; i < SEGLEN; i++) {
      if (i < (SEGLEN - seg.aux1)) {
        seg.setPixelColor(i, seg.SEGCOLOR(1));
      } else {
        if (seg.check1)
          seg.setPixelColor(i, seg.color_from_palette(m.map(percent, 100, 200, 255, 0), false, false, 0));
        else
          seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
      }
    }
  }

  if (active_leds > seg.aux1) {
    seg.aux1 += size;
    if (seg.aux1 > active_leds) seg.aux1 = active_leds;
  } else if (active_leds < seg.aux1) {
    if (seg.aux1 > size) seg.aux1 -= size; else seg.aux1 = 0;
    if (seg.aux1 < active_leds) seg.aux1 = active_leds;
  }
};

Effects1D[102] = function candleMulti(seg) {
  Effects1D._candle(seg, true);
};

Effects1D[104] = function sunrise(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (seg.call === 0 || seg.speed !== seg.aux0) {
    seg.step = m.getNow();
    seg.aux0 = seg.speed;
  }

  seg.fill(0);

  let stage = 0xFFFF;
  const s10SinceStart = Math.floor((m.getNow() - seg.step) / 100);

  if (seg.speed > 120) {
    const counter = (m.getNow() >> 1) * (((seg.speed - 120) >> 1) + 1);
    stage = m.triwave16(counter & 0xFFFF);
  } else if (seg.speed) {
    let durMins = seg.speed;
    if (durMins > 60) durMins -= 60;
    const s10Target = durMins * 600;
    const s10 = Math.min(s10SinceStart, s10Target);
    stage = m.map(s10, 0, s10Target, 0, 0xFFFF);
    if (seg.speed > 60) stage = 0xFFFF - stage;
  }

  for (let i = 0; i <= Math.floor(SEGLEN / 2); i++) {
    let wave = m.triwave16(Math.floor(i * stage / SEGLEN) & 0xFFFF);
    wave = (wave >> 8) + ((wave * seg.intensity) >> 15);
    let c;
    if (wave > 240) {
      c = seg.color_from_palette(240, false, true, 255);
    } else {
      c = seg.color_from_palette(wave, false, true, 255);
    }
    seg.setPixelColor(i, c);
    seg.setPixelColor(SEGLEN - i - 1, c);
  }
};

Effects1D[107] = function noisePal(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const scale = 15 + (seg.intensity >> 2);

  const dataSize = 96 * 2;
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    for (let p = 0; p < 2; p++) {
      const baseI = m.random8();
      const anchors = [
        [((baseI + m.random8(0, 64)) & 0xFF), 255, m.random8(128, 255)],
        [((baseI + 128) & 0xFF), 255, m.random8(128, 255)],
        [((baseI + m.random8(0, 92)) & 0xFF), 192, m.random8(128, 255)],
        [((baseI + m.random8(0, 92)) & 0xFF), 255, m.random8(128, 255)]
      ];
      for (let i = 0; i < 16; i++) {
        const ai = (i * 4 / 16);
        const a0 = Math.floor(ai) % 4;
        const a1 = (a0 + 1) % 4;
        const frac = ai - Math.floor(ai);
        const off = p * 96 + i * 3;
        for (let c = 0; c < 3; c++) {
          seg.data[off + c] = Math.floor(anchors[a0][c] * (1 - frac) + anchors[a1][c] * frac);
        }
      }
    }
    seg.step = m.getNow();
    seg.aux0 = 0;
  }

  const changePaletteMs = 4000 + seg.speed * 10;
  if (m.getNow() - seg.step > changePaletteMs) {
    seg.step = m.getNow();
    const baseI = m.random8();
    const anchors = [
      [((baseI + m.random8(0, 64)) & 0xFF), 255, m.random8(128, 255)],
      [((baseI + 128) & 0xFF), 255, m.random8(128, 255)],
      [((baseI + m.random8(0, 92)) & 0xFF), 192, m.random8(128, 255)],
      [((baseI + m.random8(0, 92)) & 0xFF), 255, m.random8(128, 255)]
    ];
    for (let i = 0; i < 16; i++) {
      const ai = (i * 4 / 16);
      const a0 = Math.floor(ai) % 4;
      const a1 = (a0 + 1) % 4;
      const frac = ai - Math.floor(ai);
      const off = 96 + i * 3;
      for (let c = 0; c < 3; c++) {
        seg.data[off + c] = Math.floor(anchors[a0][c] * (1 - frac) + anchors[a1][c] * frac);
      }
    }
  }

  for (let i = 0; i < 48; i++) {
    const cur = seg.data[i];
    const tgt = seg.data[96 + i];
    if (cur < tgt) seg.data[i] = Math.min(cur + 48, tgt);
    else if (cur > tgt) seg.data[i] = Math.max(cur - 48, tgt);
  }

  const useSEGPALETTE = (seg.palette > 0);

  for (let i = 0; i < SEGLEN; i++) {
    const index = m.perlin8(i * scale, seg.aux0 + i * scale);
    if (useSEGPALETTE) {
      seg.setPixelColor(i, seg.ColorFromPalette(index, 255, 1));
    } else {
      const fi = (index / 256.0) * 15.99;
      const i0 = Math.floor(fi) % 16;
      const i1 = (i0 + 1) % 16;
      const frac = fi - Math.floor(fi);
      const h = Math.floor(seg.data[i0 * 3] * (1 - frac) + seg.data[i1 * 3] * frac) & 0xFF;
      const s = Math.floor(seg.data[i0 * 3 + 1] * (1 - frac) + seg.data[i1 * 3 + 1] * frac);
      const v = Math.floor(seg.data[i0 * 3 + 2] * (1 - frac) + seg.data[i1 * 3 + 2] * frac);
      seg.setPixelColor(i, m.hsv2rgb(h, s, v));
    }
  }

  seg.aux0 += m.beatsin8(10, 1, 4);
};

Effects1D[111] = function chunchun(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  seg.fade_out(254);
  const counter = (m.getNow() * (6 + (seg.speed >> 4))) >>> 0;
  const numBirds = 2 + (SEGLEN >> 3);
  const span = Math.floor((seg.intensity << 8) / numBirds);

  let cnt = counter;
  for (let i = 0; i < numBirds; i++) {
    cnt = (cnt - span) >>> 0;
    const megumin = (m.sin16(cnt & 0xFFFF) + 0x8000) & 0xFFFF;
    let bird = (megumin * SEGLEN) >>> 16;
    bird = m.constrain(bird, 0, SEGLEN - 1);
    seg.setPixelColor(bird, seg.color_from_palette(Math.floor((i * 255) / numBirds), false, false, 0));
  }
};

Effects1D[113] = function washingMachine(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const x0 = (m.getNow() >> 7) & 0xFF;
  let a = 127;
  let x = x0;
  if (x > 127) { a = -127; x -= 127; }
  let speed;
  if (x < 15) { speed = Math.floor(x * a / 15); }
  else if (x < 75) { speed = a; }
  else if (x < 90) { speed = Math.floor((90 - x) * a / 15); }
  else { speed = 0; }

  seg.step = ((seg.step || 0) + Math.floor((speed * 2048) / (512 - seg.speed))) >>> 0;

  for (let i = 0; i < SEGLEN; i++) {
    const col = m.sin8((Math.floor((seg.intensity / 25 + 1) * 255 * i / SEGLEN) + (seg.step >> 7)) & 0xFF);
    seg.setPixelColor(i, seg.color_from_palette(col, false, false, 3));
  }
};

Effects1D[115] = function blends(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const pixelLen = SEGLEN > 255 ? 255 : SEGLEN;
  const dataSize = 4 * (pixelLen + 1);
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
  }

  const buf = seg.data.buffer;
  const pixels = new Uint32Array(buf, 0, pixelLen + 1);
  const blendSpeed = m.map(seg.intensity, 0, 255, 10, 128);
  let shift = ((m.getNow() * ((seg.speed >> 3) + 1)) >> 8) >>> 0;

  for (let i = 0; i < pixelLen; i++) {
    const qw = m.quadwave8(((i + 1) * 16) & 0xFF);
    pixels[i] = m.color_blend(pixels[i], seg.color_from_palette((shift + qw) & 0xFF, false, false, 255), blendSpeed);
    shift = (shift + 3) >>> 0;
  }

  let offset = 0;
  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, pixels[offset]);
    offset++;
    if (offset >= pixelLen) offset = 0;
  }
};

Effects1D[147] = function perlinMove(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  seg.fade_out(255 - seg.custom1);
  for (let i = 0; i < Math.floor(seg.intensity / 16) + 1; i++) {
    const locn = m.perlin16(
      Math.floor(m.getNow() * 128 / (260 - seg.speed)) + i * 15000,
      Math.floor(m.getNow() * 128 / (260 - seg.speed))
    );
    const pixloc = m.map(locn, 50 * 256, 192 * 256, 0, SEGLEN - 1);
    seg.setPixelColor(pixloc, seg.color_from_palette(pixloc % 255, false, false, 0));
  }
};

Effects1D[128] = function pixels(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  if (!seg._pixVals) seg._pixVals = new Uint8Array(32);
  const vol = seg.getVolume();
  seg._pixVals[m.getNow() % 32] = vol;
  seg.fade_out(64 + (seg.speed >> 1));
  for (let i = 0; i < Math.floor(seg.intensity / 8); i++) {
    const segLoc = m.random16(SEGLEN);
    seg.setPixelColor(segLoc, m.color_blend(seg.SEGCOLOR(1),
      seg.color_from_palette(seg._pixVals[i % 32] + i * 4, false, false, 0), vol));
  }
};

Effects1D[129] = function pixelwave(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (seg.call === 0) {
    seg.fill(0);
  }

  const vol = seg.getVolume();

  const secondHand = (Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) + 1) % 16;
  if (secondHand !== (seg.aux0 || 0)) {
    seg.aux0 = secondHand;

    const pixBri = Math.min(255, Math.floor(vol * seg.intensity / 64)) & 0xFF;

    seg.setPixelColor(Math.floor(SEGLEN / 2),
      m.color_blend(seg.SEGCOLOR(1),
        seg.color_from_palette(m.getNow() & 0xFF, false, false, 0), pixBri));
    for (let i = SEGLEN - 1; i > Math.floor(SEGLEN / 2); i--) seg.setPixelColor(i, seg.getPixelColor(i - 1));
    for (let i = 0; i < Math.floor(SEGLEN / 2); i++) seg.setPixelColor(i, seg.getPixelColor(i + 1));
  }
};

function _gravcenter_base(seg, mode) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  if (!seg._gravData) seg._gravData = { topLED: 0, gravityCounter: 0 };
  const gravcen = seg._gravData;
  const vol = seg.getVolume();

  if (mode === 1) seg.fade_out(253);
  else if (mode === 2) seg.fade_out(249);
  else if (mode === 3) seg.fade_out(250);
  else seg.fade_out(251);

  let segmentSampleAvg = vol * seg.intensity / 255.0;
  let tempsamp;
  if (mode === 2) {
    segmentSampleAvg *= 0.25;
    tempsamp = m.constrain(Math.floor(segmentSampleAvg * 2.0 * (SEGLEN - 1) / 64.0), 0, SEGLEN - 1);
  } else {
    segmentSampleAvg *= 0.125;
    tempsamp = m.constrain(Math.floor(segmentSampleAvg * 2.0 * (SEGLEN / 2.0) / 32.0), 0, Math.floor(SEGLEN / 2));
  }

  const gravity = 8 - Math.floor(seg.speed / 32);
  const offset = (mode === 2) ? 0 : 1;
  if (tempsamp >= gravcen.topLED) gravcen.topLED = tempsamp - offset;
  else if (gravcen.gravityCounter % gravity === 0) gravcen.topLED--;

  const half = Math.floor(SEGLEN / 2);
  if (mode === 1) {
    for (let i = 0; i < tempsamp; i++) {
      const index = (Math.floor(segmentSampleAvg * 24 + m.getNow() / 200)) & 0xFF;
      seg.setPixelColor(i + half, seg.color_from_palette(index, false, false, 0));
      seg.setPixelColor(half - 1 - i, seg.color_from_palette(index, false, false, 0));
    }
    if (gravcen.topLED >= 0) {
      seg.setPixelColor(gravcen.topLED + half, 0x808080);
      seg.setPixelColor(half - 1 - gravcen.topLED, 0x808080);
    }
  } else if (mode === 2) {
    for (let i = 0; i < tempsamp; i++) {
      const index = m.perlin8(Math.floor(i * segmentSampleAvg + m.getNow()), Math.floor(5000 + i * segmentSampleAvg));
      seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(index, false, false, 0), Math.min(255, Math.floor(segmentSampleAvg * 8))));
    }
    if (gravcen.topLED > 0) seg.setPixelColor(gravcen.topLED, seg.color_from_palette(m.getNow() & 0xFF, false, false, 0));
  } else if (mode === 3) {
    const MAX_FREQ_LOG10 = 4.04238;
    let FFT_MajorPeak = seg.getMajorPeak();
    if (FFT_MajorPeak < 1) FFT_MajorPeak = 1;
    const freqIndex = Math.floor((Math.log10(FFT_MajorPeak) - (MAX_FREQ_LOG10 - 1.78)) * 255) & 0xFF;
    for (let i = 0; i < tempsamp; i++) {
      seg.setPixelColor(i + half, seg.color_from_palette(freqIndex, false, false, 0));
      seg.setPixelColor(half - i - 1, seg.color_from_palette(freqIndex, false, false, 0));
    }
    if (gravcen.topLED >= 0) {
      seg.setPixelColor(gravcen.topLED + half, 0x808080);
      seg.setPixelColor(half - 1 - gravcen.topLED, 0x808080);
    }
  } else {
    for (let i = 0; i < tempsamp; i++) {
      const index = m.perlin8(Math.floor(i * segmentSampleAvg + m.getNow()), Math.floor(5000 + i * segmentSampleAvg));
      const col = m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(index, false, false, 0), Math.min(255, Math.floor(segmentSampleAvg * 8)));
      seg.setPixelColor(i + half, col);
      seg.setPixelColor(half - i - 1, col);
    }
    if (gravcen.topLED >= 0) {
      seg.setPixelColor(gravcen.topLED + half, seg.color_from_palette(m.getNow() & 0xFF, false, false, 0));
      seg.setPixelColor(half - 1 - gravcen.topLED, seg.color_from_palette(m.getNow() & 0xFF, false, false, 0));
    }
  }
  gravcen.gravityCounter = (gravcen.gravityCounter + 1) % gravity;
}

Effects1D[132] = function gravimeter(seg) { _gravcenter_base(seg, 2); };

Effects1D[156] = function gravcenter(seg) { _gravcenter_base(seg, 0); };

Effects1D[116] = function tvSimulator(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const now = m.getNow();

  if (!seg._tvSim) {
    seg._tvSim = {
      totalTime: 0, fadeTime: 0, startTime: 0, elapsed: 0,
      sliderValues: 0,
      sceeneStart: 0, sceeneDuration: 0,
      sceeneColorHue: 0, sceeneColorSat: 0, sceeneColorBri: 0,
      actualColorR: 0, actualColorG: 0, actualColorB: 0,
      pr: 0, pg: 0, pb: 0
    };
    seg.aux0 = 0;
    seg.aux1 = 0;
  }
  const tv = seg._tvSim;

  const colorSpeed = m.map(seg.speed, 0, 255, 1, 20);
  const colorIntensity = m.map(seg.intensity, 0, 255, 10, 30);

  const sliderVal = (seg.speed << 8) | seg.intensity;
  if (sliderVal !== tv.sliderValues) {
    tv.sliderValues = sliderVal;
    seg.aux1 = 0;
  }

  if ((now - tv.sceeneStart) >= tv.sceeneDuration || seg.aux1 === 0) {
    tv.sceeneStart = now;
    tv.sceeneDuration = m.random16(60 * 250 * colorSpeed, 60 * 750 * colorSpeed);
    tv.sceeneColorHue = m.random16(0, 768);
    tv.sceeneColorSat = m.random8(100, 130 + colorIntensity);
    tv.sceeneColorBri = m.random8(200, 240);
    seg.aux1 = 1;
    seg.aux0 = 0;
  }

  let nr, ng, nb;
  if (seg.aux0 === 0) {
    let hue, sat, bri;
    let j = m.random8(4 * colorIntensity);
    if (m.random8() < 128) {
      hue = (j < tv.sceeneColorHue) ? tv.sceeneColorHue - j : 767 - tv.sceeneColorHue - j;
    } else {
      hue = (j + tv.sceeneColorHue) < 767 ? tv.sceeneColorHue + j : tv.sceeneColorHue + j - 767;
    }
    j = m.random8(2 * colorIntensity);
    sat = Math.max(0, tv.sceeneColorSat - j);
    j = m.random8(100);
    bri = Math.max(0, tv.sceeneColorBri - j);

    // Adafruit constant-brightness HSB to RGB.
    const temp = new Uint8Array(5);
    const n = Math.floor(hue / 256) % 3;
    const x = ((((hue & 255) * sat) >> 8) * bri) >> 8;
    const s = ((256 - sat) * bri) >> 8;
    temp[0] = temp[3] = s;
    temp[1] = temp[4] = x + s;
    temp[2] = bri - x;
    tv.actualColorR = temp[n + 2];
    tv.actualColorG = temp[n + 1];
    tv.actualColorB = temp[n];
  }

  nr = m.gamma8(tv.actualColorR) * 257;
  ng = m.gamma8(tv.actualColorG) * 257;
  nb = m.gamma8(tv.actualColorB) * 257;

  if (seg.aux0 === 0) {
    seg.aux0 = 1;
    tv.totalTime = m.random16(250, 2500);
    tv.fadeTime = m.random16(0, tv.totalTime);
    if (m.random8(10) < 3) tv.fadeTime = 0;
    tv.startTime = now;
  }

  tv.elapsed = now - tv.startTime;

  let r, g, b;
  if (tv.elapsed < tv.fadeTime) {
    r = m.map(tv.elapsed, 0, tv.fadeTime, tv.pr, nr);
    g = m.map(tv.elapsed, 0, tv.fadeTime, tv.pg, ng);
    b = m.map(tv.elapsed, 0, tv.fadeTime, tv.pb, nb);
  } else {
    r = nr; g = ng; b = nb;
  }

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, m.colorPack(r >> 8, g >> 8, b >> 8));
  }

  if (tv.elapsed >= tv.totalTime) {
    tv.pr = nr; tv.pg = ng; tv.pb = nb;
    seg.aux0 = 0;
  }
};

Effects1D[179] = function flowStripe(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const hl = Math.floor(SEGLEN * 10 / 13);
  const hue = Math.floor(m.getNow() / (seg.speed + 1)) & 0xFF;
  const t = Math.floor(m.getNow() / (Math.floor(seg.intensity / 8) + 1));

  for (let i = 0; i < SEGLEN; i++) {
    let c = Math.floor((Math.abs(i - hl) * 127) / Math.max(1, hl));
    c = m.sin8(c & 0xFF);
    c = m.sin8((Math.floor(c / 2) + t) & 0xFF);
    const b = m.sin8((c + Math.floor(t / 8)) & 0xFF);
    seg.setPixelColor(i, seg.color_from_palette((b + hue) & 0xFF, false, true, 3));
  }
};

Effects1D[184] = function wavesins(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  for (let i = 0; i < SEGLEN; i++) {
    const bri = m.sin8((Math.floor(m.getNow() / 4) + i * seg.intensity) & 0xFF);
    const idx = m.beatsin8(seg.speed, seg.custom1, seg.custom1 + seg.custom2, 0, i * (seg.custom3 << 3));
    seg.setPixelColor(i, seg.color_from_palette(idx, false, false, 0, bri));
  }
};

Effects1D[218] = function colorClouds(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0) {
    seg.aux0 = m.random16();
    seg.aux1 = m.random16();
  }
  const volX0 = seg.aux0;
  const hueX0 = seg.aux1;
  const hueOffset0 = (volX0 + hueX0) & 0xFF;

  const cozy = seg.check3;
  const volSpeed = 1 + seg.speed;
  const hueSpeed = 1 + seg.intensity;
  const volSqueeze = 8 + seg.custom1;
  const hueSqueeze = seg.custom2;
  const volCutoff = 12500 + seg.custom3 * 900;
  const volSaturate = 52000;

  const now = m.getNow();
  const volT = Math.floor(now * volSpeed / 8);
  const hueT = Math.floor(now * hueSpeed / 8);
  const hueOffset = (m.beat88(64) >> 8) & 0xFF;

  for (let i = 0; i < SEGLEN; i++) {
    const volX = i * volSqueeze * 64;
    let vol = m.perlin16(volX0 + volX, volT);
    vol = m.map(vol, volCutoff, volSaturate, 0, 255);
    vol = m.constrain(vol, 0, 255);

    const hueX = i * hueSqueeze * 8;
    let hue = (m.perlin16(hueX0 + hueX, hueT) >> 7) & 0xFF;
    hue = (hue + hueOffset0 + hueOffset) & 0xFF;
    if (cozy) {
      hue = m.cos8((128 + Math.floor(hue / 2)) & 0xFF);
    }

    let pixel;
    if (seg.palette) {
      pixel = seg.color_from_palette(hue, false, true, 0, vol);
    } else {
      pixel = m.hsv2rgb(hue, 255, vol);
    }

    if (m.colorR(pixel) + m.colorG(pixel) + m.colorB(pixel) <= 2) {
      pixel = 0;
    }

    seg.setPixelColor(i, pixel);
  }
};

Effects1D[219] = function slowTransition(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const totalSteps = seg.check2 ? (16 * 255) : 255;
  const duration = seg.speed === 0 ? 10000 : seg.speed * 60000;

  if (seg.call === 0 || !seg._stData) {
    seg._stData = { startTime: m.getNow(), stepsDone: 0xFFFF };
    seg.aux1 = seg.speed;
  }

  if (seg.aux1 !== seg.speed) {
    seg._stData.startTime = m.getNow();
    seg._stData.stepsDone = 0;
    seg.aux1 = seg.speed;
  }

  const elapsed = m.getNow() - seg._stData.startTime;
  let expectedSteps = Math.min(totalSteps, Math.floor(elapsed * totalSteps / duration));

  if (seg._stData.stepsDone < expectedSteps) {
    seg._stData.stepsDone = expectedSteps;
  }

  for (let i = 0; i < SEGLEN; i++) {
    const paletteIndex = Math.floor(i * 255 / SEGLEN);
    seg.setPixelColor(i, seg.ColorFromPalette(paletteIndex, 255, 0));
  }
};

Effects1D[130] = function juggles(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  seg.fade_out(224);

  const vol = seg.getVolume();
  const my_sampleAgc = Math.min(255, Math.max(0, vol));

  const numBalls = Math.floor(seg.intensity / 32) + 1;
  for (let i = 0; i < numBalls; i++) {
    const pos = m.beatsin16_t(Math.floor(seg.speed / 4) + i * 2, 0, SEGLEN - 1);
    seg.setPixelColor(pos, m.color_blend(seg.SEGCOLOR(1),
      seg.color_from_palette(Math.floor(m.getNow() / 4) + i * 2, false, false, 0), my_sampleAgc));
  }
};

Effects1D[131] = function matripix(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0 || !seg._matPixels) {
    seg._matPixels = new Uint32Array(SEGLEN);
  }
  const pixels = seg._matPixels;

  const vol = seg.getVolume();

  const secondHand = (Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500)) % 16;
  if (secondHand !== (seg.aux0 || 0)) {
    seg.aux0 = secondHand;

    const pixBri = Math.min(255, Math.floor(vol * seg.intensity / 64)) & 0xFF;
    const k = SEGLEN - 1;

    for (let i = 0; i < k; i++) {
      pixels[i] = pixels[i + 1];
      seg.setPixelColor(i, pixels[i]);
    }
    pixels[k] = m.color_blend(seg.SEGCOLOR(1),
      seg.color_from_palette(m.getNow() & 0xFF, false, false, 0), pixBri);
    seg.setPixelColor(k, pixels[k]);
  }
};

Effects1D[133] = function plasmoid(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const vol = seg.getVolume();
  if (!seg._plasData) seg._plasData = { thisphase: 0, thatphase: 0 };
  seg.fadeToBlackBy(32);
  seg._plasData.thisphase += m.beatsin8(6, -4, 4);
  seg._plasData.thatphase += m.beatsin8(7, -4, 4);
  for (let i = 0; i < SEGLEN; i++) {
    let thisbright = Math.floor(m.cubicwave8(((i * (1 + Math.floor(3 * seg.speed / 32)) + seg._plasData.thisphase) & 0xFF)) / 2);
    thisbright += Math.floor(m.cos8(((i * (97 + Math.floor(5 * seg.speed / 32)) + seg._plasData.thatphase) & 0xFF)) / 2);
    const colorIndex = thisbright;
    if (vol * seg.intensity / 64 < thisbright) thisbright = 0;
    seg.addPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(colorIndex, false, false, 0), thisbright));
  }
};

function _puddles_base(seg, peakdetect) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  let size = 0;
  const fadeVal = m.map(seg.speed, 0, 255, 224, 254);
  const pos = m.random16(SEGLEN);
  seg.fade_out(fadeVal);

  const volumeRaw = seg.getVolume();
  const samplePeak = seg.getSamplePeak();
  const volumeSmth = seg.getVolume();

  if (peakdetect) {
    if (samplePeak === 1) {
      size = Math.floor(volumeSmth * seg.intensity / 256 / 4) + 1;
      if (pos + size >= SEGLEN) size = SEGLEN - pos;
    }
  } else {
    if (volumeRaw > 1) {
      size = Math.floor(volumeRaw * seg.intensity / 256 / 8) + 1;
      if (pos + size >= SEGLEN) size = SEGLEN - pos;
    }
  }
  for (let i = 0; i < size; i++) {
    seg.setPixelColor(pos + i, seg.color_from_palette(m.getNow() & 0xFF, false, false, 0));
  }
}

Effects1D[134] = function puddles(seg) { _puddles_base(seg, false); };

Effects1D[135] = function midnoise(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  seg.fade_out(seg.speed);
  seg.fade_out(seg.speed);

  const vol = seg.getVolume();
  let tmpSound2 = vol * seg.intensity / 256.0;
  tmpSound2 *= seg.intensity / 128.0;
  let maxLen = Math.floor(tmpSound2 * (SEGLEN / 2) / 127.0);
  if (maxLen > Math.floor(SEGLEN / 2)) maxLen = Math.floor(SEGLEN / 2);

  for (let i = Math.floor(SEGLEN / 2) - maxLen; i < Math.floor(SEGLEN / 2) + maxLen; i++) {
    const index = m.perlin8(Math.floor(i * vol + seg.aux0), seg.aux1 + Math.floor(i * vol));
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0));
  }
  seg.aux0 += m.beatsin8(5, 0, 10);
  seg.aux1 += m.beatsin8(4, 0, 10);
};

Effects1D[136] = function noisemeter(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const vol = seg.getVolume();
  const fadeRate = m.map(seg.speed, 0, 255, 200, 254);
  seg.fade_out(fadeRate);

  const tmpSound2 = vol * 2.0 * seg.intensity / 255.0;
  let maxLen = Math.floor(tmpSound2 * SEGLEN / 255.0);
  if (maxLen < 0) maxLen = 0;
  if (maxLen > SEGLEN) maxLen = SEGLEN;

  for (let i = 0; i < maxLen; i++) {
    const index = m.perlin8(Math.floor(i * vol + seg.aux0), seg.aux1 + Math.floor(i * vol));
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0));
  }
  seg.aux0 += m.beatsin8(5, 0, 10);
  seg.aux1 += m.beatsin8(4, 0, 10);
};

Effects1D[137] = function freqwave(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (seg.call === 0) seg.fill(0);

  const MAX_FREQUENCY = 11025;
  let FFT_MajorPeak = seg.getMajorPeak();
  const volumeSmth = seg.getVolume();

  const secondHand = Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) % 16;
  if (seg.aux0 !== secondHand) {
    seg.aux0 = secondHand;

    const sensitivity = m.mapf(seg.custom3 || 1, 1, 31, 1, 10);
    const pixVal = Math.min(255.0, volumeSmth * seg.intensity / 256.0 * sensitivity);
    const intensity = pixVal / 255.0;

    let color = 0;
    if (FFT_MajorPeak > MAX_FREQUENCY) FFT_MajorPeak = 1.0;

    if (FFT_MajorPeak < 80) {
      color = 0;
    } else {
      const upperLimit = 80 + 42 * seg.custom2;
      const lowerLimit = 80 + 3 * seg.custom1;
      let i;
      if (lowerLimit !== upperLimit) {
        i = m.map(Math.floor(FFT_MajorPeak), lowerLimit, upperLimit, 0, 255) & 0xFF;
      } else {
        i = Math.floor(FFT_MajorPeak) & 0xFF;
      }
      const b = Math.min(255, Math.floor(255.0 * intensity));
      color = m.hsv2rgb(i, 240, b);
    }

    seg.setPixelColor(Math.floor(SEGLEN / 2), color);
    for (let i = SEGLEN - 1; i > Math.floor(SEGLEN / 2); i--) seg.setPixelColor(i, seg.getPixelColor(i - 1));
    for (let i = 0; i < Math.floor(SEGLEN / 2); i++) seg.setPixelColor(i, seg.getPixelColor(i + 1));
  }
};

Effects1D[138] = function freqmatrix(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (seg.call === 0) seg.fill(0);

  const MAX_FREQUENCY = 11025;
  let FFT_MajorPeak = seg.getMajorPeak();
  const volumeSmth = seg.getVolume();

  const secondHand = Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) % 16;
  if (seg.aux0 !== secondHand) {
    seg.aux0 = secondHand;

    const sensitivity = m.map(seg.custom3 || 0, 0, 31, 1, 10);
    let pixVal = Math.floor(volumeSmth * seg.intensity * sensitivity / 256.0);
    if (pixVal > 255) pixVal = 255;
    const intensity = m.map(pixVal, 0, 255, 0, 100) / 100.0;

    let color = 0;
    if (FFT_MajorPeak > MAX_FREQUENCY) FFT_MajorPeak = 1;

    if (FFT_MajorPeak < 80) {
      color = 0;
    } else {
      const upperLimit = 80 + 42 * seg.custom2;
      const lowerLimit = 80 + 3 * seg.custom1;
      let i;
      if (lowerLimit !== upperLimit) {
        i = m.map(Math.floor(FFT_MajorPeak), lowerLimit, upperLimit, 0, 255) & 0xFF;
      } else {
        i = Math.floor(FFT_MajorPeak) & 0xFF;
      }
      let b = Math.floor(255 * intensity);
      if (b > 255) b = 255;
      color = m.hsv2rgb(i, 240, b);
    }

    seg.setPixelColor(0, color);
    for (let i = SEGLEN - 1; i > 0; i--) seg.setPixelColor(i, seg.getPixelColor(i - 1));
  }
};

Effects1D[140] = function waterfall(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const dataSize = SEGLEN * 4;
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    const buf = new Uint32Array(seg.data.buffer, 0, SEGLEN);
    buf.fill(0);
    seg.aux0 = 255;
  }
  const pixels = new Uint32Array(seg.data.buffer, 0, SEGLEN);

  let FFT_MajorPeak = seg.getMajorPeak();
  const samplePeak = seg.getSamplePeak();
  const my_magnitude = seg.getMagnitude() / 8.0;

  const secondHand = (Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) + 1) % 16;
  if (seg.aux0 !== secondHand) {
    seg.aux0 = secondHand;

    let pixCol = Math.floor((Math.log10(FFT_MajorPeak) - 2.26) * 150);
    if (FFT_MajorPeak < 182.0) pixCol = 0;

    const k = SEGLEN - 1;
    if (samplePeak) {
      pixels[k] = m.hsv2rgb(92, 92, 92);
    } else {
      pixels[k] = m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(((pixCol + seg.intensity) & 0xFF), false, false, 0), Math.min(255, Math.floor(my_magnitude)) & 0xFF);
    }
    seg.setPixelColor(k, pixels[k]);
    for (let i = 0; i < k; i++) {
      pixels[i] = pixels[i + 1];
      seg.setPixelColor(i, pixels[i]);
    }
  }
};

Effects1D[141] = function freqpixels(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const MAX_FREQ_LOG10 = 4.04238;
  let FFT_MajorPeak = seg.getMajorPeak();
  const my_magnitude = seg.getMagnitude() / 16.0;

  let fadeRate = m.map(seg.speed * seg.speed, 0, 65535, 1, 255);
  const fadeoutDelay = Math.floor((256 - seg.speed) / 64);
  if (fadeoutDelay <= 1 || (seg.call % fadeoutDelay) === 0) seg.fade_out(fadeRate);

  let pixCol = Math.floor((Math.log10(FFT_MajorPeak) - 1.78) * 255.0 / (MAX_FREQ_LOG10 - 1.78));
  if (FFT_MajorPeak < 61.0) pixCol = 0;

  for (let i = 0; i < Math.floor(seg.intensity / 32) + 1; i++) {
    const locn = m.random16(SEGLEN);
    seg.setPixelColor(locn, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(((seg.intensity + pixCol) & 0xFF), false, false, 0), Math.min(255, Math.floor(my_magnitude)) & 0xFF));
  }
};

Effects1D[143] = function noisefire(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const vol = seg.getVolume();

  if (seg.call === 0) seg.fill(0);

  if (!seg._firePal) {
    seg._firePal = [
      m.hsv2rgb(0, 255, 2), m.hsv2rgb(0, 255, 4), m.hsv2rgb(0, 255, 8), m.hsv2rgb(0, 255, 8),
      m.hsv2rgb(0, 255, 16), 0xFF0000, 0xFF0000, 0xFF0000,
      0xFF8C00, 0xFF8C00, 0xFFA500, 0xFFA500,
      0xFFFF00, 0xFFA500, 0xFFFF00, 0xFFFF00
    ];
  }

  for (let i = 0; i < SEGLEN; i++) {
    let index = m.perlin8(Math.floor(i * seg.speed / 64), Math.floor(m.getNow() * seg.speed / 64 * SEGLEN / 255));
    index = Math.floor((255 - i * 256 / SEGLEN) * index / (256 - seg.intensity));
    index = m.constrain(index, 0, 255);
    const palIdx = Math.floor(index * 15 / 255);
    const bri = Math.min(255, vol * 2);
    seg.setPixelColor(i, m.color_fade(seg._firePal[m.constrain(palIdx, 0, 15)], bri));
  }
};

Effects1D[144] = function puddlepeak(seg) {
  _puddles_base(seg, true);
};

Effects1D[145] = function noisemove(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const fadeoutDelay = Math.floor((256 - seg.speed) / 96);
  if (fadeoutDelay <= 1 || (seg.call % fadeoutDelay) === 0) seg.fadeToBlackBy(4 + Math.floor(seg.speed / 4));

  const numBins = m.map(seg.intensity, 0, 255, 0, 16);
  for (let i = 0; i < numBins; i++) {
    const locn = m.perlin16(Math.floor(m.getNow() * seg.speed) + i * 50000, Math.floor(m.getNow() * seg.speed));
    const pixloc = m.map(locn, 7500, 58000, 0, SEGLEN - 1);
    const fftVal = seg.getFFT(i % 16);
    seg.setPixelColor(pixloc, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(i * 64, false, false, 0), Math.min(255, fftVal * 4)));
  }
};

Effects1D[148] = function ripplePeak(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const MAXSTEPS = 16;
  const maxRipples = 16;
  if (seg.call === 0 || !seg.data || seg.data.length < maxRipples * 4) {
    seg.allocateData(maxRipples * 4);
    for (let i = 0; i < maxRipples; i++) seg.data[i * 4] = 254;
  }

  const samplePeak = seg.getSamplePeak();
  let FFT_MajorPeak = seg.getMajorPeak();

  seg.fade_out(240);
  seg.fade_out(240);

  for (let i = 0; i < Math.floor(seg.intensity / 16); i++) {
    const off = i * 4;
    if (samplePeak) seg.data[off] = 255;

    const state = seg.data[off];
    if (state === 254) continue;

    if (state === 255) {
      seg.data[off + 2] = m.random16(SEGLEN) & 0xFF;
      seg.data[off + 3] = (m.random16(SEGLEN) >> 8) & 0xFF;
      let colorVal = 0;
      if (FFT_MajorPeak > 1) {
        colorVal = Math.floor(Math.log10(FFT_MajorPeak) * 128);
      }
      seg.data[off + 1] = colorVal & 0xFF;
      seg.data[off] = 0;
    } else if (state === 0) {
      const pos = seg.data[off + 2] | (seg.data[off + 3] << 8);
      if (pos < SEGLEN) seg.setPixelColor(pos, seg.color_from_palette(seg.data[off + 1], false, false, 0));
      seg.data[off]++;
    } else if (state >= MAXSTEPS) {
      seg.data[off] = 254;
    } else {
      const pos = seg.data[off + 2] | (seg.data[off + 3] << 8);
      const col = seg.color_from_palette(seg.data[off + 1], false, false, 0);
      const bri = Math.min(255, Math.floor(2 * 255 / state));
      seg.setPixelColor((pos + state + SEGLEN) % SEGLEN, m.color_blend(seg.SEGCOLOR(1), col, bri));
      seg.setPixelColor((pos - state + SEGLEN) % SEGLEN, m.color_blend(seg.SEGCOLOR(1), col, bri));
      seg.data[off]++;
    }
  }
};

Effects1D[151] = function pacman(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const YELLOW = 0xFFFF00, RED = 0xFF0000, BLUE = 0x0000FF;
  const PURPLEISH = 0xB000B0, ORANGEISH = 0xFF8800, WHITEISH = 0x999999, ORANGEYELLOW = 0xFFCC00;
  const ghostColorArr = [RED, PURPLEISH, 0x00FFFF, ORANGEISH];
  const numGhosts = m.map(seg.custom3 || 0, 0, 31, 2, 8);
  const numPowerDots = Math.max(1, m.map(seg.intensity, 0, 255, 1, Math.min(Math.floor(SEGLEN / 10), 255)));
  const smearMode = seg.check2;

  if (SEGLEN <= 16 + 2 * numGhosts) return;

  if (seg.call === 0 || !seg._pac) {
    seg._pac = {
      chars: [],
      counter: 0
    };
    seg._pac.chars[0] = { pos: 0, topPos: 0, color: YELLOW, direction: true, blue: false, eaten: false };
    for (let i = 1; i <= numGhosts; i++) {
      seg._pac.chars[i] = { pos: -2 * (i + 1), topPos: 0, color: ghostColorArr[(i - 1) % 4], direction: true, blue: false, eaten: false };
    }
    for (let i = 0; i < numPowerDots; i++) {
      seg._pac.chars[numGhosts + 1 + i] = { pos: 0, color: ORANGEYELLOW, direction: true, blue: false, eaten: false };
    }
    seg._pac.chars[numGhosts + 1].pos = SEGLEN - 1;
  }

  const p = seg._pac;
  if (m.getNow() > seg.step) { seg.step = m.getNow(); p.counter++; }

  if (!smearMode) seg.fill(0);

  const everyXLeds = Math.floor(((SEGLEN - 10) * 256) / numPowerDots);
  for (let i = 1; i < numPowerDots; i++) {
    p.chars[numGhosts + 1 + i].pos = 10 + Math.floor(i * everyXLeds / 256);
  }

  if (p.counter % 10 === 0) {
    const dotColor = (p.chars[numGhosts + 1].color === ORANGEYELLOW) ? 0 : ORANGEYELLOW;
    for (let i = 0; i < numPowerDots; i++) p.chars[numGhosts + 1 + i].color = dotColor;
  }

  if (seg.check1) {
    const step = seg.check3 ? 1 : 2;
    for (let i = SEGLEN - 1; i > p.chars[0].topPos; i -= step) seg.setPixelColor(i, WHITEISH);
  }

  for (let i = 0; i < numPowerDots; i++) {
    const d = p.chars[numGhosts + 1 + i];
    if (!d.eaten && d.pos >= 0 && d.pos < SEGLEN) seg.setPixelColor(d.pos, d.color);
  }

  for (let j = 0; j < numPowerDots; j++) {
    const dot = p.chars[numGhosts + 1 + j];
    if (p.chars[0].pos === dot.pos && !dot.eaten) {
      for (let i = 0; i <= numGhosts; i++) p.chars[i].direction = false;
      for (let i = 1; i <= numGhosts; i++) { p.chars[i].color = BLUE; p.chars[i].blue = true; }
      dot.eaten = true;
      break;
    }
  }

  if (p.chars[1].blue && p.chars[0].pos <= 0) {
    for (let i = 0; i <= numGhosts; i++) p.chars[i].direction = true;
    for (let i = 1; i <= numGhosts; i++) { p.chars[i].color = ghostColorArr[(i - 1) % 4]; p.chars[i].blue = false; }
    if (p.chars[numGhosts + 1].eaten) {
      for (let i = 0; i < numPowerDots; i++) p.chars[numGhosts + 1 + i].eaten = false;
      p.chars[0].topPos = 0;
    }
  }

  const maxBlinkPos = Math.max(20, p.chars[0].topPos);
  const startBlink = SEGLEN < 64 ? Math.floor(SEGLEN / 3) : m.map(seg.custom1 || 0, 0, 255, 20, maxBlinkPos);
  if (p.counter % 15 === 0 && p.chars[1].blue && p.chars[0].pos <= startBlink) {
    const gc = (p.chars[1].color === BLUE) ? WHITEISH : BLUE;
    for (let i = 1; i <= numGhosts; i++) p.chars[i].color = gc;
  }

  if (p.counter % m.map(seg.speed, 0, 255, 15, 1) === 0) {
    p.chars[0].pos += p.chars[0].direction ? 1 : -1;
    for (let i = 1; i <= numGhosts; i++) p.chars[i].pos += p.chars[i].direction ? 1 : -1;
  }

  if (p.chars[0].pos >= 0 && p.chars[0].pos < SEGLEN) seg.setPixelColor(p.chars[0].pos, p.chars[0].color);
  for (let i = 1; i <= numGhosts; i++) {
    if (p.chars[i].pos >= 0 && p.chars[i].pos < SEGLEN) seg.setPixelColor(p.chars[i].pos, p.chars[i].color);
  }
  if (p.chars[0].topPos < p.chars[0].pos) p.chars[0].topPos = p.chars[0].pos;

  seg.blur(Math.floor((seg.custom2 || 0) / 2));
};

Effects1D[155] = function freqmap(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const MAX_FREQ_LOG10 = 4.04238;
  let FFT_MajorPeak = seg.getMajorPeak();
  const my_magnitude = seg.getMagnitude() / 4.0;

  if (seg.call === 0) seg.fill(0);
  const fadeoutDelay = Math.floor((256 - seg.speed) / 32);
  if (fadeoutDelay <= 1 || (seg.call % fadeoutDelay) === 0) seg.fade_out(seg.speed);

  let locn = Math.floor((Math.log10(FFT_MajorPeak) - 1.78) * SEGLEN / (MAX_FREQ_LOG10 - 1.78));
  if (locn < 1) locn = 0;
  if (locn >= SEGLEN) locn = SEGLEN - 1;

  let pixCol = Math.floor((Math.log10(FFT_MajorPeak) - 1.78) * 255.0 / (MAX_FREQ_LOG10 - 1.78));
  if (FFT_MajorPeak < 61.0) pixCol = 0;

  const bright = Math.min(255, Math.floor(my_magnitude)) & 0xFF;
  seg.setPixelColor(locn, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(((seg.intensity + pixCol) & 0xFF), false, false, 0), bright));
};

Effects1D[157] = function gravcentric(seg) { _gravcenter_base(seg, 1); };

Effects1D[158] = function gravfreq(seg) { _gravcenter_base(seg, 3); };

Effects1D[159] = function djLight(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const mid = Math.floor(SEGLEN / 2);
  if (seg.call === 0) seg.fill(0);

  const secondHand = (Math.floor(m.getNow() * 1000 / (256 - seg.speed) / 500) + 1) % 64;
  if (seg.aux0 !== secondHand) {
    seg.aux0 = secondHand;
    const r = Math.floor(seg.getFFT(15) / 2);
    const g = Math.floor(seg.getFFT(5) / 2);
    const b = Math.floor(seg.getFFT(0) / 2);
    const fadeBri = m.map(seg.getFFT(4), 0, 255, 255, 4);
    const color = m.color_fade(m.colorPack(r, g, b), 255 - fadeBri);
    seg.setPixelColor(mid, color);
    for (let i = SEGLEN - 1; i > mid; i--) seg.setPixelColor(i, seg.getPixelColor(i - 1));
    for (let i = 0; i < mid; i++) seg.setPixelColor(i, seg.getPixelColor(i + 1));
  }
};

Effects1D[163] = function blurz(seg) {
  // Blurz. By Andrew Tuline.
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (seg.call === 0) {
    seg.fill(0);
    seg.aux0 = 0;
  }

  const fadeoutDelay = Math.floor((256 - seg.speed) / 32);
  if (fadeoutDelay <= 1 || (seg.call % fadeoutDelay) === 0) {
    seg.fade_out(seg.speed);
  }

  const speedFormulaL = 5 + Math.floor(50 * (255 - seg.speed) / SEGLEN);

  seg.step += m.FRAMETIME;
  if (seg.step > speedFormulaL) {
    const segLoc = m.random16(SEGLEN);
    const fftBand = seg.aux0 % 16;
    const fftVal = seg.getFFT(fftBand);
    const palIdx = Math.floor(2 * fftVal * 240 / Math.max(1, SEGLEN - 1));
    const bri = (2 * fftVal) & 0xFF;
    const col = m.color_blend(
      seg.SEGCOLOR(1),
      seg.color_from_palette(palIdx, false, false, 0),
      bri
    );
    seg.setPixelColor(segLoc, col);
    seg.aux0 = (seg.aux0 + 1) % 16;

    seg.step = 1;
    seg.blur(seg.intensity);
  }
};

Effects1D[185] = function rocktaves(seg) {
  // Rocktaves. By Andrew Tuline.
  const m = M();
  const SEGLEN = seg.vLength();
  seg.fadeToBlackBy(16);

  let FFT_MajorPeak = seg.getMajorPeak();
  const my_magnitude = seg.getMagnitude() / 16.0;

  let frTemp = FFT_MajorPeak;
  let octCount = 0;
  let volTemp = 0;

  volTemp = Math.floor(32.0 + my_magnitude * 1.5);
  if (my_magnitude < 48) volTemp = 0;
  if (my_magnitude > 144) volTemp = 255;
  volTemp = Math.min(255, volTemp);

  while (frTemp > 249) { octCount++; frTemp = frTemp / 2; }

  frTemp -= 132.0;
  frTemp = Math.abs(frTemp * 2.1);

  const beat = m.beatsin8(8 + octCount * 4, 0, 255, 0, octCount * 8);
  const i = m.constrain(m.map(beat, 0, 255, 0, SEGLEN - 1), 0, SEGLEN - 1);
  seg.addPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(Math.floor(frTemp) & 0xFF, false, false, 0), volTemp));
};

function _dissolve(seg, color) {
  const m = M();
  const SEGLEN = seg.vLength();

  const dataSize = SEGLEN * 4;
  if (seg.call === 0 || !seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
    const c1 = seg.SEGCOLOR(1);
    for (let i = 0; i < SEGLEN; i++) {
      const off = i * 4;
      seg.data[off]     = (c1 >> 24) & 0xFF;
      seg.data[off + 1] = (c1 >> 16) & 0xFF;
      seg.data[off + 2] = (c1 >> 8) & 0xFF;
      seg.data[off + 3] = c1 & 0xFF;
    }
    seg.aux0 = 1;
  }

  function getPixel(idx) {
    const off = idx * 4;
    return ((seg.data[off] << 24) | (seg.data[off+1] << 16) | (seg.data[off+2] << 8) | seg.data[off+3]) >>> 0;
  }
  function setPixel(idx, c) {
    const off = idx * 4;
    seg.data[off]     = (c >> 24) & 0xFF;
    seg.data[off + 1] = (c >> 16) & 0xFF;
    seg.data[off + 2] = (c >> 8) & 0xFF;
    seg.data[off + 3] = c & 0xFF;
  }

  const c1 = seg.SEGCOLOR(1);

  for (let j = 0; j <= Math.floor(SEGLEN / 15); j++) {
    if (m.random8() <= seg.intensity) {
      for (let times = 0; times < 10; times++) {
        const i = m.random16(SEGLEN);
        if (seg.aux0) {
          if (getPixel(i) === c1) {
            setPixel(i, color === seg.SEGCOLOR(0)
              ? seg.color_from_palette(i, true, false, 0)
              : color);
            break;
          }
        } else {
          if (getPixel(i) !== c1) {
            setPixel(i, c1);
            break;
          }
        }
      }
    }
  }

  let incompletePixels = 0;
  for (let i = 0; i < SEGLEN; i++) {
    const px = getPixel(i);
    seg.setPixelColor(i, px);
    if (seg.check2) {
      if (seg.aux0) {
        if (px === c1) incompletePixels++;
      } else {
        if (px !== c1) incompletePixels++;
      }
    }
  }

  if (seg.step > (255 - seg.speed) + 15) {
    seg.aux0 = seg.aux0 ? 0 : 1;
    seg.step = 0;
  } else {
    if (seg.check2) {
      if (incompletePixels === 0) seg.step++;
    } else {
      seg.step++;
    }
  }
}

Effects1D[18] = function dissolve(seg) {
  const m = M();
  _dissolve(seg, seg.check1 ? seg.color_wheel(m.random8()) : seg.SEGCOLOR(0));
};

Effects1D[19] = function dissolveRnd(seg) {
  const m = M();
  _dissolve(seg, seg.color_wheel(m.random8()));
};

Effects1D[21] = function sparkleDark(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.check2) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
    }
  }

  if (m.getNow() - seg.aux0 > seg.step) {
    if (m.random8((255 - seg.intensity) >> 4) === 0) {
      seg.setPixelColor(m.random16(SEGLEN), seg.SEGCOLOR(1));
    }
    seg.step = m.getNow();
    seg.aux0 = 255 - seg.speed;
  }
};

Effects1D[22] = function sparklePlus(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (!seg.check2) {
    for (let i = 0; i < SEGLEN; i++) {
      seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
    }
  }

  if (m.getNow() - seg.aux0 > seg.step) {
    if (m.random8((255 - seg.intensity) >> 4) === 0) {
      const len = Math.max(1, Math.floor(SEGLEN / 3));
      for (let i = 0; i < len; i++) {
        seg.setPixelColor(m.random16(SEGLEN), seg.SEGCOLOR(1));
      }
    }
    seg.step = m.getNow();
    seg.aux0 = 255 - seg.speed;
  }
};

Effects1D[24] = function mode_strobe_rainbow(seg) {
  const m = M();
  _blink(seg, seg.color_wheel(seg.call & 0xFF), seg.SEGCOLOR(1), true, false);
};

Effects1D[25] = function strobeMega(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette(i, true, false, 1));
  }

  seg.aux0 = 50 + 20 * (255 - seg.speed);
  const count = 2 * (Math.floor(seg.intensity / 10) + 1);
  if (seg.aux1 < count) {
    if ((seg.aux1 & 1) === 0) {
      seg.fill(seg.SEGCOLOR(0));
      seg.aux0 = 15;
    } else {
      seg.aux0 = 50;
    }
  }

  if (m.getNow() - seg.aux0 > seg.step) {
    seg.aux1++;
    if (seg.aux1 > count) seg.aux1 = 0;
    seg.step = m.getNow();
  }
};

Effects1D[26] = function mode_blink_rainbow(seg) {
  const m = M();
  _blink(seg, seg.color_wheel(seg.call & 0xFF), seg.SEGCOLOR(1), false, false);
};

Effects1D[29] = function chaseRandom(seg) {
  _chase(seg, seg.SEGCOLOR(1), seg.SEGCOLOR(2) ? seg.SEGCOLOR(2) : seg.SEGCOLOR(0), seg.SEGCOLOR(0), false);
};

Effects1D[30] = function chaseRainbow(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let color_sep = Math.floor(256 / SEGLEN);
  if (color_sep === 0) color_sep = 1;
  const color_index = seg.call & 0xFF;
  const color = seg.color_wheel(((seg.step * color_sep) + color_index) & 0xFF);

  _chase(seg, color, seg.SEGCOLOR(0), seg.SEGCOLOR(1), false);
};

Effects1D[31] = function chaseFlash(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const FLASH_COUNT = 4;
  let advance = true;
  const flash_step = seg.aux1 % ((FLASH_COUNT * 2) + 1);

  if (m.getNow() < seg.step) {
    advance = false;
  } else {
    seg.aux1++;
  }

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette(i, true, false, 0));
  }

  const index = seg.aux0;
  const n = index;
  const mp1 = (index + 1) % SEGLEN;

  let delay = 10 + Math.floor((30 * (255 - seg.speed)) / SEGLEN);
  if (flash_step < (FLASH_COUNT * 2)) {
    if (flash_step % 2 === 0) {
      seg.setPixelColor(n, seg.SEGCOLOR(1));
      seg.setPixelColor(mp1, seg.SEGCOLOR(1));
      delay = 20;
    } else {
      delay = 30;
    }
  } else if (advance) {
    seg.aux0 = mp1;
  }

  if (advance) {
    seg.step = m.getNow() + delay;
  }
};

Effects1D[32] = function chaseFlashRnd(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const FLASH_COUNT = 4;
  let advance = true;

  if (m.getNow() < seg.step) {
    seg.call--;
    advance = false;
  }
  const flash_step = seg.call % ((FLASH_COUNT * 2) + 1);

  for (let i = 0; i < seg.aux1; i++) {
    seg.setPixelColor(i, seg.color_wheel(seg.aux0));
  }

  let delay = 1 + Math.floor((10 * (255 - seg.speed)) / SEGLEN);
  if (flash_step < (FLASH_COUNT * 2)) {
    const n = seg.aux1;
    const mp1 = (seg.aux1 + 1) % SEGLEN;
    if (flash_step % 2 === 0) {
      seg.setPixelColor(n, seg.SEGCOLOR(0));
      seg.setPixelColor(mp1, seg.SEGCOLOR(0));
      delay = 20;
    } else {
      seg.setPixelColor(n, seg.color_wheel(seg.aux0));
      seg.setPixelColor(mp1, seg.SEGCOLOR(1));
      delay = 30;
    }
  } else if (advance) {
    seg.aux1 = (seg.aux1 + 1) % SEGLEN;
    if (seg.aux1 === 0) {
      seg.aux0 = m.get_random_wheel_index(seg.aux0);
    }
  }

  if (advance) {
    seg.step = m.getNow() + delay;
  }
};

Effects1D[33] = function rainbowRunner(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const n = seg.step;
  const mp1 = (seg.step + 1) % SEGLEN;
  const color2 = seg.color_wheel((Math.floor(n * 256 / Math.max(1, SEGLEN)) + (seg.call & 0xFF)) & 0xFF);
  const color3 = seg.color_wheel((Math.floor(mp1 * 256 / Math.max(1, SEGLEN)) + (seg.call & 0xFF)) & 0xFF);

  _chase(seg, seg.SEGCOLOR(0), color2, color3, false);
};

Effects1D[35] = function trafficLight(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (SEGLEN <= 1) return;

  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette(i, true, false, 1));
  }

  let mdelay = 500;
  for (let i = 0; i < SEGLEN - 2; i += 3) {
    switch (seg.aux0) {
      case 0:
        seg.setPixelColor(i, 0x00FF0000);
        mdelay = 150 + (100 * (255 - seg.speed));
        break;
      case 1:
        seg.setPixelColor(i, 0x00FF0000);
        mdelay = 150 + (20 * (255 - seg.speed));
        seg.setPixelColor(i + 1, 0x00EECC00);
        break;
      case 2:
        seg.setPixelColor(i + 2, 0x0000FF00);
        mdelay = 150 + (100 * (255 - seg.speed));
        break;
      case 3:
        seg.setPixelColor(i + 1, 0x00EECC00);
        mdelay = 150 + (20 * (255 - seg.speed));
        break;
    }
  }

  if (m.getNow() - seg.step > mdelay) {
    seg.aux0++;
    if (seg.aux0 === 1 && seg.intensity > 140) seg.aux0 = 2;
    if (seg.aux0 > 3) seg.aux0 = 0;
    seg.step = m.getNow();
  }
};

Effects1D[36] = function mode_color_sweep_random(seg) {
  _color_wipe(seg, true, true);
};

Effects1D[39] = function mode_running_random(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const cycleTime = 25 + (3 * (255 - seg.speed));
  const it = Math.floor(m.getNow() / cycleTime);
  if (seg.call === 0) seg.aux0 = m.jsRandom16();

  const zoneSize = ((255 - seg.intensity) >> 4) + 1;
  let PRNG16 = seg.aux0;

  let z = it % zoneSize;
  let nzone = (!z && it !== seg.aux1);
  for (let i = SEGLEN - 1; i >= 0; i--) {
    if (nzone || z >= zoneSize) {
      let lastrand = PRNG16 >> 8;
      let diff = 0;
      while (Math.abs(diff) < 42) {
        PRNG16 = ((PRNG16 * 2053) + 13849) & 0xFFFF;
        diff = ((PRNG16 >> 8) - lastrand) << 16 >> 16;
      }
      if (nzone) {
        seg.aux0 = PRNG16;
        nzone = false;
      }
      z = 0;
    }
    seg.setPixelColor(i, seg.color_wheel(PRNG16 >> 8));
    z++;
  }

  seg.aux1 = it;
};

Effects1D[47] = function mode_loading(seg) {
  _gradient_base(seg, true);
};

Effects1D[48] = function rollingBalls(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const maxNumBalls = 16;
  const ballSize = 20;
  const dataSize = ballSize * maxNumBalls;

  if (!seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
  }

  const numBalls = Math.floor(seg.intensity / 16) + 1;
  const hasCol2 = seg.SEGCOLOR(2) !== 0;
  const buf = seg.data.buffer;

  if (seg.call === 0) {
    if (hasCol2) seg.fill(0); else seg.fill(seg.SEGCOLOR(1));
    for (let i = 0; i < maxNumBalls; i++) {
      const dv = new DataView(buf, i * ballSize);
      dv.setFloat64(0, m.getNow(), true);
      const vel = 20.0 * (m.random16(1000, 10000) / 10000.0);
      dv.setFloat32(8, (m.random8() < 128) ? -vel : vel, true);
      dv.setFloat32(12, m.random16(0, 10000) / 10000.0, true);
      dv.setFloat32(16, m.random16(1000, 10000) / 10000.0, true);
    }
  }

  const cfac = (m.scale8(8, 255 - seg.speed) + 1) * 20000.0;

  if (seg.check3) {
    seg.fade_out(250);
  } else {
    if (!seg.check2) seg.fill(hasCol2 ? 0 : seg.SEGCOLOR(1));
  }

  for (let i = 0; i < numBalls; i++) {
    const dv = new DataView(buf, i * ballSize);
    const lastBounce = dv.getFloat64(0, true);
    let velocity = dv.getFloat32(8, true);
    let height = dv.getFloat32(12, true);
    const mass = dv.getFloat32(16, true);

    const timeSinceLastUpdate = (m.getNow() - lastBounce) / cfac;
    let thisHeight = height + velocity * timeSinceLastUpdate;

    if (thisHeight < -0.5 || thisHeight > 1.5) {
      thisHeight = height = m.random16(0, 10000) / 10000.0;
      dv.setFloat64(0, m.getNow(), true);
      dv.setFloat32(12, height, true);
    }

    if ((thisHeight <= 0.0 && velocity < 0.0) || (thisHeight >= 1.0 && velocity > 0.0)) {
      velocity = -velocity;
      dv.setFloat64(0, m.getNow(), true);
      dv.setFloat32(8, velocity, true);
      dv.setFloat32(12, thisHeight, true);
      height = thisHeight;
    }

    if (seg.check1) {
      for (let j = i + 1; j < numBalls; j++) {
        const dvj = new DataView(buf, j * ballSize);
        const velJ = dvj.getFloat32(8, true);
        if (velJ !== velocity) {
          const heightJ = dvj.getFloat32(12, true);
          const lastBounceJ = dvj.getFloat64(0, true);
          const massJ = dvj.getFloat32(16, true);
          const tcollided = (cfac * (height - heightJ) +
            velocity * (lastBounceJ - lastBounce)) / (velJ - velocity);

          if (tcollided > 2.0 && tcollided < (m.getNow() - lastBounceJ)) {
            const newHeight = height + velocity * (tcollided + (lastBounceJ - lastBounce)) / cfac;
            const newBounceTime = Math.round(tcollided + 0.5) + lastBounceJ;
            dv.setFloat32(12, newHeight, true);
            dvj.setFloat32(12, newHeight, true);
            dv.setFloat64(0, newBounceTime, true);
            dvj.setFloat64(0, newBounceTime, true);
            const vtmp = velocity;
            velocity = ((mass - massJ) * vtmp + 2.0 * massJ * velJ) / (mass + massJ);
            const newVelJ = ((massJ - mass) * velJ + 2.0 * mass * vtmp) / (mass + massJ);
            dv.setFloat32(8, velocity, true);
            dvj.setFloat32(8, newVelJ, true);
            thisHeight = newHeight + velocity * (m.getNow() - newBounceTime) / cfac;
            height = dv.getFloat32(12, true);
          }
        }
      }
    }

    let color = seg.SEGCOLOR(0);
    if (seg.palette) {
      color = seg.color_from_palette(Math.floor(i * 255 / numBalls), false, false, 0);
    } else if (hasCol2) {
      color = seg.SEGCOLOR(i % 3);
    }

    if (thisHeight < 0.0) thisHeight = 0.0;
    if (thisHeight > 1.0) thisHeight = 1.0;
    const pos = Math.round(thisHeight * (SEGLEN - 1));
    seg.setPixelColor(pos, color);
    dv.setFloat64(0, m.getNow(), true);
    dv.setFloat32(12, thisHeight, true);
  }
};
Effects1D[54] = function mode_chase_tricolor(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const color1 = seg.SEGCOLOR(2);
  const color2 = seg.SEGCOLOR(0);

  const cycleTime = 50 + ((255 - seg.speed) << 1);
  const it = Math.floor(m.getNow() / cycleTime);
  const width = 1 + (seg.intensity >> 4);
  let index = it % (width * 3);

  for (let i = 0; i < SEGLEN; i++, index++) {
    if (index > (width * 3) - 1) index = 0;

    let color = color1;
    if (index > (width << 1) - 1) {
      color = seg.color_from_palette(i, true, false, 1);
    } else if (index > width - 1) {
      color = color2;
    }

    seg.setPixelColor(SEGLEN - i - 1, color);
  }
};

Effects1D[58] = function mode_icu(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const now16 = m.getNow() & 0xFFFF;
  let dest = seg.aux1;
  const space = (seg.intensity >> 3) + 2;
  let state = (seg.step >> 16) & 0xFFFF;
  let nextUpdate = seg.step & 0xFFFF;

  const pindex = m.map(dest, 0, Math.max(1, SEGLEN - Math.floor(SEGLEN / space)), 0, 255);
  const col = seg.color_from_palette(pindex, false, false, 0);
  const bgcol = seg.check2 ? 0 : seg.SEGCOLOR(1);
  seg.fill(bgcol);

  if (state !== 1) {
    if (dest < SEGLEN) seg.setPixelColor(dest, col);
    const eye2pos = dest + Math.floor(SEGLEN / space);
    if (eye2pos < SEGLEN) seg.setPixelColor(eye2pos, col);
    if (state === 3) {
      if (seg.aux0 > seg.aux1) {
        dest++;
      } else if (seg.aux0 < seg.aux1) {
        dest--;
      }
      if (dest >= 0 && dest < SEGLEN) seg.setPixelColor(dest, col);
      const nextEye2 = dest + Math.floor(SEGLEN / space);
      if (nextEye2 >= 0 && nextEye2 < SEGLEN) seg.setPixelColor(nextEye2, col);
    }
  }

  if (((now16 - nextUpdate) << 16 >> 16) >= 0) {
    switch (state) {
      case 0:
        state++;
        if (m.jsRandom8(6) === 0) {
          nextUpdate = (now16 + 200) & 0xFFFF;
          break;
        }
      case 1:
        nextUpdate = (now16 + 500 + m.jsRandom16(1000)) & 0xFFFF;
        state = 2;
        break;
      case 2:
        seg.aux0 = m.jsRandom16(Math.max(1, SEGLEN - Math.floor(SEGLEN / space)));
        nextUpdate = now16;
        state = 3;
        break;
      default:
        seg.aux1 = dest;
        nextUpdate = (now16 + m.SPEED_FORMULA_L(seg.speed, SEGLEN)) & 0xFFFF;
        if (seg.aux0 === dest) {
          nextUpdate = (now16 + 500 + m.jsRandom16(1000)) & 0xFFFF;
          state = 0;
        }
        break;
    }
  }

  seg.step = ((state & 0xFFFF) << 16) | (nextUpdate & 0xFFFF);
};

Effects1D[60] = function mode_dual_larson_scanner(seg) {
  const prevCheck1 = seg.check1;
  seg.check1 = true;
  Effects1D[40](seg);
  seg.check1 = prevCheck1;
};

Effects1D[61] = function stream2(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0) {
    const r = m.jsRandom8(), g = m.jsRandom8(), b = m.jsRandom8();
    seg.step = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    seg.aux0 = m.jsRandom16();
  }

  const cycleTime = 25 + 3 * (255 - seg.speed);
  const it = Math.floor(m.getNow() / cycleTime);
  let color = seg.step;
  let seed = seg.aux0;
  function prngRandom8(mod) {
    seed = ((seed * 1103515245) + 12345) & 0x7FFFFFFF;
    const val = (seed >> 16) & 0xFF;
    if (typeof mod !== 'undefined' && mod > 0) return val % mod;
    return val;
  }

  for (let i = SEGLEN - 1; i >= 0; i--) {
    const cr = (prngRandom8(6) !== 0) ? ((color >> 16) & 0xFF) : prngRandom8();
    const cg = (prngRandom8(6) !== 0) ? ((color >> 8) & 0xFF) : prngRandom8();
    const cb = (prngRandom8(6) !== 0) ? (color & 0xFF) : prngRandom8();
    color = ((cr & 0xFF) << 16) | ((cg & 0xFF) << 8) | (cb & 0xFF);
    seg.setPixelColor(i, color);
    if (i === SEGLEN - 1 && seg.aux1 !== (it & 0xFFFF)) {
      seg.step = color;
      seg.aux0 = seed;
    }
  }

  seg.aux1 = it & 0xFFFF;
};
Effects1D[70] = function noise1(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const scale = 320;
  seg.step = (seg.step || 0) + (1 + (seg.speed >> 4));

  for (let i = 0; i < SEGLEN; i++) {
    const shift_x = m.beatsin8_t(11);
    const shift_y = Math.floor(seg.step / 42);
    const real_x = (i + shift_x) * scale;
    const real_y = (i + shift_y) * scale;
    const real_z = seg.step;
    const noise = m.perlin16(real_x, real_y, real_z) >> 8;
    const index = m.sin8_t(((noise * 3) & 0xFF));
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0));
  }
};
Effects1D[71] = function noise2(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const scale = 1000;
  seg.step = (seg.step || 0) + (1 + (seg.speed >> 1));

  for (let i = 0; i < SEGLEN; i++) {
    const shift_x = seg.step >> 6;
    const real_x = (i + shift_x) * scale;
    const noise = m.perlin16(real_x, 0, 4223) >> 8;
    const index = m.sin8_t(((noise * 3) & 0xFF));
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0, noise));
  }
};
Effects1D[72] = function noise3(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const scale = 800;
  seg.step = (seg.step || 0) + (1 + seg.speed);

  for (let i = 0; i < SEGLEN; i++) {
    const shift_x = 4223;
    const shift_y = 1234;
    const real_x = (i + shift_x) * scale;
    const real_y = (i + shift_y) * scale;
    const real_z = seg.step * 8;
    const noise = m.perlin16(real_x, real_y, real_z) >> 8;
    const index = m.sin8_t(((noise * 3) & 0xFF));
    seg.setPixelColor(i, seg.color_from_palette(index, false, false, 0, noise));
  }
};
Effects1D[73] = function noise4(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const stp = ((m.getNow() * seg.speed) >> 7) >>> 0;

  for (let i = 0; i < SEGLEN; i++) {
    const index = m.perlin16(i << 12, stp);
    seg.setPixelColor(i, seg.color_from_palette(index & 0xFF, false, false, 0));
  }
};
Effects1D[77] = function copySegment(seg) {
  const SEGLEN = seg.vLength();
  for (let i = 0; i < SEGLEN; i++) {
    seg.setPixelColor(i, seg.color_from_palette(Math.floor(i * 255 / SEGLEN), false));
  }
};

Effects1D[81] = function twinklecat(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let PRNG16 = 11337;
  function nextPRNG() {
    PRNG16 = ((PRNG16 * 2053) + 1384) & 0xFFFF;
    return PRNG16;
  }

  if (seg.speed > 100) seg.aux0 = 3 + ((255 - seg.speed) >> 3);
  else seg.aux0 = 22 + ((100 - seg.speed) >> 1);

  let bg = seg.SEGCOLOR(1);
  let bglight = m.getAverageLight(bg);
  if (bglight > 64) {
    bg = m.color_fade(bg, 16, true);
  } else if (bglight > 16) {
    bg = m.color_fade(bg, 64, true);
  } else {
    bg = m.color_fade(bg, 86, true);
  }
  bg = m.gamma32inv(bg);
  const backgroundBrightness = m.getAverageLight(bg);

  for (let i = 0; i < SEGLEN; i++) {
    nextPRNG();
    const myclockoffset16 = PRNG16;
    nextPRNG();
    const myspeedmultiplierQ5_3 = ((((PRNG16 & 0xFF) >> 4) + (PRNG16 & 0x0F)) & 0x0F) + 0x08;
    const myclock30 = (((m.getNow() * myspeedmultiplierQ5_3) >> 3) + myclockoffset16) >>> 0;
    const myunique8 = (PRNG16 >> 8) & 0xFF;

    const ticks = Math.floor(myclock30 / Math.max(1, seg.aux0));
    const fastcycle8 = ticks & 0xFF;
    let slowcycle16 = ((ticks >> 8) + myunique8) & 0xFFFF;
    slowcycle16 = (slowcycle16 + m.sin8(slowcycle16 & 0xFF)) & 0xFFFF;
    slowcycle16 = ((slowcycle16 * 2053) + 1384) & 0xFFFF;
    const slowcycle8 = ((slowcycle16 & 0xFF) + ((slowcycle16 >> 8) & 0xFF)) & 0xFF;

    const twinkleDensity = (seg.intensity >> 5) + 1;

    let bright = 0;
    if ((Math.floor((slowcycle8 & 0x0E) / 2)) < twinkleDensity) {
      const ph = fastcycle8;
      bright = 255 - ph;
      if (seg.check2) {
        bright = ph;
      }
    }

    const hue = (slowcycle8 - myunique8) & 0xFF;
    let c;
    if (bright > 0) {
      c = seg.ColorFromPalette(hue, bright, 0);
      if (!seg.check1) {
        if (fastcycle8 >= 128) {
          const cooling = (fastcycle8 - 128) >> 4;
          let cr = m.colorR(c), cg = m.colorG(c), cb = m.colorB(c);
          cg = m.qsub8(cg, cooling);
          cb = m.qsub8(cb, cooling * 2);
          c = m.colorPack(cr, cg, cb);
        }
      }
    } else {
      c = 0;
    }

    const cbright = m.getAverageLight(c);
    const deltabright = cbright - backgroundBrightness;
    if (deltabright >= 32 || bg === 0) {
      seg.setPixelColor(i, c);
    } else if (deltabright > 0) {
      seg.setPixelColor(i, m.color_blend(bg, c, Math.min(255, deltabright * 8)));
    } else {
      seg.setPixelColor(i, bg);
    }
  }
};

Effects1D[82] = function halloweenEyes(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const is2D = seg._is2D;

  const initializeOn = 0, stateOn = 1, stateBlink = 2, initializeOff = 3, stateOff = 4, stateCount = 5;

  const maxWidth = is2D ? seg.vWidth() : SEGLEN;
  const HALLOWEEN_EYE_SPACE = Math.max(2, is2D ? (seg.vWidth() >> 4) : (SEGLEN >> 5));
  const HALLOWEEN_EYE_WIDTH = HALLOWEEN_EYE_SPACE >> 1;
  const eyeLength = (2 * HALLOWEEN_EYE_WIDTH) + HALLOWEEN_EYE_SPACE;
  if (eyeLength >= maxWidth) return;

  if (seg.call === 0 || !seg.data || seg.data.length < 16) {
    seg.allocateData(16);
    seg.data.fill(0);
  }

  const dv = new DataView(seg.data.buffer);
  let state = seg.data[0] % stateCount;
  let dataColor = seg.data[1];
  let startPos = dv.getUint16(2, true);
  let duration = Math.max(1, dv.getUint16(4, true));
  let eyeY = dv.getUint16(6, true);
  let startTime = dv.getUint32(8, true);
  let blinkEndTime = dv.getUint32(12, true);

  function saveData() {
    seg.data[0] = state;
    seg.data[1] = dataColor;
    dv.setUint16(2, startPos & 0xFFFF, true);
    dv.setUint16(4, duration & 0xFFFF, true);
    dv.setUint16(6, eyeY & 0xFFFF, true);
    dv.setUint32(8, startTime >>> 0, true);
    dv.setUint32(12, blinkEndTime >>> 0, true);
  }

  if (!seg.check2) seg.fill(seg.SEGCOLOR(1));

  const now = m.getNow();
  const elapsedTime = (now - startTime) >>> 0;

  switch (state) {
    case initializeOn: {
      startPos = m.random16(0, Math.max(1, maxWidth - eyeLength - 1));
      dataColor = m.random8();
      if (is2D) eyeY = m.random16(seg.vHeight() - 1);
      duration = 128 + m.random16(seg.intensity * 64);
      state = stateOn;
    }
    case stateOn: {
      const start2ndEye = startPos + HALLOWEEN_EYE_WIDTH + HALLOWEEN_EYE_SPACE;
      duration = Math.min(duration, 128 + (seg.intensity * 64));
      const fadeInAnimationState = Math.floor(elapsedTime * 256 * 8 / Math.max(1, duration));
      const backgroundColor = seg.SEGCOLOR(1);
      const eyeColor = seg.color_from_palette(dataColor, false, false, 0);
      let c = eyeColor;

      if (fadeInAnimationState < 256) {
        c = m.color_blend(backgroundColor, eyeColor, fadeInAnimationState & 0xFF);
      } else if (elapsedTime > 1024) {
        const remainingTime = (elapsedTime >= duration) ? 0 : (duration - elapsedTime);
        if (remainingTime > 1024) {
          if (m.random8() < 4) {
            c = backgroundColor;
            state = stateBlink;
            blinkEndTime = now + m.random8(8, 128);
          }
        }
      }

      if (c !== backgroundColor) {
        for (let ei = 0; ei < HALLOWEEN_EYE_WIDTH; ei++) {
          if (is2D) {
            seg.setPixelColorXY(startPos + ei, eyeY, c);
            seg.setPixelColorXY(start2ndEye + ei, eyeY, c);
          } else {
            seg.setPixelColor(startPos + ei, c);
            seg.setPixelColor(start2ndEye + ei, c);
          }
        }
      }
      break;
    }
    case stateBlink: {
      if (now >= blinkEndTime) {
        state = stateOn;
      }
      break;
    }
    case initializeOff: {
      const eyeOffTimeBase = seg.speed * 128;
      duration = eyeOffTimeBase + m.random16(eyeOffTimeBase);
      state = stateOff;
    }
    case stateOff: {
      const eyeOffBase = seg.speed * 128;
      duration = Math.min(duration, 2 * eyeOffBase);
      break;
    }
    default: {
      state = initializeOn;
      break;
    }
  }

  if (elapsedTime > duration) {
    if (state === initializeOn || state === stateOn || state === stateBlink) {
      state = initializeOff;
    } else {
      state = initializeOn;
    }
    startTime = now;
  }

  saveData();
};

Effects1D[84] = function solidPatternTri(seg) {
  const SEGLEN = seg.vLength();
  const blockSize = Math.max(1, Math.floor(seg.intensity / 32) + 1);
  const colors = [seg.SEGCOLOR(0), seg.SEGCOLOR(1), seg.SEGCOLOR(2)];

  for (let i = 0; i < SEGLEN; i++) {
    const colorIdx = Math.floor(i / blockSize) % 3;
    seg.setPixelColor(i, colors[colorIdx]);
  }
};

Effects1D[90] = function fireworks1D(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  const is2D = seg._is2D;
  const cols = is2D ? seg.vWidth() : 1;
  const rows = is2D ? seg.vHeight() : SEGLEN;

  const sparkBytes = 20;
  const maxSparks = 53;
  let numSparks = Math.min(5 + ((rows * cols) >> 1), maxSparks);
  const dataSize = sparkBytes * numSparks + 4;

  if (!seg.data || seg._dataLen < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
    seg.aux0 = 0;
  }

  const buf = seg.data.buffer;

  function getSpark(i) {
    const off = i * sparkBytes;
    const dv = new DataView(buf, off, sparkBytes);
    return {
      get pos() { return dv.getFloat32(0, true); },
      set pos(v) { dv.setFloat32(0, v, true); },
      get posX() { return dv.getFloat32(4, true); },
      set posX(v) { dv.setFloat32(4, v, true); },
      get vel() { return dv.getFloat32(8, true); },
      set vel(v) { dv.setFloat32(8, v, true); },
      get velX() { return dv.getFloat32(12, true); },
      set velX(v) { dv.setFloat32(12, v, true); },
      get col() { return dv.getUint16(16, true); },
      set col(v) { dv.setUint16(16, v & 0xFFFF, true); },
      get colIndex() { return seg.data[off + 18]; },
      set colIndex(v) { seg.data[off + 18] = v & 0xFF; },
    };
  }

  const dgOff = sparkBytes * numSparks;
  const dgView = new DataView(buf, dgOff, 4);
  function getDyingGravity() { return dgView.getFloat32(0, true); }
  function setDyingGravity(v) { dgView.setFloat32(0, v, true); }

  if (dataSize !== seg.aux1) {
    setDyingGravity(0.0);
    seg.aux0 = 0;
    seg.aux1 = dataSize;
  }

  seg.fade_out(252);

  const flare = getSpark(0);
  let gravity = -0.0004 - (seg.speed / 800000.0);
  gravity *= rows;

  if (seg.aux0 < 2) {
    if (seg.aux0 === 0) {
      flare.pos = 0;
      if (is2D) {
        flare.posX = m.random16(2, Math.max(3, cols - 3));
      } else {
        flare.posX = (seg.intensity > m.random8()) ? 1.0 : 0.0;
      }
      let peakHeight = 75 + m.random8(180);
      peakHeight = (peakHeight * (rows - 1)) >> 8;
      flare.vel = Math.sqrt(-2.0 * gravity * peakHeight);
      flare.velX = is2D ? (m.random8(9) - 4) / 64.0 : 0;
      flare.col = 255;
      seg.aux0 = 1;
    }

    if (flare.vel > 12 * gravity) {
      if (is2D) {
        const bri = Math.min(255, Math.max(0, flare.col));
        seg.setPixelColorXY(
          Math.floor(flare.posX),
          rows - Math.floor(flare.pos) - 1,
          m.colorPack(bri, bri, bri)
        );
      } else {
        const drawPos = (Math.trunc(flare.posX) !== 0) ? rows - Math.floor(flare.pos) - 1 : Math.floor(flare.pos);
        if (drawPos >= 0 && drawPos < SEGLEN) {
          const bri = Math.min(255, Math.max(0, flare.col));
          seg.setPixelColor(drawPos, m.colorPack(bri, bri, bri));
        }
      }
      flare.pos += flare.vel;
      flare.pos = Math.max(0, Math.min(rows - 1, flare.pos));
      if (is2D) {
        flare.posX += flare.velX;
        flare.posX = Math.max(0, Math.min(cols - 1, flare.posX));
      }
      flare.vel += gravity;
      flare.col = Math.max(0, flare.col - 2);
    } else {
      seg.aux0 = 2;
    }
  } else if (seg.aux0 < 4) {
    let nSparks = Math.floor(flare.pos) + m.random8(4);
    nSparks = Math.max(nSparks, 4);
    nSparks = Math.min(nSparks, numSparks);

    if (seg.aux0 === 2) {
      for (let i = 1; i < nSparks; i++) {
        const sp = getSpark(i);
        sp.pos = flare.pos;
        sp.posX = flare.posX;
        sp.vel = (m.random16(20001) / 10000.0) - 0.9;
        if (rows < 32) sp.vel *= 0.5;
        sp.velX = is2D ? (m.random16(20001) / 10000.0) - 1.0 : 0;
        sp.col = 345;
        sp.colIndex = m.random8();
        sp.vel *= flare.pos / rows;
        sp.velX *= is2D ? flare.posX / cols : 0;
        sp.vel *= -gravity * 50;
      }
      setDyingGravity(gravity / 2);
      seg.aux0 = 3;
    }

    if (getSpark(1).col > 4) {
      for (let i = 1; i < nSparks; i++) {
        const sp = getSpark(i);
        sp.pos += sp.vel;
        sp.posX += sp.velX;
        sp.vel += getDyingGravity();
        if (is2D) sp.velX += getDyingGravity();
        if (sp.col > 3) sp.col -= 4;

        if (sp.pos > 0 && sp.pos < rows) {
          if (is2D && !(sp.posX >= 0 && sp.posX < cols)) continue;
          const prog = sp.col;
          let spColor = seg.palette ? seg.color_wheel(sp.colIndex) : seg.SEGCOLOR(0);
          let c = 0;
          if (prog > 300) {
            c = m.color_blend(spColor, 0xFFFFFF, Math.min(255, (prog - 300) * 5));
          } else if (prog > 45) {
            c = m.color_blend(0, spColor, Math.min(255, prog - 45));
            const cooling = (300 - prog) >> 5;
            let cr = m.colorR(c), cg = m.colorG(c), cb = m.colorB(c);
            cg = m.qsub8(cg, cooling);
            cb = m.qsub8(cb, cooling * 2);
            c = m.colorPack(cr, cg, cb);
          }
          if (is2D) {
            seg.setPixelColorXY(Math.floor(sp.posX), rows - Math.floor(sp.pos) - 1, c);
          } else {
            const drawPos = (Math.trunc(sp.posX) !== 0) ? rows - Math.floor(sp.pos) - 1 : Math.floor(sp.pos);
            if (drawPos >= 0 && drawPos < SEGLEN) seg.setPixelColor(drawPos, c);
          }
        }
      }
      if (seg.check3) seg.blur(16);
      setDyingGravity(getDyingGravity() * 0.8);
    } else {
      seg.aux0 = 6 + m.random8(10);
    }
  } else {
    seg.aux0--;
    if (seg.aux0 < 4) {
      seg.aux0 = 0;
    }
  }
};
Effects1D[93] = function sinelonDual(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  seg.fade_out(seg.intensity);
  const pos = m.beatsin16(Math.floor(seg.speed / 10), 0, SEGLEN - 1);
  if (seg.call === 0) seg.aux0 = pos;

  const color1 = seg.color_from_palette(pos, true, false, 0);
  let color2 = seg.SEGCOLOR(2);
  if (!color2) color2 = seg.color_from_palette(pos, true, false, 0);

  seg.setPixelColor(pos, color1);
  seg.setPixelColor(SEGLEN - 1 - pos, color2);

  if (seg.aux0 !== pos) {
    if (seg.aux0 < pos) {
      for (let i = seg.aux0; i < pos; i++) {
        seg.setPixelColor(i, color1);
        seg.setPixelColor(SEGLEN - 1 - i, color2);
      }
    } else {
      for (let i = seg.aux0; i > pos; i--) {
        seg.setPixelColor(i, color1);
        seg.setPixelColor(SEGLEN - 1 - i, color2);
      }
    }
    seg.aux0 = pos;
  }
};

Effects1D[94] = function sinelonRainbow(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;
  seg.fade_out(seg.intensity);

  const pos = m.beatsin16(Math.floor(seg.speed / 10), 0, SEGLEN - 1);
  if (seg.call === 0) seg.aux0 = pos;
  const color1 = seg.color_wheel((pos & 0x07) * 32);
  seg.setPixelColor(pos, color1);

  if (seg.aux0 !== pos) {
    if (seg.aux0 < pos) {
      for (let i = seg.aux0; i < pos; i++) {
        seg.setPixelColor(i, color1);
      }
    } else {
      for (let i = seg.aux0; i > pos; i--) {
        seg.setPixelColor(i, color1);
      }
    }
    seg.aux0 = pos;
  }
};

Effects1D[99] = function rippleRainbow(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  if (seg.call === 0) {
    seg.aux0 = m.random8();
    seg.aux1 = m.random8();
  }
  if (seg.aux0 === seg.aux1) {
    seg.aux1 = m.random8();
  } else if (seg.aux1 > seg.aux0) {
    seg.aux0++;
  } else {
    seg.aux0--;
  }
  seg.fill(m.color_blend(seg.color_wheel(seg.aux0), 0, 235));
  _ripple_base(seg);
};
Effects1D[103] = function solidGlitter(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  seg.fill(seg.SEGCOLOR(0));
  const col = seg.SEGCOLOR(2) ? seg.SEGCOLOR(2) : 0xFFFFFF;
  if (seg.intensity > m.random8()) {
    seg.setPixelColor(m.random16(SEGLEN), col);
  }
};

Effects1D[105] = function phased(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const allfreq = 16;
  if (seg.call === 0) seg._phasedPhase = 0;
  if (seg._phasedPhase === undefined) seg._phasedPhase = 0;

  const cutOff = 255 - seg.intensity;
  const modVal = 5;

  let index = Math.floor(m.getNow() / 64);
  seg._phasedPhase += seg.speed / 32.0;

  for (let i = 0; i < SEGLEN; i++) {
    let val = (i + 1) * allfreq;
    if (modVal !== 0) val += seg._phasedPhase * ((i % modVal) + 1) / 2;
    let b = m.cubicwave8(Math.floor(val) & 0xFF);
    b = (b > cutOff) ? (b - cutOff) : 0;
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(index & 0xFF, false, false, 0), b));
    index += Math.floor(256 / SEGLEN);
    if (SEGLEN > 256) index++;
  }
};

Effects1D[106] = function twinkleup(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  let prngState = 535;
  function prngRandom8() {
    prngState = ((prngState * 1103515245) + 12345) & 0x7FFFFFFF;
    return (prngState >> 16) & 0xFF;
  }

  for (let i = 0; i < SEGLEN; i++) {
    const ranstart = prngRandom8();
    let pixBri = m.sin8((ranstart + Math.floor(16 * m.getNow() / (256 - seg.speed))) & 0xFF);
    if (prngRandom8() > seg.intensity) pixBri = 0;
    const palIdx = (prngRandom8() + Math.floor(m.getNow() / 100)) & 0xFF;
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(palIdx, false, false, 0), pixBri));
  }
};

Effects1D[109] = function phasedNoise(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  const allfreq = 16;
  if (seg.call === 0) seg._phasedPhase = 0;
  if (seg._phasedPhase === undefined) seg._phasedPhase = 0;

  const cutOff = 255 - seg.intensity;

  let index = Math.floor(m.getNow() / 64);
  seg._phasedPhase += seg.speed / 32.0;

  for (let i = 0; i < SEGLEN; i++) {
    let modVal = m.perlin8(i * 10 + i * 10) >> 4;
    if (modVal === 0) modVal = 1;

    let val = (i + 1) * allfreq;
    val += seg._phasedPhase * ((i % modVal) + 1) / 2;
    let b = m.cubicwave8(Math.floor(val) & 0xFF);
    b = (b > cutOff) ? (b - cutOff) : 0;
    seg.setPixelColor(i, m.color_blend(seg.SEGCOLOR(1), seg.color_from_palette(index & 0xFF, false, false, 0), b));
    index += Math.floor(256 / SEGLEN);
    if (SEGLEN > 256) index++;
  }
};

Effects1D[112] = function dancingShadows(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  if (SEGLEN <= 1) return;

  const SPOT_MAX_COUNT = 49;
  const SPOT_TYPES_COUNT = 6;
  const numSpotlights = m.map(seg.intensity, 0, 255, 2, SPOT_MAX_COUNT);
  const initialize = (seg.aux0 !== numSpotlights);
  seg.aux0 = numSpotlights;

  const SPOT_SIZE = 16;
  const dataSize = SPOT_SIZE * numSpotlights;
  if (seg.call === 0 || !seg.data || seg.data.length < dataSize) {
    seg.allocateData(dataSize);
    seg.data.fill(0);
    seg.aux0 = 0;
  }

  seg.fill(0);

  const now = m.getNow();
  const buf = seg.data.buffer;

  for (let i = 0; i < numSpotlights; i++) {
    const off = i * SPOT_SIZE;
    const dv = new DataView(buf, off, SPOT_SIZE);
    let speed = dv.getFloat32(0, true);
    let colorIdx = seg.data[off + 4];
    let position = dv.getInt16(6, true);
    let lastUpdate = dv.getUint32(8, true);
    let width = seg.data[off + 12];
    let type = seg.data[off + 13];

    let respawn = false;

    if (!initialize) {
      const delta = Math.floor((now - lastUpdate) * speed * ((1.0 + seg.speed) / 100.0));
      if (Math.abs(delta) >= 1) {
        position += delta;
        lastUpdate = now;
      }
      respawn = (speed > 0 && position > SEGLEN + 2) || (speed < 0 && position < -(width + 2));
    }

    if (initialize || respawn) {
      colorIdx = m.random8();
      width = m.random8(1, 10);
      speed = 1.0 / m.random8(4, 50);

      if (initialize) {
        position = m.random16(SEGLEN);
        speed *= m.random8(2) ? 1.0 : -1.0;
      } else {
        if (m.random8(2)) {
          position = SEGLEN + width;
          speed *= -1.0;
        } else {
          position = -width;
        }
      }
      lastUpdate = now;
      type = m.random8(SPOT_TYPES_COUNT);
    }

    dv.setFloat32(0, speed, true);
    seg.data[off + 4] = colorIdx;
    dv.setInt16(6, position, true);
    dv.setUint32(8, lastUpdate, true);
    seg.data[off + 12] = width;
    seg.data[off + 13] = type;

    const color = seg.color_from_palette(colorIdx, false, false, 255);
    const start = position;

    if (width <= 1) {
      if (start >= 0 && start < SEGLEN) {
        seg.blendPixelColor(start, color, 128);
      }
    } else {
      switch (type) {
        case 0:
          for (let j = 0; j < width; j++) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, 128);
          }
          break;
        case 1:
          for (let j = 0; j < width; j++) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, m.cubicwave8(m.map(j, 0, width - 1, 0, 255)));
          }
          break;
        case 2:
          for (let j = 0; j < width; j++) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, m.cubicwave8((2 * m.map(j, 0, width - 1, 0, 255)) & 0xFF));
          }
          break;
        case 3:
          for (let j = 0; j < width; j += 2) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, 128);
          }
          break;
        case 4:
          for (let j = 0; j < width; j += 3) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, 128);
          }
          break;
        case 5:
          for (let j = 0; j < width; j += 4) {
            if ((start + j) >= 0 && (start + j) < SEGLEN) seg.blendPixelColor(start + j, color, 128);
          }
          break;
      }
    }
  }
};

Effects1D[117] = function dynamicSmooth(seg) {
  const m = M();
  const SEGLEN = seg.vLength();

  if (seg.call === 0 || !seg.data) {
    seg.allocateData(SEGLEN);
    for (let i = 0; i < SEGLEN; i++) seg.data[i] = m.random8();
  }
  const cycleTime = 50 + (255 - seg.speed) * 15;
  const it = Math.floor(m.getNow() / cycleTime);
  if (it !== seg.step && seg.speed !== 0) {
    for (let i = 0; i < SEGLEN; i++) {
      if (m.random8() <= seg.intensity) seg.data[i] = m.random8();
    }
    seg.step = it;
  }
  for (let i = 0; i < SEGLEN; i++) {
    seg.blendPixelColor(i, seg.color_wheel(seg.data[i]), 16);
  }
};

Effects1D[161] = function shimmer(seg) {
  const m = M();
  const SEGLEN = seg.vLength();
  const now = m.getNow();

  if (seg.call === 0 || !seg._shimData) {
    seg._shimData = { lastTime: now };
  }

  const radius = (seg.custom1 * SEGLEN >> 7) + 1;
  const traversalDistance = (SEGLEN + 2 * radius) << 8;
  const traversalTime = 200 + (255 - seg.speed) * 80;
  const speed = ((traversalDistance << 5) / traversalTime) | 0;
  let position = seg.step | 0;
  const inputstate = (seg.intensity << 8) | seg.custom1;

  if (seg.call === 0 || inputstate !== seg.aux1) {
    position = -(radius << 8);
    seg.aux0 = 0;
    seg._shimData.lastTime = now;
    seg.aux1 = inputstate;
  }

  if (seg.speed) {
    const deltaTime = (now - seg._shimData.lastTime) & 0x7F;
    seg._shimData.lastTime = now;

    if (seg.aux0 > 0) {
      seg.aux0 = (seg.aux0 > deltaTime) ? seg.aux0 - deltaTime : 0;
    } else {
      const step = 1 + ((speed * deltaTime) >> 5);
      position += step;
      const endposition = (SEGLEN + radius) << 8;
      if (position > endposition) {
        seg.aux0 = seg.intensity * 236;
        if (seg.check3) seg.aux0 = m.random16(seg.aux0 + 1000);
        position = -(radius << 8);
      }
      seg.step = position;
    }

    if (seg.check2) position = (SEGLEN << 8) - position;
  } else {
    position = (SEGLEN << 7);
  }

  for (let i = 0; i < SEGLEN; i++) {
    const dist = Math.abs(position - (i << 8));
    if (dist < (radius << 8)) {
      let color = seg.color_from_palette(Math.floor(i * 255 / SEGLEN), false, false, 0);
      const blend = Math.floor(dist / radius);
      if (seg.custom2) {
        let modVal;
        if (seg.check1) {
          modVal = ((m.sin16((i * seg.custom2 << 6) + (now * seg.custom3 << 5)) >> 8) + 128) & 0xFF;
        } else {
          modVal = (m.perlin16(i * seg.custom2 << 7, now * seg.custom3 << 5) >> 8) & 0xFF;
        }
        color = m.color_fade(color, modVal, true);
      }
      seg.setPixelColor(i, m.color_blend(color, seg.SEGCOLOR(1), blend));
    } else {
      seg.setPixelColor(i, seg.SEGCOLOR(1));
    }
  }
};

if (typeof window !== 'undefined') {
  window.Effects1D = Effects1D;
}
