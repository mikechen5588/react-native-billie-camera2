package com.billiecamera.camera.render.core;

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.billiecamera.camera.render.base.BaseGLFilter
import com.billiecamera.camera.render.base.RenderEngine
import com.billiecamera.camera.render.base.RenderInfo
import com.billiecamera.camera.render.utils.ErrorReporter

/**
 * @description: 渲染器Handler
 * 
 * @date: 2022/10/21 10:19
 */
internal class RendererHandler(looper: Looper, private val impl: RenderEngine) :
    Handler(looper), RenderEngine {

    companion object {
        const val TAG = "RendererHandler"
    }

    enum class RenderOpt {
        MSG_CREATE_RENDER,
        MSG_RENDER_TARGET_SIZE,
        MSG_SET_INPUT_SIZE,
        MSG_CLEAR_INPUT_INFO,
        MSG_CLEAR_RENDER_TARGET,
        MSG_DESTROY,
        MSG_RENDER_FRAME,
        MSG_APPLY_FILTER,
        MSG_TAKE_PICTURE
    }

    override fun createRenderer(renderTarget: Any) {
        obtainMessage(RenderOpt.MSG_CREATE_RENDER.ordinal, renderTarget).sendToTarget()
    }

    override fun setInputTexture(input: SurfaceTexture, width: Int, height: Int) {
        obtainMessage(RenderOpt.MSG_SET_INPUT_SIZE.ordinal, RenderInfo(input, width, height)).sendToTarget()
    }

    override fun clearInputTexture() {
        obtainMessage(RenderOpt.MSG_CLEAR_INPUT_INFO.ordinal).sendToTarget()
    }

    override fun updateRenderTargetInfo(width: Int, height: Int) {
        obtainMessage(RenderOpt.MSG_RENDER_TARGET_SIZE.ordinal, width, height).sendToTarget()
    }

    override fun clearRenderTarget() {
        obtainMessage(RenderOpt.MSG_CLEAR_RENDER_TARGET.ordinal).sendToTarget()
    }

    override fun destroyRenderer() {
        sendEmptyMessage(RenderOpt.MSG_DESTROY.ordinal)
    }

    override fun renderFrame() {
        sendEmptyMessage(RenderOpt.MSG_RENDER_FRAME.ordinal)
    }

    override fun applyFilter(filter: BaseGLFilter) {
        obtainMessage(RenderOpt.MSG_APPLY_FILTER.ordinal, filter).sendToTarget()
    }

    override fun takePicture(pictureListener: RenderEngine.PictureListener) {
        obtainMessage(RenderOpt.MSG_TAKE_PICTURE.ordinal, pictureListener).sendToTarget()
    }

    override fun postOnRenderCallback(callback: RenderEngine.OnRenderCallback) {
        impl.postOnRenderCallback(callback)
    }

    override fun handleMessage(msg: Message) {
        val renderOpt = RenderOpt.values()[msg.what]
        try {
            if (renderOpt != RenderOpt.MSG_RENDER_FRAME) {
                println("RenderOpt=$renderOpt ++")
            }
            val start = SystemClock.uptimeMillis()
            when (renderOpt) {
                RenderOpt.MSG_CREATE_RENDER -> impl.createRenderer(msg.obj)
                RenderOpt.MSG_RENDER_TARGET_SIZE -> {
                    impl.updateRenderTargetInfo(msg.arg1, msg.arg2)
                    println("target size,width=${msg.arg1},height=${msg.arg2}")

                }
                RenderOpt.MSG_SET_INPUT_SIZE -> {
                    val renderInfo = msg.obj as RenderInfo
                    impl.setInputTexture(renderInfo.input, renderInfo.width, renderInfo.height)
                    println("input size,width=${renderInfo.width},height=${renderInfo.height}")
                }
                RenderOpt.MSG_CLEAR_RENDER_TARGET -> impl.clearRenderTarget()
                RenderOpt.MSG_DESTROY -> impl.destroyRenderer()
                RenderOpt.MSG_RENDER_FRAME -> impl.renderFrame()
                RenderOpt.MSG_APPLY_FILTER -> impl.applyFilter(msg.obj as BaseGLFilter)
                RenderOpt.MSG_TAKE_PICTURE -> {
                    impl.takePicture(msg.obj as RenderEngine.PictureListener)
                }
                RenderOpt.MSG_CLEAR_INPUT_INFO -> impl.clearInputTexture()
            }
            val end = SystemClock.uptimeMillis()
            if (renderOpt != RenderOpt.MSG_RENDER_FRAME) {
                println("RenderOpt=$renderOpt --, const time=${end - start}ms")
            }

        } catch (e: Exception) {
            ErrorReporter.reportError(renderOpt.name, e)
        }
    }


}