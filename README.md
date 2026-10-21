<p align="center"><img src="docs/img/icon.svg" width="96" alt="InkPeek icon"></p>

# InkPeek

**Read books in a language you are learning without leaving the page.**

Tap a word or select a sentence in your e-book reader. A calm black-and-white card opens over the page with the
dictionary entry, a translation that fits the context, and short explanations from an AI tutor. Close the card and
you are back on the same line.

Made for Android e-ink readers (Onyx Boox and others); works on any Android device.

## What you can do

<table>
<tr>
<td width="33%"><img src="docs/img/card-dictionary.png" alt="Dictionary entry for a word over the page"></td>
<td width="33%"><img src="docs/img/card-phrase.png" alt="A paragraph translated in place"></td>
<td width="33%"><img src="docs/img/card-grammar.png" alt="Grammar of a sentence explained"></td>
</tr>
<tr>
<td valign="top"><b>Look up a word.</b> Entries from the language packs, offline. Inflected forms find their base
form: <i>Träumen</i> → <i>Traum</i>.</td>
<td valign="top"><b>Translate a sentence or a paragraph.</b> On the device, without the internet; or by AI when
you set it up.</td>
<td valign="top"><b>Understand why it is said this way.</b> Analysis (AI): Grammar — which form, how it is built,
the rule behind it — and Words.</td>
</tr>
<tr>
<td width="33%"><img src="docs/img/card-context.png" alt="Examples of a word in context"></td>
<td width="33%"><img src="docs/img/card-gloss.png" alt="Word-by-word gloss of a sentence, offline"></td>
<td width="33%"><img src="docs/img/settings-languages.png" alt="Language packs to download"></td>
</tr>
<tr>
<td valign="top"><b>See it in use.</b> Context (AI): Explanation — what it means here, register, idioms — and
Examples with a translation.</td>
<td valign="top"><b>Word by word, offline.</b> A selected sentence gets a gloss from the language packs: each word with
its base form and meaning. Tap a word to open its entry.</td>
<td valign="top"><b>Choose your languages.</b> Tick the pairs you read; each pack brings a dictionary and offline
translation. ☆ keeps a word or phrase in My words; the history keeps the rest.</td>
</tr>
</table>

<p align="center"><sub>Onyx Boox Go 6, FBReader, Franz Kafka «Die Verwandlung» (public domain), German → English.
Answers come in the app's language.</sub></p>

## Quick start
1. **Install** the APK from [Releases](https://github.com/kafeg/inkpeek/releases) and open InkPeek. Allow access to
   files when it asks.
2. **Choose your languages:** Languages → tick the pairs of your books → Download. Answers come in the app's language.
3. **Optional — AI:** choose a provider on the **AI** tab and paste your API key.
4. **Connect your reader** (below). In FBReader this is two settings.

## Connect your reader

### FBReader (the free version is enough)
Open **Settings → Dictionary & translator** and set **both**:
- **Look up single word in** → **ColorDict 3**
- **Translate phrases via** → **ColorDict 3**

The default phrase translator is Google Translate, a Premium feature: if you see "buy Premium" when you select a
phrase, this setting is the fix.

| a word | a sentence or a paragraph |
|---|---|
| Long-tap the word, then the **translate** button (文A). | Long-tap, drag the handle to the end of the text, then **translate** (文A). |
| <img src="docs/img/fbreader-word-select.png" width="240"> <img src="docs/img/card-dictionary.png" width="240"> | <img src="docs/img/fbreader-phrase-select.png" width="240"> <img src="docs/img/card-phrase.png" width="240"> |
| The card opens on the dictionary. | The card opens on Translate (or on the gloss, without AI). |

Tip: in **Settings → Dictionary**, set the long-tap action to **select single word, release to open dictionary**.
Then one gesture opens a word.

### KOReader
KOReader calls only the dictionary apps it knows. Add InkPeek to the list with one file,
`/sdcard/koreader/dictionaries.lua`:

```lua
return {
    { "InkPeek", "InkPeek", false, "io.github.kafeg.inkpeek", "text" },
}
```

Restart KOReader. Then **Settings → Dictionary settings → Use external dictionary**, and **Dictionary: InkPeek**.
A word or a selection now opens in InkPeek. This file replaces KOReader's built-in list of external dictionaries;
copy other entries from
[device.lua](https://github.com/koreader/koreader/blob/master/frontend/device/android/device.lua) if you need them.

### Other readers
| reader | how |
|---|---|
| AlReaderX | Settings → General settings → **Dictionary for single words** → **GoldenDict/ColorDict 3**; select a word or a passage (Text selection) → the translate icon |
| Cool Reader | Settings → Dictionary → **ColorDict new / GoldenDict**. On Onyx Boox Cool Reader does not offer it (its list there is Onyx's own); Share → InkPeek works, but Cool Reader adds the author and the title to the shared text |
| Moon+ Reader | Dictionary → **ColorDict** (not tested by us) |
| any app | text selection menu → **Look up in InkPeek**, or Share → InkPeek |

If another dictionary app answers the same request, Android asks which one to use: choose InkPeek → Always.

## How it works

### The card
- **Dictionary** comes first. For a word: the header from the packs — "das Haus (Hauses, мн. Häuser)", "go (went, gone)
  неправ." — then one line per dictionary, those into the app's language first; tap a line for the full entry. For a
  sentence: the word-by-word gloss.
- **Translate**: by AI when it can answer (set up and online), offline on the device otherwise — both are tabs, the
  available one first. Which language it was and how sits in small print at the card's bottom.
- **AI tabs** (marked "AI", dimmed when AI cannot answer): Analysis — Grammar and Words; Context — Explanation and
  Examples. Opening any AI tab asks for all of them at once (the open one first), so the others open instantly; the
  answers are kept with the history entry and go when you remove that entry.
- What you selected decides what the tabs do: one word, a short phrase (up to 8 words), or a passage (longer, or more
  than one sentence).
- **My words**: ☆ saves a word or phrase; the app keeps the history too, each entry with one short line.
- Built for e-ink: black on white, large text, no animations, few settings.
- The app is in English, Русский, Deutsch, Français, Español, Italiano, Português and Polski — exactly the answer
  languages of InkPeek's language packs; a new language comes with its packs. Answers come in the app's language.

### Languages
Settings → **Languages**: every language pair InkPeek has; tick the ones you read (or all of them). The card uses only
the ticked pairs; "Remove unticked" frees the space of the others. For each pair InkPeek downloads its pack from this project's [`packs` release](https://github.com/kafeg/inkpeek/releases/tag/packs)
— nothing comes from other servers.

Packs now — books in a language, answers in the app's language:

| answers | books |
|---|---|
| English | German, Spanish, French, Italian, Polish, Portuguese, Dutch, Russian |
| Русский | English, German, Spanish, French, Polish |
| Deutsch | English, Russian |
| Français, Español, Italiano, Português, Polski | English |

A pair is published when its dictionary knows at least 85% of the 5,000 most frequent words of the book language
(subtitle frequency lists, forms included) and its golden words pass. A pack is about 25–60 MB; translation models are
shared between pairs (German → Russian goes German → English → Russian).

### How the packs are made
InkPeek does not ship third-party dictionaries as they are: the scripts in [`packs/`](packs/README.md) build every pack
from open data, test it and publish it.

- **The dictionary of a pair** (x → y) is our own: the y-language Wiktionary's articles about x-words (German words in
  the Russian Wiktionary for German → Russian), read from [kaikki.org](https://kaikki.org)'s Wiktextract data; for
  English words also the translation tables of the English Wiktionary; [WikDict](https://www.wikdict.com) only where
  Wiktionary has no article. Every article starts with one line — the word with its grammar, then its main meanings:
  "das Haus (Hauses, мн. Häuser) — дом, здание", "gehen (geht, ging, ist gegangen) сильн. — идти". The card's header,
  the dictionary list and the word-by-word gloss all show this line.
- **The grammar** comes from Wiktionary's inflection tables (the German Wiktionary for German, the English one for the
  other languages):
  - a noun's gender as its article — **der / die / das** in German (le / la, el / la, il / la, o / a, de / het in
    other languages), its genitive and plural;
  - a verb's principal parts — German: 3rd person, Präteritum and Perfekt with *haben* or *sein*; English: past and
    participle — and whether it is strong or irregular;
  - every inflected form leads to its word: *Häusern* → Haus, *ging* → gehen, *went* → go, *домами* → дом. Related
    words (an aspect partner, a derived adjective) have their own entries and are not taken for forms.
  - Only what Wiktionary says, no guessing from endings. A word with more than one gender in
    Wiktionary shows them all (*der / das Zentimeter*).
- **Offline translation:** [Firefox Translations](https://github.com/mozilla/translations) models by Mozilla, the best
  one for each direction, mirrored in our release and run on the device by the
  [Bergamot](https://github.com/browsermt/bergamot-translator) engine (C++, built into the app).
- **Language identification:** [fastText](https://fasttext.cc) lid.176; when it is unsure (a word or two), the pack
  dictionary that knows more of the words decides.
- **Tests:** golden words for every language and pair (forms, headers, meanings) must pass before a pack is
  published; `scripts/device-test.sh` checks the app itself on a device — headers, meanings, language identification
  and offline translation of real words and sentences.

### Offline translation
The Translate tab works without the internet: the Bergamot engine runs the models of the language packs on the device. The language of the selection is found by fastText (when it is
unsure — a word or two — by the pack dictionary that knows more of the words); a text in a language without a pack
is named as such ("This looks like French"). AI translation needs none of this.

**It goes through English.** Mozilla trains every model to or from English, so German → Russian is German →
English → Russian. The meaning comes through; nuances can get lost: *du* and *Sie* both become *you*, so the Russian
may pick the wrong one of ты / вы; the speaker's gender may change.

On a Boox Go 6 (Snapdragon 662, 2 GB) a sentence takes 0.1–0.9 s with the pair's models in memory (about 560 MB), or
1–2 s when free memory is short and InkPeek loads one model at a time; the models leave memory after 3 idle minutes.

### AI
Providers: **Claude** (Anthropic), **OpenAI**, **OpenRouter** (hundreds of models, some free), and **any
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

**Privacy:** only the selected text and the tab's request go to the provider you chose: when you open an AI tab, or
Translate while AI is on and online. With AI off nothing leaves the device. No telemetry, no accounts.

### Protocol
| intent | text extra | notes |
|---|---|---|
| `colordict.intent.action.SEARCH` | `EXTRA_QUERY` | the ColorDict / GoldenDict protocol; `EXTRA_GRAVITY`, `EXTRA_HEIGHT` and `EXTRA_FULLSCREEN` are ignored |
| `android.intent.action.PROCESS_TEXT` | `EXTRA_PROCESS_TEXT` | system selection menu, KOReader |
| `android.intent.action.SEND` (`text/plain`) | `EXTRA_TEXT` | Share |

Test from a computer: `adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus`

## Install and build
Release APKs are built and signed by [GitHub Actions](.github/workflows/release.yml) from the tagged commit; each has
a `.sha256` file.

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/device-test.sh        # the language packs on a connected device: headers, forms, language, offline translation
```

The version is `0.3.<number of commits>`; the same number is the version code.

Kotlin, Android Views, Android 11+ (minSdk 30), 64-bit ARM. The translation engine is C++ built with the NDK:
`git clone --recurse-submodules` (or `git submodule update --init --recursive`). Design notes: [docs/spec.md](docs/spec.md).

## License
MIT. Language packs: dictionaries and grammar CC BY-SA 4.0 (Wiktionary contributors, Wiktextract/kaikki.org, WikDict), translation models MPL-2.0 (Mozilla), language identification CC BY-SA 3.0 (fastText).

Third-party code and data: [Bergamot translator](https://github.com/browsermt/bergamot-translator) with marian
(MPL-2.0; the Android build from [RTranslator](https://github.com/niedev/RTranslator)'s fork); the JNI bridge
adapts RTranslator's `BergamotTranslator.cpp` (Apache-2.0, Luca Martino); translation models from Mozilla
(MPL-2.0), downloaded at run time, not in the APK.
