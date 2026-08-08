package com.aqua_ix.youbimiku.ads

/**
 * `ads` フレーバー用のファクトリ。広告SDKを呼び出す実装を返す。
 */
object AdControllerFactory {
    fun create(): AdController = AdNetworkController()
}
