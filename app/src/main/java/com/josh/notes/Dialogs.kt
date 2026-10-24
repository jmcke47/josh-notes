package com.josh.notes

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Alert dialogs; in the Old School theme they get the bevelled stone panel. */
fun osDialog(ctx: Context): MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(ctx).apply {
    if (AppSettings.oldSchool(ctx)) background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.os_panel)
}
