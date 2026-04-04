package com.aqua_ix.youbimiku.config

import android.content.Context

fun getSupportRequestCount(context: Context): Int {
    return SharedPreferenceManager.get(
        context,
        Key.SUPPORT_REQUEST_COUNT.name,
        0
    )
}

fun setSupportRequestCount(context: Context, count: Int) {
    return SharedPreferenceManager.put(
        context,
        Key.SUPPORT_REQUEST_COUNT.name,
        count
    )
}

fun isSupporter(context: Context): Boolean {
    return SharedPreferenceManager.get(context, Key.IS_SUPPORTER.name, false)
}

fun setSupporter(context: Context) {
    SharedPreferenceManager.put(context, Key.IS_SUPPORTER.name, true)
}
