package com.aqua_ix.youbimiku.config

import android.content.Context
import com.aqua_ix.youbimiku.R

enum class AIModelConfig {
    DIALOG_FLOW,
    OPEN_AI;
}

fun getAIModel(context: Context): String? {
    return SharedPreferenceManager.get(
        context,
        Key.AI_MODEL.name,
        ""
    )
}

fun setAIModel(context: Context, model: AIModelConfig) {
    return SharedPreferenceManager.put(
        context,
        Key.AI_MODEL.name,
        model.name
    )
}

fun getDisplayName(context: Context, model: AIModelConfig): String {
    return when (model) {
        AIModelConfig.DIALOG_FLOW -> context.getString(R.string.setting_ai_model_dialogflow)
        AIModelConfig.OPEN_AI -> context.getString(R.string.setting_ai_model_openai)
    }
}