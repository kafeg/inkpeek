"""Firefox Translations models from Mozilla's Remote Settings, mirrored into our releases (MPL-2.0)."""
import hashlib
import io
import json
import os
import subprocess
import tarfile
import urllib.request

RECORDS = "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models-v2/records"
FILES = "https://firefox-settings-attachments.cdn.mozilla.net/"
ARCH = ["base", "base-memory", "tiny"]  # best first


def best_sets(records):
    """(from, to) → the best complete set: full model first, newest version; nightly-only and alpha skipped."""
    groups = {}
    for r in records:
        if "channel" in r.get("filter_expression", "") or not all(c.isdigit() or c == "." for c in r["version"]):
            continue
        groups.setdefault((r["sourceLanguage"], r["targetLanguage"], r["version"], r["architecture"]), []).append(r)
    best = {}
    for (s, t, v, a), rs in groups.items():
        types = {r["fileType"] for r in rs}
        if not ({"model", "lex"} <= types and ("vocab" in types or {"srcvocab", "trgvocab"} <= types)):
            continue
        rank = (ARCH.index(a) if a in ARCH else 99, [-int(p) for p in v.split(".")])
        if (s, t) not in best or rank < best[(s, t)][0]:
            best[(s, t)] = (rank, {"from": s, "to": t, "version": v, "arch": a, "files": rs})
    return {k: v for k, (_, v) in best.items()}


def fetch_records():
    with urllib.request.urlopen(RECORDS, timeout=60) as r:
        return json.load(r)["data"]


def build(s, out_path, work):
    """Download one set, check every file, pack as tar.zst (original names + a 'set' line)."""
    os.makedirs(work, exist_ok=True)
    raw = io.BytesIO()
    with tarfile.open(fileobj=raw, mode="w") as tar:
        for r in s["files"]:
            url = FILES + r["attachment"]["location"]
            zst = os.path.join(work, r["name"] + ".zst")
            if not os.path.exists(zst):
                urllib.request.urlretrieve(url, zst)
            data = subprocess.run(["zstd", "-dc", zst], check=True, capture_output=True).stdout
            if hashlib.sha256(data).hexdigest() != r["decompressedHash"]:
                raise ValueError(f"checksum mismatch: {r['name']}")
            info = tarfile.TarInfo(r["name"]); info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
        line = f"{s['version']} {s['arch']}\n".encode()
        info = tarfile.TarInfo("set"); info.size = len(line)
        tar.addfile(info, io.BytesIO(line))
    subprocess.run(["zstd", "-q", "-19", "-T0", "-f", "-o", out_path], input=raw.getvalue(), check=True)
