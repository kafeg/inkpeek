# InkPeek language packs

Scripts that build what InkPeek downloads for offline use, from open data. The app downloads only from this
repository's releases (`packs-*`), never from third-party servers.

| component | what | from | license |
|---|---|---|---|
| `grammar-<x>` | one line per lemma: part of speech, gender, genitive, plural, verb principal parts, auxiliary, strong / weak / irregular; `.syn`: every inflected form → its lemma | Wiktionary via [kaikki.org](https://kaikki.org) (de: de.wiktionary + classes from en.wiktionary; others: en.wiktionary) | CC BY-SA 4.0 |
| `dict-<x>-<y>` | one dictionary per pair: articles from the Wiktionary of language y, first line "header — first meanings", WikDict where Wiktionary has no article, `.syn` from the grammar | kaikki.org, [WikDict](https://www.wikdict.com) | CC BY-SA 4.0 |
| `model-<x>-<y>` | offline translation model (always to or from English) | [Firefox Translations](https://github.com/mozilla/translations), Mozilla Remote Settings; the full model where there is one | MPL-2.0 |

No guessing: forms, genders and classes come only from Wiktionary data; hand edits live in `overrides/<x>.tsv`.

## Build
```bash
python3 packs/build.py all --langs de,en --answers ru,en --work /tmp/packs-work --out packs-out
python3 -m unittest discover -s packs/tests                         # unit tests
PACKS_OUT=packs-out python3 -m unittest discover -s packs/tests     # + golden words on the built packs
```
Needs Python 3.10+ and `zstd`. The kaikki dumps are streamed, never unpacked (English: 3 GB compressed, 25 GB
inside); a run with them already downloaded takes about 4 minutes and 2 GB of memory on a laptop. Outputs:
`<id>.zip` (StarDict folders), `model-<x>-<y>.tar.zst`, and `packs.json` — id, kind, languages, file name, size,
sha256, license, attribution, stats.

## Release
The `Packs` workflow (manual, and monthly) builds, runs the tests and creates a draft release `packs-YYYY.MM.DD-N`;
a person checks and publishes it.
