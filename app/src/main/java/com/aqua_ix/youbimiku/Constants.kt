package com.aqua_ix.youbimiku

class Constants {
    companion object {
        const val ARGUMENT_CANCELABLE = "cancelable"
        const val OPENAI_MODEL = "gpt-3.5-turbo"
    }
}

class RemoteConfigKey {
    companion object {
        const val MAX_USER_TEXT_LENGTH = "max_user_text_length"
        const val MAX_TOKENS = "max_tokens"
        const val AD_NETWORK = "ad_network"
        const val AD_DISPLAY_REQUEST_TIMES = "ad_display_request_times"
        const val OPENAI_ENABLED = "openai_enabled"
    }

    class AdNetwork {
        companion object {
            const val IMOBILE = "imobile"
            const val IRONSOURCE = "ironsource"
        }
    }
}