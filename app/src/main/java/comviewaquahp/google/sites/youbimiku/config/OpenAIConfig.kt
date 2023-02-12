package comviewaquahp.google.sites.youbimiku.config

import android.content.Context

const val OPENAI_REQUEST_COUNT_TO_SHOW_INTERSTITIAL = 30

fun getOpenAIRequestCount(context: Context): Int {
    return SharedPreferenceManager.get(
        context,
        Key.OPENAI_REQUEST_COUNT.name,
        0
    )
}

fun setOpenAIRequestCount(context: Context, count: Int) {
    return SharedPreferenceManager.put(
        context,
        Key.OPENAI_REQUEST_COUNT.name,
        count
    )
}