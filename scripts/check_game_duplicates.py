#!/usr/bin/env python3
"""
Similarity analysis on game list CSV to find exact and near-duplicate entries.
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


def token_set(s: str) -> set:
    return set(normalize(s).split())


def jaccard(a: set, b: set) -> float:
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def levenshtein(s1: str, s2: str) -> int:
    s1, s2 = s1.lower(), s2.lower()
    if s1 == s2:
        return 0
    if not s1:
        return len(s2)
    if not s2:
        return len(s1)
    prev = list(range(len(s2) + 1))
    for i, c1 in enumerate(s1):
        curr = [i + 1]
        for j, c2 in enumerate(s2):
            curr.append(min(
                prev[j + 1] + 1,
                curr[j] + 1,
                prev[j] + (0 if c1 == c2 else 1),
            ))
        prev = curr
    return prev[-1]


def levenshtein_ratio(s1: str, s2: str) -> float:
    d = levenshtein(s1, s2)
    m = max(len(s1), len(s2), 1)
    return 1.0 - (d / m)


def main():
    with open(CSV_PATH, newline="", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        header = next(reader)
        # Title is column index 1
        title_idx = header.index("Title") if "Title" in header else 1
        rows = list(reader)

    titles = []
    for row in rows:
        if len(row) > title_idx and row[title_idx].strip():
            titles.append(row[title_idx].strip())

    print(f"Total entries: {len(titles)}\n")

    # 1) Exact duplicates (same string)
    seen = {}
    exact_dupes = []
    for i, t in enumerate(titles):
        if t in seen:
            exact_dupes.append((t, seen[t], i))
        else:
            seen[t] = i
    if exact_dupes:
        print("=== EXACT DUPLICATES (same title string) ===")
        for title, first_idx, dup_idx in exact_dupes:
            print(f"  '{title}' @ rows {first_idx+2} and {dup_idx+2}")
        print(f"Count: {len(exact_dupes)}\n")
    else:
        print("No exact duplicate titles.\n")

    # 2) Normalized duplicates (same after normalizing)
    norm_to_originals = {}
    for i, t in enumerate(titles):
        n = normalize(t)
        if not n:
            continue
        norm_to_originals.setdefault(n, []).append((i, t))

    norm_dupes = {n: origs for n, origs in norm_to_originals.items() if len(origs) > 1}
    if norm_dupes:
        print("=== NORMALIZED DUPLICATES (same after normalizing) ===")
        for n, origs in sorted(norm_dupes.items(), key=lambda x: -len(x[1])):
            print(f"  normalized: '{n}'")
            for idx, orig in origs:
                print(f"    -> '{orig}' (row {idx+2})")
            print()
        print(f"Groups: {len(norm_dupes)}\n")
    else:
        print("No normalized duplicates.\n")

    # 3) High-similarity pairs (likely same game, different form)
    # Only compare pairs that share at least one token (faster)
    n = len(titles)
    token_to_indices = {}
    for i, t in enumerate(titles):
        for tok in token_set(t):
            token_to_indices.setdefault(tok, set()).add(i)

    pairs = []
    seen_pair = set()
    for i in range(n):
        a = titles[i]
        na, tok_a = normalize(a), token_set(a)
        candidates = set()
        for tok in tok_a:
            candidates.update(token_to_indices.get(tok, set()))
        candidates.discard(i)
        for j in candidates:
            if i > j:
                i2, j2 = j, i
            else:
                i2, j2 = i, j
            if (i2, j2) in seen_pair:
                continue
            seen_pair.add((i2, j2))
            b = titles[j]
            nb, tok_b = normalize(b), token_set(b)
            if na == nb:
                continue
            jacc = jaccard(tok_a, tok_b)
            if jacc < 0.70:
                continue
            lev_ratio = levenshtein_ratio(na, nb)
            if jacc >= 0.85 or lev_ratio >= 0.90:
                pairs.append((a, b, jacc, lev_ratio))

    pairs.sort(key=lambda x: -(x[2] + x[3]))

    if pairs:
        print("=== HIGH-SIMILARITY PAIRS (possible same game, different form) ===")
        print("(Jaccard = token overlap, Lev = string similarity)\n")
        for a, b, jacc, lev in pairs[:80]:  # cap output
            print(f"  Jaccard={jacc:.2f} Lev={lev:.2f}")
            print(f"    '{a}'")
            print(f"    '{b}'")
            print()
        if len(pairs) > 80:
            print(f"  ... and {len(pairs) - 80} more pairs.\n")
        print(f"Total high-similarity pairs: {len(pairs)}\n")
    else:
        print("No high-similarity pairs found.\n")

    # Summary
    print("=== SUMMARY ===")
    print(f"Exact duplicates: {len(exact_dupes)}")
    print(f"Normalized duplicate groups: {len(norm_dupes)}")
    print(f"High-similarity pairs (Jacc>=0.85 or Lev>=0.90): {len(pairs)}")


if __name__ == "__main__":
    main()
