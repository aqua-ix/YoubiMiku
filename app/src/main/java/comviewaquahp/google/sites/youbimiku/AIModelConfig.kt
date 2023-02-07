package comviewaquahp.google.sites.youbimiku

import android.content.Context

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