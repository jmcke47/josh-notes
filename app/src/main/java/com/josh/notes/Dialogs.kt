package com.josh.notes

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.josh.oldschool.OsDialogs

/**
 * Alert dialogs. In the Old School theme simple info/confirm dialogs become chatbox dialogs ("Click here to
 * continue" / "Select an Option"); dialogs with custom views or lists get the stone panel with stone buttons.
 */
fun osDialog(ctx: Context): MaterialAlertDialogBuilder =
    if (AppSettings.oldSchool(ctx)) OsDialogs.alert(ctx) else MaterialAlertDialogBuilder(ctx)

/** Long-press option lists: the kit's "Choose Option" context menu in Old School, a plain list dialog otherwise. */
/** Long-press style menu. Old School: "Choose Option" menu with [title] as the orange target after each option
 *  (pass [target] = false for menus whose options already name their target). */
fun osOptions(ctx: Context, title: String, options: List<String>, target: Boolean = true, onPick: (Int) -> Unit) {
    if (AppSettings.oldSchool(ctx)) OsDialogs.contextMenu(ctx, options, target = title.takeIf { target }, onPick = onPick).show()
    else MaterialAlertDialogBuilder(ctx).setTitle(title).setItems(options.toTypedArray()) { _, w -> onPick(w) }.show()
}
