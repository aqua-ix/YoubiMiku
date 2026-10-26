package com.aqua_ix.youbimiku.support

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

/**
 * 寄付（支援）機能の呼び出しを隠蔽するインターフェース。
 * `support` フレーバーには実際に支援ダイアログを出す実装、`noSupport` フレーバーには何もしない実装を置くことで、
 * 呼び出し側（MainActivity）は寄付関連のクラス・リソースを直接参照しない。
 */
interface SupportController {
    /**
     * 支援メニュー項目を[menu]に追加する。表示可否（支援リンクがあるか）の判断も実装側で行う。
     * 項目のidは[MENU_ITEM_ID_SUPPORT]。
     */
    fun setupMenu(activity: AppCompatActivity, menu: Menu)

    /**
     * メニュー項目が選択されたときに呼ぶ。支援項目だった場合は処理してtrueを返す。
     */
    fun onOptionsItemSelected(activity: AppCompatActivity, item: MenuItem): Boolean

    /**
     * メッセージを送信するたびに呼ぶ。設定回数に達したら支援ダイアログを表示する。
     */
    fun onMessageSent(activity: AppCompatActivity)

    companion object {
        /** 支援メニュー項目のid。MainActivityが`menu.add`で使っているid（1）と衝突しない値にする。 */
        const val MENU_ITEM_ID_SUPPORT = 100
    }
}
