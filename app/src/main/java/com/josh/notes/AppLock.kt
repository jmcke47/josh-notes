package com.josh.notes

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.josh.notes.widget.NotesWidgetProvider

/** Optional app lock (fingerprint/face or screen lock PIN/pattern). */
object AppLock {
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private const val GRACE_MS = 30_000L   // brief trips to share sheet / file picker don't re-lock

    @Volatile private var unlocked = false
    @Volatile private var backgroundSince = 0L
    private var started = 0

    fun enabled(ctx: Context) = ctx.getSharedPreferences("settings", 0).getBoolean("app_lock", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("settings", 0).edit().putBoolean("app_lock", on).apply()
        if (on) unlocked = true // the user just authenticated to turn it on
        NotesWidgetProvider.refreshAll(ctx)
    }

    fun available(ctx: Context) =
        BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun needsUnlock(ctx: Context) = enabled(ctx) && !unlocked

    fun markUnlocked() { unlocked = true }

    // Called from NotesApp's ActivityLifecycleCallbacks
    fun onActivityStarted() {
        if (started == 0 && backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > GRACE_MS) unlocked = false
        started++
    }
    fun onActivityStopped() {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) backgroundSince = SystemClock.elapsedRealtime()
    }

    fun prompt(activity: AppCompatActivity, title: String, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail() }
        })
        p.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Josh Notes")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build())
    }
}

/** Base for screens that show note content: covers the UI until unlocked when App lock is on. */
open class LockableActivity : AppCompatActivity() {
    private var cover: View? = null

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (AppLock.needsUnlock(this)) showCover()
    }

    override fun onResume() {
        super.onResume()
        // Auto-prompt once per lock; after a cancel the user taps "Unlock" (avoids a re-prompt loop).
        if (AppLock.needsUnlock(this)) { showCover(); if (!autoPrompted) { autoPrompted = true; requestUnlock() } }
        else hideCover()
    }

    private var prompting = false
    private var autoPrompted = false
    private fun requestUnlock() {
        if (prompting) return
        prompting = true
        AppLock.prompt(this, "Unlock Josh Notes",
            onSuccess = { prompting = false; AppLock.markUnlocked(); hideCover(); onUnlocked() },
            onFail = { prompting = false })
    }

    /** Hook for subclasses (e.g. refresh content after unlock). */
    open fun onUnlocked() {}

    private fun showCover() {
        if (cover != null) return
        val root = window.decorView as ViewGroup
        val pad = (24 * resources.displayMetrics.density).toInt()
        val v = FrameLayout(this).apply {
            setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            isClickable = true; isFocusable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(pad, pad, pad, pad)
                addView(ImageView(context).apply { setImageResource(R.drawable.ic_lock)
                    layoutParams = LinearLayout.LayoutParams(pad * 3, pad * 3) })
                addView(TextView(context).apply { text = getString(R.string.locked_title)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
                    setPadding(0, pad / 2, 0, pad / 2) })
                addView(MaterialButton(context).apply { text = getString(R.string.unlock); setOnClickListener { requestUnlock() } })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        root.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        cover = v
    }

    private fun hideCover() {
        cover?.let { (it.parent as? ViewGroup)?.removeView(it) }
        cover = null
        autoPrompted = false
    }
}
