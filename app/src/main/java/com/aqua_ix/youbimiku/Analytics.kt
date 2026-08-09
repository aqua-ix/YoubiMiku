package com.aqua_ix.youbimiku

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * 改善の効果を測るための主要イベントをAnalyticsに記録する。
 *
 * 送るのは種別と件数だけで、**会話の本文は送らない**。
 * 何が失敗したのかは[AIErrorType]の種別で分かるようにし、本文は端末の外に出さない。
 */
object Analytics {

    private const val EVENT_SEND_MESSAGE = "send_message"
    private const val EVENT_AI_ERROR = "ai_error"
    private const val EVENT_MODE_CHANGE = "mode_change"
    private const val EVENT_MODEL_CHANGE = "model_change"

    private const val PARAM_AI_MODEL = "ai_model"
    private const val PARAM_ERROR_TYPE = "error_type"
    private const val PARAM_HAS_PARTIAL_RESPONSE = "has_partial_response"
    private const val PARAM_UI_MODE = "ui_mode"

    private val analytics: FirebaseAnalytics
        get() = Firebase.analytics

    fun logSendMessage(aiModel: String) {
        analytics.logEvent(EVENT_SEND_MESSAGE) {
            param(PARAM_AI_MODEL, aiModel)
        }
    }

    /**
     * AI応答の失敗を種別ごとに数える。
     *
     * [hasPartialResponse]は、ストリーミングで途中まで届いてから失敗したかどうか。
     * 一文字も届かない失敗とは原因も体感も違うため区別できるようにする（#100）。
     */
    fun logAIError(type: AIErrorType, aiModel: String, hasPartialResponse: Boolean = false) {
        analytics.logEvent(EVENT_AI_ERROR) {
            param(PARAM_ERROR_TYPE, type.value)
            param(PARAM_AI_MODEL, aiModel)
            param(PARAM_HAS_PARTIAL_RESPONSE, if (hasPartialResponse) 1L else 0L)
        }
    }

    fun logModeChange(uiMode: String) {
        analytics.logEvent(EVENT_MODE_CHANGE) {
            param(PARAM_UI_MODE, uiMode)
        }
    }

    fun logModelChange(aiModel: String) {
        analytics.logEvent(EVENT_MODEL_CHANGE) {
            param(PARAM_AI_MODEL, aiModel)
        }
    }
}

/** AI応答が失敗した理由の分類。Analyticsで集計できるように短い識別子を持たせる */
enum class AIErrorType(val value: String) {
    /** 通信の失敗（圏外・タイムアウトなど）。リトライで回復しうる */
    NETWORK("network"),

    /** 通信は成立したが応答が得られなかった */
    RESPONSE("response"),

    /** 応答が空だった */
    EMPTY_RESPONSE("empty_response"),

    /** クライアントの初期化が終わっていない、または失敗している */
    NOT_INITIALIZED("not_initialized"),
}
