# Validation — EasyPiano 1.0.0

Original validation report supplied with the source archive, dated 2026-11-17.
These results describe the original build environment. Publication status below
is historical; repository GitHub Actions runs and Releases record subsequent validation and publication.

| Check | Result |
| --- | --- |
| JDK | Temurin 17 |
| Android SDK | Compile 37.2, target 37, min 26 |
| Gradle / AGP / NDK | 8.13 / 8.13.2 / 27.2.12479018 |
| `test assembleRelease lintRelease` | Passed; release built with R8 |
| JUnit (debug + release) | 5 tests each; 0 failures |
| Key table | MIDI 29–108 inclusive, F1–C8, 47 white + 33 black = 80 |
| Native sampler tests | Passed: tuning ratio, sustain, release, 64 voices, stealing, queue overflow |
| Sample integrity | 81 original OGG files; SHA256, 3 layers and complete keyboard coverage verified |
| Native audio renders | All 80 notes × 3 layers: 240 renders; largest measured fundamental deviation 1.752 cents |
| Release signing | Verified APK Signature Scheme v2; Android Debug fallback certificate |
| APK ABIs | arm64-v8a + armeabi-v7a |
| ZIP/native alignment | `zipalign -c -P 16 4` passed |
| x86_64 emulator build | `-Pemulator=true assembleDebug` passed |
| Android 8.0 emulator runtime | Installed, launched, decoded the bank, opened audio, recognized a C4 touch (261.6 Hz), ran glissando gesture and opened settings; no fatal exception/native crash in captured logs |
| Lint | 0 errors; 2 warnings (newer Kotlin available, Android orientation policy) |
| GitHub write/publish | Blocked by integration authorization (HTTP 403); no remote commit/tag/release was created |

The fallback debug signing was explicitly requested when no production key is configured. Keep one permanent signing key for update compatibility; different machines' debug keys are not interchangeable.

The tuning measurements use the actual Vorbis decoder and the production sampler, including playback-rate interpolation. Treble calibration uses the audible beginning of each sample. Acoustic piano strings contain inharmonic partials and beating; the reported figures refer to the measured fundamental in the analysis windows.

Hardware acceptance remains necessary: ARM release install/launch on a real device, five-finger touch, audible quality/release, latency, sustained frame rate, notch behavior and audio-route changes. An x86 emulator smoke check is not a physical audio or latency measurement. GitHub Actions execution and tag-triggered release also remain unverified until repository write access is authorized.
