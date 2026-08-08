package com.aqua_ix.youbimiku

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * アプリのHTTP通信で共有するクライアント。
 *
 * 翻訳とメッセージ報告がそれぞれ[java.net.HttpURLConnection]を組み立てていたのをまとめ、
 * タイムアウトとリダイレクトの扱いを1か所に持たせる。OpenAIのクライアントが使っている
 * Ktorをそのまま使うため、依存は増やさない。
 *
 * 生成には時間がかかるため初回の通信まで遅延させる。通信と合わせて[kotlinx.coroutines.Dispatchers.IO]
 * の上から使う。プロセスと同じ寿命で使い回すので閉じない。
 */
object HttpClientProvider {

    // 応答が返らないまま待ち続けないための上限
    private const val CONNECT_TIMEOUT_IN_MILLIS = 10_000L
    private const val SOCKET_TIMEOUT_IN_MILLIS = 15_000L
    private const val REQUEST_TIMEOUT_IN_MILLIS = 30_000L

    val client: HttpClient by lazy { createClient() }

    private fun createClient(): HttpClient =
        // エンジンを明示しないとAPKを走査して探すことになり、生成に数百ms余分にかかる
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_IN_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_IN_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_IN_MILLIS
            }

            // 通信先のGoogle Apps ScriptはGET・POSTのどちらにも302を返し、
            // リダイレクト先はGETしか受け付けない（POSTのまま追うと405になる）。
            // KtorのHttpRedirectはGETとHEADしか追わないため、302をGETに落として追う
            // OkHttp側のリダイレクト処理に任せる。
            followRedirects = false
            engine {
                config {
                    followRedirects(true)
                    followSslRedirects(true)
                }
            }

            // 成功か失敗かは呼び出し側がステータスコードを見て判断する
            expectSuccess = false
        }
}
