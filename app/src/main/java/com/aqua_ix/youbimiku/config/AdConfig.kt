package com.aqua_ix.youbimiku.config

import android.content.Context

// 旧キー名。AIモデルに依存しない「広告表示用のメッセージ数」なので
// OPENAI_REQUEST_COUNTから[Key.MESSAGE_COUNT_FOR_AD]へ移行した
private const val LEGACY_MESSAGE_COUNT_FOR_AD_KEY = "OPENAI_REQUEST_COUNT"

/** インタースティシャル広告を表示するまでに送信されたメッセージ数 */
fun getMessageCountForAd(context: Context): Int {
    return SharedPreferenceManager.get(
        context,
        Key.MESSAGE_COUNT_FOR_AD.name,
        0
    )
}

fun setMessageCountForAd(context: Context, count: Int) {
    return SharedPreferenceManager.put(
        context,
        Key.MESSAGE_COUNT_FOR_AD.name,
        count
    )
}

/**
 * 旧キーに残っているカウントを新キーへ引き継ぐ。アプリ更新直後の1回だけ意味を持つ。
 * 引き継がないとアップデートしたユーザーのカウントが0に戻ってしまう。
 */
fun migrateMessageCountForAd(context: Context) {
    if (!SharedPreferenceManager.contains(context, LEGACY_MESSAGE_COUNT_FOR_AD_KEY)) {
        return
    }
    if (!SharedPreferenceManager.contains(context, Key.MESSAGE_COUNT_FOR_AD.name)) {
        setMessageCountForAd(
            context,
            SharedPreferenceManager.get(context, LEGACY_MESSAGE_COUNT_FOR_AD_KEY, 0)
        )
    }
    SharedPreferenceManager.remove(context, LEGACY_MESSAGE_COUNT_FOR_AD_KEY)
}
