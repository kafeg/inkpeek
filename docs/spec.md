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
- Flashcards / spaced repetition, history sync, online dictionaries.
- A full dictionary app (search screen, browsing). Saved words and history are lists, not a trainer.

## Invocation
| entry | how readers send it | text |
|---|---|---|
| `colordict.intent.action.SEARCH` | external dictionary item "ColorDict" in the reader | extra `EXTRA_QUERY` |
| `android.intent.action.PROCESS_TEXT` | system text-selection menu | extra `EXTRA_PROCESS_TEXT` |
| `android.intent.action.SEND` (`text/plain`) | "Share" | extra `EXTRA_TEXT` |

- ColorDict extras `EXTRA_HEIGHT`, `EXTRA_GRAVITY` and `EXTRA_FULLSCREEN` are ignored (`EXTRA_FULLSCREEN`: some readers
  send `true` when their status bar is hidden; InkPeek always opens the card).
- The activity is a dialog over the caller's task (`taskAffinity=""`, `excludeFromRecents`, no history). Back or a
  tap outside closes it.

## Card
Core works offline without AI; AI tabs are optional extras. Tabs: **Dictionary** (core), then the AI tabs
**AI translation**, **Analysis**, **Context** — "AI" in the icon's corner, dimmed and a reason on tap when AI is off,
has no key, or there is no network (a saved answer still opens). AI off in settings → no AI tabs. A tab keeps its
content. Selection kinds: word · phrase (2–8 words, one sentence) · passage (more); the model is told the kind.
The card opens on Dictionary; a phrase opens on AI translation only with `phrase_auto_ai` (default off) and AI able
to answer. No AI request on card open.
Header of a word from local data only: the article from WikDict's grammar mark ("das Gewitter"); the grammar pack
(milestone 10) adds plural and verb forms.
Dictionary tab for a word: one short line per dictionary with a hit — "form → **base** *part of speech* — first
meanings · dictionary"; tap a line for the full article, "←" back. Order: meanings in the answer script, then
dictionaries whose headword is the AI's base form (German "ging" → gehen before an English look-alike), then the
settings order; the list re-sorts once when the quick answer arrives. The AI's base form is also looked up
(exact case), so "went" finds "go"; its part of speech picks the entry (WikDict "go" verb, not noun). A dictionary
that resolves a form (Häuser → Haus) makes the base form be looked up in the other dictionaries.

## Card layout
1. The card (border): header — the selection or, for a word, its base form with article / plural or verb forms (large),
   a small line under it; "←" at the right while a word's article is open from the gloss. Body (scrolls). A hint line at
   the bottom when AI is off or there is no network.
2. The icon row at the bottom of the card, under a thin line (one frame, no dead space around it): Dictionary,
   Translate, Analysis, Context · ☆ Save (★ when saved), My words · ✕ Close at the right. Icons ≤ 44 dp; the open tab
   has a slightly heavier border (4 vs 2); AI icons carry "AI" in their top-right corner, drawn smaller to leave
   room for it. Long press shows the name.
- My words in the card: Words / Phrases with counts; tap opens the entry in the card, ✕ removes. No history here.
- Size: 92% of screen width, 80% of height, centered. Body text 20 sp, header +30%. No settings for these: fewer
  options (decided 21.10).
- No colors, no shadows, no ripple. Thin black borders. Light dim behind the card.

## Dictionaries
- Format: StarDict 3 — `.ifo`, `.idx` (or `.idx.gz`), `.dict` or `.dict.dz` (dictzip random access), optional `.syn`.
  `sametypesequence` `m`, `t`, `x`, `h`, `g` → plain text or simple HTML.
- Library: the pack dictionaries only, `/sdcard/InkPeek/packs/dicts/<id>/` (Languages tab, milestone 12); no
  import, no third-party lists. Needs `MANAGE_EXTERNAL_STORAGE` (the app is sideloaded).
- Order in the card: dictionaries into the app's language first.
- Short line of an article: WikDict — the `<div>` translations of the first (or the AI's) part-of-speech block and
  its grammar mark ("noun, neutral"); Wiktionary — the first sense of the first `<ol>`; plain text (Mueller) — the
  first line without `[transcription]`, `_v.`, `1>` marks; always up to the first ";".
- Own index per dictionary, built once (app files): every headword and `.syn` form, normalized (NFC, lower case, no
  soft hyphen), sorted, binary search over a memory-mapped file. Upstream `.idx` sort order does not matter.
  `.dict.dz` is unpacked once into the app's files.
- Query normalization: trim, strip surrounding punctuation and quotes, collapse spaces; soft hyphen removed.
  The card and the AI get a phrase with its sentence end (`?!.…`) kept and stray edge quotes / brackets removed.
- Rendering: HTML through `Html.fromHtml` into a `TextView` (no WebView, no JS); images ignored.

## AI
- Providers: Claude (Anthropic Java SDK), OpenAI and OpenRouter (OpenAI-compatible `/chat/completions`, `/models`).
  Non-streaming (one e-ink refresh per answer); `<think>` blocks stripped; error shown in the card.
- Key per provider in settings; Claude can also read a key file (default `/sdcard/koreader/settings/anthropic.key`,
  shared with KOReader plugins). Never logged, never shown in full.
- Model per provider: free text or "Choose model…" from the provider's model list. Claude default `claude-sonnet-5-5` (Haiku 5.5 was faster but got grammar wrong).
- Least reasoning each model allows. Claude: effort low; thinking `disabled`, except Sonnet 5.5 (`between_tools`)
  and Opus 5.5 / Fable (cannot be off); a 400 retries without these and is remembered. Refusal fallback
  (`fallbacks: "default"`) on models that support it. One client per key, 45 s timeout, one retry.
- OpenAI-compatible: a "no reasoning" field by host — OpenRouter `reasoning.effort: none`, OpenAI / Groq / Mistral /
  local servers `reasoning_effort: none` (OpenAI then `low`), Gemini `none` then `minimal`, DeepSeek
  `thinking.disabled`, DashScope `enable_thinking: false`; on HTTP 400/422 the next variant, then none; the working
  one is remembered per (host, model) in settings.
- Network: "online" = a validated network; a VPN counts only with a real network under it. Offline → no request.
- Errors: no network; server unreachable (internet or VPN); 403 / "location not supported" → a neutral "the service
  refused: check the network or VPN and the key"; 401 → key; 429; credits. A phrase or passage shows the offline gloss under the error.
- Answer language: the app's language (en, ru, de; system language by default, English otherwise). No separate setting.
- Two requests, each answered as one JSON object (Claude: structured outputs with a JSON Schema; OpenAI-compatible:
  `response_format: json_object`, then without it on 400/422; the reply is parsed from the first `{` to the last `}`).
  Prompts: `Ask` in `ai/Ai.kt`, placeholders `{text}`, `{lang}`.
  - **Quick**, on opening AI translation: `base`, `article`, `plural`, `pos` (gender of the original word), `forms`,
    `irregular`, `form_of`, `meanings`, `translation` (keeps the sentence type). Shown only in the AI translation tab.
  - **Detail**, on the first open of Analysis or Context, in parts; the app writes the headings (localized, the same
    every time): `usage` (word: how to use it; phrase: how it is built and why; passage: constructions), `words`
    (word: synonyms and related; else the harder words, "**base** — meaning"), `examples` (3 × {text, translation};
    none for a passage), `notes` (register and nuances; passage: what it says). Analysis = usage + words, Context =
    examples + notes; an empty part is not shown. Fills both tabs.
- System prompt: answer only in `{lang}`; a text already in `{lang}` gets an explanatory-dictionary answer; light
  Markdown only (**bold**, *italic*, "- " lists) rendered in the card; brief; the model sees only the selection and
  must not guess its sentence.
- OpenRouter default model `openrouter/free`.
- Data sent: only the selected text and the prompt, only when an AI tab is opened. No telemetry.

## Storage
- One SQLite file `/sdcard/InkPeek/inkpeek.db` (app files when all-files access is missing); survives a reinstall
  (debug ↔ release needs one). No WAL (shared storage), no UPSERT (Android 8–9 SQLite).
- `ai`: answers by (text, answer language, request quick / detail, prompt hash) as JSON, and the offline translation
  (`offline-<from>`); shown again without a request, also offline. An answer lives as long as its text is in the
  history or saved: removing it there (✕, clear history, the 1000 limit) removes its answers. No separate setting.
- `saved`: words and phrases (a passage counts as a phrase) saved with ☆ in the card header (★ = saved, tap removes),
  with one short meaning: the cached translation, else the first dictionary meaning; empty → the cached translation
  at display time.
- `history`: every card opening, last 1000, with one short line: a word — the pack dictionary's first line; a phrase —
  its offline translation (started when the card opens); else the cached AI translation, else "—". Only in the app.
- Settings → My words: Words / Phrases / History as second-level tabs (text, the open one underlined), rows of the
  text and one line (cut with "…"); tap opens the card, ✕ removes; "Clear history" under the list.

## Settings screen
Tabs: Languages (pack pairs, milestone 12) · AI (on/off, phrase mode, provider, key and its source, model picker with
filter / maker / free only / sort, AI or offline first in Translate) · Card
(app language, what each icon does) · My words. UI exactly in the answer languages of the packs (en, ru, de); system language
by default. The app language is also the answer language.

## Tech
- Kotlin, Android Views (no Compose), minSdk 30, targetSdk 35, arm64. Libraries: anthropic-java, OkHttp,
  kotlinx-coroutines, commons-compress + zstd-jni (pack archives). Native: Bergamot, fastText.
- Packages: `stardict` (pure JVM: ifo/idx/syn/dict/dictzip readers, lookup), `ai` (Claude client, actions),
  `ui` (card activity, settings).
- Tests: JVM unit tests for `stardict` with a tiny generated dictionary.
- Build: Gradle wrapper; `./gradlew assembleDebug`; release signed with a local keystore (not in git).
- License: MIT.

## Milestones
v0.1–v0.2 (done): card + intents + StarDict; AI actions + settings; polish, 18 UI languages, releases.

### v0.3 (plan; each milestone: spec → code → check on the Boox → commit, stop)
4. **Fixes and speed** (done).
   - Selection text: words lose edge punctuation; phrases keep the sentence end (`.?!…`), stray quotes and
     brackets at the edges go. Dictionaries still get the fully cleaned text.
   - Claude: one client per key, at most one retry; default model `claude-sonnet-5-5`, thinking off where the
     model allows it (Opus 5.5 / Fable: effort low only; Sonnet 5.5: `between_tools`; others: `disabled`);
     a 400 retries once without the extras.
   - OpenAI-compatible: a "no reasoning" field chosen by host (OpenAI, OpenRouter, Gemini, DeepSeek, DashScope,
     Groq, Mistral, others), then a weaker one, then none, on HTTP 400/422; the variant that worked is
     remembered per host and model.
   - Network: "online" means a validated network. Errors: 401 → key; 403 or "location not supported" → the
     provider blocks the region (VPN) or the key; network exceptions anywhere in the cause chain → offline.
   - A phrase with no network or a failed AI answer shows the offline gloss in the same page.
5. **Storage** (done) — one SQLite file `/sdcard/InkPeek/inkpeek.db` (survives reinstall): AI answers cached by
   (text, answer language, section, schema version), "↻" asks again; saved words and phrases (☆ in the card);
   history of lookups (app only, not in the card). App: a "My words" tab — Words / Phrases / History, remove.
   Export later.
6. **AI sections** (done) — Translate · Analysis (grammar + words) · Context (examples + explain). Two requests:
   a quick structured answer on open (head facts: base form, part of speech, article / gender, plural, verb
   forms, irregular, auxiliary; and the translation), and one detailed answer that fills Analysis and Context.
   No per-tab on/off setting.
7. **Card** (done) — icon bar under the card, outside its border: Dictionary, Translate, Analysis, Context · Save,
   My words · Close (bottom right). Long press shows the name; a settings page
   explains the icons. Header: the base form with its article and plural, or the verb's principal parts with
   "irregular"; the selected form under it when it differs.
8. **Dictionaries** (done) — WikDict (compact, CC BY-SA, `download.wikdict.com/dictionaries/stardict/wikdict-<src>-<dst>.zip`)
   first in "Get dictionaries", offered for the reading languages → answer language; Wiktionary as the
   full-article dictionary. Dictionary tab for a word: one short line per dictionary (form → base — meaning),
   tap for the full article.
9. **Core and AI apart** (done). Nothing in the core waits for AI or needs a network.
   - Header from local data only (the grammar pack; until it exists, the article from WikDict's grammar mark).
   - Tabs: core — Dictionary, Translate (offline engine of milestone 11; until then the dictionary lines / gloss),
     Save, My words, Close. AI — AI translation, Analysis, Context: an "AI" mark on the icon; inactive (and a short
     reason on tap) when AI is off, has no key, or there is no network. No AI request on card open.
   - No "Ask again".
Decisions (21.10): minSdk 30 (Android 11, the Boox); translation models downloaded by the app straight from Mozilla,
the full ("base") models; the pack list is one list, each pack brings everything for its pair; UI languages =
exactly the answer languages of the packs; Translate: offline first (decided 21.10: the core tab sends nothing by default; setting: AI first when it can answer), the offline engine
otherwise, with a plain note in the app and the README that it goes through English.
10. **Language packs** (scripts, tests, workflow done for de, en → ru, en; then all languages) — built by our scripts in `packs/` (Python, std lib), checked by tests, published as assets
   of our own GitHub releases (`packs-YYYY.MM.N`) with a manifest `packs.json`; the app downloads only from there —
   no third-party server at run time (Mozilla models mirrored, MPL-2.0). Too big for the APK (all 10 languages ≈ 0.9 GB).
   - Languages: de→ru, en→ru, de→en, then en→de, ru→de (German answers bring the German UI back), grammar ru. Then an analysis of which other packs reach the same quality; a new
     answer language comes in tandem — its packs and the UI language together, never one without the other.
   - Components, each a separate asset, shared between pairs: `dict-<x>-<y>` (one StarDict per pair: our own articles from the
     Wiktionary of language y via kaikki.org, first line "header — first meanings", WikDict where Wiktionary has
     nothing, `.syn` from the grammar), `model-<x>-<y>` (Mozilla's best model for the direction).
   - A pair is published only when its tests pass: golden words (form → lemma, article, plural, principal parts,
     first meaning), coverage of frequent words, first-line length.
   - Inputs pinned by date and sha256 (`packs/sources.json`); the build streams the kaikki dumps, never unpacks them.
   - CI per component: slices of the kaikki dumps are cut once and kept in release `packs-sources` (re-cut only
     when a dump changes: ETag / Last-Modified); a run builds only the languages it is given, tests them and
     uploads those assets to release `packs`, merging `packs.json` (other components stay as they are).
11. **Offline translation engine** (done; models picked per direction in settings until the pack manager) — Bergamot (Firefox Translations, MPL-2.0) built with the NDK; the JNI wrapper
   adapted from RTranslator (Apache-2.0, attribution); Mozilla's models (via English: de→en→ru) from the packs.
   Prototype measured on the Boox (research note): 0.15–0.85 s per sentence with both models loaded (≈ 560 MB),
   1.2–2 s with one model at a time (≈ 380 MB); engine ≈ 3.3 MB in the APK; needs API 28+.
   - Translate tab (core, no AI mark): with AI on, two segments like My words — AI and Offline; the setting picks the
     first one; AI dimmed (reason on tap) when it cannot answer; the offline text is marked "through English".
   - Models in `/sdcard/InkPeek/models/<from><to>/` (model, lex, vocab), from the packs (milestone 10: mirrored from
     Mozilla Remote Settings `translations-models-v2`, the newest "base" record, else "base-memory").
   - Memory: the models of one pair stay loaded when the device has room (else one at a time), freed after 3 idle
     minutes, on low memory and when another pair is translated.
12. **My languages** (done; checked on the Boox) — one system instead of the offline-translation block, the
   dictionary catalog, "language of my books" and the user's own dictionaries (all gone, 21.10). Packs list from our
   release `packs` (`packs.json`). No answer-language setting: answers come in the app's language. The list shows
   every pair of the packs (those into the app's language first); the user ticks any or all, the app downloads
   what the pairs need — `dict-<x>-<y>`, the models through English, `langid` — sha256 checked; status, size,
   update; what is not ticked (or no longer in the list) is removed. The card's dictionaries are the packs only.
   - Header of a word: the first line of the pack dictionary's article (built from Wiktionary's grammar), offline.
   - Language of a selection (offline translation only; AI needs none): fastText (`lid.176.ftz`, CC BY-SA 3.0,
     ≈ 1 MB; fastText MIT, native) among the installed book languages; when it is unsure (< 0.5, a word or two), the
     pack dictionary that knows more of the words (forms included). A language without a pack is named as such.
   - The grammar is a build input of the dictionaries, not a download.
13. **App redesign** — settings grew from the card's widgets: a plan and a mockup first, standard controls sized
   for e-ink, sections, fewer options.

Replaced (20.10): Offline Translator app via AIDL (GPL app, 44 MB, its own pack UI — we could not control the
languages); grammar from AI; AI as the default translation.
