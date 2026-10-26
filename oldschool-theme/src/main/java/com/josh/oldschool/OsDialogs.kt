package com.josh.oldschool

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Dialog + context-menu components. Themed by ThemeOverlay.OldSchool.Dialog (set as materialAlertDialogTheme). */
object OsDialogs {
    /** MaterialAlertDialogBuilder with the bevelled stone panel background. */
    fun builder(ctx: Context): MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(ctx).apply {
        background = ContextCompat.getDrawable(ctx, R.drawable.os_panel)
    }

    /**
     * Classic right-click style menu: a dark "Choose Option" header and one row per option. [target] (optional) is
     * shown after each option in orange-yellow, e.g. "Open" + "photo.jpg". Tapping outside cancels.
     */
    fun contextMenu(ctx: Context, options: List<String>, target: String? = null, header: String = "Choose Option",
                    onPick: (Int) -> Unit): AlertDialog {
        lateinit var dlg: AlertDialog
        val list = contextMenuView(ctx, options, target, header) { dlg.dismiss(); onPick(it) }
        dlg = MaterialAlertDialogBuilder(ctx).setView(list).create()
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dlg
    }

    /** The menu panel on its own (embed it in a PopupWindow or your own dialog). */
    fun contextMenuView(ctx: Context, options: List<String>, target: String? = null, header: String = "Choose Option",
                        onPick: (Int) -> Unit): LinearLayout {
        val font = Os.font(ctx)
        val pad = Os.dpi(ctx, 10f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.os_context_menu)
            setPadding(Os.dpi(ctx, 3f), Os.dpi(ctx, 3f), Os.dpi(ctx, 3f), Os.dpi(ctx, 6f))
            addView(TextView(ctx).apply {
                text = header; typeface = font; textSize = 16f; setTextColor(ctx.getColor(R.color.os_menu_header_text))
                setBackgroundColor(Color.BLACK); setPadding(pad, pad / 2, pad, pad / 2)
            })
            options.forEachIndexed { i, o ->
                addView(TextView(ctx).apply {
                    typeface = font; textSize = 17f; setTextColor(Color.WHITE); Os.shadow(this, 1f)
                    text = if (target == null) o else android.text.SpannableStringBuilder(o).apply {
                        append(" ")
                        val s = length; append(target)
                        setSpan(android.text.style.ForegroundColorSpan(ctx.getColor(R.color.os_orange)), s, length, 0)
                    }
                    gravity = Gravity.CENTER_VERTICAL; minHeight = Os.dpi(ctx, 40f)
                    setPadding(pad, 0, pad, 0)
                    setBackgroundResource(R.drawable.os_menu_row)
                    setOnClickListener { onPick(i) }
                })
            }
        }
    }
}
