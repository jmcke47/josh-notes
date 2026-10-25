package com.josh.notes

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.NumberFormat

/** Skill-style stats: each stat is a "skill" with XP and a level on the classic experience curve. */
class StatsActivity : LockableActivity() {
    private val os by lazy { AppSettings.oldSchool(this) }
    private val font by lazy { if (os) runCatching { ResourcesCompat.getFont(this, R.font.pixelify_sans) }.getOrNull() else null }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val nf = NumberFormat.getIntegerInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom); WindowInsetsCompat.CONSUMED
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
    }

    override fun onResume() { super.onResume(); render() }

    private fun text(t: String, size: Float, color: Int? = null, shadow: Boolean = os) = TextView(this).apply {
        text = t; textSize = size; font?.let { typeface = it }
        color?.let { setTextColor(it) }
        if (shadow) setShadowLayer(0.01f, dp(1).toFloat(), dp(1).toFloat(), Color.BLACK) else setShadowLayer(0f, 0f, 0f, 0)
    }

    fun render() {
        val box = findViewById<LinearLayout>(R.id.stats_content)
        box.removeAllViews()
        val skills = Stats.skills(this)
        val total = skills.sumOf { it.level }
        val yellow = getColor(R.color.os_yellow)

        // Skill grid (icon + level), like a classic skills panel.
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        for (s in skills) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                if (os) setBackgroundResource(R.drawable.os_button_normal)
                addView(ImageView(context).apply {
                    setImageResource(s.icon)
                    if (!os) imageTintList = null
                }, LinearLayout.LayoutParams(dp(30), dp(30)))
                addView(text("${s.level}", 18f, if (os) yellow else null).apply { setPadding(dp(8), 0, 0, 0) })
                contentDescription = "${s.name} level ${s.level}"
            }
            val card: View = if (os) tile else MaterialCardView(this).apply { addView(tile) }
            grid.addView(card, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0; height = dp(56); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        // Total level tile.
        val totalTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            if (os) setBackgroundResource(R.drawable.os_button_normal)
            addView(text("Total level:", 12f, if (os) yellow else null))
            addView(text("$total", 16f, if (os) yellow else null))
        }
        grid.addView(if (os) totalTile else MaterialCardView(this).apply { addView(totalTile) },
            GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0; height = dp(56); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        val gridWrap = FrameLayout(this).apply {
            if (os) { setBackgroundResource(R.drawable.os_panel); setPadding(dp(8), dp(8), dp(8), dp(8)) }
            addView(grid)
        }
        box.addView(gridWrap)

        // Details per skill.
        for (s in skills) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(12))
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(context).apply { setImageResource(s.icon) }, LinearLayout.LayoutParams(dp(24), dp(24)))
                    addView(text(s.name, 18f, if (os) getColor(R.color.os_orange) else null).apply { setPadding(dp(8), 0, 0, 0) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text("Lvl ${s.level}", 16f, if (os) yellow else null))
                })
                addView(text("${nf.format(s.amount)} ${s.unit}" + if (s.note.isNotEmpty()) " · ${s.note}" else "", 14f,
                    if (os) Color.WHITE else null).apply { setPadding(0, dp(4), 0, dp(4)) })
                val base = Levels.xpForLevel(s.level)
                val pct = if (s.nextXp > base) ((s.xp - base) * 100L / (s.nextXp - base)).toInt() else 100
                addView(LinearProgressIndicator(context).apply {
                    max = 100; setProgressCompat(pct, false); trackThickness = dp(8)
                    if (os) { setIndicatorColor(getColor(R.color.os_green)); trackColor = 0xFF7F0000.toInt() }
                })
                addView(text("XP: ${nf.format(s.xp)}" + if (s.level < Levels.MAX_LEVEL) " · Next level at: ${nf.format(s.nextXp)}" else "",
                    13f, if (os) getColor(R.color.os_cyan) else null).apply { setPadding(0, dp(4), 0, 0) })
            }
            val wrapped: View = if (os) row.apply { setBackgroundResource(R.drawable.os_panel) } else MaterialCardView(this).apply { addView(row) }
            box.addView(wrapped, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })
        }
        box.addView(text("Secret notes don't count towards words.", 12f, if (os) getColor(R.color.os_text_dim) else null, shadow = os)
            .apply { setPadding(dp(6), dp(10), dp(6), dp(6)) })
    }
}
