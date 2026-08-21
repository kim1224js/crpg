package es.kim.crpg.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import es.kim.crpg.data.OwnedItemEntity
import es.kim.crpg.game.EquipmentOption

class EquipmentOptionDialog(private val context: Context) {
    private val gold = 0xFFD0A653.toInt()
    private val goldDark = 0xFF725322.toInt()
    private val leather = 0xE619120F.toInt()
    private val normalGradeColor = 0xFFE6E1D8.toInt()

    fun show(item: OwnedItemEntity, option: EquipmentOption) {
        val dialog = Dialog(context).apply { setCanceledOnTouchOutside(true) }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(20), dp(22))
            background = panel(leather, gold, 14f, 2)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label(item.displayName, Color.WHITE, 24f, true), LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(Button(context).apply {
            text = "×"
            contentDescription = "닫기"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            background = panel(0x804B2A20.toInt(), goldDark, 5f, 1)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(12) })
        panel.addView(header)
        panel.addView(label("노말", normalGradeColor, 16f, true).apply {
            setPadding(0, dp(2), 0, dp(12))
        })
        panel.addView(View(context).apply { setBackgroundColor(goldDark) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { bottomMargin = dp(14) })
        panel.addView(label("옵션", gold, 16f, true).apply { setPadding(0, 0, 0, dp(6)) })
        panel.addView(label(option.category, 0xFFBFAF95.toInt(), 14f).apply { setPadding(dp(10), dp(4), dp(10), dp(4)) })
        option.lines.forEach {
            panel.addView(label(it, Color.WHITE, 16f).apply { setPadding(dp(10), dp(7), dp(10), dp(7)) })
        }
        option.specialEffect?.let { effect ->
            panel.addView(label(effect, 0xFFFFD77A.toInt(), 15f).apply {
                setPadding(dp(10), dp(9), dp(10), dp(9)); background = panel(0xE63A2113.toInt(), goldDark, 8f, 1)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.setContentView(scroll)
        val maximumHeight = minOf(dp(560), (context.resources.displayMetrics.heightPixels * 0.86f).toInt())
        dialog.setOnShowListener { dialog.window?.setLayout(dp(430), maximumHeight) }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); setDimAmount(0.72f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setLayout(dp(430), maximumHeight)
        }
    }

    private fun label(value: String, color: Int, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; setTextColor(color); textSize = size; if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun panel(fill: Int, stroke: Int, radius: Float, width: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).toInt()
}
