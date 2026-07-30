# Steady Sight

> A bundle of comfort settings for players who get motion sick in first-person games: an always-on vignette, a smoother camera over steps and minecart rides, and the vanilla accessibility settings that help most — all in one mod, not several you'd have to find and combine yourself.

## What it does

- **Always-on vignette** — dims the edges of the screen at all times, giving your eyes a steady frame to rest on. Three strength presets (Subtle / Standard / Strong) plus Off.
- **Vanilla accessibility settings, applied once** — the first time you load this mod, it turns off view bobbing and auto-jump, and zeroes out FOV effects, screen distortion effects, and damage tilt. These are Minecraft's own built-in settings, just switched to the values most associated with comfort instead of left at their defaults. This runs exactly once: if you change any of them back afterward, Steady Sight won't touch them again, and the whole thing can be turned off in the config screen if you'd rather manage these yourself.
- **Smooth step camera** — Minecraft snaps your view instantly whenever you walk up a one-block ledge (stairs, slabs, and the like), an abrupt vertical jolt most other games don't have. This eases it over a few frames instead. Only the camera is smoothed — your actual position, movement, physics, and collision are untouched.
- **Smooth minecart-slope camera** — the same jolt happens when a minecart crosses a rail that starts or ends a slope, and gets the same treatment: camera only, no change to how the cart actually moves.

None of this is a cure for motion sickness and nothing here claims to be one. Each part corresponds to something researchers have actually measured, not promised — restricting peripheral vision is associated with lower cybersickness, and uncommanded, jarring screen movement is a recognized trigger. Whether any of it helps you specifically is something you'll have to find out by trying it, which is why every piece is separately toggleable instead of all-or-nothing.

## Settings

One setting screen: a single strength preset for the vignette, plus one on/off toggle per feature. No sliders, no raw numbers to guess at.

## Client-side only

No need to install it on the server, and nobody else sees any of it.

## Supported

Minecraft 1.21.1 · NeoForge only.

## Works well alongside

Steady Sight doesn't touch camera rotation inertia, render distance, or frame pacing — mods like Sodium (frame rate) and Distant Horizons (render distance) already cover that ground, and Steady Sight is built to run alongside them rather than duplicate what they do.

The step and minecart camera smoothing is a from-scratch implementation, not a repackaging of any existing "smooth steps" mod — if you already run one that covers the same ground, you don't need this mod's camera-smoothing toggles turned on.

## Languages

9 languages (machine-baseline; native-speaker PRs welcome).

## License

[MIT License](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
