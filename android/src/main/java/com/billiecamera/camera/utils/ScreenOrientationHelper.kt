@file:Suppress("MemberVisibilityCanBePrivate")

package com.billiecamera.camera.utils

import android.content.Context
import android.os.Build
import android.view.OrientationEventListener
import android.view.WindowManager

class ScreenOrientationHelper {
    val ORIENTATION_TYPE_0 = 0
    val ORIENTATION_TYPE_90 = 90
    val ORIENTATION_TYPE_180 = 180
    val ORIENTATION_TYPE_270 = 270

    // orientation event listener
    private var mEventListener: OrientationEventListener? = null

    // orientation change call back
    private var mOrientationListener: OrientationListener? = null

    // current orientation
    private var currentType = ORIENTATION_TYPE_0

    var rawOrientation = ORIENTATION_TYPE_0
        private set
        public get() {
            return if(field < 45 || field > 360 - 45) {
                ORIENTATION_TYPE_0
            } else if(field in 45..134) {
                ORIENTATION_TYPE_90
            } else if(field in 135..224) {
                ORIENTATION_TYPE_180
            } else {
                ORIENTATION_TYPE_270
            }
        }

    fun init(context: Context, listener: OrientationListener) {
        // get global app context
        val appContext = context
        // orientation change callBack
        mOrientationListener = listener
        // new orientation listener
        mEventListener = object : OrientationEventListener(appContext) {
            override fun onOrientationChanged(orientation: Int) {
                rawOrientation = orientation

                if (rawOrientation == ORIENTATION_TYPE_0) {
                    //0
                    if (currentType == 0) {
                        return
                    }
                    currentType = ORIENTATION_TYPE_0
                    mOrientationListener?.onChange(ORIENTATION_TYPE_0)
                } else if (rawOrientation == ORIENTATION_TYPE_90) {
                    //90
                    if (currentType == ORIENTATION_TYPE_90) {
                        return
                    }
                    currentType = ORIENTATION_TYPE_90
                    mOrientationListener?.onChange(ORIENTATION_TYPE_90)
                } else if (rawOrientation == ORIENTATION_TYPE_180) {
                    //180
                    if (currentType == ORIENTATION_TYPE_180) {
                        return
                    }
                    currentType = ORIENTATION_TYPE_180
                    mOrientationListener?.onChange(ORIENTATION_TYPE_180)
                } else if (rawOrientation == ORIENTATION_TYPE_270) {
                    //270
                    if (currentType == ORIENTATION_TYPE_270) {
                        return
                    }
                    currentType = ORIENTATION_TYPE_270
                    mOrientationListener?.onChange(ORIENTATION_TYPE_270)
                }
            }
        }
        register()
    }

    private fun getScreenRotation(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return context.display?.rotation ?: ORIENTATION_TYPE_0
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay?.rotation ?: 0
    }

    fun register() {
        mEventListener?.enable()
    }

    fun unRegister() {
        mEventListener?.disable()
    }

    interface OrientationListener {
        /**
         *
         * @param orientation
         */
        fun onChange(orientation: Int)
    }
}