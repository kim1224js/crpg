package es.kim.crpg.ui.event

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

class RedMoonView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = min(width, height) * 0.31f
        val cx = width * 0.5f
        val cy = height * 0.48f
        paint.color = 0x203F0000
        canvas.drawCircle(cx, cy, radius * 1.42f, paint)
        paint.color = 0x405E0000
        canvas.drawCircle(cx, cy, radius * 1.22f, paint)
        paint.color = 0xFFB51F27.toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = 0xFF7D111A.toInt()
        canvas.drawCircle(cx - radius * .28f, cy - radius * .18f, radius * .18f, paint)
        canvas.drawCircle(cx + radius * .24f, cy + radius * .22f, radius * .12f, paint)
        paint.color = 0x55FF9B84
        canvas.drawCircle(cx - radius * .24f, cy - radius * .28f, radius * .31f, paint)
    }
}
