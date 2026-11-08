#!/usr/bin/env python3
"""
The words the word ladder accepts as steps: every Spanish word of 4 and 5 letters in the public-domain
word list of Debian's wspanish package (/usr/share/dict/spanish), plain, without accents, plus the
app's own words of those lengths. A step may be any of them, though puzzles are made from the app's
own, common words.

Writes app/src/main/assets/ladder.txt, one word per line. Re-run with wspanish installed:
    python3 tools/generate_ladder_words.py
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
SOURCE = Path("/usr/share/dict/spanish")
LENGTHS = (4, 5)
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
        if len(p) in LENGTHS and set(p) <= LETTERS:
            words.add(p)
    own = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    for length in LENGTHS:
        words.update(plain(w) for w in own[str(length)])
    out = ASSETS / "ladder.txt"
    out.write_text("\n".join(sorted(words)) + "\n", encoding="utf-8")
    print(f"{len(words)} words -> {out}")


if __name__ == "__main__":
    main()
