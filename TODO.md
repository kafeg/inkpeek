# TODO
- [ ] Import from file: test through the system file picker on a device (unit-tested; adb taps do not reach the picker).
- [ ] README screenshots of the 0.8 card and settings (`scripts/screens.sh`).
- [ ] F-Droid: metadata, a reproducible source build (NDK submodules), the anti-feature for AI.
- [ ] Bergamot memory: `TranslationModel` keeps the whole model file (`memory_.models`) after the graph is loaded; freeing it saves ~270 MB per OPUS-MT big model (1.31 → 1.04 GB), then measure and set `ram_mb` per model from that (now a constant 3500 in `build.py`). Needs our own patch or fork of the pinned submodule.
