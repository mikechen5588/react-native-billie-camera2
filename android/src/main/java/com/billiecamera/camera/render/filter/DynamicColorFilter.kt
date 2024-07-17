package com.billiecamera.camera.render.filter;

import android.content.Context
import android.opengl.GLES30
import com.billiecamera.camera.render.base.BaseGLFilter

/**
 * @description: 动态颜色滤镜
 * 
 * @date: 2022/10/21 15:28
 */
class DynamicColorFilter(context: Context) : BaseGLFilter(context, VERTEX_SHADER, DYNAMIC_COLOR_FRAGMENT_SHADER) {

    companion object {
        const val DYNAMIC_COLOR_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputTexture;
            uniform float red;
            uniform float green;
            uniform float blue;
            
            void main() {
                lowp vec4 textureColor = texture2D(inputTexture, textureCoordinate);
                gl_FragColor = vec4(textureColor.r*red,textureColor.g*green,textureColor.b*blue,textureColor.w);
            }
        """
    }

    private var mRedHandle = GLES30.GL_NONE
    private var mGreenHandle = GLES30.GL_NONE
    private var mBlueHandle = GLES30.GL_NONE

    override fun initializeProgram() {
        super.initializeProgram()
        mRedHandle = GLES30.glGetUniformLocation(mProgramHandle, "red")
        mGreenHandle = GLES30.glGetUniformLocation(mProgramHandle, "green")
        mBlueHandle = GLES30.glGetUniformLocation(mProgramHandle, "blue")
    }

    fun setRed(red: Float) {
        runOnDraw{
            val clamp = clamp(red, 0.0f, 1.0f)
            setFloat(mRedHandle, clamp)
        }

    }

    fun setGreen(green: Float) {
        runOnDraw{
            val clamp = clamp(green, 0.0f, 1.0f)
            setFloat(mGreenHandle, clamp)
        }

    }

    fun setBlue(blue: Float) {
        runOnDraw {
            val clamp = clamp(blue, 0.0f, 1.0f)
            setFloat(mBlueHandle, clamp)
        }
    }
}