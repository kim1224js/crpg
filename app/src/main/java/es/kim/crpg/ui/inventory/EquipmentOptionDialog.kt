package es.kim.crpg.ui.inventory

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
import android.widget.TextView
import es.kim.crpg.data.OwnedItemEntity
import es.kim.crpg.game.catalog.EquipmentOption
import es.kim.crpg.ui.common.gameScrollView

class EquipmentOptionDialog(private val context: Context) {
    private val gold = 0xFFD0A653.toInt()
    private val goldDark = 0xFF725322.toInt()
    private val leather = 0xE619120F.toInt()
    private val normalGradeColor = 0xFFE6E1D8.toInt()

    fun show(
        item: OwnedItemEntity,
        option: EquipmentOption,
        grade: String = "NORMAL",
        baseAttackPower: Int = 0,
        baseDurability: Int = 3,
        appraisedAttackPower: Int? = null,
        salePrice: Int? = null,
        onSell: (() -> Unit)? = null
    ) {
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
        panel.addView(label(gradeName(grade), gradeColor(grade), 16f, true).apply {
            setPadding(0, dp(2), 0, dp(12))
        })
        panel.addView(View(context).apply { setBackgroundColor(goldDark) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { bottomMargin = dp(14) })
        panel.addView(label("옵션", gold, 16f, true).apply { setPadding(0, 0, 0, dp(6)) })
        panel.addView(label(option.category, 0xFFBFAF95.toInt(), 14f).apply { setPadding(dp(10), dp(4), dp(10), dp(4)) })
        val attackDelta = appraisedAttackPower?.minus(baseAttackPower)
        val durabilityDelta = item.durability - baseDurability
        val durabilityChange = if (durabilityDelta > 0) "  ▲ +${durabilityDelta}" else ""
        panel.addView(label("원정 수명  ${item.dungeonUseCount} / ${item.durability}회$durabilityChange", 0xFFD7C49C.toInt(), 15f).apply {
            setPadding(dp(10), dp(5), dp(10), dp(7))
        })
        option.lines.forEach { line ->
            val displayedLine = if (line.startsWith("공격력") && appraisedAttackPower != null) {
                val marker = when {
                    attackDelta == null || attackDelta == 0 -> ""
                    attackDelta > 0 -> "  ▲ +$attackDelta"
                    else -> "  ▼ $attackDelta"
                }
                "공격력  $appraisedAttackPower$marker"
            } else line
            panel.addView(label(displayedLine, Color.WHITE, 16f).apply { setPadding(dp(10), dp(7), dp(10), dp(7)) })
        }
        option.specialEffect?.let { effect ->
            panel.addView(label(effect, 0xFFFFD77A.toInt(), 15f).apply {
                setPadding(dp(10), dp(9), dp(10), dp(9)); background = panel(0xE63A2113.toInt(), goldDark, 8f, 1)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        if (salePrice != null && onSell != null) {
            panel.addView(Button(context).apply {
                text = "판매  ${salePrice}G"
                setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                minHeight = 0; stateListAnimator = null
                background = panel(0xFF5B2418.toInt(), gold, 7f, 2)
                setOnClickListener { dialog.dismiss(); onSell() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(14) })
        }
        val scroll = context.gameScrollView(panel)
        dialog.setContentView(scroll)
        val maximumHeight = minOf(dp(560), (context.resources.displayMetrics.heightPixels * 0.86f).toInt())
        val maximumWidth = minOf(dp(430), context.resources.displayMetrics.widthPixels - dp(24))
        dialog.setOnShowListener { dialog.window?.setLayout(maximumWidth, maximumHeight) }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); setDimAmount(0.72f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setLayout(maximumWidth, maximumHeight)
        }
    }

    private fun label(value: String, color: Int, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; setTextColor(color); textSize = size; if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun gradeName(grade: String) = when (grade) {
        "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"; "UNIQUE" -> "유니크"
        "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> "노말"
    }
    private fun gradeColor(grade: String) = when (grade) {
        "HIGH" -> 0xFF65D57A.toInt(); "RARE" -> 0xFF61AFFF.toInt(); "EPIC" -> 0xFFC36BFF.toInt()
        "UNIQUE" -> 0xFFFFAD45.toInt(); "LEGENDARY" -> 0xFFFF5959.toInt(); "MYTHIC" -> 0xFFFFE586.toInt()
        else -> normalGradeColor
    }
    private fun panel(fill: Int, stroke: Int, radius: Float, width: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(width), stroke)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).toInt()
}
