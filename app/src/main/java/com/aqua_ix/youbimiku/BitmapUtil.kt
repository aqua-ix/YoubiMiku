package com.aqua_ix.youbimiku

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * リソースの画像を[targetSize]に合わせて縮小しながら読み込む。
 *
 * 表示サイズよりはるかに大きい画像をそのまま読み込むと、1枚で数MBのBitmapになる。
 */
fun decodeSampledBitmap(resources: Resources, resId: Int, targetSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        // 表示サイズに合わせて自分で縮小するので、画面密度に合わせた拡大はさせない
        inScaled = false
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeResource(resources, resId, options)
    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetSize)
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeResource(resources, resId, options)
}

/**
 * [targetSize]を下回らない範囲で最大の縮小率（2のべき乗）を返す。
 *
 * アイコンは正方形に切り抜かれて表示されるため、短辺を基準にする。
 */
internal fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    val shorterSide = minOf(width, height)
    if (targetSize <= 0 || shorterSide <= 0) {
        return 1
    }
    var sampleSize = 1
    while (shorterSide / (sampleSize * 2) >= targetSize) {
        sampleSize *= 2
    }
    return sampleSize
}
