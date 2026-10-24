# fx-eupl-src

Source code of the WLED-derived effect files distributed by MeowScript LLC, licensed under the EUPL-1.2.
Derived from WLED (https://github.com/wled/WLED). Credits and original notices are in NOTICE.

## Which version contains what

Each tag below holds exactly the WLED-derived files that the app version contained, plus LICENSE, NOTICE
and this README.
This table and the tags it names are authoritative. Older tags with the same version number stay in
place so existing links keep working, but where an `-r2` tag exists, use the `-r2` tag.

| App version | Store builds | Tag | WLED-derived files |
|---|---|---|---|
| iOS 1.0.1 | build 13 | `ios-1.0.1` | `ios/swift/PaletteColorData.swift` (palette data only) |
| iOS 1.0.2 | build 17 | `ios-1.0.2` | same as iOS 1.0.1 |
| iOS 1.0.3 | build 18 | `ios-1.0.3` | same as iOS 1.0.1 |
| iOS 1.0.4 | build 19 | `ios-1.0.4` | same as iOS 1.0.1 |
| iOS 1.0.5 | build 26 | `ios-1.0.5` | same as iOS 1.0.1 |
| iOS 1.1.0 | build 23 | `ios-1.1.0` | `shared/engine/` (effect engine, 18 files), `shared/simulator/` (effects-1d, effects-2d, effects-meta, math), `ios/simulator/` (palettes, segment), `ios/swift/` (WLEDEffects1D and Ext1 to Ext4, WLEDEffects2D and Ext1, EffectSimulationEngine+WLEDEffects, PaletteColorData) |
| iOS 1.1.1 | build 54 (builds 49 and 50 had the same files) | `ios-1.1.1-r2` | as iOS 1.1.0, with the full particle system and palette files of the engine (FXparticleSystem.cpp/.h, palettes.h, stubs/fastled_palettes.cpp) in place of the stubs/FXparticleSystem.h stub, `shared/simulator/effects-ps.js`, `ios/swift/` WLEDParticleSystem, PSFireworksHandler and EffectSimulationEngine+ParticleEffects, and `ios/engine-page/` (audio-sim, audio-sim-embedded) |
| iOS 1.1.2 | build 56 | `ios-1.1.2-r2` | same file set as iOS 1.1.1; `ios/simulator/palettes.js`, `ios/simulator/segment.js` and `ios/swift/PaletteColorData.swift` changed |
| iOS 1.1.3 | build 57 | `ios-1.1.3-r2` | identical to iOS 1.1.2 |
| iOS 1.1.4 | build 58 | `ios-1.1.4` | same file set as iOS 1.1.3 except `shared/simulator/effects-ps.js`, which 1.1.4 no longer contains; several files changed |
| iOS 1.1.5 | build 59 | `ios-1.1.5` | identical to iOS 1.1.4 |
| iOS 1.1.6 | build 60 | `ios-1.1.6` | identical to iOS 1.1.4 |
| iOS 1.1.7 | build 61 | `ios-1.1.7` | identical to iOS 1.1.4 |
| iOS 1.1.8 | build 62 | `ios-1.1.8` | identical to iOS 1.1.4 |
| Android 1.0.5 | version code 5 | `android-1.0.5` | `android/kotlin/PaletteColorData.kt` (palette data only) |
| Android 1.0.6 | version code 6 | `android-1.0.6` | same as Android 1.0.5 |
| Android 1.0.7 | version code 8 | `android-1.0.7-r2` | same as Android 1.0.5 |
| Android 1.0.8 | version code 9 | `android-1.0.8-r2` | same as Android 1.0.5 |
| Android 1.0.9 | version code 10 | `android-1.0.9-r2` | same as Android 1.0.5 |
| Android 1.1.2 | version codes 13 and 14 | `android-1.1.2-r2` | `shared/simulator/` (effects-1d, effects-2d, effects-meta, effects-ps, math), `android/simulator/` (palettes, segment), `android/kotlin/` (16 files) |
| Android 1.1.4 | version code 15 | `android-1.1.4` | same file set as Android 1.1.2 except `shared/simulator/effects-ps.js`, which 1.1.4 no longer contains; several files changed |
| Android 1.1.5 | version codes 16 and 17 | `android-1.1.5` | identical to Android 1.1.4 |
| Android 1.1.6 | version code 18 | `android-1.1.6` | identical to Android 1.1.4 |
| Android 1.1.7 | version code 19 | `android-1.1.7` | identical to Android 1.1.4 |
| Android 1.1.8 | version code 20 | `android-1.1.8` | identical to Android 1.1.4 |

There was no iOS 1.0.0, Android 1.1.0, Android 1.1.1 or Android 1.1.3 release.

Corrections to older tags:

- `android-1.0.7`, `android-1.0.8` and `android-1.0.9` say "same derived source as 1.1.2". That is wrong.
  Those versions contained only the palette data file. Use the `-r2` tags.
- `ios-1.1.2`, `ios-1.1.3` and `android-1.1.2` are missing `shared/simulator/effects-ps.js`, and the iOS tags are
  missing the particle and audio excerpts. They also hold the files of both apps. Use the `-r2` tags.
- `ios-1.1.1` has the right files, but its message names builds 49 and 50. The App Store build was 54, with
  the same derived files. `ios-1.1.1-r2` says so.
