package com.aqua_ix.youbimiku.ads

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.aqua_ix.youbimiku.BuildConfig.BUILD_TYPE
import com.aqua_ix.youbimiku.BuildConfig.IMOBILE_BANNER_SID
import com.aqua_ix.youbimiku.BuildConfig.IMOBILE_INTERSTITIAL_SID
import com.aqua_ix.youbimiku.BuildConfig.IMOBILE_MID
import com.aqua_ix.youbimiku.BuildConfig.IMOBILE_PID
import com.aqua_ix.youbimiku.BuildConfig.IRONSOURCE_APP_KEY
import com.aqua_ix.youbimiku.RemoteConfigKey
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.integration.IntegrationHelper
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.LevelPlayBannerListener
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import jp.co.imobile.sdkads.android.FailNotificationReason
import jp.co.imobile.sdkads.android.ImobileSdkAd
import jp.co.imobile.sdkads.android.ImobileSdkAdListener

/**
 * `ads` フレーバー向けの実装。iMobileとIronSourceのSDKを直接呼び出す。
 * 初期化した広告ネットワークを保持し、ライフサイクル処理は初期化済みのものだけを対象にする。
 */
class AdNetworkController : AdController {
    private var adNetwork = ""
    private var ironSourceBannerLayout: IronSourceBannerLayout? = null

    override fun setup(
        activity: AppCompatActivity,
        adNetwork: String,
        actionBarSize: Int,
        isResumed: Boolean,
        onBannerHeightChanged: (Int) -> Unit
    ) {
        when (adNetwork) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                initImobileBanner(activity, actionBarSize, onBannerHeightChanged)
                initImobileInterstitial(activity)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                initIronSource(activity, actionBarSize, onBannerHeightChanged)
            }

            else -> {
                Log.d(TAG, "Ad network is not configured: $adNetwork")
                return
            }
        }
        this.adNetwork = adNetwork

        // 初期化がonResumeより後になる場合があるため、表示中ならSDKに再開を伝える。
        // iMobileはinitImobileBanner()内でstart()済みなので、ここではIronSourceだけを対象にする。
        if (isResumed && adNetwork == RemoteConfigKey.AdNetwork.IRONSOURCE) {
            onResume(activity)
        }
    }

    private fun initImobileBanner(
        activity: AppCompatActivity,
        actionBarSize: Int,
        onBannerHeightChanged: (Int) -> Unit
    ) {
        ImobileSdkAd.registerSpotInline(
            activity,
            IMOBILE_PID,
            IMOBILE_MID,
            IMOBILE_BANNER_SID
        )
        ImobileSdkAd.start(IMOBILE_BANNER_SID)

        val imobileBannerLayout = FrameLayout(activity)
        val imobileBannerLayoutParam = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        imobileBannerLayoutParam.gravity = Gravity.TOP or Gravity.CENTER
        imobileBannerLayout.visibility = View.INVISIBLE
        activity.addContentView(imobileBannerLayout, imobileBannerLayoutParam)
        ViewCompat.setOnApplyWindowInsetsListener(imobileBannerLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + actionBarSize
            }
            windowInsets
        }
        ImobileSdkAd.showAd(activity, IMOBILE_BANNER_SID, imobileBannerLayout, true)

        ImobileSdkAd.setImobileSdkAdListener(IMOBILE_BANNER_SID, object : ImobileSdkAdListener() {
            override fun onAdShowCompleted() {
                Log.d(TAG, "ImobileSdkAd($IMOBILE_BANNER_SID) onAdReadyCompleted")
                imobileBannerLayout.visibility = View.VISIBLE
                onBannerHeightChanged(imobileBannerLayout.height)
            }

            override fun onFailed(reason: FailNotificationReason) {
                Log.d(TAG, "ImobileSdkAd($IMOBILE_BANNER_SID) onFailed: $reason")
                imobileBannerLayout.visibility = View.INVISIBLE
                onBannerHeightChanged(0)
            }
        })
    }

    private fun initImobileInterstitial(activity: AppCompatActivity) {
        ImobileSdkAd.registerSpotFullScreen(
            activity,
            IMOBILE_PID,
            IMOBILE_MID,
            IMOBILE_INTERSTITIAL_SID
        )
        ImobileSdkAd.start(IMOBILE_INTERSTITIAL_SID)
    }

    private fun initIronSource(
        activity: AppCompatActivity,
        actionBarSize: Int,
        onBannerHeightChanged: (Int) -> Unit
    ) {
        val bannerLayout = initIronSourceBanner(activity, actionBarSize, onBannerHeightChanged)
        initIronSourceInterstitial()
        IronSource.init(
            activity,
            IRONSOURCE_APP_KEY,
            IronSource.AD_UNIT.BANNER,
            IronSource.AD_UNIT.INTERSTITIAL
        )
        IronSource.loadBanner(bannerLayout)
        IronSource.loadInterstitial()
    }

    private fun initIronSourceBanner(
        activity: AppCompatActivity,
        actionBarSize: Int,
        onBannerHeightChanged: (Int) -> Unit
    ): IronSourceBannerLayout {
        val size = ISBannerSize.BANNER
        val bannerLayout = IronSource.createBanner(activity, size)
        ironSourceBannerLayout = bannerLayout
        bannerLayout.apply {
            levelPlayBannerListener = object : LevelPlayBannerListener {
                override fun onAdLoaded(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner loaded: $adInfo")
                    onBannerHeightChanged(bannerLayout.height)
                }

                override fun onAdLoadFailed(error: IronSourceError) {
                    Log.e(TAG, "IronSource banner load failed: $error")
                    onBannerHeightChanged(0)
                }

                override fun onAdClicked(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner clicked: $adInfo")
                }

                override fun onAdScreenPresented(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner screen presented: $adInfo")
                }

                override fun onAdScreenDismissed(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner screen dismissed: $adInfo")
                }

                override fun onAdLeftApplication(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner left application: $adInfo")
                }
            }

            val layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER
            }
            activity.addContentView(bannerLayout, layoutParams)
            ViewCompat.setOnApplyWindowInsetsListener(bannerLayout) { v, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top + actionBarSize
                }
                windowInsets
            }
            if (BUILD_TYPE == "debug") {
                IntegrationHelper.validateIntegration(activity)
            }
        }
        return bannerLayout
    }

    private fun initIronSourceInterstitial() {
        IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
            override fun onAdReady(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial ready: $adInfo")
            }

            override fun onAdLoadFailed(error: IronSourceError?) {
                Log.e(TAG, "IronSource interstitial load failed: $error")
            }

            override fun onAdOpened(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial opened: $adInfo")
            }

            override fun onAdShowSucceeded(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial show succeeded: $adInfo")
            }

            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo) {
                Log.e(TAG, "IronSource interstitial show failed: $error, $adInfo")
            }

            override fun onAdClicked(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial clicked: $adInfo")
            }

            override fun onAdClosed(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial closed: $adInfo")
            }
        })
    }

    override fun showInterstitial(activity: AppCompatActivity) {
        when (adNetwork) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                Log.d(TAG, "ImobileSdkAd.showAd")
                ImobileSdkAd.showAd(activity, IMOBILE_INTERSTITIAL_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                Log.d(TAG, "IronSource.showInterstitial")
                IronSource.showInterstitial()
            }
        }
    }

    override fun onResume(activity: AppCompatActivity) {
        when (adNetwork) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.start(IMOBILE_BANNER_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                IronSource.onResume(activity)
            }
        }
    }

    override fun onPause(activity: AppCompatActivity) {
        when (adNetwork) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.stop(IMOBILE_BANNER_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                IronSource.onPause(activity)
            }
        }
    }

    override fun onDestroy(activity: AppCompatActivity) {
        when (adNetwork) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.stop(IMOBILE_BANNER_SID)
                ImobileSdkAd.stop(IMOBILE_INTERSTITIAL_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                ironSourceBannerLayout?.let { IronSource.destroyBanner(it) }
            }
        }
    }

    companion object {
        val TAG = AdNetworkController::class.java.name.toString()
    }
}
