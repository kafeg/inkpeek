"""OPUS-MT (Helsinki-NLP) models for direct offline translation: the plan of models and their conversion for Bergamot.

A Tatoeba-MT model is Marian with a joint vocab.yml and tied embeddings; Bergamot reads only SentencePiece vocabularies.
remap() maps the embeddings to the ids of source.spm / target.spm, adds the target tags (">>rus<<") to source.spm
as whole pieces, then marian-conv (x86 only) makes it int8.
"""
import io
import itertools
import os
import shlex
import sqlite3
import subprocess
import zipfile

URL = "https://object.pouta.csc.fi/Tatoeba-MT-models/"
ISO3 = {"de": "deu", "en": "eng", "ru": "rus", "fr": "fra", "es": "spa", "it": "ita", "pt": "por", "pl": "pol", "uk": "ukr"}
CYRILLIC = {"ru", "uk"}
# One short sentence per source language: the converted model must translate it (not echo it, not stay empty).
CHECK = {"de": "Das Haus ist groß und alt.", "en": "The house is big and old.", "ru": "Дом большой и старый.",
         "fr": "La maison est grande et vieille.", "es": "La casa es grande y vieja.", "it": "La casa è grande e vecchia.",
         "pt": "A casa é grande e velha.", "pl": "Dom jest duży i stary.", "uk": "Будинок великий і старий."}


def plan(db_path, released_path, langs, tolerance=0.02):
    """Fewest released models that serve every direction within [tolerance] chrF of the best one (FLORES-200 devtest)."""
    db = sqlite3.connect(db_path)
    released = {l.strip() for l in open(released_path, encoding="utf-8")}
    best, scores = {}, {}
    for s, t in itertools.permutations(langs, 2):
        rows = [(m, sc) for m, sc in db.execute("select model, score from scores where langpair=? and testset='flores200-devtest' "
                                                "order by score desc", (f"{ISO3[s]}-{ISO3[t]}",))
                if m in released and m.startswith("Tatoeba-MT-models/")]
        if not rows:
            continue
        best[(s, t)] = rows[0][1]
        for m, sc in rows:
            scores.setdefault(m, {})[(s, t)] = sc
    need, out = set(best), []
    while need:
        ok = lambda m, d: d in scores[m] and scores[m][d] >= best[d] - tolerance  # noqa: E731
        m = max(scores, key=lambda m: sum(ok(m, d) for d in need))
        got = sorted(d for d in need if ok(m, d))
        out.append({"model": m.removeprefix("Tatoeba-MT-models/"), "covers": [f"{s}-{t}" for s, t in got],
                    "chrf": {f"{s}-{t}": round(scores[m][(s, t)] * 100, 1) for s, t in got}})
        need -= set(got)
    return out


def files_of(folder):
    """The Marian checkpoint, its vocab and the two SentencePiece models inside an unpacked model zip."""
    names = os.listdir(folder)
    pick = lambda f: os.path.join(folder, next(n for n in sorted(names) if f(n)))  # noqa: E731
    return (pick(lambda n: n.endswith(".npz") and "best-perplexity" in n), pick(lambda n: n.endswith(".vocab.yml")),
            pick(lambda n: n == "source.spm"), pick(lambda n: n == "target.spm"))


def remap(npz_in, vocab_yml, src_spm, trg_spm, out, targets):
    """Writes out/model.npz (embeddings by spm ids), out/srcvocab.spm (+ target tags), out/trgvocab.spm; returns the tags."""
    import numpy as np
    import sentencepiece as spm
    import yaml
    from sentencepiece import sentencepiece_model_pb2 as pb
    os.makedirs(out, exist_ok=True)
    vocab = {k: int(v) for k, v in yaml.load(open(vocab_yml, encoding="utf-8"), Loader=yaml.BaseLoader).items()}
    unk = vocab.get("<unk>", 1)
    # A multi-target model has a tag per target language in its vocabulary; a single-target one has none.
    tags = {t: f">>{ISO3[t]}<<" for t in targets if f">>{ISO3[t]}<<" in vocab}
    m = pb.ModelProto()
    m.ParseFromString(open(src_spm, "rb").read())
    have = {p.piece for p in m.pieces}
    for tag in tags.values():
        if tag not in have:
            p = m.pieces.add(); p.piece = tag; p.score = 0.0; p.type = pb.ModelProto.SentencePiece.USER_DEFINED
    src_out, trg_out = os.path.join(out, "srcvocab.spm"), os.path.join(out, "trgvocab.spm")
    open(src_out, "wb").write(m.SerializeToString())
    open(trg_out, "wb").write(open(trg_spm, "rb").read())

    def rows(path):
        sp = spm.SentencePieceProcessor(model_file=path)
        return np.array([vocab.get(sp.id_to_piece(i), unk) for i in range(sp.get_piece_size())])

    src_rows, trg_rows = rows(src_out), rows(trg_out)
    z = dict(np.load(npz_in))
    cfg = yaml.safe_load(z["special:model.yml"].tobytes().decode("utf-8").rstrip("\0"))
    if "Wemb" not in z:
        raise ValueError(f"{npz_in}: expected tied embeddings (Wemb)")
    w = z.pop("Wemb")
    z["encoder_Wemb"], z["decoder_Wemb"] = w[src_rows], w[trg_rows]
    z["decoder_ff_logit_out_b"] = z["decoder_ff_logit_out_b"][:, trg_rows]
    cfg.update({"tied-embeddings-all": False, "tied-embeddings-src": False, "tied-embeddings": True,
                "dim-vocabs": [len(src_rows), len(trg_rows)]})
    z["special:model.yml"] = np.frombuffer((yaml.safe_dump(cfg, allow_unicode=True) + "\0").encode("utf-8"), dtype=np.int8)
    # Marian's cnpy reads plain zip only; numpy's savez forces zip64 headers on every member.
    with zipfile.ZipFile(os.path.join(out, "model.npz"), "w", zipfile.ZIP_STORED, allowZip64=False) as zf:
        for k, v in z.items():
            buf = io.BytesIO(); np.lib.format.write_array(buf, np.ascontiguousarray(v), allow_pickle=False)
            zf.writestr(k + ".npy", buf.getvalue())
    return tags


CONFIG = """models: [model.intgemm8.bin]
vocabs: [srcvocab.spm, trgvocab.spm]
relative-paths: true
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 1
quiet: true
quiet-translation: true
gemm-precision: int8shiftAll
"""


def quantize(folder, marian_conv):
    """model.npz → model.intgemm8.bin; [marian_conv] is a command (may run in an x86 container)."""
    subprocess.run(shlex.split(marian_conv) + ["-f", os.path.join(folder, "model.npz"), "-t",
                                               os.path.join(folder, "model.intgemm8.bin"), "--gemm-type", "intgemm8"],
                   check=True, capture_output=True)
    os.remove(os.path.join(folder, "model.npz"))


def check(folder, bergamot, covers, tags):
    """Each covered direction translates its CHECK sentence: not empty, not the input, in the target's script."""
    cfg = os.path.join(folder, "check.yml")
    open(cfg, "w").write(CONFIG.replace("quiet: true\n", "quiet: false\n"))  # errors and their reasons in the log
    out = {}
    try:
        for d in covers:
            s, t = d.split("-")
            src = (tags.get(t, "") + " " + CHECK[s]).strip()
            r = subprocess.run(shlex.split(bergamot) + ["--model-config-paths", cfg], input=src + "\n", capture_output=True, text=True)
            if r.returncode != 0:
                raise RuntimeError(f"{d}: bergamot failed ({r.returncode}): {r.stderr[-1500:]} {r.stdout[-500:]}")
            got = r.stdout.strip()
            cyr = any("а" <= ch.lower() <= "я" or ch.lower() in "іїєґ" for ch in got)
            if not got or got == CHECK[s] or cyr != (t in CYRILLIC):
                raise ValueError(f"{d}: bad check translation {got!r}")
            out[d] = got
    finally:
        os.remove(cfg)
    return out
