package com.billiecamera.camera.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class TypeButton : View {
    private var button_type = 0
    private var button_size = 0

    private var center_X = 0f
    private var center_Y = 0f
    private var button_radius = 0f

    private var mPaint: Paint? = null
    private var path: Path? = null
    private var strokeWidth = 0f

    private var index = 0f
    private var rectF: RectF? = null

    constructor(context: Context?) : super(context)

    constructor(context: Context?, type: Int, size: Int) : super(context) {
        this.button_type = type
        button_size = size
        button_radius = size / 2.0f
        center_X = size / 2.0f
        center_Y = size / 2.0f

        mPaint = Paint()
        path = Path()
        strokeWidth = size / 50f
        index = button_size / 12f
        rectF = RectF(center_X, center_Y - index, center_X + index * 2, center_Y + index)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(button_size, button_size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val localPaint = mPaint ?: return
        val localPath = path ?: return;
        
        //如果类型为取消，则绘制内部为返回箭头
        if (button_type == TYPE_CANCEL) {
            localPaint.isAntiAlias = true
            localPaint.color = -0x11232324
            localPaint.style = Paint.Style.FILL
            canvas.drawCircle(center_X, center_Y, button_radius, localPaint)

            localPaint.color = Color.BLACK
            localPaint.style = Paint.Style.STROKE
            localPaint.strokeWidth = strokeWidth

            localPath.moveTo(center_X - index / 7, center_Y + index)
            localPath.lineTo(center_X + index, center_Y + index)

            localPath.arcTo(rectF!!, 90f, -180f)
            localPath.lineTo(center_X - index, center_Y - index)
            canvas.drawPath(localPath, localPaint)

            localPaint.style = Paint.Style.FILL
            localPath.reset()
            localPath.moveTo(center_X - index, (center_Y - index * 1.5).toFloat())
            localPath.lineTo(center_X - index, (center_Y - index / 2.3).toFloat())
            localPath.lineTo((center_X - index * 1.6).toFloat(), center_Y - index)
            localPath.close()
            canvas.drawPath(localPath, localPaint)
        }
        //如果类型为确认，则绘制绿色勾
        if (button_type == TYPE_CONFIRM) {
            localPaint.isAntiAlias = true
            localPaint.color = -0x1
            localPaint.style = Paint.Style.FILL
            canvas.drawCircle(center_X, center_Y, button_radius, localPaint)
            localPaint.isAntiAlias = true
            localPaint.style = Paint.Style.STROKE
            localPaint.color = -0xff3400
            localPaint.strokeWidth = strokeWidth

            localPath.moveTo(center_X - button_size / 6f, center_Y)
            localPath.lineTo(center_X - button_size / 21.2f, center_Y + button_size / 7.7f)
            localPath.lineTo(center_X + button_size / 4.0f, center_Y - button_size / 8.5f)
            localPath.lineTo(center_X - button_size / 21.2f, center_Y + button_size / 9.4f)
            localPath.close()
            canvas.drawPath(localPath, localPaint)
        }
    }

    companion object {
        const val TYPE_CANCEL: Int = 0x001
        const val TYPE_CONFIRM: Int = 0x002
    }
}
