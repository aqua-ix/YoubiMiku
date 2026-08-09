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

        /**
         * メモリ不足として記録する水位。
         *
         * [ComponentCallbacks2]の定数は深刻さの順に並んでいないため、大小比較では絞れない。
         * `TRIM_MEMORY_UI_HIDDEN`(20)は「画面が隠れた」ことだけを伝えるもので
         * メモリの状況を何も表さないのに`TRIM_MEMORY_RUNNING_LOW`(10)より大きく、
         * 閾値で判定するとホームキーを押すたびにこれで上書きされてしまう
         * （直前の`TRIM_MEMORY_RUNNING_CRITICAL`(15)が消える）。拾う水位を列挙する。
         *
         * `TRIM_MEMORY_RUNNING_MODERATE`は軽度なので入れない。
         * どの定数もAPI 35で非推奨になったが、コールバックはminSdk 23から現行までこの値で届く。
         */
        @Suppress("DEPRECATION")
        private val MEMORY_PRESSURE_LEVELS = setOf(
            // 前面にいる間に届く、システムのメモリが少ないという通知
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            // キャッシュされたプロセスとして、殺される候補になったという通知
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
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
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level !in MEMORY_PRESSURE_LEVELS) {
            // 画面が隠れただけの通知も同じ仕組みで届くため、メモリの状況を表す水位だけを拾う
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
