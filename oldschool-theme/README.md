# Old School theme kit (`oldschool-theme`) — v1.3.1

A self-contained Android library module with the "Old School" look used by **Josh Notes** and **Josh Learn**:
stone interface panels with bevels, parchment scrolls, yellow pixel text with a hard black shadow, cyan secondary
text, orange links. **All artwork is original** (procedural/hand-made by `tools/gen_art.py`); no game assets,
logos, sprites or fonts from any commercial game. Body text uses **OS Small**, a self-drawn pixel font made for this
kit (`tools/gen_font.py`, SIL OFL 1.1); Pixelify Sans (OFL) is still shipped for apps that referenced it.

**v1.1 = the 2007-era redesign**: carved dark wood/stone frames with rivets, parchment chatbox lists with a bottom tab
row, a side-panel strip of square stone tabs, yellow `#FFFF00` headers, "Choose Option" menus, "Click here to
continue" / "Select an Option" chatbox dialogs, a 3-column skills grid, right-side XP drops and a minimap orb.
See section 3b and *Upgrading from 1.0.0*.

* Package / namespace: `com.josh.oldschool` (Kotlin classes `Os*`)
* Every resource is prefixed **`os_`** (drawables, colours, dimens, layouts, ids, attrs, font, asset); styles live
  under `Theme.OldSchool`, `ThemeOverlay.OldSchool.*`, `Widget.OldSchool.*`, `TextAppearance.OldSchool.*`.
* Nothing in it refers to an app package, so it drops into any app.
* Version: `VERSION` file = `Os.VERSION` = `BuildConfig.KIT_VERSION` in the module. See `CHANGELOG.md`.
* Requirements: AGP 9.x (built-in Kotlin), minSdk 26, compileSdk 37, AppCompat 1.8 + Material 1.14.

## 1. Drop it into an app (vendored module — the robust way)

The kit is **vendored** (copied) into each app as a Gradle module and committed with the app. Each app therefore
builds on its own (fresh clone, GitHub, CI) and never depends on `/workspace/shared` existing; upgrades are an
explicit re-sync.

```bash
/workspace/shared/oldschool-theme/tools/sync-into.sh /path/to/your-project      # -> your-project/oldschool-theme/
```

One-time wiring:

```kotlin
// settings.gradle.kts
include(":oldschool-theme")

// build.gradle.kts (root) — same AGP version as your app plugin
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
}

// app/build.gradle.kts
dependencies { implementation(project(":oldschool-theme")) }
```

```properties
# gradle.properties — lets app code say R.drawable.os_panel instead of com.josh.oldschool.R.drawable.os_panel
android.nonTransitiveRClass=false
```
(Or keep non-transitive R and `import com.josh.oldschool.R as OsR`.)

Upgrading: re-run `sync-into.sh` (it replaces only `<project>/oldschool-theme/`, writes `.kit-version`), rebuild,
commit. Josh Notes does this automatically at the start of `release.sh`.

**Important when adopting:** delete the app's own copies of any `os_*` resources (e.g. `colors_os.xml`,
`drawable/os_*.xml`, `drawable-nodpi/os_*.png`, `font/pixelify_sans.ttf`). App resources silently override library
resources of the same name, so stale copies would make the two apps drift apart. The font is now
`@font/os_pixelify_sans`.

### Upgrading from 1.3.0 to 1.3.1
Re-sync and rebuild. Scroll views under the screen frame's right border get extra right padding (the frame band, 7dp)
so their scrollbar is visible; remove any local workaround for that. Opt out of sharpened shadows per view with
`Os.noSharpShadow(view)` or an XML `<tag android:id="@id/os_no_sharp_shadow" android:value="true" />`; use
`Os.keepScrollbarsInsideFrame(view)` when you draw a frame yourself. See `RESYNC-1.3.1.md`.

### Upgrading from 1.2.0 to 1.3.0
Re-sync and rebuild; no code changes are required (no API or resource was removed or renamed). 1.3 is a visual pass
under the same names plus a few additions (section 3d). Things you may notice: panels/buttons are the classic
`#3E3529` brown, the "Choose Option" menu is flat `#5D5447` with white options that turn yellow when pressed, stone
buttons have pressed/focused/disabled looks and an `os_button_text` colour state list, `ScrollView`s get an
always-visible stone scrollbar (theme `android:scrollViewStyle`), and `Os.applyScreenFrame(root)` now also snaps
shadowed text to a one-font-pixel black shadow (`Os.crispShadows`). If a screen must keep a fading scrollbar, give
that ScrollView `android:fadeScrollbars="true"`.

### Upgrading from 1.1.0 to 1.2.0
Re-sync and rebuild; nothing to change. 1.2 only **adds** APIs (section 3c). Two small fixes you get for free:
`Widget.OldSchool.Button.LinkInk` (the parchment overlay's button style) no longer inherits the dark theme's text
shadow, and `ThemeOverlay.OldSchool.Parchment` now also covers `android:buttonStyle`, `buttonStyle`,
`materialButtonOutlinedStyle` and `android:editTextColor`. Local fallbacks for the items in 3c can be deleted.

### Upgrading from 1.0.0 (what changed)
1. Re-sync (`sync-into.sh`), rebuild. No API was removed; everything from 1.0 still compiles.
2. **Font**: the theme font is now `@font/os_font` (family: `os_font_small.ttf` regular 400 + `os_font_small_bold.ttf`
   bold 700). In code use `Os.font(ctx)` / `Os.font(ctx, bold = true)` instead of loading `R.font.os_pixelify_sans`.
   Add `assets/licenses/os_font-OFL.txt` to your licences screen (keep the Pixelify one too).
3. **Look changes you get for free**: toolbar is dark wood with rivets and a bold yellow title; panel tile is darker
   brown-black; `os_widget_bg` / `os_chatbox_carved` use the carved riveted frame; stone buttons (`os_button_*`) are
   textured and invert their bevel when pressed; `OsSkillTile` is the recessed classic tile (level/level), dialog
   titles are yellow, dialog body orange, dialog buttons stone.
4. **Opt-in pieces** (section 3b): `Os.applyScreenFrame(root)`, `OsDialogs.alert(ctx)` (chatbox dialogs),
   `OsDialogs.chat/options`, side tabs, chat tabs, `OsXpDrops.rightSide`, `OsSkillTile.setTotal`, `os_minimap_mic`.
5. `OsDialogs.contextMenu` now ends with a **Cancel** row by default (`cancel = false` to hide; it never calls `onPick`).

## 2. Theme

```xml
<style name="Theme.YourApp.OldSchool" parent="Theme.OldSchool">
    <!-- your app-only attributes here -->
</style>
<style name="Theme.YourApp.OldSchool.Translucent" parent="Theme.OldSchool.Translucent" />
```
`Theme.OldSchool` (Material3 dark base) sets colours, the pixel font, window stone background, and default styles
for TextView, Card, RadioButton, Switch, MaterialButton (bevelled stone), borderless/link buttons, ExtendedFAB
(minimap orb), Toolbar, Snackbar (chatbox), alert dialogs (`ThemeOverlay.OldSchool.Dialog`), cut-0 shapes and every
Material text appearance. Paper areas (editor, lessons): wrap with `android:theme="@style/ThemeOverlay.OldSchool.Parchment"`
for black ink text, no shadow.

## 3. What's inside

| Piece | Resources / classes |
|---|---|
| Colour tokens | `os_colors.xml`: `os_gold os_panel os_panel_dark os_panel_light os_stone os_yellow os_orange os_cyan os_white os_link os_text os_text_dim os_ink os_ink_soft os_ink_brown os_chat_blue os_chat_blue_dark os_chat_red os_link_ink os_red os_green os_parchment os_menu_bg os_menu_header_text os_answer_correct os_answer_wrong os_bar_green os_bar_red os_xp_gold os_timer_ok os_timer_warn os_timer_low`; `color/os_tab_text` |
| Dimensions | `os_dimens.xml`: `os_outline os_bevel os_space_xs…xl os_text_small/body/button/title/heading os_icon_small/icon/icon_large os_button_min_height os_answer_min_height os_tab_height os_slot_size os_orb_size os_scroll_roll_height os_seek_height os_bar_height os_widget_header_height os_widget_row_min_height` |
| Font + licence | `font/os_pixelify_sans.ttf`, `assets/licenses/os_PixelifySans-OFL.txt` (show it in your licences screen) |
| Text styles | `TextAppearance.OldSchool.{Display,Headline,Title,Body,Label}{Large,Medium,Small}` and `.Ink.*` twins, `TextAppearance.OldSchool.LessonTitle/LessonBody`, `Widget.OldSchool.TextView(.Ink)` |
| Textures | `os_stone_tile os_panel_tile os_parchment_tile os_chat_tile` (nodpi, tile with `tileMode=repeat filter=false`) |
| Panels (layer-list) | `os_panel os_stone_bg os_scroll os_chatbox os_chat_row os_toolbar_bg os_banner os_context_menu` |
| 9-patch frames | `os_frame` (border only, transparent centre — overlay on anything), `os_frame_filled` |
| Buttons | layer-list `os_button` (+`_normal/_pressed`); 9-patch `os_button_9` (`os_button_up/_down`), style `Widget.OldSchool.Button(.Stone/.Link/.LinkInk)` |
| Tabs | layer-list `os_tab` (+`_normal/_selected`); 9-patch `os_tab_9` (`os_tab_up/_down`, selected or checked), style `Widget.OldSchool.Tab` |
| Orb | `os_orb` (stone ring), `os_minimap` + `os_minimap_fab` |
| Checkbox | `os_checkbox` selector (`os_ic_check_on/off`), style `Widget.OldSchool.CheckBox` |
| Icons (16×16 art, nodpi 48px) | `os_ic_scroll os_ic_chest os_ic_bin os_ic_wrench os_ic_magnifier os_ic_quill os_ic_book os_ic_inkpot os_ic_flame os_ic_tick_scroll os_ic_check_on os_ic_check_off`; widget vectors `os_ic_widget_add/mic/attach/bell/pin`; `os_hitsplat` |
| Dialog | `OsDialogs.builder(ctx)` → `MaterialAlertDialogBuilder` with the stone panel |
| Context menu | `OsDialogs.contextMenu(ctx, listOf("Open","Delete"), target = "photo.jpg") { i -> }.show()` ("Choose Option" header; target shown in orange); `contextMenuView(...)` for your own PopupWindow |
| XP | `OsXp.xpForLevel / levelFor / fraction` (classic public curve, 99 max) |
| XP drop | `OsXpDrop.show(activity, 10, icon = R.drawable.os_ic_quill)` |
| Banner | `OsBanner.levelUp(activity, "Learning", 12)` (chatbox + pixel fireworks); generic `OsBanner.show(activity, lines, icon, fireworks)`; `OsFireworks` view |
| Quest scroll | `OsQuestScroll.show(activity, "Quest complete!", "You have completed", title, listOf("1 Quest point"))` |
| Sounds | `OsChiptune.play(Kind.LEVEL_UP / QUEST / CORRECT / WRONG / TICK)` — synthesised, original melodies. Gate it behind your own setting. |
| **Lesson card** | `com.josh.oldschool.OsLessonCard` (vertical LinearLayout) or `style="@style/Widget.OldSchool.LessonCard"` on any layout; drawable `os_lesson_card` (tiled parchment between two `os_scroll_roll` 9-patch rolls). Use `TextAppearance.OldSchool.LessonTitle/LessonBody`. Sample: `layout/os_lesson_card_sample.xml` |
| **Quiz answers** | `com.josh.oldschool.OsAnswerButton` — `result = Result.NONE / CORRECT / WRONG` (pressed is automatic). XML: `app:os_state_correct="true"`. Drawable `os_answer_button` (custom states `os_state_correct/os_state_wrong`), pieces `os_answer_normal/_pressed/_correct/_wrong` |
| **Timer orb** | `com.josh.oldschool.OsTimerOrb` — `start(30_000) { timeUp() }`, `pause() resume() stop()`, `setRemaining(f)`, `onTick`; ring of 32 segments drains anticlockwise from 12 o'clock, green → yellow (≤50%) → red (≤20%), seconds in the middle. XML: `app:os_durationMs app:os_segments app:os_showText app:os_fraction`; size `@dimen/os_orb_size` |
| **Inventory slot** | `com.josh.oldschool.OsInventorySlot` — `emoji = "🌊"; label = "Tides"; count = 12` (count shown top-left with 100K/10M notation; `isSelected` = white outline). XML: `app:os_emoji app:os_label app:os_count`. Drawables `os_slot os_slot_selected os_slot_bg` |
| **Depth slider** | `<SeekBar style="@style/Widget.OldSchool.SeekBar" />` (stone groove `os_seekbar_track`, gold fill, `os_seek_thumb` knob); stepped: `Widget.OldSchool.SeekBar.Discrete` + `android:max="5"` |
| Skill tile | `com.josh.oldschool.OsSkillTile.set(R.drawable.os_ic_book, 42, "Lessons", 99)` |
| Bars | `style="@style/Widget.OldSchool.ProgressBar"` (green on red) / `.Xp` (gold) — `os_progress_hp`, `os_progress_xp` |
| Widget frame pieces (RemoteViews-safe) | backgrounds `os_widget_bg os_widget_row os_chatbox os_chat_row os_frame_filled os_banner`; layouts `os_rv_frame` (header bitmap + 2 buttons + ListView `os_rv_list`/`os_rv_empty`), `os_rv_check_row`, `os_rv_inventory_slot`, `os_rv_skill_tile`, `os_rv_progress(_xp)`, `os_rv_banner`, `os_rv_answer`; helper `OsRemote.pixelText/slot/skill/progress/answerBackground`, `OsPixelText.render/fitSize` |

## 3b. New in 1.1 (2007-era redesign)

| Piece | Resources / classes |
|---|---|
| Colours | `os_header` (#FFFF00), `os_highlight_orange/cyan/white`, `os_wood os_wood_light os_wood_dark`, `os_dialog_red os_dialog_blue`, `os_quest_red/yellow/green`; `color/os_chat_tab_text` |
| Headers | `TextAppearance.OldSchool.Header` — bold yellow #FFFF00 with a 1px black shadow (toolbar titles use it automatically) |
| Font | `@font/os_font` (OS Small regular/bold, self-drawn, OFL: `assets/licenses/os_font-OFL.txt`); `Os.font(ctx, bold)` |
| Carved frames | 9-patch `os_frame_carved` (dark wood band, bevelled light/dark edges, rivets in the corners; border only), `os_frame_carved_filled`, `os_frame_thin`, `os_rivet`, texture `os_wood_tile`; `os_screen_frame` |
| Screen frame | `Os.applyScreenFrame(rootView)` — draws the carved frame as a foreground that follows the root's padding (system-bar insets); call after insets are applied |
| Chatbox list | `os_chatbox_carved` (parchment inside a carved frame) for lists; bottom tab row: style `Widget.OldSchool.ChatTab` (`os_chat_tab` selector: `os_chat_tab_up/down`, white text when selected, orange otherwise; use `isSelected`) |
| Side tabs | style `Widget.OldSchool.SideTab` on an `ImageButton` (`os_side_tab`: square stone, red when selected, darker when pressed) inside `os_side_panel`; size `@dimen/os_side_tab_size` |
| Icons (new) | `os_ic_stats os_ic_quest os_ic_tag os_ic_mic os_ic_plus os_ic_memo` |
| Orbs | `os_minimap_mic` (minimap + mic badge, for dictation, `@dimen/os_minimap_size`), `os_minimap_plain`, `os_orb_new` (orb + plus) |
| Choose Option menu | `OsDialogs.contextMenu(ctx, options, target, header = "Choose Option", cancel = true) { i -> }` — white option + orange target, Cancel row |
| Chat dialogs | `OsDialogs.chat(ctx, title, message, continueText = "Click here to continue") { }` (bottom chatbox, tap anywhere); `OsDialogs.options(ctx, message, listOf("Yes","No")) { i -> }` ("Select an Option" in red); long messages scroll |
| Drop-in builder | `OsDialogs.alert(ctx)` = `OsAlertBuilder`, a `MaterialAlertDialogBuilder`: title/message + 1 button → chat dialog; 2–3 buttons → options (positive, neutral, negative order); custom views/lists → stone panel dialog. Listeners get the real dialog + `BUTTON_*`. Don't call `getButton()` on chat-style results (there are none) |
| Skills grid | `OsSkillTile.set(icon, level, label = null, baseLevel = level)`, `setTotal(total, "Total level:")`; put 3 per row in a GridLayout on `os_widget_bg` |
| XP drops | `OsXpDrops.rightSide(activity, listOf(R.drawable.os_ic_quill to 40, R.drawable.os_ic_book to 120))` — staggered drops rising up the right edge |
| Stone buttons | `os_button_normal/_pressed` textured; style `Widget.OldSchool.Button.Dialog` for dialog buttons |
| Pixel text → bitmap | `OsPixelText.render(ctx, text, sizeSp, color, maxWidthPx, maxLines = 1, shadow = true, strike = false, bold = false)` returns a `Bitmap`; `OsPixelText.fitSize(..., bold)` shrinks to fit. `OsRemote.pixelText(ctx, views, imageViewId, text, sizeSp, color, maxWidthPx, ...)` sets it straight into a RemoteViews ImageView. Use for widgets, notifications, anywhere an app font can't load |

## 3c. New in 1.2

| Piece | Resources / classes |
|---|---|
| **2-line slot labels** | `OsRemote.slot(ctx, views, emoji, label, count, widthPx, labelMaxLines = 2)`; label bitmap word-wraps to `widthPx` minus padding, lines centred, last line ellipsized; `labelSp` defaults to 9sp when >1 line (11sp for 1). `OsPixelText.render(..., center = true)` / `OsRemote.pixelText(..., bold, center)` for your own wrapped bitmaps |
| **Stretch slot (RemoteViews)** | `@layout/os_rv_inventory_slot_wide`: `match_parent` width (fills a GridView column / weighted cell), height wraps (min `os_slot_size`), `os_slot_bg` 9-patch stretches; same ids as `os_rv_inventory_slot` so `OsRemote.slot` fills both |
| **Chat lines** | `OsChat.text(ctx, speaker, message, speakerColor = black, messageColor = chat blue)` → spanned "Speaker: message"; Views: `OsChat.view(ctx, …)` / `OsChat.bind(textView, …)` (pixel font, no shadow, `os_chat_row` background, `maxLines`); RemoteViews: `@layout/os_rv_chat_line` + `OsChat.remote(ctx, views, speaker, message, widthPx, maxLines = 2)` (= `OsRemote.chatLine`). Put lines on `os_chatbox`. Pixel bitmaps keep span colours and a black shadow |
| **Parchment restyle** | XML: `android:theme="@style/ThemeOverlay.OldSchool.Parchment"` (ink text, no shadows, link-ink text buttons). Code / already-inflated views: `Os.parchmentize(view)` (recursive: yellow/white → ink, orange → link ink, cyan → chat blue; no shadows; Buttons/MaterialButtons → flat link-ink text buttons on the `os_chat_row` press highlight; EditText ink + brown hint; CheckBox/Radio ink). `Os.inkFor(ctx, color)` for single colours |
| **Bottom nav** | `<com.josh.oldschool.OsBottomNav app:menu="…" />` (a `BottomNavigationView` that defaults to `Widget.OldSchool.BottomNav` and keeps pixel icons untinted), or `style="@style/Widget.OldSchool.BottomNav"` on a plain `BottomNavigationView` **plus** `OsBottomNav.style(nav)` (Material ignores `itemIconTint="@null"` in styles). Same art as the side tabs: 50dp stone squares (`os_bottom_nav_item`), red when checked, darker when pressed, on `os_side_panel`, no ripple/indicator, unlabelled. `Widget.OldSchool.BottomNav.Labeled` / `OsBottomNav.style(nav, labeled = true)`: stone over icon + bold pixel label (white checked / orange). Monochrome vector icons: `app:itemIconTint="@color/os_bottom_nav_icon_tint"` |
| **Chips** | `style="@style/Widget.OldSchool.Chip"` (assist), `.Chip.Choice` (single-select, no check mark), `.Chip.Filter` (multi-select, pixel check mark): square stone face, black edge, orange bold pixel text; checked = red face + white text. In code: `OsChips.assist/choice/filter(ctx, text)` or `Chip(ContextThemeWrapper(ctx, R.style.ThemeOverlay_OldSchool_Chip_Choice), null, com.google.android.material.R.attr.chipStyle)`. Colours `os_chip_bg/os_chip_text/os_chip_stroke` |
| **Animation test hook** | `OsAnimations.freeze(0.5f)` holds every kit animation (XP drop(s), fireworks, timer orb) at 50%; `seek(f)` moves them; `release()` / `reset()` lets them finish. While frozen, banner/quest-scroll fades complete instantly and banners don't auto-dismiss; timer `onFinished` never fires. `OsAnimations.scale` (alias `Os.animationScale`): 0 = instant, 10 = slow motion. `heldCount`, `testMode`, `duration(ms)`; `OsAnimations.play(animator, freezable)` to put your own animators under the same switch. Always `OsAnimations.reset()` in `@After` |

Robolectric example:
```kotlin
@After fun tidy() = OsAnimations.reset()
@Test fun xpDropShot() {
    OsAnimations.freeze(0.4f)
    OsXpDrops.rightSide(activity, listOf(R.drawable.os_ic_book to 120))
    snap(activity)                       // drop is mid-rise, fully visible
}
```

### Widgets (RemoteViews) notes
Launchers can't load app fonts or custom views in widgets, so: pixel text = `OsRemote.pixelText(...)` (renders a
bitmap into an ImageView), answer states = swap `os_answer_*` backgrounds with
`setInt(id, "setBackgroundResource", OsRemote.answerBackground(result))`, checkboxes = swap `os_ic_check_on/off`.
Emoji in `os_rv_inventory_slot` stay a TextView (system emoji font works in widgets).
Single-frame widget recipe (as in Josh Notes 1.6): root background `os_widget_bg` (carved frame), title =
`OsPixelText.render(..., getColor(R.color.os_header), bold = true)`, header buttons get
`setInt(id, "setBackgroundResource", R.drawable.os_side_tab)`, list on `os_chatbox`, and a bottom row of ImageViews with
`os_chat_tab_up/down` backgrounds + pixel-text bitmaps (white = selected, orange = not) wired to PendingIntents.

## 3d. New in 1.3 (closer to the classic look)
All original art; same file names as before where a piece was redrawn.
- **Palette**: panel tile is now `#3E3529`-based; new colours `os_bevel_light/dark`, `os_text_shadow`,
  `os_menu_option(_hover)`, `os_chat_input(_hint)`, `os_chat_cyan/white`, `os_button_disabled_text`,
  `os_orb_green/red/cyan/yellow`. `os_menu_bg` is now opaque `#5D5447`.
- **Bevels & rivets**: `os_frame_carved(_filled)`, `os_frame_thin`, `os_frame(_filled)` redrawn: 1px light/dark
  bevel, engraved groove, recessed inner lip, domed 5x5 studs (`os_rivet`). Side panel strip is brown stone.
- **Text shadow & font**: `Os.fontPixel(textSizePx)`, `Os.crispShadow(tv)`, `Os.crispShadows(root, keep)` — hard
  black shadow offset by exactly one font pixel; `OsPixelText`/`OsRemote` bitmaps use the same offset.
  `TextAppearance.OldSchool.Yellow/Orange/Cyan/White/MenuHeader/MenuOption`. OS Small 1.3: hand-made bold
  N/M/W/V/X/Y/Z/K/m/w/v/x (bold N no longer reads as M) and accented é è à á ä ö ü ñ ç.
- **Tabs & icons**: chamfered side tabs (selected = deep red with glowing rim), 2-step chamfered filter tabs and
  chat tabs (selected = gold underline); re-shaded `os_ic_stats/scroll/quest/wrench`; new `os_ic_pack`,
  `os_ic_star`, `os_ic_bell`, `os_ic_gear`.
- **Chatbox**: lighter parchment `os_chat_tile`, thinner inner rule; `Widget.OldSchool.ChatInput` (blue text,
  `@drawable/os_chat_input` rule above).
- **Orbs**: `os_orb` ring lit from the top-left; stat orbs `@drawable/os_orb_stat_green/red/cyan/yellow`
  (`Widget.OldSchool.StatOrb`, `@dimen/os_orb_stat_size`; put the number to the left, like the classic orbs).
- **Right-click menu**: `OsDialogs.contextMenu(View)` = flat `#5D5447` panel, black outline, black "Choose Option"
  header in `#5D5447`, white options that turn yellow while pressed (`@color/os_menu_option_text`);
  `@drawable/os_menu_panel` 9-patch for your own menus.
- **Buttons**: `os_button` / `os_button_9` gain focused (gold inner line) and disabled (grey stone) states
  (`os_button_focused(_bg)`, `os_button_disabled`); `Widget.OldSchool.Button` text uses `@color/os_button_text`.
- **Scrollbar**: `os_scrollbar_thumb` (bevelled stone with grip) on `os_scrollbar_track`, set in `Theme.OldSchool`
  (`@dimen/os_scrollbar_size` 8dp) + `Widget.OldSchool.ScrollView` (never fades).

## 4. Regenerating art
`python3 tools/gen_art.py` (Pillow) rewrites `drawable-nodpi/*.png` (deterministic; same bytes every run) and the
9-patches/orb/thumb in `drawable-xxhdpi/` (`tools/gen_kit_parts.py`, then the v1.3 overrides/additions in
`tools/gen_v13.py`, which can also be run alone; 1 art pixel = 1dp, 9-patch guides added).
Bump `VERSION` + `CHANGELOG.md` for any visible change, then re-sync both apps.

## 5. Rules for contributors
Keep everything `os_`-prefixed and package-agnostic, original art only (no game assets, names or logos), and no
app-specific strings. Tests/showcase for the kit live in Josh Notes (`OldSchoolKitTest`, renders
`screens/22-kit-components.png` and `screens/23-kit-widget-pieces.png`) and `OldSchool11Test`
(`screens/25-kit-v11-pieces.png`) and `OldSchoolKit12Test` (`screens/26-kit-v12-pieces.png`) and `OldSchoolKit13Test` (`screens/28-kit-v13-pieces.png`). Font: `python3 tools/gen_font.py` (needs fontTools) rebuilds both TTFs from the glyph
table in the script.
