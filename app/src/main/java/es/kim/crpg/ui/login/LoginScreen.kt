package es.kim.crpg.ui.login

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min

class LoginScreen(
    context: Context,
    onLogin: () -> Unit
) : FrameLayout(context) {
    val nameInput = EditText(context)
    val autoLoginCheckBox = CheckBox(context)
    val deathNoticeText = TextView(context)
    private val namePlaceholder = TextView(context)
    private val loginButton = TextView(context)

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            setImageBitmap(context.assets.open("ui/login/login_screen.png").use(BitmapFactory::decodeStream))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        nameInput.apply {
            contentDescription = "이름 입력"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            elevation = 4f
            setOnClickListener { focusNameInput() }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onLogin()
                    true
                } else {
                    false
                }
            }
        }
        addView(nameInput, LayoutParams(1, 1))

        namePlaceholder.apply {
            text = "이름"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            elevation = 5f
            isClickable = true
            setOnClickListener { focusNameInput() }
        }
        addView(namePlaceholder, LayoutParams(1, 1))
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                namePlaceholder.visibility = if (value.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })

        loginButton.apply {
            text = "로그인"
            contentDescription = "로그인"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            elevation = 5f
            background = ColorDrawable(Color.TRANSPARENT)
            setOnClickListener { onLogin() }
        }
        addView(loginButton, LayoutParams(1, 1))

        autoLoginCheckBox.isChecked = true
        autoLoginCheckBox.visibility = View.GONE
        addView(autoLoginCheckBox, LayoutParams(1, 1))

        deathNoticeText.apply {
            visibility = View.GONE
            setTextColor(0xFFFFB7A8.toInt())
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        addView(deathNoticeText, LayoutParams(1, 1))

        nameInput.clearFocus()
        requestFocus()
    }

    fun focusNameInput() {
        nameInput.requestFocus()
        nameInput.post {
            val inputMethodManager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(nameInput, 0)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        val offsetX = (width - DESIGN_WIDTH * scale) / 2f
        val offsetY = (height - DESIGN_HEIGHT * scale) / 2f

        place(nameInput, offsetX, offsetY, scale, 390f, 505f, 500f, 88f)
        place(namePlaceholder, offsetX, offsetY, scale, 390f, 505f, 500f, 88f)
        place(loginButton, offsetX, offsetY, scale, 495f, 612f, 290f, 82f)
        place(deathNoticeText, offsetX, offsetY, scale, 300f, 405f, 680f, 82f)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun place(
        view: View,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
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
