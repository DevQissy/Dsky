package com.devqissy.dsky.util

import android.os.Build
import android.view.View
import androidx.fragment.app.Fragment

/**
 * Utility class for dealing with Right-to-Left locales.
 */
internal object Rtl {
    /**
     * @param fragment The UI element to analyze
     * @return True if the UI is configured to use a Right-to-Left locale.
     */
    fun isRtl(fragment: Fragment): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return false
        val layoutDirection = fragment.resources.configuration.layoutDirection
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}