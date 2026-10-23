"""One dictionary for a language pair x→y: articles from the Wiktionary of language y (kaikki), a header from the
grammar of x, WikDict for the words that Wiktionary has no article for, every inflected form in .syn."""
import html
import re

from . import stardict

ARTICLE = {"de": {"m": "der", "f": "die", "n": "das"}, "fr": {"m": "le", "f": "la"}, "es": {"m": "el", "f": "la"},
           "it": {"m": "il", "f": "la"}, "pt": {"m": "o", "f": "a"}, "nl": {"m": "de", "f": "de", "n": "het"}}
# Labels in the answer language: plural, gender, verb class.
LABELS = {
    "ru": {"pl": "мн.", "m": "м.", "f": "ж.", "n": "ср.", "irregular": "неправ.", "strong": "сильн.", "weak": "слаб.", "article": "артикль", "nominative": "им.", "accusative": "вин.", "dative": "дат.", "genitive": "род."},
    "uk": {"pl": "мн.", "m": "ч.", "f": "ж.", "n": "с.", "irregular": "неправ.", "strong": "сильн.", "weak": "слаб.", "article": "артикль", "nominative": "наз.", "accusative": "знах.", "dative": "дав.", "genitive": "род."},
    "en": {"pl": "pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "irreg.", "strong": "strong", "weak": "weak", "article": "article", "nominative": "nom.", "accusative": "acc.", "dative": "dat.", "genitive": "gen."},
    "de": {"pl": "Pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "unregelm.", "strong": "stark", "weak": "schwach", "article": "Artikel", "nominative": "Nom.", "accusative": "Akk.", "dative": "Dat.", "genitive": "Gen."},
    "fr": {"pl": "pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "irrég.", "strong": "fort", "weak": "faible", "article": "article", "nominative": "nom.", "accusative": "acc.", "dative": "dat.", "genitive": "gén."},
    "es": {"pl": "pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "irreg.", "strong": "fuerte", "weak": "débil", "article": "artículo", "nominative": "nom.", "accusative": "ac.", "dative": "dat.", "genitive": "gen."},
    "it": {"pl": "pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "irreg.", "strong": "forte", "weak": "debole", "article": "articolo", "nominative": "nom.", "accusative": "acc.", "dative": "dat.", "genitive": "gen."},
    "pt": {"pl": "pl.", "m": "m.", "f": "f.", "n": "n.", "irregular": "irreg.", "strong": "forte", "weak": "fraco", "article": "artigo", "nominative": "nom.", "accusative": "ac.", "dative": "dat.", "genitive": "gen."},
    "pl": {"pl": "lm", "m": "m.", "f": "ż.", "n": "n.", "irregular": "nieregul.", "strong": "mocny", "weak": "słaby", "article": "rodzajnik", "nominative": "M.", "accusative": "B.", "dative": "C.", "genitive": "D."},
    "nl": {"pl": "mv.", "m": "m.", "f": "v.", "n": "o.", "irregular": "onregelm.", "strong": "sterk", "weak": "zwak", "article": "lidwoord", "nominative": "nom.", "accusative": "acc.", "dative": "dat.", "genitive": "gen."},
}
ABBREVIATIONS = {v.split("/")[-1].split()[-1] for lab in LABELS.values() for v in lab.values() if v.endswith(".")}
PERFECT = {"sein": "ist", "haben": "hat"}  # German Perfekt: the auxiliary's 3rd person
CASES = ("nominative", "accusative", "dative", "genitive")
GENDERS = (("masculine", "m"), ("feminine", "f"), ("neuter", "n"))
DETERMINERS = {"det", "article"}


def determiner(tagged, x, y):
    """A determiner's form described by its table, in the answer language: "артикль der: ж. им./вин., мн. им./вин.".
    The answer Wiktionary's article of the lemma ("der") describes its masculine form, not this one."""
    lab = LABELS.get(y, LABELS["en"])
    lemmas = [lm for lm, _ in tagged]
    lemma = ARTICLE.get(x, {}).get("m") if ARTICLE.get(x, {}).get("m") in lemmas else lemmas[0]
    groups = {}
    for lm, t in tagged:
        key = "pl" if "plural" in t else next((c for g, c in GENDERS if g in t), None)
        if lm == lemma and key:
            groups.setdefault(key, set()).update(c for c in CASES if c in t)
    segs = [f"{lab[k]} " + "/".join(lab[c] for c in CASES if c in groups[k]) for k in ("m", "f", "n", "pl") if groups.get(k)]
    if not segs:
        return None
    kind = lab["article"] + " " if lemma in ARTICLE.get(x, {}).values() else ""
    return f"{kind}{lemma}: {', '.join(segs)}"


def header(word, lemma, x, y):
    """'das Haus (Hauses, мн. Häuser)', 'gehen (geht, ging, ist gegangen) неправ.', 'gut (besser, am besten)'."""
    lab = LABELS.get(y, LABELS["en"])
    if lemma is None:
        return html.escape(word), ""
    if lemma.pos == "noun":
        arts = ARTICLE.get(x)
        g = sorted(lemma.gender)
        if arts and g and all(c in arts for c in g):
            head = "/".join(dict.fromkeys(arts[c] for c in g)) + " " + word
            gender = ""
        else:
            head, gender = word, " ".join(lab[c] for c in g if c in lab)
        bits = [gender] if gender else []
        if lemma.gen and x == "de":
            bits.append(lemma.gen[0])
        if lemma.plural:
            bits.append(f"{lab['pl']} {lemma.plural[0]}")
        return html.escape(head), ", ".join(bits)
    if lemma.pos == "verb":
        pp = lemma.pp[0] if lemma.pp else ""
        if pp and x == "de" and lemma.aux:
            pp = f"{PERFECT.get(lemma.aux[0], lemma.aux[0])} {pp}"
        parts = [p for p in (lemma.pres3[0] if (lemma.pres3 and x == "de") else "", lemma.past[0] if lemma.past else "", pp) if p]
        cls = " ".join(lab.get(c, c) for c in lemma.cls.split()) if lemma.cls in ("irregular", "strong", "irregular strong", "irregular weak") else ""
        return html.escape(word), ", ".join(parts) + (f"|{cls}" if cls else "")
    if lemma.pos == "adj" and lemma.cmp:
        return html.escape(word), ", ".join(p for p in (lemma.cmp[0], lemma.sup[0] if lemma.sup else "") if p)
    return html.escape(word), ""


def _head_html(head, tail):
    forms, _, cls = tail.partition("|")
    out = f"<b>{head}</b>"
    if forms:
        out += f" ({html.escape(forms)})"
    if cls:
        out += f" <i>{html.escape(cls)}</i>"
    return out


def _unparen(s):
    """Without parenthesized notes, nested ones too; a stray bracket goes as well."""
    prev = None
    while prev != s:
        prev, s = s, re.sub(r"\s*\([^()]*\)", "", s)
    s = s.replace("(", "").replace(")", "").strip(" ;,")
    # A sentence's full stop goes, the stop of our own abbreviations stays ("им./вин.").
    last = s.split()[-1].split("/")[-1] if s.split() else ""
    return s if last in ABBREVIATIONS else s.rstrip(".")


SUB = " — "


def _short(glosses, limit=60):
    """The first meanings for the first line: unique items in order (tables repeat a word for every sense)."""
    out = []
    for gl in glosses:
        # A sub-sense ("book — collection of sheets of paper") counts as its parent meaning here.
        for item in re.split(r"\s*[;,]\s*", _unparen(gl.split(SUB, 1)[0])):
            if item and item not in out:
                if out and len(", ".join(out + [item])) > limit:
                    return ", ".join(out)
                out.append(item)
    return ", ".join(out) or (glosses[0][:limit] if glosses else "")


def senses(record):
    """(gloss, examples) of an entry, without form-of senses."""
    out = []
    for s in record.get("senses", []):
        if "form_of" in s or "alt_of" in s:
            continue
        gs = [g.strip() for g in s.get("glosses") or [] if g.strip()]
        # en.wiktionary nests senses: ["book:", "collection of sheets of paper"] — the parent is the meaning.
        gl = gs[0].rstrip(":") + SUB + gs[-1] if len(gs) > 1 and gs[0].endswith(":") else (gs[-1] if gs else "")
        if not gl:
            continue
        ex = []
        for e in (s.get("examples") or [])[:2]:
            t = (e.get("text") or "").strip()
            if t:
                tr = e.get("translation") or e.get("english") or ""
                ex.append(t + (f" — {tr}" if tr else ""))
        out.append((gl, ex))
    return out


STRESS = re.compile("[\u0300\u0301]")


def translations(records, y):
    """English Wiktionary's translation tables of English words: word → [(pos, [(gloss, [])])]; a gloss is the
    translations of one sense, with the article for German nouns, the sense itself in parentheses."""
    out = {}
    for r in records:
        senses, order = {}, []
        for t in r.get("translations") or []:
            tags = set(t.get("tags") or [])
            if t.get("lang_code") != y or not t.get("word") or tags & {"archaic", "obsolete", "dated", "rare"} \
                    or any(c.isupper() for tag in tags for c in tag):          # dialects: "Alemannic-German"
                continue
            w = STRESS.sub("", t["word"])
            g = [c for c in ("masculine", "feminine", "neuter") if c in tags]
            if y in ARTICLE and len(g) == 1 and g[0][0] in ARTICLE[y]:
                w = f"{ARTICLE[y][g[0][0]]} {w}"
            sense = t.get("sense") or ""
            if sense not in senses:
                senses[sense] = []; order.append(sense)
            if w not in senses[sense]:
                senses[sense].append(w)
        if senses:
            glosses = [(", ".join(senses[s]) + (f" ({s})" if s else ""), []) for s in order]
            out.setdefault(r["word"], []).append((r.get("pos"), glosses))
    return out


def wikdict_articles(base):
    """WikDict: headword → (grammar mark, translations); translations are its plain <div> lines."""
    out = {}
    for w, a in stardict.Reader(base).words:
        tr = [html.unescape(t).strip() for t in re.findall(r"<div>([^<]+)</div>", a)]
        tr = [t for t in dict.fromkeys(tr) if t]
        if tr:
            m = re.search(r'class="grammar"[^>]*>([^<]+)<', a)
            out[w] = (m.group(1).strip() if m else "", tr)
    return out


def build(x, y, records, grammar, wikdict_base=None, tables=None):
    """Articles (HTML) and .syn pairs for x→y; [records] — entries of language x in the y-Wiktionary; [tables] —
    translation tables (English words) that go first."""
    blocks = {w: list(b) for w, b in (tables or {}).items()}
    stats = {"tables": len(blocks)}
    for r in records:
        ss = senses(r)
        if ss:
            blocks.setdefault(r["word"], []).append((r.get("pos"), ss))
    stats["wiktionary"] = len(blocks) - stats["tables"]
    stats["wikdict"] = 0
    if wikdict_base:
        for w, (gram, tr) in wikdict_articles(wikdict_base).items():
            if w not in blocks:
                pos = "noun" if gram.startswith("noun") else "verb" if gram.startswith("verb") else None
                blocks[w] = [(pos, [(t, []) for t in tr[:8]])]
                stats["wikdict"] += 1
    # A determiner's form ("das", "dem") gets its own description first, unless its own article is one already.
    for f, tagged in grammar.tagged.items():
        if not any(p in DETERMINERS for p, _ in blocks.get(f, [])):
            d = determiner(tagged, x, y)
            if d:
                blocks[f] = [("det", [(d, [])])] + blocks.get(f, [])
    articles, firsts = {}, []
    for w, bs in blocks.items():
        parts = []
        for pos, ss in bs:
            head, tail = header(w, grammar.lemmas.get((w, pos)), x, y)
            first = _head_html(head, tail) + " — " + html.escape(_short([g for g, _ in ss]))
            body = "".join(f"<li>{html.escape(g)}" + "".join(f"<br><small>◆ {html.escape(e)}</small>" for e in ex) + "</li>" for g, ex in ss)
            parts.append(f'<p class="head">{first}</p><ol>{body}</ol>')
            firsts.append(len(re.sub(r"<[^>]+>", "", html.unescape(first))))
        articles[w] = "".join(parts)
    firsts.sort()
    stats["weak"] = stats["tables"] + stats["wiktionary"] < 10000  # mostly WikDict: fewer and shorter articles
    stats["first_median"] = firsts[len(firsts) // 2] if firsts else 0
    stats["first_p90"] = firsts[int(len(firsts) * 0.9)] if firsts else 0
    return articles, grammar.syn(), stats
