#!/usr/bin/env python3
"""The Anki cases (README.md here): keys.json, fields.json, dedupe.json, from docs/formats.md's "Anki notes". Seeded."""
import hashlib
import json
import os
import random
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from corpus import EDGE, TEXTS  # noqa: E402

SEED = 20261101
LANGS = ["ru", "en", "de", "uk", "fr", "es", "pl", "pt", "it", "ja", "zh", "ar", "he", "RU", "De"]


def space(ch):
    """Whitespace as Kotlin's isWhitespace: space separators (no-break ones too), line and paragraph separators, TAB LF VT FF CR, U+001C-U+001F."""
    return unicodedata.category(ch) in ("Zs", "Zl", "Zp") or ch in "\t\n\x0b\x0c\r\x1c\x1d\x1e\x1f"


def trim(s):
    a, b = 0, len(s)
    while a < b and space(s[a]): a += 1
    while b > a and space(s[b - 1]): b -= 1
    return s[a:b]


def norm(text):
    """The lookup key: NFC, soft hyphens out, whitespace trimmed at the edges, lower case (full mapping)."""
    return trim(unicodedata.normalize("NFC", text).replace("\u00ad", "")).lower()


def key(text, lang, history, answer):
    return ("h-" if history else "") + (lang or answer).lower() + "-" + hashlib.sha256(norm(text).encode()).hexdigest()[:32]


def blank(s):
    return trim(s) == ""


def brief(answer):
    """A quick AI answer's line: its translation, else "<article> <base> — <meanings>"; 140 characters at most."""
    try:
        j = json.loads(answer) if answer is not None else None
    except ValueError:
        return ""
    if not isinstance(j, dict): return ""
    s = lambda k: j[k] if isinstance(j.get(k), str) else ""
    t = s("translation")
    if blank(t):
        ms = ", ".join(m for m in j.get("meanings", []) if isinstance(m, str)) if isinstance(j.get("meanings"), list) else ""
        head = " ".join(x for x in (s("article"), s("base")) if not blank(x))
        t = " — ".join(x for x in (head, ms) if not blank(x))
    return t[:140]


def html(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")


def back(line):
    i = line.find(" — ")
    return html(line) if i < 0 else '<div class="head">%s</div>%s' % (html(line[:i]), html(line[i + 3:]))


def fields(e):
    line = e["meaning"] if not blank(e["meaning"]) else brief(e["quick"])
    return {"Front": html(e["text"]), "Back": back(line), "Example": "", "Key": key(e["text"], e["lang"], e["history"], e["answer"])}


def texts():
    return [t for g in TEXTS.values() for t in g] + EDGE


QUICK = [
    None, "", "not json", "[1,2]", '{"translation":"дом"}', '{"translation":"  ","meanings":["house","home"]}',
    '{"base":"Haus","article":"das","meanings":["house","home"]}', '{"base":"Haus","meanings":[]}', '{"article":" ","base":"Haus"}',
    '{"meanings":["a",3,"b",null,"c"]}', '{"translation":5,"meanings":["x"]}', '{"translation":"<b>&</b>\\nzwei"}',
    json.dumps({"translation": "😀" * 150}), json.dumps({"translation": "Ä" * 139 + "😀🏽"}), json.dumps({"meanings": ["𠮷野家"] * 40}),
    '{"translation":"","article":"","base":"","meanings":[]}',
]


def write(name, data, about):
    with open(os.path.join(HERE, name), "w", encoding="utf-8", newline="") as f:
        f.write('{"about": %s,\n "cases": [' % json.dumps(about) + ",".join("\n  " + json.dumps(c, ensure_ascii=False, separators=(",", ":")) for c in data) + "\n ]}\n")


def main():
    rng = random.Random(SEED)
    keys = []
    for text, kind, _ in texts():
        for lang in rng.sample(LANGS, 2) + [""]:
            for history in (False, True):
                answer = rng.choice(["ru", "en", "de"])
                keys.append({"text": text, "lang": lang, "history": history, "answer": answer, "key": key(text, lang, history, answer)})
    write("keys.json", keys, "Key: (history: \"h-\") + lower(lang, or answer when empty) + \"-\" + 32 hex of sha256(lookup key of text)")

    cases = []
    for text, kind, meanings in texts():
        for lang, meaning in sorted(meanings.items()):
            cases.append({"text": text, "kind": kind, "lang": lang, "meaning": meaning, "quick": rng.choice(QUICK), "history": rng.random() < 0.3, "answer": "ru"})
    for i, q in enumerate(QUICK):
        for meaning in ("", "  ", " "):
            cases.append({"text": "Wort %d" % i, "kind": "word", "lang": rng.choice(["ru", "en", ""]), "meaning": meaning, "quick": q, "history": i % 2 == 1, "answer": "de"})
    cases += [{"text": t, "kind": "phrase", "lang": "ru", "meaning": m, "quick": None, "history": False, "answer": "ru"} for t, m in [
        ("a & b", "das Haus (Hauses, pl. Häuser) — дом"), ("x", " — leer davor"), ("y", "kopf — "), ("z", "a — b — c"),
        ("<i>", "eins\nzwei — drei\nvier"), ("w", "ohne Strich-Trennung"), ("v", "en–dash – not a split"), ("u", "—ohne Leerzeichen—")]]
    for c in cases: c["fields"] = fields(c)
    write("fields.json", cases, "an entry as the log has it (lang: code; quick: its newest quick AI answer) -> Front, Back, Example, Key")

    dedupe = []
    for i in range(60):
        notes, ids = [], rng.sample(range(1_700_000_000_000, 1_800_000_000_000), rng.randint(1, 9))
        ks = ["ru-" + "%032x" % rng.getrandbits(128) for _ in range(rng.randint(1, 3))] + ([""] if i % 7 == 0 else [])
        for n in ids:
            notes.append({"id": n, "key": rng.choice(ks), "audio": rng.choice(["", "", "[sound:lexipeek_%d.wav]" % n]),
                          "tags": rng.choice([["inkpeek"], ["lexipeek"], ["inkpeek", "lexipeek"], ["lexipeek", "leech"], []])})
        audio, deleted = {}, []
        by = {}
        for n in sorted(notes, key=lambda n: n["id"]):
            if n["key"]: by.setdefault(n["key"], []).append(n)
        for ns in by.values():
            if len(ns) < 2: continue
            if not ns[0]["audio"]:
                other = next((n["audio"] for n in ns[1:] if n["audio"]), "")
                if other: audio[str(ns[0]["id"])] = other
            deleted += [n["id"] for n in ns[1:]]
        dedupe.append({"notes": notes, "audio": audio, "deleted": sorted(deleted)})
    write("dedupe.json", dedupe, "Peek notes (id, Key, Audio, tags) -> per Key the smallest id stays, takes the first non-empty Audio by id when its own is empty; the others are deleted; an empty Key is never touched; tags do not matter")
    print("keys %d, fields %d, dedupe %d" % (len(keys), len(cases), len(dedupe)))


if __name__ == "__main__":
    main()
