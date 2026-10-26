package com.josh.oldschool

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog components:
 *  - [contextMenu]: the "Choose Option" long-press menu (ends with "Cancel").
 *  - [chat]: chatbox-style info message with "Click here to continue".
 *  - [options]: chatbox-style "Select an Option" list for confirmations.
 *  - [alert]: a drop-in MaterialAlertDialogBuilder that turns simple title/message/button dialogs into [chat] or
 *    [options] automatically, and gives the rest (custom views, lists) the bevelled stone panel.
 *  - [builder]: plain MaterialAlertDialogBuilder with the stone panel.
 */
object OsDialogs {
    /** MaterialAlertDialogBuilder with the bevelled stone panel background. */
    fun builder(ctx: Context): MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(ctx).apply {
        background = ContextCompat.getDrawable(ctx, R.drawable.os_panel)
    }

    /** Drop-in builder: simple dialogs become chatbox dialogs (see [OsAlertBuilder]). */
    fun alert(ctx: Context): MaterialAlertDialogBuilder = OsAlertBuilder(ctx)

    /**
     * Classic right-click style menu: a black "Choose Option" header, one row per option and a final "Cancel".
     * [target] (optional) is shown after each option in [targetColor] (orange by default; cyan for objects, yellow for
     * people in the classic convention), e.g. "Open" + "photo.jpg". Tapping outside cancels.
     */
    fun contextMenu(ctx: Context, options: List<String>, target: String? = null, header: String = "Choose Option",
                    cancel: Boolean = true, targetColor: Int = ctx.getColor(R.color.os_orange), onPick: (Int) -> Unit): AlertDialog {
        lateinit var dlg: AlertDialog
        val list = contextMenuView(ctx, options, target, header, cancel, targetColor) { i -> dlg.dismiss(); if (i >= 0) onPick(i) }
        dlg = AlertDialog.Builder(ctx).setView(list).create()
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dlg
    }

    /** The menu panel on its own (embed it in a PopupWindow or your own dialog). "Cancel" reports -1. */
    fun contextMenuView(ctx: Context, options: List<String>, target: String? = null, header: String = "Choose Option",
                        cancel: Boolean = true, targetColor: Int = ctx.getColor(R.color.os_orange),
                        onPick: (Int) -> Unit): LinearLayout {
        val pad = Os.dpi(ctx, 10f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.os_context_menu)
            setPadding(Os.dpi(ctx, 3f), Os.dpi(ctx, 3f), Os.dpi(ctx, 3f), Os.dpi(ctx, 6f))
            addView(TextView(ctx).apply {
                text = header; typeface = Os.font(ctx, bold = true); textSize = 16f; setTextColor(ctx.getColor(R.color.os_menu_header_text))
                setBackgroundColor(Color.BLACK); setPadding(pad, pad / 2, pad, pad / 2)
            })
            val rows = options.mapIndexed { i, o -> i to o } + if (cancel) listOf(-1 to "Cancel") else emptyList()
            rows.forEach { (i, o) ->
                addView(TextView(ctx).apply {
                    typeface = Os.font(ctx, bold = true); textSize = 17f; setTextColor(Color.WHITE); Os.shadow(this, 1f)
                    text = if (target == null || i < 0) o else android.text.SpannableStringBuilder(o).apply {
                        append(" ")
                        val s = length; append(target)
                        setSpan(android.text.style.ForegroundColorSpan(targetColor), s, length, 0)
                    }
                    gravity = Gravity.CENTER_VERTICAL; minHeight = Os.dpi(ctx, 40f)
                    setPadding(pad, 0, pad, 0)
                    setBackgroundResource(R.drawable.os_menu_row)
                    setOnClickListener { onPick(i) }
                })
            }
        }
    }

    private fun chatDialog(ctx: Context, content: View, cancelable: Boolean): AlertDialog {
        val d = AlertDialog.Builder(ctx).setView(content).setCancelable(cancelable).create()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { y = Os.dpi(ctx, 72f) }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.25f); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        return d
    }

    private fun line(ctx: Context, t: CharSequence, color: Int, size: Float, bold: Boolean = false) = TextView(ctx).apply {
        text = t; setTextColor(color); textSize = size; typeface = Os.font(ctx, bold); gravity = Gravity.CENTER
        setShadowLayer(0f, 0f, 0f, 0)
    }

    /** Message line; long text (licences, changelogs) scrolls inside a box capped at ~45% of the screen. */
    private fun message(ctx: Context, m: CharSequence, top: Int, bottom: Int): View {
        val tv = line(ctx, m, Color.BLACK, 15f).apply { setPadding(0, top, 0, bottom) }
        if (m.length < 320) return tv
        tv.gravity = Gravity.START
        val h = (ctx.resources.displayMetrics.heightPixels * 0.45f).toInt()
        return android.widget.ScrollView(ctx).apply {
            addView(tv); isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        }
    }

    /** The chatbox panel: optional icon on the left, a column of lines on the right. */
    fun chatPanel(ctx: Context, @DrawableRes icon: Int, build: LinearLayout.() -> Unit): LinearLayout {
        val pad = Os.dpi(ctx, 16f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.os_chatbox_carved); setPadding(pad, pad, pad, pad)
            minimumHeight = Os.dpi(ctx, 120f)
            if (icon != 0) addView(ImageView(ctx).apply { setImageResource(icon) },
                LinearLayout.LayoutParams(Os.dpi(ctx, 44f), Os.dpi(ctx, 44f)).apply { marginEnd = pad / 2 })
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; build() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Info message: [title] (dark blue, bold), [message] (black), "Click here to continue" (blue). Tap to continue. */
    fun chat(ctx: Context, title: CharSequence?, message: CharSequence?, @DrawableRes icon: Int = 0,
             continueText: String = "Click here to continue", cancelable: Boolean = true,
             onContinue: (() -> Unit)? = null): AlertDialog {
        lateinit var d: AlertDialog
        val panel = chatPanel(ctx, icon) {
            if (!title.isNullOrBlank()) addView(line(ctx, title, ctx.getColor(R.color.os_chat_blue_dark), 17f, bold = true))
            if (!message.isNullOrBlank()) addView(message(ctx, message, Os.dpi(ctx, 4f), 0))
            addView(line(ctx, continueText, ctx.getColor(R.color.os_dialog_blue), 15f, bold = true).apply {
                setPadding(0, Os.dpi(ctx, 8f), 0, 0); tag = "os_continue"
            })
        }
        panel.setOnClickListener { d.dismiss(); onContinue?.invoke() }
        panel.isClickable = true
        d = chatDialog(ctx, panel, cancelable)
        return d
    }

    /**
     * Confirmation: optional [message] (black), "Select an Option" (dark red), then [options] in black (white while
     * pressed). Reports the picked index.
     */
    fun options(ctx: Context, message: CharSequence?, options: List<String>, title: String = "Select an Option",
                @DrawableRes icon: Int = 0, cancelable: Boolean = true, onPick: (Int) -> Unit): AlertDialog {
        lateinit var d: AlertDialog
        val panel = chatPanel(ctx, icon) {
            if (!message.isNullOrBlank()) addView(message(ctx, message, 0, Os.dpi(ctx, 6f)))
            addView(line(ctx, title, ctx.getColor(R.color.os_dialog_red), 16f, bold = true))
            options.forEachIndexed { i, o ->
                addView(line(ctx, o, Color.BLACK, 16f).apply {
                    minHeight = Os.dpi(ctx, 36f); setPadding(Os.dpi(ctx, 8f), 0, Os.dpi(ctx, 8f), 0)
                    setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                        intArrayOf(Color.WHITE, Color.BLACK)))
                    setBackgroundResource(R.drawable.os_menu_row)
                    setOnClickListener { d.dismiss(); onPick(i) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        d = chatDialog(ctx, panel, cancelable)
        return d
    }
}

/**
 * MaterialAlertDialogBuilder that renders simple dialogs the classic way:
 *  - title/message + one button  -> [OsDialogs.chat] ("Click here to continue" = the positive/only button)
 *  - title/message + 2-3 buttons -> [OsDialogs.options] (button labels become "Select an Option" choices)
 *  - anything with a custom view or list -> normal dialog on the stone panel with stone buttons.
 * Listeners receive the real DialogInterface and the usual BUTTON_* constant.
 */
open class OsAlertBuilder(ctx: Context) : MaterialAlertDialogBuilder(ctx) {
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private class Btn(val text: CharSequence, val which: Int, val l: DialogInterface.OnClickListener?)
    private val buttons = mutableMapOf<Int, Btn>()
    private var custom = false
    private var cancelableFlag = true
    private var dismiss: DialogInterface.OnDismissListener? = null
    private var cancelL: DialogInterface.OnCancelListener? = null

    init { background = ContextCompat.getDrawable(ctx, R.drawable.os_panel) }

    private fun str(id: Int): CharSequence = context.getText(id)

    override fun setTitle(t: CharSequence?): MaterialAlertDialogBuilder { title = t; return super.setTitle(t) }
    override fun setTitle(id: Int): MaterialAlertDialogBuilder { title = str(id); return super.setTitle(id) }
    override fun setMessage(m: CharSequence?): MaterialAlertDialogBuilder { message = m; return super.setMessage(m) }
    override fun setMessage(id: Int): MaterialAlertDialogBuilder { message = str(id); return super.setMessage(id) }
    override fun setPositiveButton(t: CharSequence?, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder {
        t?.let { buttons[DialogInterface.BUTTON_POSITIVE] = Btn(it, DialogInterface.BUTTON_POSITIVE, l) }; return super.setPositiveButton(t, l) }
    override fun setPositiveButton(id: Int, l: DialogInterface.OnClickListener?) = setPositiveButton(str(id), l)
    override fun setNegativeButton(t: CharSequence?, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder {
        t?.let { buttons[DialogInterface.BUTTON_NEGATIVE] = Btn(it, DialogInterface.BUTTON_NEGATIVE, l) }; return super.setNegativeButton(t, l) }
    override fun setNegativeButton(id: Int, l: DialogInterface.OnClickListener?) = setNegativeButton(str(id), l)
    override fun setNeutralButton(t: CharSequence?, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder {
        t?.let { buttons[DialogInterface.BUTTON_NEUTRAL] = Btn(it, DialogInterface.BUTTON_NEUTRAL, l) }; return super.setNeutralButton(t, l) }
    override fun setNeutralButton(id: Int, l: DialogInterface.OnClickListener?) = setNeutralButton(str(id), l)
    override fun setCancelable(c: Boolean): MaterialAlertDialogBuilder { cancelableFlag = c; return super.setCancelable(c) }
    override fun setOnDismissListener(l: DialogInterface.OnDismissListener?): MaterialAlertDialogBuilder { dismiss = l; return super.setOnDismissListener(l) }
    override fun setOnCancelListener(l: DialogInterface.OnCancelListener?): MaterialAlertDialogBuilder { cancelL = l; return super.setOnCancelListener(l) }
    override fun setView(v: View?): MaterialAlertDialogBuilder { custom = true; return super.setView(v) }
    override fun setView(id: Int): MaterialAlertDialogBuilder { custom = true; return super.setView(id) }
    override fun setCustomTitle(v: View?): MaterialAlertDialogBuilder { custom = true; return super.setCustomTitle(v) }
    override fun setItems(items: Array<out CharSequence>?, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setItems(items, l) }
    override fun setItems(id: Int, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setItems(id, l) }
    override fun setAdapter(a: android.widget.ListAdapter?, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setAdapter(a, l) }
    override fun setSingleChoiceItems(items: Array<out CharSequence>?, checked: Int, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setSingleChoiceItems(items, checked, l) }
    override fun setSingleChoiceItems(id: Int, checked: Int, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setSingleChoiceItems(id, checked, l) }
    override fun setSingleChoiceItems(a: android.widget.ListAdapter?, checked: Int, l: DialogInterface.OnClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setSingleChoiceItems(a, checked, l) }
    override fun setMultiChoiceItems(items: Array<out CharSequence>?, checked: BooleanArray?, l: DialogInterface.OnMultiChoiceClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setMultiChoiceItems(items, checked, l) }
    override fun setMultiChoiceItems(id: Int, checked: BooleanArray?, l: DialogInterface.OnMultiChoiceClickListener?): MaterialAlertDialogBuilder { custom = true; return super.setMultiChoiceItems(id, checked, l) }

    /** True when [create] will render a chatbox dialog. */
    val isChat: Boolean get() = !custom && (title != null || message != null) && buttons.size <= 3

    override fun create(): AlertDialog {
        if (!isChat) return super.create()
        val ordered = listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE).mapNotNull { buttons[it] }
        lateinit var d: AlertDialog
        d = if (ordered.size <= 1) {
            val b = ordered.firstOrNull()
            OsDialogs.chat(context, title, message, cancelable = cancelableFlag) { b?.l?.onClick(d, b.which) }
        } else {
            val text = listOfNotNull(title?.takeIf { it.isNotBlank() }, message?.takeIf { it.isNotBlank() }).joinToString("\n")
            OsDialogs.options(context, text, ordered.map { it.text.toString() }, cancelable = cancelableFlag) { i ->
                ordered[i].l?.onClick(d, ordered[i].which)
            }
        }
        d.setOnDismissListener(dismiss); cancelL?.let { d.setOnCancelListener(it) }
        return d
    }
}
