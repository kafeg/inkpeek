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

- ColorDict extras: `EXTRA_HEIGHT`, `EXTRA_GRAVITY` are hints only. `EXTRA_FULLSCREEN` is ignored: some readers
  send `true` when their status bar is hidden; InkPeek always opens the card.
- The activity is a dialog over the caller's task (`taskAffinity=""`, `excludeFromRecents`, no history). Back or a
  tap outside closes it.

## Card
Tabs at the bottom: **Dictionary**, **Translate**, **Analysis**, **Context** (AI off → only the dictionary and a hint).
A tab keeps its content. Selection kinds: word · phrase (2–8 words, one sentence) · passage (more); the model is told
the kind. A phrase or passage opens on Translate (setting `phrase_auto_ai`, default on), a word on Dictionary.
Header of a word, once the quick answer is in: "das Haus · die Häuser" or the verb's forms ("gehen · geht · ging ·
ist gegangen"); under it, small: "Häusern → noun, neuter · dative plural · irregular".
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
2. Under the card, outside its border, a white strip with bordered icons: Dictionary, Translate, Analysis, Context ·
   ☆ Save (★ when saved), My words · ✕ Close at the right. The open tab has a thick border. Long press shows the name;
   setting "names under the icons" writes it (short) under each icon. Icon size fits the width (≤ 52 dp).
- My words in the card: Words / Phrases with counts; tap opens the entry in the card, ✕ removes. No history here.
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
- Get dictionaries: live lists of WikDict (`download.wikdict.com/dictionaries/stardict/` index, `.zip`, CC BY-SA),
  Wiktionary (xxyzz/wiktionary_stardict releases, `.tar.zst`) and FreeDict (`freedict-database.json`); language
  names, pairs into the answer language first, ★ WikDict among them (compact: short translations, gender, forms →
  base). A ★ download goes first in the order. The download dialog says what each source is good for.
- Short line of an article: WikDict — the `<div>` translations of the first (or the AI's) part-of-speech block and
  its grammar mark ("noun, neutral"); Wiktionary — the first sense of the first `<ol>`; plain text (Mueller) — the
  first line without `[transcription]`, `_v.`, `1>` marks; always up to the first ";".
- Own index per dictionary, built once (app files): every headword and `.syn` form, normalized (NFC, lower case, no
  soft hyphen), sorted, binary search over a memory-mapped file. Upstream `.idx` sort order does not matter.
  `.dict.dz` is unpacked once into the app's files.
- Query normalization: trim, strip surrounding punctuation and quotes, collapse spaces; soft hyphen removed.
  The card and the AI get a phrase with its sentence end (`?!.…`) kept and stray edge quotes / brackets removed.
- Rendering: HTML through `Html.fromHtml` into a `TextView` (no WebView, no JS); images ignored.
- Dictionary list in settings: enable/disable, order.

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
- Answer language: setting, default Russian.
- Two requests, each answered as one JSON object (Claude: structured outputs with a JSON Schema; OpenAI-compatible:
  `response_format: json_object`, then without it on 400/422; the reply is parsed from the first `{` to the last `}`).
  Prompts: `Ask` in `ai/Ai.kt`, placeholders `{text}`, `{lang}`.
  - **Quick**, on card open (AI on): `base`, `article`, `plural`, `pos` (gender of the original word), `forms`,
    `irregular`, `form_of`, `meanings`, `translation` (keeps the sentence type); no grammar or comments. Fills the header and Translate.
  - **Detail**, on the first open of Analysis or Context: `analysis` (word: use, synonyms, related; phrase: how it is
    built and why + harder words; passage: constructions + up to 15 words) and `context` (3 examples with
    translation + register / idioms; passage: what happens and what is implied). Fills both tabs.
- System prompt: answer only in `{lang}`; a text already in `{lang}` gets an explanatory-dictionary answer; light
  Markdown only (**bold**, *italic*, "- " lists) rendered in the card; brief; the model sees only the selection and
  must not guess its sentence.
- OpenRouter default model `openrouter/free`.
- Data sent: only the selected text and the prompt — the quick request on card open (AI on), the detailed one on a
  tap. No telemetry.

## Storage
- One SQLite file `/sdcard/InkPeek/inkpeek.db` (app files when all-files access is missing); survives a reinstall
  (debug ↔ release needs one). No WAL (shared storage), no UPSERT (Android 8–9 SQLite).
- `ai`: answers by (text, answer language, request quick / detail, prompt hash) as JSON; shown again without a request,
  also offline. "↻ Ask again" under an answer replaces it. Settings → AI: delete saved answers (count shown).
- `saved`: words and phrases (a passage counts as a phrase) saved with ☆ in the card header (★ = saved, tap removes),
  with one short meaning: the cached translation, else the first dictionary meaning; empty → the cached translation
  at display time.
- `history`: every card opening, last 1000; only in the app, not in the card.
- Settings → My words: Words / Phrases / History with counts; tap opens the card, ✕ removes; clear history.

## Settings screen
Tabs: Dictionaries (list with status, enable, order, delete; import; get) · AI (on/off, phrase mode, provider, key
and its source, model picker with filter / maker / free only / sort, answer language, delete saved answers) · Card
(app language, text size, dim, position, names under icons, what each icon does) · My words. UI in 18 languages; system language
by default.

## Tech
- Kotlin, Android Views (no Compose), minSdk 26, targetSdk 35. Libraries: anthropic-java, OkHttp, kotlinx-coroutines,
  commons-compress + xz + zstd-jni (archives).
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
   My words · Close (bottom right). Long press shows the name; optional captions under icons; a settings page
   explains the icons. Header: the base form with its article and plural, or the verb's principal parts with
   "irregular"; the selected form under it when it differs.
8. **Dictionaries** (done) — WikDict (compact, CC BY-SA, `download.wikdict.com/dictionaries/stardict/wikdict-<src>-<dst>.zip`)
   first in "Get dictionaries", offered for the reading languages → answer language; Wiktionary as the
   full-article dictionary. Dictionary tab for a word: one short line per dictionary (form → base — meaning),
   tap for the full article.
9. **Core and AI apart.** Nothing in the core waits for AI or needs a network.
   - Header from local data only (the grammar pack; until it exists, the article from WikDict's grammar mark).
   - Tabs: core — Dictionary, Translate (offline engine of milestone 11; until then the dictionary lines / gloss),
     Save, My words, Close. AI — AI translation, Analysis, Context: an "AI" mark on the icon; inactive (and a short
     reason on tap) when AI is off, has no key, or there is no network. No AI request on card open.
   - No "Ask again".
10. **Language packs** — one curated pack per supported language pair, built by scripts in this repo
   (`packs/`), checked by tests, published as release assets with a manifest (`packs.json`: version, files, size,
   sha256, license, sources). A pack = dictionary (curated and post-processed from open sources, merged where that
   helps, one per pair — not a list of 100 raw dictionaries) + grammar (from Wiktionary via kaikki.org: article,
   plural, every inflected form → base, verb principal parts, irregular) + offline translation models. First the
   analysis: sources and quality per pair, for ru, en, de and the main others (fr, es, it, pl, uk, …).
11. **Offline translation engine** — Bergamot (Firefox Translations, MPL-2.0) built with the NDK; the JNI wrapper
   adapted from RTranslator (Apache-2.0, attribution); Mozilla's models (via English: de→en→ru) from the packs.
   Translate is offline by default. Prototype first: build, measure de→ru on a Boox, size and memory.
12. **Pack manager** — first run: reading languages and answer language → download the packs; status (installed,
   update, size), delete. Raw dictionary catalog and file import stay as "other dictionaries".
13. **App redesign** — settings grew from the card's widgets: a plan and a mockup first, standard controls sized
   for e-ink, sections, fewer options.

Replaced (20.10): Offline Translator app via AIDL (GPL app, 44 MB, its own pack UI — we could not control the
languages); grammar from AI; AI as the default translation.
