package com.aqua_ix.youbimiku.config

import android.util.Log
import com.aqua_ix.youbimiku.R
import com.aqua_ix.youbimiku.RemoteConfigKey
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

/**
 * RemoteConfigの値取得を隠す薄いラッパー。
 *
 * デフォルト値の適用が終わるまでは各getterが「未取得」をnullで返す。
 * 値が未設定・不正な場合も同様にnullを返すため、呼び出し側は
 * 「未取得・未設定・不正値」をまとめて扱える。
 *
 * デフォルト値の適用後・fetch完了前は、デフォルト値または前回activateされた
 * キャッシュを返す（fetchを待たずに読んでも、組み込みの初期値である
 * 空文字・0・falseではなくremote_config_defaults.xmlの値が使われる）。
 */
object RemoteConfigProvider {

    private const val MINIMUM_FETCH_INTERVAL_IN_SECONDS = 3600L

    // 取得できないまま待ち続けないようにタイムアウトを短くする（既定は60秒）
    private const val FETCH_TIMEOUT_IN_SECONDS = 10L

    // remote_config_defaults.xmlのmax_user_text_lengthと同じ値
    private const val FALLBACK_MAX_USER_TEXT_LENGTH = 140

    // remote_config_defaults.xmlのopenai_modelと同じ値
    private const val FALLBACK_OPENAI_MODEL = "gpt-4o-mini"

    // remote_config_defaults.xmlのmax_context_messages・max_context_charsと同じ値
    private const val FALLBACK_MAX_CONTEXT_MESSAGES = 20
    private const val FALLBACK_MAX_CONTEXT_CHARS = 2000

    private const val EMPTY_JSON_ARRAY = "[]"

    private val TAG = RemoteConfigProvider::class.java.simpleName

    private val remoteConfig: FirebaseRemoteConfig by lazy { Firebase.remoteConfig }

    // デフォルト値が適用され、各getterが意味のある値を返せる状態かどうか。
    // 書き込みはメインスレッド、読み出しはコルーチンからも行われるため@Volatileにする
    @Volatile
    private var isDefaultsApplied = false

    /**
     * デフォルト値を適用したうえでfetchを試み、完了後に[onReady]を呼ぶ。
     *
     * fetchが失敗した場合も（デフォルト値・前回キャッシュで動作させるため）[onReady]を呼ぶ。
     * 引数にはfetchが成功したかどうかを渡す。
     */
    fun initialize(onReady: (Boolean) -> Unit) {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = MINIMUM_FETCH_INTERVAL_IN_SECONDS
            fetchTimeoutInSeconds = FETCH_TIMEOUT_IN_SECONDS
        }
        // 設定→デフォルト値→fetchの順に直列化する
        // （fetchTimeoutInSecondsが未反映のままfetchするのを避けるため）
        remoteConfig.setConfigSettingsAsync(configSettings).addOnCompleteListener { settingsTask ->
            if (!settingsTask.isSuccessful) {
                Log.e(TAG, "Failed to apply remote config settings.", settingsTask.exception)
            }
            applyDefaultsAndFetch(onReady)
        }
    }

    private fun applyDefaultsAndFetch(onReady: (Boolean) -> Unit) {
        // setDefaultsAsync完了前に値を読むと組み込みの初期値（空文字・0・false）が返るため、
        // デフォルト値の適用を待ってからfetchする
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
            .addOnCompleteListener { defaultsTask ->
                if (!defaultsTask.isSuccessful) {
                    Log.e(TAG, "Failed to apply remote config defaults.", defaultsTask.exception)
                }
                isDefaultsApplied = defaultsTask.isSuccessful
                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // リモートの値が使えるなら、デフォルト値の適用が失敗していても値は読める
                        isDefaultsApplied = true
                    } else {
                        Log.e(
                            TAG,
                            "Failed to fetch remote config. Fall back to defaults.",
                            task.exception
                        )
                    }
                    onReady(task.isSuccessful)
                }
            }
    }

    /** 未取得・未設定・未知の広告ネットワークの場合はnull */
    val adNetwork: String?
        get() = if (isDefaultsApplied) parseAdNetwork(remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) else null

    /** 未取得・未設定・0以下の場合はnull（インタースティシャルを表示しない） */
    val adDisplayRequestTimes: Int?
        get() = if (isDefaultsApplied) {
            parsePositiveCount(remoteConfig.getDouble(RemoteConfigKey.AD_DISPLAY_REQUEST_TIMES))
        } else {
            null
        }

    /** 未取得・未設定・0以下の場合はnull（支援ダイアログを表示しない） */
    val supportDisplayRequestTimes: Int?
        get() = if (isDefaultsApplied) {
            parsePositiveCount(remoteConfig.getDouble(RemoteConfigKey.SUPPORT_DISPLAY_REQUEST_TIMES))
        } else {
            null
        }

    /** 未取得の場合はnull（呼び出し側で「隠さない」などのフォールバックを選べるようにする） */
    val isOpenAIEnabled: Boolean?
        get() = if (isDefaultsApplied) remoteConfig.getBoolean(RemoteConfigKey.OPENAI_ENABLED) else null

    /** 未取得・不正値の場合はフォールバック値（0を返すと入力が全て切り捨てられるため） */
    val maxUserTextLength: Int
        get() = if (isDefaultsApplied) {
            parsePositiveCount(remoteConfig.getDouble(RemoteConfigKey.MAX_USER_TEXT_LENGTH))
                ?: FALLBACK_MAX_USER_TEXT_LENGTH
        } else {
            FALLBACK_MAX_USER_TEXT_LENGTH
        }

    /** 未取得・未設定・0以下の場合はnull（上限を指定しない） */
    val maxTokens: Int?
        get() = if (isDefaultsApplied) parsePositiveCount(remoteConfig.getDouble(RemoteConfigKey.MAX_TOKENS)) else null

    /**
     * OpenAIに使うモデルID。未取得・未設定の場合はフォールバック値。
     * 空文字のまま送るとリクエストが失敗するため、フォールバック値に寄せる。
     */
    val openAIModel: String
        get() = if (isDefaultsApplied) {
            parseModelId(remoteConfig.getString(RemoteConfigKey.OPENAI_MODEL)) ?: FALLBACK_OPENAI_MODEL
        } else {
            FALLBACK_OPENAI_MODEL
        }

    /**
     * 文脈として送る履歴の件数の上限。未取得の場合はフォールバック値。
     *
     * 0を指定すると文脈を送らなくなる（未取得・未設定を0に丸めるのは
     * トークンを余分に消費しない側に倒すため）。
     */
    val maxContextMessages: Int
        get() = if (isDefaultsApplied) {
            parseNonNegativeCount(remoteConfig.getDouble(RemoteConfigKey.MAX_CONTEXT_MESSAGES))
        } else {
            FALLBACK_MAX_CONTEXT_MESSAGES
        }

    /** 文脈として送る履歴の合計文字数の上限。扱いは[maxContextMessages]と同じ */
    val maxContextChars: Int
        get() = if (isDefaultsApplied) {
            parseNonNegativeCount(remoteConfig.getDouble(RemoteConfigKey.MAX_CONTEXT_CHARS))
        } else {
            FALLBACK_MAX_CONTEXT_CHARS
        }

    /** 未取得・未設定の場合は空のJSON配列 */
    val supportLinksJson: String
        get() = if (isDefaultsApplied) {
            remoteConfig.getString(RemoteConfigKey.SUPPORT_LINKS).takeIf { it.isNotBlank() } ?: EMPTY_JSON_ARRAY
        } else {
            EMPTY_JSON_ARRAY
        }
}

/** 空文字や未知の値を「未設定」として扱う */
internal fun parseAdNetwork(value: String): String? {
    return when (value.trim()) {
        RemoteConfigKey.AdNetwork.IMOBILE -> RemoteConfigKey.AdNetwork.IMOBILE
        RemoteConfigKey.AdNetwork.IRONSOURCE -> RemoteConfigKey.AdNetwork.IRONSOURCE
        else -> null
    }
}

/** 空文字（0.0として返る）や0以下の値を「未設定」として扱う */
internal fun parsePositiveCount(value: Double): Int? {
    return value.toInt().takeIf { it > 0 }
}

/** 空文字や空白だけの値を「未設定」として扱う */
internal fun parseModelId(value: String): String? {
    return value.trim().takeIf { it.isNotEmpty() }
}

/**
 * 0以上の上限値として解釈する。
 * 0（空文字も0.0として返る）は上限そのままの意味で、負の値は0に丸める。
 */
internal fun parseNonNegativeCount(value: Double): Int {
    return value.toInt().coerceAtLeast(0)
}
