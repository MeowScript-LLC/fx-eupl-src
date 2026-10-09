/*
 * Derived from WLED (https://github.com/wled/WLED), EUPL-1.2.
 *
 * WLED: Copyright (c) 2016-present Christian Schwinne and individual WLED contributors.
 * Licensed under the EUPL v. 1.2 or later.
 * FX.cpp, FX_fcn.cpp, FX.h: Copyright (c) 2016 Harm Aldick. Licensed under the EUPL v. 1.2 or later.
 * Adapted from code originally licensed under the MIT license.
 *
 * Modifications Copyright (c) 2026 MeowScript LLC, licensed under EUPL-1.2.
 * Modified by MeowScript LLC in 2026, last modified on 2026-10-09.
 */

'use strict';

const EFFECT_DATA_RAW = {
  0:   { name: "Solid", data: ";!;;" },
  1:   { name: "Blink", data: "!,Duty cycle;!,!;!;01" },
  2:   { name: "Breathe", data: "!;!,!;!;01" },
  3:   { name: "Wipe", data: "!,!;!,!;!" },
  4:   { name: "Wipe Random", data: "!;;!" },
  5:   { name: "Random Colors", data: "!,Fade time;;!;01" },
  6:   { name: "Sweep", data: "!,!;!,!;!" },
  7:   { name: "Dynamic", data: "!,!,,,,Smooth;;!" },
  8:   { name: "Colorloop", data: "!,Saturation;;!;01" },
  9:   { name: "Rainbow", data: "!,Size;;!" },
  10:  { name: "Scan", data: "!,# of dots,,,,,Overlay;!,!,!;!" },
  11:  { name: "Scan Dual", data: "!,# of dots,,,,,Overlay;!,!,!;!" },
  12:  { name: "Fade", data: "!;!,!;!;01" },
  13:  { name: "Theater", data: "!,Gap size;!,!;!" },
  14:  { name: "Theater Rainbow", data: "!,Gap size;,!;!" },
  15:  { name: "Running", data: "!,Wave width;!,!;!" },
  16:  { name: "Saw", data: "!,Width;!,!;!" },
  17:  { name: "Twinkle", data: "!,!;!,!;!;;m12=0" },
  18:  { name: "Dissolve", data: "Repeat speed,Dissolve speed,,,,Random,Complete;!,!;!" },
  19:  { name: "Dissolve Rnd", data: "Repeat speed,Dissolve speed;,!;!" },
  20:  { name: "Sparkle", data: "!,,,,,,Overlay;!,!;!;;m12=0" },
  21:  { name: "Sparkle Dark", data: "!,!,,,,,Overlay;Bg,Fx;!;;m12=0" },
  22:  { name: "Sparkle+", data: "!,!,,,,,Overlay;Bg,Fx;!;;m12=0" },
  23:  { name: "Strobe", data: "!;!,!;!;01" },
  24:  { name: "Strobe Rainbow", data: "!;,!;!;01" },
  25:  { name: "Strobe Mega", data: "!,!;!,!;!;01" },
  26:  { name: "Blink Rainbow", data: "Frequency,Blink duration;!,!;!;01" },
  27:  { name: "Android", data: "!,Width;!,!;!;;m12=1" },
  28:  { name: "Chase", data: "!,Width;!,!,!;!" },
  29:  { name: "Chase Random", data: "!,Width;!,,!;!" },
  30:  { name: "Chase Rainbow", data: "!,Width;!,!;!" },
  31:  { name: "Chase Flash", data: "!;Bg,Fx;!" },
  32:  { name: "Chase Flash Rnd", data: "!;!,!;!" },
  33:  { name: "Rainbow Runner", data: "!,Size;Bg;!" },
  34:  { name: "Colorful", data: "!,Saturation;1,2,3;!" },
  35:  { name: "Traffic Light", data: "!,US style;,!;!" },
  36:  { name: "Sweep Random", data: "!;;!" },
  37:  { name: "Chase 2", data: "!,Width;!,!;!" },
  38:  { name: "Aurora", data: "!,!;1,2,3;!;;sx=24,pal=50" },
  39:  { name: "Stream", data: "!,Zone size;;!" },
  40:  { name: "Scanner", data: "!,Trail,Delay,,,Dual,Bi-delay;!,!,!;!;;m12=0,c1=0" },
  41:  { name: "Lighthouse", data: "!,Fade rate;!,!;!" },
  42:  { name: "Fireworks", data: ",Frequency;!,!;!;12;ix=192,pal=11" },
  43:  { name: "Rain", data: "!,Spawning rate;!,!;!;12;ix=128,pal=0" },
  44:  { name: "Tetrix", data: "!,Width,,,,One color;!,!;!;;sx=0,ix=0,pal=11,m12=1" },
  45:  { name: "Fire Flicker", data: "!,!;!;!;01" },
  46:  { name: "Gradient", data: "!,Spread;!,!;!;;ix=16" },
  47:  { name: "Loading", data: "!,Fade;!,!;!;;ix=16" },
  48:  { name: "Rolling Balls", data: "!,# of balls,,,,Collide,Overlay,Trails;!,!,!;!;1;m12=1" },
  49:  { name: "Fairy", data: "!,# of flashers;!,!;!" },
  50:  { name: "Two Dots", data: "!,Dot size,,,,,Overlay;1,2,Bg;!" },
  51:  { name: "Fairytwinkle", data: "!,!;!,!;!;;m12=0" },
  52:  { name: "Running Dual", data: "!,Wave width;L,!,R;!" },
  54:  { name: "Chase 3", data: "!,Size;1,2,3;!" },
  55:  { name: "Tri Wipe", data: "!;1,2,3;!" },
  56:  { name: "Tri Fade", data: "!;1,2,3;!" },
  57:  { name: "Lightning", data: "!,!,,,,,Overlay;!,!;!" },
  58:  { name: "ICU", data: "!,!,,,,,Overlay;!,!;!" },
  59:  { name: "Multi Comet", data: "!,Fade;!,!;!;1" },
  60:  { name: "Scanner Dual", data: "!,Trail,Delay,,,Dual,Bi-delay;!,!,!;!;;m12=0,c1=0" },
  61:  { name: "Stream 2", data: "!;;" },
  62:  { name: "Oscillate", data: ";;;" },
  63:  { name: "Pride 2015", data: "!;;" },
  64:  { name: "Juggle", data: "!,Trail;;!;;sx=64,ix=128" },
  65:  { name: "Palette", data: "Shift,Size,Rotation,,,Animate Shift,Animate Rotation,Anamorphic;;!;12;ix=112,c1=0,o1=1,o2=0,o3=1" },
  66:  { name: "Fire 2012", data: "Cooling,Spark rate,,2D Blur,Boost;;!;1;pal=35,sx=64,ix=160,m12=1,c2=128" },
  67:  { name: "Colorwaves", data: "!,Hue;!;!;;pal=26" },
  68:  { name: "Bpm", data: "!;!;!;;sx=64" },
  69:  { name: "Fill Noise", data: "!;!;!" },
  70:  { name: "Noise 1", data: "!;!;!;;pal=20" },
  71:  { name: "Noise 2", data: "!;!;!;;pal=43" },
  72:  { name: "Noise 3", data: "!;!;!;;pal=35" },
  73:  { name: "Noise 4", data: "!;!;!;;pal=26" },
  74:  { name: "Colortwinkles", data: "Fade speed,Spawn speed;;!;;m12=0" },
  75:  { name: "Lake", data: "!;Fx;!" },
  76:  { name: "Meteor", data: "!,Trail,,,,Gradient,,Smooth;;!;1" },
  77:  { name: "Copy Segment", data: ",Color shift,Lighten,Brighten,ID,Axis(2D),FullStack(last frame);;;12;ix=0,c1=0,c2=0,c3=0" },
  78:  { name: "Railway", data: "!,Smoothness;1,2;!;;pal=3" },
  79:  { name: "Ripple", data: "!,Wave #,Blur,,,,Overlay;,!;!;12;c1=0" },
  80:  { name: "Twinklefox", data: "!,Twinkle rate,,,,Cool;!,!;!" },
  81:  { name: "Twinklecat", data: "!,Twinkle rate,,,,Cool,Reverse;!,!;!" },
  82:  { name: "Halloween Eyes", data: "Eye off time,Eye on time,,,,,Overlay;!,!;!;12" },
  83:  { name: "Solid Pattern", data: "Fg size,Bg size;Fg,!;!;;pal=0" },
  84:  { name: "Solid Pattern Tri", data: ",Size;1,2,3;;;pal=0" },
  85:  { name: "Spots", data: "Spread,Width,,,,,Overlay;!,!;!" },
  86:  { name: "Spots Fade", data: "Spread,Width,,,,,Overlay;!,!;!" },
  87:  { name: "Glitter", data: "!,!,,,,,Overlay;,,Glitter color;!;;pal=11,m12=0" },
  88:  { name: "Candle", data: "!,!;!,!;!;01;sx=96,ix=224,pal=0" },
  89:  { name: "Fireworks Starburst", data: "Chance,Fragments,,,,,Overlay;,!;!;;pal=11,m12=0" },
  90:  { name: "Fireworks 1D", data: "Gravity,Firing side;!,!;!;12;pal=11,ix=128" },
  91:  { name: "Bouncing Balls", data: "Gravity,# of balls,,,,,Overlay;!,!,!;!;1;m12=1" },
  92:  { name: "Sinelon", data: "!,Trail;!,!,!;!" },
  93:  { name: "Sinelon Dual", data: "!,Trail;!,!,!;!" },
  94:  { name: "Sinelon Rainbow", data: "!,Trail;,,!;!" },
  95:  { name: "Popcorn", data: "!,!,,,,,Overlay;!,!,!;!;;m12=1" },
  96:  { name: "Drip", data: "Gravity,# of drips,,,,,Overlay;!,!;!;;m12=1" },
  97:  { name: "Plasma", data: "Phase,!;!;!" },
  98:  { name: "Percent", data: "!,% of fill,,,,One color;!,!;!" },
  99:  { name: "Ripple Rainbow", data: "!,Wave #;;!;12" },
  100: { name: "Heartbeat", data: "!,!;!,!;!;01;m12=1" },
  101: { name: "Pacifica", data: "!,Angle;;!;;pal=51" },
  102: { name: "Candle Multi", data: "!,!;!,!;!;;sx=96,ix=224,pal=0" },
  103: { name: "Solid Glitter", data: ",!;Bg,,Glitter color;;;m12=0" },
  104: { name: "Sunrise", data: "Time [min],Width;;!;;pal=35,sx=60" },
  105: { name: "Phased", data: "!,!;!,!;!" },
  106: { name: "Twinkleup", data: "!,Intensity;!,!;!;;m12=0" },
  107: { name: "Noise Pal", data: "!,Scale;;!" },
  108: { name: "Sine", data: "!,Scale;;!" },
  109: { name: "Phased Noise", data: "!,!;!,!;!" },
  110: { name: "Flow", data: "!,Zones;;!;;m12=1" },
  111: { name: "Chunchun", data: "!,Gap size;!,!;!" },
  112: { name: "Dancing Shadows", data: "!,# of shadows;!;!" },
  113: { name: "Washing Machine", data: "!,!;;!" },
  114: { name: "Rotozoomer", data: "!,Scale,,,,Alt;;!;2;pal=54" },
  115: { name: "Blends", data: "Shift speed,Blend speed;;!" },
  116: { name: "TV Simulator", data: "!,!;;!;01" },
  117: { name: "Dynamic Smooth", data: "!,!;;!" },
  118: { name: "Spaceships", data: "!,Blur,,,,Smear;;!;2" },
  119: { name: "Crazy Bees", data: "!,Blur,,,,Smear;;!;2;pal=11,ix=0" },
  120: { name: "Ghost Rider", data: "Fade rate,Blur;;!;2" },
  121: { name: "Blobs", data: "!,# blobs,Blur,Trail;!;!;2;c1=8" },
  122: { name: "Scrolling Text", data: "!,Y Offset,Trail,Font size,Rotate,Gradient,Custom Font,Reverse;!,!,Gradient;!;2;ix=128,c1=0,rev=0,mi=0,rY=0,mY=0" },
  123: { name: "Drift Rose", data: "Fade,Blur,,,,Smear;;!;2;pal=11" },
  124: { name: "Distortion Waves", data: "!,Scale,,,,Fill,Zoom,Alt;;!;2;pal=0" },
  125: { name: "Soap", data: "!,Smoothness,Density;;!;2;pal=11" },
  126: { name: "Octopus", data: "!,,Offset X,Offset Y,Legs,fasttan;;!;2" },
  127: { name: "Waving Cell", data: "!,Blur,Amplitude 1,Amplitude 2,Amplitude 3,,Flow;;!;2;ix=0" },
  128: { name: "Pixels", data: "Fade rate,# of pixels;!,!;!;1v;m12=0,si=0" },
  129: { name: "Pixelwave", data: "!,Sensitivity;!,!;!;1v;ix=64,m12=2,si=0" },
  130: { name: "Juggles", data: "!,# of balls;!,!;!;01v;m12=0,si=0" },
  131: { name: "Matripix", data: "!,Brightness;!,!;!;1v;ix=64,m12=2,si=1" },
  132: { name: "Gravimeter", data: "Rate of fall,Sensitivity;!,!;!;1v;ix=128,m12=2,si=0" },
  133: { name: "Plasmoid", data: "Phase,# of pixels;!,!;!;01v;sx=128,ix=128,m12=0,si=0" },
  134: { name: "Puddles", data: "Fade rate,Puddle size;!,!;!;1v;m12=0,si=0" },
  135: { name: "Midnoise", data: "Fade rate,Max. length;!,!;!;1v;ix=128,m12=1,si=0" },
  136: { name: "Noisemeter", data: "Fade rate,Width;!,!;!;1v;ix=128,m12=2,si=0" },
  137: { name: "Freqwave", data: "Speed,Sound effect,Low bin,High bin,Pre-amp;;;01f;m12=2,si=0" },
  138: { name: "Freqmatrix", data: "Speed,Sound effect,Low bin,High bin,Sensitivity;;;01f;m12=3,si=0" },
  139: { name: "GEQ", data: "Fade speed,Ripple decay,# of bands,,Bin,Color bars;!,,Peaks;!;2f;c1=255,c2=64,pal=11,si=0,c3=0" },
  140: { name: "Waterfall", data: "!,Adjust color,Select bin,Volume (min);!,!;!;01f;c2=0,m12=2,si=0" },
  141: { name: "Freqpixels", data: "Fade rate,Starting color and # of pixels;!,!,;!;1f;m12=0,si=0" },
  143: { name: "Noisefire", data: "!,!;;;01v;m12=2,si=0" },
  144: { name: "Puddlepeak", data: "Fade rate,Puddle size,Select bin,Volume (min);!,!;!;1v;c2=0,m12=0,si=0" },
  145: { name: "Noisemove", data: "Move speed,Fade rate;!,!;!;01f;m12=0,si=0" },
  146: { name: "Noise2D", data: "!,Scale;;!;2" },
  147: { name: "Perlin Move", data: "!,# of pixels,Fade rate;!,!;!" },
  148: { name: "Ripple Peak", data: "Fade rate,Max # of ripples,Select bin,Volume (min);!,!;!;1v;c2=0,m12=0,si=0" },
  149: { name: "Firenoise", data: "X scale,Y scale,,,,Palette;;!;2;pal=66" },
  150: { name: "Squared Swirl", data: ",Fade,,,Blur;;!;2" },
  151: { name: "PacMan", data: "Speed,# of PowerDots,Blink distance,Blur,# of Ghosts,Dots,Smear,Compact;;!;1;m12=0,sx=192,ix=64,c1=64,c2=0,c3=12,o1=1,o2=0" },
  152: { name: "DNA", data: "Scroll speed,Blur,,,,Smear;;!;2;ix=0" },
  153: { name: "Matrix", data: "!,Spawning rate,Trail,,,Custom color;Spawn,Trail;;2" },
  154: { name: "Metaballs", data: "!;;!;2" },
  155: { name: "Freqmap", data: "Fade rate,Starting color;!,!;!;1f;m12=0,si=0" },
  156: { name: "Gravcenter", data: "Rate of fall,Sensitivity;!,!;!;1v;ix=128,m12=2,si=0" },
  157: { name: "Gravcentric", data: "Rate of fall,Sensitivity;!,!;!;1v;ix=128,m12=3,si=0" },
  158: { name: "Gravfreq", data: "Rate of fall,Sensitivity;!,!;!;1f;ix=128,m12=0,si=0" },
  159: { name: "DJ Light", data: "Speed;;;01f;m12=2,si=0" },
  160: { name: "Funky Plank", data: "Scroll speed,,# of bands;;;2f;si=0" },
  161: { name: "Shimmer", data: "Speed,Interval,Size,Granular,Flow,Zebra,Reverse,Sporadic;Fx,Bg,Cx;!;1;pal=15,sx=220,ix=10,c2=0,c3=0" },
  162: { name: "Pulser", data: "!,Blur;;!;2" },
  163: { name: "Blurz", data: "Fade rate,Blur;!,Color mix;!;1f;m12=0,si=0" },
  164: { name: "Drift", data: "Rotation speed,Blur,,,,Twin,Smear;;!;2;ix=0" },
  165: { name: "Waverly", data: "Amplification,Sensitivity,,,,,Blur;;!;2v;ix=64,si=0" },
  166: { name: "Sun Radiation", data: "Variance,Brightness;;;2" },
  167: { name: "Colored Bursts", data: "Speed,# of lines,,,Blur,Gradient,Smear,Dots;;!;2;c3=16" },
  168: { name: "Julia", data: ",Max iterations per pixel,X center,Y center,Area size,Blur;!;!;2;ix=24,c1=128,c2=128,c3=16" },
  172: { name: "Game Of Life", data: "!,,Blur,,,,,Mutation;!,!;!;2;pal=11,sx=128" },
  173: { name: "Tartan", data: "X scale,Y scale,,,Sharpness;;!;2" },
  174: { name: "Polar Lights", data: "!,Scale,,,,Flip Palette;;!;2;pal=71" },
  175: { name: "Swirl", data: "!,Sensitivity,Blur;,Bg Swirl;!;2v;ix=64,si=0" },
  176: { name: "Lissajous", data: "X frequency,Fade rate,Blur,,Speed,Smear;!;!;2;c3=15" },
  177: { name: "Frizzles", data: "X frequency,Y frequency,Blur,,,Smear;;!;2" },
  178: { name: "Plasma Ball", data: "Speed,,Fade,Blur;;!;2" },
  179: { name: "Flow Stripe", data: "Hue speed,Effect speed;;!;pal=11" },
  180: { name: "Hiphotic", data: "X scale,Y scale,,,Speed;!;!;2" },
  181: { name: "Sindots", data: "!,Dot distance,Fade rate,Blur,,Smear;;!;2" },
  182: { name: "DNA Spiral", data: "Scroll speed,Y frequency,Blur,,,Smear;;!;2;c1=0" },
  183: { name: "Black Hole", data: "Fade rate,Outer Y freq.,Outer X freq.,Inner X freq.,Inner Y freq.,Solid,,Blur;!;!;2;pal=11" },
  184: { name: "Wavesins", data: "!,Brightness variation,Starting color,Range of colors,Color variation;!;!" },
  185: { name: "Rocktaves", data: ";!,!;!;01f;m12=1,si=0" },
  186: { name: "Akemi", data: "Color speed,Dance;Head palette,Arms & Legs,Eyes & Mouth;Face palette;2f;si=0" },
  218: { name: "Color Clouds", data: "!,!,Clouds,Colors,Distance,,,Cozy;;!;;sx=24,ix=32,c1=48,c2=64,c3=12,pal=0" },
  219: { name: "Slow Transition", data: "Time (min),,,,,,Sweep;!;!;1;pal=2,sx=0,ix=0" },
};

const DEFAULT_SLIDER_LABELS = [
  "Effect speed",
  "Effect intensity",
  "Custom 1",
  "Custom 2",
  "Custom 3"
];

const DEFAULT_CHECK_LABELS = ["Option 1", "Option 2", "Option 3"];
const DEFAULT_COLOR_LABELS = ["Fx", "Bg", "Cs"];

function parseEffectData(id) {
  const entry = EFFECT_DATA_RAW[id];
  if (!entry) return null;

  const result = {
    id,
    name: entry.name,
    sliders: [],
    checks: [],
    colors: [],
    palette: true,
    is1D: true,
    is2D: false,
    isVolume: false,
    isFreq: false,
    defaults: {},
  };

  const parts = entry.data.split(';');

  if (parts[0]) {
    const items = parts[0].split(',');
    for (let i = 0; i < 5; i++) {
      const label = items[i];
      if (label === undefined || label === '') {
        result.sliders.push({ visible: false, label: DEFAULT_SLIDER_LABELS[i] });
      } else if (label === '!') {
        result.sliders.push({ visible: true, label: DEFAULT_SLIDER_LABELS[i] });
      } else {
        result.sliders.push({ visible: true, label: label });
      }
    }
    for (let i = 5; i < 8; i++) {
      const label = items[i];
      if (label === undefined || label === '') {
        result.checks.push({ visible: false, label: DEFAULT_CHECK_LABELS[i - 5] });
      } else if (label === '!') {
        result.checks.push({ visible: true, label: DEFAULT_CHECK_LABELS[i - 5] });
      } else {
        result.checks.push({ visible: true, label: label });
      }
    }
  }

  while (result.sliders.length < 5) result.sliders.push({ visible: false, label: DEFAULT_SLIDER_LABELS[result.sliders.length] });
  while (result.checks.length < 3) result.checks.push({ visible: false, label: DEFAULT_CHECK_LABELS[result.checks.length] });

  if (parts[1]) {
    const items = parts[1].split(',');
    for (let i = 0; i < 3; i++) {
      const label = items[i];
      if (label === undefined || label === '') {
        result.colors.push({ visible: false, label: DEFAULT_COLOR_LABELS[i] });
      } else if (label === '!') {
        result.colors.push({ visible: true, label: DEFAULT_COLOR_LABELS[i] });
      } else {
        result.colors.push({ visible: true, label: label });
      }
    }
  }
  while (result.colors.length < 3) result.colors.push({ visible: false, label: DEFAULT_COLOR_LABELS[result.colors.length] });

  if (parts[2] !== undefined) {
    const pal = parts[2].trim();
    if (pal === '' || pal === undefined) {
      result.palette = false;
    } else if (pal === '!') {
      result.palette = true;
    } else if (!isNaN(parseInt(pal))) {
      result.palette = false;
      result.defaults.pal = parseInt(pal);
    } else {
      result.palette = true;
    }
  }

  if (parts[3]) {
    const flags = parts[3].trim();
    result.is1D = flags.includes('1') || flags.includes('0') || flags === '';
    result.is2D = flags.includes('2');
    result.isVolume = flags.includes('v');
    result.isFreq = flags.includes('f');
    if (flags === '2' || flags === '2v' || flags === '2f') {
      result.is1D = false;
      result.is2D = true;
    }
    if (flags === '12') {
      result.is1D = true;
      result.is2D = true;
    }
  }

  if (parts[4]) {
    const pairs = parts[4].split(',');
    for (const pair of pairs) {
      const [key, val] = pair.split('=');
      if (key && val !== undefined) {
        result.defaults[key.trim()] = parseInt(val);
      }
    }
  }

  return result;
}

const EFFECTS = {};
const EFFECT_LIST = [];

for (const id in EFFECT_DATA_RAW) {
  const parsed = parseEffectData(parseInt(id));
  if (parsed) {
    EFFECTS[id] = parsed;
    EFFECT_LIST.push(parsed);
  }
}

EFFECT_LIST.sort((a, b) => a.id - b.id);

function get1DEffects() { return EFFECT_LIST.filter(e => e.is1D && !e.is2D); }
function get2DEffects() { return EFFECT_LIST.filter(e => e.is2D); }
function getBothEffects() { return EFFECT_LIST.filter(e => e.is1D && e.is2D); }
function getAudioEffects() { return EFFECT_LIST.filter(e => e.isVolume || e.isFreq); }
function getNonAudioEffects() { return EFFECT_LIST.filter(e => !e.isVolume && !e.isFreq); }

function getEffectDefaults(id) {
  const fx = EFFECTS[id];
  if (!fx) return { sx: 128, ix: 128, c1: 128, c2: 128, c3: 16, pal: 0 };

  return {
    sx: fx.defaults.sx !== undefined ? fx.defaults.sx : 128,
    ix: fx.defaults.ix !== undefined ? fx.defaults.ix : 128,
    c1: fx.defaults.c1 !== undefined ? fx.defaults.c1 : 128,
    c2: fx.defaults.c2 !== undefined ? fx.defaults.c2 : 128,
    c3: fx.defaults.c3 !== undefined ? fx.defaults.c3 : 16,
    pal: fx.defaults.pal !== undefined ? fx.defaults.pal : 0,
    o1: fx.defaults.o1 || 0,
    o2: fx.defaults.o2 || 0,
    o3: fx.defaults.o3 || 0,
    m12: 0,
    si: fx.defaults.si || 0,
  };
}

if (typeof window !== 'undefined') {
  window.EFFECTS = EFFECTS;
  window.EFFECT_LIST = EFFECT_LIST;
  window.EFFECT_DATA_RAW = EFFECT_DATA_RAW;
  window.parseEffectData = parseEffectData;
  window.getEffectDefaults = getEffectDefaults;
  window.get1DEffects = get1DEffects;
  window.get2DEffects = get2DEffects;
  window.getBothEffects = getBothEffects;
  window.getAudioEffects = getAudioEffects;
  window.getNonAudioEffects = getNonAudioEffects;
}
