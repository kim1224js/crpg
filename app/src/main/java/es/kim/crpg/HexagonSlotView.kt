package es.kim.crpg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

class HexagonSlotView(
    context: Context,
    private val fillColor: Int,
    private val strokeColor: Int
) : FrameLayout(context) {
    private val hexagonPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val inset = strokePaint.strokeWidth
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        val quarterWidth = (right - left) * 0.25f

        hexagonPath.reset()
        hexagonPath.moveTo(left + quarterWidth, top)
        hexagonPath.lineTo(right - quarterWidth, top)
        hexagonPath.lineTo(right, (top + bottom) / 2f)
        hexagonPath.lineTo(right - quarterWidth, bottom)
        hexagonPath.lineTo(left + quarterWidth, bottom)
        hexagonPath.lineTo(left, (top + bottom) / 2f)
        hexagonPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(hexagonPath, fillPaint)
        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(hexagonPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
        canvas.drawPath(hexagonPath, strokePaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val regularHexWidth = min(availableWidth, (availableHeight / HEX_HEIGHT_RATIO).toInt())
        setMeasuredDimension(regularHexWidth, (regularHexWidth * HEX_HEIGHT_RATIO).toInt())
    }

    companion object {
        const val HEX_HEIGHT_RATIO = 0.8660254f
    }
}
