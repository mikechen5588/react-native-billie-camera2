package com.billiecamera.camera.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.text.format.DateUtils
import android.widget.Toast
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date


object CapturePhotoUtils {

    fun saveImagePath(context: Context, uri:String):Uri? {
        try {
            val input: InputStream? = context.contentResolver.openInputStream(uri.toUri())
            try {
                val resultBitmap = BitmapFactory.decodeStream(input, null, null)
                return saveImage(context, resultBitmap, true)
            } catch (e:Throwable) {
                e.printStackTrace()
            } finally {
                try {
                    input?.close()
                } catch (e:Throwable) {
                    e.printStackTrace()
                }
            }
        } catch (e:Throwable) {
            e.printStackTrace()
        }
        return null
    }

    // 保存图片
    fun saveImage(context: Context, bitmap: Bitmap?, recycleBitmap:Boolean):Uri? {
        if (bitmap == null) {
            return null
        }
        val isSaveSuccess = if (Build.VERSION.SDK_INT < 29) {
            saveImageToGallery(context, bitmap)
        } else {
            saveImageToGallery1(context, bitmap)
        }
        if (isSaveSuccess == null) {
            Toast.makeText(context, "save picture to gallery fail", Toast.LENGTH_SHORT).show()
        }
        if(recycleBitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return isSaveSuccess
    }

    /**
     * android 10 以下版本
     */
    fun saveImageToGallery(context: Context?, image: Bitmap): Uri? {
        // 首先保存图片
        val storePath =
            Environment.getExternalStorageDirectory().absolutePath + File.separator + "dearxy"

        val appDir = File(storePath)
        if (!appDir.exists()) {
            appDir.mkdir()
        }
        val fileName = System.currentTimeMillis().toString() + ".jpg"
        val file = File(appDir, fileName)
        val uri = Uri.fromFile(file)
        try {
            val fos = FileOutputStream(file)
            // 通过io流的方式来压缩保存图片
            val isSuccess = image.compress(Bitmap.CompressFormat.JPEG, 60, fos)
            fos.flush()
            fos.close()

            // 保存图片后发送广播通知更新数据库
            context?.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            return if (isSuccess) {
                uri
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * android 10 以上版本
     */
    fun saveImageToGallery1(context: Context, image: Bitmap): Uri? {
        val mImageTime = System.currentTimeMillis()
        val imageDate: String = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(mImageTime))
        val SCREENSHOT_FILE_NAME_TEMPLATE = "winetalk_%s.png" //图片名称，以"winetalk"+时间戳命名
        val mImageFileName = String.format(SCREENSHOT_FILE_NAME_TEMPLATE, imageDate)

        val values = ContentValues()
        values.put( MediaStore.MediaColumns.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + File.separator + "winetalk")
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, mImageFileName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        values.put(MediaStore.MediaColumns.DATE_ADDED, mImageTime / 1000)
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, mImageTime / 1000)
        values.put(
            MediaStore.MediaColumns.DATE_EXPIRES,
            (mImageTime + DateUtils.DAY_IN_MILLIS) / 1000
        )
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)

        val resolver = context.contentResolver
        val uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values)
        try {
            // First, write the actual data for our screenshot
            resolver.openOutputStream(uri!!).use { out ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, out!!)) {
                    return null
                }
            }
            // Everything went well above, publish it!
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            values.putNull(MediaStore.MediaColumns.DATE_EXPIRES)
            resolver.update(uri, values, null, null)

            return uri
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                resolver.delete(uri!!, null)
            }
        }
        return null
    }
}