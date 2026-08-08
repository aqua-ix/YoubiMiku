package com.aqua_ix.youbimiku

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * 翻訳APIとのやり取りを行う。
 *
 * クエリは[io.ktor.client.request.parameter]がエンコードするため、`&` `#` `+` `%` や
 * 空白、改行、絵文字、日本語を含む文章でも壊れない（文字列連結では
 * パラメータが分断されたり、URLごと拒否されたりしていた）。
 *
 * 失敗は[Result]で返す。エラー文言を翻訳結果として返すと呼び出し側が成功と区別できず、
 * ミクの発言としてそのまま表示されてしまうため。
 *
 * 翻訳する本文と通信先のURLはどちらもログに出さない。本文はユーザーの発言そのもので、
 * URLは`secrets.properties`の値だが、Ktorのタイムアウト例外はメッセージにURLを埋め込む。
 * 例外を[AppLog]経由で出すことで、メッセージもスタックトレースも伏せた形になる。
 */
object TranslateUtil {

    private const val TAG = "TranslateUtil"

    private const val PARAM_TEXT = "text"
    private const val PARAM_TARGET = "target"
    private const val TARGET_JA = "ja"
    private const val TARGET_EN = "en"

    suspend fun translateEnToJa(text: String): Result<String> = translate(text, TARGET_JA)

    suspend fun translateJaToEn(text: String): Result<String> = translate(text, TARGET_EN)

    private suspend fun translate(text: String, target: String): Result<String> = try {
        val response = HttpClientProvider.client.get(BuildConfig.TRANSLATE_END_POINT) {
            parameter(PARAM_TEXT, text)
            parameter(PARAM_TARGET, target)
        }
        if (!response.status.isSuccess()) {
            throw IOException("Unexpected status code: ${response.status.value}")
        }
        // 前後の改行は吹き出しの余白になるだけなので落とす
        Result.success(response.bodyAsText().trim())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.e(TAG, "Failed to translate into $target.", e)
        Result.failure(e)
    }
}
