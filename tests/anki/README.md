# Anki cases

Shared by InkPeek (`AnkiCasesTest`) and LexiPeek (`anki_test.cpp`, through its `tests/inkpeek` submodule). The rules:
`docs/formats.md`, "Anki notes". Made by `generate.py` (seed `SEED`, texts from `tests/corpus.py`; run it again after
a change, never edit the output). Each file: `{"about", "cases": [...]}`, one case a line. A failing case is a finding
about the app: never change a case to make one app pass.

| file | a case | checks |
|---|---|---|
| `keys.json` | `text, lang, history, answer` → `key` | every text of the corpus in 3 languages and none (`answer` then), upper-case codes, history; NFC/NFD, soft hyphen, edge whitespace (no-break, ideographic, VT, NEL), ẞ, Greek final sigma, Turkish İ |
| `fields.json` | an entry as the log has it (`lang`: a code; `quick`: its newest quick AI answer or `null`) → `fields` (`Front, Back, Example, Key`) | headers ` — `, escapes, line breaks; a blank `meaning` → the brief line: a translation, base and meanings, not JSON, mistyped fields, 140 characters with emoji |
| `dedupe.json` | `notes` (`id, key, audio, tags`) → `audio` (kept id → the Audio it takes), `deleted` (sorted) | 1–9 notes of 1–3 Keys, empty Keys, Audio on the oldest or another, both apps' tags |
