# Old School theme kit — changelog

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
