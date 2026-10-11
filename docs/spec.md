# InkPeek — spec

A small pop-up dictionary for Android e-ink readers. Select a word in a reading app: a clean card opens over the
page with local dictionary entries and, on request, AI help (translation in context, grammar, explanation).
Close the card and you are back on the same page.

## Goals
- One calm card over the page, centered, black on white, large text, no animations. Built for e-ink.
- Works with any reader that calls an external dictionary through the common ColorDict intent, through the system
  text-selection menu, or through "Share".
- Local StarDict dictionaries first, instant. AI only when it helps.
- Small, readable code; well-known libraries where they save work.

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

## Card
Tabs at the bottom: **Dictionary** first, then one tab per enabled AI action. A tab keeps its content; an AI answer
is asked once and shown again when you come back. AI off → only the dictionary and a hint.
A phrase (has a space) opens on the first AI tab (setting `phrase_auto_ai`, default on).
Dictionary tab: one dictionary per screen, ◀ ▶ between dictionaries with a hit. A dictionary that resolves a form
(Häuser → Haus) makes the base form be looked up in the other dictionaries.

## Card layout
1. Header: the query (large), close button.
2. Body (scrolls): dictionary blocks in the configured order, each with a small dictionary name; only dictionaries
   with a hit. AI answer block below, when present.
3. Footer: the tabs (labels auto-size to fit).
- Size: 92% of screen width, 80% of height, centered (setting: center / top / bottom / follow the reader's
  `EXTRA_GRAVITY`).
- Font size: one setting (sp) for the body; header +30%.
- No colors, no shadows, no ripple. Thin black borders. Light dim behind the card (setting, default on).

## Dictionaries
- Format: StarDict 3 — `.ifo`, `.idx` (or `.idx.gz`), `.dict` or `.dict.dz` (dictzip random access), optional `.syn`.
  `sametypesequence` `m`, `t`, `x`, `h`, `g` → plain text or simple HTML.
- Library: one folder, `/sdcard/dictdata` (created when missing; the ColorDict folder). Each dictionary in its own
  subfolder. Needs `MANAGE_EXTERNAL_STORAGE` (the app is sideloaded).
- Import: a file picker takes `.zip`, `.tar`, `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.zst` or loose StarDict files.
- Get dictionaries: live lists of Wiktionary (xxyzz/wiktionary_stardict releases, `.tar.zst`) and FreeDict
  (`freedict-database.json`, `stardict` releases); filter by language pair, tap to download and import.
- Own index per dictionary, built once (app files): every headword and `.syn` form, normalized (NFC, lower case, no
  soft hyphen), sorted, binary search over a memory-mapped file. Upstream `.idx` sort order does not matter.
  `.dict.dz` is unpacked once into the app's files.
- Query normalization: trim, strip surrounding punctuation and quotes, collapse spaces; soft hyphen removed.
- Rendering: HTML through `Html.fromHtml` into a `TextView` (no WebView, no JS); images ignored.
- Dictionary list in settings: enable/disable, order.

## AI
- Providers: Claude (Anthropic Java SDK), OpenAI and OpenRouter (OpenAI-compatible `/chat/completions`, `/models`).
  Non-streaming (one e-ink refresh per answer); `<think>` blocks stripped; error shown in the card.
- Key per provider in settings; Claude can also read a key file (default `/sdcard/koreader/settings/anthropic.key`,
  shared with KOReader plugins). Never logged, never shown in full.
- Model per provider: free text or "Choose model…" from the provider's model list. Claude default `claude-opus-5-5`
  at effort low; refusal fallback (`fallbacks: "default"`) on models that support it.
- Answer language: setting, default Russian.
- Actions (tabs; on/off in settings): Translate, Grammar (how the phrase is built and why), Words (harder words in
  base form), Examples, Explain (off by default). Prompts: `ai/Ai.kt`, placeholders `{text}`, `{lang}`.
- System prompt: answer only in `{lang}`; a text already in `{lang}` gets an explanatory-dictionary answer; light
  Markdown only (**bold**, *italic*, "- " lists) rendered in the card; ≤ 120 words unless needed.
- OpenRouter: `reasoning: {enabled: false}` (retry without it on HTTP 400); default model `openrouter/free`.
- Cache: last 100 answers in memory per (action, text).
- Data sent: only the selected text and the action prompt, only on a tap (or phrase auto mode). No telemetry.

## Settings screen
Tabs: Dictionaries (list with status, enable, order, delete; import; get) · AI (on/off, phrase mode, provider, key and
its source, model picker with filter / maker / free only / sort, answer language) · Card (app language, text size,
dim, position, AI tabs with descriptions). UI: English, Russian, German; system language by default.

## Tech
- Kotlin, Android Views (no Compose), minSdk 26, targetSdk 35. Libraries: anthropic-java, OkHttp, kotlinx-coroutines,
  commons-compress + xz + zstd-jni (archives).
- Packages: `stardict` (pure JVM: ifo/idx/syn/dict/dictzip readers, lookup), `ai` (Claude client, actions),
  `ui` (card activity, settings).
- Tests: JVM unit tests for `stardict` with a tiny generated dictionary.
- Build: Gradle wrapper; `./gradlew assembleDebug`; release signed with a local keystore (not in git).
- License: MIT.

## Milestones
1. Card + intents + StarDict lookup (no AI). Install on a reader, check with FBReader / Cool Reader.
2. AI actions + phrase auto mode + settings.
3. Polish: dictionary ordering UI, action editor, release build + GitHub release.
