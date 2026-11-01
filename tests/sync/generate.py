#!/usr/bin/env python3
"""The generated reader cases: tests/sync/generated/<case>/ (README.md here, "generated/").

Devices with their own clocks write lines as docs/formats.md says (HLC, `c`, month files); a sync tool copies some of
them now and then. expected.json is this script's own merge of the folder, written from the format's text; every app
must agree with it (InkPeek's JVM tests, LexiPeek's doctest). Same SEED, same bytes.

  tests/sync/generate.py                      # rewrites tests/sync/generated/
  tests/sync/generate.py --stress DIR [OPS]   # one big case into DIR/stress (50,000 ops), for INKPEEK_STRESS / LEXIPEEK_STRESS
"""
import datetime
import json
import os
import random
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from corpus import BASES, NAMES, pool  # noqa: E402

SEED = 20261101
OPS = {
    "saved.put": ["text", "kind", "lang", "meaning"],
    "saved.del": ["text"],
    "history.seen": ["text", "kind", "lang", "meaning", "base", "c"],
    "history.del": ["text"],
    "ai.put": ["text", "lang", "section", "ver", "model", "answer"],
}
EPOCH = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)
HOUR = 3_600_000


def at(y, mo, d, h=0, mi=0, s=0):
    return int((datetime.datetime(y, mo, d, h, mi, s, tzinfo=datetime.timezone.utc) - EPOCH).total_seconds()) * 1000


def month(ms):
    t = EPOCH + datetime.timedelta(milliseconds=ms)
    return "%04d-%02d.jsonl" % (t.year, t.month)


def jstr(s):
    out = ['"']
    for ch in s:
        o = ord(ch)
        if ch == '"': out.append('\\"')
        elif ch == "\\": out.append("\\\\")
        elif ch == "\b": out.append("\\b")
        elif ch == "\f": out.append("\\f")
        elif ch == "\n": out.append("\\n")
        elif ch == "\r": out.append("\\r")
        elif ch == "\t": out.append("\\t")
        elif o < 0x20: out.append("\\u%04x" % o)
        else: out.append(ch)
    out.append('"')
    return "".join(out)


def fmt(l, extra=None):
    """The canonical form; [extra]: unknown fields after the known ones (a newer app's line)."""
    parts = ['"t":[%d,%d]' % l["t"], '"d":' + jstr(l["d"]), '"op":' + jstr(l["op"])]
    for k in OPS[l["op"]]:
        if k == "base" and not l.get("base"): continue
        v = l.get(k, 0 if k in ("c", "ver") else "")
        parts.append('"%s":%s' % (k, v if k in ("c", "ver") else jstr(v)))
    for k, v in (extra or {}).items():
        parts.append(jstr(k) + ":" + json.dumps(v, ensure_ascii=False, separators=(",", ":")))
    return "{" + ",".join(parts) + "}"


# The reader, as docs/formats.md says.

def parse(raw):
    try:
        j = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        return None
    if not isinstance(j, dict): return None
    t = j.get("t")
    if not (isinstance(t, list) and len(t) == 2 and all(type(x) is int for x in t)): return None
    op, d, text = j.get("op"), j.get("d"), j.get("text")
    if not (isinstance(op, str) and op in OPS and isinstance(d, str) and isinstance(text, str)): return None
    l = {"t": (t[0], t[1]), "d": d, "op": op, "text": text, "raw": raw}
    for k in OPS[op]:
        v = j.get(k)
        l[k] = (v if type(v) is int else 0) if k in ("c", "ver") else (v if isinstance(v, str) else "")
    return l


def order(l):
    return (l["t"][0], l["t"][1], l["d"].encode(), l["raw"])


def read(root):
    raw = set()
    log = os.path.join(root, "inkpeek-log")
    for dev in sorted(os.listdir(log)):
        folder = os.path.join(log, dev)
        if not os.path.isdir(folder): continue
        for name in sorted(os.listdir(folder)):
            p = os.path.join(folder, name)
            if not name.endswith(".jsonl") or name.startswith(".") or not os.path.isfile(p): continue
            with open(p, "rb") as f:
                b = f.read()
            raw.update(b[: b.rfind(b"\n") + 1].split(b"\n")[:-1])
    return [l for l in map(parse, raw) if l]


def merge(lines):
    by_text = {}
    for l in sorted({l["raw"]: l for l in lines}.values(), key=order):
        by_text.setdefault(l["text"], []).append(l)
    saved, history, ai = [], [], []
    for text, ls in by_text.items():
        s = [l for l in ls if l["op"].startswith("saved.")]
        if s and s[-1]["op"] == "saved.put":
            dels = [i for i, l in enumerate(s) if l["op"] == "saved.del"]
            first, win = s[dels[-1] + 1 if dels else 0], s[-1]
            saved.append({"text": text, "kind": win["kind"], "lang": win["lang"], "meaning": win["meaning"], "created": win["t"][0],
                          "id": "%s:%d:%d" % (first["d"], *first["t"]), "origin": first["d"], "updated_by": win["d"]})
        h = [l for l in ls if l["op"].startswith("history.")]
        dels = [i for i, l in enumerate(h) if l["op"] == "history.del"]
        h = h[dels[-1] + 1:] if dels else h
        if h:
            top, counts = h[-1], {}
            for l in h: counts[l["d"]] = max(counts.get(l["d"], 0), l["c"])
            history.append({"text": text, "kind": top["kind"], "lang": top["lang"], "base": top["base"] or None, "meaning": top["meaning"],
                            "seen": top["t"][0], "count": sum(counts.values()), "devices": sorted(counts)})
        if (saved and saved[-1]["text"] == text) or (history and history[-1]["text"] == text):
            answers = {}
            for l in ls:
                if l["op"] == "ai.put": answers[(l["lang"], l["section"], l["ver"])] = l
            for (lang, section, ver), l in sorted(answers.items()):
                ai.append({"text": text, "lang": lang, "section": section, "ver": ver, "model": l["model"], "answer": l["answer"], "created": l["t"][0]})
    key = lambda e: e["text"].encode()
    return {"saved": sorted(saved, key=key), "history": sorted(history, key=key),
            "ai": sorted(ai, key=lambda e: (e["text"].encode(), e["lang"], e["section"], e["ver"]))}


def compact(lines):
    """One device's lines that merge to the same state with any other logs (docs/formats.md, "Compaction")."""
    keep, by_text = set(), {}
    for l in sorted(lines, key=order): by_text.setdefault(l["text"], []).append(l)
    for ls in by_text.values():
        s = [l for l in ls if l["op"].startswith("saved.")]
        dels = [i for i, l in enumerate(s) if l["op"] == "saved.del"]
        keep.update(l["raw"] for l in (s[dels[-1]:] if dels else s))
        h = [l for l in ls if l["op"].startswith("history.")]
        dels = [i for i, l in enumerate(h) if l["op"] == "history.del"]
        if dels: keep.add(h[dels[-1]]["raw"])
        seen = [l for l in (h[dels[-1] + 1:] if dels else h) if l["op"] == "history.seen"]
        if seen: keep.add(seen[-1]["raw"])
        answers = {}
        for l in ls:
            if l["op"] == "ai.put": answers[(l["lang"], l["section"], l["ver"])] = l
        keep.update(l["raw"] for l in answers.values())
    return keep


# The devices.

PLATFORMS = ["boox", "onyx", "samsung", "google", "mac", "win", "linux"]
B32 = "abcdefghijklmnopqrstuvwxyz234567"


class Device:
    def __init__(self, rng, i, sc):
        plat = PLATFORMS[(i * 3 + rng.randrange(len(PLATFORMS))) % len(PLATFORMS)]
        self.id = plat + "-" + "".join(rng.choice(B32) for _ in range(8))
        self.app = "lexipeek" if plat in ("mac", "win", "linux") else "inkpeek"
        self.name = {"boox": "Boox Note Air4 C", "onyx": "Onyx Page", "samsung": "Galaxy S24", "google": "Pixel 9",
                     "mac": "MacBook Air", "win": "Desktop PC", "linux": "ThinkPad"}[plat]
        self.created = sc["base"] - 30 * 24 * HOUR
        self.skew = rng.choice(sc.get("skews", [0]))
        self.answer = rng.choice(sc.get("answers", ["ru", "en", "de"]))
        self.lines, self.extra = [], {}  # own lines in write order; raw → a non-canonical form of it
        self.known, self.texts = {}, {}  # text → lines this device read; text → its kind and meanings
        self.clock = (0, -1)
        self.upto = {}  # another device → how many of its lines it read
        self.offline = False

    def see(self, l):
        self.known.setdefault(l["text"], []).append(l)
        if l["t"] > self.clock: self.clock = l["t"]

    def looks(self, text):
        h = sorted((l for l in self.known.get(text, []) if l["op"].startswith("history.")), key=order)
        dels = [i for i, l in enumerate(h) if l["op"] == "history.del"]
        return max([l["c"] for l in (h[dels[-1] + 1:] if dels else h) if l["d"] == self.id], default=0)

    def write(self, now, l):
        ms = max(self.clock[0], now)
        l.update(t=(ms, self.clock[1] + 1 if ms == self.clock[0] else 0), d=self.id)
        if l["op"] == "history.seen": l["c"] = self.looks(l["text"]) + 1
        l["raw"] = fmt(l).encode()
        self.see(l)
        self.lines.append(l)
        return l


def answer_of(rng, section, meaning):
    if section.startswith("offline-"): return meaning.split(" — ")[-1]
    if section == "quick":
        if rng.random() < 0.5: return json.dumps({"translation": meaning.split(" — ")[-1]}, ensure_ascii=False, separators=(",", ":"))
        return json.dumps({"base": "Haus", "article": "das", "meanings": meaning.split(" — ")[-1].split(", ")}, ensure_ascii=False, separators=(",", ":"))
    return "**%s**\n\n- \"%s\"\t\\ %s" % (section, meaning, "1. Bedeutung")


def simulate(sc, rng):
    devs = [Device(rng, i, sc) for i in range(sc["devices"])]
    texts = pool(sc["groups"])
    if sc.get("hot"): texts = rng.sample(texts, min(sc["hot"], len(texts)))
    weights = sc.get("weights", {"saved.put": 18, "saved.del": 8, "history.seen": 45, "history.del": 6, "ai.put": 23})
    ops, w = list(weights), list(weights.values())
    now = sc["base"]
    if sc.get("offline_start"):
        for d in devs: d.offline = True
    for step in range(sc["ops"]):
        now += rng.choice(sc.get("steps", [0, 250, 1000, 4000, 60_000]))
        if sc.get("offline_start") and step == sc["ops"] // 2:
            for d in devs: d.offline = False
        d = rng.choice(devs)
        if sc.get("flaky") and rng.random() < 0.01: d.offline = not d.offline
        if not d.offline and rng.random() < sc.get("sync", 0.3):
            for o in devs:
                if o is d: continue
                got = d.upto.get(o.id, 0)
                new = len(o.lines) if rng.random() < 0.8 else rng.randint(got, len(o.lines))
                for l in o.lines[got:new]: d.see(l)
                d.upto[o.id] = max(got, new)
        if sc.get("jump") and step == sc["ops"] // 3: d.skew -= sc["jump"]
        op = rng.choices(ops, w)[0]
        text, kind, meanings = rng.choice(texts)
        if op in ("saved.del", "history.del", "ai.put") and d.known and rng.random() < 0.8:
            text = rng.choice(sorted(d.known))
            kind, meanings = d.texts.get(text, (kind, meanings))
        d.texts[text] = (kind, meanings)
        lang = d.answer if d.answer in meanings and rng.random() < 0.9 else sorted(meanings)[rng.randrange(len(meanings))]
        meaning = meanings[lang] + rng.choice(["", "", "", ", ещё", " (2)"])
        l = {"op": op, "text": text}
        if op == "saved.put": l.update(kind=kind, lang=lang, meaning=meaning)
        elif op == "history.seen": l.update(kind=kind, lang=lang, meaning=meaning, base=BASES.get(text, ""))
        elif op == "ai.put":
            section = rng.choice(["quick", "quick", "analysis", "context", "offline-de"])
            l.update(lang=NAMES.get(lang, lang), section=section, ver=rng.choice([0, 3, -1532418720, 2071311475]),
                     model="bergamot" if section.startswith("offline-") else rng.choice(["claude-haiku-5-5", "gpt-5.2-mini"]),
                     answer=answer_of(rng, section, meaning))
        d.write(now + d.skew + rng.choice([0, 0, 7, -13]), l)
        if sc.get("variants") and rng.random() < sc["variants"]: d.extra[l["raw"]] = variant(rng, l)
    if sc.get("clone"): clone(devs[0])
    return devs


def clone(d):
    """A second install with the same id wrote lines with the same t: the line's UTF-8 bytes order them."""
    ms = d.clock[0] + 5000
    for i, (a, b) in enumerate([("ｶﾀｶﾅ", "😀"), ("\ue000", "𠮷"), ("Z", "Ä"), ("я", "ё")]):
        for m in (a, b):
            for op, more in [("saved.put", {"kind": "word", "lang": "ru"}), ("history.seen", {"kind": "word", "lang": "ru", "c": 1}),
                             ("ai.put", {"lang": "Russian", "section": "quick", "ver": 3, "model": "m"})]:
                l = {"t": (ms, i), "d": d.id, "op": op, "text": "Klon %d" % i, "meaning": m + " — " + m, "answer": m, **more}
                l["raw"] = fmt(l).encode()
                d.lines.append(l)


def variant(rng, l):
    """The same line in a form a reader must take as well: unknown fields, another key order, escapes, CRLF, spaces."""
    kind = rng.choice(["fields", "order", "escapes", "crlf", "space"])
    if kind == "fields": return fmt(l, rng.choice([{"book": "Effi Briest", "tags": ["nature", "ö"]}, {"page": 12}, {"v": 2, "x": {"y": [1, None, True]}}])).encode()
    if kind == "order":
        j = {k: l[k] for k in ["op", "d"] + [k for k in OPS[l["op"]] if k != "base" or l.get("base")]}
        j["t"] = list(l["t"])
        return json.dumps(j, ensure_ascii=False, separators=(", ", ": ")).encode()
    if kind == "escapes": return fmt(l).replace("/", "\\/").encode() if "/" in fmt(l) else json.dumps(json.loads(fmt(l)), separators=(",", ":")).encode()
    if kind == "crlf": return fmt(l).encode() + b"\r"
    return b" \t" + fmt(l).encode() + b" "


BROKEN = [
    b'{"t":[%(ms)s,0,1],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,0.5],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,2.0],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":["1793491204000",0],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,true],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,1],"op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,1],"d":7,"op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,2],"d":"%(d)s","op":"saved.del"}',
    b'{"t":[%(ms)s,2],"d":"%(d)s","op":"saved.del","text":null}',
    b'{"t":[%(ms)s,3],"d":"%(d)s","op":"note.put","text":%(text)s,"note":"p. 12"}',
    b'{"t":[%(ms)s,3],"d":"%(d)s","op":"saved.purge","text":%(text)s}',
    b'{"t":[%(ms)s,3],"d":"%(d)s","op":null,"text":%(text)s}',
    b'["t",1793491204000]',
    b'"saved.del"',
    b'garbage',
    b'',
    b'{',
    b'{"t":[%(ms)s,4],"d":"%(d)s","op":"saved.del","text":%(text)s}}',
    b'{"t":[%(ms)s,4],"d":"%(d)s","op":"saved.del","text":%(text)s} trailing',
    b'\x01{"t":[%(ms)s,4],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,4],"d":"%(d)s","op":"saved.del","text":%(text)s}\x0b',
    b'\xef\xbb\xbf{"t":[%(ms)s,5],"d":"%(d)s","op":"saved.del","text":%(text)s}',
    b'{"t":[%(ms)s,6],"d":"%(d)s","op":"saved.del","text":"%(raw)s\xff"}',
    b'{"t":[%(ms)s,6],"d":"%(d)s","op":"saved.del","text":"%(raw)s\xc3"}',
]


def missing_and_typed(rng, d, now):
    """Known ops with fields missing or of another type: they read as "" / 0."""
    t = (now, 900 + rng.randrange(99))
    return [fmt({"t": t, "d": d, "op": "saved.put", "text": "Fehlt"})[:-1].encode().split(b',"kind"')[0] + b"}",
            ('{"t":[%d,%d],"d":"%s","op":"history.seen","text":"Typen","kind":5,"lang":["ru"],"meaning":null,"c":2.5}' % (t[0], t[1] + 1, d)).encode(),
            ('{"t":[%d,%d],"d":"%s","op":"history.seen","text":"Typen","kind":"word","lang":"ru","meaning":"типы","c":"3"}' % (t[0], t[1] + 2, d)).encode(),
            ('{"t":[%d,%d],"d":"%s","op":"history.seen","text":"Zahl","kind":"word","lang":"ru","meaning":"число","c":4.0}' % (t[0], t[1] + 3, d)).encode(),
            ('{"t":[%d,%d],"d":"%s","op":"ai.put","text":"Zahl","lang":"Russian","section":"quick","ver":3.0,"model":"m","answer":"{}"}' % (t[0], t[1] + 4, d)).encode(),
            ('{"t":[%d,%d],"d":"%s","op":"history.seen","text":"Basis","kind":"word","lang":"ru","meaning":"основа","base":"","c":1}' % (t[0], t[1] + 5, d)).encode()]


def write_case(out, sc, devs, rng, noise=True):
    log = os.path.join(out, "inkpeek-log")
    for i, d in enumerate(devs):
        folder = os.path.join(log, d.id)
        os.makedirs(folder, exist_ok=True)
        if i != 1 or not noise:  # a folder without device.json is read too
            dj = '{"device":%s,"app":%s,"name":%s,"created":%d%s}\n' % (jstr(d.id), jstr(d.app), jstr(d.name), d.created, ',"color":"grey"' if i == 2 else "")
            with open(os.path.join(folder, "device.json"), "w", encoding="utf-8", newline="") as f: f.write(dj)
        files = {}
        for l in d.lines:
            files.setdefault(month(l["t"][0]), []).append(d.extra.get(l["raw"], l["raw"]) if noise else l["raw"])
        if noise and sc.get("broken") and d.lines:
            last = sorted(files)[-1]
            # Newer than every line, of a saved text: one read by mistake would change the state.
            newest = max(l["t"][0] for o in devs for l in o.lines) + HOUR
            text = [l["text"] for l in d.lines if l["op"] == "saved.put"][-1]
            sub = {b"d": d.id.encode(), b"ms": str(newest).encode(), b"text": jstr(text).encode(), b"raw": jstr(text)[1:-1].encode()}
            extra = [b % sub for b in BROKEN] + missing_and_typed(rng, d.id, d.lines[-1]["t"][0])
            for b in extra: files[last].insert(rng.randrange(len(files[last]) + 1), b)
        if noise and sc.get("copies") and d.lines:
            name = rng.choice(sorted(files))
            ls = files[name]
            only = ls.pop(rng.randrange(len(ls)))  # a line only in a copy counts
            stem = name[:-6]
            copies = {stem + " (conflicted copy 2026-11-02).jsonl": [x for x in ls if rng.random() < 0.6] + [only],
                      stem + ".sync-conflict-20261102-101500-ABCDEFG.jsonl": ls[: len(ls) // 2], stem + " (1).jsonl": list(ls)}
            files.update(copies)
            ignored = fmt({"t": (d.lines[-1]["t"][0] + 10 * HOUR, 0), "d": d.id, "op": "saved.del", "text": d.lines[-1]["text"]}).encode()
            for n in [name + ".tmp", "." + name, ".syncthing." + name + ".tmp", stem + ".txt"]: files[n] = [ignored]
            os.makedirs(os.path.join(folder, "old"), exist_ok=True)
            with open(os.path.join(folder, "old", name), "wb") as f: f.write(ignored + b"\n")
        for name, ls in files.items():
            body = b"".join(x + b"\n" for x in ls)
            if noise and sc.get("cut") and name == sorted(n for n in files if n[:4].isdigit() and n.endswith(".jsonl") and len(n) == 13)[-1]:
                body += fmt({"t": (d.lines[-1]["t"][0] + HOUR, 0), "d": d.id, "op": "saved.del", "text": d.lines[-1]["text"]}).encode()[: rng.choice([20, 10_000])]
            with open(os.path.join(folder, name), "wb") as f: f.write(body)
    if noise and sc.get("copies"):
        with open(os.path.join(log, "stray.jsonl"), "wb") as f:
            f.write(fmt({"t": (devs[0].lines[-1]["t"][0] + HOUR, 0), "d": devs[0].id, "op": "saved.del", "text": devs[0].lines[-1]["text"]}).encode() + b"\n")


def dump(path, state):
    """One entry a line: short, and diffs show the entry."""
    lists = (' "%s": [%s\n ]' % (k, ",".join("\n  " + json.dumps(e, ensure_ascii=False, separators=(",", ":")) for e in v)) for k, v in state.items())
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write("{\n" + ",\n".join(lists) + "\n}\n")


def build(out, sc):
    rng = random.Random("%d:%s" % (SEED, sc["name"]))
    devs = simulate(sc, rng)
    case = os.path.join(out, sc["name"])
    write_case(case, sc, devs, rng)
    state = merge(read(case))
    dump(os.path.join(case, "expected.json"), state)
    if sc.get("compacted"):
        # The same history compacted on every device: the same state.
        twin = os.path.join(out, sc["name"] + "-compacted")
        for d in devs:
            keep = compact(d.lines)
            d.lines = [l for l in d.lines if l["raw"] in keep]
        write_case(twin, sc, devs, rng)
        assert merge(read(twin)) == state, sc["name"]
        dump(os.path.join(twin, "expected.json"), state)
    return state


ALL = ["de", "ru", "fr", "es", "pl", "pt", "it", "cjk", "rtl", "emoji", "other"]
OCT = at(2026, 10, 31, 21)
SCENARIOS = [
    {"name": "mixed-2-devices", "devices": 2, "ops": 600, "groups": ALL, "base": at(2026, 9, 20)},
    {"name": "mixed-3-devices-skew", "devices": 3, "ops": 1200, "groups": ALL + ["edge"], "base": OCT, "skews": [-5 * HOUR, 0, 3 * HOUR], "jump": 2 * HOUR},
    {"name": "mixed-5-devices", "devices": 5, "ops": 2000, "groups": ALL + ["edge"], "base": at(2026, 8, 1),
     "steps": [0, 1000, 60_000, HOUR, 9 * HOUR], "flaky": True, "variants": 0.03},
    {"name": "hlc-ties", "devices": 3, "ops": 800, "groups": ["de", "ru", "cjk"], "base": OCT, "steps": [0, 0, 0, 1], "sync": 0.1, "clone": True},
    {"name": "races-put-del-put", "devices": 4, "ops": 1200, "groups": ALL, "hot": 12, "base": OCT, "steps": [0, 1, 500],
     "weights": {"saved.put": 40, "saved.del": 35, "history.seen": 10, "history.del": 10, "ai.put": 5}, "sync": 0.5},
    {"name": "offline-same-words", "devices": 4, "ops": 600, "groups": ["de", "fr"], "hot": 8, "base": OCT, "offline_start": True,
     "weights": {"saved.put": 50, "saved.del": 5, "history.seen": 35, "history.del": 2, "ai.put": 8}},
    {"name": "month-boundary", "devices": 3, "ops": 800, "groups": ALL, "base": at(2026, 10, 31, 23, 50), "steps": [0, 100, 1000],
     "skews": [-HOUR, 0, 20 * 60_000]},
    {"name": "year-boundary", "devices": 3, "ops": 800, "groups": ALL, "base": at(2026, 12, 31, 23, 55), "steps": [0, 100, 1000],
     "skews": [-2 * HOUR, 0, 30 * 60_000]},
    {"name": "conflicted-copies", "devices": 3, "ops": 600, "groups": ALL, "base": at(2026, 10, 25), "copies": True},
    {"name": "history-compaction", "devices": 3, "ops": 1500, "groups": ["de", "ru", "es"], "hot": 30, "base": at(2026, 10, 1), "compacted": True,
     "steps": [1000, 60_000, HOUR], "weights": {"saved.put": 20, "saved.del": 12, "history.seen": 45, "history.del": 10, "ai.put": 13}},
    {"name": "unknown-and-broken", "devices": 3, "ops": 500, "groups": ALL + ["edge"], "base": at(2026, 11, 1), "variants": 0.25, "broken": True, "cut": True},
    {"name": "edge-texts", "devices": 2, "ops": 300, "groups": ["edge"], "base": at(2026, 11, 1)},
] + [
    {"name": "lang-" + g, "devices": 2 + i % 3, "ops": 150, "groups": [g], "base": at(2026, 10, 30, 12), "answers": ["ru", "en"]}
    for i, g in enumerate(ALL)
]


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--stress":
        ops = int(sys.argv[3]) if len(sys.argv) > 3 else 50_000
        sc = {"name": "stress", "devices": 5, "ops": ops, "groups": ALL + ["edge"], "base": at(2026, 6, 1), "steps": [0, 1000, 60_000, HOUR],
              "skews": [-3 * HOUR, 0, 0, 2 * HOUR], "flaky": True, "variants": 0.02, "copies": True, "cut": True, "broken": True}
        shutil.rmtree(os.path.join(sys.argv[2], "stress"), ignore_errors=True)
        build(sys.argv[2], sc)
        return
    out = os.path.join(HERE, "generated")
    shutil.rmtree(out, ignore_errors=True)
    for sc in SCENARIOS:
        st = build(out, sc)
        print("%-24s saved %4d  history %4d  ai %4d" % (sc["name"], len(st["saved"]), len(st["history"]), len(st["ai"])))


if __name__ == "__main__":
    main()
