"""
The player's combat animations, written against the skeleton in rig.py (read its header for the
channels and angle conventions). Style rules every clip follows:
  * everything starts from and returns to the GUARD stance, never a stiff upright pose;
  * blows snap out in a tick or two, hold, then recover slowly (the hit lands on `impact`);
  * the hips and chest turn into every strike, the off hand stays up, the eyes stay on target;
  * elbows and knees do the work: fists are carried at the chin on bent arms, the legs are
    never locked, kicks and knees chamber, and a punch only straightens the arm on the hit.
"""
import rig
from rig import Clip, K, P


def S(hips=(0, 0, 0), chest=(0, 0, 0), look=(0, 0, 0), la=(0, 0, 0), ra=(0, 0, 0), ll=(0, 0, 0), rl=(0, 0, 0),
      pos=(0, 0, 0), le=0, re=0, lk=0, rk=0, arms_on_chest=False):
    """
    A standing pose written relative to the TARGET instead of the turning body, so the feet stay
    planted and the fists stay on line while the hips and chest rotate underneath:
      look   head pitch / yaw / tilt relative to straight ahead
      la/ra  upper-arm angles relative to the world: first value -90 = level, middle value = where
             the raised arm points (0 = at the target, + = right)
      ll/rl  thigh angles as if the hips were square and upright
      le/re  elbow bend, lk/rk knee bend (a number, or (bend, hinge turn): turn +90 folds the
             forearm toward the player's left, -90 toward the right)
    arms_on_chest: the arms' middle value is relative to the chest instead (for spins, where the
    body turns through more than a full circle).
    """
    face = 0.0 if arms_on_chest else hips[1] + chest[1]
    pitch = (hips[0] + 180.0) % 360.0 - 180.0   # a pose at the end of a full flip is upright again
    lean = pitch + chest[0]
    body = rig.body_matrix(hips)

    def leg(angles):
        world = rig.FLIP @ rig.euler(*angles) @ rig.FLIP
        local = rig.FLIP @ (body.T @ world) @ rig.FLIP
        return rig.decompose(local, (0.0, 0.0, 0.0))

    def joint(v):
        return (float(v), 0.0, 0.0) if isinstance(v, (int, float)) else (float(v[0]), float(v[1]), 0.0)

    return P(hips=hips, pos=pos, chest=chest, head=(look[0] - pitch, look[1] - hips[1], look[2]),
             la=(la[0] - lean, la[1] + face, la[2]), ra=(ra[0] - lean, ra[1] + face, ra[2]),
             ll=leg(ll), rl=leg(rl), le=joint(le), re=joint(re), lk=joint(lk), rk=joint(rk))


# Arms: (upper arm, elbow). The fist of a bent arm sits forward of and above the elbow.
LEAD_UP = dict(la=(-50, 12, 8), le=116)      # left fist back at the chin while the right hand works
REAR_UP = dict(ra=(-38, -16, -6), re=124)    # right fist at the chin while the left hand works


def guard(breath=0.0, **over):
    """Guard: bladed stance, left foot and left hand leading, knees soft, fists at the chin."""
    args = dict(hips=(5, -26, 0), chest=(8 - 2.0 * breath, 6, 0), look=(6 - breath, 0, 0),
                la=(-54 - 2 * breath, 4, 4), le=102, ra=(-36 - 2 * breath, -16, -4), re=120,
                ll=(-30, 0, -8), lk=30, rl=(8, 0, 10), rk=34)
    args.update(over)
    return S(**args)


G = guard()

PLAYER = []
PREVIEW = {}   # clip name -> keyword arguments for preview.clip_sheet


def add(clip, **preview):
    PLAYER.append(clip)
    if preview:
        PREVIEW[clip.name] = preview
    return clip


# --- locomotion (looping, on the layer below the combat moves) -----------------------------------

add(Clip("stance", [K(0, guard(0)), K(20, guard(1)), K(40, guard(0))], loop=True), ticks=[0, 10, 20, 30])

# Side-steps and back-steps: the guard held, feet shuffling in place.
add(Clip("shuffle", [
    K(0, guard(ll=(-36, 0, -9), lk=40, rl=(4, 0, 9), rk=24, pos=(0.6, 0, 0))),
    K(4, guard(ll=(-24, 0, -7), lk=20, rl=(14, 0, 11), rk=46, pos=(-0.6, 0, 0))),
    K(8, guard(ll=(-36, 0, -9), lk=40, rl=(4, 0, 9), rk=24, pos=(0.6, 0, 0))),
], loop=True))


def stride(k, reach, lean, pump, upper, elbow, heel):
    """Foot strike of a running/advancing cycle: k = +1 right foot forward ... -1 left foot forward."""
    front, back = (-reach, 0, 0), (reach * 0.7, 0, 0)
    return S(hips=(lean, 7 * k, 0), chest=(lean * 0.4, -14 * k, 0), look=(4, 0, 0),
             la=(upper - pump * k, 8, 6), le=elbow, ra=(upper + pump * k, -8, -6), re=elbow,
             rl=front if k > 0 else back, rk=16 if k > 0 else heel,
             ll=back if k > 0 else front, lk=heel if k > 0 else 16)


def passing(k, lean, upper, elbow, lift):
    """Mid-stride: k = +1 the right leg carries the weight while the left swings through."""
    support, swing = (4, 0, 0), (-34, 0, 0)
    return S(hips=(lean, 0, 0), chest=(lean * 0.4, 0, 0), look=(4, 0, 0),
             la=(upper, 8, 6), le=elbow, ra=(upper, -8, -6), re=elbow,
             rl=support if k > 0 else swing, rk=22 if k > 0 else lift,
             ll=swing if k > 0 else support, lk=lift if k > 0 else 22)


# Advancing in guard (walking while in a fight): compact steps, fists stay up.
add(Clip("advance", [
    K(0, stride(1, 26, 8, 8, -44, 108, 40)), K(2, passing(1, 8, -44, 108, 70), "lin"),
    K(4, stride(-1, 26, 8, 8, -44, 108, 40), "lin"), K(6, passing(-1, 8, -44, 108, 70), "lin"),
    K(8, stride(1, 26, 8, 8, -44, 108, 40), "lin"),
], loop=True))
# Sprint: driving forward, long strides, heels kicking up, arms pumping.
add(Clip("run", [
    K(0, stride(1, 44, 20, 46, -14, 92, 78)), K(2, passing(1, 20, -14, 92, 108), "lin"),
    K(4, stride(-1, 44, 20, 46, -14, 92, 78), "lin"), K(6, passing(-1, 20, -14, 92, 108), "lin"),
    K(8, stride(1, 44, 20, 46, -14, 92, 78), "lin"),
], loop=True))

# --- strikes --------------------------------------------------------------------------------------
# Close strikes land on tick 2. The game starts a clip late when the hit is sooner than that, so
# what matters most is the impact pose and the follow-through after it.

# Lead straight: the left shoulder drives through, rear hand glued to the chin.
add(Clip("jab_left", [
    K(0, guard(la=(-60, 2, 6), le=110, hips=(5, -22, 0))),
    K(2, S(hips=(8, -44, 0), chest=(9, -12, 0), look=(5, 0, 0), pos=(0, 0, 4.5),
           la=(-99, 0, 0), le=6, ll=(-40, 0, -8), lk=32, rl=(22, 0, 10), rk=20, **REAR_UP), "snap"),
    K(4, S(hips=(7, -40, 0), chest=(8, -10, 0), look=(5, 0, 0), pos=(0, 0, 4),
           la=(-94, 0, 0), le=14, ll=(-39, 0, -8), lk=32, rl=(21, 0, 10), rk=20, **REAR_UP)),
    K(9, G),
], impact=2))

# Rear cross: hips and shoulders whip round, rear leg drives.
add(Clip("jab_right", [
    K(0, guard(hips=(5, -32, 0), ra=(-30, -24, -6), re=128)),
    K(2, S(hips=(11, 30, 0), chest=(11, 20, 0), look=(6, 0, 0), pos=(0, 0, 5.5),
           ra=(-100, 0, 0), re=6, ll=(-40, 0, -9), lk=36, rl=(30, 0, 9), rk=26, **LEAD_UP), "snap"),
    K(4, S(hips=(10, 26, 0), chest=(10, 18, 0), look=(6, 0, 0), pos=(0, 0, 5),
           ra=(-95, 0, 0), re=14, ll=(-39, 0, -9), lk=36, rl=(28, 0, 9), rk=26, **LEAD_UP)),
    K(10, G),
], impact=2))

# Rear hook: elbow up at shoulder height, forearm across, the whole body swings it through.
add(Clip("hook_right", [
    K(0, guard(hips=(5, -40, 0), chest=(8, -10, 0), ra=(-84, 70, 0), re=(96, 90))),
    K(2, S(hips=(8, 22, 0), chest=(8, 26, 0), look=(5, 0, 0), pos=(0, 0, 3.5),
           ra=(-90, 30, 0), re=(92, 90), ll=(-36, 0, -10), lk=34, rl=(22, 0, 12), rk=30, **LEAD_UP), "snap"),
    K(4, S(hips=(7, 42, 0), chest=(7, 32, 0), look=(5, 0, 0), pos=(0, 0, 3),
           ra=(-88, -4, 0), re=(104, 90), ll=(-35, 0, -10), lk=34, rl=(22, 0, 12), rk=30, **LEAD_UP), "out"),
    K(10, G),
], impact=2))

# Lead hook.
add(Clip("hook_left", [
    K(0, guard(hips=(5, -12, 0), chest=(8, 14, 0), la=(-84, -66, 0), le=(96, -90))),
    K(2, S(hips=(8, -52, 0), chest=(8, -24, 0), look=(5, 0, 0), pos=(0, 0, 3.5),
           la=(-90, -28, 0), le=(92, -90), ll=(-38, 0, -9), lk=34, rl=(18, 0, 11), rk=26, **REAR_UP), "snap"),
    K(4, S(hips=(7, -66, 0), chest=(7, -30, 0), look=(5, 0, 0), pos=(0, 0, 3),
           la=(-88, 6, 0), le=(104, -90), ll=(-37, 0, -9), lk=34, rl=(18, 0, 11), rk=26, **REAR_UP), "out"),
    K(10, G),
], impact=2))

# Step-in elbow: the arm folds tight and the point of the elbow is driven across.
add(Clip("elbow", [
    K(0, guard(hips=(5, -34, 0), ra=(-80, 60, -6), re=(130, 90))),
    K(2, S(hips=(10, 46, 0), chest=(8, 30, 0), look=(6, 0, 0), pos=(0, 0, 6.5),
           ra=(-94, -10, 0), re=(138, 90), la=(-46, 26, 14), le=118,
           ll=(-42, 0, -8), lk=38, rl=(26, 0, 10), rk=28), "snap"),
    K(4, S(hips=(9, 56, 0), chest=(8, 34, 0), look=(6, 0, 0), pos=(0, 0, 6),
           ra=(-92, -32, 0), re=(140, 90), la=(-46, 26, 14), le=118,
           ll=(-41, 0, -8), lk=38, rl=(26, 0, 10), rk=28), "out"),
    K(10, G),
], impact=2))

# Knee: both hands snatch the head down onto a driving right knee.
add(Clip("knee", [
    K(0, guard(la=(-92, 6, 8), le=40, ra=(-90, -10, -8), re=40)),
    K(2, S(hips=(-6, 6, 0), chest=(20, 0, 0), look=(14, 0, 0), pos=(0, 0, 3),
           la=(-44, 10, 4), le=84, ra=(-44, -10, -4), re=84,
           ll=(-4, 0, -4), lk=10, rl=(-100, 0, 2), rk=124), "snap"),
    K(4, S(hips=(-5, 6, 0), chest=(18, 0, 0), look=(14, 0, 0), pos=(0, 0, 3),
           la=(-42, 10, 4), le=88, ra=(-42, -10, -4), re=88,
           ll=(-4, 0, -4), lk=12, rl=(-94, 0, 2), rk=120)),
    K(10, G),
], impact=2))

# Front kick: knee up, then the hips thrust the foot through; shoulders counter back.
add(Clip("front_kick", [
    K(0, guard(hips=(2, -10, 0), rl=(-66, 0, 4), rk=104, ll=(-6, 0, -6), lk=14)),
    K(2, S(hips=(-16, 4, 0), chest=(10, -6, 0), look=(4, 0, 0), pos=(0, 0, 3),
           la=(-46, 10, 14), le=110, ra=(-20, -6, -22), re=60,
           ll=(4, 0, -5), lk=10, rl=(-93, 0, 0), rk=4), "snap"),
    K(4, S(hips=(-14, 4, 0), chest=(10, -6, 0), look=(4, 0, 0), pos=(0, 0, 3),
           la=(-46, 10, 14), le=110, ra=(-20, -6, -22), re=60,
           ll=(4, 0, -5), lk=10, rl=(-88, 0, 0), rk=14)),
    K(7, S(hips=(-4, -8, 0), chest=(8, 0, 0), look=(5, 0, 0), pos=(0, 0, 1),
           la=(-52, 6, 8), le=106, ra=(-34, -14, -8), re=110,
           ll=(-8, 0, -6), lk=16, rl=(-60, 0, 3), rk=96)),
    K(11, G),
], impact=2))

# Side kick: turn side-on, lean away, drive the heel straight out.
add(Clip("side_kick", [
    K(0, guard(hips=(2, 30, 0), rl=(-70, 0, 6), rk=110, ll=(-4, 0, -6), lk=12)),
    K(2, S(hips=(0, 80, -24), chest=(0, 4, 8), look=(0, 0, 0), pos=(0, 0, 2),
           la=(-50, -64, 0), le=112, ra=(-58, 0, 0), re=10,
           ll=(3, 0, -3), lk=8, rl=(-94, 0, 0), rk=4), "snap"),
    K(4, S(hips=(0, 78, -22), chest=(0, 4, 8), look=(0, 0, 0), pos=(0, 0, 2),
           la=(-50, -64, 0), le=112, ra=(-58, 0, 0), re=14,
           ll=(3, 0, -3), lk=8, rl=(-89, 0, 0), rk=12)),
    K(7, S(hips=(2, 50, -6), chest=(4, 4, 0), look=(4, 0, 0), pos=(0, 0, 1),
           la=(-52, -20, 4), le=108, ra=(-36, -6, -4), re=112,
           ll=(-6, 0, -6), lk=14, rl=(-64, 0, 6), rk=100)),
    K(12, G),
], impact=2))

# Spinning backfist: the fist whips out backhanded and the turn carries on right round.
add(Clip("spin_backfist", [
    K(0, S(hips=(5, 24, 0), chest=(8, 20, 0), look=(6, 0, 0), ra=(-84, -30, 0), re=(100, 90),
           la=(-48, 30, 10), le=116, ll=(-30, 0, -8), lk=30, rl=(8, 0, 10), rk=34, arms_on_chest=True)),
    K(2, S(hips=(6, -54, 0), chest=(6, -24, 0), look=(4, 0, 0), pos=(0, 0, 3),
           ra=(-98, 66, 0), re=(8, 90), la=(-44, 24, 10), le=120,
           ll=(-30, 0, -9), lk=30, rl=(14, 0, 10), rk=30, arms_on_chest=True), "out"),
    K(4, S(hips=(6, -150, 0), chest=(6, -20, 0), look=(4, -210, 0), pos=(0, 0, 3),
           ra=(-90, 96, 0), re=(24, 90), la=(-44, 20, 10), le=120,
           ll=(-24, 0, -8), lk=26, rl=(10, 0, 9), rk=28, arms_on_chest=True), "lin"),
    K(7, S(hips=(6, -292, 0), chest=(6, -10, 0), look=(4, -360, 0), pos=(0, 0, 2),
           ra=(-50, 20, 0), re=(96, 40), la=(-50, 4, 8), le=108,
           ll=(-26, 0, -8), lk=28, rl=(10, 0, 10), rk=32, arms_on_chest=True), "lin"),
    K(11, S(hips=(5, -386, 0), chest=(8, 6, 0), look=(6, -360, 0), la=(-54, -16, 4), le=102,
            ra=(-36, -36, -4), re=120, ll=(-30, 0, -8), lk=30, rl=(8, 0, 10), rk=34, arms_on_chest=True), "out"),
], impact=2))

# Overhand right: loaded from behind the ear and thrown down through the target with everything.
add(Clip("haymaker", [
    K(0, guard(hips=(0, -46, 0), chest=(2, -14, 0), ra=(-120, 40, -18), re=110)),
    K(2, S(hips=(18, 34, 0), chest=(22, 22, 0), look=(10, 0, 0), pos=(0, 0, 7),
           ra=(-92, -4, 0), re=26, la=(-20, 30, 26), le=70,
           ll=(-50, 0, -9), lk=48, rl=(34, 0, 10), rk=30), "snap"),
    K(4, S(hips=(20, 44, 0), chest=(26, 26, 0), look=(12, 0, 0), pos=(0, 0, 7),
           ra=(-62, -22, 0), re=38, la=(-16, 30, 26), le=70,
           ll=(-50, 0, -9), lk=50, rl=(34, 0, 10), rk=30), "out"),
    K(7, S(hips=(16, 30, 0), chest=(20, 16, 0), look=(10, 0, 0), pos=(0, 0, 5),
           ra=(-46, -20, 0), re=70, la=(-36, 20, 16), le=96,
           ll=(-46, 0, -9), lk=46, rl=(28, 0, 10), rk=30)),
    K(13, G),
], impact=2))

# --- lunging strikes: airborne on the way in, landing the hit on tick 7 ---------------------------

# Flying side kick: trailing leg tucked under, kicking leg shot out on the hit.
add(Clip("flying_kick", [
    K(0, guard(hips=(14, -10, 0), ll=(-44, 0, -6), lk=50, rl=(20, 0, 8), rk=40)),
    K(3, S(hips=(0, 60, -14), chest=(4, 0, 4), look=(0, 0, 0), pos=(0, 9, 0),
           la=(-50, -50, 0), le=110, ra=(-56, 0, 0), re=40,
           ll=(-30, 0, -4), lk=118, rl=(-84, 0, 4), rk=116), "out"),
    K(6, S(hips=(0, 82, -32), chest=(2, 2, 8), look=(0, 0, 0), pos=(0, 10, 0),
           la=(-50, -60, 0), le=112, ra=(-58, 0, 0), re=30,
           ll=(-26, 0, -4), lk=124, rl=(-88, 0, 4), rk=110), "io"),
    K(7, S(hips=(0, 84, -40), chest=(0, 2, 10), look=(0, 0, 0), pos=(0, 8, 3),
           la=(-50, -64, 0), le=112, ra=(-60, 0, 0), re=8,
           ll=(-24, 0, -4), lk=126, rl=(-95, 0, 0), rk=2), "snap"),
    K(9, S(hips=(0, 82, -36), chest=(0, 2, 10), look=(0, 0, 0), pos=(0, 5, 3),
           la=(-50, -64, 0), le=112, ra=(-60, 0, 0), re=12,
           ll=(-22, 0, -4), lk=116, rl=(-90, 0, 0), rk=10)),
    K(12, S(hips=(6, 30, 0), chest=(8, 4, 0), look=(5, 0, 0), pos=(0, 0, 1),
            la=(-52, 14, 6), le=106, ra=(-36, -10, -4), re=114,
            ll=(-38, 0, -10), lk=52, rl=(14, 0, 14), rk=46)),
    K(16, G),
], impact=7))

# Superman punch: the rear leg kicks back as the right fist is thrown from the air.
add(Clip("flying_punch", [
    K(0, guard(hips=(14, -14, 0), ll=(-44, 0, -6), lk=50, rl=(20, 0, 8), rk=40)),
    K(3, S(hips=(10, -40, 0), chest=(4, -18, 0), look=(4, 0, 0), pos=(0, 8, 0),
           ra=(-110, 40, -20), re=116, la=(-92, 0, 0), le=20,
           ll=(-74, 0, -4), lk=96, rl=(-40, 0, 6), rk=100), "out"),
    K(6, S(hips=(14, -30, 0), chest=(6, -16, 0), look=(4, 0, 0), pos=(0, 9, 0),
           ra=(-116, 40, -20), re=122, la=(-90, 0, 0), le=24,
           ll=(-60, 0, -4), lk=90, rl=(-52, 0, 6), rk=110), "io"),
    K(7, S(hips=(30, 30, 0), chest=(14, 22, 0), look=(10, 0, 0), pos=(0, 7, 4),
           ra=(-98, 0, 0), re=4, la=(-26, 30, 24), le=96,
           ll=(-34, 0, -6), lk=50, rl=(46, 0, 8), rk=60), "snap"),
    K(9, S(hips=(28, 30, 0), chest=(14, 22, 0), look=(10, 0, 0), pos=(0, 3, 4),
           ra=(-92, -6, 0), re=12, la=(-26, 30, 24), le=96,
           ll=(-42, 0, -6), lk=48, rl=(40, 0, 8), rk=50)),
    K(12, S(hips=(14, 20, 0), chest=(12, 12, 0), look=(8, 0, 0), pos=(0, 0, 3),
            ra=(-50, -10, 0), re=80, la=(-44, 20, 12), le=106,
            ll=(-46, 0, -8), lk=54, rl=(24, 0, 10), rk=38)),
    K(16, G),
], impact=7))

# --- beatdown (mashed on a dazed enemy) and its finisher --------------------------------------------

# Short body shots from a crouch, elbows tight.
BEAT_LEGS = dict(ll=(-42, 0, -10), lk=46, rl=(22, 0, 12), rk=34)
add(Clip("beatdown_right", [
    K(0, S(hips=(12, -10, 0), chest=(14, -6, 0), look=(8, 0, 0), pos=(0, 0, 4),
           ra=(-10, 10, -8), re=100, la=(-46, 12, 8), le=112, **BEAT_LEGS)),
    K(1, S(hips=(14, 28, 0), chest=(16, 22, 0), look=(8, 0, 0), pos=(0, 0, 5),
           ra=(-58, 0, 0), re=58, la=(-46, 14, 10), le=114, **BEAT_LEGS), "snap"),
    K(3, S(hips=(13, 22, 0), chest=(15, 18, 0), look=(8, 0, 0), pos=(0, 0, 5),
           ra=(-54, 0, 0), re=64, la=(-46, 14, 10), le=114, **BEAT_LEGS)),
    K(6, G),
], impact=1))
add(Clip("beatdown_left", [
    K(0, S(hips=(12, 10, 0), chest=(14, 6, 0), look=(8, 0, 0), pos=(0, 0, 4),
           la=(-10, -10, 8), le=100, ra=(-40, -12, -8), re=118, **BEAT_LEGS)),
    K(1, S(hips=(14, -30, 0), chest=(16, -22, 0), look=(8, 0, 0), pos=(0, 0, 5),
           la=(-58, 0, 0), le=58, ra=(-40, -14, -10), re=120, **BEAT_LEGS), "snap"),
    K(3, S(hips=(13, -24, 0), chest=(15, -18, 0), look=(8, 0, 0), pos=(0, 0, 5),
           la=(-54, 0, 0), le=64, ra=(-40, -14, -10), re=120, **BEAT_LEGS)),
    K(6, G),
], impact=1))

# Finisher: rising uppercut that lifts both of them off the floor (starts on the hit).
add(Clip("finisher", [
    K(0, S(hips=(10, 20, 0), chest=(10, 20, 0), look=(-6, 0, 0), pos=(0, 0, 4),
           ra=(-50, -4, 0), re=96, la=(-16, 20, 20), le=70,
           ll=(-48, 0, -10), lk=56, rl=(26, 0, 12), rk=44)),
    K(2, S(hips=(-6, 34, 0), chest=(-10, 26, 0), look=(-20, 0, 0), pos=(0, 5, 5),
           ra=(-150, 0, 0), re=44, la=(20, 20, 26), le=40,
           ll=(-14, 0, -6), lk=10, rl=(14, 0, 8), rk=24), "snap"),
    K(5, S(hips=(-6, 36, 0), chest=(-10, 26, 0), look=(-18, 0, 0), pos=(0, 3, 5),
           ra=(-146, 0, 0), re=50, la=(20, 20, 26), le=44,
           ll=(-16, 0, -6), lk=14, rl=(14, 0, 8), rk=30)),
    K(8, S(hips=(8, 10, 0), chest=(10, 12, 0), look=(4, 0, 0), pos=(0, 0, 4),
           ra=(-44, -10, 0), re=96, la=(-40, 20, 14), le=104,
           ll=(-42, 0, -10), lk=50, rl=(20, 0, 12), rk=40)),
    K(13, G),
], impact=0))

# --- counters -------------------------------------------------------------------------------------
# A counter is a little choreographed scene: on the press the attackers are frozen into it and
# pulled to their marks around the player; the defence comes first and the damage only lands on
# each blow's impact tick. One press answers everyone who is winding up, and the scene depends on
# how many that is. The marks and impact ticks here must match CounterChoreography.java, and the
# enemies' halves are in clips_mob.py.
#
#   one attacker    in front, 1.35 blocks                 (three variants in rotation)
#   two             front-left and front-right (38 deg)   both on tick 7
#   three           front, right (105 deg), left          ticks 6, 10, 14
#   four            front, right, behind, left            ticks 6, 9, 12, 15

BRACED = dict(ll=(-26, 0, -9), lk=28, rl=(12, 0, 11), rk=36)
DRIVEN = dict(ll=(-40, 0, -9), lk=36, rl=(30, 0, 9), rk=26)

# One: the swing is stopped on the left forearm, the right hand is loaded and thrown through the face.
add(Clip("counter", [
    K(0, G),
    K(2, S(hips=(0, -36, 0), chest=(2, -10, 0), look=(4, 0, 0), pos=(0, 0, -1),
           la=(-94, -32, -14), le=104, ra=(-30, -20, -8), re=126, **BRACED), "snap"),
    K(4, S(hips=(2, -46, 0), chest=(4, -16, 0), look=(4, 0, 0), pos=(0, 0, -1),
           la=(-90, -36, -16), le=108, ra=(-8, -6, -12), re=118, **BRACED)),
    K(6, S(hips=(11, 30, 0), chest=(11, 20, 0), look=(6, 0, 0), pos=(0, 0, 5.5),
           ra=(-101, 0, 0), re=6, la=(-60, 30, 12), le=110, **DRIVEN), "snap"),
    K(9, S(hips=(10, 26, 0), chest=(10, 18, 0), look=(6, 0, 0), pos=(0, 0, 5),
           ra=(-96, 0, 0), re=14, la=(-56, 30, 12), le=112, **DRIVEN)),
    K(15, G),
], impact=6))

# One: the arm is swept aside with the left, the right knee comes up and the foot goes through the chest.
add(Clip("counter_kick", [
    K(0, G),
    K(2, S(hips=(2, -34, 0), chest=(4, -14, 0), look=(4, 0, 0),
           la=(-70, 40, 26), le=70, ra=(-34, -14, -8), re=122, **BRACED), "snap"),
    K(4, S(hips=(0, -14, 0), chest=(6, -8, 0), look=(4, 0, 0),
           la=(-60, 66, 34), le=50, ra=(-30, -6, -14), re=110,
           ll=(-6, 0, -6), lk=14, rl=(-68, 0, 4), rk=108)),
    K(6, S(hips=(-18, 6, 0), chest=(10, -10, 0), look=(4, 0, 0), pos=(0, 0, 4),
           la=(-56, 70, 34), le=54, ra=(-20, -4, -26), re=64,
           ll=(4, 0, -5), lk=10, rl=(-95, 0, 0), rk=4), "snap"),
    K(9, S(hips=(-16, 6, 0), chest=(10, -10, 0), look=(4, 0, 0), pos=(0, 0, 4),
           la=(-54, 66, 30), le=60, ra=(-20, -4, -26), re=64,
           ll=(4, 0, -5), lk=10, rl=(-89, 0, 0), rk=14)),
    K(12, S(hips=(-4, -8, 0), chest=(8, 0, 0), look=(5, 0, 0), pos=(0, 0, 1),
            la=(-52, 6, 8), le=106, ra=(-34, -14, -8), re=110,
            ll=(-8, 0, -6), lk=16, rl=(-60, 0, 3), rk=96)),
    K(16, G),
], impact=6))

# One: the arm is caught in both hands, the hips drop under it and the enemy is heaved overhead
# (it leaves the hands on tick 4 and comes down behind on tick 14).
add(Clip("counter_throw", [
    K(0, G),
    K(2, S(hips=(8, -6, 0), chest=(12, 0, 0), look=(2, 0, 0), pos=(0, 0, 2),
           la=(-84, 6, 6), le=40, ra=(-80, -6, -6), re=44,
           ll=(-36, 0, -12), lk=40, rl=(14, 0, 14), rk=44), "snap"),
    K(4, S(hips=(16, 0, 0), chest=(18, 0, 0), look=(0, 0, 0), pos=(0, 0, 3),
           la=(-120, 4, 8), le=70, ra=(-118, -4, -8), re=74,
           ll=(-52, 0, -16), lk=70, rl=(16, 0, 18), rk=64), "out"),
    K(7, S(hips=(-10, 0, 0), chest=(-18, 0, 0), look=(-26, 0, 0), pos=(0, 0, 0),
           la=(-214, 0, 12), le=30, ra=(-214, 0, -12), re=30,
           ll=(-28, 0, -12), lk=30, rl=(6, 0, 14), rk=24), "out"),
    K(10, S(hips=(8, -10, 0), chest=(12, 0, 0), look=(8, 0, 0), pos=(0, 0, 0),
            la=(-40, 10, 16), le=70, ra=(-36, -10, -16), re=74,
            ll=(-32, 0, -10), lk=34, rl=(10, 0, 12), rk=36)),
    K(15, G),
], impact=4))

# Two: a hand on each head, hauled back and smashed together in front.
SQUARE = dict(ll=(-20, 0, -12), lk=40, rl=(-20, 0, 12), rk=40)
add(Clip("counter_double", [
    K(0, G),
    K(3, S(hips=(6, 0, 0), chest=(8, 0, 0), look=(4, 0, 0), pos=(0, 0, 1),
           la=(-98, -36, 0), le=12, ra=(-98, 36, 0), re=12, **SQUARE), "snap"),
    K(5, S(hips=(0, 0, 0), chest=(-2, 0, 0), look=(2, 0, 0), pos=(0, 0, -1),
           la=(-92, -58, 0), le=46, ra=(-92, 58, 0), re=46, **SQUARE)),
    K(7, S(hips=(14, 0, 0), chest=(16, 0, 0), look=(10, 0, 0), pos=(0, 0, 3.5),
           la=(-96, -8, 0), le=30, ra=(-96, 8, 0), re=30,
           ll=(-30, 0, -12), lk=50, rl=(-10, 0, 12), rk=40), "snap"),
    K(10, S(hips=(12, 0, 0), chest=(14, 0, 0), look=(10, 0, 0), pos=(0, 0, 3),
            la=(-90, -24, 0), le=36, ra=(-90, 24, 0), re=36,
            ll=(-30, 0, -12), lk=50, rl=(-10, 0, 12), rk=40)),
    K(17, G),
], impact=7))

# Three: front kick, backfist out to the right, then round to a side kick on the left.
add(Clip("counter_triple", [
    K(0, G),
    K(3, guard(hips=(2, -10, 0), rl=(-66, 0, 4), rk=104, ll=(-6, 0, -6), lk=14)),
    K(6, S(hips=(-16, 4, 0), chest=(10, -6, 0), look=(4, 0, 0), pos=(0, 0, 3),
           la=(-46, 10, 14), le=110, ra=(-20, -6, -22), re=60,
           ll=(4, 0, -5), lk=10, rl=(-93, 0, 0), rk=4), "snap"),
    K(8, S(hips=(6, 14, 0), chest=(8, 10, 0), look=(4, -40, 0), pos=(0, 0, 1),
           ra=(-84, -20, 0), re=(100, 90), la=(-50, 10, 8), le=114,
           ll=(-14, 0, -8), lk=24, rl=(-16, 0, 10), rk=30)),
    K(10, S(hips=(6, -64, 0), chest=(6, -22, 0), look=(4, -90, 0), pos=(1.5, 0, 0),
            ra=(-97, 105, 0), re=(8, 90), la=(-46, 20, 10), le=118,
            ll=(-8, 0, -12), lk=22, rl=(-4, 0, 16), rk=30), "snap"),
    K(12, S(hips=(2, -10, 8), chest=(4, -6, -4), look=(2, 60, 0), pos=(1, 0, 0),
            ra=(-40, 20, -6), re=110, la=(-56, -40, 4), le=100,
            rl=(2, 0, 6), rk=14, ll=(-58, 0, -34), lk=112)),
    K(14, S(hips=(0, 16, 24), chest=(0, 4, -8), look=(0, 100, 0), pos=(-2, 0, 0),
            la=(-58, -105, 0), le=10, ra=(-46, 40, 0), re=114,
            rl=(3, 0, 3), rk=8, ll=(15, 0, -93), lk=4), "snap"),
    K(17, S(hips=(0, 14, 20), chest=(0, 4, -8), look=(0, 100, 0), pos=(-2, 0, 0),
            la=(-58, -105, 0), le=14, ra=(-46, 40, 0), re=114,
            rl=(3, 0, 3), rk=8, ll=(15, 0, -86), lk=14)),
    K(20, S(hips=(4, -6, 4), chest=(6, 4, 0), look=(4, 30, 0), pos=(-1, 0, 0),
            la=(-54, -20, 4), le=104, ra=(-38, -6, -4), re=116,
            rl=(4, 0, 8), rk=24, ll=(-40, 0, -24), lk=80)),
    K(24, G),
], impact=6))

# Four: right hand to the front, side kick out to the right, a back kick behind, backfist to the left.
add(Clip("counter_quad", [
    K(0, G),
    K(3, guard(hips=(5, -34, 0), ra=(-26, -24, -6), re=128)),
    K(6, S(hips=(11, 30, 0), chest=(11, 20, 0), look=(6, 0, 0), pos=(0, 0, 4.5),
           ra=(-100, 0, 0), re=6, la=(-50, 12, 8), le=116, **DRIVEN), "snap"),
    K(8, S(hips=(4, 6, -10), chest=(6, 6, 4), look=(2, -70, 0), pos=(0, 0, 1),
           ra=(-50, 40, 0), re=100, la=(-50, -20, 8), le=112,
           ll=(0, 0, -6), lk=14, rl=(-34, 0, 52), rk=112)),
    K(9, S(hips=(0, 0, -24), chest=(0, 0, 8), look=(0, -90, 0), pos=(-1.5, 0, 0),
           ra=(-58, 90, 0), re=10, la=(-50, -50, 4), le=114,
           ll=(3, 0, -3), lk=8, rl=(0, 0, 93), rk=4), "snap"),
    K(11, S(hips=(22, 0, -4), chest=(10, 0, 0), look=(14, -50, 0), pos=(0, 0, 1.5),
            ra=(-60, 20, 14), re=70, la=(-60, -20, -14), le=70,
            rl=(-18, 0, 8), rk=26, ll=(-44, 0, -4), lk=112)),
    K(12, S(hips=(40, 0, 0), chest=(12, 0, 0), look=(24, -70, 0), pos=(0, 0, 3),
            ra=(-70, 16, 20), re=60, la=(-70, -16, -20), le=60,
            rl=(-22, 0, 8), rk=26, ll=(88, 0, 0), lk=4), "snap"),
    K(14, S(hips=(10, 6, 0), chest=(8, 14, 0), look=(4, 50, 0), pos=(0, 0, 1),
            la=(-84, 20, 0), le=(100, -90), ra=(-40, -10, -8), re=118,
            ll=(-12, 0, -14), lk=30, rl=(6, 0, 12), rk=26)),
    K(15, S(hips=(6, 56, 0), chest=(6, 22, 0), look=(4, 90, 0), pos=(-1.5, 0, 0),
            la=(-97, -90, 0), le=(8, -90), ra=(-42, 20, -8), re=120,
            ll=(-10, 0, -18), lk=24, rl=(6, 0, 14), rk=26), "snap"),
    K(18, S(hips=(6, 50, 0), chest=(6, 20, 0), look=(4, 80, 0), pos=(-1.5, 0, 0),
            la=(-92, -84, 0), le=(14, -90), ra=(-42, 20, -8), re=120,
            ll=(-10, 0, -18), lk=24, rl=(6, 0, 14), rk=26)),
    K(24, G),
], impact=6))

# Caught by an attack that was not countered: rocked back onto the heels, cover, back to guard.
add(Clip("hurt", [
    K(0, G),
    K(1, S(hips=(-10, -34, 0), chest=(-12, -20, 0), look=(-22, -24, -8), pos=(0, 0, -2.5),
           la=(-70, 10, -20), le=70, ra=(-50, 10, 20), re=90,
           ll=(-36, 0, -9), lk=20, rl=(4, 0, 11), rk=40), "snap"),
    K(4, S(hips=(2, -30, 0), chest=(8, -8, 0), look=(10, -8, 0), pos=(0, 0, -3),
           la=(-60, 6, 4), le=118, ra=(-44, -14, -4), re=126,
           ll=(-22, 0, -8), lk=34, rl=(-4, 0, 10), rk=44)),
    K(10, G),
]))

# Stun: a two-handed shove that rocks the enemy onto its heels.
add(Clip("stun", [
    K(0, guard(la=(-40, 10, 12), le=120, ra=(-36, -10, -12), re=124, hips=(2, -12, 0))),
    K(2, S(hips=(16, 0, 0), chest=(14, 0, 0), look=(8, 0, 0), pos=(0, 0, 7),
           la=(-96, 6, 0), le=10, ra=(-96, -6, 0), re=10,
           ll=(-46, 0, -10), lk=42, rl=(32, 0, 12), rk=26), "snap"),
    K(5, S(hips=(14, 0, 0), chest=(12, 0, 0), look=(8, 0, 0), pos=(0, 0, 6.5),
           la=(-90, 6, 0), le=22, ra=(-90, -6, 0), re=22,
           ll=(-45, 0, -10), lk=42, rl=(30, 0, 12), rk=26)),
    K(11, G),
], impact=2))


# --- evades ----------------------------------------------------------------------------------------

def tuck(pitch, air=0.0, z=0.0):
    """Curled into a ball for rolls and flips (limbs relative to the turning body)."""
    return P(hips=(pitch, 0, 0), pos=(0, air, z), chest=(34, 0, 0), head=(46, 0, 0),
             la=(-56, 16, 4), le=110, ra=(-56, -16, -4), re=110,
             ll=(-116, 0, -5), lk=128, rl=(-116, 0, 5), rk=128)


# Dive roll in the held direction: dive onto the hands, over the shoulders, up into guard.
add(Clip("roll", [
    K(0, guard(hips=(24, 0, 0), chest=(18, 0, 0), la=(-70, 6, 6), le=50, ra=(-66, -6, -6), re=50,
               ll=(-50, 0, -6), lk=60, rl=(18, 0, 8), rk=50)),
    K(2, P(hips=(72, 0, 0), chest=(28, 0, 0), head=(34, 0, 0), la=(-128, 10, 4), le=30, ra=(-128, -10, -4), re=30,
           ll=(-70, 0, -6), lk=50, rl=(-30, 0, 6), rk=40), "out"),
    K(4, tuck(148), "lin"),
    K(8, tuck(318), "lin"),
    K(10, P(hips=(374, 0, 0), chest=(22, 0, 0), head=(-6, 0, 0), la=(-46, 10, 8), le=80, ra=(-42, -10, -8), re=84,
            ll=(-86, 0, -10), lk=110, rl=(-50, 0, 12), rk=104), "out"),
    K(13, guard(hips=(365, -26, 0))),
]))

# Back handspring when evading with no direction held.
add(Clip("evade", [
    K(0, guard(hips=(-8, -6, 0), chest=(-4, 0, 0), la=(-150, 4, 6), le=20, ra=(-150, -4, -6), re=20,
               ll=(-34, 0, -6), lk=50, rl=(-18, 0, 8), rk=46)),
    K(2, P(hips=(-70, 0, 0), pos=(0, 6, 0), chest=(-22, 0, 0), head=(-30, 0, 0), la=(-196, 0, 8), ra=(-196, 0, -8),
           ll=(-10, 0, -5), lk=10, rl=(8, 0, 5), rk=16), "out"),
    K(4, P(hips=(-170, 0, 0), pos=(0, 0, 0), chest=(-16, 0, 0), head=(-34, 0, 0), la=(-186, 0, 10), le=14,
           ra=(-186, 0, -10), re=14, ll=(-28, 0, -6), lk=20, rl=(22, 0, 6), rk=40), "lin"),
    K(6, P(hips=(-262, 0, 0), pos=(0, 5, 0), chest=(20, 0, 0), head=(10, 0, 0), la=(-150, 0, 12), le=20,
           ra=(-150, 0, -12), re=20, ll=(-84, 0, -5), lk=80, rl=(-64, 0, 5), rk=90), "lin"),
    K(8, P(hips=(-346, 0, 0), pos=(0, 0, 0), chest=(22, 0, 0), head=(-10, 0, 0), la=(-46, 10, 10), le=80,
           ra=(-42, -10, -10), re=84, ll=(-60, 0, -9), lk=76, rl=(-10, 0, 11), rk=70), "out"),
    K(11, guard(hips=(-355, -26, 0))),
]))

# Vault: a handspring over the enemy's shoulders, landing behind it.
add(Clip("vault", [
    K(0, guard(hips=(24, 0, 0), chest=(14, 0, 0), la=(-150, 4, 6), le=20, ra=(-150, -4, -6), re=20,
               ll=(-50, 0, -6), lk=60, rl=(18, 0, 8), rk=50)),
    K(2, P(hips=(100, 0, 0), pos=(0, 12, 0), chest=(10, 0, 0), head=(-30, 0, 0), la=(-172, 0, 8), le=16,
           ra=(-172, 0, -8), re=16, ll=(-10, 0, -8), lk=20, rl=(24, 0, 8), rk=50), "out"),
    K(4, P(hips=(190, 0, 0), pos=(0, 13, 0), chest=(6, 0, 0), head=(-20, 0, 0), la=(-178, 0, 12), le=30,
           ra=(-178, 0, -12), re=30, ll=(-56, 0, -10), lk=70, rl=(20, 0, 10), rk=40), "lin"),
    K(6, P(hips=(290, 0, 0), pos=(0, 7, 0), chest=(24, 0, 0), head=(20, 0, 0), la=(-100, 0, 16), le=40,
           ra=(-100, 0, -16), re=40, ll=(-96, 0, -6), lk=100, rl=(-80, 0, 6), rk=104), "lin"),
    K(8, P(hips=(352, 0, 0), pos=(0, 0, 0), chest=(22, 0, 0), head=(-8, 0, 0), la=(-46, 10, 10), le=80,
           ra=(-42, -10, -10), re=84, ll=(-64, 0, -10), lk=80, rl=(-8, 0, 12), rk=70), "out"),
    K(11, guard(hips=(365, -26, 0))),
]))

# Slide: feet first, low and long, lead leg straight, the other knee up, one hand on the floor.
SLIDE = P(hips=(-64, 0, 0), chest=(14, 0, 0), head=(-36, 0, 0), la=(96, 0, -14), le=14, ra=(-30, -10, -10), re=96,
          ll=(-62, 0, -16), lk=104, rl=(-27, 0, 3), rk=4)
add(Clip("slide", [
    K(0, guard(hips=(-12, 0, 0), la=(-10, 0, -10), le=30, ll=(-46, 0, -6), lk=40, rl=(-18, 0, 6), rk=20)),
    K(3, SLIDE, "out"),
    K(8, P(SLIDE, hips=(-60, 0, 0), ra=(-34, -10, -10))),
    K(10, P(hips=(-16, 0, 0), chest=(16, 0, 0), head=(-4, 0, 0), la=(-20, 0, -10), le=60, ra=(-36, -10, -8), re=100,
            ll=(-84, 0, -10), lk=110, rl=(-50, 0, 8), rk=96)),
    K(13, G),
]))

# --- takedowns --------------------------------------------------------------------------------------

# Ground takedown: drop to a knee beside the downed enemy, pin it, load the fist high, drive it down.
KNEEL = dict(ll=(-86, 0, -12), lk=92, rl=(2, 0, 10), rk=98)   # left foot planted, right knee on the floor
add(Clip("ground_takedown", [
    K(0, G),
    K(5, S(hips=(14, -10, 0), chest=(12, -4, 0), look=(34, 0, 0), pos=(0, 0, 2),
           la=(-36, 6, 0), le=30, ra=(-90, 10, -10), re=100, **KNEEL), "out"),
    K(11, S(hips=(12, -18, 0), chest=(8, -10, 0), look=(34, 0, 0), pos=(0, 0, 2),
            la=(-34, 6, 0), le=26, ra=(-150, 20, -16), re=96, **KNEEL)),
    K(17, S(hips=(8, -24, 0), chest=(4, -14, 0), look=(32, 0, 0), pos=(0, 0, 2),
            la=(-34, 6, 0), le=26, ra=(-164, 24, -18), re=104, **KNEEL)),
    K(20, S(hips=(28, 18, 0), chest=(26, 16, 0), look=(40, 0, 0), pos=(0, 0, 3),
            la=(-10, 20, 20), le=50, ra=(-34, 0, 0), re=10, **KNEEL), "snap"),
    K(24, S(hips=(26, 16, 0), chest=(24, 14, 0), look=(38, 0, 0), pos=(0, 0, 3),
            la=(-12, 20, 20), le=50, ra=(-38, 0, 0), re=18, **KNEEL)),
    K(31, G),
], impact=20))

# Special takedown: leap, knees tucked, and bring a hammer blow down from above into a kneeling landing.
add(Clip("special_takedown", [
    K(0, guard(hips=(18, -10, 0), chest=(14, 0, 0), ll=(-54, 0, -8), lk=70, rl=(14, 0, 10), rk=60,
               ra=(-10, -10, -10), re=70)),
    K(3, S(hips=(-6, -30, 0), chest=(-8, -14, 0), look=(10, 0, 0), pos=(0, 13, 0),
           ra=(-160, 30, -14), re=100, la=(-100, 0, 4), le=20,
           ll=(-80, 0, -6), lk=110, rl=(-20, 0, 8), rk=100), "out"),
    K(6, S(hips=(-4, -34, 0), chest=(-10, -16, 0), look=(14, 0, 0), pos=(0, 14, 2),
           ra=(-170, 32, -16), re=108, la=(-96, 0, 4), le=24,
           ll=(-70, 0, -6), lk=104, rl=(-10, 0, 8), rk=110)),
    K(7, S(hips=(24, 30, 0), chest=(24, 20, 0), look=(24, 0, 0), pos=(0, 6, 5),
           ra=(-74, 0, 0), re=14, la=(-20, 30, 26), le=60,
           ll=(-70, 0, -8), lk=70, rl=(20, 0, 10), rk=70), "snap"),
    K(9, S(hips=(26, 34, 0), chest=(26, 22, 0), look=(26, 0, 0), pos=(0, 0, 5),
           ra=(-50, -8, 0), re=22, la=(-16, 30, 26), le=60,
           ll=(-86, 0, -12), lk=92, rl=(2, 0, 10), rk=98), "out"),
    K(13, S(hips=(22, 30, 0), chest=(22, 20, 0), look=(22, 0, 0), pos=(0, 0, 5),
            ra=(-52, -8, 0), re=28, la=(-20, 30, 24), le=64,
            ll=(-86, 0, -12), lk=92, rl=(2, 0, 10), rk=98)),
    K(19, G),
], impact=7))

# --- gadgets ----------------------------------------------------------------------------------------
# Ready for the gadget system: a thrown blade and a grapple claw. `impact` is the tick the blade
# leaves the hand / the claw fires / the line is yanked / the arm connects, so gadget code only has
# to say how many ticks away that moment is (Anim.play(player, name, lead)). The enemy's side of
# each is in clips_mob.py. The gadget itself is not drawn by these clips: it goes in the right hand.

# Quick throw in the middle of a fight: drawn across the body and flicked out backhanded, the
# right shoulder leading.
add(Clip("throw_quick", [
    K(0, guard(hips=(5, 34, 0), chest=(8, 26, 0), ra=(-34, -74, 0), re=(112, 90), la=(-50, 40, 8), le=112)),
    K(2, S(hips=(6, 60, 0), chest=(6, 12, 0), look=(4, 0, 0), pos=(0, 0, 3),
           ra=(-95, 0, 0), re=(6, 90), la=(-44, 70, 10), le=118,
           ll=(-24, 0, -9), lk=24, rl=(-14, 0, 10), rk=20), "snap"),
    K(5, S(hips=(6, 52, 0), chest=(6, 6, 0), look=(4, 0, 0), pos=(0, 0, 3),
           ra=(-84, 44, 0), re=(14, 90), la=(-44, 66, 10), le=118,
           ll=(-24, 0, -9), lk=24, rl=(-12, 0, 10), rk=20), "out"),
    K(11, G),
], impact=2))

# Aimed throw: side-on, lead hand sighting the target, blade cocked by the ear. Loops while aiming.
AIM = dict(hips=(0, -62, 0), chest=(2, -16, 0), look=(2, 0, 0),
           la=(-92, 0, 0), le=10, ra=(-112, 96, 0), re=118,
           ll=(-28, 0, -8), lk=24, rl=(16, 0, 10), rk=30)
add(Clip("throw_aim", [
    K(0, S(**AIM)),
    K(12, S(**{**AIM, "chest": (0, -19, 0), "ra": (-115, 98, 0)})),
    K(24, S(**AIM)),
], loop=True), ticks=[0, 12])
add(Clip("throw_release", [
    K(0, S(**AIM)),
    K(2, S(hips=(12, 24, 0), chest=(14, 22, 0), look=(6, 0, 0), pos=(0, 0, 5),
           ra=(-84, -6, 0), re=8, la=(-30, 40, 20), le=100,
           ll=(-44, 0, -9), lk=40, rl=(28, 0, 10), rk=28), "snap"),
    K(5, S(hips=(16, 38, 0), chest=(18, 26, 0), look=(8, 0, 0), pos=(0, 0, 5),
           ra=(-44, -30, 0), re=16, la=(-26, 40, 20), le=100,
           ll=(-44, 0, -9), lk=42, rl=(28, 0, 10), rk=28), "out"),
    K(12, G),
], impact=2))

# Grapple claw: snapped up on a straight arm, braced with the off hand; the shot kicks the arm back.
CLAW = dict(hips=(4, -34, 0), chest=(4, -10, 0), look=(4, 0, 0), pos=(0, 0, 1),
            ra=(-94, 0, 0), re=6, la=(-78, 30, 0), le=(64, -90),
            ll=(-34, 0, -9), lk=30, rl=(14, 0, 10), rk=30)
add(Clip("claw_fire", [
    K(0, guard(ra=(-60, -10, -4), re=80)),
    K(2, S(**CLAW), "snap"),
    K(3, S(**{**CLAW, "hips": (0, -34, 0), "pos": (0, 0, -0.5), "ra": (-106, 0, 0), "re": 22}), "snap"),
    K(6, S(**CLAW)),
    K(14, S(**CLAW)),
    K(20, G),
], impact=2))

# Holding the line taut on a hooked enemy (loops until the pull, or while the claw is in flight).
add(Clip("claw_hold", [
    K(0, S(**CLAW)),
    K(10, S(**{**CLAW, "hips": (2, -36, 0), "pos": (0, 0, 0.4), "ra": (-92, 0, 0), "re": 12})),
    K(20, S(**CLAW)),
], loop=True), ticks=[0, 10])

# Yank the hooked enemy in: both hands rip the line back to the hip, hips turn, weight sits back.
add(Clip("claw_pull", [
    K(0, S(**CLAW)),
    K(2, S(hips=(-10, -68, 0), chest=(-6, -22, 0), look=(4, 0, 0), pos=(0, 0, -3),
           ra=(-10, 30, 0), re=104, la=(-40, 60, 0), le=(96, -90),
           ll=(-46, 0, -9), lk=30, rl=(-6, 0, 12), rk=56), "snap"),
    K(6, S(hips=(-8, -62, 0), chest=(-4, -20, 0), look=(4, 0, 0), pos=(0, 0, -2.5),
           ra=(-14, 30, 0), re=100, la=(-42, 56, 0), le=(92, -90),
           ll=(-44, 0, -9), lk=30, rl=(-4, 0, 12), rk=52)),
    K(13, G),
], impact=2))

# Clothesline for an enemy arriving on the end of the line: a stiff arm through the neck.
add(Clip("claw_slam", [
    K(0, guard(hips=(5, -44, 0), chest=(8, -14, 0), ra=(-86, 80, 0), re=30)),
    K(2, S(hips=(12, 30, 0), chest=(10, 24, 0), look=(4, 0, 0), pos=(0, 0, 6),
           ra=(-94, 14, 0), re=10, la=(-44, 30, 16), le=116,
           ll=(-44, 0, -9), lk=40, rl=(30, 0, 10), rk=28), "snap"),
    K(5, S(hips=(12, 52, 0), chest=(10, 30, 0), look=(4, -20, 0), pos=(0, 0, 6),
           ra=(-88, -30, 0), re=16, la=(-44, 30, 16), le=116,
           ll=(-44, 0, -9), lk=40, rl=(30, 0, 10), rk=28), "out"),
    K(12, G),
], impact=2))
