# Offline translation

*Part of the InkPeek documentation; the overview is in the [README](../README.md).*

## Offline translation
The Translate tab works without the internet: the Bergamot engine runs the models of the language packs on the device. The language of the selection is found by fastText (when it is
unsure — a word or two — by the pack dictionary that knows more of the words); a text in a language without a pack
is named as such ("This looks like French"). AI translation needs none of this.

**It goes through English.** Mozilla trains every model to or from English, so German → Russian is German →
English → Russian. The meaning comes through; nuances can get lost: *du* and *Sie* both become *you*, so the Russian
may pick the wrong one of ты / вы; the speaker's gender may change.

One model is in memory at a time. With plenty of free memory it stays there between translations and leaves after a
few idle minutes; when memory is short, it is freed after each translation.

**Three methods, or Off** (Languages → Translation; one for all pairs, the app downloads what your pairs need; a
method this device cannot run is shown dimmed with the reason; after a download the page shows how long a phrase
takes on this device; downloaded models stay when you switch, the list under the methods removes one):

| method | how | size | needs |
|---|---|---|---|
| Fast · Mozilla (default) | the models above, through English | ~31 MB per direction | any device |
| Direct · OPUS-MT | [Helsinki-NLP](https://github.com/Helsinki-NLP/Tatoeba-Challenge) group models straight between languages (a few multilingual models cover every direction); converted for Bergamot by `packs/` | ~170 MB each | 4 GB of memory |
| AI model · Hy-MT2 | [Tencent's](https://huggingface.co/tencent/Hy-MT2-1.8B) 1.8B translation model, Q4, run by [llama.cpp](https://github.com/ggml-org/llama.cpp); told both languages | 1.1 GB | 6 GB of memory and a CPU with int8 dot product |

German → Russian, the same sentences: *Er nimmt ihre rechte Hand und legt sie auf den Schläger* — Mozilla: «…берет ее
правой рукой и кладет ее на ракетку»; OPUS-MT: «Он берет ее за правую руку и кладет на ракетку»; *Können Sie mir
bitte sagen, wie spät es ist?* — Mozilla: «Не могли бы вы сказать, сколько времени?»; OPUS-MT: «Не подскажете,
который час?». A pair without a model of the chosen method, or a device that cannot run it now, gets Mozilla. The card
names the model under the translation.
