@file:Suppress("DEPRECATION")

package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import android.view.Surface
import java.io.File
import java.io.IOException
import java.lang.Long.signum
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 相机控制器
 */
@SuppressLint("LogNotTimber")
class CameraController(context: Activity) : ICameraController, Camera.PreviewCallback {

    companion object {
        private const val TAG = "CameraController"

        // 16:9的默认宽高(理想值)
        private const val DEFAULT_16_9_WIDTH = 1920
        private const val DEFAULT_16_9_HEIGHT = 1080

        fun rectFToRect(rectF: RectF, rect: Rect) {
            rect.left = rectF.left.roundToInt()
            rect.top = rectF.top.roundToInt()
            rect.right = rectF.right.roundToInt()
            rect.bottom = rectF.bottom.roundToInt()
        }

        private fun clamp(value: Float, min: Float, max: Float): Float {
            if (value < min)
                return min
            return if (value > max)
                max
            else
                value
        }

        /**
         * 确保所选区域在在合理范围内
         *
         * @param touchCoordinateInCameraReaper
         * @param focusAreaSize
         * @return
         */
        private fun clamp(touchCoordinateInCameraReaper: Int, focusAreaSize: Int): Int {
            val result = if (abs(touchCoordinateInCameraReaper) + focusAreaSize > 1000) {
                if (touchCoordinateInCameraReaper > 0) {
                    1000 - focusAreaSize
                } else {
                    -1000 + focusAreaSize
                }
            } else {
                touchCoordinateInCameraReaper - focusAreaSize / 2
            }
            return result
        }

        /**
         * 检查摄像头(前置/后置)是否支持闪光灯
         *
         * @param camera 摄像头
         * @return
         */
        private fun checkSupportFlashLight(camera: Camera?): Boolean {
            if (camera == null) {
                return false
            }
            val parameters = camera.parameters
            return checkSupportFlashLight(parameters)
        }

        /**
         * 检查摄像头(前置/后置)是否支持闪光灯
         *
         * @param parameters 摄像头参数
         * @return
         */
        private fun checkSupportFlashLight(parameters: Camera.Parameters): Boolean {
            if (parameters.flashMode == null) {
                return false
            }
            val supportedFlashModes = parameters.supportedFlashModes
            return supportedFlashModes != null &&
                    supportedFlashModes.isNotEmpty() &&
                    (supportedFlashModes.size != 1 || supportedFlashModes[0] != Camera.Parameters.FLASH_MODE_OFF)
        }

        /**
         * 选择合适的FPS
         *
         * @param parameters
         * @param expectedThousandFps 期望的FPS
         * @return
         */
        private fun chooseFixedPreviewFps(parameters: Camera.Parameters, expectedThousandFps: Int): Int {
            val supportedFps = parameters.supportedPreviewFpsRange
            for (entry in supportedFps) {
                if (entry[0] == entry[1] && entry[0] == expectedThousandFps) {
                    parameters.setPreviewFpsRange(entry[0], entry[1])
                    return entry[0]
                }
            }
            val temp = IntArray(2)
            parameters.getPreviewFpsRange(temp)
            val guess = if (temp[0] == temp[1]) {
                temp[0]
            } else {
                temp[1] / 2
            }
            return guess
        }

        /**
         * 计算最完美的Size
         *
         * @param sizes
         * @param expectWidth
         * @param expectHeight
         * @return
         */
        private fun calculatePerfectSize(
            sizes: List<Camera.Size>, expectWidth: Int,
            expectHeight: Int, calculateType: CalculateType
        ): Camera.Size {
            sortList(sizes) // 根据宽度进行排序

            // 根据当前期望的宽高判定
            val bigEnough: MutableList<Camera.Size> = ArrayList()
            val noBigEnough: MutableList<Camera.Size> = ArrayList()
            for (size in sizes) {
                if (size.height * expectWidth / expectHeight == size.width) {
                    if (size.width > expectWidth && size.height > expectHeight) {
                        bigEnough.add(size)
                    } else {
                        noBigEnough.add(size)
                    }
                }
            }
            // 根据计算类型判断怎么如何计算尺寸
            var perfectSize: Camera.Size? = null
            when (calculateType) {
                CalculateType.Min ->                 // 不大于期望值的分辨率列表有可能为空或者只有一个的情况，
                    // Collections.min会因越界报NoSuchElementException
                    if (noBigEnough.size > 1) {
                        perfectSize = Collections.min(noBigEnough, CompareAreaSize())
                    } else if (noBigEnough.size == 1) {
                        perfectSize = noBigEnough[0]
                    }
                CalculateType.Max ->                 // 如果bigEnough只有一个元素，使用Collections.max就会因越界报NoSuchElementException
                    // 因此，当只有一个元素时，直接使用该元素
                    if (bigEnough.size > 1) {
                        perfectSize = Collections.max(bigEnough, CompareAreaSize())
                    } else if (bigEnough.size == 1) {
                        perfectSize = bigEnough[0]
                    }
                CalculateType.Lower ->                 // 优先查找比期望尺寸小一点的，否则找大一点的，接受范围在0.8左右
                    if (noBigEnough.size > 0) {
                        val size = Collections.max(noBigEnough, CompareAreaSize())
                        if (size.width.toFloat() / expectWidth >= 0.8 &&
                            size.height.toFloat() / expectHeight > 0.8
                        ) {
                            perfectSize = size
                        }
                    } else if (bigEnough.size > 0) {
                        val size = Collections.min(bigEnough, CompareAreaSize())
                        if (expectWidth.toFloat() / size.width >= 0.8 &&
                            (expectHeight / size.height).toFloat() >= 0.8
                        ) {
                            perfectSize = size
                        }
                    }
                CalculateType.Larger ->                 // 优先查找比期望尺寸大一点的，否则找小一点的，接受范围在0.8左右
                    if (bigEnough.size > 0) {
                        val size = Collections.min(bigEnough, CompareAreaSize())
                        if (expectWidth.toFloat() / size.width >= 0.8 &&
                            (expectHeight / size.height).toFloat() >= 0.8
                        ) {
                            perfectSize = size
                        }
                    } else if (noBigEnough.size > 0) {
                        val size = Collections.max(noBigEnough, CompareAreaSize())
                        if (size.width.toFloat() / expectWidth >= 0.8 &&
                            size.height.toFloat() / expectHeight > 0.8
                        ) {
                            perfectSize = size
                        }
                    }
            }
            // 如果经过前面的步骤没找到合适的尺寸，则计算最接近expectWidth * expectHeight的值
            if (perfectSize == null) {
                var result = sizes[0]
                var widthOrHeight = false // 判断存在宽或高相等的Size
                // 辗转计算宽高最接近的值
                for (size in sizes) {
                    // 如果宽高相等，则直接返回
                    if (size.width == expectWidth &&
                        size.height == expectHeight &&
                        size.height.toFloat() / size.width.toFloat() == CameraDisplayInfo.instance.currentRatio
                    ) {
                        result = size
                        break
                    }
                    // 仅仅是宽度相等，计算高度最接近的size
                    if (size.width == expectWidth) {
                        widthOrHeight = true
                        if (abs(result.height - expectHeight) > abs(size.height - expectHeight) &&
                            size.height.toFloat() / size.width.toFloat() == CameraDisplayInfo.instance.currentRatio
                        ) {
                            result = size
                            break
                        }
                    } else if (size.height == expectHeight) {
                        widthOrHeight = true
                        if (abs(result.width - expectWidth) > abs(size.width - expectWidth) &&
                            size.height.toFloat() / size.width.toFloat() == CameraDisplayInfo.instance.currentRatio
                        ) {
                            result = size
                            break
                        }
                    } else if (!widthOrHeight) {
                        if (abs(result.width - expectWidth) > abs(size.width - expectWidth) &&
                            abs(result.height - expectHeight) > abs(size.height - expectHeight) &&
                            size.height.toFloat() / size.width.toFloat() == CameraDisplayInfo.instance.currentRatio
                        ) {
                            result = size
                        }
                    }
                }
                perfectSize = result
            }
            return perfectSize
        }

        /**
         * 分辨率由大到小排序
         *
         * @param list
         */
        private fun sortList(list: List<Camera.Size>) {
            Collections.sort(list, CompareAreaSize())
        }
    }

    // 期望的fps
    private val mExpectFps = CameraDisplayInfo.DESIRED_PREVIEW_FPS

    // 预览宽度
    private var mPreviewWidth = DEFAULT_16_9_WIDTH

    // 预览高度
    private var mPreviewHeight = DEFAULT_16_9_HEIGHT

    // 预览角度
    private var mOrientation = 0

    // 相机对象
    private var mCamera: Camera? = null

    // 摄像头id
    private var mCameraId: Int

    // SurfaceTexture成功回调
    private var mSurfaceTextureListener: OnSurfaceTextureListener? = null

    // 预览数据回调
    private var mPreviewCallback: PreviewCallback? = null

    // 输出纹理更新回调
    private var mFrameAvailableListener: OnFrameAvailableListener? = null

    // 相机输出的SurfaceTexture
    private var mOutputTexture: SurfaceTexture? = null
    private var mOutputThread: HandlerThread? = null

    // 上下文
    private val mContext: Activity

    init {
        Log.i(TAG, "CameraController: created！")
        mContext = context
        mCameraId =
            if (com.billiecamera.camera.camera.CameraApi.hasFrontCamera(context))
                Camera.CameraInfo.CAMERA_FACING_FRONT
            else
                Camera.CameraInfo.CAMERA_FACING_BACK
    }

    override fun openCamera() {
        closeCamera()
        if (mCamera != null) {
            throw RuntimeException("camera already initialized!")
        }
        val camera = Camera.open(mCameraId) ?: throw RuntimeException("Unable to open camera")
        mCamera = camera

        val parameters = camera.parameters
        val cameraDisplayInfo = CameraDisplayInfo.instance
        with(cameraDisplayInfo) {
            cameraId = mCameraId
            supportFlash = checkSupportFlashLight(parameters)
            previewFps = chooseFixedPreviewFps(parameters, mExpectFps * 1000)
        }
        parameters.setRecordingHint(true)
        // 后置摄像头自动对焦
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK &&
            supportAutoFocusFeature(parameters)
        ) {
            camera.cancelAutoFocus()
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        camera.parameters = parameters
        setPreviewSize(camera, mPreviewWidth, mPreviewHeight)
        setPictureSize(camera, mPreviewWidth, mPreviewHeight)
        mOrientation = calculateCameraPreviewOrientation(mContext)
        camera.setDisplayOrientation(mOrientation)
        releaseSurfaceTexture()
        mOutputTexture = createDetachedSurfaceTexture()
        try {
            camera.setPreviewTexture(mOutputTexture)
            camera.setPreviewCallback(this)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        camera.startPreview()
        mOutputTexture?.let {
            mSurfaceTextureListener?.onSurfaceTexturePrepared(it)
        }
    }

    override fun startRecording(callback:((video: File?)->Unit)) {
    }

    override fun stopRecord() {
    }

    /**
     * 创建一个SurfaceTexture并
     *
     * @return
     */
    @SuppressLint("ObsoleteSdkInt")
    private fun createDetachedSurfaceTexture(): SurfaceTexture {
        // 创建一个新的SurfaceTexture并从解绑GL上下文
        val surfaceTexture = SurfaceTexture(0)
        surfaceTexture.detachFromGLContext()
        if (Build.VERSION.SDK_INT >= 21) {
            var outputThread = mOutputThread
            outputThread?.quit()
            outputThread = HandlerThread("FrameAvailableThread")
            outputThread.start()
            surfaceTexture.setOnFrameAvailableListener(
                { texture: SurfaceTexture ->
                    mFrameAvailableListener?.onFrameAvailable(texture)
                },
                Handler(outputThread.looper)
            )
            mOutputThread = outputThread
        } else {
            surfaceTexture.setOnFrameAvailableListener { texture: SurfaceTexture ->
                mFrameAvailableListener?.onFrameAvailable(texture)
            }
        }
        return surfaceTexture
    }

    /**
     * 释放资源
     */
    private fun releaseSurfaceTexture() {
        mOutputTexture?.let {
            mSurfaceTextureListener?.onSurfaceTextureDestroyed(it)
            it.release()
        }
        mOutputTexture = null

        mOutputThread?.quit()
        mOutputThread = null
    }

    override fun closeCamera() {
        mCamera?.let {
            it.setPreviewCallback(null)
            it.setPreviewCallbackWithBuffer(null)
            it.addCallbackBuffer(null)
            it.stopPreview()
            it.release()
        }
        mCamera = null

        releaseSurfaceTexture()
    }

    override fun setOnSurfaceTextureListener(listener: OnSurfaceTextureListener?) {
        mSurfaceTextureListener = listener
    }

    override fun setPreviewCallback(callback: PreviewCallback?) {
        mPreviewCallback = callback
    }

    override fun setOnFrameAvailableListener(listener: OnFrameAvailableListener?) {
        mFrameAvailableListener = listener
    }

    override fun switchCamera() {
        var front = !isFront
        front = front && com.billiecamera.camera.camera.CameraApi.hasFrontCamera(mContext)
        // 期望值不一致
        if (front != isFront) {
            isFront = front
            openCamera()
        }
    }

    override fun destroy() {
    }

    override var isFront: Boolean
        get() = mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT
        set(value) {
            mCameraId = if (value) {
                Camera.CameraInfo.CAMERA_FACING_FRONT
            } else {
                Camera.CameraInfo.CAMERA_FACING_BACK
            }
        }

    override val orientation: Int
        get() = mOrientation

    override val previewWidth: Int
        get() = mPreviewWidth

    override val previewHeight: Int
        get() = mPreviewHeight

    @Deprecated("Deprecated in Java")
    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        mPreviewCallback?.onPreviewFrame(data)
    }

    override fun canAutoFocus(): Boolean {
        val focusModes = mCamera?.parameters?.supportedFocusModes
        return focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)
    }

    override fun autoFocus(display: Display, x: Float, y: Float, width: Int, height: Int, focusSize: Float) {
        val camera = mCamera ?: return
        val tapAreaInCamera = calculateTapArea(x, y, width, height, focusSize)
        val parameters = camera.parameters // 先获取当前相机的参数配置对象
        if (supportAutoFocusFeature(parameters)) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO // 设置聚焦模式
        }
        if (parameters.maxNumFocusAreas > 0) {
            val focusAreas: MutableList<Camera.Area> = ArrayList()
            focusAreas.add(Camera.Area(tapAreaInCamera, CameraDisplayInfo.Weight))
            // 设置聚焦区域
            if (parameters.maxNumFocusAreas > 0) {
                parameters.focusAreas = focusAreas
            }
            // 设置计量区域
            if (parameters.maxNumMeteringAreas > 0) {
                parameters.meteringAreas = focusAreas
            }
            // 取消掉进程中所有的聚焦功能
            camera.parameters = parameters
            camera.autoFocus { _: Boolean, it: Camera ->
                val params = it.parameters
                // 设置自动对焦
                if (supportAutoFocusFeature(params)) {
                    it.cancelAutoFocus()
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }
                it.parameters = params
                it.autoFocus(null)
            }
        }
    }

    override fun supportTorch(front: Boolean): Boolean {
        return if (front) {
            true
        } else {
            !checkSupportFlashLight(mCamera)
        }

    }

    override fun setFlashLight(on: Boolean) {
        if (supportTorch(isFront)) {
            return
        }
        mCamera?.let {
            val parameters = it.parameters
            if (on) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            } else {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            }
            it.parameters = parameters
        }
    }

    override fun zoom(zoomRatio: Float) {
        if (canZoom()) {
            mCamera?.let {
                val parameters = it.parameters
                val maxZoom = parameters.maxZoom
                parameters.zoom = max(0, min((zoomRatio * 100).toInt(), maxZoom))
                it.parameters = parameters
            }
        }
    }

    private fun canZoom(): Boolean {
        return mCamera?.parameters?.isZoomSupported ?: false
    }

    override fun zoomIn() {
        if (canZoom()) {
            mCamera?.let {
                val parameters = it.parameters
                val current = parameters.zoom
                val maxZoom = parameters.maxZoom
                parameters.zoom = min(current + 1, maxZoom)
                it.parameters = parameters
            }
        }
    }

    override fun zoomOut() {
        if (canZoom()) {
            mCamera?.let {
                val parameters = it.parameters
                val current = parameters.zoom
                parameters.zoom = max(current - 1, 0)
                it.parameters = parameters
            }
        }
    }

    override val zoomRatio: Float
        get() {
            if (canZoom()) {
                val parameters = mCamera!!.parameters
                val current = parameters.zoom
                val zoomRatios = parameters.zoomRatios
                val zoomValue = zoomRatios[current]
                return zoomValue / 100f
            }
            return 1.0f
        }


    /**
     * 设置预览大小
     *
     * @param camera
     * @param expectWidth
     * @param expectHeight
     */
    private fun setPreviewSize(camera: Camera, expectWidth: Int, expectHeight: Int) {
        val parameters = camera.parameters
        val size = calculatePerfectSize(
            parameters.supportedPreviewSizes,
            expectWidth, expectHeight, CalculateType.Lower
        )
        parameters.setPreviewSize(size.width, size.height)
        mPreviewWidth = size.width
        mPreviewHeight = size.height
        camera.parameters = parameters
    }

    /**
     * 设置拍摄的照片大小
     *
     * @param camera
     * @param expectWidth
     * @param expectHeight
     */
    private fun setPictureSize(camera: Camera, expectWidth: Int, expectHeight: Int) {
        val parameters = camera.parameters
        val size = calculatePerfectSize(
            parameters.supportedPictureSizes,
            expectWidth, expectHeight, CalculateType.Max
        )
        parameters.setPictureSize(size.width, size.height)
        camera.parameters = parameters
    }

    /**
     * 设置预览角度，setDisplayOrientation本身只能改变预览的角度
     * previewFrameCallback以及拍摄出来的照片是不会发生改变的，拍摄出来的照片角度依旧不正常的
     * 拍摄的照片需要自行处理
     * 这里Nexus5X的相机简直没法吐槽，后置摄像头倒置了，切换摄像头之后就出现问题了。
     *
     * @param activity
     */
    private fun calculateCameraPreviewOrientation(activity: Activity): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(CameraDisplayInfo.instance.cameraId, info)
        val rotation = activity.windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360
        } else {
            result = (info.orientation - degrees + 360) % 360
        }
        return result
    }

    /**
     * 计算点击区域
     *
     * @param x
     * @param y
     * @param width
     * @param height
     * @param focusSize
     * @return
     */
    private fun calculateTapArea(x: Float, y: Float, width: Int, height: Int, focusSize: Float): Rect {
        val viewFocusWidth = width * focusSize
        val viewFocusHeight = height * focusSize
        val left = clamp(x - viewFocusWidth / 2, 0f, width - viewFocusWidth)
        val top = clamp(y - viewFocusHeight / 2, 0f, height - viewFocusHeight)
        val matrix = Matrix()
        //matrix.postScale(mirror ? -1 : 1, 1);
        matrix.postRotate(orientation.toFloat())
        matrix.postScale(width / 2000f, height / 2000f)
        matrix.postTranslate(width / 2f, height / 2f)
        val focusRect = RectF(left, top, left + viewFocusWidth, top + viewFocusHeight)
        val rect = Rect()
        matrix.mapRect(focusRect)
        rectFToRect(focusRect, rect)
        return rect
    }

    /**
     * 判断是否支持自动对焦
     *
     * @param parameters
     * @return
     */
    private fun supportAutoFocusFeature(parameters: Camera.Parameters): Boolean {
        val focusModes = parameters.supportedFocusModes
        return focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
    }

    /**
     * 比较器
     */
    private class CompareAreaSize : Comparator<Camera.Size> {
        override fun compare(pre: Camera.Size, after: Camera.Size): Int {
            return signum(
                pre.width.toLong() * pre.height -
                        after.width.toLong() * after.height
            )
        }
    }
}