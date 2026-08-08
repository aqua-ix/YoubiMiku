package com.aqua_ix.youbimiku.ads

import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * 広告SDKを同梱しない`noAds`フレーバー向けの、何もしない実装。
 */
class NoOpAdController : AdController {
    override fun setup(
        activity: AppCompatActivity,
        adNetwork: String,
        actionBarSize: Int,
        isResumed: Boolean,
        onBannerHeightChanged: (Int) -> Unit
    ) {
        Log.d(TAG, "Ad network is disabled by flavor.")
    }

    // 広告を表示することはないので常にfalse。呼び出し側のカウントは閾値に留まるだけで、
    // このフレーバーでは使われない
    override fun showInterstitial(activity: AppCompatActivity): Boolean = false

    override fun onResume(activity: AppCompatActivity) {}

    override fun onPause(activity: AppCompatActivity) {}

    override fun onDestroy(activity: AppCompatActivity) {}

    companion object {
        val TAG = NoOpAdController::class.java.name.toString()
    }
}
