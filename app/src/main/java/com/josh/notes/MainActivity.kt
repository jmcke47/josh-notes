package com.josh.notes

import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object { @JvmStatic var themeChanged = false }

    private val adapter = NotesAdapter { openNote(it.id) }
    private lateinit var empty: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var root: View
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var updateController: UpdateController
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private val io = Executors.newSingleThreadExecutor()
    private val volumeKeys by lazy { VolumeKeyDictation(this) { openNote(null, dictate = true) } }

    private var searchQuery = ""
    private var showArchive = false
    private var sortMode: SortMode
        get() = runCatching { SortMode.valueOf(prefs.getString("sort", null) ?: "EDITED") }.getOrDefault(SortMode.EDITED)
        set(v) = prefs.edit { putString("sort", v.name) }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        io.execute {
            val r = runCatching {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(NoteStore.exportJson(this).toByteArray()) }
                NoteStore.all(this).size
            }
            runOnUiThread {
                r.onSuccess { snack("Exported $it notes") }.onFailure { error("Export failed", it) }
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        io.execute {
            val r = runCatching {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                NoteStore.importJson(this, text)
            }
            runOnUiThread {
                r.onSuccess { (added, updated) -> refresh(); snack("Imported: $added new, $updated updated") }
                    .onFailure { error("Import failed — not a Josh Notes backup?", it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        showArchive = savedInstanceState?.getBoolean("archive") ?: false

        updateController = UpdateController(this)
        toolbar = findViewById(R.id.toolbar)
        setupMenu()

        empty = findViewById(R.id.empty)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        ItemTouchHelper(SwipeToDelete()).attachToRecyclerView(list)

        fab = findViewById(R.id.fab)
        fab.setOnClickListener { openNote(null) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showArchive) setArchiveMode(false) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        applyMode()

        if (savedInstanceState == null) updateController.quietCheck { m ->
            Snackbar.make(root, "Update available: v${m.versionName}", Snackbar.LENGTH_INDEFINITE)
                .setAnchorView(fab.takeIf { it.isShown })
                .setAction("Update") { updateController.startUpdate(m) }
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("archive", showArchive)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun onResume() {
        super.onResume()
        if (themeChanged) { themeChanged = false; recreate(); return }
        refresh()
        updateController.onResume()
    }

    private fun setupMenu() {
        val menu = toolbar.menu
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as SearchView).apply {
            queryHint = getString(R.string.search)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(q: String?) = true.also { clearFocus() }
                override fun onQueryTextChange(q: String?): Boolean { searchQuery = q.orEmpty(); refresh(); return true }
            })
        }
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem) = true.also { searchQuery = ""; refresh() }
        })
        syncSortChecks()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_edited -> setSort(SortMode.EDITED)
                R.id.sort_created -> setSort(SortMode.CREATED)
                R.id.sort_title -> setSort(SortMode.TITLE)
                R.id.action_archive_view -> setArchiveMode(!showArchive)
                R.id.action_export -> exportLauncher.launch("josh-notes-backup-" +
                        SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json")
                R.id.action_import -> importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.action_check_updates -> updateController.checkForUpdates()
                R.id.action_about -> showAbout()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun setSort(m: SortMode) { sortMode = m; syncSortChecks(); refresh() }

    private fun syncSortChecks() {
        val id = when (sortMode) { SortMode.EDITED -> R.id.sort_edited; SortMode.CREATED -> R.id.sort_created; SortMode.TITLE -> R.id.sort_title }
        toolbar.menu.findItem(id)?.isChecked = true
    }

    private fun setArchiveMode(on: Boolean) { showArchive = on; applyMode(); refresh() }

    private fun applyMode() {
        toolbar.title = getString(if (showArchive) R.string.archive_title else R.string.app_name)
        toolbar.menu.findItem(R.id.action_archive_view)?.title = getString(if (showArchive) R.string.show_notes else R.string.show_archive)
        if (showArchive) fab.hide() else fab.show()
        if (showArchive) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { setArchiveMode(false) }
        } else toolbar.navigationIcon = null
    }

    private fun refresh() {
        val q = searchQuery.trim().lowercase()
        val notes = NoteStore.all(this)
            .filter { it.archived == showArchive }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.contentText.lowercase().contains(q) }
            .sortedFor(sortMode)
        adapter.submit(notes)
        empty.setText(when { q.isNotEmpty() -> R.string.empty_search; showArchive -> R.string.empty_archive; else -> R.string.empty_notes })
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openNote(id: Long?, dictate: Boolean = false) {
        startActivity(Intent(this, EditNoteActivity::class.java).apply {
            if (id != null) putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
            if (dictate) putExtra(EditNoteActivity.EXTRA_DICTATE, true)
        })
    }

    private fun snack(msg: String) = Snackbar.make(root, msg, Snackbar.LENGTH_LONG).setAnchorView(fab.takeIf { it.isShown }).show()

    private fun error(title: String, t: Throwable) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n\nUpdate source:\n${updateController.manifestUrl()}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class SwipeToDelete : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
            val note = adapter.itemAt(vh.bindingAdapterPosition) ?: return refresh()
            NoteStore.delete(this@MainActivity, note.id)
            refresh()
            Snackbar.make(root, R.string.note_deleted, Snackbar.LENGTH_LONG)
                .setAnchorView(fab.takeIf { it.isShown })
                .setAction(R.string.undo) { NoteStore.restore(this@MainActivity, note); refresh() }
                .show()
        }
        override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
            vh.itemView.alpha = 1f - minOf(1f, kotlin.math.abs(dX) / vh.itemView.width)
            super.onChildDraw(c, rv, vh, dX, dY, state, active)
        }
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) { super.clearView(rv, vh); vh.itemView.alpha = 1f }
    }

    private class NotesAdapter(val onClick: (Note) -> Unit) : RecyclerView.Adapter<NotesAdapter.VH>() {
        private var items: List<Note> = emptyList()
        init { setHasStableIds(true) }

        fun itemAt(pos: Int) = items.getOrNull(pos)

        fun submit(list: List<Note>) {
            items = list
            @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.title)
            val body: TextView = v.findViewById(R.id.body)
            val date: TextView = v.findViewById(R.id.date)
            val pin: ImageView = v.findViewById(R.id.pin)
            val strip: View = v.findViewById(R.id.color_strip)
        }

        override fun getItemId(position: Int) = items[position].id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val n = items[position]
            val ctx = h.itemView.context
            h.title.text = n.displayTitle.ifBlank { ctx.getString(R.string.untitled) }
            val preview = when {
                n.checklist -> n.contentText
                n.title.isBlank() -> n.body.lines().drop(1).joinToString("\n").trim()
                else -> n.body.trim()
            }
            h.body.text = preview
            h.body.visibility = if (preview.isBlank()) View.GONE else View.VISIBLE
            val done = n.items.count { it.checked }
            h.date.text = DateUtils.getRelativeTimeSpanString(n.updatedAt).toString() +
                    if (n.checklist && n.items.isNotEmpty()) "  ·  $done/${n.items.size} done" else ""
            h.pin.visibility = if (n.pinned) View.VISIBLE else View.GONE
            val color = NoteColors.palette[n.color]
            h.strip.visibility = if (n.color == 0) View.GONE else View.VISIBLE
            h.strip.setBackgroundColor(color)
            h.itemView.setOnClickListener { onClick(n) }
        }
    }
}
