package es.kim.crpg.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import es.kim.crpg.ui.inventory.HexagonSlotView

fun Context.sectionTitle(title: String): TextView = TextView(this).apply {
    text = title
    setTextColor(GameUiTheme.GOLD)
    textSize = 18f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(6), 0, 0, 0)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34))
}

fun Context.itemImage(assetPath: String): ImageView = ImageView(this).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
    setImageBitmap(assets.open(assetPath).use(BitmapFactory::decodeStream))
}

fun Context.quantityBadge(value: String): TextView = TextView(this).apply {
    text = value
    setTextColor(Color.WHITE)
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
}

fun Context.itemQuantityLayoutParams(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
    dp(38), dp(24), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
).apply { bottomMargin = dp(3) }

fun Context.itemGridHeight(rows: Int): Int {
    val slotHeight = (dp(64) * HexagonSlotView.HEX_HEIGHT_RATIO).toInt()
    return rows * (slotHeight + dp(8)) + dp(4)
}

fun Context.antiqueButton(label: String, width: Int, height: Int): Button = Button(this).apply {
    text = label
    setTextColor(Color.WHITE)
    textSize = 15f
    gravity = Gravity.CENTER
    minWidth = 0
    minHeight = 0
    setPadding(0, 0, 0, 0)
    stateListAnimator = null
    background = antiquePanel(0xFF5B2418.toInt(), GameUiTheme.GOLD, 7f, 2)
    layoutParams = LinearLayout.LayoutParams(width, height)
}

fun Context.antiquePanel(fillColor: Int, strokeColor: Int, radiusDp: Float, strokeDp: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeDp), strokeColor)
    }

fun Context.matchParentParams(marginDp: Int = 0): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.MATCH_PARENT
).apply { setMargins(marginDp, marginDp, marginDp, marginDp) }

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
