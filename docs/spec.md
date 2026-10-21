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
   Translate, Analysis, Context · ☆ Save (★ when saved), Notes · ✕ Close at the right. Icons ≤ 44 dp; the open tab
   has a slightly heavier border (4 vs 2); AI icons carry "AI" in their top-right corner, drawn smaller to leave
   room for it. Long press shows the name.
- Notes in the card: Words / Phrases with counts; tap opens the entry in the card, ✕ removes. No history here.
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
  - **Analysis** (`grammar`, `words`) and **Context** (`explanation`, `examples`: 3 × {text, translation}, none for a
    passage), the prompts of v0.2's separate tabs; the app writes the headings (Grammar, Words; Explanation, Examples).
    Opening any AI tab, or Translate's AI tab, sends all three requests at once, the open one first; each is saved.
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
- Settings → Notes: Words / Phrases / History as second-level tabs (text, the open one underlined), rows of the
  text and one line (cut with "…"); tap opens the card, ✕ removes; "Clear history" under the list.

## Settings screen
Tabs: Languages (pack pairs, milestone 12) · AI (on/off, provider, key and its source, model picker with
filter / maker / free only / sort) · Card
(app language, a phrase opens on Translate, what each icon does) · Notes. UI exactly in the answer languages of the packs (en, ru, de, fr, es, it, pt, pl); system language
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
exactly the answer languages of the packs; Translate: AI first when it can answer (on, set up, online; decided 20.10, no setting), the offline engine
otherwise, with a plain note in the app and the README that it goes through English.
10. **Language packs** (scripts, tests, workflow done for de, en → ru, en; then all languages) — built by our scripts in `packs/` (Python, std lib), checked by tests, published as assets
   of our own GitHub releases (`packs-YYYY.MM.N`) with a manifest `packs.json`; the app downloads only from there —
   no third-party server at run time (Mozilla models mirrored, MPL-2.0). Too big for the APK (all 10 languages ≈ 0.9 GB).
   - Pairs (20.10): de, es, fr, it, pl, pt, nl, ru → en; en, de, es, fr, pl → ru; en, ru → de; en → fr, es, it, pt, pl.
     Published only when the dictionary knows ≥ 85% of the book language's 5,000 most frequent words (subtitle lists,
     forms included; de→ru 89%) and the golden words pass. Not yet: it, pt, nl → ru (73–75%), ru → de is the weakest
     published (68%). A new answer language comes in tandem — its packs and the UI language together, never one
     without the other. No reversed translation tables (decided 20.10: they added noise to strong pairs).
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

14. **Version 0.4: readers' wishes** (from MobileRead, 21.10: own dictionaries, a smaller pop-up, a dark mode; no AI).
   New options only; the defaults stay as in 0.3 except "a phrase opens on Translate" (on by default).
   1. **Card settings in three tabs**: Main (language, phrases), Window, Icons. **Window**: height — by content (a small pop-up, like KOReader's) or a share of the
      screen; width; position — top / centre / bottom, or where the reader asks (`EXTRA_GRAVITY`, `EXTRA_HEIGHT` of the
      ColorDict protocol: Librera and FBReader send them, so the card does not cover the word); text size; theme —
      light, dark (white on black) or as in the system (night mode). **Icons**: the icon bar on or off (off: the
      Dictionary tab only, tap outside closes); each icon with what it does and when it is there (Translate: AI or
      offline translation on; Analysis, Context: AI chosen); the save icons (☆, Notes) on or off under them — the
      history is kept either way.
   2. **Languages in two tabs.** "InkPeek": the pairs as in 0.3, then "what the card takes from the language packs" —
      words (articles, word-by-word), grammar (the header), offline translation; the text's language always comes from
      the packs (the ticked pairs, whatever is switched off). "Other dictionaries" (StarDict only, as in 0.2): download
      from public sources (WikDict, Wiktionary by xxyzz, FreeDict; each source on or off), import of files and archives
      (zip, tar.gz/bz2/xz/zst) into `InkPeek/dicts`, folders used in place (KOReader's `koreader/data/dict`, found ones
      offered); each dictionary on or off, imported ones can be deleted. In the card they come after the packs, each
      line signed with its source ("InkPeek · German → Russian" or the dictionary's name), with their article's short
      meaning; their header is the selected word.
   3. **What is there decides what shows**: Translate's Offline tab only with a pack's models installed and offline
      translation on, its AI tab only with AI on; no Translate icon with neither; when the text's language (fastText) has
      a published pair into the app's language that is not installed, the Offline tab names that InkPeek pack. AI: a
      list whose first entry is "Off" (no AI icons, no AI hints) instead of the switch.
   4. **Settings in one style.** Every section has three tabs — the main one, a second one, help: Languages (InkPeek ·
      Other dictionaries · Help), AI (Main · Prompts · Help), Card (Main · Appearance · Icons), Notes — formerly My words (Words ·
      Phrases · History). One row everywhere: a box, an icon or nothing on the left; a title in one line; an explanation of at most
      two lines (texts written to fit, ~80 characters, in every language); a drop-down a little apart from its text.
      Every delete asks first. Help tabs are rows of the same kind, no small print.
      - **Languages → InkPeek** looks like Other dictionaries: "Your packs" (installed pairs: a box — the card uses it,
        ✕ — remove it; an update line when the release has newer files), "What to take from the packs", "Download":
        the source (GitHub, kafeg/inkpeek) and a button to a page listing the published pairs (tap → size → download).
      - **Other dictionaries: one folder** (default `InkPeek/dicts`, can be another, e.g. KOReader's `koreader/data/dict`;
        found ones offered): read from it, downloads and imports go into it, ✕ deletes from it (the dialog says where).
      - **Card → Main**: language, phrases on Translate, the icon bar, the AI icons (there only when AI is chosen), the
        save icons. **Icons**: what each icon does (help).
      - **AI → Prompts**: each request's parts (the meaning of every answer key, the general style) can be edited and
        reset; the keys and the JSON format stay fixed. Answers saved with an older text are kept, not asked again.
      - **Notes**: 50 per page, the pager and "Clear history" under the list.
   5. Readers: Librera (selection menu → "+" → InkPeek) checked and in the README.
15. **Version 0.5: copy and updates** (MobileRead, 21.10: "can't copy the output", "check for updates").
   1. **Copy, share, search the web**: hold a line (the dictionary list, word by word, history, notes — in the card and
      in settings) or select part of a long text (Translate, AI answers, an article, the headers). A small menu of our
      icons opens over it (copy, share, search the web; select all for a text), like FBReader's: nothing on the page
      moves. One card for every way in (a reader, the search field, Notes), so it works the same everywhere.
   2. **Version and updates**: the settings' status line shows the version on the right, "0.5.112 → 0.5.118" when a
      newer release is out. A tap opens About: what InkPeek is, the version and the update state, Check now, a daily
      check (on by default; when settings open, at most once a day; one request to GitHub), GitHub, the licenses.
      Update: the APK through the download dialog (progress, Cancel, Hide), then Android's installer.
   3. **The card's dictionary** (MobileRead, 21.10): Card → Main — short lines (tap for the article) or full articles,
      one dictionary per page, ◀ ▶ or a swipe; Dictionary order — one list of the packs and the other dictionaries in
      use, ↑ ↓, the card follows it (new ones join at the end, the packs first). Card → Window becomes Appearance.
   4. **CHANGELOG.md**: what changed per version for people who use the app; a release's notes come from it, and the
      update shows them.
   5. Releases by `scripts/release.sh` (checks, changelog, tag, the build, the APK's checksum and signature, notes),
      screenshots and layout checks by `scripts/screens.sh`.

Replaced (20.10): Offline Translator app via AIDL (GPL app, 44 MB, its own pack UI — we could not control the
languages); grammar from AI; AI as the default translation.
