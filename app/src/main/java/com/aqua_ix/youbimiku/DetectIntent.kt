package com.aqua_ix.youbimiku

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import com.aqua_ix.youbimiku.config.LanguageConfig
import com.aqua_ix.youbimiku.config.getLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
    }

    private class Clients(val sessions: SessionsClient, val contexts: ContextsClient)

    // 生成済みかどうかを判別する必要があるため、by lazyではなくLazyを直接持つ
    private val lazyClients = lazy { createClients() }

    private val clients: Clients
        get() = lazyClients.value

    private fun createClients(): Clients {
        // fromStreamはストリームを閉じないので、useで確実に閉じる
        val credentials = context.resources.openRawResource(R.raw.dialogflow_secret).use {
            GoogleCredentials.fromStream(it).createScoped(SCOPE)
        }
        return Clients(createSessions(credentials), createContexts(credentials))
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
        val shouldTranslate = getLanguage(context).equals(LanguageConfig.LANGUAGE_EN.name)
        val sendText = if (shouldTranslate) TranslateUtil.translateEnToJa(text) else text
        val request = DetectIntentRequest.newBuilder()
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

        val res = clients.sessions.detectIntent(request)
        if (shouldTranslate) {
            return@withContext TranslateUtil.translateJaToEn(res.queryResult.fulfillmentText)
        }

        Log.d(TAG, "response result : ${res.queryResult}")
        res.queryResult.fulfillmentText
    }

    /**
     * 会話のコンテキストを破棄してクライアントを解放する。
     *
     * どちらもブロッキング処理でメインスレッドからは呼べず、Activityの終了後にも
     * 完了させたいため、アプリスコープでの投げっぱなしにする。
     */
    fun shutdown() {
        if (!lazyClients.isInitialized()) {
            // 一度も送信していなければクライアントもコンテキストも存在しない
            return
        }
        Application.applicationScope.launch {
            resetContexts()
            closeClients()
        }
    }

    private fun resetContexts() {
        try {
            clients.contexts.deleteAllContexts(SessionName.format(PROJECT_ID, session))
        } catch (e: Exception) {
            // 終了時の後片付けなので、失敗しても次回の会話には影響しない
            Log.e(TAG, "Failed to reset the contexts.", e)
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
            Log.e(TAG, "Failed to close the clients.", e)
        }
    }
}
