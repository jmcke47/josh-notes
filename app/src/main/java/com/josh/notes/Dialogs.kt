package com.josh.notes

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.josh.oldschool.OsDialogs

/** Alert dialogs; in the Old School theme they get the kit's bevelled stone panel. */
fun osDialog(ctx: Context): MaterialAlertDialogBuilder =
    if (AppSettings.oldSchool(ctx)) OsDialogs.builder(ctx) else MaterialAlertDialogBuilder(ctx)

/** Long-press option lists: the kit's "Choose Option" context menu in Old School, a plain list dialog otherwise. */
fun osOptions(ctx: Context, title: String, options: List<String>, onPick: (Int) -> Unit) {
    if (AppSettings.oldSchool(ctx)) OsDialogs.contextMenu(ctx, options, target = title, onPick = onPick).show()
    else MaterialAlertDialogBuilder(ctx).setTitle(title).setItems(options.toTypedArray()) { _, w -> onPick(w) }.show()
}
