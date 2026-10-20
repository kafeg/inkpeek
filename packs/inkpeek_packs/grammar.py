"""Grammar of one language from Wiktionary data: lemmas with gender, plural, verb principal parts and the
strong / weak / irregular mark, and every inflected form → its lemmas. Only what Wiktionary says; no guessing."""
import re
from collections import defaultdict

GENDER = {"masculine": "m", "feminine": "f", "neuter": "n"}
# Forms that are not a usable spelling of the word, or a word of their own.
SKIP_FORM = {"table-tags", "inflection-template", "class", "romanization", "auxiliary", "diminutive", "augmentative",
             "alternative", "obsolete", "archaic", "dialectal", "misspelling", "abbreviation", "canonical", "nonstandard",
             "error-unrecognized-form", "rare", "variant", "feminine", "masculine"}
# Related words that Wiktionary lists among the forms: an aspect partner (знать → узнать), a relational or possessive
# adjective (собака → собачий), an abstract or verbal noun. They have their own entries; not inflections.
RELATED = {"relational", "possessive", "abstract-noun", "noun-from-verb"}
ASPECT = {"perfective", "imperfective", "semelfactive", "iterative", "dated", "infinitive"}
SKIP_SENSE = {"obsolete", "archaic", "misspelling", "nonstandard", "dialectal", "rare"}
PRONOUN = re.compile(r"^(ich|du|er/sie/es|wir|ihr|sie|Sie)\s+")
CLASS_WORDS = ("irregular", "strong", "weak", "mixed")
# Wiktionary marks stress in Russian and Ukrainian forms (до́ма); books do not.
STRESS = re.compile("[\u0300\u0301]")
UNSTRESS = {"ru", "uk"}


class Lemma:
    __slots__ = ("word", "pos", "gender", "plural", "gen", "pres3", "past", "pp", "aux", "cmp", "sup", "cls")

    def __init__(self, word, pos):
        self.word, self.pos, self.gender, self.cls = word, pos, set(), ""
        self.plural, self.gen, self.pres3, self.past, self.pp, self.aux, self.cmp, self.sup = ([] for _ in range(8))

    def line(self):
        """One article line: pos=noun<TAB>g=n<TAB>pl=Häuser … (only the fields that are set)."""
        f = [("pos", self.pos), ("g", "".join(sorted(self.gender))), ("gen", _first(self.gen)), ("pl", _first(self.plural)),
             ("pres3", _first(self.pres3)), ("past", _first(self.past)), ("pp", _first(self.pp)), ("aux", _first(self.aux)),
             ("cmp", _first(self.cmp)), ("sup", _first(self.sup)), ("class", self.cls)]
        return "\t".join(f"{k}={v}" for k, v in f if v)


def _first(xs):
    return xs[0] if xs else ""


def _add(xs, x):
    x = STRESS.sub("", x) if x else x
    if x and x not in xs and x != "—":
        xs.append(x)


class Grammar:
    def __init__(self, lang):
        self.lang = lang
        self.lemmas = {}                      # (word, pos) → Lemma
        self.forms = defaultdict(list)        # form → lemmas, first the one whose own table lists the form

    def lemma(self, word, pos):
        word = self.plain(word)
        return self.lemmas.setdefault((word, pos), Lemma(word, pos))

    def plain(self, s):
        return STRESS.sub("", s) if self.lang in UNSTRESS else s

    def form(self, form, lemma, table=True):
        form, lemma = self.plain(PRONOUN.sub("", form.strip().rstrip("!"))), self.plain(lemma)
        if not form or " " in form or form == lemma:
            return
        ls = self.forms[form]
        if lemma in ls:
            return
        ls.insert(0, lemma) if table and ls else ls.append(lemma)

    def entries(self):
        """Headword → article (one line per part of speech): words with a fact (gender, forms, class) and the base
        forms that inflected forms point to; a bare "pos=noun" of an uninflected word helps nobody."""
        targets = {l for ls in self.forms.values() for l in ls}
        out = defaultdict(list)
        for (w, _), L in sorted(self.lemmas.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")):
            line = L.line()
            if "\t" in line or w in targets:
                out[w].append(line)
        return {w: "\n".join(ls) for w, ls in out.items()}

    def syn(self):
        return [(f, l) for f, ls in self.forms.items() for l in ls]


def marginal(r):
    """An entry whose every sense is archaic, rare or regional ("die Gift" = gift, archaic; "der Gift", South German):
    it must not add a gender or forms to the common word."""
    ss = r.get("senses") or []
    return bool(ss) and all(set(s.get("tags") or []) & (SKIP_SENSE | {"regional"}) or
                            any(c.isupper() for t in s.get("tags") or [] for c in t) for s in ss)


def from_dewiktionary(records, g):
    """German from de.wiktionary: gender, plural, genitive, the conjugation table, and every form."""
    for r in records:
        w, pos, tags = r["word"], r.get("pos"), r.get("tags", [])
        if "form-of" in tags:
            for s in r.get("senses", []):
                if SKIP_SENSE & set(s.get("tags", [])):
                    continue
                for fo in s.get("form_of", []) or []:
                    if fo.get("word") and fo["word"] != w:
                        g.form(w, fo["word"], table=False)
            continue
        if marginal(r):
            continue
        L = g.lemma(w, pos)
        L.gender |= {GENDER[t] for t in tags if t in GENDER}
        for f in r.get("forms", []):
            t, x = set(f.get("tags", [])), f.get("form", "")
            if not x or t & {"obsolete", "archaic"}:
                continue
            if pos == "noun":
                if {"nominative", "plural"} <= t:
                    _add(L.plural, x)
                if {"genitive", "singular"} <= t:
                    _add(L.gen, x)
                if t & {"nominative", "genitive", "dative", "accusative"}:
                    g.form(x, w)
            elif pos == "verb":
                if f.get("source"):            # the full conjugation table
                    g.form(x, w)
                    continue
                if "present" in t and "er" in (f.get("pronouns") or []):
                    _add(L.pres3, x)
                if t == {"past"}:
                    _add(L.past, x)
                if "participle-2" in t:
                    _add(L.pp, x)
                if "auxiliary" in t:
                    _add(L.aux, x)
                if t & {"present", "past", "subjunctive-ii", "imperative", "participle-2"}:
                    g.form(x, w)
            elif pos == "adj":
                if "comparative" in t:
                    _add(L.cmp, x)
                if "superlative" in t:
                    _add(L.sup, x)
                g.form(re.sub(r"^am ", "", x), w)
            elif t and not t & SKIP_FORM:
                g.form(x, w)


def from_enwiktionary(records, g, irregular_category=None, fill=False):
    """Any language from en.wiktionary. [fill]: only add what is missing (German: classes on top of de.wiktionary)."""
    for r in records:
        w, pos, ss = r["word"], r.get("pos"), r.get("senses", [])
        cats = set(r.get("categories", [])) | {c for s in ss for c in s.get("categories", [])}
        forms_of = [s for s in ss if "form_of" in s]
        for s in forms_of:
            if SKIP_SENSE & set(s.get("tags", [])):
                continue
            for x in s["form_of"]:
                if x.get("word") and x["word"] != w and not fill:
                    g.form(w, x["word"], table=False)
        if forms_of and len(forms_of) == len([s for s in ss if "alt_of" not in s]):
            continue                                  # a form entry only
        if (fill and (w, pos) not in g.lemmas) or marginal(r):
            continue
        L = g.lemma(w, pos)
        if not fill:
            for s in ss:
                L.gender |= {GENDER[t] for t in s.get("tags", []) if t in GENDER}
            L.gender |= {GENDER[t] for t in r.get("tags", []) if t in GENDER}
        classes = []
        for f in r.get("forms", []):
            t, x = set(f.get("tags", [])), f.get("form", "")
            if not x:
                continue
            if t & {"table-tags", "class"}:
                classes.append(x)
            if "canonical" in t and not fill:  # the headword with its gender (Russian, Polish…: "кни́га f inan")
                L.gender |= {GENDER[g] for g in t if g in GENDER}
            if fill:
                continue
            if "auxiliary" in t:
                _add(L.aux, x)
            if t in ({"plural"}, {"nominative", "plural"}):
                _add(L.plural, x)
            if t == {"past"}:
                _add(L.past, x)
            if t == {"participle", "past"}:
                _add(L.pp, x)
            if t == {"present", "singular", "third-person"}:
                _add(L.pres3, x)
            if t == {"comparative"}:
                _add(L.cmp, x)
            if t == {"superlative"}:
                _add(L.sup, x)
            if t & SKIP_FORM or t & RELATED or (t and t <= ASPECT) or " " in x or x == w:
                continue
            g.form(x, w)
        if pos == "verb" and not L.cls:
            for c in classes:
                found = [k for k in CLASS_WORDS if k in c.lower()]
                if found:
                    L.cls = " ".join(found[:2]) if "irregular" in found else found[0]
                    break
            if not L.cls and irregular_category and irregular_category in cats:
                L.cls = "irregular"


def overrides(path, g):
    """Hand edits, one per line: '<lemma> <TAB> <pos> <TAB> class=irregular' or '<form> <TAB> -<lemma>' (drop a lemma)."""
    try:
        lines = open(path, encoding="utf-8").read().splitlines()
    except FileNotFoundError:
        return
    for line in lines:
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) == 3 and parts[2].startswith("class="):
            if (parts[0], parts[1]) in g.lemmas:
                g.lemmas[(parts[0], parts[1])].cls = parts[2][6:]
        elif len(parts) == 2 and parts[1].startswith("-"):
            ls = g.forms.get(parts[0], [])
            if parts[1][1:] in ls:
                ls.remove(parts[1][1:])
