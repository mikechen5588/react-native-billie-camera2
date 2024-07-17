package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * 预览帧分析器
 */
@SuppressLint("LogNotTimber")
class PreviewCallbackAnalyzer(private val mPreviewCallback: PreviewCallback?) : ImageAnalysis.Analyzer {


    companion object {
        private const val TAG = "PreviewCallbackAnalyzer"
        private const val VERBOSE = false
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
//        val start = System.currentTimeMillis()
//        val previewCallback = mPreviewCallback ?: return
//        val imageData = image.image ?: return
//        val imageBytes = getDataFromImage(
//            imageData,
//            ImageConvert.COLOR_FORMAT_NV21
//        )
//        // 使用完需要释放，否则下一次不会回调了
//        image.close()
//
//        previewCallback.onPreviewFrame(imageBytes)
    }
}