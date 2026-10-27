"""Self-drawn proportional pixel font for the Old School kit ("OS Small" regular + bold).
Original glyph designs (7px cap height, 5px x-height, 2px descenders), built into TrueType outlines (one square per
pixel run) with fontTools. Released under the SIL Open Font License 1.1 (see assets/licenses/os_font-OFL.txt).
Run from the kit root: python3 tools/gen_font.py"""
import os
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PX = 100            # font units per pixel
ROWS = 9            # rows 0..6 = cap zone (row 6 sits on the baseline), rows 7..8 = descender
UPEM = 1100

# Glyphs: rows from the top of the cap zone. A leading number "n:" means "start at row n" (rows above are blank).
G = {
 'A': ".##.|#..#|#..#|####|#..#|#..#|#..#", 'B': "###.|#..#|#..#|###.|#..#|#..#|###.",
 'C': ".###|#...|#...|#...|#...|#...|.###", 'D': "###.|#..#|#..#|#..#|#..#|#..#|###.",
 'E': "####|#...|#...|###.|#...|#...|####", 'F': "####|#...|#...|###.|#...|#...|#...",
 'G': ".###|#...|#...|#.##|#..#|#..#|.###", 'H': "#..#|#..#|#..#|####|#..#|#..#|#..#",
 'I': "###|.#.|.#.|.#.|.#.|.#.|###",      'J': "..##|...#|...#|...#|...#|#..#|.##.",
 'K': "#..#|#..#|#.#.|##..|#.#.|#..#|#..#", 'L': "#...|#...|#...|#...|#...|#...|####",
 'M': "#...#|##.##|#.#.#|#.#.#|#...#|#...#|#...#", 'N': "#...#|##..#|#.#.#|#..##|#...#|#...#|#...#",
 'O': ".##.|#..#|#..#|#..#|#..#|#..#|.##.", 'P': "###.|#..#|#..#|###.|#...|#...|#...",
 'Q': ".##.|#..#|#..#|#..#|#..#|#.#.|.#.#", 'R': "###.|#..#|#..#|###.|#.#.|#..#|#..#",
 'S': ".###|#...|#...|.##.|...#|...#|###.", 'T': "#####|..#..|..#..|..#..|..#..|..#..|..#..",
 'U': "#..#|#..#|#..#|#..#|#..#|#..#|.##.", 'V': "#...#|#...#|#...#|.#.#.|.#.#.|.#.#.|..#..",
 'W': "#...#|#...#|#...#|#.#.#|#.#.#|##.##|#...#", 'X': "#...#|.#.#.|.#.#.|..#..|.#.#.|.#.#.|#...#",
 'Y': "#...#|#...#|.#.#.|..#..|..#..|..#..|..#..", 'Z': "#####|....#|...#.|..#..|.#...|#....|#####",
 'a': "2:.###|#..#|#..#|#..#|.###", 'b': "#...|#...|###.|#..#|#..#|#..#|###.",
 'c': "2:.###|#...|#...|#...|.###", 'd': "...#|...#|.###|#..#|#..#|#..#|.###",
 'e': "2:.##.|#..#|####|#...|.###", 'f': ".##|#..|###|#..|#..|#..|#..",
 'g': "2:.###|#..#|#..#|#..#|.###|...#|###.", 'h': "#...|#...|###.|#..#|#..#|#..#|#..#",
 'i': "#|.|#|#|#|#|#", 'j': "..#|...|..#|..#|..#|..#|..#|..#|##.",
 'k': "#...|#...|#..#|#.#.|##..|#.#.|#..#", 'l': "#|#|#|#|#|#|#",
 'm': "2:####.|#.#.#|#.#.#|#.#.#|#.#.#", 'n': "2:###.|#..#|#..#|#..#|#..#",
 'o': "2:.##.|#..#|#..#|#..#|.##.", 'p': "2:###.|#..#|#..#|#..#|###.|#...|#...",
 'q': "2:.###|#..#|#..#|#..#|.###|...#|...#", 'r': "2:#.##|##..|#...|#...|#...",
 's': "2:.###|#...|.##.|...#|###.", 't': ".#.|.#.|###|.#.|.#.|.#.|..#",
 'u': "2:#..#|#..#|#..#|#..#|.###", 'v': "2:#...#|#...#|.#.#.|.#.#.|..#..",
 'w': "2:#...#|#...#|#.#.#|#.#.#|.#.#.", 'x': "2:#...#|.#.#.|..#..|.#.#.|#...#",
 'y': "2:#..#|#..#|#..#|#..#|.###|...#|###.", 'z': "2:####|...#|.##.|#...|####",
 '0': ".##.|#..#|#.##|##.#|#..#|#..#|.##.", '1': ".#.|##.|.#.|.#.|.#.|.#.|###",
 '2': ".##.|#..#|...#|..#.|.#..|#...|####", '3': "###.|...#|...#|.##.|...#|...#|###.",
 '4': "#..#|#..#|#..#|####|...#|...#|...#", '5': "####|#...|#...|###.|...#|...#|###.",
 '6': ".##.|#...|#...|###.|#..#|#..#|.##.", '7': "####|...#|...#|..#.|..#.|.#..|.#..",
 '8': ".##.|#..#|#..#|.##.|#..#|#..#|.##.", '9': ".##.|#..#|#..#|.###|...#|...#|.##.",
 '!': "#|#|#|#|#|.|#", '"': "#.#|#.#", '#': "1:.#.#.|#####|.#.#.|#####|.#.#.",
 '$': "..#..|.####|#.#..|.###.|..#.#|####.|..#..", '%': "2:##..#|##.#.|..#..|.#.##|#..##",
 '&': ".#..|#.#.|#.#.|.#..|#.##|#..#|.###", "'": "#|#", '(': ".#|#.|#.|#.|#.|#.|.#", ')': "#.|.#|.#|.#|.#|.#|#.",
 '*': "1:#.#|.#.|###|.#.|#.#", '+': "2:.#.|.#.|###|.#.|.#.", ',': "6:.#|#.", '-': "4:###", '.': "6:#",
 '/': "...#|...#|..#.|.#..|.#..|#...|#...", ':': "2:#|.|.|.|#", ';': "2:.#|..|..|..|.#|#.",
 '<': "1:..#|.#.|#..|.#.|..#", '=': "3:###|...|###", '>': "1:#..|.#.|..#|.#.|#..",
 '?': ".##.|#..#|...#|..#.|.#..|....|.#..", '@': ".###.|#...#|#.###|#.#.#|#.###|#....|.###.",
 '[': "##|#.|#.|#.|#.|#.|##", '\\': "#...|#...|.#..|..#.|..#.|...#|...#", ']': "##|.#|.#|.#|.#|.#|##",
 '^': ".#.|#.#", '_': "8:####", '`': "#.|.#", '{': "..#|.#.|.#.|#..|.#.|.#.|..#", '|': "#|#|#|#|#|#|#|#",
 '}': "#..|.#.|.#.|..#|.#.|.#.|#..", '~': "3:.#.#|#.#.",
 '\u2026': "6:#.#.#", '\u00b7': "4:#", '\u2014': "4:#####", '\u2013': "4:####", '\u2019': "#|#", '\u2018': "#|#",
 '\u201c': "#.#|#.#", '\u201d': "#.#|#.#", '\u2022': "3:##|##", '\u00b0': ".#.|#.#|.#.",
 '\u00a3': ".##.|#..#|#...|###.|#...|#...|####", '\u00d7': "2:#.#|.#.|#.#", '\u2192': "2:..#.|####|..#.",
}
# v1.3: a few accented letters (original pixel designs).
G.update({
 '\u00e9': "..#.|.#..|.##.|#..#|####|#...|.###", '\u00e8': ".#..|..#.|.##.|#..#|####|#...|.###",
 '\u00e0': ".#..|..#.|.###|#..#|#..#|#..#|.###", '\u00e1': "..#.|.#..|.###|#..#|#..#|#..#|.###",
 '\u00fc': "#..#|....|#..#|#..#|#..#|#..#|.###", '\u00f6': "#..#|....|.##.|#..#|#..#|#..#|.##.",
 '\u00e4': "#..#|....|.###|#..#|#..#|#..#|.###", '\u00f1': ".#.#|#.#.|###.|#..#|#..#|#..#|#..#",
 '\u00e7': "2:.###|#...|#...|#...|.###|..#.|.#..",
})
# v1.3: hand-made bold forms for letters whose diagonals clog when simply doubled (bold N read like M).
BOLD = {
 'N': "##...##|###..##|##.#.##|##..###|##...##|##...##|##...##",
 'M': "##...##|###.###|##.#.##|##.#.##|##...##|##...##|##...##",
 'W': "##...##|##...##|##...##|##.#.##|##.#.##|###.###|##...##",
 'V': "##...##|##...##|##...##|.##.##.|.##.##.|..###..|...#...",
 'X': "##...##|.##.##.|..###..|...#...|..###..|.##.##.|##...##",
 'Y': "##...##|##...##|.##.##.|..###..|...##..|...##..|...##..",
 'Z': "######|....##|...##.|..##..|.##...|##....|######",
 'K': "##..##|##.##.|####..|###...|####..|##.##.|##..##",
 'm': "2:#####.|##.#.##|##.#.##|##.#.##|##.#.##", 'w': "2:##...##|##...##|##.#.##|##.#.##|.##.##.",
 'v': "2:##..##|##..##|.####.|.####.|..##..", 'x': "2:##..##|.####.|..##..|.####.|##..##",
}

def rows_of(spec):
    start = 0
    if ':' in spec.split('|')[0] and spec[0].isdigit():
        n, spec = spec.split(':', 1); start = int(n)
    rows = spec.split('|')
    w = max(len(r) for r in rows)
    grid = [[False]*w for _ in range(ROWS)]
    for i, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == '#': grid[start+i][x] = True
    return grid, w

def embolden(grid, w):
    out = [[False]*(w+1) for _ in range(ROWS)]
    for y in range(ROWS):
        for x in range(w):
            if grid[y][x]: out[y][x] = out[y][x+1] = True
    return out, w+1

def glyph(grid, w):
    pen = TTGlyphPen(None)
    for y in range(ROWS):
        x = 0
        while x < w:
            if grid[y][x]:
                x0 = x
                while x < w and grid[y][x]: x += 1
                top = (7 - y) * PX; bot = top - PX          # row 6's bottom edge = baseline (0)
                l, r = (x0 + 0) * PX + PX // 2, x * PX + PX // 2   # half-pixel left bearing
                pen.moveTo((l, bot)); pen.lineTo((l, top)); pen.lineTo((r, top)); pen.lineTo((r, bot)); pen.closePath()
            else: x += 1
    return pen.glyph()

def build(bold, out):
    order = ['.notdef', 'space'] + ['u%04X' % ord(c) for c in G]
    cmap = {32: 'space'}; glyphs = {}; metrics = {}
    nd = TTGlyphPen(None)
    for (a, b, c, d) in [(50, 0, 450, 700)]:
        nd.moveTo((a, b)); nd.lineTo((a, d)); nd.lineTo((c, d)); nd.lineTo((c, b)); nd.closePath()
    glyphs['.notdef'] = nd.glyph(); metrics['.notdef'] = (500, 50)
    glyphs['space'] = TTGlyphPen(None).glyph(); metrics['space'] = (300 + (100 if bold else 0), 0)
    for c, spec in G.items():
        name = 'u%04X' % ord(c); grid, w = rows_of(spec)
        if bold and c in BOLD: grid, w = rows_of(BOLD[c])
        elif bold: grid, w = embolden(grid, w)
        glyphs[name] = glyph(grid, w); cmap[ord(c)] = name
        xs = [x for y in range(ROWS) for x in range(w) if grid[y][x]]
        metrics[name] = ((w + 1) * PX, (min(xs) if xs else 0) * PX + PX // 2)
    style = 'Bold' if bold else 'Regular'
    fb = FontBuilder(UPEM, isTTF=True)
    fb.setupGlyphOrder(order); fb.setupCharacterMap(cmap); fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=900, descent=-300)
    fb.setupNameTable({'familyName': 'OS Small', 'styleName': style,
                       'uniqueFontIdentifier': 'OSSmall-' + style + '-1.3', 'fullName': 'OS Small ' + style,
                       'psName': 'OSSmall-' + style, 'version': 'Version 1.300',
                       'copyright': 'Copyright 2026 Josh (Josh Notes / Josh Learn). Original pixel design.',
                       'licenseDescription': 'This Font Software is licensed under the SIL Open Font License, Version 1.1.',
                       'licenseInfoURL': 'https://openfontlicense.org'})
    fb.setupOS2(sTypoAscender=900, sTypoDescender=-300, sTypoLineGap=0, usWinAscent=1000, usWinDescent=350,
                sxHeight=500, sCapHeight=700, usWeightClass=700 if bold else 400,
                fsSelection=(0x20 if bold else 0x40), achVendID='JOSH')
    fb.setupPost(); fb.setupDummyDSIG() if hasattr(fb, 'setupDummyDSIG') else None
    fb.font['head'].macStyle = 1 if bold else 0
    fb.save(out)

FONT = os.path.join(ROOT, 'src/main/res/font/')
build(False, FONT + 'os_font_small.ttf')
build(True, FONT + 'os_font_small_bold.ttf')
print('font ok', len(G), 'glyphs')
