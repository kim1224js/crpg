package es.kim.crpg.ui.common

import android.app.Dialog
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.LinearInterpolator

object AntiqueGameDialog {
    data class Action(
        val label: String,
        val primary: Boolean = false,
        val onClick: () -> Unit = {}
    )

    data class Config(
        val title: String,
        val subtitle: String? = null,
        val body: String,
        val warning: String? = null,
        val actions: List<Action>,
        val cancelable: Boolean = true,
        val actionsAboveBody: Boolean = false,
        val scrollHint: String? = null,
        val autoScrollBody: Boolean = false,
        val autoScrollDurationMs: Long = 12_000L,
        val bodyHeightDp: Int = 280,
        val onCancel: (() -> Unit)? = null
    )

    fun show(context: Context, config: Config): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(config.cancelable)
        dialog.setCanceledOnTouchOutside(config.cancelable)

        val root = FrameLayout(context).apply {
            setPadding(dp(context, 22), dp(context, 18), dp(context, 22), dp(context, 18))
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 26), dp(context, 18), dp(context, 26), dp(context, 22))
            background = panel(0xFA130E0B.toInt(), GameUiTheme.GOLD_DARK, 15f, 2, context)
            elevation = dp(context, 18).toFloat()
        }
        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
        ))

        panel.addView(TextView(context).apply {
            text = "◆  CRPG  ◆"
            setTextColor(0xFF8E6A31.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = .18f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 24)))
        panel.addView(TextView(context).apply {
            text = config.title
            setTextColor(GameUiTheme.GOLD)
            textSize = 27f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(dp(context, 3).toFloat(), 0f, dp(context, 2).toFloat(), Color.BLACK)
        })
        config.subtitle?.let { subtitle ->
            panel.addView(TextView(context).apply {
                text = subtitle
                setTextColor(0xFFD2C2A4.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 5), 0, dp(context, 12))
            })
        }
        panel.addView(View(context).apply { setBackgroundColor(GameUiTheme.GOLD_DARK) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)
        ).apply { bottomMargin = dp(context, 12) })

        val buttons = createActionButtons(context, dialog, config.actions)
        if (config.actionsAboveBody) {
            panel.addView(buttons, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)
            ).apply { bottomMargin = dp(context, 10) })
        }

        config.scrollHint?.let { hint ->
            panel.addView(TextView(context).apply {
                text = hint
                setTextColor(0xFFBDA77F.toInt())
                textSize = 12f
                gravity = Gravity.END
                setPadding(0, 0, dp(context, 5), dp(context, 4))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val bodyText = TextView(context).apply {
            text = config.body
            setTextColor(Color.WHITE)
            textSize = 16f
            setLineSpacing(dp(context, 4).toFloat(), 1f)
            setPadding(dp(context, 16), dp(context, 13), dp(context, 16), dp(context, 13))
            background = panel(0xD91D1612.toInt(), 0xFF4E3B25.toInt(), 9f, 1, context)
        }
        var autoScrollAnimator: ValueAnimator? = null
        val bodyScroll = context.gameScrollView().apply {
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            addView(bodyText, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) autoScrollAnimator?.cancel()
                false
            }
        }
        panel.addView(bodyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(context, 10)
        })

        config.warning?.let { warning ->
            panel.addView(TextView(context).apply {
                text = "⚠  $warning"
                setTextColor(0xFFFFC0AE.toInt())
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                background = panel(0xD94A1713.toInt(), 0xFF9A4538.toInt(), 8f, 1, context)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(context, 14)
            })
        }

        if (!config.actionsAboveBody) {
            panel.addView(buttons, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)
            ))
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .78f }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                minOf(dp(context, 680), context.resources.displayMetrics.widthPixels - dp(context, 30)),
                minOf(dp(context, 610), context.resources.displayMetrics.heightPixels - dp(context, 24))
            )
            if (config.autoScrollBody) {
                bodyScroll.postDelayed({
                    val scrollDistance = (bodyText.height - bodyScroll.height).coerceAtLeast(0)
                    if (scrollDistance > 0 && dialog.isShowing) {
                        autoScrollAnimator = ValueAnimator.ofInt(0, scrollDistance).apply {
                            duration = config.autoScrollDurationMs
                            interpolator = LinearInterpolator()
                            addUpdateListener { bodyScroll.scrollTo(0, it.animatedValue as Int) }
                            start()
                        }
                    }
                }, 900L)
            }
        }
        dialog.setOnDismissListener { autoScrollAnimator?.cancel() }
        dialog.setOnCancelListener { config.onCancel?.invoke() }
        dialog.show()
        return dialog
    }

    private fun createActionButtons(
        context: Context,
        dialog: Dialog,
        actions: List<Action>
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        actions.forEachIndexed { index, action ->
            addView(TextView(context).apply {
                text = action.label
                contentDescription = action.label
                setTextColor(if (action.primary) 0xFF241607.toInt() else Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = actionBackground(context, action.primary)
                setOnClickListener {
                    dialog.dismiss()
                    action.onClick()
                }
            }, LinearLayout.LayoutParams(0, dp(context, 48), 1f).apply {
                if (index > 0) marginStart = dp(context, 10)
            })
        }
    }

    private fun actionBackground(context: Context, primary: Boolean): RippleDrawable {
        val fill = if (primary) 0xFFD0A653.toInt() else 0xFF2B211A.toInt()
        val stroke = if (primary) 0xFFFFD980.toInt() else GameUiTheme.GOLD_DARK
        return RippleDrawable(
            ColorStateList.valueOf(0x44FFFFFF),
            panel(fill, stroke, 8f, 2, context),
            panel(Color.WHITE, Color.WHITE, 8f, 0, context)
        )
    }

    private fun panel(fill: Int, stroke: Int, radius: Float, strokeWidth: Int, context: Context) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(context, strokeWidth), stroke)
        }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(context: Context, value: Float) = (value * context.resources.displayMetrics.density).toInt()
}
