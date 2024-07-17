package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo

/**
 * Image数据转换工具
 */
@SuppressLint("LogNotTimber")
internal object ImageConvert {

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntDef(value = [COLOR_FORMAT_I420, COLOR_FORMAT_NV21])
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    internal annotation class ColorFormat

    private const val TAG = "ImageConvert"
    private const val VERBOSE = false
    const val COLOR_FORMAT_I420 = 1
    const val COLOR_FORMAT_NV21 = 2

    /**
     * 获取转换后的格式
     * @param image         Image数据对象
     * @param colorFormat   转换的颜色格式
     * @return              图像字节数组
     */
    @JvmStatic
    fun getDataFromImage(image: Image, @ColorFormat colorFormat: Int): ByteArray {
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        if (VERBOSE) {
            Log.v(TAG, "get data from " + planes.size + " planes")
        }
        var yLength = 0
        var stride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    yLength = 0
                    stride = 1
                }
                1 -> {
                    if (colorFormat == COLOR_FORMAT_I420) {
                        yLength = width * height
                        stride = 1
                    } else {
                        yLength = width * height + 1
                        stride = 2
                    }
                }
                2 -> {
                    if (colorFormat == COLOR_FORMAT_I420) {
                        yLength = (width * height * 1.25).toInt()
                        stride = 1
                    } else {
                        yLength = width * height
                        stride = 2
                    }
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            if (VERBOSE) {
                Log.v(TAG, "pixelStride $pixelStride")
                Log.v(TAG, "rowStride $rowStride")
                Log.v(TAG, "width $width")
                Log.v(TAG, "height $height")
                Log.v(TAG, "buffer size " + buffer.remaining())
            }
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && stride == 1) {
                    length = w
                    buffer[data, yLength, length]
                    yLength += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer[rowData, 0, length]
                    for (col in 0 until w) {
                        data[yLength] = rowData[col * pixelStride]
                        yLength += stride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
            if (VERBOSE) {
                Log.v(TAG, "Finished reading data from plane $i")
            }
        }
        return data
    }

    /**
     * 判断Image对象中的格式是否支持，目前只支持YUV_420_888、NV21、YV12
     * @param image Image对象
     * @return  返回格式支持的结果
     */
    private fun isImageFormatSupported(image: Image): Boolean {
        when (image.format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }
}