# TODO
- [ ] Import from file: test through the system file picker on a device (unit-tested; adb taps do not reach the picker).
- [ ] README screenshots of the 0.8 card and settings (`scripts/screens.sh`).
- [ ] F-Droid: metadata, a reproducible source build (NDK submodules), the anti-feature for AI.
- [ ] Translation memory: measure each model held (`adb` `/proc/<pid>/status` VmRSS) and set `ram_mb` per model in `build.py` (now a constant 3500; an OPUS-MT big model holds ~0.9 GB).
