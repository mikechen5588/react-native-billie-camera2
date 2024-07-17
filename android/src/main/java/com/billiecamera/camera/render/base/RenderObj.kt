package com.billiecamera.camera.render.base

import android.graphics.SurfaceTexture

/**
 * @description: 渲染对象，分别为输入和输出
 * 
 * @date: 2022/10/21 17:06
 */
data class RenderInfo(val input: SurfaceTexture, val width: Int, val height: Int)
data class RenderTargetInfo(val input: Any, val width: Int, val height: Int)