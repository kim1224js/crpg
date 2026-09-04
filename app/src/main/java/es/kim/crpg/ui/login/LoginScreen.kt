package es.kim.crpg.ui.login

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import es.kim.crpg.ui.common.GameUiTheme
import kotlin.math.min

class LoginScreen(
    context: Context,
    onLogin: () -> Unit
) : FrameLayout(context) {
    val nameInput = EditText(context)
    val autoLoginCheckBox = CheckBox(context)
    val deathNoticeText = TextView(context)
    private val nameLabel = TextView(context)
    private val loginButton = Button(context)

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(context.assets.open("ui/login/login_screen.png").use(BitmapFactory::decodeStream))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        nameLabel.apply {
            text = "이름"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
        }
        addView(nameLabel)

        nameInput.apply {
            hint = "모험가 이름 입력"
            setTextColor(Color.WHITE)
            setHintTextColor(0xB3FFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setSingleLine(true)
            background = framedBackground(0xDD100C0A.toInt(), GameUiTheme.GOLD_DARK, 2f, 9f)
            setPadding(18, 0, 18, 0)
            elevation = 8f
        }
        addView(nameInput)

        loginButton.apply {
            text = "로그인"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = framedBackground(0xE02A1710.toInt(), GameUiTheme.GOLD, 2f, 10f)
            stateListAnimator = null
            setPadding(0, 0, 0, 0)
            elevation = 8f
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            setOnClickListener { onLogin() }
        }
        addView(loginButton)

        autoLoginCheckBox.apply {
            text = "자동 로그인"
            setTextColor(Color.WHITE)
            textSize = 15f
            buttonTintList = ColorStateList.valueOf(GameUiTheme.GOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        addView(autoLoginCheckBox)

        deathNoticeText.apply {
            visibility = View.GONE
            setTextColor(0xFFFFB7A8.toInt())
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        addView(deathNoticeText)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        val offsetX = (width - DESIGN_WIDTH * scale) / 2f
        val offsetY = (height - DESIGN_HEIGHT * scale) / 2f
        place(autoLoginCheckBox, offsetX, offsetY, scale, 505f, 455f, 270f, 36f)
        place(deathNoticeText, offsetX, offsetY, scale, 300f, 392f, 680f, 78f)
        place(nameLabel, offsetX, offsetY, scale, 400f, 492f, 480f, 28f)
        place(nameInput, offsetX, offsetY, scale, 400f, 515f, 480f, 75f)
        place(loginButton, offsetX, offsetY, scale, 510f, 625f, 260f, 60f)
    }

    private fun framedBackground(fill: Int, stroke: Int, strokeWidth: Float, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(strokeWidth.toInt().coerceAtLeast(1), stroke)
            cornerRadius = radius
        }

    private fun place(view: View, offsetX: Float, offsetY: Float, scale: Float, x: Float, y: Float, width: Float, height: Float) {
        view.layoutParams = LayoutParams((width * scale).toInt(), (height * scale).toInt()).apply {
            leftMargin = (offsetX + x * scale).toInt()
            topMargin = (offsetY + y * scale).toInt()
        }
    }

    private companion object {
        const val DESIGN_WIDTH = 1280f
        const val DESIGN_HEIGHT = 720f
    }
}
