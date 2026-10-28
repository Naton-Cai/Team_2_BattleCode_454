#!/usr/bin/env python3
"""Landscape slides for baby-rat greedy, visible BFS, and DistBug pathing."""

from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import letter, landscape
from reportlab.pdfgen import canvas

PAGE = landscape(letter)
W, H = PAGE

NAVY = HexColor("#1e3a5f")
TEAL = HexColor("#0f766e")
GOLD = HexColor("#b45309")
SLATE = HexColor("#334155")
MUTED = HexColor("#64748b")
LINE = HexColor("#cbd5e1")
SOFT = HexColor("#f1f5f9")
WALL = HexColor("#475569")
UNSEEN = HexColor("#e2e8f0")
OPEN = HexColor("#ffffff")
OCC = HexColor("#fecaca")
RAT = HexColor("#1d4ed8")
GOAL = HexColor("#ca8a04")
ARROW = HexColor("#0f766e")

FILLS = {
    ".": OPEN,
    "R": HexColor("#dbeafe"),
    "G": HexColor("#fef3c7"),
    "#": WALL,
    "?": UNSEEN,
    "O": OCC,
    "RG": HexColor("#e0e7ff"),
    " ": HexColor("#f8fafc"),
}
LABEL_COLOR = {
    ".": MUTED,
    "R": RAT,
    "G": GOLD,
    "#": white,
    "?": MUTED,
    "O": HexColor("#991b1b"),
    "RG": NAVY,
    " ": MUTED,
}


def new_page(c, title, subtitle=None):
    c.showPage()
    c.setFillColor(white)
    c.rect(0, 0, W, H, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.rect(0, H - 18, W, 18, fill=1, stroke=0)
    c.setFillColor(TEAL)
    c.rect(0, 0, W, 8, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.setFont("Helvetica-Bold", 22)
    c.drawString(36, H - 52, title)
    if subtitle:
        c.setFillColor(MUTED)
        c.setFont("Helvetica", 11)
        c.drawString(36, H - 70, subtitle)
    c.setStrokeColor(LINE)
    c.setLineWidth(1)
    c.line(36, H - 80, W - 36, H - 80)


def footer(c, n, total):
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 9)
    c.drawString(36, 18, "Team 2  ·  Battlecode 2026  ·  Baby rat pathing")
    c.drawRightString(W - 36, 18, f"{n} / {total}")


def wrapped(c, text, x, y, max_width, font="Helvetica", size=11, leading=15, color=SLATE):
    c.setFont(font, size)
    c.setFillColor(color)
    words = text.split()
    line = ""
    for word in words:
        trial = (line + " " + word).strip()
        if c.stringWidth(trial, font, size) <= max_width:
            line = trial
        else:
            c.drawString(x, y, line)
            y -= leading
            line = word
    if line:
        c.drawString(x, y, line)
        y -= leading
    return y


def bullets(c, items, x, y, max_width=720, size=12, leading=18):
    for item in items:
        c.setFillColor(TEAL)
        c.circle(x + 4, y + 3, 3, fill=1, stroke=0)
        y = wrapped(c, item, x + 16, y, max_width - 16, size=size, leading=leading) - 4
    return y


def step_list(c, steps, x, y, width=360):
    for i, text in enumerate(steps, 1):
        c.setFillColor(TEAL)
        c.circle(x + 10, y + 4, 10, fill=1, stroke=0)
        c.setFillColor(white)
        c.setFont("Helvetica-Bold", 10)
        c.drawCentredString(x + 10, y, str(i))
        y = wrapped(c, text, x + 28, y, width - 28, size=11, leading=14) - 8
    return y


def cell_style(token):
    key = token if token in FILLS else "."
    return FILLS[key], LABEL_COLOR.get(key, SLATE), token


def draw_grid(c, origin_x, origin_y, rows, cell=30, caption=None, highlights=None):
    """rows[0] is north. highlights: {(r, col): color} in row/col from north-west."""
    highlights = highlights or {}
    nrows = len(rows)
    ncols = max(len(r) for r in rows)
    height = nrows * cell
    top = origin_y + height
    for r, row in enumerate(rows):
        for col, token in enumerate(row):
            x = origin_x + col * cell
            y = top - (r + 1) * cell
            fill, ink, label = cell_style(token)
            c.setFillColor(fill)
            c.setStrokeColor(LINE)
            c.setLineWidth(1.2)
            c.roundRect(x, y, cell, cell, 3, fill=1, stroke=1)
            if (r, col) in highlights:
                c.setStrokeColor(highlights[(r, col)])
                c.setLineWidth(2.4)
                c.roundRect(x + 1.2, y + 1.2, cell - 2.4, cell - 2.4, 3, fill=0, stroke=1)
            c.setFillColor(ink)
            font = "Helvetica-Bold" if token in ("R", "G", "RG", "#") else "Helvetica"
            c.setFont(font, 11 if cell >= 28 else 9)
            c.drawCentredString(x + cell / 2, y + cell / 2 - 4, label)
    if caption:
        c.setFillColor(MUTED)
        c.setFont("Helvetica-Oblique", 9)
        c.drawString(origin_x, origin_y - 14, caption)
    return origin_x + ncols * cell, origin_y + height


def arrow(c, x1, y1, x2, y2, color=ARROW):
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(2)
    c.line(x1, y1, x2, y2)
    ang = __import__("math").atan2(y2 - y1, x2 - x1)
    import math

    size = 7
    p1 = (x2, y2)
    p2 = (x2 - size * math.cos(ang - 0.4), y2 - size * math.sin(ang - 0.4))
    p3 = (x2 - size * math.cos(ang + 0.4), y2 - size * math.sin(ang + 0.4))
    p = c.beginPath()
    p.moveTo(*p1)
    p.lineTo(*p2)
    p.lineTo(*p3)
    p.close()
    c.drawPath(p, fill=1, stroke=0)


def card(c, x, y, w, h, title=None):
    c.setFillColor(SOFT)
    c.setStrokeColor(LINE)
    c.setLineWidth(1)
    c.roundRect(x, y, w, h, 8, fill=1, stroke=1)
    if title:
        c.setFillColor(NAVY)
        c.setFont("Helvetica-Bold", 12)
        c.drawString(x + 12, y + h - 20, title)


def build(path):
    c = canvas.Canvas(path, pagesize=PAGE)
    total = 10

    # 1 Title
    c.setFillColor(white)
    c.rect(0, 0, W, H, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.rect(0, 0, 18, H, fill=1, stroke=0)
    c.setFillColor(TEAL)
    c.rect(18, 0, 8, H, fill=1, stroke=0)
    c.setFillColor(NAVY)
    c.setFont("Helvetica-Bold", 34)
    c.drawString(56, H - 160, "Baby rat pathing")
    c.setFillColor(TEAL)
    c.setFont("Helvetica", 16)
    c.drawString(56, H - 190, "Greedy  ·  Visible BFS  ·  DistBug")
    c.setFillColor(SLATE)
    c.setFont("Helvetica", 12)
    wrapped(
        c,
        "How a 1×1 baby rat takes one step toward cheese or the published king. "
        "North is up. The rat sees a 90-degree cone with radius squared 20.",
        56,
        H - 230,
        680,
        size=12,
        leading=18,
    )
    card(c, 56, 90, 700, 210, "On each movement turn")
    y = 250
    for left, right in (
        ("Visible BFS", "Empty-handed, walking toward a pile the rat can already see."),
        ("DistBug", "Carrying enough cheese, walking toward the published king."),
        ("Greedy", "Fallback inside both helpers, and the 8-neighbor probe they share."),
        ("Wander", "No published king, or no pile to chase."),
    ):
        c.setFillColor(TEAL)
        c.setFont("Helvetica-Bold", 12)
        c.drawString(76, y, left)
        c.setFillColor(SLATE)
        c.setFont("Helvetica", 12)
        c.drawString(190, y, right)
        y -= 36
    footer(c, 1, total)

    # 2 Legend + when
    new_page(c, "Map key and where each helper runs", "Diagrams match the team tests: north up, +x east.")
    legend = [
        ("R", "Rat"),
        ("G", "Goal"),
        ("#", "Wall or dirt"),
        ("?", "Not seen this turn"),
        ("O", "Other robot"),
        (".", "Open tile"),
    ]
    x = 50
    for token, name in legend:
        draw_grid(c, x, H - 155, [[token]], cell=32)
        c.setFillColor(SLATE)
        c.setFont("Helvetica", 10)
        c.drawCentredString(x + 16, H - 178, name)
        x += 120
    c.setFillColor(MUTED)
    c.setFont("Helvetica-Oblique", 9)
    c.drawString(
        50,
        H - 198,
        "? is a tile the rat has not seen. O is another robot on a passable tile, not a wall.",
    )
    card(c, 36, 70, 350, 280, "Cheese chase  →  Visible BFS")
    bullets(
        c,
        [
            "The pile is chosen from cheese the rat currently sees.",
            "BFS can walk around a wall in that cone.",
            "Tiles outside the cone are not on the path.",
            "If there is no seen path, it uses greedy.",
        ],
        52,
        310,
        310,
    )
    card(c, 406, 70, 350, 280, "King return  →  DistBug")
    bullets(
        c,
        [
            "The king publishes its center. It is often far outside the 90-degree cone.",
            "BFS cannot invent a path through unseen tiles.",
            "DistBug hugs a wall it has seen and keeps hugging next turn.",
            "Until it sees a wall, it walks greedy.",
        ],
        422,
        310,
        310,
    )
    footer(c, 2, total)

    # 3 Greedy steps
    new_page(c, "Greedy  —  one step", "Used as the fallback. Does not look at map info, only which neighbors are legal.")
    step_list(
        c,
        [
            "If the rat is already on the goal, do nothing.",
            "Take the compass direction toward the goal.",
            "Build an 8-step probe: straight, then left/right pairs, then the opposite.",
            "Take the first legal neighbor.",
            "If every neighbor is illegal, turn to face the goal (if turning is allowed).",
        ],
        36,
        H - 110,
        360,
    )
    c.setFillColor(NAVY)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(430, H - 110, "Goal east: probe order")
    ox, oy = 460, 230
    cell = 54
    labels = [
        [("6", "NW"), ("4", "N"), ("2", "NE")],
        [("8", "W"), ("R", "rat"), ("1", "E")],
        [("7", "SW"), ("5", "S"), ("3", "SE")],
    ]
    for r, row in enumerate(labels):
        for col, (num, name) in enumerate(row):
            x = ox + col * cell
            y = oy + (2 - r) * cell
            fill = HexColor("#dbeafe") if num == "R" else (HexColor("#ccfbf1") if num == "1" else OPEN)
            c.setFillColor(fill)
            c.setStrokeColor(LINE)
            c.roundRect(x, y, cell - 4, cell - 4, 4, fill=1, stroke=1)
            c.setFillColor(TEAL if num != "R" else RAT)
            c.setFont("Helvetica-Bold", 16)
            c.drawCentredString(x + (cell - 4) / 2, y + 22, num if num != "R" else "R")
            c.setFillColor(MUTED)
            c.setFont("Helvetica", 8)
            c.drawCentredString(x + (cell - 4) / 2, y + 8, name)
    c.setFillColor(MUTED)
    c.setFont("Helvetica-Oblique", 9)
    c.drawString(ox, oy - 18, "First legal number wins. Goal is east, so 1 is straight at G.")
    footer(c, 3, total)

    # 4 Greedy wall problem
    new_page(
        c,
        "Greedy along a wall it has not planned around",
        "Goal is north. North, northwest, and northeast are blocked. West and east are open.",
    )
    draw_grid(
        c,
        60,
        220,
        [
            [" ", "G", " "],
            [" ", ".", " "],
            ["#", "#", "#"],
            [".", "R", "."],
        ],
        cell=42,
        caption="Wall north of the rat. Greedy tries N, then NW, then NE, then west before east.",
        highlights={(3, 0): ARROW},
    )
    c.setFillColor(SLATE)
    c.setFont("Helvetica", 12)
    y = wrapped(c, "Step: west, not east.", 60, 190, 320, size=12)
    card(c, 400, 120, 360, 340, "Why west?")
    bullets(
        c,
        [
            "Straight at the goal is north — blocked.",
            "Left of north is northwest — blocked.",
            "Right of north is northeast — blocked.",
            "Next left is west — open. That is the move.",
            "East is also open, but east is later in the probe.",
            "If the goal is only just over the wall, the next turn can walk back east. That is the ping-pong.",
        ],
        416,
        410,
        320,
    )
    footer(c, 4, total)

    # 5 BFS steps
    new_page(c, "Visible BFS  —  one step", "Only tiles the rat saw this turn are walkable. Unknown tiles are not a path.")
    step_list(
        c,
        [
            "If the rat is already on the goal, do nothing.",
            "Collect this turn's passable seen tiles, plus the tile the rat is standing on.",
            "If almost nothing is seen, use greedy.",
            "Target is the goal if that tile was seen and passable. Otherwise the seen passable tile closest to the goal.",
            "Breadth-first search on those seen tiles only (8 neighbors, if both ends were seen).",
            "Walk the first step of that path, if that step is legal.",
            "If there is no seen path, use greedy.",
        ],
        36,
        H - 110,
        720,
    )
    card(c, 36, 70, 720, 160, "Seen tiles only")
    draw_grid(
        c,
        56,
        88,
        [
            ["?", "?", "?"],
            ["R", "#", "G"],
            [".", ".", "."],
        ],
        cell=28,
        caption="BFS may walk the southern open tiles. The northern ? tiles are not in the search.",
        highlights={(2, 0): ARROW, (2, 1): ARROW, (2, 2): ARROW},
    )
    footer(c, 5, total)

    # 6 BFS vs greedy
    new_page(c, "BFS walks a corridor it can see", "Greedy will take an unseen shortcut if that neighbor looks legal.")
    card(c, 36, 80, 350, 400, "Greedy")
    draw_grid(
        c,
        70,
        200,
        [
            ["?", "?", "?"],
            ["R", "#", "G"],
            [".", ".", "."],
        ],
        cell=40,
        caption="East is the wall. Next greedy probe is northeast — the unseen shortcut.",
        highlights={(0, 1): GOLD},
    )
    c.setFillColor(GOLD)
    c.setFont("Helvetica-Bold", 11)
    c.drawString(70, 170, "Likely first step: northeast into ?")
    card(c, 406, 80, 350, 400, "Visible BFS")
    draw_grid(
        c,
        440,
        200,
        [
            ["?", "?", "?"],
            ["R", "#", "G"],
            [".", ".", "."],
        ],
        cell=40,
        caption="Northern tiles were never seen, so they are not on the BFS path.",
        highlights={(2, 1): ARROW},
    )
    c.setFillColor(TEAL)
    c.setFont("Helvetica-Bold", 11)
    c.drawString(440, 170, "First step: southeast into the seen corridor")
    footer(c, 6, total)

    # 7 DistBug steps
    new_page(c, "DistBug  —  one step", "Remembers a wall across turns. Leaves only when closer than the first hit.")
    step_list(
        c,
        [
            "If already on the goal, stop following and do nothing.",
            "If the goal changed, drop the old wall-follow and start over.",
            "If currently following: if the way toward the goal is open and the rat is closer than when it first hit the wall, walk that way and stop following.",
            "If still following, hug the wall (sweep toward the chosen hand; prefer a step that matches current facing).",
            "If not following: if the tile toward the goal is blocked, and the rat has seen a wall, dirt, or map edge there, start following and hug.",
            "Otherwise walk greedy. A blocked tile the rat has not seen does not start a follow. Another robot on a passable tile is not a wall.",
        ],
        36,
        H - 110,
        720,
    )
    footer(c, 7, total)

    # 8 DistBug seen vs unseen
    new_page(
        c,
        "DistBug only hugs a wall it has seen",
        "Left-hand follow if left is open or right is not. Facing into the wall makes the first open left-hand step northeast.",
    )
    card(c, 36, 90, 350, 390, "Blocked, but not seen")
    draw_grid(
        c,
        80,
        230,
        [
            [".", ".", ".", ".", "."],
            [".", "R", "?", ".", "G"],
            [".", ".", ".", ".", "."],
        ],
        cell=36,
        caption="East is blocked. DistBug does not start follow. Greedy may step northeast.",
        highlights={(0, 2): ARROW},
    )
    c.setFillColor(SLATE)
    c.setFont("Helvetica", 11)
    wrapped(c, "No follow next turn.", 80, 200, 280)
    card(c, 406, 90, 350, 390, "Seen wall on the way to the goal")
    draw_grid(
        c,
        450,
        230,
        [
            [".", ".", ".", ".", "."],
            [".", "R", "#", ".", "G"],
            [".", ".", ".", ".", "."],
        ],
        cell=36,
        caption="Rat faces east. DistBug starts follow and steps northeast along the wall.",
        highlights={(0, 2): ARROW},
    )
    c.setFillColor(SLATE)
    c.setFont("Helvetica", 11)
    wrapped(c, "Still following on the next turn.", 450, 200, 280)
    footer(c, 8, total)

    # 9 Leave + occupancy
    new_page(c, "Leaving the wall, and what is not a wall", "DistBug stops only when closer than the recorded hit and the way to the goal is open.")
    card(c, 36, 300, 720, 190, "Leave")
    c.setFillColor(SLATE)
    c.setFont("Helvetica", 11)
    wrapped(
        c,
        "Already following. First hit was farther away. East is open and the rat is closer, so walk toward the goal and stop hugging.",
        56,
        460,
        680,
        size=11,
    )
    draw_grid(
        c,
        56,
        328,
        [[".", "R", ".", ".", "G"]],
        cell=40,
        caption="Closer-than-hit is what stops walking around a U-shaped wall forever.",
        highlights={(0, 2): ARROW},
    )
    card(c, 36, 70, 720, 210, "Another robot is not a wall")
    draw_grid(
        c,
        56,
        100,
        [
            [".", ".", ".", ".", "."],
            [".", "R", "O", ".", "G"],
            [".", ".", ".", ".", "."],
        ],
        cell=32,
        caption="East occupied. DistBug does not hug. Next open greedy step is northeast.",
        highlights={(0, 2): ARROW},
    )
    footer(c, 9, total)

    # 10 Summary
    new_page(c, "What to remember", "All three take one step per turn. Standing on the goal never moves or turns.")
    rows = [
        ("Greedy", "First legal neighbor toward the goal.", "Open ground; fallback."),
        ("Visible BFS", "Shortest path on tiles seen this turn.", "Cheese pile already in the cone."),
        ("DistBug", "Greedy until a seen wall, then hug until closer.", "King often outside the cone."),
    ]
    y = H - 120
    c.setFont("Helvetica-Bold", 11)
    c.setFillColor(MUTED)
    c.drawString(50, y, "Helper")
    c.drawString(180, y, "Idea")
    c.drawString(500, y, "Used for")
    y -= 16
    c.setStrokeColor(LINE)
    c.line(36, y + 8, W - 36, y + 8)
    y -= 20
    for name, idea, used in rows:
        c.setFillColor(TEAL)
        c.setFont("Helvetica-Bold", 13)
        c.drawString(50, y, name)
        c.setFillColor(SLATE)
        c.setFont("Helvetica", 12)
        c.drawString(180, y, idea)
        c.drawString(500, y, used)
        y -= 36
    y -= 10
    bullets(
        c,
        [
            "BFS is not greedy. It searches seen tiles. Greedy only ranks the eight neighbors.",
            "BFS will not walk a tile the rat has not seen, even if that step looks open.",
            "DistBug will not hug a wall it has not seen, and will not hug another rat.",
            "Cheese → Visible BFS. King → DistBug. Both call greedy when they have nothing better.",
        ],
        50,
        y,
        700,
        size=12,
        leading=18,
    )
    footer(c, 10, total)

    c.save()


if __name__ == "__main__":
    out = "/Users/jorgeprado/dev/Team_2_BattleCode_454/docs/baby-rat-pathing.pdf"
    build(out)
    print(out)
