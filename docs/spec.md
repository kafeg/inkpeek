# InkPeek — spec

A small pop-up dictionary for Android e-ink readers. Select a word in a reading app: a clean card opens over the
page with local dictionary entries and, on request, AI help (translation in context, grammar, explanation).
Close the card and you are back on the same page.

## Goals
- One calm card over the page, centered, black on white, large text, no animations. Built for e-ink.
- Works with any reader that calls an external dictionary through the common ColorDict intent, through the system
  text-selection menu, or through "Share".
- Local StarDict dictionaries first, instant. AI only when it helps.
- Small, dependency-free, readable code.

## Non-goals (v1)
- Flashcards / Anki export, history sync, dictionary downloads, online dictionaries.
- A full dictionary app (search screen, browsing, bookmarks).

## Invocation
| entry | how readers send it | text |
|---|---|---|
| `colordict.intent.action.SEARCH` | external dictionary item "ColorDict" in the reader | extra `EXTRA_QUERY` |
| `android.intent.action.PROCESS_TEXT` | system text-selection menu | extra `EXTRA_PROCESS_TEXT` |
| `android.intent.action.SEND` (`text/plain`) | "Share" | extra `EXTRA_TEXT` |

- ColorDict extras: `EXTRA_HEIGHT`, `EXTRA_GRAVITY` are hints only. `EXTRA_FULLSCREEN` is ignored: some readers
  send `true` when their status bar is hidden; InkPeek always opens the card.
- The activity is a dialog over the caller's task (`taskAffinity=""`, `excludeFromRecents`, no history). Back or a
  tap outside closes it.

## Modes
| selection | first view | AI |
|---|---|---|
| one word | dictionary entries | buttons |
| phrase / sentence (has a space) | AI translation (setting `phrase_auto_ai`, default on) | buttons |

A word is found in no dictionary → show "not found" and the AI buttons.

## Card layout
1. Header: the query (large), close button.
2. Body (scrolls): dictionary blocks in the configured order, each with a small dictionary name; only dictionaries
   with a hit. AI answer block below, when present.
3. Footer: AI action buttons (one row, wraps): Translate, Grammar, Explain, Examples. Configurable list.
- Size: 90% of screen width, up to 75% of height, centered (setting: center / top / bottom / follow the reader's
  `EXTRA_GRAVITY`).
- Font size: one setting (sp) for the body; header +30%.
- No colors, no shadows, no ripple. Thin black borders. Light dim behind the card (setting, default on).

## Dictionaries
- Format: StarDict 3 — `.ifo`, `.idx` (or `.idx.gz`), `.dict` or `.dict.dz` (dictzip random access), optional `.syn`.
  `sametypesequence` `m`, `t`, `x`, `h`, `g` → plain text or simple HTML.
- Folder: setting, default `/sdcard/dictdata` (shared with ColorDict / GoldenDict). Each subfolder = a dictionary.
  Needs `MANAGE_EXTERNAL_STORAGE` (the app is sideloaded).
- Lookup order per dictionary: exact → lower case → `.syn` synonyms/forms. Index: binary search over the sorted
  `.idx` (StarDict order: case-insensitive ASCII, then byte order); loaded on first use, kept in memory.
- Query normalization: trim, strip surrounding punctuation and quotes, collapse spaces; soft hyphen removed.
- Rendering: HTML through `Html.fromHtml` into a `TextView` (no WebView, no JS); images ignored.
- Dictionary list in settings: enable/disable, order.

## AI (Claude)
- Anthropic Messages API, HTTPS, non-streaming (one e-ink refresh per answer). Timeout 30 s; error shown in the card.
- Key: from a file (setting, default `/sdcard/koreader/settings/anthropic.key`, shared with KOReader plugins) or
  typed in settings. Never logged, never shown in full.
- Model: setting, default `claude-haiku-5-5` (fast); `claude-sonnet-5-5` selectable.
- Answer language: setting, default Russian.
- Actions: name + prompt template. Placeholders: `{text}` selection, `{lang}` answer language. Defaults:
  - Translate — translate `{text}` into `{lang}`; for a single word: main senses, part of speech, gender/plural for
    nouns, base form if it is an inflected form.
  - Grammar — explain the grammar of `{text}` in `{lang}`, briefly.
  - Explain — explain meaning and usage of `{text}` in `{lang}`; idioms and register.
  - Examples — 3 short example sentences with translation into `{lang}`.
- Short answers: system prompt asks for plain text, no Markdown tables, ≤ 120 words unless asked.
- Cache: last 100 answers in memory per (action, text).
- Data sent: only the selected text and the action prompt, only on a tap (or phrase auto mode). No telemetry.

## Settings screen
Font size · dictionary folder + list (enable, order) · position · dim · phrase auto AI · AI on/off · key source ·
model · answer language · actions (edit name/prompt, add, remove, order).

## Tech
- Kotlin, Android Views (no Compose), minSdk 26, targetSdk 35. No third-party libraries:
  `HttpURLConnection`, `org.json`, `java.util.zip`.
- Packages: `stardict` (pure JVM: ifo/idx/syn/dict/dictzip readers, lookup), `ai` (Claude client, actions),
  `ui` (card activity, settings).
- Tests: JVM unit tests for `stardict` with a tiny generated dictionary.
- Build: Gradle wrapper; `./gradlew assembleDebug`; release signed with a local keystore (not in git).
- License: MIT.

## Milestones
1. Card + intents + StarDict lookup (no AI). Install on a reader, check with FBReader / Cool Reader.
2. AI actions + phrase auto mode + settings.
3. Polish: dictionary ordering UI, action editor, release build + GitHub release.
