"""
Generates src/main/resources/assets/freeflow/player_animations/freeflow_combat.json
(Blockbench / GeckoLib animation format, read by playerAnimator).

Edit the keyframe tables below and re-run:
    python tools/gen_animations.py

Conventions (playerAnimator):
  - bones: head, torso, right_arm, left_arm, right_leg, left_leg, and "body" (the whole player)
  - rotation in degrees [x, y, z]; arms/legs: negative x swings forward/up
  - body position in pixels (1/16 block); y up
"""
import json
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "freeflow",
                   "player_animations", "freeflow_combat.json")

Z = [0, 0, 0]
GUARD_L = [-70, 10, -25]  # left fist up, guarding the face


def kf(frames):
    return {f"{t:g}": {"vector": v} for t, v in frames}


def anim(length, **bones):
    out = {"loop": False, "animation_length": length, "bones": {}}
    for bone, channels in bones.items():
        out["bones"][bone] = {ch: kf(fr) for ch, fr in channels.items()}
    return out


def mirrored(a):
    """Swap right/left limbs and mirror rotations, giving the opposite-side version of a move."""
    swap = {"right_arm": "left_arm", "left_arm": "right_arm", "right_leg": "left_leg", "left_leg": "right_leg"}
    out = {"loop": False, "animation_length": a["animation_length"], "bones": {}}
    for bone, chans in a["bones"].items():
        target = swap.get(bone, bone)
        out["bones"][target] = {}
        for ch, frames in chans.items():
            fr = [(float(t), d["vector"]) for t, d in frames.items()]
            if ch == "rotation":
                fr = [(t, [v[0], -v[1], -v[2]]) for t, v in fr]
            elif ch == "position":
                fr = [(t, [-v[0], v[1], v[2]]) for t, v in fr]
            out["bones"][target][ch] = kf(fr)
    return out


A = {}

# --- Close-range strikes ---------------------------------------------------------
A["jab_right"] = anim(
    0.28,
    right_arm={"rotation": [(0, Z), (0.03, [-40, 0, 35]), (0.08, [-105, 25, 5]), (0.16, [-100, 20, 5]), (0.28, [-40, 0, 15])]},
    left_arm={"rotation": [(0, Z), (0.06, GUARD_L), (0.28, [-40, 0, -15])]},
    torso={"rotation": [(0, Z), (0.03, [0, 20, 0]), (0.08, [12, -40, 0]), (0.18, [10, -35, 0]), (0.28, Z)]},
    right_leg={"rotation": [(0, Z), (0.08, [-30, 0, 0]), (0.28, Z)]},
    left_leg={"rotation": [(0, Z), (0.08, [25, 0, 0]), (0.28, Z)]},
    body={"position": [(0, Z), (0.08, [0, -1, 2]), (0.28, Z)]},
)
A["jab_left"] = mirrored(A["jab_right"])

A["hook_right"] = anim(
    0.32,
    right_arm={"rotation": [(0, Z), (0.05, [-30, 0, 80]), (0.12, [-95, -70, 10]), (0.2, [-85, -80, 0]), (0.32, [-30, 0, 15])]},
    left_arm={"rotation": [(0, Z), (0.06, GUARD_L), (0.32, [-40, 0, -15])]},
    torso={"rotation": [(0, Z), (0.05, [0, 35, 0]), (0.12, [10, -55, 0]), (0.22, [8, -50, 0]), (0.32, Z)]},
    right_leg={"rotation": [(0, Z), (0.12, [-20, 0, 10]), (0.32, Z)]},
    left_leg={"rotation": [(0, Z), (0.12, [20, 0, -10]), (0.32, Z)]},
    body={"position": [(0, Z), (0.12, [0, -1.5, 1]), (0.32, Z)]},
)
A["hook_left"] = mirrored(A["hook_right"])

A["elbow"] = anim(
    0.3,
    right_arm={"rotation": [(0, Z), (0.05, [-60, 0, 90]), (0.11, [-100, -95, 30]), (0.2, [-95, -90, 25]), (0.3, [-30, 0, 15])]},
    left_arm={"rotation": [(0, Z), (0.06, GUARD_L), (0.3, Z)]},
    torso={"rotation": [(0, Z), (0.05, [0, 30, 0]), (0.11, [15, -60, 0]), (0.3, Z)]},
    right_leg={"rotation": [(0, Z), (0.11, [-35, 0, 0]), (0.3, Z)]},
    body={"position": [(0, Z), (0.11, [0, -1, 2.5]), (0.3, Z)]},
)

A["knee"] = anim(
    0.35,
    right_leg={"rotation": [(0, Z), (0.06, [20, 0, 0]), (0.13, [-110, 0, 5]), (0.22, [-105, 0, 5]), (0.35, Z)]},
    right_arm={"rotation": [(0, Z), (0.08, [-120, 0, 20]), (0.13, [-50, 0, 10]), (0.35, Z)]},
    left_arm={"rotation": [(0, Z), (0.08, [-120, 0, -20]), (0.13, [-50, 0, -10]), (0.35, Z)]},
    torso={"rotation": [(0, Z), (0.13, [30, 0, 0]), (0.25, [25, 0, 0]), (0.35, Z)]},
    body={"position": [(0, Z), (0.13, [0, 2, 2]), (0.35, Z)]},
)

A["front_kick"] = anim(
    0.38,
    right_leg={"rotation": [(0, Z), (0.07, [-60, 0, 0]), (0.14, [-100, 0, 0]), (0.24, [-95, 0, 0]), (0.38, Z)]},
    left_leg={"rotation": [(0, Z), (0.14, [15, 0, 0]), (0.38, Z)]},
    right_arm={"rotation": [(0, Z), (0.14, [30, 0, 40]), (0.38, Z)]},
    left_arm={"rotation": [(0, Z), (0.14, GUARD_L), (0.38, Z)]},
    torso={"rotation": [(0, Z), (0.14, [-20, -10, 0]), (0.38, Z)]},
    body={"position": [(0, Z), (0.14, [0, 0, 3]), (0.38, Z)]},
)

A["roundhouse"] = anim(
    0.45,
    right_leg={"rotation": [(0, Z), (0.08, [-30, 0, 30]), (0.18, [-75, 30, 85]), (0.28, [-70, 0, 80]), (0.45, Z)]},
    left_leg={"rotation": [(0, Z), (0.18, [0, 0, -10]), (0.45, Z)]},
    right_arm={"rotation": [(0, Z), (0.18, [20, 0, -40]), (0.45, Z)]},
    left_arm={"rotation": [(0, Z), (0.18, [-60, 0, -70]), (0.45, Z)]},
    torso={"rotation": [(0, Z), (0.08, [0, 40, 0]), (0.18, [0, -45, -30]), (0.3, [0, -40, -25]), (0.45, Z)]},
    body={"rotation": [(0, Z), (0.18, [0, -50, 0]), (0.45, Z)], "position": [(0, Z), (0.18, [0, 1, 1]), (0.45, Z)]},
)

A["spin_backfist"] = anim(
    0.45,
    body={"rotation": [(0, Z), (0.28, [0, 360, 0]), (0.45, [0, 360, 0])]},
    right_arm={"rotation": [(0, Z), (0.12, [-60, 0, 30]), (0.24, [-90, 0, 100]), (0.32, [-90, 0, 95]), (0.45, Z)]},
    left_arm={"rotation": [(0, Z), (0.12, GUARD_L), (0.45, Z)]},
    torso={"rotation": [(0, Z), (0.24, [10, 0, 15]), (0.45, Z)]},
    right_leg={"rotation": [(0, Z), (0.12, [-20, 0, 15]), (0.28, Z)]},
)

A["haymaker"] = anim(
    0.45,
    right_arm={"rotation": [(0, Z), (0.12, [40, 0, 50]), (0.2, [-130, -40, 0]), (0.3, [-110, -50, 0]), (0.45, Z)]},
    left_arm={"rotation": [(0, Z), (0.12, [-90, 0, -40]), (0.2, [10, 0, -30]), (0.45, Z)]},
    torso={"rotation": [(0, Z), (0.12, [-15, 45, 0]), (0.2, [25, -50, 0]), (0.32, [20, -45, 0]), (0.45, Z)]},
    right_leg={"rotation": [(0, Z), (0.2, [-40, 0, 0]), (0.45, Z)]},
    left_leg={"rotation": [(0, Z), (0.2, [30, 0, 0]), (0.45, Z)]},
    body={"position": [(0, Z), (0.12, [0, 0, -1.5]), (0.2, [0, -2, 3]), (0.45, Z)]},
)

# --- Long-range strikes (played during a lunge) -------------------------------------
A["flying_kick"] = anim(
    0.42,
    body={"position": [(0, Z), (0.1, [0, 5, 0]), (0.25, [0, 6, 0]), (0.42, Z)],
          "rotation": [(0, Z), (0.25, [0, -25, 0]), (0.42, Z)]},
    right_leg={"rotation": [(0, Z), (0.1, [-50, 0, 0]), (0.22, [-100, 0, 0]), (0.32, [-95, 0, 0]), (0.42, Z)]},
    left_leg={"rotation": [(0, Z), (0.1, [-80, 0, 0]), (0.22, [40, 0, 0]), (0.42, Z)]},
    right_arm={"rotation": [(0, Z), (0.22, [40, 0, 50]), (0.42, Z)]},
    left_arm={"rotation": [(0, Z), (0.22, [-80, 0, -60]), (0.42, Z)]},
    torso={"rotation": [(0, Z), (0.22, [-25, 0, 0]), (0.42, Z)]},
)

A["flying_knee"] = anim(
    0.4,
    body={"position": [(0, Z), (0.1, [0, 5, 0]), (0.24, [0, 6, 0]), (0.4, Z)]},
    right_leg={"rotation": [(0, Z), (0.12, [-60, 0, 0]), (0.22, [-120, 0, 0]), (0.3, [-115, 0, 0]), (0.4, Z)]},
    left_leg={"rotation": [(0, Z), (0.22, [30, 0, 0]), (0.4, Z)]},
    right_arm={"rotation": [(0, Z), (0.12, [-160, 0, 20]), (0.22, [-60, 0, 10]), (0.4, Z)]},
    left_arm={"rotation": [(0, Z), (0.12, [-160, 0, -20]), (0.22, [-60, 0, -10]), (0.4, Z)]},
    torso={"rotation": [(0, Z), (0.22, [35, 0, 0]), (0.4, Z)]},
)

# --- Beatdown / finisher -------------------------------------------------------------
A["beatdown_right"] = anim(
    0.16,
    right_arm={"rotation": [(0, [-50, 0, 30]), (0.05, [-100, 25, 0]), (0.16, [-50, 0, 30])]},
    left_arm={"rotation": [(0, [-80, 0, -20]), (0.16, [-80, 0, -20])]},
    torso={"rotation": [(0, [10, 15, 0]), (0.05, [15, -30, 0]), (0.16, [10, 15, 0])]},
    body={"position": [(0, [0, -1, 0]), (0.05, [0, -1, 2]), (0.16, [0, -1, 0])]},
)
A["beatdown_left"] = mirrored(A["beatdown_right"])

A["finisher"] = anim(
    0.5,
    right_arm={"rotation": [(0, Z), (0.1, [45, 0, 25]), (0.2, [-175, -10, 0]), (0.32, [-170, -10, 0]), (0.5, Z)]},
    left_arm={"rotation": [(0, Z), (0.2, [35, 0, -30]), (0.5, Z)]},
    torso={"rotation": [(0, Z), (0.1, [25, -25, 0]), (0.2, [-20, 20, 0]), (0.5, Z)]},
    right_leg={"rotation": [(0, Z), (0.1, [-40, 0, 0]), (0.2, [10, 0, 0]), (0.5, Z)]},
    body={"position": [(0, Z), (0.1, [0, -4, 0]), (0.2, [0, 4, 2]), (0.5, Z)]},
)

# --- Defensive / utility ----------------------------------------------------------------
A["counter"] = anim(
    0.45,
    left_arm={"rotation": [(0, Z), (0.04, [-120, 0, -45]), (0.14, [-115, 0, -40]), (0.24, [-50, 0, -10]), (0.45, Z)]},
    right_arm={"rotation": [(0, Z), (0.1, [20, 0, 40]), (0.18, [-105, 25, 0]), (0.3, [-100, 20, 0]), (0.45, Z)]},
    torso={"rotation": [(0, Z), (0.04, [-10, 35, 0]), (0.18, [15, -45, 0]), (0.45, Z)]},
    right_leg={"rotation": [(0, Z), (0.18, [-30, 0, 0]), (0.45, Z)]},
    left_leg={"rotation": [(0, Z), (0.04, [20, 0, 0]), (0.45, Z)]},
    body={"position": [(0, Z), (0.04, [0, -1, -1]), (0.18, [0, -1, 2]), (0.45, Z)]},
)

A["stun"] = anim(
    0.4,
    right_arm={"rotation": [(0, Z), (0.1, [-90, 90, 0]), (0.25, [-90, -60, 0]), (0.4, Z)]},
    left_arm={"rotation": [(0, Z), (0.1, [-70, 50, 0]), (0.25, [-80, -80, 0]), (0.4, Z)]},
    torso={"rotation": [(0, Z), (0.1, [0, 50, 0]), (0.25, [10, -50, 0]), (0.4, Z)]},
    body={"rotation": [(0, Z), (0.25, [0, -40, 0]), (0.4, Z)]},
)

A["evade"] = anim(
    0.32,
    body={"position": [(0, Z), (0.08, [0, -5, 0]), (0.24, [0, -4, 0]), (0.32, Z)],
          "rotation": [(0, Z), (0.08, [0, 0, 20]), (0.24, [0, 0, 15]), (0.32, Z)]},
    torso={"rotation": [(0, Z), (0.08, [35, 0, 0]), (0.32, Z)]},
    right_leg={"rotation": [(0, Z), (0.08, [-60, 0, 10]), (0.32, Z)]},
    left_leg={"rotation": [(0, Z), (0.08, [30, 0, -10]), (0.32, Z)]},
    right_arm={"rotation": [(0, Z), (0.08, [-60, 0, 50]), (0.32, Z)]},
    left_arm={"rotation": [(0, Z), (0.08, [-60, 0, -50]), (0.32, Z)]},
)

A["vault"] = anim(
    0.36,
    body={"rotation": [(0, Z), (0.36, [-360, 0, 0])], "position": [(0, Z), (0.18, [0, 8, 0]), (0.36, Z)]},
    right_leg={"rotation": [(0, Z), (0.08, [-110, 0, 0]), (0.28, [-110, 0, 0]), (0.36, Z)]},
    left_leg={"rotation": [(0, Z), (0.08, [-110, 0, 0]), (0.28, [-110, 0, 0]), (0.36, Z)]},
    right_arm={"rotation": [(0, Z), (0.08, [-170, 0, 25]), (0.36, Z)]},
    left_arm={"rotation": [(0, Z), (0.08, [-170, 0, -25]), (0.36, Z)]},
)

A["ground_takedown"] = anim(
    1.0,
    body={"position": [(0, Z), (0.12, [0, -7, 0]), (0.88, [0, -7, 0]), (1.0, Z)]},
    right_leg={"rotation": [(0, Z), (0.12, [-95, 0, 0]), (0.88, [-95, 0, 0]), (1.0, Z)]},
    left_leg={"rotation": [(0, Z), (0.12, [15, 0, 0]), (0.88, [15, 0, 0]), (1.0, Z)]},
    torso={"rotation": [(0, Z), (0.12, [40, 0, 0]), (0.88, [40, 0, 0]), (1.0, Z)]},
    right_arm={"rotation": [(0, Z), (0.2, [-175, 0, 0]), (0.28, [-40, 0, 0]), (0.45, [-175, 0, 0]), (0.53, [-40, 0, 0]),
                            (0.75, [-180, 0, 0]), (0.83, [-20, 0, 0]), (1.0, Z)]},
    left_arm={"rotation": [(0, Z), (0.12, [-70, 0, -25]), (0.88, [-70, 0, -25]), (1.0, Z)]},
)

A["special_takedown"] = anim(
    0.55,
    body={"rotation": [(0, Z), (0.35, [0, 360, 0]), (0.55, [0, 360, 0])],
          "position": [(0, Z), (0.15, [0, 6, 0]), (0.35, [0, 5, 0]), (0.55, Z)]},
    right_leg={"rotation": [(0, Z), (0.15, [-40, 0, 40]), (0.3, [-95, 0, 35]), (0.42, [-90, 0, 30]), (0.55, Z)]},
    left_leg={"rotation": [(0, Z), (0.15, [-60, 0, 0]), (0.55, Z)]},
    right_arm={"rotation": [(0, Z), (0.2, [-30, 0, 100]), (0.55, Z)]},
    left_arm={"rotation": [(0, Z), (0.2, [-30, 0, -100]), (0.55, Z)]},
    torso={"rotation": [(0, Z), (0.3, [-15, 0, 0]), (0.55, Z)]},
)

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": A}, f, indent=1)
    print(f"{len(A)} animations -> {os.path.normpath(out)}")
    print(", ".join(A))
