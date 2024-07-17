package com.billiecamera.camera.render.core;

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.view.Surface
import android.view.SurfaceHolder
import com.billiecamera.camera.render.base.BaseGLFilter
import com.billiecamera.camera.render.base.RenderEngine
import com.billiecamera.camera.render.egl.EglCore
import com.billiecamera.camera.render.egl.WindowSurface
import com.billiecamera.camera.render.filter.GLImageReader
import com.billiecamera.camera.render.filter.OESFilter
import com.billiecamera.camera.render.utils.OpenGLUtils
import com.wonderbricks.camera.render.base.DisplayFilter
import java.nio.FloatBuffer
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * @description: 相机渲染
 * 
 * @date: 2022/10/21 10:36
 */
class GLRenderEngine(val context: Context) : RenderEngine, GLImageReader.ImageReceiveListener {

    companion object {
        private const val TAG = "GLRenderEngine"
    }

    private var mScaleType = ScaleType.CENTER_CROP

    private var mVertexBuffer: FloatBuffer? = null
    private var mTextureBuffer: FloatBuffer? = null

    private var mDisplayVertexBuffer: FloatBuffer? = null
    private var mDisplayTextureBuffer: FloatBuffer? = null

    private var mDisplayWidth = 0
    private var mDisplayHeight = 0

    private var mInputWidth = 0
    private var mInputHeight = 0

    private var mInputTexture: SurfaceTexture? = null

    /**
     * EGL核心类
     */
    private var mEglCore: com.billiecamera.camera.render.egl.EglCore? = null

    /**
     * 最终显示的Surface
     */
    private var mDisplaySurface: com.billiecamera.camera.render.egl.WindowSurface? = null

    /**
     * OES纹理
     */
    private var mOESTexture = GLES30.GL_NONE

    private val mOnRenderCallbacks = LinkedList<RenderEngine.OnRenderCallback>()

    /**
     * 同步
     */
    private var mSync = Object()
    private var mReadingImage = false
    private var mImageReader: GLImageReader? = null

    private var mOESFilter = OESFilter(context)
    private var mDisplayFilter = DisplayFilter(context)
    private var mAdditionalFilter: BaseGLFilter? = null
    private var mPictureListener: RenderEngine.PictureListener? = null

    /**
     * 创建EGL环境
     */
    override fun createRenderer(renderTarget: Any) {

        createEGL(renderTarget)

        GLES30.glDisable(GLES30.GL_DITHER)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        mOESFilter.initializeProgram()
        mAdditionalFilter?.initializeProgram()
        mDisplayFilter.initializeProgram()
        mImageReader = GLImageReader(context, this).apply {
            bindToEgl(mEglCore!!.eglContext)
            initializeProgram()
        }

    }

    private fun createDisplaySurface(surface: Surface) {
        mDisplaySurface = WindowSurface(mEglCore, surface, false)
            .apply {
            makeCurrent()
        }
    }

    private fun createDisplaySurface(surface: SurfaceTexture) {
        mDisplaySurface = WindowSurface(mEglCore, surface).apply {
            makeCurrent()
        }
    }

    private fun createDisplaySurface(holder: SurfaceHolder) {
        mDisplaySurface = WindowSurface(
            mEglCore,
            holder.surface,
            false
        ).apply {
            makeCurrent()
        }
    }

    private fun createBuffers() {
        releaseBuffers()
        mDisplayVertexBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(
            com.billiecamera.camera.render.utils.TextureRotationUtils.CubeVertices)
        mDisplayTextureBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(
            com.billiecamera.camera.render.utils.TextureRotationUtils.TextureVertices)
        mVertexBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(com.billiecamera.camera.render.utils.TextureRotationUtils.CubeVertices)
        mTextureBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(com.billiecamera.camera.render.utils.TextureRotationUtils.TextureVertices)
    }

    private fun releaseBuffers() {
        mVertexBuffer?.clear()
        mVertexBuffer = null

        mTextureBuffer?.clear()
        mTextureBuffer = null

        mDisplayVertexBuffer?.clear()
        mDisplayVertexBuffer = null

        mDisplayTextureBuffer?.clear()
        mDisplayTextureBuffer = null
    }

    private fun releaseFilters() {
        mOESFilter.release()
        mDisplayFilter.release()

        mAdditionalFilter?.release()
        mAdditionalFilter = null
    }

    private fun notifyFilterSizeChanged() {
        mOESFilter.setInputSize(mInputWidth, mInputHeight)
        mOESFilter.setDisplaySize(mDisplayWidth, mDisplayHeight)
        mAdditionalFilter?.setInputSize(mInputWidth, mInputHeight)
        mAdditionalFilter?.setDisplaySize(mDisplayWidth, mDisplayHeight)
        mDisplayFilter.setInputSize(mInputWidth, mInputHeight)
        mDisplayFilter.setDisplaySize(mDisplayWidth, mDisplayHeight)
    }

    override fun destroyRenderer() {
        releaseBuffers()
        releaseFilters()

        mImageReader?.release()
        mImageReader = null

        mDisplaySurface?.release()
        mDisplaySurface = null

        mEglCore?.release()
        mEglCore = null

        mReadingImage = false
    }

    override fun renderFrame() {

        if (mEglCore == null || mDisplaySurface == null || mInputTexture == null) {
            println("please init render first!!!")
            return
        }

        ensureOESTexture()

        if (mInputTexture == null || mOESTexture == GLES30.GL_NONE) {
            println("please setup input texture first!!!")
            return
        }

        if (mVertexBuffer == null || mTextureBuffer == null ||
            mDisplayVertexBuffer == null || mDisplayTextureBuffer == null) {
            println("please setup render target info first!!!")
            return
        }


        val inputTexture = mInputTexture!!
        val displaySurface = mDisplaySurface!!
        val vertexBuffer = mVertexBuffer!!
        val textureBuffer = mTextureBuffer!!
        val displayVertexBuffer = mDisplayVertexBuffer!!
        val displayTextureBuffer = mDisplayTextureBuffer!!

        /**
         * 将渲染目标绑定到当前环境
         */
        displaySurface.makeCurrent()

        /**
         * 获取图像和转换矩阵
         */
        try {
            inputTexture.updateTexImage()
            inputTexture.getTransformMatrix(mOESFilter.getTransformMatrix())
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        synchronized(mOnRenderCallbacks) {
            while (!mOnRenderCallbacks.isEmpty()) {
                mOnRenderCallbacks.removeFirst().onRender(this)
            }
        }

        var currentTextureId = mOESTexture
        /**
         * 将相机的OES纹理绘制到一个FBO
         */
        currentTextureId = mOESFilter.drawFrameBuffer(currentTextureId, vertexBuffer, textureBuffer)

        /**
         * 将滤镜绘制到另一个FBO
         */
        currentTextureId = mAdditionalFilter?.drawFrameBuffer(currentTextureId, vertexBuffer, textureBuffer) ?: currentTextureId

        /**
         * 将FBO的纹理绘制到渲染目标
         */

        mDisplayFilter.drawFrame(currentTextureId, displayVertexBuffer, displayTextureBuffer)

        /**
         * 显示到屏幕
         */
        displaySurface.swapBuffers()

        /**
         * 拍照，将当前纹理绘制到ImageReader里面去
         */
        synchronized(mSync) {
            if (mReadingImage) {
                mImageReader?.drawFrame(currentTextureId, vertexBuffer, textureBuffer)
                mReadingImage = false
            }
        }
    }

    override fun setInputTexture(input: SurfaceTexture, width: Int, height: Int) {

        if (mInputTexture != input) {
            mInputTexture = input
            /**
             * 创建OES纹理，用于接收相机传来的数据
             */
            if (mOESTexture != GLES30.GL_NONE) {
                OpenGLUtils.deleteTexture(mOESTexture)
            }
            mOESTexture = GLES30.GL_NONE
        }

        mInputWidth = width
        mInputHeight = height

//        mImageReader?.setInputSize(mInputWidth, mInputHeight)
//        mImageReader?.setImageSize(mInputWidth, mInputHeight)
//
//        mOESFilter.createFrameBufferInNeeded(width, height)
//        mAdditionalFilter?.createFrameBufferInNeeded(width, height)
//
//        onSizeChanged()
    }

    /**
     * 创建OES纹理，用于接收相机传来的数据
     */
    private fun ensureOESTexture() {
        if(mOESTexture != GLES30.GL_NONE) {
            return
        }
        mOESTexture = OpenGLUtils.createOESTexture()
        try {
            mInputTexture?.attachToGLContext(mOESTexture)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mImageReader?.setInputSize(mInputWidth, mInputHeight)
        mImageReader?.setImageSize(mInputWidth, mInputHeight)

        mOESFilter.createFrameBufferInNeeded(mInputWidth, mInputHeight)
        mAdditionalFilter?.createFrameBufferInNeeded(mInputWidth, mInputHeight)

        onSizeChanged()
    }

    private fun onSizeChanged() {
        if (mInputWidth != 0 && mInputHeight != 0 &&
            mDisplayWidth != 0 && mDisplayHeight != 0
        ) {
            adjustCoordinateSize()
            notifyFilterSizeChanged()
        }
    }

    override fun clearInputTexture() {

        mInputTexture = null
        mInputWidth = 0
        mInputHeight = 0

        releaseBuffers()
    }

    override fun updateRenderTargetInfo(width: Int, height: Int) {

        mDisplayWidth = width
        mDisplayHeight = height

        onSizeChanged()
    }

    override fun clearRenderTarget() {
        mDisplayWidth = 0
        mDisplayHeight = 0

        releaseBuffers()
    }

    private fun createEGL(renderTarget: Any) {

        mEglCore = EglCore(
            null,
            EglCore.FLAG_RECORDABLE
        )

        when (renderTarget) {
            is SurfaceHolder -> {
                createDisplaySurface(renderTarget)
            }
            is Surface -> {
                createDisplaySurface(renderTarget)
            }
            is SurfaceTexture -> {
                createDisplaySurface(renderTarget)
            }
            else -> {
                throw IllegalArgumentException("unknown render target=${renderTarget.javaClass.name}")
            }
        }
    }

    private fun adjustCoordinateSize() {
        var textureCoord: FloatArray? = null
        var vertexCoord: FloatArray? = null
        val textureVertices: FloatArray = com.billiecamera.camera.render.utils.TextureRotationUtils.TextureVertices
        val vertexVertices: FloatArray = com.billiecamera.camera.render.utils.TextureRotationUtils.CubeVertices

        val ratioMax: Float = max(
            mDisplayWidth.toFloat() / mInputWidth,
            mDisplayHeight.toFloat() / mInputHeight
        )
        // 新的宽高
        val imageWidth = (mInputWidth * ratioMax).roundToInt()
        val imageHeight = (mInputHeight * ratioMax).roundToInt()
        // 获取视图跟texture的宽高比
        val ratioWidth: Float = imageWidth.toFloat() / mDisplayWidth.toFloat()
        val ratioHeight: Float = imageHeight.toFloat() / mDisplayHeight.toFloat()
        if (mScaleType === ScaleType.CENTER_INSIDE) {
            vertexCoord = floatArrayOf(
                vertexVertices[0] / ratioHeight, vertexVertices[1] / ratioWidth,
                vertexVertices[2] / ratioHeight, vertexVertices[3] / ratioWidth,
                vertexVertices[4] / ratioHeight, vertexVertices[5] / ratioWidth,
                vertexVertices[6] / ratioHeight, vertexVertices[7] / ratioWidth
            )
        } else if (mScaleType === ScaleType.CENTER_CROP) {
            val distHorizontal = (1 - 1 / ratioWidth) / 2
            val distVertical = (1 - 1 / ratioHeight) / 2
            textureCoord = floatArrayOf(
                addDistance(textureVertices[0], distHorizontal), addDistance(textureVertices[1], distVertical),
                addDistance(textureVertices[2], distHorizontal), addDistance(textureVertices[3], distVertical),
                addDistance(textureVertices[4], distHorizontal), addDistance(textureVertices[5], distVertical),
                addDistance(textureVertices[6], distHorizontal), addDistance(textureVertices[7], distVertical)
            )
        }
        if (vertexCoord == null) {
            vertexCoord = vertexVertices
        }
        if (textureCoord == null) {
            textureCoord = textureVertices
        }
        // 更新VertexBuffer 和 TextureBuffer
        if (mVertexBuffer == null || mTextureBuffer == null ||
            mDisplayVertexBuffer == null || mDisplayTextureBuffer == null
        ) {
            createBuffers()
        }
        mDisplayVertexBuffer?.clear()
        mDisplayVertexBuffer?.put(vertexCoord)?.position(0)
        mDisplayTextureBuffer?.clear()
        mDisplayTextureBuffer?.put(textureCoord)?.position(0)
    }

    /**
     * 计算距离
     * @param coordinate
     * @param distance
     * @return
     */
    private fun addDistance(coordinate: Float, distance: Float): Float {
        return if (coordinate == 0.0f) distance else 1 - distance
    }


    override fun applyFilter(filter: BaseGLFilter) {
        mAdditionalFilter = filter
        postOnRenderCallback(object : RenderEngine.OnRenderCallback {
            override fun onRender(renderEngine: RenderEngine) {
                mAdditionalFilter?.initializeProgram()
                mAdditionalFilter?.setInputSize(mInputWidth, mInputHeight)
                mAdditionalFilter?.setDisplaySize(mDisplayWidth, mDisplayHeight)
                mAdditionalFilter?.createFrameBufferInNeeded(mInputWidth, mInputWidth)
            }
        })
    }

    override fun takePicture(pictureListener: RenderEngine.PictureListener) {
        synchronized(mSync) {
            mReadingImage = true
            mPictureListener = pictureListener
        }
    }

    override fun postOnRenderCallback(callback: RenderEngine.OnRenderCallback) {
        synchronized(mOnRenderCallbacks) {
            mOnRenderCallbacks.addLast(callback)
        }
    }

    override fun onImageReceive(bitmap: Bitmap) {
        mPictureListener?.onTakePicture(bitmap)
        mPictureListener = null
    }
}