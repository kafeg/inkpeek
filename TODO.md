# TODO
- [ ] Import from file: test through the system file picker on a device (unit-tested; adb taps do not reach the picker).
- [ ] README screenshots of the 0.8 card and settings (`scripts/screens.sh`).
- [ ] F-Droid: metadata, a reproducible source build (NDK submodules), the anti-feature for AI.
- [ ] OPUS-MT `ram_mb` (3500 in `build.py`): a big model now holds ~0.9 GB (was ~1.13). Lower it to ~2500 (3 GB devices) only after a test on a real 3 GB device with a reader open; old app versions read the same manifest and hold ~1.13 GB.
- [ ] Target language for one look-up (asked on MobileRead): only the settings choose it — the answer language and the
  one language you learn; no way to pick another installed pack's language from the card. A picker there goes against
  "the card has no settings of its own" (KISS in `CLAUDE.md`).
- [ ] Offline translation into German, formality (asked on MobileRead): Mozilla mixes du/Sie inside one text and
  garbles some sentences; Hy-MT2 is always formal. Model limitation — only Hy-MT2's prompt (`template` in the packs
  manifest, `packs/build.py`) could carry a hint; nothing to fix for Mozilla.
- [ ] LexiPeek, its own repo (asked on MobileRead): the card's font is only System/Serif/Rounded/Monospaced — a font
  file as InkPeek has; the packs download still needed several tries after the 0.2.395 fix (unverified).
