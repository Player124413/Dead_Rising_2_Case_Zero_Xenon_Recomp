# Release notes — v1.0.0 (FINAL, release-github-plan §5.2)

**This is the text to paste into the GitHub Release body.** Final as of
2026-09-05, operator instruction: *"this will truly be our 1.0.0."* Binaries are
commit `407eb79`: the fix round of 2026-09-05 is fully in — the device-following
prompt wording (MASH on keyboard only), the always-on mouse camera, and the Case
West back-imports (XMA hardware loops, LRU texture-slot recycling, the Q glyph
for the in-game Y prompt). Both artifacts sit in `dist/` and `~/Release/` with the checksums below
(repackaged once post-tag for the player-facing README rewrite — binaries
unchanged); if either is EVER rebuilt, refresh its hash here before attaching.

---

## Dead Rising 2: Case Zero — Native PC Port v1.0.0

The first public release. The game is **completable start to finish** on both
platforms — this build has been played through end to end.

**You must own the game.** No Capcom content ships in this repository or in
these downloads; the runtime reads everything from your own XBLA package
(quickstart in the README — drop the package in `assets/package/` and run).

### What's in the port, by era

- **Boot → title → gameplay**: the whole XBLA title statically recompiled
  (57,822 PowerPC functions → C++), kernel HLE written against hardware
  captures, honest-failure discipline throughout.
- **Renderer**: Vulkan 1.3, translated Xenos shaders (the disc's own 1,265
  pixel shaders built at first run), EDRAM tiling semantics, cube-map
  snapshots, deferred scoped clears, parallel command recording, and a frame
  that holds 60 fps at 1440p through the heaviest crowds on the dev machine.
- **Audio**: real XMA decoding through ffmpeg — music, speech, effects,
  hardware-looped voices, and the cinematics that gate on them.
- **Built for the long haul**: texture memory recycles over a full playthrough
  (no whitening or slowdown on marathon sessions).
- **Save/load**: full round trip, relocated to the per-user directory.
- **60 fps**: the title's own present-interval configuration, surfaced as a
  setting (30/60/90/120/240/480 or off).
- **Keyboard/mouse**: native DR2-PC-style bindings fed to the title's own
  PC input layer (shipped dormant in the 360 build), raw mouse camera,
  our-own-art key-cap prompt icons with live device-follow, and a
  player-editable `kbmap.txt`.
- **The PC options screen**: revived from the dormant layout the 360 build
  ships — resolution (720p–5K, applies live), display mode, vsync, shadow
  quality. **MSAA 2x** is the default; `CZ_VK_MSAA=0` restores single-sample.
- **First run**: fully self-contained — in-process package extract, disc
  shader build, and generation of the patched menu/prompt assets from your
  data, under one progress window. A pipeline pre-warm seed makes even the
  first session smooth.

### Requirements

- GPU + driver with **Vulkan 1.3** (dynamic rendering).
- **Windows** 10+ x86-64, or **Linux** x86-64 with **glibc ≥ 2.43**.
- Your own copy of the Dead Rising 2: Case Zero XBLA package (~825 MB).
- ~2 GB free disk after first-run unpacking.

### Known limitations

- **Linux glibc floor** (2.43): older distributions refuse to start with a
  `GLIBC_x.yz not found` message. An AppImage/old-base build is planned.
- **No macOS** yet (test hardware, not architecture — an ARM64 path exists).
- A subtle **hair-shading flicker** on Chuck in motion
  (`docs/hair-flicker-part92.md`) — real hardware does not show it; open.

### Legal

This project is not affiliated with, or endorsed by, Capcom or Microsoft.
Dead Rising 2: Case Zero is © Capcom Co., Ltd. The downloads contain the
recompiled program and this project's own runtime/art only; all game content
is read from, or generated at first run from, the player's own copy.
Project code: PolyForm Noncommercial 1.0.0. Third-party licences:
`THIRD_PARTY.md` inside each bundle. Built on hedge-dev's XenonRecomp and
XenosRecomp.

### Checksums (SHA-256)

```
0d1aa15c2d233d9686d516107f441b0b5b07fee05484087b668675c06037758f  CaseZeroRecomp-linux-x86_64.tar.zst
d911ad171b66a9058c7b92c9f8a82ca13335ffa6fd19aca20b012c04cb9d1869  CaseZeroRecomp-windows-x86_64.zip
```
