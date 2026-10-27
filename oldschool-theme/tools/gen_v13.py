"""v1.3.0 "closer to the classic look" art pass. ORIGINAL procedural pixel art only (no game assets).
Overrides some v1.0-1.2 pieces under the SAME file names (backwards compatible) and adds new ones.
Run standalone from the kit root (python3 tools/gen_v13.py) or via gen_art.py (runs last)."""
import os, math, random
from PIL import Image, ImageDraw
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(ROOT, 'src/main/res/drawable-nodpi/')
OUT9 = os.path.join(ROOT, 'src/main/res/drawable-xxhdpi/')
S = 3

def clamp(v): return max(0, min(255, int(v)))
def shade(c, k): return tuple(clamp(v + k) for v in c)
A = lambda c: tuple(c) + (255,)

def nine(img, name, stretch, pad):
    big = img.resize((img.width * S, img.height * S), Image.NEAREST)
    out = Image.new('RGBA', (big.width + 2, big.height + 2), (0, 0, 0, 0)); out.paste(big, (1, 1))
    px = out.load(); K = (0, 0, 0, 255)
    sx0, sx1, sy0, sy1 = [v * S for v in stretch]; px0, px1, py0, py1 = [v * S for v in pad]
    for x in range(sx0, sx1): px[x + 1, 0] = K
    for y in range(sy0, sy1): px[0, y + 1] = K
    for x in range(px0, px1): px[x + 1, out.height - 1] = K
    for y in range(py0, py1): px[out.width - 1, y + 1] = K
    out.save(OUT9 + name + '.9.png', optimize=True)

def mottled(w, h, base, var, blobs, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (w, h), base); px = img.load()
    field = [[0.0] * w for _ in range(h)]
    for _ in range(blobs):
        cx, cy, r, k = rnd.randrange(w), rnd.randrange(h), rnd.randint(3, 12), rnd.uniform(-1, 1)
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                d = (x * x + y * y) / (r * r)
                if d <= 1: field[(cy + y) % h][(cx + x) % w] += k * (1 - d)
    for y in range(h):
        for x in range(w):
            n = field[y][x] * var + rnd.uniform(-var * 0.35, var * 0.35)
            px[x, y] = tuple(clamp(c + n) for c in base)
    return img

# ---- palette (original values, tuned to the 2007-era feel)
BLACK = (0, 0, 0)
FACE = (62, 53, 41)        # #3E3529 interface brown
FACE_MID = (74, 64, 50)
HI = (118, 106, 86)        # bevel highlight (top/left)
HI2 = (92, 82, 66)
LO = (28, 23, 17)          # bevel shadow (bottom/right)
GROOVE = (40, 34, 26)

# ---- textures: classic brown panel + lighter chatbox parchment
mottled(64, 64, FACE, 7, 40, 11).save(OUT + 'os_panel_tile.png', optimize=True)
mottled(128, 64, (206, 191, 152), 6, 50, 31).save(OUT + 'os_chat_tile.png', optimize=True)

# ---- rivets: 5x5 domed metal stud with a dark ring and a bright top-left glint
def rivet(img, cx, cy):
    ring = (12, 9, 6)
    for (x, y) in [(cx-1, cy-2), (cx, cy-2), (cx+1, cy-2), (cx-2, cy-1), (cx+2, cy-1), (cx-2, cy), (cx+2, cy),
                   (cx-2, cy+1), (cx+2, cy+1), (cx-1, cy+2), (cx, cy+2), (cx+1, cy+2)]:
        img.putpixel((x, y), A(ring))
    dome = {(-1, -1): (236, 226, 200), (0, -1): (178, 166, 140), (1, -1): (140, 128, 106),
            (-1, 0): (178, 166, 140), (0, 0): (140, 128, 106), (1, 0): (98, 88, 72),
            (-1, 1): (120, 110, 92), (0, 1): (98, 88, 72), (1, 1): (64, 56, 44)}
    for (dx, dy), c in dome.items(): img.putpixel((cx + dx, cy + dy), A(c))
    img.putpixel((cx + 2, cy + 2), A((50, 42, 32)))   # cast shadow

# ---- carved frame: black | highlight | face | groove | face | inverted bevel | black, studs in the corners
def carved(size=24, band=7, rivets=True, centre=None):
    N = size; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); px = img.load()
    for y in range(N):
        for x in range(N):
            e = min(x, y, N-1-x, N-1-y)
            if e >= band:
                if centre: px[x, y] = centre
                continue
            tl = (x == e or y == e) and not (x == N-1-e or y == N-1-e)   # on the top/left side of ring e
            if e == 0 or e == band - 1: c = BLACK
            elif e == 1: c = HI if tl else LO
            elif e == band - 2: c = LO if tl else HI2          # recessed inner lip
            elif band >= 7 and e == band // 2: c = GROOVE      # engraved groove in the middle of the band
            else: c = FACE_MID if tl else FACE
            px[x, y] = A(c)
    if rivets:
        m = band // 2
        for (cx, cy) in ((m, m), (N-1-m, m), (m, N-1-m), (N-1-m, N-1-m)): rivet(img, cx, cy)
    return img

nine(carved(), 'os_frame_carved', (10, 14, 10, 14), (7, 17, 7, 17))
nine(carved(centre=A(FACE)), 'os_frame_carved_filled', (10, 14, 10, 14), (7, 17, 7, 17))
nine(carved(size=16, band=5, rivets=False), 'os_frame_thin', (6, 10, 6, 10), (5, 11, 5, 11))
r = Image.new('RGBA', (5, 5), (0, 0, 0, 0)); rivet(r, 2, 2); r.resize((15, 15), Image.NEAREST).save(OUT9 + 'os_rivet.png', optimize=True)

# ---- simple stone bevel (frames / buttons): 1px black outline, 1px light TL, 1px dark BR
def bevel(w, h, face, light, dark, pressed=False, inset=None, chamfer=1, focus=None):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    hi, lo = (dark, light) if pressed else (light, dark)
    d.rectangle([0, 0, w-1, h-1], fill=A(BLACK))
    d.rectangle([1, 1, w-2, h-2], fill=A(lo))
    d.rectangle([1, 1, w-3, h-3], fill=A(hi))
    if inset is None: d.rectangle([2, 2, w-3, h-3], fill=A(face))
    else: d.rectangle([2, 2, w-3, h-3], fill=inset)
    if focus: d.rectangle([2, 2, w-3, h-3], outline=A(focus))
    for k in range(chamfer):  # cut corners
        for (x, y) in ((k, 0), (w-1-k, 0), (k, h-1), (w-1-k, h-1), (0, k), (w-1, k), (0, h-1-k), (w-1, h-1-k)):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img

nine(bevel(12, 12, None, HI, LO, inset=(0, 0, 0, 0)), 'os_frame', (4, 8, 4, 8), (3, 9, 3, 9))
nine(bevel(12, 12, FACE, HI, LO), 'os_frame_filled', (4, 8, 4, 8), (4, 8, 4, 8))
BTN = (84, 73, 57)
nine(bevel(12, 12, BTN, (140, 126, 100), (30, 25, 18)), 'os_button_up', (4, 8, 4, 8), (3, 9, 3, 9))
nine(bevel(12, 12, (60, 51, 39), (140, 126, 100), (30, 25, 18), pressed=True), 'os_button_down', (4, 8, 4, 8), (3, 9, 3, 9))
nine(bevel(12, 12, BTN, (140, 126, 100), (30, 25, 18), focus=(200, 180, 60)), 'os_button_focused', (4, 8, 4, 8), (3, 9, 3, 9))
nine(bevel(12, 12, (70, 66, 60), (104, 100, 92), (34, 32, 28)), 'os_button_disabled', (4, 8, 4, 8), (3, 9, 3, 9))

# ---- side-panel tabs: chamfered stone squares; selected = deep red with a glowing rim
def side_tab(face, light, dark, glow=None):
    N = 16; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, N-1, N-1], fill=A(BLACK))
    d.rectangle([1, 1, N-2, N-2], fill=A(dark))
    d.rectangle([1, 1, N-3, N-3], fill=A(light))
    d.rectangle([2, 2, N-3, N-3], fill=A(face))
    if glow:
        d.rectangle([2, 2, N-3, N-3], outline=A(glow))
        d.rectangle([3, 3, N-4, N-4], outline=A(shade(glow, -40)))
    for (x, y) in ((0, 0), (1, 0), (0, 1), (N-1, 0), (N-2, 0), (N-1, 1), (0, N-1), (1, N-1), (0, N-2),
                   (N-1, N-1), (N-2, N-1), (N-1, N-2)):
        img.putpixel((x, y), (0, 0, 0, 0))
    for (x, y) in ((1, 1), (N-2, 1), (1, N-2), (N-2, N-2)): img.putpixel((x, y), A(BLACK))
    return img
nine(side_tab(FACE, HI, LO), 'os_side_tab_up', (6, 10, 6, 10), (4, 12, 4, 12))
nine(side_tab((104, 24, 14), (170, 70, 44), (50, 10, 6), glow=(196, 64, 36)), 'os_side_tab_down', (6, 10, 6, 10), (4, 12, 4, 12))

# ---- filter tabs (row above the side panel): 2-step chamfered top; selected red and open at the bottom
def tab(face, light, dark, selected):
    w, h = 12, 12
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    bottom = h - 1 if selected else h - 2
    d.rectangle([0, 3, w-1, h-1], fill=A(BLACK)); d.rectangle([1, 1, w-2, 2], fill=A(BLACK)); d.rectangle([3, 0, w-4, 0], fill=A(BLACK))
    d.rectangle([1, 3, w-2, bottom], fill=A(dark)); d.rectangle([2, 2, w-3, 2], fill=A(light))
    d.rectangle([1, 3, w-3, bottom], fill=A(light)); d.rectangle([3, 1, w-4, 1], fill=A(light))
    d.rectangle([2, 3, w-3, bottom], fill=A(face))
    if not selected: d.line([0, h-1, w-1, h-1], fill=A(BLACK))
    return img
nine(tab((104, 24, 14), (170, 70, 44), (50, 10, 6), True), 'os_tab_down', (4, 8, 4, 8), (5, 7, 4, 9))
nine(tab(FACE, HI, LO, False), 'os_tab_up', (4, 8, 4, 8), (5, 7, 4, 9))

# ---- chat tabs: chamfered stone; selected lighter with a gold underline
def chat_tab(face, light, dark, edge=None):
    W, H = 14, 10; img = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 2, W-1, H-1], fill=A(BLACK)); d.line([1, 1, W-2, 1], fill=A(BLACK)); d.line([2, 0, W-3, 0], fill=A(BLACK))
    d.rectangle([1, 2, W-2, H-2], fill=A(dark)); d.rectangle([1, 2, W-3, H-3], fill=A(light)); d.line([2, 1, W-3, 1], fill=A(light))
    d.rectangle([2, 2, W-3, H-3], fill=A(face))
    if edge: d.line([2, H-3, W-3, H-3], fill=A(edge))
    return img
nine(chat_tab(FACE, HI, LO), 'os_chat_tab_up', (5, 9, 4, 6), (3, 11, 2, 8))
nine(chat_tab((96, 84, 64), (150, 134, 106), (34, 28, 20), edge=(220, 180, 60)), 'os_chat_tab_down', (5, 9, 4, 6), (3, 11, 2, 8))

# ---- orbs: metal ring lit from the top-left, black lips, dark well; coloured "stat" wells with a gloss
def orb(well=None):
    N = 32; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); px = img.load(); c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - c, y - c)
            if r > 15.6: continue
            if r > 14.6: col = BLACK
            elif r > 12.4:
                lit = math.cos(math.atan2(y - c, x - c) + 2.36)       # 1 at top-left, -1 at bottom-right
                col = shade((96, 86, 70), 40 * lit)
                if r > 14.0 and lit > 0.3: col = shade(col, 18)
            elif r > 11.6: col = BLACK
            elif well:
                k = 1 - r / 11.6                                       # brighter in the middle
                col = tuple(clamp(v * (0.55 + 0.6 * k)) for v in well)
                gx, gy = x - (c - 4), y - (c - 4)                      # gloss blob top-left
                if gx * gx + gy * gy < 9: col = shade(col, 50)
            else:
                lit = (x + y) / (2 * N)
                col = shade((22, 18, 13), -6 + 12 * (1 - lit))
            px[x, y] = A(col)
    return img.resize((N * S, N * S), Image.NEAREST)
orb().save(OUT9 + 'os_orb.png', optimize=True)
for nm, col in (('green', (40, 200, 40)), ('red', (210, 30, 20)), ('cyan', (20, 200, 220)), ('yellow', (230, 200, 30))):
    orb(col).save(OUT9 + 'os_orb_' + nm + '.png', optimize=True)

# ---- scrollbar: recessed dark track + bevelled stone thumb with three grip lines
def scroll_thumb():
    W, H = 8, 16; img = bevel(W, H, BTN, (140, 126, 100), (30, 25, 18), chamfer=0)
    d = ImageDraw.Draw(img)
    for yy in (6, 8, 10): d.line([2, yy, W-3, yy], fill=A((40, 33, 24))); d.line([2, yy+1, W-3, yy+1], fill=A((120, 108, 86)))
    return img
nine(scroll_thumb(), 'os_scrollbar_thumb', (3, 5, 3, 5), (0, 8, 0, 16))
def scroll_track():
    W, H = 8, 8; img = Image.new('RGBA', (W, H), A((22, 18, 13))); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W-1, H-1], outline=A(BLACK)); d.line([1, 1, 1, H-2], fill=A((12, 9, 6))); d.line([W-2, 1, W-2, H-2], fill=A((52, 44, 34)))
    return img
nine(scroll_track(), 'os_scrollbar_track', (3, 5, 3, 5), (0, 8, 0, 8))

# ---- context-menu ("Choose Option") 9-patch: classic flat #5D5447 body, black outline inside a 1px border
def menu():
    W, H = 12, 12; img = Image.new('RGBA', (W, H), A((93, 84, 71))); d = ImageDraw.Draw(img)
    d.rectangle([1, 1, W-2, H-2], outline=A(BLACK))
    return img
nine(menu(), 'os_menu_panel', (4, 8, 4, 8), (2, 10, 2, 10))

# ---- icons: re-shaded side-tab icons (2-tone + highlight) and new ones (pack, star, bell, gear)
PAL = {'.': None, 'k': (0, 0, 0), 'w': (255, 255, 255), 'y': (255, 255, 0), 'Y': (196, 160, 20), 'o': (255, 152, 31),
       'b': (122, 84, 40), 'B': (84, 56, 24), 'n': (160, 116, 60), 'p': (226, 208, 160), 'P': (176, 152, 104),
       'q': (246, 234, 196), 'g': (150, 150, 150), 'G': (96, 96, 96), 'h': (200, 200, 200), 'r': (190, 30, 20),
       'R': (120, 14, 10), 'c': (0, 220, 220), 'l': (200, 200, 210), 'L': (130, 130, 140), 'e': (40, 190, 40),
       'E': (16, 110, 16), 'u': (60, 90, 200)}
ICONS = {
 'os_ic_stats': [  # three shaded skill bars on a plinth
  "................", "............kk..", "...........kyYk.", "...........kyYk.", "......kk...kyYk.", ".....keEk..kyYk.",
  ".....keEk..kyYk.", "..kk.keEk..kyYk.", ".krRkkeEk..kyYk.", ".krRkkeEk..kyYk.", ".krRkkeEk..kyYk.", ".krRkkeEk..kyYk.",
  "kkkkkkkkkkkkkkkk", "khhhhhhhhhhhhhGk", "kGGGGGGGGGGGGGGk", "kkkkkkkkkkkkkkkk"],
 'os_ic_scroll': [  # rolled scroll with shaded rolls
  "................", "..kkkkkkkkkkkk..", ".kPqqqqqqqqqqPk.", ".kBkkkkkkkkkkBk.", "..kppppppppppk..", "..kpkkkkkkkpPk..",
  "..kppppppppppk..", "..kpkkkkkkpppk..", "..kppppppppPpk..", "..kpkkkkkkkkpk..", "..kppppppppppk..", "..kpkkkkkPpppk..",
  ".kPkkkkkkkkkkPk.", ".kBqqqqqqqqqqBk.", "..kkkkkkkkkkkk..", "................"],
 'os_ic_quest': [  # map with a dotted trail and red X
  "................", "..kkkkkkkkkkkk..", ".kqppPpppppPppk.", ".kpPppppppppppk.", ".kpbpbppppPpppk.", ".kppppbpppppPpk.",
  ".kpPpppbpprkrpk.", ".kppPppppbkrkpk.", ".kppppppppbkrpk.", ".kPpppppppppk...", ".kpppppppPPppk..", ".kpppPppppppppk.",
  ".kPPPPPPPPPPPPk.", "..kkkkkkkkkkkk..", "................", "................"],
 'os_ic_wrench': [  # shaded spanner
  "................", "..........kkk...", ".........khlk....", "........khlk..k..", "........khlkkkLk.", "........khhllllk.",
  ".......khllllLk..", "......khlLLLkk...", ".....khlLk.......", "....khlLk........", "...khlLk.........", "..khlLk..........",
  ".khlLk...........", ".kLLk............", "..kk.............", "................"],
 'os_ic_pack': [  # backpack
  "................", ".....kkkkkk.....", "....kbk..kbk....", "...kkkkkkkkkk...", "..knnnnnnnnnnk..", "..knbbbbbbbbBk..",
  "..knbkkkkkkbBk..", "..knbkYYYYkbBk..", "..knbkkkkkkbBk..", "..knbbbbbbbbBk..", "..knbbbbbbbbBk..", "..knbbbbbbbbBk..",
  "..kBBBBBBBBBBk..", "...kkkkkkkkkk...", "................", "................"],
 'os_ic_star': [
  "................", ".......kk.......", "......kyyk......", "......kyyk......", ".....kyyyYk.....", "kkkkkkyyyYkkkkkk",
  ".kyyyyyyyyyyyYk.", "..kyyyyyyyyyYk..", "...kyyyyyyyYk...", "...kyyyyyyyYk...", "..kyyyYkkyyyYk..", "..kyyYk..kyyYk..",
  ".kyYkk....kkYYk.", ".kkk........kkk.", "................", "................"],
 'os_ic_bell': [
  "................", ".......kk.......", "......kyYk......", ".....kyyyYk.....", "....kyyyyyYk....", "....kyyyyyYk....",
  "....kyyyyyYk....", "...kyyyyyyyYk...", "...kyyyyyyyYk...", "..kyyyyyyyyyYk..", "..kYYYYYYYYYYk..", "..kkkkkkkkkkkk..",
  "......kYYk......", ".......kk.......", "................", "................"],
 'os_ic_gear': [
  "................", "......kkkk......", "......khLk......", "..kk.khhhLk.kk..", "..khkhhhhhLkLk..", "...khhhkkhhLk...",
  ".kkhhhk..khLkk..", ".khhhk....khLLk.", ".kLhhk....kLLLk.", ".kkhLLk..kLLkk..", "...khLLkkLLLk...", "..khkLLLLLLkLk..",
  "..kk.kLLLLk.kk..", "......kLLk......", "......kkkk......", "................"],
}
for name, rows in ICONS.items():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); p = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            col = PAL.get(ch)
            if col: p[x, y] = A(col)
    img.resize((48, 48), Image.NEAREST).save(OUT + name + '.png', optimize=True)
print('v13 art ok')
