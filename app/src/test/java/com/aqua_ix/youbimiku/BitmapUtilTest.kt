package com.aqua_ix.youbimiku

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapUtilTest {

    @Test
    fun returnsOneWhenTheImageIsNotLargerThanTheTarget() {
        assertEquals(1, calculateInSampleSize(320, 240, 200))
        assertEquals(1, calculateInSampleSize(200, 200, 200))
    }

    @Test
    fun shrinksByPowerOfTwoWithoutGoingUnderTheTarget() {
        // 短辺を基準にするので 768 / 2 = 384 まで縮める
        assertEquals(2, calculateInSampleSize(1024, 768, 200))
        assertEquals(4, calculateInSampleSize(1024, 768, 150))
        assertEquals(8, calculateInSampleSize(1024, 768, 50))
    }

    @Test
    fun returnsOneForUnknownSize() {
        // 画像の読み込みに失敗した場合はサイズが -1 になる
        assertEquals(1, calculateInSampleSize(-1, -1, 200))
        assertEquals(1, calculateInSampleSize(320, 240, 0))
    }
}
