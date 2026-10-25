"""Wiktionary data from kaikki.org (wiktextract): streamed, never unpacked."""
import gzip
import json
import os


def records(path, lang):
    """Entries of one language from a dump or a slice (.jsonl or .jsonl.gz)."""
    # A cheap substring check first; the real check is the top-level field (translations carry lang_code too).
    needle = f'"lang_code": "{lang}"'
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            if needle not in line:
                continue
            r = json.loads(line)
            if r.get("lang_code") == lang:
                yield r


def slice_dump(path, langs, out_dir, edition):
    """One pass over a dump → <out_dir>/<edition>.<lang>.jsonl.gz per language; returns counts."""
    os.makedirs(out_dir, exist_ok=True)
    outs = {l: gzip.open(os.path.join(out_dir, f"{edition}.{l}.jsonl.gz"), "wt", encoding="utf-8", compresslevel=3) for l in langs}
    needles = {l: f'"lang_code": "{l}"' for l in langs}
    counts = dict.fromkeys(langs, 0)
    opener = gzip.open if path.endswith(".gz") else open
    try:
        with opener(path, "rt", encoding="utf-8") as fh:
            for line in fh:
                hits = [l for l, n in needles.items() if n in line]
                if not hits:
                    continue
                code = json.loads(line).get("lang_code")
                if code in outs:
                    outs[code].write(line)
                    counts[code] += 1
    finally:
        for o in outs.values():
            o.close()
    return counts
