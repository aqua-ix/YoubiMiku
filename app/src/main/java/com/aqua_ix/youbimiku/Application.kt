package com.aqua_ix.youbimiku

import android.content.ComponentCallbacks2
import android.os.StrictMode
import com.aqua_ix.youbimiku.config.SharedPreferenceManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Application : android.app.Application() {

    companion object {
        private const val TAG = "Application"

        lateinit var instance: Application private set

        /**
         * Activityの終了後にも完了させたい後片付け用のスコープ。
         * プロセスと同じ寿命を持つのでキャンセルしない。
         */
        val applicationScope: CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
                // 拾いきれなかった例外がログに落ちるだけにならないよう、Crashlyticsにも記録する
                AppLog.e(TAG, "Unhandled error in the application scope", throwable)
            }
        )
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        setupStrictMode()

        // 設定の初回読み込みをメインスレッドで待たないよう、先に読み込ませておく
        applicationScope.launch { SharedPreferenceManager.warmUp(this@Application) }
    }

    /**
     * メモリ不足の通知を記録する。
     *
     * `lowmemorykiller`にプロセスを殺された場合、アプリ側には何も残せずクラッシュにもならない
     * （#101で実際に起きた）。あとから起きたクラッシュのレポートから、その直前に
     * メモリが逼迫していたことを読み取れるようにしておく。
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // TRIM_MEMORY_RUNNING_LOWはAPI 35で非推奨になったが、コールバック自体は
        // minSdk 23から現行までこの水位で届くため、判定にはそのまま使う
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // バックグラウンドに回っただけの通知も同じ仕組みで届くため、逼迫した水位だけを拾う
            return
        }
        AppLog.setCustomKey(CrashlyticsKey.LAST_TRIM_LEVEL, level)
        AppLog.d(TAG) { "onTrimMemory: level=$level" }
    }

    /**
     * メインスレッドでのディスク・ネットワークアクセスをログで気付けるようにする。
     * デバッグビルドのみ、かつ検出のみ（penaltyLog）で、アプリは落とさない。
     */
    private fun setupStrictMode() {
        if (!BuildConfig.DEBUG) {
            return
        }
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
    }
}
