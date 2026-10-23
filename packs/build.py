#!/usr/bin/env python3
"""InkPeek language packs. Usage:
  build.py slices --langs de,en --answers ru,en --work W   # slices from release packs-sources, re-cut when a dump changed
  build.py all --langs de,en --answers ru,en --work W --out O   # grammar, pair dictionaries, models, langid
  build.py grammar X | dict X Y | models | langid             # one step (same --work / --out)
  build.py publish --out O --release packs                    # upload O's assets, merge packs.json in the release
  build.py opus [model ...] --marian-conv CMD --bergamot CMD  # OPUS-MT models of opus.json, converted for Bergamot
  build.py opus-convert [model ...] | opus-finish              # the same in two steps: x86 (int8), then ARM (check, pack)
  build.py llm                                                # AI translation models of llm.json (GGUF, mirrored)
Inputs: kaikki.org dumps (sliced per language), WikDict, Mozilla's models, fastText's language identification,
OPUS-MT (Helsinki-NLP), Hy-MT2 (Tencent)."""
import argparse
import datetime
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from inkpeek_packs import dictionary, grammar, kaikki, models, opus, related, stardict  # noqa: E402

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


LANGID_URL = "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.ftz"


def gh(*args, check=True):
    return subprocess.run(["gh", *args], check=check, capture_output=True, text=True)


def remote_stamp(url):
    """ETag / Last-Modified / size of a remote file: a changed stamp means a new dump."""
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "InkPeek-packs"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return "|".join(r.headers.get(h, "") for h in ("ETag", "Last-Modified", "Content-Length"))


def editions(a):
    """The Wiktionary editions this run reads: en (grammar of every language), each answer's, each book language's own
    (German grammar; related words, synonyms, expressions of every book language)."""
    return sorted({"en"} | {y for y in a.answers + a.langs if y in SOURCES["kaikki"]})


MIN_DIRECT = 60
FREQ = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{x}/{x}_50k.txt"
_related = {}


def related_lines(a, x):
    """The .rel lines of book language [x]: its own Wiktionary first, then en.wiktionary; built once per run."""
    if x not in _related:
        data = {}
        if x in SOURCES["kaikki"] and x != "en":
            related.collect(kaikki.records(slice_file(a, x, x), x), data)
        related.collect(kaikki.records(slice_file(a, "en", x), x), data)
        ranks = related.rank(fetch(FREQ.format(x=x), os.path.join(a.work, "freq", f"{x}.txt")))
        _related[x] = related.lines(data, ranks)
        print(f"related-{x}: {len(_related[x])} words", flush=True)
    return _related[x]


def cmd_slices(a):
    """Make W/slices hold <edition>.<lang>.jsonl.gz for every language of the list, from release packs-sources;
    a dump whose stamp changed (or a missing slice) is downloaded and cut again for all languages, then uploaded."""
    rel, d = a.sources_release, os.path.join(a.work, "slices")
    os.makedirs(d, exist_ok=True)
    langs = sorted(SOURCES["languages"])
    have = gh("release", "view", rel, "--json", "assets", "--jq", ".assets[].name", check=False)
    assets = set(have.stdout.split()) if have.returncode == 0 else set()
    if have.returncode != 0:
        gh("release", "create", rel, "--title", "Pack sources (kaikki slices)", "--notes",
           "Per-language slices of the kaikki.org Wiktionary dumps (CC BY-SA 4.0), used to build the packs.", "--prerelease")
    state = {}
    if "state.json" in assets:
        gh("release", "download", rel, "-p", "state.json", "-D", d, "--clobber")
        state = json.load(open(os.path.join(d, "state.json")))
    for ed in editions(a):
        url = SOURCES["kaikki"][ed]
        stamp = remote_stamp(url)
        names = [f"{ed}.{l}.jsonl.gz" for l in langs]
        if state.get(ed) == stamp and all(n in assets for n in names):
            for n in names:
                if n.split(".")[1] in set(a.langs) | set(a.answers):
                    gh("release", "download", rel, "-p", n, "-D", d, "--clobber")
            print(f"slices {ed}: from {rel}", flush=True)
            continue
        print(f"slices {ed}: dump changed or slices missing — cutting", flush=True)
        counts = kaikki.slice_dump(fetch(url, dump(a, ed)), langs, d, ed)
        print(f"  {counts}", flush=True)
        os.remove(dump(a, ed))
        gh("release", "upload", rel, *[os.path.join(d, n) for n in names], "--clobber")
        state[ed] = stamp
        json.dump(state, open(os.path.join(d, "state.json"), "w"), indent=1)
        gh("release", "upload", rel, os.path.join(d, "state.json"), "--clobber")


def cmd_langid(a):
    """fastText's language identification model, mirrored (CC BY-SA 3.0)."""
    path = fetch(LANGID_URL, os.path.join(a.out, "langid-176.ftz"))
    return {"id": "langid", "kind": "langid", "version": "176",
            "file": {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)},
            "license": "CC-BY-SA-3.0", "attribution": "fastText language identification (lid.176), Facebook AI Research"}


def cmd_publish(a):
    """Upload this run's assets to the release and merge their entries into its packs.json."""
    mine = json.load(open(os.path.join(a.out, "packs.json"), encoding="utf-8"))
    have = gh("release", "view", a.release, "--json", "assets", "--jq", ".assets[].name", check=False)
    if have.returncode != 0:
        gh("release", "create", a.release, "--latest=false", "--title", "Language packs", "--notes",
           "InkPeek language packs: grammar, dictionaries (CC BY-SA 4.0: Wiktionary contributors, Wiktextract/kaikki.org, "
           "WikDict), translation models (MPL-2.0, Mozilla), language identification (CC BY-SA 3.0, fastText). "
           "Built by packs/ in this repository.")
        old = []
    else:
        tmp = os.path.join(a.work, "published")
        os.makedirs(tmp, exist_ok=True)
        r = gh("release", "download", a.release, "-p", "packs.json", "-D", tmp, "--clobber", check=False)
        old = json.load(open(os.path.join(tmp, "packs.json"), encoding="utf-8"))["components"] if r.returncode == 0 else []
    ids = {c["id"] for c in mine["components"]}
    # Only what the app downloads (grammar is a build input since 21.10); stale assets go from the release too.
    kinds = {"dict", "model", "langid", "opus", "llm"}
    merged = sorted([c for c in old if c["id"] not in ids and c["kind"] in kinds] + mine["components"], key=lambda c: c["id"])
    for c in old:
        if c["kind"] not in kinds:
            gh("release", "delete-asset", a.release, c["file"]["name"], "--yes", check=False)
    path = os.path.join(a.out, "packs.json")
    json.dump({"format": 1, "generated": a.date, "components": merged}, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    files = [os.path.join(a.out, c["file"]["name"]) for c in mine["components"]]
    gh("release", "upload", a.release, *files, path, "--clobber")
    print(f"published {sorted(ids)} to {a.release}; {len(merged)} components in packs.json", flush=True)


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
    # Same content → same bytes → same sha256: a fixed time in the zip, files in a fixed order.
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for root, _, files in sorted(os.walk(folder)):
            for f in sorted(files):
                info = zipfile.ZipInfo(os.path.relpath(os.path.join(root, f), os.path.dirname(folder)), (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(os.path.join(root, f), "rb") as src:
                    z.writestr(info, src.read(), compresslevel=9)
    return {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)}


def cmd_grammar(a, x):
    g = build_grammar(a, x)
    folder = os.path.join(a.out, "build", f"grammar-{x}")
    shutil.rmtree(folder, ignore_errors=True)
    st = stardict.write(os.path.join(folder, f"grammar-{x}"), g.entries(), g.syn(), types="m",
                        bookname=f"InkPeek grammar {x}", description=ATTRIBUTION)
    print(f"grammar-{x}: {st}", flush=True)
    return None  # build input only: the dictionaries carry its header and forms; kept in build/ for the golden tests


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
    tables = dictionary.translations(kaikki.records(slice_file(a, "en", "en"), "en"), y) if x == "en" else None
    articles, syn, st = dictionary.build(x, y, kaikki.records(slice_file(a, y, x), x), g, wd, tables)
    folder = os.path.join(a.out, "build", f"dict-{x}-{y}")
    shutil.rmtree(folder, ignore_errors=True)
    st.update(stardict.write(os.path.join(folder, f"dict-{x}-{y}"), articles, syn, types="h",
                             bookname=f"InkPeek {x} → {y}", description=ATTRIBUTION))
    # Related words, synonyms, expressions, forms, IPA, origin of the book language's words (the card's article rows).
    rel = related_lines(a, x)
    with open(os.path.join(folder, f"dict-{x}-{y}.rel"), "w", encoding="utf-8") as f:
        f.write("\n".join(rel) + "\n")
    st["related"] = len(rel)
    # The share of the book language's 5,000 most frequent words the pair knows (headword or form): the pack's label.
    known = {w.lower() for w in articles} | {f.lower() for f, h in syn if h in articles}
    st["coverage"] = related.coverage(known, fetch(FREQ.format(x=x), os.path.join(a.work, "freq", f"{x}.txt")))
    print(f"dict-{x}-{y}: {st}", flush=True)
    # A pair without English is published only when it is at least basic: the reader has the pair through English too.
    if "en" not in (x, y) and st["coverage"] < MIN_DIRECT:
        print(f"dict-{x}-{y}: coverage {st['coverage']} < {MIN_DIRECT}, not published", flush=True)
        shutil.rmtree(folder, ignore_errors=True)
        return None
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


def cmd_opus_convert(a, only):
    """Each model of opus.json: download, vocab remapped to SentencePiece, int8 (x86) → <out>/opus/<id>/ with meta.json."""
    for e in json.load(open(os.path.join(HERE, "opus.json"), encoding="utf-8")):
        name = e["model"].split("/")[0]
        cid = "opus-" + name.replace("+", "_")  # "+" in a release asset name is not kept by GitHub
        if only and cid not in only and name not in only:
            continue
        work = os.path.join(a.work, "opus", name)
        src = os.path.join(work, "src")
        if not os.path.isdir(src):
            zp = fetch(opus.URL + e["model"] + ".zip", os.path.join(work, "model.zip"))
            zipfile.ZipFile(zp).extractall(src)
            os.remove(zp)
        folder = os.path.join(a.out, "opus", cid)
        shutil.rmtree(folder, ignore_errors=True)
        tags = opus.remap(*opus.files_of(src), folder, sorted({d.split("-")[1] for d in e["covers"]}))
        opus.quantize(folder, a.marian_conv)
        lic = open(os.path.join(src, "LICENSE"), encoding="utf-8", errors="replace").read() if os.path.exists(os.path.join(src, "LICENSE")) else ""
        license_id = "Apache-2.0" if "Apache License" in lic else "CC-BY-4.0"
        attribution = (f"OPUS-MT, Helsinki-NLP: {e['model']} ({opus.URL}{e['model']}.zip). Changed by InkPeek packs: "
                       f"vocabulary mapped to SentencePiece, weights quantized to int8. License: {license_id}.")
        open(os.path.join(folder, "ATTRIBUTION"), "w", encoding="utf-8").write(attribution + "\n")
        if lic:
            open(os.path.join(folder, "LICENSE"), "w", encoding="utf-8").write(lic)
        json.dump({"id": cid, "covers": e["covers"], "tags": tags, "chrf": e["chrf"], "license": license_id, "attribution": attribution},
                  open(os.path.join(folder, "meta.json"), "w", encoding="utf-8"), ensure_ascii=False)
        shutil.rmtree(src)  # ~1 GB per model: a CI runner has room for one at a time
        print(f"{cid}: converted, tags {tags}", flush=True)


def cmd_opus_finish(a):
    """Converted models (<out>/opus/*): a check translation per direction (on ARM, the app's code path), then packed."""
    out = []
    root = os.path.join(a.out, "opus")
    for cid in sorted(os.listdir(root)) if os.path.isdir(root) else []:
        folder = os.path.join(root, cid)
        meta = json.load(open(os.path.join(folder, "meta.json"), encoding="utf-8"))
        got = opus.check(folder, a.bergamot, meta["covers"], meta["tags"])
        print(f"{cid}: {len(got)} directions checked; {got.get('de-ru') or next(iter(got.values()))}", flush=True)
        os.remove(os.path.join(folder, "meta.json"))
        path = os.path.join(a.out, cid + ".tar.zst")
        subprocess.run(f"tar -C {shlex.quote(root)} -cf - {shlex.quote(cid)} | zstd -q -19 -T0 -f -o {shlex.quote(path)}", shell=True, check=True)
        out.append({"id": cid, "kind": "opus", "from": "", "to": "", "covers": meta["covers"], "tags": meta["tags"], "chrf": meta["chrf"],
                    "ram_mb": 3500, "file": {"name": os.path.basename(path), "size": os.path.getsize(path), "sha256": sha256(path)},
                    "license": meta["license"], "attribution": meta["attribution"]})
        shutil.rmtree(folder)
    return out


def cmd_llm(a):
    """AI translation models of llm.json: the GGUF as published (checked against the Hub's sha256), mirrored."""
    out = []
    for e in json.load(open(os.path.join(HERE, "llm.json"), encoding="utf-8")):
        url = f"https://huggingface.co/{e['repo']}/resolve/{e['revision']}/{e['file']}"
        tree = json.load(urllib.request.urlopen(f"https://huggingface.co/api/models/{e['repo']}/tree/{e['revision']}", timeout=60))
        want = next(f["lfs"]["oid"] for f in tree if f["path"] == e["file"])
        name = e["id"] + ".gguf"
        path = fetch(url, os.path.join(a.out, name))
        if sha256(path) != want:
            raise ValueError(f"{e['file']}: sha256 differs from the Hub")
        out.append({"id": e["id"], "kind": "llm", "from": "", "to": "", "name": e["name"], "covers": e["covers"],
                    "template": e["template"], "names": e["names"], "ram_mb": e["ram_mb"], "cpu": e["cpu"], "mirror": url,
                    "file": {"name": name, "size": os.path.getsize(path), "sha256": want},
                    "license": e["license"], "attribution": e["attribution"]})
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
    p.add_argument("--release", default="packs")
    p.add_argument("--sources-release", default="packs-sources")
    p.add_argument("--marian-conv", default="marian-conv", help="command that runs marian-conv (x86: int8 packing)")
    p.add_argument("--bergamot", default="bergamot", help="command that runs the bergamot CLI (check translations)")
    a = p.parse_args()
    a.langs, a.answers = a.langs.split(","), a.answers.split(",")
    os.makedirs(a.out, exist_ok=True)
    if a.cmd == "slices":
        return cmd_slices(a)
    if a.cmd == "publish":
        return cmd_publish(a)
    comps = []
    if a.cmd in ("all", "grammar"):
        for x in (a.args or a.langs):
            cmd_grammar(a, x)
    if a.cmd in ("all", "dict"):
        pairs = [tuple(a.args)] if a.args else [(x, y) for x in a.langs for y in a.answers if x != y]
        comps += [c for c in (cmd_dict(a, x, y) for x, y in pairs) if c]
    if a.cmd in ("all", "models"):
        dirs = sorted({(x, "en") for x in a.langs if x != "en"} | {("en", y) for y in a.answers if y != "en"})
        comps += cmd_models(a, dirs)
    if a.cmd in ("all", "langid"):
        comps.append(cmd_langid(a))
    if a.cmd in ("opus", "opus-convert"):
        cmd_opus_convert(a, set(a.args))
    if a.cmd in ("opus", "opus-finish"):
        comps += cmd_opus_finish(a)
    if a.cmd == "llm":
        comps += cmd_llm(a)
    if comps:
        manifest = os.path.join(a.out, "packs.json")
        old = json.load(open(manifest, encoding="utf-8"))["components"] if os.path.exists(manifest) else []
        ids = {c["id"] for c in comps}
        json.dump({"format": 1, "generated": a.date, "components": [c for c in old if c["id"] not in ids] + comps},
                  open(manifest, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
