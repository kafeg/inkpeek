"""A book language's word families, synonyms, expressions, forms, pronunciation and origin, from Wiktionary.

Its own words, so from its richest edition (native, e.g. de.wiktionary for German) first, then en.wiktionary. Lists are
sorted by frequency (FrequencyWords); everything is kept. Written next to a pack's dictionary as <id>.rel: one line per
word, sorted, tab-separated — word, related, synonyms, antonyms, expressions, IPA, origin, forms ('|' between items).
"""
import re

COLUMNS = ["rel", "syn", "ant", "expr", "ipa", "ety", "forms"]
# Forms of the tables, not their machinery.
SKIP_TAGS = {"table-tags", "inflection-template", "class", "romanization", "auxiliary"}


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


def _order(items, ranks):
    """Frequent first; a phrase counts as its rarest word; unknown ones keep their order after the known."""
    big = len(ranks) + 1

    def key(it):
        i, x = it
        ws = re.findall(r"[^\W\d_]+", x.lower())
        r = max((ranks.get(t, big) for t in ws), default=big)
        return (r, i)
    return [x for _, x in sorted(enumerate(items), key=key)]


def lines(data, ranks, max_forms=24):
    """The .rel lines, sorted by word: the words a reader meets (in the frequency list), with something to show.
    Forms are capped: some tables of en.wiktionary list hundreds (nearly the whole file otherwise)."""
    out = []
    for w in sorted(data):
        e = data[w]
        if (w not in ranks and w.lower() not in ranks) or not any(e[c] for c in COLUMNS):
            continue
        e["forms"] = e["forms"][:max_forms]
        cols = [w]
        for c in COLUMNS:
            v = e[c] if c in ("ipa", "ety", "forms") else _order(e[c], ranks)
            cols.append("|".join(v))
        out.append("\t".join(cols))
    return out
