package com.josh.oldschool

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BottomNavigationView that matches the side tabs exactly: defaults to `Widget.OldSchool.BottomNav` (stone squares,
 * red when checked, darker pressed, no ripple/indicator) and leaves full-colour pixel icons untinted.
 * (Material ignores `itemIconTint="@null"` in a style, so the untinting needs code: this class or [style].)
 * XML: `<com.josh.oldschool.OsBottomNav ... app:menu="@menu/x" />`; `style="@style/Widget.OldSchool.BottomNav.Labeled"`
 * for labels. Setting `app:itemIconTint` (e.g. `@color/os_bottom_nav_icon_tint` for vector icons) keeps that tint.
 */
open class OsBottomNav @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    BottomNavigationView(ctx, attrs, 0, R.style.Widget_OldSchool_BottomNav) {
    init {
        val tinted = attrs != null && (0 until attrs.attributeCount).any { attrs.getAttributeName(it) == "itemIconTint" }
        if (!tinted) itemIconTintList = null
        if (attrs == null || (0 until attrs.attributeCount).none { attrs.getAttributeName(it) == "background" }) panel(this)
    }

    companion object {
        private fun panel(nav: BottomNavigationView) {
            nav.backgroundTintList = null
            nav.background = androidx.core.content.ContextCompat.getDrawable(nav.context, R.drawable.os_side_panel)
        }

        /** Restyle an existing BottomNavigationView (e.g. inflated with `style="@style/Widget.OldSchool.BottomNav"`):
         *  untinted pixel icons, side-tab item background, no ripple or active indicator. */
        @JvmStatic @JvmOverloads fun style(nav: BottomNavigationView, labeled: Boolean = false): BottomNavigationView = nav.apply {
            itemIconTintList = null
            panel(this)
            itemBackground = androidx.core.content.ContextCompat.getDrawable(context,
                if (labeled) R.drawable.os_bottom_nav_item_fill else R.drawable.os_bottom_nav_item)
            itemRippleColor = null
            isItemActiveIndicatorEnabled = false
            labelVisibilityMode = if (labeled) LABEL_VISIBILITY_LABELED else LABEL_VISIBILITY_UNLABELED
        }
    }
}
