package es.kim.crpg.ui.login

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

class LoginScreen(
    context: Context,
    onLogin: () -> Unit
) : FrameLayout(context) {
    val nameInput = EditText(context)
    val autoLoginCheckBox = CheckBox(context)
    val deathNoticeText = TextView(context)
    private val deathNoticeCard = LinearLayout(context)
    private val deathCauseText = TextView(context)
    private val deathHeirText = TextView(context)
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

        deathNoticeCard.apply {
            visibility = View.GONE
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xF218100D.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), 0xFFD0A653.toInt())
            }
            addView(TextView(context).apply {
                text = "◆  가문의 기록  ◆"
                setTextColor(0xFFFFD786.toInt())
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(27)))
            addView(deathCauseText.apply {
                setTextColor(0xFFFFA392.toInt())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(25)))
            addView(View(context).apply { setBackgroundColor(0xFF725322.toInt()) }, LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(3); bottomMargin = dp(5) })
            addView(ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(deathNoticeText.apply {
                    setTextColor(0xFFF4E8D3.toInt())
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(dp(8), 0, dp(8), 0)
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(deathHeirText.apply {
                setTextColor(0xFFD7C39D.toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 2
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(5) })
        }
        addView(deathNoticeCard, LayoutParams(1, 1))

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

    fun showDeathNotice(deceasedName: String, generation: Int, cause: String, message: String) {
        deathCauseText.text = "사망 원인  ·  $cause"
        deathNoticeText.text = message
        deathHeirText.text = "${generation}세 ${deceasedName}의 뜻을 이을 후계자 이름을 입력하세요"
        deathNoticeCard.visibility = View.VISIBLE
    }

    fun hideDeathNotice() {
        deathNoticeCard.visibility = View.GONE
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
        place(deathNoticeCard, offsetX, offsetY, scale, 270f, 292f, 740f, 190f)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DESIGN_WIDTH = 1280f
        const val DESIGN_HEIGHT = 720f
    }
}
