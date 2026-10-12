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

var audioMs=0, audioLastPeakVol=0, audioPeakHoldUntil=0, audioFftBuf=null;
function audioBeatsin8(bpm, lo, hi){
  var phase=(audioMs*bpm/60000.0)*Math.PI*2;
  var s=(Math.sin(phase)+1)*0.5;
  return lo + Math.floor(s*(hi-lo));
}
function audioTick(dtMs){
  if(!Module||!_setAudio||!_fftPtr)return;
  audioMs+=dtMs;
  var fft=audioFftBuf;
  for(var b=0;b<16;b++){
    var bpm=Math.max(1,Math.floor(120/(b+1)));
    fft[b]=audioBeatsin8(bpm,0,255)&0xFF;
  }
  Module.HEAPU8.set(fft,_fftPtr);
  var vol=audioBeatsin8(Math.floor(120/9),0,255);
  var peak=0;
  if(vol>audioLastPeakVol+10 && vol>60){peak=1; audioPeakHoldUntil=audioMs+50;}
  else if(audioMs<audioPeakHoldUntil){peak=1;}
  audioLastPeakVol=vol;
  var maxBin=0,maxVal=0;
  for(var b2=0;b2<16;b2++){if(fft[b2]>maxVal){maxVal=fft[b2];maxBin=b2;}}
  var majorPeak=Math.max(1,Math.min(11025, 80*Math.pow(2, maxBin*6/15)));
  var magnitude=vol*16;
  _setAudio(vol, vol, peak, majorPeak, magnitude, 31, 8, _fftPtr);
}
