/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * Audio simulation based on simulateSound() in WLED util.cpp.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-05-10.
 */

let _audioMs = 0, _audioLastPeak = 0, _audioPeakHold = 0;
function _audioBeatsin8(bpm, lo, hi) {
  const phase = (_audioMs * bpm / 60000.0) * Math.PI * 2;
  const s = (Math.sin(phase) + 1) * 0.5;
  return lo + Math.floor(s * (hi - lo));
}
function audioTick(dtMs) {
  if (!Module || !_setAudio || !_fftPtr) return;
  _audioMs += dtMs;
  const fft = _audioFftBuf;
  for (let b = 0; b < 16; b++) {
    const bpm = Math.max(1, Math.floor(120 / (b + 1)));
    fft[b] = _audioBeatsin8(bpm, 0, 255) & 0xFF;
  }
  Module.HEAPU8.set(fft, _fftPtr);
  const vol = _audioBeatsin8(Math.floor(120 / 9), 0, 255);
  let peak = 0;
  if (vol > _audioLastPeak + 10 && vol > 60) { peak = 1; _audioPeakHold = _audioMs + 50; }
  else if (_audioMs < _audioPeakHold) { peak = 1; }
  _audioLastPeak = vol;
  let maxBin = 0, maxVal = 0;
  for (let b = 0; b < 16; b++) { if (fft[b] > maxVal) { maxVal = fft[b]; maxBin = b; } }
  const majorPeak = Math.max(1, Math.min(11025, 80 * Math.pow(2, maxBin * 6 / 15)));
  const magnitude = vol * 16;
  _setAudio(vol, vol, peak, majorPeak, magnitude, 31, 8, _fftPtr);
}
