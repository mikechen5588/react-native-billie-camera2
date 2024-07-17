package com.billiecamera.camera.render.filter;

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.billiecamera.camera.render.base.BaseGLFilter

/**
 * @description: OES纹理转换滤镜
 * 
 * @date: 2022/10/21 15:23
 */
class OESFilter(context: Context) : BaseGLFilter(context, OES_VERTEX_SHADER, OES_FRAGMENT_SHADER) {

    companion object {
        const val OES_VERTEX_SHADER = """
            uniform mat4 transformMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;

            varying vec2 textureCoordinate;

            void main() {
                gl_Position = aPosition;
                textureCoordinate = (transformMatrix * aTextureCoord).xy;
            }
        """
        const val OES_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform samplerExternalOES inputTexture;
            void main() {
                gl_FragColor = texture2D(inputTexture, textureCoordinate);
            }
        """
    }

    override val mTextureType = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    private var mTransformMatrixHandle = GLES30.GL_NONE
    private var mTransformMatrix = FloatArray(16)

    override fun initializeProgram() {
        super.initializeProgram()

        mTransformMatrixHandle = GLES30.glGetUniformLocation(mProgramHandle, "transformMatrix")

    }

    override fun onDrawStart() {
        super.onDrawStart()

        GLES30.glUniformMatrix4fv(mTransformMatrixHandle, 1, false, mTransformMatrix, 0)
    }


    fun getTransformMatrix() = mTransformMatrix

}