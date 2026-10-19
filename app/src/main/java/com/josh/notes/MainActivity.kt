package com.josh.notes

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private val adapter = NotesAdapter { openNote(it.id) }
    private lateinit var empty: View
    private lateinit var updateController: UpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        updateController = UpdateController(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_updates -> { updateController.checkForUpdates(); true }
                R.id.action_update_url -> { updateController.editUpdateUrl(); true }
                R.id.action_about -> { showAbout(); true }
                else -> false
            }
        }

        empty = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<ExtendedFloatingActionButton>(R.id.fab).setOnClickListener { openNote(null) }

        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateController.onResume()
    }

    private fun handleWidgetIntent(intent: Intent?) {
        // (Widget items open EditNoteActivity directly; kept for future deep links.)
    }

    private fun refresh() {
        val notes = NoteStore.all(this)
        adapter.submit(notes)
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openNote(id: Long?) {
        startActivity(Intent(this, EditNoteActivity::class.java).apply {
            if (id != null) putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
        })
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n\nUpdate source:\n${updateController.manifestUrl()}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private class NotesAdapter(val onClick: (Note) -> Unit) : RecyclerView.Adapter<NotesAdapter.VH>() {
        private var items: List<Note> = emptyList()

        fun submit(list: List<Note>) {
            items = list
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.title)
            val body: TextView = v.findViewById(R.id.body)
            val date: TextView = v.findViewById(R.id.date)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val n = items[position]
            val ctx = h.itemView.context
            h.title.text = n.displayTitle.ifBlank { ctx.getString(R.string.untitled) }
            h.body.text = n.body
            h.body.visibility = if (n.body.isBlank() || n.title.isBlank()) View.GONE else View.VISIBLE
            h.date.text = DateUtils.getRelativeTimeSpanString(n.updatedAt)
            h.itemView.setOnClickListener { onClick(n) }
        }
    }
}
