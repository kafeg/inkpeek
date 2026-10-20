"""Golden words on built packs: PACKS_OUT=<out dir> python3 -m unittest discover -s packs/tests (skipped without it)."""
import html
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from inkpeek_packs import stardict  # noqa: E402

OUT = os.environ.get("PACKS_OUT")
GOLDEN = os.path.join(os.path.dirname(__file__), "golden")


def load(name):
    r = stardict.Reader(stardict.find(os.path.join(OUT, "build", name)))
    arts = dict(r.words)
    words = [w for w, _ in r.words]
    forms = {}
    for f, i in r.syn:
        forms.setdefault(f, []).append(words[i])
    return arts, forms


def rows(path):
    for line in open(path, encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            yield line.rstrip("\n").split("\t")


@unittest.skipUnless(OUT, "PACKS_OUT not set")
class GoldenTest(unittest.TestCase):
    """Every built grammar-<x> is checked against golden/<x>.tsv, every dict-<x>-<y> against golden/<x>-<y>.tsv."""

    def test_built(self):
        built = os.listdir(os.path.join(OUT, "build"))
        for name in sorted(built):
            key = name.split("-", 1)[1]
            gfile = os.path.join(GOLDEN, f"{key}.tsv")
            self.assertTrue(os.path.exists(gfile), f"no golden words for {name}")
            arts, forms = load(name)
            for kind, word, want in rows(gfile):
                with self.subTest(pack=name, kind=kind, word=word):
                    if kind == "form":
                        self.assertIn(want, forms.get(word, []))
                    elif kind == "gram":
                        self.assertIn(want, arts.get(word, "").replace("\t", "\n").splitlines())
                    else:
                        first = re.search(r'<p class="head">(.*?)</p>', arts.get(word, ""))
                        text = html.unescape(re.sub(r"<[^>]+>", "", first.group(1))) if first else ""
                        (self.assertTrue(text.startswith(want), text) if kind == "head" else self.assertIn(want, text))


if __name__ == "__main__":
    unittest.main()
