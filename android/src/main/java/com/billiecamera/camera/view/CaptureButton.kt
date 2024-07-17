@file:Suppress("EnumValuesSoftDeprecate")

package com.billiecamera.camera.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import com.billiecamera.camera.listener.CaptureListener

class CaptureButton : View {
    private var state = 0 //当前按钮状态

    //  //按钮可执行的功能状态（拍照,录制,两者）
    // 获取当前按钮支持状态
    var buttonState: ButtonState = ButtonState.BUTTON_STATE_ONLY_CAPTURE

    private val progress_color = -0x11e951ea //进度条颜色
    private val outside_color = -0x11232324 //外圆背景色
    private val inside_color = -0x1 //内圆背景色


    private var event_Y = 0f //Touch_Event_Down时候记录的Y值


    private var mPaint: Paint? = null

    private var strokeWidth = 0f //进度条宽度
    private var outside_add_size = 0 //长按外圆半径变大的Size
    private var inside_reduce_size = 0 //长安内圆缩小的Size

    //中心坐标
    private var centerX = 0f
    private var centerY = 0f

    private var buttonRadius = 0f //按钮半径
    private var buttonOutsideRadius = 0f //外圆半径
    private var buttonInsideRadius = 0f //内圆半径
    private var button_size = 0 //按钮大小

    private var progress = 0f //录制视频的进度
    private var duration = 0 //录制视频最大时间长度
    private var min_duration = 0 //最短录制时间限制
    private var recorded_time = 0 //记录当前录制的时间

    private var rectF: RectF? = null

    private var longPressRunnable: LongPressRunnable? = null //长按后处理的逻辑Runnable
    private var captureLisenter: CaptureListener? = null //按钮回调接口
    private var timer: RecordCountDownTimer? = null //计时器

    private var canCaptureVideo:Boolean = true

    constructor(context: Context?) : super(context)

    constructor(context: Context?, size: Int) : super(context) {
        this.button_size = size
        buttonRadius = size / 2.0f

        buttonOutsideRadius = buttonRadius
        buttonInsideRadius = buttonRadius * 0.75f

        strokeWidth = (size / 15).toFloat()
        outside_add_size = size / 8
        inside_reduce_size = size / 8

        mPaint = Paint()
        mPaint?.isAntiAlias = true

        progress = 0f
        longPressRunnable = LongPressRunnable()

        state = STATE_IDLE //初始化为空闲状态
        //初始化按钮为可录制可拍照
        buttonState = ButtonState.BUTTON_STATE_ONLY_CAPTURE
        println("CaptureButtom start")
        duration = 10 * 1000 //默认最长录制时间为10s
        println("CaptureButtom end")
        min_duration = 1500 //默认最短录制时间为1.5s

        centerX = ((button_size + outside_add_size * 2) / 2).toFloat()
        centerY = ((button_size + outside_add_size * 2) / 2).toFloat()

        rectF = RectF(
            centerX - (buttonRadius + outside_add_size - strokeWidth / 2),
            centerY - (buttonRadius + outside_add_size - strokeWidth / 2),
            centerX + (buttonRadius + outside_add_size - strokeWidth / 2),
            centerY + (buttonRadius + outside_add_size - strokeWidth / 2)
        )

        timer = RecordCountDownTimer(duration.toLong(), (duration / 360).toLong()) //录制定时器
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(button_size + outside_add_size * 2, button_size + outside_add_size * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mPaint?.style = Paint.Style.FILL

        mPaint?.color = outside_color //外圆（半透明灰
        if(mPaint != null) {
            canvas.drawCircle(centerX, centerY, buttonOutsideRadius, mPaint!!)
        }

        mPaint?.color = inside_color //内圆（白色）
        if(mPaint != null) {
            canvas.drawCircle(centerX, centerY, buttonInsideRadius, mPaint!!)
        }

        //如果状态为录制状态，则绘制录制进度条
        if (state == STATE_RECORDERING) {
            mPaint?.color = progress_color
            mPaint?.style = Paint.Style.STROKE
            mPaint?.strokeWidth = strokeWidth
            if(rectF != null && mPaint != null) {
                canvas.drawArc(rectF!!, -90f, progress, false, mPaint!!)
            }
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                println("state = $state")
                if (event.pointerCount > 1 || state != STATE_IDLE) {
                    // todo nothing
                } else {
                    event_Y = event.y //记录Y值
                    state = STATE_PRESS //修改当前状态为点击按下

                    //LogUtil.e("systimestamp111---"+System.currentTimeMillis());

                    //判断按钮状态是否为可录制状态
                    if (buttonState.canRecord()) {
                        // 同时延长500启动长按后处理的逻辑Runnable
                        postDelayed(longPressRunnable, 500)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> if (captureLisenter != null && state == STATE_RECORDERING && (buttonState.canRecord())) {
                //记录当前Y值与按下时候Y值的差值，调用缩放回调接口
                captureLisenter?.recordZoom(event_Y - event.y)
            }

            MotionEvent.ACTION_UP -> {
                //根据当前按钮的状态进行相应的处理  ----CodeReview---抬起瞬间应该重置状态 当前状态可能为按下和正在录制
                //state = STATE_BAN;
                handlerPressByState()
            }
        }
        return true
    }

    //当手指松开按钮时候处理的逻辑
    private fun handlerPressByState() {
        removeCallbacks(longPressRunnable) //移除长按逻辑的Runnable
        when (state) {
            STATE_PRESS -> if (captureLisenter != null && (buttonState.canTakePicture())) {
                startCaptureAnimation(buttonInsideRadius)
            } else {
                state = STATE_IDLE
            }

            STATE_LONG_PRESS, STATE_RECORDERING -> {
                timer?.cancel() //停止计时器
                //录制结束
                recordEnd()
            }
        }
        state = STATE_IDLE
    }

    //录制结束
    fun recordEnd() {
        if (recorded_time < min_duration) {
            captureLisenter?.recordShort(recorded_time.toLong())
        } else {
            // 回调录制时间过短
            // 回调录制结束
            captureLisenter?.recordEnd(recorded_time.toLong())
        }
        resetRecordAnim() //重制按钮状态
    }

    //重制状态
    private fun resetRecordAnim() {
        state = STATE_BAN
        progress = 0f //重制进度
        invalidate()
        //还原按钮初始状态动画
        startRecordAnimation(
            buttonOutsideRadius,
            buttonRadius,
            buttonInsideRadius,
            buttonRadius * 0.75f
        )
    }

    //内圆动画
    private fun startCaptureAnimation(inside_start: Float) {
        val inside_anim = ValueAnimator.ofFloat(inside_start, inside_start * 0.75f, inside_start)
        inside_anim.addUpdateListener { animation: ValueAnimator ->
            buttonInsideRadius = animation.animatedValue as Float
            invalidate()
        }
        inside_anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                //回调拍照接口
//                if (captureLisenter != null) {
//                    captureLisenter.takePictures();
//                }
                // 为何拍照完成要将状态掷为禁止？？？？此处貌似bug！！！！！！---CodeReview
                //state = STATE_BAN;
                //state = STATE_IDLE;
            }

            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                if (captureLisenter != null) {
                    captureLisenter?.takePictures()
                }
                // 防止重复点击 状态重置
                state = STATE_BAN
            }
        })
        inside_anim.setDuration(50)
        inside_anim.start()
    }

    //内外圆动画
    private fun startRecordAnimation(
        outside_start: Float,
        outside_end: Float,
        inside_start: Float,
        inside_end: Float
    ) {
        val outside_anim = ValueAnimator.ofFloat(outside_start, outside_end)
        val inside_anim = ValueAnimator.ofFloat(inside_start, inside_end)
        //外圆动画监听
        outside_anim.addUpdateListener { animation: ValueAnimator ->
            buttonOutsideRadius = animation.animatedValue as Float
            invalidate()
        }
        //内圆动画监听
        inside_anim.addUpdateListener { animation: ValueAnimator ->
            buttonInsideRadius = animation.animatedValue as Float
            invalidate()
        }
        val set = AnimatorSet()
        //当动画结束后启动录像Runnable并且回调录像开始接口
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                //设置为录制状态
                if (state == STATE_LONG_PRESS) {
                    if (captureLisenter != null) captureLisenter?.recordStart()
                    state = STATE_RECORDERING
                    timer?.start()
                } else {
                    // 此处动画包括长按起始动画和还原动画 若不是长按状态应该还原状态为空闲？？？？---CodeReview
                    state = STATE_IDLE
                }
            }
        })
        set.playTogether(outside_anim, inside_anim)
        set.setDuration(100)
        set.start()
    }


    //更新进度条
    private fun updateProgress(millisUntilFinished: Long) {
        recorded_time = (duration - millisUntilFinished).toInt()
        progress = 360f - millisUntilFinished / duration.toFloat() * 360f
        invalidate()
    }

    //录制视频计时器
    private inner class RecordCountDownTimer(millisInFuture: Long, countDownInterval: Long) :
        CountDownTimer(millisInFuture, countDownInterval) {
        override fun onTick(millisUntilFinished: Long) {
            updateProgress(millisUntilFinished)
        }

        override fun onFinish() {
            //updateProgress(duration);
            recordEnd()
        }
    }

    //长按线程
    private inner class LongPressRunnable : Runnable {
        override fun run() {
            state = STATE_LONG_PRESS //如果按下后经过500毫秒则会修改当前状态为长按状态
            //启动按钮动画，外圆变大，内圆缩小
            startRecordAnimation(
                buttonOutsideRadius,
                buttonOutsideRadius + outside_add_size,
                buttonInsideRadius,
                buttonInsideRadius - inside_reduce_size
            )
        }
    }

    /**************************************************
     * 对外提供的API                     *
     */
    //设置最长录制时间
    fun setDuration(duration: Int) {
        this.duration = duration
        timer = RecordCountDownTimer(duration.toLong(), (duration / 360).toLong()) //录制定时器
    }

    //设置最短录制时间
    fun setMinDuration(duration: Int) {
        this.min_duration = duration
    }

    //设置回调接口
    fun setCaptureLisenter(captureLisenter: CaptureListener?) {
        this.captureLisenter = captureLisenter
    }

    //设置按钮功能（拍照和录像）
    fun setButtonFeatures(state: ButtonState) {
        this.buttonState = state
    }

    val isIdle: Boolean
        //是否空闲状态
        get() = if (state == STATE_IDLE) true else false

    //设置状态
    fun resetState() {
        state = STATE_IDLE
    }

    companion object {
        const val BUTTON_STATE_ONLY_CAPTURE: Int = 0x0 //只能拍照
        const val BUTTON_STATE_ONLY_RECORDER: Int = 0x1 //只能录像
        const val BUTTON_STATE_BOTH: Int = 0x2


        const val STATE_IDLE: Int = 0x001 //空闲状态
        const val STATE_PRESS: Int = 0x002 //按下状态
        const val STATE_LONG_PRESS: Int = 0x003 //长按状态
        const val STATE_RECORDERING: Int = 0x004 //录制状态
        const val STATE_BAN: Int = 0x005 //禁止状态
    }
}

enum class ButtonState(val status:Int, val text:String) {
    BUTTON_STATE_ONLY_CAPTURE(0, "单击拍照"),
    BUTTON_STATE_ONLY_RECORDER(1, "长按摄像"),
    BUTTON_STATE_BOTH(2, "单击拍照，长按摄像");

    fun canRecord():Boolean {
        return status == 1 || status == 2
    }

    fun canTakePicture(): Boolean {
        return  status == 0 || status == 2
    }

    companion object {
        fun getButtonState(value:Int): ButtonState? {
            ButtonState.values().forEach {
                if(it.status == value) {
                    return it
                }
            }
            return null
        }
    }
}
