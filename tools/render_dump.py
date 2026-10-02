"""Render app/build/drawing-dump/drawings.json as one PNG contact sheet (first-angle layout) for visual review.

usage: python tools/render_dump.py [out.png]
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
data = json.load(open(os.path.join(ROOT, "app", "build", "drawing-dump", "drawings.json"), encoding="utf-8"))
S = 4.0  # px per mm
GAP = 22  # mm between views
CELL_W, CELL_H = 900, 760
cols = 3
rows = math.ceil(len(data) / cols)
img = Image.new("RGB", (cols * CELL_W, rows * CELL_H), "#F6F3EA")
d = ImageDraw.Draw(img)


def dashed(p, q, on, off, width, fill):
    L = math.hypot(q[0] - p[0], q[1] - p[1])
    if L < 1e-6:
        return
    ux, uy = (q[0] - p[0]) / L, (q[1] - p[1]) / L
    t = 0.0
    while t < L:
        e = min(t + on, L)
        d.line([(p[0] + ux * t, p[1] + uy * t), (p[0] + ux * e, p[1] + uy * e)], fill=fill, width=width)
        t = e + off


for i, (pid, part) in enumerate(data.items()):
    ox, oy = (i % cols) * CELL_W + 50, (i // cols) * CELL_H + 60
    d.text((ox, oy - 40), f"{pid}  tris={part['tris']} sharp={part['sharp']}", fill="#16181C")
    fb = part["views"]["FRONT"]["bounds"]
    tb = part["views"]["TOP"]["bounds"]
    lb = part["views"]["LEFT"]["bounds"]
    fw, fh = fb[2] - fb[0], fb[3] - fb[1]
    th = tb[3] - tb[1]
    # sheet origin (mm): front view top-left at (0,0); y grows downward on the sheet
    origins = {
        "FRONT": (0 - fb[0], fh + fb[1]),  # u -> x ; v -> y = (fh + minV) - v
        "TOP": (0 - tb[0], fh + GAP + th + tb[1]),
        "LEFT": (fw + GAP - lb[0], fh + lb[1]),
    }
    for vk, v in part["views"].items():
        ax, ay = origins[vk]

        def P(pt):
            return (ox + (ax + pt[0]) * S, oy + (ay - pt[1]) * S)

        for kind, color, width in (("hidden", "#16181C", 2), ("center", "#C4372B", 2), ("visible", "#16181C", 5)):
            for pl in v[kind]:
                pts = [P(p) for p in pl["pts"]]
                if pl["closed"]:
                    pts.append(pts[0])
                for a, b in zip(pts, pts[1:]):
                    if kind == "visible":
                        d.line([a, b], fill=color, width=width)
                    elif kind == "hidden":
                        dashed(a, b, 14, 7, width, color)
                    else:
                        dashed(a, b, 30, 8, width, color)
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, ".scratch", "drawings.png")
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
print("wrote", out, img.size)
