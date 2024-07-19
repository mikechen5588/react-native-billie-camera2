package com.billiecamera.camera.handler

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Environment.DIRECTORY_DCIM
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.billiecamera.camera.utils.FileUtils
import com.billiecamera.camera.view.ResModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class ImageCropHandler(
    private var activity:AppCompatActivity,
    private var callBack:((res:ResModel?)->Unit)? = null
) {

    /**
     * result model
     */
    private var resModel:ResModel = ResModel()

    /**
     * get video or image result
     */
    private val getContent = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            activity.finish()
            return@registerForActivityResult
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val resultIntent = result.data
            FileUtils.getImageInfo(activity, resModel, resultIntent?.data)
            // 显示toast
            if(resModel.uri == null) {
                launch(Dispatchers.Main) {
                    Toast.makeText(activity,
                        "type = ${result.data?.type}, uri = ${result.data?.data}", Toast.LENGTH_LONG).show()
                }
                activity.finish()
                return@launch
            }
            callBack?.invoke(resModel)
            println("resModel = " + resModel.uri)
        }
    }

    /**
     * 裁剪图片
     */
    fun cropPhoto(uri: Uri, width:Int, height:Int) {
        val intent = Intent("com.android.camera.action.CROP")
        // Intent intent = new Intent("android.intent.action.EDIT");
        // intent.setAction("android.intent.action.EDIT");
        //  intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setDataAndType(uri, "image/*")

        //  intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.putExtra("crop", "true")
        intent.putExtra("aspectX", 1)
        intent.putExtra("aspectY", 1)

        intent.putExtra("outputX", width)
        intent.putExtra("outputY", height)
        intent.putExtra("return-data", false)
        val cropTemp: File? = activity.getExternalFilesDir(DIRECTORY_DCIM)
        val cropTempName = File(cropTemp, System.currentTimeMillis().toString() + "_crop_temp.jpg")
        Log.e("getPath", cropTempName.absolutePath)

        val uriForFile =
            FileProvider.getUriForFile(activity, "com.billiecamera.fileprovider", cropTempName)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uriForFile)

        grantPermissionFix(intent, uriForFile)

        getContent.launch(intent)
    }

    private fun grantPermissionFix(intent: Intent, uri: Uri) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val resolveInfos: List<ResolveInfo> = activity.packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            activity.grantUriPermission(packageName, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.setAction(null)
            intent.setComponent(ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name))
            break
        }
    }
}