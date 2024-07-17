package com.billiecamera.camera.render

import android.app.Activity
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.Window
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.billiecamera.camera.camera.CameraXController
import com.billiecamera.camera.camera.ICameraController
import com.billiecamera.camera.camera.OnFrameAvailableListener
import com.billiecamera.camera.camera.OnSurfaceTextureListener
import com.billiecamera.camera.camera.PreviewCallback
import com.billiecamera.camera.render.base.BaseGLFilter
import com.billiecamera.camera.render.base.RenderEngine
import com.billiecamera.camera.render.core.RendererThread
import java.io.File

/**
 * @description: 用于控制相机、创建渲染环境、以及生命周期相关
 * @date: 2022/10/21 16:01
 */
class MCameraManager(val context: Activity) :
    PreviewCallback,
    OnFrameAvailableListener,
    OnSurfaceTextureListener,
    DefaultLifecycleObserver {

    var rendererListener: RendererListener? = null
    private lateinit var mRenderEngine: RenderEngine
    private lateinit var mCameraController: ICameraController
    private val mRendererThread = RendererThread(context.applicationContext)

    var displayId:Int = 0

    fun setupWithLifeCycle(lifecycleOwner: LifecycleOwner, renderTarget: Any) {
        lifecycleOwner.lifecycle.addObserver(this)
        bindRenderTarget(renderTarget)
    }

    override fun onCreate(owner: LifecycleOwner) {
        createRenderThread()
        createCamera(context, owner)
    }

    override fun onStart(owner: LifecycleOwner) {

    }

    override fun onResume(owner: LifecycleOwner) {
        openCamera()
    }

    override fun onPause(owner: LifecycleOwner) {
        closeCamera()
    }

    override fun onStop(owner: LifecycleOwner) {

    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroyRenderThread()
        mCameraController.destroy()
        owner.lifecycle.removeObserver(this)
    }

    private fun bindRenderTarget(renderTarget: Any) {
        println("bindRenderTarget renderTarget=$renderTarget")
        when (renderTarget) {
            is SurfaceView -> {
                setupWithRenderTarget(renderTarget)
            }
            is TextureView -> {
                setupWithRenderTarget(renderTarget)
            }
            else -> {
                throw IllegalArgumentException("invalid render target, must be SurfaceView or TextureView!!!")
            }
        }
    }

    private fun createCamera(context: Activity, lifecycleOwner: LifecycleOwner) {
        val controller = CameraXController(context, lifecycleOwner)
        with(controller) {
            //setPreviewCallback(this@MCameraManager)
            setOnFrameAvailableListener(this@MCameraManager)
            setOnSurfaceTextureListener(this@MCameraManager)
        }
        mCameraController = controller
    }

    private fun setupWithRenderTarget(surfaceView: SurfaceView) {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                createRender(holder)
                rendererListener?.onRendererCreated()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                setRenderTarget(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                destroyRender()
            }
        })
    }

    private fun setupWithRenderTarget(surfaceView: TextureView) {
        surfaceView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                createRender(surface)
                rendererListener?.onRendererCreated()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                setRenderTarget(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                destroyRender()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
        }
    }

    private fun createRenderThread() {
        val start = SystemClock.uptimeMillis()
        println("createRenderThread++")
        mRendererThread.start()
        mRenderEngine = mRendererThread.getHandler()
        val end = SystemClock.uptimeMillis()
        println("createRenderThread--,const time=${end - start}ms")
    }

    private fun destroyRenderThread() {
        val start = SystemClock.uptimeMillis()
        println("destroyRenderThread++")
        mRendererThread.quit()
        val end = SystemClock.uptimeMillis()
        println("destroyRenderThread--,const time=${end - start}ms")
    }

    fun startRecord(callback:((video:File?)->Unit)) {
        val start = SystemClock.uptimeMillis()
        println("startRecord++")
        mCameraController.startRecording(callback)
        val end = SystemClock.uptimeMillis()
        println("startRecord--,const time=${end - start}ms")
    }

    fun stopRecord() {
        mCameraController.stopRecord()
    }

    fun openCamera() {
        val start = SystemClock.uptimeMillis()
        println("openCamera++")
        mCameraController.openCamera()
        val end = SystemClock.uptimeMillis()
        println("openCamera--,const time=${end - start}ms")

    }

    fun closeCamera() {
        val start = SystemClock.uptimeMillis()
        println("closeCamera++")
        mCameraController.closeCamera()
        val end = SystemClock.uptimeMillis()
        println("closeCamera--,const time=${end - start}ms")
    }

    fun switchCamera() {
        println("switchCamera")
        mCameraController.switchCamera()
    }

    private fun clearRenderTarget() {
        mRenderEngine.clearRenderTarget()
    }

    private fun createRender(renderTarget: Any) {
        mRenderEngine.createRenderer(renderTarget)
    }

    private fun setRenderTarget(width: Int, height: Int) {
        mRenderEngine.updateRenderTargetInfo(width, height)
    }

    private fun destroyRender() {
        mRenderEngine.destroyRenderer()
    }

    fun applyFilter(filter: BaseGLFilter) {
        mRenderEngine.applyFilter(filter)
    }

    fun takePicture(listener: RenderEngine.PictureListener) {
        mRenderEngine.takePicture(listener)
    }

    override fun onPreviewFrame(data: ByteArray) {

    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        mRenderEngine.renderFrame()
    }

    override fun onSurfaceTexturePrepared(surfaceTexture: SurfaceTexture) {
        val cameraController = mCameraController
        val orientation = cameraController.orientation
        val width: Int
        val height: Int
        if (orientation == 90 || orientation == 270) {
            width = cameraController.previewHeight
            height = cameraController.previewWidth
        } else {
            width = cameraController.previewWidth
            height = cameraController.previewHeight
        }
        mRenderEngine.setInputTexture(surfaceTexture, width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) {
        mRenderEngine.clearInputTexture()
    }

    private fun isCameraFront(): Boolean {
        return mCameraController.isFront
    }

    fun autoFocus(display: Display, x: Float, y: Float, width: Int, height: Int, focusSize: Float) {
        mCameraController.autoFocus(display, x, y, width, height, focusSize)
    }

    fun openTorch(window: Window) {
        println("openTorch")
        if (mCameraController.isFront) {
            val lp = window.attributes.apply {
                screenBrightness = 1.0f
            }
            window.attributes = lp
        } else {
            mCameraController.setFlashLight(true)
        }
    }

    fun closeTorch(window: Window) {
        println("closeTorch")
        if (mCameraController.isFront) {
            val lp = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = lp
        } else {
            mCameraController.setFlashLight(false)
        }
    }

    fun zoom(scaleFactor: Float) {
        println("zoom=$scaleFactor")
        mCameraController.zoom(scaleFactor)
    }

    fun getZoomRatio(): Float {
        return mCameraController.zoomRatio
    }

    interface RendererListener {
        fun onRendererCreated()
    }
}