# Data formats

The formats InkPeek reads and writes that other apps share: the language packs list, the records (saved words,
history, answers) and the sync log; and the sentence breaks of offline translation. InkPeek is the reference; [LexiPeek](https://github.com/kafeg/lexipeek) (macOS)
implements the same formats with its own code. A format changes here and in `tests/` first, then in the apps.

## Language packs: `packs.json`
Published in the GitHub release `packs` of this repo (`https://github.com/kafeg/inkpeek/releases/download/packs/`),
next to the files it lists. Written by `packs/build.py` (`write_manifest`, `publish` merges a run's entries into the
release's list); read by `Packs.parse` (`app/.../packs/Packs.kt`). UTF-8 JSON.

```json
{"format": 1, "generated": "2026-10-23", "components": [ … ]}
```

`format`: 1. `generated`: the build date. `components`: sorted by `id`. Each component:

| field | kinds | meaning |
|---|---|---|
| `id` | all | unique; a plain name (`[A-Za-z0-9._+-]`, no `..`): it becomes a path. `dict-<from>-<to>`, `model-<from>-<to>`, `langid`, `opus-<name>`, `llm-<name>` |
| `kind` | all | `dict`, `model` (Mozilla, Bergamot), `langid` (fastText), `opus` (OPUS-MT, Bergamot), `llm` (GGUF, llama.cpp). An unknown kind is not installed |
| `from`, `to` | all | ISO 639-1 codes; `""` for `opus`, `llm` (they use `covers`) and `langid` |
| `file` | all | `{"name", "size", "sha256"}`: the asset name in the release, bytes, lowercase hex. The download is checked against `sha256` |
| `license`, `attribution` | all | shown in the app |
| `stats` | dict | build statistics; the app reads `stats.coverage` only |
| `version` | model, langid | the source's version (informative) |
| `arch` | model | Mozilla's model architecture (informative) |
| `covers` | opus, llm | directions it translates: `["de-ru", …]` |
| `tags` | opus | target → the token put before a sentence (`{"fr": ">>fra<<"}`) |
| `chrf` | opus | direction → chrF of the check translation (informative) |
| `ram_mb` | opus, llm | the device RAM it needs; missing: 0 (any) |
| `cpu` | llm | CPU features it needs on arm64 (`["asimddp"]`, Linux's `/proc/cpuinfo` names) |
| `cpu_by_arch` | llm | CPU features it needs per architecture (`{"arm64": ["asimddp"], "x86_64": ["avx2", "fma"]}`): a reader takes its own architecture's list; an architecture not in it cannot run the model. Missing: `cpu` on arm64 |
| `name` | llm | the model's display name |
| `template` | llm | the prompt; `{from}`, `{to}` (language names from `names`) and `{text}` are replaced |
| `names` | llm | code → English language name for `template` |
| `mirror` | llm | the original URL, tried before the release asset; same `sha256` |

Readers ignore fields they do not know. `stats` of a dict: `coverage` (% of the book language's 5,000 most frequent
words the pair knows; 0 or missing: unknown), `words`, `forms`, `related` and other build counts.

### Labels and what a pair needs
- Level of a pair (`Packs.level`): `full` — coverage ≥ 85 and Mozilla models for the pair (direct when one side is
  `en`, else both `<from>-en` and `en-<to>`); `basic` — coverage ≥ 60; else `experimental`; no coverage: no label.
- A pair needs: `dict-<from>-<to>`, the Mozilla models through English, `langid`; with the method "direct" also the
  first `opus` whose `covers` has the pair, with "llm" the first such `llm`.
- An update: an installed component whose `sha256` in the list differs from the installed one.

### Files of a component
| kind | archive | contents |
|---|---|---|
| dict | `.zip` | one folder `dict-<from>-<to>/`: StarDict (`.ifo`, `.idx`, `.dict.dz`, `.syn`) and `.rel` |
| model | `.tar.zst` | Mozilla's model files (Bergamot) |
| opus | `.tar.zst` | one folder `<id>/`: model, vocabulary (SentencePiece), `ATTRIBUTION`, `LICENSE` |
| langid | `.ftz` | the fastText model, as is |
| llm | `.gguf` | the model, as is |

Archives are unpacked flat (file names only, folders dropped). The first line of a dict article is its header
(article, plural, verb forms). `.rel`: one line per word, sorted by UTF-8 bytes, tab-separated: `word`, `rel`, `syn`,
`ant`, `expr`, `ipa`, `ety`, `forms` (lists joined by `|`; a form is `form:tag tag`), `rank` (in the frequency list,
0: not in it), `sep` (a separable verb's particle, else empty). Built in `packs/inkpeek_packs/related.py`.

InkPeek's install layout (its own, not shared): `dicts/<id>/`, `langid.ftz`, `installed.json` (id → sha256), the
last list as `packs.json`; models in `<models>/<from><to>/`, `opus/<id>/`, `llm/<id>.gguf`.

## Languages you learn
A setting, per device (not synced), with the same meaning in both apps: per answer language, **one** other language (or
none). InkPeek: Prefs `study_<answer>` = `"de"` (an older `"de,en"`: only its first code counts); LexiPeek: `settings.json`
`"study": {"ru": ["de"]}` — a list of at most one, kept for compatibility (more: only the first counts). The language
counts only with its pack `dict-<answer>-<x>` installed. A text in the answer language is then shown in it (what each tab
shows: `docs/spec.md`, milestone 21). AI answers for it are saved under a `ver` of their own:
`(prompt + the study paragraph with the language's name).hashCode()`; none keeps the prompt's own `ver`.

## Records
InkPeek keeps them in one SQLite file (`Store.kt`, `/sdcard/InkPeek/inkpeek.db`, schema version 4). The tables are
the shared model: the sync log carries the same fields, the merged state in the tests uses the same names.

| table | key | columns |
|---|---|---|
| `saved` | `text` | `kind` (`word` / `phrase`), `meaning` (the line shown), `lang` (the language of `meaning`), `created` (ms) |
| `history` | `text` | `kind`, `seen` (ms of the last look), `count` (looks), `meaning`, `lang`, `base` (the base form of a looked-up form, `"andere"` → `"ander"`; NULL: none) |
| `ai` | `text, lang, section, ver` | `model`, `answer`, `created` (ms) |

- `ai` holds answers of AI and offline translation. `lang`: the answer language. `section`: `quick`, `analysis`,
  `context` (AI) or `offline-<from>` (offline translation). `ver`: the prompt's version (or the method's), so a new
  prompt asks again. `answer`: a JSON object as text for AI sections (`quick`: `translation`, or `base`, `article`,
  `meanings`), plain text for `offline-*`. `model`: the model id, `bergamot`, `opus`, `llm`.
- An answer lives as long as its `text` is in `history` or `saved`.
- The history keeps the newest 1,000 entries (by `seen`), with their answers.
- Looks of a word count across its forms: Σ `count` where `COALESCE(base, text)` is the word.
- A word saved as its base form takes the looked-up form's answers along.

## Sync log v2
Not implemented yet; this is the format both apps implement (v1, below, is read only). No servers: the logs live in a
folder that a sync tool (Syncthing, a cloud drive, a NAS share) copies between devices; the user picks it in each app
(`<sync>`). One folder serves InkPeek and LexiPeek together, any number of installs of each. Synced: saved words and
phrases, the history, the answers (`ai`). Not synced: settings, packs, models, voices. The records (above) are built
from the logs; each app keeps them in its own store, with `origin`, `updated_by` and `devices` (below).

### Files
```
<sync>/inkpeek-log/
  <device>/device.json      the device: id, app, name, when it was added
  <device>/2026-10.jsonl    the device's lines whose t[0] is in that month (UTC)
  <device>/2026-11.jsonl
<sync>/log/<device>.jsonl   v1 logs: read, never written
```
- Only the device `<device>` writes in `inkpeek-log/<device>/`. Every device reads every device folder, its own too.
- In a device folder a reader reads every file whose name ends with `.jsonl` and does not start with `.`: the month
  files and the copies cloud clients make on a conflict (`2026-11 (conflicted copy 2026-11-02).jsonl`,
  `2026-11.sync-conflict-20261102-101500-ABCDEFG.jsonl`, `2026-11 (1).jsonl`). The merge is idempotent, so the copies
  are harmless; the file a line is in does not matter. Not read: other names (`*.tmp`, `.syncthing.*`), files directly
  in `inkpeek-log/`, deeper folders. A folder without `device.json` (not synced yet) is read too.
- UTF-8, one JSON object per line, every line ends with `\n`.

### Device id
- `<platform>-<8 random characters>` of lower-case base32 (RFC 4648: `a-z`, `2-7`): `mac-k3fw2xqa`, `boox-7hq2mzta`.
  `<platform>`: 1–12 of `a-z0-9`. LexiPeek: `mac`, `win`, `linux`; InkPeek: the device maker (`Build.MANUFACTURER`
  lower-cased, only `a-z0-9`, at most 12; empty: `android`). Only informative: readers never parse an id.
- Made once and kept in the app's own data, not in `<sync>`: LexiPeek's data folder (survives a reinstall), InkPeek's
  private app data (lost on an uninstall). With it the app keeps `name`, `created` and `last`: the last line it wrote
  (its bytes, without `\n`). A new id always means a new folder; the old folder stays and is read.
- A device that wrote v1 (LexiPeek's `mac-91c0`) takes a new v2 id; its v1 file stays and is read, never written again.

### `device.json`
```json
{"v":2,"device":"mac-k3fw2xqa","app":"lexipeek","name":"MacBook Air","created":1793145600000}
```
- `v`: 2. `device`: the id, the same as the folder name. `app`: `inkpeek` or `lexipeek`. `name`: the name people see
  (by default the device model or the computer's name; changed in the app's settings). `created`: when the device was
  added (the id was made), ms since the epoch; apps show it in local time.
- Written whole (canonical form, below, and `\n`) when the device first writes to the folder or finds the file
  missing, and when its name changes. A new name makes no new id and no new log.
- Readers ignore unknown fields. Apps show `name` for a device's lines (the device of a saved word); without the file:
  the id.

### A line
```json
{"v":2,"t":[1793491198000,0],"d":"mac-k3fw2xqa","op":"saved.put","text":"Haus","kind":"word","lang":"ru","meaning":"дом","id":"mac-k3fw2xqa:1793491198000:0"}
```
- The same as v1 (the ops and their fields, `t`, the clock), with `v`: 2 and `id` on `saved.put`:
  `<d>:<t[0]>:<t[1]>`, the line's own key. `d` and `t` give the same value, so readers do not need `id`: a missing or
  different `id` changes nothing.
- Skipped: a line without the final `\n` (being written or cut; also when it is valid JSON), a line that is not a
  JSON object, `v` other than 2 (in `log/`: other than 1), an unknown `op`, a line without `t` (two integers), `d` or
  `text` (strings). A missing field of a known op reads as `""` (`ver`, `c`: 0; `base`: none). Unknown fields are
  ignored. So a new op or field needs no new `v`; a new `v` is for lines that older readers must not apply.
- The canonical form, so that both apps write the same bytes: compact JSON (no spaces); keys `v, t, d, op`, then
  `saved.put`: `text, kind, lang, meaning, id`; `saved.del`, `history.del`: `text`; `history.seen`:
  `text, kind, lang, meaning, base, c` (`base` left out when none); `ai.put`: `text, lang, section, ver, model,
  answer`. Strings: UTF-8 as is; escaped are only `"` (`\"`), `\` (`\\`), `\b \f \n \r \t` and the other characters
  below U+0020 (`\u001f`, lower-case hex); `/` and non-ASCII are not escaped. Integers in decimal.

### Writing
- A line goes to the month file of its `t[0]` in UTC: `YYYY-MM.jsonl` (`1793491199999` → `2026-10.jsonl`,
  `1793491200000` → `2026-11.jsonl`). The clock never goes back, so a device writes only to its newest month; past
  months never change, except by the device's own compaction.
- Appending: a file that does not end with `\n` (a crash mid-line) is cut back to its last `\n` first. Where a file
  cannot be appended to (Android SAF providers without `"wa"`), the current month file is written whole: its complete
  lines as they are in the folder now and the new line: the same bytes as appending.
- The clock: as v1, from the greatest `t` of every line read (v1 and v2, every device, its own too). `c` of
  `history.seen`: as v1, counted over the v1 and v2 lines read.
- After a line is written the app stores it as `last`. A crash in between can only cause a needless new id (below).

### Two installs with one id
Copied app data, a restored backup or a cloned machine give two installs one id: two writers of one folder. Each
install checks at start and when its sync folder is chosen, before it writes:
- `own`: the complete, valid v2 lines with `d` = its id in `inkpeek-log/<id>/` (every file read, as above).
- `own` is empty: **keep** the id (nothing written there yet, or another folder).
- Else the lines of `own` with the greatest `t`: all of them byte-equal to `last` → **keep**; else (no `last`, an
  older one, or another line with the same `t`) → **new** id: a new device (new id, `created` = now, the same
  `name`); the clock goes on. In a race both installs may take new ids: that costs a folder, no data.

### Continue as a device
An app without an id (first start, data deleted, Android after an uninstall) looks at the folder when it is chosen:
- Candidates: every `inkpeek-log/<x>/` whose `device.json` is readable, with `v` 2, `device` = `<x>` and `app` = this
  app, whose last activity is at least 24 hours (86,400,000 ms) before now (the wall clock). Last activity: the
  greatest `t[0]` of the complete, valid v2 lines with `d` = `<x>` in that folder; none: `created`.
- No candidates: a new device, no question. Else the app asks: "Continue as `<name>`" for each candidate, newest
  last activity first (equal: by id), with its last activity in local time; and "New device".
- Continue: the app takes `<x>` as its id, `name` and `created` from its `device.json`, `last` = the newest line of
  `own`; the clock from every line read. A device active in the last 24 hours is not offered: it is probably in use,
  and two writers would follow (the check above would split them again).
- v1 logs are never offered. Old logs are read in every case.

### The merge
- The lines: `log/*.jsonl` (v1) and the device folders (v2), as above. Byte-equal lines are one (a line read twice:
  conflicted copies, the same file twice).
- Order: `t[0]`, then `t[1]`, then `d` (string order), then the line's bytes (only two installs with one id write two
  lines with the same `t` and `d`). "Newest" means last in this order.
- The rules of v1 (`saved`: the newest `saved.*` wins; `history.seen` after the last `history.del`, Σ per device of
  its greatest `c`; `ai`: union, the newest per key, only for a `text` in `saved` or `history`), and:
  - `saved`: `origin` is the `d` of the first `saved.put` after the text's newest `saved.del` (no delete: its first
    `saved.put`); `id` is that put's `<d>:<t[0]>:<t[1]>`; `updated_by` is the `d` of the winning put. A word saved
    again after a delete is a new entry, of the device that saved it again. `created` stays the winning put's `t[0]`.
  - `history`: `devices` are the `d` of the devices whose `history.seen` lines count, sorted.

### Compaction
A device may make its own files shorter, never other devices' files. Over all its lines (every file of its folder),
per `text` it keeps: its newest `saved.del` and every `saved.put` after it; its newest `history.del` and its newest
`history.seen` after it; per `(lang, section, ver)` its newest `ai.put`. Kept lines stay byte-equal and in the file of
their month; a changed month file is written whole (`<name>.tmp`, then renamed); a month left empty is deleted.
Merged with any other logs, the result is the same state, `origin` and `updated_by` too. The device's newest line is
always kept, so its clock and `last` stay valid.

### From v1
- v2 readers also read `<sync>/log/*.jsonl` (v1 lines only); v2 writers write only `inkpeek-log/`. A v1 reader does
  not see v2 logs. The v1 cases stay valid for v2 readers (their fields).

### Test cases
`tests/sync/v2/` (format and list: `tests/sync/README.md`): `reader/` (a folder → the merged state), `identity/` (a
folder and what the app keeps → keep / new / ask), `writer/` (steps at fixed clock values → the bytes of the device's
files). Every app runs all of them; a failing case is a finding about the app, never a reason to change the case.

## Sync log v1 (read only)
The previous format, written by LexiPeek before v2. v2 readers still read it from `<sync>/log/`; nothing new writes
it. What follows is v1 as it was.

### Files
- `<folder>/log/<device>.jsonl`. Only its own device writes a file, only appends. Others read every `*.jsonl`.
- `device`: made on first run, never changes: a short name and a random part (`mac-91c0`, `boox-3f2a`).
- UTF-8, one JSON object per line, every line ends with `\n`. A writer whose file does not end with `\n` (a crash
  mid-line) cuts it back to the last `\n` before appending.
- Compaction: a device may replace its own file (written whole, then renamed) with a shorter one that merges to the
  same state: per key its last `saved.*`, its last `history.del` and its last `history.seen` after it, its `ai.put`
  lines still kept. It never touches other devices' files.

### A line
```json
{"v":1,"t":[1761400000000,0],"d":"mac-91c0","op":"saved.put","text":"Haus","kind":"word","lang":"en","meaning":"house"}
```
- `v`: 1. `t`: `[ms, n]`, a hybrid logical clock. `d`: the device. `op` and its fields:

| op | fields | merge |
|---|---|---|
| `saved.put` | `text, kind, lang, meaning` | per `text` the newest `saved.*` wins; `created` = its `t.ms` |
| `saved.del` | `text` | the same rule: a newer delete removes the entry, a newer put brings it back |
| `history.seen` | `text, kind, lang, meaning, c`, optional `base` | see below |
| `history.del` | `text` | drops the entry's `history.seen` lines at or before it |
| `ai.put` | `text, lang, section, ver, model, answer` | union by `(text, lang, section, ver)`; the same key: the newest wins; `created` = its `t.ms` |

- Order of lines: by `t[0]`, then `t[1]`, then `d` (string order). "Newest" means last in this order; the order of
  lines in a file and of files does not matter. Equal lines (a line read twice) are one.
- `history.seen` of `text`, of the lines after its last `history.del`: `count` = Σ over devices of the device's
  greatest `c`; `seen` = `t.ms` of the newest; `kind`, `lang`, `meaning`, `base` from the newest (no `base`: NULL).
  None left: no entry.
- `c`: the device's count of looks of `text` since the last `history.del` of it the device knows. Each look writes
  one line; reading a line twice does not count it twice.
- `ai` keeps only answers whose `text` is in the merged `saved` or `history`.
- Clearing the history writes one `history.del` per entry. The 1,000-entry limit is applied by each app to its own
  store, not by the merge.
- Skipped: a line without the final `\n` (being written or cut), a line that is not a JSON object, `v` other than 1,
  an unknown `op`, a line without `t` (two integers), `d` or `text`. Unknown fields of a known op are ignored.

### The clock
`t = [ms, n]`, kept per device across runs (from the last line of its own file). For each new line, with `now` the
wall clock and `(l, n)` the last `t` written or read:
- a line of its own: `l' = max(l, now)`; `n' = n + 1` when `l' = l`, else 0;
- after reading other devices' logs: `l` and `n` become the greatest `t` read, when it is greater than the device's own.

So a device's `t` always grows, also when its wall clock goes back, and a change made after reading another
device's change is newer than it. `ms` is close to the wall time, not equal: use it for order and display only.

### Test cases (v1)
`tests/sync/<case>/`: the logs (`<device>.jsonl`) and `expected.json`, the merged state:
`{"saved": [{text, kind, lang, meaning, created}], "history": [{text, kind, lang, base, meaning, seen, count}],
"ai": [{text, lang, section, ver, model, answer, created}]}`. Compare as sets by key (the lists are sorted for reading).

| case | what it checks |
|---|---|
| `lww-save-delete` | the newest of put / delete wins, across devices; a put after a delete brings the word back; equal `t` → the greater `d`; a delete of a word never saved |
| `tombstone-stale-put` | a delete beats older puts that arrive later (a device that was offline); `history.del` drops older looks, a later look starts the count again |
| `history-count` | the same word looked up on two devices: counts summed per device, a repeated line counted once; the newest look's fields; a form with its `base` is its own entry |
| `ai-union` | answers of both devices together; the same key → the newest; another `ver` or `lang` is another answer; answers of a deleted history entry are dropped |
| `cut-last-line` | a broken line in the middle and a last line without `\n` (also one that is valid JSON) are skipped |
| `unknown-op` | an unknown `op`, `v: 2` and a line without `t` are skipped; extra fields of a known op are ignored |
| `clock-backwards` | the order is by `t`, not by wall time: a device whose clock went back keeps counting `n`; a change made after reading another log wins |

## Sentence breaks
Offline translation cuts a text into sentences: an OPUS-MT model with a target tag gets the tag before each one, Hy-MT2
gets pieces of whole sentences (`Engine.sentences`, `Llm.chunks`). InkPeek uses Android's
`BreakIterator.getSentenceInstance(Locale(<lang>))`: ICU's rules (Unicode UAX #29) **without abbreviation
suppressions**, so "Dr.", "z. B.", "J. R. R." and "3. Mai" end a sentence. Each piece is trimmed; a blank line gives
an empty piece.

Cases: `tests/sentences/sentences.tsv`, UTF-8, one case per line, tab-separated: `lang`, `text`, then the expected
sentences in order; `\n` in a field is a line break; `#` starts a comment line. Checked on a device by
`SentencesOnDeviceTest` (`scripts/device-test.sh`); a failing line is logged as the device gives it. The
expectations came from ICU 78 on a desktop and pass on Android 15; a device whose ICU disagrees is a finding, recorded here.
