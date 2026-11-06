package com.speed.s25subtitle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.TextView

class OutlinedTextView(context: Context) : TextView(context) {
    var strokeColor: Int = Color.BLACK
    var strokeWidthPx: Float = 8f

    override fun onDraw(canvas: Canvas) {
        val textColor = currentTextColor
        val paint = paint
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeMiter = 10f
        paint.strokeWidth = strokeWidthPx
        setTextColor(strokeColor)
        super.onDraw(canvas)

        paint.style = Paint.Style.FILL
        setTextColor(textColor)
        super.onDraw(canvas)
    }
}
