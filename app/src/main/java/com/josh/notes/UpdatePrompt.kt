package com.josh.notes

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Non-blocking update prompt laid over the bottom of the screen: "Update available" + Update / Later. */
object UpdatePrompt {
    const val TAG = "josh_update_prompt"

    fun show(a: Activity, m: UpdateController.Manifest, bottomOffset: Int = 0, onUpdate: () -> Unit): View? {
        val content = a.findViewById<FrameLayout>(android.R.id.content) ?: return null
        content.findViewWithTag<View>(TAG)?.let { content.removeView(it) }   // never stack two prompts
        val os = AppSettings.oldSchool(a)
        fun dp(v: Float) = (v * a.resources.displayMetrics.density).toInt()
        val panel = LinearLayout(a).apply {
            tag = TAG; orientation = LinearLayout.VERTICAL; isClickable = true
            if (os) { setBackgroundResource(R.drawable.os_chatbox_carved); setPadding(dp(14f), dp(12f), dp(14f), dp(10f)) }
            else { setBackgroundResource(R.drawable.update_prompt_bg); setPadding(dp(16f), dp(14f), dp(16f), dp(8f)); elevation = dp(6f).toFloat() }
        }
        val title = TextView(a).apply {
            text = "Update available: v${m.versionName}"; textSize = 17f; gravity = if (os) Gravity.CENTER else Gravity.START
            if (os) { typeface = com.josh.oldschool.Os.font(a, true); setTextColor(a.getColor(R.color.os_dialog_red)); setShadowLayer(0f, 0f, 0f, 0) }
        }
        panel.addView(title)
        if (m.notes.isNotBlank()) panel.addView(TextView(a).apply {
            text = m.notes; textSize = 14f; maxLines = 3; gravity = title.gravity; setPadding(0, dp(4f), 0, dp(4f))
            if (os) { typeface = com.josh.oldschool.Os.font(a); setTextColor(a.getColor(R.color.os_chat_blue)); setShadowLayer(0f, 0f, 0f, 0) }
        })
        val row = LinearLayout(a).apply { gravity = if (os) Gravity.CENTER else Gravity.END }
        fun dismiss() { (panel.parent as? ViewGroup)?.removeView(panel) }
        fun btn(t: String, primary: Boolean, click: () -> Unit) = MaterialButton(a, null,
            if (os || primary) com.google.android.material.R.attr.materialButtonStyle else androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = t; setOnClickListener { dismiss(); click() }
        }
        row.addView(btn("Later", false) {}, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8f) })
        row.addView(btn("Update", true, onUpdate))
        panel.addView(row)
        content.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(10f), 0, dp(10f), bottomOffset + dp(12f) + navInset(content))
        })
        return panel
    }

    private fun navInset(v: View): Int = androidx.core.view.ViewCompat.getRootWindowInsets(v)
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
}
