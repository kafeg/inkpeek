# Offline translation

*Part of the InkPeek documentation; the overview is in the [README](../README.md).*

## Offline translation
The Translate tab works without the internet: the Bergamot engine runs the models of the language packs on the device. The language of the selection is found by fastText (when it is
unsure — a word or two — by the pack dictionary that knows more of the words); a text in a language without a pack
is named as such ("This looks like French"). AI translation needs none of this.

**It goes through English.** Mozilla trains every model to or from English, so German → Russian is German →
English → Russian. The meaning comes through; nuances can get lost: *du* and *Sie* both become *you*, so the Russian
may pick the wrong one of ты / вы; the speaker's gender may change.

On a Boox Go 6 (Snapdragon 662, 2 GB) a sentence takes 0.1–0.9 s with the pair's models in memory (about 560 MB), or
1–2 s when free memory is short and InkPeek loads one model at a time; the models leave memory after 3 idle minutes.

**Three methods** (Languages → InkPeek → Translation method; one for all pairs, the app downloads what your pairs
need and removes what none needs; a method this device cannot run is shown dimmed with the reason):

| method | how | size | needs | measured |
|---|---|---|---|---|
| Fast · Mozilla (default) | the models above, through English | ~31 MB per direction | any device | Go 6: 0.1–0.9 s |
| Direct · OPUS-MT | [Helsinki-NLP](https://github.com/Helsinki-NLP/Tatoeba-Challenge) group models straight between languages (8 models cover the 56 directions of the 8 languages); converted for Bergamot by `packs/` | ~170 MB each | 4 GB of memory | phone (Snapdragon 860): ≈1.5 s per phrase in the app, 1.6 GB; does not fit a 2 GB Go 6 |
| AI model · Hy-MT2 | [Tencent's](https://huggingface.co/tencent/Hy-MT2-1.8B) 1.8B translation model, Q4, run by [llama.cpp](https://github.com/ggml-org/llama.cpp); told both languages | 1.1 GB | 6 GB and a CPU with int8 dot product | phone: ≈2.4 s per phrase in the app, 1.2 GB |

German → Russian, the same sentences: *Er nimmt ihre rechte Hand und legt sie auf den Schläger* — Mozilla: «…берет ее
правой рукой и кладет ее на ракетку»; OPUS-MT: «Он берет ее за правую руку и кладет на ракетку»; *Können Sie mir
bitte sagen, wie spät es ist?* — Mozilla: «Не могли бы вы сказать, сколько времени?»; OPUS-MT: «Не подскажете,
который час?». A pair without a model of the chosen method, or a device that cannot run it now, gets Mozilla. The card
names the model under the translation.
