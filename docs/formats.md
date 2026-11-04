# Data formats

The formats InkPeek reads and writes that other apps share: the language packs list, the records (saved words,
history, answers), the sync log and the Anki notes; how a selection becomes a card; and the sentence breaks of offline translation. InkPeek is the reference; [LexiPeek](https://github.com/kafeg/lexipeek) (macOS)
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

### Answer versions
`ver` is a Java `String.hashCode()` (a 32-bit int), so an answer saved by one app opens in the other:
- AI: of the request's original prompt (`Ask.prompt()`: the default abouts, the picked keys, without the system prompt);
  with a language you learn, of the prompt + the study paragraph with its English name (`Ai.study`).
- Offline: of the sha256s of the models it took, in order, joined by `", "` (`OfflineTranslation.version`): Mozilla's
  steps (`model-<a>-<b>`, two through English), else the one OPUS-MT or Hy-MT2 component.

Cases: `tests/versions/versions.json` (`ask` + `study` or `offline` → `ver`): every app runs all of them. A changed
prompt changes its numbers: the case changes in the same commit, and the other app follows.

## Sync log
The one format both apps write and read. No servers: the logs live in a folder
that a sync tool (Syncthing, a cloud drive, a NAS share) copies between devices; the user picks it in each app
(`<sync>`). One folder serves InkPeek and LexiPeek together, any number of installs of each. Synced: saved words and
phrases, the history, the answers (`ai`). Not synced: settings, packs, models, voices. The records (above) are built
from the logs; each app keeps them in its own store, with `id`, `origin`, `updated_by` and `devices` (below).

### Files
```
<sync>/inkpeek-log/
  <device>/device.json      the device: id, app, name, when it was added
  <device>/2026-10.jsonl    the device's lines whose t[0] is in that month (UTC)
  <device>/2026-11.jsonl
```
- Only the device `<device>` writes in `inkpeek-log/<device>/`, and only appends (compaction aside). Every device reads
  every device folder, its own too.
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
- An app without an id (first start, its data deleted, Android after an uninstall) makes a new one. The logs of its
  earlier id are read as every other device's: nothing is lost, and no question is asked.

### `device.json`
```json
{"device":"mac-k3fw2xqa","app":"lexipeek","name":"MacBook Air","created":1793145600000}
```
- `device`: the id, the same as the folder name. `app`: `inkpeek` or `lexipeek`. `name`: the name people see (by
  default the device model or the computer's name; changed in the app's settings). `created`: when the device was
  added (the id was made), ms since the epoch; apps show it in local time.
- Written whole (canonical form, below, and `\n`) when the device first writes to the folder or finds the file
  missing, and when its name changes. A new name makes no new id and no new log.
- Readers ignore unknown fields. Apps show `name` for a device's lines (the device of a saved word); without the file:
  the id.

### A line
```json
{"t":[1793491198000,0],"d":"mac-k3fw2xqa","op":"saved.put","text":"Haus","kind":"word","lang":"ru","meaning":"дом"}
```
- `t`: `[ms, n]`, the clock (below). `d`: the device that wrote it. `op` and its fields:

| op | fields |
|---|---|
| `saved.put` | `text, kind, lang, meaning` |
| `saved.del` | `text` |
| `history.seen` | `text, kind, lang, meaning, base, c` (`base` only when there is one) |
| `history.del` | `text` |
| `ai.put` | `text, lang, section, ver, model, answer` |

- `c` of `history.seen`: the device's count of looks of `text` since the newest `history.del` of it that the device
  knows (every device's), this look included. Each look writes one line; reading a line twice does not count it twice.
- Clearing the history writes one `history.del` per entry. The 1,000-entry limit is applied by each app to its own
  store, not by the merge.
- Skipped: a line without the final `\n` (being written or cut; also when it is valid JSON), a line that is not valid
  UTF-8, a line that is not one JSON object (JSON's whitespace — space, `\t`, `\r` — around it is allowed: a CRLF file
  reads; a BOM or anything else before or after it is not), an unknown `op`, a line without `t` (two integers), `d` or
  `text` (strings). A missing field of a known op, or one of another type (a number with a fraction or exponent is not
  an integer: `"c":2.5`, `"ver":3.0`), reads as `""` (`ver`, `c`: 0; `base`: none). Unknown fields are ignored: a new
  op or field needs no other change. Field order and escapes do not matter for reading (the bytes still make the line).
- The canonical form, so that both apps write the same bytes: compact JSON (no spaces); keys `t, d, op`, then the
  op's fields in the order of the table. Strings: UTF-8 as is; escaped are only `"` (`\"`), `\` (`\\`),
  `\b \f \n \r \t` and the other characters below U+0020 (`\u001f`, lower-case hex); `/` and non-ASCII are not
  escaped. Integers in decimal.

### The clock
`t = [ms, n]`, a hybrid logical clock, kept per device across runs. For each new line, with `now` the wall clock and
`(l, n)` the greatest `t` written or read (every line read, every device, its own too):
`l' = max(l, now)`; `n' = n + 1` when `l' = l`, else 0. So a device's `t` always grows, also when its wall clock goes
back, and a change made after reading another device's change is newer than it. `ms` is close to the wall time, not
equal: use it for order and display only.

### Writing
- A line goes to the month file of its `t[0]` in UTC: `YYYY-MM.jsonl` (`1793491199999` → `2026-10.jsonl`,
  `1793491200000` → `2026-11.jsonl`). The clock never goes back, so a device writes only to its newest month; past
  months never change, except by the device's own compaction.
- Appending: a file that does not end with `\n` (a crash mid-line) is cut back to its last `\n` first. Where a file
  cannot be appended to (Android SAF providers without `"wa"`), the current month file is written whole: its complete
  lines as they are in the folder now and the new line: the same bytes as appending.
- After a line is written the app stores it as `last`. A crash in between can only cause a needless new id (below).

### The merge
- The lines: every device folder, as above. Byte-equal lines are one (a line read twice: conflicted copies, the same
  file twice).
- Order: `t[0]`, then `t[1]`, then `d`, then the line's bytes (only two installs with one id write two lines with the
  same `t` and `d`); strings compare by their UTF-8 bytes (code points, not UTF-16 units: `😀` after `ｶ`). "Newest" means last in this order; the order of lines in a file and of files does
  not matter.
- `saved`, per `text`: the newest `saved.*` wins: a put keeps the entry (`kind`, `lang`, `meaning`, `created` = its
  `t[0]`), a delete removes it, a newer put brings it back. `origin` is the `d` of the first `saved.put` after the
  text's newest `saved.del` (no delete: its first `saved.put`); `id` is that put's `<d>:<t[0]>:<t[1]>`; `updated_by`
  is the `d` of the winning put. A word saved again after a delete is a new entry, of the device that saved it again.
- `history`, per `text`, of the `history.seen` lines after its newest `history.del` (a delete drops the looks at or
  before it): `count` = Σ over devices of the device's greatest `c`; `seen` = `t[0]` of the newest; `kind`, `lang`,
  `meaning`, `base` from the newest (no `base`: NULL); `devices` = the `d` of the devices whose lines count, sorted.
  None left: no entry.
- `ai`: union by `(text, lang, section, ver)`; the same key: the newest wins; `created` = its `t[0]`. Only answers
  whose `text` is in the merged `saved` or `history`.

### Compaction
A device may make its own files shorter, never other devices' files. Over all its lines (every file of its folder),
per `text` it keeps: its newest `saved.del` and every `saved.put` after it; its newest `history.del` and its newest
`history.seen` after it; per `(lang, section, ver)` its newest `ai.put`. Kept lines stay byte-equal and in the file of
their month; a changed month file is written whole (`<name>.tmp`, then renamed); a month left empty is deleted.
Merged with any other logs, the result is the same state, `id`, `origin` and `updated_by` too. The device's newest
line is always kept, so its clock and `last` stay valid.

### Two installs with one id
Copied app data, a restored backup or a cloned machine give two installs one id: two writers of one folder. Each
install checks at start and when its sync folder is chosen, before it writes:
- `own`: the complete, valid lines with `d` = its id in `inkpeek-log/<id>/` (every file read, as above).
- `own` is empty: **keep** the id (nothing written there yet, or another folder).
- Else the lines of `own` with the greatest `t`: all of them byte-equal to `last` → **keep**; else (no `last`, an
  older one, or another line with the same `t`) → **new** id: a new device (new id, `created` = now, the same
  `name`); the clock goes on. In a race both installs may take new ids: that costs a folder, no data.

### Test cases
`tests/sync/` (format and list: `tests/sync/README.md`): `reader/` (a folder → the merged state), `identity/` (a
folder and what the app keeps → keep / new), `writer/` (steps at fixed clock values → the bytes of the device's
files), `generated/` (reader cases made by `tests/sync/generate.py`: many devices, ops and languages). Every app runs
all of them; a failing case is a finding about the app, never a reason to change the case.

## Anki notes
Both apps send the saved words, phrases and (when on) the history to Anki by themselves: LexiPeek through AnkiConnect,
InkPeek through AnkiDroid. One note type `Peek`, fields `Front, Back, Audio, Example, Key` (`Key` in no template), one
note per entry by its Key. AnkiWeb merges notes by their GUID, which neither app can set: the same entry sent by both
apps must give the same fields, or each app rewrites the other's on every sync.
- `Key`: `h-` for a history note, then the entry's `lang` (the answer language when it has none: old rows) in lower
  case, `-`, the first 32 hex digits of sha256 (UTF-8) of the text's lookup key: NFC, soft hyphens (U+00AD) out,
  whitespace trimmed at the edges (Kotlin's `isWhitespace`: Unicode's space separators, no-break ones too, line and
  paragraph separators, TAB LF VT FF CR, U+001C–U+001F), lower case by Unicode's full mapping (Java's
  `toLowerCase(Locale.ROOT)`: `İ` → `i̇`, `Σ` at a word's end → `ς`). `ru-1a21f58f9570039d5e23f3619c8e9749` is `Haus`.
- `Front`: the text as it is, escaped (`&` `<` `>` → `&amp;` `&lt;` `&gt;`, `\n` → `<br>`).
- `Back`: the entry's `meaning`; when it is blank (only whitespace, as above), the brief line of its newest quick AI
  answer (`ai`: section `quick`, `lang` the English name of the entry's language, the greatest `created`). Cut at its
  first ` — `: `<div class="head">` + the part before + `</div>` + the part after, each escaped as `Front`; without
  ` — `: the line escaped. The brief line of an answer: a JSON object (else `""`); its `translation` (a string) when not
  blank, else `article` and `base` (strings, the non-blank ones, joined by a space) and the strings of `meanings`
  (joined by `, `), the non-blank of these two joined by ` — `; its first 140 characters (code points).
- `Example`: `""`. `Audio`: LexiPeek's reading (`[sound:lexipeek_<Key>.wav]`); a note updated without a reading keeps
  its Audio, and InkPeek never changes it. Tags: `inkpeek` or `lexipeek` on the notes an app adds; never read.
- Duplicates (both apps added a note before AnkiWeb synced): among the `Peek` notes, per Key (not empty), the smallest
  id stays; when its Audio is empty it takes the first non-empty Audio of the others by id; the others are deleted.
  Each app checks every 10 minutes and on "Send now" (InkPeek every Key, LexiPeek the Keys of its own entries).

Cases: `tests/anki/` (`keys.json`, `fields.json`, `dedupe.json`; made by `tests/anki/generate.py`, see its README):
every app runs all of them.

## Selection and lookup
How a selected text becomes a card: what it opens as (a word or a phrase), what the card shows, what offline
translation and AI get, and the text looked up and stored (`history`, `saved` and `ai` key on it). Both apps give the
same card for the same text and dictionaries.
- Spaces: NFC, soft hyphens (U+00AD) out, each run of whitespace (Java's `\s`: SPACE, TAB, LF, VT, FF, CR) one space.
  Kind: whitespace-separated words, as `Kind.of`: one → a word; up to 8 and one sentence → a phrase; else a passage.
- **A word**: its edges lose punctuation, symbols and spaces (`\p{P}`, `\p{S}`, `\s`). A pair around the whole goes
  together (`"привет"` → `привет`, `“you—resentful,”` → `you—resentful`). An opening quote or bracket whose closing one
  is inside stays, and so does a closing one whose opening one is inside: `(s)he` stays `(s)he`. Pairs: `()` `[]` `{}`
  `（）` `［］` `«»` `»«` `‹›` `›‹` `“”` `„“` `„”` `‚‘` `‚’` `‘’` `""` `''` `「」` `『』` `《》` `〈〉`. The title, the
  translation's and AI's text and the lookup are this word.
- **A phrase or passage**: the title, offline translation and AI get the selection as selected, spaces as above and
  trimmed (`"привет" - сказала она`). The lookup, the word-by-word list and the stored text are the selection without
  stray edge punctuation: as above, but `¿` `¡` at the start and `.` `!` `?` `…` `。` `！` `？` at the end stay, and
  pairs are not looked for (`привет" - сказала она`, as stored so far).
- **A word with a dash or hyphen between letters** (`\p{Pd}`: `-` U+2010–U+2015, U+2E3A, U+2E3B…) that no dictionary
  knows, in this order:
  1. the word itself, exactly: a real entry (`well-known`, `mother-in-law`, `E-Mail`) opens as a word;
  2. the same word with each run of dashes as one space (`half asleep`), when a dictionary has that headword: a word
     card of it (title and stored text `half asleep`);
  3. else a phrase: its words one by one, as `you — resentful` (title as selected, stored text the word).
  While a dictionary is still being indexed the word stays a word ("preparing"): a card never changes its kind later.
- **Word by word** (phrases, step 3): words are `\p{L}[\p{L}'’-]*`, so any dash but `-` separates them. A word with
  `-` that no dictionary knows gives its parts in its place (`half-asleep` → `half`, `asleep`); a known one stays whole.
  A part already in the list is not repeated.

Cases: `tests/lookup/selection.tsv` (title, query, kind; no dictionary needed) and `tests/lookup/dashes.tsv` (with
a dictionary). UTF-8, one case a line, `#` a comment, tab-separated:
`known` (the headwords of one dictionary, `|`-separated; each article `<p class="head">word — x</p>`), `text` (the
selection), `kind` (`word` / `phrase` / `passage`), `title` (also what translation and AI get), `query` (the text
looked up and stored), `words` (a word: the headwords shown; a phrase: the word list's words in order; `?` after a word
no dictionary knows; `|`-separated, empty for none). Every app runs all of them.

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
