package com.aqua_ix.youbimiku.support

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

/**
 * 寄付機能を同梱しない`noSupport`フレーバー向けの、何もしない実装。
 */
class NoOpSupportController : SupportController {
    override fun setupMenu(activity: AppCompatActivity, menu: Menu) {}

    override fun onOptionsItemSelected(activity: AppCompatActivity, item: MenuItem): Boolean = false

    override fun onMessageSent(activity: AppCompatActivity) {}
}
