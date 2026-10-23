"""A book language's word families, synonyms, expressions, forms, pronunciation and origin, from Wiktionary.

Its own words, so from its richest edition (native, e.g. de.wiktionary for German) first, then en.wiktionary. Lists are
sorted by frequency (FrequencyWords); everything is kept. Written next to a pack's dictionary as <id>.rel: one line per
word, sorted, tab-separated — word, related, synonyms, antonyms, expressions, IPA, origin, forms ('|' between items),
its rank in the frequency list (0: not in it) and, for a separable verb, its particle ("zumachen" → "zu").
"""
import re

COLUMNS = ["rel", "syn", "ant", "expr", "ipa", "ety", "forms"]
# Forms of the tables, not their machinery.
SKIP_TAGS = {"table-tags", "inflection-template", "class", "romanization", "auxiliary"}
# The finite forms of a verb's table: where a separable verb is written apart ("macht zu").
FINITE = {"present", "past", "subjunctive-i", "subjunctive-ii", "imperative"}
REFLEXIVE = {"mich", "dich", "sich", "uns", "euch", "mir", "dir"}


def _clean(s):
    return re.sub(r"\s+", " ", (s or "").replace("|", "/").replace("\t", " ")).strip()


def _origin(text):
    """The prose of an etymology: en.wiktionary puts an 'Etymology tree' of bare roots first."""
    lines = [l.strip() for l in (text or "").split("\n") if l.strip()]
    if lines and lines[0].startswith("Etymology tree"):
        lines = [l for l in lines if len(l) > 40 and " " in l]
    return _clean(" ".join(lines))[:700]


def collect(records, into):
    """Adds what [records] (one edition, one language) say about each word to [into]: word → {column: [...]}."""
    for r in records:
        w = r.get("word")
        if not w:
            continue
        e = into.setdefault(w, {c: [] for c in COLUMNS})
        if r.get("pos") == "verb":
            e["_verb"] = True
            # A separable verb: its finite forms are written apart, the particle last ("macht zu", "krümme mich zusammen").
            for f in r.get("forms") or []:
                x, t = (f.get("form") or "").split(), set(f.get("tags") or [])
                if len(x) == 3 and x[1] in REFLEXIVE:
                    x = [x[0], x[2]]
                if (len(x) == 2 and t & FINITE and "auxiliary" not in t and x[1].isalpha() and x[1].islower()
                        and w.startswith(x[1]) and len(w) - len(x[1]) > 2):
                    e.setdefault("_sep", set()).add(x[1])

        def add(col, x):
            x = _clean(x)
            if x and x != w and x not in e[col]:
                e[col].append(x)

        for k in ("derived", "related"):
            for x in r.get(k) or []:
                t = x.get("word", "")
                add("expr" if " " in t.strip() else "rel", t)
        for k in ("expressions", "proverbs"):
            for x in r.get(k) or []:
                add("expr", x.get("word", ""))
        senses = r.get("senses") or []
        for col, k in (("syn", "synonyms"), ("ant", "antonyms")):
            for x in (r.get(k) or []) + [y for s in senses for y in (s.get(k) or [])]:
                add(col, x.get("word", ""))
        for s in r.get("sounds") or []:
            if s.get("ipa") and len(e["ipa"]) < 2:
                add("ipa", s["ipa"])
        ety = r.get("etymology_text") or (r.get("etymology_texts") or [""])[0]
        if ety and not e["ety"]:
            add("ety", _origin(ety))
        for f in r.get("forms") or []:
            tags = [t for t in f.get("tags") or [] if t not in SKIP_TAGS]
            form = _clean(f.get("form"))
            if form and tags and form != w and form != "-" and not any(t in SKIP_TAGS for t in f.get("tags") or []):
                add("forms", f"{form}:{' '.join(tags)}")


def rank(freq_path):
    """word → rank in a FrequencyWords list ('word count' lines, most frequent first)."""
    out = {}
    for i, line in enumerate(open(freq_path, encoding="utf-8")):
        w = line.split(" ", 1)[0]
        out.setdefault(w, i)
    return out


WORD = re.compile(r"[^\W\d_]+(['’-][^\W\d_]+)*")


def coverage(known, freq_path, n=5000):
    """The share (0–100) of the [n] most frequent words (letters only) that [known] (lower case) has."""
    top = []
    for line in open(freq_path, encoding="utf-8"):
        w = line.split(" ", 1)[0]
        if WORD.fullmatch(w):
            top.append(w.lower())
            if len(top) == n:
                break
    return round(100 * sum(w in known for w in top) / max(len(top), 1), 1)


def _order(items, ranks):
    """Frequent first; a phrase counts as its rarest word; unknown ones keep their order after the known."""
    big = len(ranks) + 1

    def key(it):
        i, x = it
        ws = re.findall(r"[^\W\d_]+", x.lower())
        r = max((ranks.get(t, big) for t in ws), default=big)
        return (r, i)
    return [x for _, x in sorted(enumerate(items), key=key)]


def separable(data, w):
    """The particle of a separable verb: its tables write it apart and the rest is a verb (zu + machen)."""
    ps = [p for p in data[w].get("_sep", ()) if data.get(w[len(p):], {}).get("_verb")]
    return min(ps, key=len) if ps else ""


def lines(data, ranks, max_forms=24):
    """The .rel lines, sorted by word: the words a reader meets (in the frequency list) with something to show, and
    every separable verb (the card finds it apart in a sentence). Forms are capped: some tables of en.wiktionary list
    hundreds (nearly the whole file otherwise)."""
    out = []
    for w in sorted(data):
        e = data[w]
        sep = separable(data, w)
        rank = ranks.get(w, ranks.get(w.lower(), -1)) + 1
        if not sep and (not rank or not any(e[c] for c in COLUMNS)):
            continue
        e["forms"] = e["forms"][:max_forms]
        cols = [w]
        for c in COLUMNS:
            v = e[c] if c in ("ipa", "ety", "forms") else _order(e[c], ranks)
            cols.append("|".join(v))
        out.append("\t".join(cols + [str(rank), sep]))
    return out
