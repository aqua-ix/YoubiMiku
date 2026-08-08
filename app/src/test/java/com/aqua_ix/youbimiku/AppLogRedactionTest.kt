package com.aqua_ix.youbimiku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ログとCrashlyticsに出す前にシークレットを伏せる処理のテスト。
 *
 * 実際の値（`secrets.properties`）は使わず、同じ形をしたダミーで確かめる。
 * ここが壊れると通信先とユーザーの発言がそのまま端末のログに残るため、
 * 想定している入力の形をテストで固定しておく。
 */
class AppLogRedactionTest {

    private val translateEndPoint = "https://script.example.com/macros/s/AKfycbwDUMMY/exec"
    private val avatarBaseUrl = "https://avatar.example.com"
    private val patterns = buildSecretPatterns(listOf(translateEndPoint, avatarBaseUrl))

    @Test
    fun redactSecrets_masksTheEndpoint() {
        assertEquals(MASK, redactSecrets(translateEndPoint, patterns))
    }

    /**
     * 翻訳APIは本文をクエリに載せるため、ホストだけを伏せると発言が残ってしまう。
     * Ktorのタイムアウト例外の実際の形で確かめる。
     */
    @Test
    fun redactSecrets_masksTheQueryStringWithTheEndpoint() {
        val message = "Request timeout has expired " +
                "[url=$translateEndPoint?text=my+private+message&target=ja, " +
                "request_timeout=30000 ms]"

        val redacted = redactSecrets(message, patterns)

        assertFalse(redacted.contains("my+private+message"))
        assertFalse(redacted.contains("script.example.com"))
        assertEquals(
            "Request timeout has expired [url=$MASK, request_timeout=30000 ms]",
            redacted,
        )
    }

    /** 区切りの手前で止めるので、例外の残りは読める形で残る */
    @Test
    fun redactSecrets_keepsTheRestOfTheMessage() {
        val redacted = redactSecrets("GET $avatarBaseUrl/assets/main.js failed with 502", patterns)

        assertEquals("GET $MASK failed with 502", redacted)
    }

    @Test
    fun redactSecrets_masksApiKeys() {
        val redacted = redactSecrets("token=sk-proj-AbCdEf0123456789 rejected", patterns)

        assertEquals("token=$MASK rejected", redacted)
    }

    @Test
    fun redactSecrets_keepsMessagesWithoutSecrets() {
        val message = "The AI request failed: type=network, streamed=0 chars"

        assertEquals(message, redactSecrets(message, patterns))
    }

    /**
     * 空文字や短い値からパターンを作るとあらゆる位置に一致し、行全体が伏せられてしまう。
     * 未設定のシークレットが混ざっても他のログを潰さないことを確かめる。
     */
    @Test
    fun buildSecretPatterns_dropsValuesThatAreTooShort() {
        val withUnset = buildSecretPatterns(listOf("", " ", "short", translateEndPoint))

        assertEquals(1, withUnset.size)
        assertEquals("hello", redactSecrets("hello", withUnset))
    }

    /** 正規表現の記号を含む値でもパターンとして壊れない */
    @Test
    fun buildSecretPatterns_escapesRegexCharacters() {
        val secret = "https://example.com/a+b(c)?d"
        val escaped = buildSecretPatterns(listOf(secret))

        assertTrue(escaped.isNotEmpty())
        assertEquals(MASK, redactSecrets(secret, escaped))
        assertEquals("https://example.com/aXb", redactSecrets("https://example.com/aXb", escaped))
    }
}
