<p align="center"><img src="docs/img/icon.svg" width="96" alt="InkPeek icon"></p>

# InkPeek

A pop-up dictionary with an AI tutor for Android e-ink readers.

Select a word in your reading app and a calm, high-contrast card opens over the page: entries from your own
StarDict dictionaries, and tabs that ask an AI to translate in context, explain the grammar, list the harder words or
give examples. Close the card and you are on the same page.

<!-- screenshots: docs/img/*.png -->

## What it does
- **Dictionary tab** — your StarDict dictionaries, one per screen, ◀ ▶ to page between them. Inflected forms are
  found through the dictionary's own forms list (`Häuser` → `Haus`), and the base form is then looked up in every
  other dictionary too.
- **AI tabs** — Translate, Grammar, Words, Examples, Explain. Each tab asks once and keeps the answer. Short answers
  with light formatting (bold words from the text, translations in italics). Choose the tabs in settings.
- **Phrases** — select a sentence and the card opens on Translate (configurable).
- **Built for e-ink** — black on white, large text, no animations, no colors.
- **Languages** — the UI follows the system language (English, Russian, German); answers come in the language you
  choose.

## Works with your reader
InkPeek answers the common **ColorDict intent**, the same one used by ColorDict and GoldenDict, so readers that can
call an external dictionary open it as a pop-up:

| reader | how |
|---|---|
| FBReader | Settings → Dictionary → **ColorDict 3** (for words and for the translator) |
| Cool Reader | Settings → Dictionary → **ColorDict new / GoldenDict** |
| Moon+ Reader | Dictionary → **ColorDict** |
| any app | text selection menu → **Look up in InkPeek**, or Share → InkPeek |

If another dictionary app answers the same intent, Android asks once which one to use: choose InkPeek → Always.

<details><summary>Protocol details</summary>

| intent | text extra | notes |
|---|---|---|
| `colordict.intent.action.SEARCH` | `EXTRA_QUERY` | `EXTRA_GRAVITY` and `EXTRA_HEIGHT` are hints; `EXTRA_FULLSCREEN` is ignored (some readers send `true` when their status bar is hidden) |
| `android.intent.action.PROCESS_TEXT` | `EXTRA_PROCESS_TEXT` | system selection menu |
| `android.intent.action.SEND` (`text/plain`) | `EXTRA_TEXT` | Share |

Test from a computer: `adb shell am start -a colordict.intent.action.SEARCH --es EXTRA_QUERY Haus`
</details>

## Dictionaries
Everything lives in one folder on the device: **`/sdcard/InkPeek/`** — `dicts/` (one folder per dictionary) and
`keys/`. InkPeek needs the "all files" permission for it (the app asks).

- **Get dictionaries** — live lists of [Wiktionary dictionaries](https://github.com/xxyzz/wiktionary_stardict)
  (hundreds of language pairs) and [FreeDict](https://freedict.org); filter by language pair, tap to download.
- **Import file** — StarDict as `.zip`, `.tar`, `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tar.zst`, or the loose
  `.ifo` / `.idx` / `.dict(.dz)` / `.syn` files.
- Or copy a StarDict folder into `/sdcard/InkPeek/dicts/`.

InkPeek builds its own index for each dictionary once, in the background (the settings screen shows the progress),
so badly sorted upstream indexes still work; `.dict.dz` is read in place.

## AI
Providers: **Claude** (Anthropic), **OpenAI**, **OpenRouter** (hundreds of models, some free; the model list has a
filter, "free only", a maker filter and sorting). Put the key in settings, or into a file the app reads:

```
/sdcard/InkPeek/keys/anthropic.key
/sdcard/InkPeek/keys/openai.key
/sdcard/InkPeek/keys/openrouter.key
```

```bash
adb shell mkdir -p /sdcard/InkPeek/keys
adb push openrouter.txt /sdcard/InkPeek/keys/openrouter.key
```

Only the selected text and the tab's prompt are sent, only when you open an AI tab (or select a phrase with
"phrases: AI at once" on). No telemetry.

## Install
Download the APK from [Releases](https://github.com/kafeg/inkpeek/releases) — built and signed by
[GitHub Actions](.github/workflows/release.yml) from the tagged commit; a `.sha256` file is attached.

```bash
adb install inkpeek-v0.2.0.apk
```

## Build
```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Kotlin, Android Views, minSdk 26. See [docs/spec.md](docs/spec.md).

## License
MIT. Dictionaries you download keep their own licenses (Wiktionary: CC BY-SA; FreeDict: see each dictionary).
