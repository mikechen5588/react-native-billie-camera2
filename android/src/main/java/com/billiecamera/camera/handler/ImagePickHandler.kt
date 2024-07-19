package com.billiecamera.camera.utils

import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.billiecamera.camera.view.CaptureButton
import com.billiecamera.camera.view.ResModel
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImagePickHandler(var activity: AppCompatActivity, var callBack:((res: ResModel?)->Unit)? = null) {

    /**
     * result model
     */
    private var resModel:ResModel = ResModel()

    /**
     * open Gallery
     */
    fun openGallery(videoEnable:Int) {
        XXPermissions.with(activity)
            .permission(Permission.READ_MEDIA_VISUAL_USER_SELECTED)
            .permission(Permission.READ_MEDIA_VIDEO)
            .permission(Permission.READ_MEDIA_IMAGES)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        val intent = Intent(Intent.ACTION_PICK)
                        when (videoEnable) {
                            CaptureButton.BUTTON_STATE_BOTH -> {
                                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/* video/*")
                            }
                            CaptureButton.BUTTON_STATE_ONLY_RECORDER -> {
                                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "video/*")
                            }
                            else -> {
                                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                            }
                        }
                        getContent.launch(intent)
                    } else {
                        // not all grand
                        Toast.makeText(activity,
                            "request camera permission", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(activity, permissions)
                    } else {
                        // not all grand
                        Toast.makeText(activity,
                            "request camera permission", Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    /**
     * get video or image result
     */
    private var getContent = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val resultIntent = result.data
            resModel = ResModel()
            if(result.data?.type?.contains("video") == true) {
                // read video info
                FileUtils.getMediaInfo(activity, resModel, resultIntent?.data)
            } else if(result.data?.type?.contains("image") == true) {
                // read picture info
                FileUtils.getImageInfo(activity, resModel, resultIntent?.data)
            }

            // 显示toast
            if(resModel.uri == null) {
                launch(Dispatchers.Main) {
                    Toast.makeText(activity,
                        "type = ${result.data?.type}, uri = ${result.data?.data}", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            println("resModel = " + resModel.uri)

            // select null file
            if(resModel.uri == null) {
                return@launch
            }
            callBack?.invoke(resModel)
        }
    }
}