from flask import Flask, request, jsonify
import datetime
import hashlib
import json
import os
import time

app = Flask(__name__)
PORT = 3156
DATA_FILE = "leaderboard.json"


def load_leaderboard() -> dict:
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r") as f:
            return json.load(f)
    return {}


def save_leaderboard():
    with open(DATA_FILE, "w") as f:
        json.dump(leaderboard, f, indent=2)


leaderboard: dict[str, list[dict]] = load_leaderboard()


def seed_for_date(date_str: str) -> int:
    # print("seed_for_date")
    h = hashlib.sha256(date_str.encode()).digest()
    return int.from_bytes(h[:8], "big") & 0x7FFFFFFFFFFFFFFF


@app.route("/")
def status():
    return "OK"


@app.route("/daily-seed")
def get_seed():
    # print("get_seed")
    date_str = request.args.get("date")
    if not date_str:
        date_str = datetime.date.today().isoformat()
    return jsonify({"date": date_str, "seed": seed_for_date(date_str)})


@app.route("/leaderboard")
def get_leaderboard():
    # print("get_leaderboard")
    date_str = request.args.get("date")
    limit = int(request.args.get("limit", 10))
    if not date_str:
        date_str = datetime.date.today().isoformat()

    entries = leaderboard.get(date_str, [])
    sorted_entries = sorted(entries, key=lambda e: e["score"], reverse=True)
    return jsonify({"entries": sorted_entries[:limit]})


@app.route("/leaderboard/submit", methods=["POST"])
def submit_score():
    # print("submit_score")
    data = request.get_json(force=True)

    date_str = data.get("date")
    score = data.get("score")
    player_name = data.get("playerName", "Player")

    if not date_str or score is None:
        return jsonify({"error": "Missing required fields"}), 400

    if date_str not in leaderboard:
        leaderboard[date_str] = []

    entries = leaderboard[date_str]
    existing = next((e for e in entries if e["playerName"] == player_name), None)
    if existing:
        if score > existing["score"]:
            existing["score"] = score
            existing["submittedAt"] = int(time.time() * 1000)
            save_leaderboard()
    else:
        entries.append({
            "playerName": player_name,
            "score": score,
            "submittedAt": int(time.time() * 1000),
        })
        save_leaderboard()

    return jsonify({"ok": True}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, debug=True)
