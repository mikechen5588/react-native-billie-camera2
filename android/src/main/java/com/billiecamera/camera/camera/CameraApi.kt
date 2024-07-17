package com.billiecamera.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import com.billiecamera.camera.utils.DisplayUtils
import com.billiecamera.camera.utils.SystemUtils

/**
 * 判断是否可用Camera2接口，也就是进而判断是否使用CameraX相机库
 */
@SuppressLint("LogNotTimber")
object CameraApi {
    private const val TAG = "CameraApi"

    /**
     * 判断能否使用Camera2 的API
     * @param context
     * @return
     */
    @SuppressLint("ObsoleteSdkInt")
    fun hasCamera2(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        try {
            val manager = (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
            val idList = manager.cameraIdList
            if (idList.isEmpty()) {
                return false
            }
            var support = true
            for (str in idList) {
                if (str == null || str.trim().isEmpty()) {
                    support = false
                    break
                }
                val characteristics = manager.getCameraCharacteristics(str)
                val iSupportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                if (iSupportLevel != null
                    && (iSupportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
                            || iSupportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)
                ) {
                    support = false
                    break
                }
            }
            return support
        } catch (ignore: Throwable) {
            return false
        }
    }

    /**
     * 判断是否存在前置摄像头
     * @param context
     * @return
     */
    fun hasFrontCamera(context: Context): Boolean {
        val brand = com.billiecamera.camera.utils.SystemUtils.getDeviceBrand()
        val model = com.billiecamera.camera.utils.SystemUtils.getSystemModel()
        // 华为折叠屏手机判断是否处于展开状态
        if (brand.contains("HUAWEI") && model.contains("TAH-")) {
            var width = com.billiecamera.camera.utils.DisplayUtils.getDisplayWidth(context)
            var height = com.billiecamera.camera.utils.DisplayUtils.getDisplayHeight(context)
            if (width < 0 || height < 0) {
                return true
            }
            if (width < height) {
                val temp = width
                width = height
                height = temp
            }
            Log.d(com.billiecamera.camera.camera.CameraApi.TAG, "hasFrontCamera: $model, width = $width, height = $height")
            if (width * 1.0f / height <= 4.0 / 3.0) {
                return false
            }
        }
        return true
    }
}