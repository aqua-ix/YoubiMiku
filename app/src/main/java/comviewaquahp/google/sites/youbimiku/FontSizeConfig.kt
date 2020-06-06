@file:Suppress("unused")

package comviewaquahp.google.sites.youbimiku

enum class FontSizeConfig(val size: Float) {
    FONT_SIZE_SMALL(Constants.FONT_SIZE_SMALL),
    FONT_SIZE_MEDIUM(Constants.FONT_SIZE_MEDIUM),
    FONT_SIZE_LARGE(Constants.FONT_SIZE_LARGE);

    companion object {
        fun getType(name: String?): FontSizeConfig {
            return values().find {
                it.name == name
            } ?: FONT_SIZE_MEDIUM
        }

        fun getType(ordinal: Int): FontSizeConfig{
            return values().find {
                it.ordinal == ordinal
            } ?: FONT_SIZE_MEDIUM
        }

        fun getSize(ordinal: Int): Float{
            return getType(ordinal).size
        }

        fun getSize(name: String?): Float {
            return getType(name).size
        }

    }
}