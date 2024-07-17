package com.billiecamera.camera.camera

import android.view.Display
import java.io.File

/**
 * 相机控制器接口
 */
interface ICameraController {
    /**
     * 打开相机
     */
    fun openCamera()

    fun startRecording(callback:((video: File?)->Unit))

    fun stopRecord()

    /**
     * 关闭相机
     */
    fun closeCamera()

    /**
     * 设置准备成功监听器
     *
     * @param listener
     */
    fun setOnSurfaceTextureListener(listener: OnSurfaceTextureListener?)

    /**
     * 设置预览回调
     */
    fun setPreviewCallback(callback: PreviewCallback?)

    /**
     * 设置纹理更新回调
     */
    fun setOnFrameAvailableListener(listener: OnFrameAvailableListener?)

    /**
     * 切换相机
     */
    fun switchCamera()

    /**
     * 销毁相机
     */
    fun destroy()

    /**
     * 是否前置摄像头
     */
    var isFront: Boolean
    /**
     * 获取预览Surface的旋转角度
     */
    val orientation: Int

    /**
     * 获取预览宽度
     */
    val previewWidth: Int

    /**
     * 获取预览高度
     */
    val previewHeight: Int

    /**
     * 是否支持自动对焦
     */
    fun canAutoFocus(): Boolean

    /**
     * 自动对焦
     *
     * @param display   当前显示 [DisplayManager.getDisplay()]
     * @param x         对焦点x
     * @param y         对焦点y
     * @param width     预览区域宽度
     * @param height    预览区域高度
     * @param focusSize 对焦区域大小(预览区域百分比)
     */
    fun autoFocus(display: Display, x: Float, y: Float, width: Int, height: Int, focusSize: Float)

    /**
     * 判断是否支持闪光灯
     *
     * @param front 是否前置摄像头
     */
    fun supportTorch(front: Boolean): Boolean

    /**
     * 设置闪光灯
     *
     * @param on 是否打开闪光灯
     */
    fun setFlashLight(on: Boolean)

    /**
     * 设置缩放比例
     */
    fun zoom(zoomRatio: Float)

    /**
     * zoom in
     */
    fun zoomIn()

    /**
     * zoom out
     */
    fun zoomOut()

    val zoomRatio: Float
}