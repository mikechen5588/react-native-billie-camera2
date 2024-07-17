package com.billiecamera.camera.render.core;

import android.content.Context
import com.billiecamera.camera.render.base.RenderEngine

/**
 * @description: 创建渲染引擎
 * 
 * @date: 2022/10/21 12:20
 */
object RenderEngineFactory {

    fun createRenderEngine(context: Context): RenderEngine {
        return GLRenderEngine(context)
    }
}