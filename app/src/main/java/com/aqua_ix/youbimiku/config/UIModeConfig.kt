package com.aqua_ix.youbimiku.config

import android.content.Context

enum class UIModeConfig {
    CHAT,
    AVATAR
}

fun getUIMode(context: Context): String? {
    return SharedPreferenceManager.get(
        context,
        Key.UI_MODE.name,
        ""
    )
}

fun setUIMode(context: Context, mode: UIModeConfig) {
    return SharedPreferenceManager.put(
        context,
        Key.UI_MODE.name,
        mode.name
    )
}