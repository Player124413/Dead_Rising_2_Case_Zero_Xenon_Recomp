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

Play **Dead Rising 2: Case Zero** — the Xbox 360 exclusive prologue to Dead
Rising 2 — natively on Windows and Linux. Not an emulator: the game's code is
translated ahead of time and runs directly on your PC, with a Vulkan renderer,
real Xbox 360 audio, and native keyboard/mouse support.

> **The port is essentially complete.** The game is **100% playable start to
> finish** — this build has been completed end to end on both platforms — and
> should look right in nearly all places. A few minor issues remain (below);
> none block progress.

**You must own the game.** No Capcom content ships in this repository or in
these downloads — the game runs from your own copy of the XBLA package.

### How to install

1. Download the build for your system below and unpack it anywhere.
2. Copy your own XBLA package file (~825 MB, no file extension — on the
   console it lives at
   `Content/0000000000000000/58410A8D/000D0000/<long name>`) into the
   unpacked folder's `assets/package/`, or just drag it onto the launcher.
3. Run `cz_runtime.exe` (Windows) or `./cz_runtime` (Linux). The first run
   sets everything up by itself under a progress bar — unpacks your package,
   prepares all 1,265 shaders (~10 s), and generates the menu/prompt assets
   from your data. Later launches start straight into the game.

Saves and settings live outside the game folder (Windows:
`Saved Games\Dead Rising 2 Case Zero\`; Linux:
`~/.local/share/Dead Rising 2 Case Zero/`), so reinstalling never touches
them. A README inside the bundle covers troubleshooting.

### Highlights

- **The whole game** — Still Creek, combo weapons, cinematics, save/load,
  completable start to finish.
- **60 fps** (the game's own hidden mode, surfaced) — the original 30 fps
  pacing stays available as a setting, along with higher caps.
- **Native keyboard/mouse** with the Dead Rising 2 PC control scheme, raw
  mouse look, and real key icons on every prompt — prompts switch between
  key and controller art automatically based on what you touched last.
  Rebindable via `kbmap.txt`. Any controller SDL recognizes works too.
- **The restored PC options screen**: the Xbox build ships a dormant PC
  graphics menu; this port revives it in-game — resolution from 720p to 5K
  (applies live, no restart), display mode, vsync, shadow quality.
- **MSAA 2x** anti-aliasing by default, adjustable field of view, a settings
  launcher, and a pipeline pre-warm so even the first session plays smoothly.
- **Real Xbox 360 audio** (XMA) through ffmpeg — music, speech, effects,
  looping ambience, and the cinematics that depend on them.
- **Built for marathon sessions**: texture memory recycles over a full
  playthrough — no whitening or slow degradation on long runs.
- Under the hood: 57,822 PowerPC functions statically recompiled to native
  code, the 360 GPU's command stream executed on Vulkan 1.3, and a first run
  that builds everything it needs from your own copy of the game.

### Requirements

- GPU + driver with **Vulkan 1.3**.
- **Windows** 10+ x86-64, or **Linux** x86-64 with **glibc 2.43 or newer**.
- Your own copy of the Dead Rising 2: Case Zero XBLA package (~825 MB).
- ~2 GB free disk after first-run unpacking.

### Known issues (minor)

- A subtle **shading flicker on Chuck's hair** in motion; real hardware does
  not show it and it is being tracked.
- The occasional spot may shade slightly differently than the console.
- **Linux glibc floor** (2.43): older distributions refuse to start with a
  `GLIBC_x.yz not found` message. An AppImage-style build is planned.
- **No macOS build yet** — awaits test hardware, nothing structural.

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
