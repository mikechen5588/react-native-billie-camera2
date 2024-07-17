package com.billiecamera.camera.render.base

import android.graphics.Bitmap
import android.graphics.SurfaceTexture

/**
 * @description: 渲染引擎
 * 
 * @date: 2022/10/21 10:17
 */
interface RenderEngine {

    /**
     * 创建EGL环境
     *
     * @param renderTarget 渲染目标，可以使SurfaceHolder、Surface、SurfaceTexture
     */
    fun createRenderer(renderTarget: Any)

    /**
     * 销毁渲染环境
     */
    fun destroyRenderer()


    /**
     * 设置输入图像的surface、宽高
     *  @param input 代表数据源，一般是绑定到相机的SurfaceTexture
     */
    fun setInputTexture(input: SurfaceTexture, width: Int, height: Int)

    /**
     * 清除输入，释放资源，暂停渲染
     */
    fun clearInputTexture()

    /**
     * 更新渲染目标信息,一般是在SurfaceChange时调用
     */
    fun updateRenderTargetInfo(width: Int, height: Int)

    /**
     * 清除渲染目标，释放资源，暂停渲染
     */
    fun clearRenderTarget()

    /**
     * 执行一次渲染
     */
    fun renderFrame()

    /**
     * 设置滤镜
     */
    fun applyFilter(filter: BaseGLFilter)

    /**
     * 拍照
     */
    fun takePicture(pictureListener: PictureListener)

    /**
     * 优先事件
     */
    fun postOnRenderCallback(callback: OnRenderCallback)


    interface OnRenderCallback {
        fun onRender(renderEngine: RenderEngine)
    }

    fun interface PictureListener{
        fun onTakePicture(picture: Bitmap)
    }

}