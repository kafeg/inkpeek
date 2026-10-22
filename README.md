<p align="center"><img src="docs/img/icon.svg" width="96" alt="InkPeek icon"></p>

# InkPeek

**Read books in a language you are learning without leaving the page.**

Tap a word or select a sentence in your e-book reader. A calm black-and-white card opens over the page with the
dictionary entry, a translation that fits the context, and short explanations from an AI tutor. Close the card and
you are back on the same line.

It is also a plain offline pop-up dictionary when you want one: your own StarDict dictionaries (KOReader's folder
works as it is), a small card at the edge of the page, white on black if you like, and no AI at all.

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
<td valign="top"><b>Word by word, offline.</b> A selected sentence is translated word by word from the language packs: each word with
its base form and meaning. Tap a word to open its entry.</td>
<td valign="top"><b>Choose your languages.</b> Download the pairs you read: each pack brings a dictionary, grammar and
offline translation. Add your own StarDict dictionaries next to them.</td>
</tr>
</table>

<p align="center"><sub>Onyx Boox Go 6, FBReader, Franz Kafka «Die Verwandlung» (public domain), German → English.
Answers come in the app's language.</sub></p>

## Quick start
1. **Install** the APK from [Releases](https://github.com/kafeg/inkpeek/releases) and open InkPeek. Allow access to
   files when it asks.
2. **Choose your languages:** Languages → InkPeek → **Download packs…** → tap the pairs of your books. Answers come in
   the app's language. Have dictionaries already? Languages → **Other dictionaries** (below).
3. **Optional — AI:** choose a provider on the **AI** tab and paste your API key. Without it InkPeek works fully
   offline.
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
| The card opens on the dictionary. | The card opens on Translate (or on the word-by-word translation when there is no translation: no pack, no AI). |

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
| Librera | select text → **+** in the selection menu → the first **InkPeek** in the list. Librera sends where the word is: with Card → Appearance → Position → **Where the reader asks** the card opens above or below it |
| AlReaderX | Settings → General settings → **Dictionary for single words** → **GoldenDict/ColorDict 3**; select a word or a passage (Text selection) → the translate icon |
| Cool Reader | Settings → Dictionary → **ColorDict new / GoldenDict**. On Onyx Boox Cool Reader does not offer it (its list there is Onyx's own); Share → InkPeek works, but Cool Reader adds the author and the title to the shared text |
| Moon+ Reader | Dictionary → **ColorDict** (not tested by us) |
| any app | text selection menu → **Look up in InkPeek**, or Share → InkPeek |

If another dictionary app answers the same request, Android asks which one to use: choose InkPeek → Always.

## How it works

### How it works
- [The card, AI and the protocol](docs/how-it-works.md): what each tab does, look-up from any text, Notes and export,
  the card's look; AI providers, keys, prompts, what goes to the network.
- [Languages and packs](docs/languages.md): every pair with its coverage and label, how the packs are made (dictionary,
  grammar, related words, tests), other StarDict dictionaries.
- [Offline translation](docs/translation.md): the three methods — Mozilla through English, OPUS-MT direct, the Hy-MT2
  AI model — with sizes, requirements and speeds.

## Install and build
Release APKs are built and signed by [GitHub Actions](.github/workflows/release.yml) from the tagged commit; each has
a `.sha256` file.

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/device-test.sh        # the language packs on a connected device: headers, forms, language, offline translation
```

The version is `<appVersion>.<number of commits>` (`appVersion` in `gradle.properties`); the commit count is also the version code. What changed: [CHANGELOG.md](CHANGELOG.md).

**Updates:** the version is on the right of the settings' status line; a tap opens About. Once a day, when settings
open, InkPeek asks GitHub for the latest release (one request; About → "Check once a day" turns it off). When a newer
one is out, the version line says so; Update downloads it in the app and Android's installer asks you to confirm (the
first time also to allow InkPeek to install apps).

Kotlin, Android Views, Android 11+ (minSdk 30), 64-bit ARM. The translation engine is C++ built with the NDK:
`git clone --recurse-submodules` (or `git submodule update --init --recursive`). Design notes: [docs/spec.md](docs/spec.md).

## License
MIT. Other dictionaries you download or import keep their own licenses. Language packs: dictionaries and grammar CC BY-SA 4.0 (Wiktionary contributors, Wiktextract/kaikki.org, WikDict), translation models MPL-2.0 (Mozilla), language identification CC BY-SA 3.0 (fastText).

Third-party code and data: [Bergamot translator](https://github.com/browsermt/bergamot-translator) with marian
(MPL-2.0; the Android build from [RTranslator](https://github.com/niedev/RTranslator)'s fork); the JNI bridge
adapts RTranslator's `BergamotTranslator.cpp` (Apache-2.0, Luca Martino); translation models from Mozilla
(MPL-2.0), OPUS-MT models by Helsinki-NLP (CC BY 4.0) and Hy-MT2 by Tencent (Apache-2.0), downloaded at run time, not
in the APK; [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT) runs the AI model.
