package com.aqua_ix.youbimiku.support

/**
 * `support` フレーバー用のファクトリ。支援ダイアログを表示する実装を返す。
 */
object SupportControllerFactory {
    fun create(): SupportController = SupportDialogController()
}
