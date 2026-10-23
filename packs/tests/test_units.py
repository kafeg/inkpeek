"""Unit tests on small inputs: run with `python3 -m unittest discover -s packs/tests`."""
import gzip
import os
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from inkpeek_packs import dictionary, grammar, related, stardict  # noqa: E402


class StarDictTest(unittest.TestCase):
    def test_dictzip_chunks_inflate_alone(self):
        data = os.urandom(5000) + b"x" * (stardict.CHUNK * 2 + 17)
        dz = stardict.dictzip(data)
        self.assertEqual(gzip.decompress(dz), data)
        xlen = struct.unpack("<H", dz[10:12])[0]
        ver, chlen, count = struct.unpack("<HHH", dz[16:22])
        sizes = struct.unpack(f"<{count}H", dz[22:22 + 2 * count])
        self.assertEqual((ver, chlen, count), (1, stardict.CHUNK, 3))
        pos = 12 + xlen
        for i, n in enumerate(sizes):  # the way the app reads one chunk
            chunk = zlib.decompressobj(-15).decompress(dz[pos:pos + n])
            self.assertEqual(chunk, data[i * chlen:(i + 1) * chlen])
            pos += n

    def test_write_and_read(self):
        with tempfile.TemporaryDirectory() as d:
            base = os.path.join(d, "t")
            st = stardict.write(base, {"Haus": "дом", "abend": "вечер", "Ärger": "досада"}, [("Häuser", "Haus"), ("x", "nope")])
            self.assertEqual((st["words"], st["forms"]), (3, 1))
            r = stardict.Reader(base)
            self.assertEqual([w for w, _ in r.words], ["abend", "Haus", "Ärger"])  # ASCII case-insensitive, then bytes
            self.assertEqual(r.syn, [("Häuser", 1)])


def rec(word, pos, **kw):
    return dict(word=word, pos=pos, lang_code="de", **kw)


class GrammarTest(unittest.TestCase):
    def test_dewiktionary_noun_and_verb(self):
        g = grammar.Grammar("de")
        grammar.from_dewiktionary([
            rec("Haus", "noun", tags=["neuter"], forms=[
                {"form": "Häuser", "tags": ["nominative", "plural"]}, {"form": "Hauses", "tags": ["genitive", "singular"]},
                {"form": "Häusern", "tags": ["dative", "plural"]}]),
            rec("gehen", "verb", forms=[
                {"form": "geht", "tags": ["present"], "pronouns": ["er"]}, {"form": "ging", "tags": ["past"]},
                {"form": "gegangen", "tags": ["participle-2", "perfect"]}, {"form": "sein", "tags": ["auxiliary", "perfect"]},
                {"form": "ich ging", "tags": ["past"], "source": "Flexion:gehen"}]),
            rec("ging", "verb", tags=["form-of"], senses=[{"form_of": [{"word": "gehen"}]}]),
        ], g)
        self.assertEqual(g.lemmas[("Haus", "noun")].line(), "pos=noun\tg=n\tgen=Hauses\tpl=Häuser")
        self.assertEqual(g.forms["Häusern"], ["Haus"])
        self.assertEqual(g.forms["ging"], ["gehen"])
        head, tail = dictionary.header("gehen", g.lemmas[("gehen", "verb")], "de", "ru")
        self.assertEqual(tail, "geht, ging, ist gegangen")
        self.assertEqual(dictionary.header("Haus", g.lemmas[("Haus", "noun")], "de", "ru"), ("das Haus", "Hauses, мн. Häuser"))

    def test_enwiktionary_irregular_and_overrides(self):
        g = grammar.Grammar("en")
        en = lambda w, p, **kw: dict(word=w, pos=p, lang_code="en", **kw)
        grammar.from_enwiktionary([
            en("go", "verb", categories=["English irregular verbs"], senses=[{"glosses": ["to move"]}],
               forms=[{"form": "went", "tags": ["past"]}, {"form": "gone", "tags": ["participle", "past"]}]),
            en("went", "verb", senses=[{"form_of": [{"word": "go"}]}, {"form_of": [{"word": "wend"}]}]),
            en("walk", "verb", senses=[{"glosses": ["to step"]}], forms=[{"form": "walked", "tags": ["past"]}]),
        ], g, irregular_category="English irregular verbs")
        self.assertEqual(g.lemmas[("go", "verb")].cls, "irregular")
        self.assertEqual(g.lemmas[("walk", "verb")].cls, "")       # no guessing from -ed
        self.assertEqual(g.forms["went"][0], "go")                 # the lemma whose own table lists it, first
        with tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False, encoding="utf-8") as f:
            f.write("went\t-wend\n")
        grammar.overrides(f.name, g)
        os.unlink(f.name)
        self.assertEqual(g.forms["went"], ["go"])

    def test_related_words_are_not_forms(self):
        g = grammar.Grammar("ru")
        ru = lambda w, p, forms: dict(word=w, pos=p, lang_code="ru", senses=[{"glosses": ["x"]}], forms=forms)
        grammar.from_enwiktionary([
            ru("ребёнок", "noun", [{"form": "дете́й", "tags": ["genitive", "plural"]}, {"form": "де́тский", "tags": ["adjective", "relational"]}]),
            ru("знать", "verb", [{"form": "узна́ть", "tags": ["perfective"]}, {"form": "зна́ю", "tags": ["first-person", "present", "singular"]}]),
        ], g)
        self.assertEqual(g.forms["детей"], ["ребёнок"])
        self.assertEqual(g.forms["знаю"], ["знать"])
        self.assertNotIn("детский", g.forms)
        self.assertNotIn("узнать", g.forms)


class DictionaryTest(unittest.TestCase):
    def test_first_line(self):
        g = grammar.Grammar("de")
        g.lemma("Haus", "noun").gender.add("n"); g.lemmas[("Haus", "noun")].plural.append("Häuser")
        g.form("Häusern", "Haus")
        arts, syn, st = dictionary.build("de", "ru", [rec("Haus", "noun", senses=[
            {"glosses": ["дом, здание"], "examples": [{"text": "Das Haus ist alt."}]}, {"glosses": ["семья (род)"]}])], g)
        self.assertIn('<p class="head"><b>das Haus</b> (мн. Häuser) — дом, здание, семья</p>', arts["Haus"])
        self.assertEqual(dictionary._short(["das Haus (abode)", "das Haus (building)", "das Haus, die Kammer (chamber (of parliament))", "gehen) x"]),
                         "das Haus, die Kammer, gehen x")
        self.assertIn(("Häusern", "Haus"), syn)


class TablesTest(unittest.TestCase):
    def test_translation_tables(self):
        r = dict(word="house", pos="noun", lang_code="en", translations=[
            {"lang_code": "de", "word": "Haus", "sense": "human abode", "tags": ["neuter"]},
            {"lang_code": "de", "word": "Huus", "sense": "human abode", "tags": ["Alemannic-German", "neuter"]},
            {"lang_code": "ru", "word": "дом", "sense": "human abode", "tags": ["masculine"]},
            {"lang_code": "de", "word": "Kammer", "sense": "chamber", "tags": ["feminine"]}])
        self.assertEqual(dictionary.translations([r], "de"), {"house": [("noun", [("das Haus (human abode)", []), ("die Kammer (chamber)", [])])]})
        self.assertEqual(dictionary.translations([r], "ru")["house"][0][1][0][0], "дом (human abode)")


if __name__ == "__main__":
    unittest.main()


class RelatedTest(unittest.TestCase):
    """Related words, synonyms, expressions: from both editions, frequent first, only words a reader meets."""
    def test_dewiktionary_article_cell_is_not_a_form(self):
        g = grammar.Grammar("de")
        grammar.from_dewiktionary([{"word": "zweite Lautverschiebung", "pos": "phrase", "forms": [
            {"form": "keine -", "tags": ["nominative", "plural"]}, {"form": "keine", "tags": ["accusative", "plural"]}]}], g)
        self.assertNotIn("keine", g.forms)

    def test_nested_sense_keeps_its_parent(self):
        r = {"word": "bok", "senses": [{"glosses": ["book:", "collection of sheets of paper"]}, {"glosses": ["book:", "a work of literature"]},
                                        {"glosses": ["beech"]}]}
        ss = dictionary.senses(r)
        self.assertEqual(ss[0][0], "book — collection of sheets of paper")
        self.assertEqual(dictionary._short([g for g, _ in ss]), "book, beech")

    def test_coverage(self):
        import tempfile
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as f:
            f.write("the 9\n, 8\nhouse 7\n123 6\ngo 5\n")
        self.assertEqual(related.coverage({"the", "go"}, f.name, n=3), 66.7)

    def test_collect_and_order(self):
        native = [{"word": "machen", "derived": [{"word": "zumachen"}, {"word": "aufmachen"}, {"word": "Machenschaft"}],
                   "expressions": [{"word": "das macht nichts"}], "synonyms": [{"word": "tun"}], "antonyms": [{"word": "unterlassen"}],
                   "sounds": [{"ipa": "[ˈmaxn̩]"}], "etymology_texts": ["althochdeutsch mahhōn"],
                   "forms": [{"form": "macht", "tags": ["present"]}, {"form": "weak", "tags": ["table-tags"]}]},
                  {"word": "Rarität", "derived": [{"word": "x"}]}]
        en = [{"word": "machen", "derived": [{"word": "aufmachen"}, {"word": "Kleider machen Leute"}],
               "senses": [{"synonyms": [{"word": "herstellen"}]}]}]
        data = {}
        related.collect(native, data); related.collect(en, data)
        ranks = {"machen": 1, "aufmachen": 5, "zumachen": 9, "tun": 2, "kleider": 300, "leute": 40, "das": 0, "macht": 3, "nichts": 4}
        lines = related.lines(data, ranks)
        self.assertEqual(1, len(lines))  # "Rarität" is not a word a reader meets
        word, rel, syn, ant, expr, ipa, ety, forms = lines[0].split("\t")
        self.assertEqual("machen", word)
        self.assertEqual(["aufmachen", "zumachen", "Machenschaft"], rel.split("|"))  # frequent first, unknown last
        self.assertEqual(["tun", "herstellen"], syn.split("|"))
        self.assertEqual("unterlassen", ant)
        self.assertEqual(["das macht nichts", "Kleider machen Leute"], expr.split("|"))
        self.assertEqual("[ˈmaxn̩]", ipa)
        self.assertEqual("althochdeutsch mahhōn", ety)
        self.assertEqual("macht:present", forms)
