#!/usr/bin/env python3
"""
Standalone script to fetch RetroAchievements console IDs and all games per console.
Uses curl for HTTP (avoids Python urllib hangs on some networks). Saves data under
data/retroachievements/ for later use (e.g. selecting games by console ID).

Usage:
  python3 scripts/fetch_retroachievements_data.py

Requires: curl on PATH.

Output:
  data/retroachievements/consoles.json     - list of { "ID", "Name", ... }
  data/retroachievements/games/<id>.json   - per console: { "consoleId", "consoleName", "games": [...] }
"""

import json
import os
import subprocess
import time

def _local_property(name):
    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "local.properties")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith(name + "="):
                return line.split("=", 1)[1].strip()
    return None


API_KEY = os.environ.get("RA_API_KEY") or _local_property("retroAchievementsApiKey")
if not API_KEY:
    raise SystemExit("Set RA_API_KEY or add retroAchievementsApiKey to local.properties")
BASE_URL = "https://retroachievements.org/API"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "data", "retroachievements")
GAMES_DIR = os.path.join(DATA_DIR, "games")

# Timeout per request (seconds). Some game-list responses are large.
CURL_TIMEOUT = 120


def get(url: str) -> bytes:
    result = subprocess.run(
        [
            "curl",
            "-s",
            "-S",
            "-A", "TwinLore/1.0 (RetroAchievements data fetch)",
            "--max-time", str(CURL_TIMEOUT),
            "--", url,
        ],
        capture_output=True,
        timeout=CURL_TIMEOUT + 10,
    )
    if result.returncode != 0:
        err = result.stderr.decode(errors="replace") if result.stderr else ""
        raise RuntimeError(f"curl failed (exit {result.returncode}): {err}")
    return result.stdout


def fetch_console_ids() -> list:
    url = f"{BASE_URL}/API_GetConsoleIDs.php?y={API_KEY}"
    data = get(url)
    return json.loads(data.decode("utf-8"))


def fetch_game_list(console_id: int) -> list:
    # i=console_id, h=1 (include hashes), f=1 (only games with achievements)
    url = f"{BASE_URL}/API_GetGameList.php?y={API_KEY}&i={console_id}&h=1&f=1"
    data = get(url)
    return json.loads(data.decode("utf-8"))


def main() -> None:
    os.makedirs(GAMES_DIR, exist_ok=True)

    print("Fetching console IDs...", flush=True)
    try:
        consoles = fetch_console_ids()
    except RuntimeError as e:
        print(f"Error: {e}", flush=True)
        return
    except json.JSONDecodeError as e:
        print(f"Invalid JSON from API: {e}", flush=True)
        return
    if not consoles:
        print("No consoles returned. Check API key.", flush=True)
        return

    consoles_path = os.path.join(DATA_DIR, "consoles.json")
    with open(consoles_path, "w", encoding="utf-8") as f:
        json.dump(consoles, f, indent=2, ensure_ascii=False)
    print(f"Saved {len(consoles)} consoles to {consoles_path}", flush=True)

    for i, console in enumerate(consoles):
        cid = console.get("ID")
        name = console.get("Name") or f"Console_{cid}"
        if cid is None:
            continue
        print(f"[{i + 1}/{len(consoles)}] Fetching games for console {cid} ({name})...", flush=True)
        try:
            games = fetch_game_list(int(cid))
        except RuntimeError as e:
            print(f"  Error for console {cid}: {e}", flush=True)
            continue
        except json.JSONDecodeError as e:
            print(f"  JSON error for console {cid}: {e}", flush=True)
            continue

        payload = {
            "consoleId": cid,
            "consoleName": name,
            "games": games,
        }
        out_path = os.path.join(GAMES_DIR, f"{cid}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, indent=2, ensure_ascii=False)
        print(f"  Saved {len(games)} games to {out_path}", flush=True)

        # Be nice to the API: short delay between console requests
        if i < len(consoles) - 1:
            time.sleep(0.5)

    print("Done.", flush=True)


if __name__ == "__main__":
    main()