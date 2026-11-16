# Credits

## Motion capture

Several player and enemy animations (run, roll, jabs, hooks, power punch, front kick, side kick,
enemy fall-on-the-back and get-up) are converted from the **CMU Graphics Lab Motion Capture
Database**:

> The data used in this project was obtained from mocap.cs.cmu.edu.
> The database was created with funding from NSF EIA-0196217.

Trials used (see `tools/mocap.py`, `PLAYER_CLIPS` / `MOB_CLIPS`):
127_06, 127_23, 144_20, 14_01, 143_23, 135_04, 135_11, 90_18, 140_08.

The raw ASF/AMC files are not stored in this repository. To rebuild the animations, download the
listed trials (and each subject's `.asf`) from `http://mocap.cs.cmu.edu/subjects/<subject>/` into a
folder and run:

```
python tools/mocap.py build <that folder>
```

## Libraries

- [playerAnimator](https://github.com/KosmX/fabricPlayerAnimation) (MIT) - player animation layers
- Fabric API, Cloth Config, ModMenu
