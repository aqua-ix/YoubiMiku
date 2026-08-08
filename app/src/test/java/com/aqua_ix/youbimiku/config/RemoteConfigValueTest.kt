package com.aqua_ix.youbimiku.config

import com.aqua_ix.youbimiku.RemoteConfigKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RemoteConfigの値を解釈する処理のテスト。
 * 取得に失敗した場合やRemoteConfigに値が無い場合は空文字（数値は0.0）が返るため、
 * それらが「未設定」として扱われることを確認する。
 */
class RemoteConfigValueTest {

    @Test
    fun parseAdNetwork_returnsNullForUnsetValue() {
        assertNull(parseAdNetwork(""))
        assertNull(parseAdNetwork(" "))
    }

    @Test
    fun parseAdNetwork_returnsNullForUnknownValue() {
        assertNull(parseAdNetwork("admob"))
    }

    @Test
    fun parseAdNetwork_returnsKnownValue() {
        assertEquals(RemoteConfigKey.AdNetwork.IMOBILE, parseAdNetwork("imobile"))
        assertEquals(RemoteConfigKey.AdNetwork.IRONSOURCE, parseAdNetwork("ironsource"))
        assertEquals(RemoteConfigKey.AdNetwork.IRONSOURCE, parseAdNetwork(" ironsource "))
    }

    @Test
    fun parsePositiveCount_returnsNullForUnsetOrInvalidValue() {
        assertNull(parsePositiveCount(0.0))
        assertNull(parsePositiveCount(-1.0))
    }

    @Test
    fun parsePositiveCount_returnsPositiveValue() {
        assertEquals(30, parsePositiveCount(30.0))
        assertEquals(1, parsePositiveCount(1.9))
    }
}
