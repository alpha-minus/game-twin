#!/usr/bin/env python3
"""
Normalize special/accented letters in RetroAchievements game data to common ASCII.
E.g. Pokémon -> Pokemon, Astérix -> Asterix.

Run from project root:
  python3 scripts/normalize_retroachievements_text.py

Reads and overwrites data/retroachievements/games/*.json (and optionally consoles.json).
"""

import json
import os
import unicodedata

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "data", "retroachievements")
GAMES_DIR = os.path.join(DATA_DIR, "games")

# Characters that don't decompose to base + combining (e.g. ß, œ)
SPECIAL_MAP = {
    "\u00df": "ss",   # ß
    "\u00e6": "ae",   # æ
    "\u00c6": "Ae",   # Æ
    "\u0153": "oe",   # œ
    "\u0152": "Oe",   # Œ
}


def normalize_text(s: str) -> str:
    if not s or not isinstance(s, str):
        return s
    for char, replacement in SPECIAL_MAP.items():
        s = s.replace(char, replacement)
    # NFD = decomposed form (é -> e + combining acute accent)
    nfd = unicodedata.normalize("NFD", s)
    # Keep only base characters (strip combining marks)
    return "".join(c for c in nfd if unicodedata.category(c) != "Mn")


def normalize_obj(obj):
    """Recursively normalize all string values in dicts/lists."""
    if isinstance(obj, dict):
        return {k: normalize_obj(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [normalize_obj(v) for v in obj]
    if isinstance(obj, str):
        return normalize_text(obj)
    return obj


def main() -> None:
    if not os.path.isdir(GAMES_DIR):
        print(f"Games dir not found: {GAMES_DIR}", flush=True)
        return

    count = 0
    for name in sorted(os.listdir(GAMES_DIR)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(GAMES_DIR, name)
        if not os.path.isfile(path):
            continue
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        normalized = normalize_obj(data)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(normalized, f, indent=2, ensure_ascii=False)
        count += 1
        print(f"Normalized {path}", flush=True)

    # Optionally normalize console names in consoles.json
    consoles_path = os.path.join(DATA_DIR, "consoles.json")
    if os.path.isfile(consoles_path):
        with open(consoles_path, "r", encoding="utf-8") as f:
            consoles = json.load(f)
        consoles = normalize_obj(consoles)
        with open(consoles_path, "w", encoding="utf-8") as f:
            json.dump(consoles, f, indent=2, ensure_ascii=False)
        print(f"Normalized {consoles_path}", flush=True)
        count += 1

    print(f"Done. Normalized {count} file(s).", flush=True)


if __name__ == "__main__":
    main()
