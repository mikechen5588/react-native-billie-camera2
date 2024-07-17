package com.billiecamera.camera.camera

/**
 * @description: 相机相关枚举
 * 
 * @date: 2022/11/22 16:07
 */

/**
 * 长宽比
 * Created by cain.huang on 2017/7/27.
 */
enum class AspectRatio {
    RATIO_4_3, RATIO_1_1, RATIO_16_9
}

/**
 * 相机预览和拍照计算类型
 * Created by cain on 2018/1/14.
 */
internal enum class CalculateType {
    Min,  // 最小
    Max,  // 最大
    Larger,  // 大一点
    Lower // 小一点
}

/**
 * Created by cain.huang on 2017/9/28.
 */
enum class GalleryType {
    PICTURE,  // 图片
    VIDEO_60S,  // 拍60秒
    VIDEO_15S // 拍15秒
}