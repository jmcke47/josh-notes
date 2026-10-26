package com.josh.oldschool

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IdRes

/**
 * Chatbox lines: a coloured speaker name, then the message ("Guide: Welcome to the lesson!"), on the parchment
 * os_chat_row strip (1px divider, darker while pressed). Defaults follow the classic chatbox: black name, blue message.
 *
 * Views: `OsChat.view(ctx, "Guide", "Welcome!")` or `OsChat.bind(textView, ...)`.
 * RemoteViews: inflate `@layout/os_rv_chat_line` and call `OsChat.remote(ctx, views, "Guide", "Welcome!", widthPx)`
 * (or [OsRemote.chatLine]).
 */
object OsChat {
    /** "speaker: message" with the two colours as spans. Blank [speaker] = message only. */
    @JvmStatic @JvmOverloads
    fun text(ctx: Context, speaker: CharSequence?, message: CharSequence,
             @ColorInt speakerColor: Int = ctx.getColor(R.color.os_ink),
             @ColorInt messageColor: Int = ctx.getColor(R.color.os_chat_blue),
             separator: String = ": "): CharSequence {
        val b = SpannableStringBuilder()
        if (!speaker.isNullOrBlank()) {
            b.append(speaker).append(separator)
            b.setSpan(ForegroundColorSpan(speakerColor), 0, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val start = b.length
        b.append(message)
        b.setSpan(ForegroundColorSpan(messageColor), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return b
    }

    /** Style an existing TextView as a chat line. */
    @JvmStatic @JvmOverloads
    fun bind(tv: TextView, speaker: CharSequence?, message: CharSequence,
             @ColorInt speakerColor: Int = tv.context.getColor(R.color.os_ink),
             @ColorInt messageColor: Int = tv.context.getColor(R.color.os_chat_blue),
             sizeSp: Float = 15f, maxLines: Int = Int.MAX_VALUE): TextView = tv.apply {
        text = text(context, speaker, message, speakerColor, messageColor)
        typeface = Os.font(context); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setShadowLayer(0f, 0f, 0f, 0); gravity = Gravity.CENTER_VERTICAL or Gravity.START
        this.maxLines = maxLines; if (maxLines != Int.MAX_VALUE) ellipsize = android.text.TextUtils.TruncateAt.END
        setBackgroundResource(R.drawable.os_chat_row)
        val h = Os.dpi(context, 8f); val v = Os.dpi(context, 4f); setPadding(h, v, h, v)
        minHeight = Os.dpi(context, 28f)
        contentDescription = if (speaker.isNullOrBlank()) message else "$speaker: $message"
    }

    /** New chat-line TextView. */
    @JvmStatic @JvmOverloads
    fun view(ctx: Context, speaker: CharSequence?, message: CharSequence,
             @ColorInt speakerColor: Int = ctx.getColor(R.color.os_ink),
             @ColorInt messageColor: Int = ctx.getColor(R.color.os_chat_blue),
             sizeSp: Float = 15f, maxLines: Int = Int.MAX_VALUE): TextView =
        bind(TextView(ctx), speaker, message, speakerColor, messageColor, sizeSp, maxLines)

    /** Fill an os_rv_chat_line (pixel-text bitmap, wraps up to [maxLines]). */
    @JvmStatic @JvmOverloads
    fun remote(ctx: Context, views: RemoteViews, speaker: CharSequence?, message: CharSequence, widthPx: Int,
               @ColorInt speakerColor: Int = ctx.getColor(R.color.os_ink),
               @ColorInt messageColor: Int = ctx.getColor(R.color.os_chat_blue),
               sizeSp: Float = 13f, maxLines: Int = 2, @IdRes textId: Int = R.id.os_chat_line_text) {
        val pad = Os.dpi(ctx, 16f)
        OsRemote.pixelText(ctx, views, textId, text(ctx, speaker, message, speakerColor, messageColor), sizeSp,
            messageColor, (widthPx - pad).coerceAtLeast(1), maxLines, shadow = false)
        views.setContentDescription(textId, if (speaker.isNullOrBlank()) message else "$speaker: $message")
    }
}
