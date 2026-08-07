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
        onBannerHeightChanged: (Int) -> Unit
    ) {
        Log.d(TAG, "Ad network is disabled by flavor.")
    }

    override fun showInterstitial(activity: AppCompatActivity) {}

    override fun onResume(activity: AppCompatActivity) {}

    override fun onPause(activity: AppCompatActivity) {}

    override fun onDestroy(activity: AppCompatActivity) {}

    companion object {
        val TAG = NoOpAdController::class.java.name.toString()
    }
}
