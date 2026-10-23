# Languages and packs

*Part of the InkPeek documentation; the overview is in the [README](../README.md).*

## Languages
Settings → Languages → **InkPeek**: your packs — the box says the card uses it, ✕ removes it (models shared with
another pair stay); **Download packs…** lists every pair InkPeek has, a tap downloads one. Packs come only from this
project's [`packs` release](https://github.com/kafeg/inkpeek/releases/tag/packs); when it has newer files, an
update line appears. **What the card takes from the language packs**: words (articles and word by word), grammar (the
header) and offline translation, each on or off — keep the grammar and the translation and take the words from your
own dictionaries, if you like. The text's language is always told by the packs. **Languages → Article**: the rows under a pack's full article — related
words, synonyms, expressions, forms, origin (origin off at first) — and the help.

Packs now — every pair, what is inside and how much of real text it knows. **Top-5,000 coverage**: take the 5,000 most
frequent words of the language you read (the pair's first language; frequency from a large corpus of subtitles,
[FrequencyWords](https://github.com/hermitdave/FrequencyWords), OpenSubtitles 2018) and count how many of them the
pair's dictionary knows, as a word or as one of its inflected forms. German → Russian at 89% knows 4,450 of the 5,000
most frequent German words. A pair is published when its golden words pass, with a label on the
download page: **full** — 85 % or more and offline translation; **basic** — 60 % or more (Russian → German: the German
Wiktionary has few Russian articles); **experimental** — below, for a language with little open data.

<!-- packs:begin -->
| pair (book → answers) | top-5,000 coverage | dictionary | where the articles come from | offline translation (Mozilla model) | download |
|---|---|---|---|---|---|
| German → English | **93%** | 124,522 words, 380,350 forms | 90,302 from English Wiktionary; 34,220 from WikDict | German → English (base-memory 3.0) | 34 MB |
| Spanish → English | **93%** | 111,167 words, 1,037,106 forms | 107,552 from English Wiktionary; 3,615 from WikDict | Spanish → English (base-memory 3.0) | 35 MB |
| French → English | **92%** | 165,040 words, 327,505 forms | 92,890 from English Wiktionary; 72,150 from WikDict | French → English (base-memory 3.0) | 34 MB |
| Italian → English | **89%** | 157,246 words, 821,142 forms | 154,580 from English Wiktionary; 2,666 from WikDict | Italian → English (base-memory 3.0) | 34 MB |
| Dutch → English | **90%** | 86,014 words, 122,655 forms | 67,235 from English Wiktionary; 18,779 from WikDict | Dutch → English (base-memory 3.0) | 30 MB |
| Polish → English | **93%** | 104,285 words, 853,709 forms | 90,234 from English Wiktionary; 14,051 from WikDict | Polish → English (base-memory 3.0) | 33 MB |
| Portuguese → English | **88%** | 74,816 words, 343,230 forms | 72,750 from English Wiktionary; 2,066 from WikDict | Portuguese → English (base-memory 3.0) | 30 MB |
| Russian → English | **92%** | 76,888 words, 503,478 forms | 57,053 from English Wiktionary; 19,835 from WikDict | Russian → English (base-memory 3.1) | 32 MB |
| English → German | **94%** | 71,889 words, 61,983 forms | 67,486 from English Wiktionary's translation tables; 3,951 from German Wiktionary; 452 from WikDict | English → German (base-memory 3.0) | 31 MB |
| Russian → German | **68%** | 24,360 words, 137,381 forms | 1,497 from German Wiktionary; 22,863 from WikDict | Russian → English → German (base-memory 3.0, base-memory 3.1) | 51 MB |
| English → Spanish | **97%** | 68,351 words, 61,857 forms | 62,673 from English Wiktionary's translation tables; 5,054 from Spanish Wiktionary; 624 from WikDict | English → Spanish (base-memory 3.0) | 30 MB |
| English → French | **98%** | 131,487 words, 83,361 forms | 71,679 from English Wiktionary's translation tables; 59,429 from French Wiktionary; 379 from WikDict | English → French (base-memory 3.0) | 35 MB |
| English → Italian | **94%** | 61,047 words, 56,631 forms | 51,208 from English Wiktionary's translation tables; 9,371 from Italian Wiktionary; 468 from WikDict | English → Italian (base-memory 3.0) | 30 MB |
| English → Polish | **99%** | 109,446 words, 82,152 forms | 65,558 from English Wiktionary's translation tables; 43,689 from Polish Wiktionary; 199 from WikDict | English → Polish (base-memory 3.0) | 32 MB |
| English → Portuguese | **93%** | 57,733 words, 54,244 forms | 52,544 from English Wiktionary's translation tables; 4,032 from Portuguese Wiktionary; 1,157 from WikDict | English → Portuguese (base-memory 3.0) | 30 MB |
| German → Russian | **89%** | 93,836 words, 321,072 forms | 83,779 from Russian Wiktionary; 10,057 from WikDict | German → English → Russian (base 3.0, base-memory 3.0) | 63 MB |
| English → Russian | **98%** | 94,485 words, 77,116 forms | 59,033 from English Wiktionary's translation tables; 35,008 from Russian Wiktionary; 444 from WikDict | English → Russian (base 3.0) | 40 MB |
| Spanish → Russian | **86%** | 21,857 words, 233,388 forms | 15,667 from Russian Wiktionary; 6,190 from WikDict | Spanish → English → Russian (base 3.0, base-memory 3.0) | 60 MB |
| French → Russian | **86%** | 36,217 words, 109,603 forms | 20,817 from Russian Wiktionary; 15,400 from WikDict | French → English → Russian (base 3.0, base-memory 3.0) | 60 MB |
| Polish → Russian | **89%** | 37,609 words, 258,327 forms | 18,764 from Russian Wiktionary; 18,845 from WikDict | Polish → English → Russian (base 3.0, base-memory 3.0) | 59 MB |

<sub>Packs of 2026-10-20; table made by [`packs/report.py`](../packs/report.py). Download: the dictionary, the models of the route (shared between pairs) and language identification (1 MB).</sub>
<!-- packs:end -->

Grammar of the book languages (the first line of every article and the forms that lead to it):

| book language | grammar from | the header shows |
|---|---|---|
| German | German Wiktionary; strong / weak from English Wiktionary | der / die / das, genitive and plural; verbs: 3rd person, Präteritum, Perfekt with *haben* or *sein*, strong or irregular |
| English | English Wiktionary | plural; verbs: past and participle, irregular |
| Russian | English Wiktionary | gender and plural; irregular verbs |
| French, Spanish, Italian, Portuguese, Dutch, Polish | English Wiktionary | gender (with the article: le / la, el / la, il / la, o / a, de / het), plural; verb forms where Wiktionary's tables have them |

In every language each inflected form in Wiktionary's tables leads to its word (the "forms" column above): *Häusern* →
Haus, *went* → go, *домами* → дом, *chevaux* → cheval.

## How the packs are made
InkPeek does not ship third-party dictionaries as they are: the scripts in [`packs/`](../packs/README.md) build every pack
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
- **Related words, synonyms, expressions, forms, origin** of the book language's words: its own Wiktionary first
  (de.wiktionary for German), then the English one, kept for the words of the frequency list and sorted by frequency;
  one file next to the dictionary, read by binary search.
- **Offline translation:** [Firefox Translations](https://github.com/mozilla/translations) models by Mozilla, the best
  one for each direction, mirrored in our release and run on the device by the
  [Bergamot](https://github.com/browsermt/bergamot-translator) engine (C++, built into the app); OPUS-MT and Hy-MT2 for
  the other methods ([offline translation](translation.md)).
- **Language identification:** [fastText](https://fasttext.cc) lid.176; when it is unsure (a word or two), the pack
  dictionary that knows more of the words decides.
- **Tests:** golden words for every language and pair (forms, headers, meanings) must pass before a pack is
  published; `scripts/device-test.sh` checks the app itself on a device — headers, meanings, language identification
  and offline translation of real words and sentences.

## Other dictionaries
Settings → Languages → **Other dictionaries**: any StarDict dictionary, the format of KOReader, GoldenDict and
ColorDict. They live in one folder — `InkPeek/dicts` unless you choose another; choose KOReader's
(`koreader/data/dict`) and both apps share the same dictionaries (InkPeek offers the folders of KOReader, ColorDict and
GoldenDict it finds). Downloads and imports go into that folder; each dictionary can be switched off or deleted.

- **Download** from public sources, each of which can be switched off: [WikDict](https://www.wikdict.com) (short
  translations, part of speech and gender — ★ marks the ones into the app's language),
  [Wiktionary by xxyzz](https://github.com/xxyzz/wiktionary_stardict) (full articles) and
  [FreeDict](https://freedict.org). Their licenses are their own.
- **Import**: pick a dictionary's files together (`.ifo`, `.idx` or `.idx.gz`, `.dict` or `.dict.dz`, `.syn` if any)
  or an archive with them: zip, tar, tar.gz, tar.bz2, tar.xz, tar.zst.
- In the card they come after the packs, each line with the dictionary's name and its first meaning; for them the
  header is the selected word itself.
