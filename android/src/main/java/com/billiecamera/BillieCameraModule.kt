package com.billiecamera

import com.billiecamera.camera.CameraActivity
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

// Example method
// See https://reactnative.dev/docs/native-modules-android
class BillieCameraModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  /**
   * open the camera
   */
  @ReactMethod
  fun startCamera(enableVideo:Int, promise: Promise) {
    println("open the camera open the camera open the camera")
    val activity = currentActivity ?: return
    println("open the camera open the camera open the camera111")
    CameraActivity.startCamera(activity, enableVideo) {
      // return value
      promise.resolve(it)
    }
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun testCamera(a: Double, b: Double, promise: Promise) {
    promise.resolve(a * b)
  }

  companion object {
    const val NAME = "BillieCamera2"
  }
}
