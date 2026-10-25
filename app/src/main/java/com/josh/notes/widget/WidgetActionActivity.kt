package com.josh.notes.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.josh.notes.Checklists
import com.josh.notes.EditNoteActivity

/**
 * Invisible trampoline for taps on widget list rows: open the note, or tick/untick a checklist item in place (the
 * store change refreshes every widget straight away). Uses Theme.NoDisplay and finishes immediately.
 */
class WidgetActionActivity : Activity() {
    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_INDEX = "item_index"
        const val EXTRA_TEXT = "item_text"
        const val ACTION_OPEN = "open"
        const val ACTION_TOGGLE = "toggle"

        /** Returns true if handled without opening the app. */
        fun handle(ctx: Context, intent: Intent): Boolean {
            val id = intent.getLongExtra(EditNoteActivity.EXTRA_NOTE_ID, 0L)
            if (id == 0L) return false
            if (intent.getStringExtra(EXTRA_ACTION) == ACTION_TOGGLE) {
                // With app lock on, ticking from the home screen isn't allowed: open the app instead.
                if (com.josh.notes.AppLock.enabled(ctx)) return false
                Checklists.toggle(ctx, id, intent.getIntExtra(EXTRA_INDEX, -1), intent.getStringExtra(EXTRA_TEXT))
                return true
            }
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        if (!handle(this, i)) {
            val id = i.getLongExtra(EditNoteActivity.EXTRA_NOTE_ID, 0L)
            startActivity(Intent(this, EditNoteActivity::class.java).apply {
                if (id != 0L) putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
        finish()
    }
}
