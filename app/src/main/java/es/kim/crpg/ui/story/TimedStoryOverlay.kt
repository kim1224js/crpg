package es.kim.crpg.ui.story

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
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
        val compactGameOver = gameOver && activity.resources.displayMetrics.heightPixels < activity.dp(500)
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
            val horizontalPadding = if (compactGameOver) 26 else 42
            val verticalPadding = if (compactGameOver) 16 else if (gameOver) 24 else 28
            setPadding(activity.dp(horizontalPadding), activity.dp(verticalPadding), activity.dp(horizontalPadding), activity.dp(verticalPadding))
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
            textSize = if (compactGameOver) 27f else if (gameOver) 32f else 29f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (gameOver) {
                letterSpacing = .12f
                setShadowLayer(activity.dp(5).toFloat(), 0f, activity.dp(3).toFloat(), Color.BLACK)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(if (compactGameOver) 42 else if (gameOver) 52 else 48)).apply {
            bottomMargin = activity.dp(if (compactGameOver) 6 else 10)
        })

        val lineViews = config.lines.mapIndexed { index, line ->
            TextView(activity).apply {
                text = line
                setTextColor(lineColor(gameOver, index, config.lines.lastIndex))
                textSize = if (compactGameOver) {
                    if (index == 0) 15f else 14f
                } else if (gameOver && index == 0) 18f else if (!gameOver && index == config.lines.lastIndex) 17f else 16f
                typeface = if (index == 0 && gameOver || index == config.lines.lastIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                alpha = 0f
                if (gameOver) translationY = -activity.dp(10).toFloat()
                setLineSpacing(activity.dp(if (compactGameOver) 2 else if (gameOver) 4 else 3).toFloat(), 1f)
                panel.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = activity.dp(if (!gameOver && index == config.lines.lastIndex) 0 else if (compactGameOver) 6 else if (gameOver) 10 else 12) })
            }
        }
        val prompt = TextView(activity).apply {
            text = config.prompt
            setTextColor(GameUiTheme.GOLD)
            textSize = if (compactGameOver) 13f else 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            alpha = 0f
            letterSpacing = if (gameOver) .05f else .08f
        }
        panel.addView(prompt, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(if (compactGameOver) 32 else if (gameOver) 38 else 38)
        ).apply { topMargin = activity.dp(if (compactGameOver) 4 else if (gameOver) 6 else 18) })

        val scroll = activity.gameScrollView(panel, showScrollbar = false)
        val panelWidth = minOf(
            activity.dp(if (gameOver) 680 else 900),
            activity.resources.displayMetrics.widthPixels - activity.dp(if (gameOver) 32 else 70)
        )
        val panelHeight = minOf(
            activity.dp(if (gameOver) 540 else 680),
            activity.resources.displayMetrics.heightPixels - activity.dp(if (gameOver) 24 else 40)
        )
        overlay.addView(scroll, FrameLayout.LayoutParams(
            panelWidth,
            panelHeight,
            Gravity.CENTER
        ))

        var finishing = false
        fun finishStory() {
            if (finishing) return
            finishing = true
            overlay.isClickable = false
            lineViews.forEach { it.animate().cancel() }
            prompt.animate().cancel()
            overlay.animate().alpha(0f).setDuration(180L).withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                config.onFinished()
            }.start()
        }

        if (gameOver) {
            overlay.addView(View(activity).apply {
                contentDescription = "사망 화면 닫기"
                isClickable = true
                isFocusable = true
                setOnClickListener { finishStory() }
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        overlay.addView(TextView(activity).apply {
                text = "×"
                contentDescription = if (gameOver) "사망 화면 닫기" else "프롤로그 닫기"
                setTextColor(0xFFFFE1A6.toInt())
                textSize = if (compactGameOver) 25f else 30f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = activity.antiquePanel(
                    0xE61A100C.toInt(),
                    GameUiTheme.GOLD_DARK,
                    10f,
                    1
                )
                elevation = activity.dp(20).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { finishStory() }
            }, FrameLayout.LayoutParams(
                activity.dp(if (compactGameOver) 42 else 52),
                activity.dp(if (compactGameOver) 42 else 52),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = (activity.resources.displayMetrics.heightPixels - panelHeight) / 2 + activity.dp(8)
                marginEnd = (activity.resources.displayMetrics.widthPixels - panelWidth) / 2 + activity.dp(8)
            })
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
        var revealedAll = false
        overlay.postDelayed({
            revealedAll = true
            prompt.animate().alpha(1f).setDuration(700L).start()
        }, (config.lines.lastIndex * 1_000L) + lineFadeDuration)
        overlay.setOnClickListener {
            if (finishing) return@setOnClickListener
            if (!gameOver && !revealedAll) {
                revealedAll = true
                lineViews.forEach { lineView ->
                    lineView.animate().cancel()
                    lineView.alpha = 1f
                    lineView.translationY = 0f
                }
                prompt.animate().cancel()
                prompt.text = "한 번 더 터치하여 시작"
                prompt.alpha = 1f
                scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
                return@setOnClickListener
            }
            finishStory()
        }
    }

    private fun lineColor(gameOver: Boolean, index: Int, lastIndex: Int): Int = when {
        gameOver && index == 0 -> 0xFFFFA099.toInt()
        index == lastIndex -> if (gameOver) GameUiTheme.GOLD else 0xFFFFD58A.toInt()
        else -> Color.WHITE
    }
}
