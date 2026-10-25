# How InkPeek works

*Part of the InkPeek documentation; the overview is in the [README](../README.md).*

## The card
- **Dictionary** comes first. For a word: the header from the packs — "das Haus (Hauses, мн. Häuser)", "go (went, gone)
  неправ." — then one short line per dictionary, signed with its source: the packs first ("InkPeek · German →
  Russian"), then your other dictionaries by name. The › at a line's end opens that dictionary's full entry, ← goes
  back. For a sentence: the word-by-word gloss, one row per word; a German verb written apart comes first ("mache … zu:
  zumachen").
- **Under a pack's full article**, from Wiktionary: **related words** (*machen* → anmachen, zumachen, Macher; *make* →
  make up), **synonyms and antonyms**, **expressions and proverbs**, **forms and pronunciation** (IPA), **origin**. One
  row each with a preview; its › opens the list — frequent words first, 20 per page, each with its first meaning — and
  a word's › looks it up as a card of its own (← returns). Which ones: Card → Article.
- **Translate**: Offline first (on the device), AI second — AI asks only when its tab is tapped. Each is there only
  when it can work: Offline with a pack installed and a method on, AI with a provider and its module on; with neither
  there is no Translate icon. A text with no offline translation opens on Dictionary (word by word).
- **The AI tab** (one icon marked "AI", dimmed when AI cannot answer right now; Analysis and Context its tabs, only the
  modules that are on): Analysis — Grammar and Words; Context —
  Explanation and Examples; more parts in AI → Modules ([below](#ai)); a module with no part ticked has no tab and sends
  nothing. Opening one AI tab asks for the others at once (a setting, on by default), so they open instantly; the
  answers are kept with the history entry and go when you remove that entry. With AI off they are gone.
- **What opens first** (Card → Opening): a word — dictionary lines or full articles; a phrase — the offline
  translation (default), the AI translation, word by word, Analysis or Context (an AI choice asks AI on every opening).
- What you selected decides what the tabs do: one word, a short phrase (up to 8 words), or a passage (longer, or more
  than one sentence).
- **Select any text** to copy, share or search the web — a list row too (its › opens it): double tap a word, triple
  tap a paragraph, or hold and move the handles. A small menu of icons opens over it, like FBReader's.
- **Notes**: ☆ saves a word as its base form (*andere* → ander), a phrase as it is; the app keeps the history too
  (the last 1,000), each entry with one short line (50 per page, in the card and in settings). Under a word's header:
  how common it is and how often you looked it up. **TSV** saves the open list in `Download/InkPeek` (text, meaning, AI
  translation, language, date — Anki imports it as is); **Export** and **Import** move everything (saved, history, AI
  answers) as one file — an import adds what the device does not have. A copy of everything is made each day in
  `InkPeek/backup` (the last five kept): to go back to one, Import it.
- **Look up from any text**: select a word in an article, a translation or an AI answer — the menu's 📖 opens it in the
  same card, ← returns to the word before.
- **The card's look** (Card → Appearance): height by content (a small card, like KOReader's) or a share of the screen;
  width; turning — scrolling, or by pages (a swipe up or down turns a whole page: on some readers scrolling redraws
  the whole screen when it stops); position — top, centre, bottom, or where the reader asks (FBReader and Librera send it, so the card does not
  cover the word); text size; dimming the page; the font, on a page of its own with a sample — Android's fonts that
  have the letters the card needs, or your own `.ttf` / `.otf` (found in `fonts/` and `koreader/fonts`, or chosen as a
  file). Card → Opening: the icon bar and the save icons can be hidden. Card → Article: **Dictionary order** puts packs
  and other dictionaries in the order you want.
- **⚙ App** (next to the version): the app's language, the theme of the whole app — light, dark (white on black) or as
  in Android's night mode —, updates, file access. **? Help**: a page per topic, each with a way to its settings; it
  can add InkPeek to KOReader's dictionary list.
- Built for e-ink: black on white, no animations.
- The app is in English, Русский, Deutsch, Français, Español, Italiano, Português and Polski — exactly the answer
  languages of InkPeek's language packs. Answers come in the app's language unless Languages → Translation sets
  another one; AI is told the text's language when the packs know it.

## AI
AI is optional: the AI tab's first choice is **Off** (a fresh install starts there unless a key is already in
`InkPeek/keys`). Providers: **Claude** (Anthropic), **OpenAI**, **OpenRouter** (hundreds of models, some free), and **any
OpenAI-compatible server** — your own or a cloud one. The model list has a search, a maker filter, "free only" and
sorting. For your own server, pick a preset and change the address if needed; a local server needs no key.

Answers should come in seconds, so InkPeek asks every model to skip its reasoning pass where the provider allows
it. Claude defaults to Sonnet 5.5; Haiku 5.5 is faster and cheaper but weaker on grammar, Opus 5.5 deeper but slower. When a
provider refuses access (403), it often blocks the country: a VPN helps.

| preset | address |
|---|---|
| LM Studio | `http://<computer IP>:1234/v1` |
| Ollama | `http://<computer IP>:11434/v1` |
| llama.cpp server | `http://<computer IP>:8080/v1` |
| vLLM | `http://<computer IP>:8000/v1` |
| Groq | `https://api.groq.com/openai/v1` |
| DeepSeek | `https://api.deepseek.com/v1` |
| Qwen (Alibaba Cloud) | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` |
| Mistral | `https://api.mistral.ai/v1` |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` |

A local server must listen on the network, not only on localhost (LM Studio: "Serve on Local Network"; Ollama:
`OLLAMA_HOST=0.0.0.0`). A small local model can be slow; InkPeek waits up to 2 minutes for it.

Put the key in settings, or in a file:

```
/sdcard/InkPeek/keys/anthropic.key
/sdcard/InkPeek/keys/openai.key
/sdcard/InkPeek/keys/openrouter.key
/sdcard/InkPeek/keys/custom.key
```

```bash
adb push openrouter.txt /sdcard/InkPeek/keys/openrouter.key
```

**Modules** (AI → Modules): Translation, Analysis and Context, a box per part — the AI translation; Grammar, Words,
Related words, Synonyms and antonyms; Explanation, Examples, Expressions and proverbs, Origin (the last two of each
off at first). A part not asked is not in the request; a changed set asks again for words already answered; a module
with nothing ticked has no tab. Each part's ✎ edits what it asks for, and AI → Connection has the general style's ✎;
the answer's keys and format stay fixed, answers already saved are kept.

**Privacy:** only the selected text and the tab's request go to the provider you chose: when you open an AI tab, or
Translate while AI is on and online. With AI off no text leaves the device; the other network use is the list of
packs (read from GitHub when settings open), the daily update check (one request to GitHub, can be turned off) and
downloads you start. No telemetry, no accounts.

## Protocol
| intent | text extra | notes |
|---|---|---|
| `colordict.intent.action.SEARCH` | `EXTRA_QUERY` | the ColorDict / GoldenDict protocol; `EXTRA_GRAVITY` and `EXTRA_HEIGHT` place the card with Position → Where the reader asks; `EXTRA_FULLSCREEN` is ignored |
| `android.intent.action.PROCESS_TEXT` | `EXTRA_PROCESS_TEXT` | system selection menu, KOReader |
| `android.intent.action.SEND` (`text/plain`) | `EXTRA_TEXT` | Share |

Test from a computer: `adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus`
