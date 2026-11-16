"""
CMU motion-capture (ASF/AMC) -> Minecraft player/mob animation converter.

Source data: CMU Graphics Lab Motion Capture Database (http://mocap.cs.cmu.edu),
"free for all uses". Files are read from a local folder (not stored in the repo).

What it does:
  * forward kinematics of the CMU skeleton (ASF bones + AMC per-frame rotations, 120 fps)
  * maps the motion onto Minecraft's 6-box body:
      body (whole player)  <- spine line (pelvis -> neck) + hip facing, plus height changes
      torso twist          <- shoulder line vs hip line
      arms                 <- shoulder -> wrist direction   (Minecraft has no elbows)
      legs                 <- hip -> ankle direction        (no knees)
      head                 <- neck -> head-top direction
  * samples at 20 fps and writes playerAnimator (GeckoLib-format) keyframes.

Usage (from the repo root):
  python tools/mocap.py inspect <data_dir> <trial>            # timeline to pick a clip window
  python tools/mocap.py build <data_dir>                      # build all clips listed in CLIPS
"""
import json
import math
import os
import sys

# ----------------------------------------------------------------------------- vector math

def v_add(a, b): return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
def v_sub(a, b): return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
def v_scale(a, s): return [a[0] * s, a[1] * s, a[2] * s]
def v_dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def v_cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
def v_len(a): return math.sqrt(v_dot(a, a))
def v_norm(a):
    l = v_len(a)
    return [a[0] / l, a[1] / l, a[2] / l] if l > 1e-9 else [0.0, 0.0, 0.0]

def m_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def m_vec(m, v):
    return [m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]]
def m_t(m): return [[m[j][i] for j in range(3)] for i in range(3)]

def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]
def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

def euler_xyz(ax, ay, az):
    """ASF/AMC convention ('sxyz', static axes): R = Rz(az) * Ry(ay) * Rx(ax). Degrees in."""
    return m_mul(rz(math.radians(az)), m_mul(ry(math.radians(ay)), rx(math.radians(ax))))

# ----------------------------------------------------------------------------- ASF / AMC

class Bone:
    def __init__(self):
        self.name = ""
        self.direction = [0, 0, 0]
        self.length = 0.0
        self.axis = [0, 0, 0]
        self.dof = []
        self.children = []
        self.parent = None


def parse_asf(path):
    bones = {}
    root = Bone()
    root.name = "root"
    bones["root"] = root
    section = None
    cur = None
    lines = open(path, encoding="latin-1").read().split("\n")
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        i += 1
        if not line or line.startswith("#"):
            continue
        if line.startswith(":"):
            section = line.split()[0]
            continue
        parts = line.split()
        if section == ":root":
            if parts[0] == "orientation":
                root.axis = [float(x) for x in parts[1:4]]
        elif section == ":bonedata":
            if parts[0] == "begin":
                cur = Bone()
            elif parts[0] == "end":
                bones[cur.name] = cur
                cur = None
            elif parts[0] == "name":
                cur.name = parts[1]
            elif parts[0] == "direction":
                cur.direction = [float(x) for x in parts[1:4]]
            elif parts[0] == "length":
                cur.length = float(parts[1])
            elif parts[0] == "axis":
                cur.axis = [float(x) for x in parts[1:4]]
            elif parts[0] == "dof":
                cur.dof = [d.lower() for d in parts[1:]]
        elif section == ":hierarchy":
            if parts[0] in ("begin", "end"):
                continue
            parent = bones[parts[0]]
            for child in parts[1:]:
                bones[child].parent = parent
                parent.children.append(bones[child])
    for b in bones.values():
        b.C = euler_xyz(*b.axis)
        b.Cinv = m_t(b.C)
    return bones


def parse_amc(path):
    frames = []
    cur = None
    for line in open(path, encoding="latin-1"):
        line = line.strip()
        if not line or line.startswith("#") or line.startswith(":"):
            continue
        parts = line.split()
        if parts[0].isdigit() and len(parts) == 1:
            cur = {}
            frames.append(cur)
        elif cur is not None:
            cur[parts[0]] = [float(x) for x in parts[1:]]
    return frames


def forward_kinematics(bones, frame):
    """Returns {bone: (end_position, world_matrix)}; root -> (translation, matrix)."""
    out = {}
    r = frame["root"]
    root = bones["root"]
    m = m_mul(root.C, m_mul(euler_xyz(r[3], r[4], r[5]), root.Cinv))
    out["root"] = (r[0:3], m)

    def visit(bone):
        parent_pos, parent_m = out[bone.parent.name]
        vals = frame.get(bone.name, [])
        rot = {"rx": 0.0, "ry": 0.0, "rz": 0.0}
        for d, v in zip(bone.dof, vals):
            rot[d] = v
        motion = euler_xyz(rot["rx"], rot["ry"], rot["rz"])
        mat = m_mul(parent_m, m_mul(bone.C, m_mul(motion, bone.Cinv)))
        pos = v_add(parent_pos, v_scale(m_vec(mat, bone.direction), bone.length))
        out[bone.name] = (pos, mat)
        for c in bone.children:
            visit(c)

    for c in root.children:
        visit(c)
    return out

# ----------------------------------------------------------------------------- Minecraft mapping

def joints(fk):
    p = lambda n: fk[n][0]
    return {
        "pelvis": p("root"),
        "lhip": p("lhipjoint"), "rhip": p("rhipjoint"),
        "lknee": p("lfemur"), "rknee": p("rfemur"),
        "lankle": p("ltibia"), "rankle": p("rtibia"),
        "neck": p("thorax"),
        "headbase": p("upperneck"), "headtop": p("head"),
        "lshoulder": p("lclavicle"), "rshoulder": p("rclavicle"),
        "lwrist": p("lradius"), "rwrist": p("rradius"),
    }


def body_frame(j):
    """Orthonormal (right, up, back) of the body in CMU world space (Y up)."""
    up = v_norm(v_sub(j["neck"], j["pelvis"]))
    right = v_sub(j["rhip"], j["lhip"])
    right = v_norm(v_sub(right, v_scale(up, v_dot(right, up))))
    forward = v_cross(up, right)
    back = v_scale(forward, -1)
    return right, up, back


def mc_limb_angles(d_body):
    """
    Limb direction (body-local right/up/back) -> Minecraft part (xRot, zRot) in degrees for a
    limb hanging down at rest. Model space: x = player's left, y = down, z = back.
    """
    mx, my, mz = -d_body[0], -d_body[1], d_body[2]
    a = math.asin(max(-1.0, min(1.0, mz)))
    b = math.atan2(-mx, my)
    return math.degrees(a), math.degrees(b)


def mc_head_angles(d_body):
    mx, my, mz = -d_body[0], -d_body[1], d_body[2]
    a = math.asin(max(-1.0, min(1.0, -mz)))
    b = math.atan2(mx, -my)
    return math.degrees(a), math.degrees(b)


def decompose_zyx(m):
    """m = Rz(g) * Ry(b) * Rx(a) -> (a, b, g) radians."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    g = math.atan2(m[1][0], m[0][0])
    return a, b, g


def nearest_euler(prev, a, b, g):
    """
    A rotation has two Rz*Ry*Rx angle sets: (a, b, g) and (a+180, 180-b, g+180). Pick the one
    (and the 360-wraps) closest to the previous frame so interpolation never takes a long way round.
    """
    best, best_d = None, None
    for cand in ((a, b, g), (a + 180.0, 180.0 - b, g + 180.0)):
        c = [unwrap(pv, cv) for pv, cv in zip(prev, cand)]
        d = sum((cv - pv) ** 2 for cv, pv in zip(c, prev))
        if best is None or d < best_d:
            best, best_d = c, d
    return best


def unwrap(prev, cur):
    while cur - prev > 180:
        cur -= 360
    while cur - prev < -180:
        cur += 360
    return cur


def load(data_dir, trial):
    subject = trial.split("_")[0]
    bones = parse_asf(os.path.join(data_dir, subject + ".asf"))
    frames = parse_amc(os.path.join(data_dir, trial + ".amc"))
    return bones, frames


def convert(bones, frames, start_s, end_s, keep_travel=False, keep_yaw=True, fps=120, speed=1.0,
            stand_s=None):
    """
    Converts [start_s, end_s] of a take to keyframes at 20 per second of OUTPUT time
    (speed > 1 plays the motion faster). stand_s: a time in the take where the actor stands
    upright, used as the height reference (defaults to the clip start).
    """
    step = max(1, round(fps / 20 * speed))
    f0 = int(start_s * fps)
    f1 = min(len(frames) - 1, int(end_s * fps))
    sample = list(range(f0, f1 + 1, step))
    all_j = [joints(forward_kinematics(bones, frames[i])) for i in sample]
    ref = joints(forward_kinematics(bones, frames[int(stand_s * fps)])) if stand_s is not None else all_j[0]

    # Scale: standing leg length (hip -> ankle) maps to Minecraft's 0.75-block leg.
    j0 = all_j[0]
    leg = (v_len(v_sub(ref["lankle"], ref["lhip"])) + v_len(v_sub(ref["rankle"], ref["rhip"]))) / 2
    floor = min(min(j["lankle"][1], j["rankle"][1]) for j in all_j + [ref])
    rest_pelvis = ref["pelvis"][1] - min(ref["lankle"][1], ref["rankle"][1]) + (min(ref["lankle"][1], ref["rankle"][1]) - floor)
    to_blocks = 0.75 / leg

    # Reference frame: facing at the clip start -> Minecraft local (right +X, up +Y, back +Z).
    r0, _, b0 = body_frame(j0)
    r0h = v_norm([r0[0], 0, r0[2]])
    b0h = v_norm([b0[0], 0, b0[2]])
    W = [r0h, [0, 1, 0], b0h]  # rows: world -> local

    tracks = {k: [] for k in ("body_rot", "body_rot_actual", "body_pos", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg")}
    prev_actual = [0.0, 0.0, 0.0]
    for idx, j in enumerate(all_j):
        t = round(idx * 0.05, 3)
        right, up, back = body_frame(j)
        B = [right, up, back]                       # rows = body axes in world
        Bl = m_t([m_vec(W, right), m_vec(W, up), m_vec(W, back)])  # columns = body axes in local
        a, b, g = decompose_zyx(Bl)
        actual = nearest_euler(prev_actual, math.degrees(a), math.degrees(b), math.degrees(g))
        prev_actual = actual
        if not keep_yaw:
            actual = [actual[0], 0.0, actual[2]]
        # file format negates x and y (playerAnimator's loader flips them back)
        rot = [-actual[0], -actual[1], actual[2]]
        tracks["body_rot"].append((t, rot))
        tracks["body_rot_actual"].append((t, actual))

        # Height: pelvis above the floor vs standing, in blocks; optional travel.
        dy = (j["pelvis"][1] - floor - rest_pelvis) * to_blocks
        off = [0.0, dy, 0.0]
        if keep_travel:
            d = m_vec(W, v_sub(j["pelvis"], j0["pelvis"]))
            off[0], off[2] = d[0] * to_blocks, d[2] * to_blocks
        # file format: x = -local x, y = local y, z = local z (back); pixels
        tracks["body_pos"].append((t, [round(-off[0] * 16, 2), round(off[1] * 16, 2), round(off[2] * 16, 2)]))

        def local(v):
            return [v_dot(v, right), v_dot(v, up), v_dot(v, back)]

        # Torso twist: shoulder line vs hip line around the spine.
        sh = local(v_sub(j["rshoulder"], j["lshoulder"]))
        twist = math.degrees(math.atan2(sh[2], sh[0]))
        tracks["torso"].append((t, [0, round(max(-40, min(40, twist)), 2), 0]))

        ha, hb = mc_head_angles(local(v_norm(v_sub(j["headtop"], j["headbase"]))))
        tracks["head"].append((t, [round(ha, 2), 0, round(hb, 2)]))
        for side, sh_key, wr_key in (("right_arm", "rshoulder", "rwrist"), ("left_arm", "lshoulder", "lwrist")):
            xa, za = mc_limb_angles(local(v_norm(v_sub(j[wr_key], j[sh_key]))))
            tracks[side].append((t, [round(xa, 2), 0, round(za, 2)]))
        for side, hip_key, an_key in (("right_leg", "rhip", "rankle"), ("left_leg", "lhip", "lankle")):
            xa, za = mc_limb_angles(local(v_norm(v_sub(j[an_key], j[hip_key]))))
            tracks[side].append((t, [round(xa, 2), 0, round(za, 2)]))

    length = round((len(sample) - 1) * 0.05, 3)
    return length, tracks


def to_animation(length, tracks, loop=False, ease_out=0.0):
    """playerAnimator JSON animation; optionally blends the last ease_out seconds back to rest."""
    def kf(frames):
        return {f"{t:g}": {"vector": [round(x, 2) for x in v]} for t, v in frames}

    def blend_out(frames):
        if ease_out <= 0:
            return frames
        out = []
        for t, v in frames:
            k = max(0.0, min(1.0, (length - t) / ease_out))
            out.append((t, [x * k for x in v]))
        return out

    bones = {
        "body": {"rotation": kf(blend_out(tracks["body_rot"])), "position": kf(blend_out(tracks["body_pos"]))},
        "torso": {"rotation": kf(blend_out(tracks["torso"]))},
        "head": {"rotation": kf(blend_out(tracks["head"]))},
    }
    for limb in ("right_arm", "left_arm", "right_leg", "left_leg"):
        bones[limb] = {"rotation": kf(blend_out(tracks[limb]))}
    return {"loop": loop, "animation_length": length, "bones": bones}

# ----------------------------------------------------------------------------- inspect

def inspect(data_dir, trial):
    bones, frames = load(data_dir, trial)
    print(f"{trial}: {len(frames)} frames = {len(frames) / 120:.1f}s")
    length, tr = convert(bones, frames, 0, len(frames) / 120)
    print(" time  hipY  pitch   yaw | Rarm(x,z)   Larm(x,z)  | Rleg(x,z)   Lleg(x,z)")
    for i in range(0, len(tr["body_rot"]), 2):
        t = tr["body_rot"][i][0]
        br = tr["body_rot"][i][1]
        by = tr["body_pos"][i][1][1] / 16
        ra, la = tr["right_arm"][i][1], tr["left_arm"][i][1]
        rl, ll = tr["right_leg"][i][1], tr["left_leg"][i][1]
        print(f"{t:5.1f} {by:5.2f} {br[0]:6.0f} {br[1]:6.0f} | {ra[0]:5.0f},{ra[2]:4.0f}  {la[0]:5.0f},{la[2]:4.0f} | "
              f"{rl[0]:5.0f},{rl[2]:4.0f}  {ll[0]:5.0f},{ll[2]:4.0f}")

# ----------------------------------------------------------------------------- clips

# name: (trial, start s, end s, options)   -- filled in after inspecting the takes
# Player animations (playerAnimator format). Names match combat/Anim.java.
PLAYER_CLIPS = {
    # --- locomotion ---
    "run": ("127_06", 0.2, 0.8, {"loop": True, "yaw": False, "pitch_scale": 0.45}),
    "roll": ("127_23", 0.0, 1.9, {"yaw": False, "speed": 3.6}),
    # --- strikes (window: just before the impact peak to the recovery) ---
    "jab_right": ("144_20", 1.80, 2.30, {"yaw": False, "ease_out": 0.12}),
    "jab_left": ("144_20", 1.05, 1.55, {"yaw": False, "ease_out": 0.12}),
    "hook_right": ("14_01", 7.40, 7.90, {"yaw": False, "ease_out": 0.12}),
    "hook_left": ("14_01", 8.30, 8.80, {"yaw": False, "ease_out": 0.12}),
    "haymaker": ("143_23", 3.95, 4.55, {"yaw": False, "ease_out": 0.15}),
    "front_kick": ("135_04", 2.95, 3.65, {"yaw": False, "ease_out": 0.15}),
    "side_kick": ("135_11", 2.75, 3.50, {"yaw": False, "ease_out": 0.15}),
}

# Enemy animations (MobAnimator format: absolute angles per tick).
MOB_CLIPS = {
    "fall_back": ("90_18", 0.55, 1.45, {"yaw": False}),
    "get_up": ("140_08", 1.4, 5.4, {"yaw": False, "speed": 4.0, "stand": 6.0}),
}


def build_mob(data_dir, out_path, clips):
    """
    Mob clips: one frame per tick with ABSOLUTE values, applied by MobAnimator:
      body  = [a, b, g] degrees, applied as Rz(g) Ry(b) Rx(a) around a point 0.7 blocks up
      pos   = [x, y, z] blocks in the body's yaw frame (x right, y up, z back)
      limbs = [xRot, yRot, zRot] degrees in model space
    """
    out = {}
    for name, (trial, s, e, opt) in clips.items():
        bones, frames = load(data_dir, trial)
        length, tr = convert(bones, frames, s, e, keep_yaw=opt.get("yaw", True), speed=opt.get("speed", 1.0),
                             stand_s=opt.get("stand"))
        clip = []
        for i in range(len(tr["body_rot_actual"])):
            p = tr["body_pos"][i][1]
            clip.append({
                "body": [round(x, 2) for x in tr["body_rot_actual"][i][1]],
                "pos": [round(-p[0] / 16, 3), round(p[1] / 16, 3), round(p[2] / 16, 3)],
                **{k: tr[k][i][1] for k in ("torso", "head", "right_arm", "left_arm", "right_leg", "left_leg")},
            })
        out[name] = clip
        print(f"mob {name}: {trial} {s}-{e}s -> {len(clip)} ticks")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f)
    print(f"wrote {len(out)} mob clips to {out_path}")


def build(data_dir, out_path, clips):
    anims = {}
    for name, (trial, s, e, opt) in clips.items():
        bones, frames = load(data_dir, trial)
        length, tracks = convert(bones, frames, s, e, keep_travel=opt.get("travel", False),
                                 keep_yaw=opt.get("yaw", True), speed=opt.get("speed", 1.0),
                                 stand_s=opt.get("stand"))
        if opt.get("pitch_scale") is not None:
            k = opt["pitch_scale"]
            tracks["body_rot"] = [(t, [v[0] * k, v[1], v[2]]) for t, v in tracks["body_rot"]]
        anims[name] = to_animation(length, tracks, loop=opt.get("loop", False), ease_out=opt.get("ease_out", 0.0))
        print(f"{name}: {trial} {s}-{e}s -> {length}s")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)
    print(f"wrote {len(anims)} animations to {out_path}")


if __name__ == "__main__":
    if sys.argv[1] == "inspect":
        inspect(sys.argv[2], sys.argv[3])
    elif sys.argv[1] == "build":
        assets = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "freeflow")
        build(sys.argv[2], os.path.join(assets, "player_animations", "freeflow_mocap.json"), PLAYER_CLIPS)
        build_mob(sys.argv[2], os.path.join(assets, "mob_animations.json"), MOB_CLIPS)
