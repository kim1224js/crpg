package es.kim.crpg.ui.story

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.androidgamesdk.GameActivity
import es.kim.crpg.ui.common.GameUiTheme
import es.kim.crpg.ui.common.antiquePanel
import es.kim.crpg.ui.common.dp
import es.kim.crpg.ui.common.gameScrollView

object TimedStoryOverlay {
    enum class Style { PROLOGUE, GAME_OVER }

    data class Config(
        val title: String,
        val lines: List<String>,
        val prompt: String,
        val style: Style,
        val onFinished: () -> Unit
    )

    fun show(activity: GameActivity, config: Config) {
        val gameOver = config.style == Style.GAME_OVER
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(if (gameOver) 0xE6000000.toInt() else Color.BLACK)
            isClickable = true
            isFocusable = true
        }
        if (!gameOver) {
            overlay.addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = .24f
                setImageBitmap(activity.assets.open("ui/login/login_screen.png").use(BitmapFactory::decodeStream))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(activity.dp(42), activity.dp(if (gameOver) 30 else 28), activity.dp(42), activity.dp(28))
            background = activity.antiquePanel(
                if (gameOver) 0xF5170C0B.toInt() else 0xE8120D0A.toInt(),
                if (gameOver) 0xFF7D2925.toInt() else GameUiTheme.GOLD_DARK,
                if (gameOver) 14f else 12f,
                if (gameOver) 2 else 1
            )
            if (gameOver) elevation = activity.dp(18).toFloat()
        }
        panel.addView(TextView(activity).apply {
            text = config.title
            setTextColor(if (gameOver) 0xFFCF3F38.toInt() else GameUiTheme.GOLD)
            textSize = if (gameOver) 34f else 29f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (gameOver) {
                letterSpacing = .12f
                setShadowLayer(activity.dp(5).toFloat(), 0f, activity.dp(3).toFloat(), Color.BLACK)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(if (gameOver) 58 else 48)).apply {
            bottomMargin = activity.dp(14)
        })

        val lineViews = config.lines.mapIndexed { index, line ->
            TextView(activity).apply {
                text = line
                setTextColor(lineColor(gameOver, index, config.lines.lastIndex))
                textSize = if (gameOver && index == 0) 18f else if (!gameOver && index == config.lines.lastIndex) 17f else 16f
                typeface = if (index == 0 && gameOver || index == config.lines.lastIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                alpha = 0f
                if (gameOver) translationY = -activity.dp(10).toFloat()
                setLineSpacing(activity.dp(if (gameOver) 4 else 3).toFloat(), 1f)
                panel.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = activity.dp(if (!gameOver && index == config.lines.lastIndex) 0 else if (gameOver) 14 else 12) })
            }
        }
        val prompt = TextView(activity).apply {
            text = config.prompt
            setTextColor(GameUiTheme.GOLD)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            alpha = 0f
            letterSpacing = if (gameOver) .05f else .08f
        }
        panel.addView(prompt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(if (gameOver) 42 else 38)
        ).apply { topMargin = activity.dp(if (gameOver) 8 else 18) })

        val scroll = activity.gameScrollView(panel, showScrollbar = false)
        overlay.addView(scroll, FrameLayout.LayoutParams(
            minOf(activity.dp(if (gameOver) 780 else 900), activity.resources.displayMetrics.widthPixels - activity.dp(70)),
            activity.resources.displayMetrics.heightPixels - activity.dp(if (gameOver) 36 else 40),
            Gravity.CENTER
        ))
        activity.addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.bringToFront()

        val lineFadeDuration = if (gameOver) 1_350L else 1_550L
        lineViews.forEachIndexed { index, lineView ->
            lineView.postDelayed({
                if (overlay.parent != null) {
                    lineView.animate().alpha(1f).translationY(0f).setDuration(lineFadeDuration).start()
                    scroll.post { scroll.smoothScrollTo(0, lineView.bottom) }
                }
            }, index * 1_000L)
        }
        var enabled = false
        overlay.postDelayed({
            enabled = true
            prompt.animate().alpha(1f).setDuration(700L).start()
        }, (config.lines.lastIndex * 1_000L) + lineFadeDuration)
        overlay.setOnClickListener {
            if (!enabled) return@setOnClickListener
            enabled = false
            overlay.isClickable = false
            overlay.animate().alpha(0f).setDuration(800L).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                config.onFinished()
            }.start()
        }
    }

    private fun lineColor(gameOver: Boolean, index: Int, lastIndex: Int): Int = when {
        gameOver && index == 0 -> 0xFFFFA099.toInt()
        index == lastIndex -> if (gameOver) GameUiTheme.GOLD else 0xFFFFD58A.toInt()
        else -> Color.WHITE
    }
}
