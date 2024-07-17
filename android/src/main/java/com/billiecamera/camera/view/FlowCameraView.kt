package com.billiecamera.camera.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.billiecamera.R
import com.billiecamera.camera.listener.CaptureListener
import com.billiecamera.camera.listener.ClickListener
import com.billiecamera.camera.listener.FlowCameraListener
import com.billiecamera.camera.listener.OnVideoPlayPrepareListener
import com.billiecamera.camera.listener.TypeListener
import com.billiecamera.camera.render.MCameraManager
import com.billiecamera.camera.utils.FileUtils
import com.billiecamera.camera.utils.ScreenOrientationHelper
import com.billiecamera.databinding.FlowCameraViewBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


class FlowCameraView : FrameLayout {

    private var helper: ScreenOrientationHelper? = null

    private val binding by lazy {
        FlowCameraViewBinding.bind(View.inflate(context, R.layout.flow_camera_view, this))
    }

    // 相机管理类
    private lateinit var cameraManager: MCameraManager

    // play video
    private var mMediaPlayer: MediaPlayer? = null

    // click back button handle
    private var leftClickListener:ClickListener? = null

    // browse album and get picture
    private var galleryClickListener:ClickListener? = null

    // camera back
    private var flowCameraListener:FlowCameraListener? = null

    // capture result
    private var resModel:ResModel = ResModel()

    constructor(context: Context) :
            this(context, null)
    constructor(context: Context, attributeSet: AttributeSet?) :
            this(context, attributeSet, 0)
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) :
            super(context, attributeSet, defStyleAttr) {
        initView(context, attributeSet, defStyleAttr)

        // listen to the screen orientation
        helper = ScreenOrientationHelper().apply {
            init(context, object : ScreenOrientationHelper.OrientationListener {
                override fun onChange(orientation: Int) {
                    binding.debugText.text = "角度: ${helper?.rawOrientation}"
                }
            })
        }
    }

    private fun initView(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) {
        val a = context.theme.obtainStyledAttributes(attributeSet,
            R.styleable.FlowCameraView, defStyleAttr, 0)
        val iconSrc = a.getResourceId(R.styleable.FlowCameraView_iconSrc, R.drawable.ic_camera)
        val iconLeft = a.getResourceId(R.styleable.FlowCameraView_iconLeft, 0)
        val iconRight = a.getResourceId(R.styleable.FlowCameraView_iconRight, 0)
        val duration = a.getInteger(R.styleable.FlowCameraView_duration_max, 10 * 1000)
        a.recycle()

        binding.imageSwitch.setImageResource(iconSrc)
        binding.imageSwitch.isEnabled = false

        // Wait for the views to be properly laid out
        binding.videoPreview.post {
            // Keep track of the display in which this view is attached
            val displayId = binding.videoPreview.display.displayId
        }

        binding.captureLayout.setDuration(duration)
        binding.captureLayout.setIconSrc(iconLeft, iconRight)
        binding.captureLayout.setCaptureLisenter(captureHandler)
        binding.captureLayout.setTypeListener(typeListener)
        binding.captureLayout.setLeftClickListener(object : ClickListener {
            override fun onClick() {
                if(leftClickListener != null) {
                    leftClickListener?.onClick()
                } else {
                    getActivity()?.finish()
                }
            }
        })
        binding.captureLayout.setGalleryListener(object : ClickListener {
            override fun onClick() {
                galleryClickListener?.onClick()
            }
        })
    }

    fun initCamera(activity: Activity, lifecycleOwner: LifecycleOwner) {
        if(::cameraManager.isInitialized) {
            return
        }
        cameraManager = MCameraManager(activity).apply {
            setupWithLifeCycle(lifecycleOwner, binding.videoPreview)
            rendererListener = object : MCameraManager.RendererListener {
                override fun onRendererCreated() {
                    // 每次重新创建Renderer都要恢复这个滤镜参数
                    // applyFilter(mDynamicColorFilter)
                    // applyEffect(mCurrentColorIndex)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if(event == Lifecycle.Event.ON_DESTROY) {
                    helper?.unRegister()
                    mMediaPlayer?.stop()
                    mMediaPlayer?.release()
                    lifecycleOwner.lifecycle.removeObserver(this)
                }
            }
        })
    }

    private val typeListener = object : TypeListener {
        override fun cancel() {
            resetState()
        }

        override fun confirm() {
            FileUtils.scanPhotoAlbum(context, File(resModel.uri))
            flowCameraListener?.success(resModel)
        }
    }

    private val captureHandler = object : CaptureListener {
        override fun takePictures() {
            binding.imageSwitch.visibility = View.GONE
            binding.imageFlash.visibility = View.GONE

            val cameraMgr = getCameraMgr() ?: return
            cameraMgr.takePicture {
                binding.captureLayout.post {
                    binding.captureLayout.postDelayed({
                        getCameraMgr()?.closeCamera()
                    }, 500)

                    deleteDeprecateFiles()
                    // Create output file to hold the image
                    val photoFile = FileUtils.saveBitmap(context, it)
                    resModel.uri = photoFile?.absolutePath
                    resModel.contentType = "image"
                    resModel.width = it.width
                    resModel.height = it.height

                    binding.videoPreview.visibility = View.GONE
                    binding.imagePhoto.visibility = View.VISIBLE
                    binding.imagePhoto.setImageBitmap(it)

                    binding.captureLayout.startTypeBtnAnimator()
                }
            }
        }

        override fun recordShort(time: Long) {
            binding.imageSwitch.visibility = View.VISIBLE
            binding.imageFlash.visibility = View.VISIBLE
            binding.captureLayout.resetCaptureLayout()
            binding.captureLayout.setTextWithAnimation("录制时间过短")
        }

        override fun recordStart() {
            binding.imageSwitch.visibility = View.GONE
            binding.imageFlash.visibility = View.GONE

            deleteDeprecateFiles()
            getCameraMgr()?.startRecord {
                resModel.uri = it?.absolutePath
                resModel.contentType = "video"
                startVideoPlayInit(it?.toUri())

                getActivity()?.lifecycleScope?.launch {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(it?.absolutePath)
                    // 获取视频时长，单位：毫秒(ms)
                    val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    resModel.duration = durationString?.toLong()

                    //获取视频宽度（单位：px）
                    val widthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    resModel.width = widthString?.toInt()

                    //获取视频高度（单位：px）
                    val heightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    resModel.height = heightString?.toInt()

                    //获取帧
                    val bitmap = retriever.frameAtTime
                    if(bitmap != null) {
                        resModel.thumbUrl = FileUtils.saveBitmap(context, bitmap)?.toString()
                    }
                }
            }
        }

        override fun recordEnd(time: Long) {
            binding.videoPreview.visibility = View.GONE
            binding.mVideo.visibility = View.VISIBLE
            binding.captureLayout.startTypeBtnAnimator()
            getCameraMgr()?.stopRecord()
        }

        override fun recordZoom(zoom: Float) { }
        override fun recordError() { }
    }


    /**
     * reset status
     */
    private fun resetState() {
        getCameraMgr()?.stopRecord()
        getCameraMgr()?.closeCamera()
        getCameraMgr()?.openCamera()
        binding.mVideo.visibility = View.GONE
        binding.imagePhoto.visibility = View.GONE
        binding.imageSwitch.visibility = View.VISIBLE
        binding.imageFlash.visibility = View.VISIBLE
        binding.videoPreview.visibility = View.VISIBLE
        binding.captureLayout.resetCaptureLayout()
    }

    private fun startVideoPlayInit(uri: Uri?) {
        uri ?: return
        if (binding.mVideo.isAvailable) {
            startVideoPlay(uri, object : OnVideoPlayPrepareListener {
                override fun onPrepared() {
                    binding.videoPreview.visibility = View.GONE
                }
            })
        } else {
            binding.mVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int ) {
                    startVideoPlay(uri, object : OnVideoPlayPrepareListener {
                        override fun onPrepared() {
                            binding.videoPreview.visibility = View.GONE
                        }
                    })
                }
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            }
        }
    }

    /**
     * start to play video
     * @param videoFile
     */
    private fun startVideoPlay(uri: Uri, listener: OnVideoPlayPrepareListener) {
        try {
            mMediaPlayer?.stop()
            mMediaPlayer?.release()
            mMediaPlayer = MediaPlayer()
            mMediaPlayer?.setDataSource(context, uri)
            mMediaPlayer?.setSurface(Surface(binding.mVideo.surfaceTexture))
            mMediaPlayer?.isLooping = true
            mMediaPlayer?.setOnPreparedListener { mp: MediaPlayer ->
                mp.start()
                val ratio = mp.videoWidth * 1f / mp.videoHeight
                val width1 = binding.mVideo.width
                val layoutParams = binding.mVideo.layoutParams
                layoutParams?.height = (width1 / ratio).toInt()
                binding.mVideo.layoutParams = layoutParams
                listener.onPrepared()
            }
            mMediaPlayer?.prepareAsync()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getCameraMgr(): MCameraManager? {
        if(!::cameraManager.isInitialized) {
            return null
        }
        return cameraManager;
    }

    /**
     * public method                     *
     */
    fun setFlowCameraListener(flowCameraListener: FlowCameraListener?) {
        this.flowCameraListener = flowCameraListener
    }

    /**
     * set the max record video time
     */
    fun setRecordVideoMaxTime(maxDurationTime: Int) {
        binding.captureLayout.setDuration(maxDurationTime * 1000)
    }

    /**
     * 设置拍摄模式分别是
     * 单独拍照 单独摄像 或者都支持
     * @param state
     */
    fun setCaptureMode(state: ButtonState) {
        binding.captureLayout.setButtonFeatures(state)
    }

    /**
     * 关闭相机界面按钮
     * @param clickListener
     */
    fun setLeftClickListener(clickListener: ClickListener) {
        leftClickListener = clickListener
    }

    /**
     * browse Gallery
     */
    fun setGalleryClickListener(galleryClickListener:ClickListener) {
        this.galleryClickListener = galleryClickListener
    }

    private fun getActivity():AppCompatActivity? {
        var curContext = context
        while (curContext is ContextWrapper) {
            if(curContext is AppCompatActivity) {
                return curContext
            }
            curContext = curContext.baseContext as ContextWrapper
        }
        return null
    }

    fun deleteDeprecateFiles() {
        if(resModel.uri != null && resModel.uri?.isNotEmpty() == true) {
            File(resModel.uri).apply {
                if(exists()) {
                    delete()
                }
            }
        }
        resModel.uri = null

        if(resModel.thumbUrl != null && resModel.thumbUrl?.isNotEmpty() == true) {
            File(resModel.thumbUrl).apply {
                if(exists()) {
                    delete()
                }
            }
        }
        resModel.thumbUrl = null
    }
}


data class ResModel(
    // file path
    var uri: String? = null,
    // content type, image/video
    var contentType:String? = null,
    // content width
    var width:Int? = null,
    // content height
    var height:Int? = null,
    // video first frame
    var thumbUrl:String? = null,

    var duration:Long? = null) {

    fun isVideo():Boolean {
        return contentType?.contains("video") == true
    }
}