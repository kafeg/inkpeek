"""Golden words on built packs: PACKS_OUT=<out dir> python3 -m unittest discover -s packs/tests (skipped without it)."""
import html
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from inkpeek_packs import stardict  # noqa: E402

OUT = os.environ.get("PACKS_OUT")
ANSWER = {"de": "ru", "en": "ru"}


def load(name):
    r = stardict.Reader(stardict.find(os.path.join(OUT, "build", name)))
    arts = dict(r.words)
    words = [w for w, _ in r.words]
    forms = {}
    for f, i in r.syn:
        forms.setdefault(f, []).append(words[i])
    return arts, forms


@unittest.skipUnless(OUT, "PACKS_OUT not set")
class GoldenTest(unittest.TestCase):
    def test_languages(self):
        for lang in ("de", "en"):
            gfile = os.path.join(os.path.dirname(__file__), "golden", f"{lang}.tsv")
            if not os.path.isdir(os.path.join(OUT, "build", f"grammar-{lang}")):
                continue
            gram, gforms = load(f"grammar-{lang}")
            arts, _ = load(f"dict-{lang}-{ANSWER[lang]}")
            for line in open(gfile, encoding="utf-8"):
                if not line.strip() or line.startswith("#"):
                    continue
                kind, key, want = line.rstrip("\n").split("\t")
                with self.subTest(lang=lang, kind=kind, key=key):
                    if kind == "form":
                        self.assertIn(want, gforms.get(key, []))
                    elif kind == "gram":
                        self.assertIn(want, gram.get(key, "").replace("\t", "\n").splitlines())
                    else:
                        first = re.search(r'<p class="head">(.*?)</p>', arts.get(key, ""))
                        text = html.unescape(re.sub(r"<[^>]+>", "", first.group(1))) if first else ""
                        (self.assertTrue(text.startswith(want), text) if kind == "head" else self.assertIn(want, text))


if __name__ == "__main__":
    unittest.main()
