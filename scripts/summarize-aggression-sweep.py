#!/usr/bin/env python3
"""Build the AGGRESSION win matrix from a=*-vs-*.log files. Does not write Config."""

import re
import sys
from pathlib import Path

knob_names = (
    "BACKSTAB_ROUND",
    "BABY_CAT_FLEE_HEALTH",
    "KING_CAT_FLEE_HEALTH",
    "MAX_BABY_RATS",
    "BABY_HUNT_IN_BACKSTAB",
)
pair_re = re.compile(r"^a=(.+)-vs-(.+)\.log$")


def pkg(raw):
    """Match scripts/sweep-aggression.sh aggression_pkg: -1 -> ratplayer_am1."""
    token = norm_a(raw).replace("-", "m").replace(".", "p")
    return f"ratplayer_a{token}"


def teams_for_pair(a1, a2, text):
    named_a, named_b = pkg(a1), pkg(a2)
    if named_a in text and (named_a == named_b or named_b in text):
        return named_a, named_b
    return "ratplayer1", "ratplayer2"


def parse_wins(text, team_a, team_b):
    wins = {team_a: 0, team_b: 0}
    games = 0
    block = re.search(r"== Wins ==\n(.*)", text, re.S)
    if not block:
        return wins, games
    for line in block.group(1).splitlines():
        parts = line.split()
        if len(parts) != 2:
            continue
        name, value = parts
        if name == "games":
            games = int(value)
        elif name in wins:
            wins[name] = int(value)
    return wins, games


def norm_a(raw):
    a = raw.strip()
    if a.endswith("f") or a.endswith("F"):
        a = a[:-1]
    return format(float(a), "g")


def parse_knobs(text, knobs_by_a):
    for m in re.finditer(
        r"effective Config \(aggression=([^)]+)\):(.*?)(?=\n\[|\n== |\n> Task |\Z)",
        text,
        re.S,
    ):
        block = m.group(2)
        found = {}
        for name in knob_names:
            km = re.search(rf"{name}\s+(\S+)", block)
            if km:
                found[name] = km.group(1)
        if found:
            knobs_by_a[norm_a(m.group(1))] = found


def main(argv):
    if len(argv) != 5:
        sys.exit(f"usage: {argv[0]} SWEEP_DIR TEAM_A TEAM_B SUMMARY_PATH")
    sweep_dir, team_a, team_b, summary_path = (
        Path(argv[1]),
        argv[2],
        argv[3],
        Path(argv[4]),
    )
    point_files = sorted(
        sweep_dir.glob("a=*-vs-*.log"),
        key=lambda p: tuple(float(x) for x in pair_re.match(p.name).groups()),
    )

    knobs_by_a = {}
    results = {}
    grid_labels = []
    seen = set()
    for path in point_files:
        m = pair_re.match(path.name)
        a1, a2 = m.group(1), m.group(2)
        for label in (a1, a2):
            if label not in seen:
                seen.add(label)
                grid_labels.append(label)
        text = path.read_text(errors="replace")
        parse_knobs(text, knobs_by_a)
        named_a, named_b = teams_for_pair(a1, a2, text)
        if a1 == a2:
            _, games = parse_wins(text, named_a, named_b)
            half = games // 2 if games else 0
            results[(a1, a2)] = (half, half, games)
        else:
            wins, games = parse_wins(text, named_a, named_b)
            results[(a1, a2)] = (wins[named_a], wins[named_b], games)

    grid_labels.sort(key=float)
    n = len(grid_labels)
    cells = [[None] * n for _ in range(n)]
    for i, a1 in enumerate(grid_labels):
        for j, a2 in enumerate(grid_labels):
            if (a1, a2) in results:
                w1, w2, games = results[(a1, a2)]
                cells[i][j] = (w1, w2, games)
            elif (a2, a1) in results:
                w1, w2, games = results[(a2, a1)]
                cells[i][j] = (w2, w1, games)

    lines = ["== Matrix (row wins-col wins) =="]
    col_w = max(7, *(len(a) for a in grid_labels), len("vs_field")) if grid_labels else 7
    headers = ["a".ljust(col_w)] + [a.rjust(col_w) for a in grid_labels] + [
        "vs_field".rjust(col_w)
    ]
    lines.append(" ".join(headers))
    for i, a1 in enumerate(grid_labels):
        row = [a1.ljust(col_w)]
        field_w = field_g = 0
        for j, _a2 in enumerate(grid_labels):
            cell = cells[i][j]
            if cell is None:
                row.append("-".rjust(col_w))
                continue
            w1, w2, games = cell
            row.append(f"{w1}-{w2}".rjust(col_w))
            if i != j and games:
                field_w += w1
                field_g += games
        if field_g:
            row.append(f"{field_w}/{field_g}".rjust(col_w))
        else:
            row.append("-".rjust(col_w))
        lines.append(" ".join(row))

    lines.append("")
    lines.append("== Knobs ==")
    knob_headers = ("a", "backstab", "baby_flee", "king_flee", "max_babies", "hunt")
    knob_rows = []
    for a in grid_labels:
        knobs = knobs_by_a.get(norm_a(a), {})
        knob_rows.append(
            [
                a,
                knobs.get("BACKSTAB_ROUND", "-"),
                knobs.get("BABY_CAT_FLEE_HEALTH", "-"),
                knobs.get("KING_CAT_FLEE_HEALTH", "-"),
                knobs.get("MAX_BABY_RATS", "-"),
                knobs.get("BABY_HUNT_IN_BACKSTAB", "-"),
            ]
        )
    widths = [len(h) for h in knob_headers]
    for row in knob_rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(cell))
    lines.append(" ".join(h.ljust(widths[i]) for i, h in enumerate(knob_headers)))
    for row in knob_rows:
        lines.append(" ".join(row[i].ljust(widths[i]) for i in range(len(knob_headers))))
    if not point_files:
        lines.append("No pair logs found.")
    lines.append("")
    lines.append("Does not write winners into Config.")
    body = "\n".join(lines) + "\n"
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text(body)
    print()
    print(body, end="")
    print(f"Summary: {summary_path}")


if __name__ == "__main__":
    main(sys.argv)
