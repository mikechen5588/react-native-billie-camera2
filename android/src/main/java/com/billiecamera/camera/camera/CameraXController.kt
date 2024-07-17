package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.provider.MediaStore
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.billiecamera.camera.utils.FileUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
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
class CameraXController(context: Context, lifecycleOwner: LifecycleOwner) : ICameraController {

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
    private val mExecutor = Executors.newSingleThreadExecutor()
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



    // 新参数
    private val cameraCapabilities = mutableMapOf<Int, CameraCapability>()
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var currentRecording: Recording? = null
    private var recordingState: VideoRecordEvent? = null

    private var qualityIndex = 0
    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(context) }
    private var enumerationDeferred: Deferred<Unit>? = null

    private var mCallback: ((video: File?) -> Unit)? = null


    init {
        mContext = context
        mLifecycleOwner = lifecycleOwner
        mFacingFront = false
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

    /**
     * 初始化相机配置
     */
    private fun bindCameraUseCases() {
        initCamera()

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
        // mPreviewAnalyzer = ImageAnalysis.Builder()
        //     .setTargetResolution(Size(mPreviewWidth, mPreviewHeight))
        //    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        //    .build()
        //    .apply {
        //        mPreviewCallback?.let {
        //            setAnalyzer(mExecutor, PreviewCallbackAnalyzer(it))
        //        }
        //    }

        // 前后置摄像头选择器
        var lensFacing = CameraSelector.LENS_FACING_BACK
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (mFacingFront){
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    CameraSelector.LENS_FACING_BACK
                }
            )
            .build()


        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality = cameraCapabilities[lensFacing]?.qualities?.get(qualityIndex)
        val qualitySelector = QualitySelector.from(quality ?: Quality.FHD)
        // val rotation = root.videoPreview.display?.rotation

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)


        try {
            mPreview = preview
            // 绑定输出
            mCamera = cameraProvider.bindToLifecycle(
                mLifecycleOwner,
                cameraSelector,
                preview,
                videoCapture,
                // mPreviewAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initCamera() {
        mLifecycleOwner.lifecycleScope.async {
            val localProvider = mCameraProvider ?: return@async
            val lensFacings = arrayOf(CameraSelector.DEFAULT_BACK_CAMERA,
                CameraSelector.DEFAULT_FRONT_CAMERA)
            val qualities = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            for (camSelector in lensFacings) {
                try {
                    // just get the camera.cameraInfo to query capabilities
                    // we are not binding anything here.
                    if (localProvider.hasCamera(camSelector)) {
                        val camera = localProvider.bindToLifecycle(mLifecycleOwner, camSelector)
                        QualitySelector.getSupportedQualities(camera.cameraInfo)
                            .filter { quality ->
                                qualities.contains(quality)
                            }.also {
                                CameraCapability(
                                    camSelector,
                                    camera.cameraInfo.lensFacing,
                                    it
                                ).apply {
                                    cameraCapabilities[camera.cameraInfo.lensFacing] = this
                                }
                            }
                    }
                } catch (exc: java.lang.Exception) {
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(callback: (video: File?) -> Unit) {
        mCallback = callback
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA).format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            mContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording?.stop()
        currentRecording = videoCapture.output
            .prepareRecording(mContext, mediaStoreOutput)
            .apply { withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

    }

    override fun stopRecord() {
        currentRecording?.stop()
        currentRecording = null
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event
        if (event is VideoRecordEvent.Finalize) {
            recordingState = null
            // display the captured video
            val videoFile = FileUtils.getAbsolutePathFromUri(
                context, event.outputResults.outputUri)?.let { File(it) }

            // startVideoPlayInit(event.outputResults.outputUri)
            // display the captured video

            mCallback?.invoke(videoFile)
            mCallback = null
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
        var lensFacing = CameraSelector.LENS_FACING_BACK
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(
                if (mFacingFront){
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    CameraSelector.LENS_FACING_BACK
                }
            )
            .build()

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality = cameraCapabilities[lensFacing]?.qualities?.get(qualityIndex)
        val qualitySelector = QualitySelector.from(quality ?: Quality.FHD)
        // val rotation = root.videoPreview.display?.rotation

        // build a recorder, which can:
        //   - record video/audio to MediaStore(only shown here), File, ParcelFileDescriptor
        //   - be used create recording(s) (the recording performs recording)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            // 绑定输出
            mCamera = cameraProvider.bindToLifecycle(
                mLifecycleOwner,
                cameraSelector,
                mPreview,
                videoCapture,
                // mPreviewAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun destroy() {
        try {
            mExecutor.shutdown()
        } catch (e:Throwable) {
            e.printStackTrace()
        }
        // 解除绑定
        mIsCameraOpened = false
        mCameraProvider?.unbindAll()
        mCameraProvider = null

        currentRecording?.stop()
        currentRecording = null

        mOutputTexture = null

        mCallback = null
        mFrameAvailableListener = null
        mSurfaceTextureListener = null
        mPreviewCallback = null
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
        return mCamera != null
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


data class CameraCapability(val camSelector: CameraSelector, val lensFacing:Int, val qualities: List<Quality>)