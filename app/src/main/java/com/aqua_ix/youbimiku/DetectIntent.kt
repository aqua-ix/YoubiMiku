package com.aqua_ix.youbimiku

import android.content.Context
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import com.aqua_ix.youbimiku.config.LanguageConfig
import com.aqua_ix.youbimiku.config.getLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dialogflowとのやり取りを行う。
 *
 * 認証情報の読み込みとgRPCクライアントの生成は数百ms単位でかかるため、
 * 生成時ではなく初回送信時（[Dispatchers.IO]の上）まで遅延させる。
 * OpenAIを使っている間は一度も生成されない。
 */
class DetectIntent(
    private val context: Context,
    private val session: String,
) {

    companion object {
        private const val TAG = "DetectIntent"
        const val PROJECT_ID = BuildConfig.DIALOGFLOW_PROJECT_ID
        val SCOPE = listOf("https://www.googleapis.com/auth/cloud-platform")

        // クライアントの終了を待つ上限。終わらない場合も待ち続けないようにする
        private const val SHUTDOWN_TIMEOUT_IN_SECONDS = 5L

        // 実行中の送信の完了を待つ上限と確認の間隔
        private const val IN_FLIGHT_WAIT_TIMEOUT_IN_MILLIS = 3_000L
        private const val IN_FLIGHT_WAIT_INTERVAL_IN_MILLIS = 100L
    }

    private class Clients(val sessions: SessionsClient, val contexts: ContextsClient)

    private val lazyClients = lazy { createClients() }

    // 送信が始まったかどうか。クライアントの生成前や生成中に終了要求が来ても
    // 取りこぼさないよう、生成ではなく送信の入口で立てる
    @Volatile
    private var isSendStarted = false

    private val clients: Clients
        get() = lazyClients.value

    // 実行中の送信の数。同期RPCは割り込めないため、終了処理が実行中の送信と
    // 競合しないように完了を待てるようにする
    private val inFlightRequests = AtomicInteger(0)

    private fun createClients(): Clients {
        // fromStreamはストリームを閉じないので、useで確実に閉じる
        val credentials = context.resources.openRawResource(R.raw.dialogflow_secret).use {
            GoogleCredentials.fromStream(it).createScoped(SCOPE)
        }
        val sessions = createSessions(credentials)
        return try {
            Clients(sessions, createContexts(credentials))
        } catch (e: Exception) {
            // 片方だけ生成された状態で失敗すると閉じる機会がなくなる
            sessions.close()
            throw e
        }
    }

    private fun createSessions(credentials: GoogleCredentials): SessionsClient {
        val sessionsSetting =
            SessionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()
        return SessionsClient.create(sessionsSetting)
    }

    private fun createContexts(credentials: GoogleCredentials): ContextsClient {
        val contextsSettings =
            ContextsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()
        return ContextsClient.create(contextsSettings)
    }

    /**
     * Dialogflowに送信して応答を返す。
     * gRPCの同期呼び出しと翻訳のHTTP通信を含むため、呼び出し元の
     * ディスパッチャに関わらず[Dispatchers.IO]の上で実行する。
     */
    suspend fun send(text: String): String = withContext(Dispatchers.IO) {
        isSendStarted = true
        inFlightRequests.incrementAndGet()
        try {
            request(text)
        } finally {
            inFlightRequests.decrementAndGet()
        }
    }

    /**
     * 翻訳に失敗した場合は例外にする。エラー文言を翻訳結果として扱うと、
     * ミクの応答として表示されたうえ履歴にも残ってしまうため、
     * 呼び出し元（[MainActivity]）にエラーとして伝える。
     */
    private suspend fun request(text: String): String {
        val shouldTranslate = getLanguage(context).equals(LanguageConfig.LANGUAGE_EN.name)
        val sendText =
            if (shouldTranslate) TranslateUtil.translateEnToJa(text).getOrThrow() else text
        val detectIntentRequest = DetectIntentRequest.newBuilder()
            .setQueryInput(
                QueryInput.newBuilder()
                    .setText(
                        TextInput
                            .newBuilder()
                            .setText(sendText)
                            .setLanguageCode("jp")
                    )
                    .build()
            )
            .setSession(SessionName.format(PROJECT_ID, session))
            .build()

        val res = clients.sessions.detectIntent(detectIntentRequest)
        if (shouldTranslate) {
            return TranslateUtil.translateJaToEn(res.queryResult.fulfillmentText).getOrThrow()
        }

        // QueryResultにはユーザーの発言（queryText）と応答の本文が丸ごと入るため出力しない。
        // 応答が取れているかどうかは長さで分かる
        val fulfillmentText = res.queryResult.fulfillmentText
        AppLog.d(TAG) { "The response is received: ${fulfillmentText.length} chars" }
        return fulfillmentText
    }

    /**
     * 会話のコンテキストを破棄してクライアントを解放する。
     *
     * どちらもブロッキング処理でメインスレッドからは呼べず、Activityの終了後にも
     * 完了させたいため、アプリスコープでの投げっぱなしにする。
     */
    fun shutdown() {
        if (!isSendStarted) {
            // 一度も送信していなければクライアントもコンテキストも存在しない
            return
        }
        Application.applicationScope.launch {
            awaitInFlightRequests()
            if (!lazyClients.isInitialized()) {
                // 送信がクライアントの生成まで至らなかった場合は片付けるものがない。
                // 片付けのためだけに生成してしまわないよう、ここでは参照しない
                return@launch
            }
            resetContexts()
            closeClients()
        }
    }

    /**
     * 実行中の送信が終わるのを待つ。
     * 同期RPCの途中でクライアントを閉じると、応答を取りこぼしたうえに
     * コンテキストの破棄も中途半端になるため。待ちきれない場合は諦めて先に進む。
     */
    private suspend fun awaitInFlightRequests() {
        var waited = 0L
        while (inFlightRequests.get() > 0 && waited < IN_FLIGHT_WAIT_TIMEOUT_IN_MILLIS) {
            delay(IN_FLIGHT_WAIT_INTERVAL_IN_MILLIS)
            waited += IN_FLIGHT_WAIT_INTERVAL_IN_MILLIS
        }
        if (inFlightRequests.get() > 0) {
            AppLog.w(TAG, "Shutting down while a request is still running.")
        }
    }

    private fun resetContexts() {
        try {
            clients.contexts.deleteAllContexts(SessionName.format(PROJECT_ID, session))
        } catch (e: Exception) {
            // 終了時の後片付けなので、失敗しても次回の会話には影響しない
            AppLog.e(TAG, "Failed to reset the contexts.", e)
        }
    }

    private fun closeClients() {
        // closeしないとgRPCのチャネルとスレッドが残り続ける
        try {
            clients.sessions.close()
            clients.contexts.close()
            clients.sessions.awaitTermination(SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
            clients.contexts.awaitTermination(SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to close the clients.", e)
        }
    }
}
