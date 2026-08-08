# Steady Sight

> A bundle of comfort settings for players who get motion sick in first-person games: an always-on vignette, a smoother camera over steps and minecart rides, and the vanilla accessibility settings that help most — all in one mod, not several you'd have to find and combine yourself.

## What it does

- **Always-on vignette** — dims the edges of the screen at all times, giving your eyes a steady frame to rest on. Three strength presets (Subtle / Standard / Strong) plus Off.
- **Vanilla accessibility settings, applied once** — the first time you load this mod, it turns off view bobbing and auto-jump, and zeroes out FOV effects, screen distortion effects, and damage tilt. These are Minecraft's own built-in settings, just switched to the values most associated with comfort instead of left at their defaults. This runs exactly once: if you change any of them back afterward, Steady Sight won't touch them again. On NeoForge and Forge, this (and every other feature below) can also be turned off in the config screen or config file; Fabric builds have neither and always run with everything on at its default (see Settings below).
- **Smooth step camera** — Minecraft snaps your view instantly whenever you walk up a one-block ledge (stairs, slabs, and the like), an abrupt vertical jolt most other games don't have. This eases it over a few frames instead. Only the camera is smoothed — your actual position, movement, physics, and collision are untouched.
- **Smooth minecart-slope camera** — the same jolt happens when a minecart crosses a rail that starts or ends a slope, and gets the same treatment: camera only, no change to how the cart actually moves.

None of this is a cure for motion sickness and nothing here claims to be one. Each part corresponds to something researchers have actually measured, not promised — restricting peripheral vision is associated with lower cybersickness, and uncommanded, jarring screen movement is a recognized trigger. Whether any of it helps you specifically is something you'll have to find out by trying it, which is why every piece is separately toggleable instead of all-or-nothing.

## Settings

A single strength preset for the vignette, plus one on/off toggle per feature. No sliders, no raw numbers to guess at.

- **NeoForge** — settings live in a config screen (accessible from the mods list) and a config file.
- **Forge 1.20.1** — same config file, no config screen (Forge 1.20.1 has no built-in screen generator, and this mod doesn't ship a custom one).
- **Fabric** (all versions) — no config file and no config screen. Every feature runs at its default: vignette Standard, everything else on. These are the same defaults NeoForge and Forge ship with, so a Fabric install behaves like an untouched NeoForge/Forge one.

## Client-side only

No need to install it on the server, and nobody else sees any of it.

## Supported

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.20.1 | — | ✅ | ✅ |
| 1.21.1 | ✅ | — | ✅ |
| 1.21.11 | ✅ | — | ✅ |
| 26.1.2 | ✅ | — | ✅ |
| 26.2 | ✅ | — | ✅ |

## Feature differences by loader

Every version has all four features (vignette, vanilla-settings push, step/minecart camera smoothing, other-mods'-shake capping) — nothing is missing outright. Two things differ by loader, both covered above and in more detail in-repo:

- **Config**: NeoForge has a config screen; Forge 1.20.1 has the config file but no screen; Fabric has neither and runs on fixed defaults.
- **Reset on respawn/dimension change**: the step-camera smoothing resets its history on respawn and dimension change on NeoForge and Forge 1.20.1. Fabric's client APIs don't expose an equivalent event on any version, so this reset doesn't happen there — the worst case is at most one under-a-block smoothing pass carried over from before the respawn, not a visible glitch.

The other-mods'-shake capping is a no-op wherever the target mod (Blueprint, Punchy, or SmoothGUI) isn't installed for that game version — this isn't a loader difference, just nothing to cap.

## Works well alongside

Steady Sight doesn't touch camera rotation inertia, render distance, or frame pacing — mods like Sodium (frame rate) and Distant Horizons (render distance) already cover that ground, and Steady Sight is built to run alongside them rather than duplicate what they do.

The step and minecart camera smoothing is a from-scratch implementation, not a repackaging of any existing "smooth steps" mod — if you already run one that covers the same ground, you don't need this mod's camera-smoothing toggles turned on.

## Languages

9 languages (machine-baseline; native-speaker PRs welcome).

## License

[All Rights Reserved](LICENSE) — modpack inclusion welcome, no credit required. Source is published so you can read exactly what it does.

## Credits

- Author: KURONAMI
