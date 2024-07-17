
package com.billiecamera.camera.render.filter;

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.opengl.EGLContext
import android.os.SystemClock
import com.billiecamera.camera.render.base.BaseGLFilter
import java.nio.FloatBuffer

/**
 * @description: 从GPU中抓图的Filter
 * 
 * @date: 2022/10/21 12:06
 */
class GLImageReader(context: Context, val mListener: ImageReceiveListener) : BaseGLFilter(context) {


    private var mEglCore: com.billiecamera.camera.render.egl.EglCore? = null
    private var mWindowSurface: com.billiecamera.camera.render.egl.WindowSurface? = null
    private var mVertexBuffer: FloatBuffer? = null
    private var mTextureBuffer: FloatBuffer? = null
    private var mImageReader: ImageReader? = null


    fun bindToEgl(eglContext: EGLContext) {
        mEglCore = com.billiecamera.camera.render.egl.EglCore(
            eglContext,
            com.billiecamera.camera.render.egl.EglCore.FLAG_RECORDABLE
        )
        mVertexBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(com.billiecamera.camera.render.utils.TextureRotationUtils.CubeVertices)
        mTextureBuffer = com.billiecamera.camera.render.utils.OpenGLUtils.createFloatBuffer(com.billiecamera.camera.render.utils.TextureRotationUtils.TextureVertices)
    }


    @SuppressLint("WrongConstant")
    fun setImageSize(width: Int, height: Int) {
        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1).apply {
                setOnImageAvailableListener(ImageAvailable(), null)
            }
            mWindowSurface = com.billiecamera.camera.render.egl.WindowSurface(
                mEglCore,
                mImageReader!!.surface,
                true
            )
        }

        setDisplaySize(width, height)
    }

    override fun drawFrame(textureId: Int, vertexBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        val windowSurface = mWindowSurface ?: return
        windowSurface.makeCurrent()
        super.drawFrame(textureId, vertexBuffer, textureBuffer)
        windowSurface.swapBuffers()
    }

    override fun release() {
        super.release()
        mWindowSurface?.makeCurrent()
        mImageReader?.close()
        mImageReader = null
        mWindowSurface?.release()
        mWindowSurface = null
        mEglCore?.release()
        mEglCore = null
    }


    private inner class ImageAvailable : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            val startTime = SystemClock.uptimeMillis()

            val image = reader.acquireNextImage()
            if (image == null) {
                println("ImageAvailable image is null!!!")
                return
            }
            val planes = image.planes
            val width = image.width //设置的宽
            val height = image.height //设置的高
            val pixelStride = planes[0].pixelStride //像素个数，RGBA为4
            val rowStride = planes[0].rowStride //这里除pixelStride就是真实宽度
            val rowPadding = rowStride - pixelStride * width //计算多余宽度
            val buffer = planes[0].buffer

            val data = ByteArray(rowStride * height)
            buffer.get(data)
            val pixelData = IntArray(width * height)
            var offset = 0
            var index = 0
            for (i in 0 until height) {
                for (j in 0 until width) {
                    var pixel = 0
                    pixel = pixel or (data[offset].toInt() and 0xff shl 16) // R
                    pixel = pixel or (data[offset + 1].toInt() and 0xff shl 8) // G
                    pixel = pixel or (data[offset + 2].toInt() and 0xff) // B
                    pixel = pixel or (data[offset + 3].toInt() and 0xff shl 24) // A
                    pixelData[index++] = pixel
                    offset += pixelStride
                }
                offset += rowPadding
            }
            val bitmap = Bitmap.createBitmap(
                pixelData,
                width, height,
                Bitmap.Config.ARGB_8888
            )
//            val bitmap = Bitmap.createBitmap(
//                width, height,
//                Bitmap.Config.ARGB_8888
//            )
//            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            val endTime = SystemClock.uptimeMillis()
            println("cost ${endTime - startTime}ms")
            mListener.onImageReceive(bitmap)
        }
    }

    /**
     * 图片接受监听器
     */
    interface ImageReceiveListener {
        fun onImageReceive(bitmap: Bitmap)
    }

}