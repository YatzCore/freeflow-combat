"""
Offline preview of the generated animations: draws the posed box model for a row of ticks and
writes one contact sheet (PNG) per clip, so poses can be judged without starting the game.

    python tools/preview.py <out_dir> [clip ...]

Needs Pillow. The floor is the thick line; anything drawn below it would be inside the ground.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

import rig

FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]

PLAYER_COLORS = {"head": (224, 180, 140), "torso": (52, 54, 70), "right_arm": (70, 74, 96),
                 "left_arm": (40, 42, 58), "right_leg": (58, 60, 80), "left_leg": (30, 32, 46)}
MOB_COLORS = {"head": (90, 140, 80), "torso": (60, 130, 140), "right_arm": (100, 150, 90),
              "left_arm": (70, 115, 65), "right_leg": (80, 70, 150), "left_leg": (55, 48, 110)}


def view_matrix(azimuth, elevation):
    """Rows = screen right, screen up, toward the camera. Azimuth 0 looks at the actor's front."""
    a, e = math.radians(azimuth), math.radians(elevation)
    cam = np.array([math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e)])  # front is -z
    right = np.cross([0, 1, 0], cam)
    right /= np.linalg.norm(right)
    up = np.cross(cam, right)
    return np.array([right, up, cam])


def place(boxes, x=0.0, z=0.0, yaw=0.0):
    """Moves an actor: yaw (degrees, + = turned left) then offset (px; z = forward of the origin actor)."""
    m = rig.ry(math.radians(yaw))
    return {n: pts @ m.T + np.array([x, 0.0, -z]) for n, pts in boxes.items()}


def draw_actors(draw, actors, view, origin, scale):
    polys = []
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    for boxes, colors in actors:
        for name, pts in boxes.items():
            centre = pts.mean(axis=0)
            for face in FACES:
                quad = pts[list(face)]
                normal = np.cross(quad[1] - quad[0], quad[2] - quad[0])
                if np.dot(normal, quad.mean(axis=0) - centre) < 0:
                    normal = -normal
                normal /= np.linalg.norm(normal) + 1e-9
                if np.dot(normal, view[2]) <= 0:
                    continue
                shade = 0.55 + 0.45 * max(0.0, float(np.dot(normal, light)))
                base = colors[name.replace('_lower', '')]
                if name.endswith('_lower'):
                    base = tuple(min(255, int(c * 1.18) + 6) for c in base)   # forearms and shins a touch lighter
                col = tuple(int(c * shade) for c in base)
                proj = quad @ view.T
                screen = [(origin[0] + p[0] * scale, origin[1] - p[1] * scale) for p in proj]
                polys.append((proj[:, 2].mean(), screen, col))
            if name == "head":
                # a dark visor on the face (model -z = entity -z = front) so facing is readable
                # front face = model z lo: corners 0 (top), 2 (bottom), 6 (bottom), 4 (top)
                f = pts[[0, 2, 6, 4]]
                top = f[0] + (f[1] - f[0]) * 0.45, f[3] + (f[2] - f[3]) * 0.45
                bot = f[0] + (f[1] - f[0]) * 0.7, f[3] + (f[2] - f[3]) * 0.7
                quad = np.array([top[0], bot[0], bot[1], top[1]])
                normal = np.cross(f[1] - f[0], f[3] - f[0])
                if np.dot(normal, f.mean(axis=0) - centre) < 0:
                    normal = -normal
                if np.dot(normal, view[2]) > 0:
                    proj = quad @ view.T
                    screen = [(origin[0] + p[0] * scale, origin[1] - p[1] * scale) for p in proj]
                    polys.append((proj[:, 2].mean() + 0.5, screen, (20, 20, 24)))
    for _, screen, col in sorted(polys, key=lambda p: p[0]):
        draw.polygon(screen, fill=col, outline=(15, 15, 18))


def sheet(path, cells, labels, azimuth=70, elevation=8, scale=5.0, cell=(230, 290), floor=0.84, per_row=8):
    """cells: list of actor lists [(boxes, colors), ...] -- one cell per tick, wrapped into rows."""
    w, h = cell
    rows = (len(cells) + per_row - 1) // per_row
    img = Image.new("RGB", (w * min(per_row, len(cells)), h * rows), (176, 196, 222))
    draw = ImageDraw.Draw(img)
    view = view_matrix(azimuth, elevation)
    for i, actors in enumerate(cells):
        cx, cy = (i % per_row) * w, (i // per_row) * h
        ox, oy = cx + w // 2, cy + int(h * floor)
        draw.rectangle([cx, oy, cx + w, cy + h], fill=(120, 124, 130))
        draw.line([cx, oy, cx + w, oy], fill=(60, 62, 66), width=2)
        draw_actors(draw, actors, view, (ox, oy), scale)
        draw.rectangle([cx, cy, cx + w, cy + h], outline=(150, 165, 190))
        draw.text((cx + 6, cy + 4), labels[i], fill=(20, 20, 30))
    img.save(path)


def clip_sheet(path, clip, ticks=None, partner=None, partner_at=(0.0, 26.0, 180.0), **kw):
    """Contact sheet for a clip; partner = (clip, tick offset) drawn as an enemy facing the player."""
    frames = rig.bake(clip)
    if ticks is None:
        step = 1 if len(frames) <= 12 else 2 if len(frames) <= 24 else 3
        ticks = list(range(0, len(frames), step))
    pframes = rig.bake(partner) if partner else None
    cells, labels = [], []
    for t in ticks:
        f = frames[min(t, len(frames) - 1)]
        actors = [(f.boxes(), PLAYER_COLORS)]
        if pframes:
            pf = pframes[min(t, len(pframes) - 1)]
            actors.append((place(pf.boxes(), *partner_at), MOB_COLORS))
        cells.append(actors)
        mark = " *" if clip.impact == t else ""
        labels.append(f"{clip.name} t{t}{mark}")
    sheet(path, cells, labels, **kw)


def both_views(path, clip, **kw):
    """Side view and three-quarter front view of the same clip, stacked."""
    a, b = path + ".a.png", path + ".b.png"
    clip_sheet(a, clip, **kw)
    clip_sheet(b, clip, azimuth=25, elevation=14, **kw)
    ia, ib = Image.open(a), Image.open(b)
    out = Image.new("RGB", (max(ia.width, ib.width), ia.height + ib.height), (0, 0, 0))
    out.paste(ia, (0, 0))
    out.paste(ib, (0, ia.height))
    out.save(path)
    ia.close(), ib.close()
    os.remove(a), os.remove(b)


if __name__ == "__main__":
    import gen_animations as g

    out = sys.argv[1]
    only = set(sys.argv[2:])
    os.makedirs(out, exist_ok=True)
    for clip in g.PLAYER + g.MOB:
        if only and clip.name not in only:
            continue
        kw = dict(g.PREVIEW.get(clip.name, {}))
        if "partner" in kw:
            kw["partner"] = next(c for c in g.PLAYER + g.MOB if c.name == kw["partner"])
        both_views(os.path.join(out, clip.name + ".png"), clip, **kw)
    print("wrote previews to", out)
