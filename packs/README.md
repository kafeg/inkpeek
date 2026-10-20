# InkPeek language packs

Scripts that build what InkPeek downloads for offline use, from open data. The app downloads only from this
repository's releases (`packs-*`), never from third-party servers.

| component | what | from | license |
|---|---|---|---|
| `dict-<x>-<y>` | one dictionary per pair: first line "header — first meanings" (the header from the grammar: article, plural, verb forms, irregular), the full article, every inflected form in `.syn` | Wiktionary via [kaikki.org](https://kaikki.org) (English words: en.wiktionary's translation tables first), [WikDict](https://www.wikdict.com) | CC BY-SA 4.0 |
| `model-<x>-<y>` | offline translation model (always to or from English) | [Firefox Translations](https://github.com/mozilla/translations), Mozilla Remote Settings; the full model where there is one | MPL-2.0 |
| `langid` | language identification | [fastText](https://fasttext.cc) lid.176 | CC BY-SA 3.0 |

The grammar of each book language (gender, genitive, plural, verb principal parts, auxiliary, strong / weak /
irregular, every form → its lemma) is built from Wiktionary — de: de.wiktionary + classes from en.wiktionary; others:
en.wiktionary — as an input of the dictionaries; it is not published on its own.

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

On a device the app itself is tested on the installed packs: `scripts/device-test.sh` (headers and meanings of
words and their forms per pair, language identification, offline translation; cases in
`app/src/androidTest/assets/cases.tsv`).

## Release
The `Packs` workflow (manual) takes the book languages and answer languages to build, e.g. `de,en` and `ru,en`:
1. `slices` — per-language slices of the kaikki dumps come from release `packs-sources`; a dump is downloaded and
   cut again only when kaikki changed it (ETag / Last-Modified) or a slice is missing.
2. `all` — grammar of each book language, a dictionary per pair, the models through English, `langid`.
3. Unit tests and golden words (`tests/golden/<x>.tsv` per grammar, `<x>-<y>.tsv` per dictionary).
4. `publish` — uploads only these assets to release `packs` and merges their entries into its `packs.json`;
   components built earlier stay as they are. The app reads `packs.json` from that release.
