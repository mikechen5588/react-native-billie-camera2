package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * CameraX库封装处理
 */
@SuppressLint("LogNotTimber")
class CameraXController2(context: Context, lifecycleOwner: LifecycleOwner) : ICameraController {

    companion object {
        private const val TAG = "CameraXController"

        // 16:9的默认宽高(理想值)，CameraX的预览方式与Camera1不一致，设置的预览宽高需要是实际的预览宽高
        private const val DEFAULT_16_9_WIDTH = 1080
        private const val DEFAULT_16_9_HEIGHT = 1920
    }

    // 预览宽度
    private val mPreviewWidth = DEFAULT_16_9_WIDTH

    // 预览高度
    private val mPreviewHeight = DEFAULT_16_9_HEIGHT

    // 预览角度
    private val mRotation: Int
    private val mContext: Context

    // 生命周期对象(Fragment/Activity)
    private val mLifecycleOwner: LifecycleOwner

    // 是否打开前置摄像头
    private var mFacingFront: Boolean

    // Camera提供者
    private var mCameraProvider: ProcessCameraProvider? = null

    // Camera接口
    private var mCamera: Camera? = null

    // 预览配置
    private var mPreview: Preview? = null

    // 预览帧
    private val mExecutor: Executor = Executors.newSingleThreadExecutor()
    private var mPreviewAnalyzer: ImageAnalysis? = null

    // 预览回调
    private var mPreviewCallback: PreviewCallback? = null

    // SurfaceTexture准备监听器
    private var mSurfaceTextureListener: OnSurfaceTextureListener? = null

    // 纹理更新监听器
    private var mFrameAvailableListener: OnFrameAvailableListener? = null

    // 相机数据输出的SurfaceTexture
    private var mOutputTexture: SurfaceTexture? = null
    private var mIsCameraOpened = false



    init {
        mContext = context
        mLifecycleOwner = lifecycleOwner
        mFacingFront = true
        mRotation = 90
    }

    @SuppressLint("RestrictedApi")
    override fun openCamera() {
        mIsCameraOpened = true
        mLifecycleOwner.lifecycleScope.launch {
            mCameraProvider = ProcessCameraProvider.getInstance(mContext).await()
            if (!mIsCameraOpened) {
                mCameraProvider?.unbindAll()
                mCameraProvider = null
                return@launch
            }
            bindCameraUseCases()
        }
    }

    override fun startRecording(callback:((video: File?)->Unit)) {
    }

    override fun stopRecord() {
    }

    /**
     * 初始化相机配置
     */
    private suspend fun bindCameraUseCases() {

        val cameraProvider = mCameraProvider ?: return
        // 解除绑定
        cameraProvider.unbindAll()

        // 预览画面
        val preview = Preview.Builder()
            .setTargetResolution(Size(mPreviewWidth, mPreviewHeight))
            .build()

        // 预览绑定SurfaceTexture
        preview.setSurfaceProvider { surfaceRequest: SurfaceRequest ->
            // 创建SurfaceTexture
            val surfaceTexture = createDetachedSurfaceTexture(surfaceRequest.resolution)
            val surface = Surface(surfaceTexture)
            surfaceRequest.provideSurface(surface, mExecutor) { surface.release() }
            mOutputTexture?.let {
                mSurfaceTextureListener?.onSurfaceTexturePrepared(it)
            }
        }

        // 预览帧回调
        mPreviewAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(mPreviewWidth, mPreviewHeight))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                mPreviewCallback?.let {
                    setAnalyzer(mExecutor, PreviewCallbackAnalyzer(it))
                }
            }
        // 前后置摄像头选择器
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (mFacingFront)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK
            )
            .build()


        try {
            mPreview = preview
            // 绑定输出
            mCamera = cameraProvider.bindToLifecycle(
                mLifecycleOwner, cameraSelector, preview,
                mPreviewAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建一个SurfaceTexture并
     */
    private fun createDetachedSurfaceTexture(size: Size): SurfaceTexture {
        // 创建一个新的SurfaceTexture并从解绑GL上下文
        var outputTexture = mOutputTexture
        if (outputTexture == null) {
            outputTexture = SurfaceTexture(0)
                .apply {
                    setDefaultBufferSize(size.width, size.height)
                    detachFromGLContext()
                    setOnFrameAvailableListener { texture: SurfaceTexture ->
                        mFrameAvailableListener?.onFrameAvailable(texture)
                    }
                }
            mOutputTexture = outputTexture
        }
        return outputTexture
    }

    /**
     * 释放输出的SurfaceTexture，防止内存泄露
     */
    private fun releaseSurfaceTexture() {
        mOutputTexture?.let {
            mSurfaceTextureListener?.onSurfaceTextureDestroyed(it)
            it.release()
        }
        mOutputTexture = null
    }

    @SuppressLint("RestrictedApi")
    override fun closeCamera() {
        try {
            mIsCameraOpened = false
            mCameraProvider?.unbindAll()
            mCameraProvider = null
            releaseSurfaceTexture()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    @SuppressLint("RestrictedApi")
    override fun switchCamera() {
        isFront = !isFront
        val cameraProvider = mCameraProvider ?: return
        // 解除绑定
        cameraProvider.unbindAll()
        // 前后置摄像头选择器
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (mFacingFront)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK
            )
            .build()
        try {
            // 绑定输出
            mCamera = cameraProvider.bindToLifecycle(
                mLifecycleOwner, cameraSelector, mPreview,
                mPreviewAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun destroy() {

    }

    override var isFront: Boolean
        get() = mFacingFront
        set(value) {
            mFacingFront = value
        }

    override val orientation: Int
        get() = mRotation

    override val previewWidth: Int
        get() {
            return if (mRotation == 90 || mRotation == 270) {
                mPreviewHeight
            } else {
                mPreviewWidth
            }
        }

    override val previewHeight: Int
        get() {
            return if (mRotation == 90 || mRotation == 270) {
                mPreviewWidth
            } else {
                mPreviewHeight
            }
        }

    override fun canAutoFocus(): Boolean {
        if (mCamera == null) {
            return false
        }
//        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //        final CameraCharacteristics characteristics = manager.getCameraCharacteristics()
        return true
    }


    override fun autoFocus(display: Display, x: Float, y: Float, width: Int, height: Int, focusSize: Float) {
        val camera = mCamera ?: return
        val cameraInfo = camera.cameraInfo
        val meteringPointFactory = DisplayOrientedMeteringPointFactory(display, cameraInfo, width.toFloat(), height.toFloat())
        val meteringPoint = meteringPointFactory.createPoint(x, y, focusSize)

        /**
         * 自动对焦，自动曝光，自动白平衡
         */
        val meteringAction = FocusMeteringAction
            .Builder(meteringPoint, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AWB)
            .setAutoCancelDuration(3000, TimeUnit.MILLISECONDS)
            .build()
        camera.cameraControl.startFocusAndMetering(meteringAction)
            .apply {
                addListener({
                    try {
                        if (get().isFocusSuccessful) {
                            println("auto focus successfully!!")
                        } else {
                            println("auto focus failed!!")
                        }
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(mContext))
            }
    }

    override fun supportTorch(front: Boolean): Boolean {
        return mCamera?.cameraInfo?.hasFlashUnit() ?: false
    }

    override fun setFlashLight(on: Boolean) {
        if (!supportTorch(isFront)) {
            println("Failed to set flash light: $on")
            return
        }
        mCamera?.cameraControl?.enableTorch(on)
    }

    override fun zoom(zoomRatio: Float) {
        val camera = mCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val fixedZoomRatio = max(
            zoomState.minZoomRatio,
            min(
                zoomState.maxZoomRatio,
                zoomRatio
            )
        )
        camera.cameraControl.setZoomRatio(fixedZoomRatio)
    }

    override fun zoomIn() {
        val camera = mCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val currentZoomRatio = min(
            zoomState.maxZoomRatio,
            zoomState.zoomRatio + 0.1f
        )
        camera.cameraControl.setZoomRatio(currentZoomRatio)
    }

    override fun zoomOut() {
        val camera = mCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val currentZoomRatio = max(
            zoomState.minZoomRatio,
            zoomState.zoomRatio - 0.1f
        )
        camera.cameraControl.setZoomRatio(currentZoomRatio)
    }

    override val zoomRatio: Float
        get() {
            val zoomState = mCamera?.cameraInfo?.zoomState?.value ?: return 1.0f
            return zoomState.zoomRatio
        }

}