package com.billiecamera.camera.render.widgets;

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import java.lang.Integer.min

/**
 * @description: 预览 2/3比例
 * @date: 2022/12/14 18:41
 */
class StorySurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs) {

//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
//        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
//        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
//        var desireHeight = (maxWidth * STORY_IMAGE_PREVIEW_RATIO).toInt()
//        val newHeight = if (heightMode == MeasureSpec.UNSPECIFIED) {
//            desireHeight
//        } else {
//            if (desireHeight > maxHeight) {
//                desireHeight=(maxWidth * STORY_IMAGE_PREVIEW_RATIO_FALLBACK).toInt()
//            }
//            min(desireHeight, maxHeight)
//        }
//        val measureSpec = MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
//        super.onMeasure(widthMeasureSpec, measureSpec)
//    }
}