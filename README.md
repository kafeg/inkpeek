# InkPeek

A small pop-up dictionary for Android e-ink readers.

Select a word in your reading app — a calm, high-contrast card opens over the page with entries from your local
StarDict dictionaries. Tap **Translate**, **Grammar**, **Explain** or **Examples** to ask an AI (Claude) about the
word or phrase. Close the card and you are back on the same page.

- Works with readers that call an external dictionary via the ColorDict intent, the text-selection menu or Share.
- Built for e-ink: black on white, large text, no animations.
- Local dictionaries are instant and offline; AI is used only on request (and, optionally, for whole phrases).

Status: early development. See [docs/spec.md](docs/spec.md).

## Build
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License
MIT
