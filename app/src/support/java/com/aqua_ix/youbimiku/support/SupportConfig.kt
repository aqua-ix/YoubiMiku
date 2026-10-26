package com.aqua_ix.youbimiku.support

import android.content.Context
import com.aqua_ix.youbimiku.config.SharedPreferenceManager

// 既存ユーザーの保存値を引き継ぐため、かつて Key enum の name として使っていた文字列をそのまま使う
private const val KEY_SUPPORT_REQUEST_COUNT = "SUPPORT_REQUEST_COUNT"
private const val KEY_IS_SUPPORTER = "IS_SUPPORTER"

fun getSupportRequestCount(context: Context): Int {
    return SharedPreferenceManager.get(context, KEY_SUPPORT_REQUEST_COUNT, 0)
}

fun setSupportRequestCount(context: Context, count: Int) {
    SharedPreferenceManager.put(context, KEY_SUPPORT_REQUEST_COUNT, count)
}

fun isSupporter(context: Context): Boolean {
    return SharedPreferenceManager.get(context, KEY_IS_SUPPORTER, false)
}

fun setSupporter(context: Context) {
    SharedPreferenceManager.put(context, KEY_IS_SUPPORTER, true)
}
