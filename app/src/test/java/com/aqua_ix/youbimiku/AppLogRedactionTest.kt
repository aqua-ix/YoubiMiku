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
     * 名前解決の失敗はスキームを含まない形でホスト名を持つ。
     * 値そのものにしか一致しないと通信先が伏せられずにCrashlyticsまで届く。
     */
    @Test
    fun redactSecrets_masksTheBareHost() {
        val message = "java.net.UnknownHostException: Unable to resolve host " +
                "\"avatar.example.com\": No address associated with hostname"

        val redacted = redactSecrets(message, patterns)

        assertFalse(redacted.contains("avatar.example.com"))
        assertTrue(redacted.contains("No address associated with hostname"))
    }

    /** 接続の失敗はホスト名に解決したIPアドレスを添えて出るため、そちらも伏せる */
    @Test
    fun redactSecrets_masksTheBareHostWithItsAddress() {
        val message = "failed to connect to avatar.example.com/203.0.113.1 (port 443) after 10000ms"

        val redacted = redactSecrets(message, patterns)

        assertFalse(redacted.contains("avatar.example.com"))
        assertFalse(redacted.contains("203.0.113.1"))
        assertEquals("failed to connect to $MASK (port 443) after 10000ms", redacted)
    }

    /** ホスト名の一部として続いているだけの別の通信先は伏せない */
    @Test
    fun redactSecrets_keepsOtherHosts() {
        val message = "GET https://notavatar.example.com/ping failed"

        assertEquals(message, redactSecrets(message, patterns))
    }

    /** URLでないシークレット（DialogflowのプロジェクトID）はホスト名を持たない */
    @Test
    fun buildSecretPatterns_acceptsValuesThatAreNotUrls() {
        val projectId = "youbimiku-dummy-1234"
        val withProjectId = buildSecretPatterns(listOf(projectId))

        assertEquals(MASK, redactSecrets(projectId, withProjectId))
    }

    /**
     * 空文字や短い値からパターンを作るとあらゆる位置に一致し、行全体が伏せられてしまう。
     * 未設定のシークレットが混ざっても他のログを潰さないことを確かめる。
     */
    @Test
    fun buildSecretPatterns_dropsValuesThatAreTooShort() {
        val withUnset = buildSecretPatterns(listOf("", " ", "short", translateEndPoint))

        assertEquals("hello short", redactSecrets("hello short", withUnset))
        assertEquals(MASK, redactSecrets(translateEndPoint, withUnset))
    }

    /** 正規表現の記号を含む値でもパターンとして壊れない */
    @Test
    fun buildSecretPatterns_escapesRegexCharacters() {
        val secret = "https://escaped.example.com/a+b(c)?d"
        val escaped = buildSecretPatterns(listOf(secret))

        assertTrue(escaped.isNotEmpty())
        assertEquals(MASK, redactSecrets(secret, escaped))
        assertEquals(
            "GET https://other.example.org/aXb",
            redactSecrets("GET https://other.example.org/aXb", escaped),
        )
    }
}
