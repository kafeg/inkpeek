#!/usr/bin/env python3
"""The data of the separable-verb unit test, from a built pack: the sentences of the "sep" cases (device tests,
app/src/androidTest/assets/cases.tsv) with the forms and particles the pack has for their words.

  python3 scripts/separable-fixture.py dict-de-en.zip > app/src/test/resources/separable-de.tsv
"""
import re
import struct
import sys
import zipfile

CASES = "app/src/androidTest/assets/cases.tsv"


def main(pack):
    z = zipfile.ZipFile(pack)
    name = lambda ext: next(n for n in z.namelist() if n.endswith(ext))  # noqa: E731
    idx, words, i = z.read(name(".idx")), [], 0
    while i < len(idx):
        j = idx.index(b"\0", i); words.append(idx[i:j].decode()); i = j + 9
    forms = {}
    for w in words:
        forms.setdefault(w.lower(), set()).add(w)
    syn, i = z.read(name(".syn")), 0
    while i < len(syn):
        j = syn.index(b"\0", i)
        forms.setdefault(syn[i:j].decode().lower(), set()).add(words[struct.unpack(">I", syn[j + 1:j + 5])[0]])
        i = j + 5
    particle = {}
    for line in z.read(name(".rel")).decode().splitlines():
        c = line.split("\t")
        if len(c) > 9 and c[9]:
            particle[c[0]] = c[9]
    cases = [l.rstrip("\n").split("\t") for l in open(CASES, encoding="utf-8") if l.startswith("sep\t")]
    tokens = {t for _, _, s, _ in cases for t in re.findall(r"[^\W\d_]+", s)}
    print(f"# From {pack.split('/')[-1]} by scripts/separable-fixture.py: \"?sentence\\texpected\" (the device cases), "
          "\"form\\tlemmas\", \"=verb\\tparticle\"")
    for _, _, s, e in cases:
        print(f"?{s}\t{e}")
    need = set()
    for t in sorted({t.lower() for t in tokens}):
        if t in forms:
            print(t + "\t" + "|".join(sorted(forms[t])))
            need |= {p + b for b in forms[t] for p in tokens if p.islower()}
    for v in sorted(need & particle.keys()):
        print(f"={v}\t{particle[v]}")


if __name__ == "__main__":
    main(sys.argv[1])
