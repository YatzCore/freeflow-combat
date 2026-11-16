"""
Generates src/main/resources/assets/freeflow/player_animations/freeflow_combat.json
(Blockbench / GeckoLib animation format, read by playerAnimator).

Edit the keyframe tables below and re-run:
    python tools/gen_animations.py

Conventions used in the tables below (checked against playerAnimator's loader and renderer):
  - bones: head, torso, right_arm, left_arm, right_leg, left_leg, and "body" (the whole player,
    pivoting 0.7 blocks above the feet)
  - rotation in degrees [x, y, z]; arms/legs: negative x swings forward/up; torso: positive x leans forward
  - body rotation: positive x pitches the whole player FORWARD (a front flip is +360)
  - body position in pixels (1/16 block): y up, z FORWARD (the generator converts: in the
    file format +z is backward)
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


def anim(length, loop=False, **bones):
    out = {"loop": loop, "animation_length": length, "bones": {}}
    for bone, channels in bones.items():
        out["bones"][bone] = {}
        for ch, fr in channels.items():
            if bone == "body" and ch == "position":
                fr = [(t, [v[0], v[1], -v[2]]) for t, v in fr]  # tables use z = forward
            out["bones"][bone][ch] = kf(fr)
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

# Parry with the left forearm, then a straight kick to the chest (enemy is launched).
A["counter_kick"] = anim(
    0.5,
    left_arm={"rotation": [(0, Z), (0.04, [-120, 30, -50]), (0.12, [-100, 0, -40]), (0.25, GUARD_L), (0.5, Z)]},
    right_arm={"rotation": [(0, Z), (0.04, [-60, 0, 30]), (0.2, [30, 0, 45]), (0.5, Z)]},
    right_leg={"rotation": [(0, Z), (0.12, [-40, 0, 0]), (0.2, [-105, 0, 0]), (0.3, [-100, 0, 0]), (0.5, Z)]},
    left_leg={"rotation": [(0, Z), (0.2, [15, 0, 0]), (0.5, Z)]},
    torso={"rotation": [(0, Z), (0.04, [0, 30, 0]), (0.2, [-25, -10, 0]), (0.5, Z)]},
    body={"position": [(0, Z), (0.2, [0, 1, 2]), (0.5, Z)]},
)

# Catch the attacking arm, step in, and throw the enemy over the shoulder.
A["counter_throw"] = anim(
    0.6,
    right_arm={"rotation": [(0, Z), (0.05, [-110, 0, 10]), (0.15, [-120, 0, 0]), (0.3, [-175, 0, -10]),
                            (0.42, [-30, 0, 0]), (0.6, Z)]},
    left_arm={"rotation": [(0, Z), (0.05, [-105, 0, -10]), (0.15, [-115, 0, 0]), (0.3, [-170, 0, 10]),
                           (0.42, [-40, 0, 0]), (0.6, Z)]},
    torso={"rotation": [(0, Z), (0.15, [-10, 30, 0]), (0.3, [-20, 0, 0]), (0.42, [45, -20, 0]), (0.6, Z)]},
    right_leg={"rotation": [(0, Z), (0.15, [-35, 0, 0]), (0.42, [-25, 0, 0]), (0.6, Z)]},
    left_leg={"rotation": [(0, Z), (0.15, [20, 0, 0]), (0.42, [30, 0, 0]), (0.6, Z)]},
    body={"position": [(0, Z), (0.15, [0, -2, 1]), (0.3, [0, -1, 0]), (0.42, [0, -4, 0]), (0.6, Z)],
          "rotation": [(0, Z), (0.15, [0, 25, 0]), (0.42, [0, -20, 0]), (0.6, Z)]},
)

A["stun"] = anim(
    0.4,
    right_arm={"rotation": [(0, Z), (0.1, [-90, 90, 0]), (0.25, [-90, -60, 0]), (0.4, Z)]},
    left_arm={"rotation": [(0, Z), (0.1, [-70, 50, 0]), (0.25, [-80, -80, 0]), (0.4, Z)]},
    torso={"rotation": [(0, Z), (0.1, [0, 50, 0]), (0.25, [10, -50, 0]), (0.4, Z)]},
    body={"rotation": [(0, Z), (0.25, [0, -40, 0]), (0.4, Z)]},
)

# Back-hop when evading with no direction held: a quick backflip.
A["evade"] = anim(
    0.4,
    body={"rotation": [(0, Z), (0.05, [-20, 0, 0]), (0.32, [-360, 0, 0]), (0.4, [-360, 0, 0])],
          "position": [(0, Z), (0.16, [0, 9, -2]), (0.32, [0, 2, -3]), (0.4, Z)]},
    right_leg={"rotation": [(0, Z), (0.08, [-100, 0, 0]), (0.28, [-100, 0, 0]), (0.4, Z)]},
    left_leg={"rotation": [(0, Z), (0.08, [-100, 0, 0]), (0.28, [-100, 0, 0]), (0.4, Z)]},
    right_arm={"rotation": [(0, Z), (0.08, [-170, 0, 20]), (0.32, [-60, 0, 20]), (0.4, Z)]},
    left_arm={"rotation": [(0, Z), (0.08, [-170, 0, -20]), (0.32, [-60, 0, -20]), (0.4, Z)]},
)

# Combat roll: the player is turned to face the roll direction, then tucks and rolls forward.
def _roll():
    """
    Forward roll that stays on the ground. playerAnimator spins the whole body around a point
    0.7 blocks up, but a tucked body is a ball centred ~1.35 blocks up (hips 0.75 .. head 2.0,
    radius ~0.65). Spinning around the ball's centre while keeping it on the floor needs this
    position offset per angle theta (derived from T(0.7) R T(-0.7) == T(0.65) R T(-1.35)):
        y = -0.05 - 0.65*cos(theta) blocks,   forward z = -0.65*sin(theta) blocks
    The tuck blends in over the first 0.05 s and out over the last 0.07 s.
    """
    import math
    start, spin_end, end = 0.05, 0.31, 0.4
    times = [0, 0.03, 0.05, 0.08, 0.11, 0.14, 0.17, 0.2, 0.23, 0.26, 0.29, 0.31, 0.35, 0.4]
    rot, pos = [], []
    for t in times:
        theta = 0.0 if t <= start else min(1.0, (t - start) / (spin_end - start)) * 360.0
        tuck = min(1.0, t / start) if t < spin_end else max(0.0, 1.0 - (t - spin_end) / (end - spin_end))
        r = math.radians(theta)
        y = (-0.05 - 0.65 * math.cos(r)) * 16 * tuck
        z = (-0.65 * math.sin(r)) * 16 * tuck
        rot.append((t, [theta, 0, 0]))
        pos.append((t, [0, round(y, 2), round(z, 2)]))
    tucked = lambda v: [(0, Z), (start, v), (spin_end, v), (end, Z)]
    return anim(
        end,
        body={"rotation": rot, "position": pos},
        head={"rotation": tucked([45, 0, 0])},
        right_leg={"rotation": tucked([-125, 0, 5])},
        left_leg={"rotation": tucked([-125, 0, -5])},
        right_arm={"rotation": tucked([-80, 0, -25])},
        left_arm={"rotation": tucked([-80, 0, 25])},
    )


A["roll"] = _roll()

# Rolling into an enemy: a high front flip over it.
A["vault"] = anim(
    0.42,
    body={"rotation": [(0, Z), (0.06, [-15, 0, 0]), (0.36, [360, 0, 0]), (0.42, [360, 0, 0])],
          "position": [(0, Z), (0.1, [0, 8, 1]), (0.2, [0, 13, 3]), (0.32, [0, 6, 3]), (0.42, Z)]},
    right_leg={"rotation": [(0, Z), (0.1, [-115, 0, 0]), (0.3, [-115, 0, 0]), (0.42, Z)]},
    left_leg={"rotation": [(0, Z), (0.1, [-115, 0, 0]), (0.3, [-115, 0, 0]), (0.42, Z)]},
    right_arm={"rotation": [(0, Z), (0.06, [-175, 0, 20]), (0.3, [-120, 0, 30]), (0.42, Z)]},
    left_arm={"rotation": [(0, Z), (0.06, [-175, 0, -20]), (0.3, [-120, 0, -30]), (0.42, Z)]},
    torso={"rotation": [(0, Z), (0.1, [30, 0, 0]), (0.3, [30, 0, 0]), (0.42, Z)]},
)

# Drop onto the downed enemy: lean over it, knee down (rear leg back), one hand pinning it,
# three hammer blows with the other.
def _crouch(depth):
    """
    Minecraft's own sneak pose, scaled by depth (1 = vanilla sneak): torso tilts forward and drops,
    head and arms drop with it, legs shift back under the hips so nothing separates. Returns
    per-bone (rotation, position) vectors. Part positions are bedrock-style pixels (y up).
    """
    return {
        "torso": ([28 * depth, 0, 0], [0, -3.2 * depth, 0]),
        "head": ([0, 0, 0], [0, -4.2 * depth, 0]),
        "right_arm": ([0, 0, 0], [0, -3.2 * depth, 0]),
        "left_arm": ([0, 0, 0], [0, -3.2 * depth, 0]),
        "right_leg": ([0, 0, 0], [0, -0.2 * depth, 4 * depth]),
        "left_leg": ([0, 0, 0], [0, -0.2 * depth, 4 * depth]),
    }


def _ground_takedown():
    deep = _crouch(1.4)
    times = [0, 0.12, 0.88, 1.0]
    w = [0, 1, 1, 0]
    bones = {}
    for bone, (rot, pos) in deep.items():
        bones[bone] = {
            "rotation": [(t, [v * k for v in rot]) for t, k in zip(times, w)],
            "position": [(t, [v * k for v in pos]) for t, k in zip(times, w)],
        }
    # Head looks down at the enemy; off hand braced on it; three hammer blows straight down.
    bones["head"]["rotation"] = [(0, Z), (0.12, [35, 0, 0]), (0.88, [35, 0, 0]), (1.0, Z)]
    bones["left_arm"]["rotation"] = [(0, Z), (0.12, [-50, 0, -10]), (0.88, [-50, 0, -10]), (1.0, Z)]
    bones["right_arm"]["rotation"] = [(0, Z), (0.18, [-170, 0, 10]), (0.26, [-35, 0, 0]), (0.42, [-170, 0, 10]),
                                      (0.5, [-35, 0, 0]), (0.68, [-175, 0, 10]), (0.77, [-30, 0, 0]),
                                      (0.9, [-45, 0, 0]), (1.0, Z)]
    bones["body"] = {"position": [(0, Z), (0.12, [0, -3, 0]), (0.88, [0, -3, 0]), (1.0, Z)]}
    a = anim(1.0, **bones)
    a["keep_torso"] = True
    return a


A["ground_takedown"] = _ground_takedown()

# Leap into a spinning hook kick.
A["special_takedown"] = anim(
    0.55,
    # Big leaping front kick: jump, chamber, extend, land.
    body={"position": [(0, Z), (0.1, [0, 5, 1]), (0.26, [0, 7, 3]), (0.42, [0, 3, 3]), (0.55, Z)],
          "rotation": [(0, Z), (0.26, [-10, 0, 0]), (0.55, Z)]},
    right_leg={"rotation": [(0, Z), (0.1, [-100, 0, 0]), (0.24, [-95, 0, 0]), (0.36, [-90, 0, 0]), (0.55, Z)]},
    left_leg={"rotation": [(0, Z), (0.1, [-80, 0, 0]), (0.24, [30, 0, 0]), (0.42, [20, 0, 0]), (0.55, Z)]},
    right_arm={"rotation": [(0, Z), (0.1, [-120, 0, 30]), (0.24, [40, 0, 40]), (0.55, Z)]},
    left_arm={"rotation": [(0, Z), (0.1, [-120, 0, -30]), (0.24, [-70, 0, -50]), (0.55, Z)]},
    torso={"rotation": [(0, Z), (0.24, [-20, 0, 0]), (0.55, Z)]},
)

# Feet-first slide: low, leaning back, legs out front, one arm back for balance.
A["slide"] = anim(
    0.5,
    body={"rotation": [(0, Z), (0.08, [-55, 0, 0]), (0.42, [-50, 0, 0]), (0.5, Z)],
          "position": [(0, Z), (0.08, [0, -10, 0]), (0.42, [0, -10, 0]), (0.5, Z)]},
    right_leg={"rotation": [(0, Z), (0.08, [-80, 0, 5]), (0.42, [-80, 0, 5]), (0.5, Z)]},
    left_leg={"rotation": [(0, Z), (0.08, [-45, 0, -5]), (0.42, [-45, 0, -5]), (0.5, Z)]},
    right_arm={"rotation": [(0, Z), (0.08, [40, 0, 35]), (0.42, [40, 0, 35]), (0.5, Z)]},
    left_arm={"rotation": [(0, Z), (0.08, [-70, 0, -20]), (0.42, [-70, 0, -20]), (0.5, Z)]},
    torso={"rotation": [(0, Z), (0.08, [-10, 0, 0]), (0.42, [-10, 0, 0]), (0.5, Z)]},
    head={"rotation": [(0, Z), (0.08, [30, 0, 0]), (0.42, [30, 0, 0]), (0.5, Z)]},
)

# Combat run (looping): leaning forward, arms trailing back, long strides.
A["run"] = anim(
    0.5,
    loop=True,
    body={"rotation": [(0, [8, 0, 0]), (0.5, [8, 0, 0])]},
    torso={"rotation": [(0, [18, 0, 0]), (0.5, [18, 0, 0])]},
    head={"rotation": [(0, [-15, 0, 0]), (0.5, [-15, 0, 0])]},
    right_arm={"rotation": [(0, [30, 0, 15]), (0.25, [55, 0, 15]), (0.5, [30, 0, 15])]},
    left_arm={"rotation": [(0, [55, 0, -15]), (0.25, [30, 0, -15]), (0.5, [55, 0, -15])]},
    right_leg={"rotation": [(0, [-55, 0, 0]), (0.25, [45, 0, 0]), (0.5, [-55, 0, 0])]},
    left_leg={"rotation": [(0, [45, 0, 0]), (0.25, [-55, 0, 0]), (0.5, [45, 0, 0])]},
)

MAX_TORSO_TWIST = 25.0


def _sample(frames, t):
    """Linear interpolation of a keyframe dict {"time": {"vector": v}} at time t (clamped)."""
    pts = sorted((float(k), d["vector"]) for k, d in frames.items())
    if t <= pts[0][0]:
        return list(pts[0][1])
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        if t0 <= t <= t1:
            k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return list(pts[-1][1])


def fix_torso(a):
    """
    In Minecraft's player model the torso is a separate box hinged at the neck: pitching it
    forward/back swings it away from the hips and legs, which looks broken. Move all torso pitch
    onto the whole body ("body" bone) instead, and limit torso twist.
    """
    if a.pop("keep_torso", False):
        return a
    bones = a["bones"]
    torso = bones.get("torso", {}).get("rotation")
    if not torso:
        return a
    body = bones.setdefault("body", {})
    body_rot = body.get("rotation")
    times = sorted({float(k) for k in torso} | ({float(k) for k in body_rot} if body_rot else set()))
    new_body, new_torso = [], []
    for t in times:
        tv = _sample(torso, t)
        bv = _sample(body_rot, t) if body_rot else [0, 0, 0]
        new_body.append((t, [bv[0] + tv[0], bv[1], bv[2]]))
        twist = max(-MAX_TORSO_TWIST, min(MAX_TORSO_TWIST, tv[1]))
        new_torso.append((t, [0, twist, tv[2]]))
    body["rotation"] = kf(new_body)
    bones["torso"]["rotation"] = kf(new_torso)
    return a


if __name__ == "__main__":
    for name in A:
        A[name] = fix_torso(A[name])
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": A}, f, indent=1)
    print(f"{len(A)} animations -> {os.path.normpath(out)}")
    print(", ".join(A))
