#!/usr/bin/env python3
"""
Remove duplicated game title variants from the CSV (same game, different punctuation).
Keeps the first occurrence of each normalized title.
"""
import csv
import re
from pathlib import Path

CSV_PATH = Path(__file__).resolve().parent.parent / "app/src/main/assets/retro_and_indie_games_unique.csv"
STOP_WORDS = {"the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for", "with", "from"}


def normalize(s: str) -> str:
    s = s.lower().strip()
    s = re.sub(r"[.:;,!?'\"()\-]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    tokens = [w for w in s.split() if w and w not in STOP_WORDS]
    return " ".join(tokens)


def main():
    with open(CSV_PATH, newline="", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        header = next(reader)
        rows = list(reader)

    title_idx = header.index("Title") if "Title" in header else 1
    seen_normalized = set()
    kept = []
    removed = []

    for row in rows:
        if len(row) <= title_idx:
            kept.append(row)
            continue
        title = row[title_idx].strip()
        norm = normalize(title)
        if not norm:
            kept.append(row)
            continue
        if norm in seen_normalized:
            removed.append(title)
            continue
        seen_normalized.add(norm)
        kept.append(row)

    with open(CSV_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(kept)

    print(f"Removed {len(removed)} duplicate variant(s):")
    for t in removed:
        print(f"  - {t}")
    print(f"Kept {len(kept)} rows (total was {len(rows)}).")


if __name__ == "__main__":
    main()
