package es.kim.crpg.ui.login

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
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

        nameInput.apply {
            hint = "이름"
            setTextColor(Color.WHITE)
            setHintTextColor(0xCCFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
        }
        addView(nameInput)

        loginButton.apply {
            text = "로그인"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            stateListAnimator = null
            setPadding(0, 0, 0, 0)
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
        place(autoLoginCheckBox, offsetX, offsetY, scale, 505f, 474f, 270f, 36f)
        place(deathNoticeText, offsetX, offsetY, scale, 300f, 392f, 680f, 78f)
        place(nameInput, offsetX, offsetY, scale, 400f, 515f, 480f, 75f)
        place(loginButton, offsetX, offsetY, scale, 510f, 625f, 260f, 60f)
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
