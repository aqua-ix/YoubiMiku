@file:Suppress("unused")

package com.aqua_ix.youbimiku.config

import android.content.Context
import com.github.bassaer.chatmessageview.view.ChatView

const val XSMALL = 24f
const val SMALL = 32f
const val MEDIUM = 44f
const val LARGE = 52f

enum class FontSizeConfig(val size: Float) {
    FONT_SIZE_XSMALL(XSMALL),
    FONT_SIZE_SMALL(SMALL),
    FONT_SIZE_MEDIUM(MEDIUM),
    FONT_SIZE_LARGE(LARGE);

    companion object {
        fun getType(name: String?): FontSizeConfig {
            return values().find {
                it.name == name
            } ?: FONT_SIZE_MEDIUM
        }

        fun getType(ordinal: Int): FontSizeConfig {
            return values().find {
                it.ordinal == ordinal
            } ?: FONT_SIZE_MEDIUM
        }

        fun getSize(ordinal: Int): Float {
            return getType(ordinal).size
        }

        fun getSize(name: String?): Float {
            return getType(name).size
        }

    }
}

fun setFontSize(size: Float, view: ChatView) {
    view.setMessageFontSize(size)
    view.setUsernameFontSize(size - 10)
    view.setTimeLabelFontSize(size - 10)
}

fun getFontSizeType(context: Context): String? {
    return SharedPreferenceManager.get(
        context,
        Key.FONT_SIZE.name,
        FontSizeConfig.FONT_SIZE_MEDIUM.name
    )
}