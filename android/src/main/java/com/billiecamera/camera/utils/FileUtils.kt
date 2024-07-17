package com.billiecamera.camera.utils

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.billiecamera.camera.view.ResModel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale


object FileUtils {

    /**
     * 当确认保存此文件时才去扫描相册更新并显示视频和图片
     * @param dataFile
     */
    fun scanPhotoAlbum(context:Context, dataFile: File?) {
        if (dataFile == null) {
            return
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            dataFile.absolutePath.substring(dataFile.absolutePath.lastIndexOf(".") + 1)
        )
        MediaScannerConnection.scanFile(
            context,
            arrayOf(dataFile.absolutePath),
            arrayOf(mimeType),
            null
        )
    }

    /**
     * A helper function to get the captured file location.
     */
    fun getAbsolutePathFromUri(context: Context, contentUri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context
                .contentResolver
                .query(contentUri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
            if (cursor == null) {
                return null
            }
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(columnIndex)
        } catch (e: RuntimeException) {
            Log.e("VideoViewerFragment", String.format(
                "Failed in getting absolute path for Uri %s with Exception %s",
                contentUri.toString(), e.toString()))
            null
        } finally {
            cursor?.close()
        }
    }

    fun saveBitmap(context: Context, it:Bitmap, recycleBitmap:Boolean = false):File? {
        // Create output file to hold the image
        val photoFile = getFile(context)

        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(photoFile)
            it.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.flush()
            return photoFile
        } catch (e:Throwable) {
            e.printStackTrace()
        } finally {
            if(recycleBitmap) {
                it.recycle()
            }
            try {
                fileOutputStream?.close()
            } catch (e:Throwable) {
                e.printStackTrace()
            }
        }


        return null
    }

    fun getFile(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = appContext.externalCacheDir?.let {
            File(it, System.currentTimeMillis().toString()).apply { mkdirs() }
        }

        val baseFolder = if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir

        return File(
            baseFolder, SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA)
                .format(System.currentTimeMillis()) + ".jpg"
        )
    }

    fun getMediaInfo(context: Context, resModel:ResModel, it: Uri?) {
        it ?: return
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, it)
        // 获取视频时长，单位：毫秒(ms)
        val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        resModel.duration = durationString?.toLong()

        //获取视频宽度（单位：px）
        val widthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        resModel.width = widthString?.toInt()

        //获取视频高度（单位：px）
        val heightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        resModel.height = heightString?.toInt()

        //获取帧
        val bitmap = retriever.frameAtTime
        if(bitmap != null) {
            resModel.thumbUrl = saveBitmap(context, bitmap)?.toString()
        }
    }

    fun getImageInfo(context: Context, resModel: ResModel, it:Uri?) {
        it ?: return

        val onlyBoundsOptions = BitmapFactory.Options()
        val input: InputStream = context.contentResolver.openInputStream(it) ?: return

        try {
            onlyBoundsOptions.inJustDecodeBounds = true
            BitmapFactory.decodeStream(input, null, onlyBoundsOptions)
        } catch (e:Throwable) {
            e.printStackTrace()
        } finally {
            try {
                input.close()
            } catch (e:Throwable) {
                e.printStackTrace()
            }
        }

        resModel.uri = it.toString()
        resModel.width = onlyBoundsOptions.outWidth
        resModel.height = onlyBoundsOptions.outHeight
        resModel.contentType = "image"
    }
}