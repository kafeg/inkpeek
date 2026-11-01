# Sync log cases

Shared by InkPeek and LexiPeek (through its `tests/inkpeek` submodule). The format: `docs/formats.md`, "Sync log".
Plain data (UTF-8, JSON, JSON lines) that any runner reads. Byte-exact: `.gitattributes` here keeps git from changing
line ends. A failing case is a finding about the app: never change a case to make one app pass.
Runners take every case folder they find (a new case needs no code) and every file in it as it is: names with spaces,
brackets and leading dots are part of the cases.

## `reader/<case>/`
The case folder is a `<sync>` folder: `inkpeek-log/<device>/`, and `expected.json` (not read as a log: it is in no
device folder). The reader merges the folder; the state is compared with `expected.json`:
```
{"saved":   [{text, kind, lang, meaning, created, id, origin, updated_by}],
 "history": [{text, kind, lang, base, meaning, seen, count, devices}],
 "ai":      [{text, lang, section, ver, model, answer, created}]}
```
As sets by key (`saved`, `history`: `text`; `ai`: `text, lang, section, ver`). `base`: `null` when none. `devices`:
a sorted list. The lists are sorted for reading.

| case | what it checks |
|---|---|
| `two-devices` | a phrase, a form with `base`, an answer with escapes, an answer of a text in neither list dropped |
| `lww-save-delete` | the newest of put / delete wins, across devices; a put after a delete brings the word back (a new `origin`); equal `t` → the greater `d`; a delete of a word never saved |
| `tombstone-stale-put` | a delete beats older puts that arrive later (a device that was offline); `history.del` drops older looks, a later look starts the count again |
| `history-count` | the same word looked up on two devices: counts summed per device, a repeated line counted once; the newest look's fields; a form with its `base` is its own entry |
| `ai-union` | answers of both devices together; the same key → the newest; another `ver` or `lang` is another answer; answers of a deleted history entry are dropped |
| `several-devices` | four devices: LWW across them, equal `t` → the greater `d` (a put and an answer), a `history.del` dropping older looks of every device and a stale offline look |
| `conflicted-copy` | the same lines in a month file and in two conflicted copies count once, a line only in a copy counts; `*.tmp` and names starting with `.` are not read |
| `cut-last-line` | a broken line, an empty line and `[1,2]` in the middle are skipped; a last line without `\n` is dropped, also when it is valid JSON |
| `clock-backwards` | the order is by `t`: a device whose clock went back counts `n` on; two different lines with the same `t` and `d` (a copy from a second install) → ordered by their bytes |
| `unknown-op-field` | an unknown op, a `t` of three numbers or with a fraction, no `t`, `d` or `text`: skipped; unknown fields: read; a `.jsonl` directly in `inkpeek-log/` and an unknown field of `device.json`: ignored |
| `month-boundary` | 23:59:59.999 and 00:00 UTC in two month files: one log; a delete in November beats a put in October; the count goes on |
| `compacted-month` | a device's October compacted (fewer lines) merges to the state of the full log, `origin` too |
| `origin-updated-by` | first and last device of a saved word across three devices; two devices saving before they see each other; an old put of a third device as the origin of later updates; a word saved and deleted elsewhere is gone |
| `save-delete-save` | saved, deleted on another device, saved on a third: the third is the origin; a stale put older than the delete is not; a delete and a put of one device; equal `t` of a delete and a put |

## `identity/<case>/`
A `<sync>` folder as above, `local.json` and `expected.json`. `local.json`: `{"app", "device", "last", "now"}`:
the app, its id (`null`: none), its stored last line (without `\n`; `null`: none) and the wall clock (ms). With an id
the app runs the check "Two installs with one id", without one "Continue as a device". `expected.json`:
`{"decision": "keep"}`, `{"decision": "new"}` or `{"decision": "ask", "offer": [ids, in the order shown]}`.

| case | what it checks |
|---|---|
| `keep-same-last-line` | the newest own line equals `last` (an older different line in a conflicted copy does not matter) |
| `keep-cut-last-line` | a cut last line does not count; a line of another device in the folder does not count |
| `keep-no-own-lines` | the folder has no lines of the id (a new folder) |
| `new-newer-line-in-folder` | the folder has an own line newer than `last` (the other install wrote) |
| `new-no-local-line` | own lines in the folder, nothing stored |
| `new-two-last-lines` | two different own lines with the newest `t`, one of them `last` |
| `ask-continue` | offered: the same app, last line 24 h ago exactly, 3 days ago, no lines and `created` 30 days ago, newest first; not offered: active within 24 h (by one ms), another app, `device.json` naming another id, no `device.json`; another device's line in a folder does not count as its activity |
| `new-nothing-to-continue` | the same folder for the other app at another time: its only log is active → a new device |

## `writer/<case>/`
`scenario.json`: `{"device", "app", "name", "created", "steps": [...]}`. A step: `now` (the wall clock, ms) and a
line as the app makes it: `op` and its fields (`history.seen`: `base` only when there is one), without `t` and `c`:
the writer makes those. `before/` (optional): the `<sync>` folder at start, read first (the clock, counts, a cut
last line). `after/inkpeek-log/<device>/`: every file of the device's folder after the steps, byte for byte, and no
other files there; nothing else in the folder changes. Without `before/` the folder is empty and the device new
(`device.json` from the scenario). A writer that cannot append (SAF) gives the same bytes.

| case | what it checks |
|---|---|
| `new-device` | `device.json`; save, look, answer, look of a form, a look with the clock back a minute (into November by `t`, not by wall time), clear, look again (`c` from 1), delete, two saves at one `now` (`n` 0, 1); October and November files; escapes (`"`, `\`, `\n`, `\t`), non-ASCII as is |
| `resume` | a device with logs: its last line cut is dropped from the file before appending, its clock jumps to a newer line of another device, another device's `history.del` starts `c` again; past month and `device.json` unchanged |
