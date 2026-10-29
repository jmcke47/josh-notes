# Old School theme kit — changelog

## 1.3.1 (2026-10-29)
Fix + small additions (reported by Learn Dev). Backwards compatible.
- Fix: `Os.applyScreenFrame(root)` drew its border over the right edge of full-width scroll views, hiding the
  always-visible scrollbar from 1.3.0. It now raises the right padding of any view with a vertical scrollbar that
  reaches under the border to the frame band (`@dimen/os_frame_band`), re-checked after every layout.
- New `Os.keepScrollbarsInsideFrame(view, borderPx = os_frame_band, recurse = true)` for frames you draw yourself.
- New per-view opt-out of the sharpened shadow: `Os.noSharpShadow(view[, off])` or
  `<tag android:id="@id/os_no_sharp_shadow" android:value="true" />`; applies to the view and its children.
- New ids `os_no_sharp_shadow`, `os_scrollbar_inset`. Tests: `OldSchoolKit131Test` in Josh Notes.

## 1.3.0 (2026-10-27)
Classic-look pass: closer to the 2007-era interface. Original art only. Backwards compatible (nothing removed or
renamed; redrawn pieces keep their names). See README 3d.
- Palette: `#3E3529` panel tile, lighter chatbox parchment, opaque `#5D5447` menu; new colours (bevel, shadow, menu
  option, chat input, disabled button text, orb wells).
- Frames: carved frames redrawn with 1px bevels, engraved groove, recessed lip and domed studs; side panel = stone.
- Text: `Os.fontPixel`, `Os.crispShadow`, `Os.crispShadows` (one-font-pixel hard black shadow; `applyScreenFrame`
  applies it and keeps it applied); pixel-text bitmaps use the same offset; `TextAppearance.OldSchool.Yellow/Orange/
  Cyan/White/MenuHeader/MenuOption`. Font OS Small 1.3: proper bold diagonals (N/M/W/V/X/Y/Z/K/m/w/v/x), accents.
- Tabs/icons: chamfered side, filter and chat tabs; re-shaded stats/scroll/quest/wrench; new pack/star/bell/gear.
- Chatbox: `Widget.OldSchool.ChatInput` + `os_chat_input`.
- Orbs: re-lit `os_orb`; `os_orb_stat_green/red/cyan/yellow`, `Widget.OldSchool.StatOrb`, `os_orb_stat_size`.
- Menu: flat panel, black header, white options turning yellow when pressed (`os_menu_option_text`), `os_menu_panel`.
- Buttons: focused/disabled states (`os_button_focused(_bg)`, `os_button_disabled`), `os_button_text` colours.
- Scrollbar: `os_scrollbar_thumb/track` in `Theme.OldSchool`, `Widget.OldSchool.ScrollView` (never fades),
  `os_scrollbar_size`.
- Tools: `tools/gen_v13.py` (run by `gen_art.py`). Tests: `OldSchoolKit13Test` in Josh Notes.

## 1.2.0 (2026-10-26)
Additions requested by Josh Learn (v1.4.2 had local fallbacks for these). Backwards compatible; no visual change to
existing pieces apart from the LinkInk shadow fix.
- `OsRemote.slot(..., labelMaxLines, labelSp)`: 2-line wrapped, centred slot labels; `OsPixelText.render(..., center)`,
  `OsRemote.pixelText(..., bold, center)`.
- `@layout/os_rv_inventory_slot_wide`: stretchable (match_parent) RemoteViews slot for widget grid columns.
- `OsChat` (text/view/bind/remote) + `@layout/os_rv_chat_line` + `OsRemote.chatLine`: coloured speaker + message on
  `os_chat_row`; `OsPixelText` keeps span colours with a black shadow.
- `Os.parchmentize(view)` / `Os.inkFor(ctx, color)`; `ThemeOverlay.OldSchool.Parchment` also sets button styles and
  edit-text colour; `Widget.OldSchool.Button.LinkInk` has no shadow.
- `Widget.OldSchool.BottomNav` (+ `.Labeled`), `OsBottomNav` view + `OsBottomNav.style(nav)`, drawables
  `os_bottom_nav_item(_fill)`, colours `os_bottom_nav_text`, `os_bottom_nav_icon_tint`, `TextAppearance.OldSchool.NavLabel`.
- `Widget.OldSchool.Chip` / `.Chip.Choice` / `.Chip.Filter`, `ThemeOverlay.OldSchool.Chip(.Choice/.Filter)`, `OsChips`,
  `TextAppearance.OldSchool.Chip`, colours `os_chip_bg/text/stroke`.
- `OsAnimations` test hook (freeze/seek/release/reset/scale, `Os.animationScale`); XP drops, banners, quest scroll,
  fireworks and timer orb all run through it.

## 1.1.0 (2026-10-26)
The 2007-era redesign. Original art only; backwards compatible with 1.0 APIs.
- Font: **OS Small** regular + bold, self-drawn pixel font (cap height 7px, legible small text), SIL OFL 1.1
  (`assets/licenses/os_font-OFL.txt`); theme font is now `@font/os_font`; `Os.font(ctx, bold)`.
- Frames: carved dark wood/stone 9-patches with bevelled edges and riveted corners (`os_frame_carved(_filled)`,
  `os_frame_thin`, `os_rivet`, `os_wood_tile`), `os_screen_frame` + `Os.applyScreenFrame(root)`; toolbar now wood + rivets.
- Chatbox list (`os_chatbox_carved`) with bottom chat tabs (`Widget.OldSchool.ChatTab`); side-panel strip of square stone
  tabs (`Widget.OldSchool.SideTab`, `os_side_panel`); new icons stats/quest/tag/mic/plus/memo.
- Colours: `os_header` #FFFF00 + `TextAppearance.OldSchool.Header` (1px black shadow), orange/cyan/white highlights,
  wood, dialog red/blue, quest red/yellow/green. Darker panel tile.
- Dialogs: `OsDialogs.chat` ("Click here to continue"), `OsDialogs.options` ("Select an Option"), `OsDialogs.alert` /
  `OsAlertBuilder` drop-in; context menu gets a Cancel row; long messages scroll.
- Stone buttons textured with an inverted bevel when pressed; dialog button style.
- `OsSkillTile` redesigned (recessed slot, level/base level) + `setTotal`; `OsXpDrops.rightSide` staggered right-edge drops.
- Minimap orbs `os_minimap_mic`, `os_minimap_plain`, `os_orb_new`.
- `OsPixelText.render/fitSize` gain `bold`; documented as the pixel-text-to-bitmap helper for RemoteViews.

## 1.0.0 (2026-10-26)
First shared release (extracted from Josh Notes v1.5.0; Josh Notes consumes it unchanged-looking).
- Tokens: `os_colors.xml`, `os_dimens.xml`, `os_attrs.xml`; full `Theme.OldSchool` (+ `.Translucent`), `ThemeOverlay.OldSchool.Parchment`, `ThemeOverlay.OldSchool.Dialog`, all `TextAppearance.OldSchool.*` / `.Ink.*`.
- Font: Pixelify Sans (`@font/os_pixelify_sans`) + SIL OFL 1.1 licence (`assets/licenses/os_PixelifySans-OFL.txt`).
- Drawables: stone/panel/parchment/chat textures, layer-list panels/buttons/tabs/toolbar/chatbox/scroll, 9-patch frames, buttons, tabs, answer states, slots, scroll roll; orb, seek thumb, checkbox, minimap FAB, hitsplat, icons.
- Components: OsXp, OsXpDrop, OsBanner (+ level-up), OsFireworks, OsQuestScroll, OsDialogs (stone dialog + "Choose Option" context menu), OsChiptune, OsPixelText, OsRemote.
- New for Josh Learn: OsLessonCard (parchment scroll with rolled ends), OsAnswerButton (normal/pressed/correct/wrong), OsTimerOrb (draining ring), OsInventorySlot (emoji + label + stack count), Widget.OldSchool.SeekBar (stone track + gold knob, `.Discrete` with ticks), OsSkillTile, HP/XP bars, banner, RemoteViews layouts `os_rv_*`.
