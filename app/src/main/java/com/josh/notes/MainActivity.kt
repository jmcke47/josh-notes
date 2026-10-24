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

class MainActivity : LockableActivity() {

    companion object { @JvmStatic var themeChanged = false }

    private val adapter by lazy { NotesAdapter(oldSchool) { if (mode == Mode.TRASH) trashActions(it) else openNote(it.id) } }
    private lateinit var empty: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var root: View
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var updateController: UpdateController
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private val io = Executors.newSingleThreadExecutor()
    private val volumeKeys by lazy { VolumeKeyDictation(this) { if (!AppLock.needsUnlock(this)) openNote(null, dictate = true) } }

    private var searchQuery = ""
    private enum class Mode { NOTES, ARCHIVE, TRASH }
    private var mode = Mode.NOTES
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
        mode = runCatching { Mode.valueOf(savedInstanceState?.getString("mode") ?: "NOTES") }.getOrDefault(Mode.NOTES)

        if (savedInstanceState == null) checkNewerBackup()
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
        if (oldSchool) setupOldSchool(list)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode != Mode.NOTES) setMode(Mode.NOTES) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        applyMode()

        if (savedInstanceState == null) updateController.quietCheck { m ->
            Snackbar.make(root, "Update available: v${m.versionName}", Snackbar.LENGTH_INDEFINITE)
                .setAnchorView(anchor())
                .setAction("Update") { updateController.startUpdate(m) }
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mode", mode.name)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent) =
        volumeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun onUnlocked() = refresh()

    private fun anchor(): View? = if (oldSchool) findViewById<View>(R.id.os_tabs) else fab.takeIf { it.isShown }
    private val oldSchool by lazy { AppSettings.oldSchool(this) }
    private val osFab by lazy { findViewById<View>(R.id.os_fab) }

    /** Old School theme: chatbox list, stone tab bar, minimap-style new-note button. */
    private fun setupOldSchool(list: RecyclerView) {
        val d = resources.displayMetrics.density
        fab.visibility = View.GONE
        osFab.visibility = View.VISIBLE
        osFab.setOnClickListener { openNote(null) }
        findViewById<View>(R.id.os_tabs).visibility = View.VISIBLE
        (list.layoutParams as ViewGroup.MarginLayoutParams).apply {
            val m = (8 * d).toInt(); setMargins(m, m, m, (56 * d).toInt())
        }
        list.setBackgroundResource(R.drawable.os_chatbox)
        val p = (6 * d).toInt()
        list.setPadding(p, p, p, (90 * d).toInt())
        findViewById<View>(R.id.tab_notes).setOnClickListener { setMode(Mode.NOTES) }
        findViewById<View>(R.id.tab_archive).setOnClickListener { setMode(Mode.ARCHIVE) }
        findViewById<View>(R.id.tab_trash).setOnClickListener { setMode(Mode.TRASH) }
        findViewById<View>(R.id.tab_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.tab_search).setOnClickListener {
            if (mode != Mode.NOTES) setMode(Mode.NOTES)
            toolbar.menu.findItem(R.id.action_search)?.expandActionView()
        }
        empty.setTextColor(getColor(R.color.os_ink))
        empty.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun updateTabs() {
        if (!oldSchool) return
        findViewById<View>(R.id.tab_notes).isSelected = mode == Mode.NOTES
        findViewById<View>(R.id.tab_archive).isSelected = mode == Mode.ARCHIVE
        findViewById<View>(R.id.tab_trash).isSelected = mode == Mode.TRASH
        osFab.visibility = if (mode == Mode.NOTES) View.VISIBLE else View.GONE
    }

    /** Sync: if the backup folder holds a newer backup from another phone, offer to merge it. */
    private fun checkNewerDriveBackup() {
        if (!GoogleDrive.enabled(this)) return
        Thread {
            val item = runCatching { GoogleDrive.newerBackup(this) }.getOrNull() ?: return@Thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                Snackbar.make(root, "Newer notes backup in Google Drive", Snackbar.LENGTH_INDEFINITE)
                    .setAnchorView(anchor())
                    .setAction("Merge") {
                        Thread {
                            val r = runCatching { GoogleDrive.restoreMerge(this, item) }
                            runOnUiThread {
                                refresh()
                                Snackbar.make(root, r.fold({ "Merged: $it notes updated or added" },
                                    { "Couldn't merge from Drive: ${it.message}" }), Snackbar.LENGTH_LONG).show()
                            }
                        }.start()
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(b: Snackbar?, event: Int) { if (event != DISMISS_EVENT_ACTION) GoogleDrive.dismiss(this@MainActivity, item.name) }
                    }).show()
            }
        }.start()
    }

    private fun checkNewerBackup() {
        checkNewerDriveBackup()
        if (Backup.folder(this) == null) return
        Thread {
            val found = runCatching { Backup.newerBackup(this) }.getOrNull() ?: return@Thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val (info, meta) = found
                val from = meta.optString("deviceModel").ifBlank { "another device" }
                Snackbar.make(root, "Newer notes backup from $from", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Merge") {
                        Thread {
                            val r = runCatching { Backup.restoreMerge(this, info.uri) }
                            runOnUiThread {
                                refresh()
                                Snackbar.make(root, r.fold({ "Merged: $it notes updated or added" },
                                    { "Couldn't merge (storage app offline or slow?): ${it.message}" }), Snackbar.LENGTH_LONG).show()
                            }
                        }.start()
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(b: Snackbar?, event: Int) { if (event != DISMISS_EVENT_ACTION) Backup.dismiss(this@MainActivity, info.name) }
                    }).show()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (themeChanged) { themeChanged = false; recreate(); return }
        refresh()
        updateController.onResume()
        Levels.consumePendingLevel(this).takeIf { it > 0 }?.let { lvl -> root.post { Levels.levelUp(this, lvl) } }
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
                R.id.action_archive_view -> setMode(if (mode == Mode.ARCHIVE) Mode.NOTES else Mode.ARCHIVE)
                R.id.action_trash_view -> setMode(if (mode == Mode.TRASH) Mode.NOTES else Mode.TRASH)
                R.id.action_empty_trash -> confirmEmptyTrash()
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

    private fun setMode(m: Mode) { mode = m; applyMode(); refresh() }

    private fun confirmEmptyTrash() {
        osDialog(this).setTitle("Empty Recently deleted?")
            .setMessage("All notes in Recently deleted will be permanently deleted.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Delete forever") { _, _ -> val n = NoteStore.emptyTrash(this); refresh(); snack("Deleted $n notes forever") }
            .show()
    }

    private fun trashActions(n: Note) {
        val daysLeft = (NoteStore.TRASH_DAYS - (System.currentTimeMillis() - n.deletedAt) / 86_400_000L).coerceAtLeast(0)
        osDialog(this)
            .setTitle(n.displayTitle.ifBlank { getString(R.string.untitled) })
            .setMessage(n.contentText.take(500).ifBlank { "(empty)" } + "\n\nPermanently deleted in $daysLeft days.")
            .setNeutralButton("Delete forever") { _, _ -> NoteStore.deleteForever(this, n.id); refresh() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Restore") { _, _ -> NoteStore.untrash(this, n.id); refresh(); snack("Note restored") }
            .show()
    }

    private fun applyMode() {
        toolbar.title = getString(when (mode) { Mode.ARCHIVE -> R.string.archive_title; Mode.TRASH -> R.string.trash_title; else -> R.string.app_name })
        toolbar.menu.findItem(R.id.action_archive_view)?.title = getString(if (mode == Mode.ARCHIVE) R.string.show_notes else R.string.show_archive)
        toolbar.menu.findItem(R.id.action_trash_view)?.title = getString(if (mode == Mode.TRASH) R.string.show_notes else R.string.show_trash)
        toolbar.menu.findItem(R.id.action_empty_trash)?.isVisible = mode == Mode.TRASH
        if (oldSchool) updateTabs() else if (mode != Mode.NOTES) fab.hide() else fab.show()
        if (mode != Mode.NOTES) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { setMode(Mode.NOTES) }
        } else toolbar.navigationIcon = null
    }

    private fun refresh() {
        val q = searchQuery.trim().lowercase()
        val notes = NoteStore.all(this)
            .filter { when (mode) { Mode.TRASH -> it.inTrash; Mode.ARCHIVE -> !it.inTrash && it.archived; Mode.NOTES -> !it.inTrash && !it.archived } }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.contentText.lowercase().contains(q) ||
                    it.attachments.any { a -> a.name.lowercase().contains(q) || a.transcript.lowercase().contains(q) } }
            .let { if (mode == Mode.TRASH) it.sortedByDescending { n -> n.deletedAt } else it.sortedFor(sortMode) }
        adapter.submit(notes)
        empty.setText(when { q.isNotEmpty() -> R.string.empty_search; mode == Mode.ARCHIVE -> R.string.empty_archive; mode == Mode.TRASH -> R.string.empty_trash; else -> R.string.empty_notes })
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openNote(id: Long?, dictate: Boolean = false) {
        startActivity(Intent(this, EditNoteActivity::class.java).apply {
            if (id != null) putExtra(EditNoteActivity.EXTRA_NOTE_ID, id)
            if (dictate) putExtra(EditNoteActivity.EXTRA_DICTATE, true)
        })
    }

    private fun snack(msg: String) = Snackbar.make(root, msg, Snackbar.LENGTH_LONG).setAnchorView(anchor()).show()

    private fun error(title: String, t: Throwable) {
        osDialog(this).setTitle(title).setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showAbout() {
        osDialog(this)
            .setTitle(R.string.app_name)
            .setMessage("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n\nUpdate source:\n${updateController.manifestUrl()}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class SwipeToDelete : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
            val note = adapter.itemAt(vh.bindingAdapterPosition) ?: return refresh()
            if (mode == Mode.TRASH) { NoteStore.deleteForever(this@MainActivity, note.id); refresh(); return }
            NoteStore.delete(this@MainActivity, note.id)
            refresh()
            Snackbar.make(root, R.string.note_moved_to_trash, Snackbar.LENGTH_LONG)
                .setAnchorView(anchor())
                .setAction(R.string.undo) { NoteStore.restore(this@MainActivity, note); refresh() }
                .show()
        }
        override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
            vh.itemView.alpha = 1f - minOf(1f, kotlin.math.abs(dX) / vh.itemView.width)
            super.onChildDraw(c, rv, vh, dX, dY, state, active)
        }
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) { super.clearView(rv, vh); vh.itemView.alpha = 1f }
    }

    private class NotesAdapter(val oldSchool: Boolean, val onClick: (Note) -> Unit) : RecyclerView.Adapter<NotesAdapter.VH>() {
        private val thumbs = java.util.concurrent.Executors.newFixedThreadPool(2)
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
            val bell: ImageView = v.findViewById(R.id.bell)
            val strip: View = v.findViewById(R.id.color_strip)
            val clip: ImageView = v.findViewById(R.id.clip)
            val memo: ImageView = v.findViewById(R.id.memo)
            val thumb: ImageView = v.findViewById(R.id.thumb)
            val hitsplat: View? = v.findViewById(R.id.hitsplat)
            val hitsplatText: TextView? = v.findViewById(R.id.hitsplat_text)
        }

        override fun getItemId(position: Int) = items[position].id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(if (oldSchool) R.layout.item_note_os else R.layout.item_note, parent, false))

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
            h.bell.visibility = if (n.hasReminder && !n.inTrash) View.VISIBLE else View.GONE
            if (n.hasReminder) h.date.text = "${h.date.text}  ·  ⏰ " + ReminderFormat.short(ctx, n.reminderAt)
            val color = NoteColors.palette[n.color]
            h.strip.visibility = if (n.color == 0) View.GONE else View.VISIBLE
            h.strip.setBackgroundColor(color)
            h.memo.visibility = if (n.hasAudio) View.VISIBLE else View.GONE
            // Old School: red "hitsplat" with days overdue.
            h.hitsplat?.let { hs ->
                val now = System.currentTimeMillis()
                val overdue = n.reminderAt in 1..now && !n.inTrash
                hs.visibility = if (overdue) View.VISIBLE else View.GONE
                if (overdue) h.hitsplatText?.text = ((now - n.reminderAt) / 86_400_000L + 1).coerceAtMost(99).toString()
            }
            if (oldSchool) {
                h.title.text = h.title.text.toString() + ":"
                h.bell.visibility = if (n.hasReminder && !n.inTrash && n.reminderAt > System.currentTimeMillis()) View.VISIBLE else View.GONE
            }
            h.clip.visibility = if (n.hasFiles || (n.hasImages && n.inTrash)) View.VISIBLE else View.GONE
            val img = n.attachments.firstOrNull { it.kind == "image" }
            h.thumb.tag = img?.id
            h.thumb.setImageDrawable(null)
            h.thumb.visibility = if (img != null) View.VISIBLE else View.GONE
            if (img != null) {
                val px = (56 * ctx.resources.displayMetrics.density).toInt()
                thumbs.execute {
                    val bmp = runCatching { Attachments.thumbnail(ctx, n.id, img, px) }.getOrNull()
                    h.thumb.post { if (h.thumb.tag == img.id) h.thumb.setImageBitmap(bmp) }
                }
            }
            h.itemView.setOnClickListener { onClick(n) }
        }
    }
}
