package com.josh.oldschool

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.IdRes

/**
 * Helpers for home-screen widgets (RemoteViews can't use custom views or app fonts):
 * pixel text is pre-rendered to bitmaps, every other piece is a plain drawable/layout from the kit.
 *
 * Layouts: os_rv_frame (header + list), os_rv_inventory_slot, os_rv_skill_tile, os_rv_progress, os_rv_banner,
 * os_rv_check_row, os_rv_answer. Drawables: os_widget_bg, os_widget_row, os_frame_filled, os_answer_* , os_slot,
 * os_ic_check_on/off, os_ic_widget_* .
 */
object OsRemote {
    /** Render [text] in the pixel font into ImageView [id] (falls back to hiding it if blank). */
    fun pixelText(ctx: Context, views: RemoteViews, @IdRes id: Int, text: CharSequence, sizeSp: Float, color: Int,
                  maxWidthPx: Int, maxLines: Int = 1, shadow: Boolean = true) {
        val bmp = runCatching { OsPixelText.render(ctx, text, sizeSp, color, maxWidthPx, maxLines, shadow) }.getOrNull()
        if (bmp == null) views.setViewVisibility(id, android.view.View.GONE)
        else { views.setViewVisibility(id, android.view.View.VISIBLE); views.setImageViewBitmap(id, bmp); views.setContentDescription(id, text) }
    }

    /** Fill an os_rv_inventory_slot. */
    fun slot(ctx: Context, views: RemoteViews, emoji: CharSequence, label: CharSequence?, count: Int = 0, widthPx: Int) {
        views.setTextViewText(R.id.os_slot_emoji, emoji)
        if (label.isNullOrEmpty()) views.setViewVisibility(R.id.os_slot_label, android.view.View.GONE)
        else pixelText(ctx, views, R.id.os_slot_label, label, 11f, ctx.getColor(R.color.os_yellow), widthPx)
        if (count > 1) pixelText(ctx, views, R.id.os_slot_count, OsInventorySlot.stackText(count), 11f, ctx.getColor(R.color.os_yellow), widthPx)
        else views.setViewVisibility(R.id.os_slot_count, android.view.View.GONE)
    }

    /** Fill an os_rv_skill_tile. */
    fun skill(ctx: Context, views: RemoteViews, iconRes: Int, level: Int, maxLevel: Int, label: CharSequence?, widthPx: Int) {
        views.setImageViewResource(R.id.os_skill_icon, iconRes)
        pixelText(ctx, views, R.id.os_skill_level, "$level/$maxLevel", 16f, ctx.getColor(R.color.os_yellow), widthPx)
        if (label.isNullOrEmpty()) views.setViewVisibility(R.id.os_skill_label, android.view.View.GONE)
        else pixelText(ctx, views, R.id.os_skill_label, label, 11f, ctx.getColor(R.color.os_cyan), widthPx)
    }

    /** Fill an os_rv_progress bar (0..max) with an optional pixel caption. */
    fun progress(ctx: Context, views: RemoteViews, value: Int, max: Int, caption: CharSequence?, widthPx: Int) {
        views.setProgressBar(R.id.os_progress, max.coerceAtLeast(1), value.coerceIn(0, max.coerceAtLeast(1)), false)
        if (caption.isNullOrEmpty()) views.setViewVisibility(R.id.os_progress_caption, android.view.View.GONE)
        else pixelText(ctx, views, R.id.os_progress_caption, caption, 11f, ctx.getColor(R.color.os_white), widthPx)
    }

    /** Answer-state drawable for an os_rv_answer row (no custom states in RemoteViews). */
    fun answerBackground(result: OsAnswerButton.Result): Int = when (result) {
        OsAnswerButton.Result.NONE -> R.drawable.os_answer_normal
        OsAnswerButton.Result.CORRECT -> R.drawable.os_answer_correct
        OsAnswerButton.Result.WRONG -> R.drawable.os_answer_wrong
    }
}
