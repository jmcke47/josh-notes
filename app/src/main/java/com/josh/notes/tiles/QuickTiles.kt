package com.josh.notes.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.josh.notes.EditNoteActivity

/** Quick Settings tiles: "New note" and "Dictate note" (uses the app's dictation / voice action setting). */
abstract class QuickCaptureTile : TileService() {
    abstract fun intent(): Intent

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val i = intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val launch = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, i.action.hashCode(), i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(i)
            }
        }
        // On the lock screen, ask Android to unlock first (notes never show over the keyguard).
        if (isLocked) unlockAndRun { launch() } else launch()
    }
}

class NewNoteTileService : QuickCaptureTile() {
    override fun intent() = Intent(this, EditNoteActivity::class.java).setAction("com.josh.notes.NEW_NOTE_TILE")
}

class DictateTileService : QuickCaptureTile() {
    override fun intent() = Intent(this, EditNoteActivity::class.java).setAction(EditNoteActivity.ACTION_DICTATE)
}
