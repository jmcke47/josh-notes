package com.josh.notes.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.josh.notes.AppLock
import com.josh.notes.CheckItem
import com.josh.notes.EditNoteActivity
import com.josh.notes.Note
import com.josh.notes.NoteColors
import com.josh.notes.NoteStore
import com.josh.notes.R

class NotesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0))

    /** One list row: a note, or (single-checklist widgets) one checklist item. */
    sealed class Row {
        data class NoteRow(val note: Note) : Row()
        data class ItemRow(val note: Note, val index: Int, val item: CheckItem) : Row()
    }

    /**
     * Builds the list rows. Used two ways:
     *  - API 31+: [collection] turns every row into a RemoteCollectionItems entry that travels inside the widget's own
     *    RemoteViews (one bitmap cache for the whole hierarchy, ids remapped by the framework).
     *  - API 26-30: the classic RemoteViewsFactory (launcher pulls rows from this service).
     * Every row sets every view it owns (no state can leak between recycled rows), and the row's content and its
     * click fill-in are always built from the same [Row] object.
     */
    @androidx.annotation.VisibleForTesting
    internal class Factory(private val context: Context, private val widgetId: Int = 0, private val pixel: Boolean = true) : RemoteViewsFactory {
        @androidx.annotation.VisibleForTesting internal var rows: List<Row> = emptyList()
        /** Pixel-text bitmap bytes used so far; past [budget] rows fall back to plain text. */
        private var bitmapBytes = 0L
        private val budget = bitmapBudget(context)

        override fun onCreate() {}
        override fun onDataSetChanged() { rows = load(context, widgetId); bitmapBytes = 0 }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getLoadingView(): RemoteViews? = null
        /** Distinct row layouts across all themes (note row + checklist item row per theme). */
        override fun getViewTypeCount() = WidgetTheme.entries.flatMap { listOf(it.item, it.checkRow) }.distinct().size
        override fun getItemId(position: Int) = rows.getOrNull(position)?.let { rowId(it) } ?: -1L - position
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val theme = WidgetTheme.of(context)
            return when (val r = rows.getOrNull(position)) {
                is Row.ItemRow -> checkRow(context, theme, widgetId, r.note, r.index, r.item, rowInset = 20, pix = ::pix)
                is Row.NoteRow -> noteRow(theme, r.note)
                null -> RemoteViews(context.packageName, theme.item) // only if the launcher asks past the end
            }
        }

        /** Render pixel text unless disabled or the widget's bitmap budget is spent (then null = use plain text). */
        private fun pix(text: CharSequence, sp: Float, color: Int, w: Int, maxLines: Int = 1, strike: Boolean = false, bold: Boolean = false): android.graphics.Bitmap? {
            if (!pixel || bitmapBytes > budget) return null
            val b = runCatching { PixelText.render(context, text, sp, color, w, maxLines, shadow = false, strike = strike, bold = bold) }.getOrNull() ?: return null
            bitmapBytes += b.allocationByteCount
            return b
        }

        private fun noteRow(theme: WidgetTheme, n: Note): RemoteViews {
            val os = theme == WidgetTheme.OLDSCHOOL
            val rv = RemoteViews(context.packageName, theme.item)
            val locked = AppLock.enabled(context)
            val (done, total) = n.progress
            val showItems = n.checklist && !n.secret && !locked && total > 0
            val title = n.displayTitle.ifBlank { context.getString(R.string.untitled) } +
                if (n.checklist && !n.secret && total > 0) "  $done/$total" else ""
            val preview = if (locked || n.secret || n.checklist) "" else when {
                n.title.isBlank() -> n.body.lines().drop(1).joinToString("\n").trim()
                else -> n.body.trim()
            }
            val gone = View.GONE; val visible = View.VISIBLE
            rv.setViewVisibility(R.id.widget_item_bell, if (n.hasReminder) visible else gone)
            rv.setViewVisibility(R.id.widget_item_clip, if (n.attachments.isNotEmpty() && !n.secret) visible else gone)
            rv.setViewVisibility(R.id.widget_item_pin, if (n.pinned) visible else gone)
            if (n.color == 0) rv.setViewVisibility(R.id.widget_item_color, View.INVISIBLE)
            else {
                rv.setViewVisibility(R.id.widget_item_color, visible)
                rv.setInt(R.id.widget_item_color, "setColorFilter", NoteColors.palette[n.color])
            }
            if (os) {
                // Chatbox row in the pixel font (pre-rendered; launchers can't load app fonts).
                val dm = context.resources.displayMetrics
                val now = System.currentTimeMillis()
                val overdue = n.reminderAt in 1..now
                val icons = (if (n.pinned) 16 else 0) + (if (n.attachments.isNotEmpty()) 16 else 0) +
                    (if (n.hasReminder && !overdue) 16 else 0) + (if (overdue) 32 else 0)
                val rowW = ((WidgetTheme.widthDp(context, widgetId) - 44) * dm.density).toInt()
                val titleBmp = pix("$title:", 14f, context.getColor(R.color.os_ink), rowW - (icons * dm.density).toInt(), bold = true)
                rv.setImageViewBitmap(R.id.widget_item_title_img, titleBmp)
                rv.setViewVisibility(R.id.widget_item_title_img, if (titleBmp == null) gone else visible)
                rv.setTextViewText(R.id.widget_item_title, "$title:")
                rv.setViewVisibility(R.id.widget_item_title, if (titleBmp == null) visible else gone)
                val body = if (preview.isEmpty()) null
                    else pix(preview, 12f, context.getColor(R.color.os_chat_blue), rowW - ((if (overdue) 32 else 0) * dm.density).toInt(), maxLines = 2)
                rv.setImageViewBitmap(R.id.widget_item_body_img, body)
                rv.setViewVisibility(R.id.widget_item_body_img, if (body == null) gone else visible)
                rv.setTextViewText(R.id.widget_item_body, preview)
                rv.setViewVisibility(R.id.widget_item_body, if (body == null && preview.isNotEmpty()) visible else gone)
                rv.setViewVisibility(R.id.widget_item_hitsplat, if (overdue) visible else gone)
                if (overdue) {
                    rv.setTextViewText(R.id.widget_item_hitsplat_text, ((now - n.reminderAt) / 86_400_000L + 1).coerceAtMost(99).toString())
                    rv.setViewVisibility(R.id.widget_item_bell, gone)
                } else rv.setTextViewText(R.id.widget_item_hitsplat_text, "")
            } else {
                rv.setTextViewText(R.id.widget_item_title, title)
                rv.setTextViewText(R.id.widget_item_body, preview)
                rv.setViewVisibility(R.id.widget_item_body, if (preview.isEmpty()) gone else visible)
            }
            rv.setContentDescription(R.id.widget_item, if (preview.isEmpty()) title else "$title: $preview")

            // Checklist notes: the next few open items, tickable right here.
            rv.removeAllViews(R.id.widget_item_checks)
            val open = if (showItems) n.items.withIndex().filter { it.value.text.isNotBlank() && !it.value.checked }.take(MAX_INLINE) else emptyList()
            for ((i, item) in open) rv.addView(R.id.widget_item_checks, checkRow(context, theme, widgetId, n, i, item, rowInset = 44, pix = ::pix))
            rv.setViewVisibility(R.id.widget_item_checks, if (open.isEmpty()) gone else visible)

            rv.setOnClickFillInIntent(R.id.widget_item, Intent()
                .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_OPEN)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id))
            return rv
        }
    }

    companion object {
        const val MAX_INLINE = 3

        /** Stable, unique id for a row (note id, or note id * 1000 + item index for checklist items). */
        internal fun rowId(r: Row): Long = when (r) {
            is Row.NoteRow -> r.note.id
            is Row.ItemRow -> r.note.id * 1000 + r.index
        }

        /**
         * Half of the system's widget bitmap ceiling (6 x screen pixels, enforced for targetSdk 37), leaving room for
         * the header/tab bitmaps and other sizes.
         */
        internal fun bitmapBudget(context: Context): Long {
            val dm = context.resources.displayMetrics
            return 3L * dm.widthPixels * dm.heightPixels
        }

        /**
         * API 31+: all rows as RemoteCollectionItems, carried inside the widget's RemoteViews. This replaces the
         * RemoteViewsService path, whose rows' pixel-text bitmaps could be resolved against the wrong bitmap cache on
         * newer launchers/Android versions (rows showed other rows' or the tab bar's text, and taps hit a different
         * note than the one drawn).
         */
        @androidx.annotation.RequiresApi(31)
        internal fun collection(context: Context, widgetId: Int, pixel: Boolean = true): RemoteViews.RemoteCollectionItems {
            val f = Factory(context, widgetId, pixel).apply { onDataSetChanged() }
            val items = (0 until f.count).map { f.getItemId(it) to f.getViewAt(it) }
            val b = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true)
                .setViewTypeCount(items.map { it.second.layoutId }.distinct().size.coerceAtLeast(1))
            items.forEach { (id, v) -> b.addItem(id, v) }
            return b.build()
        }

        internal fun load(context: Context, widgetId: Int): List<Row> {
            val opts = WidgetOptions.get(context, widgetId)
            val notes = NoteStore.all(context)
            if (opts.mode == WidgetOptions.Mode.CHECKLIST) {
                val n = opts.checklist(context) ?: return emptyList()
                if (n.secret || AppLock.enabled(context)) return emptyList()
                return n.items.withIndex().filter { it.value.text.isNotBlank() }.map { Row.ItemRow(n, it.index, it.value) }
            }
            return opts.filter(notes).map { Row.NoteRow(it) }
        }

        /** A tickable item (themed; Old School uses the stone checkbox + pixel text). */
        internal fun checkRow(context: Context, theme: WidgetTheme, widgetId: Int, n: Note, index: Int, item: CheckItem, rowInset: Int,
                              pix: ((CharSequence, Float, Int, Int, Int, Boolean, Boolean) -> android.graphics.Bitmap?)? = null): RemoteViews {
            val rv = RemoteViews(context.packageName, theme.checkRow)
            rv.setImageViewResource(R.id.widget_check_box, if (item.checked) theme.checkOn else theme.checkOff)
            if (theme == WidgetTheme.OLDSCHOOL) {
                val dm = context.resources.displayMetrics
                val w = ((WidgetTheme.widthDp(context, widgetId) - rowInset - 40) * dm.density).toInt()
                val color = context.getColor(if (item.checked) R.color.os_ink_soft else R.color.os_chat_blue)
                val bmp = if (pix != null) pix(item.text, 13f, color, w, 2, item.checked, false)
                    else runCatching { PixelText.render(context, item.text, 13f, color, w, maxLines = 2, shadow = false, strike = item.checked) }.getOrNull()
                rv.setImageViewBitmap(R.id.widget_check_text_img, bmp)
                rv.setViewVisibility(R.id.widget_check_text_img, if (bmp == null) View.GONE else View.VISIBLE)
                rv.setTextViewText(R.id.widget_check_text, item.text)
                rv.setTextColor(R.id.widget_check_text, color)
                rv.setViewVisibility(R.id.widget_check_text, if (bmp == null) View.VISIBLE else View.GONE)
            } else {
                rv.setTextViewText(R.id.widget_check_text, item.text)
                rv.setFloat(R.id.widget_check_text, "setAlpha", if (item.checked) 0.55f else 1f)
            }
            rv.setContentDescription(R.id.widget_check_row, (if (item.checked) "Done: " else "To do: ") + item.text)
            rv.setOnClickFillInIntent(R.id.widget_check_row, Intent()
                .putExtra(WidgetActionActivity.EXTRA_ACTION, WidgetActionActivity.ACTION_TOGGLE)
                .putExtra(EditNoteActivity.EXTRA_NOTE_ID, n.id)
                .putExtra(WidgetActionActivity.EXTRA_INDEX, index)
                .putExtra(WidgetActionActivity.EXTRA_TEXT, item.text))
            return rv
        }
    }
}
