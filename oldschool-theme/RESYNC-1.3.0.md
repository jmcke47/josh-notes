# Josh Learn: re-sync guide for Old School kit 1.3.0

Josh Learn should be on kit 1.2.0 already. 1.3.0 is **drop-in**: nothing was removed or renamed, so it should build with
no code changes. Most of the changes are visual.

## Steps
1. Re-sync the kit: `/workspace/shared/oldschool-theme/tools/sync-into.sh /workspace/learn-app`
   (this replaces only `learn-app/oldschool-theme/`; `.kit-version` becomes `1.3.0`).
2. Check for app-side copies that override the kit. App resources with the same name win, and you'd lose the new
   look: `grep -rn 'name="os_\|/os_' app/src/main/res | grep -v '@'`. Then delete any local `os_*` drawables or colours,
   especially `os_menu_bg`, `os_context_menu`, `os_button*`, `os_frame_carved*`, `os_orb*`, `os_side_tab*`,
   `os_chat_tile`, `os_panel_tile`.
3. Build and run the tests/screenshots, then look at the screens listed under "What to look at".
4. Commit "Adopt Old School kit 1.3.0" and release as usual.

## What changes without any code
- Panels are the classic `#3E3529` brown. Carved frames have a groove, a recessed lip and domed corner studs. The
  side-panel strip is stone, not wood.
- Side tabs, filter tabs and chat tabs have chamfered corners. A selected side tab is deep red with a glowing rim.
- The "Choose Option" menu (`OsDialogs.contextMenu`) is a flat `#5D5447` panel with a black header. Options are white
  and turn yellow while pressed.
- `Widget.OldSchool.Button` and `os_button` / `os_button_9` gain focused (gold line) and disabled (grey stone) looks.
  The text colour comes from `@color/os_button_text`.
- Every `ScrollView` gets an always-visible stone scrollbar (via `android:scrollViewStyle` in `Theme.OldSchool`).
  To keep a fading one, put `android:fadeScrollbars="true"` on that view.
- `Os.applyScreenFrame(root)` also snaps shadowed text to a hard black shadow of exactly one font pixel, including
  rows added later. Text with no shadow (parchment/ink) is untouched.
- Widget pixel-text bitmaps (`OsPixelText`, `OsRemote.*`) use the same one-font-pixel shadow.
- The OS Small font has proper bold N/M/W/V/X/Y/Z/K/m/w/v/x (bold "N" used to look like "M") plus é è à á ä ö ü ñ ç.
- Redrawn: `os_orb` (timer orb ring), the chatbox parchment, and the `os_ic_stats/scroll/quest/wrench` icons.

## New things you can use (optional)
| What | Name |
|---|---|
| One-font-pixel shadow | `Os.fontPixel(px)`, `Os.crispShadow(tv)`, `Os.crispShadows(root, keep = false)` |
| Shadowed text colours | `TextAppearance.OldSchool.Yellow / Orange / Cyan / White` |
| Menu text styles | `TextAppearance.OldSchool.MenuHeader / MenuOption`, `@color/os_menu_option_text` |
| Menu panel for custom popups | `@drawable/os_menu_panel` (9-patch) |
| Stat orbs (HP/run-style) | `@drawable/os_orb_stat_green / red / cyan / yellow`, `Widget.OldSchool.StatOrb`, `@dimen/os_orb_stat_size` |
| Chat input line | `Widget.OldSchool.ChatInput` (EditText), `@drawable/os_chat_input`, `@color/os_chat_input(_hint)` |
| Button states | `@drawable/os_button_focused`, `os_button_focused_bg`, `os_button_disabled`, `@color/os_button_text` |
| Scrollbar | `@drawable/os_scrollbar_thumb`, `os_scrollbar_track`, `@dimen/os_scrollbar_size`, `Widget.OldSchool.ScrollView` |
| Icons | `@drawable/os_ic_pack`, `os_ic_star`, `os_ic_bell`, `os_ic_gear` |
| Colours | `os_bevel_light/dark`, `os_text_shadow`, `os_chat_cyan/white`, `os_orb_green/red/cyan/yellow` |

## Things to check in Learn
- **Quiz answer buttons / disabled states:** if Learn disables `Widget.OldSchool.Button`s, they now show the grey
  disabled stone with dim text instead of a darkened overlay.
- **Code that hard-sets `Color.WHITE` on menu rows:** use `os_menu_option_text` instead to keep the yellow press.
- **Lesson ScrollViews:** their scrollbar is now always visible. That's intended; opt out per view if needed.
- **Custom `setShadowLayer` calls:** on screens with `applyScreenFrame`, any black shadow is snapped to one font
  pixel after layout. To keep a custom offset, use a non-black shadow colour or don't call `applyScreenFrame` there.

Reference screenshots (rendered in Josh Notes): `/workspace/notes-app/screens/28-kit-v13-pieces.png` and
`/workspace/notes-app/screens/kit13-before-after.png`.
