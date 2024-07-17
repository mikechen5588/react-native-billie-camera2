package com.billiecamera.camera.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class ReturnButton(context: Context?) : View(context) {
    private var size = 0

    private var centerX = 0
    private var centerY = 0

    private var strokeWidth = 0f

    private var paint: Paint? = null
    var path: Path? = null

    constructor(context: Context?, size: Int) : this(context) {
        this.size = size
        centerX = size / 2
        centerY = size / 2

        strokeWidth = size / 15f

        paint = Paint()
        paint?.isAntiAlias = true
        paint?.color = Color.WHITE
        paint?.style = Paint.Style.STROKE
        paint?.strokeWidth = strokeWidth

        path = Path()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size, size / 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val localPath = path ?: return
        val localPaint = paint ?: return
        localPath.moveTo(strokeWidth, strokeWidth / 2)
        localPath.lineTo(centerX.toFloat(), centerY - strokeWidth / 2)
        localPath.lineTo(size - strokeWidth, strokeWidth / 2)
        canvas.drawPath(localPath, localPaint)
    }
}
