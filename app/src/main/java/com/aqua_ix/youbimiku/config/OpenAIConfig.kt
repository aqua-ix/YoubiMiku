package com.aqua_ix.youbimiku.config

import android.content.Context

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