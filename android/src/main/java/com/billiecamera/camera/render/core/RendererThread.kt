package com.billiecamera.camera.render.core;

import android.content.Context
import android.os.Looper
import android.os.Process
import com.billiecamera.camera.render.base.RenderEngine

/**
 * @description: 渲染线程
 * 
 * @date: 2022/10/20 19:15
 */
class RendererThread(val context: Context) : Thread("MRendererThread") {


    companion object {
        private const val TAG = "MRendererThread"

    }

    private val mLock = Object()
    private var mLooper: Looper? = null
    private var mPriority: Int = Process.THREAD_PRIORITY_DEFAULT
    private var mRunning = false
    private var mHandler: RenderEngine? = null
    private var mRenderer = RenderEngineFactory.createRenderEngine(context)

    override fun run() {
        Looper.prepare()
        synchronized(mLock) {
            mLooper = Looper.myLooper()
            mHandler = RendererHandler(mLooper!!, mRenderer)
            mLock.notifyAll()
        }
        Process.setThreadPriority(mPriority)
        mRunning = true
        Looper.loop()
        mRunning = false
        println("Camera render stopped!!!")
    }

    fun getLooper(): Looper? {
        if (!isAlive) {
            return null
        }

        synchronized(mLock) {
            while (isAlive && mLooper == null) {
                try {
                    mLock.wait()
                } catch (e: InterruptedException) {
                    println("Camera render interrupted when getLoop()!!!")
                }
            }
        }
        return mLooper
    }

    fun getHandler(): RenderEngine {
        getLooper()
        return mHandler!!
    }

    fun quit() {
        getLooper()?.quitSafely()
    }

}