#!/usr/bin/env python3
"""InkPeek language packs. Usage:
  build.py all --langs de,en --answers ru,en --work W --out O   # everything for these languages
  build.py slice | grammar X | dict X Y | models | manifest       # one step (same --work / --out)
Inputs: kaikki.org dumps (fetched into W/dl, sliced per language into W/slices), WikDict, Mozilla's models."""
import argparse
import datetime
import hashlib
import json
import os
import shutil
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inkpeek_packs import dictionary, grammar, kaikki, models, stardict  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = json.load(open(os.path.join(HERE, "sources.json"), encoding="utf-8"))
ATTRIBUTION = ("Wiktionary contributors (CC BY-SA 4.0); data extracted by Wiktextract / kaikki.org; "
               "WikDict (CC BY-SA 4.0, from DBnary). Built by InkPeek packs. License: CC BY-SA 4.0.")


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "InkPeek-packs"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path + ".part", "wb") as o:
            shutil.copyfileobj(r, o, 1 << 20)
        os.replace(path + ".part", path)
    return path


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def dump(a, edition):
    return os.path.join(a.work, "dl", f"kaikki-{edition}.jsonl.gz")


def slice_file(a, edition, lang):
    """The slice of [lang] in the [edition] Wiktionary; made on demand from the dump."""
    for ext in (".jsonl.gz", ".jsonl"):
        p = os.path.join(a.work, "slices", f"{edition}.{lang}{ext}")
        if os.path.exists(p):
            return p
    langs = sorted(set(a.langs) | {lang})
    print(f"slice {edition}: {','.join(langs)}", flush=True)
    counts = kaikki.slice_dump(fetch(SOURCES["kaikki"][edition], dump(a, edition)), langs, os.path.join(a.work, "slices"), edition)
    print(f"  {counts}", flush=True)
    return os.path.join(a.work, "slices", f"{edition}.{lang}.jsonl.gz")


_grammars = {}


def build_grammar(a, x):
    if x in _grammars:
        return _grammars[x]
    g = grammar.Grammar(x)
    name = SOURCES["languages"][x]
    if x == "de":
        grammar.from_dewiktionary(kaikki.records(slice_file(a, "de", "de"), "de"), g)
        grammar.from_enwiktionary(kaikki.records(slice_file(a, "en", "de"), "de"), g, fill=True)
    else:
        grammar.from_enwiktionary(kaikki.records(slice_file(a, "en", x), x), g, irregular_category=f"{name} irregular verbs")
    grammar.overrides(os.path.join(HERE, "overrides", f"{x}.tsv"), g)
    _grammars[x] = g
    return g


def pack(a, cid, folder):
    """Zip a built folder as <out>/<cid>.zip; returns its manifest file entry."""
    path = os.path.join(a.out, f"{cid}.zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for root, _, files in os.walk(folder):
            for f in sorted(files):
                z.write(os.path.join(root, f), os.path.relpath(os.path.join(root, f), os.path.dirname(folder)))
    return {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)}


def cmd_grammar(a, x):
    g = build_grammar(a, x)
    folder = os.path.join(a.out, "build", f"grammar-{x}")
    shutil.rmtree(folder, ignore_errors=True)
    st = stardict.write(os.path.join(folder, f"grammar-{x}"), g.entries(), g.syn(), types="m", date=a.date,
                        bookname=f"InkPeek grammar {x}", description=ATTRIBUTION)
    print(f"grammar-{x}: {st}", flush=True)
    return {"id": f"grammar-{x}", "kind": "grammar", "from": x, "file": pack(a, f"grammar-{x}", folder),
            "license": "CC-BY-SA-4.0", "attribution": ATTRIBUTION, "stats": st}


def cmd_dict(a, x, y):
    g = build_grammar(a, x)
    if y not in SOURCES["kaikki"]:
        print(f"dict-{x}-{y}: no {y} Wiktionary in kaikki, skipped", flush=True)
        return None
    wd = None
    try:
        z = fetch(SOURCES["wikdict"].format(x=x, y=y), os.path.join(a.work, "dl", f"wikdict-{x}-{y}.zip"))
        d = os.path.join(a.work, "wikdict", f"{x}-{y}")
        if not os.path.isdir(d):
            zipfile.ZipFile(z).extractall(d)
        wd = stardict.find(d)
    except Exception as e:  # not every pair exists at WikDict
        print(f"  no WikDict {x}-{y}: {e}", flush=True)
    articles, syn, st = dictionary.build(x, y, kaikki.records(slice_file(a, y, x), x), g, wd)
    folder = os.path.join(a.out, "build", f"dict-{x}-{y}")
    shutil.rmtree(folder, ignore_errors=True)
    st.update(stardict.write(os.path.join(folder, f"dict-{x}-{y}"), articles, syn, types="h", date=a.date,
                             bookname=f"InkPeek {x} → {y}", description=ATTRIBUTION))
    print(f"dict-{x}-{y}: {st}", flush=True)
    return {"id": f"dict-{x}-{y}", "kind": "dict", "from": x, "to": y, "file": pack(a, f"dict-{x}-{y}", folder),
            "license": "CC-BY-SA-4.0", "attribution": ATTRIBUTION, "stats": st}


def cmd_models(a, directions):
    sets = models.best_sets(models.fetch_records())
    out = []
    for s, t in directions:
        if (s, t) not in sets:
            print(f"model-{s}-{t}: none at Mozilla", flush=True)
            continue
        m = sets[(s, t)]
        path = os.path.join(a.out, f"model-{s}-{t}.tar.zst")
        models.build(m, path, os.path.join(a.work, "models", f"{s}{t}"))
        print(f"model-{s}-{t}: {m['version']} {m['arch']}", flush=True)
        out.append({"id": f"model-{s}-{t}", "kind": "model", "from": s, "to": t, "version": m["version"], "arch": m["arch"],
                    "file": {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)},
                    "license": "MPL-2.0", "attribution": "Mozilla Firefox Translations (github.com/mozilla/translations)"})
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("cmd")
    p.add_argument("args", nargs="*")
    p.add_argument("--langs", default="de,en")
    p.add_argument("--answers", default="ru,en")
    p.add_argument("--work", default="work")
    p.add_argument("--out", default="out")
    p.add_argument("--date", default=datetime.date.today().isoformat())
    a = p.parse_args()
    a.langs, a.answers = a.langs.split(","), a.answers.split(",")
    os.makedirs(a.out, exist_ok=True)
    comps = []
    if a.cmd in ("all", "grammar"):
        for x in (a.args or a.langs):
            comps.append(cmd_grammar(a, x))
    if a.cmd in ("all", "dict"):
        pairs = [tuple(a.args)] if a.args else [(x, y) for x in a.langs for y in a.answers if x != y]
        comps += [c for c in (cmd_dict(a, x, y) for x, y in pairs) if c]
    if a.cmd in ("all", "models"):
        dirs = sorted({(x, "en") for x in a.langs if x != "en"} | {("en", y) for y in a.answers if y != "en"})
        comps += cmd_models(a, dirs)
    if comps:
        manifest = os.path.join(a.out, "packs.json")
        old = json.load(open(manifest, encoding="utf-8"))["components"] if os.path.exists(manifest) else []
        ids = {c["id"] for c in comps}
        json.dump({"format": 1, "generated": a.date, "components": [c for c in old if c["id"] not in ids] + comps},
                  open(manifest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
