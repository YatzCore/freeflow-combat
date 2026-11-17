"""
Animation rig for the freeflow combat animations.

Minecraft's humanoid model is six loose boxes: nothing is parented to anything, so leaning the
torso leaves the arms and head behind and lowering the body pushes the feet through the floor.
The limbs are also single boxes: no elbows, no knees. This module puts a real skeleton on top:

    hips (whole body: rotation + offset)
      |- legs                      (rotate at the hip joints, bend at the knees)
      `- chest (lean/twist/bend at the waist)
           |- head                 (stays level: its rotation is relative to the hips)
           `- arms                 (rotate at the shoulders, carried by the chest, bend at the elbows)

Poses are written against that skeleton, interpolated with easing, sampled once per game tick,
flattened back to Minecraft's six boxes (every box gets a position AND a rotation, every limb a
bend) and finally GROUNDED: the lowest corner of the posed model is put exactly on the floor
(plus the pose's "air" height). Nothing can sink into the ground, and crouches, lunges and
kneels get the right hip height for free.

Units: pixels (1/16 block) and degrees.

Pose channels (all 3-vectors):
    hips   pitch (+ = forward), yaw (+ = turn left), roll (+ = lean right)   whole body, at hip height
    pos    x (+ = right), air (height of the lowest point above the floor), z (+ = forward)
    chest  lean (+ = forward), twist (+ = left shoulder back), side (+ = bend right)
    head   pitch (+ = look down), yaw (+ = look left), tilt (+ = right)       relative to the hips
    ra/la  right/left arm,  Minecraft angles relative to the chest (the UPPER arm when the elbow bends)
    rl/ll  right/left leg,  Minecraft angles relative to the hips  (the THIGH when the knee bends)
    re/le  right/left elbow: bend (+ = the natural way: forearm folds forward), hinge turn, unused
    rk/lk  right/left knee:  bend (+ = the natural way: shin folds back),      hinge turn, unused
  Minecraft limb angles [x, y, z] for a limb hanging down:
    x: -90 = straight forward, -180 = straight up, + = backward
    y: with the limb raised forward, + swings it to the player's right, - to the left
    z: + swings it out to the player's right, - to the left

How a limb bends (the game's BentLimbs.java does exactly the same): the limb's box is cut across
its middle; the lower half turns about a hinge through the centre of that cut (the hinge is the
limb's own left-right axis, turned by "hinge turn" degrees about the limb's length); the ring of
corners on the cut is laid on the plane halfway between the two halves so the joint stays closed.
"""
import math

import numpy as np

# ----------------------------------------------------------------------------- model

HIP_Y = 12.0          # hip joint height above the feet (px)
MODEL_TOP = 24.016    # model-space origin (neck) above the feet: 1.501 blocks
PA_PIVOT = 11.2       # playerAnimator rotates the "body" bone around 0.7 blocks up

# name: (pivot in model space, box min, box max)   -- model space: x = player's left, y = down, z = back
PARTS = {
    "head": ((0, 0, 0), (-4, -8, -4), (4, 0, 4)),
    "torso": ((0, 0, 0), (-4, 0, -2), (4, 12, 2)),
    "right_arm": ((-5, 2, 0), (-3, -2, -2), (1, 10, 2)),
    "left_arm": ((5, 2, 0), (-1, -2, -2), (3, 10, 2)),
    "right_leg": ((-1.9, 12, 0), (-2, 0, -2), (2, 12, 2)),
    "left_leg": ((1.9, 12, 0), (-2, 0, -2), (2, 12, 2)),
}
PART_NAMES = list(PARTS)
WAIST = np.array([0.0, 12.0, 0.0])

# limb: (pose channel of its joint, sign of the hinge rotation for a natural bend, bone name in the files)
JOINTS = {
    "right_arm": ("re", -1.0, "right_elbow"),
    "left_arm": ("le", -1.0, "left_elbow"),
    "right_leg": ("rk", 1.0, "right_knee"),
    "left_leg": ("lk", 1.0, "left_knee"),
}
LIMBS = list(JOINTS)
MAX_JOINT_STRETCH = 1.6   # how far the outer corner of a joint may be pushed out to keep it closed

CHANNELS = ("hips", "pos", "chest", "head", "ra", "la", "rl", "ll", "re", "le", "rk", "lk")
BENDS = ("re", "le", "rk", "lk")
REST = {c: (0.0, 0.0, 0.0) for c in CHANNELS}


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler(x, y, z):
    """Minecraft ModelPart rotation: Rz * Ry * Rx, degrees in."""
    return rz(math.radians(z)) @ ry(math.radians(y)) @ rx(math.radians(x))


def decompose(m, prev):
    """Rz*Ry*Rx angles (degrees) of matrix m, choosing the solution closest to prev."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    g = math.atan2(m[1][0], m[0][0])
    a, b, g = math.degrees(a), math.degrees(b), math.degrees(g)
    best, best_d = None, None
    for cand in ((a, b, g), (a + 180.0, 180.0 - b, g + 180.0)):
        c = [cv - 360.0 * round((cv - pv) / 360.0) for pv, cv in zip(prev, cand)]
        d = sum((cv - pv) ** 2 for cv, pv in zip(c, prev))
        if best is None or d < best_d:
            best, best_d = c, d
    return best


# ----------------------------------------------------------------------------- poses

def P(base=None, **over):
    """A pose: copy of base (or the rest pose) with some channels replaced. Joints take a number."""
    out = dict(REST if base is None else base)
    for k, v in over.items():
        if k not in CHANNELS:
            raise KeyError(k)
        if k in BENDS and isinstance(v, (int, float)):
            v = (v, 0.0, 0.0)
        out[k] = tuple(float(x) for x in v)
    return out


def mirror(pose):
    """Left/right mirror image of a pose."""
    def lr(v):
        return (v[0], -v[1], -v[2])
    return {
        "hips": lr(pose["hips"]),
        "pos": (-pose["pos"][0], pose["pos"][1], pose["pos"][2]),
        "chest": lr(pose["chest"]),
        "head": lr(pose["head"]),
        "ra": lr(pose["la"]), "la": lr(pose["ra"]),
        "rl": lr(pose["ll"]), "ll": lr(pose["rl"]),
        "re": lr(pose["le"]), "le": lr(pose["re"]),
        "rk": lr(pose["lk"]), "lk": lr(pose["rk"]),
    }


def blend(a, b, k):
    return {c: tuple(x + (y - x) * k for x, y in zip(a[c], b[c])) for c in CHANNELS}


EASE = {
    "lin": lambda k: k,
    "in": lambda k: k * k,
    "out": lambda k: 1 - (1 - k) ** 3,
    "io": lambda k: k * k * (3 - 2 * k),
    "snap": lambda k: 1 - (1 - k) ** 5,
    # overshoots the target a little and settles back
    "back": lambda k: 1 + 2.2 * (k - 1) ** 3 + 1.2 * (k - 1) ** 2,
}


def K(tick, pose, ease="io"):
    """Keyframe: reach `pose` at `tick`; `ease` shapes the motion arriving at it."""
    return (tick, pose, ease)


class Clip:
    """
    keys: list of K(...). loop: seamless cycle of `length` ticks (the last key must equal the
    first). impact: the tick on which the blow lands, the blade leaves the hand, the line is
    yanked... (playback is aligned to the tick the server makes it happen).
    """

    def __init__(self, name, keys, loop=False, impact=None, parts=None):
        self.name = name
        self.keys = sorted(keys, key=lambda k: k[0])
        self.loop = loop
        self.impact = impact
        self.length = self.keys[-1][0]
        self.parts = parts  # None = all; otherwise the bones this clip controls (rest stay vanilla)

    def pose_at(self, t):
        keys = self.keys
        if t <= keys[0][0]:
            return dict(keys[0][1])
        for (t0, p0, _), (t1, p1, e1) in zip(keys, keys[1:]):
            if t0 <= t <= t1:
                k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                return blend(p0, p1, EASE[e1](k))
        return dict(keys[-1][1])

    def mirrored(self, name):
        return Clip(name, [(t, mirror(p), e) for t, p, e in self.keys], self.loop, self.impact, self.parts)


# ----------------------------------------------------------------------------- forward kinematics

FLIP = np.diag([-1.0, -1.0, 1.0])  # model space <-> entity space (x right, y up, z back)


def body_matrix(hips):
    """Whole-body rotation in entity space (x right, y up, z back)."""
    pitch, yaw, roll = hips
    return rz(math.radians(-roll)) @ ry(math.radians(yaw)) @ rx(math.radians(-pitch))


def flatten(pose, prev=None):
    """
    Skeleton pose -> Minecraft's six loose boxes.
    Returns {part: (pivot xyz in model space, [xRot, yRot, zRot] degrees, rotation matrix)}.
    """
    prev = prev or {}
    lean, twist, side = pose["chest"]
    rc = euler(lean, -twist, -side)
    hp, hy, ht = pose["head"]
    out = {}

    def put(name, pivot, m):
        out[name] = (pivot, decompose(m, prev.get(name, (0.0, 0.0, 0.0))), m)

    neck = WAIST + rc @ (np.array(PARTS["torso"][0], float) - WAIST)
    put("torso", neck, rc)
    put("head", neck, euler(hp, -hy, -ht))
    for name, ch in (("right_arm", "ra"), ("left_arm", "la")):
        shoulder = WAIST + rc @ (np.array(PARTS[name][0], float) - WAIST)
        put(name, shoulder, rc @ euler(*pose[ch]))
    for name, ch in (("right_leg", "rl"), ("left_leg", "ll")):
        put(name, np.array(PARTS[name][0], float), euler(*pose[ch]))
    return out


def joint_angles(pose, part):
    """(hinge rotation, hinge turn) in degrees as the game applies them to this limb; (0, 0) = straight."""
    if part not in JOINTS:
        return 0.0, 0.0
    channel, sign, _ = JOINTS[part]
    bend, turn, _ = pose[channel]
    return sign * bend, turn


def bend_point(p, lo, hi, angle, turn):
    """
    Where a point of a limb's box (part space, px) ends up when the limb is bent: `angle` degrees
    about the hinge through the centre of the box's middle cut, the hinge being the x axis turned
    `turn` degrees about the limb's length. BentLimbs.java is the same function.
    """
    if angle == 0.0:
        return np.array(p, float)
    mid_y = (lo[1] + hi[1]) / 2.0
    c = np.array([(lo[0] + hi[0]) / 2.0, mid_y, (lo[2] + hi[2]) / 2.0])
    if p[1] < mid_y - 1e-6:
        return np.array(p, float)
    t = math.radians(turn)
    axis = np.array([math.cos(t), 0.0, math.sin(t)])
    d = np.array(p, float) - c

    def about_axis(v, a):
        # Rodrigues rotation of v about `axis` by a radians
        return v * math.cos(a) + np.cross(axis, v) * math.sin(a) + axis * np.dot(axis, v) * (1 - math.cos(a))

    a = math.radians(angle)
    if p[1] > mid_y + 1e-6:
        return c + about_axis(d, a)
    # on the cut: halfway round, pushed out so both halves still meet it
    along = axis * np.dot(axis, d)
    across = d - along
    stretch = min(MAX_JOINT_STRETCH, 1.0 / max(1e-6, math.cos(a / 2.0)))
    return c + along + about_axis(across, a / 2.0) * stretch


def limb_boxes(part, pivot, m, angle=0.0, turn=0.0):
    """
    The box(es) of a part in model space, each as 8 corners. A limb is always returned as its
    upper and lower half (so a bent one draws and measures correctly).
    """
    _, lo, hi = PARTS[part]

    def box(y0, y1):
        pts = [bend_point([x, y, z], lo, hi, angle, turn)
               for x in (lo[0], hi[0]) for y in (y0, y1) for z in (lo[2], hi[2])]
        return np.array(pts) @ m.T + pivot

    if part in JOINTS:
        mid = (lo[1] + hi[1]) / 2.0
        return {part: box(lo[1], mid), part + "_lower": box(mid, hi[1])}
    return {part: box(lo[1], hi[1])}


def to_entity(model_pts, hips, offset):
    """Model-space points -> entity space (px; origin at the feet), given body rotation and hip offset."""
    p = model_pts @ FLIP.T + np.array([0.0, MODEL_TOP, 0.0])
    pivot = np.array([0.0, HIP_Y, 0.0])
    return (p - pivot) @ body_matrix(hips).T + pivot + offset


def posed_boxes(pose, flat, y):
    """{box name: 8 corners in entity space} with the hips raised by y (limbs come as two halves)."""
    off = np.array([pose["pos"][0], y, -pose["pos"][2]])
    out = {}
    for name, (pv, _, m) in flat.items():
        angle, turn = joint_angles(pose, name)
        for box_name, pts in limb_boxes(name, pv, m, angle, turn).items():
            out[box_name] = to_entity(pts, pose["hips"], off)
    return out


def ground_height(pose, flat):
    """Hip offset that puts the lowest corner of the posed model on the floor."""
    lowest = min(pts[:, 1].min() for pts in posed_boxes(pose, flat, 0.0).values())
    return -lowest


# ----------------------------------------------------------------------------- baking

class Frame:
    """One baked tick: the skeleton pose, the flattened boxes and the grounded hip height."""

    def __init__(self, pose, flat, y):
        self.pose = pose
        self.flat = flat
        self.y = y

    def boxes(self):
        return posed_boxes(self.pose, self.flat, self.y)


def bake(clip):
    """Samples a clip once per tick (0..length; a loop stops one short) and grounds every frame."""
    frames = []
    prev = None
    last = clip.length - 1 if clip.loop else clip.length
    for t in range(0, last + 1):
        pose = clip.pose_at(t)
        flat = flatten(pose, prev)
        prev = {n: f[1] for n, f in flat.items()}
        frames.append(Frame(pose, flat, ground_height(pose, flat) + pose["pos"][1]))
    return frames


def body_file_transform(frame):
    """
    playerAnimator "body" bone values for a frame: (rotation, position) in the file's own
    conventions. It rotates around a point 0.7 blocks up; we rotate around the hips, so the
    difference goes into the position.
    """
    pitch, yaw, roll = frame.pose["hips"]
    r = body_matrix(frame.pose["hips"])
    d = np.array([0.0, HIP_Y - PA_PIVOT, 0.0])
    off = np.array([frame.pose["pos"][0], frame.y, -frame.pose["pos"][2]]) + d - r @ d
    return [pitch, -yaw, -roll], [-off[0], off[1], off[2]]


def wrap(v):
    return [((x + 180.0) % 360.0) - 180.0 for x in v]


def _r(v, n=2):
    return [round(float(x), n) + 0.0 for x in v]


def _time(tick):
    # playerAnimator truncates time*20 to a tick: nudge so float rounding can never drop one
    return f"{(tick + 0.02) / 20:g}"


def to_player_json(clip):
    """
    playerAnimator (GeckoLib-format) animation: one keyframe per tick for every bone. The joints
    travel as four extra bones (right_elbow, left_elbow, right_knee, left_knee) whose rotation is
    [hinge rotation, hinge turn, 0]; playerAnimator blends them like any other bone and
    BentLimbs.java reads them back. They are always written, even when straight, so a
    straight-armed move is never bent by the stance playing underneath it.
    """
    frames = bake(clip)
    n = len(frames)
    tracks = {"body": {"rotation": [], "position": []}}
    for name in PART_NAMES:
        tracks[name] = {"rotation": [], "position": []}
    for _, _, bone in JOINTS.values():
        tracks[bone] = {"rotation": []}
    for f in frames:
        rot, pos = body_file_transform(f)
        tracks["body"]["rotation"].append(rot)
        tracks["body"]["position"].append(pos)
        for name in PART_NAMES:
            pivot, ang, _ = f.flat[name]
            d = pivot - np.array(PARTS[name][0], float)
            tracks[name]["rotation"].append(list(ang))
            tracks[name]["position"].append([d[0], -d[1], d[2]])
        for limb, (_, _, bone) in JOINTS.items():
            angle, turn = joint_angles(f.pose, limb)
            tracks[bone]["rotation"].append([angle, turn, 0.0])

    wanted = set(clip.parts) if clip.parts else set(tracks)
    bones = {}
    for bone, chans in tracks.items():
        if bone not in wanted:
            continue
        out = {}
        for ch, values in chans.items():
            if ch == "position" and bone != "body" and all(abs(x) < 0.005 for v in values for x in v):
                continue  # this bone never leaves its socket
            keys = {}
            for t, v in enumerate(values):
                last = t == n - 1 and not clip.loop
                if ch == "rotation" and last and any(abs(x) > 180.0 for x in v):
                    # full turns end on e.g. 360: hand over to the next pose from the equivalent 0
                    keys[_time(t)] = {"pre": {"vector": _r(v)}, "post": {"vector": _r(wrap(v))}}
                else:
                    keys[_time(t)] = {"vector": _r(v)}
            out[ch] = keys
        bones[bone] = out
    anim = {"animation_length": round((n if clip.loop else n - 1) / 20.0, 3), "bones": bones}
    if clip.loop:
        anim["loop"] = True
    return anim


MOB_ROW = 6 + 6 * 6 + 4 * 2


def to_mob_clip(clip):
    """
    Enemy clip, one row per tick (see MobClips.java):
      [bodyX, bodyY, bodyZ,  offX, offZ, air,
       then per part (head, torso, right_arm, left_arm, right_leg, left_leg): xRot, yRot, zRot, dx, dy, dz,
       then per limb (right_arm, left_arm, right_leg, left_leg): hinge rotation, hinge turn]
    body = whole-body rotation in degrees (applied Rz*Ry*Rx around the hips, entity space: x right,
    y up, z back); off/air in blocks; part rotations in degrees and pivot offsets in model pixels.
    The game puts the lowest point of the posed model `air` above the floor itself, because every
    mob has different proportions.
    """
    rows = []
    for f in bake(clip):
        pitch, yaw, roll = f.pose["hips"]
        row = [-pitch, yaw, -roll, f.pose["pos"][0] / 16.0, -f.pose["pos"][2] / 16.0, f.pose["pos"][1] / 16.0]
        for name in PART_NAMES:
            pivot, ang, _ = f.flat[name]
            d = pivot - np.array(PARTS[name][0], float)
            row += list(ang) + [d[0], d[1], d[2]]
        for limb in LIMBS:
            row += list(joint_angles(f.pose, limb))
        assert len(row) == MOB_ROW
        rows.append(_r(row, 3))
    return rows


# ----------------------------------------------------------------------------- verification

def simulate_player_json(anim, substeps=4):
    """
    Independent check of an exported animation: replays it the way playerAnimator does (tick
    truncation, linear interpolation between keys, body bone around 0.7 blocks) and the way
    BentLimbs bends the limbs, and returns, per sample, the height of the lowest box corner above
    the floor in pixels.
    """
    def track(bone, ch):
        keys = anim["bones"].get(bone, {}).get(ch)
        if not keys:
            return None
        pts = []
        for t, v in keys.items():
            tick = int(float(np.float32(float(t)) * np.float32(20.0)))
            if "vector" in v:
                pts.append((tick, v["vector"], v["vector"]))
            else:
                pts.append((tick, v["pre"]["vector"], v["post"]["vector"]))
        pts.sort(key=lambda p: p[0])
        return pts

    def sample(pts, t):
        if pts is None:
            return np.zeros(3)
        if t <= pts[0][0]:
            return np.array(pts[0][1], float)
        for (t0, _, v0), (t1, v1, _) in zip(pts, pts[1:]):
            if t0 <= t <= t1:
                k = (t - t0) / (t1 - t0)
                return np.array(v0, float) + (np.array(v1, float) - np.array(v0, float)) * k
        return np.array(pts[-1][2], float)

    bones = ["body"] + PART_NAMES + [j[2] for j in JOINTS.values()]
    tr = {(b, c): track(b, c) for b in bones for c in ("rotation", "position")}
    end = max(p[0] for pts in tr.values() if pts for p in pts)
    lows = []
    for i in range(end * substeps + 1):
        t = i / substeps
        low = 1e9
        brot = sample(tr[("body", "rotation")], t)
        bpos = sample(tr[("body", "position")], t)
        # loader: rotation x,y negated; position x negated (and /16). renderer: T(pos + 0.7) Rz Ry Rx T(-0.7)
        r = rz(math.radians(brot[2])) @ ry(math.radians(-brot[1])) @ rx(math.radians(-brot[0]))
        off = np.array([-bpos[0], bpos[1], bpos[2]])
        pivot = np.array([0.0, PA_PIVOT, 0.0])
        for name in PART_NAMES:
            rot = sample(tr[(name, "rotation")], t)
            pos = sample(tr[(name, "position")], t)
            pv = np.array(PARTS[name][0], float) + np.array([pos[0], -pos[1], pos[2]])
            angle, turn = 0.0, 0.0
            if name in JOINTS:
                joint = sample(tr[(JOINTS[name][2], "rotation")], t)
                angle, turn = joint[0], joint[1]
            for pts in limb_boxes(name, pv, euler(*rot), angle, turn).values():
                pts = pts @ FLIP.T + np.array([0.0, MODEL_TOP, 0.0])
                pts = (pts - pivot) @ r.T + pivot + off
                low = min(low, pts[:, 1].min())
        lows.append((t, low))
    return lows
