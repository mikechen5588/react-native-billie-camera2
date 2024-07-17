package com.billiecamera.camera.listener

import com.billiecamera.camera.view.ResModel
import java.io.File

interface FlowCameraListener {

    fun success(file: ResModel)

    fun onError(videoCaptureError: Int, message: String, cause: Throwable?)
}
