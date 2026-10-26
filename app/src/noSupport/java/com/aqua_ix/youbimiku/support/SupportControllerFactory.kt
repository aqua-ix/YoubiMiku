package com.aqua_ix.youbimiku.support

/**
 * `noSupport` フレーバー用のファクトリ。寄付関連のコードを同梱しないため何もしない実装を返す。
 */
object SupportControllerFactory {
    fun create(): SupportController = NoOpSupportController()
}
