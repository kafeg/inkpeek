# Changelog

What changed for people who use InkPeek. The version is `<major>.<minor>.<commits>`: the last number is the commit
count, so every release has its own.

## 0.8 — unreleased
- Settings in a new order, at most three tabs each, every setting with a line saying what it does: Languages (Packs ·
  Translation · Dictionaries), Card (Opening · Article · Look), AI (Connection · Modules), Notes. ⚙ next to the
  version: the app's language, theme, updates, file access. ? Help: a page per topic, each with a way to its settings;
  it can add InkPeek to KOReader's dictionary list.
- The language of the answers is a setting of its own (Languages → Translation): an English app with answers,
  dictionaries and translations in German. By default it follows the app's language.
- AI only when you ask: a phrase opens on what Card → Opening says (the offline translation by default; an AI choice
  there asks on every opening), Offline comes before AI in Translate, and a text without an offline translation opens
  on Dictionary. AI → Modules: Translation, Analysis, Context — a module with nothing ticked has no tab; ✎ edits each
  part's prompt. AI is told the text's language when the packs know it.
- Offline translation Off is a method of its own.
- Lists: a row opens by the › at its end; its text is selected like any other.
- Translation method: downloaded models stay when you switch the method (switching back downloads nothing); the
  list under the methods shows them with their size, ✕ removes one.
- A word in word by word opens as a card of its own: its header, its ☆; ← comes back to the phrase.
- Nothing runs for what is switched off: hidden AI tabs send no requests (Translate by AI asks only for the
  translation), offline translation that is off does not start, an AI model starts only when its tab opens, and
  switched-off dictionaries are not indexed.
- Card → Opening lists only what can open now (Analysis · AI and Context · AI only with their module on, the offline
  translation only with a method and its models); a choice that is gone falls back to the offline translation or the
  dictionary.
- Under an article, a section row shows two lines; › opens the whole list. Section lists and Notes turn pages on
  top, like the dictionaries: ◀ the title and "21–40 of 125" ▶, or a swipe.
- Help: more on the card, selection and its menu, the offline translation and AI; the button says where it goes
  ("Go to: Card → Opening").
- Card → Appearance → Turning: by pages — a swipe up or down turns a whole page, starting at a whole line (on some
  readers scrolling redraws the whole screen when it stops); the line over the icons shows the pages, the open one thick.
- Show the English (Languages → Translation, Fast · Mozilla): the English step of a translation through English,
  smaller, under it.
- A copy of the notes, the history and the AI answers is made each day in InkPeek/backup; the last five are kept.
- Translate has "AI" in its corner when it holds the AI translation.
- One AI icon in the card instead of two: Analysis and Context are its tabs, as Translate holds Offline and AI; with one
  module on, no tabs; with none, no icon.
- A long header (a phrase, a passage) shows its first lines with "…" and is not scrolled inside; a tap opens the whole
  text in its place, the page under it moving down, to select from there; ▲ under it or Back closes it.
- A double tap in the card's header selects a word (an e-ink double tap often arrives as three, and the third took the
  whole header).
- A capitalised word at a sentence's start is first its own word: *Verdammt* → verdammt, *Fertig* → fertig (not the
  verbs verdammen, fertigen).
- Notes are safer: a damaged notes file is put aside (inkpeek.db.corrupt-…), not deleted; notes made before file
  access was allowed are added to the shared file, not left behind; a cut-short pack update keeps the old version.
- Fixed: two offline translations at once (a card and the speed test) could hang; the speed test ran on every visit to
  the settings; a word's look was counted again when the card came back (←, a new theme).
- Fixed: an AI Opening without AI (no network, no key) opens the offline translation; ← from a section of a full
  article keeps ◀ ▶ there, ← to word by word keeps its scroll; ✕ in the card's Notes clears ★ of a word saved as its
  base form; AI → Connection shows the answer language just changed; an AI tab is dimmed only when that tab cannot answer.
- Fixed: the download dialog could stay at Cancel / Hide without saying it was done.

## 0.7 — 2026-10-23
- Word by word names a German verb written apart first: «Ich mache die Tür zu» → *mache … zu: zumachen*. Found by the
  packs' data (the verb's tables write it apart); a particle after its clause's end is not taken.
- ☆ saves a word as its base form: *andere* and *ander* are one note; *das Haus* is the word *Haus*.
- Under the card's header: how common the word is (among the 1,000 / 5,000 / 20,000 most frequent) and how often you
  looked it up, in any of its forms.
- Notes: Export and Import of everything at once (saved words and phrases, the history, AI answers) as one file in
  Download/InkPeek; an import adds what the device does not have. The list's own export is now TSV.
- Lists look alike: a thin line between the dictionaries of a word and between notes.
- German articles are described by their own table: *die* — article der: f. nom./acc., pl. nom./acc. (it showed
  der's masculine meaning before).
- Word by word is ready when its tab opens: a phrase's card works it out from the start.
- Ukrainian → Russian is no longer offered: a pair without English is published only from 60 % of the frequent
  words (it had 43 %); Ukrainian → English stays.

## 0.6 — 2026-10-23
- Offline translation method for all pairs (Languages → InkPeek → Translation method): Mozilla (fast, through English
  between two other languages), OPUS-MT (direct between two languages, no English in the middle) or the Hy-MT2 AI
  model (best quality, needs 6 GB of memory and a newer processor). The app downloads what your pairs need; a method
  this device cannot run is dimmed with the reason; after the download it shows the speed on this device.
- Under a pack's article: related words (anmachen, zumachen…; make up…), synonyms and antonyms, expressions and
  proverbs, forms with pronunciation and the word's origin — each a row, a tap opens the list, frequent words first,
  page by page, a tap on a word looks it up. Which ones: Languages → Article (was Help).
- Word by word: the same list style.
- Settings tabs fit on one line in every language (Languages → Other).
- New book languages: Ukrainian (into English and Russian), Czech, Bulgarian, Finnish, Hungarian, Swedish, Danish
  and Romanian (into English); Italian, Portuguese and Dutch into Russian.
- Dictionary first lines keep a word's main meaning where Wiktionary splits it into sub-senses (Swedish bok — book).
- The packs page labels each pair: full, basic or experimental, with the share of the 5,000 most frequent words its
  dictionary knows.
- AI → Answers (was Help): what Analysis and Context ask for — grammar, words, related words, synonyms, explanation,
  examples, expressions, origin; the explanations below.
- Look up from any text in the card: select a word in an article or an answer, 📖 opens it, ← returns to where the
  card was: its tab, the open article or list, the scroll.
- Export of Notes (words, phrases or the history) as a TSV file for Anki or a spreadsheet, with Share.
- The page behind the card is no longer dimmed (a grey layer on e-ink); Card → Appearance → Dim the page brings it back.

## 0.5 — 2026-10-21
- Copy, share or search the web: hold a line of the dictionary list, word by word, Notes or the history (in the card
  and in settings) — its text is selected, the handles narrow or widen it — or select in a translation, an article,
  an AI answer or a header (double tap: a word, triple tap: the paragraph). A small menu of icons opens over it; a tap
  beside it closes it.
- The version in the settings header, with a link when a newer one is out; About: what InkPeek is, the version,
  "Check now", a daily check you can turn off, GitHub and the license.
- Updates download inside the app with progress and Cancel, then Android's installer opens.
- Downloads of packs and dictionaries: one at a time, in a dialog with progress, Cancel and Hide; leaving the page does
  not stop them.
- A dictionary already in the folder is not added again (downloaded or imported twice); the download page marks the
  ones you have.
- Dictionary order: packs and other dictionaries in one list, in the order you want (Card → Main).
- The dictionary in the card as short lines or as full articles, ◀ ▶ or a swipe between dictionaries (Card → Main).
- Card → Window is now Card → Appearance (size, position, text, font).
- Articles are black and white: a dictionary's own colors (WikDict's green grammar, grey IPA) are removed.
- Rows you can select (dictionary lines, Notes, the history) open on the first tap again, not the second.
- Back and page arrows as icons; "→" drawn in the middle of the line.
- Notes and the history show each entry's line in the app's language when there is one (a saved line keeps its
  language).
- Triple tap on the card's header selects it with the menu; a tap beside a selection in the card closes it.
- A dictionary replaced in its folder by a new edition (or an index cut short) gets a new index; two screens never
  build one at the same time.
- Language packs: a cancelled or failed download leaves nothing behind; a pair left half-way by an older version is
  not shown as installed and can be finished.
- Translate by AI failing (network, key) shows the word-by-word gloss under a phrase and asks again next time.
- An update confirmed while InkPeek was in the background opens the installer when settings open.
- The card's font (Card → Appearance → Font): a page with a sample, Android's fonts that have your letters, and your
  own .ttf / .otf from `fonts/`, `koreader/fonts` or a file; bold and italic files of the family are used with it.
- Dialogs and messages follow the theme too (Boox drew Android's own white).
- Dark theme for the whole app, settings too (Card → Main → Theme).
- Notes in the card: 50 per page, like in settings.
- Card → Icons: which icons the card shows on top, what each one does below.
- Settings sections in the order of setting up: Languages, Card, AI, Notes.
- "→" in AI answers and offline translations drawn in the middle of the line; dimmed buttons (the first page's ◀)
  easier to see.

## 0.4 — 2026-10-21
- Other dictionaries: any StarDict dictionary (KOReader's format) — download from WikDict, Wiktionary (xxyzz) or
  FreeDict, import files or archives, or use one folder for all of them (KOReader's works as it is).
- The card's look: height by content (a small card), width, position (top, centre, bottom, or where the reader asks —
  FBReader and Librera send it), text size, light / dark / as the system.
- What the card takes from the language packs: words, grammar, offline translation — each on or off.
- AI is optional: "Off" first in its list; a fresh install starts without AI. Prompts can be edited.
- Translate shows only what can work: Offline with a pack, AI with a provider; and names the pack you need.
- Settings in one style, three tabs per section with Help; Notes (formerly My words) 50 per page; every delete asks.
- Librera tested; safer deletes (only a dictionary's own files).

## 0.3 — 2026-10-21
- Language packs from InkPeek's own release: a dictionary built from Wiktionary, grammar (gender, plural, verb forms)
  and Mozilla's offline translation, run on the device; 20 language pairs.
- Offline translation of phrases and passages, word by word for every sentence.
- The card: a header with the word's grammar, an icon bar, Analysis and Context by AI.
- Saved words and phrases, the history, answers kept for offline reading.
- The app in 8 languages: English, Русский, Deutsch, Français, Español, Italiano, Português, Polski.

## 0.2 — 2026-10-11
- AI tabs (Claude, OpenAI, OpenRouter, any OpenAI-compatible server) with answers in your language.
- Offline word-by-word gloss from local dictionaries; clear errors when there is no network or key.
- Signed release APKs; setup for FBReader and KOReader.

## 0.1 — 2026-10-11
- The pop-up card over the page: StarDict dictionaries with their own index, import and a catalog; AI actions.
