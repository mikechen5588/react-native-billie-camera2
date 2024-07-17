package com.billiecamera.camera.render.utils;

/**
 * @description: 临时报错信息上报
 * 
 * @date: 2022/11/28 16:27
 */
object ErrorReporter {

    private const val KEY_MESSAGE = "message"
    private const val KEY_STACKTRACE = "stackTrace"


    fun reportError(msg: String, e: Throwable?) {
        //FIXME 更换新的日志库后上报
//        if (e == null) {
//            LogUtils.e("ErrorReporter", msg)
//        } else {
//            LogUtils.e("ErrorReporter", msg, e, false)
//        }

//        val writer = StringWriter()
//        val printWriter = PrintWriter(writer)
//        val exception = RuntimeException()
//        if (BuildConfig.DEBUG) {
//            exception.printStackTrace()
//        }
//        exception.printStackTrace(printWriter)
//        val stackTrace = writer.toString()
//        val infos = HashMap<String, Any>()
//        infos[KEY_MESSAGE] = msg
//        infos[KEY_STACKTRACE] = stackTrace
//        BranchHelper.getInstance().onEvent(EventKey.EGL_ERROR, infos)
//        FirebaseHelper.getInstance().onEvent(EventKey.EGL_ERROR, infos)
    }
}