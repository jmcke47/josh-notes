"""Generates ORIGINAL pixel art for the "Old School" theme (procedural textures + hand-made 16x16 icons).
No game assets are used. Run from the kit root: python3 tools/gen_art.py"""
import random, math
from PIL import Image, ImageDraw
import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(ROOT, 'src/main/res/drawable-nodpi/')
OUT9 = os.path.join(ROOT, 'src/main/res/drawable-xxhdpi/')
random.seed(1234)

def clamp(v): return max(0, min(255, int(v)))

def mottled(w, h, base, var, blobs, seed):
    rnd = random.Random(seed)
    img = Image.new('RGB', (w, h), base); px = img.load()
    # low-frequency blotches (tileable via wraparound)
    field = [[0.0]*w for _ in range(h)]
    for _ in range(blobs):
        cx, cy, r, k = rnd.randrange(w), rnd.randrange(h), rnd.randint(3, 12), rnd.uniform(-1, 1)
        for y in range(-r, r+1):
            for x in range(-r, r+1):
                d = (x*x+y*y)/(r*r)
                if d <= 1: field[(cy+y) % h][(cx+x) % w] += k*(1-d)
    for y in range(h):
        for x in range(w):
            n = field[y][x]*var + rnd.uniform(-var*0.35, var*0.35)
            px[x, y] = tuple(clamp(c+n) for c in base)
    return img

# Window background: dark stone blocks (tileable).
def stone_blocks():
    W = H = 128
    img = Image.new('RGB', (W, H), (30, 27, 22)); d = ImageDraw.Draw(img)
    rnd = random.Random(7)
    for r in range(4):
        y0, y1 = r*32, r*32+32
        off = 0 if r % 2 == 0 else 32
        for i in range(-1, 3):
            x0 = i*64+off
            b = rnd.randint(60, 74); col = (b, b-5, b-13)
            for dx in (0, -W, W):
                xa = x0+dx+2; xb = x0+dx+62
                d.rectangle([xa, y0+2, xb, y1-2], fill=col)
                d.line([xa, y0+2, xb, y0+2], fill=tuple(c+18 for c in col))
                d.line([xa, y0+2, xa, y1-2], fill=tuple(c+12 for c in col))
                d.line([xa, y1-2, xb, y1-2], fill=tuple(c-26 for c in col))
                d.line([xb, y0+2, xb, y1-2], fill=tuple(c-22 for c in col))
    px = img.load()
    for _ in range(3000):
        x, y = rnd.randrange(W), rnd.randrange(H); k = rnd.randint(-10, 10)
        px[x, y] = tuple(clamp(v+k) for v in px[x, y])
    img.save(OUT+'os_stone_tile.png', optimize=True)

stone_blocks()
# Interface panel texture: mottled grey-brown stone.
mottled(64, 64, (50, 41, 31), 8, 40, 11).save(OUT+'os_panel_tile.png', optimize=True)
# Parchment / scroll texture.
mottled(128, 128, (214, 196, 154), 12, 70, 21).save(OUT+'os_parchment_tile.png', optimize=True)
# Chatbox parchment (slightly darker, greyer).
mottled(128, 64, (190, 174, 136), 9, 50, 31).save(OUT+'os_chat_tile.png', optimize=True)

# Minimap-style round button (original procedural "map").
def minimap(plus=True, name='os_minimap.png'):
    S = 64; img = Image.new('RGBA', (S, S), (0, 0, 0, 0)); px = img.load()
    rnd = random.Random(5)
    land = mottled(S, S, (58, 92, 44), 14, 30, 41).load()
    c = (S-1)/2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x-c, y-c)
            if r <= 31.5:
                if r > 29.5: px[x, y] = (0, 0, 0, 255)
                elif r > 26.5:
                    ang = math.atan2(y-c, x-c)
                    lit = math.cos(ang + 2.36)  # light from top-left
                    b = 92 + 28*lit
                    px[x, y] = (clamp(b), clamp(b-8), clamp(b-22), 255)
                elif r > 25.5: px[x, y] = (0, 0, 0, 255)
                else:
                    p = land[x, y]
                    px[x, y] = p + (255,)
    d = ImageDraw.Draw(img)
    # a winding path and a pond (original)
    pts = [(10, 40), (18, 36), (26, 38), (34, 32), (42, 30), (52, 22)]
    d.line(pts, fill=(150, 128, 92), width=3)
    d.ellipse([38, 40, 50, 49], fill=(40, 70, 140))
    d.rectangle([16, 18, 21, 23], fill=(120, 110, 100)); d.rectangle([17, 19, 20, 22], fill=(160, 150, 136))
    # yellow plus with black outline in the centre
    if plus:
        for (x0, y0, x1, y1) in [(28, 20, 35, 43), (20, 28, 43, 35)]:
            d.rectangle([x0-1, y0-1, x1+1, y1+1], fill=(0, 0, 0))
        for (x0, y0, x1, y1) in [(29, 21, 34, 42), (21, 29, 42, 34)]:
            d.rectangle([x0, y0, x1, y1], fill=(255, 255, 0))
        d.rectangle([29, 21, 30, 42], fill=(255, 255, 160)); d.rectangle([21, 29, 42, 30], fill=(255, 255, 160))
    else:
        # plain map: white "you are here" dot plus a few yellow/white map dots (original)
        d.rectangle([31, 31, 32, 32], fill=(255, 255, 255))
        for (x, y) in [(22, 26), (44, 16), (40, 52), (14, 46)]: d.rectangle([x, y, x+1, y+1], fill=(255, 255, 0))
        for (x, y) in [(28, 47), (47, 36)]: d.rectangle([x, y, x+1, y+1], fill=(255, 255, 255))
    # re-cut to circle
    for y in range(S):
        for x in range(S):
            if math.hypot(x-c, y-c) > 31.5: px[x, y] = (0, 0, 0, 0)
    img.resize((192, 192), Image.NEAREST).save(OUT+name, optimize=True)
minimap()
minimap(False, 'os_minimap_plain.png')

# 16x16 icons from character maps.
PAL = {'.': None, 'k': (0, 0, 0), 'w': (255, 255, 255), 'y': (255, 255, 0), 'o': (255, 152, 31), 'b': (122, 84, 40),
       'B': (84, 56, 24), 'p': (226, 208, 160), 'P': (176, 152, 104), 'g': (150, 150, 150), 'G': (96, 96, 96),
       'r': (190, 30, 20), 'R': (120, 14, 10), 'c': (0, 220, 220), 'l': (200, 200, 210), 'L': (130, 130, 140), 'e': (40, 190, 40)}
ICONS = {
 'os_ic_scroll': [  # rolled scroll
  "................",
  "..kkkkkkkkkkkk..",
  ".kPppppppppppPk.",
  ".kPkkkkkkkkkkPk.",
  "..kppppppppppk..",
  "..kpkkkkkkkpk...",
  "..kppppppppppk..",
  "..kpkkkkkkpppk..",
  "..kppppppppppk..",
  "..kpkkkkkkkkpk..",
  "..kppppppppppk..",
  "..kpkkkkkppppk..",
  ".kPkkkkkkkkkkPk.",
  ".kPppppppppppPk.",
  "..kkkkkkkkkkkk..",
  "................"],
 'os_ic_chest': [  # chest
  "................",
  "................",
  "...kkkkkkkkkk...",
  "..kbbbbbbbbbbk..",
  ".kbBbbbbbbbbBbk.",
  ".kbBbbbbbbbbBbk.",
  ".kkkkkkyykkkkkk.",
  ".kbBbbbkkbbbBbk.",
  ".kbBbbbyybbbBbk.",
  ".kbBbbbbbbbbBbk.",
  ".kbBbbbbbbbbBbk.",
  ".kyBbbbbbbbbByk.",
  ".kkkkkkkkkkkkkk.",
  "................",
  "................",
  "................"],
 'os_ic_bin': [  # bucket-style bin
  "................",
  "......kkkk......",
  ".....kGkkGk.....",
  "..kkkkkkkkkkkk..",
  "..klllllllllLk..",
  "..kkkkkkkkkkkk..",
  "...kgLgLgLgLk...",
  "...kgLgLgLgLk...",
  "...kgLgLgLgLk...",
  "...kgLgLgLgLk...",
  "...kgLgLgLgLk...",
  "...kgLgLgLgLk...",
  "....kgLgLgLk....",
  "....kkkkkkkk....",
  "................",
  "................"],
 'os_ic_wrench': [  # wrench
  "................",
  "..........kkk...",
  ".........kllk...",
  "........kllk..k.",
  "........kllkkkLk",
  "........klllllLk",
  ".......klllllLk.",
  "......kllLLLkk..",
  ".....kllLk......",
  "....kllLk.......",
  "...kllLk........",
  "..kllLk.........",
  ".kllLk..........",
  ".kLLk...........",
  "..kk............",
  "................"],
 'os_ic_magnifier': [  # magnifier
  "................",
  "....kkkkk.......",
  "...kcccccK......",
  "..kcwccccck.....",
  "..kcwccccck.....",
  "..kccccccck.....",
  "..kccccccck.....",
  "...kcccccK......",
  "....kkkkkbk.....",
  ".........kbk....",
  "..........kbk...",
  "...........kbk..",
  "............kbk.",
  ".............kk.",
  "................",
  "................"],
 'os_ic_quill': [  # quill
  "................",
  "...........kkk..",
  "..........kwwwk.",
  ".........kwwwlk.",
  "........kwwwlk..",
  ".......kwwwlk...",
  "......kwwwlk....",
  ".....kwwwlk.....",
  "....kwwwlk......",
  "....kwwlk.......",
  "...kwllk........",
  "...kklk.........",
  "..kbkk..........",
  ".kbk............",
  ".kk.............",
  "................"],

 'os_ic_book': [  # open book
  "................",
  "................",
  ".kkkkkk..kkkkkk.",
  "kppppppkkppppppk",
  "kpPPPppkkpPPPppk",
  "kppppppkkppppppk",
  "kpPPPPpkkpPPPPpk",
  "kppppppkkppppppk",
  "kpPPPppkkpPPPppk",
  "kppppppkkppppppk",
  "kpPPPPpkkpPPPPpk",
  "kppppppkkppppppk",
  "kbbbbbbkkbbbbbbk",
  ".kkkkkkkkkkkkkk.",
  "................",
  "................"],
 'os_ic_inkpot': [  # quill over ink pot
  "................",
  "............kk..",
  "...........kwwk.",
  "..........kwwlk.",
  ".........kwwlk..",
  "........kwwlk...",
  ".......kwwlk....",
  "......kwllk.....",
  ".....kklkk......",
  "....kkkk........",
  "...kGGGGk.......",
  "..kGggggGk......",
  "..kGkkkkGk......",
  "..kGkkkkGk......",
  "...kGGGGk.......",
  "....kkkk........"],
 'os_ic_flame': [  # flame
  "................",
  ".......k........",
  "......kok.......",
  "......kook......",
  ".....koook..k...",
  "....kooyok.kok..",
  "...kooyyyokook..",
  "...koyyyyyooook.",
  "..kooyywyyyoook.",
  "..koyywwwyyyook.",
  "..koyywwwwyyok..",
  "..kooyywwyyook..",
  "...kooyyyyook...",
  "....kkooookk....",
  "......kkkk......",
  "................"],
 'os_ic_tick_scroll': [  # tick on parchment
  "................",
  ".kkkkkkkkkkkkk..",
  "kpppppppppppppk.",
  "kpPPPPPPPPPPppk.",
  "kppppppppppkkpk.",
  "kpppppppppkeekk.",
  "kppkkppppkeek.k.",
  "kpkeekppkeekppk.",
  "kppkeekkeekpppk.",
  "kpppkeeeekppppk.",
  "kppppkeekpppppk.",
  "kpppppkkppppppk.",
  "kpPPPPPPPPPPppk.",
  "kpppppppppppppk.",
  ".kkkkkkkkkkkkk..",
  "................"],
 'os_ic_check_off': [  # empty stone checkbox
  "................",
  "................",
  "..kkkkkkkkkkkk..",
  "..kGGGGGGGGGGk..",
  "..kGLLLLLLLLgk..",
  "..kGLggggggLgk..",
  "..kGLggggggLgk..",
  "..kGLggggggLgk..",
  "..kGLggggggLgk..",
  "..kGLggggggLgk..",
  "..kGLggggggLgk..",
  "..kGLLLLLLLLgk..",
  "..kGgggggggggk..",
  "..kkkkkkkkkkkk..",
  "................",
  "................"],
 'os_ic_check_on': [  # stone checkbox with green tick
  "................",
  "............kk..",
  "..kkkkkkkkkkeek.",
  "..kGGGGGGGGkeek.",
  "..kGLLLLLLkeekk.",
  "..kGLggggkeekk..",
  "..kGkkgggkeek...",
  "..kkeekgkeekgk..",
  "..kGkeekeekLgk..",
  "..kGLkeeeekLgk..",
  "..kGLgkeekgLgk..",
  "..kGLLLkkLLLgk..",
  "..kGgggggggggk..",
  "..kkkkkkkkkkkk..",
  "................",
  "................"],
}
for name, rows in ICONS.items():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            col = PAL.get(ch if ch != 'K' else 'k')
            if col: px[x, y] = col + (255,)
    img.resize((48, 48), Image.NEAREST).save(OUT+name+'.png', optimize=True)
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gen_kit_parts.py')).read())
print('ok')
