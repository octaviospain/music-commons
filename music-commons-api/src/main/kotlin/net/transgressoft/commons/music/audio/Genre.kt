package net.transgressoft.commons.music.audio

/**
 * @author Octavio Calleya
 */
enum class Genre {
    ROCK, UNDEFINED;

    fun capitalize(): String {
        val replaced = name.replace('_', ' ')
        val capitalized = CharArray(replaced.length)
        capitalized[0] = replaced[0].titlecaseChar()
        for (c in 1 until replaced.toCharArray().size) if (replaced[c - 1] == ' ' || replaced[c - 1] == ',') capitalized[c] =
            replaced[c].titlecaseChar() else capitalized[c] = replaced[c].lowercaseChar()
        return String(capitalized)
    }

    companion object {
        @JvmStatic
        fun parseGenre(value: String): Genre {
            for (genre in entries) {
                if (genre.name.equals(value.replace(" ", "_"), ignoreCase = true)) return genre
            }
            return UNDEFINED
        }
    }
}