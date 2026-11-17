"""
Enemy animations (wind-ups, swings, hit reactions, falls, get-ups, gadget reactions), written
against the same skeleton as the player's clips. MobAnimator.java plays them on any humanoid mob
and puts the lowest point of the posed model on the floor itself, so only the "air" value matters
for height.

The enemy faces the player; "forward" in these poses is toward the player. Reactions snap on the
hit and settle slowly, like the player's strikes. Knees buckle and arms fold: nothing here is a
rigid plank.
"""
from clips_player import S
from rig import Clip, K, P

MOB = []
PREVIEW = {}


def add(clip, **preview):
    MOB.append(clip)
    if preview:
        PREVIEW[clip.name] = preview
    return clip


# Ready stance of a brawler: hunched a little, knees loose, hands low and half closed.
N = S(hips=(4, 8, 0), chest=(7, 0, 0), look=(2, 0, 0), la=(-22, 6, -6), le=46, ra=(-26, -6, 6), re=52,
      ll=(-16, 0, -5), lk=18, rl=(4, 0, 6), rk=20)

# Flat on its back, 6 px behind where it stood (so the feet stay where they were), one knee up.
LIE = P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 0, 0), head=(0, 14, 0),
        la=(0, 0, -52), le=24, ra=(-14, 0, 60), re=30, ll=(-34, 0, -12), lk=66, rl=(-4, 0, 16), rk=6)
# Out cold: one arm thrown over the head, head rolled aside, limbs slack.
KO = P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 8, 0), head=(4, 42, 0),
       la=(-172, 0, -24), le=40, ra=(0, 0, 74), re=20, ll=(0, 0, -24), lk=8, rl=(-22, 0, 9), rk=44)

# Dazed sway (also the tail of the counter reactions): knees sagging, arms hanging.
DAZED_A = S(hips=(10, 6, 5), chest=(16, -4, 4), look=(26, -10, 12), pos=(2, 0, 0),
            la=(-8, 0, -6), le=22, ra=(-4, 0, 8), re=16, ll=(-22, 0, -9), lk=34, rl=(-8, 0, 7), rk=30)
DAZED_B = S(hips=(8, -6, -5), chest=(13, 4, -4), look=(22, 12, -12), pos=(-2, 0, 0),
            la=(-2, 0, -8), le=14, ra=(-10, 0, 6), re=24, ll=(-12, 0, -7), lk=28, rl=(-18, 0, 9), rk=36)

# --- attacking ------------------------------------------------------------------------------------

WOUND = S(hips=(-12, -26, 0), chest=(-12, -16, 0), look=(0, 0, 0), pos=(0, 0, -2),
          ra=(-140, 40, 12), re=104, la=(-70, 0, -6), le=34,
          ll=(-30, 0, -6), lk=20, rl=(10, 0, 8), rk=34)
add(Clip("windup", [
    K(0, N),
    K(4, S(hips=(-9, -22, 0), chest=(-9, -14, 0), look=(0, 0, 0), pos=(0, 0, -1.5),
           ra=(-124, 36, 10), re=96, la=(-66, 0, -6), le=38,
           ll=(-28, 0, -6), lk=20, rl=(8, 0, 8), rk=30), "out"),
    K(12, WOUND, "lin"),
]))
add(Clip("attack", [
    K(0, WOUND),
    K(2, S(hips=(17, 28, 0), chest=(18, 18, 0), look=(8, 0, 0), pos=(0, 0, 5),
           ra=(-74, -10, 0), re=22, la=(-20, 20, -20), le=50,
           ll=(-44, 0, -6), lk=40, rl=(28, 0, 8), rk=24), "snap"),
    K(5, S(hips=(18, 36, 0), chest=(20, 22, 0), look=(10, 0, 0), pos=(0, 0, 5),
           ra=(-40, -30, 0), re=30, la=(-18, 20, -20), le=50,
           ll=(-44, 0, -6), lk=42, rl=(28, 0, 8), rk=24), "out"),
    K(8, S(hips=(12, 20, 0), chest=(14, 10, 0), look=(8, 0, 0), pos=(0, 0, 3),
           ra=(-30, -20, 4), re=44, la=(-22, 12, -12), le=48,
           ll=(-32, 0, -6), lk=32, rl=(16, 0, 8), rk=24)),
]))

# Armed enemies heave the weapon overhead with both hands and split downwards on straight arms
# (a held weapon follows a bent forearm too: ItemInHandLayerMixin).
WOUND_ARMED = S(hips=(-14, 0, 0), chest=(-14, 0, 0), look=(-4, 0, 0), pos=(0, 0, -2),
                ra=(-188, 6, 6), la=(-186, -6, -6), ll=(-28, 0, -7), lk=18, rl=(12, 0, 8), rk=30)
add(Clip("windup_armed", [
    K(0, N),
    K(5, S(hips=(-10, 0, 0), chest=(-10, 0, 0), look=(-2, 0, 0), pos=(0, 0, -1.5),
           ra=(-172, 6, 6), la=(-170, -6, -6), ll=(-26, 0, -7), lk=18, rl=(10, 0, 8), rk=28), "out"),
    K(12, WOUND_ARMED, "lin"),
]))
add(Clip("attack_armed", [
    K(0, WOUND_ARMED),
    K(2, S(hips=(20, 0, 0), chest=(24, 0, 0), look=(12, 0, 0), pos=(0, 0, 5),
           ra=(-58, 6, 0), la=(-56, -6, 0), ll=(-46, 0, -8), lk=44, rl=(28, 0, 9), rk=24), "snap"),
    K(5, S(hips=(24, 0, 0), chest=(28, 0, 0), look=(14, 0, 0), pos=(0, 0, 5),
           ra=(-36, 6, 0), la=(-34, -6, 0), ll=(-48, 0, -8), lk=48, rl=(28, 0, 9), rk=24), "out"),
    K(8, S(hips=(14, 0, 0), chest=(16, 0, 0), look=(8, 0, 0), pos=(0, 0, 3),
           ra=(-40, 4, 4), la=(-38, -4, -4), ll=(-34, 0, -7), lk=34, rl=(16, 0, 8), rk=24)),
]))

# --- getting hit ------------------------------------------------------------------------------------

# Light hit: the head is snapped round and the shoulders follow.
HIT_L = add(Clip("hit_light_l", [
    K(0, N),
    K(1, S(hips=(-6, 14, 0), chest=(-9, 22, 0), look=(-12, 48, 12), pos=(0, 0, -1),
           la=(-40, 0, -22), le=30, ra=(-18, 0, 26), re=60, ll=(-20, 0, -6), lk=24, rl=(2, 0, 7), rk=22), "snap"),
    K(3, S(hips=(-4, 10, 0), chest=(-5, 14, 0), look=(-4, 30, 8), pos=(0, 0, -1),
           la=(-34, 0, -16), le=36, ra=(-22, 0, 18), re=56, ll=(-19, 0, -6), lk=22, rl=(3, 0, 7), rk=22)),
    K(7, N),
]))
add(HIT_L.mirrored("hit_light_r"))

# Medium hit: bent backwards off balance, a stumbling step to stay up.
add(Clip("hit_medium", [
    K(0, N),
    K(1, S(hips=(-13, 0, 0), chest=(-17, 4, 0), look=(-28, 0, 0), pos=(0, 0, -2),
           la=(-72, 0, -40), le=30, ra=(-66, 0, 44), re=36, ll=(-34, 0, -6), lk=16, rl=(6, 0, 8), rk=30), "snap"),
    K(4, S(hips=(-6, -6, 0), chest=(6, -4, 0), look=(8, 0, 0), pos=(0, 0, -3.5),
           la=(-36, 0, -28), le=40, ra=(-32, 0, 30), re=44, ll=(2, 0, -8), lk=30, rl=(-30, 0, 8), rk=24)),
    K(7, S(hips=(7, 4, 0), chest=(13, 0, 0), look=(10, 0, 0), pos=(0, 0, -2),
           la=(-24, 0, -12), le=44, ra=(-28, 0, 12), re=48, ll=(-22, 0, -6), lk=26, rl=(4, 0, 7), rk=24)),
    K(10, N),
]))


def ball(pitch, air, z):
    return P(hips=(pitch, 0, 0), pos=(0, air, z), chest=(30, 0, 0), head=(40, 0, 0),
             la=(-60, 10, -6), le=96, ra=(-64, -10, 6), re=100, ll=(-108, 0, -6), lk=124, rl=(-102, 0, 6), rk=120)


# Heavy hit: folded round the blow, thrown back, over the shoulders and back onto the feet.
add(Clip("launched", [
    K(0, N),
    K(2, P(hips=(-42, 0, 0), pos=(0, 5, -2), chest=(26, 0, 0), head=(30, 0, 0),
           la=(-82, 0, -16), le=30, ra=(-86, 0, 16), re=34, ll=(-62, 0, -6), lk=40, rl=(-54, 0, 6), rk=30), "snap"),
    K(5, ball(-124, 7, -4), "lin"),
    K(9, ball(-246, 3, -5), "lin"),
    K(12, P(hips=(-336, 0, 0), pos=(0, 0, -4), chest=(24, 0, 0), head=(-10, 0, 0),
            la=(-30, 0, -26), le=50, ra=(-34, 0, 26), re=54, ll=(-72, 0, -12), lk=84, rl=(-14, 0, 12), rk=70), "out"),
    K(16, P(N, hips=(-356, 8, 0))),
]))

add(Clip("stunned", [K(0, DAZED_A), K(16, DAZED_B), K(32, DAZED_A)], loop=True), ticks=[0, 8, 16, 24])

# --- going down ---------------------------------------------------------------------------------------

add(Clip("fall_back", [
    K(0, N),
    K(2, P(hips=(-30, 0, 0), pos=(0, 0, -2), chest=(-12, 0, 0), head=(18, 0, 0),
           la=(-74, 0, -30), le=30, ra=(-84, 0, 32), re=36, ll=(-40, 0, -6), lk=30, rl=(-26, 0, 6), rk=40), "out"),
    K(5, P(hips=(-80, 0, 0), pos=(0, 3, -5), chest=(-6, 0, 0), head=(18, 0, 0),
           la=(-112, 0, -40), le=20, ra=(-124, 0, 42), re=24, ll=(-56, 0, -10), lk=50, rl=(-40, 0, 10), rk=44), "lin"),
    K(7, P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 0, 0), head=(-8, 0, 0),
           la=(-40, 0, -50), le=20, ra=(-52, 0, 56), re=26, ll=(-30, 0, -12), lk=40, rl=(-18, 0, 14), rk=24), "in"),
    K(8, P(hips=(-87, 0, 0), pos=(0, 1.5, -6), chest=(6, 0, 0), head=(6, 6, 0),
           la=(-30, 0, -52), le=26, ra=(-40, 0, 58), re=32, ll=(-44, 0, -12), lk=60, rl=(-26, 0, 15), rk=30), "out"),
    K(11, LIE, "in"),
]))

# Sits up, draws the feet under, pushes off a hand and stands.
add(Clip("get_up", [
    K(0, LIE),
    K(4, P(hips=(-34, 0, 0), pos=(0, 0, -5), chest=(36, 0, 0), head=(10, 0, 0),
           la=(42, 0, -16), le=16, ra=(38, 0, 16), re=20, ll=(-84, 0, -8), lk=96, rl=(-70, 0, 8), rk=60)),
    K(8, P(hips=(26, 0, 0), pos=(0, 0, -3), chest=(28, 0, 0), head=(-30, 0, 0),
           la=(-40, 0, -10), le=30, ra=(-44, 0, 10), re=34, ll=(-112, 0, -10), lk=120, rl=(-84, 0, 10), rk=116)),
    K(12, P(hips=(16, 6, 0), pos=(0, 0, -1), chest=(16, 0, 0), head=(-8, 0, 0),
            la=(-28, 0, -8), le=44, ra=(-32, 0, 8), re=48, ll=(-66, 0, -7), lk=74, rl=(-22, 0, 8), rk=62)),
    K(16, N),
]))

# Knocked out on its feet: spun by the blow, the knees go, drops flat and stays there.
add(Clip("ko_fall", [
    K(0, N),
    K(1, S(hips=(-10, 20, 0), chest=(-14, 30, 0), look=(-30, 62, 14), pos=(0, 0, -2),
           la=(-90, 0, -30), le=20, ra=(-70, 0, 50), re=30, ll=(-26, 0, -6), lk=30, rl=(0, 0, 8), rk=36), "snap"),
    K(4, P(hips=(-52, 14, 0), pos=(0, 4, -5), chest=(-10, 18, 0), head=(20, 26, 0),
           la=(-124, 0, -40), le=30, ra=(-60, 0, 60), re=20, ll=(-56, 0, -10), lk=60, rl=(-24, 0, 14), rk=50), "lin"),
    K(7, P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 8, 0), head=(-8, 30, 0),
           la=(-150, 0, -30), le=30, ra=(-20, 0, 66), re=20, ll=(-16, 0, -20), lk=24, rl=(-30, 0, 10), rk=50), "in"),
    K(8, P(hips=(-86, 0, 0), pos=(0, 2, -6), chest=(8, 8, 0), head=(8, 34, 0),
           la=(-150, 0, -28), le=36, ra=(-30, 0, 68), re=26, ll=(-34, 0, -22), lk=40, rl=(-44, 0, 10), rk=64), "out"),
    K(12, KO, "in"),
]))

# Knocked out while already down (ground takedown): the body jolts under the blow and goes limp.
add(Clip("ko_ground", [
    K(0, LIE),
    K(1, P(LIE, chest=(24, 0, 0), head=(30, 0, 0), la=(-34, 0, -50), le=50, ra=(-44, 0, 56), re=54,
           ll=(-60, 0, -12), lk=80, rl=(-40, 0, 16), rk=50), "snap"),
    K(4, P(LIE, chest=(-4, 4, 0), head=(-8, 24, 0), la=(-90, 0, -40), le=30, ra=(-10, 0, 66), re=20,
           ll=(-16, 0, -18), lk=24, rl=(-18, 0, 12), rk=30), "in"),
    K(8, KO),
]))

# --- countered --------------------------------------------------------------------------------------
# The enemies' halves of the counters in clips_player.py (same ticks, see the table there). Every
# clip starts from the wind-up the enemy was in when the counter was pressed; nothing here hurts
# until its impact tick.

MOB_BRACED = dict(ll=(-30, 0, -6), lk=20, rl=(10, 0, 8), rk=34)

# One, strike: the swing is stopped dead on tick 2, the punch lands on 6, reels away dazed.
add(Clip("countered_strike", [
    K(0, WOUND),
    K(2, S(hips=(-4, -12, 0), chest=(-6, -10, 0), look=(-4, 0, 0), pos=(0, 0, 1.5),
           ra=(-126, 12, 10), re=58, la=(-50, 0, -10), le=40, **MOB_BRACED), "snap"),
    K(5, S(hips=(-6, -10, 0), chest=(-8, -8, 0), look=(-6, 0, 0), pos=(0, 0, 1.5),
           ra=(-122, 14, 12), re=64, la=(-48, 0, -12), le=44, **MOB_BRACED)),
    K(6, S(hips=(-13, 16, 0), chest=(-19, 26, 0), look=(-32, 44, 12), pos=(0, 0, -2),
           ra=(-116, 24, 30), re=70, la=(-52, 0, -38), le=30,
           ll=(-34, 0, -6), lk=20, rl=(6, 0, 8), rk=30), "snap"),
    K(10, S(hips=(-4, 8, 0), chest=(4, 10, 0), look=(10, 22, 8), pos=(0, 0, -4),
            ra=(-30, 0, 24), re=40, la=(-22, 0, -22), le=34,
            ll=(2, 0, -8), lk=30, rl=(-30, 0, 8), rk=26)),
    K(16, DAZED_A),
]))

# One, kick: the arm is slapped aside on tick 2, the kick folds it in half on 6, lands reeling.
add(Clip("countered_kick", [
    K(0, WOUND),
    K(2, S(hips=(0, -22, 0), chest=(4, -28, 0), look=(0, -12, 0), pos=(0, 0, 1.5),
           ra=(-84, 72, 20), re=40, la=(-40, 0, -14), le=40, **MOB_BRACED), "snap"),
    K(5, S(hips=(0, -24, 0), chest=(4, -30, 0), look=(0, -12, 0), pos=(0, 0, 1.5),
           ra=(-76, 78, 22), re=44, la=(-40, 0, -14), le=40, **MOB_BRACED)),
    K(6, P(hips=(-32, 0, 0), pos=(0, 3, -2), chest=(38, 0, 0), head=(30, 0, 0),
           la=(-82, 0, -14), le=30, ra=(-90, 60, 20), re=40, ll=(-62, 0, -6), lk=44, rl=(-52, 0, 6), rk=36), "snap"),
    K(9, P(hips=(-26, 0, 0), pos=(0, 4, -5), chest=(34, 0, 0), head=(26, 0, 0),
           la=(-70, 0, -18), le=36, ra=(-74, 20, 18), re=44, ll=(-56, 0, -8), lk=56, rl=(-40, 0, 8), rk=60)),
    K(12, S(hips=(8, 0, 0), chest=(20, 0, 0), look=(22, 0, 0), pos=(0, 0, -6),
            la=(-24, 0, -12), le=30, ra=(-28, 0, 12), re=34,
            ll=(-40, 0, -8), lk=50, rl=(16, 0, 8), rk=30), "out"),
    K(17, DAZED_A),
]))

# One, throw: caught on tick 2, off its feet on 4, over the head of the player, slammed down on 14.
add(Clip("thrown", [
    K(0, WOUND),
    K(2, S(hips=(18, 0, 0), chest=(14, 0, 0), look=(-8, 0, 0), pos=(0, 0, 3),
           ra=(-104, 0, 4), re=14, la=(-90, 0, -4), le=30,
           ll=(-14, 0, -6), lk=30, rl=(24, 0, 7), rk=30), "snap"),
    K(4, P(hips=(40, 0, 0), pos=(0, 3, 3), chest=(14, 0, 0), head=(-14, 0, 0),
           ra=(-118, 0, 4), re=10, la=(-108, 0, -4), le=20, ll=(-10, 0, -6), lk=30, rl=(34, 0, 7), rk=40), "out"),
    K(7, P(hips=(104, 0, 0), pos=(0, 14, 4), chest=(6, 0, 0), head=(-20, 0, 0),
           ra=(-156, 0, 8), re=10, la=(-146, 0, -8), le=20, ll=(4, 0, -10), lk=50, rl=(38, 0, 10), rk=60), "lin"),
    K(9, P(hips=(176, 0, 0), pos=(0, 22, 0), chest=(-10, 0, 0), head=(-10, 0, 0),
           ra=(-170, 0, 20), re=20, la=(-160, 0, -20), le=30, ll=(-30, 0, -14), lk=60, rl=(12, 0, 14), rk=40), "lin"),
    K(12, P(hips=(248, 0, 0), pos=(0, 9, -3), chest=(-8, 0, 0), head=(12, 0, 0),
            ra=(-124, 0, 40), re=20, la=(-116, 0, -40), le=20, ll=(-46, 0, -12), lk=40, rl=(-20, 0, 12), rk=30), "lin"),
    K(14, P(LIE, hips=(270, 0, 0), la=(-44, 0, -50), ra=(-54, 0, 56), ll=(-30, 0, -12), lk=40, rl=(-18, 0, 15), rk=24), "in"),
    K(15, P(LIE, hips=(273, 0, 0), pos=(0, 2, -6), chest=(8, 0, 0), head=(8, 6, 0),
            ll=(-46, 0, -12), lk=70, rl=(-28, 0, 16), rk=34), "out"),
    K(18, P(LIE, hips=(270, 0, 0)), "in"),
]))

# Shared ends: flat on its back from a blow that folds it (a kick) or one that spins its head.
def _slam(t, ease_in="in"):
    return [
        K(t, P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 0, 0), head=(-8, 0, 0),
               la=(-40, 0, -50), le=20, ra=(-52, 0, 56), re=26, ll=(-30, 0, -12), lk=40, rl=(-18, 0, 14), rk=24), ease_in),
        K(t + 1, P(hips=(-87, 0, 0), pos=(0, 1.5, -6), chest=(6, 0, 0), head=(6, 6, 0),
                   la=(-30, 0, -52), le=26, ra=(-40, 0, 58), re=32, ll=(-44, 0, -12), lk=60, rl=(-26, 0, 15), rk=30), "out"),
        K(t + 4, LIE, "in"),
    ]


def _kicked(t):
    """Folded round a kick on tick t, thrown back and down."""
    return [
        K(t, P(hips=(-34, 0, 0), pos=(0, 3, -2), chest=(38, 0, 0), head=(30, 0, 0),
               la=(-82, 0, -14), le=30, ra=(-86, 0, 14), re=34, ll=(-62, 0, -6), lk=44, rl=(-52, 0, 6), rk=36), "snap"),
        K(t + 3, P(hips=(-64, 0, 0), pos=(0, 5, -5), chest=(18, 0, 0), head=(20, 0, 0),
                   la=(-100, 0, -30), le=20, ra=(-106, 0, 32), re=24, ll=(-60, 0, -8), lk=40, rl=(-46, 0, 8), rk=30), "lin"),
    ] + _slam(t + 6)


def _struck(t):
    """Head snapped round to its left on tick t, spun off its feet and down."""
    return [
        K(t, S(hips=(-10, 20, 0), chest=(-14, 30, 0), look=(-30, 62, 14), pos=(0, 0, -2),
               la=(-90, 0, -30), le=20, ra=(-70, 0, 50), re=30, ll=(-26, 0, -6), lk=30, rl=(0, 0, 8), rk=36), "snap"),
        K(t + 3, P(hips=(-54, 14, 0), pos=(0, 4, -5), chest=(-10, 18, 0), head=(20, 26, 0),
                   la=(-124, 0, -40), le=30, ra=(-60, 0, 60), re=20, ll=(-56, 0, -10), lk=60, rl=(-24, 0, 14), rk=50), "lin"),
    ] + _slam(t + 6)


def _waiting(t, glance):
    """Checked in mid wind-up while the others are dealt with: the arm drops, it starts to back off."""
    return [
        K(0, WOUND),
        K(max(1, t - 2), S(hips=(-8, -16, 0), chest=(-8, -8, 0), look=(2, glance, 0), pos=(0, 0, -1),
                           ra=(-104, 34, 12), re=96, la=(-58, 0, -10), le=50,
                           ll=(-28, 0, -6), lk=22, rl=(8, 0, 8), rk=32)),
    ]


# Two: grabbed by the head on tick 3, hauled back, heads cracked together on 7, both go down.
# (clash_l is the enemy on the player's left; the other one is its mirror image.)
CLASH_L = add(Clip("clash_l", [
    K(0, WOUND),
    K(3, S(hips=(10, 10, 0), chest=(12, 0, 0), look=(12, 0, 0), pos=(-2, 0, 3),
           ra=(-90, 20, 10), re=70, la=(-40, 0, -20), le=40,
           ll=(-30, 0, -6), lk=30, rl=(10, 0, 8), rk=30), "snap"),
    K(5, S(hips=(-4, 6, 6), chest=(-2, 0, 4), look=(-6, -10, 6), pos=(2, 0, 1),
           ra=(-70, 20, 20), re=60, la=(-50, 0, -24), le=40,
           ll=(-22, 0, -6), lk=24, rl=(2, 0, 8), rk=34)),
    K(7, S(hips=(20, 14, -16), chest=(16, 0, -14), look=(14, 30, -16), pos=(-7, 0, 6),
           ra=(-40, 0, 40), re=30, la=(-30, 0, -40), le=30,
           ll=(-36, 0, -10), lk=36, rl=(16, 0, 6), rk=36), "snap"),
    K(9, S(hips=(-12, -10, 10), chest=(-14, -10, 8), look=(-30, -40, 10), pos=(-3, 0, 2),
           ra=(-60, 0, 50), re=20, la=(-70, 0, -40), le=20,
           ll=(-30, 0, -8), lk=26, rl=(0, 0, 8), rk=40), "out"),
    K(12, P(hips=(-58, -10, 0), pos=(0, 4, -4), chest=(-8, -10, 0), head=(18, -20, 0),
            la=(-116, 0, -40), le=24, ra=(-90, 0, 56), re=20, ll=(-54, 0, -10), lk=56, rl=(-30, 0, 12), rk=46), "lin"),
] + _slam(14)))
add(CLASH_L.mirrored("clash_r"))

# Three: the one in front is kicked on 6, the one on the right takes the backfist on 10, the one
# on the left the side kick on 14.
add(Clip("triple_front", _waiting(6, 0)[:1] + _kicked(6)))
add(Clip("triple_right", _waiting(10, 40) + _struck(10)))
add(Clip("triple_left", _waiting(14, -40) + _kicked(14)))

# Four: front punched on 6, right side-kicked on 9, the one behind back-kicked on 12, left backfist on 15.
QUAD_FRONT = Clip("quad_front_mirror", _waiting(6, 0)[:1] + _struck(6))
add(QUAD_FRONT.mirrored("quad_front"))
add(Clip("quad_right", _waiting(9, 30) + _kicked(9)))
add(Clip("quad_back", _waiting(12, 0) + _kicked(12)))
QUAD_LEFT = Clip("quad_left_mirror", _waiting(15, 30) + _struck(15))
add(QUAD_LEFT.mirrored("quad_left"))

# --- gadgets ------------------------------------------------------------------------------------------
# The enemy's side of the gadget moves in clips_player.py. Each starts on the tick the gadget
# connects (the blade arrives, the claw bites, the line is yanked, the arm lands).

# A thrown blade to the head: the head whips back, a hand goes to the face, staggers off dazed.
add(Clip("blade_struck", [
    K(0, N),
    K(1, S(hips=(-12, -10, 0), chest=(-16, -14, 0), look=(-38, -20, -8), pos=(0, 0, -2),
           la=(-60, 0, -30), le=40, ra=(-70, 0, 36), re=50, ll=(-34, 0, -6), lk=18, rl=(4, 0, 8), rk=32), "snap"),
    K(4, S(hips=(-6, -14, 0), chest=(2, -10, 0), look=(8, -24, -6), pos=(0, 0, -3.5),
           la=(-30, 0, -24), le=40, ra=(-86, -30, 0), re=(132, 60), ll=(0, 0, -8), lk=30, rl=(-30, 0, 8), rk=26)),
    K(8, S(hips=(10, -6, 0), chest=(18, -4, 0), look=(24, -12, -8), pos=(0, 0, -3),
           la=(-16, 0, -14), le=30, ra=(-84, -34, 0), re=(136, 60), ll=(-24, 0, -8), lk=36, rl=(-6, 0, 8), rk=30)),
    K(13, DAZED_A),
]))

# The claw bites into the chest: jolted forward by it, hands snatch at the line, heels dug in.
HOOKED = S(hips=(-10, 0, 0), chest=(16, 0, 0), look=(14, 0, 0), pos=(0, 0, 1),
           la=(-58, 24, 0), le=(104, -90), ra=(-58, -24, 0), re=(104, 90),
           ll=(-46, 0, -8), lk=30, rl=(-24, 0, 9), rk=20)
add(Clip("hooked", [
    K(0, N),
    K(1, S(hips=(10, 0, 0), chest=(22, 0, 0), look=(-10, 0, 0), pos=(0, 0, 3),
           la=(-40, 0, -30), le=30, ra=(-44, 0, 34), re=34, ll=(-20, 0, -6), lk=30, rl=(22, 0, 8), rk=30), "snap"),
    K(5, HOOKED),
    K(9, S(hips=(-12, 0, 0), chest=(18, 0, 0), look=(14, 0, 0), pos=(0, 0, 1.5),
           la=(-58, 24, 0), le=(108, -90), ra=(-58, -24, 0), re=(108, 90),
           ll=(-48, 0, -8), lk=32, rl=(-26, 0, 9), rk=20)),
]))

# Yanked in: torn off its feet toward the player, lands running and pitches up dazed in front of them.
add(Clip("pulled", [
    K(0, HOOKED),
    K(2, P(hips=(62, 0, 0), pos=(0, 6, 4), chest=(-10, 0, 0), head=(-30, 0, 0),
           la=(30, 0, -20), le=30, ra=(34, 0, 20), re=34, ll=(30, 0, -8), lk=50, rl=(50, 0, 8), rk=70), "snap"),
    K(5, P(hips=(54, 0, 0), pos=(0, 5, 4), chest=(-6, 0, 0), head=(-28, 0, 0),
           la=(20, 0, -24), le=40, ra=(24, 0, 24), re=44, ll=(-40, 0, -8), lk=70, rl=(40, 0, 8), rk=60)),
    K(8, S(hips=(30, 0, 0), chest=(20, 0, 0), look=(10, 0, 0), pos=(0, 0, 3),
           la=(-50, 0, -30), le=40, ra=(-56, 0, 30), re=44, ll=(-60, 0, -8), lk=60, rl=(30, 0, 8), rk=50), "out"),
    K(11, S(hips=(18, 0, 0), chest=(22, 0, 0), look=(20, 0, 0), pos=(0, 0, 2),
            la=(-30, 0, -16), le=34, ra=(-34, 0, 16), re=38, ll=(-6, 0, -8), lk=40, rl=(-40, 0, 8), rk=56)),
    K(15, DAZED_A),
]))

# Clotheslined on the way in: the head and chest stop, the legs carry on, slams down on its back.
add(Clip("clotheslined", [
    K(0, S(hips=(30, 0, 0), chest=(20, 0, 0), look=(10, 0, 0), pos=(0, 0, 3),
           la=(-50, 0, -30), le=40, ra=(-56, 0, 30), re=44, ll=(-60, 0, -8), lk=60, rl=(30, 0, 8), rk=50)),
    K(2, P(hips=(-50, 0, 0), pos=(0, 7, 0), chest=(-18, 0, 0), head=(-30, 0, 0),
           la=(-90, 0, -40), le=20, ra=(-96, 0, 44), re=24, ll=(-70, 0, -8), lk=30, rl=(-56, 0, 8), rk=20), "snap"),
    K(5, P(hips=(-96, 0, 0), pos=(0, 6, -3), chest=(-6, 0, 0), head=(10, 0, 0),
           la=(-110, 0, -44), le=20, ra=(-116, 0, 46), re=24, ll=(-60, 0, -10), lk=40, rl=(-44, 0, 10), rk=30), "lin"),
    K(7, P(hips=(-90, 0, 0), pos=(0, 0, -6), chest=(0, 0, 0), head=(-8, 0, 0),
           la=(-40, 0, -50), le=20, ra=(-52, 0, 56), re=26, ll=(-30, 0, -12), lk=40, rl=(-18, 0, 14), rk=24), "in"),
    K(8, P(hips=(-87, 0, 0), pos=(0, 2, -6), chest=(8, 0, 0), head=(8, 6, 0),
           la=(-30, 0, -52), le=26, ra=(-40, 0, 58), re=32, ll=(-48, 0, -12), lk=66, rl=(-28, 0, 15), rk=32), "out"),
    K(11, LIE, "in"),
]))
