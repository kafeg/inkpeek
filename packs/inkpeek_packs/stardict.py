"""StarDict 3: a writer (.ifo, .idx, .syn, dictzip .dict.dz) and a small reader for input dictionaries."""
import gzip
import os
import struct
import zlib

CHUNK = 58315  # dictzip's usual chunk; each chunk inflates on its own


def sort_key(word):
    """StarDict order: ASCII case-insensitive, then byte order."""
    b = word.encode("utf-8")
    return (b.lower(), b)


def dictzip(data):
    """gzip with an "RA" extra field listing compressed chunk sizes (random access by chunk)."""
    chunks = [data[i:i + CHUNK] for i in range(0, len(data), CHUNK)] or [b""]
    comp = zlib.compressobj(9, zlib.DEFLATED, -15)
    parts = []
    for i, c in enumerate(chunks):
        parts.append(comp.compress(c) + comp.flush(zlib.Z_FINISH if i == len(chunks) - 1 else zlib.Z_FULL_FLUSH))
    if len(chunks) > 32000:
        raise ValueError("dictionary too large for one dictzip member")
    ra = struct.pack("<HHH", 1, CHUNK, len(chunks)) + b"".join(struct.pack("<H", len(p)) for p in parts)
    extra = b"RA" + struct.pack("<H", len(ra)) + ra
    head = b"\x1f\x8b\x08\x04" + b"\0\0\0\0" + b"\x02\x03" + struct.pack("<H", len(extra)) + extra
    tail = struct.pack("<II", zlib.crc32(data) & 0xFFFFFFFF, len(data) & 0xFFFFFFFF)
    return head + b"".join(parts) + tail


def write(base, articles, syn=(), bookname="", description="", types="h", date=""):
    """[articles]: headword → article; [syn]: (form, headword) pairs, kept only for known headwords."""
    words = sorted(articles, key=sort_key)
    index = {w: i for i, w in enumerate(words)}
    data, idx = bytearray(), bytearray()
    for w in words:
        a = articles[w].encode("utf-8")
        idx += w.encode("utf-8") + b"\0" + struct.pack(">II", len(data), len(a))
        data += a
    pairs = sorted({(f, index[h]) for f, h in syn if h in index and f != h}, key=lambda p: (sort_key(p[0]), p[1]))
    os.makedirs(os.path.dirname(base) or ".", exist_ok=True)
    with open(base + ".idx", "wb") as o:
        o.write(idx)
    with open(base + ".dict.dz", "wb") as o:
        o.write(dictzip(bytes(data)))
    if pairs:
        with open(base + ".syn", "wb") as o:
            for f, i in pairs:
                o.write(f.encode("utf-8") + b"\0" + struct.pack(">I", i))
    ifo = ["StarDict's dict ifo file", "version=3.0.0", f"bookname={bookname}", f"wordcount={len(words)}",
           f"idxfilesize={len(idx)}", f"sametypesequence={types}"]
    if pairs:
        ifo.append(f"synwordcount={len(pairs)}")
    if date:
        ifo.append(f"date={date}")
    if description:
        ifo.append("description=" + description.replace("\n", "<br>"))
    with open(base + ".ifo", "w", encoding="utf-8") as o:
        o.write("\n".join(ifo) + "\n")
    return {"words": len(words), "forms": len(pairs), "dict_bytes": len(data)}


def _read(path):
    for p in (path, path + ".dz", path + ".gz"):
        if os.path.exists(p):
            with open(p, "rb") as f:
                raw = f.read()
            return raw if p == path else gzip.decompress(raw)
    return None


class Reader:
    """A whole dictionary in memory: headwords with articles, and .syn forms."""

    def __init__(self, base):
        with open(base + ".ifo", encoding="utf-8") as f:
            self.info = dict(l.split("=", 1) for l in f.read().splitlines()[1:] if "=" in l)
        data, idx = _read(base + ".dict"), _read(base + ".idx")
        self.words = []
        i = 0
        while i < len(idx):
            j = idx.index(b"\0", i)
            off, size = struct.unpack(">II", idx[j + 1:j + 9])
            self.words.append((idx[i:j].decode("utf-8"), data[off:off + size].decode("utf-8", "replace")))
            i = j + 9
        self.syn = []
        raw = _read(base + ".syn") or b""
        i = 0
        while i < len(raw):
            j = raw.index(b"\0", i)
            (n,) = struct.unpack(">I", raw[j + 1:j + 5])
            self.syn.append((raw[i:j].decode("utf-8"), n))
            i = j + 5


def find(folder):
    """The base path (without .ifo) of the first dictionary in [folder]."""
    for root, _, files in os.walk(folder):
        for f in sorted(files):
            if f.endswith(".ifo"):
                return os.path.join(root, f[:-4])
    raise FileNotFoundError(f"no .ifo in {folder}")
