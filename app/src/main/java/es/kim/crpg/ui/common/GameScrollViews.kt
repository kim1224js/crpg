package es.kim.crpg.ui.common

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView

fun Context.gameScrollView(
    content: View? = null,
    showScrollbar: Boolean = true,
    fillViewport: Boolean = true
): ScrollView = ScrollView(this).apply {
    isFillViewport = fillViewport
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    isVerticalScrollBarEnabled = showScrollbar
    isScrollbarFadingEnabled = !showScrollbar
    clipToPadding = false
    content?.let {
        addView(it, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }
}
