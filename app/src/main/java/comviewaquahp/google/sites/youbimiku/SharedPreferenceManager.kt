package comviewaquahp.google.sites.youbimiku

import android.content.Context
import android.content.SharedPreferences

open class SharedPreferenceManager {

    companion object {
        private fun instance(context: Context): SharedPreferences {
            val name = context.packageName + "_preferences"
            val mode = Context.MODE_PRIVATE
            return context.getSharedPreferences(name, mode)
        }

        fun get(context: Context, key: String, defValue: String): String? {
            return instance(context).getString(key, defValue)
        }

        fun put(context: Context, key: String, value: String) {
            instance(context).edit().putString(key, value).apply()
        }

        fun get(context: Context, key: String, defValue: Int): Int? {
            return instance(context).getInt(key, defValue)
        }

        fun put(context: Context, key: String, value: Int) {
            instance(context).edit().putInt(key, value).apply()
        }
    }
}

enum class Key {
    USER_NAME,
    FONT_SIZE,
    LAUNCH_COUNT
}