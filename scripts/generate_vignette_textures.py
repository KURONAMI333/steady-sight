#!/usr/bin/env python3
"""Generates the three Steady Sight vignette masks (SUBTLE/STANDARD/STRONG).

Run from anywhere; output paths below are resolved relative to this
script's own location, not the working directory:

    python generate_vignette_textures.py

Background (DESIGN_COMPILE.md, revised 2026-07-30, a runClient
verdict on draw method A): a linear alpha ramp (`edgeAlpha * (1 - t)`)
reaches 0 at its endpoint, but its *slope* does not — human vision is
sensitive to the second derivative of luminance, not just the first, so
the point where a linear gradient is cut off reads as a visible edge (a
Mach band) even though the alpha value itself is continuous. The same
method's corner handling (max of two axis-aligned linear ramps) has an
analogous problem: the line where one ramp starts winning over the other
is itself a discontinuity in the alpha field's derivative. Baking the
mask into a texture with a smoothstep curve (zero slope at *both* ends)
removes both problems at once, and removes them for good rather than
just making them harder to notice at a given opacity — the corner-max
math and the linear-ramp math are gone from the codebase entirely, not
tuned to be less visible.

Channel layout changed 2026-07-31 (GAP_LOG G76): the shape now lives in
the RGB channel (grayscale, R==G==B) and alpha is always 255 (fully
opaque). This mirrors vanilla's own `assets/minecraft/textures/misc/
vignette.png` exactly — measured from the decompiled 1.21.1 client jar:
center (0,0,0,255), rising through gray toward the edges, alpha constant
at 255 everywhere. Previously this file baked the shape into alpha with
RGB pinned to (0,0,0), which happened to be numerically equivalent for
the plain colour the RGB result painted onto the screen (see
SteadySightOverlay's Javadoc for the derivation) but left a
strength-shaped pattern in the framebuffer's *alpha* channel — invisible
to vanilla's own final composite, but readable by anything downstream
that samples alpha (e.g. an Iris shader pack's post-processing), which is
the mechanism behind the "輪郭が露骨に見える" report this change fixes.
Below `inner_radius`, RGB is exactly 0; from there outward it eases in
via smoothstep (3t^2 - 2t^3, zero slope at both ends — see "why
smoothstep" below), reaching 255 only right at the corners.

Distance from center is measured as a superellipse (squircle), not a
circle:

    nx = |x - cx| / cx      # 0 at center, 1 at the left/right edge
    ny = |y - cy| / cy      # 0 at center, 1 at the top/bottom edge
    r  = (nx**n + ny**n) ** (1/n)

which is exactly 1.0 at the midpoint of *every* edge (not just corners)
and SUPERELLIPSE_N controls how quickly it rounds off between an edge
midpoint and a corner (r ≈ 2**(1/n) there, clamped to 1.0 by smoothstep).
This went through two prior distance definitions before landing here —
see "history" below for why a plain circle doesn't work for a mask this
gets stretched non-uniformly onto a rectangular screen.

Peak opacity (maxOpacity) is intentionally NOT baked in here — every mask
uses the full 0-255 RGB range regardless of preset darkness. The renderer
scales the sampled RGB down to the preset's maxOpacity at draw time via
GuiGraphics#setColor's colour multiplier (r=g=b=maxOpacity), exactly the
role vanilla's own vignetteBrightness tint plays over its vignette
texture — see SteadySightOverlay for the derivation — so this script
only needs to vary with innerRadius, not with every (innerRadius,
maxOpacity) pair.

Why smoothstep (a runClient verdict on draw method A, 2026-07-30):
a linear alpha ramp (`edgeAlpha * (1 - t)`) reaches 0 at its endpoint,
but its *slope* does not — human vision is sensitive to the second
derivative of luminance, not just the first, so the point where a linear
gradient is cut off reads as a visible edge (a Mach band) even though
the alpha value itself is continuous. Method A's corner handling (max of
two axis-aligned linear ramps) had an analogous problem: the line where
one ramp starts winning over the other is itself a discontinuity in the
alpha field's derivative. A texture with a smoothstep curve (zero slope
at *both* ends) removes both problems at once.

History — two prior distance definitions, both wrong for a different
reason (2026-07-30, same day):

1. `r = euclidean_distance / (SIZE/2)` (edge-midpoint distance). r == 1.0
   at the midpoint of each edge, so every corner pixel (farther from
   center than an edge midpoint) had r > 1 and clamped to alpha == 255
   uniformly — a solid flat-alpha block filling each corner. Verdict: "これは
   暗すぎる、さすがにだめだわ" at the same maxOpacity that had read as fine
   under draw method A, which never had a plateau like this.
2. `r = euclidean_distance / (center * sqrt(2))` (true-corner distance,
   the fix for #1). This does remove the corner block — but a plain
   circular distance metric, stretched non-uniformly onto a widescreen
   destination rect by the renderer's blit (aspect distortion is
   accepted by design, see SteadySightOverlay), makes the edge midpoints
   land at r ≈ 0.707, not r == 1.0. At innerRadius=0.64, smoothstep(0.64,
   1.0, 0.707) ≈ 4/255 ≈ 1.6%, and after the preset's maxOpacity
   multiplier the edge midpoints were under human luminance
   discrimination threshold — invisible. Verdict: "コレ　わからんね". The
   superellipse fixes this because it is defined per-axis (nx, ny), not
   via a single euclidean radius, so it reaches r == 1.0 at every edge
   midpoint *and* at the corners, regardless of how non-uniformly the
   destination rectangle stretches it.

General lesson for future UI masks (see GAP_LOG G41): a square/circular
distance field, stretched non-uniformly onto a rectangle, does not keep
"1.0 at the edge" where you'd expect — a circle inscribed in a square
touches the square's edges at their midpoints only, so a plain
`sqrt(dx^2+dy^2)` distance always undershoots at edge midpoints once it's
stretched to a non-square destination. Measure distance in a way that is
1.0 at the actual boundary of the shape you're masking (a superellipse,
or the destination's own aspect-corrected coordinates), not in a way
that's 1.0 at some other reference point (a circle's radius, an
image diagonal, etc.) that happens to coincide with the boundary only in
the corners or only along one axis.

`inner_radius` below must be kept in sync by hand with
compute/StrengthPreset.java's per-preset values (there is no shared
source of truth between the Java enum and this script — see GAP_LOG for
why a single shared texture with runtime UV zoom was considered and
rejected in favor of one baked file per preset).
"""

from pathlib import Path

from PIL import Image

SCRIPT_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = (
    SCRIPT_DIR.parent
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "steady_sight"
    / "textures"
    / "gui"
)
SIZE = 256

# Superellipse exponent. Edge midpoints reach r==1.0 exactly for ANY n
# (ny==0 there, so the ny**n term vanishes regardless of n) — n only
# controls how much the corner overshoots r=1 (r_corner = 2**(1/n),
# clamped to 1.0 downstream). Larger n shrinks that overshoot toward 0
# (n -> infinity is a literal square, r_corner -> 1 exactly); it does NOT
# make the corner saturation worse, despite that being the intuitive
# guess. Swept n=4..80 empirically (GAP_LOG G41-G42): n=4 left a 22-23px
# run of alpha==255 along the diagonal near each corner; n=6 was the best
# value in the 3-6 range this was scoped to, cutting that to 17px with no
# change to the 2nd-derivative peak (both stay perfectly smooth — the
# run length and the Mach-band question are independent measurements,
# see G42). Going well past 6 (e.g. n=40) shrinks the run further but
# was out of scope for this round and risks its own smoothness problems
# as the shape approaches a literal square (a non-smooth max() in the
# limit) — not explored.
SUPERELLIPSE_N = 6

# (filename, inner_radius) — inner_radius must match StrengthPreset.java exactly.
PRESETS = [
    ("vignette_subtle.png", 0.72),
    ("vignette_standard.png", 0.64),
    ("vignette_strong.png", 0.54),
]


def smoothstep(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def superellipse_radius(dx: float, dy: float, cx: float, cy: float, n: float) -> float:
    """0 at the center, exactly 1.0 at every edge midpoint, > 1.0 past that
    toward the corners (clamped to 1.0 by smoothstep downstream)."""
    nx = abs(dx) / cx
    ny = abs(dy) / cy
    return (nx**n + ny**n) ** (1.0 / n)


def generate(inner_radius: float) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    center = (SIZE - 1) / 2.0
    span = 1.0 - inner_radius

    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - center
            dy = y - center
            r = superellipse_radius(dx, dy, center, center, SUPERELLIPSE_N)
            if span > 1e-6:
                t = (r - inner_radius) / span
            else:
                t = 1.0 if r > inner_radius else 0.0
            shade = round(smoothstep(t) * 255.0)
            # RGB carries the shape (grayscale); alpha is always fully
            # opaque, matching vanilla's vignette.png (see module docstring,
            # GAP_LOG G76) instead of the old alpha-only encoding.
            pixels[x, y] = (shade, shade, shade, 255)

    return image


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for filename, inner_radius in PRESETS:
        image = generate(inner_radius)
        path = OUTPUT_DIR / filename
        image.save(path)
        print(f"wrote {path} (innerRadius={inner_radius})")


if __name__ == "__main__":
    main()
