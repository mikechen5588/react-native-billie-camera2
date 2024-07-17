package com.billiecamera.camera.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo

/**
 * @description: 相机相关接口
 * 
 * @date: 2022/11/22 16:07
 */

/**
 * 截帧监听器
 * Created by cain.huang on 2017/12/27.
 */
interface OnCaptureListener {
    // 截帧回调
    fun onCapture(bitmap: Bitmap?)
}

/**
 * fps监听器
 */
interface OnFpsListener {
    // fps回调
    fun onFpsCallback(fps: Float)
}

interface OnFrameAvailableListener {
    fun onFrameAvailable(surfaceTexture: SurfaceTexture)
}

/**
 * 媒体拍摄回调
 */
interface OnPreviewCaptureListener {
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntDef(value = [MediaTypePicture, MediaTypeVideo])
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class MediaType

    // 媒体选择
    fun onMediaSelectedListener(path: String?, @MediaType type: Int)

    companion object {
        const val MediaTypePicture = 0
        const val MediaTypeVideo = 1
    }
}

/**
 * SurfaceTexture准备成功监听器
 */
interface OnSurfaceTextureListener {
    fun onSurfaceTexturePrepared(surfaceTexture: SurfaceTexture)
    fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture)
}

/**
 * 预览回调数据
 */
interface PreviewCallback {
    fun onPreviewFrame(data: ByteArray)
}