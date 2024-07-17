package com.billiecamera.camera.camera

import android.hardware.Camera

/**
 * 相机配置参数
 */
class CameraDisplayInfo private constructor() {

    companion object {
        // 最大权重
        const val MAX_FOCUS_WEIGHT = 1000

        // 录制时长(毫秒)
        const val DEFAULT_RECORD_TIME = 15000

        // 16:9的默认宽高(理想值)
        const val DEFAULT_16_9_WIDTH = 1280
        const val DEFAULT_16_9_HEIGHT = 720

        // 4:3的默认宽高(理想值)
        const val DEFAULT_4_3_WIDTH = 1024
        const val DEFAULT_4_3_HEIGHT = 768

        // 期望fps
        const val DESIRED_PREVIEW_FPS = 30

        // 这里反过来是因为相机的分辨率跟屏幕的分辨率宽高刚好反过来
        const val Ratio_4_3 = 0.75f
        const val Ratio_16_9 = 0.5625f

        // 对焦权重最大值
        const val Weight = 100

        /**
         * 获取相机配置参数
         * @return
         */
        val instance = CameraDisplayInfo()
    }

    // 是否显示人脸关键点
    var drawFacePoints = false

    // 是否显示fps
    var showFps = false

    // 相机长宽比类型
    @JvmField
    var aspectRatio: AspectRatio? = null

    // 当前长宽比
    var currentRatio = 0f

    // 期望帧率
    var expectFps = 0

    // 实际帧率
    var previewFps = 0

    // 期望预览宽度
    var expectWidth = 0

    // 期望预览高度
    var expectHeight = 0

    // 实际预览宽度
    var previewWidth = 0

    // 实际预览高度
    var previewHeight = 0

    // 是否高清拍照
    var highDefinition = false

    // 预览角度
    var orientation = 0

    // 是否后置摄像头
    @JvmField
    var backCamera = false

    // 摄像头id
    var cameraId = 0

    // 是否支持闪光灯
    var supportFlash = false

    // 对焦权重，最大值为1000
    @JvmField
    var focusWeight = 0

    // 是否允许录制
    var recordable = false

    // 录制时长(ms)
    var recordTime = 0

    // 是否允许录制音频
    var recordAudio = false

    // 是否触屏拍照
    var touchTake = false

    // 是否延时拍照
    var takeDelay = false

    // 是否夜光增强
    var luminousEnhancement = false

    // 亮度值
    var brightness = 0

    // 拍照类型
    var mGalleryType: GalleryType? = null

    // 拍摄监听器
    var captureListener: OnPreviewCaptureListener? = null

    // 截屏回调
    var captureCallback: OnCaptureListener? = null

    // fps回调
    var fpsCallback: OnFpsListener? = null

    // 是否显示对比效果
    var showCompare = false

    // 是否拍照
    var isTakePicture = false

    // 是否允许景深
    var enableDepthBlur = false

    // 是否允许暗角
    var enableVignette = false

    init {
        reset()
    }

    /**
     * 重置为初始状态
     */
    private fun reset() {
        drawFacePoints = false
        showFps = false
        aspectRatio = AspectRatio.RATIO_16_9
        currentRatio = Ratio_16_9
        expectFps = DESIRED_PREVIEW_FPS
        previewFps = 0
        expectWidth = DEFAULT_16_9_WIDTH
        expectHeight = DEFAULT_16_9_HEIGHT
        previewWidth = 0
        previewHeight = 0
        highDefinition = false
        orientation = 0
        backCamera = true
        cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
        supportFlash = false
        focusWeight = 1000
        recordable = true
        recordTime = DEFAULT_RECORD_TIME
        recordAudio = true
        touchTake = false
        takeDelay = false
        luminousEnhancement = false
        brightness = -1
        mGalleryType = GalleryType.VIDEO_15S
        captureListener = null
        captureCallback = null
        fpsCallback = null
        showCompare = false
        isTakePicture = false
        enableDepthBlur = false
        enableVignette = false
    }

    /**
     * 设置预览长宽比
     * @param aspectRatio
     */
    fun setAspectRatio(aspectRatio: AspectRatio) {
        this.aspectRatio = aspectRatio
        if (aspectRatio === AspectRatio.RATIO_16_9) {
            expectWidth = DEFAULT_16_9_WIDTH
            expectHeight = DEFAULT_16_9_HEIGHT
            currentRatio = Ratio_16_9
        } else {
            expectWidth = DEFAULT_4_3_WIDTH
            expectHeight = DEFAULT_4_3_HEIGHT
            currentRatio = Ratio_4_3
        }
    }

    /**
     * 设置是否后置相机
     * @param backCamera
     */
    fun setBackCamera(backCamera: Boolean) {
        this.backCamera = backCamera
        cameraId = if (backCamera) {
            Camera.CameraInfo.CAMERA_FACING_BACK
        } else {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        }
    }

    /**
     * 设置对焦权重
     * @param focusWeight
     */
    fun setFocusWeight(focusWeight: Int) {
        require(!(focusWeight < 0 || focusWeight > MAX_FOCUS_WEIGHT)) { "focusWeight must be 0 ~ 1000" }
        this.focusWeight = focusWeight
    }


}