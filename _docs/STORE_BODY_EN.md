# Steady Sight

> A bundle of comfort settings for players who get motion sick in first-person games: an always-on vignette, a smoother camera over steps and minecart rides, the vanilla accessibility settings that help most, and the screen-shake settings in other mods you already have — all in one mod, not several you'd have to find and combine yourself.

None of this is a cure for motion sickness and nothing here claims to be one. Each part corresponds to something researchers have actually measured, not promised: restricting peripheral vision is consistently associated with lower cybersickness, and uncommanded, jarring screen movement is a recognized trigger. Whether any of it helps you specifically is something you'll have to find out by trying it — that's why the pieces are separately toggleable instead of all-or-nothing.

- **Vignette** — dims the edges of the screen at all times, giving your eyes a steady frame to rest on. Three strength presets (Subtle / Standard / Strong) plus Off.
- **Vanilla comfort settings, applied automatically** — the first time you load this mod, it turns off view bobbing, screen distortion effects, FOV effects, damage tilt, and auto-jump, and raises the field of view to 80 if yours is currently lower (the default is 70; if you're already running something wider, it's left alone). These are Minecraft's own built-in settings, just switched to the values most associated with comfort instead of left at their defaults. Applied once only: if you change any of them back afterward, Steady Sight won't touch them again (this also holds setting-by-setting across mod updates — if a future version adds another setting to this list, only the new one gets pushed, not the ones you already changed back). Toggle this off in the config screen if you'd rather manage these yourself.
- **Smooth step camera** — Minecraft snaps your view instantly whenever you walk up a one-block ledge (stairs, slabs, and the like), or ride a minecart across a rail that starts or ends a slope — both are abrupt, full-height vertical jolts most other games don't have. This eases those jolts over a few frames instead. Only the camera is smoothed — your actual position, movement, physics, and collision are exactly what they'd be without this mod, so it changes nothing about how the game plays, multiplayer included.
- **Other mods' comfort settings, applied automatically** — the same one-time push as above, but reaching into a handful of other mods you might already have installed: Blueprint's screen-shake scale (capped at 0.3, not zeroed — it's a shared library many other mods draw their own shake effects from, so this keeps the "something happened" cue legible while capping how hard it hits) and shaker cap, Punchy's freeze-frame hit shake, and SmoothGUI's menu-open animation speed. Every one of these is a setting that moves the camera itself — nothing here touches a mod just because it adds visual detail (particles, arm animation, extra effects); those aren't associated with motion sickness the way camera movement is, so they're left alone. Only applies to a mod you actually have installed, only once, and never overwrites a value you've deliberately changed since. A separate toggle from the vanilla-settings one above, because touching another mod's own config file is a different thing to opt into than touching Minecraft's own settings.

One setting screen, no sliders, no numbers to guess at — pick a vignette strength, and leave the other toggles on their defaults unless you want to change them.

Steady Sight doesn't touch camera rotation inertia, render distance, or frame pacing — mods like Smooth Camera Movement, Distant Horizons, and Sodium already cover those, and Steady Sight is built to run alongside any of them rather than duplicate what they do. (Its step and minecart camera smoothing above is a from-scratch implementation, not a repackaging of Countered's Smooth Steps, Do a Minecart Roll, Minecart Camera Center, or similar mods — if you already run one of those, you don't need this feature turned on here.)

Client-side only — no need to install it on the server, and nobody else sees any of it.

The mod's own text (config screen labels and tooltips) is localized in 9 languages (machine-baseline; native-speaker PRs welcome).

All Rights Reserved — modpack inclusion welcome, no credit required. Source is published so you can read exactly what it does.

Source and issues: https://github.com/KURONAMI333/steady-sight

Author: KURONAMI
