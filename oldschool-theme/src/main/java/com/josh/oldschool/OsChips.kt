package com.josh.oldschool

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.chip.Chip

/**
 * Code-built Old School chips (Material's Chip has no style-resource constructor). In XML just use
 * `style="@style/Widget.OldSchool.Chip"`, `.Choice` or `.Filter`.
 */
object OsChips {
    /** Plain / assist chip. */
    @JvmStatic fun assist(ctx: Context, text: CharSequence): Chip = make(ctx, R.style.ThemeOverlay_OldSchool_Chip, text)
    /** Single-select chip (no check mark; red + white text when checked). Put in a ChipGroup with singleSelection. */
    @JvmStatic fun choice(ctx: Context, text: CharSequence): Chip = make(ctx, R.style.ThemeOverlay_OldSchool_Chip_Choice, text)
    /** Multi-select chip (pixel check mark when checked). */
    @JvmStatic fun filter(ctx: Context, text: CharSequence): Chip = make(ctx, R.style.ThemeOverlay_OldSchool_Chip_Filter, text)

    private fun make(ctx: Context, overlay: Int, text: CharSequence) =
        Chip(ContextThemeWrapper(ctx, overlay), null, com.google.android.material.R.attr.chipStyle).apply { this.text = text }
}
