package com.billiecamera.camera.render.base

import android.content.Context
import android.graphics.PointF
import android.opengl.GLES30
import com.billiecamera.camera.render.utils.OpenGLUtils
import com.billiecamera.camera.render.utils.TextureRotationUtils
import java.nio.FloatBuffer
import java.util.LinkedList

/**
 * @description: 滤镜基类
 * 
 * @date: 2022/10/21 14:07
 */
open class BaseGLFilter(
    protected val mContext: Context,
    protected var mVertexShader: String,
    protected var mFragmentShader: String
) : Filter {

    companion object {
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 textureCoordinate;
            void main() {
                gl_Position = aPosition;
                textureCoordinate = aTextureCoord.xy;
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputTexture;
            void main() {
                gl_FragColor = texture2D(inputTexture, textureCoordinate);
            }
        """

        private const val TAG = "BaseFilter"
    }

    constructor(context: Context) : this(context, VERTEX_SHADER, FRAGMENT_SHADER)

    private val mRunOnDraw = LinkedList<Runnable>()

    protected var mIsEnabled = true

    protected var mIsDrawing = false

    protected var mCoordsPerVertex = com.billiecamera.camera.render.utils.TextureRotationUtils.CoordsPerVertex
    protected var mVertexCount = com.billiecamera.camera.render.utils.TextureRotationUtils.CubeVertices.size / mCoordsPerVertex


    protected var mProgramHandle = GLES30.GL_NONE
    protected var mPositionHandle = GLES30.GL_NONE
    protected var mTextureCoordinateHandle = GLES30.GL_NONE
    protected var mInputTextureHandle = GLES30.GL_NONE

    protected var mImageWidth = 0
    protected var mImageHeight = 0

    protected var mDisplayWidth = 0
    protected var mDisplayHeight = 0

    protected var mFrameBufferWidth = 0
    protected var mFrameBufferHeight = 0

    protected var mFrameBuffers = IntArray(0)
    protected var mFrameBufferTextures = IntArray(0)

    protected open val mTextureType = GLES30.GL_TEXTURE_2D


    open fun initializeProgram() {
        println("${javaClass.simpleName} initializeProgram!!!")
        if (mVertexShader.isEmpty() || mFragmentShader.isEmpty()) {
            throw IllegalArgumentException("vertex shader or fragment shader must not be empty!!!")
        }
        mProgramHandle = com.billiecamera.camera.render.utils.OpenGLUtils.createProgram(mVertexShader, mFragmentShader)
        mPositionHandle = GLES30.glGetAttribLocation(mProgramHandle, "aPosition")
        mTextureCoordinateHandle = GLES30.glGetAttribLocation(mProgramHandle, "aTextureCoord")
        mInputTextureHandle = GLES30.glGetUniformLocation(mProgramHandle, "inputTexture")
    }

    fun createFrameBufferInNeeded(width: Int, height: Int) {
        if (mFrameBuffers.isNotEmpty() ||
            mFrameBufferWidth != width ||
            mFrameBufferHeight != height) {
            destroyFrameBuffer()
        }
        mFrameBufferWidth = width
        mFrameBufferHeight = height
        mFrameBuffers = IntArray(1)
        mFrameBufferTextures = IntArray(1)
        com.billiecamera.camera.render.utils.OpenGLUtils.createFrameBuffer(mFrameBuffers, mFrameBufferTextures, mFrameBufferWidth, mFrameBufferHeight)
    }

    private fun bindFrameBuffer() {
        GLES30.glViewport(0, 0, mFrameBufferWidth, mFrameBufferHeight)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFrameBuffers[0])
        GLES30.glUseProgram(mProgramHandle)
    }

    private fun unBindFrameBuffer() {
        GLES30.glUseProgram(0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_NONE)
    }

    fun drawFrameBuffer(textureId: Int, vertexBuffer: FloatBuffer, textureBuffer: FloatBuffer): Int {
        if (textureId == GLES30.GL_NONE || mFrameBuffers.isEmpty() || !mIsEnabled) {
            return textureId
        }
        /**
         * 绑定FBO
         */
        bindFrameBuffer()

        mIsDrawing = true

        runPendingTaskOnDraw()
        /**
         * 绘制纹理
         */
        drawTexture(textureId, vertexBuffer, textureBuffer)
        /**
         * 解绑FBO
         */
        unBindFrameBuffer()

        mIsDrawing = false
        /**
         * 将FBO的纹理返回
         */
        return mFrameBufferTextures[0]
    }


    open fun drawFrame(textureId: Int, vertexBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        if (textureId == GLES30.GL_NONE || !mIsEnabled) {
            return
        }

        /**
         * 设置绘制区域大小
         */
        GLES30.glViewport(0, 0, mDisplayWidth, mDisplayHeight)
        /**
         * 使用指定颜色清除颜色缓冲区
         */
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        /**
         * 使用当前program
         */
        GLES30.glUseProgram(mProgramHandle)

        mIsDrawing = true
        /**
         * 运行任务
         */
        runPendingTaskOnDraw()
        /**
         * 绘制纹理
         */
        drawTexture(textureId, vertexBuffer, textureBuffer)

        mIsDrawing = false
    }


    private fun drawTexture(textureId: Int, vertexBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        /**
         * 绑定顶点坐标缓冲区
         */
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(mPositionHandle, mCoordsPerVertex, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(mPositionHandle)

        /**
         * 绑定纹理坐标缓冲区
         */
        textureBuffer.position(0)
        GLES30.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES30.GL_FLOAT, false, 0, textureBuffer)
        GLES30.glEnableVertexAttribArray(mTextureCoordinateHandle)
        /**
         * 绑定纹理
         */
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(mTextureType, textureId)
        GLES30.glUniform1i(mInputTextureHandle, 0)

        /**
         * 绘制
         */
        onDrawStart()
        onDrawFrame()
        onDrawEnd()
        /**
         * 解绑
         */
        GLES30.glDisableVertexAttribArray(mPositionHandle)
        GLES30.glDisableVertexAttribArray(mTextureCoordinateHandle)
        GLES30.glBindTexture(mTextureType, GLES30.GL_NONE)

    }

    protected open fun onDrawStart() {

    }

    protected open fun onDrawEnd() {

    }

    protected open fun onDrawFrame() {
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, mVertexCount)
    }

    private fun destroyFrameBuffer() {
        if (mFrameBufferTextures.isNotEmpty()) {
            GLES30.glDeleteTextures(1, mFrameBufferTextures, 0)
            mFrameBufferTextures = IntArray(0)
        }
        if (mFrameBuffers.isNotEmpty()) {
            GLES30.glDeleteFramebuffers(1, mFrameBuffers, 0)
            mFrameBuffers = IntArray(0)
        }
        mFrameBufferWidth = 0
        mFrameBufferHeight = 0
    }

    open fun release() {
        if (mProgramHandle != GLES30.GL_NONE) {
            GLES30.glDeleteProgram(mProgramHandle)
            mProgramHandle = GLES30.GL_NONE
            mPositionHandle = GLES30.GL_NONE
            mTextureCoordinateHandle = GLES30.GL_NONE
            mInputTextureHandle = GLES30.GL_NONE
        }
        destroyFrameBuffer()
    }

    fun setInputSize(width: Int, height: Int) {
        mImageWidth = width
        mImageHeight = height
    }

    fun setDisplaySize(width: Int, height: Int) {
        mDisplayWidth = width
        mDisplayHeight = height
    }

    private fun runPendingTaskOnDraw() {
        synchronized(mRunOnDraw) {
            while (!mRunOnDraw.isEmpty()) {
                mRunOnDraw.removeFirst().run()
            }
        }
    }

    fun runOnDraw(runnable: Runnable) {
        if (mIsDrawing) {
            runnable.run()
        } else {
            synchronized(mRunOnDraw) {
                mRunOnDraw.addLast(runnable)
            }
        }

    }

    fun setInteger(location: Int, value: Int) {
        runOnDraw {
            GLES30.glUniform1f(location, value.toFloat())
        }
    }

    fun setFloat(location: Int, value: Float) {
        runOnDraw {
            GLES30.glUniform1f(location, value)
        }
    }

    fun setFloat2(location: Int, value: FloatArray) {
        runOnDraw {
            GLES30.glUniform2fv(location, 1, FloatBuffer.wrap(value))
        }
    }

    fun setFloat3(location: Int, value: FloatArray) {
        runOnDraw {
            GLES30.glUniform3fv(location, 1, FloatBuffer.wrap(value))
        }
    }

    fun setFloat4(location: Int, value: FloatArray) {
        runOnDraw {
            GLES30.glUniform4fv(location, 1, FloatBuffer.wrap(value))
        }
    }

    fun setFloatN(location: Int, value: FloatArray) {
        runOnDraw {
            GLES30.glUniform1fv(location, value.size, FloatBuffer.wrap(value))
        }
    }

    fun setFloat2(location: Int, value: PointF) {
        runOnDraw {
            val array = FloatArray(2)
            array[0] = value.x
            array[1] = value.y
            GLES30.glUniform2fv(location, 1, FloatBuffer.wrap(array))
        }
    }

    fun setUniformMatrix3f(location: Int, matrix: FloatArray) {
        runOnDraw {
            GLES30.glUniformMatrix3fv(location, 1, false, matrix, 0)
        }
    }

    fun setUniformMatrix4f(location: Int, matrix: FloatArray) {
        runOnDraw {
            GLES30.glUniformMatrix4fv(location, 1, false, matrix, 0)
        }
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        if (value < min) {
            return min
        } else if (value > max) {
            return max;
        }
        return value
    }
}