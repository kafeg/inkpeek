#!/usr/bin/env python3
"""The table of language packs (docs/languages.md), from what is published: packs.json and the dictionaries of release "packs".

  python3 packs/report.py --work /tmp/packs-report            # prints the table
  python3 packs/report.py --work /tmp/packs-report --readme docs/languages.md   # rewrites it between the markers

Coverage: the share of the book language's 5,000 most frequent words (FrequencyWords, OpenSubtitles 2018, letters only)
that the pair's dictionary knows, as a headword or an inflected form.
"""
import argparse
import json
import os
import zipfile

from build import FREQ, MIN_DIRECT, fetch
from inkpeek_packs import related

ASSETS = "https://github.com/kafeg/inkpeek/releases/download/packs/"
NAMES = {"en": "English", "de": "German", "ru": "Russian", "fr": "French", "es": "Spanish", "it": "Italian",
         "pt": "Portuguese", "pl": "Polish", "nl": "Dutch", "uk": "Ukrainian",
         "cs": "Czech", "bg": "Bulgarian", "fi": "Finnish", "hu": "Hungarian", "sv": "Swedish", "da": "Danish", "ro": "Romanian"}
BEGIN, END = "<!-- packs:begin -->", "<!-- packs:end -->"


def known(zip_path):
    """Every headword and form of a StarDict dictionary in a pack zip, lower case."""
    out = set()
    with zipfile.ZipFile(zip_path) as z:
        for n in z.namelist():
            step = 9 if n.endswith(".idx") else 5 if n.endswith(".syn") else 0
            if not step:
                continue
            b, i = z.read(n), 0
            while i < len(b):
                j = b.index(b"\0", i)
                out.add(b[i:j].decode("utf-8").lower())
                i = j + step
    return out


def steps(x, y):
    return [(x, y)] if "en" in (x, y) else [(x, "en"), ("en", y)]


def mb(n):
    return f"{n / 1e6:.0f} MB"


def table(work):
    manifest = json.load(open(fetch(ASSETS + "packs.json", os.path.join(work, "packs.json")), encoding="utf-8"))
    by_id = {c["id"]: c for c in manifest["components"]}
    langid = by_id.get("langid", {}).get("file", {}).get("size", 0)
    rows = []
    for c in sorted((c for c in manifest["components"] if c["kind"] == "dict"), key=lambda c: (c["to"] != "en", c["to"], c["from"])):
        x, y, st = c["from"], c["to"], c.get("stats", {})
        ws = known(fetch(ASSETS + c["file"]["name"], os.path.join(work, "dl", c["file"]["name"])))
        t = related.top(fetch(FREQ.format(x=x), os.path.join(work, "freq", f"{x}.txt")))
        cover = sum(w in ws for w in t) / len(t)
        models = [by_id.get(f"model-{a}-{b}") for a, b in steps(x, y)]
        route = " → ".join([NAMES[x]] + [NAMES[b] for _, b in steps(x, y)])
        kinds = ", ".join(sorted({f"{m.get('arch', '?')} {m.get('version', '')}".strip() for m in models if m}))
        size = c["file"]["size"] + sum(m["file"]["size"] for m in models if m) + langid
        src = []
        if st.get("tables"):
            src.append(f"{st['tables']:,} from English Wiktionary's translation tables")
        if st.get("wiktionary"):
            src.append(f"{st['wiktionary']:,} from {NAMES[y]} Wiktionary")
        if st.get("wikdict"):
            src.append(f"{st['wikdict']:,} from WikDict")
        label = "full" if cover >= 0.85 and all(models) else "basic" if cover >= MIN_DIRECT / 100 else "experimental"
        rows.append(f"| {NAMES[x]} → {NAMES[y]} | **{cover * 100:.0f}%** {label} | {st.get('words', 0):,} words, {st.get('forms', 0):,} forms "
                    f"| {'; '.join(src)} | {route} ({kinds}) | {mb(size)} |")
    head = ("| pair (book → answers) | top-5,000 coverage, label | dictionary | where the articles come from | offline translation "
            "(Mozilla model) | download |\n|---|---|---|---|---|---|")
    return head + "\n" + "\n".join(rows) + f"\n\n<sub>Packs of {manifest.get('generated', '?')}; table made by "\
        "[`packs/report.py`](../packs/report.py). Download: the dictionary, the models of the route (shared between pairs) and "\
        "language identification (1 MB).</sub>"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--work", required=True, help="download cache")
    p.add_argument("--readme", help="rewrite the table between the markers in this file")
    a = p.parse_args()
    t = table(a.work)
    if not a.readme:
        print(t)
        return
    s = open(a.readme, encoding="utf-8").read()
    i, j = s.index(BEGIN) + len(BEGIN), s.index(END)
    open(a.readme, "w", encoding="utf-8").write(s[:i] + "\n" + t + "\n" + s[j:])


if __name__ == "__main__":
    main()
