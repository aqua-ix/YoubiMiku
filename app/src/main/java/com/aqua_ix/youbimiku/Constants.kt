package com.aqua_ix.youbimiku

class Constants {
    companion object {
        const val ARGUMENT_CANCELABLE = "cancelable"
    }
}

class RemoteConfigKey {
    companion object {
        const val MAX_USER_TEXT_LENGTH = "max_user_text_length"
        const val MAX_TOKENS = "max_tokens"
        const val OPENAI_MODEL = "openai_model"
        const val MAX_CONTEXT_MESSAGES = "max_context_messages"
        const val MAX_CONTEXT_CHARS = "max_context_chars"
        const val AD_NETWORK = "ad_network"
        const val AD_DISPLAY_REQUEST_TIMES = "ad_display_request_times"
        const val OPENAI_ENABLED = "openai_enabled"
        const val SUPPORT_LINKS = "support_links"
        const val SUPPORT_DISPLAY_REQUEST_TIMES = "support_display_request_times"
    }

    class AdNetwork {
        companion object {
            const val IMOBILE = "imobile"
            const val IRONSOURCE = "ironsource"
        }
    }
}