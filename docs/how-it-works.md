# How InkPeek works

*Part of the InkPeek documentation; the overview is in the [README](../README.md).*

## The card
- **Dictionary** comes first. For a word: the header from the packs — "das Haus (Hauses, мн. Häuser)", "go (went, gone)
  неправ." — then one short line per dictionary, signed with its source: the packs first ("InkPeek · German →
  Russian"), then your other dictionaries by name. Tap a line for that dictionary's full entry, ← goes back. For a
  sentence: the word-by-word gloss, one row per word.
- **Under a pack's full article**, from Wiktionary: **related words** (*machen* → anmachen, zumachen, Macher; *make* →
  make up), **synonyms and antonyms**, **expressions and proverbs**, **forms and pronunciation** (IPA), **origin**. One
  row each with a preview; a tap opens the list — frequent words first, 20 per page, each with its first meaning — and
  a tap on a word looks it up (← returns). Which ones: Languages → InkPeek → What the card takes.
- **Translate**: by AI when it can answer (set up and online), offline on the device otherwise — both are tabs, the
  available one first. Each is there only when it can work: Offline with a pack installed, AI with a provider chosen;
  with neither there is no Translate icon. When a text's language has a pack you have not installed, the Offline tab
  names it.
- **AI tabs** (marked "AI", dimmed when AI cannot answer right now): Analysis — Grammar and Words; Context —
  Explanation and Examples; more parts on request (AI → Answers, [below](#ai)). Opening any AI tab asks for all of them at once (the open one first), so the others open
  instantly; the answers are kept with the history entry and go when you remove that entry. With AI off they are gone.
- What you selected decides what the tabs do: one word, a short phrase (up to 8 words), or a passage (longer, or more
  than one sentence).
- **Hold a text** to copy, share or search the web: a line of the dictionary list, word by word, Notes or the history
  is selected whole (the handles narrow it); in a translation, an article or an AI answer select any part. A small menu
  of icons opens over it, like FBReader's.
- **Notes**: ☆ saves a word or phrase; the app keeps the history too, each entry with one short line (50 per page, in
  the card and in settings). **Export** saves the open list as a TSV file in `Download/InkPeek` (text, meaning, AI
  translation, language, date — Anki imports it as is) and offers Share.
- **Look up from any text**: select a word in an article, a translation or an AI answer — the menu's 📖 opens it in the
  same card, ← returns to the word before.
- **The card's look** (Card → Appearance): height by content (a small card, like KOReader's) or a share of the screen;
  width; position — top, centre, bottom, or where the reader asks (FBReader and Librera send it, so the card does not
  cover the word); text size; the font, on a page of its own with a sample — Android's fonts that have the letters
  the card needs, or your own `.ttf` / `.otf` (found in `fonts/` and `koreader/fonts`, or chosen as a file). Card → Main: the theme of the whole app — light, dark (white on
  black) or as in Android's night mode; the icon bar,
  the AI icons and the save icons can each be hidden; the dictionary as short lines or as full articles (◀ ▶ or a
  swipe between dictionaries); **Dictionary order** puts packs and other dictionaries in the order you want.
- Built for e-ink: black on white, no animations. Languages and Card each have a Help tab; AI → Answers explains each part of an answer.
- The app is in English, Русский, Deutsch, Français, Español, Italiano, Português and Polski — exactly the answer
  languages of InkPeek's language packs; a new language comes with its packs. Answers come in the app's language.

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

**Prompts** (AI → Prompts): what each part of an answer asks for — Grammar, Words, Explanation, the translation's
facts — and the general style can be edited and reset; the answer's keys and format stay fixed. Answers already saved
are kept.

**Answers** (AI → Answers): what Analysis and Context ask for, a box each with what it gives — Grammar, Words,
Related words, Synonyms and antonyms; Explanation, Examples, Expressions and proverbs, Origin (the last two of each
off at first). A part not asked is not in the request; a changed set asks again for words already answered.

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
