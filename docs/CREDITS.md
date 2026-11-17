# Credits

## Animations

All player and enemy animations are original and hand-authored for this mod. They are written as
poses on a small skeleton (`tools/rig.py`, `tools/clips_player.py`, `tools/clips_mob.py`) and baked
to the game's formats by:

```
python tools/gen_animations.py
```

`python tools/preview.py <folder>` draws contact sheets of every clip without starting the game
(needs Pillow and NumPy).

## Libraries

- [playerAnimator](https://github.com/KosmX/fabricPlayerAnimation) (MIT) - player animation layers
- Fabric API, Cloth Config, ModMenu
