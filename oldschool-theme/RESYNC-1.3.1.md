# Josh Learn: re-sync guide for Old School kit 1.3.1

This is a drop-in fix on top of 1.3.0. Nothing was removed or renamed.

## Steps
1. `/workspace/shared/oldschool-theme/tools/sync-into.sh /workspace/learn-app` (`.kit-version` becomes `1.3.1`).
2. Delete any local workaround for the hidden scrollbar (extra `paddingEnd`, or a custom inset on full-width
   ScrollViews). The kit handles it now.
3. Build, run the tests, check a long scrolling screen in Old School, then commit "Adopt Old School kit 1.3.1".

## What changed
- **Fix:** `Os.applyScreenFrame(root)` no longer hides scrollbars. After every layout, any view with a vertical
  scrollbar whose right edge reaches under the frame border gets its right padding raised to `@dimen/os_frame_band`
  (7dp), so the inside-overlay scrollbar is drawn just left of the border. Views that already have enough right padding
  are not changed. Content in those views loses up to 7dp on the right, which the border covered anyway.
- **New:** `Os.keepScrollbarsInsideFrame(view, borderPx = os_frame_band, recurse = true)` does the same for frames
  you draw yourself (for example a carved panel around a list). It is idempotent and remembers the original padding.
- **New:** a per-view opt-out of the sharpened one-font-pixel shadow. It applies to the view and all of its children:
  - Code: `Os.noSharpShadow(view)` (undo with `Os.noSharpShadow(view, off = false)`)
  - XML: `<tag android:id="@id/os_no_sharp_shadow" android:value="true" />` inside the view element
- **New ids:** `os_no_sharp_shadow`, `os_scrollbar_inset` (internal).

Reference screenshot: `/workspace/notes-app/screens/31-scrollbar-visible.png`.
