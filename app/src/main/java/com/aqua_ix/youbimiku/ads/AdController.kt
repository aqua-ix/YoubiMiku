package com.aqua_ix.youbimiku.ads

import androidx.appcompat.app.AppCompatActivity

/**
 * 広告SDKの呼び出しを隠蔽するインターフェース。
 * `ads` フレーバーには実際に広告を表示する実装、`noAds` フレーバーには何もしない実装を置くことで、
 * 呼び出し側（MainActivity）は広告SDKのクラスを直接参照しない。
 */
interface AdController {
    /**
     * 広告ネットワークを初期化する。
     *
     * RemoteConfigの取得完了を待ってから呼ばれるため、[activity]がすでにonResume済みのことがある。
     * その場合はSDKに再開を伝える必要があるので、[isResumed]で状態を渡す。
     *
     * @param adNetwork RemoteConfigの`ad_network`の値
     * @param actionBarSize バナーをアクションバーの下に表示するために上マージンへ加算する高さ
     * @param isResumed 呼び出し時点で[activity]が表示中（onResume済み・onPause前）かどうか
     * @param onBannerHeightChanged バナーの表示状態が変わったときに、確保すべき高さを通知する
     */
    fun setup(
        activity: AppCompatActivity,
        adNetwork: String,
        actionBarSize: Int,
        isResumed: Boolean,
        onBannerHeightChanged: (Int) -> Unit
    )

    /**
     * インタースティシャル広告を表示する。初期化済みの広告ネットワークがない場合は何もしない。
     */
    fun showInterstitial(activity: AppCompatActivity)

    fun onResume(activity: AppCompatActivity)

    fun onPause(activity: AppCompatActivity)

    fun onDestroy(activity: AppCompatActivity)
}
