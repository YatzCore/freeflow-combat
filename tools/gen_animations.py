"""
Generates every freeflow animation:

    src/main/resources/assets/freeflow/player_animations/freeflow_combat.json   (playerAnimator)
    src/main/resources/assets/freeflow/mob_animations.json                      (MobClips.java)
    src/main/resources/assets/freeflow/animation_meta.json                      (impact ticks)

    python tools/gen_animations.py            # write the files (fails if anything clips the floor)
    python tools/preview.py <dir> [clip ...]  # contact sheets, to look at poses without the game

The clips themselves live in clips_player.py and clips_mob.py; the skeleton, the flattening to
Minecraft's boxes and the automatic floor contact live in rig.py.
"""
import json
import os

from clips_mob import MOB
from clips_mob import PREVIEW as MOB_PREVIEW
from clips_player import PLAYER
from clips_player import PREVIEW as PLAYER_PREVIEW
from rig import simulate_player_json, to_mob_clip, to_player_json

PREVIEW = {**PLAYER_PREVIEW, **MOB_PREVIEW}

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets",
                      "freeflow")


def check_floor(name, anim, airborne):
    """
    On every tick no box corner may be below the floor, and grounded clips must touch it. Between
    two ticks the game blends in straight lines, which can dip a fast tumble a little: that is
    capped here and lifted out at render time (GroundFit.java).
    """
    lows = simulate_player_json(anim)
    for t, low in lows:
        on_tick = abs(t - round(t)) < 1e-6
        if low < (-0.1 if on_tick else -2.5):
            raise SystemExit(f"{name}: sinks {-low:.2f} px into the floor at tick {t:g}")
        if not airborne and on_tick and low > 0.1:
            raise SystemExit(f"{name}: floats {low:.2f} px above the floor at tick {t:g}")


def check_continuity(name, anim):
    """Neighbouring keyframes more than half a turn apart would spin the wrong way round."""
    for bone, chans in anim["bones"].items():
        prev = None
        for key in chans.get("rotation", {}).values():
            cur = key["vector"] if "vector" in key else key["pre"]["vector"]
            if prev is not None and any(abs(a - b) > 175 for a, b in zip(cur, prev)):
                raise SystemExit(f"{name}: {bone} jumps {prev} -> {cur} in one tick")
            prev = key["vector"] if "vector" in key else key["post"]["vector"]


def main():
    for group in (PLAYER, MOB):
        names = [c.name for c in group]
        assert len(names) == len(set(names)), "duplicate clip name"
    animations = {}
    for clip in PLAYER:
        anim = to_player_json(clip)
        airborne = any(p["pos"][1] > 0 for _, p, _ in clip.keys)
        check_floor(clip.name, anim, airborne)
        check_continuity(clip.name, anim)
        animations[clip.name] = anim
    with open(os.path.join(ASSETS, "player_animations", "freeflow_combat.json"), "w", encoding="utf-8") as f:
        json.dump({"format_version": "1.8.0", "animations": animations}, f, separators=(",", ":"))

    with open(os.path.join(ASSETS, "mob_animations.json"), "w", encoding="utf-8") as f:
        json.dump({c.name: to_mob_clip(c) for c in MOB}, f, separators=(",", ":"))

    meta = {c.name: {"impact": c.impact, "length": c.length} for c in PLAYER if c.impact is not None}
    with open(os.path.join(ASSETS, "animation_meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=1)
    print(f"{len(PLAYER)} player clips, {len(MOB)} enemy clips")


if __name__ == "__main__":
    main()
