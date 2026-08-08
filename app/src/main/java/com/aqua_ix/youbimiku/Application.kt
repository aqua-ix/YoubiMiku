package com.aqua_ix.youbimiku

import android.os.StrictMode
import android.util.Log
import com.aqua_ix.youbimiku.config.SharedPreferenceManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Application : android.app.Application() {

    companion object {
        private val TAG = Application::class.java.simpleName

        lateinit var instance: Application private set

        /**
         * Activityの終了後にも完了させたい後片付け用のスコープ。
         * プロセスと同じ寿命を持つのでキャンセルしない。
         */
        val applicationScope: CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Unhandled error in the application scope", throwable)
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
