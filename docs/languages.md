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
| pair (book → answers) | top-5,000 coverage, label | dictionary | where the articles come from | offline translation (Mozilla model) | download |
|---|---|---|---|---|---|
| Bulgarian → English | **82%** basic | 23,828 words, 137,329 forms | 21,253 from English Wiktionary; 2,575 from WikDict | Bulgarian → English (base-memory 3.0) | 28 MB |
| Czech → English | **85%** basic | 52,837 words, 359,930 forms | 46,906 from English Wiktionary; 5,931 from WikDict | Czech → English (base-memory 3.0) | 30 MB |
| Danish → English | **87%** full | 26,159 words, 112,120 forms | 24,445 from English Wiktionary; 1,714 from WikDict | Danish → English (tiny 3.0) | 18 MB |
| German → English | **93%** full | 124,694 words, 380,477 forms | 90,522 from English Wiktionary; 34,172 from WikDict | German → English (base-memory 3.0) | 38 MB |
| Spanish → English | **93%** full | 111,500 words, 1,042,160 forms | 107,891 from English Wiktionary; 3,609 from WikDict | Spanish → English (base-memory 3.0) | 37 MB |
| Finnish → English | **94%** full | 186,495 words, 18,258,880 forms | 173,455 from English Wiktionary; 13,040 from WikDict | Finnish → English (base-memory 3.0) | 82 MB |
| French → English | **92%** full | 165,254 words, 328,414 forms | 93,159 from English Wiktionary; 72,095 from WikDict | French → English (base-memory 3.0) | 37 MB |
| Hungarian → English | **81%** basic | 37,803 words, 900,812 forms | 37,803 from English Wiktionary | Hungarian → English (base-memory 3.1) | 32 MB |
| Italian → English | **89%** full | 157,482 words, 821,421 forms | 154,817 from English Wiktionary; 2,665 from WikDict | Italian → English (base-memory 3.0) | 37 MB |
| Dutch → English | **90%** full | 86,166 words, 122,932 forms | 67,407 from English Wiktionary; 18,759 from WikDict | Dutch → English (base-memory 3.0) | 33 MB |
| Polish → English | **93%** full | 104,434 words, 854,814 forms | 90,389 from English Wiktionary; 14,045 from WikDict | Polish → English (base-memory 3.0) | 36 MB |
| Portuguese → English | **88%** full | 75,078 words, 344,218 forms | 73,017 from English Wiktionary; 2,061 from WikDict | Portuguese → English (base-memory 3.0) | 32 MB |
| Romanian → English | **68%** basic | 98,049 words, 404,721 forms | 98,049 from English Wiktionary | Romanian → English (tiny 3.0) | 21 MB |
| Russian → English | **92%** full | 77,198 words, 505,749 forms | 57,406 from English Wiktionary; 19,792 from WikDict | Russian → English (base-memory 3.1) | 38 MB |
| Swedish → English | **93%** full | 68,850 words, 304,960 forms | 58,157 from English Wiktionary; 10,693 from WikDict | Swedish → English (tiny 3.0) | 22 MB |
| Ukrainian → English | **58%** experimental | 29,497 words, 267,928 forms | 29,497 from English Wiktionary | Ukrainian → English (tiny 3.0) | 19 MB |
| English → German | **94%** full | 72,130 words, 62,185 forms | 67,705 from English Wiktionary's translation tables; 3,950 from German Wiktionary; 475 from WikDict | English → German (base-memory 3.0) | 38 MB |
| Russian → German | **68%** basic | 24,360 words, 137,673 forms | 1,497 from German Wiktionary; 22,863 from WikDict | Russian → English → German (base-memory 3.0, base-memory 3.1) | 57 MB |
| English → Spanish | **97%** full | 68,557 words, 62,046 forms | 62,872 from English Wiktionary's translation tables; 5,047 from Spanish Wiktionary; 638 from WikDict | English → Spanish (base-memory 3.0) | 37 MB |
| English → French | **98%** full | 131,822 words, 83,642 forms | 72,059 from English Wiktionary's translation tables; 59,371 from French Wiktionary; 392 from WikDict | English → French (base-memory 3.0) | 42 MB |
| English → Italian | **94%** full | 61,305 words, 56,844 forms | 51,462 from English Wiktionary's translation tables; 9,365 from Italian Wiktionary; 478 from WikDict | English → Italian (base-memory 3.0) | 37 MB |
| English → Polish | **99%** full | 110,727 words, 82,691 forms | 67,298 from English Wiktionary's translation tables; 43,215 from Polish Wiktionary; 214 from WikDict | English → Polish (base-memory 3.0) | 39 MB |
| English → Portuguese | **93%** full | 57,902 words, 54,405 forms | 52,706 from English Wiktionary's translation tables; 4,028 from Portuguese Wiktionary; 1,168 from WikDict | English → Portuguese (base-memory 3.0) | 37 MB |
| German → Russian | **89%** full | 93,836 words, 321,070 forms | 83,779 from Russian Wiktionary; 10,057 from WikDict | German → English → Russian (base 3.0, base-memory 3.0) | 67 MB |
| English → Russian | **98%** full | 94,664 words, 77,351 forms | 59,260 from English Wiktionary's translation tables; 34,942 from Russian Wiktionary; 462 from WikDict | English → Russian (base 3.0) | 47 MB |
| Spanish → Russian | **86%** full | 21,857 words, 233,408 forms | 15,667 from Russian Wiktionary; 6,190 from WikDict | Spanish → English → Russian (base 3.0, base-memory 3.0) | 61 MB |
| French → Russian | **86%** full | 36,217 words, 109,621 forms | 20,817 from Russian Wiktionary; 15,400 from WikDict | French → English → Russian (base 3.0, base-memory 3.0) | 63 MB |
| Italian → Russian | **75%** basic | 22,677 words, 183,963 forms | 15,726 from Russian Wiktionary; 6,951 from WikDict | Italian → English → Russian (base 3.0, base-memory 3.0) | 62 MB |
| Dutch → Russian | **73%** basic | 20,111 words, 37,564 forms | 11,486 from Russian Wiktionary; 8,625 from WikDict | Dutch → English → Russian (base 3.0, base-memory 3.0) | 61 MB |
| Polish → Russian | **89%** full | 37,609 words, 258,593 forms | 18,764 from Russian Wiktionary; 18,845 from WikDict | Polish → English → Russian (base 3.0, base-memory 3.0) | 62 MB |
| Portuguese → Russian | **74%** basic | 17,907 words, 86,811 forms | 12,767 from Russian Wiktionary; 5,140 from WikDict | Portuguese → English → Russian (base 3.0, base-memory 3.0) | 59 MB |
| Ukrainian → Russian | **43%** experimental | 36,025 words, 105,002 forms | 36,025 from Russian Wiktionary | Ukrainian → English → Russian (base 3.0, tiny 3.0) | 49 MB |

<sub>Packs of 2026-10-23; table made by [`packs/report.py`](../packs/report.py). Download: the dictionary, the models of the route (shared between pairs) and language identification (1 MB).</sub>
<!-- packs:end -->

Grammar of the book languages (the first line of every article and the forms that lead to it):

| book language | grammar from | the header shows |
|---|---|---|
| German | German Wiktionary; strong / weak from English Wiktionary | der / die / das, genitive and plural; verbs: 3rd person, Präteritum, Perfekt with *haben* or *sein*, strong or irregular |
| English | English Wiktionary | plural; verbs: past and participle, irregular |
| Russian, Ukrainian | English Wiktionary | gender and plural; irregular verbs |
| French, Spanish, Italian, Portuguese, Dutch, Polish | English Wiktionary | gender (with the article: le / la, el / la, il / la, o / a, de / het), plural; verb forms where Wiktionary's tables have them |
| Czech, Bulgarian, Finnish, Hungarian, Swedish, Danish, Romanian | English Wiktionary | gender where the language has one, plural; the forms of Wiktionary's tables |

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
