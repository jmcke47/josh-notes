# Old School theme kit (`oldschool-theme`) — v1.1.0

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

### Widgets (RemoteViews) notes
Launchers can't load app fonts or custom views in widgets, so: pixel text = `OsRemote.pixelText(...)` (renders a
bitmap into an ImageView), answer states = swap `os_answer_*` backgrounds with
`setInt(id, "setBackgroundResource", OsRemote.answerBackground(result))`, checkboxes = swap `os_ic_check_on/off`.
Emoji in `os_rv_inventory_slot` stay a TextView (system emoji font works in widgets).
Single-frame widget recipe (as in Josh Notes 1.6): root background `os_widget_bg` (carved frame), title =
`OsPixelText.render(..., getColor(R.color.os_header), bold = true)`, header buttons get
`setInt(id, "setBackgroundResource", R.drawable.os_side_tab)`, list on `os_chatbox`, and a bottom row of ImageViews with
`os_chat_tab_up/down` backgrounds + pixel-text bitmaps (white = selected, orange = not) wired to PendingIntents.

## 4. Regenerating art
`python3 tools/gen_art.py` (Pillow) rewrites `drawable-nodpi/*.png` (deterministic; same bytes every run) and the
9-patches/orb/thumb in `drawable-xxhdpi/` (`tools/gen_kit_parts.py`; 1 art pixel = 1dp, 9-patch guides added).
Bump `VERSION` + `CHANGELOG.md` for any visible change, then re-sync both apps.

## 5. Rules for contributors
Keep everything `os_`-prefixed and package-agnostic, original art only (no game assets, names or logos), and no
app-specific strings. Tests/showcase for the kit live in Josh Notes (`OldSchoolKitTest`, renders
`screens/22-kit-components.png` and `screens/23-kit-widget-pieces.png`) and `OldSchool11Test`
(`screens/25-kit-v11-pieces.png`). Font: `python3 tools/gen_font.py` (needs fontTools) rebuilds both TTFs from the glyph
table in the script.
