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
