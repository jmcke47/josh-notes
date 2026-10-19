package com.josh.notes

import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        // Theme
        val themeGroup = findViewById<RadioGroup>(R.id.theme_group)
        val theme = AppSettings.theme(this)
        ThemeMode.entries.forEach { t ->
            themeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = t.label; isChecked = t == theme
                setOnClickListener { if (AppSettings.theme(this@SettingsActivity) != t) AppSettings.setTheme(this@SettingsActivity, t) }
            })
        }
        findViewById<MaterialSwitch>(R.id.dynamic_color).apply {
            if (!DynamicColors.isDynamicColorAvailable()) { isEnabled = false; text = "$text — not supported on this phone" }
            isChecked = AppSettings.dynamicColor(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                AppSettings.setDynamicColor(this@SettingsActivity, on)
                MainActivity.themeChanged = true
                recreate()
            }
        }

        // Volume trigger
        val triggers = findViewById<RadioGroup>(R.id.trigger_group)
        val current = DictationTrigger.get(this)
        DictationTrigger.entries.forEach { t ->
            triggers.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = t.label; isChecked = t == current
                setOnClickListener { DictationTrigger.set(this@SettingsActivity, t) }
            })
        }

        // Advanced (hidden-ish): update source URL
        val updater = UpdateController(this)
        findViewById<View>(R.id.advanced_toggle).setOnClickListener {
            val box = findViewById<View>(R.id.advanced_box)
            box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.update_url_btn).setOnClickListener { updater.editUpdateUrl() }
        findViewById<android.widget.TextView>(R.id.version_text).text =
            "Josh Notes v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        // Engine
        val dictPrefs = getSharedPreferences("dictation", 0)
        val engine = dictPrefs.getString("engine", null) ?: "AUTO"
        val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val modelDir = File(filesDir, DictationController.MODEL_NAME)
        val engines = listOf(
            "AUTO" to "Automatic (Android on-device recognizer if available, else Vosk)",
            "SYSTEM" to "Android on-device recognizer" + if (onDevice) " (available)" else " (not on this phone; offline-preferred)",
            "VOSK" to "Vosk, fully offline (one-time ${DictationController.MODEL_SIZE_MB} MB download)",
        )
        val group = findViewById<RadioGroup>(R.id.engine_group)
        engines.forEach { (key, label) ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = label; isChecked = key == engine
                setOnClickListener { dictPrefs.edit { putString("engine", key) } }
            })
        }
        findViewById<View>(R.id.delete_model).apply {
            isEnabled = modelDir.exists()
            setOnClickListener {
                modelDir.deleteRecursively(); isEnabled = false
                Toast.makeText(this@SettingsActivity, "Offline voice model deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
