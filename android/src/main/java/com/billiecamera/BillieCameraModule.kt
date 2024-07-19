package com.billiecamera

import com.billiecamera.camera.CameraActivity
import com.billiecamera.camera.CropPictureActivity
import com.billiecamera.camera.view.ButtonState
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
    val activity = currentActivity ?: return
    CameraActivity.startCamera(activity, enableVideo) {
      // return value
      promise.resolve(it)
    }
  }

  /**
   * select avatar
   */
  @ReactMethod
  fun chooseAvatar(width:Int, height:Int, promise: Promise) {
    val activity = currentActivity ?: return
    CameraActivity.startCamera(activity, ButtonState.BUTTON_STATE_ONLY_CAPTURE.status) {
      // return value
      // to crop image
      CropPictureActivity.startCamera(activity, it, width, height) {crop ->
        promise.resolve(crop)
      }
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
