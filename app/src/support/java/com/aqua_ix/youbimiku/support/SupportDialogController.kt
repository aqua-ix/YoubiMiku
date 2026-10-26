package com.aqua_ix.youbimiku.support

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aqua_ix.youbimiku.AppLog
import com.aqua_ix.youbimiku.R
import com.aqua_ix.youbimiku.config.RemoteConfigProvider
import org.json.JSONArray

/**
 * `support`フレーバー向けの実装。支援リンクのダイアログとメニュー項目、
 * メッセージ送信回数による自動表示を担当する。
 */
class SupportDialogController : SupportController {
    override fun setupMenu(activity: AppCompatActivity, menu: Menu) {
        val hasSupportLinks = parseSupportLinks(RemoteConfigProvider.supportLinksJson).isNotEmpty()
        menu.add(Menu.NONE, SupportController.MENU_ITEM_ID_SUPPORT, Menu.NONE, R.string.support_menu_title)
            .isVisible = hasSupportLinks
    }

    override fun onOptionsItemSelected(activity: AppCompatActivity, item: MenuItem): Boolean {
        if (item.itemId != SupportController.MENU_ITEM_ID_SUPPORT) return false
        showSupportDialog(activity)
        return true
    }

    override fun onMessageSent(activity: AppCompatActivity) {
        val supportTimes = RemoteConfigProvider.supportDisplayRequestTimes
        val context = activity.applicationContext
        if (supportTimes == null || isSupporter(context)) return

        val supportCount = getSupportRequestCount(context) + 1
        setSupportRequestCount(context, supportCount)
        if (supportCount >= supportTimes) {
            AppLog.d(TAG) { "Support display request count: $supportCount" }
            setSupportRequestCount(context, 0)
            showSupportDialog(activity, requireUrls = true)
        }
    }

    private fun openUrl(activity: AppCompatActivity, url: String) {
        try {
            val uri = Uri.parse(url)
            if (uri.scheme != "http" && uri.scheme != "https") return
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.support_url_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSupportDialog(activity: AppCompatActivity, requireUrls: Boolean = false) {
        val links = parseSupportLinks(RemoteConfigProvider.supportLinksJson)

        if (requireUrls && links.isEmpty()) return

        val view = activity.layoutInflater.inflate(R.layout.dialog_support, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setNegativeButton(R.string.support_later, null)
            .setNeutralButton(R.string.support_already) { _, _ ->
                setSupporter(activity.applicationContext)
                activity.invalidateOptionsMenu()
            }
            .create()

        val container = view.findViewById<LinearLayout>(R.id.support_buttons_container)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.resources.getDimensionPixelSize(R.dimen.support_button_height)
        ).also { it.bottomMargin = activity.resources.getDimensionPixelSize(R.dimen.support_button_margin) }

        links.forEach { (name, url) ->
            val button = Button(activity).apply {
                layoutParams = params
                text = activity.getString(R.string.support_button_label, name)
                isAllCaps = false
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.colorPrimary)
                )
                setOnClickListener { openUrl(activity, url); dialog.dismiss() }
            }
            container.addView(button)
        }

        dialog.show()
    }

    private fun parseSupportLinks(json: String): List<Pair<String, String>> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val name = obj.optString("name")
                val url = obj.optString("url")
                if (name.isNotEmpty() && url.isNotEmpty()) name to url else null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse support_links.", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SupportDialogController"
    }
}
