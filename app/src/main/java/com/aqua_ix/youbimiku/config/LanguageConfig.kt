@file:Suppress("unused")

package com.aqua_ix.youbimiku.config

import android.content.Context
import java.util.*

const val JP = "jp"
const val EN = "en"

enum class LanguageConfig(val language: String) {
    LANGUAGE_JP(JP),
    LANGUAGE_EN(EN);

    companion object {
        fun getType(name: String?): LanguageConfig {
            return values().find {
                it.name == name
            } ?: getDefault()
        }

        fun getType(ordinal: Int): LanguageConfig {
            return values().find {
                it.ordinal == ordinal
            } ?: getDefault()
        }

        fun getLanguage(ordinal: Int): String {
            return getType(ordinal).language
        }

        fun getLanguage(name: String?): String {
            return getType(name).language
        }

        fun getDefault(): LanguageConfig {
            return if (Locale.getDefault().language.equals("ja")) {
                LANGUAGE_JP
            } else {
                LANGUAGE_EN
            }
        }

    }
}

fun getLanguage(context: Context): String? {
    return SharedPreferenceManager.get(
        context,
        Key.LANGUAGE.name,
        LanguageConfig.getDefault().name
    )
}