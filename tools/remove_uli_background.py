"""Remove solid/dark square backgrounds from Uli sprites, keeping soft orange aura."""

from __future__ import annotations

from collections import deque
from pathlib import Path

from PIL import Image

DRAWABLE = Path("app/src/main/res/drawable")
BACKUP = Path("docs/assets/uli-sprites-with-bg")
FILES = [
    "uli_hero.png",
    "uli_idle.png",
    "uli_protected.png",
    "uli_blocking.png",
    "uli_thinking.png",
    "uli_success.png",
    "uli_paused.png",
]


def color_dist(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** 0.5


def is_bg_candidate(rgb: tuple[int, int, int], seeds: list[tuple[int, int, int]], threshold: float) -> bool:
    r, g, b = rgb
    # Keep vivid orange / lime / cream accents even if near edges.
    if max(r, g, b) - min(r, g, b) > 45 and (r > 120 or g > 140):
        return False
    # Keep bright pixels (face, glow cores).
    if (r + g + b) / 3 > 70:
        return False
    return any(color_dist(rgb, seed) <= threshold for seed in seeds)


def remove_background(img: Image.Image, threshold: float = 42.0) -> Image.Image:
    rgba = img.convert("RGBA")
    w, h = rgba.size
    px = rgba.load()

    samples = [
        (0, 0),
        (w - 1, 0),
        (0, h - 1),
        (w - 1, h - 1),
        (w // 2, 0),
        (w // 2, h - 1),
        (0, h // 2),
        (w - 1, h // 2),
        (8, 8),
        (w - 9, 8),
        (8, h - 9),
        (w - 9, h - 9),
    ]
    seeds = []
    for x, y in samples:
        r, g, b, a = px[x, y]
        if a > 0:
            seeds.append((r, g, b))
    # Deduplicate roughly
    uniq: list[tuple[int, int, int]] = []
    for s in seeds:
        if all(color_dist(s, u) > 8 for u in uniq):
            uniq.append(s)
    seeds = uniq or [(5, 6, 14), (0, 0, 0)]

    visited = [[False] * h for _ in range(w)]
    q: deque[tuple[int, int]] = deque()
    for x, y in samples:
        r, g, b, a = px[x, y]
        if a > 0 and is_bg_candidate((r, g, b), seeds, threshold):
            q.append((x, y))
            visited[x][y] = True

    while q:
        x, y = q.popleft()
        r, g, b, a = px[x, y]
        if not is_bg_candidate((r, g, b), seeds, threshold):
            continue
        # Soft alpha at fringes of the flood for cleaner cutouts.
        lum = (r + g + b) / 3
        if lum < 28:
            px[x, y] = (r, g, b, 0)
        else:
            # Near-character fringe: fade rather than hard cut.
            fade = max(0, min(255, int((lum - 28) * 6)))
            px[x, y] = (r, g, b, min(a, fade // 3))

        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny]:
                visited[nx][ny] = True
                nr, ng, nb, na = px[nx, ny]
                if na > 0 and is_bg_candidate((nr, ng, nb), seeds, threshold):
                    q.append((nx, ny))

    # Second pass: kill remaining near-black edge bands that weren't flood-connected.
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            near_edge = x < 4 or y < 4 or x >= w - 4 or y >= h - 4
            if near_edge and (r + g + b) / 3 < 22 and max(r, g, b) - min(r, g, b) < 18:
                px[x, y] = (r, g, b, 0)

    return crop_alpha(rgba, pad=12)


def crop_alpha(img: Image.Image, pad: int = 12) -> Image.Image:
    bbox = img.getbbox()
    if not bbox:
        return img
    l, t, r, b = bbox
    l = max(0, l - pad)
    t = max(0, t - pad)
    r = min(img.width, r + pad)
    b = min(img.height, b + pad)
    cropped = img.crop((l, t, r, b))
    # Normalize to square transparent canvas so layout stays stable.
    side = max(cropped.width, cropped.height)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    ox = (side - cropped.width) // 2
    oy = (side - cropped.height) // 2
    canvas.paste(cropped, (ox, oy), cropped)
    return canvas


def main() -> None:
    BACKUP.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        src = DRAWABLE / name
        if not src.exists():
            print("skip missing", name)
            continue
        original = Image.open(src)
        BACKUP.joinpath(name).write_bytes(src.read_bytes())
        # Hero/idle share denser navy grid; use slightly looser threshold.
        thr = 48.0 if "hero" in name or "idle" in name else 40.0
        out = remove_background(original, threshold=thr)
        out.save(src, format="PNG", optimize=True)
        print(f"{name}: {original.size} -> {out.size}")


if __name__ == "__main__":
    main()
