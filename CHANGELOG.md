# Changelog

What changed for people who use InkPeek. The version is `<major>.<minor>.<commits>`: the last number is the commit
count, so every release has its own.

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
