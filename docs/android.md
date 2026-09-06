# Android port — experimental

**This is a port implementation, not a verified playable Android release.** The
repository has no real generated PPC image or game package. Host/packaging tests
cannot establish boot, picture correctness, sound, performance or save/load on a
phone. Do not relabel the CI diagnostics APK as the game.

## What was added

- An original English/Russian launcher using the Android UnleashedRecomp port as a
  feature/UX reference, **without copying its GPL code/art or including mods**.
- SAF import of an owned **Case Zero XBLA STFS package**, a ZIP or an extracted
  folder. Nested game directories are recognized; title ID `58410A8D`, XEX header
  bounds and required files are checked. Imports use bounded streaming, a
  foreground service, progress/cancellation and staged directory replacement.
- Shared graphics settings (720p or higher, FPS cap, shadows, vsync, MSAA), with
  conservative 720p/30 FPS/low-shadow/no-MSAA/no-RT defaults.
- Multi-pointer Xbox-style touch controls, analog sticks, optional swipe camera,
  size/opacity and a screen-relative layout editor. Physical SDL gamepads remain
  available; touching a controller can hide the overlay. Input is cleared on
  cancellation/backgrounding/disconnect from the view.
- A separate `:runtime` process, matching SDL Java/native versions, app-private
  paths, native logs, lifecycle pause checkpoints and ARM64 crash PCs. The
  desktop runtime's intentional `_Exit` does not terminate the launcher.
- Vulkan feature/descriptor-limit checks, system driver by default and explicit
  opt-in import of a trusted ARM64 Adreno `.so` or `meta.json` driver ZIP through
  adrenotools/volk. No bundled Turnip binary or automatic unverified downloads.
- CPU BC1–BC5 texture decoding for devices without native BC support, after
  untile/endian conversion and before **both** initial and refresh uploads.
  Cube faces/mips keep separate offsets; this costs additional RAM/CPU.
- Read-only Files-provider access, save ZIP export/restore, cache clearing,
  bounded diagnostic report sharing and an optional FPS/PSS overlay.
- User-initiated release updates with HTTPS-host restrictions, a mandatory
  GitHub SHA-256 digest, package/version checks and a matching installed signing
  certificate before handing an APK to Android's installer.
- Pinned source dependencies, a checksummed Gradle wrapper, ARM64 target-side
  XEX-loader build, source-built LGPL XMA codecs, on-device DXC build recipes,
  16 KB ELF alignment checks, and separate diagnostic/game build guards.

## Requirements and limitations

- **Android 10 / API 29+, arm64-v8a only.** 32-bit and x86 Android are rejected.
- **Vulkan 1.3 plus** `shaderInt64`, `independentBlend`, buffer-device-address,
  descriptor-indexing/update-after-bind features and at least five descriptor
  sets / 256 entries per heap. Use **Check device** for named missing features;
  the selected driver is checked again inside the runtime. Vulkan version alone
  does not prove compatibility.
- Allow several GB of free internal storage: importing temporarily holds both
  the source/staging data and the previous installation. Imports retain a
  rollback directory until the next replacement; saves live separately.
- `default.xex`, `data/shaders/deadrisingprologue-ps.big` and
  `data/frontend/fecmn.big` must come from your own matching game version. An
  arbitrary Xbox 360 XEX with a matching filename is not sufficient.
- On-device shader preparation requires **Android ARM64 DXC**, not the desktop
  Linux `libdxcompiler.so`. Both APK flavors require it in their packaging
  allowlist. First-sight shaders still use the existing in-process translator.
- Sub-720p rendering, per-game-context touch layouts (menu/cutscene detection),
  GFXReconstruct capture, an on-screen GPU profiler, Exynos driver packages and
  bundled driver variants are **not implemented**. The reference's Sonic-specific
  ISO/title-update/DLC installer is not applicable to this XBLA title.
- No game/controller/GPU model is claimed device-tested yet. In particular,
  suspend/resume across surface recreation, Adreno/Mali texture correctness,
  weak-memory ARM64 guest behavior and long-session memory use require device
  validation. No workaround is presented as proof of those properties.
- Android removes private data when the app is uninstalled. **Export saves first.**
  APK updates with the same application ID/signing key preserve data. The
  diagnostics app has a separate ID and cannot overwrite the game app's saves.

## Build the diagnostics APK (no game required)

Use Linux, JDK 17, Python 3, Git, CMake 3.31.6, Ninja, Clang and the Android SDK.
Install the pinned SDK inputs:

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018'
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export CZ_JOBS=2       # DXC and the renderer are large; avoid memory exhaustion

tools/android/build.sh diagnostic
cd android
./gradlew --no-daemon testDiagnosticDebugUnitTest lintDiagnosticDebug assembleDiagnosticDebug
cd ..
python3 tools/android/verify_apk.py \
  android/app/build/outputs/apk/diagnostic/debug/app-diagnostic-debug.apk --mode diagnostic
```

The resulting app is **Case Zero — Diagnostics**
(`com.casezero.recomp.diagnostic`). Its primary button runs the native mapping /
timebase smoke harness, not the game. Read `logs/runtime.log` through the launcher
or share a report. The smoke result explicitly says **stub, not gameplay**.

## Build a game APK using your own generated code

First follow the existing desktop recompiler setup with your own matching XEX,
`config/CaseZero.toml` and the committed XenonRecomp patches. **Never generate
stubs over your real `ppc/` directory**, and never upload the XEX or generated code
in a public CI artifact. The generated tree must include `ppc_config.h`,
`ppc_context.h`, `ppc_recomp_shared.h` and `ppc_func_mapping.cpp`.

```sh
tools/android/build.sh game /absolute/path/to/real/ppc
cd android
./gradlew --no-daemon assembleGameDebug   # local development, NOT a signed release
```

A game build refuses a stub header; the runtime additionally refuses to execute
stub guest code outside `--smoke`. `build-info.json` records `guest_code` and the
source revision, **not** a claim that gameplay was tested.

For a local production-signed APK, supply the Gradle environment variables
`ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` through your own secure build environment, then run
`assembleGameRelease`. No keys/passwords belong in Git or chat. Preserve the same
key for subsequent versions; increment `versionCode` when publishing an update.
The updater only accepts the release asset
`CaseZeroRecomp-android-arm64-v8a.apk`, the expected package and the same signing
identity. CI never substitutes a debug signature for a production signature or
publishes stub APKs as game releases.

Licenses are staged under APK `assets/licenses/`. Before distributing a game APK,
include any corresponding LGPL source/relink materials required by the bundled
components. See `THIRD_PARTY.md`.

## CI and what green means

- `build.yml`: host Windows/Linux **compile/link + stub smoke**, on feature branch
  pushes as well as PRs. Windows uses a pinned vcpkg checkout and its supported
  file binary cache, not the removed `x-gha` provider.
- `checks.yml`: actionlint, portable ASan/UBSan C++ tests and Python packaging /
  resource-contract tests. Uses synthetic data only.
- `android.yml`: source-builds target ARM64 native libraries and DXC, runs JVM
  importer tests and Android lint, packages a **diagnostic** APK, checks its
  ABI/ELF segment alignment/required libraries/no-game-data contract and Android
  signature. Logs are artifacts even on failure. No game-data secrets or private
  repositories are required; it does not automatically publish GitHub releases.

Forks may have Actions disabled by GitHub until their owner enables them. A
workflow that has not run is **unverified**, not green. Network/toolchain failures
are not suppressed with `continue-on-error` or a catch-all `|| true`.

## Portable tests available without an Android SDK

```sh
cmake -S runtime/tests -B build/portable-tests -G Ninja
cmake --build build/portable-tests
ctest --test-dir build/portable-tests --output-on-failure
python3 -m unittest discover -s tools/tests -v
```

C++ covers BC1–BC5 palettes/alpha/edge dimensions/invalid lengths, touch-state
clamping/merging/release/concurrent access, and synthetic STFS title IDs,
truncation, traversal, duplicate names, invalid parents, symlink escapes and
cyclic chains. JVM tests cover ZIP-slip, case collisions, archive budgets,
interruption-safe replacement, XEX identity/bounds and atomic settings.

## Required device release gate (not yet completed)

1. Install a real signed ARM64 APK on both a 4 KB and a 16 KB page-size device.
2. Import a valid package, ZIP and folder; cancel each, rotate/background, exhaust
   disk space and kill the process during replacement. Prior game/saves survive.
3. Boot from an empty shader cache; verify all shaders prepare and log failures.
4. Play from opening to Still Creek, a cinematic, combat and the ending. Check
   audio, touch camera, simultaneous triggers/buttons, USB/Bluetooth hotplug.
5. Compare native BC with `CZ_VK_FORCE_BC_DECODE=1` on identical frames, including
   alpha-cutouts, normals, cube reflections, mip chains and streamed refreshes.
6. Home/lock/unlock/switch apps repeatedly; no stuck buttons, guest time jumps,
   audio backlog, surface-loss loop or continued rendering into a dead surface.
7. Save/load, export/restore, update the APK without uninstalling and verify saves.
8. Test a compatible imported driver and a failing one; diagnostics name the
   driver failure, and the next launcher visit offers the system-driver recovery.
9. Run a long session under thermal throttling; record FPS, PSS and GPU memory.
10. Only after these gates pass, mark a release playable and list tested devices.
