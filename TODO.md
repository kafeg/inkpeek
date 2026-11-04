# TODO
- [ ] Import from file: test through the system file picker on a device (unit-tested; adb taps do not reach the picker).
- [ ] README screenshots of the 0.8 card and settings (`scripts/screens.sh`).
- [ ] F-Droid: metadata, a reproducible source build (NDK submodules), the anti-feature for AI.
- [ ] OPUS-MT `ram_mb` (3500 in `build.py`): a big model now holds ~0.9 GB (was ~1.13). Lower it to ~2500 (3 GB devices) only after a test on a real 3 GB device with a reader open; old app versions read the same manifest and hold ~1.13 GB.
