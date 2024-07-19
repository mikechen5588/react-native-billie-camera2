package com.billiecamera.camera

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.billiecamera.R
import com.billiecamera.camera.handler.ImagePickHandler
import com.billiecamera.camera.listener.ClickListener
import com.billiecamera.camera.listener.FlowCameraListener
import com.billiecamera.camera.view.ButtonState
import com.billiecamera.camera.view.CaptureButton.Companion.BUTTON_STATE_BOTH
import com.billiecamera.camera.view.CaptureButton.Companion.BUTTON_STATE_ONLY_CAPTURE
import com.billiecamera.camera.view.ResModel
import com.billiecamera.databinding.CameraActivityBinding
import com.google.gson.Gson
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CameraActivity  : AppCompatActivity() {

    /**
     * result model
     */
    private var resModel:ResModel? = null

    /**
     * result callBack
     */
    private var callBack:((res:String)->Unit)? = null

    /**
     * binding view
     */
    private lateinit var binding: CameraActivityBinding

    /**
     * can capture video
     */
    private var videoEnable = 0

    // image pic
    private var imagePickHandler = ImagePickHandler(this) {
        resModel = it
        callResult()
    }

    private val placeView:FrameLayout by lazy {
        FrameLayout(this@CameraActivity).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    companion object {
        // select result callBack
        private var sCallBack:((res:String)->Unit)? = null
        private var videoEnableKey = "videoEnable"

        var hasOpenCamera = false

        // start Activity
        fun startCamera(context:Activity, videoEnable:Int, callBack:((res:String)->Unit)) {
            println("open the camera open the camera open the camera3333")
            if(hasOpenCamera) {
                callBack.invoke("")
                return
            }
            try {
                println("open the camera open the camera open the camera 55555")
                val intent = Intent(context, CameraActivity::class.java)
                intent.putExtra(videoEnableKey, videoEnable)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callBack = sCallBack
        sCallBack = null

        setImmersionBar()
        // capture model
        videoEnable = intent.getIntExtra(videoEnableKey, BUTTON_STATE_ONLY_CAPTURE)

        setContentView(placeView)

        XXPermissions.with(this)
            // request single permission
            .permission(Permission.RECORD_AUDIO)
            // request single permission
            .permission(Permission.CAMERA)
            // request interceptor
            //.interceptor(new PermissionInterceptor())
            // 设置不触发错误检测机制（局部设置）
            //.unchecked()
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        grantedCallBack()
                    } else {
                        // not all grand
                        Toast.makeText(this@CameraActivity,
                            "request camera permission", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(this@CameraActivity, permissions)
                    } else {
                        // not all grand
                        Toast.makeText(this@CameraActivity,
                            "request camera permission", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            })
    }

    private fun grantedCallBack() {
        binding = CameraActivityBinding.inflate(layoutInflater, placeView, true)

        // 绑定生命周期 您就不用关心Camera的开启和关闭了 不绑定无法预览
        binding.flowCamera.initCamera(this, this)

        // 设置白平衡模式
        // flowCamera.setWhiteBalance(WhiteBalance.AUTO)

        // 设置只支持单独拍照拍视频还是都支持
        // BUTTON_STATE_ONLY_CAPTURE  BUTTON_STATE_ONLY_RECORDER  BUTTON_STATE_BOTH
        binding.flowCamera.setCaptureMode(ButtonState.getButtonState(videoEnable) ?: ButtonState.BUTTON_STATE_ONLY_CAPTURE)

        // 开启HDR
        // flowCamera.setHdrEnable(Hdr.ON)

        // 设置最大可拍摄小视频时长
        binding.flowCamera.setRecordVideoMaxTime(10)
        // 设置拍照或拍视频回调监听
        binding.flowCamera.setFlowCameraListener(object : FlowCameraListener {
            // 录制完成视频文件返回
            override fun success(file: ResModel) {
                resModel = file
                callResult()
            }
            // 操作拍照或录视频出错
            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@CameraActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        })
        binding.flowCamera.setOnClickListener {

        }
        //左边按钮点击事件
        binding.flowCamera.setLeftClickListener(object : ClickListener {
            override fun onClick() {
                finish()
            }
        })
        binding.flowCamera.setGalleryClickListener(object : ClickListener {
            override fun onClick() {
                imagePickHandler.openGallery(videoEnable)
            }
        })
    }

    /**
     * 设置沉浸式状态栏
     */
    private fun setImmersionBar() {
        immersionBar {
            statusBarDarkFont(true, 0.2f)
            navigationBarColor(R.color.white)
            hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hasOpenCamera = false
        callBack = null
    }

    private fun callResult() {
        val model = resModel ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val gson = Gson()
            val json = gson.toJson(model)
            callBack?.invoke(json)

            finish()
        }
    }
}
