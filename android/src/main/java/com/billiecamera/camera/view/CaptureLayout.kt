package com.billiecamera.camera.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.billiecamera.camera.listener.CaptureListener
import com.billiecamera.camera.listener.ClickListener
import com.billiecamera.camera.listener.ReturnListener
import com.billiecamera.camera.listener.TypeListener

class CaptureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    // 选择拍照 拍视频 或者都有
    //只能拍照
    private val BUTTON_STATE_ONLY_CAPTURE = 0x101

    //只能录像
    private val BUTTON_STATE_ONLY_RECORDER = 0x102
    private val BUTTON_STATE_BOTH = 0x103

    //拍照按钮监听
    private var captureListener: CaptureListener? = null

    //拍照或录制后接结果按钮监听
    private var typeListener: TypeListener? = null

    //退出按钮监听
    private var returnListener: ReturnListener? = null

    //左边按钮监听
    private var leftClickListener: ClickListener? = null

    // gallery browse
    private var galleryListener:ClickListener? = null

    //右边按钮监听
    private var rightClickListener: ClickListener? = null

    fun setTypeListener(typeListener: TypeListener?) {
        this.typeListener = typeListener
    }

    fun setCaptureLisenter(captureLisenter: CaptureListener?) {
        this.captureListener = captureLisenter
    }

    fun setReturnLisenter(returnListener: ReturnListener?) {
        this.returnListener = returnListener
    }

    //拍照按钮
    private var btnCapture: CaptureButton? = null

    //确认按钮
    private var btnConfirm: TypeButton? = null

    //取消按钮
    private var btnCancel: TypeButton? = null

    //返回按钮
    private var btnReturn: ReturnButton? = null

    //返回按钮
    private var galleryBtn: ReturnButton? = null

    //左边自定义按钮
    private var ivCustomLeft: ImageView? = null

    //右边自定义按钮
    private var ivCustomRight: ImageView? = null

    //提示文本
    private var txtTip: TextView? = null

    private var textTip: String? = null

    private var layoutWidth = 0
    private val layoutHeight: Int
    private val buttonSize: Int
    private var iconLeft = 0
    private var iconRight = 0

    init {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        manager.defaultDisplay.getMetrics(outMetrics)

        layoutWidth = if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            outMetrics.widthPixels
        } else {
            outMetrics.widthPixels / 2
        }
        buttonSize = (layoutWidth / 4.5f).toInt()
        layoutHeight = buttonSize + (buttonSize / 5) * 2 + 100

        initView()
        initEvent()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(layoutWidth, layoutHeight)
    }

    fun initEvent() {
        //默认Typebutton为隐藏
        ivCustomRight?.visibility = GONE
        btnCancel?.visibility = GONE
        btnConfirm?.visibility = GONE
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun startTypeBtnAnimator() {
        //拍照录制结果后的动画
        if (this.iconLeft != 0) {
            ivCustomLeft?.visibility = GONE
        } else {
            btnReturn?.visibility = GONE
            galleryBtn?.visibility = GONE
        }

        if (this.iconRight != 0) {
            ivCustomRight?.visibility = GONE
        }
        btnCapture?.visibility = GONE
        btnCancel?.visibility = VISIBLE
        btnConfirm?.visibility = VISIBLE
        btnCancel?.isClickable = false
        btnConfirm?.isClickable = false
        val animatorCancel =
            ObjectAnimator.ofFloat(btnCancel, "translationX", (layoutWidth / 4).toFloat(), 0f)
        val animatorConfirm =
            ObjectAnimator.ofFloat(btnConfirm, "translationX", (-layoutWidth / 4).toFloat(), 0f)
        val set = AnimatorSet()
        set.playTogether(animatorCancel, animatorConfirm)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                btnCancel?.isClickable = true
                btnConfirm?.isClickable = true
            }
        })
        set.setDuration(500)
        set.start()
    }


    private fun initView() {
        setWillNotDraw(false)
        //拍照按钮
        btnCapture = CaptureButton(context, buttonSize)
        val btnCaptureParam = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        btnCaptureParam.gravity = Gravity.CENTER
        btnCapture?.layoutParams = btnCaptureParam
        btnCapture?.setCaptureLisenter(object : CaptureListener {
            override fun takePictures() {
                captureListener?.takePictures()
                startAlphaAnimation()
            }

            override fun recordShort(time: Long) {
                captureListener?.recordShort(time)
            }

            override fun recordStart() {
                captureListener?.recordStart()
                startAlphaAnimation()
            }

            override fun recordEnd(time: Long) {
                captureListener?.recordEnd(time)
            }

            override fun recordZoom(zoom: Float) {
                captureListener?.recordZoom(zoom)
            }

            override fun recordError() {
                captureListener?.recordError()
            }
        })

        //取消按钮
        btnCancel = TypeButton(context, TypeButton.TYPE_CANCEL, buttonSize)
        val btnCancelParam = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        btnCancelParam.gravity = Gravity.CENTER_VERTICAL
        btnCancelParam.setMargins((layoutWidth / 4) - buttonSize / 2, 0, 0, 0)
        btnCancel?.layoutParams = btnCancelParam
        btnCancel?.setOnClickListener { view: View? ->
            typeListener?.cancel()
        }

        //确认按钮
        btnConfirm = TypeButton(context, TypeButton.TYPE_CONFIRM, buttonSize)
        val btnConfirmParam = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        btnConfirmParam.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        btnConfirmParam.setMargins(0, 0, (layoutWidth / 4) - buttonSize / 2, 0)
        btnConfirm?.layoutParams = btnConfirmParam
        btnConfirm?.setOnClickListener { view: View? ->
            if (typeListener != null) {
                typeListener?.confirm()
            }
        }

        //返回按钮
        btnReturn = ReturnButton(context, (buttonSize / 2.5f).toInt())
        val btnReturnParam = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        btnReturnParam.gravity = Gravity.CENTER_VERTICAL
        btnReturnParam.setMargins(layoutWidth / 7, 0, 0, 0)
        btnReturn?.layoutParams = btnReturnParam
        btnReturn?.setOnClickListener { v: View? ->
            leftClickListener?.onClick()
        }
        //左边自定义按钮
        ivCustomLeft = ImageView(context)
        val ivCustomParamLeft =
            LayoutParams((buttonSize / 2.5f).toInt(), (buttonSize / 2.5f).toInt())
        ivCustomParamLeft.gravity = Gravity.CENTER_VERTICAL
        ivCustomParamLeft.setMargins(layoutWidth / 6, 0, 0, 0)
        ivCustomLeft?.layoutParams = ivCustomParamLeft
        ivCustomLeft?.setOnClickListener { v: View? ->
            if (leftClickListener != null) {
                leftClickListener?.onClick()
            }
        }

        //右边自定义按钮
        ivCustomRight = ImageView(context)
        val ivCustomParamRight =
            LayoutParams((buttonSize / 2.5f).toInt(), (buttonSize / 2.5f).toInt())
        ivCustomParamRight.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        ivCustomParamRight.setMargins(0, 0, layoutWidth / 6, 0)
        ivCustomRight?.layoutParams = ivCustomParamRight
        ivCustomRight?.setOnClickListener { v: View? ->
            if (rightClickListener != null) {
                rightClickListener?.onClick()
            }
        }

        txtTip = TextView(context)
        val txtParam = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        txtParam.gravity = Gravity.CENTER_HORIZONTAL
        txtParam.setMargins(0, 0, 0, 0)
        switchTextTip(btnCapture?.buttonState ?: ButtonState.BUTTON_STATE_ONLY_CAPTURE)
        txtTip?.setTextColor(-0x1)
        txtTip?.gravity = Gravity.CENTER
        txtTip?.layoutParams = txtParam


        //返回按钮
        galleryBtn = ReturnButton(context, (buttonSize / 2.5f).toInt())
        val galleryBtnParam = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        galleryBtnParam.gravity = Gravity.CENTER_VERTICAL or  Gravity.END
        galleryBtnParam.setMargins(0, 0, layoutWidth / 7, 0)
        galleryBtn?.layoutParams = galleryBtnParam
        galleryBtn?.setOnClickListener { v: View? ->
            galleryListener?.onClick()
        }

        this.addView(btnCapture)
        this.addView(btnCancel)
        this.addView(btnConfirm)
        this.addView(btnReturn)
        this.addView(galleryBtn)
        this.addView(ivCustomLeft)
        this.addView(ivCustomRight)
        this.addView(txtTip)
    }

    /**************************************************
     * 对外提供的API                      *
     */
    fun resetCaptureLayout() {
        btnCapture?.resetState()
        btnCancel?.visibility = GONE
        btnConfirm?.visibility = GONE
        btnCapture?.visibility = VISIBLE
        galleryBtn?.visibility = VISIBLE
        switchTextTip(btnCapture?.buttonState ?: ButtonState.BUTTON_STATE_ONLY_CAPTURE)
        txtTip?.visibility = VISIBLE

        if (this.iconLeft != 0) {
            ivCustomLeft?.visibility = VISIBLE
        } else {
            btnReturn?.visibility = VISIBLE
        }

        if (this.iconRight != 0) {
            ivCustomRight?.visibility = VISIBLE
        }
    }

    fun startAlphaAnimation() {
        txtTip?.visibility = INVISIBLE
    }

    @SuppressLint("ObjectAnimatorBinding")
    fun setTextWithAnimation(tip: String?) {
        txtTip?.text = tip
        val animatorTxtTip = ObjectAnimator.ofFloat(txtTip, "alpha", 0f, 1f, 1f, 0f)
        animatorTxtTip.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                switchTextTip(btnCapture?.buttonState ?: ButtonState.BUTTON_STATE_ONLY_CAPTURE)
                txtTip?.alpha = 1f
            }
        })
        animatorTxtTip.setDuration(2500)
        animatorTxtTip.start()
    }

    fun setDuration(duration: Int) {
        btnCapture?.setDuration(duration)
    }

    fun setButtonFeatures(state: ButtonState) {
        btnCapture?.setButtonFeatures(state)
        switchTextTip(state)
    }

    private fun switchTextTip(state: ButtonState) {
        txtTip?.text = state.text
    }

    fun setTip(tip: String?) {
        textTip = tip
        txtTip?.text = textTip
    }

    fun setIconSrc(iconLeft: Int, iconRight: Int) {
        this.iconLeft = iconLeft
        this.iconRight = iconRight
        if (this.iconLeft != 0) {
            ivCustomLeft?.setImageResource(iconLeft)
            ivCustomLeft?.visibility = VISIBLE
            btnReturn?.visibility = GONE
        } else {
            ivCustomLeft?.visibility = GONE
            btnReturn?.visibility = VISIBLE
        }
        if (this.iconRight != 0) {
            ivCustomRight?.setImageResource(iconRight)
            ivCustomRight?.visibility = VISIBLE
        } else {
            ivCustomRight?.visibility = GONE
        }
    }

    fun setLeftClickListener(leftClickListener: ClickListener?) {
        this.leftClickListener = leftClickListener
    }

    fun setGalleryListener(galleryListener:ClickListener?) {
        this.galleryListener = galleryListener
    }

    fun setRightClickListener(rightClickListener: ClickListener?) {
        this.rightClickListener = rightClickListener
    }
}
