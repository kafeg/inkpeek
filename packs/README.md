# InkPeek language packs

Scripts that build what InkPeek downloads for offline use, from open data. The app downloads only from this
repository's releases (`packs-*`), never from third-party servers.

| component | what | from | license |
|---|---|---|---|
| `dict-<x>-<y>` | one dictionary per pair: first line "header — first meanings" (the header from the grammar: article, plural, verb forms, irregular), the full article, every inflected form in `.syn` | Wiktionary via [kaikki.org](https://kaikki.org) (English words: en.wiktionary's translation tables first), [WikDict](https://www.wikdict.com) | CC BY-SA 4.0 |
| `model-<x>-<y>` | offline translation model (always to or from English) | [Firefox Translations](https://github.com/mozilla/translations), Mozilla Remote Settings; the full model where there is one | MPL-2.0 |
| `langid` | language identification | [fastText](https://fasttext.cc) lid.176 | CC BY-SA 3.0 |
| `opus-<model>` | direct translation (no English in between), one group model for several directions (`covers`); vocabulary mapped to SentencePiece, int8; target tags (`>>rus<<`) in `tags` | [OPUS-MT](https://github.com/Helsinki-NLP/Tatoeba-Challenge), Helsinki-NLP; the plan in `opus.json` (fewest models within 2 chrF of the best per direction, FLORES-200) | CC BY 4.0 / Apache-2.0 |
| `llm-<model>` | AI translation model (GGUF) as published, with its prompt template and requirements; `mirror` = the Hub's file, which the app tries first | [Hy-MT2](https://huggingface.co/tencent/Hy-MT2-1.8B) (Tencent), `llm.json` | Apache-2.0 |

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

The table of pairs in `docs/languages.md` (coverage of the 5,000 most frequent words, sources, models, sizes) is made
from the published packs: `python3 packs/report.py --work /tmp/packs-report --readme docs/languages.md`.

## Translation models
`.github/workflows/mt.yml` (manual): builds marian-conv and the bergamot CLI from the app's Bergamot (int8 packing is
x86-only), then `build.py opus-convert` (download the Marian zip, remap, quantize; x86) and `build.py opus-finish` (one check
translation per direction with the app's engine; ARM) and
`build.py llm` (the GGUF at its pinned revision, checked against the Hub's sha256), and publishes to `packs`. Locally:
`--marian-conv "docker run --platform linux/amd64 …"`. The plan is regenerated from the OPUS-MT leaderboard with
`opus.plan()` (scores/chrf_scores.db, released-models.txt).

## Adding a language
A language is one of two roles, often both: a **book language** (the text you read) and an **answer language** (the
app's language, the dictionaries' and translations' target). Do every step that applies, in this order; nothing is
added by hand on a device.
1. **Is there enough data?** `packs/report.py` and `research` notes: kaikki edition of the language (its own Wiktionary
   or en.wiktionary), WikDict pairs, top-5,000 coverage of the pairs you plan. Every pack is published with its coverage (`stats.coverage`); the
   app labels it: **full** (≥ 85 % and offline translation), **basic** (≥ 60 %), **experimental** (below).
2. **Sources:** `sources.json` → `kaikki` (the language's own wiktextract dump, if any) and `languages` (its English
   name). Grammar comes only from data (`grammar.py`: genders, plurals, verb forms of that edition); no endings rules —
   fixes go to `overrides/<x>.tsv`.
3. **Mozilla models:** a book language needs `x→en`, an answer language `en→y` at Mozilla (its model records, read by `models.py`; the build
   prints "none at Mozilla" otherwise — then the pair has no offline translation).
4. **OPUS-MT:** add the language to `opus.ISO3`, regenerate `opus.json` with `opus.plan()` (OPUS-MT leaderboard: the
   new directions get their models; check the coverage list it prints), run the *Translation models* workflow.
5. **Hy-MT2 (`llm.json`):** if the model knows the language (its card lists 33), add its English name to `names` and
   the new directions to `covers`; the workflow republishes the entry.
6. **Golden words:** `tests/golden/<x>.tsv` (book) and `tests/golden/<x>-<y>.tsv` (pairs) — real words and forms that
   must come out right; device cases in `app/src/androidTest/assets/cases.tsv`.
7. **Build and publish:** the *Packs* workflow with the book and answer languages, then `scripts/device-test.sh` on a
   device with the new pair installed.
8. **App, answer language only:** `Prefs.ANSWERS`, `values-<y>/strings.xml` with every key of `values/strings.xml`
   (the CI test of string keys fails otherwise), the language in the README's list; `Fonts.LETTERS` gets its letters if
   they are new (the font page shows only fonts that have them).
9. **README:** the pairs table (`report.py --readme`), CHANGELOG.

## Release
The `Packs` workflow (manual) takes the book languages and answer languages to build, e.g. `de,en` and `ru,en`:
1. `slices` — per-language slices of the kaikki dumps come from release `packs-sources`; a dump is downloaded and
   cut again only when kaikki changed it (ETag / Last-Modified) or a slice is missing.
2. `all` — grammar of each book language, a dictionary per pair, the models through English, `langid`.
3. Unit tests and golden words (`tests/golden/<x>.tsv` per grammar, `<x>-<y>.tsv` per dictionary).
4. `publish` — uploads only these assets to release `packs` and merges their entries into its `packs.json`;
   components built earlier stay as they are. The app reads `packs.json` from that release.
