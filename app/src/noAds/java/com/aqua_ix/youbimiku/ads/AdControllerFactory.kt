package com.aqua_ix.youbimiku.ads

/**
 * `noAds` フレーバー用のファクトリ。広告SDKを同梱しないため何もしない実装を返す。
 */
object AdControllerFactory {
    fun create(): AdController = NoOpAdController()
}
