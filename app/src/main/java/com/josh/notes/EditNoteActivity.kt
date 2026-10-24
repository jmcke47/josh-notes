package com.josh.notes

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class EditNoteActivity : LockableActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_DICTATE = "dictate"
        const val EXTRA_TITLE = "init_title"
        const val EXTRA_BODY = "init_body"
        const val ACTION_DICTATE = "com.josh.notes.DICTATE"
    }

    private var noteId: Long = 0L
    private var deleted = false
    private var pinned = false
    private var color = 0
    private var archived = false
    private var checklist = false
    private var createdAt = 0L
    private var reminderAt = 0L
    private var reminderFired = false
    private var repeatRule = Repeat.NONE
    private var attachments: List<Attachment> = emptyList()
    private var createdHere = false
    private lateinit var panel: AttachmentsPanel
    private var lastEdited = 0L
    private var viewMode = false

    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleEt: EditText
    private lateinit var bodyEt: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var addItem: View
    private lateinit var colorBar: View
    private lateinit var bodyView: android.widget.TextView
    private lateinit var footer: android.widget.TextView
    private lateinit var reminderChip: com.google.android.material.chip.Chip
    private var pendingReminder = false
    private val notifPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Snackbar.make(toolbar, "Notifications are off — the reminder won't show. Enable them in Settings.", Snackbar.LENGTH_LONG).show()
        if (pendingReminder) { pendingReminder = false; pickReminderDate() }
    }
    private var lastFocused: EditText? = null
    private lateinit var dictation: DictationController
    private val volumeKeys by lazy { VolumeKeyDictation(this) { if (!AppLock.needsUnlock(this)) voiceAction() } }

    /** Mic button / widget mic / volume keys: dictate, record a memo, or both (Settings). */
    private fun voiceAction() {
        if (viewMode) enterEdit()
        when (VoiceAction.get(this)) {
            VoiceAction.DICTATE -> dictation.start()
            VoiceAction.MEMO -> panel.recordMemo(false)
            VoiceAction.BOTH -> panel.recordMemo(true)
        }
    }

    /** Make sure the note exists in the store (needed before attaching files). */
    private fun ensureNoteId(): Long {
        if (noteId == 0L) {
            noteId = NoteStore.upsert(this, buildNote().copy(id = 0L)).id
            createdHere = true
            NoteStore.get(this, noteId)?.let { createdAt = it.createdAt; lastEdited = it.updatedAt }
        }
        return noteId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        if (AppSettings.oldSchool(this)) findViewById<View>(R.id.paper).apply {
            setBackgroundResource(R.drawable.os_scroll)
            val d = resources.displayMetrics.density
            setPadding((10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt(), (16 * d).toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                    or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        toolbar = findViewById(R.id.toolbar)
        titleEt = findViewById(R.id.title)
        bodyEt = findViewById(R.id.body)
        listBox = findViewById(R.id.checklist)
        addItem = findViewById(R.id.add_item)
        colorBar = findViewById(R.id.color_bar)
        bodyView = findViewById(R.id.body_view)
        footer = findViewById(R.id.footer)
        reminderChip = findViewById(R.id.reminder_chip)
        reminderChip.setOnClickListener {
            osDialog(this).setTitle(R.string.reminder)
                .setItems(arrayOf("Change date & time", "Change repeat", "Remove reminder")) { _, w ->
                    when (w) { 0 -> setReminder(); 1 -> pickRepeat(reminderAt); 2 -> clearReminder() }
                }.show()
        }
        reminderChip.setOnCloseIconClickListener { clearReminder() }
        bodyView.setOnClickListener {
            // A tap on a link is handled by LinkMovementMethod (it selects the link span) — ignore that one.
            val t = bodyView.text
            if (t is android.text.Spannable && android.text.Selection.getSelectionStart(t) != android.text.Selection.getSelectionEnd(t)) {
                android.text.Selection.removeSelection(t); return@setOnClickListener
            }
            enterEdit()
        }
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = updateFooter()
        }
        titleEt.addTextChangedListener(watcher); bodyEt.addTextChangedListener(watcher)
        dictation = DictationController(this) { insertAtCursor(it) }
        panel = AttachmentsPanel(this, findViewById(R.id.attachments_box), dictation, ::ensureNoteId,
            { attachments }, { attachments = it; save(); updateFooter() }, { insertAtCursor(it) })

        trackFocus(titleEt); trackFocus(bodyEt)
        addItem.setOnClickListener { addRow(CheckItem("", false), focus = true) }

        val restoredId = savedInstanceState?.getLong(EXTRA_NOTE_ID, 0L) ?: 0L
        noteId = if (restoredId != 0L) restoredId else intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val note = noteId.takeIf { it != 0L }?.let { NoteStore.get(this, it) }
        if (note == null) noteId = 0L

        if (savedInstanceState != null && savedInstanceState.containsKey("checklist")) {
            // Rebuild from instance state (rotation etc.).
            pinned = savedInstanceState.getBoolean("pinned"); color = savedInstanceState.getInt("color")
            archived = savedInstanceState.getBoolean("archived"); checklist = savedInstanceState.getBoolean("checklist")
            createdAt = savedInstanceState.getLong("createdAt")
            reminderAt = savedInstanceState.getLong("reminderAt"); reminderFired = savedInstanceState.getBoolean("reminderFired")
            lastEdited = savedInstanceState.getLong("lastEdited")
            repeatRule = savedInstanceState.getString("repeat")?.let { runCatching { Repeat.fromJson(org.json.JSONObject(it)) }.getOrNull() } ?: Repeat.NONE
            attachments = note?.attachments ?: emptyList()
            val texts = savedInstanceState.getStringArrayList("itemTexts") ?: arrayListOf()
            val checks = savedInstanceState.getBooleanArray("itemChecks") ?: BooleanArray(0)
            setChecklistItems(texts.mapIndexed { i, t -> CheckItem(t, checks.getOrElse(i) { false }) })
        } else if (note != null) {
            titleEt.setText(note.title); bodyEt.setText(note.body)
            pinned = note.pinned; color = note.color; archived = note.archived
            checklist = note.checklist; createdAt = note.createdAt
            reminderAt = note.reminderAt; reminderFired = note.reminderFired; lastEdited = note.updatedAt
            repeatRule = note.repeat; attachments = note.attachments
            setChecklistItems(note.items)
        } else {
            intent.getStringExtra(EXTRA_TITLE)?.let { titleEt.setText(it) }
            intent.getStringExtra(EXTRA_BODY)?.let { bodyEt.setText(it) }
            titleEt.requestFocus()
        }
        applyChecklistMode()
        updateMenu(); updateColor(); updateReminderChip(); panel.render()
        val wantsDictation0 = intent.getBooleanExtra(EXTRA_DICTATE, false) || intent.action == ACTION_DICTATE
        if (savedInstanceState == null && note != null && !note.checklist && note.body.isNotBlank() && !wantsDictation0) enterView()
        updateFooter()

        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_mic -> voiceAction()
                R.id.action_attach -> { if (viewMode) enterEdit(); panel.showAddMenu() }
                R.id.action_reminder -> setReminder()
                R.id.action_pin -> { pinned = !pinned; updateMenu()
                    Snackbar.make(toolbar, if (pinned) "Pinned" else "Unpinned", Snackbar.LENGTH_SHORT).show() }
                R.id.action_color -> pickColor()
                R.id.action_checklist -> toggleChecklist()
                R.id.action_share -> share()
                R.id.action_archive -> { archived = !archived; save(); finish() }
                R.id.action_delete -> confirmDelete()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        val wantsDictation = savedInstanceState == null &&
                (intent.getBooleanExtra(EXTRA_DICTATE, false) || intent.action == ACTION_DICTATE)
        if (wantsDictation) {
            (if (checklist) null else bodyEt)?.requestFocus()
            if (AppLock.needsUnlock(this)) dictateAfterUnlock = true else bodyEt.post { voiceAction() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent) = volumeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    private var dictateAfterUnlock = false
    override fun onUnlocked() { if (dictateAfterUnlock) { dictateAfterUnlock = false; bodyEt.post { voiceAction() } } }

    // ---------------------------------------------------------------- view / edit mode (tappable links)

    private fun enterView() {
        viewMode = true
        bodyView.text = bodyEt.text.toString()
        bodyView.visibility = View.VISIBLE; bodyEt.visibility = View.GONE
    }

    private fun enterEdit() {
        if (!viewMode) return
        viewMode = false
        bodyView.visibility = View.GONE; bodyEt.visibility = View.VISIBLE
        bodyEt.requestFocus(); bodyEt.setSelection(bodyEt.text.length)
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.showSoftInput(bodyEt, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------------------------------------------------------------- footer

    private fun updateFooter() {
        if (!::footer.isInitialized) return
        val text = if (checklist) currentItems().joinToString("\n") { it.text } else bodyEt.text.toString()
        val all = titleEt.text.toString() + "\n" + text
        val words = Regex("\\S+").findAll(all).count()
        val chars = text.length + titleEt.text.length
        val edited = if (lastEdited > 0) "Edited " + android.text.format.DateUtils.getRelativeTimeSpanString(
            lastEdited, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS) else "New note"
        footer.text = "$words words · $chars characters · $edited"
    }

    // ---------------------------------------------------------------- reminders

    private fun updateReminderChip() {
        reminderChip.visibility = if (reminderAt > 0) View.VISIBLE else View.GONE
        if (reminderAt > 0) {
            val time = android.text.format.DateFormat.getTimeFormat(this).format(reminderAt)
            reminderChip.text = if (repeatRule.isRepeating)
                repeatRule.describe(reminderAt, time) + " · next " + ReminderFormat.short(this, reminderAt)
            else ReminderFormat.short(this, reminderAt) + if (reminderAt <= System.currentTimeMillis()) " · past" else ""
            reminderChip.isCloseIconVisible = true
        }
        toolbar.menu.findItem(R.id.action_reminder)?.icon?.mutate()?.alpha = if (reminderAt > 0) 255 else 110
    }

    private fun setReminder() {
        if (!Reminders.canNotify(this) && android.os.Build.VERSION.SDK_INT >= 33) {
            pendingReminder = true
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        pickReminderDate()
    }

    private fun pickReminderDate() {
        val now = java.util.Calendar.getInstance()
        val start = if (reminderAt > System.currentTimeMillis()) reminderAt else now.timeInMillis + 3600_000L
        // MaterialDatePicker works in UTC midnight millis.
        val local = java.util.Calendar.getInstance().apply { timeInMillis = start }
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }
        val dp = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("Remind me on")
            .setSelection(utc.timeInMillis)
            .setCalendarConstraints(com.google.android.material.datepicker.CalendarConstraints.Builder()
                .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.now()).build())
            .build()
        dp.addOnPositiveButtonClickListener { sel ->
            val d = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = sel }
            val tp = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(if (android.text.format.DateFormat.is24HourFormat(this))
                    com.google.android.material.timepicker.TimeFormat.CLOCK_24H else com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                .setHour(local.get(java.util.Calendar.HOUR_OF_DAY)).setMinute(local.get(java.util.Calendar.MINUTE))
                .setTitleText("At")
                .build()
            tp.addOnPositiveButtonClickListener {
                val at = java.util.Calendar.getInstance().apply {
                    clear()
                    set(d.get(java.util.Calendar.YEAR), d.get(java.util.Calendar.MONTH), d.get(java.util.Calendar.DAY_OF_MONTH), tp.hour, tp.minute, 0)
                }.timeInMillis
                if (at <= System.currentTimeMillis()) {
                    Snackbar.make(toolbar, "That time has already passed", Snackbar.LENGTH_SHORT).show(); return@addOnPositiveButtonClickListener
                }
                pickRepeat(at)
            }
            tp.show(supportFragmentManager, "time")
        }
        dp.show(supportFragmentManager, "date")
    }

    private fun clearReminder() {
        reminderAt = 0L; reminderFired = false; repeatRule = Repeat.NONE; updateReminderChip(); save()
    }

    /** Step 3 of setting a reminder: repetition. */
    private fun pickRepeat(at: Long) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val opts = arrayOf("Doesn't repeat", "Every day", "Every week…", "Every month (on the ${Repeat.ordinal(cal.get(java.util.Calendar.DAY_OF_MONTH))})",
            "Every year", "Every N days…")
        val cur = when (repeatRule.type) { Repeat.Type.NONE -> 0; Repeat.Type.DAILY -> 1; Repeat.Type.WEEKLY -> 2
            Repeat.Type.MONTHLY -> 3; Repeat.Type.YEARLY -> 4; Repeat.Type.EVERY_N_DAYS -> 5 }
        osDialog(this).setTitle("Repeat")
            .setSingleChoiceItems(opts, cur) { d, w ->
                d.dismiss()
                when (w) {
                    0 -> applyReminder(at, Repeat.NONE)
                    1 -> applyReminder(at, Repeat(Repeat.Type.DAILY))
                    2 -> pickWeekdays(at, if (repeatRule.type == Repeat.Type.WEEKLY && repeatRule.days.isNotEmpty()) repeatRule.days else setOf(dow))
                    3 -> applyReminder(at, Repeat(Repeat.Type.MONTHLY, dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)))
                    4 -> applyReminder(at, Repeat(Repeat.Type.YEARLY))
                    5 -> pickEveryN(at)
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun pickWeekdays(at: Long, initial: Set<Int>) {
        val order = Repeat.ORDER
        val checked = order.map { it in initial }.toBooleanArray()
        osDialog(this).setTitle("Repeat on")
            .setMultiChoiceItems(order.map { Repeat.dayName(it) }.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val days = order.filterIndexed { i, _ -> checked[i] }.toSet()
                if (days.isEmpty()) applyReminder(at, Repeat.NONE)
                else { val r = Repeat(Repeat.Type.WEEKLY, days); applyReminder(r.first(at), r) }
            }.show()
    }

    private fun pickEveryN(at: Long) {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(repeatRule.every.coerceAtLeast(2).toString()); selectAll() }
        val pad = (20 * resources.displayMetrics.density).toInt()
        osDialog(this).setTitle("Repeat every … days")
            .setView(android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = input.text.toString().toIntOrNull()?.coerceIn(1, 3650) ?: 1
                applyReminder(at, if (n == 1) Repeat(Repeat.Type.DAILY) else Repeat(Repeat.Type.EVERY_N_DAYS, every = n))
            }.show()
    }

    private fun applyReminder(at: Long, rule: Repeat) {
        var t = at
        if (t <= System.currentTimeMillis()) t = rule.next(t, System.currentTimeMillis()).takeIf { it > 0 } ?: t
        reminderAt = t; reminderFired = false; repeatRule = rule
        updateReminderChip()
        save() // schedule straight away
        if (!Reminders.canExact(this)) offerExactAlarms()
        else Snackbar.make(toolbar, "Reminder set: " + reminderChip.text, Snackbar.LENGTH_SHORT).show()
    }

    private fun offerExactAlarms() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        osDialog(this)
            .setTitle("Allow exact reminders?")
            .setMessage("Without the \"Alarms & reminders\" permission, Android may deliver reminders a few minutes late.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open settings") { _, _ ->
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName"))) }
            }.show()
    }

    override fun onDestroy() { panel.release(); dictation.release(); super.onDestroy() }

    private fun trackFocus(et: EditText) {
        et.setOnFocusChangeListener { v, has -> if (has) lastFocused = v as EditText }
    }

    /** Insert dictated text at the cursor of the last focused field (body / item by default). */
    private fun EditText.doAfterTextChangedCompat(f: () -> Unit) = addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = f()
    })

    private fun insertAtCursor(text: String) {
        if (viewMode) enterEdit()
        val target = lastFocused?.takeIf { it.isAttachedToWindow }
            ?: if (checklist) addRow(CheckItem("", false), focus = true) else bodyEt
        val e = target.text
        val start = target.selectionStart.coerceAtLeast(0).coerceAtMost(e.length)
        val end = target.selectionEnd.coerceAtLeast(start).coerceAtMost(e.length)
        val before = if (start > 0 && !e[start - 1].isWhitespace()) " " else ""
        val after = if (end < e.length && !e[end].isWhitespace()) " " else ""
        val ins = before + (if (start > 0 && e.substring(0, start).trimEnd().let { it.isNotEmpty() && it.last() !in ".!?\n" })
            (if (text.startsWith("I ") || text.startsWith("I'") || text == "I") text else text.replaceFirstChar { it.lowercase() }) else text) + after
        e.replace(start, end, ins)
        target.requestFocus()
        target.setSelection((start + ins.length - after.length).coerceAtMost(target.text.length))
    }

    // ---------------------------------------------------------------- checklist

    private fun setChecklistItems(items: List<CheckItem>) {
        listBox.removeAllViews()
        items.forEach { addRow(it, focus = false) }
    }

    private fun addRow(item: CheckItem, focus: Boolean, index: Int = -1): EditText {
        val row = LayoutInflater.from(listBox.context).inflate(R.layout.item_check, listBox, false)
        val cb = row.findViewById<MaterialCheckBox>(R.id.check)
        val et = row.findViewById<EditText>(R.id.text)
        et.setText(item.text)
        cb.isChecked = item.checked
        strike(et, item.checked)
        cb.setOnCheckedChangeListener { _, c ->
            strike(et, c)
            if (c && et.text.isNotBlank()) {
                val items = currentItems().filter { it.text.isNotBlank() }
                val allDone = items.size >= 2 && items.all { it.checked }
                Levels.reward(this, Levels.XP_ITEM + if (allDone) Levels.XP_CHECKLIST_DONE else 0)
            }
        }
        et.doAfterTextChangedCompat { updateFooter() }
        trackFocus(et)
        et.imeOptions = EditorInfo.IME_ACTION_NEXT
        et.setRawInputType(EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES)
        et.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE) {
                addRow(CheckItem("", false), focus = true, index = listBox.indexOfChild(row) + 1); true
            } else false
        }
        row.findViewById<ImageButton>(R.id.remove).setOnClickListener {
            if (lastFocused == et) lastFocused = null
            listBox.removeView(row)
        }
        if (index in 0..listBox.childCount) listBox.addView(row, index) else listBox.addView(row)
        if (focus) et.requestFocus()
        return et
    }

    private fun strike(et: EditText, on: Boolean) {
        et.paintFlags = if (on) et.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else et.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        et.alpha = if (on) 0.6f else 1f
    }

    private fun currentItems(): List<CheckItem> = (0 until listBox.childCount).map {
        val row = listBox.getChildAt(it)
        CheckItem(row.findViewById<EditText>(R.id.text).text.toString(), row.findViewById<MaterialCheckBox>(R.id.check).isChecked)
    }

    private fun toggleChecklist() {
        if (!checklist) {
            val lines = bodyEt.text.toString().lines().map { it.trim().removePrefix("☐").removePrefix("☑").removePrefix("-").trim() }
                .filter { it.isNotEmpty() }
            setChecklistItems(lines.map { CheckItem(it, false) })
            if (lines.isEmpty()) addRow(CheckItem("", false), focus = true)
            bodyEt.setText("")
            checklist = true
        } else {
            bodyEt.setText(currentItems().filter { it.text.isNotBlank() }.joinToString("\n") { it.text })
            listBox.removeAllViews()
            checklist = false
        }
        lastFocused = null
        applyChecklistMode(); updateMenu()
    }

    private fun applyChecklistMode() {
        if (checklist && viewMode) { viewMode = false; bodyView.visibility = View.GONE }
        bodyEt.visibility = if (checklist) View.GONE else View.VISIBLE
        listBox.visibility = if (checklist) View.VISIBLE else View.GONE
        addItem.visibility = listBox.visibility
    }

    // ---------------------------------------------------------------- pin / colour / share

    private fun updateMenu() {
        toolbar.menu.findItem(R.id.action_pin)?.apply {
            title = getString(if (pinned) R.string.unpin else R.string.pin)
            icon?.mutate()?.alpha = if (pinned) 255 else 110
        }
        toolbar.menu.findItem(R.id.action_checklist)?.title = getString(if (checklist) R.string.to_text else R.string.to_checklist)
        toolbar.menu.findItem(R.id.action_archive)?.title = getString(if (archived) R.string.unarchive else R.string.archive)
    }

    private fun updateColor() {
        colorBar.visibility = if (color == 0) View.GONE else View.VISIBLE
        colorBar.setBackgroundColor(NoteColors.palette[color])
    }

    private fun pickColor() {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(this).apply { setPadding(pad * 2, pad, pad * 2, 0); orientation = LinearLayout.HORIZONTAL }
        val scroll = android.widget.HorizontalScrollView(this).apply { addView(row) }
        val dialog = osDialog(this).setTitle(R.string.colour).setView(scroll)
            .setNegativeButton(android.R.string.cancel, null).create()
        NoteColors.palette.forEachIndexed { i, c ->
            row.addView(ImageView(this).apply {
                val size = (40 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = pad }
                setImageResource(if (i == 0) R.drawable.ic_close else R.drawable.color_dot)
                if (i != 0) imageTintList = ColorStateList.valueOf(c)
                contentDescription = NoteColors.names[i]
                alpha = if (i == color) 1f else 0.75f
                scaleX = if (i == color) 1.15f else 1f; scaleY = scaleX
                setOnClickListener { color = i; updateColor(); dialog.dismiss() }
            })
        }
        dialog.show()
    }

    /** Editor-owned fields on top of the stored note (keeps snooze/trash state set elsewhere). */
    private fun buildNote(): Note {
        val base = noteId.takeIf { it != 0L }?.let { NoteStore.get(this, it) } ?: Note(noteId, "", "", System.currentTimeMillis(), createdAt)
        val reminderChanged = base.reminderAt != reminderAt || base.repeat != repeatRule
        return base.copy(
            id = noteId, title = titleEt.text.toString(),
            body = if (checklist) "" else bodyEt.text.toString(),
            createdAt = if (createdAt > 0) createdAt else base.createdAt,
            pinned = pinned, color = color, archived = archived, checklist = checklist,
            items = if (checklist) currentItems() else emptyList(),
            reminderAt = reminderAt, reminderFired = reminderFired, repeat = repeatRule,
            snoozeAt = if (reminderChanged) 0L else base.snoozeAt,
            attachments = attachments,
        )
    }

    private fun share() {
        val text = buildNote().shareText()
        if (text.isBlank()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            titleEt.text.toString().takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }, getString(R.string.share)))
    }

    private fun confirmDelete() {
        osDialog(this)
            .setTitle("Delete note?")
            .setMessage("It'll be kept in Recently deleted for 30 days.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (noteId != 0L) NoteStore.delete(this, noteId)
                deleted = true
                finish()
            }
            .show()
    }

    // ---------------------------------------------------------------- save

    private fun save() {
        if (deleted) return
        val n = buildNote()
        val existing = noteId.takeIf { it != 0L }?.let { NoteStore.get(this, it) }
        if (existing == null && n.isEmpty) return
        if (existing != null) {
            val contentSame = existing.title == n.title && existing.body == n.body && existing.items == n.items &&
                    existing.checklist == n.checklist && existing.attachments == n.attachments
            val metaSame = existing.pinned == n.pinned && existing.color == n.color && existing.archived == n.archived &&
                    existing.reminderAt == n.reminderAt && existing.reminderFired == n.reminderFired &&
                    existing.repeat == n.repeat && existing.snoozeAt == n.snoozeAt
            if (contentSame && metaSame) return
            // Only bump "edited" time when the content changed.
            noteId = NoteStore.upsert(this, n.copy(createdAt = existing.createdAt, updatedAt = existing.updatedAt,
                deletedAt = existing.deletedAt), touch = !contentSame).id
        } else {
            noteId = NoteStore.upsert(this, n.copy(id = 0L)).id
        }
        NoteStore.get(this, noteId)?.let { createdAt = it.createdAt; lastEdited = it.updatedAt }
        updateFooter()
    }

    override fun onPause() {
        super.onPause()
        panel.stopRecordingIfAny(); panel.stopPlayer()
        save()
        // A note created only to hold an attachment that was then removed: don't leave an empty note behind.
        if (isFinishing && createdHere && noteId != 0L && NoteStore.get(this, noteId)?.isEmpty == true) NoteStore.deleteForever(this, noteId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_NOTE_ID, noteId)
        outState.putBoolean("pinned", pinned); outState.putInt("color", color)
        outState.putBoolean("archived", archived); outState.putBoolean("checklist", checklist)
        outState.putLong("createdAt", createdAt)
        outState.putLong("reminderAt", reminderAt); outState.putBoolean("reminderFired", reminderFired)
        outState.putLong("lastEdited", lastEdited)
        outState.putString("repeat", repeatRule.toJson().toString())
        val items = currentItems()
        outState.putStringArrayList("itemTexts", ArrayList(items.map { it.text }))
        outState.putBooleanArray("itemChecks", items.map { it.checked }.toBooleanArray())
    }
}
