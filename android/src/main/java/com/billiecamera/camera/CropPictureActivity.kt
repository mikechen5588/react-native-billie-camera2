package com.billiecamera.camera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.billiecamera.camera.handler.ImageCropHandler
import com.billiecamera.camera.utils.CapturePhotoUtils
import com.billiecamera.camera.view.ResModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CropPictureActivity : AppCompatActivity() {

    companion object {
        // select result callBack
        private var sCallBack:((res:String)->Unit)? = null
        private var videoEnableKey = "videoEnable"
        private var imageSizeWidth = "imageSizeWidth"
        private var imageSizeHeight = "imageSizeHeight"

        var hasOpenCamera = false

        // start Activity
        fun startCamera(context: Activity, imageUri:String, width:Int, height:Int, callBack:((res:String)->Unit)) {
            println("open the camera open the camera open the camera3333")
            if(hasOpenCamera) {
                callBack.invoke("")
                return
            }
            try {
                println("open the camera open the camera open the camera 55555")
                val intent = Intent(context, CropPictureActivity::class.java)
                intent.putExtra(videoEnableKey, imageUri)
                intent.putExtra(imageSizeWidth, width)
                intent.putExtra(imageSizeHeight, height)
                context.startActivity(intent)
                sCallBack = callBack
                hasOpenCamera = true
                println("open the camera open the camera open the camera 6666")
            } catch (e:Throwable) {
                e.printStackTrace()
                hasOpenCamera = false
                callBack.invoke("")
            }
        }
    }

    private var sizeWidth:Int = 0
    private var sizeHeight:Int = 0

    /**
     * result callBack
     */
    private var callBack:((res:String)->Unit)? = null

    // 图片裁剪
    private val imageCropHandler by lazy {
        ImageCropHandler(this) {
            callResult(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(videoEnableKey)
        sizeWidth = intent.getIntExtra(imageSizeWidth, 100)
        sizeHeight = intent.getIntExtra(imageSizeHeight, 100)
        if(uri == null) {
            sCallBack = null
            finish()
            return
        }
        callBack = sCallBack
        sCallBack = null

        lifecycleScope.launch {
            val saveResult = CapturePhotoUtils.saveImagePath(this@CropPictureActivity, uri)
            if(saveResult == null) {
                finish()
                return@launch
            }
            imageCropHandler.cropPhoto(saveResult, sizeWidth, sizeHeight)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hasOpenCamera = false
        callBack = null
    }

    private fun callResult(model:ResModel?) {
        lifecycleScope.launch(Dispatchers.IO) {
            if(model != null) {
                val gson = Gson()
                val json = gson.toJson(model)
                callBack?.invoke(json)
            }

            finish()
        }
    }
}