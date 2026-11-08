#!/usr/bin/env python3
"""
The words the honeycomb (Panal) accepts: every Spanish word of 4 to 12 letters in the public-domain
word list of Debian's wspanish package (/usr/share/dict/spanish), plain, without accents, plus the
app's own words. Its puzzles are made from the app's own, common words.

Writes app/src/main/assets/panal.txt, one word per line. Re-run with wspanish installed:
    python3 tools/generate_panal_words.py
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SOURCE = Path("/usr/share/dict/spanish")
LETTERS = set("abcdefghijklmnñopqrstuvwxyz")
FOLD = str.maketrans("áéíóúü", "aeiouu")


def plain(word):
    return word.lower().translate(FOLD)


def main():
    words = set()
    for line in SOURCE.read_text(encoding="utf-8").splitlines():
        word = line.strip()
        # Lowercase only: capitalised entries are names.
        if not word or not word.islower():
            continue
        p = plain(word)
        if 4 <= len(p) <= 12 and set(p) <= LETTERS:
            words.add(p)
    own = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    words.update(plain(w) for ws in own.values() for w in ws)
    out = ASSETS / "panal.txt"
    out.write_text("\n".join(sorted(words)) + "\n", encoding="utf-8")
    print(f"{len(words)} words -> {out}")


if __name__ == "__main__":
    main()
