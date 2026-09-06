# Android LGPL components: source and replacement

Android APKs dynamically link **FFmpeg** (`libavcodec.so`, `libavutil.so`) and
**libmspack's LZX decoder** (`libcz_lzx.so`) under LGPL-2.1-or-later. GPL, nonfree
and version3 FFmpeg components are disabled and the resulting configuration is
checked. Xenon's GNU/binutils disassembler is **not built or linked** into the
Android loader. The project's license does not override these library licenses,
and does not restrict modification/debugging of the LGPL libraries as permitted
by their licenses.

The Android CI artifact must include `CaseZero-Android-LGPL-sources.zip` alongside
the APK. This contains exact Git archives (revision IDs and SHA-256 checksums),
FFmpeg's generated configuration, and the build recipes used by this port. When
redistributing an APK, provide these materials with equivalent access; do not
replace them with an unavailable private-repository link or omit them for a
"diagnostics" build. The library license texts and other binary-component notices
are also in APK `assets/licenses/`.

## Rebuild either library without game data

On Linux, unpack the source ZIP, then unpack both `*-source.tar.gz` archives in
that same directory. Their prefixes create `thirdparty/android/...` as expected
by the enclosed scripts. Install the pinned NDK, make and a POSIX shell. Set:

```sh
export ANDROID_NDK_HOME=/absolute/path/to/android-ndk-r27c
export CZ_ANDROID_DEPS="$PWD/thirdparty/android"
export CZ_JOBS=2
```

Modify the source as desired, keeping ABI-compatible exported functions:

```sh
bash tools/android/build_ffmpeg.sh
bash tools/android/build_lzx.sh
```

The resulting files are:

- `thirdparty/android/ffmpeg-install/lib/libavcodec.so`
- `thirdparty/android/ffmpeg-install/lib/libavutil.so`
- `thirdparty/android/lgpl-rebuild/libcz_lzx.so`

No XEX, game-generated PPC, DXC or app signing key is required for these library
builds. For a full app rebuild the public source revision is recorded in
`source-revisions.json`; follow `docs/android.md`. A full **game** rebuild still
needs the user's own matching game input. This document grants no rights to
redistribute Capcom's data or generated game code.

## Use the replacement libraries

If rebuilding the app, substitute these shared objects in
`android/generated/<mode>/jniLibs/arm64-v8a/` before Gradle's packaging step. The
packaging verifier enforces ABI/page alignment, not a fixed library content hash.

It is also possible to replace the three shared libraries in an existing APK
without relinking its game/native code:

1. **Export your saves through the launcher first.** Work on a copy of the APK.
2. Replace the matching entries under `lib/arm64-v8a/` with the rebuilt `.so` files,
   retaining their names. Keep libraries compressed (the manifest uses extracted
   native libraries). Leave other APK entries unchanged.
3. Use the Android SDK's `zipalign` and `apksigner` to align and sign the resulting
   APK **with your own key**. Old APK signatures are not valid after replacement;
   do not attempt to reuse them. No project/maintainer key is necessary.
4. Android only accepts an in-place update with the same signing identity. If you
   used a new key, uninstall the old app, install your modified APK and restore
   the save export. Uninstalling removes the old app's private data.

For example, after producing an unsigned ZIP-compatible APK:

```sh
zipalign -f -P 16 4 modified-unsigned.apk modified-aligned.apk
apksigner sign --ks /path/to/your-keystore --out modified.apk modified-aligned.apk
apksigner verify --verbose modified.apk
```

Use the same personal key for subsequent modified builds. The launcher's update
checker deliberately does not accept a release signed with a different key;
this protects updates and does not prevent side-loading your own signed build.
