# Kit parts (executed by gen_art.py, shares its helpers). All ORIGINAL procedural pixel art.
# 9-patches are drawn at 1 art-pixel = 1dp and saved at xxhdpi (x3) with 1px nine-patch guides.
S = 3  # xxhdpi scale

def nine(img, name, stretch, pad):
    """img: art-size RGBA. stretch/pad: (x0, x1, y0, y1) in ART pixels, half-open."""
    big = img.resize((img.width*S, img.height*S), Image.NEAREST)
    out = Image.new('RGBA', (big.width+2, big.height+2), (0, 0, 0, 0)); out.paste(big, (1, 1))
    px = out.load(); K = (0, 0, 0, 255)
    sx0, sx1, sy0, sy1 = [v*S for v in stretch]; px0, px1, py0, py1 = [v*S for v in pad]
    for x in range(sx0, sx1): px[x+1, 0] = K
    for y in range(sy0, sy1): px[0, y+1] = K
    for x in range(px0, px1): px[x+1, out.height-1] = K
    for y in range(py0, py1): px[out.width-1, y+1] = K
    out.save(OUT9+name+'.9.png', optimize=True)

def bevel(w, h, face, light, dark, outline=(0, 0, 0), notch=True, pressed=False, inset=None):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w-1, h-1], fill=outline+(255,))
    hi, lo = (dark, light) if pressed else (light, dark)
    d.rectangle([1, 1, w-2, h-2], fill=lo+(255,))
    d.rectangle([1, 1, w-3, h-3], fill=hi+(255,))
    d.rectangle([2, 2, w-3, h-3], fill=face+(255,)) if inset is None else d.rectangle([2, 2, w-3, h-3], fill=inset)
    if notch:  # chipped corners
        for (x, y) in ((0, 0), (w-1, 0), (0, h-1), (w-1, h-1)): img.putpixel((x, y), (0, 0, 0, 0))
    return img

def shade(c, k): return tuple(clamp(v+k) for v in c)

# Frame: stone bevel border, transparent centre (overlay it on any texture or colour).
fr = bevel(12, 12, (0, 0, 0), (138, 123, 98), (35, 28, 20), inset=(0, 0, 0, 0))
nine(fr, 'os_frame', (4, 8, 4, 8), (3, 9, 3, 9))
# Filled panel frame (flat stone face; safe for RemoteViews).
nine(bevel(12, 12, (62, 53, 41), (138, 123, 98), (35, 28, 20)), 'os_frame_filled', (4, 8, 4, 8), (4, 8, 4, 8))

# Buttons.
STONE = (91, 79, 61)
nine(bevel(12, 12, STONE, (138, 123, 98), (35, 28, 20)), 'os_button_up', (4, 8, 4, 8), (3, 9, 3, 9))
nine(bevel(12, 12, (63, 53, 41), (138, 123, 98), (35, 28, 20), pressed=True), 'os_button_down', (4, 8, 4, 8), (3, 9, 3, 9))

# Quiz answer buttons: normal / pressed / correct (green) / wrong (red).
def answer(face, light, dark, name, pressed=False):
    img = bevel(16, 14, face, light, dark, pressed=pressed)
    nine(img, name, (5, 11, 5, 9), (3, 13, 3, 11))
answer(STONE, (138, 123, 98), (35, 28, 20), 'os_answer_normal')
answer((63, 53, 41), (138, 123, 98), (35, 28, 20), 'os_answer_pressed', pressed=True)
answer((30, 110, 30), (110, 210, 90), (10, 50, 10), 'os_answer_correct')
answer((130, 28, 20), (220, 96, 70), (60, 10, 6), 'os_answer_wrong')

# Tabs: cut top corners; selected tab is red and open at the bottom.
def tab(face, light, dark, selected):
    w, h = 12, 12
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 2, w-1, h-1], fill=(0, 0, 0, 255)); d.rectangle([2, 0, w-3, 1], fill=(0, 0, 0, 255))
    d.rectangle([1, 2, w-2, h-1 if selected else h-2], fill=dark+(255,))
    d.rectangle([1, 2, w-3, h-1 if selected else h-2], fill=light+(255,)); d.point([(2, 1), (w-3, 1)], fill=light+(255,))
    d.rectangle([3, 1, w-4, 1], fill=light+(255,))
    d.rectangle([2, 3, w-3, h-1 if selected else h-2], fill=face+(255,))
    if not selected: d.line([0, h-1, w-1, h-1], fill=(0, 0, 0, 255))
    return img
nine(tab((110, 34, 22), (162, 68, 46), (53, 16, 10), True), 'os_tab_down', (4, 8, 4, 8), (5, 7, 4, 9))
nine(tab((63, 53, 41), (122, 108, 86), (28, 23, 17), False), 'os_tab_up', (4, 8, 4, 8), (5, 7, 4, 9))

# Inventory slot: recessed dark square with a faint inner bevel.
def slot():
    img = Image.new('RGBA', (12, 12), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 11, 11], fill=(20, 16, 12, 255))
    d.rectangle([1, 1, 11, 11], fill=(92, 80, 62, 255))
    d.rectangle([1, 1, 10, 10], fill=(24, 20, 15, 255))
    d.rectangle([2, 2, 10, 10], fill=(52, 45, 35, 255))
    return img
nine(slot(), 'os_slot', (4, 8, 4, 8), (2, 10, 2, 10))
s2 = slot(); ImageDraw.Draw(s2).rectangle([2, 2, 10, 10], outline=(255, 255, 255, 255))
nine(s2, 'os_slot_selected', (4, 8, 4, 8), (2, 10, 2, 10))

# Scroll roll (horizontal wooden-cored parchment roll with knobs) for the lesson card ends.
def roll():
    w, h = 24, 10
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    # knobs
    for x0 in (0, w-3):
        d.rectangle([x0, 2, x0+2, h-3], fill=(0, 0, 0, 255)); d.rectangle([x0+1, 3, x0+1, h-4], fill=(122, 84, 40, 255))
    body = [(43, 29, 12), (107, 74, 32), (176, 150, 100), (226, 208, 160), (240, 226, 186), (214, 196, 154), (176, 150, 100), (122, 96, 52), (84, 56, 24), (43, 29, 12)]
    for y, c in enumerate(body): d.line([3, y, w-4, y], fill=c+(255,))
    d.line([3, 0, 3, h-1], fill=(0, 0, 0, 255)); d.line([w-4, 0, w-4, h-1], fill=(0, 0, 0, 255))
    return img
nine(roll(), 'os_scroll_roll', (8, 16, 0, 10), (4, 20, 0, 10))

# Seekbar thumb: little stone knob with gold cap (24dp).
def thumb():
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([1, 0, 6, 7], fill=(0, 0, 0, 255)); d.rectangle([0, 1, 7, 6], fill=(0, 0, 0, 255))
    d.rectangle([1, 1, 6, 6], fill=(35, 28, 20, 255)); d.rectangle([1, 1, 5, 5], fill=(232, 200, 96, 255))
    d.rectangle([2, 2, 5, 5], fill=(200, 160, 64, 255)); d.point([(2, 2)], fill=(255, 240, 160, 255))
    return img.resize((24*S, 24*S), Image.NEAREST)
thumb().save(OUT9+'os_seek_thumb.png', optimize=True)

# Orb frame (for the timer / stat orbs): dark stone ring with a recessed well, 32dp.
def orb():
    N = 32; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); px = img.load(); c = (N-1)/2
    for y in range(N):
        for x in range(N):
            r = math.hypot(x-c, y-c)
            if r <= 15.6:
                if r > 14.6: col = (0, 0, 0)
                elif r > 12.6:
                    k = 18 if (x+y) < N-1 else -18
                    col = shade((90, 78, 60), k)
                elif r > 11.6: col = (0, 0, 0)
                else: col = (24, 20, 15)
                px[x, y] = col+(255,)
    return img.resize((N*S, N*S), Image.NEAREST)
orb().save(OUT9+'os_orb.png', optimize=True)

# ---------------------------------------------------------------- v1.1: 2007-style carved frames & side panel
def wood_tile():
    """Dark brown-black wood with horizontal grain (tileable)."""
    W, H = 64, 64; rnd = random.Random(77)
    img = Image.new('RGB', (W, H), (40, 31, 21)); px = img.load()
    for y in range(H):
        band = math.sin(y * 0.9) * 4 + math.sin(y * 0.23 + 1) * 5
        for x in range(W):
            n = band + math.sin((x + y * 3) * 0.098) * 2 + rnd.uniform(-3, 3)
            px[x, y] = tuple(clamp(c + n) for c in (40, 31, 21))
    for _ in range(26):  # dark grain streaks
        y = rnd.randrange(H); x0 = rnd.randrange(W); ln = rnd.randint(6, 22)
        for i in range(ln): px[(x0 + i) % W, y] = tuple(clamp(c - 12) for c in px[(x0 + i) % W, y])
    for _ in range(14):  # light grain
        y = rnd.randrange(H); x0 = rnd.randrange(W); ln = rnd.randint(4, 12)
        for i in range(ln): px[(x0 + i) % W, y] = tuple(clamp(c + 10) for c in px[(x0 + i) % W, y])
    return img
WOOD = wood_tile(); WOOD.save(OUT + 'os_wood_tile.png', optimize=True)

def rivet(img, cx, cy):
    """3x3 domed rivet (+ dark rim) centred at (cx, cy)."""
    d = ImageDraw.Draw(img)
    for (x, y) in [(cx-1, cy-2), (cx, cy-2), (cx+1, cy-2), (cx-2, cy-1), (cx+2, cy-1), (cx-2, cy), (cx+2, cy), (cx-2, cy+1), (cx+2, cy+1), (cx-1, cy+2), (cx, cy+2), (cx+1, cy+2)]:
        img.putpixel((x, y), (14, 10, 6, 255))
    d.rectangle([cx-1, cy-1, cx+1, cy+1], fill=(122, 110, 92, 255))
    img.putpixel((cx-1, cy-1), (214, 204, 178, 255)); img.putpixel((cx, cy-1), (170, 158, 134, 255))
    img.putpixel((cx-1, cy), (170, 158, 134, 255)); img.putpixel((cx+1, cy+1), (70, 62, 50, 255))

def carved(size=24, band=7, rivets=True, centre=None):
    """Carved wood frame: black outline, light bevel, wood band, dark bevel, black inner line; rivets in corners."""
    N = size; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); px = img.load()
    for y in range(N):
        for x in range(N):
            e = min(x, y, N-1-x, N-1-y)
            if e >= band:
                if centre: px[x, y] = centre
                continue
            if e == 0 or e == band-1: c = (0, 0, 0)
            elif e == 1: c = (122, 100, 70) if (x < N-1-y and x <= y or y < x and y < N-1-x) and (x <= y or y < N-1-x) and not (x > y and N-1-x < y) else (16, 11, 7)
            elif e == band-2: c = (16, 11, 7) if (x <= y and x < N-1-y) or (y < x and y < N-1-x) else (106, 88, 62)
            else: c = WOOD.getpixel((x % 64, y % 64))
            px[x, y] = c + (255,)
    # bevel direction: top/left light, bottom/right dark on the outer edge
    for i in range(1, N-1):
        px[i, 1] = (122, 100, 70, 255); px[1, i] = (122, 100, 70, 255)
        px[i, N-2] = (16, 11, 7, 255); px[N-2, i] = (16, 11, 7, 255)
        b = band-2
        if b < i < N-1-b:
            px[i, b] = (16, 11, 7, 255); px[b, i] = (16, 11, 7, 255)
            px[i, N-1-b] = (106, 88, 62, 255); px[N-1-b, i] = (106, 88, 62, 255)
    if rivets:
        m = band // 2
        for (cx, cy) in ((m, m), (N-1-m, m), (m, N-1-m), (N-1-m, N-1-m)): rivet(img, cx, cy)
    return img

fr = carved()
nine(fr, 'os_frame_carved', (10, 14, 10, 14), (7, 17, 7, 17))
nine(carved(centre=(50, 41, 31, 255)), 'os_frame_carved_filled', (10, 14, 10, 14), (7, 17, 7, 17))
nine(carved(size=16, band=5, rivets=False), 'os_frame_thin', (6, 10, 6, 10), (5, 11, 5, 11))
r = Image.new('RGBA', (5, 5), (0, 0, 0, 0)); rivet(r, 2, 2); r.resize((15, 15), Image.NEAREST).save(OUT9 + 'os_rivet.png', optimize=True)

# Side-panel square stone tab: normal (stone) and selected (red), carved rim.
def side_tab(face, light, dark, rim=(28, 21, 14)):
    N = 16; img = Image.new('RGBA', (N, N), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, N-1, N-1], fill=(0, 0, 0, 255))
    d.rectangle([1, 1, N-2, N-2], fill=rim + (255,))
    d.rectangle([2, 2, N-3, N-3], fill=dark + (255,))
    d.rectangle([2, 2, N-4, N-4], fill=light + (255,))
    d.rectangle([3, 3, N-4, N-4], fill=face + (255,))
    for (x, y) in ((0, 0), (N-1, 0), (0, N-1), (N-1, N-1)): img.putpixel((x, y), (0, 0, 0, 0))
    return img
nine(side_tab((62, 52, 40), (112, 98, 76), (22, 17, 11)), 'os_side_tab_up', (6, 10, 6, 10), (4, 12, 4, 12))
nine(side_tab((116, 34, 20), (178, 74, 50), (54, 14, 8)), 'os_side_tab_down', (6, 10, 6, 10), (4, 12, 4, 12))

# Chat tabs under the chatbox: small stone tab with cut top corners; selected = lighter stone, gold edge.
def chat_tab(face, light, dark, edge=None):
    W, H = 14, 10; img = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle([0, 1, W-1, H-1], fill=(0, 0, 0, 255)); d.line([1, 0, W-2, 0], fill=(0, 0, 0, 255))
    d.rectangle([1, 1, W-2, H-2], fill=dark + (255,)); d.rectangle([1, 1, W-3, H-3], fill=light + (255,))
    d.rectangle([2, 2, W-3, H-3], fill=face + (255,))
    if edge: d.line([2, H-3, W-3, H-3], fill=edge + (255,))
    return img
nine(chat_tab((58, 48, 36), (104, 90, 70), (20, 15, 10)), 'os_chat_tab_up', (5, 9, 4, 6), (3, 11, 2, 8))
nine(chat_tab((96, 82, 62), (150, 132, 104), (34, 27, 18), edge=(200, 160, 64)), 'os_chat_tab_down', (5, 9, 4, 6), (3, 11, 2, 8))

# More 16x16 icons (same palette/format as ICONS in gen_art.py).
ICONS2 = {
 'os_ic_stats': [  # skill bars on a little stone plinth
  "................", "............kk..", "...........kyyk.", "...........kyyk.", "......kk...kyyk.", ".....keek..kyyk.",
  ".....keek..kyyk.", "..kk.keek..kyyk.", ".krrkkeek..kyyk.", ".krrkkeek..kyyk.", ".krrkkeek..kyyk.", ".krrkkeek..kyyk.",
  "kkkkkkkkkkkkkkkk", "kGLLLLLLLLLLLLGk", "kkkkkkkkkkkkkkkk", "................"],
 'os_ic_quest': [  # treasure map with a red X
  "................", "..kkkkkkkkkkkk..", ".kpppPpppppPppk.", ".kpPppppppppppk.", ".kppp.pp.ppPppk.", ".kpp.pppp.pppPk.",
  ".kp.pppppprkrpk.", ".kppPpppppkrkpk.", ".kpppp.ppprkrpk.", ".kPpppp.pppppk..", ".kpppppp.pPppk..", ".kpppPppppppppk.",
  ".kppppppPpppppk.", "..kkkkkkkkkkkk..", "................", "................"],
 'os_ic_tag': [  # brown label tag with string
  "................", "..........kk....", ".........k..k...", "........k....k..", ".....kkkk.kk.k..", "....kppppk..k...",
  "...kppkkppk.k...", "..kpppkkpppk....", ".kppppppppppk...", ".kpPpppppppk....", "..kpPppppppk....", "...kpPppppk.....",
  "....kpPppk......", ".....kppk.......", "......kk........", "................"],
 'os_ic_mic': [  # stone/silver microphone
  "................", "......kkkk......", ".....klllLk.....", ".....kwllLk.....", ".....klllLk.....", ".....klllLk.....",
  "...k.klllLk.k...", "...k.kLLLLk.k...", "...k..kkkk..k...", "....k......k....", ".....kkkkkk.....", ".......kk.......",
  ".......kk.......", ".....kkkkkk.....", "................", "................"],
 'os_ic_plus': [
  "................", "................", "......kkkk......", "......kyyk......", "......kyyk......", "......kyyk......",
  "..kkkkkyykkkkk..", "..kyyyyyyyyyyk..", "..kyyyyyyyyyyk..", "..kkkkkyykkkkk..", "......kyyk......", "......kyyk......",
  "......kyyk......", "......kkkk......", "................", "................"],
 'os_ic_memo': [  # lute-ish horn = "Recording"
  "................", "...........kk...", "..........kook..", ".........kook...", "........kook....", ".......kook.....",
  "..kkk.kook......", ".kbbbkook.......", "kbBbbbkk........", "kbbkkbbk........", "kbbkkbbk........", "kbbbbbBk........",
  ".kbbbbk.........", "..kkkk..........", "................", "................"],
}
for name, rows in ICONS2.items():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            col = PAL.get(ch)
            if col: px[x, y] = col + (255,)
    img.resize((48, 48), Image.NEAREST).save(OUT + name + '.png', optimize=True)
